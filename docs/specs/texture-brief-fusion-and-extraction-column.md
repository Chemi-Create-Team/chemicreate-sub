# テクスチャ指示書：核融合炉の外装とパルス抽出塔

この作業では、この文書と「読むファイル」に挙げたものだけを読んでください。リポジトリの探索、`docs/`・`src/main/java/`・ほかの`tools/`の読み込み、ビルドは不要です。

つながるテクスチャの仕組み、抽出塔の立体モデル、構造判定、Ponder、翻訳は別の担当が済ませています。今は仮の画像が入っています。この作業で描くのはテクスチャだけです。

## 目的

### 核融合炉

今は、どのブロックにも明るい二重の額縁があります。そのため炉の大きな面が、枠付きのタイルを敷き詰めた壁に見えます。また、一番広い増殖ブランケットのオレンジ色の模様が、炉全体を柄物に見せています。

次の3点を満たすように描き直してください。

1. **外装はつながる1枚の外板にする。** 同じブロックが並んだ面は、継ぎ目のない1枚の板に見せます。枠は、面の外周（ほかのブロックや空気との境目）だけに出します。
2. **外装・ブランケット・容器は、落ち着いた銀色の外板にする。** 実機のように暗い色ではなく銀色にし、模様の主張と明るさの差を抑えます。
3. **色を付けるのは、コイルとポートだけにする。**
   - コイル：銅の巻き線。
   - ポート：流体ごとの色の口。
   - それ以外は、銀色と鋼の灰色の段階だけで描きます。

### パルス抽出塔

塔の各段は、3×3ブロックの大きさの正八角柱です。これに合わせて、側面・天面・フランジを描いてください。

## 核融合炉のテクスチャ

置き場所は`src/main/resources/assets/create_refinery_n_reactors/textures/block/fusion/`です。

### つながるテクスチャ（6種）

Createの`OMNIDIRECTIONAL`方式（Createの各ケーシングと同じ）でつなぎます。素材ごとに、次の2枚を描きます。

- `<名前>.png`（16×16）：単体の見た目。手に持ったアイテムやGUIにも使います。
- `<名前>_connected.png`（128×128）：16×16のタイルを8×8に並べた接続用の画像。

| 名前 | ブロック | 描き方 |
| --- | --- | --- |
| `reinforced_casing` | 強化外装 | 炉の外板。銀色。ポートと制御部の側面も、この外板とつながる |
| `breeding_blanket` | 増殖ブランケット | 落ち着いた銀色の外板。銅の流路の模様はやめるか、ごく控えめにする |
| `vacuum_vessel` | 真空容器 | 落ち着いた銀色の外板。ブランケットと見分けられる程度に、色味か継ぎ目の向きを変える |
| `divertor` | ダイバータ | タングステンらしい、やや濃い銀灰色の板。色は付けない |
| `toroidal_field_coil` | トロイダル磁場コイル | 色を付けてよい。銅の巻き線が、隣り合うコイルと途切れずに続いて見える |
| `central_solenoid` | 中心ソレノイド | 色を付けてよい。銅の巻き線を、コイルとは違う向きか太さで描く |

接続用の画像で、どのマスにどの縁と角を描くかは、`tools/diorite_casing_ct_masks.json`に書いてあります。各マスの意味は次のとおりです。

- **index**：マスの番号。位置は`x`（列）と`y`（行）で、どちらも0〜7。
- **outer_frame**：枠を描く辺。`up`・`down`・`left`・`right`のうち、隣が同じ素材ではない辺。
- **inner_corners**：内側の角。隣り合う2辺は同じ素材につながっていて、その間の斜めのマスだけが違う角。
- **used: false**：使われないマス。単体のタイルで埋めてかまいません。

閃緑岩ケーシングの`tools/generate_diorite_casing.py`が、マスクから接続用画像を作る手本です。このやり方をそのまま使ってください。

- 中身（外板の地）は、どのマスでも同じ絵にする。並べたときに、境目で模様がずれないようにする。
- `outer_frame`の辺にだけ、縁（面取り・影）を描く。
- `inner_corners`の角に、小さな内角を描く。

### 正面だけ描き直すもの

ポート7種（`port_deuterium`・`port_tritium`・`port_ash`・`port_helium_in`・`port_helium_out`・`port_coolant_in`・`port_coolant_out`）と、制御部（`controller_off`・`controller_on`）が対象です。

- 正面だけに使う16×16の画像です。側面は`reinforced_casing`がつながって貼られます。
- ブロックごとの額縁はやめる。正面の周囲は、隣の外板と同じ地の色にして、境目に線が出ないようにする。
- ポートは、丸い口と流体ごとの色で見分けられるようにする。今の配色（`PORT_COLOURS`）を基準にしてよい。入口と出口の区別（矢印など）は残す。
- 制御部は、表示窓の消灯（off）と点灯（on）の差を残す。

### 変えないもの

`vacuum_pump`・`maintenance_hatch`・`console_*`・`motor_generator_*`・`plasma`は、この作業では変えないでください。

## パルス抽出塔のテクスチャ

置き場所は`src/main/resources/assets/create_refinery_n_reactors/textures/block/plant_systems/`です。

塔は12段あります。下2段と上2段が分離段、間の8段が脈動段で、脈動段のうち4段に生成物の出口（真鍮のノズル）が付きます。

各段は、ブロック1個の中心に置いた正八角柱のモデルです。

| 段 | 八角柱の幅 | 付属 |
| --- | --- | --- |
| 分離段 | 48ピクセル | なし |
| 脈動段・出口段 | 40ピクセル | 中央（高さ6〜10ピクセル）に幅48ピクセルのフランジ |

| 画像 | 大きさ | 貼られる場所 |
| --- | --- | --- |
| `extraction_column_settler_side.png` | 20×16 | 分離段の八角形の側面8面。1面に画像1枚全体を貼る |
| `extraction_column_pulse_side.png` | 20×16 | 脈動段・出口段の側面8面。1面に画像1枚全体を貼る |
| `extraction_column_flange.png` | 20×16 | フランジの側面8面。高さ6〜10ピクセルの帯なので、画像の縦6〜10行目だけが見える |
| `extraction_column_top.png` | 48×48 | 天面と底面。3×3ブロック分の正方形の画像から、八角形の形に切り取って貼られる |

### 描き方

- 段を縦に重ねても、上下の端が自然につながるようにする。
  - 脈動段の側面の上端と下端は、同じ高さに同じ継ぎ目を描く。
  - 分離段の側面は、塔の上下の太い部分らしく、補強の帯や覗き窓を描いてよい。
- 素材はステンレス鋼の明るい灰色。白い床（滑らかな石）に置いても輪郭が見えるように、継ぎ目と暗い面をはっきりさせる。
- 天面は八角形の外周に沿ってボルトの並ぶ縁を描き、中央はふたにする。八角形の外（四隅）は切り取られて見えないので、何を描いてもかまわない。
- フランジは暗い鋼で、ボルトの頭を等間隔に並べる。

## 読むファイル

- **生成元（正本）**：
  - `tools/generate_fusion_assets.py`：核融合炉。`reinforced`・`coil`・`solenoid`・`vessel`・`blanket`・`divertor`・`icon_face`の各関数と、その下の書き出し部分が対象。今は単体のタイルを並べた仮の`_connected`を書き出している。
  - `tools/generate_plant_systems.py`：抽出塔。先頭近くの`extraction_column_*`の4つの`texture(...)`が対象。
- **つながるテクスチャの手本**：`tools/generate_diorite_casing.py`、`tools/create_casing_ct.py`、`tools/diorite_casing_ct_masks.json`
- **色の手本**（読んでよいが、上書きや画像の流用はしない）：Createのjarの`assets/create/textures/block/`にある次の画像。
  - `andesite_casing.png`と`andesite_casing_connected.png`
  - `brass_casing.png`
  - `copper_casing.png`
- **今の画像（参考）**：上の2つのフォルダにある、対象の画像。

テクスチャは生成元で描き、スクリプトを実行して書き出してください。画像を手で直接編集しないでください。

## 生成元の実行について

この2つのスクリプトは、テクスチャのほかに翻訳・Ponder構造・モデル・レシピも書き出します。その中には、古い内容を書き戻すものがあります。

実行したら、`git status`で次の「変えてよい」範囲以外の変更をすべて`git checkout -- <パス>`で元に戻してください。

## 変えてよいもの・変えないもの

| 変えてよい | 変えない |
| --- | --- |
| 上に挙げた画像の絵柄 | 画像のファイル名と大きさ（16×16、128×128、20×16、48×48） |
| 生成元のうち、上に挙げた画像を描く関数 | Javaのコード、モデル（`models/`）、ブロック状態（`blockstates/`） |
| | 翻訳、Ponder構造、レシピ、タグ |
| | 「変えないもの」に挙げた核融合炉の画像 |

## テクスチャの規則

- 密度は1ピクセル＝1/16ブロック。
- 1枚あたり24色以下（接続用の128×128は、単体と同じ色だけを使う）。
- ざらつきは2ピクセル以上の塊で表し、1ピクセル単位のノイズで埋めない。
- 光源は左上。写真、写真から作った画像、画像生成AIの出力、なめらかなグラデーション、広い鏡面ハイライトは使わない。
- 金属は、明るい面・基本・暗い面・継ぎ目の3〜4段階にする。
- 文字、ロゴ、放射能などの実在の警告記号は描かない。

## 完了時に確認すること

1. `python3 tools/generate_fusion_assets.py`と`python3 tools/generate_plant_systems.py`が、エラーなく終わる。
2. 次のコマンドで、大きさと色数が規則に収まっている。

```bash
python3 -c "from PIL import Image
A='src/main/resources/assets/create_refinery_n_reactors/textures/block/'
names=['fusion/'+n+s for n in ['reinforced_casing','breeding_blanket','vacuum_vessel','divertor','toroidal_field_coil','central_solenoid'] for s in ('','_connected')]
names+=['fusion/port_'+n for n in ['deuterium','tritium','ash','helium_in','helium_out','coolant_in','coolant_out']]+['fusion/controller_off','fusion/controller_on']
names+=['plant_systems/extraction_column_'+n for n in ['settler_side','pulse_side','flange','top']]
for n in names:
    i=Image.open(A+n+'.png').convert('RGBA');print(n,i.size,len(set(i.get_flattened_data())))"
```

3. `git status`で、変更が次の範囲に収まっている。
   - `tools/generate_fusion_assets.py`と`tools/generate_plant_systems.py`
   - 上の確認コマンドに挙げた画像

変更したファイル、素材ごとの色数、接続したときに気になった点を短く報告してください。

## 最初に渡す指示文

```text
docs/specs/texture-brief-fusion-and-extraction-column.md を読んで、その指示どおりに核融合炉の外装・ポートとパルス抽出塔のテクスチャを描き直してください。指示書に挙げたファイル以外は読まないでください。
```
