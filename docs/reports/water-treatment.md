# 排水処理プラント 実装引継ぎ

## 実装内容

- 新装置を増やさず、既存STIRRED槽3段で洗浄排水→中和処理水→澄明水→vanilla水と汚泥を処理。
- `CEWaterTreatment` に液体2種・粉2種・ろ材1種。中間流体は水と同じ密度1 kg/L、比熱4180 J/(kg·K)。
- 3処理は各1000 mBを保持、各1個の資材を使い最終汚泥3個。最後の液体は `minecraft:water`。
- `conserve_heat` は既存PressureReactionの省略可能なフラグ。消費流体の熱量から生成流体温度を求め、既存のsnapshot確定に含める。既定falseで既存レシピを維持。
- 新規素材・バケツ・レシピ・Ponder構造は専用generator。既存画像は変更しない。粉は共通の火薬輪郭、ろ材は折り畳んだろ過パッド形状。
- Ponderは実3槽・Createシャフト・出口直付スマート液体パイプ・ポンプ・ファンネルを示す。製品フィルターが未処理原料の搬出を防ぐ。
- 共通world-liquid棚卸しへ `liquid(name, color, density)` 形式を追加し、別担当の核融合2液体も検出。

## 主要ファイル

- `src/main/java/net/createplant/registry/CEWaterTreatment.java`
- `src/main/java/net/createplant/recipe/PressureReactionRecipe.java`
- `src/main/java/net/createplant/block/entity/ReactorMachineBlockEntity.java`
- `src/main/java/net/createplant/ponder/WaterTreatmentPonderScenes.java`
- `src/main/java/net/createplant/gametest/WaterTreatmentGameTests.java`
- `tools/generate_water_treatment.py` / `tools/data/water_treatment_*.json`
- [利用方法・資料・近似の説明](../water-treatment.md)

## 確認

作成したGameTestは3件です。

1. 全3段のsave/reloadを挟み、液量1000 mB・質量1 kg・熱量を保存して通常の水へ戻る。
2. 水・汚泥の出口が満杯のときの無消費と、空間を作った後の一度だけの再開。
3. 実Createモーター・ポンプ・スマート液体パイプで3槽を接続し、通常水1000 mBと汚泥3個を回収。

実装コミットID：`89ac56166617e5db8ed510489f7da25df4ab3723`。ビルド・GameTest・実クライアントの確定結果は下記と統合レポートに記録しています。

静的検証: 新規PythonのAST・レシピ/fragmentのJSON・`git diff --check` が成功。新しい粉2種はバニラ火薬と同じ不透明88画素・7階調で、生成PNGのバイト一致を確認。新しい2バケツは液体部分以外が既存のバニラ形状テンプレートと同一。中央統合後の `python3 tools/generate_powder_items.py --check` は登録済み粉32種・粉以外76種の全件棚卸しに成功しました。world-liquid棚卸しは水処理2種と核融合担当の2種を検出しました。

## 制約と注意

処理資材のアイテム質量・pH・反応熱・実際の汚染物はゲーム近似です。核燃料廃液や飲用安全性を扱う工程ではありません。工場側で60 ℃以下へ冷却し、全段に生成物流体フィルターを付け、汚泥を搬出してください。新規登録・creative tab・翻訳・Ponder登録は中央統合fragmentを使用します。

実装コミットID: 中央担当の検証・コミット後に追記。

## 統合検証の確定結果

2026-11-09の中央検証で、追加した本機能のGameTestを含む25件すべてが合格しました。全体は505件中504件合格で、失敗1件は作業前からある液体ブロック名44種類の英日翻訳不足です。新しい欠落キーはありません。ビルド・物理テスト・CI検証は合格し、実クライアントでは129シーンと新5機能のPonder描画を確認しました。詳細と実装コミットIDは[統合レポート](industrial-expansion.md)を参照してください。
