# 石油ラインと PETROCHEMISTRY.md の整理 レポート

## 基準と実装コミット

- 作業ブランチ：`main`
- 作業開始時の基準コミット：`9e048ba`
- 実装コミット：`0266415aab20b971db26dee9bafcf8292764176b`

## 調査で分かったこと

- **原油を処理できる設備がなかった。**
  - CDGの蒸留塔は、このMODの精製と重ならないよう、Mixinとレシピの除外で無効にされている。
  - このMODの蒸留塔（`fractionating_column`）は、空気分離塔（液体空気 → 酸素・アルゴン・窒素）に置き換わっている。
  - `separate_fractions` は「冷却済み留出物」を入力にしていたが、その流体を作る処理がどこにもなかった。
  - その結果、ガソリン・軽油・重油・ナフサ・潤滑油を作れず、分解・重合・脱硫・カートリッジまで、石油系のすべてが原料を得られなかった。
- **PETROCHEMISTRY.md が、登録されていない設備やレシピを説明していた。**
  - 設備：オイルシェールの鉱脈、原油抽出機、4段の蒸留塔、接触分解装置、重合反応器、液体燃料発電機。
  - レシピ・機能：アスファルト、石油設備の潤滑。
  - 存在しない検証スクリプト（`validate_petrochemical_resources.py`）、JEIの石油化学カテゴリ、3本のPonder。

## 実装内容

- **原油の蒸留 `distill_crude`**（反応生成物分離塔、`FRACTION_SEPARATOR`）
  - 入力：620 K以上の原油1000 mB。
  - 出力：ガソリン200・軽油300・重油200・ナフサ200・潤滑油100 mB。量は以前の `separate_fractions` と同じ。
  - 温度は既存の `ThermalStreams.accepts` の原油の判定（620 K以上）をそのまま使う。
  - 棚段1段ごとに1バッチ。分離塔の出力槽5つに収まる。
  - 選択はユーザーの指定による。新しいブロックは追加していない。
- **削除した旧型**
  - 流体：高温留出物・冷却済み留出物。あわせて危険性・物性・熱物性・冷却の別名・翻訳も削除した。
  - 翻訳：`separate_fractions` と、実在しないレシピ名 `heat_crude`。生成元の `tools/generate_reactor_assets.py` の対応表も直した。
  - 既存ワールドでは、この2種類の流体がタンクに残っていた場合、その中身が失われる。本格運用前のため、旧型を残さない方針に従った。
- **Ponder**：「原油を蒸留する」（`plant/crude_distillation`、4手順、分離塔から開く）。生成元は `tools/generate_crude_distillation_ponder.py`。
- **文書**
  - `PETROCHEMISTRY.md`：現在の設備で全面的に書き直した。全体の流れ、各工程の入出力と温度・触媒、触媒のレシピ、実在する加工レシピ、燃料と潤滑油の使い道、Ponder・JEI。
  - 脱硫の節は維持した。
  - `INDUSTRIAL_SYSTEMS.md`（精製）、`PONDER_GUIDE.md`（石油と素材）、`docs/diesel-generators-integration.md`（蒸留の流れ）、`DEVELOPMENT_CHEMISTRY.md`（概要と検証範囲）の古い記述も直した。

## テスト

| 確認 | 結果 |
| --- | --- |
| `bash gradlew build` | 成功 |
| `bash gradlew runGameTestServer`、`-PwithJei=false` でも | どちらも258件すべて成功 |
| 新規 `flashColumnDistillsHeatedCrudeIntoFiveFractions` | 冷たい原油は拒否。630 Kの原油2000 mBを棚段2段で処理し、5留分がちょうど2バッチ分（400・600・400・400・200 mB）出る |
| CIと同じ4つの検証スクリプト | 成功 |
| 実画面のPonder（日本語） | 全80シーンの品質チェックに合格し、手順3を撮影。字幕の改行を見て文言を調整した |

## 未検証・制約

- CDGのポンプジャックの原油を工業用加熱器で620 Kまで加熱し、分離塔へ送る一連の流れは、ゲーム内では通しで確認していない。
  - 加熱は既存の加熱器の試験で、蒸留は今回のGameTestで、それぞれ別に確認している。
- 生成した留分の温度は、各流体の初期温度で出てくる。蒸留後の熱をそのまま持たせる処理はない。以前の仕様と同じ。

## 残っている問題（今回は直していない）

- `tools/generate_process_tutorials.py` に、カタログ外の古いPonder原稿（`module_refinery` など、存在しない設備の説明）が残っている。
- `PONDER_GUIDE.md` の石油以外の行にも、カタログにないPonderの題名（持続性肥料など）が残っている可能性がある。今回は石油の行だけ直した。
