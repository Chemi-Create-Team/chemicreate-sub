# 工業用加熱器の3×3×3化と煙突

## 実装内容

- 工業用加熱器を、同じ「工業用加熱器」ブロック1種類を3×3×3に積み、上面中央に「工業用加熱器の煙突」を立てる形に変更した。箱と煙突がそろうと、正面下段中央の外装が自動でバーナー（`industrial_heater_burner`、既存の`IndustrialHeaterBlock`・`ReactorMachineBlockEntity`）に置き換わり、加熱器の所有者になる。
- 外装アイテムは地面を右クリックすると3×3の1段（9個）をまとめて置き、段の上面で次の段を置く（3段まで）。スニークで1個ずつ。
- 能力は管束3区画の固定：最大60,000 J/t、32 RPMで300 mB/5 tick（回転で最大4倍）、最高1,000 ℃。応力も3区画分。
- 接続（北向き）：原料は西面下段中央、出口は東面上段中央（流入方向は自動判定のまま）、燃料は正面下段西端、回転動力とCEE端子はバーナー正面。出口圧力の設定欄はバーナー正面のシャフトの上、目標温度は外装から設定（バーナーの背面が箱の内側になったため移した）。
- 煙突は中空で積み重ね可能。一番上に笠が付き、加熱中は上から煙（`CAMPFIRE_COSY_SMOKE`）が出る。クライアントの`animateTick`だけで描き、サーバー処理と通信は増やしていない。
- 旧部品（駆動部・炉室・加熱管束・排気フード・天井パネル）の登録・レシピ・ルート・モデル・ブロックステート・翻訳・タグ・形状データを削除。`industrial_heater`のIDは外装に引き継いだ。旧ワールドの加熱器は組み直しが必要。
- レシピ：外装4個＝レンガ4・ニッケル1・クロム1・鉄格子2・ジルコニア1。煙突＝旧駆動部と同じメカニカルクラフターのレシピ。
- Ponder「工業用加熱器を組み立てる」「工業用加熱器をつなぐ」を作り直し（拡張の章は削除）。完成までは外装を単体の見た目で見せ、運転中は煙突から煙を出す（チュートリアルの手順に`smoke`を追加）。製造系統のPonder（アンモニア・燃料精製・樹脂2章・製鋼）と転炉・配管圧力のPonderも新しい加熱器に合わせた。
- モデルは仮のもの。Fable向け指示書：`docs/specs/modeling-brief-industrial-heater.md`。

## 主要ファイル

`block/HeaterStructure.java`、`block/HeaterCasingBlock.java`、`block/HeaterChimneyBlock.java`、`item/HeaterCasingItem.java`、`block/IndustrialHeaterBlock.java`、`block/entity/ReactorMachineBlockEntity.java`、`registry/CEHeating.java`、`thermal/TemperatureSetting.java`、`thermal/PressureSetting.java`、`ponder/PlantPonderScenes.java`・`PlantTutorialCatalog.java`、`tools/generate_industrial_heater.py`（モデル・状態・Ponder・翻訳の正本）。旧部品を出力していた`generate_heating_assets.py`・`generate_thermal_control_assets.py`・`generate_heating_tutorials.py`・`revise_equipment_ponder.py`からは加熱器部分を外した。

## 確認

- GameTest 139件合格（ThermalControl、ThermalAssembly、Heating、Circuit、CraftingRecipe、CreativeCatalog、Audit、PlantTutorial、TurbineThermal、PressureAndFreezing、ThermalStability、SurvivalReachability、EquipmentPorts、Counterflow、ExchangerAssembly、DisplayIntegration、Reactor、ManufacturingPonder、Converter、Refrigeration）。新規に、アイテムでの段置き・4段目の拒否・煙突による完成・バーナー撤去で外装が戻ることと、外装欠けで流体を保持し再完成することを確認。
- `validate_plant_tutorials.py`合格（加熱器のPonderが外装26・バーナー1・煙突1であることを追加）。
- Xvfb＋ソフトウェア描画でPonderを撮影：`docs/quality/heating/industrial-heater-box.png`、`industrial-heater-connections.png`（煙突の煙を確認）。
- 未確認：実際のワールドでの段置きの操作感、煙の見え方、Fableによる本番モデル。

## 注意

- mainの鋼鉄素材分離（`steel_block`）を取り込み、煙突のレシピは鋼鉄ブロックを使う。
- 加熱器の能力は旧最小構成の3倍になった（旧1列＝20 kJ/t）。転炉ガスのtick換算などの文書は更新済み。
- `generate_manufacturing_ponder.py`は実行のたびにほかの製造Ponderのバイナリも書き換える（内容同一）ため、加熱器を含む章以外は元に戻している。
