# 加熱済み原料の通常流体への統合

2026-11-13。実装コミット：`1fc8442990aa3e090bb0aeb434a34e599e02121d`。

## 変更内容

`heated_naphtha`、`heated_diesel`、`heated_heavy_oil`、`heated_ethylene`、`heated_propylene`、`heated_synthesis_feed` の登録を削除した。これに付随する流動版、設置流体、バケツ、物性・危険性の別登録、翻訳、画像・モデルも削除。対象のレシピは通常のナフサ、CDGの軽油、重油、エチレン、プロピレンを使う。

`ProcessRecipe.minimumKelvin` に主原料の下限を持たせ、接触分解720 K、軽油脱硫623 K、重油分解673 K、エチレン・プロピレンの重合とエチレン酸化480 Kを条件にした。値は旧流体の基準を引き継いだ。脱硫30 bar・重油分解50 barの水素条件、触媒、所要時間、数量は維持する。

温度は第1流体入力の実温度で確認する。温度成分がない通常流体も物性の初期温度から判定し、冷たい原料は反応しない。搬入時には温度で拒否しないので、冷たい原料を槽へ入れ、その槽で加熱できる。進行・確定の両方で温度条件を確認し、冷えた間は原料と触媒を消費しない。通常流体を旧加熱済み流体へ変更していた搬入口の処理を削除した。

合成ガスは通常の合成ガスを既存の鉄触媒バスケットへ入れ、従来の触媒層の実温度・圧力・平衡計算で反応する。旧 `heated_synthesis_feed` を通す必要はない。既存の圧縮原料と生成混合物の扱いは今回置き換えていない。

JEIの加熱例も通常流体から同じ流体の温度付きスタックへ変更し、反応の説明に最低温度を表示する。加熱済み原料を生成する別の加工レシピはない。

## Ponder

小型加熱器を使っていた構造を取り除き、通常原料を入れたふた付き槽と下部バーナーへ変更した。製造Ponderの加熱ユニットは、同じ生成元からアンモニア・石油化学原料・ポリマー・鋼・燃料精製の5章を再生成した。加熱ユニットの追加は通常の1×1槽1台とバーナー1台であり、旧加熱器の外装27個を槽へ一つずつ置換する配置にはしていない。工業用加熱器を参照していた生成元のテンプレートも削除した。

日英中の説明とアイテムリンクは通常流体へ更新した。小型加熱器ブロック自体は現役設備として残しているが、同ブロックは出荷Ponderの構造・部品リンクから取り除いた。詳しい操作・温度表は[通常の原料流体と反応温度](../process-feed-temperature.md)。

主なファイル：`CEProcessFluids.java`、`ProcessRecipe.java`、`ReactorMachineBlockEntity.java`、`CatalystBasketProcess.java`、`ThermalStreams.java`、`CreatePlantJeiPlugin.java`、`tools/generate_manufacturing_ponder.py`、対象の構造NBT・翻訳・部品一覧。

## 検証

全体試験と実クライアントは省略し、関連する **44/44 GameTest合格**。GameTest 3.118秒、ビルドを含むGradle全体32秒。

- 新規3件：旧6登録がないこと、全6反応の下限直前・境界・通常の冷たいスタックでの判定、同じ槽で冷たいエチレンを加熱して1バッチ作ること、途中で冷たい原料を混ぜた際の保留、通常流体と温度の保存・復元。
- 加熱・反応の新規試験は実BlockEntityの処理を手動ステップで進めたもの。電熱線ヒーターの実配線による冷態起動の長時間試験ではない。
- 既存試験は反応レシピ、触媒バスケット、実回転入力、配管の圧力・凍結に限定した。実回転入力の重合試験には、旧流体名での温度条件回避を廃したことに合わせて下部バーナーを配置した。
- 日英中2,537キー、Ponder言語139シーンに合格。
- Ponder構造検査は95追加シーン・504説明・234部品リンク・27製造工程に合格。
- 旧6流体への有効リソース参照と、出荷Ponder内の工業用・小型加熱器参照を確認。`git diff --check`合格。

```sh
env JAVA_HOME=/workspace/.createplant-jdk21 PATH=/workspace/.createplant-jdk21/bin:$PATH JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 bash gradlew --no-daemon -I docs/quality/heater-output/server.gradle -DtestClasses=ReactionTemperatureGameTests,ReactorGameTests,CatalystBasketGameTests,DriveLessVesselGameTests,PressureAndFreezingGameTests build runGameTestServer
```

プッシュ前に `origin/main` の熱交換器外観変更（`a2350669`）をリベースで取り込み、当該変更を保持した。統合後も翻訳・Ponderの静的検証を通し、再ビルドを実施した。GameTest44件は統合前の結果であり、クライアント外観だけの変更を理由に再実行はしていない。

## 未確認事項・引き継ぎ

旧6流体の在庫を変換する移行処理はない。既存ワールドにそれらの流体・バケツが残っている場合、更新で失われる可能性がある。

実クライアントでのPonderの見え方、JEIの表示、全工程をつないだ連続運転は未確認。初回のworld tick形式の新規加熱試験では、組立直後に保持したコントローラー／ポートが無効になり、冷態起動の確認には使えなかった。今回の新規加熱確認は、組立後の同一BlockEntityへ温度・反応処理を手動ステップで適用する範囲に留めている。初回組立直後からの自動搬入・起動は、実プレイで確認が必要。

冷却前後の反応生成混合物、圧縮合成原料、物質の相変化は今回の6原料統合と別の既存処理として残した。反応熱・全反応の質量換算の校正も行っていない。加熱のために同じ化学物質の新しい流体IDを追加しないこと。
