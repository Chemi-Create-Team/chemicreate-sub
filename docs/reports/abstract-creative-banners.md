# 3分類への統合と抽象模様のバナー

実装コミット: `1a1708280b7e7364710debe890df187d3bcaf720`

単一のクリエイティブタブを「設備・道具」「素材・流体」「建材」の3見出しへ統合した。核分裂炉・核融合炉・配管・槽・計測器・保護具は設備側、鉱石・粉・金属・燃料・使用済み燃料・発破資材・液体・気体は素材側。建材は末尾に置き、鋼材ブロックも建材へ含めた。登録アイテム一覧自体は維持する。

器具を描く画像から、抽象的な歯車の形・色の帯・波・矩形などの装飾模様へ変更。3枚を内蔵 `image_gen` で編集し、控えめな陰影とドットの質感、暗い文字側を残した。最新の分類統合に合わせ、従来の炉別・発破・放射線計測などの独立した見出しと不要なPNG・翻訳を撤去した。

## 主な変更

- `CECreativeModeTabs.section`: 分類結果を3種類に限定。表示時に同じ分類へまとめる既存処理で全アイテムを集約。
- `CreativeCatalogGameTests`: 見出しが3つ・順序が設備→素材→建材であることを確認する仕様へ更新。
- 日英中の分類名と `DESIGN.md` を更新。装置・操作の追加はなくPonderは変更していない。
- [ゲーム用PNG](../../src/main/resources/assets/create_refinery_n_reactors/textures/gui/creative/) / [画像一覧](../quality/abstract-creative-banners/all-banners.png) / [プロンプト](../quality/abstract-creative-banners/prompts.json) / [画像情報](../quality/abstract-creative-banners/assets.json)

黒い余白を除き、最近傍法で162×18に正規化してディザなし・最大14色へ減色した。正規化と一覧作成はImageMagick、描画内容の編集は内蔵画像生成ツールを使用。実行時の画像生成・変換はない。

## 確認と未確認

`./gradlew --no-daemon assemble` 成功（37秒）。翻訳検査は日英中各2,546キーで合格。完成JARのバナーPNGは現行3枚のみで、検査したハッシュと一致。画像一覧を目視確認。[確認記録](../quality/abstract-creative-banners/assemble-check.txt)。

最小限のテストという指定に従い、今回も実クライアント・GameTestは実施していない。更新した分類GameTestはコンパイル確認のみ。実際のスクロール・見た目は未確認で、ビルド成功をプレイ確認の代わりとはしない。
