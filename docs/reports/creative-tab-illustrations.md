# カテゴリー別ピクセルアートバナー

- 実装コミット: `b474ed21cb0fb67c946aec4b7fa449adcef78ad9`
- 対象: Minecraft 1.21.1 / NeoForge 21.1.219 / Create 6.0.10
- 依頼: 共通の見出し背景を廃止し、添付のCosmonautics／Simulatedのように各カテゴリーへ別々の横長ピクセルアート画像を用意する。

## 変更内容

全27カテゴリー（現在表示される26分類＋「その他」の予備分類）について、内蔵 `image_gen` で1カテゴリーにつき1回、個別に画像を生成した。配管はバルブと曲管、回転機械は歯車と軸、制御は計器、核融合はトカマク、鉱石は鉱脈など、それぞれ異なる内容を描く。色替えによる使い回しはしていない。

生成した画像の余白を除き、最近傍法で162×18ピクセルへ正規化し、ディザなし・最大32色のPNGとして保存した。実使用画像は27枚合計47,201バイト。元の高解像度画像をゲームで縮小表示する方式ではなく、実ファイル自体が小さなピクセル画像になっている。生成は内蔵ツール、余白処理・縮小・減色と一覧の作成はImageMagickを使った。外部API／画像生成CLIは使っていない。

バナー全幅に各画像を描き、分類名の幅に応じた半透明の暗いプレートを左側へ重ねる。共通の工場シルエットと代表アイテムの重ね描画は削除した。既存2タブ・登録一覧・検索対象・スクロールは維持した。新しい装置や操作は追加していないため、Ponderは変更していない。

## 保存先と主要ファイル

- ゲーム用PNG: [`textures/gui/creative/`](../../src/main/resources/assets/create_refinery_n_reactors/textures/gui/creative/)
- [全27枚の一覧](../quality/creative-tab-illustrations/all-banners.png)
- [最終プロンプト群と正規化条件](../quality/creative-tab-illustrations/prompts.json)
- [各PNGのサイズ・SHA-256](../quality/creative-tab-illustrations/assets.json)
- `registry/SectionedCreativeTab.java`: 各見出しへカテゴリー固有のテクスチャIDを設定。旧アイコン・件数の不要フィールドを削除。
- `client/CreativeSectionBanners.java`: 見出しの共通図形描画をPNGの描画へ置換。文字プレートの幅だけ実際の分類名から求める。
- `client/CreativeTabsClientCheck.java`: 現在の全分類について画像リソースが存在することも確認。
- `DESIGN.md`: カテゴリーごとの画像を使う方針へ更新。

見出し用の空行と9列のレイアウト計算は前回実装を維持した。画像のIDと分類名はタブ構築時に用意し、毎フレームの画像生成・ファイル読込・色調変換は行わない。

## 確認結果

| 確認 | 結果 |
| --- | --- |
| `./gradlew --no-daemon build` | 成功（1分43秒）。既存FluidPhase 25件、HaberBosch 1,019件、ReactorPhysics 1,668件、ReactorControlを含む |
| PNG検査 | 全27枚が162×18、最大32色、全SHA-256が異なる |
| 完成JAR | 全27枚を同梱し、ファイルのハッシュが検査対象PNGと一致 |
| 翻訳検査 | 日英中各2,571キー、重複・空欄・書式不一致なし |
| 実クライアント・設備タブ | 134アイテム、12分類。画像リソースあり・登録内容の一致・見出し行との重複なし |
| 実クライアント・素材タブ | 197アイテム、14分類。同上 |
| スクロール・標準検索・バニラタブ | 合格。検索結果へ装飾用空要素を混ぜず、通常タブの一覧も保持 |
| 実画面 | 放射線計測・建材・制御・容器・液体の異なる画像と文字の読みやすさを確認 |

[結果JSON](../quality/creative-tab-illustrations/result.json)、[ビルド記録](../quality/creative-tab-illustrations/build-check.txt)、[クライアント記録](../quality/creative-tab-illustrations/client-check.txt)。日本語・GUI倍率3・1280×800、NVIDIA RTX 4060のハードウェア描画で確認。標準画面の入力ハンドラーを使う明示的な検証モードで操作し、computer-useで実画面を撮影した。

- [設備タブ](../quality/creative-tab-illustrations/equipment.jpg)
- [スクロール後の制御・計装](../quality/creative-tab-illustrations/equipment_scrolled.jpg)
- [素材タブ](../quality/creative-tab-illustrations/materials.jpg)
- [標準検索](../quality/creative-tab-illustrations/search.jpg)

`CREATIVE TABS PASSED` と結果JSONの保存後、終了処理でNVIDIAの `libEGL_nvidia.so.0` がSIGSEGVを起こし、`runClient` のGradleタスク自体は失敗終了した。画面検証の成功と、終了時のドライバークラッシュは区別する。

再現（JDK 21）:

```sh
mc-debug ./gradlew --no-daemon -I docs/quality/creative-tab-illustrations/client.gradle runClient
```

## 未確認事項・次回の注意

Sable／Aeronautics本体との同時起動、英語・中国語での実画面、専用サーバー起動と全GameTestは未実施。描画変更に絞って確認した。今回のPNGは静止画。新しい分類キーを増やす際は対応するPNGを同じディレクトリへ追加し、クライアント検証で参照を確認する。実行時の画像生成や追加依存は不要。
