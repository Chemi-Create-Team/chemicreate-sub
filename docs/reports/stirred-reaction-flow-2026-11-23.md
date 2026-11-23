# 攪拌槽の毎tick処理と内容物表示

実装コミット `9c9d14264a2e9cc42e1690a83d1924f38ad61b15`

## 処理

- `ReactorMachineBlockEntity` で攪拌槽の流体の投入量と産出量を毎tickの進行量で配分する。供給は累積量の切り上げ、産出は切り下げとの差分を使う。濃縮液の端数は原料ごとに繰り越し、濃度2倍の溶液なら250 mBで通常濃度の500 mB分になる。
- 固体原料は開始時に支払い、固体生成物は完了時に取り出せる。途中で止まっても確定済みの流量は保持し、未完了分を再開する。保存データには確定した原料条件、生成物、端数、進行度を記録する。従来版の固体を伴う反応で未確定の進行度は、原料を保持した状態で開始し直す。
- `LiddedVesselBlockEntity` と `GoggleSpec` で、攪拌槽の液体・気体とその成分をmB単位で、固体原料と生成物を個数付きで示す。同じ化学種でも水と蒸気のように相が異なれば別の行に出る。`GoggleOverlayMixin` は長くなった表示を画面に収める。クライアントへの在庫同期は既存の5tick間隔を使う。
- ジルコニウムのPonderに、反応中の蒸気発生と完了時の水和物取り出しを反映した。

## 検証

- `./gradlew --offline --no-daemon jar` が成功。対象のGameTestは33件中30件が通過した。
- 基準32 RPMで硫酸ジルコニウム溶液500 mBを毎tick 5 mBずつ消費して蒸気を4 mBずつ生成すること、2倍濃縮液の残量と丸めが保存後も保たれること、反応完了時に水和物が1個だけ得られることを確認した。
- ゴーグルのテストで、Shiftなしで全流体を表示し、水と蒸気を別々に数え、固体の原料と生成物も表示することを確認した。Minecraftクライアントで配布テストワールドを開き、稼働17%で溶液415 mB、硫酸100 mB、蒸気68 mB、ジルコンと砂が同時に見えることを確認した。
- 残る3件は以前にも失敗したテスト。熱交換器の平均温度 `heaterRequiresRealHeatAndColumnRequiresColdFeed`、冷媒の温度 `heatedSlimeBlendBecomesTwoCutsAndAReusableCoolant`、ホウ砂レシピの選択 `everyStirredReactionRunsOverTheStirrerWithItsHeat`。攪拌槽を大量設置したときの性能測定は未実施。
