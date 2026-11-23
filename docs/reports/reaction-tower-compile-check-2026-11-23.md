# 反応塔のコンパイルエラー確認

- 実装コミット `f1227aa` はすでに `main` に反映済み。`ReactionTowerBlockEntity#process(int)` で、状態名の `next` とは別に処理量の累積値を `after` として宣言し、後続の計算も合わせて修正している。
- `build.gradle` の `JavaCompile` 共通設定に `options.encoding = "UTF-8"` があることを確認した。Windowsのターミナル表示は確認対象に含めていない。
- Linux上でJava 21とGradle Wrapperを使い、キャッシュ済みのNeoForge成果物で `compileJava` を再実行した。`BUILD SUCCESSFUL` を確認した。
- コンパイル時には削除予定API等の警告が35件残る。添付で任意とされたAPI移行は別作業として扱う。
