# 攪拌槽の硫酸ジルコニウム反応とゴーグル表示

実装コミット `1fe0451c77859081b2b3a49abc73b2a184212c91`

## 変更内容

- `ReactorMachineBlockEntity` で、反応に関係のない固体や副産物が残っていても、攪拌槽が液体だけの反応を実行できるようにした。固体と流体の両方を必要とする反応に対応する流体がある場合は、引き続きその反応のために確保する。
- `LiddedVesselBlockEntity` で、攪拌槽が加熱中に原料溶液をレシピ処理より先に蒸発させないようにした。
- `GoggleSpec` のステンレスタンク・攪拌槽の見出しから候補レシピ名を外し、稼働中は進行度、停止中は停止理由を表示するようにした。
- ジルコニウムの Ponder と製造資料に、硫酸ジルコニウム水和物、硫酸ジルコニル、ジルコニアの順で作る工程を反映した。各段で得られる固体を次の反応の原料として投入する。最初の反応は硫酸ジルコニウム溶液 500 mB を消費し、水和物と蒸気 400 mB を生成する。

## 検証

- Gradle の `jar` タスクが成功した。
- 関連 GameTest 31 件のうち 27 件が成功した。追加したテストで、砂・ジルコン・硫酸が残る状態からの反応、超過熱で加熱待機中の溶液保持、ジルコニアまでの工程、ゴーグル見出しを確認した。
- 残る 4 件は変更前の `08f42b8` でも失敗した。対象は `heaterRequiresRealHeatAndColumnRequiresColdFeed`、`heatedSlimeBlendBecomesTwoCutsAndAReusableCoolant`、`rebuildSaveLoadAndRetiredPortsKeepContents`、`everyStirredReactionRunsOverTheStirrerWithItsHeat`。
- ゴーグル表示の文言は GameTest で確認した。クライアント画面での目視確認は未実施。
