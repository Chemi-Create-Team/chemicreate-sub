# 電極ヘッドを密閉したフードにした完了レポート

- 日付：2026-11-17
- 実装コミット：このレポートと同じコミット（`git log -1 -- docs/reports/electrode-head-hood.md`で確認）
- 説明：[フィルタープレス型電解槽](../filter-press-electrolysis.md)

## 指示

電極ヘッドの上面を埋め、側面にガラスのテクスチャを付けて、ガスが漏れ出なさそうな見た目にする。

## 実装

`tools/electrolysis_hardware.py`（電解セル一式のモデルの正本）の電極ヘッドだけを変更し、`tools/generate_electrolysis_cell.py`で`models/block/electrolysis_cell/head.json`と`head_terminal.json`を作り直した。ほかのモデル・テクスチャ・Javaは変更していない。

| 部分 | 変更 |
| --- | --- |
| 上面 | 両側の梁（x 0～2、14～16）の間を厚さ2 px（y 14～16）の板で閉じた。上面は電解セル上部と同じ`top`テクスチャ（四方つながりの区画）、端面は鋼板。旧来の中央のギアボックス（梁の下で見えなくなる）は削除 |
| 側面 | 四隅の柱の間にガラス（`minecraft:block/glass`、カットアウト）をはめた。長辺は柱の間（z 2～14、高さ0～12）、両端は梁の間（x 2～14、高さ0～14）。端のガラスは0.5 px内側に置き、隣のヘッドのガラスと同じ面に重ならない（ちらつき防止） |
| 回転軸 | 上面から入る半シャフト（`ElectrodeHeadRenderer`）は変更なし。板を貫いて見える |
| 端子ヘッド | 碍子と端子棒はそのまま。端子棒は板を貫く |

`glass`テクスチャはヘッドの2モデルにだけ加え、ほかのモデルの`textures`には入れない。

## 確認

- 生成後の差分は`head.json`と`head_terminal.json`の2ファイルだけ。
- 実画面（Xvfb、日本語、Ponder「フィルタープレス型電解槽」）：上面が閉じ、長辺の柱の間のガラス越しに電極板が見えることを確認した。`docs/quality/electrode-head-hood/`
- Javaを変更していないためビルド・GameTestは実行していない。

## 未確認

- ヘッドを上げ下げしている途中、端のガラス越しの見え方（Ponderの静止画1枚と、引いた全体図だけ確認）。
- X向き（90°回転）のヘッドの実画面。モデルは同じものを回すだけ。

## 追記：メカニカルミキサー風の外観（同日）

指示：テクスチャを安山岩ケーシングがモチーフのメカニカルミキサーのような感じにする。

| 部分 | 変更 |
| --- | --- |
| 上部 | 高さ10～16 pxの箱。側面はCreateの`mixer_base_side`、上面は`mechanical_press_top`（中央の穴から回転軸が入る）、下面は`andesite_casing`。前の鋼板の天板・梁・母線は削除 |
| 柱 | 四隅の2×2 px、高さ0～10 pxの柱を`andesite_casing`の縁の部分で描く |
| ガラス | 柱の間の4面、高さ0～10 px（前と同じ置き方。端は0.5 px内側） |
| 端子ヘッド | 碍子は上面の上にそのまま |

Createのテクスチャはファイルを複製せず、モデルから`create:block/...`で参照する。実画面：`docs/quality/electrode-head-hood/mixer-style.png`。

## 追記：セルと制御盤も安山岩ケーシング風に（同日）

指示：制御盤とセルも同じく安山岩ケーシング風にする。

- `tools/electrolysis_hardware.py`の外装の地を、ふた付き槽の銀色の鋼板からCreateの`andesite_casing`の木の板（中央8 pxを縦横に繰り返す）に替えた（`andesite_field()`）。縁は前から安山岩ケーシングの縁で、つながる面では縁を消す。
- 対象：セルの外装`casing.png`、上の縁`top.png`（板を横向き）、制御盤の地板`panel_2.png`・`panel_3.png`（計器・ボタン・ダイヤルの描画はそのまま）。モデルは変更なし。
- 使われなくなった`framed()`と`STREAKS`を削除。
- 実画面：`docs/quality/electrode-head-hood/andesite-cells-panel.png`。制御盤の白い文字は木の地の上でも読めることを確認した。

## 追記：制御盤を安山岩合金ブロックに（同日）

指示：制御ブロックは安山岩合金ブロックのテクスチャを使う。

- 制御盤（`controller_0`・`controller_1`）の側面・上面・下面をCreateの`create:block/andesite_block`（安山岩合金ブロック）にした。正面の地板`panel_2.png`・`panel_3.png`も同じテクスチャを4倍に拡大した上に計器・ボタン・ダイヤルを描く。
- 明るい地の上で読めるよう、表示文字と目盛り（`LEGEND`）を黒（`INK`）にした。
- 使われなくなった`panel_joins()`を削除。
- 実画面：`docs/quality/electrode-head-hood/alloy-panel.png`（LOAD・START/STOP・0～100が読める）。

## 追記：セルの上端と下端を安山岩ケーシングの縁に（同日）

指示：電解槽セルの上端が木で終わっていて違和感があるので安山岩ケーシングの縁にする。セルの下の変な模様（真鍮と黒の印が付いた鉄の帯）を消して、ここも安山岩ケーシングの縁にする。

- セルの側面は、上に電極ヘッドが載っていても上端の縁を消さない（`wall_side`の模様選びで上のつながりを無視）。
- 側面の下端（給液の帯）と上端（ガスの帯）に重ねていた鉄の帯（`rail_*`の8モデルと`fittings.png`）を削除した。外装自身の安山岩ケーシングの縁が見える。帯が示していた配管位置の目印もなくなるが、ポートの位置（給液は下面など）は変えていない。
- 実画面：`docs/quality/electrode-head-hood/cell-rims.png`。

## 追記：セルの厚みを戻し、ヘッドに実働する歯車を付けた（同日）

指示：電解槽になるとセルの厚みのある見た目が消えて薄っぺらいので、厚みが消えないようにする。ミキサー風の電極ヘッドには本来ミキサーに付いている部品がないので、実働するものを付ける。

- セル：前回削除した側面の帯（`rail_*`）を戻した。外側へ0.5 px張り出す4 pxの帯で、テクスチャは鉄と真鍮の模様ではなく、安山岩ケーシングの縁（上2 pxは明るい上縁、下2 pxは暗い下縁）。下端は常に、上端は電極ヘッドがないときに付く。
- ヘッドの外観：上部の箱を高さ12～16 pxに縮め、その下の8～12 pxにミキサーと同じ透けた帯（`mixer_base_side`の6～10行目）を付けた。柱とガラスは0～8 px。
- ヘッドの歯車：`ElectrodeHeadRenderer`がCreateの軸なし歯車（`AllPartialModels.SHAFTLESS_COGWHEEL`）を帯の高さ（中心から2 px上）でヘッドの回転に合わせて回す。上がった電極板が歯車に重ならないよう、上げたときの位置を2 px下げた（下げたときの位置は同じ）。
- 実働：`ElectrodeHeadBlock`に`ICogWheel`（小さな歯車）を付けた。Createのミキサーと同じく、横に置いた小さな歯車とかみ合って回る（逆回転）。同じ列のヘッド同士は今までどおり同じ向きに一緒に回る（`propagateRotationTo`のカスタム接続が優先）。上からの軸も今までどおり。
- Ponder「フィルタープレス型電解槽」の回転の字幕に、横の歯車でも回せることを加えた（日英中）。
- テスト：`ElectrolysisCellGameTests.aCogwheelBesideTheHeadDrivesItLikeAMixer`を追加（横の歯車がヘッドを逆向きに同じ速さで回す）。`ElectrolysisCellGameTests`15件すべて合格。`MetalRecoveryGameTests`・`MaterialPumpGameTests`も合格（変更後の1回目、計17件）。
- 実画面：`docs/quality/electrode-head-hood/cog-and-rails.png`（帯の中の歯車の歯と、セル下端の縁の帯）。

未確認：ヘッドを回している最中の歯車の動き（静止画のみ）、横の歯車で一列全体を回したときの実画面。

## 追記：歯車をやめ、電極板と一緒に下りる横桁を付けた（同日）

指示：歯車は要らないので戻す。ピストンが下がったときに一緒に下りる部品が欲しい。電解セルは写真を見ると厚みが増えていない。

- 歯車の帯・歯車の描画・`ICogWheel`・追加したGameTest・回転の字幕を、前の状態に戻した（Java・テスト・字幕・翻訳は歯車の前と同じ内容）。ヘッドの外観も上の箱（10～16 px）と柱・ガラス（0～10 px）に戻した。
- 横桁：`models/block/electrolysis_cell/crosshead.json`を追加。Createのプレスのヘッド（`mechanical_press_head`）の板12×14 px・厚さ2 pxを電極板の吊り金具の上に置き、プレスの棒（`mechanical_press_pole`）を中央から8 px上へ伸ばす。`ElectrodeHeadRenderer`が電極板と同じ移動で描く（電極板が0枚でも描く）。
- 移動量：上げたときは電極板の上端が6 px（横桁6～8 px、棒の先が上の箱の天板16 px）、下げたときは今までどおり1ブロック下（横桁0～2 px、棒の先が箱の底10 px）。棒はどの位置でも上の箱から外へ抜けない。
- セルの厚み：原因は、電極ヘッドを載せると上面の幅3 pxの縁（`wall_up`）と上端の張り出した帯が消えていたこと。ヘッドが載っていても両方を描くようにした（ブロック状態の条件から上のつながりを外した）。下端の帯は下にセルがないときに付く（今までどおり）。
- 確認：`compileJava`成功。実画面（Ponder）で、ヘッドなしのセルの厚い上縁、ヘッドを載せた後も残る上端の帯、上げた状態の横桁と、下げた状態でガラス越しに見える棒を確認した。`docs/quality/electrode-head-hood/crosshead-and-rims.png`（左：上げた状態、右：下げた状態）。

未確認：動いている最中の見え方（静止画のみ）、X向きのヘッド。

## 追記：天板から突き出す棒（同日）

指示：メカニカルミキサーの上に付いているピストンのような部品（天板から突き出た棒）がないので追加する。

- 横桁の棒を、Createのメカニカルミキサーの棒（`mechanical_mixer/pole`と同じ形：灰色の側板2枚と木の芯、上端のふた）で作り直し、天板の上まで伸ばした。電極板と一緒に動き、上げたときは天板から10 px、下げたときは4 px突き出す。
- 棒は中央にあり、上から入る回転軸を囲む（ねじ軸に沿って上下するナットの見立て）。
- モデルの座標は−16～32 pxに収める必要があるため、横桁のモデルを8 px下げて作り、`ElectrodeHeadRenderer`で半ブロック上げて描く。描画範囲を上にも1ブロック広げた（突き出た棒が視界の端で消えないように）。
- 確認：`compileJava`成功。実画面（Ponder）：`docs/quality/electrode-head-hood/rod-above-lid.png`（左：上げた状態、右：下げた状態）。

未確認：動いている最中の見え方、上に軸ブロックを置いたときの棒と軸の重なり方（Ponderでは端子のない側の端のヘッドに軸が付いている）。

## 追記：電極の碍子を2倍にして制御盤の上へ（同日）

指示：電極の碍子のサイズを2倍にして制御盤の上に設置する。

- 端子付きのヘッド（ヘッドの状態`terminal`、モデル`head_terminal`）を廃止し、2本の端子を上段の制御盤（`electrolysis_cell_controller`の`part=1`）の上面に移した。碍子は前の2倍（直径4 px、3段の笠、真鍮の端子金具2×1×2 px）で、左右4 px・12 pxの位置に立つ。
- CEEの回路装置も制御盤へ移した：`ElectrolysisControllerBlock`が`ElectricalDeviceBlock`になり、上段だけに2つのノード（高さ20.6 px）を出す。ノードの登録・破壊時や素手スニークレンチでの電線の撤去は前のヘッドと同じ方法。`ElectrolysisCellDevice`は上段の制御盤から機械を引く。`ElectrolysisStructure.terminal()`は上段の制御盤の位置を返す。装置の種類のIDは既存の`electrolysis_electrode_head`のまま（保存データを壊さないため。名前は実体と合わなくなった）。
- Ponder「フィルタープレス型電解槽」の配線先を上段の制御盤に変え、字幕を直した。製造ガイド（水素・塩素アルカリ・フッ素）と電解採取の字幕、JEIの注記、電力不足の表示も「上段の制御盤の上の端子」に直した。端子ヘッドの状態を持っていたPonder構造（電解槽、電解採取、製造ガイド5章）を作り直した。
- 直す途中で、前回の外装変更で`silver_field()`を消したため`generate_absorption_tower.py`が動かなくなっていたことに気付き、関数を戻した（吸収塔の出力は変化なし）。
- 確認：`compileJava`成功。GameTest：`ElectrolysisCellGameTests`・`CircuitGameTests`（電池から制御盤の2端子へ配線して電解が動く）・`MetalRecoveryGameTests`・`ManufacturingPonderGameTests`・`PlantTutorialGameTests`・`FluorineGameTests`の22件すべて合格。検証スクリプト（翻訳・Ponder文言・チュートリアル）合格。実画面：`docs/quality/electrode-head-hood/panel-insulators.png`（左：制御盤の上の碍子、右：Ponderの配線）。

未確認：既存ワールドで旧端子ヘッドにつないでいた電線は、旧位置のノードが消えるため付け直しが必要（移行処理は作らない）。

## 追記：碍子をCEEのコネクターと同じ見た目に（同日）

指示：碍子のデザインと色をCEEのものと同じにする。

- 制御盤の上の2本の端子を、Create: Electro Energeticsのコネクター（`electroenergetics:models/block/connector/block.json`）と同じ形にした：2×6 pxの軸、幅6 pxの白い笠2枚、4×4 pxの銅色の頭。テクスチャはCEEの`electroenergetics:block/connector`をそのまま参照する（複製しない）。大きさはCEEのコネクターと同じ（前の碍子より大きい）。
- 電線をつなぐ位置を頭の中心（高さ24 px）に合わせた。
- 確認：`compileJava`成功。`CircuitGameTests`・`ElectrolysisCellGameTests`合格。実画面：`docs/quality/electrode-head-hood/cee-connectors.png`（左：端子、右：Ponderの配線）。

## 追記：端子を2/3の大きさに（同日）

指示：端子が大きすぎるので2/3にする。

- CEEのコネクターと同じ形・テクスチャのまま、各部を根元（制御盤の上面）から2/3に縮めた（`tools/electrolysis_hardware.py`の`TERMINAL_SCALE`）。笠の幅4 px、高さは上面から約6.7 px。
- 電線をつなぐ位置を頭の中心（16＋8×2/3 px）に合わせた（`ElectrolysisControllerBlock`）。
- 確認：`compileJava`成功、`CircuitGameTests`合格。実画面：`docs/quality/electrode-head-hood/cee-connectors-small.png`。

## 追記：STARTを押すと「電極ヘッドが下りきっていません」と出る件（同日）

報告：STARTボタンを押すと、電極ヘッドが回り切っていない（下りきっていない）と表示される。

- 調査：4段の電解槽の端のヘッドの上にクリエイティブモーター（32 RPM）を置き、STARTを押すGameTestを追加した（`aMotorOnOneHeadLowersEveryHeadAfterStart`）。全ヘッドが回って下り、運転できることを確認した。回転さえ届けば仕組みは正しく動く。
- 原因：表示の問題。STARTの後、電極板が下りきるまで（32 RPMで約16 tick、遅い回転ほど長い）の間も、回転が届いていないときと同じ「インターロック：電極ヘッドが下りきっていません。ヘッドを回してSTARTを押してください」が出ていた。正常な下降中にも異常のように見える。
- 修正：状態を2つに分けた。下りきっていないヘッドがすべて回っている間は「電極板を下ろしています」（`lowering`）。回っていないヘッドがあるときだけ「インターロック：回っていない電極ヘッドがあり、電極板が下りません。どれか1つのヘッドを上から軸で回してください」（`raised`）。
- 確認：`ElectrolysisCellGameTests`・`CircuitGameTests`・`MetalRecoveryGameTests`の16件合格。`validate_translations.py`合格。

未確認：実際のワールドで報告された配置。回転の大きさ（応力不足で止まっていないか）や、軸がヘッドの上面につながっているかは、配置を見ないと分からない。

## 追記：回転動力をなくした（2026-11-17）

指示：「動かすのに回転動力はいらない」。

- `ElectrodeHeadBlock`はKineticBlockをやめ、`IBE`＋`IWrenchable`の普通のブロックにした。`ElectrodeHeadBlockEntity`は`SmartBlockEntity`になり、毎tickの`move()`でSTARTなら下へ、STOPなら上へ1/16ずつ動く（全行程16 tick）。応力・軸の接続・ヘッド同士の回転伝達は削除。
- 制御盤の状態：下降中のヘッドだけなら`lowering`（待つ）、上がっている・上昇中のヘッドがあれば`raised`。`raised`の文言を「上がっている電極ヘッドがあります。STARTを押すと全部のヘッドが下ります」に変更。
- 描画：ヘッド上の軸を描かない（横桁・棒・電極板は位置に合わせて動く）。
- Ponder：電解槽のヘッド上のモーターと、電解槽・電解採取のヘッドへの回転設定を削除。字幕`electrolysis_cell.rotation`は「回転動力は要らず、STARTでひとりでに下りる」に、製造ガイドの水素・フッ素の工程文から「上から回す」を削除。電解槽を含む製造ガイド5章（水素・食塩電解・フッ素・重水素・硫酸）を作り直した。
- 文書：`docs/filter-press-electrolysis.md`の組立手順から回転を入れる手順を削除し、上下の説明を更新。`docs/specs/electrolysis-electrode-head.md`に回転の記述が古いことを追記。

確認：`compileJava`成功。`validate_ponder_language.py`・`validate_plant_tutorials.py`・`validate_translations.py`合格。GameTest（`ElectrolysisCellGameTests`、`CircuitGameTests`、`MetalRecoveryGameTests`、`ManufacturingPonderGameTests`、`PlantTutorialGameTests`）22件合格。新しいテスト`startLowersEveryHeadAndStopRaisesThemWithoutRotation`は、回転なしでSTART後に`lowering`→全ヘッド下降、STOPで全ヘッド上昇を確かめる。実画面：`docs/quality/electrode-head-hood/no-rotation-ponder.png`（モーターのない電解槽のPonder）。

未確認：既存ワールドで回転を受けていたヘッドの上の軸は残るがつながらない（本格運用前のため移行処理なし）。ヘッドのレシピは今もCreateの軸を材料に使う（変更していない）。
