# 配管温度計・配管圧力計 実装レポート

## 基準と実装コミット

- 作業ブランチ：`work`
- 作業開始時の基準コミット：`1ee853c`（実装後に `9ecf45f` へリベース。取り込んだRBMKの変更とは、`ElectrolysisClient.java` と言語ファイルの別の行が重なっただけで、競合はなかった）
- 実装コミット：`b28983a54023f4c92c81cc057ae5bbcd03567487`

## 実装内容

- 配管の途中に入れる「配管温度計」（`pipe_thermometer`）と「配管圧力計」（`pipe_pressure_gauge`）を追加した。どちらも `AxisPipeBlock` を基にしていて、X・Y・Zのすべての軸に置ける。流体は軸方向にそのまま通す。
- 見た目はCreateの速度計・応力計を流用した。
  - 筐体、計器面、ボタン、針：Createの `create:block/gauge` と、Createの計器面・針のモデルを直接参照する（テクスチャは複製していない）。
  - 配管の接合部：筐体のシャフト穴の面に、Createの流体バルブ（`create:block/fluid_valve`）の銅フランジを付けた。
  - 文字盤の色：Createの速度計の文字盤を基に、9段階の色だけを差し替えた。温度計は青→赤、圧力計は淡いクリーム→真鍮→暗赤。2枚を1枚の16×16テクスチャ（`textures/block/pipe_gauge/faces.png`）にまとめた。
  - 本体モデル（`models/block/pipe_gauge/body.json`）は2種類で共通。
- 計器面は、配管の軸と直交する水平な面のうち、覆われていない面に表示する。Createの `GaugeBlock.shouldRenderHeadOnFace` と同じ考え方。

## 計算方法・値の取り方

| 場面 | 温度計 | 圧力計 |
| --- | --- | --- |
| ポンプ送液（Createの流体ネットワーク） | `ThermalRoutes.thermalContact` が配管位置ごとに求める流体温度（経路の放熱を反映） | `PressureSections.observe` が求める区間の平均圧力 |
| ガス配管（`GasNetworks`） | 圧力の均し後、計器がつながっているガス配管の気体の温度 | 圧力の均し後、そのガス配管の圧力 |

- ガス配管では、軸方向に並んだ計器を飛ばして次の配管・容器へつなぐ。計器自体は気体を持たない。
- 針の振れ幅（1/4回転）：温度計は0～1000 ℃の線形。圧力計は0～300 barの対数（`log10(1+bar)/log10(301)`）。上限を超えると1.1まで振れる。
- 最後の観測から40 tick（2秒）流れがなければ「未測定」に戻り、針は0へ戻る。
- 同期：温度0.1 K、または圧力0.01 bar（1000 Pa）を超えて変化したときと、測定・未測定が切り替わったときだけ送る。間隔は最短10 tick。針はクライアント側で補間する。保存データは持たない。
- ゴーグル：名前と値の2行。ディスプレイリンク：既存の「配管温度」（`pipe_temperature`）と「区間圧力」（`section_pressure`）で読める。新しい項目は追加していない。
- レシピ：コンパス＋Createの流体パイプに加えて、温度計は銅インゴット、圧力計は鉄板（いずれも不定形）。

## 主要ファイル

- `block/PipeGaugeBlock.java`、`block/entity/PipeGaugeBlockEntity.java`、`client/PipeGaugeRenderer.java`
- `registry/CETransport.java`（登録）、`ElectrolysisClient.java`（描画の登録）、`registry/CECreativeModeTabs.java`
- `thermal/ThermalRoutes.java`、`thermal/PressureSections.java`（液体の観測）、`transport/GasNetworks.java`（ガス配管の通過と観測）
- `compat/display/MachineReadings.java`（ディスプレイリンク）
- `client/PipeGaugeClientCheck.java`、`client/ClientQualityCheck.java`（撮影シーン）
- アセット：`blockstates/pipe_*.json`、`models/block/pipe_gauge/*`、`models/item/pipe_*.json`、`textures/block/pipe_gauge/faces.png`、言語ファイル（英・日・中）
- データ：レシピ、ルートテーブル、`mineable/pickaxe`

## テスト結果

- `gradlew build` 成功（FluidPhase 25件、HaberBoschProcess 1019件、ReactorPhysicsTest 44件）。
- 専用サーバーGameTest 240件すべて成功。追加した2件：
  - `pipeGaugesReadPumpedLiquidAndClearWhenTheFlowStops`：340 Kの水をポンプで送ると、温度計が±1 K以内、圧力計が送り先タンクの圧力±0.05 bar以内を示し、ディスプレイリンクから両方を読める。止めると両方が未測定に戻る。
  - `aPressureGaugeInAGasPipePassesTheGasAndReadsTheLine`：ガス配管の途中に圧力計を入れても両側のタンクの圧力が等しくなり、計器がその圧力を示す。
- 実画面：Xvfbとソフトウェア描画（Mesa）で `runClient` を起動し、`-Dcreateplant.clientQualityCheck=true -Dcreateplant.pipeGaugeClientCheck=true` で5方向から撮影した。比較列、ガス配管、縦配管、手持ちのアイテム・ホットバーを確認した。針は撮影用に、温度計に873.15 K（600 ℃）、圧力計に12 barの値を与えて振らせた。
- 撮影時のログで、計器のモデル・テクスチャの欠落はなかった。最初の8×8の文字盤テクスチャはアトラスのミップマップ段数を下げていたため、16×16の1枚にまとめて解消した。

## 警報接点（追加）

- 計器の面をレンチで長押しし、Create標準のValue Settingsで設定する。行で条件（超えたら出力／下回ったら出力）を、列でしきい値を選ぶ。保存は1つの整数（`行 ×（段数＋1）＋段`）。
- しきい値の段：温度計は0～1000 °Cを10 °C刻み（100段）。圧力計は0～2 barを0.1、～10 barを0.5、～50 barを2、～300 barを10 bar刻み（81段）。既定は温度計が100 °C超、圧力計が10 bar超。値を持たない古い計器は、読み込み時に既定へ戻す。
- 出力：条件を満たしている間、配管の軸以外の4面へ強さ15の弱い信号を出す。未測定のときは出さない。BlockStateは変えず（変えると配管の接続が再計算されるため）、状態が変わったときだけ隣接ブロックへ通知する。
- 表示：ゴーグルに警報の行を追加した（名前・値・警報の3行）。ディスプレイリンクはご指示どおり温度・圧力だけで、警報の状態やしきい値は表示しない。
- GameTest `alarmContactSignalsAboveOrBelowItsThreshold`：67 °Cの水を送り、「50 °C超で出力」で隣のランプが点灯すること、配管の軸方向は0・それ以外は15であること、「50 °C未満で出力」に切り替えると消灯することを確認した。
- Ponderに2場面（設定、出力）を追加し、計器の上のレッドストーンランプが点灯する様子を示した。実画面で撮影して確認した。

## Ponder

- 「配管の温度と圧力を測る」（`ponder/PipeGaugePonderScenes.java`、配置データは `ponder/plant/pipe_gauges.nbt`、生成スクリプトは `tools/generate_pipe_gauge_ponder.py`）。配管温度計・配管圧力計のどちらからも開ける。
- 6つの場面で構成する。設置（どの向きでも置け、流体はそのまま通る）、流れているときの読み方、目盛の範囲、流れが止まったときの未測定、ガス配管での使い方、ゴーグルとディスプレイリンク。
- Ponderの中では流体を流せないため、ポンプを回すのと同時に `PipeGaugeBlockEntity.preview()` で値を与えて針を振らせている（80 ℃・3 bar・ガス8 bar）。
- 実画面の品質チェック（`-Dcreateplant.clientQualityCheck=true -Dcreateplant.clientQualityTargets=pipe_thermometer`）で、75シーンのタイトルと日本語訳の検証に合格した。表示は場面ごとに撮影して確認した。
- 字幕の「℃」は、Ponderのフォントで崩れて表示されたため、「°C」を使った。既存の燃料プールのPonderなど、ほかにも「℃」を含む文字列がある（未修正）。

## 未検証・制約

- 実際のGPU環境での見た目、遠景のミップマップ、光源の当たり方は未確認（今回はソフトウェア描画のみ）。
- 実ゲームでのゴーグル表示の見た目と、ディスプレイリンクの表示は未確認（読み取りの処理はGameTestで確認済み）。
- コンパレーター出力はない。
- 配管は内容物を持たないので、液体の流れが止まった配管の圧力は測れない（未測定と表示する）。
- 圧力は区間の平均なので、同じ区間（ポンプ・調圧弁で区切られた範囲）の中ではどこに置いても同じ値になる。
- ガス配管の気体の温度は、配管が気体を持っているときだけ測れる。

## 次回の注意

- `MachineReadings.KEYS` の `flow_rate`（正味流量）は、読み取る処理がない古い項目。ディスプレイリンクは項目の番号で保存されるので、キーは消さずに残した。
- `textures/block/basin_plain.png` が16×14で、ブロックアトラス全体のミップマップ段数を1に下げている（ログ警告：`limits mip level from 4 to 1`）。今回の作業範囲外なので未修正。遠景のテクスチャ品質に影響する。
- Createの公式Maven（`maven.createmod.net`）の証明書の期限切れが続いている。検証では、前回と同じくModrinthのCreate 6.0.10からローカルで依存を解決した。
