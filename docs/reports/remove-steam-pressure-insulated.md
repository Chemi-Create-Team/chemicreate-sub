# 蒸気設備・圧力設備・断熱設備の削除と空気分離塔の入出口

## 削除したもの
- 蒸気設備一式：蒸気過熱器制御部、復水器制御部、過熱器管束、復水器管束、蒸気設備圧力ヘッド、燃焼モジュール、液体排熱回収モジュール、復水器冷却水モジュール、蒸気設備煙道（`CESteam`、`SteamMachine*`/`SteamPart*`/`SteamPlantStructure`/`SteamShapes`/`SteamMachineRenderer`、Ponder3本、テスト、生成ツール）。ガス管のテクスチャが使う`textures/block/steam/steel.png`と`frame.png`は残した。
- 圧力室・圧力計・高圧ポンプ：ブロック、BlockEntity、描画、Ponder（pressure_*の6本とpressure_relief）、レシピ。`PressureSections`の高圧ポンプ分岐も削除。圧力調整弁・背圧弁は残し、圧力室がなくても動くようにした（`PressureValveBlockEntity.assembled()`は常にtrue）。
- 断熱パイプ・断熱流体タンク：ブロック、モデル、テクスチャ、レシピ、`PipeMaterial.INSULATED`、Ponder（passive_heat、phase_energy、thermal_environment、water_phase_storage）。

## 削除したゲームテスト（計57件前後）
断熱タンク・断熱パイプの完全断熱を前提にした熱量・温度・圧力のテスト25件と、蒸気設備・圧力設備のテスト約30件。パイプ移送の熱量保存、PassiveHeatの一部、減圧弁の一部、リアクターの`functionalEnvelopesLimitsAndCachedScans`などの確認が失われた。Create標準のタンクはPassiveHeatで周囲と熱交換するため、そのままでは同じ検証ができない。

## 空気分離塔の入出口（`docs/distillation-plant.md`）
東面3段目に液体空気を搬入。北面1段目に液化酸素、10段目に粗アルゴン、14段目に窒素を搬出。粗液化酸素は液化酸素（`liquid_oxygen`）へ改名し、塔内で下塔・熱交換部・上塔へ受け渡す。

## 確認
ゲームテスト208件成功、クライアント起動でモデル・登録のエラーなし。
