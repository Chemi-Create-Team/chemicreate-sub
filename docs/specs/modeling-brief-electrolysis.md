# モデリング指示書：電解セルと電解セルコントローラー

この作業では、この文書と「読むファイル」に挙げたものだけを読んでください。リポジトリの探索、`docs/`・`src/main/java/`・ほかの`tools/`の読み込み、ビルドは不要です。登録・Ponder・文書・ゲーム内確認は別の担当が行います。

## 目的

外装を銅色から外し、Createと並べて浮かない16×16のピクセルアートに作り直す。現在の外装は写真風の生成画像（1024×1024・約4万色）で、規約に反している。

## 読むファイル

- `tools/electrolysis_hardware.py`：モデルとテクスチャの生成元（正本）
- `tools/generate_electrolysis_cell.py`：上を呼ぶ入口。モデル以外の部分は変更しない
- `src/main/resources/assets/create_refinery_n_reactors/textures/block/electrolysis_cell/`

モデルJSON（`models/block/electrolysis_cell/`）とブロックステートは生成物です。手で直さず、生成元を変更して`python3 tools/generate_electrolysis_cell.py`で出力してください。

## 変えてよいもの・変えないもの

| 変えてよい | 変えない |
| --- | --- |
| 外装・フレーム・締付金具・ハッチ・極板・計器盤の形状と画像 | モデル名と数（`wall_*`、`rail_*`、`end_*`、`plates_1`〜`plates_8`、`hatch`・`hatch_north`・`hatch_south`・`hatch_both`、`top_rails_*`、`top_cap_*`、`bubble`、`item`、`controller_0`〜`3`、`dial`、`needle`、`push_button`） |
| 色と材質 | ブロックステートの条件（セル：`axis`・`open`・6方向の接続、コントローラー：`facing`・`part` 0〜3） |
| | 壁は厚さ2ピクセルで内部は中空。接続面の内壁は消える |
| | 下記のレンダラー座標 |

## 構造の要点

- セルは縦2×2の断面を水平に1〜8マス並べ、片方の端面に外向き2×2のコントローラーを付ける。
- 上面に点検ハッチ（開閉あり、開くと液面と極板が見える）。上段のセルは幅方向の左右2列がそれぞれ1枚の長いハッチでつながり、外側の長辺を軸に開く。電極板は開いたハッチから入れる。CEEの電源端子は右上区画（part 3）の上面に立つ2本。下段側面が給液、上段の左右の列が陰極ガス・陽極ガスの出口。外観で区別できるようにする。
- コントローラー：`part` 0・1は下段（地板のみ）、2は左上（電圧計・電流計、緑のSTARTと赤のSTOPボタン）、3は右上（稼働率ダイヤル、上面に電源端子2個）。

## レンダラーが描く位置（動かさない）

針・ボタン・数字・ダイヤルはJavaのレンダラーが次の位置に描きます。数値はレンダラーの座標で、モデルのx座標は`16 − x`です。単位はピクセル（1ブロック＝16）。

| 部品 | 位置 |
| --- | --- |
| 電圧計・電流計の針（part 2） | 中心(4.5, 10.6)と(11.5, 10.6)、長さ2.65、振れ角±50° |
| 計器の窓（part 2） | (1.19, 10.09)〜(7.81, 14.79)と(8.19, 10.09)〜(14.81, 14.79) |
| 数値表示（part 2） | 中心x=4.5と11.5、y=8.61〜9.53 |
| START・STOPボタン（part 2） | x=4と12、y=3.4 |
| ダイヤル（part 3） | 中心(8, 7)、−135°〜+135° |
| 稼働率の数値（part 3） | 中心x=8、y=1.35〜2.5 |
| 電源端子（part 3） | 上面に立つ2本。x=4と12、z=8、高さ16〜18.3（CEEの接続点はy=18） |

## テクスチャの規則

- 1面16×16。大きな部品も同じ密度にする（2ブロック分なら32×32）。
- 1面あたり24色以下。ざらつきは2ピクセル以上の塊で示し、1ピクセルのノイズで埋めない。
- 光源は左上。外周1ピクセルの縁（右下が最も暗い）、その内側1ピクセルの明るい面取り、中央12×12は一段沈めて素材を描く。
- 写真、写真から作った画像、画像生成AIの出力、なめらかなグラデーション、広い鏡面ハイライトは使わない。
- 計器の目盛と文字だけはやや細かくしてよいが、ピクセルアートの範囲に収める。数字のフォント画像`readout.png`は流用してよい。
- `tools/data/electrolysis/material-source.png`と`prompt.json`、それを使う処理は削除する。`porcelain.png`は16×16にする。
- Createの`create:block/industrial_iron_block`など既存のブロック画像を参照するのはよい。画像を複製して色だけ変えたファイルは作らない。

## 配色

既定はニッケル・ステンレスの銀灰色（ユーザーが別の色を指定した場合はそちらを優先）。

| 部位 | 目安 |
| --- | --- |
| 外装・フレーム | 本体`#8C9396`、明部`#B4BBBE`、暗部`#5C6366` |
| 締付ボルト・取手 | 小さな差し色として真鍮色を残してよい |
| 計器盤の地板 | 暗い灰色。文字盤は白に近い灰 |
| ボタン | START緑・STOP赤。原色は避け、彩度を抑える |

銅色は使わない。

## 完了時に確認すること

1. `python3 tools/generate_electrolysis_cell.py`がエラーなく終わる。
2. 次のコマンドで、すべての画像が16の倍数の大きさで、外装の画像が24色以下になっている（計器盤`panel_*`と`readout.png`は除く）。

```bash
python3 -c "from PIL import Image;import glob
for f in sorted(glob.glob('src/main/resources/assets/create_refinery_n_reactors/textures/block/electrolysis_cell/*.png')):
    i=Image.open(f).convert('RGBA');print(f.split('/')[-1],i.size,len(set(i.getdata())))"
```

3. `git status`で、変更が`tools/electrolysis_hardware.py`、`tools/generate_electrolysis_cell.py`、`tools/data/electrolysis/`、`models/block/electrolysis_cell/`、`models/item/electrolysis_cell*.json`、`blockstates/electrolysis_cell*.json`、`textures/block/electrolysis_cell/`の範囲に収まっている。

変更したファイル、配色、確認結果、気になった点を短く報告してください。

## 最初に渡す指示文

```text
docs/specs/modeling-brief-electrolysis.md を読んで、その指示どおりに電解セルと電解セルコントローラーのモデルとテクスチャを作り直してください。指示書に挙げたファイル以外は読まないでください。
```
