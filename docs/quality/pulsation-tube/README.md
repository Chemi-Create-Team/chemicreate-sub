# 脈動抑制管と液体タンクの整理

ユーザーの追加指定は「縦も横も行けるパイプ型の脈動抑制管」。X・Y・Zの軸へ設置でき、同じ軸の1列で共有在庫を作ります。登録ID `gas_receiver` は維持し、表示名とモデルを変更しました。黒鋼液体タンク `liquid_tank` と水タンク `water_tank` は登録・レシピ・アセットから撤去しています。

```sh
export JAVA_HOME=/workspace/.local/jdk21
export GRADLE_USER_HOME=/workspace/.gradle
export PATH="$JAVA_HOME/bin:$PATH"
python3 tools/generate_pulsation_tube.py
python3 tools/update_pulsation_lessons.py
python3 tools/generate_ponder_language.py
python3 tools/validate_translations.py
python3 tools/validate_ponder_language.py
python3 tools/validate_plant_tutorials.py
bash gradlew --no-daemon clean build
bash gradlew --no-daemon -I docs/quality/compact-centrifuge/verification.gradle -DtestDir=/tmp/createplant-pulsation-regression -DtestClasses=PulsationTubeGameTests,TankGradeGameTests,MixtureGameTests,TransportQualityGameTests,FluidGradeGameTests,NativeFluidInventoryGameTests,TankGameTests,FlowControlGameTests runGameTestServer
bash gradlew --no-daemon -I docs/quality/compact-centrifuge/verification.gradle -DtestDir=/tmp/createplant-pulsation-content -DtestClasses=FlareGameTests,GasBellGameTests,ManufacturingPonderGameTests,PlantTutorialGameTests,SurvivalReachabilityGameTests runGameTestServer
mc-debug glxinfo -B
mc-debug bash ./gradlew --no-daemon -I docs/quality/pulsation-tube/client.gradle runClient
```

機能・材質・Create搬送の55件と、ガスホルダー・フレア・製造導線など37件がすべて成功しました。新規の4件は、標準タンクの気体拒否と液体受付、削除IDの不在、3軸の連結、両端のCapability、共有気体の保存・再読込、SIMULATE、レンチでの向き変更と全量保存、2台のCreateポンプによる有限8000 mBの移送を確認します。混合液の既存テストも維持しています。

モデルは `generate_pulsation_tube.py`、字幕は `tools/data/ponder_lessons.json` が正本です。`update_pulsation_lessons.py` はPonder用の旧構造を整えます。単独の旧レシーバーは隣接する配管の軸に合わせ、縦に積んだ側面出入式の旧槽は耐圧タンクへ差し替えます。これはゲームの既存ワールドを移行するコードではありません。共通Structure生成のsaveからも呼ぶので、再生成で旧配置が戻ることはありません。

起動前に `/tmp/createplant-pulsation-client/options.txt` を `lang:ja_jp`、`onboardAccessibility:false`、`guiScale:2`、`pauseOnLostFocus:false` に設定します。検証フラグのある起動だけで使い捨てワールドを作り、新しい日本語Ponderを表示します。撮影中はCreateの `coolDownAfterSkip()` で表示時刻を維持します。スクリーンショットはRTX 4060で描画したMinecraftをcomputer-use-linux MCPで取得します。通常プレイには検証処理を実行しません。

初回の画面確認でZ軸の終端の向きが逆だったため、モデルの回転を修正しています。最終の図とJSONは修正後のものです。指針は気体の圧力等級（空なら0）を表示し、物理的な圧力波の計算や架空の脈動は加えていません。大規模設備の性能、音声、他の描画Modとの組合せは未測定です。

最終実画面で3軸の3連管、両端のフランジと通常配管の位置、日本語の液体専用説明を確認しました。X軸の共有在庫24000 mBと低圧等級の指針1/3、空のY・Z軸の指針0をクライアントから記録しました。`pulsation-tube-ja.png` は修正後の未加工の画面です。

`CLIENT QUALITY PASSED` と結果JSONの保存、ワールド保存が完了しました。その後、NVIDIA `libEGL_nvidia.so.0` の終了処理でSIGSEGV（134）が起きたため、runClientコマンド自体を成功とはしていません。
