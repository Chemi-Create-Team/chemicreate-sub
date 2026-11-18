# 1マス流路の小型ドーナツ炉

2026-11-08。ユーザーの「プラズマが通るルートを1マスにして、ドーナツ型でできるだけ小さく」という指定に対応。

実装コミット：`de70da9e`。

## 実装

核融合炉を31×31×13から**9×9×7**へ縮小した。プラズマ通路は高さ1・幅1マス、全周24マスの水平閉ループ。中央は**3×3マス**を基礎より上の全高で開放する。中央柱、円柱外装、中央を覆う屋根は設けない。

通路は半径の閾値で空間を抜かず、整数座標の順序付き経路で定義する。各セルの隣接通路は前後の2セルのみで、曲がり角にも2×2の広がりを作らない。各セルの上下・内外の面は真空容器、ブランケット、ダイバータ、コイルで閉じる。レンダラーも同じ経路を使用し、管径と揺らぎを1マスの空間内へ収める。

中央3マスの開口と、内壁・通路・外壁それぞれ1マスを両側に確保する条件では、必要な幅は`3 + 2 × (1 + 1 + 1) = 9`マスとなる。独立したコイルの外装を追加すると大きくなるため、4組・32ブロックのコイルを圧力壁の区画として組み込んだ。ソレノイドは基礎の高さ1ブロックへ埋める。これはユーザー指定の小型化に合わせたゲーム用の縮約である。

7個の流体口、制御・給電盤、回転式真空ポンプは維持する。中心から接続面まで4マス。4×3の保守ハッチは中心から手前へ3マス、中央面から3マス上に置き、中央の開口へ突き出さない。クレーンの組立例・実機試験のフレームも縮小した。

定格500 MW・常用400 MW、燃料、給電、冷却、磁場、保守寿命の計算は変更していない。この寸法で現実に500 MWを実現できるという意味ではない。

| 部材 | 個数 |
| --- | ---: |
| 強化安山岩支持材 | 73 |
| コイル | 32 |
| ブランケット | 44 |
| ダイバータ | 44 |
| 真空容器 | 24 |
| ソレノイド | 1 |
| 保守ハッチ | 12 |
| 制御部・ポンプ・7流体口 | 9 |
| **実体合計** | **239** |

必要空間は309セル、構造検証対象は合計548セル。空間には24セルのプラズマ経路と、中央・外側の開放領域を含む。

## 主なファイル

- `src/main/java/net/createplant/fusion/FusionPhysics.java`、`FusionStructure.java`：寸法、整数座標の閉路、壁、接続口、空間。
- `src/main/java/net/createplant/client/FusionReactorRenderer.java`：実際の1マス流路に追従する描画。
- `src/main/java/net/createplant/gametest/FusionGameTests.java`、`PowerPlantGameTests.java`：経路、全方向の形成・接続・破損、クレーン交換。
- `src/main/java/net/createplant/ponder/FusionPonderScenes.java`、`PlantSystemsPonderScenes.java`：小型炉と保守の組立案内。
- `tools/generate_fusion_assets.py`、`generate_plant_systems.py`：通常炉・保守例NBTと寸法説明の再生成。
- `src/main/java/net/createplant/client/FusionClientCheck.java`、`PowerPlantClientCheck.java`：完成形の実クライアント確認。

## 検証

- `build runPowerPlantRegressionGameTestServer`：**29件すべて合格**、Gradle正常終了。
- 追加試験で24セルの一意性、前後2面だけの接続、閉路、水平1層、2×2の広がりがないこと、通路以外の全ての面が壁で閉じることを4方向で確認。
- 4方向の構造成立、7ポートの標準流体能力・外向きの配管空間、12枚の保守ハッチ、実体の基礎への面接続、中央3×3の開放、空間閉塞・真空容器欠損による停止を確認。
- 既存の80%燃焼要素試験、走行・昇降を伴うクレーンの部品交換も合格。
- `validate_ponder_language.py`、`validate_plant_tutorials.py`：合格。
- 独立監査でJava・Pythonの全567座標、21特殊ブロック、通常NBT・保守NBTの実体239部材とBlockStateが一致。全実体が単一の面接続グラフとなることも確認。
- RTX 4060の実クライアントへ全構成部品を配置してMCPで撮影。中央の穴と一続きの環状構造を確認。断面表示や部品を隠す処理は使用していない。外周の保守台・小型化したクレーンも撮影した。

- 実クライアントで日本語112シーンの見出し・共有文を検証し、炉と保守の2シーンを描画・保存。通常完成形は2視点のプレビューでも描画し、同期した形成状態とプラズマ温度を確認した。プレビューは稼働状態を描くための開発用表示で、全系の長時間運転の証拠にはしない。
- 画像保存と`CLIENT QUALITY PASSED`後、終了処理で既知の`libEGL_nvidia.so.0`のSIGSEGV（終了134）が発生した。MCP撮影用クライアントを終了した際にも同じドライバ障害が発生した。表示検証は完了しているが、`runClient`タスクの正常終了とは区別する。サーバー29試験とbuildは正常終了した。

## 更新と制約

旧31マス版からは組み直しが必要。停止・放電・冷却して内容物を回収し、新しいゴーストガイドに沿って組み直す。接続口・保守ハッチの位置が変わるため、配管とクレーンの配置も変更する。

タービン・冷却塔を含む全系の長時間運転、全GameTest、複数炉の性能試験は今回実施していない。`runFusionPlantGameTestServer`の旧設備は現寸法と復水冷却回路に合わせた再配置が必要で、今回の29件の回帰試験とは別である。

再実行：

```bash
JAVA_HOME=/path/to/jdk21 JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4 \
  bash gradlew --max-workers=2 build runPowerPlantRegressionGameTestServer
python3 tools/validate_ponder_language.py
python3 tools/validate_plant_tutorials.py
```

## 成果物

`/workspace/outputs/`へ保存（各8 MiB未満）：

- `one-cell-torus.png`：通常完成形を上方から撮影。
- `one-cell-torus-side.png`：通常視点。
- `one-cell-torus-crane.png`：小型化した保守クレーンの実画面。
- `create-heavy-industry-one-cell-torus-0.1.0.jar`：配布JAR、4,691,016 bytes。
- `one-cell-torus-build-test.log`、`one-cell-torus-ponder-client.log`：検証ログ。
- `one-cell-torus-client-result.json`、`one-cell-torus-ponder-result.json`：実クライアント検証結果。
- `one-cell-torus-report.md`：本報告のコピー。

JARのSHA-256：`2c17dab19273b37dff0985ffa97f084bc90a9b8cb06807f4f2d82d3a8efd04db`。JAR内の通常炉NBT・保守NBT・日本語リソースが検証に使ったソースと一致することを確認した。
