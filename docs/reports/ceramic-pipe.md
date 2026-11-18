# 粘土から作るセラミック管を追加した完了レポート

- 日付：2026-11-17
- 実装コミット：このレポートと同じコミット（`git log -1 -- docs/reports/ceramic-pipe.md`で確認）
- 説明：[温度・圧力等級](../thermal-grades.md)

## 指示

序盤で使える耐酸性のパイプとして、粘土から作れるセラミック管を追加する。

## 実装

| 部分 | 内容 |
| --- | --- |
| 材質 | `PipeMaterial.CERAMIC`：低温～高温（500 °C未満）、常圧だけ、耐食は酸・熱い酸。フッ化物（フッ化水素など）と加圧流体は通さない。既存の材質と同じ判定（`GradePipeMixin`）で、合わない流体は流れを止める |
| ブロック | `ceramic_pipe`（`MaterialPipeBlock`、Createの配管としてつながる）。性質はテラコッタと同じ（つるはしで回収、石の音）。BlockEntityは既存の`material_pipe`型に追加 |
| アイテム | `unfired_ceramic_pipe`（未焼成のセラミック管）。見た目は粘土のテクスチャを張った管の形 |
| レシピ | 粘土玉3個を横一列 → 未焼成のセラミック管2個。かまどで焼く → セラミック管1個（0.1経験値、200 tick） |
| 見た目 | 塩釉の炻器のような茶色の管と、テラコッタの受口（`textures/block/transport/ceramic_pipe.png`） |
| ポンプ | 専用ポンプは作らない。ポンプは材質で流体を制限しないので、Createのポンプでそのまま酸を送れる |
| Ponder | 「流体に合う配管を選ぶ」に、銅管の次の手順としてセラミック管の字幕を追加。8手順の上限に収めるため、ステンレス鋼管の重複した行と「発電設備の主管はステンレス鋼管」の手順（ステンレス鋼管の手順と同じ内容）を、セラミック管の行と手順に置き換えた |
| クリエイティブタブ | 配管の群に`ceramic_pipe`、中間素材の群に`unfired_ceramic_pipe` |

### 主要ファイル

- `transport/PipeMaterial.java`、`registry/CETransport.java`、`registry/CECreativeModeTabs.java`
- 生成元：`tools/generate_plant_tank_assets.py`（テクスチャ・モデル・ブロック状態・レシピ・ルートテーブル・つるはしタグ・翻訳）、`tools/generate_pipe_material_ponder.py`、`tools/data/ponder_lessons.json`
- `gametest/TransportQualityGameTests.java`、`docs/thermal-grades.md`

## 確認

- `compileJava`成功。`validate_ponder_language.py`・`validate_plant_tutorials.py`・`validate_translations.py`合格。
- GameTest：`TransportQualityGameTests`（新規`ceramicPipeCarriesAcidButNotPressureOrFluoride`：熱い硫酸塩液は通す、フッ化水素は腐食、加圧原料は圧力で拒否、ポンプで硫酸1000 mBを損失なく送る）、`PlantTutorialGameTests`、`RecipeVisibilityGameTests`、`SurvivalReachabilityGameTests`、`MaterialEquipmentGameTests`：以前からの`cooledInputsAndAllManufacturingRecipesRemainValid`以外合格。`CreativeCatalogGameTests`は以前からの六フッ化ウランのバケツで失敗するが、その前にある全アイテムのタブ所属の確認は通っている。
- 実画面（日本語）：`docs/quality/ceramic-pipe/pipe-materials-ponder.png`（Ponderのセラミック管の行と字幕）。

## 制約と未確認事項

- 序盤に酸をためるタンクはない（Createのタンク・横置きタンクは腐食性を受け付けず、ステンレス鋼タンクはステンレス鋼が要る）。セラミック管は撹拌槽・吸収塔・電解セルなどの装置の間をつなぐ用途になる。
- 実際のサバイバルで、粘土の入手量と必要な配管の長さの釣り合いは確認していない。
- 配管の傷みの修理に使う材料（ほかの管では同じ材料の板かインゴット）は、セラミック管については設定していない。
