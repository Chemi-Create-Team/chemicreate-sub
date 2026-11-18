# 追加機能の監査（CP-AUD）への対応 レポート

## 基準と実装コミット

- 作業ブランチ：`main`
- 対応した監査：`docs/reports/chat-feature-overhaul-audit.md`（Codex、`d991ed7`）
- 作業開始時の基準コミット：`d991ed7`
- 実装コミット：
  - `0b549ca`：吸収塔の解体と安全弁スクラバー（CP-AUD-002〜007）
  - `4fb8852`：AOD精錬（CP-AUD-001）
  - `6dfcabc`：石綿隔膜（CP-AUD-008）
  - `5e9a278`：翻訳とラジエーター試験（CP-AUD-009〜011）

## 対応内容

| ID | 対応 |
| --- | --- |
| CP-AUD-001 | 原因は監査の見立て（レシピの並び順）より前にあった。この MOD では Create の開いた鉢は気体を受け付けない（`BasinGases`）。そのため、アルゴンを使うミキサーのレシピは、材料が鉢に入らず一度も動けなかった。AOD 精錬を、気体を扱える撹拌反応槽（強く加熱）のレシピ `pressure_reaction/stainless_steel_ingot_aod.json` に移した。通常の 10 個のレシピは、鉢とミキサーのまま残した。並び順を変える Mixin は要らなくなったので入れていない |
| CP-AUD-002 | 吸収塔のどの外殻を壊しても、所有ブロックへすぐ知らせる（`structureChanged`）。知らせを受けた時点で処理と 4 つのポートを止め、次の tick で構造を確かめ直す |
| CP-AUD-003 | 内容物の扱いを `dismantle()` 1 か所にまとめた。塔が崩れただけなら、内容物は所有ブロックに残す（直せば再開する）。所有ブロックそのものを壊したとき、成立中でも崩れた後でも、中身を一度だけ外へ出す |
| CP-AUD-004 | 外へ出すときに `ToxicGas.release` を通す。二酸化窒素と一酸化窒素は有毒ガスの雲になる |
| CP-AUD-005 | 廃止した足場と冠部の手すりの当たり判定を消した。冠部は、モデル（48 px の段々）をブロックごとに切り分けた形にした。古いワールドに残る `part` 18〜26 は、壁と同じ形として読む |
| CP-AUD-006 | スクラバーの上面が固体や流体で塞がっていると、安全弁は大気側へ開かない（ガスも苛性ソーダも減らない）。ゴーグルに「上面が塞がっている」と出る |
| CP-AUD-007 | 一部しか中和できず残りを大気へ出したときは、経路を `partial_scrubbing`（中和しきれず、残りを大気へ放出中）にする。英・日・中で翻訳した |
| CP-AUD-008 | 隔膜の使いかけの量を、石綿のアイテムデータ `diaphragm_wear` に持たせた。苛性ソーダの 4 分の 1 の端数も、この量から計算する。列を組み直しても、移しても、再読み込みしても変わらない。石綿が複数あるときは、いちばん使い込んだものから使う |
| CP-AUD-009 | 吸収塔の表示・JEI・Ponder「硝酸を作って使う」の中国語 19 キーを、生成スクリプト（`generate_uranium_assets.py`）から足した。`validate_ponder_language.py` に、中国語で訳した Ponder は全文がそろっていることの確認を足した（これで、見出しの抜けていた「蒸汽涡轮多方块」も見つかり、直した） |
| CP-AUD-010 | 核融合の 4 流体の `block.*` と `fluid_type.*` を、`generate_fusion_assets.py` で流体名から作るようにした |
| CP-AUD-011 | ラジエーターの 3 つの試験の固定値（8,000・1,000・160,000）を、`CONDENSER_BUFFER` と物性から決めるようにした。765 MW の実装は変えていない |
| CP-AUD-012 | 対応していない（下記） |

### 監査の外で見つけて直したもの

- 流れる水が、安全弁とスクラバーを松明のように壊していた（ブロックの形が満たないため「固くない」と扱われていた）。2 つに `forceSolidOn` を付けた。

### 監査の記述と違ったこと

- CP-AUD-009 の「中国語環境ではキー文字列が表示される」は誤り。Minecraft は、無いキーを英語で表示する。ただし、中国語の訳は MOD 全体で 1,720 キー中 460 キーしかない。今回は監査の範囲（吸収塔）と、Ponder が中途半端に訳されていないことの確認に留めた。

## テスト

| 確認 | 結果 |
| --- | --- |
| 対象の GameTest（クラス別） | `NitricAcidGameTests`・`ToxicGasGameTests` 15 件、`BasinCompatibilityGameTests`・`ReactorGameTests`・`CraftingRecipeGameTests` 28 件、`ChlorAlkaliGameTests` 5 件、`RefrigerationGameTests`・`RadiatorTemperatureGameTests`・`AuditGameTests` 11 件。すべて合格 |
| `bash gradlew build runGameTestServer`（全件） | 332件すべて合格 |
| CI の検証スクリプト 4 本 | 合格 |

追加した GameTest：
- `breakingAWallStopsTheBatchAtOnceAndKeepsTheStreamsUntilTheOwnerGoes`：完了直前のバッチが、壁を壊した瞬間に止まり、20 tick 後も増減しない。そのあと元の所有ブロックを壊すと、二酸化窒素の雲がちょうど 1 つできる。
- `breakingAFormedOwnerLetsItsGasesOutOnce`：所有ブロックとほかの部品を続けて壊しても、雲は 1 つだけ。
- 吸収塔の全部品の当たり判定が、モデルの梯子・配管・ノズルの範囲に収まる（既存の試験を書き換えた）。
- `aCoveredScrubberKeepsTheReliefValveShut`：石と水で塞いだ 2 つのスクラバーでは、弁が `blocked` のまま。苛性ソーダは減らず、雲もできず、水で弁が流されない。
- 苛性ソーダが足りない試験で、弁が「中和中」と表示しないこと。
- `noBasinRecipeAsksForAGas`：Create の鉢のレシピ（ミキサー・圧縮）が気体を要求しない。
- `theDiaphragmKeepsItsWearWhenTheLineGetsANewLowEnd`：9,998 mB を流したあと列の先頭を壊し、残り 2 mB で石綿がちょうど 1 個なくなる。苛性ソーダは合計 12,500 mB。

## 未検証・制約

- CP-AUD-012（硝酸プラントを実配管・CEE 電力・実時間で通す統合試験）は作っていない。不具合ではなく試験の範囲の提案であり、低速の別試験として設計が要るため、別の作業にする。
- 実クライアントでの確認はしていない。吸収塔の周りを歩く、ブロックを置く、選択枠を見る、の 3 つは未確認。
- 中国語の訳の不足（約 1,260 キー）は残っている。
- 安全弁・スクラバー以外にも、`noOcclusion` で形が満たない設備（51 か所の登録）は、水で壊れる可能性がある。調べていない。
- JEI なしの構成（`-PwithJei=false`）は今回は流していない。
