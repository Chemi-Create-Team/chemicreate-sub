# 全Ponderの再構成 完了レポート

- 日付：2026-11-14
- 実装コミット：`3abdbaa5f43eb56708d7791ff9b46950c850191a`
- ブランチ：`main`
- 対象：登録済みの全140シーン。実ワールド側の構造判定・運転ロジックやブロックのテクスチャは変更せず、Ponderの説明と見せ方を更新した。

## 変更内容

既存の有効なモデル・組立アニメーションを再利用し、全シーンの説明ページと表示時間を再構成した。全対象は[一覧](../quality/ponder-rebuild/coverage.md)と[機械可読の集計](../quality/ponder-rebuild/coverage.json)に記録した。

1. **説明を短く整理。** 日英中の説明を`tools/data/ponder_lessons.json`へ集約。正本は題名140キー・本文690キーで、日本語231キーを書き直した。本文の文字数は29,873→27,350文字（8.4%減、題名と数量書式を除外）。ページは1～2文、長い説明は個別の目印から読み直せる。
2. **製造31章を原料→加工→完成品の順へ。** 原料側と製品側を別に強調し、加工後に「原料」「製品」の数量を表示する。数量・副産物の確率・連続プレスの回数は実レシピから取得する。27系列の中間素材から同じ章へ入る導線と、火薬から水素・窒素をたどる案内を維持した。
3. **工程に合った分留塔。** 原油5段、アンモニア3段、NO混合物・オレフィン・CO回収2段、脱硫3段、水素化分解4段を表示する。空気分離は14段。断面はすべて2×2。必要段数・東面入口・北面の各出口を実装の`DistillationProfile`と照合した。隣接する製品タンクはずらし、配管は軸の固定された経路で分離した。
4. **開放タンクの組立を可視化。** 直径4～32の29シーンを、床の半分→残りの床→外壁2段の順にした。最初から完成形を見せる構成を改めた。
5. **タービン関連の古い座標と構成を修正。** 運転・循環・異常対処の3案内で、回転位置・強調位置を高圧5断面／低圧10断面の現行配置へ合わせた。復水器の案内も9×9・10断面の低圧ユニットに置き換え、水箱を実際のケーシング隣接位置に配置。接続後は視点を回し、背後に隠れていた復水器と冷却配管を見せる。
6. **操作の案内を更新。** 原子炉の4連卓・7×4表示板のリンク、制御棒選択、AZ-5、モード操作を短く整理した。電解セル専用シーンの電線はCEEの`WireConnectionInstructions`で実端子へ接続し、説明用の直線描画を置き換えた。
7. **編集と検証を更新。** `PONDER_GUIDE.md`の旧鉢電解槽・廃止加熱器・旧ガスホルダーなどの説明を現行構成へ更新。翻訳検査に全シーンの正本・ページ・文数確認を追加した。

## 主要ファイルと生成方法

| ファイル | 役割 |
| --- | --- |
| `tools/data/ponder_lessons.json` | 全140シーンの日英中の題名・本文の正本 |
| `tools/generate_ponder_language.py` | 実行時キー、説明ページ、原料／製品表示を生成 |
| `ponder/PonderText.java` | ページ単位の表示と読む時間の調整 |
| `ponder/ManufacturingPonderScenes.java` | 原料、加工、製品を順に見せる共通再生処理 |
| `tools/generate_manufacturing_ponder.py` | 工程ごとに最小段数の分留塔と独立配管を生成 |
| `ponder/PlantPonderScenes.java` | タンクの段階表示、現行タービンの座標・回転 |
| `ponder/PlantSystemsPonderScenes.java` | 現行低圧ユニットと復水器の組立・視点 |
| `tools/generate_surface_condenser_ponder.py` | 復水器の配置データ。給水循環用の既存`generate_condenser_ponder.py`とは別の設備 |
| `gametest/ManufacturingPonderGameTests.java` | 実レシピの必要段数・2×2断面・入口と出口を照合 |

Javaの上記パスは`src/main/java/net/createplant/`からの相対パス。配置・翻訳は`src/main/resources/assets/create_refinery_n_reactors/`以下。

表示時間は、1ページの指定時間に加え、文字数（日本語等は2文字相当）から読む時間を見積もる。原則70 tick以上、見積もり上限220 tickとし、既存の長い演出に必要な指定時間は維持する。装置の物理計算や保存形式は変更していない。

説明を直す場合は正本を編集して`python3 tools/generate_ponder_language.py`を実行する。生成後の翻訳だけを書き換えない。新しいシーンには正本とカタログの両方を追加する。

## 確認結果

| 確認 | 結果 |
| --- | --- |
| Javaコンパイル | `compileJava`成功。最終の不要import・変数整理後にも実施 |
| 翻訳 | 日英中それぞれ4,566キー、重複・空文字・書式引数の不一致なし |
| Ponder文書 | 全140題名と全説明の正本・生成結果を照合。各ページ1～2文、全シーンが3言語に対応 |
| 配置の静的検査 | カタログ96シーン、説明512キー、関連付け237件の座標・ブロック状態等を確認。廃止設備の参照なし |
| 専用GameTest | **8件すべて合格**。既存の素材・レシピ・翻訳・蒸留配管に加え、8種類の分留塔構成を実装の必要段数・ポートと照合 |
| 実クライアント | NVIDIA RTX 4060で、通常モードの全140シーンをコンパイルしPonderUIで表示、検査合格。続いて代表12画面を記録し、最終の視点・数量表示の差分4画面も再確認 |
| 再生成 | 翻訳の再生成前後で3言語のファイルがバイト単位で一致 |
| 差分 | `git diff --check`合格 |

検査条件と結果は[checks.json](../quality/ponder-rebuild/checks.json)、[GameTestログ](../quality/ponder-rebuild/gametest.txt)、[全シーンのクライアント結果](../quality/ponder-rebuild/client-all-scenes.json)、[最終差分のクライアント結果](../quality/ponder-rebuild/client-final-views.json)を参照。

[代表12画面](../quality/ponder-rebuild/screenshots.json)には、復水器、電解セル、2段／3段分留塔、ガスホルダー、原子炉制御盤、小型RBMK、核融合炉、撹拌槽、タービン、直径32タンク、火薬の原料工程を収録。画像はComputer Useで実クライアントから取得した。

再実行：

```sh
python3 tools/validate_translations.py
python3 tools/validate_ponder_language.py
python3 tools/validate_plant_tutorials.py
./gradlew --no-daemon -I docs/quality/ponder-rebuild/tests.gradle runTurbineUnitGameTestServer
mc-debug ./gradlew --no-daemon -I docs/quality/ponder-rebuild/client.gradle runClient
```

## 未確認事項と環境上の制約

- 全シーンの読み込み・表示確認と、全手順を手動で通し再生することは別。全140シーンの全視点・全時刻を手動で確認したわけではない。英語・中国語の実画面の目視確認も未実施。
- 実クライアントは`CLIENT QUALITY PASSED`を記録した後、終了処理中に`libEGL_nvidia.so.0`内でSIGSEGVを起こした。Gradleの`runClient`自体は終了値134で失敗しており、起動から終了まで正常だったとは扱っていない。[終了を含む記録](../quality/ponder-rebuild/client-final-views.txt)を参照。
- この環境ではSoundSystemを起動できず、音の試聴は未確認。
- 本作業ではPonderの説明・配置・表示を検証した。全設備の実ワールド長時間運転、物理モデルの再校正、変更に無関係な全GameTestは実施していない。
