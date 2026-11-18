# 円形沈殿池とふた付き槽での浸出の検証

環境はJava 21、Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10（UjX6dr61）。

## ビルドとゲームテスト

```bash
JAVA_HOME=/workspace/.local/jdk21 GRADLE_USER_HOME=/workspace/.gradle PATH=/workspace/.local/jdk21/bin:$PATH bash gradlew --no-daemon -I docs/quality/circular-clarifier/verification.gradle -DtestDir=/tmp/createplant-circular-final-tests clean build runGameTestServer
```

関連5クラス・42件が成功。192組（24直径×2高さ×4方向）の配置寸法・立方体形状・建設計画の一致を確認。実ワールドで直径7・8・29・30×高さ3・4の形成、直径14の4方向形成、破壊・再設置と旧Capabilityの無効化、在庫保存、泥の停止、凝集剤、濁りによる電解仕事の増加を確認。ふた付き槽の実際の側面Capabilityから鉱石・酸・酸化用気体を投入し、4金属で鉱石3個→スラリー4000 mB→精製液3000 mB→インゴット9個・再生酸750 mBを確認。

`test-summary.txt` は最終実行の要約。`verification.gradle` の対象クラスはプロパティ `testClasses` で変更可能。

## 実画面

新規のゲームディレクトリには `options.txt` に `lang:ja_jp`、`onboardAccessibility:false`、`guiScale:2` を事前設定する。

```bash
mc-debug glxinfo -B
mc-debug bash gradlew --no-daemon -I docs/quality/circular-clarifier/client.gradle runClient
mc-debug bash gradlew --no-daemon -I docs/quality/circular-clarifier/facility-client.gradle runClient
```

Javaの環境変数は上のビルド例と同じものを設定する。NVIDIA RTX 4060・OpenGL 4.6で実行。Ponderの直径14・高さ4の構造がJavaの実構造と完全一致し、モデル欠落がなく、部品から中央駆動へ状態表示を解決することを確認。施設建設機では41段階の操作で既存3施設、直径7〜30の各値、＋／−、高さ4、切替後の保持を確認。

- `ponder.png`：立方体を並べた円形槽とふた付き槽。
- `constructor.png`：直径30・高さ4の日本語GUI。
- `width30-preview.png`：同じ設定の半透明プレビュー。
- `ponder-client-result.json`、`constructor-client-result.json`：実行中のチェック結果。

画像はComputer Useで実画面を取得したもの。クライアント終了時は、検証・保存完了後に既知の `libEGL_nvidia.so.0` のSIGSEGVでJavaが134終了した。通常終了の成功としては扱わない。

## データ検証

`validate_translations.py`：英日中4998キー、形式一致。`validate_ponder_language.py`：149タイトルと各言語の字幕。`validate_plant_tutorials.py`：94追加シーン・27製造系統。いずれも成功。

`validate_visual_identity.py` は既存の未分類8項目で失敗する。対象は high_pressure_turbine_unit、iron_pipe、irradiated_cartridge、irradiation_cartridge、jacketed_pipe、low_pressure_turbine_unit、pig_iron、steel_slag。今回の変更対象外として保持。

## 電解槽の同時更新との統合確認

origin/main の `38ccc4f2` も取り込んだソース `49acd604` で、次を実行。6クラス57件がすべて成功。配布Jarもこの統合版から作成した。

```bash
bash gradlew --no-daemon -I docs/quality/circular-clarifier/verification.gradle -DtestDir=/tmp/createplant-circular-integrated-tests -DtestClasses=WetProcessingGameTests,OreLeachingGameTests,ReactorGameTests,AssemblyGameTests,SurvivalReachabilityGameTests,ElectrolysisCellGameTests build runGameTestServer
```
