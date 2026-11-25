# 思案画面（Ponder）の整理と欠けたシーンの復旧 レポート

## 基準と実装コミット

- 作業ブランチ：`main`
- 作業開始時の基準コミット：`d918df8`
- 実装コミット：`05eebabbb951dee4576eb8a34806bf9b242abf8b`

## 見つかった不具合

- **現役の設備に Ponder がなかった。**
  - 対象：減圧弁、背圧弁、安全弁、配管材質（強化パイプ・超耐圧パイプ・鉄の流体パイプ・気体パイプ）、クランクケース式の二段ガス圧縮機（クランクケース・低圧/高圧シリンダー・中間冷却器・ガスレシーバー）。
  - 原因：以前のシーンが、今はない圧力室・高圧ポンプ・断熱パイプなどを使っていたため、カタログから外れていた。
- **`tools/generate_plant_tutorials.py` が `tutorials.json` を丸ごと上書きしていた。** 実行すると、他のスクリプトが作った今のシーンが消え、削除済みの15シーンが復活する状態だった。
- **削除済みシーンだけを再生成するスクリプトや処理が残っていた。**
  - スクリプト：`generate_transport_tutorials.py`、`refine_phase_pressure_ponder.py`、`generate_passive_heat_ponder.py`。
  - 処理：`generate_process_tutorials.py` の `extend()`・`layout()`、`generate_heating_tutorials.py` の更新処理、`generate_pressure_assets.py` の旧3シーンと削除済みブロックの名前、`PlantPonderScenes` の `passive_heat` 用の分岐。
- **使われていないデータ。**
  - 登録されていないシーンの題名20件、`fraction_filters` の説明文5件。
  - 配置データ2件（存在しないブロックを含む `electrolysis_ports`・`fraction_filters`）。
  - Javaから使われていない翻訳キー3件（`pressure.*.pump`・`ready`・`setting`）。
- **字幕と文書の誤り。**
  - 潤滑油カートリッジの手順4が「石油設備に使う」となっていた。今はガス圧縮機用。
  - `PONDER_GUIDE.md` の一覧の大半が、存在しないシーンだった。
  - `docs/pressure.md` が存在しないPonderを案内していた。

## 実装内容

- **新しいシーン**（どれも機能ごとの生成スクリプトで作る）
  - `pressure_valves`「減圧弁・背圧弁・安全弁」（`tools/generate_pressure_valve_ponder.py`）：ポンプの揚程と区間の圧力、減圧弁（出口が設定値で止まる）、背圧弁（入口が設定値を超えたときだけ流す）、設定範囲0.1～250 bar、安全弁の取り付けと放出先。値は `PressureSections`・`PressureSetting`・`ReliefValveBlockEntity` に合わせた。
  - `pipe_materials`「流体に合う配管を選ぶ」（`tools/generate_pipe_material_ponder.py`）：7種類の配管の温度・圧力・耐食性（`PipeMaterial` の値）、気体パイプ、劣化の時間（`TransportConfig` の既定値60秒・240秒）。
  - `crankcase_compressor`・`crankcase_compressor_large`「二段式ガス圧縮機を組み立てる」「大型の二段式ガス圧縮機を組み立てる」（`tools/generate_crankcase_ponder.py`）。
    - 以前の原稿の配置と字幕を引き継いだ。押しのけ量50 mB、最大3ブロック、大型6段・9倍、はずみ車なしで応力1.5倍、中間冷却器2～6ブロック・4 mB/t は、実装の定数と一致を確認した。
    - 古い手順は直した：ベイスンでの合成ガスの混合（今は撹拌反応槽）、水を使うラジエーター（今は直接冷却）、旧型の圧力計（今は配管圧力計と警報接点）。
- **生成スクリプトの安全化**
  - `generate_plant_tutorials.py` は、指定したシーン（省略時は担当の全シーン）だけをカタログに統合する。他のシーンは消さない。翻訳も担当分だけ書く。
  - 担当の原稿は、出荷中の字幕・カタログと一致させた。
- **文書**：`PONDER_GUIDE.md` を、登録済みのシーンの一覧と、今の作り方・検証方法に書き直した。`docs/pressure.md` の案内も直した。

## テスト

| 確認 | 結果 |
| --- | --- |
| `bash gradlew build`、`runGameTestServer` | 成功、258件すべて合格 |
| CIと同じ4つの検証スクリプト | 成功 |
| 出荷中の全配置データ | 未登録の自MODブロックを含むものがないことを機械的に確認 |
| 実画面のPonder（日本語） | 全84シーンの品質チェックに合格。新しい4シーンを撮影 |

## その後の対応（2026-11-25 時点）

- **ガスホルダー球と横型流体タンクのPonderは修正済み。** 両方のシーンが登録され、実画面も後続の検証で確認した。 [Ponderの修正報告](ponder-and-air-liquefaction.md)
- **窒素の入手経路は修正済み。** 空気を圧縮・冷却し、分離塔で窒素を回収できる。現在の吸気にはCreateのノズルを使う。 [空気の分離](../distillation-plant.md) · [吸気の仕様](../material-pumps.md)
- **原子炉部品の紐付けは4種中2種を確認。** `biological_shield_coolant_outlet` と `reactor_feed_bundle` には炉心のPonderが登録済み。`biological_shield_drive` と `reactor_fuel_channel_tube` は、個別の紐付けが見つからなかった。
