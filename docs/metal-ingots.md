# 金属インゴット追加 引き継ぎ

## 実装内容
- 6種のインゴットを追加: アルミニウム・ニッケル・チタン・ジルコニウム・クロム・モリブデン（`<metal>_ingot`）。鉄は既存のバニラインゴットを使用するため追加していない。
- 通常の`Item`として`CEItems`へ登録し、`CECreativeModeTabs`へ追加。モデル・en/ja/zh翻訳・`c:ingots/<metal>`タグを追加。
- テクスチャは添付画像から切り出した16x16。バニラ`iron_ingot`のシルエットを透過マスクに使い、1ドット=約19pxのグリッド中心をサンプリングして生成。

## 主要ファイル
- `registry/CEItems.java`, `registry/CECreativeModeTabs.java`
- `assets/create_refinery_n_reactors/{textures,models}/item/<metal>_ingot.*`, `lang/*.json`
- `data/c/tags/item/ingots/<metal>.json`

## 確認
- `./gradlew compileJava processResources --offline` 成功。
- 未確認: ゲーム内の見た目（特にアルミ・ジルコニウムなど明るい色の輪郭）。

## 次回の注意
- 入手手段は [金属の鉱石と精錬](metals.md) で実装した。使い道はまだない。
- 実装コミットID: 下記コミット参照（コミットメッセージに記載）。
