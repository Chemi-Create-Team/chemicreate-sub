# 吸収塔：ポート面の限定・ゴーグル表示・名称（2026-11-21）

実装コミット：`cdf2a30`

## 実装内容

- 名称：「大型吸収塔」を「吸収塔」に変更（ja/en/zh の表示名、Ponder、ガイド文）。登録ID `large_absorption_tower` / `large_absorption_tower_ring` と保存形式は変更なし。
- ポート（`AbsorptionTowerBlockEntity.storeyPort`）：
  - 最下段の平らな4面（各辺中央の外周ブロックの外向き面）：気体の搬入と液体の搬出。
  - 最上段の平らな4面：未吸収ガスの搬出のみ。
  - 最上段中心の天面：水・洗浄液の搬入のみ。
  - 斜めの面（角の外周ブロック）、中心ブロックの側面、中間段、底面、その他の天面：ポートなし。
- 外周ブロックの capability は中心の段の `storeyPort(外周の位置, 面)` を直接呼ぶ（`CEReactor`）。`StackedReactorBlockEntity.storeyPort` は呼び出し元ブロックの位置を受け取るよう変更。
- レンチ表示（`ProcessPorts.at`）：capability があるポートだけに枠が出る。最上段の平らな面は出口（橙）、天面は入口（青）。最下段は従来どおり内容物に応じて判定。
- ゴーグル表示：
  - 上の段は最下段（制御部）の表示を出す。外周ブロック（`ColumnRingBlockEntity`）も中心の段へ委譲する。従来は外周で何も出ず、上の段では未形成の状態が出ていた。
  - 吸収塔では撹拌槽向けの「速度倍率・応力」「回転は側面か底面中央から」「圧力容器」の行を出さず、吸収塔の段数・液体/気体のバッファ容量とポート位置を表示（`ReactorMachineBlockEntity.structureTooltip` を追加し、吸収塔で上書き）。
  - 在庫行の上限値を、気体は実際の容量（8000×64 mB）で表示するよう修正（全縦型装置共通）。
- Ponder：既存の配管配置がすでに新仕様どおりだったため構造はそのまま。字幕 `storey` / `same` / `ring` を新仕様に更新（「どのブロックにもつなげる」「小型と同じ」を削除）。
- 文書：`docs/reaction-plant.md`、`docs/toxic-gas.md` を新仕様に更新。

## テスト

- `compileJava`：成功。
- 一時的な GameTest 実行（`AbsorptionTowerGameTests, SyntheticFuelsGameTests, ProcessGasRecoveryGameTests, WaterTreatmentGameTests, ReactorGameTests, NitricPlantIntegrationGameTests, NitricAcidGameTests`）：46件すべて成功。一時実行設定は `build.gradle` に残していない。
- 吸収塔テストに、斜めの面・中心側面・外周の天面にポートがないこと、最上段の側面が水を受け付けないことの確認を追加。

## 未検証事項

- ゲーム画面でのレンチ枠・ゴーグル表示・Ponderの見た目は未確認（GUI環境なし）。
- `tools/validate_ponder_language.py` は変更前から `horizontal_tank.stainless_title.lesson_pages.count` の欠落で失敗する（本変更とは無関係）。

## 注意

- ユーザー指示の「ガスの搬入も最上段の4つの面から」は、最上段からガスを出す（排ガスの搬出）と解釈した。最上段の面からガスを入れる仕様が必要なら `storeyPort` の最上段分岐を変更する。
- `create_refinery_n_reactors.ponder.shared.absorption_tower.*`（小型の段・側面からの水など）は Java から参照されていない旧キーで、内容も古い。今回は未変更。
- 構造の形成・解体時に `invalidateCapabilities` を呼んでいない点は既存のまま（Create配管のキャッシュ更新は隣接更新に依存）。
