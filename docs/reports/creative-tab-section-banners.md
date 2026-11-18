# クリエイティブタブのカテゴリーバナー

- 実装コミット: `315c22b24136eb8e300214bccca9c171756e769b`
- 対象: Minecraft 1.21.1 / NeoForge 21.1.219 / Create 6.0.10
- 依頼: Sable・Create Aeronauticsのような、分類を視覚的に区切るクリエイティブタブ装飾。

## 実装

既存の「設備・建材」「素材・流体」の2タブへ、分類名・代表アイテム・工場のシルエットを付けた162×18ピクセルの見出しを追加した。鋼材色を基調とし、銅、冷却系の青緑、燃料系の真鍮色などを差し色にした。分類の途中からスクロールしても、次の見出しが標準のスロット行に沿って表示される。長いタブ名は表示幅に合わせて末尾を省略する。

分類は従来のアイテムグループを再利用する。登録ID・2タブの役割・実アイテム一覧を維持し、容器・液体・気体、放射線計測を区別した。日英中28個の分類名を追加。新しい画面・装置・操作方法は追加していないためPonderの追加対象はない。

外部MODのコードや画像は同梱していない。SableやAeronauticsを必須依存にせず、Minecraft標準画面内に独自のバナーを描画する。

## 主要ファイルと処理

- `registry/CECreativeModeTabs.java`: 既存2タブに専用タブ型を設定し、既存グループから分類キーを決定。
- `registry/SectionedCreativeTab.java`: 実アイテムを分類ごとにまとめ、見出し用の空行と行末の空きを含む表示用リストを作る。分類情報は内容構築時にキャッシュ。
- `client/CreativeSectionBanners.java`: 標準スロット上に鋼材プレート、差し色、分類名、既存アイコンを描画。
- `mixin/CreativeInventorySectionsMixin.java`: クライアント限定。自MODのタブを選んだ場合だけ画面の表示リストを置き換え、ツールチップ描画前にバナーを描く。通常APIには表示リスト差し替えのフックがないため限定的にMixinを使う。
- `client/CreativeTabsClientCheck.java`: 明示的に有効化したときだけ標準画面のタブ選択・スクロール・検索を検証。通常プレイでは起動しない。
- `DESIGN.md`: 標準クリエイティブ画面内の装飾方針を追記。

9列で各分類の先頭に9スロット分の空行を入れ、分類末尾を9の倍数へ切り上げる。最初の表示行はMinecraftと同じ `max(round(scroll × (総行数 − 5)), 0)`。表示行内の見出しだけを描く。`getDisplayItems()` と検索対象は実アイテムのみを返し、装飾用の空要素を公開一覧に混ぜない。別MOD・バニラのタブには置き換えを適用しない。

## 確認結果

| 確認 | 結果 |
| --- | --- |
| `./gradlew --no-daemon build` | 成功。既存のFluidPhase 25件、HaberBosch 1,019件、ReactorPhysics 1,668件、ReactorControlを含む |
| `python3 tools/validate_translations.py` | 日英中各2,571キー。重複・空欄・書式不一致なし |
| 実クライアント・設備タブ | 実アイテム134点、12分類、表示306スロット。実アイテム集合の一致・見出し行との重複なし |
| 実クライアント・素材タブ | 実アイテム197点、14分類、表示378スロット。同上 |
| 標準マウスホイール処理 | スクロール後の見出し位置を実画面で確認 |
| 標準検索 | ローカライズされたポリエチレン名で実アイテムを取得。装飾用空要素なし |
| バニラタブへ切替 | 標準の一覧を保持。装飾用空要素なし |

実画面は日本語、GUI倍率3、1280×800、NVIDIA RTX 4060のハードウェア描画。専用クライアント検証はネイティブ画面のクリック・スクロール・文字入力ハンドラーを通し、スクリーンショットはcomputer-useで撮影した。結果JSONは[こちら](../quality/creative-tabs/result.json)、ビルド記録は[こちら](../quality/creative-tabs/build-check.txt)。

- [設備タブ](../quality/creative-tabs/equipment.jpg)
- [スクロール後](../quality/creative-tabs/equipment_scrolled.jpg)
- [素材タブ](../quality/creative-tabs/materials.jpg)
- [標準検索](../quality/creative-tabs/search.jpg)

検証ログに `CREATIVE TABS PASSED` が出て結果JSONを保存した後、終了処理でNVIDIAの `libEGL_nvidia.so.0` がSIGSEGVを発生させ、`runClient` 自体の終了コードは134となった。画面検証の成功とプロセス終了の失敗は区別する。[該当ログ](../quality/creative-tabs/client-check.txt)。

再現コマンド（JDK 21）:

```sh
mc-debug ./gradlew --no-daemon -I docs/quality/creative-tabs/client.gradle runClient
```

検証用の新規フラットワールドを作り、各段階を約400クライアントtick表示してから終了する。保存済み実画面は同等の検証設定で取得した。初回試行では日本語環境に英語の検索語を渡して空になったため、検証側をローカライズされたアイテム名へ修正して再実行した。

## 参照した実装

- [Sable公式リポジトリ](https://github.com/ryanhcode/sable) / [公式配布](https://modrinth.com/mod/sable): `2.0.6+mc1.21.1`。
- [Create Aeronautics公式配布](https://modrinth.com/mod/create-aeronautics): `1.3.2+mc1.21.1`。
- [Simulated公式リポジトリ](https://github.com/Creators-of-Aeronautics/Simulated-Project): Aeronautics同梱の `simulated-neoforge-1.21.1-1.3.2.jar` 内のカテゴリーバナーを調査。見出しは9スロット幅・1行分。実際の仕組みはSable本体ではなく、この同梱モジュールにある。
- APIは解決済みNeoForge 21.1.219のMinecraftソースで `CreativeModeTab` / `CreativeModeInventoryScreen` を確認した。

## 未確認事項・次回の注意

- Sable / Aeronautics本体を同時導入した環境では未実行。タブ型を限定した独立実装であり、それらの専用APIは呼ばない。
- 英語・中国語は翻訳検査のみ。実画面の見た目は日本語で確認した。
- 専用サーバー起動と全GameTestは今回実施していない。共通側のタブ型からクライアント専用クラスは参照しない。
- Minecraft / NeoForge更新時は、標準画面のリスト更新・行数計算・ツールチップ描画呼出箇所を再確認する。
