# 他MODからの分留・物性の登録口

対象：Minecraft 1.21.1 / NeoForge 21.1.219 / Create 6.0.10。Create: Frontier Science（CFS）がトリクロロシランの精留を分留塔へ追加できるよう、登録口を3つ追加した。既存の工程・数量・時間・保存形式は変えていない。

## 実装内容

| 追加 | 内容 |
| --- | --- |
| `DistillationProfile.register(Fraction)` | 原料1種と最大5留分の分留を追加する。共通セットアップ中、最初の分留塔が動く前に呼ぶ。以後の登録は例外にする。留分は従来どおり沸点の低い順に上の出口へ割り当てる |
| `DistillationProfile.registered()` | 登録済みの追加分留。JEIの分留塔カテゴリへも表示する |
| `FluidPhase.register(Fluid, Properties)` | 他MODの流体の沸点・潜熱・臨界点・液密度。未登録の流体は従来どおり汎用値 |
| `FluidHazards.register(ResourceLocation, Properties)` | 他MODの流体の腐食性・気体区分。未登録の流体は従来どおり通常扱い |

当初は化学分留6種をデータパックレシピへ移す計画だった。分留の定義は`ProcessRecipe`にあり、触媒反応槽・遠心分離・JEI・複数のGameTestが同じ表と温度別名（`ThermalStreams`）を共有しているため、置き換えは範囲が大きい。計画に記した代替案どおり、外部から登録する最小限の入口に留めた。

主要ファイル：`chemistry/DistillationProfile.java`、`thermal/FluidPhase.java`、`transport/FluidHazards.java`、`compat/jei/CreatePlantJeiPlugin.java`。

## 確認

- `bash gradlew compileJava`：成功。
- CFSのGameTest `fractionatingColumnPurifiesTrichlorosilane`：登録した分留で、粗トリクロロシラン2,000 mBからトリクロロシラン1,600 mB（14段）と四塩化ケイ素400 mB（10段）が出ること、腐食性の登録が効くことを確認。
- `bash gradlew runGameTestServer`（2026-11-11、`main`の`5bf515c`へ載せ直した後）：612件中4件失敗。この変更を含まない`main`でも同じ4件が失敗するため、今回の変更によるものではない（`dioriteCasingUsesCreateHandApplicationAndBothCasingsFitTheMachines`、`physicalDeskControlsOneRodAndAZ5OverridesEveryRod`、`aFouledProcessStopsThenResumesAfterOneManualBrush`、`everyRegisteredEntryHasAssetsTranslationsAndLoot`）。原因は調べていない。
- 載せ直しでは、JEIの分留カテゴリの登録行が`main`の変更（硝酸吸収の分類追加）と重なったため、両方を残す形で解決した。分留塔が2×2専用になった後も、CFSの精留は2×2・14段の塔で同じ数量になることをCFSのGameTestで確認した。

## 未確認・制約

- JEIの画面で追加分留が表示されることは、クライアントを起動して確認していない。
- 追加分留の名前は翻訳キーを持たない（ゴーグルは留分の流体名を表示するため支障はない）。
- 分留レシピのデータパック化は未実施。必要になった時点で別作業とする。
