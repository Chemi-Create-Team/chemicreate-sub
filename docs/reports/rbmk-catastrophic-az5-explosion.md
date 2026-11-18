# RBMK・AZ-5破局的爆発 実装完了レポート

## 基準と実装コミット

- 作業ブランチ：`work`
- pull後の基準コミット：`7a3ba1b3`（`RbmkPlaytestWorldの変更を保存`）
- 実装コミット：`887abd5bfaf782a9f226cb3e8001b921516f9057`
- 基準コミットに残っていた `RbmkPlaytestWorld.java` の競合マーカーを解消し、新しい `RbmkPlaytestPlant` を使う経路へ統一した。
- プレイテスト用プラントに残っていた旧4×4区画・旧15ブロック高の定数も、現行の3×3区画・17ブロック高・燃料894列・制御棒111列へ合わせた。

## 実装内容

通常の炉心破損は従来どおり、蒸気爆発が最大10、炉心爆発が最大16である。今回の大爆発は、次の3条件が同時に成立した例外的なAZ-5事故だけに限定した。

1. AZ-5（緊急停止）が作動中である。
2. 制御棒がほぼ全引抜、キセノンが多い、冷却水が沸騰直前という3条件から算出した危険度が `JAM_HAZARD = 0.3` を超える。
3. 黒鉛先端効果で、AZ-5作動時の基準出力（最低でも定格の1%）の10倍を超え、制御棒が固着する。

この条件で燃料チャネルが破損すると、次の順で破局的事故を起こす。

1. 上部圧力管と上部2ブロックの生体遮蔽・エレナを、少量の不規則な残骸を残して先に破砕する。
2. 炉心上面で強さ14～24の蒸気爆発を起こす。
3. 40 tick後、炉心中央で強さ34～48の主爆発を起こす。
4. 4 tick間隔で、炉心周囲に強さ12～16の二次爆発を8回起こす。
5. 残った黒鉛・燃料チャネルと飛散した黒鉛に着火する。

状態表示には `chernobyl_explosion` を追加し、日本語では「AZ-5破局的爆発」、英語では「Catastrophic AZ-5 explosion」と表示する。

## 強度計算

- 破局的蒸気爆発：`clamp(14 + 7h + cbrt(冷却水kg) / 2, 14, 24)`
- 破局的主爆発：`clamp(34 + 10h + 5 log10(1 + 破損時W / (燃料列数 × 1 MW)), 34, 48)`
- 二次爆発：`clamp(主爆発 × 0.34, 12, 16)`
- `h` はAZ-5作動時に記録した危険度（0～1）。炉心サイズと破損時出力を反映しつつ、Minecraftサーバーを一度の無制限爆発で停止させないよう上限を設けた。

実機プレイテストの最大事故条件では、蒸気24、主爆発48、二次爆発16×8回になった。

## 主な変更ファイル

- `src/main/java/net/createplant/nuclear/ReactorCore.java`：事故種別の判定、破局的爆発の開始。
- `src/main/java/net/createplant/nuclear/ReactorCores.java`：上部構造の破砕、主爆発と二次爆発の時系列処理。
- `src/main/java/net/createplant/nuclear/ReactorPhysics.java`：条件判定と爆発強度計算。
- `src/main/java/net/createplant/client/RbmkExplosionPlaytestWorld.java`：実寸プラントを使う自動視覚テスト。
- `src/main/java/net/createplant/client/RbmkPlaytestPlant.java`、`RbmkPlaytestWorld.java`：現行3×3区画・17ブロック高への整合と競合断片の解消。
- `src/test/java/net/createplant/nuclear/ReactorPhysicsTest.java`：発動条件、非発動条件、強度上限・下限の単体テスト。
- `docs/rbmk-reactor.md`：事故仕様の説明。

## 検証結果

### ビルドと単体テスト

Java 21、JEIなし、オフライン依存キャッシュで `clean build` に成功した。

| 対象 | 結果 |
| --- | --- |
| FluidPhase | 25件合格 |
| HaberBoschProcess | 1019件合格 |
| ReactorPhysicsTest | 37件合格 |
| 配布JAR | 3.1 MiB、生成成功 |
| `git diff --check` | 合格 |
| 英語・日本語JSON | `jq`で構文確認済み |
| 競合マーカー | `src`・`docs`内になし |

### GPU実機視覚テスト

- GPU：NVIDIA GeForce RTX 4060
- OpenGL：4.6、直接レンダリング有効
- 起動オプション：`-Dcreateplant.rbmkExplosionPlaytest=visual`
- 実寸17ブロック高のプラントを建設し、爆発前後を撮影して自動判定に合格した。

| 計測値 | 結果 |
| --- | ---: |
| 蒸気爆発 | 24.0 |
| 主爆発 | 48.0 |
| 二次爆発 | 16.0 × 8回 |
| 明示的に破砕した上部構造 | 3111ブロック |
| 調査範囲の爆発前ブロック数 | 19439 |
| 調査範囲の爆発後ブロック数 | 10049 |
| 調査範囲で破壊されたブロック数 | 9390 |

爆発後は上部遮蔽と圧力管が大きく開口し、地面まで達するクレーター、破壊されたクレーン、不規則に残る下部遮蔽、飛散した黒鉛火災を目視確認した。

## 成果物

- `create-heavy-indusstry-0.1.0-az5-catastrophic.jar`
  - SHA-256：`40582293f9f380e7443986f5d36781ca8bbf8a77b68b48761ecf19a8e889d482`
- `rbmk-az5-explosion-before.png`
- `rbmk-az5-explosion-after.png`
- `rbmk-az5-explosion-result.json`

いずれも `/workspace/outputs` に保存した。

## 既知の制約と未確認事項

- 全GameTestは完走していない。232件を開始し、多数は通過したが、既存の `GasCompressionGameTests.actualcreatepumptransfersbetweensmallcompressors` が状態不一致で失敗した。その後Minecraftのチャンク保存I/Oが長時間停滞したため停止した。今回変更した原子炉系コードとは別領域で、従来レポートにも同系統の不安定要素がある。
- 既存の3つの検証スクリプトは、今回変更していないベースラインのアセットで失敗する。内訳はチュートリアルサイズ、既存 `pressure_relief` 見出し欠落、既存バケット数の期待値である。
- 視覚テストは合格ログ、結果JSON、スクリーンショットの保存まで完了したが、その後のMinecraft終了処理でNVIDIA `libEGL_nvidia.so.0` がSIGSEGVを起こし、Gradleは終了コード134になった。事故判定後の開発環境固有の描画終了問題で、ゲーム中の検証結果には影響しない。
- Maven取得先のDNS・証明書問題を回避するため、検証時だけCreate 6.0.10と同梱依存を `/tmp` のflatDirから読ませた。この一時設定はリポジトリへ含めていない。
- 保留中の炉心爆発は従来どおりメモリ上だけに存在する。蒸気爆発から主爆発までの40 tick内に保存・再読込すると、後続爆発は復元されない。
- 複数の最大炉心を同時に破局的爆発させた場合の専用サーバー負荷は未計測。

## 今後の調整時の注意

- 大爆発の発動条件は `scram && jammed && hazard > JAM_HAZARD` のまま維持し、通常の過熱事故まで一律に巨大化させないこと。
- 爆発回数・範囲を増やす場合は、専用サーバーでチャンク保存時間とtick負荷を先に計測すること。
- 既存の通常事故（蒸気最大10、炉心最大16）は、プレイヤーの小さな炉心や通常の冷却喪失事故に必要な別プロファイルとして残すこと。
