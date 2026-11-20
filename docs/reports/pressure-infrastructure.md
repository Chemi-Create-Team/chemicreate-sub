# 高圧ポンプ・ゴムホース・ケミカルハザードスーツ

対象は Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10。実装コミットは `7c1c5cf2b5f7b073ca2a41db737f6e6108d2f268`。

## 高圧ポンプ

メカニカルクラフターで製作したポンプを、空いた2×2×2の場所へ設置する。上面の入力軸へ垂直シャフトをつなぐ。背面下の吸込口と正面上の吐出口には、Create標準パイプまたは直接ホースを接続できる。筐体には鋳鉄風の外装、真鍮と銅のフランジ、ゴムの防振マウントを描いた。上部の入力軸とインペラは回転速度に追従する。

駆動負荷は8 SU/RPM。上段と下段は8,000 mBの内部槽を共有し、標準配管ではCreateの流体網を駆動する。配管は各側で最大128本。重質油などにも同じ流量計算を適用し、液体に付いた温度などのデータは保持する。配管材・タンク側の既存の受入条件が適用される。ホースでの吐出上限は毎tick 2 mB × 絶対値RPM。エンジニアゴーグルは回転数、負荷、直近20 tickの吐出量から求めたmB/t、ホースの接続先を表示する。

分解すると設置用アイテム1個に内部槽の残液を引き継ぐ。形成・分解・能力の公開はサーバー側が管理する。未ロード部を含む構造は停止し、構造が再び揃うと運転できる。

## フレキシブルゴムホース

ホースを持ち、始点と終点のフランジを順に右クリックする。アクションバーに始点座標を表示し、同じ次元で32ブロック以内なら1個を消費する。Shiftを押しながらフランジを右クリックすると始点を選び直せる。設置したホースはカテナリーで垂れ下がり、黒いチューブに黄色の識別線が入る。

ポンプに直接付けた場合は口の吸込・吐出方向で流れる。コネクタ同士のホースは背面側へ双方向の流体Capabilityを公開し、外部ポンプで送れる。コネクタは背後の機械の流体口を利用し、直接使える流体口がない場合は1,000 mBのバッファーを使用する。レンチかハサミをフランジに使うと回収できる。接続は両端と次元の保存データに記録し、一方のチャンクが閉じている間も二重回収を防ぐ。

## ケミカルハザードスーツ

工業用イエローと黒ゴムシールの防具4部位。安全ブーツだけでもCreateベルトの移動を無効化し、通常設定で約15ブロックまでの落下を吸収する。フルセットは毒、ウィザー、吐き気、盲目、腐食、高温工程液などから保護する。有毒ガスの遮断にはヘルメットのフィルターが必要で、ガスを受けた20 tickにつき最大1消耗する。使用済みヘルメットと既存のガスマスク用フィルターを作業台で組み合わせると補充できる。

一人称のヘルメット表示は視界の四隅のフレームと周辺ガスの表示からなる。`config/create_refinery_n_reactors-client.toml` の `hazardHelmetOverlay` で切り替える。

## 実装箇所

- [ポンプの配置・形成](../../src/main/java/net/createplant/block/PressurePumpStructure.java)、[駆動・共有槽](../../src/main/java/net/createplant/block/entity/HighPressurePumpBlockEntity.java)、[Createの配管距離拡張](../../src/main/java/net/createplant/mixin/HighPressurePumpRangeMixin.java)
- [ホースの保存と接続](../../src/main/java/net/createplant/transport/HoseSocket.java)、[接続アイテム](../../src/main/java/net/createplant/item/FlexibleRubberHoseItem.java)、[ホース描画](../../src/main/java/net/createplant/client/HoseRenderer.java)
- [防護処理](../../src/main/java/net/createplant/safety/HazardSuit.java)、[ベルト連携](../../src/main/java/net/createplant/mixin/HazardBootsBeltMixin.java)、[ヘルメットHUD](../../src/main/java/net/createplant/client/HazardHud.java)
- [モデル・テクスチャの生成](../../tools/generate_pressure_infrastructure_assets.py)、[Ponder](../../src/main/java/net/createplant/ponder/PressureInfrastructurePonderScenes.java)、[専用GameTest](../../src/main/java/net/createplant/gametest/PressureInfrastructureGameTests.java)

## 検証

- `./gradlew build runGameTestServer` が成功し、必須GameTest 684件すべてが通った。新規の5件で重質油の標準パイプ128本輸送、ポンプ両口のホース、接続距離32ブロックと保存データ、ベルトと落下への耐性、防護服のフィルター消費、解体時の残液保持を確認した。
- GeForce RTX 4060のクライアントでポンプ・防護服の外観、ホースの黄色い識別線、接続口が画面外にあるときのホース描画と有毒ガス警告を確認した。日本語Ponderではホースがつながり、回収後に消えることを確認した。JEIではポンプのメカニカルクラフト、ホース・コネクタ・ヘルメット・安全ブーツの通常クラフト、ヘルメットの補充レシピが確認できた。
- 翻訳キーは日本語、英語、簡体字中国語の3言語。Ponderのファイル・翻訳・接続先を検証した。
- 異なる追加MODの専用パイプや流体ハンドラー、および128本を超える分岐網の負荷は今回の測定範囲外。
- クライアントの終了時は、全ディメンションの保存後に検証環境の `libEGL_nvidia.so.0` が異常終了した。ゲーム内の確認と撮影は保存前に完了した。
- ビジュアル規約の一括検証は既存の塩化アンモニウム溶液バケツの金属部で停止した。同じ結果を変更前のコミットでも確認した。

## 配布

Discord配布用JARは8,384,638バイト。8 MiBの添付上限に収めるため、既存開発用GameTest 15組のクラス21ファイルを配布用JARから外した。通常のGradleビルドとソースには全テストが含まれる。配布用JARの残りのエントリーは通常ビルドの内容と照合した。

SHA-256 `6e6b0988f34a3b4eb31f2689094420fd28db956b07807086fa83e371249c9ebe`
