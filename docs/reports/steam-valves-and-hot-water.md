# バルブで蒸気を止める・断熱設備の完全削除・熱い水から作る蒸気 レポート

## 基準と実装コミット

- 作業ブランチ：`main`
- 作業開始時の基準コミット：`55fda4e`
- 実装コミット：`a30db86`

## 依頼

1. バルブでボイラーの蒸気を止められるようにする。
2. 断熱タンク・断熱パイプの残りを完全に削除する。
3. 蒸気の作り方の3番（100 °C以上の水を密閉タンクで沸騰させる）を正式な作り方にする。
4. 古いPonderを直す。

## 実装内容

### 1. バルブ（`block/entity/BoilerSteamOutput.java`）

- ボイラーの蒸気の経路探索で、次のように扱う：
  - Createの流体バルブ：開いているとき（`ENABLED`）だけ、配管の向き（`FluidValveBlock.getPipeAxis`）に沿って通す。
  - 閉じたバルブ：蒸気を止める。タービンへの経路が切れるので、その配管は出口として数えない。
  - スマート流体パイプ：通す（フィルターは見ない）。
- バルブの開閉でブロックの状態が変わると、経路のキャッシュが作り直される（最長でも20 tick）。

### 2. 断熱設備の残りの削除

- 翻訳キー`transport.create_heavy_indusstry.material.insulated`を削除した。
- 生成スクリプト：
  - `generate_heating_tutorials.py`：断熱タンクの手順を削除した。
  - `generate_transport_assets.py`：表示文言から「断熱」を削除した。
- テストの書き換え：
  - 断熱の削除時に機械的にCreateの部品へ置き換わっていた箇所を、同じ働きの保冷タンク・保冷配管に戻した（`PassiveHeatGameTests`）。
  - テスト名の「Insulation」「Insulated」は「Cryogenic」に直した。
  - 保冷配管だけの経路のテスト`cryogenicLineLoss`を追加した（温度が変わらない）。
  - `TransportQualityGameTests`の「断熱管で高温の酸化混合物を運ぶ」テストは、Createの銅管に置き換わっていた。超耐圧パイプのテストに直した。
- 文書：
  - 現在の仕様の文書は、断熱の記述を保冷配管・保冷タンクに置き換えるか、削除した。
    - 対象：`passive-heat-transfer.md`、`radiator-and-pipe-temperature.md`、`thermal-equipment.md`、`machine-displays.md`、`gas-compressor.md`、`fluid-temperature.md`、`water-two-phase.md`、`counterflow-heat-exchanger.md`、`diesel-generators-integration.md`、`quality/transport-and-assets.md`
  - 過去の調査・検証の記録は、冒頭に「削除済み」の注記を付けた。中身は記録として残した。
    - 対象：`passive-heat-transfer-feasibility.md`、`passive-heat-transfer-validation.md`、`thermal-equipment-validation.md`、`specs/pressure-two-phase-v1.1.md`
  - 削除した断熱タンクの撮影画像`quality/passive-heat/insulated-tank.png`を削除した。

### 3. 熱い水から作る蒸気

- 仕組み（既存）：
  - 工業用加熱器で100 °C以上にした水を、密閉タンクへ入れる。
  - タンクの二相計算で沸騰し、上面から蒸気、下面から水が出る。
- 新規GameTest `HeatingGameTests.waterHeatedAbove100CBoilsInASealedTankAndGivesSteamFromTheTop`：
  - 加熱器で水1,000 mBを200 °Cにする。
  - 2段のCreateのタンクに入れると、上面から100 °Cを超える蒸気が取り出せる。
- 新規Ponder「熱い水から蒸気を作る」（`tools/generate_steam_from_hot_water_ponder.py`）。
  - 工業用加熱器の駆動部は、構造化されていない状態ではブロックエンティティの描画が無く表示されない。そのため、入口の配管に加熱器のアイコンを出す形にした。
  - 手順：加熱器、密閉タンク、上面からポンプでタービンへ、排気を戻す。
  - 工業用加熱器と高圧タービンの外装から開ける。
- 文書：
  - `docs/pipes-and-tanks.md`に「熱い水から作る蒸気」の節を追加した。
  - `INDUSTRIAL_SYSTEMS.md`の蒸気発電に、3つの作り方を追記した。
  - 圧力の目安（200 °Cで約15 bar）は、ゲームの水の物性式から計算した値。

### 4. 古いPonder

- 登録されていないPonderの翻訳キーを削除した（英語・日本語・中国語、各14件）。
  - `ponder.high_pressure_turbine_preview.*`、`ponder.high_pressure_turbine_multiblock.*`
  - 削除済みの「蒸気パイプ」の説明が残っていた。
- 使われているPonderの字幕に、削除済みの部品（蒸気パイプ・断熱・黒鋼管・鉄の流体パイプ）やFEの記述が残っていないことを確かめた。

## テスト

| 確認 | 結果 |
| --- | --- |
| `bash gradlew build runGameTestServer` | 285件すべて合格 |
| CIの検証スクリプト | 合格（Ponder 83本の見出しと字幕） |
| 実クライアント | Ponder全92シーンが合格。「熱い水から蒸気を作る」を撮影して目視した |

- 新規 `TurbineThermalGameTests.aClosedValveStopsBoilerSteamAndAnOpenOnePassesIt`：
  - 閉じたバルブでタービンへの経路が切れる。
  - 開くとつながる。
- バルブを実際に回転で開閉する操作は試していない。テストでは、バルブの状態を直接切り替えた。

## 未検証・制約

- `docs/quality/*.json`（過去のクライアント検査の記録）には、`insulated_pipe`の文字列が残っている。記録データのため変更していない。
- `docs/passive-heat-transfer.md`の「蒸気パイプと既存設備との境界」の節は、削除済みの蒸気パイプの説明のまま。今回の依頼の範囲外のため残した。
- スマート流体パイプのフィルターでは、ボイラーの蒸気を止められない。
