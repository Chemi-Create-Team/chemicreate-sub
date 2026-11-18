# 温度・圧力等級化 B1：流体の種類による状態

2026-11-14。仕様書4.4に基づく段階別記録。実装コミット：`efd8b0e1`。

## 変更

`FluidThermal.kelvin` は `FluidHazards` の公称温度を返す。`at` は渡されたスタックを変更せずそのまま返し、`tracked` はfalse。`mix` は同じ流体・同じ成分の量を足すだけで温度を平均しない。既存の放射性物質の履歴・保存時刻の扱いは維持した。

`fluid_temperature` と `specific_thermal_energy` のデータコンポーネント登録を削除。PassiveHeat・PassiveTank・WorldLiquidTemperature、温度の輸送だけを担うCreate・ホース・CDG用mixinを削除した。タンクの容量・受入等級・連結・分割・稼働中の槽の制御位置維持はGradeTankMixin・GradeConnectivityMixinに残し、構造変更の通知はFluidTopologyMixinで継続する。

CDGのエンジン、設置・運搬バレル、携帯容器、スパウトは元の流体形式を使う。運搬バレルが過容量のときに負の搬入量を返して内容物を削除することを防ぐ、従来の容量ガードだけを残した。気候・天候・時刻による配管とタンクの熱交換、関連設定を廃止した。

温度・熱保存だけを検査していた試験は削除し、満杯直前の搬入、同じ槽の2面への接続、分岐・ループ、タンクの各幅の形成・内容量保持など有効な回帰試験はNativeFluidInventoryGameTestsへ残した。Ponderの計器説明も現在の等級・流量表示へ合わせた。

## 検証

- `build` 成功。
- FluidGrade・NativeFluidInventory・TankGrade・GasPipe・FlowControl・DieselIntegration・WorldLiquid・WorldLiquidTransfer：41件すべて合格。
- 水は旧温度指定の有無にかかわらず同じ公称温度。量300 mB＋受入500 mBは800 mBで、入力2スタックは変更されない。
- タンクの残り容量17 mBは、2つの接続面があっても合計17 mBだけ受け入れる。
- 温度指定がないCDG燃料も元の燃料性能・回転数・燃焼率を保ち、保存・携帯・運搬後も有限の量を保持。
- 日英中翻訳、140 Ponderの本文、96追加Ponderの構造と製造27系統の検証を合格。
- NVIDIA RTX 4060で全140 Ponderをコンパイル、タンク材質・ガス配管を実描画し `CLIENT QUALITY PASSED`。結果は `docs/quality/thermal-grades/b1-client-result.json`。終了処理の既知NVIDIA EGL SIGSEGV（終了134）は継続しており、runClient全体の成功とはしていない。

## 保存データと後続作業

削除された温度・熱エネルギーの成分を含む旧保存スタックは、そのまま読み込めなくなる。仕様書どおり移行処理は追加しない。

槽・熱交換器・蒸気設備にまだ利用箇所があるPhaseTank、WaterPhase、FluidPhase、PhaseChange、ThermalMathなどは、B2・B3・Cで利用側を等級へ更新してから不要部分を削除する。今回は流体スタックへの温度・エネルギーの保存と輸送を除去した段階であり、熱処理・反応・蒸気設備の最終仕様の検証は各段階で行う。
