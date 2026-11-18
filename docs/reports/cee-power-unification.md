# 電気設備のCEEへの一本化 レポート

## 基準と実装コミット

- 作業ブランチ：`main`
- 作業開始時の基準コミット：`511acdc`
- 実装コミット：`0a9a15b`

## 依頼と決定事項

- 依頼：電気を使う設備の電力を、FEではなくCEE（Create: Electro Energetics 1.1.4）の電力に統一する。
- ユーザーの決定：FEは使わず、CEEに一本化する。工業用加熱器の燃料との併用は残す。

## 調査結果（変更前）

- 実際にFEを受け取っていたのは、工業用加熱器（下面のFE入力）と鉢の電解槽（どの鉢にもFE入力）の2つだけだった。
- 反応容器・分離器・蒸留塔・冷却器などの制御部は、FEを受け取らず、回転だけで動いていた。
  - `ProcessRecipe`の`fePerTick`は、どこからも使われていなかった。
  - 文書の「制御部にFEを供給」「電力倍率」「FEバッファ」は、実装と食い違っていた。
- 溶融塩電解炉は、前回の作業ですでにCEEに直接つないでいた。

## 実装内容

### 工業用加熱器（`thermal/ThermalEquipment.java`、`block/IndustrialHeaterBlock.java`、`compat/electro/IndustrialHeaterDevice.java`）

- FEの蓄電（`EnergyStorage`・`energy()`）と、下面のFE入力を削除した。
- 駆動部をCEEの設備にした（`ElectricalDeviceBlock`）。
  - 端子は正面の燃料口の両脇に2個。
  - モデルにも、鋼の支柱と真鍮の端子を追加した。
- 発熱体は10 Ωの抵抗で、電力は V²/10 W。300 Vで9 kW。
- 受けた電力量は、加熱1回分（5 tick分の加熱能力、1区画あたり100 kJ、`electricStore()`）まで蓄える。
  - 最初は200 kJ固定にしたが、3区画の加熱器が1回に要る熱量（最大300 kJ）に足りず、GameTestで目標温度に届かなかったため、区画数に比例させた。
- 次のときは回路から外れ、電流が流れない（`wantsPower`）。
  - 蓄えが満ちている。
  - 運転していない：未完成、32 RPM未満、レッドストーン停止、安全停止。
- 熱の使い方：
  - 蓄えた電力量を先に使い、不足分を燃料で補う。燃料との併用は従来どおり。
  - 電力量は J のまま扱う。旧実装の 1 FE = 10 J の換算はなくした。
- 保存：`ElectricalJ`（J）。旧キー`FE`・`LastFE`は削除した。
- ゴーグル：電力（kW・V）、蓄えた電力量（kJ）、燃料の残量と消費量。

### 鉢の電解槽（`basin/BasinElectrolysis.java`、`block/ElectrolyzerLidBlock.java`、`compat/electro/ElectrolyzerLidDevice.java`）

- 鉢のFE入力（`energyPort`、Basinへの`EnergyStorage`登録）を削除した。
- 蓋をCEEの設備にした。
  - 端子は蓋の上面の対角2か所。モデルに鉄の支柱と真鍮の端子を追加した。
  - 並びの最初の蓋が負荷を持つ。ほかの蓋の端子は、その蓋の端子に橋渡しする（`bridge`）。このため、どの蓋につないでもよい。
- 負荷：極板1枚が160 Ωの電解槽で、並び全体では並列の 160/極板数 Ω。400 Vで極板1枚あたり1 kW。
- 極板1枚が1 tickに50 J（1 kW）を使う。生成量（水3 mB → 水素2 mB・酸素1 mB、フッ化水素2 mB → 水素1 mB・フッ素1 mB）は変えていない。
- 電力量の蓄えは40 kJ。次のときは負荷を外す：
  - 極板がない。
  - 蓄えに極板1 tick分の空きがない。
  - 制御側の鉢にレッドストーン信号がある。
- 保存：`Joules`・`Watts`。旧キー`Energy`は削除した。

### FEの撤去

- 削除：
  - `block/entity/MachineEnergyStorage.java`
  - `LiveMachinePorts.energy`
  - 反応設備のエネルギー能力の登録
  - `ProcessRecipe.fePerTick`（全レシピ定義の引数も削除）
- 表示リンク（`compat/display/MachineReadings.java`）：
  - `energy`：エネルギー能力の読み取りをやめ、工業用加熱器の蓄えた電力量（kJ）を返す。
  - 未使用だった`generation`（FE/t）：同じ位置のまま、`electric_power`（工業用加熱器の電力、kW）に置き換えた。Createはリンクの選択を位置で保存するため、並びは変えていない。
- JEI：電解のメモを kJ 表示に変更した（100 tickで5 kJ）。
- 翻訳（英語・日本語）：
  - FEの記述をすべて、CEEの端子と電力（kW・V・kJ）の記述に変更した。
  - 生成スクリプト（`tools/generate_heating_assets.py`、`generate_thermal_control_assets.py`、`generate_heating_tutorials.py`、`revise_equipment_ponder.py`、`generate_uranium_assets.py`、`generate_electrolyzer_ponder.py`）の文言も揃えた。
- Ponder：
  - 電解槽の「電力」の場面で、蓋の端子を示すようにした。字幕は160 Ω・400 V・1 kWの説明にした。
  - 工業用加熱器の手順3の字幕は、正面の端子をCEEにつなぐ説明にした。

### CEEとの接続の注意

- 回路の計算はCEEの別スレッドで行われる。結果（電圧の2乗）は volatile の欄に置き、設備のtickで積算する。
- 結果が2 tickより古いときは、電力0として扱う。
- 時刻の比較は`suppliedAt >= now - 2`の形にした。初期値`Long.MIN_VALUE`との引き算で桁あふれしないようにするため。

## 計算方法

| 設備 | 抵抗 | 電力 | 1 tickの電力量 |
| --- | --- | --- | --- |
| 工業用加熱器 | 10 Ω | V²/10 | V²/200 J（1区画あたり100 kJまで蓄える） |
| 鉢の電解槽 | 160/極板数 Ω | 極板数 × V²/160 | 極板1枚あたり50 Jで1回の電解 |
| 溶融塩電解炉 | 2 Ω | V²/2 | 変更なし |

## テスト

| 確認 | 結果 |
| --- | --- |
| `bash gradlew build runGameTestServer` | 273件すべて合格 |
| `bash gradlew -PwithJei=false runGameTestServer` | 273件すべて合格 |
| CIの検証スクリプト4本 | 合格 |
| 実クライアント | Ponder全90シーンが合格。加熱器の画面確認（7操作）も合格。ワールドに置いた加熱器の正面の端子と、Ponderの電解槽の蓋の端子を撮影して目視した |

- テストの書き換え：FEを直接入れていたテストを、CEEの回路の結果（電圧の2乗）を渡す形に変えた。
  - `HeatingGameTests`・`ThermalControlGameTests`・`ThermalStabilityGameTests`：
    - 加熱器が使った電力量と、流体が受け取った熱量（m c ΔT）の一致を、J単位で厳密に確認する。
    - 水1,000 mBを20 °C → 100 °Cで334,400 J。
  - `ModularPortsGameTests`：極板1枚の負荷が160 Ωであること。400 Vで1 tickに1回、電解すること。
  - `SharedMachineCapabilityGameTests`：エネルギー能力の共有確認を削除した（能力がなくなったため）。
- 新規 `CircuitGameTests`：CEEのクリエイティブ電池と銅線で実際に配線する。
  - 工業用加熱器：300 Vで8 kWを超える電力が流れ、蓄えが増える。
  - 鉢の電解槽：蓋につなぎ、400 Vで900 Wを超える電力が流れ、水素ができる。

## 未検証・制約

- 電解槽のPonderの字幕は、日本語の折り返しで「160 / Ω」「1 / kW」の位置で改行される。日本語字幕の折り返しは、以前から保留にしている問題と同じ。
- 実際のCEEの発電機（オルタネーター）からの給電は、ゲーム内では試していない。GameTestで、クリエイティブ電池からの実際の配線だけを確認した。

- 加熱器の発熱体（10 Ω）と蓄え（1区画あたり100 kJ）、電解槽の160 Ω・40 kJは、今回決めたゲーム用の値。実在の設備の値ではない。
- 1区画の加熱能力（20 kJ/tick = 400 kW）は、CEEの通常の発電量よりかなり大きい。電力だけで全能力を出すには、約2,000 Vが要る。
  - 通常の電線は1,500 Vまで。
  - 燃料との併用か、低い流量での運転を想定している。
- 鉢の電解槽のゴーグルは、見ている鉢の状態を表示する。そのため、制御側でない鉢では電力の状態が実際と異なることがある。項目名（Electrolysis・Plates）も英語の直書き。これは今回より前からの不整合で、今回は変更していない。
- 既存ワールドへの影響：
  - 加熱器に蓄えていたFE、電解槽の`Energy`は読み込まれない（本格運用前のため、変換処理は置かない）。
  - 表示リンクの`generation`を選んでいた場合は、`electric_power`の表示になる。
- `DEVELOPMENT_CHEMISTRY.md`の後半には、現在は登録されていない旧設備の説明がFEの数値とともに残っている：
  - 花崗岩の電解槽、高圧反応槽（小型・大型）、オストワルト法の専用設備。
  - 今回は、冒頭の電力の方針と共通の記述だけを直した。旧設備の節の扱いは、別途判断が要る。
- `docs/thermal-equipment-validation.md`・`docs/thermal-low-flow-stability.md`は、当時の検証記録としてFEの記述のまま残した。

## 次回の注意

- 電気を使う設備を追加するときは、`ElectricalDeviceBlock`（またはCEEの`SimpleElectricalDeviceBlock`）と`SimpleElectricalDevice`を使う。
  - `preTick`で抵抗を置き、`postTick`で電圧の2乗を受け取り、設備のtickで積算する。
  - 電力が要らない間は抵抗を外す。
- FEの能力やFEの数値を新しく追加しない。
