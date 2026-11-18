# 水冷ジャケット管：パイプへの後付けと八角形モデル

## 実装内容

- 水冷ジャケット管`jacketed_pipe`を、普通の液体パイプに後から付ける方式に変更した。アイテムを持って、水平で真っすぐなCreateの液体パイプ（`create:fluid_pipe`、窓付きの`create:glass_fluid_pipe`も可）を右クリックすると、そのマスがジャケット管に置き換わる。向きはパイプの軸に沿い、プレイヤーの向きが軸と同じならその向き、違えば軸の正方向になる。接続のないパイプはプレイヤーの向きを使う。
- 曲がり・分岐・縦のパイプでは付かず、アクションバーに案内を出す。パイプ以外への単独設置もしない。サバイバルではアイテムを1個消費する。
- 置き換え後は従来の構造判定（`ExchangerStructure`）がそのまま並びを認識し、1〜8個で1台になる。熱計算・接続口（中心の管は両端の面、ジャケットは最初の上面と最後の下面）は変更なし。
- 壊すとジャケット管と液体パイプの両方を落とす（外装・制御部の両方のルート）。レシピからパイプを外した（鉄板6・銅板2で3個）。
- モデルを作り直した：八角形のステンレスのジャケット（十字の2本の箱と45°回転の板4枚で作る）、端のボルト8本付き八角フランジ、Createの`pipes`テクスチャを使った管の口、ジャケットの上下ノズル、最初の端の蛇腹風リング。専用テクスチャ`textures/block/jacketed_pipe/`の`jacket`・`flange`・`bolt`（各4色以下）を生成元で描く。
- Ponder「水冷ジャケット管」を、液体パイプを通す→ジャケット管で右クリック→接続→熱移動の4段に変更。完成前の段ではジャケットの位置を液体パイプとして表示する。手順の`use`項目（対象を右クリックするアイテムの表示）を追加。ジャケット側の入口を奥側へ移し、並びが見えるようにした。

## 主要ファイル

- `item/JacketedPipeItem.java`：右クリックでの取り付け、方向判定、拒否
- `registry/CEHeating.java`：`jacketed_pipe`のアイテムを`JacketedPipeItem`で登録
- `ponder/PlantPonderScenes.java`・`PlantTutorialCatalog.java`：`use`項目と、ジャケット管の完成前表示
- `tools/generate_heat_exchangers.py`：`octagon()`・`jacket_cell()`・`textures()`・ルート・Ponder・案内文（正本）
- `tools/generate_crafting_recipes.py`：レシピ

## 確認

- GameTest：新規`aJacketClosesOnlyAroundAStraightLevelFluidPipeAndGivesBothBack`（真っすぐなパイプで取り付け・向き・1個消費、壊したときの2種類のドロップ、曲がりと縦パイプでの拒否、パイプ以外に置けないこと）。GameTest全体では、ここで直した水の試験と既存の汚れ試験の2件だけが失敗した。水の試験（`nonOccludingEquipmentSurvivesFlowingWater`）は、前回の熱交換器の変更から失敗していた。1個置いたジャケット管が自分で制御部に変わるのを「流された」と判定していたため、制御部への変化を残存として扱うよう直し、合格を確認した。`VesselFoulingGameTests.aFouledProcessStopsThenResumesAfterOneManualBrush`は、変更前のコミットでも同じく失敗する。容器側の最近の変更（1018e16）に関係するとみられ、この作業の対象外として残した。
- `validate_plant_tutorials.py`合格。
- Xvfb＋ソフトウェア描画で撮影：`docs/quality/heating/jacketed-pipe.png`（Ponder）、`docs/quality/heating/jacketed-pipe-closeup.png`（並び4個・単体・未完成の拡大、確認用の一時配置）。
- 未確認：実際のプレイでの右クリック操作（クライアント・サーバー間の手触り）、ジャケット内に残っていたパイプの流れの扱い（置き換え時にパイプ内の流れは引き継がない）。

## 注意

- ジャケットを外してパイプだけに戻す操作（レンチ）は用意していない。壊すと両方を落とすので、パイプを置き直す。
- 実装コミットIDはコミット後の`git log`を参照。
