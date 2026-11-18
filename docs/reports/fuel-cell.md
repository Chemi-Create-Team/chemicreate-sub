# 燃料電池（A4）レポート

## 基準と実装コミット

- 作業ブランチ：`main`
- 作業開始時の基準コミット：`584eaa0`
- 実装コミット：`ebe7cd1`

## 依頼と決定事項

- 依頼：A4（燃料電池）。水素と酸素から CEE の電力と水を作る。電極にニッケルを使う。
- ユーザーの決定：1ブロックを並べる形。1ブロックの出力の目安は2 kW。

## 実装内容

- ブロック `fuel_cell`（`block/FuelCellBlock.java`）：
  - CEE の `SimpleElectricalDeviceBlock`。向きは水平4方向。
  - 上面に2本の端子：ノード0（赤い碍子、正面から見て左）が＋、ノード1（黒い碍子）が－。端子の名前を翻訳キーで表示する。
- ブロックエンティティ（`block/entity/FuelCellBlockEntity.java`）：
  - 水素・酸素・水のタンク（各4,000 mB）。水素と酸素はどの面からも入り、水はどの面からも出る。ほかの流体は受け付けない。
  - 計算：
    - 電流＝（400 V － 端子電圧）÷ 20 Ω。
    - 水素の消費（mB/tick）＝ 400 V × 電流 ÷ 20 ÷ 15 J/mB。酸素はその半分、水は1.5倍（80 °C）。
    - 1 mB未満の端数は次の tick に持ち越す（1/3 の和が丸め誤差で失われないよう、1e-9 の余裕を見て切り捨てる）。
  - 回路から外れる条件：水素か酸素が空、水が満杯、逆流（回路の電圧が400 Vを超え、電流が逆に流れ込もうとした）。逆流のときは20 tick外れてから再び試す。
  - ゴーグル：状態、出力（kW・V・A）、水素・酸素・水の量。状態の同期は、変わったときと20 tickごと。
  - 回路計算から2 tick以上呼ばれていなければ「回路につながっていません」と表示する。
- CEE の機器（`compat/electro/FuelCellDevice.java`）：
  - 燃料があれば `voltageSourceWithResistance` で内部抵抗付きの電圧源になり、計算後に端子電圧を受け取る。
  - CEE のこのメソッドは引数の順が（抵抗, 電圧）で、2つ目のノードの電位を上げる。CEE のクリエイティブ電池のバイトコードで確かめ、（ノード1, ノード0）の順で渡してノード0を＋にした。
- 登録（`registry/CEFuelCell.java`、`ElectroDevices.FUEL_CELL`）、クリエイティブタブ、ツルハシのタグ。
- アセット（`tools/generate_fuel_cell.py`）：
  - ニッケル板と黒いガスケットのスタック、ステンレスの端板とタイロッド、真鍮の銘板、赤・黒の碍子と銅の端子の16pxテクスチャ。
  - モデル、ブロックステート、ルートテーブル、レシピ（ステンレス鋼板4・銅板1・ニッケル3・液体パイプ1）、翻訳（英・日・中）。
- Ponder「燃料電池で発電する」（`tools/generate_fuel_cell_ponder.py`、5手順）。
- JEI：新しい分類「燃料電池」（水素100 mB・酸素50 mB → 水150 mB、1.5 kJ）。
- 文書：新規 `docs/fuel-cell.md`。`docs/electric-power.md`、`INDUSTRIAL_SYSTEMS.md`、`PONDER_GUIDE.md` を更新した。

## テスト

| 確認 | 結果 |
| --- | --- |
| `bash gradlew build runGameTestServer` | 292件すべて合格（新規2件） |
| `bash gradlew -PwithJei=false compileJava` | 成功 |
| CIの検証スクリプト4本 | 合格 |
| 実クライアント（日本語） | Ponder全94場面の題名が合格。「燃料電池で発電する」を撮影して目視した |

- `CircuitGameTests.aFuelCellDrivesTheHeaterElementAndLetsGoOfAHigherVoltage`：燃料電池と工業用加熱器の発熱体（10 Ω）を銅線でつなぐと、端子電圧＋133 V・約1.78 kW。水素が減り、水ができる。
- `FuelCellGameTests.fuelCellBurnsHydrogenAndOxygenIntoWaterWithTheCurrent`：流体の出し入れ、電線なしで燃料を使わないこと、200 V で3 tick（2 kW、水素40・酸素20 → 水60 mB、80 °C）、450 V で切り離すこと。
- 最初の版では引数の順を（電圧, 抵抗）と思って渡しており、実回路のテストで端子電圧が−0.49 Vになって見つけた。

## 未検証・制約

- 燃料電池どうしの直列・並列、オルタネーターと同じ回路での運転は試していない。
- 逆流の判定は端子電圧で行う。交流の回路（CEE のクリエイティブ電池の交流設定など）では試していない。
- スタックの温度や劣化は扱わない。水はいつも80 °Cで出る。
- 実ワールドに置いたときの見た目は、Ponder の画面で確認した。

## 次回の注意

- CEE で電源を作るときは、`voltageSourceWithResistance(a, b, 抵抗, 電圧)` の引数の順と、2つ目のノードが＋になることに注意する。
- CEE は、ワールドに電線のノードが1つもないときは、どの機器も計算しない（`supplied` が呼ばれない）。
