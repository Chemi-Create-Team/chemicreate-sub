# チャット内追加機能のオーバーホール監査

## 基準と目的

- 監査日：2026-11-06
- 監査対象：このチャットで追加した硝酸吸収塔、素材用途、安全弁用スクラバーと、その周辺の回帰
- 静的確認時の基準コミット：`1403273fe4d3ca22192aaf848bacb12644c33f74`
- 長時間テスト実行時の基準コミット：`c099d6ea`（後に最新`origin/main`へリベースされ、機能コミットは`192c4934`と`1403273f`になった）
- 監査報告初版コミット：`ea861940cd6e785589d993834aabe3e047218b9b`
- 実装コミット：なし。この文書は不具合の引き継ぎだけであり、修正は行っていない。

この文書は、修正を担当するClaude Codeへ、確認済みの不具合、再現条件、原因、修正時の受け入れ条件を渡すためのものである。推測と確認済みの事実を分け、長時間テストで見つかった対象外の失敗と実行環境の問題も記録する。

## 結論

チャット内で追加した機能に、製品動作の不具合9件を確認した。特に優先するものは、吸収塔の構造破損後の継続処理と内容物消失、安全弁用スクラバーの閉塞無視、AODレシピの到達不能である。さらに、現在のリポジトリ全体では核融合流体の翻訳不足により監査GameTestが1件失敗し、765 MW対応後のラジエーター仕様へ追従していないと考えられるGameTestが3件失敗する。

| ID | 優先度 | 領域 | 要約 |
| --- | --- | --- | --- |
| CP-AUD-001 | 重大 | AOD精錬 | アルゴンを入れても通常のステンレス鋼レシピが先に選ばれる |
| CP-AUD-002 | 重大 | 吸収塔 | 非所有部を壊してから最大19 tick、破損した塔が処理を続ける |
| CP-AUD-003 | 重大 | 吸収塔 | 不成立後に所有ブロックを壊すと4槽の内容物が無言で消える |
| CP-AUD-004 | 高 | 吸収塔・有毒ガス | 所有ブロック破壊時にNO₂・NOを雲にせず削除する |
| CP-AUD-005 | 高 | 吸収塔の形状 | 削除済み足場と冠部の見えない当たり判定が残る |
| CP-AUD-006 | 重大 | 安全弁用スクラバー | 上面が固体で塞がれていても安全弁が大気開放できる |
| CP-AUD-007 | 中 | 安全弁の表示 | 大部分を放出した部分洗浄でも「中和中」と表示する |
| CP-AUD-008 | 高 | 石綿隔膜 | 電解槽列のコントローラー変更で石綿の消耗進捗を回避できる |
| CP-AUD-009 | 中 | 翻訳・検証 | 吸収塔の中国語19キーがなく、検証も中国語を見ていない |
| CP-AUD-010 | 高 | 登録監査 | 核融合流体の英日翻訳16キー不足で必須GameTestが失敗する |
| CP-AUD-011 | 高 | ラジエーター試験 | 容量・能力変更へ追従していない3つの必須GameTestが失敗する |
| CP-AUD-012 | 中 | 統合試験 | 硝酸プラント統合試験が配管・電力・実時間運転を通らない |

## CP-AUD-001：AODレシピが通常レシピに負ける

### 確認した事実

- [通常レシピ](../../src/main/resources/data/create_heavy_indusstry/recipe/mixing/stainless_steel_ingot.json#L3-L41)は、鉄7、クロム2、ニッケル1のアイテム10個からステンレス鋼10個を作る。
- AODレシピは同じアイテム10個にアルゴン100 mBを加え、ステンレス鋼12個を作っていたが、現在は[転炉のAOD精錬](../converter.md#1チャージの処理)へ移行した。
- Create 6.0.10の`RecipeTrie`は、同じアイテム列では流体を要求しない通常レシピを親ノード、流体を要求するAODレシピをその子として候補化する。探索は親ノードの値を子より先に返す。
- `BasinOperatingBlockEntity`の候補順は`Recipe.getIngredients().size()`、すなわちアイテム材料数を基準にしている。両方とも10なのでAOD側は優先されない。
- `BasinRecipe.match`は余分な流体がBasinにあることを不一致理由にしない。このため、アルゴン入りでも通常レシピが成立する。

### プレイヤーへの影響

超加熱Basinへ鉄7、クロム2、ニッケル1、アルゴン100 mBを入れても、通常レシピが金属を消費してステンレス鋼10個を出し、アルゴンは残る。指示書、JEI、Ponder、[金属文書](../metals.md)が示すAODの12個生産を利用できない。

### 再現と回帰テスト

1. 実際のBasin、Mechanical Mixer、超加熱Blaze Burnerを組む。
2. 上記10アイテムとアルゴン100 mBを投入する。
3. 完了後、ステンレス鋼が12個であり、アルゴンが100 mB消費されたことを確認する。
4. アルゴンなしでは従来どおり10個になることも同じテストで確認する。

現在はJSONの存在だけが確認され、実際のBasinでAOD候補が選ばれるGameTestがない。

### 修正時の注意

単にレシピIDやJSONの並び順を変えるだけでは、Trieの親子順に依存するため不十分である。同じアイテム材料を持つレシピでは、より具体的な流体条件を持つ候補が先に処理される仕組みにする。Create全体のBasin選択を変更する場合は、既存の全Mixing、Compactingレシピへの影響を確認する。材料を増やして優先度を上げる回避策は「通常レシピと同じ金属材料」という仕様を変えるため、無断で採用しない。

## CP-AUD-002：吸収塔の破損検出が遅い

### 確認した事実

- 所有Block Entityは[20 tickごとに構造を検証し、5 tickごとに処理する](../../src/main/java/net/createplant/block/entity/AbsorptionTowerBlockEntity.java#L102-L108)。
- [ブロック破壊時の即時処理](../../src/main/java/net/createplant/block/AbsorptionTowerBlock.java#L84-L87)は、壊したBlock Entity自身が`isOwner()`のときだけ`removeOwner()`を呼ぶ。
- 壁、棚段、上部外殻を壊しても、所有Block Entityへ構造変更を通知する経路がない。

### 影響

非所有部を壊した直後も`formed`が真のまま残る。次の定期検証まで最大19 tickあり、その間に最大4回の5 tick処理が走る。完了間近のバッチは、物理的に塔が壊れているのに原料を消費して生成物を作れる。

### 必要な修正と回帰テスト

- どの構成部品を撤去しても、撤去処理中に所有者へ再検証または解体を通知する。
- 通知直後から処理と全ポートを止め、同じ所有者に対する解体処理を一度だけ行う。
- GameTestでは処理進捗を完了直前にし、非所有の壁を壊し、同じtickおよび20 tick後の両方で原料・生成物が増減しないことを確認する。
- チャンク未ロードは破壊と区別し、未ロード範囲を強制ロードしない。

## CP-AUD-003：不成立後の所有ブロック破壊で内容物が消える

### 確認した事実

- 定期検証で構造が崩れると、[所属を解除して`formed=false`にする](../../src/main/java/net/createplant/block/entity/AbsorptionTowerBlockEntity.java#L111-L127)が、4つの処理槽と進捗は処理しない。
- `isOwner()`は`formed`も要求するため、不成立後の元所有者は所有者と判定されない。
- その状態で元所有ブロックを壊すと、[所有者だけを対象にする`onRemove`](../../src/main/java/net/createplant/block/AbsorptionTowerBlock.java#L84-L87)が何もせず、Block Entityと一緒に全内容物が消える。

### 必要な修正と回帰テスト

構造解体時の物資方針を一か所に集約し、所有フラグを消す前に実行する。現在のコードコメントは全4流体を放出する設計を示しているため、少なくとも無言の消失には戻さない。次をGameTestで確認する。

1. 4槽に別々の流体を入れる。
2. 非所有部を壊して不成立にする。
3. 元所有ブロックを壊す。
4. 各流体が方針どおり一度だけ返却、保持または放出され、複製も消失もしない。

直接所有ブロックを壊す既存テストだけでは、この順序を通らない。

## CP-AUD-004：吸収塔破壊時に有毒ガスが消える

### 確認した事実

[現在の`vent()`](../../src/main/java/net/createplant/block/entity/AbsorptionTowerBlockEntity.java#L217-L227)は、内容物があれば白い`CLOUD`粒子を出し、4槽を空にする。`ToxicGas.release`は呼ばない。吸収塔の原料である二酸化窒素と副産物の一酸化窒素は[ToxicGasの対象](../../src/main/java/net/createplant/safety/ToxicGas.java)である。

### 影響

稼働中の塔を直接壊しても、NO₂とNOは有毒ガス雲にならず削除される。安全弁、配管破損、タンク破裂で同じガスを漏らした場合と危険性が一致しない。

### 必要な修正と回帰テスト

- 気体槽は`ToxicGas.release`を通す。水と硝酸についても、返却、保持、流出のどれにするかを既存設計と合わせ、単なる白粒子だけで処理済みにしない。
- CP-AUD-002、003と同じ集中した解体処理から呼び、複数部品の`onRemove`で二重放出しない。
- NO₂を入れた形成済み塔の所有部と非所有部をそれぞれ壊し、雲がちょうど1つ生成され、塔の槽が空になることをGameTestで確認する。

## CP-AUD-005：削除済み足場の見えない当たり判定

### 確認した事実

- [モデル生成処理](../../tools/generate_reaction_tower.py#L243-L264)は、旧`platform`と`crown_gallery`モデル、グレーチングテクスチャを削除し、`part` 18～26を通常の壁モデルへ割り当てる。
- Java側は今も[中間層を`DECK`へ割り当て](../../src/main/java/net/createplant/block/AbsorptionTowerBlock.java#L36-L50)、[DECKとCROWNに`gallery`形状を加える](../../src/main/java/net/createplant/block/AbsorptionTowerBlock.java#L55-L80)。形状はブロック範囲から4ピクセル外へ張り出し、上へ最大27ピクセル伸びる。
- [NitricAcidGameTests](../../src/main/java/net/createplant/gametest/NitricAcidGameTests.java#L75-L82)は、廃止した保守足場の見えない形状が存在することを成功条件にしている。

### 影響

見た目は支柱だけなのに、プレイヤー、設置、選択、移動が存在しない床や手すりに遮られる。DESIGN.mdの「当たり判定、選択範囲、見た目を整合させる」に反する。

### 必要な修正と回帰テスト

- DECKと冠部ギャラリーの外向き形状を削除し、モデルに見える容器、支柱、ノズルだけへ形状を合わせる。
- 既存ワールドのBlockStateを考慮し、`part`の保存値を削る必要がなければ旧18～26は壁と同じ形状として読み続ける。
- 古い足場を要求するGameTestを、全形成部品の形状境界が現行モデルの意図した範囲内であることを確認するテストへ変更する。
- 実クライアントで塔の周囲を歩き、隣接ブロック設置と選択輪郭を確認する。

## CP-AUD-006：スクラバー上面の閉塞を無視する

### 確認した事実

- [安全弁は真上にスクラバーがあるだけで`atmosphere=true`にする](../../src/main/java/net/createplant/block/entity/ReliefValveBlockEntity.java#L69-L80)。スクラバーの上面が空いているかは判定しない。
- 中和し切れなかったガスは[スクラバーの1ブロック上へ無条件で`ToxicGas.release`](../../src/main/java/net/createplant/block/entity/ReliefValveBlockEntity.java#L92-L105)する。
- [有毒ガス文書](../toxic-gas.md#苛性ソーダの排ガススクラバー)とPonderは「残ったガスは上面から出る」と説明している。

### 影響

スクラバーの上に固体ブロックを置いても安全弁が開き、有毒ガス雲が固体内部に作られる。粒子や雲が見えにくい一方、`AreaEffectCloud`の効果判定は残るため危険を認識しにくい。

### 必要な修正と回帰テスト

- スクラバー経路を大気開放として使う前に、その上のチャンクがロード済みで、上面位置が置換可能かつ流体なしであることを確認する。
- 閉塞中は安全弁を`blocked`として扱い、容器からガスを抜かず、苛性ソーダも消費しない。
- 上面を固体で塞いだ状態、流体で塞いだ状態、空いた状態をGameTestで比較する。
- すべて中和できる場合だけ閉塞を許す別仕様を採るなら、必要量を消費前にシミュレートし、不足時に途中まで抜かない原子的処理が必要になる。現在の文書仕様に最も素直なのは、上面が空くまで経路全体を閉じることである。

## CP-AUD-007：部分洗浄の経路表示が危険を隠す

### 確認した事実

[安全弁処理](../../src/main/java/net/createplant/block/entity/ReliefValveBlockEntity.java#L92-L103)では、1 mBでも中和すれば`scrubbed=true`となり、残りを雲として放出した後も`route="scrubbing"`を設定する。例えば8,000 mBのガスに苛性ソーダ100 mBなら、100 mBを中和し、7,900 mBを放出しても「中和中」と表示する。

### 必要な修正と回帰テスト

- 全量中和できたときだけ`scrubbing`とする。
- 残量を放出した場合は少なくとも`venting`とし、必要なら`partial_scrubbing`の専用状態を英日中の翻訳、ゴーグル、Display Linkへ追加する。
- 既存の苛性ソーダ不足テストは雲の有無だけでなく、弁の`route`とスクラバーの状態表示も確認する。
- Ponderの不足説明と実際の表示名を一致させる。

## CP-AUD-008：石綿隔膜の進捗をコントローラー変更で回避できる

### 確認した事実

- [`diaphragmBrine`と`sodaQuarter`は各Basin Block Entityの`State`へ保存](../../src/main/java/net/createplant/basin/BasinElectrolysis.java#L46-L92)される。
- 処理時は[現在の列コントローラーのStateだけ](../../src/main/java/net/createplant/basin/BasinElectrolysis.java#L190-L244)を読み書きする。
- [列の低端がコントローラー](../../src/main/java/net/createplant/basin/BasinClusterManager.java#L30-L47)になる。低端へのBasin追加、列の分割・再結合、別の低端への組み直しで、別Block Entityの0から始まるカウンターが使われる。
- [既存GameTest](../../src/main/java/net/createplant/gametest/ChlorAlkaliGameTests.java#L27-L53)は固定した5槽列でちょうど10,000 mBを処理するだけで、途中の構造変更を扱わない。

### 影響

10,000 mB直前にコントローラーを入れ替えると石綿消耗までの進捗をリセットできる。新しい低端を用意し続ければ、隔膜の25 percent増産を石綿1個で規定量以上利用できる。苛性ソーダの4分の1端数も移動時に失われる、または古いコントローラーへ残る。

### 必要な修正と回帰テスト

- 列単位の進捗を、形成、分割、結合、低端変更時に消失・複製しない所有方式へ変更する。
- 9,998 mB相当まで処理し、列の低端を変え、残り2 mBを処理したときに石綿がちょうど1個消費されることを確認する。
- 分割して再結合、チャンク保存・再ロード、隔膜を別Basinへ移動した場合も、苛性ソーダ総量と石綿消費量が保存されることを確認する。
- 単純に全メンバーへ同じカウンターを複製すると、分割時に進捗が増える可能性がある。所有権移行時の合成規則を先に決める。

## CP-AUD-009：吸収塔の中国語翻訳と検証が不足する

### 欠けている19キー

- `absorption_tower.create_heavy_indusstry.inputs`
- `absorption_tower.create_heavy_indusstry.outputs`
- `absorption_tower.create_heavy_indusstry.progress`
- `absorption_tower.create_heavy_indusstry.state`
- `absorption_tower.create_heavy_indusstry.status.absorbing`
- `absorption_tower.create_heavy_indusstry.status.idle`
- `absorption_tower.create_heavy_indusstry.status.no_feed`
- `absorption_tower.create_heavy_indusstry.status.output_full`
- `absorption_tower.create_heavy_indusstry.status.unformed`
- `message.create_heavy_indusstry.absorption_tower.height`
- `message.create_heavy_indusstry.absorption_tower.missing`
- `message.create_heavy_indusstry.absorption_tower.unloaded`
- `jei.create_heavy_indusstry.nitric_absorption`
- `jei.create_heavy_indusstry.note.nitric_absorption`
- `create_heavy_indusstry.ponder.shared.guide.acid_products.step_1`から`step_5`

### 原因

- [吸収塔生成スクリプト](../../tools/generate_uranium_assets.py#L627-L680)のJEI、実行時表示、シーン文は英日2要素であり、`index < 2`のときだけ追加される。
- [Ponder言語検証](../../tools/validate_ponder_language.py#L8-L15)と[チュートリアル検証](../../tools/validate_plant_tutorials.py#L43-L46)は英語と日本語だけを対象にする。
- `zh_cn.json`にはブロック名だけがあるため、GameTestと4検証スクリプトは合格しても中国語環境ではキー文字列が表示される。

### 必要な修正と回帰テスト

- `tools/generate_uranium_assets.py`を3言語の正本にし、手作業でJSONだけを直さない。
- 元の翻訳ファイルの改行コードを保持する。
- 2つの検証スクリプトへ`zh_cn`を加え、英日中すべてで同じランタイムキー、Ponder見出し、字幕があることを確認する。
- 中国語クライアントのPonder品質チェックも少なくとも酸製品シーンで実行する。

## CP-AUD-010：核融合流体の翻訳16キー不足

### 確認した事実

必須の[`AuditGameTests.everyRegisteredEntryHasAssetsTranslationsAndLoot`](../../src/main/java/net/createplant/gametest/AuditGameTests.java#L40-L71)が、次の4流体について英語と日本語の各8キー、合計16キー不足で失敗する。

- `block.create_heavy_indusstry.deuterium_gas`
- `block.create_heavy_indusstry.tritium_gas`
- `block.create_heavy_indusstry.helium_gas`
- `block.create_heavy_indusstry.liquid_helium`
- `fluid_type.create_heavy_indusstry.deuterium_gas`
- `fluid_type.create_heavy_indusstry.tritium_gas`
- `fluid_type.create_heavy_indusstry.helium_gas`
- `fluid_type.create_heavy_indusstry.liquid_helium`

[生成スクリプト](../../tools/generate_fusion_assets.py#L447-L482)は`fluid.*`とバケツ名だけを生成し、登録された流体ブロックとFluid Typeの説明キーを生成していない。

### 必要な修正と回帰テスト

- `generate_fusion_assets.py`へ英日中の`block.*`と`fluid_type.*`を追加して再生成する。
- `AuditGameTests`を単独実行し、その後全件実行する。
- CP-AUD-009の対応と合わせ、登録監査自体も中国語キーを確認する。

これはチャット内追加機能の直接の不具合ではないが、現在の必須テストを赤くしているため、完了判定から除外しない。

## CP-AUD-011：ラジエーター必須GameTestの仕様追随漏れ

### 再現結果

`RefrigerationGameTests,RadiatorTemperatureGameTests`だけを実行しても、10件中同じ3件が失敗した。偶発的な全件テスト干渉ではない。

1. [`aRadiatorCondensesCompressedAmmoniaIntoLiquid`](../../src/main/java/net/createplant/gametest/RefrigerationGameTests.java#L97-L117)
   - 失敗：`A compressor-like charge lifts the condensing temperature above the air: 6.244163073778816 bar`
2. [`realRadiatorCellAcceptsFluidWithoutTheOldCabinet`](../../src/main/java/net/createplant/gametest/RadiatorTemperatureGameTests.java#L28-L70)
   - 失敗：`A full destination retains all unprocessed feed`
3. [`aRadiatorCondensesSteamAtItsCapacityAndHoldsAVacuum`](../../src/main/java/net/createplant/gametest/RadiatorTemperatureGameTests.java#L131-L147)
   - 失敗：`What one fan cannot condense waits at the inlet: 0`

### 有力な原因

[RadiatorBlockEntity](../../src/main/java/net/createplant/block/entity/RadiatorBlockEntity.java#L21-L25)は765 MW対応で`CONDENSER_BUFFER=32000`、`CONDENSER_FLOW=4000`、`CONDENSING_FILM=280`になっている。

- 満杯試験は出力へ8,000 mBを入れて満杯とみなすが、現在の容量は32,000 mBである。そのため後続100 mBも処理され、入口に残らない。
- 蒸気試験は1,000 mBの一部が残ることを要求するが、現在の凝縮伝熱係数では1セルが全量を処理できる。
- アンモニア試験の170,000 mB相当の充填は32,000 mB体積で約6.244 barにしかならず、外気より5 K高い凝縮温度へ届かない。以前の小さい体積を前提にした固定量と考えられる。

### 修正時の注意と回帰テスト

これは実装を旧能力へ戻す根拠ではない。現在の765 MW設計が正しいかを先に確認し、テストの8,000、1,000、160,000という固定値を公開定数と物性計算へ追従させる。

- 満杯試験は`CONDENSER_BUFFER`まで確実に出力を埋め、その後の原料が入口に残ることを確認する。
- 蒸気試験は現在能力より大きい供給量を使うか、「一部が必ず残る」という不要な前提を外し、質量・エネルギー保存、液体だけが出口へ出ること、飽和圧力を確認する。
- アンモニア試験は目標凝縮温度から必要圧力、必要気体量を物性式で求め、容量変更で壊れないFixtureにする。
- 数値変更後、対象10件と全GameTestを両方実行する。

## CP-AUD-012：硝酸プラント統合試験が実時間設備を通らない

### 確認した事実

[NitricPlantIntegrationGameTests](../../src/main/java/net/createplant/gametest/NitricPlantIntegrationGameTests.java#L140-L211)は、実在のBlock Entityと物質収支を使う点では有用だが、1回のGameTest処理内で`tickProcess()`、`process(.25)`、電解槽`tick()`を数百～数千回直接呼ぶ。[電解工程](../../src/main/java/net/createplant/gametest/NitricPlantIntegrationGameTests.java#L306-L333)も同じゲーム時刻のまま手動で進める。

### 覆えていないもの

- CreateのパイプとMechanical Pumpによる移送
- CEE電力網の実際の供給と時間変化
- サーバーtick、チャンク境界、アンロード・再ロード
- 満杯、停止、再始動を含む長時間連続運転
- 設備間の圧力差と搬送能力

### 推奨する追加確認

既存の物質・エネルギー台帳テストは残し、別の低速統合テストで実tickを進める。少なくとも吸収塔の入出力を実配管で接続し、保存・再ロードを挟んだ一連の硝酸製造を確認する。長時間化する場合は通常CI用の短縮Fixtureと、手動または夜間用のソーク試験を分ける。

## 実行した検証

### 成功したもの

| 検証 | 結果 |
| --- | --- |
| `bash gradlew build` | 成功 |
| `tools/validate_plant_tutorials.py` | 成功、84シーン、字幕405組、リンク122件 |
| `tools/validate_ponder_language.py` | 成功、89ランタイム見出し。ただし英日だけ |
| `tools/validate_visual_identity.py` | 成功、233項目 |
| `tools/test_ci_validators.py` | 11件すべて成功 |
| 追加機能対象のGameTest | 39件すべて成功 |
| JEIなしの対象GameTest | 21件すべて成功 |
| GPU情報 | NVIDIA RTX 4060、直接描画、OpenGL 4.6を確認 |
| 吸収塔モデル | クライアント撮影まで成功。モデル230要素に1ピクセル厚の要素なし |
| 安全弁用スクラバーPonder | `CLIENT QUALITY PASSED: 100 scenes, ja_jp`まで成功 |

対象GameTestの成功だけではCP-AUD-001～009の経路を通らない。例えばAODはJSON、吸収塔は直接所有部破壊、石綿は固定列、安全弁は開いた上面だけを試している。

### 全GameTest

`bash gradlew --no-daemon runGameTestServer`で327件を完走した。GameTest自体は約16分3秒、Gradle全体は約16分30秒で、必須4件が失敗した。

- CP-AUD-010の登録監査1件
- CP-AUD-011のラジエーター・冷凍3件

保存処理で約60.855秒と約256.633秒の停止があり、後者ではサーバースレッドが`ChunkMap.save/isExistingChunkFull`、ワーカースレッドが待機状態だった。いずれも回復して全件を完走した。再実行時にも発生するなら、GameTestのバッチごとの保存範囲、チャンク数、ストレージI/Oをプロファイルする。今回の監査だけでは製品コードの不具合と断定しない。

### GPUクライアント終了時のクラッシュ

吸収塔とスクラバーの両方で、描画、Ponder、撮影は完了した後、終了処理中にNVIDIAの`libEGL_nvidia.so.0+0xb56ae`でSIGSEGVし、Gradleは終了コード134になった。2回再現した。モデル読込やシーン検証の失敗ではなく、コンテナのEGL終了処理またはドライバ側の問題とみられる。Mod側での修正対象と決める前に、最小のNeoForgeクライアント、別ドライバ、ソフトウェア描画と比較する。

## 正常と確認できた範囲

次の項目は監査時のテストと静的確認で意図どおりだった。修正時に無関係な変更を加えない。

- 吸収塔の反応量はNO₂ 300 mB＋水100 mBから硝酸200 mB＋NO 100 mBである。
- 旧撹拌反応槽の硝酸レシピはなく、硝酸なしで開始できる。硝酸を作るために硝酸を要求する循環はない。
- PUREXは濃縮まで戻しても新燃料の約半分であり、燃料を増殖しない。
- 合成ルチル、ジルコニアの初期経路、蛍石フラックス、工業用ガラス・樹脂、液体酸素の相変化は対象テストに合格した。
- 専用スクラバーの側面ポート、8,000 mB容量、破壊・再設置での保存、全量中和、不足時の雲、アンモニア通過は既存テストに合格した。
- JEIなしでも対象機能の21 GameTestが合格した。

## Claude Codeへの推奨作業順

1. 作業開始時に`git fetch origin main`と`git rebase origin/main`を行う。`docs/specs/steel-unification.md`の作業は金属文書と生成スクリプトで競合し得るため、同時作業を確認する。
2. CP-AUD-002、003、004を一つの吸収塔ライフサイクル修正として扱い、物資方針と一度だけの解体処理を先に決める。
3. CP-AUD-006、007を安全弁経路の原子的処理として修正する。
4. CP-AUD-001の実Basin選択テストを先に失敗させ、その後に候補選択を修正する。
5. CP-AUD-008は形成・分割・再結合・再ロードを含む所有権テストを先に作る。
6. CP-AUD-005をモデル生成スクリプトとJava形状の両方で整合させ、実クライアントで歩行・設置を確認する。
7. CP-AUD-009、010を生成スクリプトから修正し、英日中を検証対象にする。
8. CP-AUD-011は765 MW仕様を保ったままFixtureを現在定数へ追従させる。
9. 関連GameTest、JEIなし、4検証スクリプト、全GameTest、実クライアントの順で確認する。

各修正は小さな日本語コミットに分け、生成ファイルは`tools/generate_*.py`を直して再生成する。翻訳ファイルの改行コードを維持し、プッシュ前には再度リモートを取り込む。NVIDIA終了時クラッシュと長いチャンク保存停止は、製品修正と混ぜず、比較結果が得られた場合だけ別件として扱う。
