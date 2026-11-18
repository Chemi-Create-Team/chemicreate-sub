# Create: Deep Seas・Sable・Create Aeronautics との連携

対象は Minecraft 1.21.1 / Create 6.0.10 です。これらのMODは必須ではありません。同時に入れたときだけ、ステンレス鋼ブロックとチタンブロックが潜水艦の耐圧殻・重さのあるブロックとして働きます。入れていなくても、2つのブロックは金属の貯蔵ブロックとして使えます。

## 金属ブロック

| ブロック | レシピ | 戻す | タグ |
| --- | --- | --- | --- |
| ステンレス鋼ブロック | ステンレス鋼インゴット9（`c:ingots/stainless_steel`） | ステンレス鋼インゴット9 | `c:storage_blocks/stainless_steel` |
| チタンブロック | チタンインゴット9（`c:ingots/titanium`） | チタンインゴット9 | `c:storage_blocks/titanium` |

- どちらも鉄ブロックと同じ硬さで、鉄以上のツルハシで掘ります。ビーコンの土台になります。

## Create: Deep Seas（`create_submarine` 3.4.7以降）

| ブロック | 基準深度（Base Depth） | 圧壊の確率 |
| --- | --- | --- |
| ステンレス鋼ブロック | 600 | 0.05 |
| チタンブロック | 1200 | 0.03 |

- 基準深度は、Deep Seas のゴーグル表示の「Base Depth」です。Deep Seas が自動で計算する金属ブロックの値は最大400で、黒曜石が500、岩盤が760です。チタンは、深さ1200のアビスの底まで届きます。
- 値は Deep Seas の既定表（`HullStrengthConfig.buildStaticDefaults`）に加えます（`mixin/deepseas/HullStrengthConfigMixin.java`、値は `compat/deepseas/SubmarineHulls.java`）。Deep Seas がないときは、このMixinを読み込みません。
- 既定表なので、`config/submarine_hull.json` に同じブロックの値を書けば、そちらが優先されます。最初の起動で、このファイルに2つの値が書き出されます。

## Sable・Create Aeronautics

- Sable は `c:storage_blocks` を重いブロック（重さ4.0）として扱います。ステンレス鋼ブロックは鉄と同じ4.0、チタンブロックは鉄の約57％の2.3にしました（`data/create_refinery_n_reactors/physics_block_properties/`）。
- Sable がないとき、このフォルダーは使われません。

## 動かす条件

- Sable 2.0.6・Create Aeronautics 1.3.2・Create: Deep Seas 3.4.7 は NeoForge 21.1.228 以降が必要です。このMODの開発環境は 21.1.219 のままです。
- 3つを入れて試すとき：`bash gradlew -PwithDeepSeas=true -Pneo_version=21.1.228 runGameTestServer`（`runClient` も同じ）。

## 確認

- `MetalBlockGameTests.theHullBlocksStoreTheirMetalAndHoldDeepWater`：2つのブロックの登録・タグ・レシピ。Deep Seas があるときは、基準深度が600・1200であること。
- Ponder「ステンレス鋼とチタンの耐圧殻」（2つのブロックから開く）。
