# プラント保守・炭素酸化物の再確認

Java 21、リポジトリ既定のMinecraft/NeoForge/Create構成を使用する。

```sh
bash gradlew --no-daemon -I docs/quality/maintenance/server.gradle runGameTestServer build
python3 tools/validate_translations.py
python3 tools/validate_ponder_language.py
python3 tools/validate_machine_sounds.py --require-definitions
```

最初のコマンドは関連62件を実行する。4件のAUTO冷態起動は `@ExplicitGameTest` で通常試験から除外し、この実行では各10,000 tick運転する。レポートJSONは `/tmp/createplant-maintenance-tests/compact-fuel-results/` と同ディレクトリの `maintenance-inventory.json` に保存する。`-DtestDir=...`、`-DtestClasses=...` で出力先と対象クラスを変更できる。

全体を実行する場合は `-DtestClasses=` を指定する。既知の失敗と今回の測定値は[完了レポート](../../reports/plant-maintenance-and-carbon-oxides.md)を参照。

実クライアントのPonder検査例：

```sh
mc-debug bash gradlew --no-daemon -I docs/quality/multiblock-lessons/client.gradle \
  -DqualityLanguage=zh_cn -DallScenes=false \
  -DqualityTargets=gas_receiver@plant_manufacturing_carbon_oxides \
  -DcaptureDelay=300 runClient
```

全シーンのコンパイル・実行時翻訳を検査した後、指定シーンを実際に描画する。`allScenes=true` は全シーンを順に表示するため、特定対象の確認ではfalseを使う。画面証跡を保存する場合は隔離デスクトップのComputer Useで撮影する。終了処理中にNVIDIAライブラリ内でネイティブクラッシュとなる場合、`CLIENT QUALITY PASSED` と結果JSONの有無を、Gradleプロセスの終了コードと分けて記録する。
