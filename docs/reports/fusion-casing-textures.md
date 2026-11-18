# 核融合炉の外装テクスチャ更新

2026-11-08。ユーザーが指定した現行`DESIGN.md`の外装方針に合わせ、直前に扱っていた核融合炉と制御盤の外観を更新した。

実装コミット：`52d64fe5c7cf9f4223dabbb4057ecd5ab73634e9`。

## 対象と描き方

対象は核融合炉本体、7流体口、真空ポンプ、給電盤、電動発電機、制御卓の稼働・停止面、保守ハッチ。11×11×8の構造、断面2×2のプラズマ流路、入出力や運転の計算は変更しない。

`DESIGN.md#casing-grammar`に従い、外周1pxの枠、その内側1pxの左上が明るい面取り、12×12の沈んだ内面を基本構成とする。素材の細かなノイズを減らし、灰色の鋼、暗い銅・真鍮、小さな金属の明部で描く。外装面は概ね24色以下の有限パレットを使う。

真空容器の圧力板、ブランケットの継目、ダイバータのタングステンタイル、コイルの巻線、配管口の流体記号と方向、アナログ制御盤の計器・スイッチを形で区別する。色の変更だけで部品を区別する構成にはしない。

16×16の元データは既存の生成コードで描き直す。PNGだけを修正せず、再生成で同じ結果を得られるようにする。保守ハッチは専用の圧力蓋面を参照し、実体ハンドルにはCreateの真鍮ブロック材を直接参照する。同一画像を別名で複製しない。

Create 6.0.10の解決済みJAR内の安山岩・真鍮・銅ケーシング、工業用鉄ブロックを比較対象とする。Createの画像のコピーや色替えで本体の新しい面を作らず、ハンドルの共通材だけは元のリソースを参照する。

6種類の立方体部材にはCreate標準の`CTModel`と`AllCTTypes.OMNIDIRECTIONAL`を使う。同じ種類のブロックだけを接続し、隣接する同一平面の外枠を除く。外周の枠・面取り、凹角の小さな枠は残す。各アトラスは128×128だが、16×16の接続パターンを8×8に配置したもので、ピクセル密度は16px/mのまま。部分形状と内側の面を持つ保守ハッチは連結処理に含めず、独立した蓋の継目と実体ハンドルを保つ。

## 主なファイル

- `tools/generate_fusion_assets.py`：核融合系のテクスチャ生成コード。
- `tools/generate_plant_systems.py`：保守ハッチの材質参照。
- `src/main/resources/assets/create_heavy_indusstry/textures/block/fusion/`：生成したテクスチャ。
- `src/main/resources/assets/create_heavy_indusstry/models/block/plant_systems/fusion_maintenance_hatch.json`：保守ハッチのモデル参照。
- `src/main/java/net/createplant/client/PowerPlantClientCheck.java`：通常の完成炉と、Create素材・部品・制御卓を比較する開発用展示。
- `src/main/java/net/createplant/client/FusionCasingModels.java`、`ElectrolysisClient.java`：Create標準の接続テクスチャのクライアント登録。

比較用展示の稼働・停止サンプルは静的なBlockState表示であり、実際のプラント運転を示すものではない。

## 検証

- 元の外装32枚を描き直し、保守蓋用1枚を追加。外装33枚は全て16×16・8〜13色。プラズマの描画用画像1枚は従来と完全同一。接続アトラス6枚を含め、核融合フォルダーは計40枚となる。
- 独立監査で単体の枠・面取りの明暗方向、材質と機能の描き分け、Create原本4種との配色を確認。
- CTの全256入力を正規化した47パターンが解決済みCreate 6.0.10の配置に一致。6アトラスは完全不透明・8〜12色で、単体タイル0は元画像と完全一致し、完全接続タイル54に外枠色はない。
- 単体・直線・2×2・3×3・穴・段差を8方向の回転反転で作る48パッチ×6材質を独立確認。露出辺、凹角、連結した外周の面取りが途切れず、内部の枠が除かれることを確認。
- `generate_plant_systems.py`の再生成で40画像すべてが同じハッシュになることを確認。物理計算、構造、ブロック動作、レシピ、翻訳、組立NBTに差分はない。
- `build`：正常終了。Ponder言語・構造の既存2検査も合格。
- RTX 4060の実クライアントで通常の全構成部品を置いた完成炉、Create素材と全ポート、制御盤の稼働・停止サンプル、電動発電機とポンプをMCPで撮影。欠落テクスチャがないことと、実機の接続面を確認した。
- クライアント終了時は既知の`libEGL_nvidia.so.0`のSIGSEGV（終了134）が発生した。画像の確認・保存後の終了障害であり、`runClient`タスクの正常終了とは区別する。

`validate_visual_identity.py`全体検査は次の既存4組で失敗する。変更前のHEADを一時展開して実行した結果と、変更後の失敗一覧が完全一致することを確認した。核融合系の新しい同一外観は追加していない。対象外のモデルを変更して検査を通すことはしていない。

- 復水器冷却水入口・出口。
- 冷却塔の集水槽・外殻。
- 冷却塔の温水・冷水・補給水口。
- 空の廃棄物容器・ガラス固化体。

形状・物資処理の変更を含まないため、全GameTestやプラントの長時間運転は再実行していない。

## 制約と次回の注意

MOD全体の外装を描き直したという意味ではない。旧監査で挙げられた他設備の共通外装や重複画像はこの作業の対象に含めない。

外装とモデルの材質参照の変更のため、既存の11×11×8炉は組み直す必要がない。見た目を確認するときはリソースの再読み込み、または更新JARでの再起動を行う。

## 成果物

`/workspace/outputs/`へ保存（各8 MiB未満）：

- `fusion-casing-textures.png`、`fusion-casing-textures-side.png`：通常完成形の実画面。
- `fusion-casing-materials.png`：Create素材・全ポートの比較。
- `fusion-casing-console.png`：制御盤と電動発電機・ポンプの表示。
- `fusion-textures-before.png`、`fusion-textures-after.png`：単体画像の比較。
- `fusion-connected-textures.png`、`fusion-connected-texture-audit.json`：接続面の独立監査。
- `fusion-casing-textures-build-final.log`、`fusion-casing-textures-gallery-final.log`：最終検証ログ。
- `create-heavy-industry-fusion-casing-0.1.0.jar`：配布JAR、4,704,987 bytes。
- `fusion-casing-textures-report.md`：本報告のコピー。

JARのSHA-256：`6600afd6dcc163dce2029ad3c6f64aaa81fff808001ba842966694b73b1ea523`。全40画像・ハッチモデル・対象のコンパイル済みクライアントクラスが最終検証データと一致することを確認した。
