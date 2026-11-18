# 分留塔とマルチブロックPonderの確認

Minecraft 1.21.1 / NeoForge 21.1.219 / Create 6.0.10 / Ponder 1.0.82。

Java 21で実行する。作業環境では`JAVA_HOME=/workspace/.createplant-jdk21`を使用した。

```sh
bash gradlew --no-daemon -I docs/quality/multiblock-lessons/server.gradle runGameTestServer
mc-debug bash gradlew --no-daemon -I docs/quality/multiblock-lessons/client.gradle runClient
mc-debug bash gradlew --no-daemon -I docs/quality/multiblock-lessons/client.gradle -DqualityLanguage=en_us runClient
```

サーバー側は分留塔・統合設備・構造アセット・製造Ponderの25試験を実行する。短い2×2塔の全6工程、1×1・3×3・長方形の拒否、空気分離の制限、実ポンプ、保存再読込、既存14段塔の数量、専用Ponderの実物配管で5留分の独立回収を確認する。

クライアント側は登録された全シーンをコンパイルし、翻訳と見出しを検証して、各シーンの終盤を順番に描画する。全編を人間が見たことや、Ponderが実ワールドの長時間運転試験であることを意味しない。`run/client-quality-result-<言語>.json`と`CLIENT QUALITY PASSED`を確認する。画面保存はComputer Useで行う。

個別の組立途中を確認する場合は、`-DallScenes=false -DqualityTargets=アイテムID@シーンID -DqualitySeek=時刻tick -DcaptureDelay=100`を指定する。全シーンのコンパイル・翻訳検査は個別表示時にも行う。

## アセット再生成

- 分留塔：`python3 tools/generate_vessel_consolidation_ponder.py`
- 製造系列：`tools/generate_manufacturing_ponder.py`（nbtlibが必要）
- 組立順の追加データとヘリウム液化器：最後に`python3 tools/generate_multiblock_lessons.py`

製造系列の14段塔は`tools/data/manufacturing/fractionating_column.nbt`を使う。短縮塔の専用Ponderとは座標・配管が異なるため、直接置き換えない。

短い説明ページは`processResources`で生成する。翻訳の正本は通常の`lang/*.json`で、生成された`.lesson_pages.*`を手で追加しない。英語150文字、日本語・中国語68文字を目安に文・節で分割し、各ページにCreate標準のキーフレームを付ける。中国語が未翻訳の既存説明は英語へフォールバックする。
