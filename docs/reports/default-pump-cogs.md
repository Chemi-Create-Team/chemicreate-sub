# ポンプの歯車をCreate標準色へ戻す

実装コミット：3f64bdbe3673288fd046bb661392a13a3fed0ba9

2026-11-16のユーザー指定に対応。対象は前回追加した材質別ポンプ10種。本体は対応する配管の画像を保持し、回転する歯車とアイコン内の歯車をCreate標準のMechanical Pumpと同じ色に変更した。

## 実装

- `ElectrolysisClient.java`：Create 6.0.10の `PumpRenderer` を直接登録。材質別PartialModelを参照していた専用 `MaterialPumpRenderer` と10個の不要な `cog.json` を削除。
- `models/block/material_pump/item.json`：解決済みCreate 6.0.10のポンプアイテムモデルから共通の形状を取得。先頭2要素の端部フランジは材質参照 `#4`、残る8要素の歯車は `#cog` に分け、後者を `create:block/pump` へ固定。頂点・回転・UVと表示変換はCreate標準のまま。
- 各材質の `item.json`：共通モデルを継承し、本体とparticleだけを従来の配管画像に差し替える。
- `tools/generate_material_pumps.py`：同じ構成を再生成するよう更新。`DESIGN.md` と `docs/material-pumps.md` の外観仕様も更新。
- 配管本体のモデル・テクスチャ、登録ID、搬送、回転速度、応力、レシピ、保存データは変更していない。既存Ponderは同じBE描画を通るため、歯車も標準色で表示する。

## 確認

- Java 21、Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10。
- `./gradlew --no-daemon build` 成功。原子炉物理1,668項目と制御テストも成功。
- 10種類の本体モデルをGitの変更前とJSON比較し、すべて同一であることを確認。
- 共通アイテムモデルの全10要素について、材質参照以外のデータが依存先Createの形状・回転・UVと完全に一致することを確認。
- モデルJSONと生成スクリプトの構文、`git diff --check`、JAR内に旧専用renderer・材質別cogモデルが残らないことを確認。
- `mc-debug glxinfo -B` でNVIDIA GeForce RTX 4060を確認。既存 `MaterialPumpClientCheck` で10種のワールド表示と回転、10種のアイコンを実画面で確認。スクリーンショットは `/workspace/outputs/default-pump-cogs-world.png` と `default-pump-cogs-icons.png`。
- 動作を変える修正ではないため、前回の搬送GameTest17件は今回再実行していない。新しいテストも追加していない。

## 制約・次回の注意

アイテムの本体と歯車はCreate本来のモデルで同じ `#4` を共有するため、そのまま全体をリテクスチャすると歯車も材質色になる。分離した共通モデルを維持すること。Createの依存バージョンを更新する場合は、先頭2要素がフランジ、残りが歯車というモデル構成を再確認する。

実クライアントの検証は日本語・横向きの既存fixtureで実施。搬送ロジックや全方向の再検証は行っていない。コンテナでは従来どおり終了時にNVIDIAの `libEGL_nvidia.so.0` でexit 134となる場合があり、画面確認を `runClient` タスク全体の成功とは扱わない。

配布JARのSHA-256：`818e6506e7f6307755984caa3bd106c6e929884e3a2b6243ce8e9ccb8f4f16bb`
