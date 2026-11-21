# クアーリーのガントリーをFlywheelのインスタンスで描く

実装コミットは次の3つ。

- `ccbf375384695fb89300fa90744e22c7dc4b339f`: ガントリーをFlywheelのインスタンスで描く
- `2cc40ff6929812f9b2a8d055e3189da57f3a209f`: ヘッドビンの排出口のUVとレイヤーを直す
- `fbb3a7c564557c6cfc840afd56711615c9cc9744`: Colorwheelでの確認手順と計測結果を加える

## 背景

利用者の環境で採取した120秒のsparkプロファイルでは、Render threadの合計119,956 msのうち `QuarryRenderer.renderSafe` が34,728 msで28.95%を占めた。ブロックエンティティ描画全体は41,704 msで34.77%だった。

`QuarryRenderer` は部品ごとに `ShadeSeparatingSuperByteBuffer.renderInto` を呼び、この処理が全頂点を毎フレームCPUで変換していた。`renderInto` は29,860 msで24.89%だった。1台1フレームの描画回数は約 9D + 3S + 17 回、頂点数は約 360D + 128S 個になる。Dは掘削深さ、Sは橋の長さである。D=100、S=20では977回、38,560頂点になる。

## 実装

- 新しい `QuarryVisual` は `SplitShaftVisual` を継承し、`SimpleDynamicVisual` を実装する。
- `QuarryVisual` は桁、エンドトラック、ベルト枠、トロリー、ドリルステム、ヘッドビンかグラップル、シュートの輪と蛇腹、ドリルヘッドを `TransformedInstance` で描く。橋のベルトはCreateの `ScrollInstance` で描く。
- `QuarryVisual` がインスタンスを置き直すのは、構造、ドリルの有無、ベルトの運転状態、光のどれかが変わったときと、描画位置のu・v・深さのどれかが1/1024ブロックを超えて動いたときである。
- 走行中のベルトの模様はシェーダーが `speed` で動かす。停止中のベルトは到達した位置を `offset` で保つ。
- ドリルヘッドは稼働中だけ、毎フレーム1インスタンスの変換を更新する。
- `QuarryVisual` はガントリーが通るセクションを `lightSections` に登録する。光の更新は別のワーカースレッドから届くため、フラグ `relight` は `volatile` にした。
- `QuarryRenderer.renderSafe` は `VisualizationManager.supportsVisualization(level)` が偽のときだけガントリーを描く。偽になるのはbackendがoffのときとPonderの中である。
- 搬送中のアイテムと建設を妨げるブロックの枠は、常に `QuarryRenderer` が描く。
- 部品の並びは `QuarryRenderer.parts` と `QuarryRenderer.Gantry` にまとめた。2つの描画経路は同じ配置を使う。
- `ElectrolysisClient` はクアーリードライブのvisualを `SplitShaftVisual` から `QuarryVisual` に変えた。
- Colorwheel用の専用コードは追加していない。`QuarryVisual` が使うインスタンス型はFlywheelの `TRANSFORMED` とCreateの `SCROLLING` だけである。

### ヘッドビンの修正

Flywheelで描くと、ヘッドビンの排出口の横に黒い面が出た。backendがoffのときは出なかった。焼いたメッシュの頂点色はすべて1.0だったため、原因はアンビエントオクルージョンではない。

排出口の要素はブロックの外へ7ピクセル出ている。既定のUVがケーシングのスプライトの外を指し、アトラス上の隣のスプライトを参照していた。従来の描画はcutoutレイヤーで透明画素を捨てていた。Flywheelはモデルをsolidレイヤーで焼くため、透明画素を黒く描いた。

排出口の4面のUVをケーシングのスプライト内に収め、モデルに `render_type: minecraft:cutout` を指定した。ビン上面のシュートのテクスチャにも透明画素があるため、cutoutの指定が要る。直したのは生成スクリプト `tools/generate_quarry_assets.py` とモデル `head_bin.json` である。排出口の見た目は変更前と変わる。変更前は隣のスプライトの模様が出ていた。

クアーリーのほかの部品9個はUVがスプライト内にある。ドリルステムのモデルは以前からcutout指定だった。

## 計測方法

`QuarryRenderClientCheck` が計測する。このチェックは `-Dcreateplant.quarryRenderClientCheck=true` のときだけ動く。

- フラットワールドに16×16のクアーリーを4台置き、レールの下約101〜102ブロックまで掘らせる。
- カメラは固定、垂直同期はoff、GUIは非表示、ウィンドウは854×480にする。
- 稼働中と停止中の2状態を測る。停止中はレッドストーン信号を外してガントリーが静止した状態である。
- 各状態でbackendをoff、on、off、onの順に切り替える。切り替えから100 tick待ち、続く300 tickのフレーム数を数える。

環境はMinecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10、Flywheel 1.0.6、Java 21.0.6、Core i7-11700、GeForce RTX 3080の開発用クライアントである。

シェーダーパックなしの計測は次のコマンドで実行する。

```bash
./gradlew -I docs/quality/quarry-render/client.gradle runClient
```

Colorwheelの計測は次のコマンドで実行する。

```bash
./gradlew -PwithColorwheel=true -I docs/quality/quarry-render/colorwheel.gradle runClient
```

Colorwheelの計測では、Colorwheel 1.3.2、Iris 1.8.14-beta.1、Sodium 0.8.13を加えた。`-PwithColorwheel=true` のときだけ、この3つのjarをModrinthから取得する。シェーダーパックは自作で、プログラムは `gbuffers_textured_lit` の2ファイルだけである。Colorwheelのフォールバックモードを有効にした。フォールバックモードが無効だと、Colorwheelはこのパックを非対応と判定し、backendはoffになった。

Sodiumの配布jarは本体のjarを内側に持つ。開発環境では内側のjarが読み込まれないため、`build.gradle` の `sodiumDevJar` が2つを1つのjarにまとめる。

変更前の値はコミット `ccbf375` に従来の描画経路を強制する一時的な変更を当てて測った。この一時的な変更はコミットしていない。測ったのはヘッドビンのモデルを直す前で、シェーダーパックはなしである。

## 結果

表1 シェーダーパックなしでのフレーム時間、クアーリー4台、単位はms

| 状態 | backend | 1回目 | 2回目 | 平均 | 平均fps |
| --- | --- | --- | --- | --- | --- |
| 稼働中 | flywheel:off | 8.184 | 8.307 | 8.246 | 121.3 |
| 稼働中 | flywheel:indirect | 1.039 | 1.017 | 1.028 | 972.6 |
| 停止中 | flywheel:off | 8.215 | 8.129 | 8.172 | 122.4 |
| 停止中 | flywheel:indirect | 0.839 | 0.848 | 0.844 | 1185.4 |

表2 自作シェーダーパックとColorwheelでのフレーム時間、クアーリー4台、単位はms

| 状態 | backend | 1回目 | 2回目 | 平均 | 平均fps |
| --- | --- | --- | --- | --- | --- |
| 稼働中 | flywheel:off | 11.897 | 12.825 | 12.361 | 81.0 |
| 稼働中 | colorwheel:indirect | 1.380 | 1.181 | 1.280 | 785.7 |
| 停止中 | flywheel:off | 12.626 | 12.957 | 12.792 | 78.2 |
| 停止中 | colorwheel:indirect | 0.825 | 0.959 | 0.892 | 1127.6 |

表3 変更前の描画経路でのフレーム時間、シェーダーパックなし、クアーリー4台、単位はms

| 状態 | backend | 1回目 | 2回目 | 平均 | 平均fps |
| --- | --- | --- | --- | --- | --- |
| 稼働中 | flywheel:off | 8.363 | 8.486 | 8.425 | 118.7 |
| 稼働中 | flywheel:indirect | 8.462 | 8.643 | 8.553 | 116.9 |
| 停止中 | flywheel:off | 8.805 | 7.993 | 8.399 | 119.3 |
| 停止中 | flywheel:indirect | 8.362 | 8.067 | 8.215 | 121.8 |

表1では、backendをonにすると平均フレーム時間が稼働中で8.02分の1、停止中で9.69分の1になった。offとonの差は4台分で稼働中7.218 ms、停止中7.328 msである。表2では稼働中で9.65分の1、停止中で14.34分の1になった。表3のとおり、変更前はbackendがonでもフレーム時間が変わらなかった。

計測用のワールドにはクアーリー以外の負荷がほとんどないため、この比はこの場面の値である。sparkの28.95%から計算すると、クアーリーの描画時間が0になったときのfpsの上限は $1 / (1 - 0.2895) = 1.41$ 倍になる。この上限は推測で、backendが動いているときに限る。

結果のファイルは `docs/quality/quarry-render/` に置いた。`flywheel-result.json`、`colorwheel-result.json`、`baseline-result.json` が表1から表3の元の値である。`flywheel-closeups.jpg` と `colorwheel-closeups.jpg` はトロリーとドリルヘッドの拡大画像をoffとonで並べた画像である。

## 確認結果

- `./gradlew build` は成功した。
- 専用GameTestサーバーで `QuarryGameTests` の17件が通過した。
- トロリーとドリルヘッドの拡大画像をoffとonで比べた。409,920画素のうち明るさの差が24を超えた画素は、シェーダーパックなしでトロリー916、ヘッド8だった。Colorwheelではトロリー710、ヘッド31だった。目視で部品の欠けや黒い面は見つからなかった。
- Colorwheelの計測では結果のbackendが `colorwheel:indirect` だった。各ドライブのvisualが `QuarryVisual` であることは、チェックが確かめた。

## 未確認事項

- 未確認: 利用者の環境でbackendが動いているか。backendがoffのときの描画は変更前と同じで、フレーム時間は変わらない。`/flywheel backend` を引数なしで実行すると現在のbackendが表示される。シェーダーパックを使う環境でフォールバックモードが無効のとき、計測ではbackendがoffになり、fpsは約83のままだった。フォールバックモードを有効にするとbackendは `colorwheel:indirect` になった。
- 未確認: 利用者の実ワールドでの効果。確認する方法は更新したjarで同じ場所のsparkプロファイルを取り直し、`QuarryRenderer.renderSafe` の割合を比べることである。
- 未確認: 実在のシェーダーパックでの見た目と速度。Colorwheel対応のパックでフォールバックモードを使わない場合も確認していない。
- 未確認: `flywheel:instancing` と `colorwheel:instancing`。計測で選ばれたのはindirectだけである。
- 未確認: Ponderの再生。Ponderは従来の描画経路を使う。
- 未確認: 走行中のベルトの模様の速度がoffのときと一致するか。静止画の比較では判定できない。確認する方法はゲーム内で `/flywheel backend` を切り替えて見比べることである。
- 未確認: 松明の設置など光が変わったときの追従。
- 未確認: 16×16、深さ約101以外の寸法での計測。

## 制約と次回の注意

- `/flywheel backend` で切り替えた直後は、ドライブのあるセクションが視界に入るまでガントリーが描かれない。変更前の描画経路でも同じである。
- `tools/generate_quarry_assets.py` を実行すると、`blockstates/crane_drive.json` がコミット済みの内容と違う内容に書き換わる。今回は書き換えを戻し、修正していない。
