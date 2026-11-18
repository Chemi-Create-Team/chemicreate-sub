# 流体圧力の安定化・不具合修正レポート

## 基準と実装コミット

- 作業ブランチ：`work`
- 作業開始時の基準コミット：`fc3c67e`（実装後に `7bbf142` へリベース。取り込んだ2件はクレーンの外観のみで、圧力関連のファイルと重ならない）
- 実装コミット：`554c9ad9c7280010f9f1a1a594476f46422152af`

## 修正した不具合

| 不具合 | 原因 | 修正 |
| --- | --- | --- |
| Createタンクに溜めた圧縮ガスで安全弁が開かない | `PressureVesselBehaviour.relieve()` が容積を `getCapacity()` で求めていた。気体では貯蔵枠（Createタンクで実容積の16倍、その他256倍）を返すため、圧力を1/16以下に見積もって放出量が負になっていた | 容積を `FluidPhase.capacity()`（実容積）に統一する `volume(t)` を追加し、潜熱計算・放出・破裂の爆発規模・ゴーグルの相状態表示で使う |
| 放出しても気体の圧力が下がらない | `PhaseTank` は気体を保存圧力のまま保持し、取り出しても圧力を変えない設計（`docs/reports/gas-tank-pressure.md`） | 安全弁からの放出だけ `PhaseTank.ventGas()` で行う。残りの気体は占めていた体積のまま、質量に比例して圧力が下がる。通常の取り出しは従来どおり |
| 安全弁の上限が気体では約1/1000 | 上限を2000 mB/秒で数えていた。`CAPACITY_KG=2` は定義されているだけで使われていなかった | 上限を質量（2 kg/秒）で数える。一時保持タンクは、最も軽い水素2 kgが入る容量にした |
| 気体入りのCreateタンクを分割すると気体が消える・増える | Createの `ConnectivityHandler` は1ブロック8000 mBで分配する。圧縮ガスはこれを超えるため、コントローラーが貯蔵枠まで抱え込み、他のブロックにも8000 mBが配られていた（3ブロック・10.9 barから1ブロックを外すと、160000 mBになるべきところ136000 mB） | `ThermalConnectivityMixin` で分割前に気体を取り出し、分割後に全ブロックへ等分する。各ブロックは分割前の保存圧力を引き継ぐ。壊したブロックの分は、Createが液体で行うのと同じく失われる |
| 気体入りのタンクを結合すると気体が消える | Createは、元の大きさのままのコントローラーに各部の内容物を注いだ後、各部を全量排出する。気体は定格で受け入れが止まり、残りが失われていた | 結合時に `PhaseTank.absorbGas()` で全量を受け取り、圧力は質量加重平均にする（充填時と同じ規則） |
| 小型圧縮機が吸入側のほぼ真空から圧縮して自壊する（既存テスト `actualCreatePumpTransfersBetweenSmallCompressors` が失敗していた） | 吸入圧力がほぼ0のまま15 barへ1回で圧縮すると、断熱圧縮で吐出温度が最高使用温度1200 Kを大きく超える。熱損傷で壊れ、内容物が消えていた | 1回の圧縮で吐出温度が最高使用温度を超える場合は圧縮せず、`unsafe` で吸入圧力の上昇を待つ（`EquipmentSafety.exceeds()`） |
| `GasInflow.pressure` が古い値のまま残り得る | 流体ネットワークのMixinは、充填ごとに値を設定し、tickの終わりにだけ戻していた。例外時や入れ子の充填で、古い送り元圧力が使われ得た | 充填処理を `try/finally` で囲んで必ず戻す。tick終了時のリセットは不要になったので削除した |
| ポンプ損失熱の処理が動いていない | `PressureSections.plan()` の `specific` に一度も代入されず、`joulesPerMb` は常に0で、`output()` は常に入力をそのまま返していた。`maximum` も常に無限大だった | 使われていない `joulesPerMb`・`output()`・`maximum` と、損失熱が流体に入るという誤ったコメントを削除した。挙動は変わらない |
| 既存テスト `actualAcidFlowWarnsLosesPressureAndBreaksOnlyThePipeWhenEnabled` が失敗していた | 「既定設定では配管を壊さない」と確認していたが、`allowPipeBreakage` の既定値は `docs/pressure-rupture.md` のとおり `true` に変わっていた | テスト側で `false` を明示してから確認するよう修正した。実装は変更していない |

## 主要ファイル

- `thermal/PressureVesselBehaviour.java`：実容積の `volume(t)`、気体の安全弁放出、放出量が0以下なら何もしない
- `thermal/PhaseTank.java`：`ventGas()`、`absorbGas()`、`storedPressure()` を公開
- `mixin/ThermalConnectivityMixin.java`：タンク分割（`splitMultiAndInvalidate` のHEAD・RETURN）と結合（`tryToFormNewMultiOfWidth` の `IFluidTank.fill`、`@Local(name="tankAt")`）
- `block/entity/ReliefValveBlockEntity.java`：質量で数える放出上限
- `block/entity/GasCompressorBlockEntity.java`、`thermal/EquipmentSafety.java`：吐出温度による停止
- `mixin/PassiveFluidNetworkMixin.java`、`thermal/PressureSections.java`：`GasInflow` のリセットと未使用コードの削除
- `gametest/PressureGameTests.java`：GameTest 2件を追加
- `docs/pressure.md`、`docs/gas-compressor.md`：仕様の追記

## 計算方法

- 安全弁（圧縮ガス）：放出質量 = `min(上限, m × (1 − 設定圧力 / 現在圧力))`。放出後の保存圧力 = `P × 残量 / (残量 + 放出量)`
- 分割：iブロック目の取り分 = `⌊T(i+1)/n⌋ − ⌊Ti/n⌋`（T：総mB、n：分割前のブロック数）。端数は出ない
- 結合：圧力 = `(m₁P₁ + m₂P₂) / (m₁ + m₂)`

## テスト結果

- `gradlew build` 成功。FluidPhase 25件、HaberBoschProcess 1019件、ReactorPhysicsTest 43件が合格
- 専用サーバーGameTest（`runGameTestServer`）：236件すべて成功。作業前は234件中2件が失敗していた（上記の圧縮機・酸配管の2件）
- 追加したGameTest：
  - `reliefValveVentsCompressedGasDownToItsSetPressure`：10.9 barの窒素が5 barまで下がり、放出量が理論値±2 mBに収まり、弁が閉じる
  - `splittingAGasTankKeepsEachBlocksShareAndRejoinsWithoutLoss`：3ブロックから1ブロックを外すと残りが正確に2/3で、再結合後も圧力が変わらない
- 追加した2件を修正前のコードでも実行し、どちらも失敗することを確認した（安全弁は10.88 barのまま開かず、分割後の残量は136000 mB）
- 検証時、`maven.createmod.net` のTLS証明書が2026-11-03 07:23 UTCに失効していて取得できなかった。そのため、ModrinthのCreate 6.0.10と、その同梱依存（Flywheel 1.0.6、Ponder 1.0.82、Registrate 1.3.0+67、いずれも `build.gradle` と同じ版）を、作業用ディレクトリのローカルMavenからGradleのinitスクリプトで解決した。リポジトリには含めていない

## 未検証・制約

- 実クライアントでの目視（安全弁の白煙、タンクのゴーグル表示、タンクを壊したときの見た目）は未確認
- 大量設置時の性能は計測していない。追加処理は、タンクの分割・結合時と安全弁の放出時が中心で、毎tickの追加は圧縮機1台あたり温度の比較1回だけ
- 分割時に壊したブロックの取り分は、Createの液体と同じく失われ、パーティクルなどは出ない
- 保存圧力の設計（取り出しても圧力が変わらない）は維持した。安全弁からの放出だけが圧力を下げる

## 次回の注意（未対応の発見事項）

- `FluidPhase` の物性表に、二酸化硫黄・三酸化硫黄・フッ化水素・フッ素・六フッ化ウラン（濃縮・劣化を含む）がなく、窒素相当の値になっている。実物性を入れると、沸点300 K以上のものは `GasCompression.gas()` の対象外になり、タンク容量（貯蔵枠）が変わって既存ワールドの在庫に影響するため、今回は変更していない
- 反応塔・アンモニア分離器（`BedGas`）は内容物を流体として持たないため、安全弁を付けても放出できない
- `PhaseTank.port().getTankCapacity()` は気体でも実容積（mB）を返す。一方で量は最大16倍になるため、ディスプレイリンクなどで100%を超える表示になり得る
- `docs/pressure.md` の古い記述が残っている：配管の圧力は「両端の容器圧力の高い方」とあるが、実装は区間ごとの平均圧。「表示圧力は大気圧が下限、真空は扱わない」とあるが、実装は密閉した空容器の真空と外圧による圧壊を扱う
