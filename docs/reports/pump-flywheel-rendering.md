# ポンプの中央が透明に見える描画欠落の修正

実装コミット：d3f6a45e20863321ee38e28d68e338df6a8a8c07

## 原因と修正

Create 6.0.10の `KineticBlockEntityRenderer.renderSafe()` は、ワールドで `VisualizationManager.supportsVisualization()` がtrueなら通常の回転部品描画を行わない。材質別ポンプには `PumpRenderer` だけが登録され、Flywheel用Visualizerが登録されていなかった。そのため加速描画が有効な環境では中央の歯車が消え、両端のフランジだけが残って透明に見えていた。前回の遠景での画面確認ではこの欠落を見落とした。

`ElectrolysisClient.clientSetup()` で共通の `MATERIAL_PUMP_ENTITY` に、Create本体のポンプと同じ `SingleAxisRotatingVisual.ofZ(AllPartialModels.MECHANICAL_PUMP_COG)` を登録した。Flywheelの有効時はこのVisualが描画し、無効時は既存の `PumpRenderer` が描画する。歯車の標準色、本体の材質、形状・画像、アイコン、回転・搬送・保存は変更しない。

参照したソースは、プロジェクトで解決したCreate 6.0.10（Modrinth UjX6dr61）内の `AllBlockEntityTypes.MECHANICAL_PUMP`、`KineticBlockEntityRenderer`、`SingleAxisRotatingVisual`。Create本体のポンプも同じ `ofZ` ファクトリを使う。

## 確認と再発防止

- `MaterialPumpClientCheck` にCreate標準ポンプ1台と6方向の停止中サンプルを追加。材質別10台、6方向の6台、標準1台の合計17台について、Flywheelの実際のストレージに回転Visualが生成されていることを確認するよう変更した。単なる登録の有無やアイコンモデルの解決では判定しない。
- 同fixtureに近距離カメラと任意tickでの駆動停止を追加。通常プレイでは起動しない。
- `./gradlew --no-daemon build` 成功。原子炉物理1,668項目と制御テストも成功。
- RTX 4060の実クライアントで `backend=true` の状態で17台の実体Visual検証が成功。
- 近距離で、追加ポンプをCreate標準ポンプと並べ、中央の歯車と両端の接続部が表示されることを目視確認。複数フレームで回転も確認。
- 実行中に `/flywheel backend flywheel:off` へ変更し、通常rendererによる中央部分の描画も確認。その後 `/flywheel backend DEFAULT` で `flywheel:indirect` へ復帰した。
- 6方向の停止中サンプルも目視確認。自動停止後の大型気体ポンプは `/data get block 16 80 5 Speed` が `0.0f` のまま中央が表示されることを確認。
- スクリーンショットはMCPの実画面キャプチャで、`/workspace/outputs/pump-rendering-flywheel-on.png`、`pump-rendering-flywheel-off.png`、`pump-rendering-six-directions.png`。
- `git diff --check` 成功。搬送ロジックは変更せず、既存GameTest17件は再実行していない。

再現用init：`docs/quality/material-pumps/visual.gradle`。

```bash
mc-debug ./gradlew --no-daemon -I docs/quality/material-pumps/visual.gradle runClient
```

Java 21、Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10、Flywheel 1.0.6。この環境では `JAVA_HOME=/workspace/.local/jdk21` と `GRADLE_USER_HOME=/workspace/.gradle` を使用。

## 次回の注意・制約

- Createの `KineticBlockEntityRenderer` を使う独自BEには、標準rendererの登録だけでなくFlywheel用のVisualも対で登録すること。今回の描画抜けはテクスチャの透過設定ではない。
- 任意起動fixtureの検証はFlywheel 1.0.6の実装側 `BlockEntityStorage.visualAtPos()` を使う。依存更新時はこの開発用APIも確認すること。製品の描画登録はFlywheelの通常のVisualizer APIを使用する。
- このコンテナでは従来同様にクライアント終了時にNVIDIAの `libEGL_nvidia.so.0` でSIGSEGV・exit 134となる場合がある。実画面と実体Visualの確認を、`runClient` タスク全体の正常終了とは扱わない。

配布JAR SHA-256：`06791f52d55c161ef378f09011b87d27b24d7e10c4033b0ad824d8d6e1b6aa3f`
