# 残っていた課題の修正（2026-11-25）

[監査メモ](audit-status-2026-11-25.md)に残っていた項目のうち、コードとデータで直せるものを直した。

## 修正した項目

- **水の沸騰の最初の塊。** 20℃から加熱した水は、100℃に達した計算で10 tick分の余剰熱をまとめて蒸気にしていた（8,000 mBで143〜217 mB）。`TankHeating` が、次の10 tickの加熱で沸点に届く水を見つけたら、その時点から1 tickずつ計算するようにした。余剰熱は最大1 tick分になる。新しいGameTest `waterWarmedFromColdBoilsOffWithoutAFirstLump` が、1回の計算で出る蒸気を1 tickのバーナー分（23 mB）以下と確認する。修正前は217 mBで失敗する。
- **蒸発熱の端数が保存されない。** `TankHeating` の端数（`steamCarry`）と、槽がまだ受け取っていない蒸気を保存・読み込みに加えた。ふた付き槽、タンクケーシング、Createタンクの保存処理が `TankHeating` の保存を呼んでいるので、3つとも対象になる。反応機械（`ReactorMachineBlockEntity`）の反応熱用の加熱器は保存していないので、これは対象外。新しいGameTest `theFractionOfSteamStillOwedSurvivesASaveAndLoad` は、保存した加熱器と読み込んだ加熱器が同じ蒸気を出すことを確認する。修正前は7 mBと6 mBで失敗する。
- **生成スクリプトが消したIDを出す。** `generate_grade_recipes.py`（`evaporate_ammonia` と `freeze_water` を `fluid_state` の液相で出す。出力は登録済みのJSONと同一）、`generate_grades.py`、`generate_synthesis_assets.py`、`generate_desulfurization.py`、`generate_hydrocracking.py`、`generate_lidded_vessel_ponder.py`、`generate_refrigeration.py`、`generate_seasonal_plant.py`、`generate_quality_assets.py`、`generate_world_liquids.py` から、削除済みの流体・ブロック・バケツの名前を外した。作業用の複製で実行し、削除済みIDのキーが言語ファイルに戻らないことを確認した。この確認では、他のキーや画像が変わる既存の差分も出たので、リポジトリには反映していない。`generate_thermal_control_assets.py` は構文エラー（文字列中の `'`）で実行できなかったので直した。
- **`generate_manual.py` が手作りの実績を消す。** ガイド実績フォルダを空にする処理をやめた。以前は `industrial_fertilizers` と `pressure_infrastructure` が消える状態だった。
- **中国語の未翻訳。** 英語と同じ値で2語以上の英単語が続くキー294件を翻訳した（核融合炉、冷却塔・冷凝器、PID、燃料サイクル、冷箱など）。`validate_translations.py` は通過。再生成で英語に戻らないよう、生成元に入れた：Ponderの文は `tools/data/ponder_lessons.json` と `fusion_fuel_lang.json`、その他は新しい `tools/data/zh_cn_overrides.json` を `generate_fusion_assets.py` と `generate_plant_systems.py` が読む。
- **ガス化炉の蒸気。** `gasifier_steam` タグ（水だけを列挙し、Javaの `isSteam` で絞っていた）をやめ、レシピが「水の気相」を直接指定する。
- **原子炉部品のPonder。** `biological_shield_drive` と `reactor_fuel_channel_tube` を、この2つを使う小型RBMKのPonderに結び付けた。
- **二相流のPonder。** すでに `ammonia_separator.two_phase`、`ammonia_separator.reactor_split`、`rbmk_core.water` にあるので追加はない。

- **反応塔の棚段のテストが不安定。** `solidFeedFillsTheLowestTrayFirstAndKeepsCatalystsOut` の最後の確認が、16個のイットリウムケーキを1つの落下物として探していた。バニラの `Containers.dropItemStack` は落とす山を10〜30個の乱数で分けるため、16個が2つに分かれることがあり、およそ3割で失敗した。落ちた合計が16個かどうかを確認する形にした。原因はゲーム側のバグではなくテストの書き方。

## 検証

- GameTestサーバーを全件実行：変更を入れた状態で、上記の不安定なテスト1件だけが失敗し、他の全件は合格した。そのテストを直して、クラス単位で再実行し全11件が合格した。全件を最後にもう一度実行してはいない。
- `build`（GameTestを除く）は成功。`validate_translations.py` は通過。

## 直さなかった項目と理由

- **反応熱（ProcessRecipe）。** メタノール合成などの固定レシピに反応熱を足すには、反応ごとの熱量と、槽の温度上限との釣り合いの設計が要る。アンモニア合成は出口を高温の状態で出す形で熱を表しているので、足すと二重に数える。着手していない。
- **Create標準タンクの乾き度。** 在庫照会に乾き度が載らない。Create側のタンクの内部を差し替える変更になるので、保留。
- **JUnit。** `HeatContent`、`PhaseChange`、`TwoPhase` は `FluidStack` とレジストリが要るため、GameTestで検証している。
- **クラスの大きさ。** `ReactorMachineBlockEntity` などの分割は、動作を変えない大きな差分になるので、この作業では扱わない。
- **古い検証スクリプト。** `validate_ponder_language.py`（`manufacturing_pvc.step_4` の古いページキー）と `validate_plant_tutorials.py`（構造NBTのタグ種別1）は、この作業の前から失敗する。
- **蒸気の粒子と音のクライアント確認。** 未実施。
