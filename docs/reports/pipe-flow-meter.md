# 配管流量計 実装レポート

## 基準と実装コミット

- 作業ブランチ：`main`
- 作業開始時の基準コミット：`3165f21`（作業中に取り込んだ他メンバーの変更 `06b6174`・`a236233` とは、言語ファイルと採掘タグの末尾に両方が追記した部分だけが重なった。両方を残して解消した）
- 実装コミット：`3fd3fac1c1ba5041f018a1ddf79512e7be1e1b35`

## 実装内容

- 配管の途中に入れる「配管流量計」（`pipe_flow_meter`）を追加した。作り方は配管温度計・配管圧力計と同じ。
  - `PipeGaugeBlock` に種類（`Kind`：TEMPERATURE・PRESSURE・FLOW）を持たせた。BlockEntity型・描画・警報接点・ディスプレイリンクは3種類で共通。
  - 見た目は筐体・針・配管の接合部が共通で、文字盤だけが緑（淡い緑→濃い緑）。`faces.png`（16×16）の左下 [0,8,8,16] に追加した。
- 読み取り先（`PipeGaugeBlockEntity.observeFlow`）：
  - ポンプ送液：`ThermalRoutes.thermalContact` で、その送液量を加える。同じtickの送液は足し合わせ、2 tick保持する。
  - ガス配管：`GasNetworks.step` の圧力の均し（10 tickごと）で、各容器・配管の出入り量（`Node.moved`）を記録する。計器で区切った片側の増減の合計を、計器を通った量とし、10 tickで割った値を10 tick保持する。
  - ガス配管の探索は、新しい共通の `walk` にまとめた。均し全体の探索と、計器の片側の探索で同じ規則を使う。
- 流れが止まると0になる（温度計・圧力計のように「未測定」には戻らない）。針は0～1000 mB/tの対数目盛（`log10(1+r)/log10(1001)`）。
- 警報接点：しきい値は0～10 mB/tを1、～100を5、～1000を50 mB/t刻み（46段）。既定は0 mB/t超（流れがあれば出力）。
- ディスプレイリンク：未使用だった既存の項目 `flow_rate`（正味流量 mB/t）を流量計に割り当てた。新しい翻訳項目は追加していない。
- ゴーグル：名前・流量・警報の3行（翻訳キー `pipe_gauge.create_heavy_indusstry.flow`）。
- レシピ：コンパス＋Createの流体パイプ＋プロペラ。ルートテーブル、ツルハシのタグ、クリエイティブタブ、英日中の名前を追加した。
- Ponder（`plant/pipe_gauges`）：液体の配管の中央を流量計に置き換え、流量計の説明を1ステップ追加した。設置・停止時・ガス配管・ディスプレイリンクの説明に流量計を加えた。流量計からも開ける。生成元は `tools/generate_pipe_gauge_ponder.py`。
- `PipeGaugeClientCheck`：流量計を正面の列とホットバーに加えた。描画確認の終了後にtickが呼ばれると配列の範囲外になる既存の不具合を直した。

## テスト

| 確認 | 結果 |
| --- | --- |
| `bash gradlew build` | 成功 |
| `bash gradlew runGameTestServer` | 247件すべて成功 |
| 新規：`aFlowMeterReadsWhatThePumpMovesAndZeroOnceItStops` | 20 tick分の読みの合計が受け側タンクの増加量と10%以内で一致。ディスプレイリンクで流量だけを読める。停止後は0 |
| 新規：`aFlowMeterInAGasPipeReadsTheGasThatCrossesIt` | 300 tick分の読みの合計が、計器の先へ移った気体の量と10%以内で一致。均された後は0 |
| `validate_plant_tutorials.py`・`validate_ponder_language.py`・`validate_visual_identity.py`・`test_ci_validators.py` | すべて成功（`texture-audit-after.json` の完成品数を更新） |
| 実画面 | 正面・斜め・ホットバーで、3種の計器が文字盤の色で見分けられることを確認。Ponder（日本語）の全77シーンの品質チェックに合格し、流量計のステップを撮影した |

## 未検証・制約

- 送液の経路が合流・分岐していて一つに決められない場合、既存の温度計と同じく記録しない。分岐の先に置いた流量計は0のまま。
- 向きは区別しない（ガス配管で行き来がある場合も、10 tickごとの正味の量の大きさ）。
- 「-PwithJei=false」でのGameTestと専用サーバーの起動は、今回は実行していない（共通処理からクライアント専用クラスへの参照は追加していない）。
- 大量設置時の性能は計測していない。ガス配管では流量計1台ごとに片側の探索を10 tickに1回追加で行う。

## 次回の注意

- 新しい種類の計器を足すときは、`PipeGaugeBlock.Kind` と `PipeGaugeBlockEntity` の `switch`（しきい値・表示・針・同期の閾値）、`ThermalRoutes` と `GasNetworks` の観測をそろえる。
