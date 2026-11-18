# 有毒ガスの漏れとガスマスク（C2）レポート

## 基準と実装コミット

- 作業ブランチ：`main`
- 作業開始時の基準コミット：`ffce53b`
- 実装コミット：`1f46d3d`

## 依頼と決定事項

- 依頼：C2。硫化水素・フッ素などの配管やタンクが壊れると、周りに有毒なガスが広がる。換気やガスマスクで防ぐ。
- ユーザーの決定：ガスマスクは新しいアイテム。換気は時間と屋外だけ（Createのファンは使わない）。

## 実装内容

- 有毒な流体と強さ（`safety/ToxicGas.java`）：流体の登録名で決める。刺激性（毎秒1）、有毒（毎秒2）、フッ素（毎秒3）。表は `docs/toxic-gas.md`。
- 雲（`ToxicGas.release`）：
  - バニラの `AreaEffectCloud` に、効果「有毒ガス」（3秒、強さ＝増幅値）とガスごとの色を付ける。エンティティタグで自分の雲と分かるようにした（保存される）。
  - 広さ＝1.5＋1.5×√（mB÷1000）（最大6）。長さ＝400＋mB÷5 tick（最大2,400）。空が見える所では3分の1。消えるまでに広さが半分まで縮む。
  - 1.5ブロック以内に雲があれば、その雲を広げ・延ばす。
- 漏れる場面：
  - `PressureVesselBehaviour`：破裂（中身すべて）、腐食の漏れ（毎秒1%）。
  - `ReliefValveBlockEntity`：大気へ逃がした分。
  - `PipeWearBehaviour`：劣化による配管の破損（250 mB）。
- 効果（`safety/ToxicGasEffect.java`）：1秒ごとに（増幅値＋1）のダメージ。ダメージタイプ `toxic_gas`（`bypasses_armor` のタグ）。死亡メッセージ（英・日・中）。
- ガスマスク（`registry/CESafety.java`）：
  - 頭の防具（防御1）。耐久値1,200＝吸収缶の秒数。`setNoRepair`。
  - `MobEffectEvent.Applicable` で、吸収缶が残っていれば効果をかけず、吸収缶を1使う。使い切ると防げない（壊れない）。
  - レシピ：合成ゴム2・板ガラス2・鉄板2・木炭1。再生：ガスマスク＋木炭2。
  - テクスチャ：アイコン（ゴムの面体、ガラスの目、オリーブの吸収缶）、頭のレイヤー、効果のアイコン（`tools/generate_gas_mask.py`）。
- Ponder「有毒ガスとガスマスク」（`tools/generate_toxic_gas_ponder.py`、4手順）。ガスマスクから開く。
- 文書：新規 `docs/toxic-gas.md`。`docs/pipes-and-tanks.md`、`PONDER_GUIDE.md` を更新した。

## テスト

| 確認 | 結果 |
| --- | --- |
| `bash gradlew build runGameTestServer` | 299件すべて合格（新規2件） |
| CIの検証スクリプト4本 | 合格 |
| 実クライアント（日本語） | Ponder全96場面の題名が合格。「有毒ガスとガスマスク」を撮影して目視した |

- `ToxicGasGameTests.aToxicCloudPoisonsTheBareAndAMaskWithFilterKeepsItOut`：水では雲ができない。塩素の雲が1つにまとまり、広さ4.5、長さが屋内外で変わる。素顔・使い切ったマスクは傷つき、吸収缶の残るマスクは傷つかず吸収缶が減る。
- `ToxicGasGameTests.aTankOfChlorineThatGivesWayLetsOutACloud`：塩素の入ったタンクが壊れると、その場所に雲ができる。

## 未検証・制約

- プレイヤーが頭に着けたときの見た目は撮影していない（頭のレイヤーのテクスチャを画像で確認した）。
- プレイヤーが手でタンクや配管を壊したとき、中身は雲にならない（破裂・腐食・配管の破損・安全弁だけ）。
- 気体パイプの網は壊れても雲を出さない。
- 雲は風や地形に流されない。
- 安全弁の雲は、吹出口の上のブロックの位置に出る。

## 次回の注意

- 有毒な流体を追加したら、`ToxicGas.GASES` に強さと色を足す。
