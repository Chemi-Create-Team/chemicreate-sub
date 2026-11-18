# 気液分離ドラムの確認

仕様：`docs/specs/gas-liquid-separator-drum.md`。Minecraft 1.21.1 / NeoForge 21.1.219 / Create 6.0.10 / Java 21を使用します。

```sh
export JAVA_HOME=/workspace/.local/jdk21
export GRADLE_USER_HOME=/workspace/.gradle
export PATH="$JAVA_HOME/bin:$PATH"
python3 tools/generate_ammonia_separator.py
python3 tools/generate_ammonia_separator_ponder.py
python3 tools/generate_manufacturing_ponder.py manufacturing_ammonia
python3 tools/generate_ponder_language.py
python3 tools/validate_ponder_language.py
python3 tools/validate_plant_tutorials.py
python3 tools/validate_translations.py
bash gradlew --no-daemon clean build
bash gradlew --no-daemon -I docs/quality/compact-centrifuge/verification.gradle -DtestDir=/tmp/createplant-separator-drum -DtestClasses=SeparatorDrumGameTests,HaberBoschGameTests runGameTestServer
bash gradlew --no-daemon -I docs/quality/compact-centrifuge/verification.gradle -DtestDir=/tmp/createplant-separator-regression -DtestClasses=SeparatorDrumGameTests,HaberBoschGameTests,NitricPlantIntegrationGameTests,ManufacturingPonderGameTests,PlantTutorialGameTests,RecipeVisibilityGameTests,SurvivalReachabilityGameTests,ToxicGasGameTests runGameTestServer
mc-debug glxinfo -B
mc-debug bash ./gradlew --no-daemon -I docs/quality/separator-drum/client.gradle runClient
```

モデルは `generate_ammonia_separator.py`、Ponder構造は `generate_ammonia_separator_ponder.py`、字幕は `tools/data/ponder_lessons.json` を正本とします。元の銀色外装2枚は既存の原子力設備の生成元として維持します。ふた付き槽の既存キャップは `tools/data/lidded_vessel_cap_connected.png` から専用の共有パスへ保存し直し、見た目を保ったまま廃止する分離器CTから切り離しています。

`SeparatorDrumGameTests` 4件と既存の `HaberBoschGameTests` 4件はすべて成功しました。最大6段、7段目の分離、横2×2の4列、2段で18000 mBを60 tickで2バッチ処理、ポート面、単独停止、正面保存、窓を作らないレンチ、実際のPlantTankItemによる1個積み増し、水の充填・排出と液面の割合、保存後の液量を確認しています。

関連8クラスの23件では22件成功、既知の `cooledInputsAndAllManufacturingRecipesRemainValid` 1件が `All five named cooling streams are included` で失敗しました。このテストと冷却レシピは今回変更していません。仕様書に記載された既存の失敗と一致します。

言語・Ponder検証は成功しました（4915キー×3言語、148 Ponderヘッダー）。全体の `validate_visual_identity.py` は、仕様書に記載された既存の未分類8件（high_pressure_turbine_unit、iron_pipe、irradiated_cartridge、irradiation_cartridge、jacketed_pipe、low_pressure_turbine_unit、pig_iron、steel_slag）で停止しました。今回のモデルの不正を示す失敗ではありません。

クライアント用 `/tmp/createplant-separator-client/options.txt` は `lang:ja_jp`、`onboardAccessibility:false`、`guiScale:2`、`pauseOnLostFocus:false` を設定します。上記の起動だけで専用ワールドを作り、分離器Ponderの高液位・低液位、アンモニア製造ガイドの順に表示します。スクリーンショットはcomputer-use-linux MCPで取得します。通常プレイには検証フラグは作用しません。

RTX 4060 / NVIDIA 595.99.02の実画面で、3段のドラム、八角形の胴、リブ、上下の鏡板とノズル、Create配管との位置を確認しました。液面計の割合は高液位0.5833333、低液位0.16666667です。製造ガイドの同じ3段ドラムにも実際の液体アンモニアが入り、1 mB / 48000 mBの液面として保持されました。ネイティブの確認データは `separator-ponder-state-*.json` にあります。

3枚の画像はcomputer-use-linux MCPで取得した未加工画像です。高・低液位は最終検証の固定したタイムラインを撮影し、製造ガイドの画像は同じ形状を確認した初回の表示から保存しています。既存のPonder `coolDownAfterSkip()` を検証フラグ時だけ延長し、撮影中も指定の時刻を維持します。

最終検証は `CLIENT QUALITY PASSED` と結果JSONを保存し、ワールド保存も完了しました。その後、NVIDIAの `libEGL_nvidia.so.0` の終了処理でSIGSEGV（終了コード134）が起きました。従ってrunClientプロセス自体を成功とはしていません。音声、別の描画Modとの共存、大規模配置時の性能、変更した核融合テストの全体実行は未検証です。
