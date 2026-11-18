# 電解槽の高精細化 確認資料

2026-11-11。[完了レポート](../../reports/electrolysis-realistic-hardware.md)を参照。

```sh
export JAVA_HOME=/workspace/.createplant-jdk21
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2
ART_PY=/workspace/.cache/createplant-art-venv/bin/python
"$ART_PY" tools/generate_electrolysis_cell.py
"$ART_PY" tools/generate_electrolysis_ponder.py
"$ART_PY" tools/generate_manufacturing_ponder.py
bash gradlew --no-daemon -I docs/quality/filter-press-electrolysis/server.gradle runGameTestServer
ALSOFT_DRIVERS=null __GL_THREADED_OPTIMIZATIONS=0 mc-debug bash gradlew --no-daemon -DqualityLanguage=zh_cn -I docs/quality/filter-press-electrolysis/client.gradle runClient
bash gradlew --no-daemon build
```

生成にはPillowとnbtlibが必要。この環境では `/workspace/.cache/createplant-art-venv/bin/python` を使用した。材質をAIで生成し直す必要はなく、同梱の[生成元とプロンプト](../../../tools/data/electrolysis/prompt.json)からアセットを機械的に再生成する。

クライアントは `-DqualityLanguage=ja_jp`、`en_us`、`zh_cn` を選べる。最大構成・実CEE給電・反応、16,000 mBの一時排出と返送、針と液面の補間、ハッチ停止、夜間、主電源切断を確認後、電解槽と水素・塩素アルカリ・重水素のPonderを再生する。試験専用の起動フラグが無い通常プレイではこのfixtureは実行しない。

画像はRTX 4060の実クライアントをComputer Useで撮影したもの。画像生成で作ったゲーム画面ではない。

| 画像 | 内容 |
| --- | --- |
| [全景](overview.png) | 2×2×8セル＋端面2×2盤 |
| [側面](side.png) | 側窓を削除した金属製外装 |
| [上面](hatch.png) | 開いた点検ハッチ |
| [内部](inspection.png) | ハッチ越しの電極と実流体 |
| [制御盤](panel.png) | 実CEE給電、針・液面管・発光数字 |
| [夜間](night.png) | 発光ニキシー管と運転灯 |
| [停止](off.png) | 主電源切断、レバー下降と消灯 |

数値は [実ワールド試験](world.json)、[関連GameTest](server-summary.txt)、[クライアント](client-summary.txt)、[ビルド](build-summary.txt)に保存する。
