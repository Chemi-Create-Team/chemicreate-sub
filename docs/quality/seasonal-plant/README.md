# 季節連携・JEI・白い冷却塔雲の確認

結果は `verification.json`、実JEIの全件検索は `recipe-availability-ja_jp.json`、到達性は `survival-production-audit.json` を参照。スクリーンショットはMinecraft本体をRTX 4060で描画し、computer-use-linux MCPで取得した未加工画像です。`cooling-tower-stopped.png` は片方の塔の制御部を撤去し、雲が消えた状態です。

Java 21と既存Gradle Wrapperを使用します。

```sh
export JAVA_HOME=/workspace/.local/jdk21
export GRADLE_USER_HOME=/workspace/.gradle
export PATH="$JAVA_HOME/bin:$PATH"
bash gradlew --no-daemon build
bash gradlew --no-daemon -I docs/quality/compact-centrifuge/verification.gradle -DtestDir=/tmp/createplant-seasonal-tests -DtestClasses=SeasonalPlantGameTests,PowerPlantGameTests,RadiatorTemperatureGameTests,OreLeachingGameTests,AuditGameTests,SurvivalReachabilityGameTests runGameTestServer
bash gradlew --no-daemon -I docs/quality/compact-centrifuge/verification.gradle -DtestDir=/tmp/createplant-seasonal-grades -DtestClasses=TankGradeGameTests,FluidGradeGameTests,GradeControlsGameTests runGameTestServer
bash gradlew --no-daemon -PwithSereneSeasons=true -I docs/quality/compact-centrifuge/verification.gradle -DtestDir=/tmp/createplant-seasons-present -DtestClasses=SeasonalPlantGameTests runGameTestServer
python3 tools/validate_translations.py
python3 tools/validate_ponder_language.py
python3 tools/validate_plant_tutorials.py
```

ネイティブ表示用の `/tmp/createplant-seasonal-client/options.txt` は `lang:ja_jp`、`onboardAccessibility:false`、`guiScale:2`、`particles:0`、`pauseOnLostFocus:false` を起動前に設定します。

```sh
mc-debug glxinfo -B
mc-debug bash ./gradlew --no-daemon -PwithSereneSeasons=true -I docs/quality/seasonal-plant/client.gradle runClient
```

この起動に限り、検証用の2基の冷却塔に温排水を補給し出力を排出します。通常プレイではこの処理は実行されません。雲とアイコンを目視した後、PキーでPonderと4つのJEIカテゴリの順次表示を開始します。Minecraftのウィンドウを1240×740にして取得しました。`CLIENT QUALITY PASSED` と結果JSONの書き込み、ワールド保存を確認しましたが、その後の終了処理はNVIDIAのEGLライブラリでSIGSEGVになりました。従ってrunClient自体の終了コードを成功とは扱いません。

2基での見た目と停止時の消散を確認しました。大規模設置時の性能、減少・最少設定での目視、他の描画Modとの組合せは未測定です。音声デバイスは利用できず、音の検証はしていません。
