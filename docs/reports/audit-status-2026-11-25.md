# 監査メモの現行状況

確認対象は `4f17f82`。旧報告書には当時の計測値を残し、修正済みの項目へ後続のコミットと検証結果を追記した。

## 修正済み

| 項目 | 現行の実装と確認 |
| --- | --- |
| ホウ砂と塩水の蒸発レシピの衝突 | `5eaec76`。塩水8,000 mBのホウ砂は超加熱、塩水250 mBの食塩と蒸気は通常加熱。ホウ砂の製造Ponderも更新済み。`everyStirredReactionRunsOverTheStirrerWithItsHeat` は全件・関連テストの両方で合格した。 [修正報告](thermal-and-fluid-regression-review-2026-11-24.md) |
| 熱交換器の出口からの二相流 | `0ce6a7d`、`5eaec76`、`a3fea98`。乾き度付きで配管に出せる。実機では出口の乾き度7/8で分離器へ送り、蒸気4,200 mBと凝縮水600 mBを回収した。 [実機検証](gas-liquid-separation-playtest-2026-11-24.md) |
| 気液分離機のPonderと仕様書 | `5eaec76`。Ponderに二相流の入口と上下の出口を追加し、`reaction-plant.md` にも記載済み。 [気液分離器の仕様](../reaction-plant.md#鉄触媒によるアンモニア合成) |
| 密閉槽の蒸気保存、攪拌槽の逐次蒸発 | `9f41f47`。水だけなら攪拌槽の供給枠でも毎tick蒸発し、密閉した槽では減った水と同量の蒸気が貯まる。上面からの取り出しと開放時の排出も確認済み。 [再検証](lidded-vessel-water-boiling-recheck-2026-11-25.md) |

## 一部対応、または今回再現せず

- **反応塔の棚段。** `solidFeedFillsTheLowestTrayFirstAndKeepsCatalystsOut` は全件・関連テストの両方で合格した。過去の不安定な失敗の原因を除去する変更は確認できていない。
- **Create標準タンクの二相流。** 乾き度2/8と6/8の水を1,000 mBずつ実際のポートから入れると、内部で2,000 mB・4/8になった。量だけを指定すると4/8のまま取り出せたが、同じポートの在庫照会には乾き度が載らず、その表示に基づく流体指定付きの取り出しは空になった。RBMKは蒸気と水を別のスタックとして搬出し、コンプレッサには乾き度専用の処理が見つからなかった。 [二相流の実装範囲](../two-phase-flow.md#実装した範囲2026-11-24)
- **構造ガイド。** 現在登録されるゴーストガイドは、攪拌槽、タンク構成ブロックで作る円形タンク、核融合炉。設備ごとに対応範囲が異なる。
- **原子炉部品のPonder。** 4種のうち、`biological_shield_coolant_outlet` と `reactor_feed_bundle` は炉心のPonderに紐付け済み。`biological_shield_drive` と `reactor_fuel_channel_tube` の個別の紐付けは見つからなかった。 [旧報告書の更新](ponder-cleanup.md#その後の対応2026-11-25-時点)

## 残っている項目

- **加熱と反応。** 反応熱が未対応なのは `ProcessRecipe` の固定レシピ（メタノール合成など）。アンモニア合成は出口を「高温の合成混合物」の状態で出すことで熱を表している。フィッシャー・トロプシュ合成と水性ガスシフトは反応塔の経路で、別の実装。どれも反応ごとの反応熱データを足す設計が要る。
- **沸騰。** 蒸発熱の端数は保存・読み込みで失われなくなった（[2026-11-25の対応](remaining-issues-2026-11-25.md)）。Createタンク・タンクケーシングはふた付き槽と異なる経路のまま。蒸気の粒子と音のクライアント確認は未実施。
- **中国語。** 英語と同じ値が残る件数は、2語以上の英単語が続く基準で0になった（[対応](remaining-issues-2026-11-25.md)）。1語だけの値や数式は数えていない。
- **構造判定と施設実績。** 設備ごとの判定を使っている。`FormationEffects.formed` には `Announcer.update` からの呼び出しがあり、`ReactorMachineBlockEntity` がその通知を使う。施設実績は引き続きプレイヤー周辺を40 tickごとに走査する。
- **クラスとテスト。** `ReactorMachineBlockEntity` は1,121行、`QuarryDriveBlockEntity` は964行、`CraneDriveBlockEntity` は934行。確認対象の `src/test` には3ファイルがあり、`HeatContent`、`PhaseChange`、`TwoPhase` の独立したJUnitテストは見つからなかった。
- **文書と生成スクリプト。** 確認対象には報告書のMarkdownが407本ある。`generate_manual.py` は、手作りのガイド実績JSONを消さなくなった（言語ファイルは今も書き換える）。`validate_ponder_language.py` と `validate_plant_tutorials.py` は、この作業の前から失敗している（古い `manufacturing_pvc.step_4` のページキーと、構造NBTのタグ種別）。

## 今回の再検証

Java 21で `check` は成功した。全GameTestは762件中760件が合格、関連する5クラスの再実行では64件中62件が合格した。両方で失敗したのは、`DriveLessVesselGameTests` の `aCogwheelTurnsTheStirrerWhichRunsTheTankAndCarriesItsStress` と `theTankTakesNoRotationOfItsOwn`。後者は加熱中に回転が止まっている槽の水が1,000 mBのままという前提で、現在の水だけの槽は停止中でも蒸発する。前者は給水1回から1,000 mBの加圧給水が得られるという検証で失敗した。どちらもホウ砂や反応塔のテストとは別件。その後 `c3fa98f` で、この2件を含む全765件が合格した（`preheat_water` レシピと旧IDの整理に伴うテストの移行）。

Create標準タンクの追加試験は、上記の受け入れ量、乾き度、照会値、取り出し結果を実機能のポートで確認した。
