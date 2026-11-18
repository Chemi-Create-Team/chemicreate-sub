# バナーの質感と情報量の再調整

実装コミット: `7d9b7a752155a9de21ad83749f72542ca0353587`

23枚を内蔵 `image_gen` で個別に編集した。大きな器具の構図を維持し、段階的な陰影、縁のハイライト、接合部、細かなドットの質感を追加。平坦な8色の絵から最大24色へ増やし、細かい工場風景には戻さず、添付のSimulated／Cosmonauticsを参考にした密度へ調整した。

使用済み燃料・発破資材・核分裂炉・核融合炉の4枚はバイト単位で変更なし。暗い文字側、単一タブとその並び順、Javaの描画・操作処理も変更なし。`DESIGN.md`の外観方針を更新した。装置・操作の追加はなくPonderは変更していない。

## アセットと処理

- [ゲーム用PNG保存先](../../src/main/resources/assets/create_refinery_n_reactors/textures/gui/creative/)
- [全画像一覧](../quality/balanced-creative-banners/all-banners.png)
- [プロンプト・参照画像の役割・処理条件](../quality/balanced-creative-banners/prompts.json)
- [画像ハッシュと色数](../quality/balanced-creative-banners/assets.json)

生成時の黒い余白を除き、最近傍法で162×18へ正規化し、ディザなしで最大24色へ減色。ImageMagickはこの正規化と一覧作成に使用し、描画内容は内蔵画像生成ツールで編集した。実行時の画像生成・変換や追加依存はない。

## 確認と未確認

画像一覧を目視確認。全27枚が162×18で相互に異なり、変更した23枚は最大24色、指定4枚は前回と完全一致。`./gradlew --no-daemon assemble` 成功（41秒）。完成JAR内の全27枚が検査したハッシュと一致。[記録](../quality/balanced-creative-banners/assemble-check.txt)。

ユーザーの最小限のテストという指示に従い、実クライアント・GameTestは今回実施していない。ゲーム内の表示は未確認。次回は実画面の密度・読みやすさについてユーザーの確認結果を基準に調整する。
