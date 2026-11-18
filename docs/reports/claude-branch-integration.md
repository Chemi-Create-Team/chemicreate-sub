# Claudeブランチのmain統合

作業日: 2026-11-12。

## 確認した方針と対象

選択式の確認で「両方・nifty優先」「競合箇所をClaude優先」が選択された。両ブランチの履歴をmainへ取り込み、競合ではnifty側を優先した。main独自の修正は維持し、元のClaudeブランチは削除・更新しない。

| 対象 | 統合前のコミット |
| --- | --- |
| main | `5ea1a8f84ca34f2d16f6e1b8cdbfef085a1498ec` |
| claude/funny-dirac-4s9vn1 | `b1ddec213abb13284b84cc32379f8a90edc683ce` |
| claude/nifty-noether-s7iinq | `14c1630b02610b1b54aee45ec48c4037563afc15` |

mainを基点にしたdetached worktreeで、funny、niftyの順にマージした。通常のマージ履歴を残し、履歴の書換えや強制プッシュは行わない。

- funnyのマージ: `ad602d62`
- niftyのマージ: `1370245f`
- 統合調整・検証資料の実装コミット: `6e7efbafc7102ae631730f8acb57f88de97b8e34`

## 取り込んだ変更

原子炉制御盤は横4個の卓だけで形成でき、背面に7×4の計器壁を追加できる。炉心図、計器、警報窓、温度表示、制御棒の選択・調整、モード切替、カバー付きAZ-5、PonderをClaude側の実装へ統一した。nifty側の小型計器と細かな目盛も取り込んだ。

電解セルと制御盤は鋼板・真鍮のピクセルアートを採用した。funny側の連結外装とnifty側の電極投入口を両立し、制御盤上段2個の上面から極板・隔膜を装填する。手動操作、回収、Createのファンネル搬入に対応し、セルのハッチは点検専用になる。

主な対象は `ReactorConsoleStructure`、`ReactorConsoleButton`、`ReactorControlConsoleBlockEntity`、同Renderer、電解セル・制御盤のBlock/BlockEntity、登録、モデル、画像、生成スクリプト、Ponderとその構造データ。

## 統合時の調整

- 新しい制御盤からもmainの改良済み `ReactorAutomaticControl.nextTarget` を呼ぶよう、定格出力・反応度の引数を接続した。小数の制御目標とその保存形式も維持した。
- 翻訳は行単位の競合解決だけではmainの中国語追加分やCO/CO₂の項目が落ちるため、共通祖先からClaudeが変更したキーだけを上書きした。廃止された制御盤の余分な中国語キーを整理し、日英中を各2,475キーで揃えた。
- 共有FakePlayerの手持ち・しゃがみ状態を保存復元するmain側の試験補助と、Claude側のしゃがみACK操作によるランプ試験を両立した。
- ニッケル極板の使用・回収・ドロップ試験を、新しい制御盤上面からの投入へ変更した。
- 水素製造Ponder、電解セルPonder、両生成スクリプト、`DEVELOPMENT_CHEMISTRY.md`、電解槽の説明を新しい投入位置へ合わせた。新しい説明は操作中心に短くした。
- `DESIGN.md` の旧外装指定とAUTOの記述を、今回採用した外観および保持したmain側の制御則に合わせた。

mainの吐出室圧力による圧縮機制御、AUTO制御則、CO/CO₂と反応、外部登録API、クリエイティブタブ、翻訳補完、Ponder簡略化は維持した。炉心物理・燃料・最大炉765 MWの定格校正は変更していない。

## 検証結果

環境はJava 21、Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10、Electro Energetics 1.1.4。GPUはNVIDIA RTX 4060、ドライバー595.99.02で、`mc-debug glxinfo -B` によりハードウェア描画を確認した。

| 確認 | 結果 |
| --- | --- |
| `bash gradlew --no-daemon build` | 成功。最終ビルドも成功 |
| 計算テスト | Haber-Bosch 1,019件、FluidPhase 25件、ReactorPhysics 1,670件、ReactorControl成功 |
| 通常の全GameTest | 616件中614件成功。失敗2件は既存mainと同じ |
| 電解セル単独GameTest | 12件すべて成功。上面ファンネル搬入・手動投入/回収・保存・電力・流体搬送を含む |
| AUTO冷態起動の独立試験 | main・統合版とも4件すべて成功。各10,000 tick |
| 翻訳検査 | 日英中2,475キー、重複・空欄・書式引数の不一致なし |
| Ponder言語検査 | 137シーンの日英中見出しと説明を確認 |
| 設備Ponder構造検査 | 99追加シーン、520対の説明、243部品リンク、26製造系列を確認 |
| 音定義検査 | 11種類成功 |
| 変更したリソースJSON | 138ファイルを解析し、MOD内モデル・画像参照の欠落なし |
| 生成元との照合 | 変更した日英中の電解セル・水素製造説明が生成スクリプトと一致 |
| GPUクライアント | 2回とも137 Ponderの検査成功。実ワールドの制御盤と電解セルも検査成功。終了時クラッシュは下記参照 |

通常GameTestの既存失敗は次の2件。mainの前回全件結果は615件中613件成功で、今回の欠落翻訳86項目の集合も同じだった。比較は [server-results.json](../quality/claude-main-merge/server-results.json) に保存した。

- `VesselFoulingGameTests.aFouledProcessStopsThenResumesAfterOneManualBrush`
- `AuditGameTests.everyRegisteredEntryHasAssetsTranslationsAndLoot`（英日それぞれの旧流体ブロック別名キー、計86項目）

### AUTOの数値

| 条件 | 定格 MW | main最大 MW | 統合版最大 MW | 統合版最大燃料温度 K |
| --- | ---: | ---: | ---: | ---: |
| 円形直径5・燃料部3・高濃縮 | 36.336812 | 37.153429 | 37.153429 | 1,334.94 |
| 正方形5×5・燃料部3・高濃縮 | 73.499460 | 74.117461 | 74.117461 | 1,467.66 |
| 円形直径13・燃料部7・通常 | 765 | 743.984633 | 743.984633 | 1,478.84 |
| 円形直径5・燃料部3・通常（比較用） | 36.336812 | 47.388249 | 52.415739 | 1,303.85 |

配置・開始tickも含む値は [auto-comparison.json](../quality/claude-main-merge/auto-comparison.json) を参照。通常燃料の最小炉は開始条件による差があり、統合版の単独実行では最大144.25%だった。炉心物理とAUTO制御則はmainと同一であり、これを新しい制御則の性能改善とは扱わない。

初回、長時間AUTOと通常試験を混ぜた82件の実行では、通常燃料最小炉の最大56.268379 MW（154.85%）と、ファンネルの搬入未成立で2件失敗した。その後、電解セル単独12件、通常全件616件、ExplicitのAUTO単独4件では該当試験が成功した。混合実行の失敗原因・再発条件は未特定。試験の閾値や制御則を緩めず、混合実行の失敗も残る制約として記録する。

### 実クライアント

制御盤は7×4壁・4×4炉心図・121点のプレビュー同期を確認した。この確認値は表示用プレビューであり、実炉の連続運転結果ではない。GameTestでは4方向の形成、壁を外した卓単独の運転、物理操作、保存を確認した。

電解セルは2×2×8の32セルを実CEE回路へ接続し、約46.6406 V・1,740.28 W、2周期/tで稼働した。採取時の在庫は水25,667 mB、水素222 mB、酸素111 mB。稼働率37%では667.51 Wとなった。開ハッチの停止、液面・計器の補間、試験用に抜いた16,000 mBの全量復帰、STOP後の無給電を検査した。

結果と画面:

- [電解セルの実回路検査](../quality/claude-main-merge/electrolysis-world-quality.json)
- [制御盤の画面](../quality/claude-main-merge/reactor-console.png)
- [電解セルPonderの画面](../quality/claude-main-merge/electrolysis-ponder.png)
- [制御盤側のPonder検査](../quality/claude-main-merge/ponder-console-ja_jp.json)
- [電解セル側のPonder検査](../quality/claude-main-merge/ponder-electrolysis-ja_jp.json)

両クライアントとも `CLIENT QUALITY PASSED: 137 scenes, ja_jp` の後、終了処理で既存の `SIGSEGV`、`libEGL_nvidia.so.0+0xb56ae` を再現し、JVM終了値134・Gradle終了値1となった。表示・実回路検査の成功と、プロセスの異常終了を区別する。今回この終了クラッシュは修正しておらず、原因も断定しない。

## 再実行

Java 21を使用する。専用サーバーは次のように通常試験とExplicit試験を分ける。

```sh
bash gradlew --no-daemon build
bash gradlew --no-daemon -I docs/quality/maintenance/server.gradle -DtestDir=/tmp/createplant-claude-full -DtestClasses= runGameTestServer
bash gradlew --no-daemon -I docs/quality/maintenance/server.gradle -DtestDir=/tmp/createplant-claude-auto-isolated -DtestClasses=ReactorAutoStartupGameTests runGameTestServer
bash gradlew --no-daemon -I docs/quality/maintenance/server.gradle -DtestDir=/tmp/createplant-claude-electrodes -DtestClasses=ElectrolysisCellGameTests runGameTestServer
ALSOFT_DRIVERS=null mc-debug bash gradlew --no-daemon -I docs/quality/claude-main-merge/client.gradle -Dfixture=console runClient
ALSOFT_DRIVERS=null mc-debug bash gradlew --no-daemon -I docs/quality/claude-main-merge/client.gradle -Dfixture=electrolysis runClient
```

今回の実行では `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2` を使用した。クライアントは日本語で確認した。

## 移行と未確認事項

- 原子炉制御盤の旧62個配置はそのままでは形成しない。横4個の卓と、必要なら7×4の壁へ組み直す。これは取り込んだClaude側の仕様変更。
- 極板はセル内の既存インベントリへ保存される。今回の装填口変更で保存場所は変えないが、セルのハッチ上面への既存搬送設備は制御盤上段の上面へ付け替える。
- 実際の既存ユーザーワールドの移行、英語・中国語の実画面、全方向の実クライアント表示、大規模負荷・マルチプレイは未確認。
- 核融合プラント全体や60,000 tickの燃料連続運転試験は今回再実行していない。
- 既存2件の通常GameTest失敗、混合Explicit実行時の不安定性、NVIDIA終了クラッシュは未解決として引き継ぐ。
