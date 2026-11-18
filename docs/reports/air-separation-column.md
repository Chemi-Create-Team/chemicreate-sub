# 空気分離塔 引き継ぎ

## 実装内容
- 角型蒸留塔の制御部（`fractionating_column_controller`、ブロック・BlockEntity・レンダラー・モデル・レシピ・翻訳・タグ）を削除し、動力なしで動作するようにした。
- 塔はちょうど14段で成立する（`DistillationProfile.HEIGHT`、`getMaxLength`も14）。幅1〜3は従来どおり。
- 用途を原油分留から空気分離へ変更した。原油用の収量表・熱計算・循環駆動は削除。原油はCDGの蒸留塔で処理する。
- 新しい流体：`liquid_air`、`crude_liquid_oxygen`（液体）、`argon_gas`。バケツ・ブロック・テクスチャ・翻訳（en/ja/zh）・FluidHazards・FluidPhaseを追加。

## 構造と数値
- 東面：3段目に液体空気、8段目に粗液化酸素の搬入口。北面：1段目に粗液化酸素、5段目に窒素、6段目に酸素、9段目にアルゴン、12段目に窒素の搬出口。段は下から1段目。
- 下塔：液体空気1,000 → 粗液化酸素600＋窒素400。上塔：粗液化酸素600 → 酸素210＋アルゴン10＋窒素380。合計は空気1,000あたり窒素780・酸素210・アルゴン10。
- 1サイクル100 tick、5 tickごとに判定。バッチ数は`min(断面積, 入力/バッチ量, 各出力の空き/生成量)`で、開始時に確定。
- 内容物は各セルの`held[7]`。出口は`Port`で、流体の種類だけを確認して受け入れ、シミュレーションでは変更しない。
- ブロック状態に`inlet`（0〜2）を追加し、東口のモデルと当たり判定を切り替える。

## 主要ファイル
`chemistry/DistillationProfile.java`、`block/entity/FractionatingColumnBlockEntity.java`、`block/FractionatingColumnBlock.java`、`block/FractionatingColumnShapes.java`、`registry/CEDistillation.java`、`registry/CEFluids.java`、`registry/CEItems.java`、`gametest/DistillationGameTests.java`、`tools/generate_distillation_assets.py`、`tools/generate_distillation_tutorials.py`。

## 確認
- `./gradlew runGameTestServer --offline`：256件成功（形成条件、ポート位置、組成の保存、並列バッチ、満杯・レッドストーン、再読込）。
- 実クライアントでPonder（1×1）を描画し、14段の塔・搬入口・搬出口・配管の表示を確認。
- 未確認：ワールドに実際に組んだ塔での配管接続と搬入出、幅2・3の見た目、ゴーグル表示、専用サーバー起動。

## 制約・次回の注意
- 液体空気の製造工程がない。入手はクリエイティブタブのバケツのみ。
- 旧ワールドの塔の内容物（保存形式`Hold0..7`）は読み込まれず空になる。
- 原油分留の用途は塔から無くなった。`tools/revise_equipment_ponder.py`は旧版の一回限りの移行スクリプトで、旧文言を含む。
- `docs/reports/remove-old-distillation.md`と`implemented-features-inventory.md`は旧仕様の記録のまま。
- 実装コミットID：コミットメッセージを参照。
