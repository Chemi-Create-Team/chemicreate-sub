# 産業用アームと組立ジグ（引き継ぎ）

仕様と操作は[産業用アームと組立ジグ](../industrial-arm.md)。ユーザー指定（みけ）の「GUIなしのロボットアーム式3D組立システム」を実装した。当初別リポジトリへ誤って載せたため、このリポジトリへ移して実装した。

## 実装内容と主要ファイル

- 登録: `registry/CEArm`（ブロック2、工具5、完成品2、ブロックエンティティ2、能力、応力4 SU、レシピの再読込）。クリエイティブタブは設備・道具の末尾に追加。
- `arm/`: `ArmRecipe`（JSONとNBT）、`ArmRecipes`（`data/<ns>/assembly`）、`ArmTool`、`ArmPattern`、`Trajectory`（サーバーとクライアントで共有）、`ArmPose`（Createの `ArmAngleTarget` をリフレクションで利用）、`ArmEffects`。
- `block/AssemblyJigBlock`・`block/entity/AssemblyJigBlockEntity`、`block/IndustrialArmBlock`・`block/entity/IndustrialArmBlockEntity`、`item/ArmToolItem`。
- `client/AssemblyJigRenderer`（溶接ビード・配線とフェライトコア・レーザーの溝・吹き付けの膜）、`client/IndustrialArmRenderer`（Createの部分モデルと関節変換）。
- `ponder/ArmPonderScenes`（`plant/industrial_arm`、構造は `ponder/plant/industrial_arm.nbt`）。ゴーグルはアームとジグの両方。
- サンプルレシピ `welded_iron_beam`、`magnetic_core_memory`。クラフトレシピ、モデル、テクスチャ、翻訳（en/ja/zh）、`mineable/pickaxe` タグ、戦利品表を追加。

## 計算方法

- 予約は工程ごとの未着手/予約/完了。ステージは、前のステージの全工程が完了して初めて開く。アームは自分の工具で行える最寄りの工程を取る。
- 作業の進み = 回転速度 / 16 RPM を、レシピの `time` tickで割ったもの。軌道上の位置は進捗から `Trajectory.tip` で決まり、アームの狙いと描画が同じ軌道を使う。
- サーバーは先端の位置だけを同期し、クライアントが `ArmAngleTarget` で関節角を求め、`LerpedFloat` で追従させる。ジグの進捗は2 tickごとに同期し、クライアントが補間する。

## 検証

- `compileJava` と `runGameTestServer`（全774件）。`SurvivalReachabilityGameTests` が初回に、アームの完成品2種を入手不能と報告したため、組立レシピを到達性の計算に加えた。再実行で、この件と新規 `ArmAssemblyGameTests`（5件）、`CreativeCatalogGameTests` が通った。全件を修正後に再実行はしていない。
- `ArmAssemblyGameTests`: サンプルの読み込み、ステージの待機と最寄り優先と二重予約なし、アームのいない予約の返却、完成時の仮ブロック撤去と完成品、置く先が埋まっている場合に上書きしない。

## 未検証

- 描画は一度もゲームの画面で確認していない。溶接ビードの色、配線、レーザー、膜、アームの向きと工具の位置、Ponderの流れ、ゴーグルの表示は未確認。
- 実際のアームの探索・材料の取得・作業の通しは、回転を入れたゲーム内で未確認（GameTestはジグの予約と完成の論理のみ）。ホッパー等での工具の出し入れ、パイプでの液体の出入りも未確認。
- 性能は計測していない。アームは10 tickごとに5マス範囲を探索し、作業中は2 tickごとに同期する。
- Flywheelのインスタンス描画は使わない。シェーダーパックの経路は未確認。

## 制約・次回の注意

- アーム1台につき工具は1個。複数のアームの探索範囲は5マスの立方体。
- ジグの上のマスが埋まっているとグリッパーは待つ（上書きしない）。ジグが消すのは、ジグが置いたのと同じブロックだけ。
- スプレーの液体は工程の開始時に引き落とし、途中で中断すると戻らない。
- 実装コミット: `f6af120`。
