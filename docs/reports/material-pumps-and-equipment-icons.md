# 材質別ポンプ・配管部品廃止・設備アイコン修正

実装コミット：9eb05d86d8cb279b26af86f5ebecca960541b1f5

## 実装

- 耐圧鋼、ニッケル合金、超耐圧、ジルコニウム、ステンレス鋼、保冷、塩ビ、樹脂、気体、大型気体のポンプを追加。登録IDはそれぞれの配管名の `_pipe` を `_pump` に置き換えたもの。
- 作業台でCreate標準のメカニカルポンプ1個と対応する配管1個から1個を製作。クリエイティブタブの配管群の直後へ配置。
- `MaterialPumpBlock` は解決済みCreate 6.0.10の `PumpBlock` を継承し、同じ `PumpBlockEntity` を使用する。両端接続、歯車からの回転、レンチによる逆送、停止、保存、ネットワーク更新をCreateへ委ねる。独自の搬送tickや流体在庫は追加していない。
- 応力はCreateの `BlockStressValues.IMPACTS` に標準メカニカルポンプの値を返すSupplierを登録。標準ポンプの設定を引き継ぐ。材質による流体制限は既存配管側の判定を維持し、新ポンプだけの制限は設けていない。
- 本体・回転歯車・アイテムはCreateのポンプモデルを継承し、対応する配管の既存画像を参照する。`MaterialPumpRenderer` は材質ごとのPartialModelを起動時に登録し、Createの回転描画・CachedBuffersを使用する。高解像度化や新しい画像の複製は行わない。
- 各ポンプにPonderを登録。両端の接続、歯車駆動、レンチ逆送、停止を示し、気体用2種には開放端からの吸気を追加。
- `pipe_support` と `air_filter` のブロック・アイテム・BE・レシピ・loot・モデル・テクスチャ・翻訳・採掘タグ・Ponder対象を削除。生成スクリプト、建材資料、空気分離の説明、到達性・硝酸工場テストも追従。過去の完了レポートは履歴として維持。

## エアフィルター廃止後の空気

`WorldLiquidOpenEndHandlerMixin` のCreate開放端ハンドラーに処理を追加。

- 管本体が `GasPipeBlock`（通常・大型）で、隣の開放端が空気ブロック、内部バッファが空の場合だけ大気を返す。
- 要求量は0～1,000 mB/回に制限。流体フィルターが違えば拒否し、SIMULATEでは状態を変更しない。実際の搬送はCreateの駆動ポンプが管理する。
- 非気体配管の空間からの吸入は変更しない。塞がれた入口・水中の入口から大気を生成しない。
- 空気を開放端へ排出した場合は大気へ戻す。Create標準処理へ空気の水源ブロックを生成させない。受入先が塞がれている場合や異種の内部バッファが残っている場合は拒否する。
- フィルターを使っていた製造Ponderの圧縮機吸入側も、開放した気体パイプへ変更した。

## アイコン

復水器5種、計器4種、抽出塔4種、ガラス固化関連5種、コールドボックス2種の計20種に、ブロック表示の継承不足が残っていた。関連する状態・可動部分も含む91モデルへ `minecraft:block/block` の親を設定し、既存の形状・画像・UVは維持した。91件すべてを変更前のJSONと比較し、追加した親以外のデータが同一であることを確認。

横置き液体タンクと横置き管束ボイラーの2種は、実物と同じCreateタンク外装を横向きで表示するアイテムモデルに統一。各生成スクリプトも更新した。新ポンプ10種と合わせ、32種を実際のインベントリ画面で確認した。

## 主要ファイル

- `src/main/java/net/createplant/registry/CETransport.java`：ポンプ10種、共通BE、応力登録。
- `block/MaterialPumpBlock.java`、`client/MaterialPumpRenderer.java`：Create標準ポンプとの接続と材質別描画。
- `ponder/MaterialPumpPonderScenes.java`、`ponder/ElectrolysisPonderPlugin.java`：10種のPonderと旧部品参照の削除。
- `mixin/WorldLiquidOpenEndHandlerMixin.java`：大気吸入・空気の排気。
- `tools/generate_material_pumps.py`：モデル・アイテム・レシピ・loot・翻訳・Ponder構造の生成元。
- `tools/generate_plant_systems.py`、`tools/generate_fusion_fuel.py`、`tools/generate_horizontal_tank.py`、`tools/generate_industrial_expansion.py`：アイコン修正の生成元。
- `gametest/MaterialPumpGameTests.java`、`client/MaterialPumpClientCheck.java`：搬送回帰テストと実画面確認用の任意起動fixture。
- 仕様：`docs/material-pumps.md`。

## 検証

Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10、Java 21。

| 確認 | 結果 |
| --- | --- |
| Gradle build | 成功。原子炉物理1,668項目と制御テストも成功 |
| 専用GameTestサーバー | 17件すべて成功 |
| 新ポンプ10種 | 対応配管、実歯車駆動、停止中の非搬送、各1,000 mBの全量搬送と保存、非ゼロの応力を確認 |
| 逆送・再接続・再生成 | レンチ逆送、切断時の保持、再接続後の逆送、BEを作り直してNBTを復元した後の回転・流体総量を確認 |
| 大気 | 通常・大型管、駆動・停止、閉塞、水没、異種フィルター、0量、1,000 mB上限、SIMULATEの非変更、排気時の非水源化を確認 |
| 既存工程 | 気体パイプ、世界液体の排出入、建材の加工とハーフのdrop、空気から硝酸までの物量収支、サバイバル到達性の回帰に成功 |
| 実クライアント | RTX 4060で本体・回転歯車・配管接続10種と、32種の標準インベントリアイコンを目視確認 |
| Ponder | 日本語の実クライアントで10個のアイテムから開き、全10表示対象を描画。実行時タイトル・共有文言の検証も成功 |
| 静的資産 | 全リソースJSONの構文、変更生成スクリプトの構文、旧IDのソース・リソース参照とNBTパレットからの消去、差分の空白チェックを確認 |
| 既存の共通画像検査 | 既知の素材分類9件で未通過。今回の追加による増加はない（下記） |

再現用のGradle initは `docs/quality/material-pumps/` の `server.gradle`、`client.gradle`、`ponder.gradle`。通常プレイではfixture用のシステムプロパティを設定しない。

```bash
./gradlew --no-daemon build
./gradlew --no-daemon -I docs/quality/material-pumps/server.gradle runGameTestServer
mc-debug ./gradlew --no-daemon -I docs/quality/material-pumps/client.gradle runClient
mc-debug ./gradlew --no-daemon -I docs/quality/material-pumps/ponder.gradle runClient
```

この環境では `JAVA_HOME=/workspace/.local/jdk21`、`GRADLE_USER_HOME=/workspace/.gradle` を使用。ハードウェア描画は `mc-debug glxinfo -B` のNVIDIA GeForce RTX 4060表示で確認した。

## 制約・引き継ぎ

- 画像の添付は入力ディレクトリに届いていなかったため、全ブロックアイテムの参照を調べて残っていた具体的な表示不整合を修正した。
- 旧2ブロックは登録自体を削除した。既存ワールド内の旧ブロック・旧アイテムを保持する互換登録は設けていない。
- 保存確認は同一ワールドでBEを削除・再生成してNBTを復元したもの。クライアントをまたぐ実ワールド再読込や全6方向の個別目視確認は行っていない。向き・保存の実装はCreate標準を継承する。
- 英語・日本語・中国語のキーを追加したが、Ponder実行時の言語確認は日本語で行った。
- 共通画像検査 `tools/validate_visual_identity.py` は従来の `heated_unfinished_steel`、`high_pressure_turbine_unit`、`iron_pipe`、`irradiated_cartridge`、`irradiation_cartridge`、`jacketed_pipe`、`low_pressure_turbine_unit`、`pig_iron`、`steel_slag` が素材マニフェストで未分類のため停止する。今回の対象とは別であり、この検査全体の成功とは扱わない。
- このコンテナのGPUクライアントは、画面確認または `CLIENT QUALITY PASSED` 後の終了時に `libEGL_nvidia.so.0` のSIGSEGV（exit 134）を出す。`runClient` タスク全体の成功とは報告しない。専用サーバーの17件と `build` は正常終了。描画中に新ポンプの欠落モデル・描画例外は検出されなかった。
- スクリーンショットと導入用JAR、ビルド・GameTestログ、Ponder結果JSONはDiscord作業領域の `/workspace/outputs/` に保存。

配布JARのSHA-256：`2ffcee3b1bc30d70c659fdc217e6eedcd1d11a5819c8d6367b713ae81b277727`
