# ふた付き槽 完了レポート（ChatGPT引き継ぎ用）

- 実装コミットID：`616cd041f9204d10023c543a889a2afbbe3e406d`
- 対象：新ブロック「ふた付き槽」（`create_heavy_indusstry:lidded_vessel`）

## 実装内容

ふだんはふたの閉じたタンクで、レンチで上面を右クリックするとふたが開いて鉢のような中空になるブロック。高さ1段で、並べると2×2・3×3の1つの槽になる。

- 連結：Createのタンクの連結処理（`ConnectivityHandler`）と内容物の扱い（`FluidTankBlockEntity`）を流用。高さは1段に制限し（`getMaxLength`でY軸だけ1）、幅は1〜3。ブロックは`FluidTankBlock`を継承していないので、Createのボイラー・蒸気エンジン・窓は付かない。
- ふたの状態：全パーツのブロック状態`open`・`facing`（蝶番の側）に持たせ、コントローラーの値を全パーツに揃える。保存は通常のブロック状態なので、新しいNBTは増やしていない。
- 外壁：各パーツの`north`/`east`/`south`/`west`（その面が槽の外周か）を、連結のたびに設定し直す。外周の面にだけ壁があり、開いたときの当たり判定は鉢と同じ厚さ2pxの壁と床になる。閉じているときは1ブロック全体。
- レンチ：上面を右クリックするとふた全体が開閉する。側面を右クリックすると、蝶番の向きが時計回りに回る（ユーザー指定）。しゃがんでの右クリックはCreate標準の回収。
- 上面の配管：流体ハンドラーは、ふたが開いている間は上面だけ`null`を返す。開閉時には近隣のブロックに更新を通知するので、Createのパイプがつながったり外れたりする。側面はいつでもつながる。
- 開いている間：毎秒、気体はすべて、蒸気は大気圧を超える分だけ放出する（`PhaseTank.ventGas`／`PressureVesselBehaviour.relieve`）。放出したものは`ToxicGas.release`に渡すので、有毒なら雲になる。バケツなどで直接出し入れできる（`FluidHelper`）。
- 噴き出し：開ける瞬間の圧力が`BLOWOUT_PA`（大気圧＋0.5 bar）以上だと、破裂音・蒸気の音・雲の粒子を出し、槽の上3ブロックにいるエンティティを上へ押し上げる。押し上げる強さは`min(1.5, 0.3 + 余剰圧[Pa]/1e6)`。そのうえで上記の放出を行う。槽そのものは壊れない。
- 閉じている間：定格10 barの圧力容器（`PressureVesselBehaviour.tank(..., 10, false)`）。腐食性の流体による腐食、過圧時のひずみ、破裂は既存のタンクと同じ。外気との熱交換も既存のタンクと同じ`PassiveTank`。
- 共通処理の追加：`PhaseTank.unseal()`。中身がない密閉タンクは圧力0として扱われるため、開いた槽が空になったときに真空と表示されないよう、開いている間は毎秒これで大気に開放する。
- 描画：本体はブロックモデル（外壁の組み合わせ16通り）。ふたと中身は`LiddedVesselRenderer`がコントローラーから1回だけ描く。ふたは開口全体で1枚の板として、蝶番の辺を軸に0〜105°回転する。クライアントで`LerpedFloat`を使って補間するので、開閉の同期はブロック状態の変化だけで済む。
- 表示：ゴーグルでは、既存のタンクの表示（名前・量と温度・圧力）の名前の行に、ふたの開閉を付け足す。ディスプレイリンクの`status`は、ふたの開閉を返す。
- テクスチャ：反応槽・遠心分離機などのマルチブロックと共通の銀色の外装（`block/reactor/silver/`）を使う。側面は`shell`、ふたの上面と本体の底は`top`、壁の上端とふたの縁は`trim`、内側・床・ふたの裏・金具は`frame`。専用のテクスチャは持たない（ユーザー指定で、最初に描いた専用テクスチャから置き換えた）。
- レシピ：鉄板4・鉄のトラップドア・Createの鉢（形あり）。

## 主要ファイル

- `src/main/java/net/createplant/block/LiddedVesselBlock.java`
- `src/main/java/net/createplant/block/entity/LiddedVesselBlockEntity.java`
- `src/main/java/net/createplant/client/LiddedVesselRenderer.java`
- `src/main/java/net/createplant/ponder/LiddedVesselPonderScenes.java`（`ElectrolysisPonderPlugin`へ登録）
- `src/main/java/net/createplant/gametest/LiddedVesselGameTests.java`
- `src/main/java/net/createplant/thermal/PhaseTank.java`（`unseal()`を追加）
- 登録・配線：`registry/CETransport.java`、`registry/CECreativeModeTabs.java`、`ElectrolysisClient.java`、`block/entity/PlantFluidTankBlockEntity.java`（流体ハンドラーの登録）、`compat/display/MachineReadings.java`
- 生成スクリプト：`tools/generate_lidded_vessel.py`（モデル・ブロック状態・ルートテーブル・レシピ・ツルハシのタグ・名前）、`tools/generate_lidded_vessel_ponder.py`（Ponderの構造と字幕）
- 説明：`docs/pipes-and-tanks.md`

## テスト結果

- `./gradlew compileJava`：成功。
- GameTest（ふた付き槽の3件と、タンク・圧力・監査・Ponder一覧・サバイバル到達性・ディスプレイの関連テスト、計24件）：全件成功。
  - 3×3まで連結し、上に積んだものは別の槽になる。外周の面にだけ壁がある。角を壊すと分かれて、切れた面が外壁に戻る。
  - ふたは全体が一緒に開く。開くと上面のポートが消え、側面からは入れられる。当たり判定が中空になる。蝶番が全体で回る。閉じると上面がつながり直し、液体は残る。
  - 約5 barの窒素を入れて開けると気体が全部抜け、圧力が大気圧に戻り、槽は残る。
- GameTest全体：402件中400件が成功。失敗した2件は今回の変更が触れていない箇所で、`main`で同じく失敗するかは確かめていない。
  - `theRefiningEquipmentNeedsNoRefinedMetal`：`c:ingots/*`のタグ付きアイテムが10個ある（テストは8個を想定）。
  - `noBasinRecipeAsksForAGas`：TFMGの`tfmg:mixing/cooling_fluid`がエチレンを要求する。
- 銀色の外装への置き換え後にも、実クライアントでSeek 265・780を撮影し、閉じた状態と蝶番を東に回して開いた状態を確認した（`CLIENT QUALITY PASSED`）。
- 実クライアント（`-Dcreateplant.clientQualityCheck=true -Dcreateplant.clientQualityTargets=lidded_vessel`、日本語、Seek 265・425・500・780）：4回とも`CLIENT QUALITY PASSED: 113 scenes`。Ponderの画面で、閉じた状態、開いて水が見える状態、噴き出しの雲、蝶番を東に回して開いた状態を撮影し、目で確認した。

## 未検証・制約

- 通常のワールドでの手動操作は確認していない：レンチ・バケツ・パイプの付け外し、噴き出しで実際にプレイヤーが押し上げられるか、マルチプレイでの同期。
- ふたが動く範囲にブロックがあっても、開閉は止まらない。上面にパイプを付けたまま開けると、動いている途中のふたがパイプにめり込んで見える（見た目だけの問題）。
- 閉じるときに中にいるエンティティは、通常の「ブロックに埋まったとき」の押し出しに任せている。
- 開いている間の液体の蒸発（沸点未満）は扱っていない。既存の`PressureVesselBehaviour`と同じく、大気圧を超える蒸気だけを放出する。
- 定格10 bar・噴き出しの0.5 barは、ゲーム上の設定値。
- 作業中、`src/main/java/net/createplant/client/FusionPlaytestWorld.java`に自分の作業ではない未コミットの変更があったため、コミットには含めていない。

## 次回の注意

- 加圧反応（オートクレーブ）などの用途は未実装。使うときは設備ごとに特別扱いせず、`PhaseChange`などの共通処理か、レシピで実装する。
- `LiddedVesselBlockEntity`は、自分のブロック状態を`level.getBlockState`から読んでいる。設置中にキャッシュされたブロック状態が、連結の後で設置時の値に戻されるため。`getBlockState()`に戻すと、外壁の判定が壊れる。
- 連結のとき、Createは各パーツの幅を設定して通知してから、最後にコントローラーの幅を設定する。そのため、外壁はパーツ自身の`getWidth()`で計算している。
