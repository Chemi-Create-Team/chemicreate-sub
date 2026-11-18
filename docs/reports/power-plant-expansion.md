# 実寸の発電所補機・燃料サイクル 実装報告

2026-11-07。実装コミット：`6913df50219a243e11766fba6592cfb5a344630f`。

自然通風冷却塔、復水器冷却水箱、水位・圧力計測とPID、既存門型クレーンによる核融合部品交換、再処理・MOX・ガラス固化を追加した。実機写真と寸法資料を基に、1ブロック＝1 mへ丸めてモデル化した。ゲーム内の加工時間、燃料配合、部品寿命はゲーム用の設定であり、実機の設計・運転計算ではない。

## 実寸と資料

| 設備 | 採用寸法 | 根拠・外観 |
| --- | --- | --- |
| 自然通風冷却塔 | 67×67×92 m、喉径約37 m、頂部約45 m | [Willington公式記録](https://her.derbyshire.gov.uk/Monument/MDR14200)の218/122/145/300 ft。[実機写真](https://derelictmisc.org.uk/industrial/willington/images/DSC_0021.JPG)を基に、双曲面外殻・斜柱・開放吸気口・充填材・散水部を作成 |
| PIDラック・弁 | 高さ2 m、幅約0.6 mのラック。弁は配管上の実寸部分モデル | 計装の役割は[DOEハンドブック](https://www.energy.gov/documents/doe-hdbk-1013-92-vol2)を参照。計器、ノブ、ダイアフラム、弁軸、指針を分離 |
| 核融合保守 | 既存可変寸法門型クレーン。屋根ハッチ4×3 m | [ITER遠隔保守](https://www.iter.org/machine/supporting-systems/remote-handling)、[カセット搬入試験](https://www.iter.org/mag/7/inserting-ship-bottle)。吊荷はブランケット約1×1.4×0.5 m、ダイバータ約3.5×2.1×0.8 m |
| パルス抽出塔 | 塔身約1 m、足場込み3×3×12 m | [Hanfordパルス塔資料](https://www.osti.gov/servlets/purl/4121193)。塔体・脈動部・分離室・足場・4出口を表現 |
| ガラス固化炉・容器 | 炉7×4×4 m、容器高さ3 m・直径0.625 m | [DOE DWPF資料](https://stage.energy.gov/srs/articles/fact-sheet-defense-waste-processing-facility)、[DOE/SRR実機写真](https://www.flickr.com/photos/51009184@N06/8057327350)。炉枠、耐火部、電極、投入管、注湯部をモデル化 |

16 px/mの独自アセットを生成しており、写真そのものをテクスチャへ貼ってはいない。丸め寸法や代表機種の採用であり、特定の発電所全体を完全複製したものではない。詳細な組立と接続は[設計・操作記録](../power-plant-expansion.md)と新規5シーンのPonderを参照。

## 動作と計算

### 冷却塔・復水器

- 設計放熱338 MW。水の熱容量4.18 J/(mB·K)、温度差10 Kでは約404,307 mB/tを循環する。
- 湿球温度にStull近似、アプローチ5 K。バイオーム・雨天から湿度を推定する。外気が高温・多湿になると能力が低下し、冷水限界より低温へは冷却しない。
- 放熱の80%を蒸発潜熱2,257 J/mBとして水量へ反映。定格補給量の目安は約5,990 mB/t。蒸発端数と累積値も保存する。
- 温水・冷水・補給水を独立した各16,000,000 mBの槽へ保持する。北8入口、南8出口、東補給口は標準の流体ハンドラー。全体の処理上限は1,048,576 mB/t。
- LPタービンの30 °C復水と、冷却塔循環水は別の系統。3×3×7 mの冷却水箱がLPケーシングの半径方向へ接続し、軸は動力用に空ける。
- 復水器に未放熱の熱を最大4 GJ保持し、冷却水へ渡す。満杯なら蒸気を消費せず停止し、冷却復旧で再開する。冷却水の加熱上限28 °C。
- この循環系で338 MWに達するには、例えば冷水18 °C以下・温水28 °C・必要流量が要る。20 °C、相対湿度50%なら冷水限界約18.7 °C、放熱は約314 MWへ下がる。338 MWを全気象条件で保証する設定ではない。

### PIDと計器

計器→標準Display Link→2段PIDラック→アナログレッドストーン→既存流量調節器のアクチュエータ、という構成。独自GUI・専用信号ケーブルは追加していない。水位は0–100%、圧力は10/50/100/250 barレンジで正規化する。ノブで設定値/P/Ti/Td/作用方向/自動手動を操作する。

測定値微分・積分飽和防止・自動手動の追従を実装。40 tick測定断なら出力0。弁は0–15信号で0–262,144 mB/t、既存手動設定は従来の範囲を維持する。流量の同一tick予算を保存し、再ロードしても予算をリセットしない。概要は4行以内のゴーグル、詳細は標準Display Linkで読む。

### 核融合部品の交換

ダイバータカセットも燃焼条件へ追加。ブランケット24,000、ダイバータ16,000の定格相当秒の寿命を持たせ、異常停止・クエンチで追加損傷を与える。寿命切れでもアイテムを無言で削除しない。

プラズマ停止・磁場1%未満・ブランケット100 °C以下でのみ部品を入出力できる。既存クレーンの核融合保守モードは、屋根ハッチへ到達し、残寿命75%以下の部品を交換する。降下最大15 m。新品と使用済み品の搬出空きを確認してから交換する。途中でロードした場合は古い作業参照を再利用せず、在庫を重複させない。

既存ワールドの核融合炉は屋根中央12ブロックをハッチへ交換し、ダイバータカセットを追加する必要がある。

### 再処理・MOX・ガラス固化

使用済み燃料→既存Sawによるせん断→既存化学設備の硝酸溶解→パルス抽出塔→U/Pu回収→Mixer/Press/焼結/組立→既存燃料系へ戻す。被覆片も分離する。30%TBP抽出溶媒には既存化学設備で作れる原料からのレシピを追加し、回収溶媒を循環する。直接使用済み燃料からUだけを得る旧再処理レシピは置き換えた。

抽出塔は32 RPM以上、400 tick/バッチ。溶解液4,000 mB+溶媒1,000 mB→U溶液3,500/Pu溶液100/廃液400/回収溶媒1,000 mB。4つの出力を独立したノズルへ出す。MOX混合はPuO₂1個+劣化UO₂7個→粉末8個というゲーム単位。保存・搬送・取り出し時にMOXをウラン燃料へ変換しない。

固化炉はCEEの96 Ω電極負荷。12 kVで1.5 MW、熱容量2.5 MJ/K、放熱50 W/K、1,150 °C以上で処理する。廃液1,600 mB+ガラスフリット4個+空容器1個→固化容器1個、1,200 tick。出力閉塞・材料不足時には材料を消費しない。容器は設置時3ブロックの高さを占める。

## 主要ファイル

| 役割 | 実装 |
| --- | --- |
| 登録・能力 | `registry/CEPlantSystems.java`、`CEFuelCycle.java`、`CEFusion.java` |
| 冷却塔 | `block/CoolingTowerStructure.java`、`block/entity/CoolingTowerBlockEntity.java`、`thermal/CoolingTowerPhysics.java` |
| 復水熱・冷却水 | `thermal/TurbineThermal.java`、`block/entity/CondenserWaterboxBlockEntity.java`、独立した冷却水口 |
| 計装 | `thermal/PidLoop.java`、`block/entity/ProcessControlBlockEntity.java`、`compat/display/PidDisplayTarget.java`、`MachineReadings.java` |
| 交換・損耗 | `FusionReactorControllerBlockEntity.java`、`CraneDriveBlockEntity.java`、`client/CraneRenderer.java` |
| 燃料サイクル | `FuelCycleStructure.java`、`FuelCycleMachineBlockEntity.java`、`recipe/FuelCycleRecipe.java`、`compat/electro/VitrificationMelterDevice.java` |
| アセット・説明 | `tools/generate_plant_systems.py`、`ponder/PlantSystemsPonderScenes.java`、関連JSON・NBT・PNG |
| 検証 | `gametest/PowerPlantGameTests.java`、`client/PowerPlantClientCheck.java`（明示指定時のみ） |

Javaの相対パスは`src/main/java/net/createplant/`から。レシピのU/Pu/廃液収率、アイテムの同位体組成、加工時間は実機の物量を再現しない。

## 検証データ

Minecraft 1.21.1 / NeoForge 21.1.219 / Create 6.0.10（解決コミット`ac0c444d9828da3453ae8cc65338e8de063286fb`）/ Temurin Java 21.0.13+9。Linuxの隔離環境。描画はRTX 4060で確認した。

| 確認 | 結果・範囲 |
| --- | --- |
| 最終`build` | 合格。配布JAR生成、通常のcheckも完了 |
| 全体回帰の初回 | 395件中392件合格、3件失敗。下記の修正後、該当試験を含む回帰を再実行 |
| 最終`runPowerPlantRegressionGameTestServer` | **27件すべて合格**。新規10件、クラフト、サバイバル到達性、流水耐性、既存Fusion試験を含む |
| 実寸冷却塔 | 実際の67×67×92構造を形成。2,000回の内部処理＝100秒相当、毎回338 MW、累積33.8 GJのアサーションに合格 |
| 水収支 | 入水+補給＝返送+蒸発+残量。高温多湿時停止、保存後再検証、外殻撤去による停止も合格 |
| 復水器 | 有限熱容量による停止、熱量が冷却水へ等量移動、復旧後再開を確認 |
| 実際のCreate配管 | 2台の128 RPMポンプと標準パイプで冷却水8,000 mBを搬送。温水搬出と全量保存を確認。動力源は試験用Creative Motor |
| PID | Createタンクの実測値を標準Display Linkで受信。弁の全開・閉、測定断、積分飽和回復、16段階信号の収束、同一tick流量予算の保存を確認 |
| 抽出塔 | 廃液口満杯で消費なし。保存した半バッチから1回だけ完了。4出力と回収溶媒の量が一致 |
| 固化炉 | 電圧の応答、材料一括消費、容器1個生成、温度保存を確認。試験は炉体を保存データで予熱し、常温から約32分の昇温は実時間で試していない |
| 核融合クレーン | 実際の走行・昇降を経て、両部品の残寿命が新品値24,000/16,000へ回復 |
| Ponder言語・構造 | 検証スクリプト合格。実クライアントで112シーンの見出し・共有日本語と追加5シーンの描画を検証。塔とクレーンの画面切れを修正 |
| 実ゲーム外観 | 冷却塔、抽出・固化設備、制御盤、核融合保守クレーンを実ワールドへ配置してMCPで画像を保存 |

初回回帰で見つけた3件は、同じクラフト配列の衝突、サバイバル解析での既存核分裂工程の不足、薄い計器が流水で流される問題。別配列化、実際の鉱石・遠心分離・核分裂後の使用済み燃料・HF電解の到達経路追加、計器の材質設定で修正した。さらに実配管確認で復水器口が外殻に塞がれていたため、左右の独立ポートへ変更して検証した。

全395件を修正後にもう一度実行したわけではない。最終検証は変更した機能と初回失敗を含む27件の選択回帰であり、件数を合算して「現HEADの全件合格」とは扱わない。

## 制約・未確認事項

- **今回の変更を含む、核融合炉から冷却塔までの400 MW閉ループ長時間稼働は未実施。** 冷却塔の338 MW試験は内部処理を連続呼出しした要素試験であり、100秒の実時間運転や実配管全系試験ではない。実際の配管は復水器の小流量試験で別に確認した。
- 既存の`runFusionPlantGameTestServer`は今回再実行していない。従来の試験プラントには新しい復水器冷却水回路がないため、有限復水熱を追加した本版では、冷却塔と配管を足す必要がある。旧版の400 MW合格報告を本版へ流用しない。
- D₂/T₂/液体Heのサバイバル生産は元から未完であり、今回追加していない。到達性試験の核融合ブートストラップ除外は維持した。核分裂→MOX→廃液固化は通常到達性の検査対象とした。
- 実際の閉ループ圧力・水位の同時PID調整、複数炉の長時間運転、塔多数時のTPS/描画負荷は未測定。構造検証は1回2,048セルに分割し、未ロード時停止・変更通知で無効化するが、性能向上を計測したという意味ではない。
- 冷却塔の全設置範囲のチャンクがロードされている必要がある。勝手にチャンクをロードしない。
- 実クライアントの検証完了ログと画像保存後、終了処理で`libEGL_nvidia.so.0`のSIGSEGV（終了134）が2回発生した。`runClient`タスク自体を正常終了扱いにはしていない。Java側の新機能・翻訳・描画検証は完了し、サーバー回帰とbuildは正常終了している。外部CEEの`duplex_wire_segment`モデル欠落、音声デバイス未接続も環境ログにある。
- 機器は実機の代表寸法を丸めたもの。化学レシピ、放射性物質の核種ごとの挙動、実機全体の遠隔搬送建屋までは再現していない。

## 再実行と成果物

```bash
JAVA_HOME=/path/to/jdk21 JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4 \
  bash gradlew --max-workers=2 build runPowerPlantRegressionGameTestServer
python3 tools/validate_ponder_language.py
python3 tools/validate_plant_tutorials.py
```

この隔離環境はPID/スレッド上限512。テストサーバーと実クライアントの同時起動を避け、CPU数とGradleワーカー数を制限して再現する。クライアントは`mc-debug bash gradlew --no-daemon --max-workers=2 runClient`でGPUを使用する。

`/workspace/outputs/`へ以下を保存した（各8 MiB未満）：

- `create-heavy-industry-power-plant-0.1.0.jar`（4,734,960 bytes）
- SHA-256：`44e29798209f1c1c75190c79b7559f6fdbef701466cbc20b16b0790926dfc43c`
- `power-plant-cooling-tower.png`、`power-plant-equipment.png`、`power-plant-vitrification.png`、`power-plant-pid-controls.png`、`power-plant-remote-maintenance.png`
- `power-plant-final-verification.log`、`power-plant-full-verification.log`、`power-plant-build-final.log`、Ponderクライアントログ
- 本報告書のコピー`power-plant-report.md`

先行してmainへ届いた文書コミット`5882a64b`を取り込み、その上に実装を保存した。履歴や他メンバーの試験報告を上書きしていない。
