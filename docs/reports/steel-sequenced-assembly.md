# ChatGPT引き継ぎ：パドル鉄から鋼への連続工程

後の鋼材統一作業で、ここで追加した工業用鋼材は削除し、同じ5回プレス工程の完成品をCreateの鋼材ブロックへ統合した。この文書の以下の記録は実装当時の仕様である。

## 実装内容
鋼材（`industrial_steel_stock`）は、次の順でのみ作れる。
1. 加熱したブレイズバーナー上のベイスンで、メカニカルミキサーが鉄インゴット4＋鉄鉱石（または粗鉄）2を混ぜ、パドル鉄4個にする（既存レシピ、変更なし）。
   - フラックスを使う追加経路では、同じ材料に蛍石1個を加えるとパドル鉄5個になる。鉄鉱石版と粗鉄版の両方があり、従来の4個レシピも残る。
2. パドル鉄をメカニカルプレスでちょうど5回プレスする。Createの組立連続工程（精密機構と同じ仕組み）で、途中品は`incomplete_puddled_iron`。失敗結果はなく、5回で必ず鋼材1個になる。

旧来の近道（パドル鉄の1回プレス、鉄インゴット2＋石炭の混合）は削除した。

## 主要ファイル
- `data/.../recipe/sequenced_assembly/industrial_steel_stock.json`：`loops: 5`、プレス1段、結果は鋼材のみ。
- `registry/CEPuddling.java`：`SequencedAssemblyItem`として`incomplete_puddled_iron`を登録。モデルはパドル鉄のテクスチャを共用、3言語の名称を追加。
- 削除：`recipe/pressing/industrial_steel_stock_from_puddled_iron.json`、`recipe/industrial_steel_stock.json`。

## テスト結果と未検証
`./gradlew compileJava`のみ成功。ゲーム内でのプレス5回、デポ・ベルト上の動作、JEI表示は未確認。専用の自動テストは追加していない。

## 制約・次回の注意
- Createの組立連続工程はミキサー工程を含められないため、混合は従来のベイスンレシピ、プレスのみ連続工程とした。
- 途中品に専用テクスチャはなく、パドル鉄と同じ絵。
