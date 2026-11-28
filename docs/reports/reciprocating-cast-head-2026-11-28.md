# 往復式コンプレッサーの一体鋳物シリンダーヘッド

対象バージョンは `0.7.4`。実装コミットは `b242b9ed4e02c7ab2810cc8f1a94e6dcb26e85b5`。

- 各シリンダーを覆う白灰色の鋳物を、上に2つ、下に2つの膨らみを持つ一続きの外装にした。軸方向に並べた24頂点の断面に、四隅で増える半径を加え、上下の膨らみが頂点に達する軸位置をずらした。
- 小型フランジとその接続筒を撤去した。正面中央は大型正八角形フランジ1つ、8本の立体ボルト、内側の段差、短い円筒で構成する。外装は従来の1ブロックに収まる。
- [モデル生成スクリプト](../../tools/generate_reciprocating_compressor.py)から[シリンダーのOBJ](../../src/main/resources/assets/create_refinery_n_reactors/models/block/reciprocating_compressor/industrial_cylinder.obj)を再生成できる。変更時は、外装が上下の配管接続面や隣接する気筒にはみ出さないことを確認する。

## 確認

- OBJの寸法、4つの膨らみ、大型フランジの8辺、ボルト数を検査した。
- RTX 4060の開発クライアントで、回転動力につないだ3列6気筒を四方向から確認した。
- コアModの関連GameTest 10件、Ponderデータの静的検査、JARビルドが通過した。既存プレイヤーワールドは未確認。

## ゲーム内画像

- [斜め上](images/reciprocating-cast-head/reciprocating-cast-head-oblique.jpg)
- [正面](images/reciprocating-cast-head/reciprocating-cast-head-front.jpg)
- [側面](images/reciprocating-cast-head/reciprocating-cast-head-side.jpg)
- [背面](images/reciprocating-cast-head/reciprocating-cast-head-rear.jpg)
