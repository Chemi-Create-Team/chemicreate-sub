# 3×3の大型メカニカルプレスと大型デポ

実装コミット `745b4cd9360e117daa505b63e6f57e9ac462d29c`。

- `CELargePress` で、作業台でクラフトできる3×3のプレス・デポと、大きな鉄の板を登録した。設備の中央を置くと `LargePressFootprint` が8マスの外周を配置する。デポの上面から設置すると、中央にそろえて1段分の作業空間を空ける。
- `LargeMechanicalPressBlockEntity` はCreateの `PressingBehaviour` を利用する。上面の回転軸の負荷は72 SU/RPM。3×3の作業空間と両設備の外周を確認し、ストロークの接触時に鉄ブロック1個を板1枚へ変換する。材料と完成品の在庫はCreateのデポと同じ保存形式で、大型デポの中央が保持する。
- `LargeMechanicalPressRenderer` はCreateのストロークを1.0625倍して金型を下げ、デポの表面まで届かせる。中央ピストンは金型に合わせて伸縮する。[組立の説明](../large-mechanical-press.md)

## 検証

- Gradle `build` 成功。GameTest 23件が通過し、外周の障害物による設置の拒否、実ファンネルでの投入とフィルター付き搬出、ストロークごとの個数、作業空間の閉塞、在庫の保存と解体時の回収を確認した。
- 配布JARにレシピ、モデル、テクスチャ、Ponder構造が含まれることを確認した。金型のモデル座標はMinecraftの許容範囲内。
- NVIDIA RTX 4060で起動した日本語クライアントで、JEIの鉄ブロックからの加工レシピと、Ponderの外観・組立・標準ファンネルの説明を確認した。[Ponder](../quality/large-mechanical-press/ponder.jpg) · [JEI](../quality/large-mechanical-press/jei.jpg)
- 標準の描画とCreate 6.0.10で検証した。検証環境ではワールド保存後のクライアント終了時にNVIDIA EGLで `SIGSEGV` が発生した。外部シェーダーとマルチプレイのチャンク境界での配置・解体は未検証。動くからくりへの組み込みは対象外。
