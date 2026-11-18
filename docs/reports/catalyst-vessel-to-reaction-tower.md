# 触媒反応を反応塔へ移し、ハッチを22 pxにした完了レポート

- 日付：2026-11-17
- 実装コミット：このレポートと同じコミット（`git log -1 -- docs/reports/catalyst-vessel-to-reaction-tower.md`で確認）
- 説明：[反応プラント](../reaction-plant.md#反応塔)、[役割の整理](../specs/equipment-roles.md)
- 前の作業：[反応塔を触媒バスケット式にした完了レポート](reaction-tower-baskets.md)

## 指示

1. ふた付き槽＋触媒バスケット（触媒反応器）でできることをすべて反応塔へ移し、槽の触媒バスケットの使い方を削除する。
2. 反応塔は最下段の側面から原料を入れ、最下段の底面の真ん中か最下段の側面から取り出す。
3. 反応塔の上部は穴ではなくハッチにし、上面のテクスチャも通常のもの（描かれたノズルなし）に戻す。
4. ふた付き槽のハッチを16×16 pxから22×22 pxにし、反応塔も同じハッチにする。

## 実装

| 部分 | 内容 |
| --- | --- |
| 槽の触媒モード削除 | `ReactorModule.VESSEL`、`CatalystBasketProcess`、槽の触媒スロット・触媒床・触媒の手入れ操作・ゴーグル表示・保存（`Catalyst`、`CatalystBed`）、表示スロット9（触媒床の気体）と13（触媒の個数）を削除。触媒バスケットのアイテム（`reactor_catalyst_basket`）は反応塔の部品として残し、`CEReactor.CATALYST_BASKET`で登録する。`CEReactor.INTERNALS`は撹拌翼と反応棚段だけ |
| 反応塔のレシピ | `REACTION_VESSEL`と`CATALYST_UNIT`の全レシピ。触媒の要らない反応（一酸化窒素の酸化）は、どの触媒を入れていてもバスケットのある段で行う（触媒の摩耗なし）。触媒反応が優先ではなく、原料で決まる |
| ポート | 最下段の外側面：原料の搬入と製品の搬出（同じハンドラー）。最下段の中央ブロックの下面：製品の搬出だけ。最上段の外側面：気液分離の気体の搬出だけ。中間の段・ほかの底面・天面はつながない |
| ハッチ（反応塔） | 天板の中央に22 px角の開口（塔の座標で13～35 px）。素手で天板を右クリックすると開閉する（`toggleHatch`）。開いている間は運転しない（状態`hatch`）。開けるとき中の気体を`ToxicGas.release`で放出する。バスケットと触媒の手での出し入れ・レンチでの取り出しは開いている間だけ。ファンネルでの触媒の搬入・使い切った触媒の搬出はハッチの開閉に関係なく使える。保存キー`HatchOpen` |
| ハッチの描画 | `models/block/reaction_tower/hatch.json`（22 px角・厚さ2 pxの板。上面は`reaction_tower_top`）を`ReactionTowerRenderer`が北の縁を軸に最大105°回して描く。開き具合は`LerpedFloat`でクライアントが補間する |
| 天面テクスチャ | `reaction_tower_top_connected.png`の中央に描かれていたノズルを周りの鋼板で塗りつぶした |
| ふた付き槽のハッチ | `LiddedVesselBlock.HATCH_HALF = 11`（中心から±11 px＝22 px）。3×3は中央の22 px、2×2は4ブロックの内側の角に11 pxずつ。当たり判定（`Hatch.box()`）と描画（`LiddedVesselRenderer`）を同じ値で決める。幅3の板は`capAcross`でブロックの境界ごとに分けて描く |
| Ponder | 反応塔：ハッチを開けてバスケットと触媒を入れて閉じる、原料は西の最下段、製品は東の最下段、劣化の後にハッチを開けてレンチ。製造ガイド：触媒反応の12工程を新しい種類`catalytic`（`plant/reaction_tower`の構造）で描く。撹拌槽の種類`vessel`の土台は`tools/data/manufacturing/process_vessel.nbt`（撹拌翼入り）へ移した。触媒バスケット専用のPonder（`plant/catalyst_basket`）を削除 |
| JEI | 「反応槽」「アンモニア合成」の装置を反応塔＋触媒バスケットにした。注記`note.reactor`を「反応塔でバスケットのある段ごとに1バッチ」に変更 |
| サバイバル到達性 | `REACTION_VESSEL`・`CATALYST_UNIT`の装置を「反応塔＋触媒バスケット」にした |

### 主要ファイル

- `block/ReactionTowerBlock.java`、`block/entity/ReactionTowerBlockEntity.java`、`client/ReactionTowerRenderer.java`
- `block/LiddedVesselBlock.java`、`client/LiddedVesselRenderer.java`
- `block/entity/ReactorMachineBlockEntity.java`、`block/entity/LiddedVesselBlockEntity.java`、`block/ReactorMachineBlock.java`、`chemistry/ReactorModule.java`、`registry/CEReactor.java`
- `ponder/ReactionTowerPonderScenes.java`、`ponder/ManufacturingPonderScenes.java`、`ponder/ElectrolysisPonderPlugin.java`、`compat/jei/CreatePlantJeiPlugin.java`、`compat/display/MachineReadings.java`
- 生成元：`tools/generate_reaction_tower.py`（天板の開口とハッチのモデル）、`tools/generate_manufacturing_ponder.py`（`catalytic`）、`tools/generate_vessel_consolidation_ponder.py`（撹拌槽の土台、触媒バスケットの字幕と古い翻訳の削除）、`tools/generate_lidded_vessel_ponder.py`、`tools/generate_catalyst_wear.py`、`tools/data/ponder_lessons.json`
- 削除：`CatalystBasketProcess.java`、`CatalystBasketPonderScenes.java`、`CatalystBasketGameTests.java`、`ponder/plant/catalyst_basket.nbt`（撹拌槽の土台として`tools/data/manufacturing/process_vessel.nbt`へ移動）、`catalyst_basket.*`・`vessel_internals.basket`の字幕、使われなくなった翻訳（`reactor.*.catalyst`、`catalyst_life`、`none`、`status.catalyst`、`status.core`、`status.equilibrium`、`lidded_vessel.*.catalytic_operation`、`display.*.fluid_9`・`temperature_9`・`item_13`、`vessel_overpressure`）

## 確認

- コンパイル：`compileJava`成功（GameTestを含む）。
- 検証スクリプト：`validate_ponder_language.py`・`validate_plant_tutorials.py`・`validate_translations.py`合格。`validate_visual_identity.py`は以前からの未分類8件で失敗したまま。
- GameTest（関係する13クラス、75件）：以前からの`cooledInputsAndAllManufacturingRecipesRemainValid`以外合格。内訳の主なもの：
  - `ReactionTowerGameTests`：ポートの位置（最下段の側面・底面中央・最上段の側面だけ）、最上段と底面中央からは原料が入らない、別の最下段の側面から製品を取り出す、ハッチ（閉じたハッチはバスケットを受け付けない、素手で開く、開いている間は運転しない、開けると気体を放出、触媒なしのバスケットで一酸化窒素を酸化）。
  - `ReactorGameTests.everyFixedProcessRunsFromItsRealFeeds`：`REACTION_VESSEL`・`CATALYST_UNIT`の全レシピが3段の反応塔で1バッチずつ実際の原料から製品になる。
  - `NitricPlantIntegrationGameTests`：アンモニア合成（4段の塔で4バッチ）からオストワルト法・一酸化窒素の酸化（同じ塔）・吸収までの物質収支。
  - 槽の駆動・保存・満杯・赤石・温度のテストは、触媒バスケットの代わりに撹拌槽（水の予熱レシピ）で確認するよう書き直した。撹拌槽は熱を失うと未払いのバッチの進捗が0に戻る（原料は消費しない）ので、そのとおりに確認する。
- `ConcurrentChemistryGameTests`（明示実行の4ライン同時運転、6,000 tick）：反応塔3基を含めて合格（硝酸800・硫酸2,000・塩酸4,000 mB）。
- 全GameTest（574件）：4件失敗。`linkedVesselDetailsAndSimulation…`は削除した触媒の表示スロット（`item_13`）を確かめていたため、その1行を削除して再実行し合格。残る3件（`aFouledProcessStopsThenResumesAfterOneManualBrush`、`nonOccludingEquipmentSurvivesFlowingWater`、六フッ化ウランのバケツ）は、変更前のHEADでも同じく失敗することを別の作業ツリーで確認した（今回の変更とは無関係）。
- 実画面（Xvfb、日本語）：`docs/quality/catalyst-to-reaction-tower/`
  - `tower-hatch-open.png`：反応塔のPonderでハッチが開き、バスケットを入れているところ。
  - `tower-hatch-closed-bottom-ports.png`：閉じたハッチ（22 pxの板）と最下段の両側面の配管。
  - `vessel-3x3-hatch-22px.png`、`vessel-2x2-hatch-22px.png`：ふた付き槽の22 pxのハッチ（開いた状態）。
  - `manufacturing-sulfuric-acid-tower.png`：製造ガイド「硫酸」の三酸化硫黄の工程が反応塔で描かれる。

## 制約と未確認事項

- 序盤（鉄板の段階）には触媒反応の手段がなくなった。序盤の希硫酸は鉄触媒の液相酸化（撹拌槽）で作れる。釣り合いはサバイバルで確認していない（`equipment-roles.md`に課題として記載）。
- 既存ワールドで槽の触媒スロットに入っていた触媒は、保存キーを読まなくなったため消える（本格運用前のため移行処理は作らない方針）。槽に内部部品として入っていたバスケットが上面レンチで回収できるかは確認していない。
- 反応塔のハッチを開けたままでもファンネルで触媒を出し入れできる。ハッチの上にファンネルを置くと、開いたハッチの板と重なって見える。
- ハッチの開閉の動き（補間）は実画面で途中の角度を確認していない（開いた状態と閉じた状態だけ）。
- 範囲外で見つけたもの：`gui.create_refinery_n_reactors.catalyst_note`と`gui.create_refinery_n_reactors.status.no_catalyst`は以前からどこからも使われていない（今回は削除していない）。

## 次回の注意

- 反応塔の製品ポートは最下段の側面と底面中央。気体の製品も最下段から出る（バスケットがある間）。気液分離の気体だけが最上段から出る。
- 製造ガイドの`vessel`の土台を変えるときは`tools/generate_vessel_consolidation_ponder.py`で`process_vessel.nbt`を作り直してから`tools/generate_manufacturing_ponder.py`を実行する。
- `tools/generate_lidded_vessel_ponder.py`の`RUNTIME_TEXTS`には以前、ほかで退役した翻訳が残っていた（今回4件削除）。実行後は`validate_translations.py`で余分な鍵がないか確かめる。
