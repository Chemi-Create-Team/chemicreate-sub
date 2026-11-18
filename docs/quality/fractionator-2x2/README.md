# 2×2専用分留塔の検証

Java 21、Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10で確認。

```sh
bash gradlew --no-daemon -I docs/quality/multiblock-lessons/server.gradle -DtestDir=/tmp/createplant-fractionator-2x2 -DtestClasses=DistillationGameTests,ColumnConsolidationGameTests,PlantTutorialGameTests,ManufacturingPonderGameTests,ReactorGameTests,ToxicGasGameTests,VesselNitrogenGameTests,NitricPlantIntegrationGameTests,ColumnLadderGameTests,MalformedDataGameTests runGameTestServer
bash gradlew --no-daemon build
mc-debug bash gradlew --no-daemon -I docs/quality/multiblock-lessons/client.gradle -DallScenes=false -DcaptureDelay=35 -DqualityTargets=fractionating_column@fractionating_column,fractionating_column@fractionating_column_binary,fractionating_column@fractionating_column_air,air_filter@plant_manufacturing_air,ammonia_gas_bucket@plant_manufacturing_ammonia,ammonium_nitrate@plant_manufacturing_nitric_fertilizer,cooled_olefins_bucket@plant_manufacturing_petrochemical_feedstocks,cooled_hydrocracker_effluent_bucket@plant_manufacturing_fuel_refining runClient
```

サーバー試験は61件。`server-results.txt`に完了ログを保存する。Ponder検査は全136シーンのコンパイル・翻訳・見出しの照合と、上記8教材の終盤描画を行う。全編の全フレームを人間が確認したという意味ではない。

PNGはComputer Useで取得したゲーム画面。描画にはNVIDIA GeForce RTX 4060を使用し、`mc-debug glxinfo -B`でhardware renderingを確認した。
