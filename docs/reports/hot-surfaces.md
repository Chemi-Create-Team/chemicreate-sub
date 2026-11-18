# 高温の配管・タンクに触れると火傷する レポート

## 基準と実装コミット

- 作業ブランチ：`main`
- 作業開始時の基準コミット：`556a96c`
- 実装コミット：`208cf7b`

## 依頼

- 高温の流体が入っているパイプとタンクに触れると、ダメージを受けるようにする。

## 実装内容

- `thermal/HotSurfaces.java`（新規）：
  - 生き物ごとに0.5秒（10 tick）に1回、当たり判定を0.05ブロック広げた範囲のブロックを調べる。いちばん熱い表面の温度でダメージを与える。
  - 調べる生き物は、ずらした間隔で順番に処理する。
- 表面の温度（`surfaceKelvin`）：
  - Createの液体タンクの一族（Create-Plantのタンク・横型流体タンクを含む）：コントローラーの中身の温度。
  - 開放円筒タンク：中身の温度。
  - 配管：劣化の仕組み（`PipeWearBehaviour`）が記録した、直近2秒に流れた流体の温度（`recentKelvin`）。Createの搬送とボイラーの蒸気の経路の両方が記録している。
  - 保冷配管・保冷タンク：0（真空の二重構造で外側が熱くならない）。
  - ガスホルダーは対象外。
- ダメージ：
  - 0.5秒ごとに、60 °C以上で1、100 °C以上で2、300 °C以上で4。
  - 新しいダメージの種類`create_heavy_indusstry:hot_surface`。`minecraft:is_fire`に入れたので、耐火で防げる。
  - 死亡メッセージは英語・日本語で追加した。
- 設定（`TransportConfig`の`[hot_surfaces]`）：
  - `enabled`（既定 true）
  - `burnCelsius`（既定60 °C、30〜1000）
- Ponder「流体に合うタンクを選ぶ」に手順5（火傷）を追加した。
- 文書：`docs/pipes-and-tanks.md`に「高温の配管・タンクに触れたとき」の節を追加した。

## テスト

| 確認 | 結果 |
| --- | --- |
| `bash gradlew build runGameTestServer` | 286件すべて合格 |
| CIの検証スクリプト | 合格 |

新規 `MaterialEquipmentGameTests.hotPipesAndTanksBurnWhatTouchesThemButTheCryogenicTankDoesNot`：

- 90 °Cの水を入れたCreateのタンクに接したブタは、ダメージを受ける。
- 20 °Cの水のタンクと、90 °Cの水の保冷タンクに接したブタは、ダメージを受けない。
- 90 °Cの水を流したCreateの配管は、表面温度が90 °Cになる。

## 未検証・制約

- ゲーム内でプレイヤーが実際に触れたときの見た目・音は試していない。
- 配管の温度は、流れの記録から求める。そのため、次の場合は熱くならない：
  - 配管の劣化の設定（`[chemical_transport]`の`enabled`、Createの配管なら`affectCreatePipes`）を切った場合。
  - 流れが止まって2秒たった場合。
- 低温（液体空気など）による凍傷は、今回は入れていない。
- 加熱器・反応槽などの設備そのものは対象外（配管とタンクだけ）。
