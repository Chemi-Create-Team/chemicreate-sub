# 円柱型ケースを外したトカマク

2026-11-08。ユーザー指定「トカマクのドーナツが見えるように、円柱型ケースをなくす」に対応。

実装コミット：`a444dde604af847940ee3710750c6c7b62040635`。

## 変更内容

13×13×11の核融合炉から円柱側壁と全面の屋根をなくし、基台・4本の配管支持柱・手前外周の上部支持梁へ変更した。4×3保守ハッチを中央から手前へ5ブロック寄せ、主半径3.5・8方向のコイルへ調整した。ドーナツ状の真空容器とコイル間の隙間が、完成状態で見える配置にした。

炉本体は565ブロック。支持材173、コイル128、真空容器100、ブランケット24、ダイバータ112、ソレノイド7、ハッチ12、制御部・真空ポンプ・流体口9。薄い環状部とコイルの接続部を補い、全ブロックが基礎へ面接続される。

7つの流体口、制御盤、真空ポンプの座標と向きを維持した。保守ハッチと梁は炉の向きに追従し、クレーンも新しいハッチ座標へ降下する。熱・燃料・給電・冷却・磁場・部品寿命の計算には変更を加えていない。部材IDは維持し、旧外殻部材は「強化安山岩トカマク支持材」へ表示名を変更した。

## 組立と既存ワールド

以前のケース版をそのまま完成扱いにはしない。炉を停止・放電・冷却してから側壁と屋根を撤去し、中央支持材・梁・コイル接続部を補う。ゴーストガイドを新配置の正本とする。外周・上面の必要空間が旧ケースで埋まっていると、形成は「必要な空間に障害物」で停止する。

各ポートの外向き面には従来どおりCreate標準パイプを接続できる。クレーンは外周の保守台に届くレール範囲が必要で、既存の核融合保守モードを使う。

## 主なファイル

- `fusion/FusionStructure.java`：開放支持構造と必要空間。
- `gametest/FusionGameTests.java`：4方向の形成・接続・見通し・真空容器欠損・空間閉塞の検証。
- `ponder/FusionPonderScenes.java`：通常完成形の組立説明。北半分を隠す旧断面表示を撤去。
- `client/FusionClientCheck.java`：全構成部品を置く確認用シーン。断面だけを組む処理を撤去。
- `client/PowerPlantClientCheck.java`：実ワールド外観確認用の全構成炉と撮影視点。
- `tools/generate_fusion_assets.py`：再生成できる配置・翻訳。保守Ponderも同じ定義から生成。
- `DESIGN.md`、`docs/FUSION_REACTOR.md`、`docs/power-plant-expansion.md`：構造説明を更新。

Java相対パスの基点は`src/main/java/net/createplant/`。

## 検証範囲

`build runPowerPlantRegressionGameTestServer`は最終28件すべて合格。4方向での形成、7つの標準流体能力と外側配管スペース、側壁・屋根の開口、旧ケースによる閉塞、真空容器の欠損、プラズマ空間の閉塞を検証した。既存の80%核融合要素試験、クレーンの部品交換も合格した。

Ponder言語・構造の2検証スクリプトは合格。独立監査でJava・Python・生成NBTの全配置が一致し、全565実体ブロックが基礎へつながることを確認した。

RTX 4060の実クライアントで、切断・半分非表示などを使わない通常の完成状態を撮影した。上方・側面・保守クレーン付きの画像を保存。Ponderは日本語112シーンの見出し・共有文と、炉・保守の2シーンの描画を確認した。

実クライアントの画像保存・検証完了後、終了処理で既知の`libEGL_nvidia.so.0`のSIGSEGV（終了134）が発生した。`runClient`タスク自体を正常終了とは扱わない。サーバー28試験とbuildは正常終了している。

核融合炉・タービン・冷却塔の全系長時間試験、全GameTestの再実行、複数炉の性能測定は行っていない。既存の核融合燃料生産や復水冷却回路の試験上の制約は[前回報告](compact-reactor-and-cooling-tower.md)のまま。

再実行：

```bash
JAVA_HOME=/path/to/jdk21 JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4 \
  bash gradlew --max-workers=2 build runPowerPlantRegressionGameTestServer
python3 tools/validate_ponder_language.py
python3 tools/validate_plant_tutorials.py
```

## 成果物

`/workspace/outputs/`に保存（各8 MiB未満）：

- `open-tokamak-final.png`：通常完成形の上方からの画像。
- `open-tokamak-side.png`：側面の画像。
- `open-tokamak-maintenance.png`：外周の保守台とクレーン。
- `create-heavy-industry-open-tokamak-0.1.0.jar`：配布用MOD、4,688,587 bytes。
- `open-tokamak-final-verification.log`、`open-tokamak-ponder-client.log`：検証ログ。
- `open-tokamak-report.md`：本報告のコピー。

JARのSHA-256：`d127b8ef6ab74cb2127d481419e8f6cd61130c3cd04c270a5b195615592d2ad9`。出荷JAR内の構造NBT・日本語リソースが最終検証に使用したファイルと一致することも確認した。
