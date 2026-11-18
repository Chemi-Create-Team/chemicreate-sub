# 指示書：工業用鋼材をCreateの鋼材ブロックに統合する

この文書は、Codexへの作業依頼です。作業の前に `AGENTS.md` と `DESIGN.md` を読み、その規約に従ってください。

## 背景と決定事項

- 鋼にあたるものが2つあります。どちらも日本語では「鋼材」と呼ばれ、紛らわしくなっています。
  - このMODの「工業用鋼材」（`create_refinery_n_reactors:industrial_steel_stock`）：パドル鉄を5回プレスして作るアイテム。設備14種のレシピの材料。
  - Createの「鋼材ブロック」（`create:industrial_iron_block`、英語名 Industrial Iron Block）：石切台で、鉄インゴット1から2個できる建材。ガスホルダー球の外殻と、使用済み燃料プールのレシピに使う。
- ユーザーの決定：
  1. 工業用鋼材を削除し、Createの鋼材ブロックに統合する。
  2. Createの鋼材ブロックは、工業用鋼材の作り方（パドル鉄を5回プレス）だけで作れるようにする。Createの石切台レシピは無効にする。これは「他MODのレシピを置き換えない」という約束の例外として、ユーザーが認めた。
  3. 量は、パドル鉄1 → 鋼材ブロック1（今の工業用鋼材と同じ）。

## 全体の約束

- 作業ブランチは `main` です。プッシュの前に `git fetch origin main` → `git rebase origin/main` を行い、強制プッシュはしないでください。
- Createのコードは変えないでください（Mixinも使わない）。レシピの無効化は、データだけで行います。
- 本格運用の前なので、工業用鋼材の登録を残す互換処理は作りません。登録・レシピ・アセット・翻訳・参照を、すべて同じ作業で削除してください。
- レシピとアセットは、関係する `tools/generate_*.py` を直し、流し直して作ってください。生成された JSON だけを手で直すと、次に生成したときに元へ戻ります。

## 1. 工業用鋼材を削除する

- `registry/CEHeating.java` の `STEEL`（`industrial_steel_stock`）と、`CECreativeModeTabs.java` での登録を削除します。
- 削除するファイル：
  - `assets/.../models/item/industrial_steel_stock.json`
  - `assets/.../textures/item/industrial_steel_stock.png`
  - 翻訳キー `item.create_refinery_n_reactors.industrial_steel_stock`（英・日・中）
- 次の生成スクリプトにある `industrial_steel_stock` を `create:industrial_iron_block` に置き換え、流し直します。
  - `tools/generate_crafting_recipes.py`
  - `tools/generate_thermal_control_assets.py`
  - `tools/generate_uranium_assets.py`
  - そのほか、`grep -rn industrial_steel_stock` で見つかるもの
- 材料を置き換えるレシピ（14種、数はそのまま）：
  - 熱交換器の外装・蓋・支え・管束
  - 高圧タービンの外装
  - 工業用加熱器、加熱器の屋根
  - 大型フライホイール
  - 撹拌反応槽、撹拌反応槽の芯、触媒反応槽、触媒反応槽の芯
  - 往復式コンプレッサー
  - タービン軸
- 石切台のレシピ（熱交換器の部品、加熱器の屋根）は、材料を鋼材ブロックにしても成り立ちます。ただし、Createの鋼材ブロック自身の石切台レシピ（下の2）と、同じ材料で別の結果になっても困らないか、確かめてください。

## 2. Createの鋼材ブロックの作り方をパドル法だけにする

- 新しいレシピ `recipe/sequenced_assembly/industrial_iron_block.json`：
  - 今の `sequenced_assembly/industrial_steel_stock.json` と同じ（パドル鉄、プレス1段、5回）です。結果を `create:industrial_iron_block` 1個にします。
  - 古いファイルは削除します。
- Createのレシピを無効にします。このMODの `data/create/recipe/` に同じ名前のファイルを置き、中身を `{"neoforge:conditions": [{"type": "neoforge:false"}]}` にします。
  - `industrial_iron_block_from_ingots_iron_stonecutting.json`（鉄インゴット1 → 鋼材ブロック2）
  - `weathered_iron_block_from_ingots_iron_stonecutting.json`（鉄インゴット1 → 錆びた鋼材ブロック2）
- 錆びた鋼材ブロックは、Createの「鋼材ブロックを水で洗う」（`splashing/industrial_iron_block.json`）だけで作れるようになります。これは残します。
- 窓・板窓のレシピ（鋼材ブロック＋ガラス）は、そのまま残します。
- 作業の前に、Create 6.0.10 のJAR（`data/create/recipe/`）を一覧し、鋼材ブロックや錆びた鋼材ブロックを、鉄から直接作るレシピがほかにないか確かめてください。見つかったものも、同じやり方で無効にします。

## 3. 約束の例外を文書に書く

- `DEVELOPMENT_CHEMISTRY.md`（12行目）と `PETROCHEMISTRY.md`（142行目）の「バニラや他MODのレシピを置き換えません」に、例外を1つ書き足します。
  - 書くこと：Createの鋼材ブロック・錆びた鋼材ブロックを鉄から直接作る石切台レシピだけは、鋼の工程に統一するために無効にしていること。

## 4. ガスホルダー球と燃料プール

- ガスホルダー球の外殻は、今も Createの鋼材ブロックです。コードを変える必要はありません。
  - 鋼材ブロックが高くなったので、`docs/gas-holder.md` に材料の量（直径4で24個、8で144個、16で632個のパドル鉄と、そのプレス）を書いてください。
- 使用済み燃料プールのレシピ（鋼材ブロック3）はそのままです（原子炉担当の範囲なので、変えないでください）。

## 5. Ponder と文書

- 新しい Ponder「鋼材ブロックを作る」を作ります。
  - 手順：加熱した鉢とミキサーでパドル鉄を作る（蛍石を入れると1つ増える） → ベルト上のメカニカルプレスで5回プレスする → 鋼材ブロックになる。
  - 鋼材ブロック・パドル鉄から開けるようにしてください。Createの鋼材ブロックから開く Ponder を足してよいかは、`ponder/` の既存の登録方法に合わせて確かめてください。
- 文書：
  - `docs/metals.md` に「鋼」の節を足し、パドル法 → 鍛造 → 鋼材ブロックと、使い道（設備14種・ガスホルダー球・燃料プール）を書きます。
  - `DESIGN.md` と `docs/reports/steel-sequenced-assembly.md` の工業用鋼材の記述を直します（報告書は「後に統合した」と1行足すだけでよい）。
- JEI：Createの鋼材ブロックの作り方に、新しい組立連続工程だけが出て、石切台のレシピが出ないことを確かめてください。

## 確認と完了

- `bash gradlew build runGameTestServer` が全件合格すること。
- `CraftingRecipeGameTests` が工業用鋼材を参照していれば直し、次の2つを確かめるテストを足してください。
  - 鋼材ブロックを作るレシピが組立連続工程だけであること（石切台のレシピがないこと）。
  - `industrial_steel_stock` が、どのレシピにも残っていないこと。
- `tools/validate_plant_tutorials.py`、`validate_ponder_language.py`、`validate_visual_identity.py`、`test_ci_validators.py` が合格すること。
- 新しい Ponder は、実クライアントで撮影して確かめること。
- `docs/reports/steel-unification.md` に、日本語のレポートを書いてください。
  - 書くこと：実装内容、テスト結果、未検証のこと。
  - 実装コミットの欄は、まず `COMMIT` と書いてコミットします。そのあとハッシュに置き換え、もう一度コミットします。
  - リベースでハッシュが変わったら、プッシュの前に書き直してください。
