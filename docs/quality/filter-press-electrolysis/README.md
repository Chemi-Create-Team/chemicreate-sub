# フィルタープレス型電解槽の確認

MC 1.21.1 / NeoForge 21.1.219 / Create 6.0.10 / Create: Electro Energetics 1.1.4 / Java 21。

## 再実行

```sh
export JAVA_HOME=/workspace/.createplant-jdk21
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2
bash gradlew --no-daemon -I docs/quality/filter-press-electrolysis/server.gradle runGameTestServer
bash gradlew --no-daemon -I docs/quality/filter-press-electrolysis/server.gradle -DtestClasses=NitricPlantIntegrationGameTests runGameTestServer
__GL_THREADED_OPTIMIZATIONS=0 ALSOFT_DRIVERS=null mc-debug bash gradlew --no-daemon -I docs/quality/filter-press-electrolysis/client.gradle runClient
```

クライアントは `-DqualityLanguage=en_us` または `zh_cn` で言語を選び、`-DworldCheck=false` ならPonderだけを確認する。日本語と英語では実ワールドの2×2×8・128枚装填を確認し、中国語では4種類のPonderを確認した。

サーバーの既定対象は46件。電解セルの構造・上限・四方の向き、低電圧と部分負荷、実物ボタン、ハッチ、Createの実ポンプとファンネル、有限の物資と仕事、採掘と再設置、再読込、製品詰まり、塩素アルカリ、重水、既存Basinの通常動作、CEE実回路、製造系列とレシピ到達性を含む。別実行の硝酸プラント結合試験1件を加えて関連47件が合格した。

## 記録

- `validation.json`：サーバー試験、ビルド、数値の確認範囲。
- `nitric-plant.json`：有限の給水から電解、空気分離、アンモニア合成、酸化・吸収まで流した結合試験の数値。
- `integrated-world.json`：通常のCEE銅線で48 V電源から給電した、最大32セル・128枚装填の実測。
- `client-ja_jp.json` / `client-en_us.json` / `client-zh_cn.json`：4種類のPonderの字幕・タイトルと描画確認。
- `overview.png`：形成した2×2×8のセルと2×2端面制御盤。
- `windows.png`：実在庫に対応する水位、装填枚数に対応する電極。
- `hatch.png`：点検ハッチの開放。実際に電気負荷と処理が停止する。
- `panel.png`：通電中の物理制御盤。電圧・電流・電力・生成量・極板数を表示する。

4枚のワールド画像はcomputer-use-linuxによる実クライアントの無加工キャプチャ。NVIDIA RTX 4060のハードウェア描画を `mc-debug glxinfo -B` で確認した。全MODの性能ベンチマークや、長時間のマルチプレイヤー試験は行っていない。
