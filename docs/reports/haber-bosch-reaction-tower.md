# ハーバーボッシュ反応塔を復活させた完了レポート

- 日付：2026-11-17
- 実装コミット：このレポートと同じコミット（`git log -1 -- docs/reports/haber-bosch-reaction-tower.md`で確認）
- 説明：[反応プラント](../reaction-plant.md#ハーバーボッシュ反応塔)
- 元にした版：`4c406b7^`（反応塔を削除する直前）

> **追記（同日）**：続く指示で、中心に山を詰める形をやめ、厚さ12 pxの壁と幅24 pxの縦穴を持つ3×3のタンクに触媒バスケットをはめる形に作り直した。ほかの触媒反応にも使える。現行の仕様は[反応塔を触媒バスケット式にした完了レポート](reaction-tower-baskets.md)。

## 指示

中心に四酸化三鉄の山を詰めて使う専用の反応塔を復活させる。ユーザー確認：鉄触媒バスケットの槽は残す（両方残す）、処理は今の固定レシピ式、見た目は昔のモデルとテクスチャに戻す。

## 実装

| 部分 | 内容 |
| --- | --- |
| 構造 | `HaberBoschStructure`（旧版のまま、翻訳キーの名前空間だけ変更）。3×3の底面・天面と8個の輪を3～12段、中心に山。全高5～14 |
| 処理 | `ReactionTowerBlockEntity`を新しく書いた。底面中央が持ち主。`ProcessRecipe`の`synthesize_ammonia`（100 tick、10,000 mB → 9,000 mB、HEATED）を、新しい山1層につき1バッチ同時に実行する。旧版のモル平衡・反応熱・冷却ジャケット・過熱溶融・安全弁は戻していない |
| 入出力 | 底面中央の下面＝原料、天面中央の上面＝製品。旧版のジャケット用ポートは廃止 |
| 在庫 | 原料・製品の槽は山1層あたり体積1,000 mB、高圧（64倍）。成立時に層数で容量を変える |
| 触媒 | 1層4,000バッチ（`CEItems.CATALYST_BATCHES`）。下の新しい層から`spent_triiron_tetroxide_pile`へ置き換える。再生は使い切った山＋アルミナ（作業台） |
| 停止理由 | `unformed`・`no_feed`・`heat`・`output`・`spent`・`running`・`idle` |
| 設置 | `PlantTankItem`の3×3の層・輪のまとめ置きを旧版から戻した（対象は反応塔だけ） |
| 外観 | 旧テクスチャ8枚・Createのタンクモデルを親にしたモデル・CT（`PlantTankModels`）・接続用Mixin（`ReactionTowerConnectionMixin`）を旧版から戻した |

### 主要ファイル

- `block/ReactionTowerBlock.java`、`block/HaberBoschStructure.java`、`block/entity/ReactionTowerBlockEntity.java`、`mixin/ReactionTowerConnectionMixin.java`
- `registry/CETransport.java`、`registry/CEBlocks.java`、`registry/CEItems.java`、`item/PlantTankItem.java`、`client/PlantTankModels.java`
- JEI：「アンモニア合成」に反応塔と四酸化三鉄の山を触媒として追加し、反応塔の行（`note.haber_bosch_tower`）を加えた
- Ponder：`ponder/HaberBoschPonderScenes.java`（`plant/haber_bosch_tower`）、字幕は`tools/data/ponder_lessons.json`
- 生成：`tools/generate_reaction_tower.py`（新規。モデル・ブロック状態・ルート・タグ・翻訳・Ponder構造）、`tools/generate_catalyst_wear.py`（使い切った山と再生レシピを旧版から戻した）、`tools/generate_crafting_recipes.py`（反応塔：ステンレス板4・かまど2・ガラス板2・モリブデン1）
- 旧版の圧力・平衡モデルの残りだった翻訳（`status.reacting`・`status.equilibrium`・`composition`・`overheat`・`heat`）と、使われていない`note.reactor_pressure`を削除

## 確認

- `compileJava`成功。`validate_ponder_language.py`・`validate_plant_tutorials.py`・`validate_translations.py`・`validate_machine_sounds.py`合格。`validate_visual_identity.py`は以前からの未分類8件で失敗したまま（反応塔は分類済み）。
- 新規GameTest（`HaberBoschGameTests`）：成立と中心だけのポート・窓なし・シミュレーション無変更・壁を壊すと停止／熱がないと反応しない・バッチ完了時だけ確定・3層で3バッチ・端数は待つ／出口満杯で原料を使わない／最下層から4,000バッチで使い切り・保存再読込・全層切れで停止・山を戻すと再開／輪のまとめ置き。
- 最初のプッシュ（`40958b1`）の後に見つけて直した不具合：
  - 空の気体槽は液体の体積（3層で3,000 mB）を容量として返すため、製品の出口が常に満杯と判定され、塔が動かなかった。出口の空きは模擬充填で数えるようにした。
  - サバイバル到達性テストが使い切った山を到達不能と判定したため、反応塔の経路を追加した。
  - レシピ表示テストを、JEIの触媒に四酸化三鉄の山がある行も認めるようにした。
  - テストのバーナーは燃料なしで消えるため、処理の直前に置くようにした。
- GameTest（修正後）：`HaberBoschGameTests`・`RecipeVisibilityGameTests`・`SurvivalReachabilityGameTests`の12件は、以前からの`cooledInputsAndAllManufacturingRecipesRemainValid`以外合格。修正前の実行では`CatalystBasketGameTests`・`CraftingRecipeGameTests`・`CreativeCatalogGameTests`・`ManufacturingPonderGameTests`・`PlantTutorialGameTests`も、以前からの六フッ化ウランのバケツの1件以外合格（修正はこれらの対象外）。
- 実画面（Xvfb、日本語）：Ponder「ハーバーボッシュ反応塔」を撮影し、旧来の暗い鋼板の外殻とつながったテクスチャ、四隅のバーナー、上下の配管を確認した。

## 制約・次回の注意

- 旧ワールドの反応塔は以前に削除済みのため、移行処理はない。
- 原料の流体は`COMPRESSED_SYNTHESIS_FEED`だけ受け付ける。
- 高圧気体の容量（64倍）のため、出口満杯までには約7サイクル分の製品がたまる。
