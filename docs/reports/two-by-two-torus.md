# 2×2マス流路のドーナツ炉

2026-11-08。ユーザーの「9マス版は小さすぎたので、プラズマが通る面を2×2に」という指定に対応。

実装コミット：`6d8e8a22d10fa1337ab04ed85a2ceb2b8a707a42`。

## 変更

本体を9×9×7から**11×11×8**へ変更した。プラズマ流路は**幅2×高さ2マス**、中央の開口は**3×3マス**。円柱ケースや中央柱を追加せず、全周がつながったドーナツ型を保つ。中央3マスと両側の内壁1・流路2・外壁1を合わせた外径は`3 + 2 × (1 + 2 + 1) = 11`マスとなる。

水平形状は半径3・4の角を落とした2本の整数座標閉路の和集合とする。各閉路は`max(|x|,|z|)=R`の周から4つの外角を除き、`(±(R−1),±(R−1))`を加える。内側24セルと外側32セルは重複せず、**各層56セル・2層112セル**を確保する。角で1マスに絞らず、どのセルも完全な2×2の水平タイルに含まれる。内外のレーンはそれぞれ面接続だけで一周できる。

基礎をローカルY=0、流路をY=3・4、下面壁をY=2、上面壁をY=5に置く。コイルは4組・各12ブロックの4×4断面の外周で、内部2×2を空ける。中央ソレノイドは基礎の高さに収める。制御部の基準高さはY=4であり、プラズマ断面の実中心はそのブロック中心から0.5マス下となる。レンダラーもこの中心と実際の経路に合わせる。

7流体口、真空ポンプ、給電盤、4×3保守ハッチを維持し、Ponderと保守クレーンの吊具位置を新寸法へ合わせる。中心から接続面まで5マス、保守ハッチ基準点は中心から手前へ4マス・制御部の基準高さから3マス上。定格500 MW・常用400 MWと燃料・冷却・給電・保守の計算は変更しない。寸法と層の統合はゲーム用の表現である。

| 部材 | 個数 |
| --- | ---: |
| 強化安山岩支持材 | 117 |
| コイル | 48 |
| ブランケット | 80 |
| ダイバータ | 80 |
| 真空容器 | 64 |
| ソレノイド | 1 |
| 保守ハッチ | 12 |
| 制御部・ポンプ・7流体口 | 9 |
| **実体合計** | **411** |

必要空間は538セル、構造検証対象は合計949セル。空間には112セルのプラズマ流路と、中央・外側の開放領域を含む。

## 主なファイル

- `src/main/java/net/createplant/fusion/FusionPhysics.java`、`FusionStructure.java`：寸法、2層の流路と壁、描画中心線。
- `src/main/java/net/createplant/gametest/FusionGameTests.java`：2×2の通路・密閉・4方向の形成と接続。
- `src/main/java/net/createplant/client/FusionReactorRenderer.java`：2×2流路の中心に追従する描画。
- `src/main/java/net/createplant/ponder/FusionPonderScenes.java`、`PlantSystemsPonderScenes.java`：新しい流路の案内と保守吊具。
- `tools/generate_fusion_assets.py`、`generate_plant_systems.py`：組立NBTと説明文。
- `src/main/java/net/createplant/client/FusionClientCheck.java`、`PowerPlantClientCheck.java`：完成形の実画面確認。
- `DESIGN.md`、`docs/FUSION_REACTOR.md`、`docs/power-plant-expansion.md`：現行仕様。

## 検証

- 独立監査でJavaとPythonの全968座標、21特殊ブロック、通常炉・保守NBTの本体411部材とBlockStateが一致。全411部材が単一の面接続となり、流路112セルの全外面が壁で閉じることを確認。
- 中央3×3マスは基礎より上のY=1～7で完全に開放。各層56セルの内外の閉路は重複せず、全ての角で2本の独立した面接続の経路が存在する。
- `validate_ponder_language.py`、`validate_plant_tutorials.py`：合格。
- プラズマの描画面42,000点が2×2流路内に収まることを数値確認。
- `build runPowerPlantRegressionGameTestServer`：**29件すべて合格**、Gradle正常終了。高さの非対称な範囲（基準から下4・上3マス）の検査を強化した後、最終版でも再実行して合格した。
- 2層の流路形状、角の狭窄防止、全外面の密閉、4方向の構造成立と接続、真空容器欠損・通路閉塞による停止、既存の80%燃焼要素試験、走行・昇降を伴うクレーン交換を確認。

- RTX 4060の実クライアントへ通常の全構成部品を配置し、MCPで上方・通常視点を撮影した。拡張したリングと中央の開口を確認。断面表示や部品を隠す処理は使用していない。
- 実クライアントで日本語112シーンの見出し・共有文を検証し、炉と保守の2シーンを描画・保存。通常完成形の2視点プレビューでも形成状態とプラズマ温度を確認した。プレビューは表示用の開発機能で、全系長時間運転の証拠にはしない。
- `CLIENT QUALITY PASSED`と画像保存の後、終了処理で既知の`libEGL_nvidia.so.0`のSIGSEGV（終了134）が発生した。MCP撮影用クライアントを終了した際も同じ障害が発生した。表示検証は完了したが、`runClient`タスクの正常終了とは区別する。サーバー29試験とbuildは正常終了した。

## 更新と制約

9マス版からは組み直しが必要。停止・放電・冷却・材料回収の後、新しいゴーストガイドで組む。配管口とハッチの位置にも注意する。

タービン・冷却塔を含む全系の長時間運転、全GameTest、複数炉の性能測定は今回の寸法調整には含めない。旧配置の`runFusionPlantGameTestServer`は現寸法と復水冷却回路への再配置が必要で、関連回帰試験とは区別する。

再実行：

```bash
JAVA_HOME=/path/to/jdk21 JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4 \
  bash gradlew --max-workers=2 build runPowerPlantRegressionGameTestServer
python3 tools/validate_ponder_language.py
python3 tools/validate_plant_tutorials.py
```

## 成果物

`/workspace/outputs/`へ保存（各8 MiB未満）：

- `two-by-two-torus.png`：通常完成形を上方から撮影。
- `two-by-two-torus-side.png`：通常視点。
- `create-heavy-industry-two-by-two-torus-0.1.0.jar`：配布JAR、4,693,826 bytes。
- `two-by-two-torus-verification.log`：最終版29件の動作試験とビルドのログ。
- `two-by-two-torus-ponder-client.log`：実クライアントの検証ログ。
- `two-by-two-torus-client-result.json`、`two-by-two-torus-ponder-result.json`：実クライアント検証結果。
- `two-by-two-torus-report.md`：本報告のコピー。

JARのSHA-256：`f0d4190521e00da5cbbca91e1bd6161ea2e060c14c01ca60f7a1f5ceb1047128`。JAR内の通常炉NBT・保守NBT・日本語リソースが最終検証に使用したソースと一致することを確認した。
