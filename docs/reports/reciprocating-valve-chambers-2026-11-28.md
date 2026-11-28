# 往復式コンプレッサーの弁室と八角形フランジ

対象バージョンは `0.7.3`。実装コミットは `197ec38e14132b5657705f8d9423f120cd3853de`。

各シリンダーの外端で、中央ヘッドの斜め四方向に独立した弁室を配置した。弁室の軸を26度外に開き、白灰色の胴を中ほどでふくらませてフランジ手前を絞った。中央ヘッドと各弁室に、正八角形の外フランジ、8本の立体六角ボルト、小さい八角形リング、凹みのある中央の突起を組み込んだ。

生成元は [generate_reciprocating_compressor.py](../../tools/generate_reciprocating_compressor.py)。隣接する断面の位置と半径を一致させて接合し、端面のUVを面全体に投影した。生成した [industrial_cylinder.obj](../../src/main/resources/assets/create_refinery_n_reactors/models/block/reciprocating_compressor/industrial_cylinder.obj) は16 pxの範囲に収まる。配管にはシリンダー上面の吸入口と下面の吐出口を使用する。

## 検証

- 生成後のOBJで弁室4つ、正八角形フランジ5つ、各8本のボルト、接合点と1ブロック内の寸法を検査した。
- RTX 4060の開発クライアントで、32 RPMのシャフトにつないだ3列6気筒を四方向から確認した。
- コアModの関連GameTest 10件、Ponderデータの静的検査、JARビルドが通過した。既存プレイヤーワールド全体は読み込んでいない。

## ゲーム内画像

- [斜め上](images/reciprocating-valve-chambers/reciprocating-valve-chambers-oblique.jpg)
- [正面](images/reciprocating-valve-chambers/reciprocating-valve-chambers-front.jpg)
- [側面](images/reciprocating-valve-chambers/reciprocating-valve-chambers-side.jpg)
- [背面](images/reciprocating-valve-chambers/reciprocating-valve-chambers-rear.jpg)
