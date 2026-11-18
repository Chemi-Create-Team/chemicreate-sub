# 温度・圧力の等級化 E・全段階完了レポート

2026-11-14。実装コミット：`4ebcfdf6cdf6e24575da5abfa0a42b35b6387b42`（`docs: align thermal tutorials and machine displays with fluid grades`）。

## 変更内容

- JEIの流体ツールチップを等級表示に統一。説明書第7・9・10章、全27製造系統を含むPonderの旧温度・圧力・破裂・凍結・予冷の案内を現行仕様へ更新した。日英中の共通原稿は `tools/data/ponder_lessons.json`、等級と機器表示は `tools/generate_grades.py`。
- 製造Ponderの空気ラインは一段圧縮→加圧空気→分留塔。窒素の液化は動力と水を使うコールドボックスへ変更。液体酸素の気化は熱交換器の別系統に接続する。
- 冷凍ラインから旧アンモニア圧縮・減圧・不凍液の温度循環を撤去。ラジエーターで液体アンモニアを回収し、熱交換器で気化させながら工程流体を冷却する。図のタンク・配管も高温側と極低温側の材質を合わせた。
- 精製水の図は管束ボイラーの高圧蒸気→ラジエーター→清浄な受槽→精製水フィルターへ変更。蒸気の種類と固定変換比は実装の共通表から取得し、存在しない高圧蒸気バケツは参照しない。普通の水を混ぜると蒸留由来の印を失う仕様を維持した。
- アンモニア分離器のPonder在庫を実際のタンク保存形式へ修正。タービン図に通常ポンプと駆動を追加。核融合炉図は古いPythonの構造複製を削除し、ゲーム本体の `FusionStructure.cells` から組み立てる。専用の `--ponder-only` で筐体画像を再生成せず更新できる。
- 槽のハッチ放出Ponderで、空のFluidStackを直接保存して落ちる問題を実描画で検出。通常タンクの保存処理へ統一して修正し、再実行を合格した。圧力計のPonder状態も等級と警報だけを使う。
- 表示整理中に残っていた溶融塩電解炉・ガラス固化炉の連続浴温・昇温待ち・放熱を撤去。電解炉は5 kW以上で200 kJ/Al、400 kJ/Ti・Zrの有限電気仕事を使う。固化炉は100 kW以上、1.5 MWで従来の最大処理速度。入力・出力・途中進捗の保存は維持した。
- 燃料プールの旧温度キャッシュ・温度上限・NBT表示を削除。水位・構造・燃料在庫と崩壊熱による有限の水消費は維持。空の槽は未測定表示とする。RBMK・核融合炉内部の温度・核反応・制御則は変更していない。
- `DESIGN.md`、`DEVELOPMENT_CHEMISTRY.md`、READMEと現行設備文書を更新。旧物理の文書は要点と現行リンクへ置換し、過去の `docs/reports/` の測定記録は保持。[等級ガイド](../thermal-grades.md)を現在の運転手順の入口にした。

## 検証と数値

| 確認 | 結果 |
| --- | --- |
| Gradle build | 合格 |
| 既定GameTest | **519/520件合格**。既知の清掃1件だけ失敗 |
| E関連8クラス | **44/44件合格** |
| 3言語の静的検証 | 各**4,527キー**、重複・空・書式不一致なし |
| Ponder | 全**140シーン**を実クライアントでコンパイル、実行時翻訳を合格 |
| 製造導線 | **27系統・512説明・235接続**、構造・登録IDの検証合格 |
| 生成の再実行 | 対象3製造図・核融合図・説明書・翻訳・等級を再生成して追跡ファイル差分なし |

既知の失敗は `VesselFoulingGameTests.aFouledProcessStopsThenResumesAfterOneManualBrush`。C実装コミットの同メソッド本文との一致を再確認した。既定全体タスクの終了はこの1件による失敗であり、全件合格とは扱わない。

関連試験は `ElectrolysisFurnaceGameTests`、`PowerPlantGameTests`、`MalformedDataGameTests`、`NuclearSavedValueGameTests`、`FuelPoolGameTests`、`ManufacturingPonderGameTests`、`PlantTutorialGameTests`、`PurifiedWaterGameTests`。電気仕事・材料消費・出力閉塞・保存、旧温度値の不採用、設備図のレシピと構造、精製水の在庫を確認した。

再現コマンド（JDK 21）：

```sh
bash gradlew --no-daemon build -I docs/quality/compact-centrifuge/verification.gradle \
  -DtestDir=/dev/shm/createplant-thermal-e-final \
  -DtestClasses=ElectrolysisFurnaceGameTests,PowerPlantGameTests,MalformedDataGameTests,NuclearSavedValueGameTests,FuelPoolGameTests,ManufacturingPonderGameTests,PlantTutorialGameTests,PurifiedWaterGameTests \
  runGameTestServer
python3 tools/validate_translations.py
python3 tools/validate_ponder_language.py
python3 tools/validate_plant_tutorials.py
```

新しい試験ワールドを使う。RAM上のワールドは `server.properties` の `sync-chunk-writes=false` と `view-distance=2` を使用した。ディスク保存待ちを流体処理の停止と混同しないこと。

記録：[e-verification.json](../quality/thermal-grades/e-verification.json)。ログは `/workspace/outputs/thermal-e-full01.log`、`thermal-e-verified-test.log`。

## 実クライアント

NVIDIA RTX 4060・日本語で実施。核融合炉、タービン、アンモニア冷却、精製水、空気製造を描画し、ハッチPonderの例外修正後は槽の保守・燃料プール・電解炉を確認。全140シーンと実行時翻訳は `CLIENT QUALITY PASSED`。[確認結果](../quality/thermal-grades/e-client-result-ja_jp.json)と[設定](../quality/thermal-grades/e-client-followup.gradle)を保存した。高圧蒸気の精製水図と段間ポンプも[最終設定](../quality/thermal-grades/e-client-final.gradle)で再実行し、[最終結果](../quality/thermal-grades/e-client-final-result-ja_jp.json)でも全140シーン・実行時翻訳の合格を確認した。

画面資料：[冷媒回収のラジエーター](../quality/thermal-grades/e-radiator.png)、[電解炉と固定仕事の説明](../quality/thermal-grades/e-electric-furnace.png)。これは実ゲームのPonder画面で、生成イメージではない。

品質チェック完了後、NVIDIA `libEGL_nvidia.so` 内でSIGSEGV・終了値134が再現する。品質合格とプロセスの正常終了は区別する。OpenALの出力装置がないため、実際の音声再生は未確認。初回実行のハッチPonder例外は修正済みで、この終了時GPU問題とは別である。

## A2からの全体記録

| 段階 | 実装コミット | 関連試験・記録 |
| --- | --- | --- |
| A2 タンク | `ce374648` | 37件、[報告](thermal-grades-a2.md) |
| A3 弁・計器 | `2029590c` | 15件、[報告](thermal-grades-a3.md) |
| A4 気体搬送・圧縮 | `e25b1cc9` | 22件、[報告](thermal-grades-a4.md) |
| B1 流体状態 | `efd8b0e1` | 41件、[報告](thermal-grades-b1.md) |
| B2 槽・反応 | `df4e6779` | 62件、[報告](thermal-grades-b2.md) |
| B3 熱の変換装置 | `eecae085` | 58件、[報告](thermal-grades-b3.md) |
| C 蒸気・炉の境界 | `b866a2ca` | 106件＋60,000 tick運転6構成、[報告](thermal-grades-c.md) |
| D 撤去・整理 | `24b81f4d` | 関連68件・別枠49件、[報告](thermal-grades-d.md) |
| E 説明と表示 | `4ebcfdf6` | 関連44件・全140 Ponder |

段階ごとのビルドと関連試験を終えてmainへ反映し、[進捗表](../specs/thermal-grades-plan.md)のS1～S7を完了にした。

## 未確認・引き継ぎ

- Cの通常燃料・直径5・燃料部3マスの冷態AUTOは**207.2955%**の上振れを再現した。既知の制御問題で、今回制御則を変更していない。全6構成の手動出力追従による60,000 tick＋AZ-5は合格。最大炉の定格765 MWを「測定中ずっと765 MW」とは解釈しない。実測の揺れと燃料温度はCの数値表を参照。
- 等級化後の核融合プラント全系を、実ポンプとタービン給電だけで長時間自立運転する試験は未実施。Cの4000ステップ境界結合試験は同一ワールド・有限在庫だが、試験電力と手動搬送を使った。
- 全140シーンの全時間帯を3言語で目視したわけではない。日本語の代表描画と、3言語の全説明・登録・書式を確認した。大規模工場のMSPT比較も未測定。
- 旧ワールドの熱・二相成分、削除した弁・設定、Display Linkの旧選択番号に移行処理はない。旧FusionPlantGameTests専用タスクも復元しない。現行の固定変換と炉内物理は別の境界として扱う。
