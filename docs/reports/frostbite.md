# 低温の凍傷（C3）レポート

## 基準と実装コミット

- 作業ブランチ：`main`
- 作業開始時の基準コミット：`6bce2e3`
- 実装コミット：`1c4c04a`

## 依頼

C3：液体空気など、とても冷たい配管・タンクに触れると凍傷を負う。火傷と同じ仕組みで作る。

## 実装内容

- `thermal/HotSurfaces.java`：
  - 0.5秒ごとに、生き物の当たり判定が触れているブロックのうち、いちばん熱いものといちばん冷たいものを見る（中身のない物は除く）。
  - 熱い方が火傷の温度（既定60 °C）以上なら火傷。そうでなく、冷たい方が凍傷の温度（既定 −40 °C）以下なら凍傷。
  - 凍傷のダメージ：−40 °C以下で1、−100 °C以下で2、−180 °C以下で4。
  - `LivingEntity.canFreeze()` が偽（革の防具を着ている、凍えない生き物）なら凍傷を受けない。
  - 保冷タンク・保冷配管は、火傷と同じく表面の温度を返さない（0）ので対象外。
- ダメージタイプ `create_heavy_indusstry:cold_surface`（効果 `freezing`、タグ `minecraft:is_freezing`）。死亡メッセージ（英・日・中）。
- 設定：`TransportConfig.COLD_SURFACE_CELSIUS`（`[hot_surfaces]` の `frostCelsius`、既定 −40、範囲 −273〜0）。
- Ponder「流体に合うタンクを選ぶ」の手順5に凍傷を追記した（`tools/generate_pipe_material_ponder.py` と翻訳）。
- 文書：`docs/pipes-and-tanks.md` に「とても冷たい配管・タンクに触れたとき（凍傷）」の節。

## テスト

| 確認 | 結果 |
| --- | --- |
| `bash gradlew build runGameTestServer` | 296件すべて合格（新規1件） |
| CIの検証スクリプト3本 | 合格 |

- `MaterialEquipmentGameTests.liquidAirFrostbitesWhatTouchesItsTankUnlessTheTankIsJacketedOrTheSkinLeather`：79 Kの液体空気が入ったCreateのタンクに触れたハスクは凍傷を負う。保冷タンクのもの、革の胸当てを着たものは負わない。

## 未検証・制約

- プレイヤーで試していない。画面の凍結の表示（粉雪の霜）は出さない。
- 熱い物と冷たい物の両方に触れていると、火傷だけを受ける。

## 次回の注意

- 表面の温度は `HotSurfaces.surfaceKelvin` が正本。新しいタンクや配管を足すときは、ここで温度を返すかを決める。
