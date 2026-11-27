# 3×3ステンレスタンクの内装

対象は `lidded_vessel`。バージョンは `0.4.8`。実装コミットは `e16520abee82806aa27917a44433f07dd6316136`。

- `LiddedVesselInterior.java` が内径30 pxの円筒と、直径12 pxから30 pxに広がる8区間の皿底を描画する。各断面は32分割し、頂点法線を連続させた。既存の内壁スプライトを直胴で16 pxごとに繰り返し、窓が付いた段だけ対応する内壁を開いて四角い通路につないだ。
- 液体・気体の側面と液面は同じ断面から作り、皿底の途中にある液面も内壁に沿う。量から高さを求める式は従来どおり。アイテムの描画範囲も断面に収めた。
- `generate_lidded_vessel.py` が最下段の4枚羽根のラシュトンタービン、底面の軸受、撹拌翼のアイテムモデルを生成する。軸は天板の下まで通り、4枚の固定邪魔板は壁から1 px離して取り付けた。外装、窓の位置、ハッチ、はしご、当たり判定、容量、処理速度、レシピ、1×1・2×2の描画は従来どおり。
- 22 px四方のハッチは四隅だけ半径15 pxをわずかに越える。既存の開口寸法を保ち、天板の下面で円筒の縁と開口の四隅をつないだ。
- `vessel_internals` のPonderにハッチからの観察手順と3言語の説明を追加した。実ワールドの既存検証は、槽の下へ別体の撹拌機を設置する現行仕様に合わせた。

## 確認結果

- 生成スクリプトを再実行し、4件の対象モデルの差分と再生成結果が一致した。外装テクスチャの画素・ファイルも一致した。
- Java 21のコンパイルと全813件のGameTestに成功した。専用サーバーの検証用ワールドは独立した作業ディレクトリに作った。
- RTX 4060のMinecraftクライアントで `VesselWorldClientCheck` と `VesselContentsClientCheck` が成功した。窓付き、窓なし、冷却時、半分・満量・空、撹拌機の駆動、3×3の上下の接続テクスチャを確認した。
- 同クライアントで `vessel_internals` と `vessel_basin` のPonderを表示した。アイテム表示のタービンが枠に収まり、槽の窓とハッチから円筒・皿底・邪魔板・液面を確認した。本文とページの一致は3言語で確認し、実行時の言語は日本語を使用した。
- リポジトリ全体の独立した言語検査には、既存のPVC製造ガイド `manufacturing_pvc.step_4.lesson_pages.0` の書式エラーが残っている。依頼書の `CLAUDE.md` はリポジトリ内に見つからなかったため、`AGENTS.md` と `DESIGN.md` に従った。
- 別のシェーダーパックを有効にした描画と、多数の槽を並べた場合のフレームレート比較は未測定。

[ハッチから見た内装](images/stainless-vessel-interior/stainless-vessel-interior.jpg) · [円形の液面](images/stainless-vessel-interior/stainless-vessel-liquid.jpg) · [のぞき窓](images/stainless-vessel-interior/stainless-vessel-window.jpg) · [Ponder](images/stainless-vessel-interior/stainless-vessel-ponder.jpg)
