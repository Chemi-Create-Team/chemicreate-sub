# 引き継ぎ：門型クレーン（原子炉保守・採掘）

実装コミット：`c491f5d`（ブランチ `work`）。設計の正本は [docs/gantry-crane.md](../gantry-crane.md)。前提の原子炉は [rbmk-control-rods.md](rbmk-control-rods.md)。

## ユーザーと決めた範囲

| 論点 | 決定 |
| --- | --- |
| 操作 | 自動巡回。レッドストーン信号で開始・停止、作業切替はレンチのValue Settings |
| 構造 | 門型：4本脚＋Createガントリーシャフトの走行レール2本 |
| 採掘 | 範囲を層ごとに自動採掘して格納 |
| 燃料取扱い | ペレット交換に加え、制御棒（吸収体）の交換も含む |

## 実装内容

- ブロック：クレーンの脚、門型クレーン駆動部。アイテム：使用済み制御棒吸収体。
- 主要ファイル：`block/CraneStructure.java`（構造判定）、`block/entity/CraneDriveBlockEntity.java`（移動・作業計画・採掘・燃料交換・吸収体交換・格納庫・同期）、`block/CraneDriveBlock.java`（ドリル取付）、`client/CraneRenderer.java`（桁・トロリー・ロープ・吊り具・ドリル）、`ponder/CranePonderScenes.java`、`gametest/CraneGameTests.java`、`tools/generate_crane_assets.py`。
- 原子炉側の変更：制御棒吸収体の消耗（`ReactorPhysics.ABSORBER_BURNUP`、`ControlRodChannelBlockEntity.wear`）、クレーン用API（`ReactorCore.fuelTops/rodWithdrawn/rodAbsorbers/renewRod`、`ReactorCores.controllersIn`）。制御棒チャネルのゴーグル3行目は「この棒の挿入率・消耗」に変わった。

## テスト結果

- `./gradlew build`：成功。
- `./gradlew runGameTestServer`：全221件成功。追加4件：
  - `craneFormsAndReportsAMissingLeg`：形成（長さ7・間隔5・脚3）、脚を欠くと停止し理由が「legs」
  - `quarryMinesItsSpanIntoTheStore`：ドリル1個だけ取付、2層の土と石を掘って格納、水は残る、外から石は入らない、採掘物は取り出せてシミュレーションは不変
  - `reactorServiceSwapsSpentPellets`：下の炉心の使用済みペレットを格納庫へ回収
  - `reactorServiceRenewsWornAbsorbers`：消耗50%の引き抜いた制御棒の吸収体2個を交換し、消耗0、使用済み2個

## 未検証・制約

- **クライアント未起動。** 桁・トロリー・ロープ・吊り具・ドリルの描画位置と向き、補間、Ponderの再生は実画面で確認していない。
- 桁・トロリーは描画だけで当たり判定は無い。吊り具の経路上の障害物は判定しない（レールより上に物を置かない前提）。
- 採掘はワールドの下限まで続き、範囲内の炉心や建物も掘る（中身のあるブロックは除外）。深さの上限設定は無い。
- 採掘のドロップは鉄のツルハシ相当（シルクタッチ・幸運なし）。
- 走行レールは駆動部側の1本の回転だけを見る。もう1本は構造部材。
- 構造の再検証は40 tickごとの定期走査（脚・ガントリーシャフトに変更通知が無いため）。
- 多数台設置時のMSPTは未計測。
