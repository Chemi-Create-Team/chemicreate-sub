# チューブパイプドタンクによる2×2・3×3熱交換器

## 実装内容

- 鉄パイプ`iron_pipe`を追加。Createのプレスで鉄板（`c:plates/iron`）1枚から1本。
- チューブパイプドタンク`tube_piped_tank`を追加。どれかのタンク1個（タグ`create_refinery_n_reactors:tanks`：Createの液体タンク、横置きタンク、液体・冷却水・耐圧・ステンレス鋼・保冷タンク、ガスレシーバー）と鉄パイプ4本の不定形クラフトで1個。
- 2×2・3×3のシェル＆チューブ式熱交換器は、どちらもこのタンクで組むように変更し、旧外装`heat_exchanger`・`large_heat_exchanger`の登録・レシピ・ルート・モデル・ブロックステート・翻訳・タグを削除した。制御部（`heat_exchanger_controller`・`large_heat_exchanger_controller`）は残し、壊すとタンクを落とす。
- 最短の長さを断面に合わせた：2×2は2個、3×3は3個から（最長12）。能力は従来どおり（大型は長さ1あたり2単位）。
- 形成の判定（`ExchangerStructure`）
  - タンクを置くと、3×3を先に、次に2×2を試す。
  - 断面のまわりに、どの熱交換器にも属さないタンクが接していれば、より大きな装置の組み立て途中とみなして完成させない。このため、3×3を作る途中で2×2が先にできることはない。
  - 熱交換器に属するタンクは数えないので、熱交換器どうしは隣り合わせにできる。
  - タンクを置いたときに作り直す熱交換器は、そのタンクを含むものか、その並びの延長上にあるものだけにした。以前は近くの装置をすべて作り直しており、そのあいだタンクが一時的に未使用に見えて、隣の判定を妨げていた。
  - 完成した2×2のまわりに3×3になるまでタンクを足すと、2×2の制御部がタンクに戻り、1台の3×3として形成し直す。
- 位置の番号を「区分×13＋位置」に変更した。位置は2×2が0〜3、3×3が4〜12で、番号だけで大きさが分かる。水冷ジャケット管のモデル・ブロックステートもこの番号に合わせた。
- 見た目
  - Createの縦置き液体タンクのモデル・テクスチャ・つながるテクスチャ（`AllSpriteShifts.FLUID_TANK`）を、そのまま横に倒して使う。
  - 両端の面は新しい管板テクスチャ`tube_sheet`（単体）と`tube_sheet_connected`（Createのrectangle配置、64×64）。どちらも生成元で描き、Createの銅色をそのまま使う。
  - `TubeTankCTBehaviour`は、同じ完成済み熱交換器の隣接セル同士だけをつなぐ（判定は共通コードの`ExchangerStructure.joined`）。並べただけのタンクや、長さが足りない並びは1個ずつの見た目のまま。
  - 縦置きモデルを倒すと片方の端面が上下左右反転するため、その面だけテクスチャを180°回した。
- Ponder「シェル＆チューブ式熱交換器」「大型シェル＆チューブ式熱交換器」を、タンクの作り方、最短の長さ、完成するとつながること、という内容に更新した（両方ともチューブパイプドタンクの項目）。製造系統の空気分離・冷凍の構造も新しいタンクに置き換えた。
- 古いFable向けモデリング指示書`docs/specs/modeling-brief-heat-exchangers.md`は、対象のモデルがなくなったため削除した。

## 主要ファイル

- `block/ExchangerStructure.java`・`ExchangerCasingBlock.java`
- `client/TubeTankCTBehaviour.java`・`PlantTankModels.java`
- `registry/CEHeating.java`
- `tools/generate_heat_exchangers.py`（モデル・ブロックステート・管板と鉄パイプのテクスチャ・レシピ・タグ・ルート・Ponder・翻訳の正本）
- `tools/generate_refrigeration.py`（Ponder用の熱交換器の配置）
- `tools/generate_crafting_recipes.py`（旧外装のレシピを削除）

## 確認

- 新しいGameTest
  - 3×3を長さ2まで並べても、3×3にも2×2にもならないこと。3列目で3×3が完成し、6単位になること。
  - 完成した装置内のセル（制御部を含む）はテクスチャがつながり、隣り合う別の熱交換器や並べただけのタンクはつながらないこと。
  - 2×2を2台、ほかの装置の隣に並べて完成すること。
  - 2×2を3×3に広げると1台の3×3になり、27個すべてを取り込むこと。
  - プレスのレシピ、Createの液体タンクと耐圧タンクのどちらでもクラフトできること。
- 既存の熱交換器・熱機器・レシピ・到達性の試験は、ID変更に合わせて更新した。GameTest全体を実行し、失敗は`VesselFoulingGameTests.aFouledProcessStopsThenResumesAfterOneManualBrush`の1件だけだった。この試験は前回の作業時点から失敗しており、容器側の変更（1018e16）に関係するとみられる。この作業の対象外。生成スクリプトは再実行しても差分が出ないことを確認した。
- `validate_plant_tutorials.py`は合格。
- 画面の確認は、Xvfbとソフトウェア描画でPonderを撮影して行った。
  - `docs/quality/heating/tube-piped-tank-closeup.png`：2×2×3・3×3×3・並べただけのタンクを、確認用の一時配置で拡大したもの。管板が1枚につながり、外周の枠が1つだけになることを確認した。
  - `shell-and-tube-exchanger.png`・`large-shell-and-tube-exchanger.png`：各Ponder。
- 未確認：実際のプレイでの置き心地、多数設置時の描画負荷（つながるテクスチャはCreateのタンクと同じ仕組み）。

## 注意

- 旧ワールドの`heat_exchanger`・`large_heat_exchanger`の外装は消える（本格運用前のため互換処理なし）。
- 3×3から2×2へ縮める自動変換はない。タンクを外すと3×3は止まり、組み直しが必要。

## 名前空間の変更との統合

作業中に、他メンバーのコミット（8935487）でmod IDが`create_refinery_n_reactors`に変わった。このため、手で書いた変更（Java・生成スクリプト・文書）だけを最新のmainに適用し、アセットは生成スクリプトで新しい名前空間に出力し直した。出力は、名前空間とタブ名を除いて変更前の結果と一致する。統合後にもGameTest全体を実行し、結果は同じ（既知の汚れ試験1件だけが失敗）だった。Ponderの撮影でも、管板とタンクのテクスチャが新しい名前空間で読み込まれることを確認した。

## 追加：完成時の端のふたと対向流のPonder

- 完成した熱交換器の両端の面は、管板ではなく、管を覆うボルト締めのふた（チャンネル）を表示するようにした。並べただけのタンクは従来どおり管板。新しいテクスチャ`channel_head`・`channel_head_connected`（rectangle配置）と、モデル`tube_piped_tank_formed`を生成元で作り、外装は`formed`、制御部は常にふたのモデルを使う。`TubeTankCTBehaviour`は、完成した装置のセル（`ExchangerStructure.cell`がnullでないもの）の端面にふたの、それ以外に管板のつながるテクスチャを当てる。
- 2つのPonderの胴側ポンプを逆向きにして、管側と反対方向に流す対向流の配管にした。説明に「反対向きに流すと対向流になり、並流よりよく冷やし温める」を加えた。対向流の計算自体は既存（`docs/counterflow-heat-exchanger.md`）で変更していない。同文書のUAの記述を3×3（長さ1あたり2単位）に合わせた。
- 確認：熱交換器・対向流・熱機器のGameTest 29件が合格。`validate_plant_tutorials.py`も合格。Ponderの撮影で、完成した2×2・3×3の両端が1枚のふたにつながり、並べただけのタンクは管板のままであることを確認した（`docs/quality/heating/tube-piped-tank-closeup.png`、各Ponderの画像も更新）。クライアントでは139シーンすべてが開けた。
