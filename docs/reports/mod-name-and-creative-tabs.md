# Mod名変更・クリエイティブタブ整理・大型フライホイール統合

## 実装内容

- 表示名を「Create: Reactor 'n' Refinery」に変更（`gradle.properties`の`mod_name`、`pack.mcmeta`、README見出し、タブ名3言語）。mod ID`create_heavy_indusstry`・パッケージ・登録IDは変更なし。
- 開発者名`sansemao`を`Mhikeneko`に変更（`mod_authors`）。
- クリエイティブタブを登録名のアルファベット順からカテゴリ順に変更（`registry/CECreativeModeTabs.java`）。
  - 素材・流体：空の耐食合金バケツ → 液体入りバケツ（登録順、CDGの原油・ガソリン・軽油を含む）→ 気体入りバケツ → 鉱石ブロック → 鉱物 → 粉末・化学品 → 金属 → 樹脂・ゴム → 触媒・ろ材 → 部品 → 核燃料 → 使用済み燃料・廃棄物 → 爆薬 → 防護服・ガスマスク・石けん。
  - 設備・建材：建材 → 配管 → 弁・計器・制御 → タンク・容器 → 動力 → 加熱・冷却 → 化学設備 → 原子炉 → 再処理 → 核融合 → 道具・その他。
  - 防護服4種とガスマスクを素材・流体タブへ移動。蛇紋岩と四酸化三鉄の山も素材側へ移動。
  - 一覧にない新規アイテムは、液体・気体バケツは該当グループへ、それ以外はブロックなら設備・建材、アイテムなら素材・流体の末尾に自動で並ぶ。
- 大型フライホイール：既定の`large_flywheel`を黄色の外観にし、重複する`yellow_large_flywheel`の登録・モデル・ブロックステート・テクスチャ・レシピ・ルート・タグ・翻訳を削除。タブには黄色1種だけを表示する。設置後に染料で右クリックすると色が変わり、黄色の染料で既定に戻る（既存の`LargeFlywheelBlock.useItemOn`を利用）。染色済み15色はタブに出さず、壊すとその色で落ちる。
- 作業台で染料と組み合わせるレシピは従来どおり残した（黄色版は既定品をメカニカルクラフターでのみ作る規則に合わせて削除）。

## 主要ファイル

`registry/CECreativeModeTabs.java`、`registry/CEBlocks.java`、`registry/CEItems.java`、`gametest/CreativeCatalogGameTests.java`、`gametest/AuditGameTests.java`、`gametest/RotationalEquipmentGameTests.java`、`tools/generate_plant_tutorials.py`、`textures/block/large_flywheel.png`。

## 確認

- compileJava成功。
- GameTest：CreativeCatalog、RotationalEquipment（染料で赤に変わり軸を保持、黄色で既定に戻る新規テストを含む）、CraftingRecipe、MalformedData、PlantTutorialが合格。
- AuditGameTestsは失敗。液体のワールドブロック43種の`block.*`翻訳（英・日）がないためで、`55b7cd3`（統合前のmain）の時点ですでに欠けていた。今回の変更による欠落はない（タブ漏れの確認は合格）。
- 未確認：実際のクライアント画面でのタブ表示と黄色フライホイールの見た目。

## 注意

- `yellow_large_flywheel`を置いた既存ワールドでは、そのブロックが消える（本格運用前のため旧登録は残していない）。
- 実装コミット：このレポートと同じコミット。
