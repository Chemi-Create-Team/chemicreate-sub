# 仕様書：温度・圧力の等級化（後続作業の引き継ぎ）

この文書は、温度と圧力を連続した数値から等級に置き換える作業を、続きから進める担当者（Codex）向けの仕様書です。背景と全体計画は[変更計画](thermal-grades-plan.md)を、作業規約は`AGENTS.md`と`DESIGN.md`を正本とします。この文書と計画が食い違う場合は、この文書を優先してください。

## 1. 目的と決定事項

- テストプレイで「何をすればいいかわからない」という声が出た。温度（K）と圧力（bar）を連続した数値で計算し、配管・タンク・反応のすべてに関わらせていることが、Createらしくなく分かりにくい原因になっている。
- 温度と圧力の**考え方は残す**。使い分けの理由（配管・タンクの素材、反応の条件、タービンの応力）として必要だから。ただし**数値の計算はやめ、等級にする**。プレイヤーに見せるのは等級の名前だけにする。
- ユーザーが決めたこと：
  - 計画の全段階を実施する。
  - 高圧のタンクほど同じ大きさで気体が多く入る仕組みは残す。
  - 温度5段階（極低温・低温・常温・高温・超高温）、圧力3段階（常圧・加圧・高圧）。
  - 合わない流体は**受け付けないだけ**。漏れ・損傷・破裂・爆発はなし。
  - 熱交換器の対向流は処理速度の割り増し（×1.5）として残す。
  - ふた付き槽の熱源はブレイズバーナーと電熱ヒーターの両方（Createの加熱段階で判定）。
  - 減圧弁は等級を1段下げる装置、配管の温度計・圧力計は等級を表示する。
  - 安全弁・信号式逃がし弁・背圧弁は削除する。フレアは可燃ガスを燃やす受け口、除害装置は有毒ガスを中和する受け口として残す。
  - 原子炉（RBMK）と核融合炉の**内部の**温度計算は残す。外へ出す冷却材・蒸気は等級にする。
- 本格運用前のため、旧ワールドとの互換処理は作らない（AGENTS.md）。消した仕組みの登録・アセット・翻訳・試験も同じ作業で消す。

## 2. 完了している部分

| コミット | 内容 |
| --- | --- |
| 35b2c43 | 等級の定義`thermal/Grade`（`Heat`・`Pressure`・`describe`）。流体の等級は`transport/FluidHazards.Properties.heat()`・`pressure()`。配管素材の等級（`PipeMaterial`の`hottest`・`coldest`・`strongest`）。ゴーグルの流体表示を等級に（`FluidThermal.readout`）。等級名の翻訳は`tools/generate_grades.py` |
| bf910f6 | 配管は素材の等級に合わない流体を受け付けない（`mixin/GradePipeMixin`、`PipeMaterial.pipe`・`carries`・`describe`）。配管の摩耗・凍結・やけど・漏れ・破裂と配管の圧力の記録を削除（`PipeWearBehaviour`・`HotSurfaces`・`PipeGasLeak`）。物理確認用のRBMKプレイテストを削除。ボイラー蒸気の受け渡しは`BoilerSteamOutput.attachToCreate`へ移動 |

等級の境目（`Grade`）：
- 温度：150 K未満が極低温、283.15 K未満が低温、373 K未満が常温、773.15 K未満が高温、それ以上が超高温。
- 圧力：2 bar未満が常圧、20 bar未満が加圧、それ以上が高圧。

流体の等級は今のところ`FluidHazards`の表の値（流体の種類ごとに固定）から求める。

配管素材の等級（実装済み）：

| 素材 | 最高温度 | 最低温度 | 最高圧力 | 耐食 |
| --- | --- | --- | --- | --- |
| 銅（Create） | 高温 | 低温 | 常圧 | なし |
| 樹脂 | 常温 | 低温 | 常圧 | 酸・熱い酸 |
| 強化 | 超高温 | 低温 | 加圧 | なし |
| 超高圧 | 超高温 | 低温 | 高圧 | なし |
| ステンレス鋼 | 超高温 | 低温 | 高圧 | 酸 |
| ニッケル合金 | 超高温 | 低温 | 高圧 | 酸・フッ化物 |
| ジルコニウム | 超高温 | 低温 | 高圧 | 酸・熱い酸 |
| 保冷 | 常温 | 極低温 | 加圧 | 酸 |
| PVC | 常温 | 低温 | 加圧 | 酸 |
| 弁 | 超高温 | 極低温 | 高圧 | 酸 |

## 3. 作業の進め方（全段階共通）

- 段階ごとに、ビルドとその段階に関係するGameTestが通る状態でコミットし、`main`へプッシュする。途中で壊れた状態をプッシュしない。プッシュ前にリモートを確認し、強制プッシュはしない。
- 生成スクリプト（`tools/*.py`）が正本のアセット・Ponder・翻訳は、生成元を直して再生成する。生成物を手で直さない。
- 段階ごとに`docs/reports/`へ完了レポートを書き、`thermal-grades-plan.md`の「進捗」表と「段階ごとの記録」を更新する。
- 消す物理の試験は削除し、新しいルール（受け付けない・変換する・等級で決まる）の試験を足す。必要な試験だけを足す。
- 名前・単位・停止理由は翻訳キーで管理し、日英中の3言語を揃える（`tools/validate_translations.py`）。

## 4. 残りの段階

### 4.1 A2 タンク

**目的**：タンクは自分の等級に合わない流体を受け付けない。破裂・ひずみ・押しつぶし・腐食漏れをなくす。気体の容量は圧力の等級で決まる。

**仕様**

| タンク | 入れられるもの | 最高温度 | 最低温度 | 圧力 | 耐食 |
| --- | --- | --- | --- | --- | --- |
| Createの液体タンク、横置き液体タンク、横置き管束ボイラー | すべて | 高温 | 低温 | 加圧 | なし |
| 黒鋼液体タンク（`liquid_tank`） | 液体 | 高温 | 低温 | 加圧 | なし |
| 水タンク | 水・温排水 | 高温 | 低温 | 加圧 | なし |
| 気体レシーバー、ガスタンクコンテナ | 気体 | 高温 | 低温 | 高圧 | なし |
| 50 bar耐圧タンク | すべて | 高温 | 低温 | 高圧 | なし |
| ステンレス鋼タンク | すべて | 超高温 | 低温 | 加圧 | 酸 |
| 保冷タンク | すべて | 常温 | 極低温 | 加圧 | なし |
| 開放円筒タンク・ガスホルダー | 液体・気体（それぞれ） | 常温 | 低温 | 常圧 | 腐食性を受け付けない（今どおり） |

- 判定は配管と同じ`incompatibility`の考え方を使う。素材の表を`PipeMaterial`から独立させるか共通の型にまとめるかは、重複が少ない方を選ぶ。
- 気体の容量倍率（`ThermalFluidTank.storageRatio`、今は一律256）：常圧×1、加圧×16、高圧×64。液体は体積どおり。
- タンクのゴーグル表示：中身の等級（`FluidThermal.readout`）と、そのタンクが受け付ける等級の範囲。
- 受け付けない理由は、ゴーグルか入れようとしたときの表示で分かるようにする（翻訳キー）。
- 水と蒸気は今、`PhaseTank`の二相処理でフィルターを通らずに入る。この特例はなくし、ほかの流体と同じく等級で判定する（例外を残さない）。

**主な対象**
- `thermal/PressureVesselBehaviour`（タンクから外す。破裂・ひずみ・押しつぶし・腐食を削除）
- `PlantFluidTankBlockEntity`、`HorizontalTankBlockEntity`（`ratingBar`）、`GasTankContainerBlockEntity`、`GasHolderBlockEntity`、`TankCasingBlockEntity`
- `mixin/ThermalCreateTankMixin`（Createのタンク）、`thermal/PhaseTank`、`ThermalFluidTank`、`EquipmentSafety`
- 設定`TransportConfig`の破裂・爆発の項目（`vesselRupture`・`ruptureExplosion`など）

**試験**：各タンクが等級外・腐食性の流体を拒否すること、気体の容量倍率、破裂しないこと。

### 4.2 A3 弁・計器

**仕様**
- **削除**：`relief_valve`、`redstone_relief_valve`、`back_pressure_valve`。ブロック・アイテム・BlockEntity・登録・レシピ・ルート・モデル・ブロックステート・タグ・翻訳・クリエイティブタブ・Ponderの部品登録（`ponder/ElectrolysisPonderPlugin`）・試験をまとめて消す。
- 生成元から消す：
  - `tools/generate_crafting_recipes.py`、`generate_pressure_assets.py`、`generate_pressure_valve_ponder.py`、`generate_flare_assets.py`、`generate_flare_ponder.py`。
  - 安全弁をPonderに置いている生成元：`generate_uranium_route_ponder.py`、`generate_ammonia_separator_ponder.py`、`generate_manufacturing_ponder.py`、`generate_toxic_gas_ponder.py`、`generate_lidded_vessel_ponder.py`、`generate_vessel_consolidation_ponder.py`、`generate_absorption_trays_ponder.py`。
- **減圧弁**（`pressure_reducing_valve`）：圧力の等級を1段下げる。下記の「圧縮の対応表」で、高い等級の流体を1段低い流体に変えて流す。対応がない流体は素通し。吐出圧力の設定（`PressureSetting`）は削除する。
- **圧縮の対応表**（新設、`thermal/`か`transport/`に1か所）：常圧の流体と、加圧・高圧の流体の組を定義する。既存の組は「合成ガス → 圧縮合成ガス（`compressed_synthesis_feed`）」。水素化処理・水素化分解に使う高圧水素（例：`compressed_hydrogen`）など、等級条件のある反応に必要な組だけを追加する（4.5を参照）。
- **フレア先端部**：配管から来た可燃ガスを燃やす受け口として残す。安全弁から受ける前提の処理は外す。
- **除害装置**（`relief_scrubber`）：配管から来た有毒ガスを中和する受け口として残す。
- **配管の温度計・圧力計**（`PipeGaugeBlockEntity`）：流れている流体の等級を表示する。比較器の出力は等級の段で出す。°C・barのしきい値設定は、等級を選ぶ設定に置き換える。
- **流量計・PID・発信器**（`ProcessControlBlockEntity`ほか）：測るのは流量と液位だけにし、圧力・温度の測定は削除する。

### 4.3 A4 ガス配管・圧縮機

- 気体配管は今、`transport/GasNetworks`が圧力差で気体を流している。これを削除し、液体と同じくCreateのポンプ（または圧縮機）で送る。配管の等級は2章の表に従う（気体配管は強化と同じ）。
- 往復式圧縮機（`ReciprocatingCompressorBlockEntity`・`ReciprocatingCylinderBlockEntity`）：気体を送るポンプの役と、圧縮の対応表で等級を1段上げる役を持つ。吐出圧力の設定は削除する。消費電力・潤滑油などの既存の仕組みは残す。
- 削除：`GasInflow`、`PressureSections`、`PressureSetting`、`GasNetworks`の圧力部分。
- 試験：ポンプで気体が流れること、圧縮機で等級が上がること、減圧弁で下がること。

### 4.4 B1 流体の温度を種類で表す

- 流体スタックごとの温度（データコンポーネント`CEThermal.TEMPERATURE`）を使わない。
  - `FluidThermal.kelvin(stack)`は、流体の種類の公称温度（`FluidHazards`の表の値）を返す。
  - `at()`は何もしない（スタックをそのまま返す）。
  - `tracked()`は常にfalse、`mix()`は量を足すだけ。
- 流体ごとの温度・二相・外気との熱を削除：`PassiveTank`の熱部分、`PassiveHeat`、`ThermalFluidTank.heat/cool/transferHeatTo`、`WaterPhase`、`FluidPhase`、`PhaseChange`の連続計算、`FlowingWaterTank`、`WorldLiquidTemperature`、`CoolingTowerPhysics`、`ThermalRoutes`、`ThermalMath`。
- 温度を運ぶためだけのmixinを削除：`ThermalFluidNetworkMixin`、`ThermalFluidTransportMixin`、`ThermalPipeConnectionMixin`、`ThermalConnectivityMixin`、`ThermalFilterMixin`、`ThermalOpenEndHandlerMixin`、`PassiveFluidNetworkMixin`、`PassiveTopologyMixin`、`ThermalCreateTankMixin`（タンクの判定に必要な部分は別の形で残す）。
- ほかのmodとの境界（`mixin/Diesel*`、`compat/diesel/ThermalToolTank`、`mixin/WorldLiquid*`、`mixin/NaturalWater*`）：温度の受け渡しを外す。CDGの機械・ホースとの互換は維持する。
- データコンポーネントの登録をやめる場合、旧ワールドの該当データが読めなくなる。本格運用前なので移行処理は作らず、レポートに書く。

### 4.5 B2 ふた付き槽・レシピの条件

**熱源**：槽の下の熱源の段階で決める。Createの`BlazeBurnerBlock.HeatLevel`を使い、電熱ヒーターも同じ段階を出す（`ElectricHeaterBlockEntity`はすでに`KINDLED`・`SEETHING`相当を返す）。
- 加熱 = `KINDLED`以上。
- 超加熱 = `SEETHING`。

槽そのものは温度を持たず、温まるまでの待ち時間もない。`VesselClimate`（槽の温度・沸騰・凍結）は削除する。

**レシピ**（`chemistry/ProcessRecipe`）：`minimumKelvin`・`minimumPa`を、熱源の段階と入力の等級に置き換える。今の条件付きレシピ：

| レシピ | 今の条件 | 新しい条件 |
| --- | --- | --- |
| `crack_naphtha` | 720 K（加熱ナフサ） | 超加熱。入力はナフサのまま |
| `polymerize_ethylene`・`polymerize_propylene` | 480 K | 加熱 |
| `oxidize_ethylene` | 480 K | 加熱 |
| `hydrotreat_diesel` | 30 bar・623 K | 加熱、入力の水素が高圧（圧縮の対応表の高圧水素） |
| `hydrocrack_heavy_oil` | 50 bar・673 K | 超加熱、入力の水素が高圧 |

**加熱した流体**（`thermal/ThermalStreams`）：加熱ナフサなどは、今は温度で自動的に名前が変わっている。
- 槽で入力を直接加熱する形にできるレシピは、加熱した流体をやめて入力を元の流体にする。
- 別の設備へ送る必要がある流れだけ、「加熱」の変換レシピとして残す。例：原油は分留塔へ送るので「原油 → 予熱した原油」（新しい流体）とし、分留塔は予熱した原油を受け付ける（今の「620 K以上の原油」の置き換え）。
- 使わなくなった加熱流体は、登録・アセット・翻訳・試験ごと削除する。どの流体を残すかは、サバイバルで作れること（`SurvivalReachabilityGameTests`）を基準に決める。
- 冷却した流体（`cooledStreams`：高温の生成ガス → 冷えた生成ガスの5組）は、4.6の冷却装置が変換する。

**ハーバーボッシュ・アンモニア分離**：
- `chemistry/HaberBoschBed`・`BedGas`・`GasMixture`（モルと平衡）は、1回の処理あたり決まった転化率のレシピにする。
- `AmmoniaSeparator`の平衡計算は、冷えた生成ガスから液体アンモニアと循環ガスを決まった割合で分ける形にする。
- `HaberBoschGameTests`・`NitricPlantIntegrationGameTests`を新しい形で通す。

### 4.6 B3 変換装置

| 設備 | 新しい動作 |
| --- | --- |
| 熱交換器（2×2・3×3・水冷ジャケット管、`thermal/ThermalEquipment`） | 熱い流れと冷媒の組を変換する（例：高温の生成ガス → 冷えた生成ガス、水 → 温排水）。1回の処理量は長さ×断面の単位（大型は長さ1あたり2単位）で決まり、対向流なら×1.5。組の表は1か所に定義する |
| ラジエーター＋冷却ファン | 熱い流れを1段冷やす変換。ファンの回転で処理量が決まる |
| コールドボックス | 気体 → 液化ガス（窒素・酸素・ヘリウム）の変換 |
| 冷却塔・復水器 | 温排水 → 水、蒸気 → 水 |

- 熱交換器の対向流の判定（入口の位置で自動判定）は今の仕組み（`counterflow()`）を再利用する。
- `docs/counterflow-heat-exchanger.md`の式（ε-NTU）は使わなくなる。

### 4.7 C 蒸気・タービン・原子炉の外への流れ

- 蒸気は等級で区別する。
  - 普通の蒸気`steam`（高温・常圧）：Createのボイラー、`BoilerSteamOutput`。
  - 高圧蒸気（高温・高圧、新しい流体）：横置き管束ボイラー、RBMK。
  - 過熱蒸気（超高温・高圧、新しい流体）：管束ボイラーの超加熱。
- 二段蒸気タービン（`TurbineThermal`・`SteamTurbineBlockEntity`ほか）：
  - 出力：蒸気1 mBあたりの出力が等級で決まる。
  - 応力（摩耗）：高い等級ほど大きい。タービンの中だけで計算する。
  - 出口の水：普通の水として出す。
- 削除：`SteamMain`、`SteamThermal`、`WaterPhase`の二相計算。
- RBMK（`nuclear/ReactorCore`・`ReactorPhysics`）と核融合炉（`fusion/`）の内部温度は残す。入口の冷却水・出口の蒸気は等級の流体でやりとりする。

### 4.8 D 後片付け

- 使われなくなったクラス・mixin・設定・翻訳・テクスチャ・試験を削除する。
- ディスプレイリンクの項目（`compat/display/MachineReadings.KEYS`）から、温度・圧力・配管の項目をまとめて外す。項目は番号で保存されているため、並びが変わる。本格運用前として受け入れ、レポートに書く。
- 削除前に各クラスの利用箇所を確認する。`grep`で参照がなくなったことを確かめてから消す。

### 4.9 E 文書・Ponder・説明書・JEI

- Ponder：
  - 圧力・温度・凍結の説明（`pipe_pressure`、`pressure_basics`、`pressure_valves`、`tank_materials`、加熱・冷却の各レッスン）を、等級と変換装置の説明に作り直す。
  - 製造系統のPonder 27本の説明から、K・°C・barの数値をなくす。翻訳には°C・Kを含む文が約170、barを含む文が約110ある。
- 説明書（`tools/generate_manual.py`）：
  - 620 K・720 Kなどの数値を、等級と熱源の段階の言葉に直す。
  - 第9章（熱・配管・安全）を「配管とタンクは等級と耐食で選ぶ」に書き直す。
- JEI：
  - レシピの条件を熱源の段階と等級で表示する。
  - 流体の温度のツールチップ（`PlantProcessCategory`）は等級の表示にする。
- 文書：`docs/`の温度・圧力の文書を新しいルールに書き直し、物理の文書（`passive-heat-transfer*.md`、`pressure-rupture.md`、`fluid-temperature*.md`など）は削除するか、要点だけに置き換える。`README.md`のリンクも直す。

## 5. 検証

- ビルド：`sh gradlew --no-daemon -q compileJava`
- GameTest全体：`sh gradlew --no-daemon -q runGameTestServer`
  - 既知の失敗が1件ある：`VesselFoulingGameTests.aFouledProcessStopsThenResumesAfterOneManualBrush`（1018e16以降、この作業とは無関係）。
- クラスを絞ったGameTest：

```bash
sh gradlew --no-daemon -q -I docs/quality/compact-centrifuge/verification.gradle -DtestDir=<空のフォルダー> -DtestClasses=ClassA,ClassB runGameTestServer
```

- 検証スクリプト：`python3 tools/validate_translations.py`、`python3 tools/validate_ponder_language.py`、`python3 tools/validate_plant_tutorials.py`
- 生成スクリプトは、再実行しても差分が出ないことを確認する。
- 実際の画面（Ponderの撮影と全シーンの読み込み）：Xvfbとソフトウェア描画で、次の初期化スクリプトを`-I`で渡して`runClient`する。

```bash
Xvfb :97 -screen 0 1280x800x24 &
DISPLAY=:97 LIBGL_ALWAYS_SOFTWARE=1 sh gradlew --no-daemon -q -I <下のスクリプト> -Dseek=3000 runClient
```

ログに`CLIENT QUALITY PASSED`が出れば、全Ponderシーンが開けている。撮影は`run/screenshots/quality-<言語>-<アイテム>@<番号>.png`に出る。

```groovy
allprojects {
    afterEvaluate {
        tasks.named('runClient').configure {
            doFirst {
                def options = new File(project.projectDir, 'run/options.txt')
                def selectedLanguage = System.getProperty('qualityLanguage', 'ja_jp')
                if (options.exists()) {
                    def lines = options.readLines('UTF-8')
                    lines.removeAll { it.startsWith('lang:') }
                    lines.add('lang:' + selectedLanguage)
                    options.setText(lines.join('\n') + '\n', 'UTF-8')
                }
            }
        }
        extensions.getByName('neoForge').runs.named('client') {
            programArgument '--width'
            programArgument '1280'
            programArgument '--height'
            programArgument '800'
            systemProperty 'createplant.clientQualityCheck', 'true'
            systemProperty 'createplant.clientQualityLanguage', System.getProperty('qualityLanguage','ja_jp')
            systemProperty 'createplant.clientQualityScenes', ''
            // 撮影するPonder：アイテムIDと、そのアイテムのシーン番号
            systemProperty 'createplant.clientQualityTargets', 'tube_piped_tank@0'
            systemProperty 'createplant.clientQualitySeek.0', System.getProperty('seek','2000')
            systemProperty 'createplant.clientQualityCaptureDelay', '80'
        }
    }
}
```

## 6. 注意点（これまでの作業で分かったこと）

- **配管の振る舞いの付け外し**：Createの配管に振る舞いを足す処理は、`BlockEntityBehaviourEvent`で行っている。削除したクラスがほかの振る舞いの付与も兼ねていることがある（`PipeWearBehaviour`はボイラー蒸気の受け渡しも付けていた）。消す前に、その付与の役割を確認する。
- **製造系統のPonder**：`tools/generate_manufacturing_ponder.py`は、すべての製造系統のNBTを書き直す（バイトが変わる）。また`helium_cold_box.nbt`を消してしまう。実行後は、変更対象以外のNBTと`helium_cold_box.nbt`を`git checkout`で戻す。
- **フッ化物の配管**：ウラン六フッ化物などのフッ化物は、ニッケル合金管でしか運べない。Createの銅パイプで組んだ試験・Ponderは、配管を合わせる必要がある。
- **水と蒸気の特例**：`PhaseTank`は水と蒸気をフィルターを通さずに入れる。等級の判定でもこの特例に注意する（A2でなくす）。
- **ディスプレイリンクの項目**：番号で保存されている。途中の段階では値を返さないだけにし、削除はD段階でまとめて行う。
- **加熱した流体の入手経路**：工業用加熱器の廃止で、一時的に入手経路が消えたことがある。流体を消す・置き換えるときは、`SurvivalReachabilityGameTests`で経路を確認する。

## 7. 完了の条件

- 1章の決定事項がすべて実装され、プレイヤーに見える温度・圧力の数値（K・°C・bar）が、原子炉・核融合炉の内部表示以外にない。
- 4章で削除と書いたクラス・mixin・設定・ブロックへの参照がない。
- GameTest全体が既知の失敗以外で通り、検証スクリプトが合格し、実際の画面で全Ponderシーンが開ける。
- 説明書・Ponder・JEI・文書が新しいルールで書かれている。
- 段階ごとの完了レポートと、計画の進捗表が更新されている。
