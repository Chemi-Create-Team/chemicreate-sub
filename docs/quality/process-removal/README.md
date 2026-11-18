# 設備撤去・全流体バケツの確認

`verification.json` に結果、`creative-fluid-buckets.json` に実登録から調べた流体99種、`resource-audit.json` に削除参照と28種の新規画像、`recipe-availability-ja_jp.json` に実JEIの検索結果を保存しています。`steam-buckets.png` はcomputer-use-linux MCPで取得したMinecraft本体の未加工画像です。

Java 21、Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10で確認しました。

```sh
export JAVA_HOME=/workspace/.local/jdk21
export GRADLE_USER_HOME=/workspace/.gradle
export PATH="$JAVA_HOME/bin:$PATH"
bash gradlew --no-daemon clean build
bash gradlew --no-daemon -I docs/quality/compact-centrifuge/verification.gradle -DtestDir=/tmp/createplant-process-removal-verified -DtestClasses=ProcessRemovalGameTests,OreLeachingGameTests,SurvivalReachabilityGameTests,AgitatorDriveGameTests,ElectricUtilityGameTests,CircuitGameTests,CubicPressGameTests,TankGradeGameTests,PlantTutorialGameTests,SeasonalPlantGameTests runGameTestServer
bash gradlew --no-daemon -I docs/quality/compact-centrifuge/verification.gradle -DtestDir=/tmp/createplant-process-removal-boilers -DtestClasses=TubularBoilerFlowGameTests,TubularBoilerSizeGameTests,ThermalControlGameTests runGameTestServer
python3 tools/validate_translations.py
python3 tools/validate_ponder_language.py
python3 tools/validate_plant_tutorials.py
mc-debug glxinfo -B
mc-debug bash ./gradlew --no-daemon -I docs/quality/process-removal/client.gradle runClient
```

ネイティブ画面の起動前に `/tmp/createplant-process-removal-client/options.txt` を `lang:ja_jp`、`onboardAccessibility:false`、`guiScale:2`、`pauseOnLostFocus:false` に設定します。検証用フラグで新しいフラットワールドを作り、クリエイティブタブ・スクロール・「蒸気」の検索結果・バニラタブを検査します。実画面は1240×740、RTX 4060で描画しました。結果JSONと `CREATIVE TABS PASSED`、ワールド保存を確認した後、NVIDIAのEGL終了処理がSIGSEGVで終了しました。runClientの終了コード134は成功として扱いません。

全体の外観検査 `validate_visual_identity.py` は、バケツの登録・輪郭・重複の検査を通過した後、今回対象外の9品の材料分類で停止します。対象は `catalytic_reactor`、`high_pressure_turbine_unit`、`iron_pipe`、`irradiated_cartridge`、`irradiation_cartridge`、`jacketed_pipe`、`low_pressure_turbine_unit`、`pig_iron`、`steel_slag` です。今回削除した4種の塩結晶は分類データからも削除しました。全体検査の成功は主張しません。

汚染混合ガスは現行のサバイバル生成経路がない既存流体です。今回のバケツ追加はクリエイティブへの露出だけで、生成工程は追加していません。サバイバル到達性の結果でもこの1品を `creativeOnly` と明示しています。ほかの通常品・核関連に未到達品はありません。
