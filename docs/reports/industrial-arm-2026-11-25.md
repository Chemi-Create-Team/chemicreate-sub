# 産業用アームと組立ジグ（引き継ぎ）

仕様と操作は[産業用アームと組立ジグ](../industrial-arm.md)。ユーザー指定（みけ）の「GUIなしのロボットアーム式3D組立システム」を実装した。当初別リポジトリへ誤って載せたため、このリポジトリへ移して実装した。

磁気コアメモリの通し運転と描画、停止時の材料処理、作業中の保存・再読込は[組立ラインのデバッグ報告](magnetic-core-memory-line-2026-11-25.md)で追試した。以下は初期実装時の記録。

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

## 修正（アームの動きと工具の位置）

ユーザーから「アームが正しく動かない、アタッチメントの位置がずれる」と報告を受けて修正した。

- 工具と持ったブロックは、Createでは関節の変換の後に爪の座標系で回転・移動するが、逆の順番で掛けていたため、爪から離れた位置に描かれていた。Createの `ArmRenderer.renderSafe` の順番に合わせた。
- Createの `ArmAngleTarget` は肩から1.8125マスで打ち切るため、離れた工程では腕が伸び切ったまま止まって見えた。肩から上を2倍で描き、角度は縮尺を戻した目標点で求める。届かない工程は予約しない（`ArmPose.reaches`）。
- 材料はこれまで瞬時に取っていた。取り出した入れ物へ腕を伸ばす段階（`FETCH`）を加えた。保存したワールドでは移動段階として読み込む。
- Ponderで、置いた鉄ブロックが表示されず、グリッパーが何も持っていなかったのを直した。アームの位置を1マスジグへ寄せた。

確認: `ArmAssemblyGameTests`（角度の取得と到達範囲の試験を追加、6件）と `MagneticMemoryLineGameTests` が成功。Xvfbとソフトウェア描画の実クライアントでPonderを撮影し、腕がジグの各マスまで届くこと、爪にブロックをはさんで運ぶこと、ジグの上に鉄ブロックが並ぶことを確認した。溶接ビード・配線・レーザーの描画は撮影の解像度では細部を判別できず未確認。実ワールドでの通し運転も未確認。

## 追加（Createの色、ベルト作業、溶接の撮影）

- 色: 描画の黄色の着色をやめ、ブロックとアイテムのモデルはCreateの `create:block/mechanical_arm/block` と `.../item` をそのまま使う。独自のアーム用テクスチャとブロックモデルは削除した。
- ベルト: `IndustrialArmBlockEntity.findBeltJob` / `holdBeltItem` / `beltWork`。レシピは `ArmBeltRecipe`（`assembly/` で `input` を持つJSON）。取り合いは `CLAIMED`（WeakHashMap）で防ぐ。クライアントへは `shownPhase` で作業状態を送る（ベルト作業はジグ位置を持たないため）。Ponderの最後にデポで金床を溶接修理する場面を追加。
- 確認: `ArmAssemblyGameTests`（デポ上の欠けた金床が溶接されて少し欠けた金床になる試験を追加、7件）、`MagneticMemoryLineGameTests`、`SurvivalReachabilityGameTests` の11件が成功。実クライアントでPonderを撮影し、Createと同じ色のアーム、ジグの継ぎ目とデポの金床での溶接の火花を確認した（`docs/quality/industrial-arm/weld-jig.png`, `weld-depot.png`）。ベルト（デポ以外）での動作は同じ `TransportedItemStackHandlerBehaviour` を使うが、実機では未確認。溶接ビードの色の変化は、トーチの頭に隠れて撮影でははっきり確認できていない。

## 修正（腕の太さ）

肩から先を丸ごと2倍にしていたため腕が太すぎた。腕の2区間だけを長さ方向に2倍にし（`IndustrialArmRenderer.stretched`）、関節・爪・工具はCreateの大きさに戻した。手首から先が縮んだので、関節角はCreateの式を `ArmPose.angles` に移植して腕の長さだけを変えて解く（反射での角度読み取りは削除）。`theArmAimsWithCreatesAnglesWithinItsReach` で元の長さのときCreateの `ArmAngleTarget` と角度が一致することを確認。GameTest 11件成功、Ponderの撮影で細い腕がジグの継ぎ目とデポに届くことを確認。

## 修正（コアメモリを編む動き）

以前の配線は格子の上に直線を重ねて描き、交点の上に輪を載せるだけで、線がコアを通っていなかった。`Trajectory.grid` を作り直し、コアを置く工程→X線→Y線→センス線（対角線ジグザグ）の順にし、ヘッドがコアごとに輪の中心まで沈み間で持ち上がるようにした。軌道の各区間に種類（`Trajectory.kind`）を持たせ、描画はそれで色分けする。コアは45°傾けて立て、穴をX・Y・対角線のすべてが通る向きにした。作業時間は従来どおり120 tick（GameTestの時間に合わせたまま）。Ponderの最後に1マスの配線場面を追加（`docs/quality/industrial-arm/core-wiring.png`）。GameTest 12件成功。

## 修正（コアメモリの大きさ）

配線面を上面中央の4分の1（0.5×0.5マス）にし、コアを半径0.007マスのほこりほどの大きさ、線を0.0025マスの太さにした。小さくなった分、コアを12×12に増やした。

## 修正（評価報告の不具合3件）

`docs/reports/industrial-arm-evaluation-2026-11-26.md` の高優先度3件を修正した。

- 枠の撤去で完成品が出る: `AssemblyJigBlockEntity.checkPlaced` が毎tick置いたブロックを確かめ、消えた配置工程を未着手に戻し、後のステージの工程もやり直しにする。試験 `aRemovedFrameBlockTakesItsStepAndTheLaterWorkBack`。
- 届かない入れ物から材料を取る: `fetch` は上面に爪が届かない入れ物を、アイテムを抜く前に飛ばす。試験 `theArmLeavesMaterialItCannotReach`。磁気コアメモリのラインのGameTestは投入チェストが配線アームから届かない配置に頼っていたため、銅用チェストを配線アームのそばに分けた。プレイテスト用ワールドのZIPは古い配置のままなので、手順書に注意を追記した。
- デポのスプレー中に保存すると水が消える: 液体を加工完了時に引き落とすようにした。あわせて、開き直したアームが前の自分の「使用中」の印でアイテムを取れなくなる問題も直した（`claimedByOther`）。試験 `sprayFluidIsKeptWhenTheArmIsReloadedMidWork`。

## 追加（フェライトコアとエナメル線）

磁気コアメモリの部品が無く、配線が銅インゴット1個だった。アイテム4種（フェライト粉、未焼成のフェライトリング、フェライトコア、エナメル線、`CEArm`）とレシピ4つ（加熱ミキサー2、プレス、かまど）を追加。組立レシピの `consume` に個数（`{"id", "count"}`、`ArmRecipe.MAX_CONSUME`=1024）を持たせ、配線工程を `core_set`（コア144個）と `wire_thread`（エナメル線25本）の2ステージに分けた。アームは届く入れ物を合わせて数がそろったときだけ取り出し、1スタックを超える持ち物は個数を別に保存する（`heldCount`、ItemStackの保存形式は99個まで）。GameTest: 部品レシピの読み込み、コア144個の要求、ラインの全工程（部品チェストを配線アームのそばに配置）。プレイテスト用ワールドの樽には部品が無いため、手順書に注意を追記。
