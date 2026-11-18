# RBMK原子炉データリンク実装レポート

## 基準とコミット

- 最新化した基準コミット：`59048f39b443edd062da54a4c16bd5cf0242adac`
- 実装コミット：`e2a80f1b27ad387be3bcd400a450c53a5a94ad1f`
- 作業ブランチ：`work`（検証後に `main` へ反映）

作業中にリモート `main` が2コミット進んだため、重油水素化分解・硫酸系の最新実装へリベースした。競合した日英翻訳は、最新側の全項目と今回の原子炉データリンク説明を両方残して解消した。RBMKアセット生成器を再実行して、生成物が最新翻訳を失わず再現できることも確認した。

## 実装した接続方式

- 既存ワールドとの互換性を保つため登録ID `control_rod_link` を維持し、表示名と用途を「原子炉データリンク」へ拡張した。Createのレッドストーンリンクと同じ2個の周波数アイテムをそのまま使用する。
- 形成済み原子炉の燃料チャネル、上部管・キャップ、バイオシールド、駆動部、冷媒入出力部、給水細管束へ接触したリンクを原子炉側端末として登録する。
- 6×2操作卓または3×3壁面パネルへ接触したリンクを制御室側端末として登録する。従来の「64ブロック以内の最寄り炉心」接続は廃止し、同一周波数の明示的な両端末がある場合だけ計器・炉心マップ・個別制御棒・AZ-5を有効にした。
- 同じ周波数が形成済み炉心を複数示す場合は、誤操作を防ぐため一方を勝手に選ばず `ambiguous` として接続を拒否する。欠落、炉心なし、周波数重複、接続済みをゴーグルと制御卓に表示する。
- 登録済みかつ読み込み済みの端末・炉心だけを参照し、遠方チャンクを強制読み込みしない。端末撤去、周波数変更、チャンク読み込み状態も遅延tickで再評価する。
- 原子炉データ端末だけを置いた時に全制御棒へ深度0を誤送信しないよう、100段階コントローラーが実際に隣接するリンクだけが0～100の深度指令を送る。

## Create標準表示との連携

- Create標準のDisplay Linkを原子炉データリンクへ取り付けると、一意に接続された炉心を既存の機械計測ソースとして扱う。独自GUIや独自表示ブロックは追加していない。
- 1個のDisplay Linkにつき、状態、形成状態、炉心出力、反応度、ボイド率、最高燃料温度、冷却水温度、キセノン、平均制御棒挿入率から1項目を選び、Display BoardまたはNixie Tubeへ出力できる。
- 制御卓本体へ付けたDisplay Linkでは、上記に加えて選択中制御棒の実深度と目標深度を出力できる。
- 既存の炉心計測で欠けていた冷却水温度の数値経路を追加した。リンク未接続または周波数重複時は値を捏造せず未計測として返す。

## 教材・文書・回帰試験

- 炉心Ponderへ原子炉側端末を、制御室Ponderへ制御卓側端末とCreate標準Display Linkを追加した。日英の説明、ブロック名、ゴーグル文言を更新した。
- `docs/rbmk-reactor.md` を明示リンク方式、重複拒否、Display Board／Nixie Tube出力に合わせて更新した。
- GameTestは、近くに炉心があるだけでは接続しないこと、両端末で接続すること、実際のCreate `DisplayLinkBlockEntity`が冷却水温度を出力すること、同一周波数の2炉心を拒否することを確認する。既存の1本ずつの制御とAZ-5全挿入も、データリンク経由で引き続き検証する。

## 最終検証結果

- Java 21 / NeoForge 21.1.219 / Create 6.0.10：`gradlew build` 成功。
- 単体検証：FluidPhase 25件、Haber-Bosch 1,019件、ReactorPhysics 44件が合格。
- 最新 `main` 統合後のNeoForge GameTest：必須263件すべて合格（24.93分）。結果確定後のテストワールド保存だけが既知のチャンクFuture待ちになったため、ログ保存後にプロセスを終了した。
- RBMK生成器を再実行後、作業ツリーに未生成差分なし。`git diff --check` 合格。
- NVIDIA GeForce RTX 4060 / OpenGL 4.6 / Direct Renderingで日本語クライアントを起動し、品質チェック88シーン、実行時翻訳、対象Ponder描画が合格した。
- クライアントは品質判定、画像、JSON、ワールド保存完了後の終了処理で、環境既知のNVIDIA `libEGL_nvidia.so.0` SIGSEGVを発生させた。描画判定と保存成果物には影響しない。
- 全機械向けの既存 `DisplayClientCheck` も試行したが、今回の対象へ到達する前に既存の水／蒸気タンク用ゴーグル行数期待（期待4行、実際3行）で停止した。今回の標準Display Link経路は専用GameTestとPonderクライアント検証で確認済みで、この既存失敗に関係するコードは変更していない。

## 成果物

- `/workspace/outputs/Create-Plant-reactor-data-link.jar`
  - SHA-256：`5a2f3592578b3e24c75574a50494ed7ba19f0e6104ffc790951ca18643d1873c`
- `/workspace/outputs/reactor-data-link-ponder.png`
  - SHA-256：`093a8996660a30a05571348504f7b4378d135d45dc28a3e68819550981c40539`
- `/workspace/outputs/reactor-data-link-client-check.json`
  - SHA-256：`61f52bcba102f124ef6e4da69d024edb56e6d79b4f10e15d468f9f519c335ac1`
- `/workspace/outputs/reactor-data-link-gametest-263.log.gz`
  - SHA-256：`514f1b6e1525125d47b77b7b612ddee35b61a7454fafeb34b348a33bd46b59ed`

## 主な変更箇所

- `ControlRodLinkBlockEntity`、`ControlRodLinks`：端末役割、周波数ネットワーク、一意性判定、制御卓登録。
- `ReactorControlConsoleBlockEntity`：最寄り炉心探索を明示データリンク接続へ置換。
- `ReactorCores`：炉体・遮蔽・上下サービス空間から形成済み炉心を安全に解決。
- `MachineReadings`：データリンクから炉心への所有者プロキシと冷却水温度出力。
- `RbmkGameTests`：実Create Display Link、未接続、接続、重複拒否、個別制御、AZ-5の回帰試験。
- `RbmkPonderScenes`、`ElectrolysisPonderPlugin`、`tools/generate_rbmk_assets.py`：教材、構造、翻訳、再生成経路。
