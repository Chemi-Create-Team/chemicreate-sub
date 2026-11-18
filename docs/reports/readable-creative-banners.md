# 模様に種類を見分ける手掛かりを追加

実装コミット: `2ddad04ac41e920af2985da7539c9852641a3714`

3分類と抽象的な構成を維持し、設備は歯車の穴・歯と曲管、素材は鉱石片の面と波・少数の泡、建材はブロックの目地を追加した。暗い文字側、控えめな陰影とドットの質感を保ち、細かな器具や写実的な描写へ戻していない。分類・表示ロジックは変更なし。

内蔵 `image_gen` で現在の3枚を個別に編集。黒い余白を除いて最近傍法で162×18へ正規化し、ディザなし・最大16色に減色した。ImageMagickは正規化と一覧作成に使用。`DESIGN.md`を最終表現へ更新した。装置や操作の変更はなくPonderは変更していない。

- [ゲーム用PNG](../../src/main/resources/assets/create_refinery_n_reactors/textures/gui/creative/)
- [画像一覧](../quality/readable-creative-banners/all-banners.png)
- [プロンプト・参照画像の役割](../quality/readable-creative-banners/prompts.json)
- [画像情報](../quality/readable-creative-banners/assets.json)

3枚のサイズと色数を確認し、画像一覧を目視確認。`./gradlew --no-daemon assemble` 成功（38秒）。完成JAR内の3枚が検査したハッシュと一致。[確認記録](../quality/readable-creative-banners/assemble-check.txt)。

最小限のテストという指定に従い、今回も実クライアント・GameTestは実施していない。ゲーム内の見た目は未確認。
