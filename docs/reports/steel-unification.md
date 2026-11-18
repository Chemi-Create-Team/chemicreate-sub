# 鋼材統一 実装レポート

## 実装コミット

- 実装コミット：`46c840dc1b52375d2412294adfe2ef0ddfb73739`

## 実装内容

- `create_heavy_indusstry:industrial_steel_stock` の登録、クリエイティブタブ項目、レシピ、モデル、テクスチャ、英・日・中の翻訳を削除した。本格運用前という指示に従い、既存ワールド向けの互換登録は追加していない。
- パドル鉄1個をメカニカルプレスで5回加工し、`create:industrial_iron_block` 1個を得る組立連続工程へ置き換えた。
- Create 6.0.10のJARを調べ、鉄から鋼材ブロックまたは錆びた鋼材ブロックを直接作るレシピが、指定された石切台レシピ2件だけであることを確認した。この2件をNeoForgeの偽条件で無効にし、鋼材ブロックの水洗い、窓、板窓のレシピは残した。
- 設備14種の材料を、個数と配置を変えずにCreateの鋼材ブロックへ置き換えた。ガスホルダー球と使用済み燃料プールの実装は変更していない。
- 新しいPonder「鋼材ブロックを作る」を追加した。加熱した作業鉢でのパドル鉄製造、蛍石による増量、ベルト搬送、5回のプレス、完成品を英・日・中で説明する。鋼材ブロックとパドル鉄の両方を関連部品へ登録した。
- `generate_crafting_recipes.py`、`generate_thermal_control_assets.py`、`generate_uranium_assets.py` を更新し、対象レシピと旧アセットの削除を再生成できるようにした。後者2本には、今回の対象だけを短時間で再生成する `--steel-only` を追加した。
- `docs/metals.md`、`docs/gas-holder.md`、`DESIGN.md`、化学・石油化学の開発文書、関係する過去レポートを現行仕様へ合わせた。直径4・8・16のガスホルダー球には、それぞれ24・144・632個のパドル鉄と、120・720・3,160回のプレスが必要になる。
- `CraftingRecipeGameTests` に、鋼材ブロックの完成レシピがこの組立連続工程1件だけであることと、旧アイテムが登録・入力・出力に残らないことの回帰試験を追加した。

## 確認結果

- `python3 tools/generate_crafting_recipes.py`、各 `--steel-only` 生成、`generate_steel_ponder.py`、`generate_ponder_language.py`：成功。再実行して同じ成果物になることを確認した。
- 変更した4本の生成スクリプトの `py_compile`：成功。
- `python3 tools/validate_plant_tutorials.py`：成功（追加Ponder 85件、字幕410組、部品リンク124件を検証）。
- `python3 tools/validate_ponder_language.py`：成功（実行時見出し90件、英・日全文、中国語11シーンを検証）。
- `python3 tools/validate_visual_identity.py`：成功（完成品232件、意図しない同一テクスチャなし）。
- `python3 tools/test_ci_validators.py`：11件成功。
- Java 21で `bash gradlew --no-daemon build`：成功。流体相25件、Haber-Bosch 1,019件、原子炉物理1,670件を含むGradle側試験が成功した。
- `CraftingRecipeGameTests` と `PlantTutorialGameTests` に絞ったGameTest：8件すべて成功、1.185秒。
- 実クライアント：NVIDIA RTX 4060のハードウェア描画で起動し、Createの鋼材ブロックから新Ponderを開いた。日本語の題名・字幕、加熱バーナー、作業鉢、ミキサー、ベルト、メカニカルプレスが描画されることを確認した。その後、成果物画像の確認でベルトの向きと動力配置の不備が判明し、[鋼材製造Ponder表示修正](steelmaking-ponder-display-fix.md)で修正した。
- JEI：鋼材ブロックの作り方は「組立ライン」1件だけで、5回プレスとパドル鉄からの変換を表示した。石切台カテゴリは表示されなかった。

## 未検証・残っている問題

- 全GameTest 333件も実行したが、約200件まで失敗表示なしで進んだ後、既存の `ExchangerAssemblyGameTests.shippedPonderAssembliesReallyFormWithoutManualValidation` がチャンク待ちを繰り返した。15分経過後に長時間試験の短縮ルールを適用して終了したため、全333件の完走は確認していない。
- 起動時に既存の `pressure_reaction/stainless_steel_ingot_aod` が、対応上限9個に対してアイテム入力10個を持つため読み込みを拒否される。今回の基準コミットに含まれていた別作業の問題であり、鋼材統一の対象外なので変更していない。
- 旧 `industrial_steel_stock` の登録削除に対する既存ワールド移行は実装していない。これは指示どおりだが、旧アイテムを保存した本格運用前のワールドでは消失する可能性がある。
- JEIなし構成、多数の追加MODを含む構成、長時間稼働時の性能は今回確認していない。

## 次回の注意

- 全GameTestを再実行するときは、既存の大型Ponder組立試験のチャンク待ちを先に調査する。
- AODステンレス鋼レシピの入力上限超過は、別作業としてレシピ設計を決めてから直す。
- 鋼材レシピや設備材料を再生成するときは、今回更新した生成スクリプトを使い、Createの鉄直結石切台レシピを復活させない。
