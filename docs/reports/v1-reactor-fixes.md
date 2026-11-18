# 1.0監査の原子炉関係の修正

## 概要

`docs/reports/v1-overhaul-audit.md`で「報告のみ」とした原子炉関係の5件（`V1-AUD-N01`、`N02`、`N03`、`F01`、`C01`）を、ユーザーの指示（2026-11-07）で修正した。炉物理・制御・レシピ・アセットの仕様は変えず、壊れた保存値とパケットの読み込み、制御棒コントローラーの流水耐性だけを直した。

- 実装コミット：`bdd886cdc074177f8560e6323a306068544fc190`

## 直したもの

| ID | 場所 | 修正 |
| --- | --- | --- |
| N01 | `ReactorCore.read`（サーバー） | `Hazard`・`ScramPower`・`Level`・`Precursors`・`Iodine`・`Xenon`がNaN・無限大なら静止状態（中性子源レベル・0）へ戻し、炉をスクラムする。正常な保存ではスクラムしない |
| N01 | `FuelChannelBlockEntity.read` | `Burnt`が不正なら燃え尽きた扱い（`ROD_JOULES`）。新しい燃料を作らないため。`Specific`・`FuelKelvin`は20 °Cの水と燃料へ戻す。`ControlDepth`は0、`ControlWear`は上限（使用済みの吸収体）へ戻す。`Math.clamp`はNaNを通すので、有限値の検査を先に置いた |
| N01 | `PlugPhysicsSimulator.read` | 安全弁の`PlugRelief`と同期圧力`PlugPressure`を、有限の0以上に限る |
| N02 | `ReactorCore.read`（パケット） | 出力・反応度・ボイド率・温度・制御棒挿入率・漏えいを、有限で物理的な範囲に限る。制御棒と燃料列の配列を、制御棒の数と燃料列の数へ揃える（短いパケットで範囲外を読まない） |
| N02 | `ReactorControlConsoleBlockEntity.read` | 表示値を有限の範囲に限る。`SelectedIndex`を制御棒マップの範囲へ収める（範囲外なら`rodMap`の読み出しで落ちていた） |
| N02 | `ReactorChartRecorderBlockEntity`、`FuelPoolBlockEntity` | 記録計のペン位置と燃料プールの温度を有限値に限る |
| N03 | `CENuclear.ROD_CONTROLLER` | `forceSolidOn()`を加えた。形・当たり判定・設置条件は変えていない。流水試験の原子炉・核融合炉・クレーン除外を外し、全部の非立方ブロックを検査する |
| F01 | `FusionControlConsoleBlockEntity.read` | 出力・真空度・コイル温度・励磁・プラズマ温度を有限の範囲に限る |
| C01 | `CraneDriveBlockEntity.read` | 不正な`U`・`V`・`Depth`・`Reach`を、架台の起点（u = 0、v = 1、深さ0、燃料棒の長さ）へ戻す。サーバーは読み込み後に構造を確かめ直し、範囲へ丸める（既存の処理）。クライアントは同期された構造の範囲に収める |

共通の処理は`nuclear/Finite.java`にまとめた（範囲内に丸め、非有限なら代わりの値を返す。配列は長さも揃える）。

## 復旧の方針

炉の保存が壊れていた場合は、炉物理を静止状態から始め、スクラムする。運転員がスクラムを解除するまで出力は出ない。燃料は「燃え尽きた」側に倒すので、壊れた保存から新しい燃料が増えることはない。毒物（ヨウ素・キセノン）は0に戻るため、壊れた保存の直後だけ毒物の効果が消える。保存が壊れたとき限りの動きで、正常な保存・既存ワールドには影響しない。

## テスト

修正の前に`NuclearSavedValueGameTests`（3件）を追加し、流水試験の除外を外して、4件が失敗することを確かめた（制御卓の表示、燃料チャンネル、クレーン、流水の`control_rod_controller`）。修正後は5件すべて合格した。

| 確認 | 結果 |
| --- | --- |
| `NuclearSavedValueGameTests`、`WaterResistanceGameTests`（修正前） | 4件失敗（意図どおり） |
| 同（修正後） | 5件すべて合格 |
| `bash gradlew build runGameTestServer` | 必須GameTest 368件すべて合格 |
| `bash gradlew build runGameTestServer -PwithJei=false` | 必須GameTest 368件すべて合格 |

原子炉の物理・制御の単体検査（ReactorPhysics、原子炉制御）と、既存のRBMK・燃料プール・核融合炉・クレーンのGameTestは、上の全件に含まれる。

## 未確認のこと

- 実クライアントでの表示は確かめていない。描画のコードは変えておらず、表示に渡る値の範囲だけを変えた。
- 制御棒コントローラーの見た目は変えていないため、撮影はしていない。

## 液体空気の削除（V1-AUD-014）

ユーザーの決定（2026-11-07）で、作り方のなかったクリエイティブ専用の液体空気を削除した。

- 実装コミット：`a488697e70f912cde42f2073d55c460c644ac478`
- 消したもの：
  - 流体`liquid_air`の登録とバケツ`liquid_air_bucket`。
  - 物性（`FluidPhase`）と危険性（`FluidHazards`）の登録、クリエイティブタブの項目。
  - ブロックステート、アイテムモデル、テクスチャ、英・日・中の翻訳3キー。
- 空気分離塔の原料口は、圧縮・冷却した空気だけを受け付ける。液体空気を数える処理（1 mBを空気約749 mBとする換算）をなくした。液化酸素の出口は変えていない。
- 文言の修正：
  - Ponder「空気分離塔」の手順1と、ゴーグルの口の説明から「液体空気」を外した。
  - Ponder「配管の選び方」の保冷配管の例は、液化酸素に替えた。
  - 生成スクリプト（`generate_distillation_assets.py`・`generate_distillation_tutorials.py`・`generate_pipe_material_ponder.py`・`generate_quality_assets.py`）も同じ文に揃えた。
- 試験：
  - 保冷タンク・凍傷の試験2件は、液化酸素（90 K）で同じことを確かめる形にした。普通のタンクでは沸点（90.19 K）まで温まる。
  - 分離塔の試験から、液体空気の換算の確認を外した。
  - 到達性試験の除外から液体空気のバケツを外し、流体とバケツが登録されていないことを確かめる判定を足した。
- 文書：`docs/distillation-plant.md`、`docs/pipes-and-tanks.md`、`docs/passive-heat-transfer.md`、`DEVELOPMENT_CHEMISTRY.md`。
- 既存ワールドへの影響：液体空気を入れたタンク・バケツは、読み込み時に中身が消える（クリエイティブでしか手に入らなかった）。

| 確認 | 結果 |
| --- | --- |
| 関係する試験（MaterialEquipment・Distillation・SurvivalReachability・CraftingRecipe） | 合格 |
| `bash gradlew build runGameTestServer` | 必須GameTest 369件すべて合格 |
| `bash gradlew build runGameTestServer -PwithJei=false` | 必須GameTest 369件すべて合格 |
| 検証スクリプト4本 | 合格 |
