# 流体熱交換器の作り直し（シェル＆チューブ式・水冷ジャケット管）

## 実装内容

- 棚型の流体熱交換器（制御部・外装・上部フレーム・管束の4部品）を削除し、2種類に置き換えた。どちらも1種類のブロックを一直線に並べるだけで完成し、最初の端の下側の1個が自動で制御部になる（加熱器と同じ「外装＋自動で制御部に置き換え」の方式）。
  - シェル＆チューブ式熱交換器`heat_exchanger`：断面2×2、長さ2〜12。管側は両端の面、胴側は両端の上面。
  - 水冷ジャケット管`jacketed_pipe`：断面1×1、長さ1〜8。中心の管は両端の面、ジャケットは最初のブロックの上面と最後のブロックの下面。1個でも動く。
- ブロックは見ている方向に沿って寝かせて置かれる。並びの端に足せば同じ制御部が取り込んで長くなり、外せば短くなる。制御部を壊すと外装は単体の見た目に戻る。
- 熱計算は従来の`ThermalEquipment`をそのまま使い、能力は長さLで決まる（UA 500L W/K、最大20,000L J/t、100L mB/5 tick）。回転動力・設定は不要。
- 共通の外装基底`ThermalCasingBlock`を作り、加熱器の外装もこれを継承する形に整理。旧`HorizontalThermalStructure`・`HeaterPartBlock`・`CasingWallBlock`と、旧部品の登録・レシピ・ルート・モデル・ブロックステート・翻訳・タグ・形状・Ponder（基本・拡張・連携の3章）を削除。`heat_exchanger`のIDは新しい外装が使う。旧ワールドの熱交換器は組み直しが必要。
- 表示の「下段／上段」を「管側／胴側」に変更（ゴーグル、ディスプレイリンクの項目名、バッファ名）。
- レシピ：シェル＆チューブ式4個＝鋼鉄インゴット4・銅板2・ジルコニウム2・Createの液体パイプ1。水冷ジャケット管3個＝鉄板4・銅板2・液体パイプ3。極低温再生熱交換器とコールドボックス駆動部の材料は旧管束から水冷ジャケット管へ変更。
- Ponder「シェル＆チューブ式熱交換器」「水冷ジャケット管」を新規作成。製造系統の空気分離・冷凍のPonderも新しい熱交換器に組み替えた。
- モデルは仮のもの。Fable向け指示書：`docs/specs/modeling-brief-heat-exchangers.md`。

## 主要ファイル

`block/ExchangerStructure.java`、`block/ExchangerCasingBlock.java`、`block/ExchangerControllerBlock.java`、`block/ThermalCasingBlock.java`、`block/entity/ReactorMachineBlockEntity.java`、`registry/CEHeating.java`・`CEReactor.java`、`tools/generate_heat_exchangers.py`（モデル・状態・Ponder・翻訳の正本）、`tools/generate_refrigeration.py`（Ponder用の熱交換器配置）。

## 確認

- GameTest 156件合格（熱交換器・加熱器・反応・冷凍・表示・核融合・回路・レシピ・アセット監査・Ponder構造など）。新規に、2種類×4方向×最短・最短＋1・最長での自動完成と接続口、端の外装の撤去と再設置、制御部の撤去で外装が戻ること、ジャケット管を足すと同じ設備が長くなること、出荷Ponder構造が4回転すべてで完成すること、ポンプと被覆パイプで4つの接続口を実際に流すこと（流す向き5通り）を確認。
- `validate_plant_tutorials.py`合格。生成スクリプトは再実行しても差分が出ない。
- Xvfb＋ソフトウェア描画でPonderを撮影：`docs/quality/heating/shell-and-tube-exchanger.png`、`jacketed-pipe.png`。
- 未確認：実際のワールドでの置き心地、Fableによる本番モデル。

## 注意

- 2×2の断面は制御部の位置から「進行方向の右側」に広がる。プレイヤーが並べたときは、置いた向きのまま最初の端から始まる。
- 長さ2未満のシェル＆チューブ式は完成しない（状態表示で長さを案内する）。

## 追加：大型シェル＆チューブ式熱交換器（3×3）

- `large_heat_exchanger`（制御部`large_heat_exchanger_controller`）を追加。断面3×3、長さ2〜12、接続口は2×2と同じ（管側は両端の面、胴側は両端の上段の上面）。能力は長さ1あたり2単位（UA 1,000L W/K、最大40,000L J/t、200L mB/5 tick）。
- 種類の定義は`ExchangerStructure.Kind`の`LARGE(3, 2, 12, 2)`（幅・最短・最長・長さ1あたりの単位）。外装の`cell`は「区分×9＋横＋幅×上下」に変更し、3種類で0〜35を共有する。
- レシピ：大型4個＝鋼鉄インゴット4・ジルコニウム2・銅板2・Createの液体タンク1（機械式クラフト）。
- Ponder「大型シェル＆チューブ式熱交換器」を新規作成。撮影：`docs/quality/heating/large-shell-and-tube-exchanger.png`。
- 確認：GameTest 157件合格（3種類×4方向×長さの自動完成と単位数、Ponder構造の4回転での完成を含む）。`validate_plant_tutorials.py`合格、生成スクリプトは再実行で差分なし。
- 未確認：実ワールドでの置き心地、本番モデル（指示書を3種類に更新済み）。
