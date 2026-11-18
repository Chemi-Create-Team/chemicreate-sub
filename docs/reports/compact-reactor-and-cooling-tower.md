# 核融合炉・冷却塔の小型化

2026-11-08。ユーザーの寸法選択「さらに小型」に基づく変更。

実装コミットID：`e580415c229d28fc40ee65b9d6820f8b1967fe92`。

## 変更

| 対象 | 変更前 | 変更後 |
| --- | --- | --- |
| 核融合炉 | 25×25×17 | **13×13×11** |
| 自然通風冷却塔 | 67×67×92 | **25×25×36** |
| 核融合保守の説明用クレーン | 31 m角、レール高さ32 | 19 m角、レール高さ22（Ponder座標） |

寸法はブロック数。核融合炉の設置面積は約73%、冷却塔は約86%減少する。クレーン自体は既存の可変寸法設備を使用するため、Ponderの大きさだけに制限しない。

ユーザーが求める建築しやすい寸法へ形状を圧縮した。核融合の定格500 MW・常用400 MW、冷却塔338 MW、必要流量、燃料消費、給電、磁場、部品寿命は変更していない。この寸法の実機が同じ出力・放熱を実現できるという主張ではない。実機写真を参考にした形と、ゲーム内の能力設定を区別する。

### 核融合炉

- 主半径3ブロック、縦横比1.4のトロイダル断面。プラズマ空間、ブランケット、真空容器、下部ダイバータ、中央ソレノイド、16方向のコイルを残した。
- 半幅6、高さ11、中央面5、屋根は中央面+5。7流体口と真空ポンプの位置、描画範囲、プラズマ描画、ゴーストガイドを新寸法へ追従。
- 屋根の4×3保守ハッチは維持。クレーンの降下先を屋根寸法定数から計算し、低い炉でも正しい高さへ届くようにした。
- 実体1,177ブロック。外殻685、コイル176、ソレノイド7、真空容器108、ブランケット76、ダイバータ104、ハッチ12、制御部・ポンプ・流体口9。必須プラズマ空間56セル。

### 冷却塔

- 半径12、高さ36。喉径14、頂径17、12組の斜柱、開放吸気口、3層充填材、散水格子、中空煙突を維持。
- 構造検証10,060セル、非空気3,957ブロック。チャンク確認、変更検知、蒸気の表示幅も寸法へ追従。
- 北8温水口、南8冷水口、東補給口を保持。集水槽の外側へノズルを出し、外向きの標準パイプが接続できるようにした。
- 湿球温度+5 Kの冷却限界、水と蒸発・補給の収支、出力閉塞時の停止は従来どおり。

## 主なファイル

- `fusion/FusionPhysics.java`、`FusionStructure.java`：寸法と内部構造。
- `block/CoolingTowerStructure.java`、`block/entity/CoolingTowerBlockEntity.java`：塔の形、接続口、検証範囲。
- `block/entity/FusionReactorControllerBlockEntity.java`、`CraneDriveBlockEntity.java`：ポンプ検出、描画範囲、保守ハッチ到達位置。
- `client/FusionReactorRenderer.java`、`FusionClientCheck.java`、`PowerPlantClientCheck.java`：描画と実機確認用配置。
- `guide/FusionGuide.java`、`ponder/FusionPonderScenes.java`、`PlantSystemsPonderScenes.java`：組立説明。
- `tools/generate_fusion_assets.py`、`generate_plant_systems.py`：再生成可能なPonder構造・翻訳。
- `gametest/FusionGameTests.java`、`PowerPlantGameTests.java`：新寸法・配管口・保守の検証。

Javaパスの基点は`src/main/java/net/createplant/`。

## 検証

- `build runPowerPlantRegressionGameTestServer`：最終28件すべて合格。
- 4方向の核融合炉形成、全7口の標準流体能力と外側配管スペース、全12ハッチ、構造範囲、プラズマ空間の閉塞検出を確認。
- 小型冷却塔の形成、16温冷口の外側配管スペースと標準流体能力、338 MWで100秒相当・33.8 GJの放熱、水収支、保存後再検証、外殻撤去時停止を確認。
- 小型核融合炉の80%燃焼試験、および走行・昇降を伴う両保守部品の交換試験も合格。
- 2つのPonder検証スクリプト合格。独立した生成物監査で、Java・Python・出荷NBTの構造と向きが一致。
- RTX 4060の実クライアントで、縮小後の核融合炉と保守クレーン、冷却塔を配置しMCPで撮影。日本語112シーンの見出し・共有文、縮小した3シーンの描画を確認した。
- 縮小したトーラスのプラズマ描画を、断面と内部の2視点で確認した。これは開発用プレビュー状態による描画試験であり、実運転データではない。
- 保守Ponderにも橋桁・台車・交換吊具の表示状態を設定し、新ハッチへ10 m降下する配置を示す。

## 更新時の注意と未確認事項

旧構造は自動で縮まらない。原料を回収して新寸法へ組み直す。炉は先にプラズマ停止・磁場放電・冷却を行い、保持物を回収する。コントローラーから中心までの距離と全ポート位置も変わるため、旧配管や旧クレーンの高さをそのまま使用しない。

核融合炉・タービン・冷却塔を接続した全系の長時間運転、複数基の性能測定は今回実施していない。要素試験の100秒相当は内部処理の連続呼出しであり、実時間の運転時間ではない。長時間プラント試験用コードの接続位置は更新したが、前回から未追加の復水器冷却回路が必要で、同タスクを合格扱いにはしていない。

全GameTestの再実行は行わず、変更対象を含む28件に絞った。既存D₂/T₂/液体Heのサバイバル生産など、寸法変更と無関係な制約は[前回報告](power-plant-expansion.md)のまま。

実クライアントの描画検証完了・画像保存後、終了処理で前回と同じ`libEGL_nvidia.so.0`のSIGSEGV（終了134）が発生した。`runClient`タスク自体を正常終了とは扱わない。サーバー試験とbuildは正常終了している。

## 再実行・成果物

```bash
JAVA_HOME=/path/to/jdk21 JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4 \
  bash gradlew --max-workers=2 build runPowerPlantRegressionGameTestServer
python3 tools/validate_ponder_language.py
python3 tools/validate_plant_tutorials.py
```

`/workspace/outputs/`へ保存：

- `compact-fusion-maintenance.png`：縮小後の炉と実際の保守クレーン。
- `compact-cooling-tower.png`：25×25×36の冷却塔。
- `create-heavy-industry-compact-0.1.0.jar`：配布用MOD（各成果物8 MiB未満）。
- `compact-plant-final-verification.log`、`compact-plant-ponder-client.log`、`compact-maintenance-ponder-final.log`：検証ログ。
- 本報告のコピー`compact-plant-report.md`。

最終JARは4,690,097 bytes。SHA-256：`b07abbe5f76d8b9ae66f0d464b95368c2569a980172e0f24de971fab30ba2f2e`。
