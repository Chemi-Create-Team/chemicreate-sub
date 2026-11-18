# 旧ブロック15種の削除 引き継ぎ

## 削除したブロック
高圧反応槽`pressure_vessel`、高圧反応槽の蓋`pressure_vessel_lid`、硝酸吸収塔`nitric_acid_tower`、気体タンク`gas_tank`、グレーのDresser-Rand往復式空気圧縮機`gray_dresser_rand_reciprocating_air_compressor`、ガス酸化装置`gas_oxidizer`、アンモニア酸化反応器`ammonia_oxidizer`、小型加熱器`process_heater`、反応容器`reaction_vessel`、流体分離器`fraction_separator`、触媒ユニット`catalyst_unit`、工程チャンバー`process_chamber`、原油抽出機`oil_extractor`、接触分解装置`catalytic_cracker`、重合反応器`polymerization_reactor`。

## 一緒に削除したもの
- ブロック・ブロックエンティティ・アイテム・登録・クリエイティブタブ、描画（`CompressorRenderer`・`ProcessModuleRenderer`）、ゴーグル表示（`CreateGoggleReadout`など）、JEIのカテゴリ（基本の化学反応・石油化学・工程モジュール）。
- ブロック状態・モデル・テクスチャ・ルートテーブル・レシピ・進捗・ツルハシタグ・翻訳（旧キーを含む）。
- Ponder：化学4場面（ostwald_line、petro_extraction、petro_products、production_line）と、削除したブロックを構造に含んでいた植物ガイド19場面（NBT・`tutorials.json`・翻訳）。
- 開発用スクリプト：生成6本・検証7本。
- ゲームテスト：削除した装置が主題の5ファイルと、一部のテスト。

## 影響したもの
- 大型ベイシン：圧力容器の蓋で囲む4ベイシンの大型反応器モードをなくした。2〜5個の直列（電解槽）だけが残る。
- `ProcessModule`・`ProcessRecipe`・`ReactorModule`は、触媒反応槽（`reactor_vessel`など）が使うので残した。
- ライフサイクル：圧縮機のクランクケースが使う潤滑油カートリッジは、`LubricantCartridgeItem`をなくして通常のアイテムにした。

## 確認
- `./gradlew runGameTestServer`：261件成功。
- 実クライアント（`createplant.clientQualityCheck`）：Ponder91場面の題・翻訳を検査して成功。
- 未確認：旧ワールドの読込（削除したブロックは欠けたものとして扱われる）。

## 未整理（要判断）
- 大型ベイシンの長方形クラスターの描画（`drawRectangularCluster`など）は使われなくなったが残している。
- `generate_petrochemical_ponder.py`は旧場面の生成関数を含むが、`Structure`クラスを他のスクリプトが使うため残した。`generate_process_tutorials.py`も同様。
- 削除した装置専用だった触媒アイテム（分解・重合・酸化）、液体、バケツ、`BedGas`など、使われなくなった可能性があるものは残している。
- 古い報告書（`docs/reports/`、`docs/*.md`）の記述は当時の記録として変更していない。
- 実装コミットID：コミットメッセージを参照。
