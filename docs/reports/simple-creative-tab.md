# バナーの簡略化とクリエイティブタブ統合

実装コミット: `76db96893b000d9095cd19835a2349901842086e`

## 変更

- 使用済み燃料・発破資材・核分裂炉・核融合炉の4枚のPNGは変更しない。
- 残る23枚を内蔵 `image_gen` で個別に再生成。大きな形と最大8色に絞り、細かい背景や小物を省いた。左約60%は暗い文字用背景。全バナー共通の文字プレートも暗くした。
- 162×18ピクセルのPNGへ最近傍法で正規化。実行時の生成や画像変換は行わない。
- タブを `base` の1つに統合。「設備 → 素材・流体 → 建材 → 放射線計測」の順とし、素材・流体の内部順序は維持した。旧 `materials` タブ登録と日英中の不要なタイトルを削除。
- 表示用の見出し行だけを挿入し、実アイテム一覧と検索対象へ装飾用空要素を混ぜない既存の処理を維持。

## 主要ファイル

`CECreativeModeTabs.java`（単一タブ・配列順）、`SectionedCreativeTab.java`（統合後の分類）、`CreativeSectionBanners.java`（文字背景）、`textures/gui/creative/*.png`（23枚を差し替え）。既存の `CreativeCatalogGameTests` とクライアント検証モードも単一タブの仕様へ更新した。`DESIGN.md` と日英中のタブ名も更新。装置や操作の追加はなく、Ponderは変更していない。

- [全画像](../quality/simple-creative-tab/all-banners.png)
- [生成プロンプト・処理条件](../quality/simple-creative-tab/prompts.json)
- [画像のハッシュと色数](../quality/simple-creative-tab/assets.json)
- [ゲーム用PNG保存先](../../src/main/resources/assets/create_refinery_n_reactors/textures/gui/creative/)

## 確認

`./gradlew --no-daemon build` 成功（52秒、既定のビルド内チェックを含む）。翻訳検査は日英中各2,570キーで合格。全27画像のサイズが162×18、簡略化した23枚が最大8色、指定4枚のバイト列が変更前と完全一致することを確認した。画像一覧を目視確認済み。[ビルド記録](../quality/simple-creative-tab/build-check.txt)。

ユーザー指定に従いテストプレイは実施していない。更新したGameTestとクライアント検証モードも未実行。単一タブでの実画面・スクロール・検索はユーザーによるデバッグ対象。今回の画像は静止画。
