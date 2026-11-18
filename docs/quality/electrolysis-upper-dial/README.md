# 上段パネルと細分ダイヤルの確認

2026-11-11。[完了レポート](../../reports/electrolysis-upper-dial.md)。既存の電解槽用init scriptを再利用する。

```sh
export JAVA_HOME=/workspace/.createplant-jdk21
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2
/workspace/.cache/createplant-art-venv/bin/python tools/generate_electrolysis_cell.py
/workspace/.cache/createplant-art-venv/bin/python tools/generate_electrolysis_ponder.py
/workspace/.cache/createplant-art-venv/bin/python tools/generate_manufacturing_ponder.py
bash gradlew --no-daemon -I docs/quality/filter-press-electrolysis/server.gradle runGameTestServer
ALSOFT_DRIVERS=null __GL_THREADED_OPTIMIZATIONS=0 mc-debug bash gradlew --no-daemon -I docs/quality/filter-press-electrolysis/client.gradle runClient
bash gradlew --no-daemon build
```

| 画像 | 内容 |
| --- | --- |
| [全景](overview.png) | 最大構成とCreateタンク |
| [100％](panel.png) | 上半分に2計器・2ボタン・ダイヤル、下半分は空 |
| [37％](load37.png) | ダイヤルの回転、設定値と実電流の変化 |
| [停止](off.png) | 停止後のV/A計と維持される設定値 |

画像はRTX 4060の実クライアントをComputer Useで撮影。数値は [world.json](world.json)、試験要約は [server-summary.txt](server-summary.txt)、[client-summary.txt](client-summary.txt)、[build-summary.txt](build-summary.txt) に保存する。起動フラグがない通常プレイでは試験fixtureを動かさない。
