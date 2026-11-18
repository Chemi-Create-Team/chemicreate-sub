# Create: Deep Seas・Sable・Create Aeronautics との連携 レポート

## 基準と実装コミット

- 作業ブランチ：`main`
- 作業開始時の基準コミット：`d4f6495`
- 実装コミット：`7397cfe`

## 依頼と決定事項

- 依頼：Create Aeronautics（Sable）とそのアドオン Create: Deep Seas との連携。必須の依存にはせず、同時に入れたときに効く。ステンレス鋼・チタンのブロックを新しく作り、潜水艦に使えて、Base depth（基準深度）を高くする。
- ユーザーの決定：ブロックはステンレス鋼とチタン。基準深度はステンレス鋼600、チタン1200。

## 実装内容

- ブロック（`registry/CEMetals.java`）：ステンレス鋼ブロック・チタンブロック。鉄ブロックの性質をそのまま使う。クリエイティブタブ（`CECreativeModeTabs`）に追加。
- アセット・データ（`tools/generate_metal_blocks.py`）：
  - テクスチャ：ステンレス鋼は暗い枠と4本のボルトに囲まれたヘアライン仕上げの板、チタンは暗い溶接目でつないだ青灰色の板4枚。
  - モデル・ブロックステート・ルートテーブル。
  - レシピ：インゴット9 → ブロック、ブロック → インゴット9。
  - タグ：`c:storage_blocks/<金属>`・`c:storage_blocks`（ブロック・アイテム）、ツルハシ、鉄のツール以上、ビーコンの土台。
  - 翻訳（英・日・中）。
- Deep Seas（`create_submarine`）：
  - `compat/deepseas/SubmarineHulls.java`：基準深度と圧壊の確率（ステンレス鋼600・0.05、チタン1200・0.03）。
  - `mixin/deepseas/HullStrengthConfigMixin.java`：`HullStrengthConfig.buildStaticDefaults` の最後で、既定表に2つを足す（`putIfAbsent`）。Deep Seas の自動計算は名前空間の外の金属を400で打ち切るため、自動計算ではなく既定表に入れた。設定ファイルの値が優先される。
  - `mixin/deepseas/DeepSeasMixinPlugin.java` と `create_heavy_indusstry.deepseas.mixins.json`（`required: false`）：Deep Seas がないときはMixinを読み込まない。
  - `neoforge.mods.toml`：任意の依存（`create_submarine` 3.4.7以降）と2つ目のMixin設定。
- Sable：`data/create_heavy_indusstry/physics_block_properties/` に重さ（ステンレス鋼4.0＝鉄と同じ、チタン2.3＝鉄の約57％）。`c:storage_blocks` の既定（`sable:super_heavy`、4.0）より優先度を上げた（1001）。
- ビルド（`build.gradle`・`gradle.properties`）：Deep Seas を `compileOnly`。`-PwithDeepSeas=true` のとき Sable・Create Aeronautics・Deep Seas を実行時に入れる。
- Ponder「ステンレス鋼とチタンの耐圧殻」（`tools/generate_submarine_hull_ponder.py`、4手順）。2つのブロックから開く。
- 文書：新規 `docs/deepseas-compat.md`。`docs/metals.md`、`PONDER_GUIDE.md`、`README.md` を更新した。

## テスト

| 確認 | 結果 |
| --- | --- |
| `bash gradlew build runGameTestServer` | 306件すべて合格 |
| `bash gradlew -PwithDeepSeas=true -Pneo_version=21.1.228 build runGameTestServer`（Sable 2.0.6・Create Aeronautics 1.3.2・Deep Seas 3.4.7） | 306件すべて合格。ログに「Create: Deep Seas base depth: stainless steel 600, titanium 1200」。`config/submarine_hull.json` に2つの値が書き出された。Sable の読み込みエラーなし |
| CIの検証スクリプト4本 | 合格 |
| 実クライアント（日本語） | Ponder全97場面の題名が合格。「ステンレス鋼とチタンの耐圧殻」を撮影して目視した |

- `MetalBlockGameTests.theHullBlocksStoreTheirMetalAndHoldDeepWater`：登録・貯蔵ブロックのタグ・レシピ。Deep Seas があるときは `HullStrengthConfig.getFor` の基準深度が600・1200。

## 未検証・制約

- 実際に潜水艦を組んで潜る確認はしていない（基準深度の値は Deep Seas の表から読んで確認した）。
- Sable の重さは、データがエラーなく読み込まれたことだけを確認した。物理演算での重さは測っていない。
- Sable・Create Aeronautics・Deep Seas は NeoForge 21.1.228 以降が必要。開発環境の 21.1.219 では入れられない。
- Deep Seas の内部クラス（`HullStrengthConfig`）にMixinしているため、Deep Seas の更新で表の作り方が変わると効かなくなる。GameTest（`-PwithDeepSeas=true`）で確かめる。

## 次回の注意

- 耐圧殻にする金属ブロックを増やすときは、`SubmarineHulls.HULLS` に値を足す。
- Deep Seas を更新したら、`gradle.properties` の `deepseas_version_id` を変え、`-PwithDeepSeas=true -Pneo_version=21.1.228` でGameTestを流す。
