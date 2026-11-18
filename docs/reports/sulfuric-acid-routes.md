# 硫酸の製造経路を2つ追加した完了レポート

- 日付：2026-11-17
- 実装コミット：このレポートと同じコミット（`git log -1 -- docs/reports/sulfuric-acid-routes.md`で確認）
- 説明：[反応プラント](../reaction-plant.md#鉄触媒による二酸化硫黄の液相酸化)、[フィルタープレス型電解槽](../filter-press-electrolysis.md)

## 指示

1. 水に少量の鉄（酸化鉄粉や鉄ナゲット）を入れた状態で、SO₂と空気（酸素）を吹き込んで希硫酸にする。
2. 吸収塔で亜硫酸を作り、それを電気分解して硫酸を作る。

## 実装

| 経路 | 内容 |
| --- | --- |
| 鉄触媒の液相酸化 | 撹拌翼入りのふた付き槽（`pressure_reaction`、加熱不要、200 tick）。水1000＋二酸化硫黄250＋酸素125 mB＋鉄ナゲットか酸化鉄（赤）1個 → 希硫酸1000 mB。空気版は空気600 mBで、窒素475 mBも出る。鉄は硫酸鉄として溶けるので1バッチに1個使う |
| 亜硫酸の吸収 | 新しい流体`sulfurous_acid`（亜硫酸、腐食性の液体）。吸収塔の`absorb_sulfurous_acid`：二酸化硫黄250＋水250 mB → 亜硫酸250 mB |
| 亜硫酸の電解 | ハイブリッド硫黄法。電解セルで、極板1枚・1回あたり亜硫酸2 mB → 水素1 mB（陰極側の側面）＋硫酸1 mB（奥の端面）。陽極の気体はない。電力は1回20 J（水の電解の4割） |

- 電解の制御：陽極に気体がない電解（`ElectrolysisProcess.anodeGas()`）と、気体の電解で液体の製品を奥の端面へ出すこと（`recovered`）に対応させた。出口が満杯なら原料を消費しない（既存の`fits`に条件を追加）。
- 希硫酸の量は、既存の「硫酸250 mB＋水750 mB → 希硫酸1000 mB」に合わせた。

### 主要ファイル

- `registry/CEUranium.java`（亜硫酸）、`transport/FluidHazards.java`、`chemistry/ProcessRecipe.java`（吸収）、`chemistry/ElectrolysisProcess.java`、`block/entity/ElectrolysisControllerBlockEntity.java`
- JEI：電解セルの一覧に亜硫酸の電解（注記`note.electrolysis_sulfurous`）。撹拌反応と吸収塔は既存の一覧に自動で出る
- `tools/generate_sulfuric_routes.py`（新規：液相酸化の2レシピと亜硫酸の名前）、`tools/generate_electrolysis_cell.py`（JEIの注記）、`tools/generate_world_liquids.py`を実行して亜硫酸のバケツ・液体ブロック・翻訳を生成
- Ponder：製造Ponder「硫黄と回収ガスから硫酸を作る」の末尾に3手順（液相酸化・亜硫酸の吸収・亜硫酸の電解）を追加（`ManufacturingRecipe`に`sulfurous_electrolysis`）。吸収塔のPonderのレシピの字幕に亜硫酸を追加

## 確認

- `validate_ponder_language.py`・`validate_plant_tutorials.py`・`validate_translations.py`合格。`validate_visual_identity.py`は以前からの未分類8件のみ。
- GameTest：
  - 新規：酸素版（鉄なしでは反応しない、鉄を入れると希硫酸1000 mB）、空気・酸化鉄版（希硫酸1000 mB＋窒素475 mB）、吸収塔の亜硫酸（2段並列で500 mB）、亜硫酸の電解（100回で亜硫酸200 mB → 水素100 mB＋硫酸100 mB、陽極ガスなし、水素は陰極側）。
  - `ReactorGameTests`19件すべて合格。反応・吸収塔・電解セル・塩素アルカリ・金属回収・クラフト・クリエイティブ一覧・製造Ponder・チュートリアル・レシピ表示・サバイバル到達性・反応塔の12クラスは、以前からの2件（`cooledInputsAndAllManufacturingRecipesRemainValid`、六フッ化ウランのバケツ）以外合格（撹拌槽の2件はテストの確認の誤りを直して再実行し合格）。
- 実画面（Xvfb、日本語）：製造Ponderの硫酸の章を最後まで進め、電解セルの組み立てが表示されることを確認した。追加した各手順の字幕の見た目は個別には撮影していない。

## 制約・次回の注意

- 鉄は触媒だが、溶けて酸に残る想定で毎回消費する。
- 電解セルは電力を内部の蓄えから払うので、亜硫酸の電解でも端子からの受電は通常と同じ1枚1 kWまでで、使った分だけ減る。
- 製造Ponderは章を指定して生成すること（`python3 generate_manufacturing_ponder.py manufacturing_sulfuric_acid`）。
