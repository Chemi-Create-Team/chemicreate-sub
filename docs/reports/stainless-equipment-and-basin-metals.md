# 装置をステンレス鋼製にし、ステンレス鋼までを鉢で作れるようにした完了レポート

- 日付：2026-11-18
- 実装コミット：このレポートと同じコミット（`git log -1 -- docs/reports/stainless-equipment-and-basin-metals.md`で確認）
- 説明：[DESIGN.md「装置の材質」](../../DESIGN.md#装置の材質)、[金属](../metals.md)、[密閉鉢の構想](../specs/sealed-basin.md)
- 前の作業：[硫黄を空気で燃やすレシピと内張りの設定](sulfur-in-air-and-lined-equipment.md)（内張りは取り消し済み）

## 指示

1. 装置は基本的にステンレス鋼製にする（選択肢3：ふた付き槽・吸収塔・電解セルもステンレス鋼にし、ニッケルを炭素で還元する道を足す）。
2. 序盤用の密閉鉢（メカニカルミキサーを改造して密閉したもの）は記録だけして実装しない。

## 行き詰まりの解消

装置をステンレス鋼にすると、ステンレス鋼までの金属を装置なしで作る必要がある。調べると、ニッケル（水素還元・酸素焙焼が撹拌槽）に加えて、クロム（テルミットが撹拌槽、材料のアルミニウムはジルコニア＝撹拌槽が要る溶融塩電解炉）も行き詰まっていた。そこで以前提案したフェロクロムも合わせて入れ、ステンレス鋼までをCreateの鉢とメカニカルミキサーだけで作れるようにした。

## 実装

| 部分 | 内容 |
| --- | --- |
| 鉢のレシピ（`create:mixing`） | ペントランド鉱2 → 酸化ニッケル1＋鉄塊3（加熱、空気焙焼で硫黄は気体として抜ける）。酸化ニッケル1＋炭素粉1 → ニッケルインゴット1（超加熱）。クロム鉄鉱2＋炭素粉1 → フェロクロム1（超加熱）。鉄インゴット6＋フェロクロム2＋ニッケル1 → ステンレス鋼インゴット10（超加熱、従来の鉄7・クロム2・ニッケル1と同じ組成） |
| 新アイテム | `ferrochrome`（フェロクロム）。テクスチャはクロムインゴットを暗い鉄色にしたもの。中間素材のタブ |
| 装置のレシピ | ふた付き槽：鉄板 → ステンレス鋼板。大型吸収塔：鉄板 → ステンレス鋼板（基本段から中段へ）。電解セル・電極ヘッド：鉄板 → ステンレス鋼板。電解セル制御盤は流体に触れないので鉄板のまま |
| 残した経路 | 撹拌槽の酸素焙焼・水素還元（二酸化硫黄と水を回収できる量産向け）、テルミットの純クロム（純クロムを使う部品向け）、純クロムからのステンレス鋼、転炉のAOD |
| 製造ガイド | 「ニッケル」：粉砕 → 鉢で空気焙焼 → 鉢で炭素還元 → （撹拌槽ができたら）酸素焙焼 → 水素還元 → 板。「クロム」：粉砕 → 鉢でフェロクロム → 鉢でステンレス鋼 → テルミットの純クロム → 純クロムからのステンレス鋼。「鉄鋼」：ステンレス鋼の工程をフェロクロムのレシピにした |
| 実績の順番 | 鋼鉄 → ニッケル → ステンレス鋼 → ふた付き槽 → 電解セル → 硫酸 →（浸出・ジルコニア → アルミ）。タービン・石油はステンレス鋼から |
| 説明書 | 23ページに（ステンレス鋼のページを鋼鉄の次に追加）。目次「2 最初に：鋼鉄・ステンレス・槽」、ふた付き槽の材料をステンレス鋼板に、8章からステンレス鋼の行を削除し、ニッケルとクロムの記述を「量産・純粋なクロム」向けに変更 |
| 文書 | `DESIGN.md`「装置の材質」に、装置のレシピとステンレス鋼までを鉢で作る原則、密閉鉢の構想へのリンクを追記。`docs/metals.md`に鉢の工程の表。`docs/specs/equipment-roles.md`の段階の表。`docs/specs/sealed-basin.md`（密閉鉢の構想、未実装） |

### 主要ファイル

- 生成元：`tools/generate_carbon_reduction.py`（新規：鉢のレシピ・フェロクロムのテクスチャとモデル・翻訳）、`tools/generate_crafting_recipes.py`、`tools/generate_lidded_vessel.py`、`tools/generate_electrolysis_cell.py`、`tools/generate_manufacturing_ponder.py`、`tools/data/ponder_lessons.json`、`tools/generate_manual.py`
- `registry/CEItems.java`、`registry/CECreativeModeTabs.java`
- `gametest/CarbonSteelGameTests.java`、`gametest/CraftingRecipeGameTests.java`、`gametest/ManualGameTests.java`

## 確認

- `compileJava`成功。`validate_ponder_language.py`・`validate_plant_tutorials.py`・`validate_translations.py`合格。説明書のページ寸法の確認に合格。
- 新しいGameTest `pentlanditeAndChromiteBecomeStainlessSteelInTheBasinAlone`：Createの鉢で4つのレシピを順に実際に適用し、指定のバーナーの熱で、材料をすべて使って決まった数の製品（酸化ニッケル1・ニッケル1・フェロクロム1・ステンレス鋼10）ができる。
- 方針の変更に合わせてテストを変更：`theRefiningEquipmentNeedsNoRefinedMetal` → `theRefiningEquipmentNeedsOnlyBasinMadeStainlessSteel`（装置は6種の金属インゴットを使わない。ステンレス鋼は使ってよいが、鉢の4工程が存在すること）。説明書のページ数22 → 23。
- GameTest：`CarbonSteelGameTests`、`CraftingRecipeGameTests`、`ManualGameTests`、`ReactorGameTests`、`AbsorptionTowerGameTests`、`ManufacturingPonderGameTests`、`PlantTutorialGameTests`、`RecipeVisibilityGameTests`、`SurvivalReachabilityGameTests`、`ElectrolysisCellGameTests`、`BasinCompatibilityGameTests`：以前からの`cooledInputsAndAllManufacturingRecipesRemainValid`以外合格。`CreativeCatalogGameTests`は以前からの六フッ化ウランのバケツで失敗（その前のタブ所属の確認は通る）。
- 実画面（日本語）：`docs/quality/stainless-equipment/chromium-guide-ferrochrome.png`（製造ガイド「クロム」のフェロクロムの工程。超加熱のバーナー・鉢・ミキサーと、原料・製品の表示）。

## 制約と未確認事項

- `tools/generate_lidded_vessel.py`は変更前から全体の実行が失敗する（`generate_uranium_assets`から`rotor_model`を読み込めない）。今回はレシピなどを書く`data()`だけを実行した。全体を実行すると途中で`shell_connected.png`を書き換えてから止まるので注意。
- ステンレス鋼にしたのは、ふた付き槽・大型吸収塔・電解セル・電極ヘッドだけ。ほかに鉄板などで作る流体装置（例：水冷ジャケット管、ガスタンクコンテナなど）は調べたが変えていない。どれを「装置」として変えるかは未決定。
- セラミック管と、硫黄を空気で燃やすレシピは、撹拌槽がステンレス鋼より後になったため、序盤（ステンレス鋼より前）には使い道が小さくなった。
- 序盤（ステンレス鋼まで）にネザー（ブレイズケーキでの超加熱）とクロム鉄鉱・ペントランド鉱が必要。釣り合いはサバイバルで確認していない。
- 密閉鉢は構想の記録だけで、実装していない。
