# バナーの光沢を抑えたピクセル表現

実装コミット: `b6188aaa2e8d0389df501fdd6eb8bdf2ba8c076f`

23枚を内蔵 `image_gen` で個別に編集。現在の構図と細部を保ち、白い光沢・厚い縁・深い影を抑えた。近い明度の段階的な陰影と1～2ピクセルのまとまりを強め、継ぎ目や粒状の質感を残した。前々回の単色シルエットには戻していない。

使用済み燃料・発破資材・核分裂炉・核融合炉の4枚はバイト単位で変更なし。暗い文字側、単一タブと並び順、Javaの処理も維持。`DESIGN.md`の表現方針を更新した。機能や操作の変更はなくPonderは変更していない。

- [ゲーム用PNG](../../src/main/resources/assets/create_refinery_n_reactors/textures/gui/creative/)
- [全画像一覧](../quality/pixel-creative-banners/all-banners.png)
- [生成プロンプト・参照画像の役割](../quality/pixel-creative-banners/prompts.json)
- [色数とハッシュ](../quality/pixel-creative-banners/assets.json)

生成画像の黒い余白を除き、ImageMagickの最近傍法で162×18へ正規化し、ディザなしで最大18色へ減色。描画内容は内蔵画像生成ツールで編集した。実行時の画像変換や追加依存はない。

画像一覧を目視確認。全27枚が162×18で相互に異なり、編集した23枚は最大18色、指定4枚は変更前と完全一致。`./gradlew --no-daemon assemble` 成功（25秒）。完成JAR内の全27枚が検査したハッシュと一致。[確認記録](../quality/pixel-creative-banners/assemble-check.txt)。

最小限のテストというユーザー指定に従い、今回も実クライアント・GameTestは実施していない。ゲーム内での見た目は未確認。次回の調整でも現在の構図を維持し、ユーザーの見え方を基準にする。
