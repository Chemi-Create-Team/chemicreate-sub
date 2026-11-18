# 撹拌反応槽・触媒反応器を元に戻し、吸収塔を2つの大きさにした完了レポート

- 日付：2026-11-16
- 実装コミット：このレポートと同じコミット（`git log -1 -- docs/reports/absorption-tower-sizes.md`で確認）
- 説明：[反応プラント](../reaction-plant.md#吸収塔)
- 取り下げた設計：[設計：ふた付き槽の役割を専用装置へ分ける](../specs/process-equipment-split.md)

## 指示

1. 吸収塔は「1ブロックの中心を中心とした半径1.5の八角形」と「3×3の普通の八角形」の2つの大きさを使えるようにする。前者は1×1ブロックで見た目だけ3幅（ユーザー確認済み）。
2. 色を銀色にし、ふた付き槽のような質感にする。
3. 撹拌反応槽と触媒反応器は前の仕様（ふた付き槽＋撹拌翼・触媒充填バスケット、底面中央のシャフト）に戻す。棚段の内部部品も戻し、加熱した気固反応はそこで行う。撹拌機ブロックと八角形の触媒反応器は削除する（いずれもユーザー確認済み）。

## 実装

`d1f7ba6`（3装置への分割）を巻き戻し、その上に吸収塔だけを載せ直した。

| 装置 | 状態 |
| --- | --- |
| 撹拌反応槽・触媒反応 | 分割前の仕様（3×3のふた付き槽、各段に撹拌翼か触媒充填バスケット、底面中央へ32 RPM以上）に戻した |
| 反応棚段（旧「反応・吸収棚段」） | 分割前の内部部品に戻したが、役割は加熱した気固反応（`module: absorber`のウラン工程）だけにした。`ReactorModule.ABSORBER`の`process`を`null`にし、硝酸の吸収はもう行わない |
| 吸収塔（小） | `absorption_tower`。1ブロックの段を4～8段積む。モデルは段の中心から外接半径1.5ブロック（24 px）の正八角形で、周囲のブロックへ張り出す。当たり判定と接続先は中心の1ブロック |
| 吸収塔（大） | `large_absorption_tower`。置くと3×3の八角形が外周（`large_absorption_tower_ring`）ごと置かれる。段の天面をクリックすると次の段が中心の真上に積まれる（`ColumnStoreyItem`）。外周は受け渡し専用のブロックエンティティを持ち、どのブロックにもCreateの配管をつなげる |

- 2つの大きさは同じブロックエンティティ（`AbsorptionTowerBlockEntity`、`ReactorModule.TOWER`）を使い、処理量とポートの位置は同じ。大きさの違う段は1本の塔にならない。
- 吸収のレシピは`ProcessRecipe`の`ABSORPTION_TRAYS`3件（硝酸・硫酸・塩酸）。硫酸と塩酸は撹拌反応の`pressure_reaction`から移した（分割時の決定を維持）。
- 外殻は、ふた付き槽と同じ銀色の板`block/reactor/silver/shell`をそのまま使う。天面はふた付き槽の天板と同じ配色・模様で、八角形の縁取りと中央のマンホールを描いた48 pxのテクスチャを大小別に生成した。

### 主要ファイル

- `block/AbsorptionTowerBlock.java`、`block/LargeAbsorptionTowerBlock.java`、`block/LargeAbsorptionTowerRingBlock.java`
- `block/entity/AbsorptionTowerBlockEntity.java`、`block/entity/StackedReactorBlockEntity.java`（縦に積む段の共通部。最下段が処理と在庫を持つ）、`block/entity/ColumnRingBlockEntity.java`
- `item/ColumnStoreyItem.java`、`block/ExtractionColumnSectionBlock.java`（外周ブロックを差し替える`ringCell`）
- `block/entity/ReactorMachineBlockEntity.java`：`scanStructure`と`maxSections`を差し替え可能にした。`Port`の開始位置付き入力で、レシピの入力番号と書き込み先がずれていた不具合を直した（吸収塔の上下ポートで必要）。
- `chemistry/ReactorModule.java`、`chemistry/ProcessRecipe.java`、`registry/CEReactor.java`、`compat/jei/CreatePlantJeiPlugin.java`（「硝酸吸収（ふた付き槽＋吸収棚段）」を「吸収塔」カテゴリに置き換え）
- `ponder/AbsorptionTowerPonderScenes.java`：小型と大型の2場面。製造Ponderの硝酸・硫酸・塩酸の吸収工程も吸収塔に変えた（`ManufacturingPonderScenes.tower`）。
- `tools/generate_absorption_tower.py`（新規）：モデル・天面テクスチャ・ブロック状態・ルートテーブル・タグ・翻訳・Ponder構造。レシピは`tools/generate_crafting_recipes.py`の基本段に追加した。

### 削除したもの

撹拌機（`agitator_drive`）、八角形の触媒反応器（`catalytic_reactor`とその外周）と、それらのモデル・テクスチャ・レシピ・Ponder・テスト・モデリング指示書。ふた付き槽で硝酸を吸収していた旧Ponder「吸収棚段で硝酸を吸収する」（`absorption_trays`）とその生成元、JEIの「硝酸吸収」カテゴリ、撹拌反応の`pressure_reaction/sulfuric_acid`・`hydrochloric_acid`。

## 計算方法

- 進捗は32 RPM相当の一定速度（`min(4, 32/32)` = 1／tick）。1段につき1バッチを並列に処理する。応力は0。
- 気体は0番、水は1番の入力槽、酸は2番、排ガスは3番の出力槽。最下段の側面が0番と2番、最上段の側面と天面が1番と3番を公開する。中間の段と底面は何も公開しない。
- レシピ：二酸化窒素300＋水100 → 硝酸200＋一酸化窒素100、三酸化硫黄250＋水250 → 硫酸250、塩化水素500＋水500 → 塩酸1,000（mB、各100 tick）。
- クラフト：小型は鉄板6・ガラス2・鉄格子1で2個、大型は鉄板6・ガラス2で1個（作業台、精錬金属なし。硫酸が序盤に吸収塔を必要とするため）。

## 確認

- `build`成功。`validate_ponder_language.py`、`validate_plant_tutorials.py`、`validate_translations.py`合格。`validate_visual_identity.py`は今回追加した大型吸収塔を分類したが、以前からの未分類（`iron_pipe`、`jacketed_pipe`など8件）で失敗したまま。
- GameTest（2回、計144件）：
  - 吸収塔・硝酸・硝酸プラント統合・反応・クラフト・サバイバル到達性・Ponder一覧・構造ガイド・ウラン燃料・レシピ表示・製造Ponder・説明書など14クラス76件：`RecipeVisibilityGameTests.cooledInputsAndAllManufacturingRecipesRemainValid`の1件以外合格（以前からの既知の失敗）。
  - 4工程同時運転・塩素アルカリ・触媒バスケット・底面駆動の槽・ハーバーボッシュ・槽の搬送・汚れ・水処理・反応温度など17クラス68件：`VesselFoulingGameTests.aFouledProcessStopsThenResumesAfterOneManualBrush`の1件以外合格。この1件は分割前のコミット（`d1f7ba6^`）でも同じく失敗することを別の作業ツリーで確認した。
- 上流（`5f5e9e1`、電熱線ヒーター・沈降槽・晶析槽の撤去）へリベースした後、関係する33クラス134件を再実行：上の2件と`CreativeCatalogGameTests.everyCurrentItemHasExactlyOneAppropriateTab`（六フッ化ウランのバケツ）の計3件以外合格。3件目はリベース前の上流`main`単独でも失敗することを確認した。リベースでは上流の金属回収の簡素化（浸出槽から精製溶液と鉱滓を直接回収）を残し、撹拌機を前提にした部分（説明書・Ponderの文言・サバイバル到達性・製造Ponderのフッ素章）だけを撹拌翼入りの槽に直した。
- 新規テスト（`AbsorptionTowerGameTests`）：3段・9段では形成しない／大きさの違う段は1本にならない／応力0／水は上、気体は下からだけ入り、中段と底面にポートがない／硝酸・硫酸・塩酸の量／酸の出口が満杯なら次の原料を消費しない／大型は外周から配管でき、同じ量を作る／大型の段の角をクリックすると次の段が中心の真上に外周ごと置かれる。
- 実画面（Xvfb、日本語）：Ponder「吸収塔」「大型吸収塔」を撮影し、銀色の八角形の外殻、天面、配管の接続位置を確認した。大型の天面の角に暗い欠けが出たため、天面テクスチャの八角形の外側を板の色に直した（直した後の実画面は未撮影、テクスチャ画像で確認）。

## 制約と未確認事項

- 小型の外殻は周りのブロックへ張り出すので、隣に置いた配管や別の塔は外殻に重なって見える。ユーザーの指定どおりの見た目で、当たり判定は1ブロックのまま。
- 大小の性能は同じにした。大型を選ぶ利点は見た目と外周のどこにでも配管をつなげることだけ。釣り合いはサバイバルで確認していない。
- 分割前に戻した撹拌反応槽・触媒反応の挙動は、分割前のテストで確認した範囲だけ。分割後に作ったワールドで撹拌機・触媒反応器を置いていた場合、それらのブロックは失われる（本格運用前のため互換処理なし）。
- 性能の比較計測はしていない。構造探索は設置・撤去の通知時だけ行う。

## 次回の注意

- `tools/generate_manufacturing_ponder.py`は章を指定して実行すること（例：`python3 generate_manufacturing_ponder.py manufacturing_sulfuric_acid`）。全章を再生成すると、上流の型の変化で関係のない章の構造まで書き換わる。工程の文言は`tools/data/ponder_lessons.json`の`guide.manufacturing_*`が優先される。
- 外周の付いた段をほかの装置で使う場合も、Createの配管をつなぐなら外周にブロックエンティティが必要。

## テクスチャ（追記）

ユーザーの指示で、Create風に作り直した。ふた付き槽の銀色の板（`reactor/silver/shell`）の溝の部分を地にし、つながっていない縁にCreateの安山岩ケーシングの2ピクセルの縁取りを重ねる（`tools/electrolysis_hardware.py`の`andesite_frame`・`silver_field`。安山岩ケーシングはGradleが解決したCreateのjarから読む）。電解セルの外装・上縁・制御盤の地板と、吸収塔の側面（段の継ぎ目に縁取り）がこの作り方で、吸収塔の天板は縁を安山岩ケーシングの色にした。Ponderの実画面で、吸収塔の大小と電解セル・電解採取の見た目を確認した。
