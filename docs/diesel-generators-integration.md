# Create Diesel Generators 統合

Minecraft 1.21.1 / NeoForge 21.1.219 / Create 6.0.10-281。基点は、他メンバーの改修を取り込んだ `4fc481f`。
流体登録は共通の `PlantFluid` を維持し、削除された独自GUI・電線・電池・発電機は復活させていない。既存のCreate: Electro Energeticsによる電力供給も維持する。

## 固定依存と流体

CDG **1.21.1-1.3.15**をクライアント・サーバー両方の必須依存とする。
GradleはModrinthの不変バージョンID `UoPH8lO1` を使用し、MODメタデータも完全一致を要求する。CDGの対応範囲はNeoForge `[21.1.174,)`、Create `[6.0.7,6.1.0)` なので現在の環境を含む。CDG本体・依存JARをCreate-Plantへ同梱しない。

確認した公開ソース：CDGの`1.21.1`ブランチ、コミット `79ce136730871b6f3f7b55d3cc748ff2bec2ad12`。
配布CDG JARのSHA-256：`56ef1d574278fc311f1ffa223dbd613077b899354a18d01ae8dca2578a4e2990`。

| 物質 | 使用するsource流体ID | バケツ・ワールド上の流体 |
| --- | --- | --- |
| 原油 | `createdieselgenerators:crude_oil` | CDG本来の定義 |
| ガソリン | `createdieselgenerators:gasoline` | CDG本来の定義 |
| 軽油 | `createdieselgenerators:diesel` | CDG本来の定義 |

`CEPetroFluids`はこれらのsourceを参照するだけで、新しい代理流体を登録しない。Create-Plant側の重複流体・flowing・FluidType・バケツ・対応モデルとテクスチャを削除した。
`heated_crude_oil`も廃止し、原油は加熱後もCDGの同じ流体に温度Componentを持つ。
ナフサ・重油・潤滑油・エチレン・プロピレンはCDGに同等の流体がないため維持した。CDGにはLPG流体がなく、エタノールはエチレンとは別物なので共通化対象にしていない。

## 採掘・蒸留・燃料利用

CDGの油田生成・保存、Pumpjack、`createdieselgenerators:pumpjack_output`タグを変更していない。採掘機の出力は上表の原油なので変換機を介さず受け取れる。新しい原油採掘機・タンク・ポンプは追加しない。

原油 → 工業用加熱器／流体熱交換器 → 反応プラントの分離塔（棚段2～4段）→ 留分別のCreate配管・標準タンク → CDGエンジン、という既存の設備構成を使う。
原油は加熱した槽の予熱レシピで「予熱した原油」へ変換してから分留塔へ送る。入口の温度数値は判定しない。1,000 mB当たりの留分は、重油200・潤滑油100・軽油300・ガソリン200・ナフサ200 mBで、棚段1段ごとに1バッチを同時に処理する。放熱・加熱能力に応じて流量や設定温度、設備規模を調整する。

## CDGの固有挙動

`CDGFluids`は対象の燃料を`BaseFlowingFluid.Source/Flowing`と通常の流体ブロックで登録する。専用の代理FluidやFluidBlockに差し替えない。

- `FuelType.getTypeFor`と`IEngine.getFuelType`は流体Holder／流体自体を使う。温度Componentを付けてもCDGの燃料データ・回転速度・応力容量・消費率をそのまま参照する。
- `GameEvents.onExplosion`はサーバー側で、`COMBUSTIBLES_BLOW_UP`が有効かつ燃料の通常エンジン速度が非ゼロなら、爆発中心近くの燃料源を消して後続爆発を予約する。`onServerTick`が既存のキューを処理する。判定範囲・威力・炎・連鎖処理を変更・複製していない。
- 配布データではガソリン・軽油・バイオディーゼル・植物油・エタノールがこの燃料条件を満たす。原油は燃料登録されておらず、燃料だけの爆発対象へ追加していない。
- `LighterItem.onEntityItemUpdate`には、点火したライターのアイテムが燃料流体に接触すると爆発する処理がある。ライターの燃料Component、着火操作、Creeper操作なども元のCDG処理を維持する。
- 調査した燃料のFluidクラスには、火・溶岩に触れれば必ず発火する独自の汎用処理はない。そのような条件は追加していない。流出・エンティティとの通常の相互作用もCDGのFluidType／FluidBlockに従う。

## 温度境界の対応

温度Componentを取り除く方式は使わない。CDGのエンジン（通常・モジュール・大型）、バーナー、設置オイルバレル、設置キャニスターは既存の`ThermalFluidTank`へ対応する。携帯キャニスター・燃料工具は元の`FLUID_CONTENTS` Componentと容量エンチャントを維持する`ThermalToolTank`を使う。バレルの移動構造体用ハンドラー、キャニスターへのSpout搬入にも異温度混合を適用する。

| CDG流体 | 熱計算上の密度 kg/L | 比熱 J/(kg·K) |
| --- | ---: | ---: |
| 原油 | 0.85 | 2,000 |
| ガソリン | 0.74 | 2,200 |
| 軽油 | 0.83 | 2,100 |
| バイオディーゼル | 0.88 | 2,000 |
| 植物油 | 0.92 | 1,970 |
| エタノール | 0.789 | 2,440 |

熱計算用の簡略物性であり、CDG本来のFluidType密度・粘性・外観を上書きしない。sourceとflowingの双方へ適用する。加熱・冷却・自然放熱、保冷設備、配管の熱損傷は共通の流体温度を使用する。圧力・腐食の分類や配管材質の制限も変更しない。蒸気管の気体専用制約も維持し、液体のCDG燃料はそこでは搬送・損傷させない。混合時に無視するのは自MODの温度Componentだけで、他のComponentsの差は維持する。

## 無効化対象とMixinの範囲

CDGに蒸留を完全停止する公式設定はない。最低高さの設定を変えるだけでは使用禁止にならないため、次の対象だけを制限する。

| 対象 | 処理 |
| --- | --- |
| `createdieselgenerators:distillation`型の全レシピ | `DieselRecipeMixin`でRecipeManager読込時に除外。追加データパック・再読込も対象 |
| `createdieselgenerators:distillation_controller`の作成レシピ | 同じフィルターで出力IDから除外 |
| 蒸留コントローラーによるCreateタンク変換 | `DieselDistillationControllerMixin`で`useOn`を停止 |
| `createdieselgenerators:distillation_tank`の工程 | `DieselDistillationMixin`でtickを停止。コマンドで配置しても稼働不可 |
| 表示 | Creativeイベントでコントローラーを除外。JEIはruntime API、EMI等は`c:hidden_from_recipe_viewers`タグを使用 |

元の`distillation/crude_oil`、`distillation/superheated_crude_oil`、`crafting/distillation_controller`も上記で除外される。外部レジストリは削除しない。

温度用Mixinは`DieselTankMixin`、`DieselBarrelTankMixin`、`DieselEngineTankMixin`、`DieselToolTankMixin`、`DieselSpoutMixin`、`DieselMountedTankMixin`。対象をCDGのタンク生成・携帯容器・Spout比較・移動バレルに限定する。既存`ThermalConnectivityMixin`も温度対応したCDGバレル／エンジンの結合へ適用する。他MODの通常のFluidTankやCDGの燃料イベントは置換しない。

## 主な変更ファイル

- 依存：`build.gradle`、`gradle.properties`、`META-INF/neoforge.mods.toml`。
- 登録・説明：`CEPetroFluids`、`CEProcessFluids`、`CEItems`、`CECreativeModeTabs`、Ponderの共通流体リンク、翻訳・不要なバケツ資源。
- 温度・蒸留：`FluidThermal`、`ThermalStreams`、`ThermalFluidTank`、`FractionatingColumnBlockEntity`、既存工程の`ProcessRecipe`。
- 外部境界：`compat/diesel/DieselIntegration`、`ThermalToolTank`、上記CDG向けMixin、`CreatePlantJeiPlugin`、非表示タグ。
- 検証：`DieselIntegrationGameTests`、既存の精製ライン・配管・バケツ・Ponder検証、実際のCreativeタブの非表示確認。削除済みの電池・電線・発電機を要求していた検証スクリプトも現行コードに合わせて整理。

## 検証結果と制約

- `build runGameTestServer`：改修後の構成で209件成功。採掘口の回転処理と油田残量、3種類のエンジン、バレル・キャニスター・工具・Spout・移動バレル、全サイズの蒸留、温度・熱量・流体量、満杯時の部分受入れを確認。CDGガソリンを実際のポンプで流し、標準・ガラス・フィルター・バルブと4種類の特殊配管の熱損傷、および気体専用蒸気管の液体拒否も確認。
- `-PwithJei=false runGameTestServer`：209件成功。
- CDGをruntimeから除いた起動：NeoForgeが要求バージョンと未導入を示す必須依存エラーを出すことを確認。
- クライアント：改修後の79シーンの日本語Ponder、温度表示、ゴーグル158ケースの3行制限、実際のレンチ長押しとサーバー反映7ケースを確認。JEIと全CreativeタブでCDG蒸留コントローラーが非表示。
- 爆発：CDGのガソリンと、精製製品の定義を使って温度を付けタンクを通したガソリンの両方をTNTで爆発させ、元のCDGイベントが燃料源を消すことを確認。流体Blockの定義も同一。
- 配布JAR：`build/libs/create-heavy-indusstry-0.1.0.jar`。CDGクラス・入れ子JARの同梱がないことを確認。SHA-256：`53104e12b64e253dd6a18fa69bf48fe1f7c983932d9268e62a47d94ed1faa7ac`。

EMI画面そのもの、巨大な燃料貯蔵施設での連鎖爆発、点火ライターを投げ込む操作、実際のPumpjack全機構を手作業で組み立てる長時間プレイ、移動構造体への搭載・走行・解体の一連の操作、大人数でのMSPTは未検証。EMIの非表示タグは公開ソースで対応を確認している。
CDG配布データ由来の、Sable未導入時の任意Mixin警告と`andesite_girder_strut`の未登録アイテムを参照するlootログが発生する。Electro Energeticsの`duplex_wire_segment`モデル参照にも警告がある。今回の統合対象外の外部MOD資源は変更していない。
ワールドのFluidStateや通常の固定バケツへ変換すると温度Componentは保持されない既存の制約がある。そこで再取得した流体は初期温度として扱うが、CDG固有の流体ブロック・燃料挙動は維持する。
