# 第2工程の転位・ろ過レシピ

実装コミットは `d166562334a8fe1b79f09b96660c7e938dc3ff80`。

## 実装

指定の第2工程をゲーム内の転位とろ過の2レシピに分けた。既存のふた付き撹拌槽、Createのポンプ、スマート液体パイプ、ファンネルで接続できる。両方の槽はふたを閉め、撹拌羽根を入れて底面中央から駆動する。

| ゲーム内工程 | 1バッチの入力 | 出力 | レシピ進捗 |
| --- | --- | --- | --- |
| 転位 | 亜リン酸トリメチル 1,000 mB、ヨウ素1個 | 粗生成液 1,000 mB | 120 tick |
| ろ過 | 粗生成液 1,000 mB、水処理ろ材1個 | メチルホスホン酸ジメチル 1,000 mB、ヨウ素1個 | 80 tick |

ゲーム内では液量が各段で等しく、触媒は最初の槽でいったん投入され、次の槽の固体出力から同数を回収する。ろ材はバッチごとに消費する。各出口のスマート液体パイプに対応する生成物入りバケツを指定すると、中間液と製品を個別に搬出できる。

第2工程への初回供給として、亜リン酸トリメチル入りバケツとヨウ素をクリエイティブタブに用意した。第1工程とヨウ素の採取は未実装。サバイバル到達性の監査では、この外部供給を前提とする素材とその派生バケツを `CREATIVE_ONLY` に記録した。第1工程をつないだら派生バケツも含めて再監査する。

## 主要なファイル

- `registry/CEProcessFluids.java` と `registry/CEItems.java` が液体3種とヨウ素を登録する。
- `recipe/pressure_reaction/trimethyl_phosphite_rearrangement.json` と `methylphosphonate_filtration.json` がバッチを定義する。
- `tools/generate_manufacturing_ponder.py` と `tools/data/ponder_lessons.json` が製造Ponderの正本。`python3 tools/generate_manufacturing_ponder.py manufacturing_methylphosphonate` で個別に再生成できる。
- `gametest/ReactorGameTests.java` の `filtrationReturnsTheIodineForAnotherRearrangement` が、原料・ろ材の有無と、触媒を回収して2バッチを処理できることを確認する。

## 検証

- Java 21、Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10で `bash gradlew --no-daemon --max-workers=2 build` が成功した。
- 専用サーバーで `ReactorGameTests`、`WaterTreatmentGameTests`、`ManufacturingPonderGameTests`、`SurvivalReachabilityGameTests`、`CreativeCatalogGameTests`、`AuditGameTests`、`RecipeVisibilityGameTests` を実行した。新しい2バッチの試験を含む33件中32件が合格した。失敗した `cooledInputsAndAllManufacturingRecipesRemainValid` は、変更前の `fa5090f` でも同じ冷却流体の検査で再現した。
- `validate_plant_tutorials.py`、`validate_ponder_language.py`、`validate_translations.py` が合格した。`validate_visual_identity.py` は、変更前からある `sulfur_scrubber_liquor_bucket.json` が別のバケツ画像を参照する箇所で失敗した。追加した4点の画像を個別に検証した。
- RTX 4060で起動した日本語クライアントで、ヨウ素から新しいPonderが開き、槽のモデルと日本語タイトルが表示されることを確認した。専用サーバー試験では槽の入出力ハンドラーで新工程を検証した。新工程の配管一式を実ワールドに設置しての通し運転は未確認。
