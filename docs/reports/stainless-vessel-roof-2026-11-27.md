# ステンレス撹拌槽の天面駆動

対象は `lidded_vessel` と `vessel_stirrer`。バージョンは `0.4.9`。実装コミットは `46ad641ab3506e5ae8aea7f912a7ef2ba00431c8`。

- 3×3槽の点検窓とのぞき通路は、皿底が直胴に合流する高さ10 pxから上に付く。4枚の固定邪魔板を正面の窓から45°ずらした。底近くのタービンに天面から軸を通した。
- 天面に撹拌機を載せた槽は既存の直径30 pxの断面を天面まで通し、軸の周囲に開閉する円形のふたを表示する。空の手で撹拌機の脇の天面を右クリックして開ける。開閉と圧力判定には従来の槽の操作を使用する。丸い開口は描画に適用し、ブロックの当たり判定は従来のハッチと共通。
- 3×3・高さ2～4段の天面中央で駆動を判定する。旧設備は撹拌機を底から天面へ移設する。取り外しや再設置でも処理原料は槽に保存され、以前の下置きで回転を受けることはない。
- 天面駆動に合わせ、34件のPonder構造、対応する三言語字幕、手引き、製造ガイドを更新した。

## 検証

- Ponder構造チェッカーが通過。既存Ponderの35台すべてで、天面中央の撹拌機、横の歯車、回転源を確認した。
- Java 21でコンパイルし、全813件のGameTestが成功した。下置きの駆動を受け付けないこと、天面の歯車からの駆動、圧力ハッチと在庫保持を確認した。
- RTX 4060のクライアントで、`VesselWorldClientCheck`、`VesselContentsClientCheck`、日本語の`vessel_basin`と`vessel_internals`が通過した。
- リポジトリ全体の翻訳用静的チェッカーには、既存のPVCガイド `manufacturing_pvc.step_4.lesson_pages.0` の書式エラーが残っている。

[丸い開口と上置き撹拌機](images/stainless-vessel-roof/stainless-vessel-roof-overview.jpg) · [皿底と邪魔板](images/stainless-vessel-roof/stainless-vessel-roof-interior.jpg) · [Ponder](images/stainless-vessel-roof/stainless-vessel-roof-ponder.jpg)
