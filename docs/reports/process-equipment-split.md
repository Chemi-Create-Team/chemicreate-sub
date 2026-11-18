# ふた付き槽の役割を3つの装置へ分けた完了レポート

> **取り下げ**：この実装はユーザーの指示で巻き戻しました（撹拌機・触媒反応器は削除、吸収塔のみ2つの大きさで存続）。現行は[吸収塔の完了レポート](absorption-tower-sizes.md)を参照してください。

- 日付：2026-11-16
- 実装コミット：`d1f7ba64f216a5631bf6d4f59267d3a50fb6bb5c`（`4088ef0`の上にリベース）
- 設計：[設計：ふた付き槽の役割を専用装置へ分ける](../specs/process-equipment-split.md)
- 説明：[反応プラント](../reaction-plant.md)
- モデリング依頼：[モデリング指示書：撹拌機・触媒反応器・吸収塔](../specs/modeling-brief-process-equipment.md)

## 実装

ふた付き槽に内部部品（撹拌翼・触媒充填バスケット・反応／吸収棚段）を入れて役割を変える仕組みをやめ、見た目と名前の違う3つの装置にした。

| 装置 | 組み立て | 回転・熱 | レシピ |
| --- | --- | --- | --- |
| 撹拌反応槽 | 3×3のふた付き槽1～4段＋最上段中央の撹拌機（`agitator_drive`） | 撹拌機へ32 RPM以上（上のシャフトか横の小歯車）。応力16 SU/RPM×段数を撹拌機が負う。熱は下の3×3 | `pressure_reaction`すべて（ウランの気固反応4件を含む）とCreateのミキサー |
| 触媒反応器 | `catalytic_reactor`の胴（3×3八角形）2～4段 | 回転不要。64 RPM相当の一定速度。熱は最下段の下の3×3 | `REACTION_VESSEL`10件とアンモニア合成 |
| 吸収塔 | `absorption_tower`の段（1×1）4～8段 | 回転・熱不要（32 RPM相当） | 硝酸・硫酸・塩酸の吸収（`ProcessRecipe`の`ABSORPTION_TRAYS`） |

どれも1段につき1バッチを並列に処理する。撹拌機を外すと槽は貯蔵槽に戻り、中身はそのまま残る。

### 主要ファイル

- `block/AgitatorDriveBlock.java`、`block/entity/AgitatorDriveBlockEntity.java`：撹拌機。ICogWheelのKineticBlockで、上面だけがシャフトを受ける。設置・撤去で下の槽へ構造変更を通知する。
- `block/entity/LiddedVesselBlockEntity.java`、`block/LiddedVesselBlock.java`、`block/ReactorStructure.java`：内部部品の保持・装荷・回収、底面の動力選択（`powerInput`）と`DRIVE`を削除。`module()`は「幅3かつ撹拌機あり」なら`STIRRED`。撹拌機の有無はサーバーで構造変更までキャッシュする。3×3の上下面の連結表示に使っていた`DRIVE`は`WIDE`（`wide`）として残した。撹拌機があるとハッチは開かない（閉じるのは常に可）。
- `block/entity/StackedReactorBlockEntity.java`：同じ段を縦に積む装置の共通部。最下段が処理と在庫を持ち、上の段は面ごとのポートだけを渡す。上の段に在庫があると形成しない。
- `block/CatalyticReactorBlock.java`、`block/entity/CatalyticReactorBlockEntity.java`、`block/entity/AmmoniaSynthesisBed.java`（旧`CatalystBasketProcess`）：触媒反応器と鉄触媒床。
- `block/CatalyticReactorRingBlock.java`、`block/entity/ColumnRingBlockEntity.java`：八角形の外周。Createの配管はブロックエンティティのある位置にしか接続しないため、外周に受け渡し専用の空のブロックエンティティを置いた（tickしない）。
- `block/AbsorptionTowerBlock.java`、`block/entity/AbsorptionTowerBlockEntity.java`：吸収塔。
- `item/ColumnStoreyItem.java`：八角形の胴の天面をクリックすると、次の胴を中心の真上に置く。
- `block/entity/ReactorMachineBlockEntity.java`：構造判定・熱源範囲・連続処理を差し替え可能にした（`scanStructure`、`heatFloor`、`tickContinuous`など）。`Port`の開始位置付き入力で、レシピの入力番号と書き込み先がずれていた不具合を直した（吸収塔の上下ポートで必要）。
- `recipe/PressureReactionRecipe.java`：`module`欄を削除。JSONの`"module": "absorber"`も削除した。
- `client/AgitatorDriveRenderer.java`、`client/LiddedVesselRenderer.java`：撹拌機の歯車と軸（Flywheel時は回転Visual）、撹拌機の下の各段の撹拌翼。
- `ponder/ProcessEquipmentPonderScenes.java`：3装置の専用Ponder。ふた付き槽・槽の搬送・製造工程のPonderも撹拌機の構成に更新した。
- `compat/jei/CreatePlantJeiPlugin.java`：カテゴリを「撹拌反応槽」「触媒反応器」「アンモニア合成」「吸収塔」に整理し、「加熱棚段反応」を廃止。
- `tools/generate_process_equipment.py`（新規）：3装置の仮モデル・仮テクスチャ・ブロック状態・ルートテーブル・タグ・翻訳・Ponder構造。レシピは`tools/generate_crafting_recipes.py`の基本段（作業台、精錬金属なし）に追加した。硫酸が序盤に吸収塔を必要とするため。

### 削除したもの

`reactor_catalyst_basket`と`reactor_separator_core`の登録・モデル・テクスチャ4枚・レシピ・翻訳、`catalyst_basket`・`vessel_internals`・`absorption_trays`のPonderと生成元、`pressure_reaction/sulfuric_acid`・`hydrochloric_acid`のJSON。撹拌翼（`reactor_impeller`）は撹拌機の材料として残した。

## 計算方法

- 撹拌反応槽：進捗＝`min(4, RPM/32)`／tick（従来どおり）。並列バッチ＝段数。応力＝16 SU/RPM×段数。
- 触媒反応器：回転数を64として同じ式で進む（2倍）。並列バッチ＝段数。触媒は1バッチごとに1劣化。アンモニア合成は従来の固定バッチ（10,000 mB→9,000 mB、100 tick、容量32,000 mB×段数）。
- 吸収塔：回転数32相当（1倍）。並列バッチ＝段数。気体は0番、水は1番の入力槽、酸は2番、排ガスは3番の出力槽。

## 確認

- `build`成功。`validate_ponder_language.py`と`validate_plant_tutorials.py`合格。
- GameTest：関係する31クラスを一括実行し、`RecipeVisibilityGameTests.cooledInputsAndAllManufacturingRecipesRemainValid`の1件以外すべて合格。この1件は今回の変更前から失敗している既知の監査（[金属回収のレポート](metal-recovery.md)に記載）。リベース後に主要16クラス87件を再実行し、すべて合格。
- 新規・更新した主なテスト：
  - 撹拌機：上のモーターと横の小歯車で回る。槽の下や横のモーターでは回らない。応力16 SU/RPM×段数。撹拌機を外すと貯蔵に戻り中身が残る。撹拌機の上ではハッチが開かない。2×2は撹拌機を載せても反応しない。
  - 触媒反応器：1段・5段では形成しない。天面（外周経由を含む）だけが原料を受け、最下段の側面だけが製品を出す。天面のファンネルで触媒が入る。回転なしで酸化が50 tickで終わる。Createのポンプが外周の天面から加圧合成原料を送れる。製品満杯で原料と触媒を消費しない。保存・再読込で二重処理しない。
  - 吸収塔：3段では形成しない。水は上だけ、気体は下だけから入る。中段と底面にポートがない。硝酸・硫酸・塩酸の量。酸の出口が満杯なら次の原料を消費しない。
  - 既存の硝酸プラント統合・4工程同時運転・ウラン燃料・水処理・汚れ清掃・製造レシピ・サバイバル到達性を新しい装置で通した。
- 実画面（Xvfb、日本語）：Ponder「撹拌反応槽」「触媒反応器」「吸収塔」と、撹拌機の付いたふた付き槽の場面を撮影し、仮モデルが描画され、撹拌機の上下にシャフトとモーターがつながることを確認した。

## 制約と未確認事項

- モデルとテクスチャは仮のもの。仕上げはFableへの[モデリング指示書](../specs/modeling-brief-process-equipment.md)で依頼する。
- 触媒反応器の一定速度（2倍）と吸収塔の段数は目安。サバイバルでの釣り合いは未確認。
- 旧ワールドの扱い：本格運用前のため互換処理は作っていない。内部部品を入れた槽を読み込むと、部品は失われ、槽は貯蔵槽として読み込まれる（クラッシュしないことはテストの保存・再読込経路で確認したが、実際の旧ワールドでは未確認）。
- 製造Ponderの生成時、反応装置を含まない章（空気、水素、触媒、液体ヘリウム、鋼）は上流の型の変化で構造が変わったため、元に戻して今回の差分に含めていない。反応装置を含む章は現在の型で再生成した。
- 金属回収ラインの簡素化（沈降槽・晶析槽の削除）は[方針メモ](../specs/metal-recovery-simplify.md)のとおり未着手。
- 性能の比較計測はしていない。撹拌機の有無の判定はキャッシュし、構造探索は変更通知時だけ行う。

## 次回の注意

- `tools/generate_lidded_vessel_ponder.py`などの古い生成元は、実行すると古い翻訳を書き戻す。構造だけを再生成し、翻訳は`tools/data/ponder_lessons.json`と`generate_ponder_language.py`で更新すること。
- `GameTestHelper.relativePos`は`absolutePos`の逆にならない場面がある。撹拌機の位置のような絶対座標は、そのまま`level`で扱うこと。
- 八角形の外周をほかの装置で使う場合も、Createの配管をつなぐならブロックエンティティが必要。
