# 横型タンクをボイラーにし、横置き管束ボイラーをまとめた完了レポート

- 日付：2026-11-18
- 実装コミット：このレポートと同じコミット（`git log -1 -- docs/reports/horizontal-tank-boiler.md`で確認）
- 説明：[横型タンクのボイラー](../horizontal-tank-boiler.md)

## 指示

横型タンクの下にブレイズバーナーを置いたらボイラーになるようにする（ユーザーの選択：横型タンクで蒸気を作り、別ブロックの横置き管束ボイラーは廃止して横型タンクへ置き換える。ID対応を残す）。

## 実装

| 部分 | 内容 |
| --- | --- |
| 蒸気の生成 | `block/entity/HorizontalTankBlockEntity.java`に、管束ボイラーの処理をそのまま移した。下段の各列の正の向きの端の区間が、その列の下のバーナーの熱（Create加熱段階1あたり4,000 J/t、丸め残り最大400 kJ）で水か温排水を沸かし、自分の蒸気バッファ（16,000 mB）へためる。加熱で高圧蒸気、超加熱で過熱蒸気。管束ボイラーの「長さ3区間以上」の条件はなくし、どの長さでも沸かす |
| 搬出口 | 沸騰している端の区間の上面と外向き端面は蒸気だけを出す（入れられない）。沸騰していないときは普通のタンクの面。ほかの面はタンクの面のまま |
| Createのボイラーとの関係 | タンクに蒸気エンジンが付いているときは、従来どおりCreateのボイラー（蒸気エンジン用）として働き、配管へ出す蒸気は作らない（状態`engines`）。同じバーナーで二重に動力を作らないため |
| 表示 | ゴーグル：タンクの表示に、沸騰中の端の区間の状態・蒸気・伝熱量を追加。Display Linkの状態・伝熱量・蒸気量も横型タンクから読む。稼働音も横型タンクに移した。端の区間を壊すと、残った蒸気が出る |
| 廃止 | `tubular_boiler`のブロック・アイテム・BlockEntity型・レシピ（横型タンク＋ステンレス鋼管8本）・ルートテーブル・ブロック状態・アイテムモデル・つるはしタグ・ブロック名の翻訳・使わなくなった状態の翻訳（`evaporator`・`evaporation_heat`・`upper`・`warming`）。クリエイティブタブから削除 |
| 既存ワールド | `registry/CELegacyIds.java`に、統合した登録の対応付け（`tubular_boiler` → `horizontal_fluid_tank`、ブロック・アイテム・BlockEntity型、旧mod IDのものも）を追加。ブロック状態のプロパティは同じなので向きと形もそのまま。保存キー（`TubeSteam`など）も同じなので、中の水と蒸気も保たれる |
| JEI | 「蒸気の生成」の装置と注記を横型タンクに（「横型タンク：加熱／超加熱」） |
| Ponder | 「横型タンクで蒸気を作る」（断面1×1・2×2・3×3）。構造を横型タンクで作り直し、字幕を新しい仕様（長さ自由、上面か外側の端から蒸気、蒸気エンジンを付けるとCreateのボイラー）に。タービンの手順の字幕と説明書の「回転力を増やす」も更新 |
| 文書 | `docs/tubular-boiler.md` → `docs/horizontal-tank-boiler.md`（書き直し）、`docs/thermal-grades.md`、`README.md` |

### 主要ファイル

- `block/entity/HorizontalTankBlockEntity.java`、`block/HorizontalFluidTankBlock.java`、`registry/CELegacyIds.java`、`registry/CEIndustrialExpansion.java`
- `compat/display/MachineReadings.java`、`compat/jei/CreatePlantJeiPlugin.java`、`client/MachineSoundSource.java`、`client/SteamAcidPlaytestWorld.java`
- 生成元：`tools/generate_industrial_expansion.py`、`tools/generate_grades.py`、`tools/integrate_industrial_expansion.py`、`tools/generate_manual.py`、`tools/data/ponder_lessons.json`、`tools/validate_visual_identity.py`、`tools/test_ci_validators.py`
- テスト：`HorizontalTankBoilerFlowGameTests`・`HorizontalTankBoilerSizeGameTests`（旧`TubularBoiler…`を移植）、`IndustrialExpansionGameTests`、`LegacyNamespaceGameTests`、`TankGradeGameTests`、`SurvivalReachabilityGameTests`

## 確認

- `compileJava`成功。`validate_ponder_language.py`・`validate_plant_tutorials.py`・`validate_translations.py`合格。
- GameTest：`IndustrialExpansionGameTests`・`HorizontalTankBoilerFlowGameTests`・`HorizontalTankBoilerSizeGameTests`（断面2×2・3×3、X・Z）・`HorizontalTankGameTests`・`TankGradeGameTests`・`SurvivalReachabilityGameTests`・`LegacyNamespaceGameTests`・`PlantTutorialGameTests`・`RecipeVisibilityGameTests`・`DisplayIntegrationGameTests`・`PowerPlantGameTests`・`PressureGameTests`・`CraftingRecipeGameTests`：以前からの失敗以外合格。テストで確かめた主な点は、2区間でも沸く、Z方向は南端で沸く、沸騰中の搬出口は水を受けない、側面からは水だけが出る、蒸気エンジンを付けると配管の蒸気を作らない、保存・再読込で蒸気が保たれる、分割しても蒸気は失われない、水と蒸気の量の合計が保たれる、本物のCreateのポンプで蒸気を受け側のタンクへ送れる、`tubular_boiler`のIDが横型タンクとして読み込まれる。
- 以前からの失敗（変更前のmainでも同じく失敗することを別の作業ツリーで確認したもの、または以前から報告済みのもの）：`cooledInputsAndAllManufacturingRecipesRemainValid`、`everyCurrentItemHasExactlyOneAppropriateTab`（六フッ化ウランのバケツ）、`aPumpLineOnTwoCasingFacesLosesNoSteam`（`TurbineThermalGameTests`）。
- 実画面（日本語）：`docs/quality/horizontal-tank-boiler/ponder-2x2.png`（Ponder「断面2×2の横型タンクのボイラー」）。

## 制約と未確認事項

- 既存ワールドの管束ボイラーが横型タンクになることは、登録の解決をテストで確かめただけで、実際の古いワールドでは確かめていない。
- 管束ボイラーを作るのに使ったステンレス鋼管8本は戻らない（アイテムは横型タンク1個になる）。
- 横型タンクを水の貯蔵に使っていて、下にバーナーがあると、沸騰して上面と外側の端から蒸気が出るようになった。貯蔵だけに使うならバーナーを置かない。
- 翻訳の統合スクリプト（`integrate_industrial_expansion.py`）を実行すると、ほかの機能の古い翻訳（コールドボックス・リチウムターゲット・ヘリウム回収・出荷票の字幕など）と、`tutorials.json`の古い項目を中間ファイルから書き戻してしまう。今回はタグ以外の出力を使わず、翻訳と`tutorials.json`は必要な項目だけを直した。統合スクリプトと中間ファイルの古さは範囲外のため未対応。
- 翻訳キーの接頭辞は`tubular_boiler.`のまま（表示文は新しい仕様に合わせた）。Ponderのシーン名も`tubular_boiler`のまま。
