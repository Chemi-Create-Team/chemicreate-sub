# 装置内部の実温度 完了レポート（2026-11-23）

仕様：[internal-temperature.md](../internal-temperature.md)。未検証（ゲーム内での実行はしていない）。コンパイルと翻訳チェックのみ。

## 実装した範囲（仕様8章の順）
1. 共通部品 `thermal/HeatContent`（実温度・乾き度・残熱、`NBT: Kelvin / Level / Quality / ResidualHeat`）。`ThermalFluidTank` に組み込み、入るとき熱容量で混ぜ、出るとき段階の丸め差を中身に戻し、空になれば残熱に回す。SIMULATEでは変えない。`fillAt` で実温度つきの搬入。
2. バーナー加熱 `TankHeating`：1台1 MW・超加熱2 MW（台数分を足す）。同じ槽の中身は同じ温度だけ上がる。沸点で止まり、蒸発熱を吸ってから相が変わる。Createタンク・タンクケーシング・ふた付き槽。
3. 熱交換器：並行流 `C1C2/(C1+C2)·ΔT`、対向流 `0.9·Cmin·ΔT`。沸点をまたぐ流れは蒸発・凝縮熱を含む。出口バッファーへ実温度を渡す。
4. ラジエーター（1 mBあたり287 J、常温まで）、コールドボックス（顕熱＋凝縮熱＋動力を冷却水へ。冷却水は372 Kまで）。冷却塔は今も常温の水を出すため変更なし。
5. 往復式圧縮機は実温度を保って出す。ほかの反応装置・ボイラー・ガス化炉は、生成物を指定の段階の代表温度で入れる（今のまま）。
6. 相変化と蒸発熱は、タンク加熱・熱交換器・ラジエーター・コールドボックスの各装置で組み込み。
7. 反応熱：`pressure_reaction` に任意の `reaction_heat`（J/バッチ）を追加。ふた付き槽（と反応機械）の中身へ足し引きする。JEIに「発熱／吸熱」だけ表示。
8. 文書（DESIGN.md、fluid-states.md、thermal-grades.md、counterflow-heat-exchanger.md）、Ponder文（槽の加熱、季節、熱交換器、ラジエーター）、GameTest（`InternalTemperatureGameTests`）。

## 登録した反応熱（限界反応物のmolから。正が吸熱）
| レシピ | J/バッチ |
| --- | ---: |
| sodium_hypochlorite_solution | -1162 |
| ammonium_nitrate | -1560 |
| ammonium_sulfate | -1474 |
| hydrogen_chloride | -1923 |
| sulfur_dioxide / sulfur_dioxide_stirred | -6633 |
| calcine_calcite | +3699 |
| calcine_bicarbonate | +1341 |
| urea | -271 |

## 仕様からずれた点・残り
- 反応熱を足す温度変化は、1回の反応で最大100 Kに抑える（常圧換算の気体mBは熱容量が小さく、気体だけの槽では数千Kに跳ねるため。超えた分は壁に逃げる扱い）。
- 反応熱は `pressure_reaction` のJSONだけ。アンモニア合成・燃焼・FT合成・メタノール合成・水性ガスシフトなどのJava定義（`ProcessRecipe`）は未登録。気体だけの装置なので、上の上限と合わせて値を決める必要がある。
- 反応熱のあるレシピでも、生成物は指定の段階の代表温度で入れる（仕様は「反応時点の実温度で入れる」）。
- 乾き度の途中で出す場合の部分排出（5章）は未対応。沸点の途中の中身は全量が相を変えるまで変わらない。
- 熱交換器の水蒸気の凝縮は、凝縮分の水を段階の温度（最大100 ℃）で出す今の処理のまま。
- MixtureTransfer経由の混合物の搬入は、実温度が段階の代表温度に戻る。
- 冷却塔・ガス化炉・ボイラーの専用の放熱量計算は未実装。
