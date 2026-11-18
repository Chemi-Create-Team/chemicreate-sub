# 汎用混合液の検証

Minecraft 1.21.1 / NeoForge 21.1.219 / Create 6.0.10 / Java 21で確認しました。

```sh
export JAVA_HOME=/workspace/.local/jdk21
export GRADLE_USER_HOME=/workspace/.gradle
export PATH="$JAVA_HOME/bin:$PATH"
bash gradlew --no-daemon clean build
bash gradlew --no-daemon -I docs/quality/compact-centrifuge/verification.gradle -DtestDir=/tmp/createplant-mixture-regression -DtestClasses=MixtureGameTests,TankGradeGameTests,FluidGradeGameTests,GradeControlsGameTests,LiddedVesselGameTests,DriveLessVesselGameTests,LiddedVesselPlacementGameTests,MaterialPumpGameTests,NativeFluidInventoryGameTests,DisplayIntegrationGameTests,WorldLiquidGameTests,WorldLiquidTransferGameTests,ProcessRemovalGameTests,SeasonalPlantGameTests,OreLeachingGameTests,SurvivalReachabilityGameTests runGameTestServer
# 上流の硫酸ルート取り込み後：
bash gradlew --no-daemon -I docs/quality/compact-centrifuge/verification.gradle -DtestDir=/tmp/createplant-mixture-sulfur-merge -DtestClasses=MixtureGameTests,ElectrolysisCellGameTests,AbsorptionTowerGameTests,ReactorGameTests,ProcessRemovalGameTests runGameTestServer
python3 tools/validate_translations.py
python3 tools/validate_ponder_language.py
python3 tools/validate_plant_tutorials.py
mc-debug glxinfo -B
mc-debug bash gradlew --no-daemon -I docs/quality/chemical-mixtures/client.gradle runClient
```

83件のGameTestは成功しました。混合液8件は、成分ごとの量保存、純粋流体の復元、シミュレーション、保存・再読込、熱と放射性核種、Createの実ポンプと分岐、2階建てタンクの解体・再結合、Display Linkの全件出力を検証します。ネットワーク上の熱・核種Data Componentの一致確認を追加した後、混合液8件を再実行しています。`build` の単体検証には混合液6004項目、原子炉物理1668項目、原子炉制御が含まれます。

クライアント起動前に `/tmp/createplant-mixture-client/options.txt` を `lang:ja_jp`、`onboardAccessibility:false`、`guiScale:2`、`pauseOnLostFocus:false` に設定します。専用検証フラグのある起動だけで、使い捨てワールドと有限量の槽を作ります。通常プレイには作用しません。

最初のPキーで通常ポンプを起動し、水・不凍液・Create Diesel Generatorsのエタノール・植物油を各2500 mB、合計10000 mB移送しました。移送先のクライアント同期を待って、4成分すべての量、日本語名、小数第1位の割合、4行のゴーグル概要、全5行の詳細、色 `0xFFA7D1DB` を確認しています。結果は `mixture-client-result.json` に保存しました。2回目のPキーで新しいPonderを開き、実際の日本語表示と混色した液体の描画を確認しました。

スクリーンショット3枚は、RTX 4060で描画したMinecraftをcomputer-use-linux MCPで取得した未加工画像です。`before` は槽からの移送前、`transferred` は通常Createタンクへの移送後、`ponder-ja` は今回追加したPonderです。Ponderの実行結果は `client-quality-result-ja_jp.json` を参照してください。

検証成功の結果JSONとワールド保存後、クライアント終了処理がNVIDIAの `libEGL_nvidia.so.0` でSIGSEGV（終了コード134）になりました。`runClient` コマンド自体は成功として扱っていません。音、大規模な性能、全外部Modの独自データの組合せ、別の描画Modとの組合せは未検証です。

上流 `481a006b` の硫酸ルートを取り込んだ最終状態でも、再ビルドと上記5クラスの51件が成功しました。翻訳キーは最終4967件×3言語です。既存83件は取り込み前の回帰結果として区別しています。
