# タンク外装と密な電解槽制御盤の確認

2026-11-11。[完了レポート](../../reports/electrolysis-tank-panel.md)。

再現には前回と同じ [クライアント](../filter-press-electrolysis/client.gradle)／[GameTest](../filter-press-electrolysis/server.gradle) のinit scriptを使う。

```sh
export JAVA_HOME=/workspace/.createplant-jdk21
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2
/workspace/.cache/createplant-art-venv/bin/python tools/generate_electrolysis_cell.py
/workspace/.cache/createplant-art-venv/bin/python tools/generate_electrolysis_ponder.py
bash gradlew --no-daemon -I docs/quality/filter-press-electrolysis/server.gradle runGameTestServer
ALSOFT_DRIVERS=null __GL_THREADED_OPTIMIZATIONS=0 mc-debug bash gradlew --no-daemon -I docs/quality/filter-press-electrolysis/client.gradle runClient
bash gradlew --no-daemon build
```

日本語の実ワールドと4つのPonderを再生する。実CEEの48 V電源、通常の銅線、32セル、極板128枚、水26,000 mBを使う。50％選択時に実消費電力が下がることも確認する。隣には比較用のCreate Fluid Tankを配置している。通常起動ではこのfixtureを実行しない。

| 画像 | 内容 |
| --- | --- |
| [全景](overview.png) | タンクと同じ外装、最大構成と比較用タンク |
| [側面](side.png) | 16pxの銅製外壁。側窓は復活させていない |
| [制御盤](panel.png) | 9個の指針計器、10個の操作部、3本の液面管とニキシー管 |
| [夜間](night.png) | 発光数字・運転灯 |
| [停止](off.png) | 主電源OFFで消灯 |

画像はRTX 4060の実クライアントをComputer Useで撮影したもの。数値は [world.json](world.json)、試験要約は [server-summary.txt](server-summary.txt)・[client-summary.txt](client-summary.txt)・[build-summary.txt](build-summary.txt)。
