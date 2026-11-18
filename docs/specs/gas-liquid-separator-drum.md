# 仕様：気液分離器を1×1の縦型ドラムにする

2026-11-17作成。実装は別の担当者が行う前提で書いています。反応塔は変更しません。

## 背景と決定

対象：気液分離器（ブロックID `ammonia_separator`、`block/AmmoniaSeparatorBlock.java`、`block/entity/AmmoniaSeparatorBlockEntity.java`）。

現状：Createの液体タンクの見た目替え（`PlantFluidTankBlock`）で、Createのタンクと同じように幅1～3・高さ2段以上につながります。処理量は幅×幅×高さのバッチを同時に処理します。

ユーザーの評価：3×3の気液分離器は違和感がある。そもそも大きすぎる。

決定（ユーザー選択）：**幅1に固定した縦型ドラム**にします。実機のノックアウトドラム（縦型の気液分離槽）に近い専用モデルで、胴・鏡板・ノズルを持ちます。処理量は段数で決まります。

本格運用前のため、旧データの移行処理は作りません（AGENTS.md）。

## 変更後の仕様

| 項目 | 仕様 |
| --- | --- |
| 大きさ | 1×1で2～6段。幅2以上にはつながらない。同じ列に縦に置くと1本のドラムになる。7段目以上は別のドラムになる（Createのタンクと同じ扱い） |
| 動作条件 | 2段以上で動く（今と同じ）。1段だけのときは今の「短すぎる」表示（`separator.create_refinery_n_reactors.short`）を出す |
| 処理量 | 1段につき1バッチを同時に処理（今の式 幅×幅×高さ に幅1を入れたもの）。60 tickごと（レシピどおり） |
| 容量 | 今と同じ：各在庫（原料・液体・気体・水・蒸気）が1ブロックあたり16,000 mB |
| ポート | 今と同じ。原料と冷却水・蒸気：最上段の側面（どの向きでも）。気体：最上段の上面。液体：最下段の下面。ほかの面はつながない |
| レシピ・冷却材の分離・保存・ガス放出 | 変えない（`GAS_LIQUID_SEPARATOR`の2レシピ、水と蒸気の通過、破壊時の`vent()`） |
| 正面 | 置いたときにプレイヤーの側だった面を最下段の制御ブロックに保存する（保存キー`Front`、反応塔と同じ考え方）。液面計を描く面 |

### 見た目

Createの部品と並べて違和感のない、ブロックの格子に合った形にします（DESIGN.mdの基本的なデザインルールに従う）。

| 部分 | 形 |
| --- | --- |
| 胴 | 対辺14 pxの正八角形の筒（ブロックの縁から1 px内側）。隣のドラムとは触れない。縦の継ぎ目と、各段の上下にリブ（1 px張り出す帯）を付ける |
| 上の鏡板（最上段） | 胴の上端から2段の段付きで縮む皿形（高さ合計3 px）。中央に気体出口のノズル（6×6 px、フランジ8×8 pxを上面の高さ16 pxにそろえ、Createの配管がつながる位置） |
| 下の鏡板（最下段） | 上と同じ形を下向き。中央に液体出口のノズル（下面の高さ0 pxまで） |
| 原料の入口 | 最上段の側面。配管をつないだ面だけにノズル（6×6 px）を出す必要はない。Createの配管が自分のフランジを描くので、胴の側面の高さ10～14 pxに入口の座板（8×8 px、厚さ1 px）を4面すべてに描く |
| 液面計 | 正面に縦のガラス管（幅2 px、各段の高さ1～15 px、上下に1 pxの金具）。描画はBERで、ドラム全体の液体（液体アンモニアや水）の量の割合だけ下から液体のテクスチャで満たす。中身がないときは空のガラス |
| 1段だけのとき | 上下とも鏡板の付いた短いドラム（動かない状態と分かる） |

- テクスチャ：今の`textures/block/tank/ammonia_separator*.png`（鉄灰色のリベット付き鋼板）の色をもとに、生成スクリプトで作る。Createの鋼板と同じ明るさの段階と、1 pxの縁取りを使う。
- `textures/block/tank/ammonia_separator.png`と`ammonia_separator_top.png`は`tools/generate_uranium_assets.py`の`block_textures()`が銀色の反応器テクスチャの元として読んでいる。この2枚は消さないか、読む側の元を差し替えてから消す。ほかの6枚（`_inner`、`_window`、`_window_single`、`_connected`、`_top_connected`、`_inner_connected`）は使わなくなるので削除する。
- 窓の切り替え（Createのタンクでレンチを使うと窓になる）は使わない。中は液面計で見る。

## 実装の変更点

### ブロックとブロックエンティティ

- `AmmoniaSeparatorBlockEntity`：`getMaxWidth()`を1、`getMaxLength(Axis.Y, width)`を6にする（`FractionatingColumnBlockEntity`、`GasHolderBlockEntity`と同じ上書きの仕方）。使う前に、解決済みのCreate 6.0.10のソースで`IMultiBlockEntityContainer`の`getMaxWidth`／`getMaxLength`と`FluidTankBlockEntity`の窓の扱いを確認する（AGENTS.md「作業を始める前に」）。
- `Front`の保存と読込を追加し、`setPlacedBy`で設定する（反応塔の`ReactionTowerBlock.setPlacedBy`と`ReactionTowerBlockEntity.face`を参考に）。つながりが変わったときは制御ブロックの値を使う。
- `AmmoniaSeparatorBlock`：窓の状態（Createの`FluidTankBlock.SHAPE`の窓）にならないようにする。レンチで窓を切り替えない。形状（当たり判定とアウトライン）は胴の八角形に近い箱（1～15 px）と鏡板の範囲にする。
- `mixin/GradeConnectivityMixin.java`の`AmmoniaSeparatorBlockEntity`の分岐は`getMaxLength`を使っているので、そのまま動くか確かめる（幅は1になる）。
- `PlantTankItem`（`CETransport`でアイテム登録）は今のまま使う。複数個をまとめて置く動作が幅2以上を作らないことを確認する。

### モデルと描画

- `tools/generate_ammonia_separator.py`を書き直す（モデルの正本）。Createのタンクのモデルを親にするのをやめ、`top`／`bottom`の組（単独・最下段・中間・最上段）ごとに専用モデルを生成し、ブロック状態をこの4つに対応させる。アイテムモデルは単独のドラム。
- `client/PlantTankModels.java`の`wrap(event, CETransport.AMMONIA_SEPARATOR.get(), SEPARATOR)`（Createのつながったテクスチャ）を削除する。
- 液面計のBERを`client/`に追加し、`ElectrolysisClient`で登録する。静的な部分はモデル、変わるのは液面だけ。描画は最下段の制御ブロックから1回だけ。液体の量が変わったときだけ同期する（今の`syncNeeded`の仕組みを使う）。

### Ponder

- `tools/generate_ammonia_separator_ponder.py`（`ponder/chemistry/ammonia_separator.nbt`の正本）を1×1・3段のドラムに作り直す。最上段の側面に原料の配管、上面に気体、下面に液体の配管。
- `ponder/AmmoniaSeparatorPonderScenes.java`：2×2×2用の制御ブロックの設定とセクションの範囲を1×1×3に合わせる。字幕（`tools/data/ponder_lessons.json`の`ammonia_separator.*`）を「1×1で2～6段に積む。1段につき1バッチ」に直す。液面計が見えることを1つの字幕で示す。
- `tools/generate_manufacturing_ponder.py`の`TEMPLATES['separator']`の装置・入口・出口の座標を新しい構造に合わせ、`separator`を使う章（アンモニア、採掘用爆薬など）を作り直す。工程の文（「2層以上の気液分離器で分けます」）は「1×1で2段以上の気液分離器」に直す。
- 熱交換・凝縮器などほかのPonderで分離器を置いていれば（`tools/generate_condenser_ponder.py`の部品一覧など）、置き方が1×1で成り立つか確認する。

### そのほか

- JEI：表示はそのまま。注記に段数あたりのバッチ数を書くなら翻訳で行う。
- クラフト（`tools/generate_crafting_recipes.py`の`ammonia_separator`、1個）：変えない。
- 文書：`docs/reaction-plant.md`の手順5と気液分離の記述、`docs/specs/equipment-roles.md`の「2. 気液分離」の表（処理量を「段数のバッチ」に）を更新する。完了レポートを`docs/reports/`に日本語で作る。

### テスト

- `gametest/HaberBoschGameTests.java`の`separator(...)`補助は1×1・2段を置くので、そのまま使える。
- 追加するGameTest：
  - 1×1で6段まで1本につながり、幅2以上にはならない（2×2に並べても1×1の列が4本）。7段目は別のドラム。
  - 2段のドラムで冷えた合成混合ガス18,000 mBが2バッチ同時に分離され、液体は下面、気体は上面から出る。1段では動かない。
  - 正面が置いた向きで保存され、再読込後も同じ。
- 既存のテストで幅2以上の分離器を前提にしているものがないか検索して直す（`AMMONIA_SEPARATOR`、`ammonia_separator`）。

## 確認

- 生成スクリプト（分離器、Ponder構造、製造ガイド、Ponder翻訳）を実行し、`validate_ponder_language.py`・`validate_plant_tutorials.py`・`validate_translations.py`が合格すること。`validate_visual_identity.py`は以前からの未分類8件で失敗する。
- `compileJava`（GameTestを含む）。
- GameTest：`HaberBoschGameTests`、`NitricPlantIntegrationGameTests`、`ManufacturingPonderGameTests`、`PlantTutorialGameTests`、`RecipeVisibilityGameTests`、`SurvivalReachabilityGameTests`、追加分。以前からの失敗：`cooledInputsAndAllManufacturingRecipesRemainValid`、`everyCurrentItemHasExactlyOneAppropriateTab`、`aFouledProcessStopsThenResumesAfterOneManualBrush`、`nonOccludingEquipmentSurvivesFlowingWater`。
- 実画面（日本語）：Ponder「気液分離器」と製造ガイド「アンモニア」で、ドラムの形、各段のつながり、上下のノズルと配管の位置、液面計の上下、Createの配管・タンクと並べたときの調和を確認する。

## 範囲外（今回はしない）

- 反応塔（変更しない）。
- レシピの量・時間、分離器の材料。
- 処理量の釣り合いの見直し（最大6段で1回に6バッチ。12段の反応塔1基の製品を処理するには2本必要。サバイバルで確認してから決める）。
