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

## 追加（取得・置く場所の選択）

Createのメカニカルアームと同じく、アームを持って右クリックで取得する場所と置く場所を選ぶようにした。クライアントの `ArmPointSelection` が選択と枠表示、`IndustrialArmItem.place` が設置時に `ArmPoints` パケットを送り、サーバーが `IndustrialArmBlockEntity.setPoints` で範囲と到達を確かめて記録する。右クリックの横取りは `ArmPoints.rightClick`（両側、スニーク時は除く）。ベルト・デポ加工は取得する場所だけで行うように変え（以前は範囲内を自動で探していた）、置く場所があれば完成品を運ぶ `CARRY` 段階を追加。ジグの材料は、取得する場所があればそこからだけ取る。GameTest: 選んだデポで加工しチェストへ運ぶ、選んでいないデポは加工しない・届かない場所は外す、既存のデポ試験を場所選択つきに更新。Ponderに選択の操作と運搬を追加（`docs/quality/industrial-arm/arm-points.png`）。実際のクライアントでの右クリック操作は未確認（Ponderの撮影とサーバー側の試験のみ）。

## 追加（レシピの自動選択）

ジグのレシピをレンチで選ぶ必要があったのを、周りのアームの工具と取得する場所の材料から自動で選ぶようにした（`AssemblyJigBlockEntity.autoPick`、`auto` フラグ、`IndustrialArmBlockEntity.addSources`）。材料の種類が多いレシピを優先する。レンチは固定、最後の次は自動。`setRecipe` は固定扱いなので既存のGameTestとPonderはそのまま。試験 `anAutomaticJigMakesWhatTheArmsMaterialsAllow`。

## 修正（不具合6件と選択の不具合）・追加（鉄板のメモリ、専用Ponder）

- 二重計上: 大きいチェストの両半分を選ぶと同じ中身を2回数え、コア100個で完成していた。`fetch` と自動選択で入れ物を同一性で1回だけ数え、全数を取れなければ戻す。試験 `bothHalvesOfALargeChestAreCountedOnce`。
- 自動選択の停止: 届かないアームの材料でレシピを選んでいた。工程ごとに工具を持ち届くアームを要求（`reachesStep`）。
- 枠の修理後に再開しない: 後のステージのブロックが立ったままだと置き直そうとして止まっていた。立っているブロックの工程は済みのまま残し、グリッパーは立っていれば材料を戻して済みにする。試験 `aLaterStageBlockStillStandingIsNotPlacedAgain`。
- スプレーで材料・液体が消える: 液体を材料より先に確かめ、中断時は材料を入れ物へ、液体をタンクへ戻す。試験 `jigSprayChecksFluidFirstAndGivesBackWhatItTook`、`sprayWithoutFluidKeepsItsMaterialAndAStoppedArmLetsGo`。
- ほかの設備を止める: 材料待ちのジグで止まらずベルト・デポ加工へ進む。回転0のアームはベルトのアイテムを放す。
- 読み込みで製品が変わる: 進捗はレシピが完全に一致するときだけ引き継ぐ。
- 選択が反映されない: クライアントは設置パケットより先に選択パケットを送るため、サーバーでブロックエンティティが無く捨てられていた。設置まで預かる（`ArmPoints.WAITING`）。試験 `marksSentBeforeTheArmIsPlacedReachIt`（FakePlayer）。実クライアントでの右クリック操作は未確認。
- 磁気コアメモリを鉄板（`create:iron_sheet`）を敷く `lay` 工程から作るようにし、コア144個・エナメル線25本・縁のはんだ付けを続ける。
- 専用Ponder `magnetic_core_memory`（磁気コアメモリ、フェライトコア、エナメル線、ジグ、配線ヘッドから開く）。部品の作り方、鉄板、コア、線、はんだ付け、完成の順。アームのPonderの配線場面は移した。撮影 `docs/quality/industrial-arm/memory-ponder.png`。

## 変更（工具は溶接トーチだけ、線通しは素のアーム2台）

要望: 複雑な作業は2台のアームで、溶接以外の工程は何も付けない産業用アームで行う。工具は溶接トーチだけにする（選択肢Cで確認）。

- `ArmTool` を `HAND`（何も付けない）と `WELDING_TORCH` の2つにし、グリッパー・レーザーカッター・スプレーノズル・精密配線ヘッドのアイテム、モデル、テクスチャ、クラフトレシピ、翻訳を削除。レーザーとスプレーのベルト用レシピ、液体（アームとジグのタンク、`fluid`、引き落としと返却）、切断・吹き付けの軌道と描画も削除。
- レシピの `tool` は `hand`（省略時）か `welding_torch`。素のアームの加工は `core_set`・`wire_thread` が必要で、トーチは溶接だけ。`helpers` を追加。保存済みのレシピが廃止した工具を使っていればジグは自動に戻る。
- 線通し（`helpers: 1`）: 工程を取ったアームが材料を取る前に補助の素のアームを探し、見つかればその `ASSIST` 状態のアームを毎tick線の先（`Trajectory.runEnd`）へ動かす。補助が消えたら線を返して工程を戻す。速さは2台の遅い方。補助は保存され、開き直しても続く。
- 自動選択は、補助つきの工程に届く素のアームが2台あるときだけそのレシピを選ぶ。
- 磁気コアメモリ: 素のアームが鉄板を敷き、コア144個を並べ、素のアーム2台でエナメル線25本を通し、溶接トーチではんだ付け。ラインのGameTestは素のアーム2台と溶接トーチ2台。
- Ponder: アームの場面は素のアームで組み、溶接の前にトーチを付ける。磁気コアメモリの場面は線通しの前に2台目の素のアームが現れ、線の先を押さえる（`docs/quality/industrial-arm/memory-ponder.png`）。
- GameTest: `threadingTakesASecondBareArmThatHoldsTheWire`（1台では線を取らない、2台目で開始し補助が面の端を押さえる、補助を壊すと線が戻る）、自動選択の試験を「素のアーム1台では鉄骨、2台目でメモリ」に変更。液体の試験2件は削除。
