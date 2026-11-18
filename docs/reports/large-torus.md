# 輪と中央の穴が見える大型トカマク

2026-11-08。ユーザーの「大きくしてよいのでドーナツに見える形に」という指定に対応。

実装コミット：`c533b27e7348e077063f286d25fef13f69d112cd`。

## 実装

核融合炉を**31×31×13**へ作り直した。主半径10 m、外側管断面の半径2.9 m、縦横比1.0の横長の輪を使う。コイルを含む中央の見通しの径は約12 m。中央半径5.5 m・ローカル高さ4〜12の範囲を必須空間とし、実体ブロックが入らないことを構造検証でも要求する。

8方向のコイルは`hypot(水平半径−10, 高さ−6)`の3〜4 m帯に沿って**輪の管だけ**を囲む。中央軸寄りの脚、中央を横切る上部の放射状部材、中央の屋根は設けない。基台も環状にし、中心を埋める円盤を撤去。中央ソレノイドは幅1ブロック・高さ4ブロックの低い位置へ収めた。接続は地表の細い支持材で行い、中央上部の穴は開けたままにする。

管は基台上の8脚で支える。保守台は外周に置き、炉の中央から手前へ14 m、中央面から6 m上。4×3のハッチと梁は炉の向きに追従する。クレーン用のPonder・試験フレームも広げた。

| 部材 | 個数 |
| --- | ---: |
| 支持材 | 597 |
| 真空容器 | 692 |
| ブランケット | 496 |
| ダイバータ | 196 |
| コイル | 120 |
| 低いソレノイド | 4 |
| 保守ハッチ | 12 |
| 制御部・ポンプ・流体口 | 9 |
| **実体合計** | **2,126** |

必須空間は2,857セル、構造検証は合計4,983セル。31 mの構造が3チャンクにまたがる配置でも途中の未ロード領域を見落とさないよう、四隅だけでなく全設置範囲のチャンクを確認する。

定格500 MW・常用400 MW、燃料、冷却、磁場、給電、保守寿命の計算は変更していない。低いソレノイドとコイル8方向はゲーム上の造形表現であり、実機の設計を完全再現するものではない。

## 主なファイル

- `fusion/FusionPhysics.java`、`FusionStructure.java`：寸法、環状構造、空間、チャンク確認。
- `gametest/FusionGameTests.java`、`PowerPlantGameTests.java`：大きな穴、4方向の形成、配管、構造破損、部品交換。
- `client/PowerPlantClientCheck.java`、`FusionClientCheck.java`：完成形を実ワールドへ組み、上方と通常視点で確認。
- `ponder/FusionPonderScenes.java`、`PlantSystemsPonderScenes.java`：新しい大きさと保守接続。
- `guide/FusionGuide.java`：新寸法のゴーストガイド。
- `tools/generate_fusion_assets.py`、`generate_plant_systems.py`：再生成可能なNBT・説明文。

Javaパスの基点は`src/main/java/net/createplant/`。

## 検証

- `build runPowerPlantRegressionGameTestServer`：**28件すべて合格**。
- 全4方向での形成、7流体口の標準能力と外向きの配管空間、保守ハッチ、中央の穴、プラズマ室の閉塞、真空容器の欠損を確認。
- 接続性検査で実体2,126ブロックすべてが基台へ面接続することを確認。
- 中央半径5以下・高さ4〜12の領域が空いていることを試験で確認。
- 既存の80%燃焼要素試験、実際の走行・昇降を伴うクレーンの交換試験も合格。
- Ponder言語・構造の2スクリプトは合格。独立監査でJava・Python・通常NBT・保守NBTの全配置とBlockStateが一致。
- RTX 4060の実クライアントへ**通常の全構成部品**を配置してMCPで撮影。半分を隠す処理や断面表示は使用していない。画像を確認し、大きな穴と輪の全周が見える形を採用した。
- 実クライアントで日本語112シーンの見出し・共有文と、新しい炉・保守の2シーンの描画検証を完了。通常完成形を2つの開発用確認視点でも描画した。

画像保存とクライアント検証完了後の終了処理で、既知の`libEGL_nvidia.so.0`のSIGSEGV（終了134）が発生した。`runClient`タスク自体の正常終了とは区別する。サーバー28試験とbuildは正常終了している。

## 更新と制約

以前の13 m版からは組み直しが必要。停止・放電・冷却して内容物を回収し、新しいゴーストガイドに沿って組む。制御部から中心までの距離は15 mとなり、各流体口も移動するため、配管とクレーンの範囲を変更する。

タービン・冷却塔まで含む全系長時間運転、全GameTest、複数炉の性能試験は今回実施していない。既存の`runFusionPlantGameTestServer`は旧配置の試験設備で、31 m炉と復水冷却回路を収める再配置が必要。本版で同試験を合格扱いにはしない。燃料生産など形状変更と無関係な既存の制約も従来どおり。

再実行：

```bash
JAVA_HOME=/path/to/jdk21 JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4 \
  bash gradlew --max-workers=2 build runPowerPlantRegressionGameTestServer
python3 tools/validate_ponder_language.py
python3 tools/validate_plant_tutorials.py
```

## 成果物

`/workspace/outputs/`に保存（各8 MiB未満）：

- `large-torus.png`：通常完成形の上方からのゲーム内画像。
- `large-torus-side.png`：通常視点の画像。
- `create-heavy-industry-large-torus-0.1.0.jar`：配布JAR、4,697,118 bytes。
- `large-torus-verification.log`、`large-torus-ponder-client.log`：検証ログ。
- `large-torus-report.md`：本報告のコピー。

JARのSHA-256：`3283e4e7ad2d93243722173d8048a1858b04baf406a68a4f699d05e6bb6edcb6`。JAR内の構造NBT・日本語リソースが最終検証に使ったファイルと一致することも確認した。
