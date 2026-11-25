# 産業用アームと組立ジグ

Createのメカニカルアームと同じ関節計算・モデルで動く黄色い産業用アームが、組立ジグの上で段階的な3D組立を行う。操作は右クリック、レンチ、ホッパーやファンネルへの搬入だけで、独自GUIはない。

## 部品

| 部品 | 役割 |
| --- | --- |
| 組立ジグ `assembly_jig` | アームが作業する台。作業範囲は上の最大3×3×3マス。レシピと各工程の状態・進捗を持つ。1000 mBの液体タンク（パイプで充填、スプレー用） |
| 産業用アーム `industrial_arm` | 回転で動く。下面から軸を受ける。応力4 SU。工具スロット1個と1000 mBの液体タンク |
| グリッパー `gripper` | ブロックを置く |
| 溶接トーチ `welding_torch` | 継ぎ目の溶接 |
| レーザーカッター `laser_cutter` | 外周・対角の切断 |
| スプレーノズル `spray_nozzle` | 全面の吹き付け（液体を消費） |
| 精密配線ヘッド `wiring_head` | 6×6の配線とフェライトコア |

工具はスタック1。工具を持って右クリックで装着（付いている工具とは交換）、素手の右クリックで外す。ホッパー、ファンネル、シュート、ベルトから工具スロットへ出し入れできる。ジグにレンチを使うと、読み込み済みのレシピを順に切り替える（工程が始まった後は不可）。素手で右クリックすると進捗を表示する。

## 動き

1. アームは5マス以内のジグを探す（10 tickごと）。
2. ジグの現在のステージで、自分の工具でできる工程のうち、自分に最も近い未着手のものを予約する。1つの工程は1台にだけ割り当てる。同じステージの工程は複数台で並列に進み、次のステージは前のステージがすべて終わってから始まる。
3. 工程の材料（置くブロック、消費する線材・部品）を、5マス以内のアイテムを持つブロック（チェスト、デポ、ベルトなど）から1個取る。スプレーの液体はアームのタンクを先に、足りなければジグのタンクから、工程の開始時に引き落とす。材料か液体が無ければ予約を戻し、3秒待つ。
4. 作業位置へ動き、作業する。作業時間はレシピの `time` tickを16 RPMで基準にし、回転が速いほど短い。回転が0なら止まる。
5. グリッパーは空気のマスにだけブロックを置く。マスが埋まっていれば、空くまでブロックを持って待つ。

工具の取り外し、アームの破壊、ジグの変更で予約は戻る。ジグは20 tickごとに、アームが持っていない予約を戻す。予約は保存しない。

全工程が終わって40 tick後、ジグが置いたブロックを消し（ジグが置いたものと同じブロックだけ）、完成品を上に出し、蒸気と音を出す。

## 描画

ジグの描画が、サーバーが同期した進捗から、アームと同じ軌道（`arm/Trajectory`）で描く。

- 溶接: 継ぎ目に沿ったビード。熱は白→黄→橙→暗赤→銀へ、トーチからの距離と時間で冷める。アークの閃光、火花、煙、音。
- 配線: 6×6の線。銅（縦）と銀（横）、対角線は金と銀。交点にフェライトコアの輪。
- レーザー: ビームと、通った後の暗い溝、煙。
- スプレー: ジグザグの吹き付けと、流体の色の半透明の膜、滴。

## データパック

レシピは `data/<名前空間>/assembly/<名前>.json`。表示名は翻訳キー `assembly.<名前空間>.<名前>`。

```json
{
  "size": [3, 1, 3],
  "stages": [
    [ { "pos": [1, 0, 1], "action": "place", "tool": "gripper", "block": "minecraft:iron_block" } ],
    [ { "pos": [1, 0, 1], "action": "process", "tool": "wiring_head", "pattern": "grid_wire", "consume": "minecraft:copper_ingot", "time": 120 } ]
  ],
  "result": { "id": "create_refinery_n_reactors:magnetic_core_memory", "count": 1 }
}
```

- `size`は各軸1〜3。`pos`はジグの真上のマスを原点にした相対座標で、範囲内。
- `action`は `place`（グリッパーだけ。`block`が必要）か `process`。グリッパーは置くことしかしない。
- `pattern`: `seam_weld`、`perimeter_cut`、`diagonal_cut`、`grid_wire`、`full_spray`。省略時は工具の既定。`seam_weld`は水平に隣接する `place` のマスとの境を溶接する。
- `full_spray`は `"fluid": {"id": "minecraft:water", "amount": 100}` が必要。`consume`は材料として取るアイテム。
- 不正なレシピは読み込み時に警告を出して無視する。

サンプル: `welded_iron_beam`（鉄ブロック2個を置き、継ぎ目を溶接）と `magnetic_core_memory`（鉄ブロック3個の枠、配線グリッド、両端の溶接）。

## 実装

- `arm/`: レシピ（`ArmRecipe`、`ArmRecipes`）、工具（`ArmTool`）、軌道（`Trajectory`、サーバーとクライアントで共有する純粋な計算）、Createの関節角の取得（`ArmPose`）、先端の粒子（`ArmEffects`）。
- `block/AssemblyJigBlock`、`block/entity/AssemblyJigBlockEntity`: 予約・進捗・完成。更新タグで同期し、進捗の変化は2 tickごと。クライアントは進捗を補間して描く。
- `block/IndustrialArmBlock`、`block/entity/IndustrialArmBlockEntity`: 探索・材料の取得・作業。サーバーは狙う先端の位置だけを同期し、クライアントがCreateの `ArmAngleTarget` で関節角を求めて動かす。
- `client/IndustrialArmRenderer`、`client/AssemblyJigRenderer`: 描画。Flywheelのインスタンス描画は使わない。
- Createの `ArmAngleTarget` の角度は非公開なのでリフレクションで読む（Mixin・Access Transformerは使わない）。読めない場合、アームは休止姿勢のままになり、ログに警告が出る。
- ゴーグル: アームは状態・工具・ジグの位置と工程・タンク、ジグはレシピ・ステージ・工程数・タンクを表示する。
- Ponder: `plant/industrial_arm`（`ponder/ArmPonderScenes`）。
