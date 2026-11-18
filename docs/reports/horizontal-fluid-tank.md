# 横型流体タンク（ChatGPT引き継ぎ用）

## 実装内容

Create標準の流体タンクを横に倒して使える`horizontal_fluid_tank`を追加した。Createのアイテム保管庫と同じく、置いた向き（X軸／Z軸）が長さの方向になる。見た目はCreateのタンクのモデルとテクスチャをそのまま横向きにしたもので、隣のタンクと同じ向きでつなぐと一体になる。本MODの液体タンク等（プラント用タンク）には適用していない。

- 同じ軸の横型タンクだけがつながる。断面は1×1、2×2、3×3、長さはCreateの縦タンクの最大高さ設定（既定32）まで。向きの違うタンクとは別のタンクになる。
- 窓は無い。レンチでの窓切替は何もしない。
- 容量・液体入出力・破壊時の分割・ポンプ／パイプ接続はCreateのタンクと同じ。
- **ボイラーとして使える**。ブレイズバーナー等の熱源は最下層全体（長さ×幅）の真下で数え、蒸気エンジン・蒸気笛は全セルの周囲で数える。大きさ・水量による出力上限とエンジン効率はCreateの計算そのまま。
- クラフト：`create:fluid_tank`と`horizontal_fluid_tank`は、どちらからでも1個ずつ作り替えられる（形なしレシピ）。

## 主要ファイル

- `block/HorizontalFluidTankBlock`（軸の状態、置いた向きの決定）、`block/entity/HorizontalTankBlockEntity`（軸に沿った連結・TOP/BOTTOMの更新・ボイラー更新）、`block/entity/HorizontalBoilerData`（Createの`BoilerData`を横向きの幾何で置き換え）、`block/TankCells`（軸に沿ったセル列挙）
- `block/entity/BoilerSteamOutput`のセル走査を`TankCells`に置き換え（既存の縦タンクの挙動は変わらない）
- `client/HorizontalTankCTBehaviour`と`PlantTankModels`（Createの連結テクスチャを軸に合わせて適用）
- `tools/generate_horizontal_tank.py`（ブロックステート・アイテムモデル・レシピ・ルートテーブル。モデルはCreateのタンクのモデルを回転して使う）

## テスト結果

- GameTest：`HorizontalTankGameTests` 4件を追加し、全体309件が成功。同軸の連結とTOP/BOTTOM、2×2断面と向き違いの非連結、液体の出し入れと中央を壊したときの分割、バーナーと蒸気エンジンによるボイラー判定。
- 実クライアント（`-Dcreateplant.horizontalTankClientCheck=true`）：1×4と2×2×3が、Createの銅のタンクの見た目で、長さ方向に一体で描画されることを確認した。

## 未検証・制約

- 窓と、ボイラー稼働中のゲージ表示は無い（窓なしの横型は流体が見えない）。ボイラーの実運転（蒸気エンジンの回転）はGameTestでは熱とエンジンの認識まで。
- アイテムの手持ち表示は縦向きのモデルのまま。
- 縦タンクのような「上に置くと同じ大きさで自動増設」は無く、1個ずつ置く。
- 流体の表示ブロックの光（溶岩の発光）は、Createと同じ窓つきのときだけなので、横型では発光しない。
