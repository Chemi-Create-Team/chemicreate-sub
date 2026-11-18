# 存在しない材料を指すレシピと案内の修正

実装コミット: `1ee7d9f9eec941358d8d80236b0a13ecc35e8087`

「レシピに存在しないアイテムが割り当てられ、進行できない」という報告を受け、自Modの全レシピ・タグ・実績・説明書・Ponderの案内を確認した。

## 調査方法

- 静的確認：`data/`以下の全JSONから自Mod・Create・CDG・バニラ・`c:`タグへの参照を集め、登録済みのアイテム・流体と照合した。自Mod名前空間の登録漏れはなかった。
- 実行時確認：GameTest `everyRecipeInputOfTheModCanBeSupplied` を追加し、自Modの全レシピの材料（アイテム、Create加工レシピの流体、転炉のガス）が1つ以上の実在物に解決されるかを調べた。中身のないタグは読込エラーにならず、JEIでバリアとして表示されるだけなので、この試験で検出する。
- 案内：`ponder/tutorials.json` の全工程が指すレシピIDの実在と、他Modのレシピを指す工程の説明文を確認した。手引書（`book...manual.page_*`）の数値・工程を実レシピ・`ElectrolysisProcess`と照合した。

## 見つかった問題と修正

| 対象 | 問題 | 修正 |
| --- | --- | --- |
| `cubic_press`、`gas_centrifuge`、`pressure_tank` | 材料`c:plates/steel`を満たすアイテムが自Mod・依存Modのどれにもなく、作成不能 | `c:plates/stainless_steel`（ステンレス鋼板）へ変更。仕様の「鋼板」に最も近く、序盤では作れない条件を保つ。圧力タンクと並ぶタービン部品もステンレス鋼板を使う |
| Ponder「鉄原料から鋼鉄とステンレス鋼を作る」手順2 | `create:milling/charcoal`（石臼で木炭→黒の染料）を示しながら「石臼でも炭素粉を作れる」と説明 | 自Modの`crushing/carbon_powder_from_charcoal`を示し、粉砕ホイールを使うこと、石臼では染料になることを説明 |
| Ponder「蛍石からフッ素を作る」手順1 | `create:milling/calcite`（石臼で方解石→骨粉のみ）を示しながら「石臼で蛍石を集める」と説明 | 自Modの`crushing/calcite_to_fluorite`を示し、粉砕ホイールで蛍石50％と骨粉、石臼では骨粉だけと説明 |
| 手引書 2章（英語） | 「Mill coal or charcoal」。炭素粉は粉砕レシピだけで、石臼では作れない | 「Crush coal or charcoal」。日本語・中国語は元から「粉砕」で正しい |
| レシピ実績`lubricant_cartridge`、`empty_lubricant_cartridge` | 撤去済みの潤滑カートリッジと、存在しないレシピを指す | 削除 |

手引書のその他の記述（鋼鉄の配合とプレス回数、ふた付き槽の材料、浸出量、電解のインゴット3個・希硫酸250 mB・20 kJ、硫黄・ANFO・クロム・ステンレス鋼）は実レシピと一致した。

## 主要ファイル

- レシピ：`data/.../recipe/cubic_press.json`、`gas_centrifuge.json`、`pressure_tank.json`。生成元`tools/generate_centrifuge_and_press.py`、`tools/generate_plant_tank_assets.py`も同じ材料に変更。
- Ponder：`tools/data/ponder_lessons.json`と`tools/generate_manufacturing_ponder.py`を正本として、`generate_manufacturing_ponder.py manufacturing_steel manufacturing_fluorine`と`generate_ponder_language.py`で`tutorials.json`・2章の構造NBT・3言語の翻訳を再生成。石臼の区画が不要になり、鋼鉄の章は5区画になった。
- 手引書：`tools/generate_manual.py`と`en_us.json`。
- 試験：`gametest/CraftingRecipeGameTests.java`。

## 検証

- `tools/validate_ponder_language.py`、`validate_translations.py`、`validate_plant_tutorials.py`：すべてPASS。
- `runPowerPlantRegressionGameTestServer`（35件）：修正前は新試験が上記3レシピを検出して失敗。修正後は34件成功し、新試験と`SurvivalReachabilityGameTests`も成功。
- 残る1件`nonOccludingEquipmentSurvivesFlowingWater`（ガス遠心分離機が流水で流される）は、今回の変更前の実行でも同じく失敗しており、本作業とは無関係。未修正。

## 未検証・制約

- クライアントでのJEI表示とPonderの見た目（区画の並び・字幕のページ割り）は画面で確認していない。生成元のPonder検証スクリプトは通過した。
- 外部Modが`c:plates/steel`を提供する環境でも、今回のレシピはステンレス鋼板を要求する。

## 次回の注意

- 追加修正：`data/.../tags/block/radiation_shielding/serpentinite.json`が改名前の`fuel_channel_tube`を指していたため、`reactor_fuel_channel_tube`へ直した（生成元`tools/generate_radiation_assets.py`も同じ）。燃料チャネル上部管は、生体遮蔽と同じ蛇紋岩の半価層（γ0.04 m・中性子0.09 m）で遮蔽計算される。既定のGameTestサーバー553件中551件成功し、放射線・RBMKの試験はすべて成功。失敗2件は流水試験（上記）と`everyCurrentItemHasExactlyOneAppropriateTab`（六フッ化ウランのバケツ）で、どちらも遮蔽タグとは無関係。
- 起動時に出る`createdieselgenerators:blocks/andesite_girder_strut`の戦利品表エラーはCDG側の問題で、本Modでは直せない。
- `docs/reports/product-uses.md`のモリブデングリース（空カートリッジ使用）は撤去前の記録。
