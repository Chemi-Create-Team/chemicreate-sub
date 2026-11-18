# 触媒の劣化と再生（B3）レポート

## 基準と実装コミット

- 作業ブランチ：`main`
- 作業開始時の基準コミット：`73f0b5c`
- 実装コミット：`81785eb`

## 依頼と決定事項

- 依頼：B3（触媒の劣化と再生）。触媒が使ううちに弱り、金属を足して再生する。
- ユーザーの決定：寿命は4,000バッチ。ハーバー・ボッシュ反応塔の触媒（四酸化三鉄の山）も劣化させる。

## 実装内容

### 触媒反応槽の触媒（6種）

- 耐久値4,000（`CEItems.CATALYST_BATCHES`）のアイテムにした。スタックは1個になる。
  - `setNoRepair`：バニラの「同じ道具2つを合わせて直す」レシピでは直せない。
- 反応槽（`ReactorMachineBlockEntity`）：
  - 使えるバッチ数＝ min（充填層の段数, 触媒の残り）。
  - 1サイクル終わるごとに、行ったバッチ数だけ耐久値を減らす（`wearCatalyst`）。
  - 残り0の触媒は反応槽に残し、状態を `catalyst`（触媒を使い切りました）にする。
  - ゴーグルに「触媒の寿命：残り／4,000 バッチ」。
- 再生（作業台・不定形）：鉄触媒＋鉄塊3、酸化触媒＋金塊3、分解触媒＋アルミ1、重合触媒＋チタン1、水素化脱硫触媒＋モリブデン1、水素化分解触媒＋ニッケル1 → 新品。

### ハーバー・ボッシュ反応塔

- 新しいブロック「使い切った四酸化三鉄の山」（`spent_triiron_tetroxide_pile`、落下するブロック、シャベル）。
- 構造判定（`HaberBoschStructure.isPile`）は、新しい山と使い切った山の両方を層として数える。使い切っても塔は崩れない。
- 反応塔（`ReactionTowerBlockEntity`）：
  - 1秒ごとに、働いている（新しい）層の数を数える。反応の速さ＝ 速度定数 × 働いている層の割合（`HaberBoschBed.activity`）。
  - 作ったアンモニア（mol）をためる。1層の寿命＝ 400 mBのアンモニアのmol数 × 4,000（`layerLife`）。寿命に達するごとに、一番下の新しい層を使い切った山に変える。
  - 全層を使い切ると状態は `spent`（触媒切れ）。
  - ゴーグルに「働いている層 n／全層、いちばん下の層の残り %」。
  - 保存：`TowerFresh`、`TowerWorn`。
- 再生（作業台・不定形）：使い切った山＋アルミナ1 → 新しい山。

### 文字列・文書

- JEI の注記（`note.reactor`・`note.reactor_pressure`）の「触媒は消費されない」を、「1バッチごとに劣化し、4,000バッチで使えなくなる」に直した（生成元の `generate_uranium_assets.py`・`generate_desulfurization.py` も）。
- Ponder の字幕に追記した：「1. 反応槽：基本構造と入出力」の手順3、「ハーバーボッシュ反応塔」の熱の手順。
- 文書：`docs/reaction-plant.md` に「触媒の劣化と再生」の節。`PETROCHEMISTRY.md`、`INDUSTRIAL_SYSTEMS.md` の「触媒は消費しない」の記述を直した。
- 生成スクリプト：`tools/generate_catalyst_wear.py`（使い切った山のテクスチャ・モデル・ルートテーブル、再生レシピ、翻訳）。

## テスト

| 確認 | 結果 |
| --- | --- |
| `bash gradlew build runGameTestServer` | 295件すべて合格（新規2件） |
| CIの検証スクリプト3本 | 合格 |

- `ReactorGameTests.eachBatchWearsTheCatalystUntilItIsSpent`：残り1バッチの鉄触媒・充填層2段で、1バッチだけ進み、触媒が使い切られて状態が `catalyst` になる。再生レシピがあり、修理はできない。
- `HaberBoschGameTests.theBedIsSpentFromTheBottomAndComesBackWithFreshPiles`：1.5層分で一番下が使い切られて速さが2/3、さらに使うと全層が使い切られて `spent`。新しい山を戻すと数え直す。再生レシピがある。
- 既存の `CraftingRecipeGameTests.theRefiningEquipmentNeedsNoRefinedMetal` は、精錬設備の部品のレシピを1つずつ数えていたため、鉄触媒の再生レシピで数が合わなくなった。再生レシピ（`_regeneration`）を数えないようにした。

## 未検証・制約

- 既存のワールドで重ねて持っていた触媒は、スタックが1個ずつに分かれない（バニラの扱いに従う）。本格運用前のため変換はしない。
- 触媒は、ほかの触媒の材料としても使える。そのとき劣化の程度は問われない。
- 実際の画面での耐久値のバーと、使い切った山の見た目（Ponder以外）は、撮影していない。

## 次回の注意

- 触媒に耐久値を使っているので、触媒を材料にするレシピを追加するときは、劣化した触媒でもよいかを考える。
