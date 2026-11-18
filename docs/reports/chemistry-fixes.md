# 化学工程の不整合の修正 レポート

## 基準と実装コミット

- 作業ブランチ：`main`
- 作業開始時の基準コミット：`f1779b8`（脱硫の作業で見つけた問題。[desulfurization.md](desulfurization.md) の「範囲外の問題」）
- 実装コミット：`0c06976e8171dfa1b6b648923615be4251438b19`

## 修正内容

### 1. 溶融ポリマーをアイテムにする手段がなかった

- 触媒反応槽の重合で溶融ポリエチレン・溶融ポリプロピレンはできるが、それをポリマーのアイテムに変える処理がなかった。
  - 文書には「水100で冷却・固化 → 樹脂2個＋温排水100」とあったが、冷却器は温度を下げるだけだった。
  - 以前の小型の重合反応器も登録されておらず、ポリマーは事実上入手できなかった。
- 追加したCreateの混合レシピ（`recipe/mixing/polyethylene_from_melt.json`、`polypropylene_from_melt.json`）：溶融ポリマー250 mB＋水100 mB → ポリマー2個＋温排水100 mB。
  - 作業鉢とミキサーで水に入れて急冷し、粒にする（実際の水中ペレタイザーに相当）。量は文書の記述どおり。
  - 加熱は不要。
- 使われていなかった `ProcessRecipe` の `product`（アイテムの生成物）を削除した。この処理のための部品とみられるが、どこからも参照されていなかった。
- Ponder「工業用樹脂を製造する」の手順1を、ポリマーの作り方の説明に差し替えた。シーンにはもともと作業鉢とミキサーがあり、配置は変えていない。生成元 `tools/generate_plant_tutorials.py` の原稿も同じ文に直した（スクリプトは実行していない）。

### 2. 硫黄を燃やすレシピの酸素の量

- 変更前：硫黄の粉1個＋酸素250 mB → 二酸化硫黄500 mB。S + O₂ → SO₂ は1:1なので、酸素が半分足りなかった。
- 酸素を500 mBにした（`recipe/pressure_reaction/sulfur_dioxide.json`、生成元 `tools/generate_uranium_assets.py`）。
- 硫黄の粉1個＝硫黄を含む気体500 mBという換算は変わらないので、クラウス法との釣り合いも保たれる。

### 3. DEVELOPMENT_CHEMISTRY.md の記述

- 硝酸アンモニウムは撹拌反応槽（`pressure_reaction`）で作る、と書き直した。変更前は「Mixer と Basin」「BasinRecipe」と書かれていた。
- 「GameTestで確認済み」とあったが、該当するテストがなかった。`stirredReactorMakesAmmoniumNitrateWithoutHeat` を追加し、硝酸250 mB＋アンモニア250 mB → 硝酸アンモニウム1個（加熱なし）を確認した。
- `INDUSTRIAL_SYSTEMS.md`・`PETROCHEMISTRY.md` に、急冷の手順を追記した。

## テスト

| 確認 | 結果 |
| --- | --- |
| `bash gradlew build` | 成功 |
| `bash gradlew runGameTestServer`、`-PwithJei=false` でも | どちらも257件すべて成功 |
| 新規 `aMixerQuenchesMoltenPolymerIntoPelletsAndWarmWater` | 実際の作業鉢・メカニカルミキサー・歯車・クリエイティブモーターで、480 Kの溶融ポリエチレン250 mBと水100 mBから、ポリエチレン2個と温排水100 mBができ、原料が残らない |
| 新規 `stirredReactorMakesAmmoniumNitrateWithoutHeat` | 上記のとおり |
| CIと同じ4つの検証スクリプト | 成功 |
| 実画面のPonder（日本語） | 全79シーンの品質チェックに合格し、差し替えた手順を撮影 |

## 未検証・制約・残っている問題

- ミキサーは流体の温度を見ないため、冷却していない溶融ポリマーでもそのまま急冷できる。水で急冷する工程なので、冷却器を通す必要はないものとした。
- `PETROCHEMISTRY.md` の「従来式設備」の節（原油抽出機・接触分解装置・重合反応器の表など）は、登録されていない装置を説明している。文書全体の見直しが必要なので、今回は直していない。
- 過去の報告（`docs/reports/uranium-fuel-line.md` など）の数値は当時の記録として残した。
