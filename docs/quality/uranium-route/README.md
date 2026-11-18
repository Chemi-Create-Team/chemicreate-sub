# ウラン燃料工程の確認

対象はMinecraft 1.21.1 / NeoForge 21.1.219 / Create 6.0.10。Java 21を使う。

関連GameTest 80件とビルド：

```sh
bash gradlew --no-daemon -I docs/quality/uranium-route/server.gradle build runGameTestServer
```

`server.gradle`は専用の`/tmp/createplant-uranium-route-tests`ワールドを使う。別の場所は`-DtestDir=/tmp/任意の新しい名前`、個別クラスは`-DtestClasses=UraniumFuelGameTests`で指定できる。

実クライアントの対象Ponder確認：

```sh
mc-debug bash gradlew --no-daemon -I docs/quality/uranium-route/client.gradle -DqualityLanguage=ja_jp runClient
```

英語は`en_us`、中国語は`zh_cn`を指定する。既存の品質確認用クライアントが新規検証ワールドを開き、2本の新しいPonderと更新した小型RBMKの翻訳を確認し、2本の新しいシーンを表示して終了する。`-DcaptureDelay=280`で各画面を14秒保持できる。結果は`run/client-quality-result-言語.json`。GPUは`mc-debug glxinfo -B`でRTX 4060を確認した。

ここに保存したJSONは合格結果と静的アセット監査。全工場を配管でつないだ長時間運転の実測ではない。試験の方法・数値・制約は[完了レポート](../../reports/uranium-fuel-route.md)を参照。
