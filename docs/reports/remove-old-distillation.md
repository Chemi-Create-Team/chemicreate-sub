# 旧蒸留設備の削除（ChatGPT引き継ぎ用）

角型の分留塔（`fractionating_column`と駆動部`fractionating_column_controller`）を残し、それ以外の蒸留設備を削除した。

## 削除したもの

- 旧蒸留塔`distillation_tower`（4段を積む石油工程の1段目）。石油工程の段（`PetrochemicalStage.DISTILLATION`）、ブロック、ブロックエンティティ、アイテム、レシピ、JEI表示、Ponder`chemistry/petro_distillation`を削除。
- プロセスモジュールの蒸留コントローラー`distillation_controller`とカラムトレイ`column_tray`、レシピ`distill_crude`、塔の構造判定。
- それらのモデル・テクスチャ・翻訳・ルートテーブル・レシピ・実績・Ponder項目（`fraction_filters`、`module_refinery`）と、専用のゲームテスト。

## 残したもの

分留塔の系統（`CEDistillation`、`FractionatingColumn*`、`FractionatingDrive*`、関連の資産・テスト）、石油の流体、抽出・分解・重合の設備、Create Diesel Generators側の蒸留への干渉。

## 影響

- 原油から留分を作る工程は、分留塔だけになる。旧蒸留塔・蒸留コントローラー・カラムトレイを使ったワールドの設備は消える。
- `tools/`の一部の生成スクリプト（`generate_process_assets.py`、`generate_petrochemical_models.py`、検証スクリプトなど）に旧ブロック名の記述が残っている。再実行すると旧資産が復活するため、そのままでは使わないこと。
- GameTest：全体301件が成功。
