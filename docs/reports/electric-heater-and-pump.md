# 電熱線ヒーターと電動ポンプ

実施日: 2026-11-13。開始点: `e22a94a7`。実装コミット: `3350f256a011fa3a71db2c0ddf329ead093c993b`。

ユーザー指定の2部品を追加した。新たな添付画像はなかったため、確認を提示したうえで、既存の鋼鉄ブロックの灰色と陰影を基準に、銅の帯・端子、碍子、露出した発熱線とモーターファンを専用モデル・16×16テクスチャで作成した。既存設備の外観は変更していない。

## 実装

- `electric_heater`：Electro Energeticsの2端子抵抗負荷。40 Ω、約400 Vで通常加熱、約800 Vで強加熱。閾値は実際の入力3.5 kW／14 kW。ブレイズバーナーと同じHeatLevelプロパティを使い、CreateのBasinとBoilerHeaterに対応した。前面が銅端子、上面が蛇行発熱線。停止時・通常・強加熱の3モデルで線の色と発光を変える。
- ふた付き槽には `P/20` J/tickを由来とする有限の熱を渡す。蓄熱上限5,000 J、毎tickの損失0.5％。通常のバーナーと混在する場合も、通常バーナーの従来の熱と、電気から蓄えた熱だけを合算する。
- `electric_pump`：CreateのPumpBlock・PumpBlockEntity・FluidTransportBehaviourを再利用し、回転の入力元を内蔵電動機にした。等価32 Ω、400 Vで理想5 kW。250 W未満は停止、4 RPM刻み、上限128 RPM。軸と歯車の接続を閉じ、外部への動力取り出しを許可しない。6方向の配管、レンチによる反転、モーターファンのBER描画を実装した。
- 圧送仕事は既存のPressureSectionsを使い、電動ポンプの有限512 Jバッファでも制限する。SIMULATEは予約だけ、EXECUTEで受け入れられた流量だけが消費する。既存ポンプの仕事・圧力・配管抵抗の計算は維持した。
- 両方とも停止信号で回路から負荷を外し、停電でも停止する。以前の電圧・回転数を保存して再始動させない。有限の蓄熱・仕事は保存・再読込しても増加しない。変化がない間の定期パケットと保存要求を抑えた。
- レシピ、採掘タグ、ドロップ、設備タブ、日英中の翻訳、Display Linkと4行のゴーグルを追加した。専用Ponderはそれぞれ配置・2本の配線・運転・停止を説明し、電動ポンプではレンチの操作も表示する。独自GUIはない。

主なファイルは `registry/CEElectricUtilities.java`、`block/ElectricHeaterBlock.java`、`block/ElectricPumpBlock.java`、各BlockEntity、`compat/electro/ElectricUtilityDevice.java`、`client/ElectricPumpRenderer.java`、`ponder/ElectricUtilityPonderScenes.java`。既存の `VesselClimate`、`ReactorMachineBlockEntity`、`PressureSections`、表示・登録に必要な接続を加えた。生成元は `tools/generate_electric_utilities.py`。

[操作と仕様](../electric-utilities.md)、[電力一覧](../electric-power.md)、DESIGN.md、README、Ponderガイドを更新した。

## 確認結果

Java 21、Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10、Electro Energetics 1.21.1-1.1.4で確認した。電源試験には実際に配置したクリエイティブ電池・銅線・端子を使い、回転速度を試験側から直接与えて搬送する方法は使っていない。

| 試験 | 結果 |
| --- | --- |
| 電熱線・400 V指定 | 実入力3,991.70 W、通常加熱。水1,000 mBが約20→25.96 °C。測定時の入力25,746.47 Jに対し、水の増分25,408.02 J |
| 電熱線・800 V指定 | 実入力15,966.81 W、強加熱。Createボイラー用の加熱値も1→2を確認 |
| 混在熱源 | 通常バーナー＋電熱線の強加熱でも、通常火の10,000 Jと電熱線の蓄熱だけを渡し、25,000 Jの無料の熱を作らない |
| 電動ポンプ | 6方向とも実入力約4.977～4.979 kW、124 RPM。1方向につき4,000 mBを全量搬送。2回目の投入と再起動でも合計8,000 mBを保持 |
| 停止・方向 | 両部品のレッドストーン停止・再開、電源除去による停止、ポンプの実際のレンチ逆送を確認 |
| 有限量 | 保存・再読込で蓄熱・仕事が増えず、以前の給電だけで回転を再開しない。圧送の照会だけでは消費せず、確定した搬送だけが仕事を消費 |
| 関連GameTest | 152件すべて合格。混在熱源の追加後、関係する36件を再実行してすべて合格。最後の配布ビルドでも専用4件すべて合格（重複を除いた確認対象は153件） |
| 翻訳・Ponder | 日英中2,574キー、142シーンの見出し・共有説明、構造データの検査が合格 |
| 実クライアント | RTX 4060で新規ワールドを起動し、専用2シーンのコンパイル・日本語翻訳・モデル・配線・液面を確認 |
| 配布JAR | ビルド成功。新規2アイテムのモデル・レシピ・ドロップ・Ponder、モデル6個・テクスチャ8枚を収録 |

[数値の生データ](../quality/electric-utilities/result.json)、[クライアント結果](../quality/electric-utilities/client-result-ja_jp.json)、[検証ログ](../quality/electric-utilities/verification.txt)、[JAR監査](../quality/electric-utilities/jar-audit.json)を保存した。[電動ポンプの実画面](../quality/electric-utilities/pump-ponder.jpg)はComputer Useで取得した。

試験中、密閉タンクの上面から液体を吸おうとした上向き配管は搬送できなかった。上面は蒸気用の出口という既存仕様なので、タンク側面から曲げて吸込口へ導き、6方向の動作を確認した。初回クライアント確認では新Ponderの著者キーと実行時翻訳キーの使い分けを誤っており、既存のPonderTextの仕様に合わせて修正した。

クライアント確認完了後の終了時には、以前の作業と同じ `libEGL_nvidia.so.0` のSIGSEGVが再発した。`CLIENT QUALITY PASSED: 2 scenes, ja_jp` は確認できたが、Gradleの `runClient` は終了コード134で失敗した。表示確認と終了障害を区別する。

## 未確認事項・注意

- 鉢の各混合レシピを網羅した運転、ボイラー一式からの軸出力、サバイバル用発電所の燃料収支は未確認。鉢・ボイラーの加熱段階はCreateの抽象的な仕組みであり、槽のように全体の熱収支を積算するものではない。
- 電動ポンプの実搬送は水で試験した。ガス・全流体種・全ての分岐配管の組合せを、新ポンプで網羅した試験ではない。従来の配管・圧力・温度処理の回帰試験は実施した。
- 水を吸う配管は密閉タンクの側面・下面から出す。上面からは蒸気・気体を取り出す。ポンプの通電中は閉塞・材料待ちでも電力を消費するため、制御にはレッドストーンを使う。
- 設備全体の全GameTestは今回実行していない。既知の槽清掃試験や外観検証スクリプトの旧来の失敗は修正範囲外。
- 既存の登録IDや保存形式、燃料・反応レシピ、既存設備のモデルは変更していない。新たなGUIや装置独自の配管網は追加していない。

再実行:

```sh
env JAVA_HOME=/workspace/.createplant-jdk21 \
  PATH=/workspace/.createplant-jdk21/bin:$PATH \
  JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 \
  bash gradlew --no-daemon -I docs/quality/electric-utilities/server.gradle build runGameTestServer

mc-debug bash gradlew --no-daemon -I docs/quality/electric-utilities/client.gradle runClient
python3 tools/validate_translations.py
python3 tools/validate_ponder_language.py
python3 tools/validate_plant_tutorials.py
```

クライアントもJava 21を使用する。サーバープロファイルに `-DtestClasses=` でクラス名の一覧を渡すと、関連試験も選択して再実行できる。試験で得た数値には配線距離・電線抵抗・試験時の処理順の差が含まれるため、理想電力と区別して比較する。
