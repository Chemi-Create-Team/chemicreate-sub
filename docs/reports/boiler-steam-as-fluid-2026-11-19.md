# ボイラーの蒸気を通常の流体として扱う

実装コミットは `472fffa`。検証日は2026年11月19日。Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10、Java 21を使用した。

## 依頼と原因

- 依頼：ボイラーの上にポンプを置くと蒸気を取り出せるが、ポンプの先にパイプを付けると流れが止まる。蒸気を水と同じ流体として扱い、同じ処理の気体があればまとめて直し、扱う装置も対応させる。
- 原因：`BoilerSteamOutput` が、ボイラーに接するパイプのうち、気体パイプか高圧タービンへつながるパイプを「蒸気エンジン」として数えていた。横型タンクのボイラーは蒸気エンジンが付いたと判断して `engines` 状態になり、天面のポンプへ出す蒸気を止めていた。また、Createの縦型タンクのボイラーの蒸気は配管内を流れず、経路をたどってタービンへ直接渡していた。
- 元のコードで再現した（`BoilerSteamPumpGameTests.gasPipeOnAHorizontalBoilerCarriesItsSteam`）：天面のポンプから気体パイプをボイラーの上に這わせると、ボイラーが `engines` になり、受け側は0 mB。銅パイプがタービンへつながる場合も同じ判定になる。
- 同じ「配管をたどって直接渡す」処理を持つ気体はほかになかった。空気の吸気（気体パイプの開放端）やフレアの煙突判定は通常の配管流で、対象外。

## 実装

- `BoilerSteamOutput` を削除し、Create標準・ステンレス・樹脂・材質・気体の各パイプから外した。パイプはボイラーに接していても普通のパイプになる。
- `block/entity/TankBoiler.java`（新規）：Createの縦型液体タンクの制御ブロックが、水か温排水を底面のバーナーの熱で通常蒸気へ変える。蒸気エンジンか汽笛があるときはCreateのボイラーとして働き、蒸気を作らない。沸騰中は最上段の天面が蒸気の出口になり、ほかの面と非沸騰時の天面は通常のタンクの面。
- 熱の計算は横型タンクと同じ：底面の下の各熱源の加熱段階1あたり4,000 J/t、通常蒸気2,008 J/mB、水1 mB→蒸気1 mB、丸め残り最大400 kJ、蒸気バッファ16,000 mB。出力満杯なら水と熱を消費しない。旧処理にあったCreateの「ボイラーの大きさ」による熱の上限は使わない（横型と同じ規則にするため）。
- `GradeTankMixin`：`TankBoiler` を保持し、tick・保存・読込・天面の公開ハンドラー・ゴーグル表示へつないだ。`TankBoiler` は必要なタンクにだけ遅延生成する。熱源がないタンクは1秒に1回だけ下を確かめる。結合で制御ブロックが替わったとき、旧制御ブロックの蒸気を新しい制御ブロックへ移す。
- `GradeTankMixin` の `write`・`read` の注入位置を `TAIL` から `RETURN` へ変えた。Createのこの2メソッドはディスク保存・読込の経路で途中の `return` から抜けるため、`TAIL` ではクライアント向けパケットのときしか実行されず、タンクの加熱進捗（既存）もディスクに保存されていなかった。
- `TurbineFacility`：高圧→低圧の排気配管がボイラーに接していると不成立にしていた条件を削除した（旧処理がその配管へ蒸気を注入していたための制限）。
- `SteamTurbineBlockEntity`：最大回転数の定数を自分で持つようにした。
- 不要になった `FluidTankBlockEntityMixin`（旧処理のゴーグル行）と `FluidTankBlockEntityAccessor`、翻訳キー `goggle.create_refinery_n_reactors.steam_pushed`・`steam_pipe_outlets` を削除した。ゴーグルは横型タンクと同じ `tubular_boiler.*` の状態・内容表示を使う。
- Ponder「Createのタンクで蒸気を作る」（`create_boiler_steam`、Createの液体タンクに表示）を追加した。生成は `tools/generate_create_boiler_ponder.py`。
- 文書：`DESIGN.md`（入出力と配管）、`docs/horizontal-tank-boiler.md`、`docs/thermal-grades.md`、`INDUSTRIAL_SYSTEMS.md`。

## テスト

| 確認 | 結果 |
| --- | --- |
| `BoilerSteamPumpGameTests`（新規5件） | 全件合格。横型ボイラーの天面ポンプ＋ボイラー上を通る銅・気体パイプ、Createタンクボイラーの天面ポンプ＋天面を通る気体パイプ、蒸気の保存・読込、蒸気エンジン接続時の停止、非加熱時の天面がタンク面になることを確認 |
| `TurbineThermalGameTests` | 旧処理専用の3件を、Createタンクボイラー→天面ポンプ→銅パイプ→タービンの質量保存テスト1件に置き換えて合格 |
| 全GameTest（`runGameTestServer`） | 630件中8件失敗。変更前のコード（627件）でも同じ8件が失敗しており、今回の変更による新たな失敗はない |
| `./gradlew build` | 合格 |
| `validate_translations.py`、`validate_ponder_language.py`、`validate_plant_tutorials.py` | 合格 |

既存の失敗8件（変更前から）：`afouledprocessstopsthenresumesafteronemanualbrush`、`anativemechanicalpumpkeepsincompatiblegasatthesource`、`anativepumpreachesthesecondfeedandconservesgas`、`apumpfeedscompressedairfromatankintothecoldbox`、`apumplineontwocasingfaceslosesnosteam`、`everycurrentitemhasexactlyoneappropriatetab`、`everyregisteredentryhasassetstranslationsandloot`、`nonoccludingequipmentsurvivesflowingwater`。

## 未検証・制約

- 実クライアントでのPonderの見た目、ゴーグル表示、配管の流れの描画は確認していない。
- 古いワールドで旧処理の配管内部にたまっていた未送出の蒸気（`BoilerPendingSteam`、最大64,000 mB/本）は引き継がない。
- 蒸気エンジンも汽笛もない、水を入れたCreateのタンクを点火したバーナーの上に置くと、水を蒸気（最大16,000 mB）に変える。以前は温度等級が上がるだけだった。
- 蒸気を持つ制御ブロックを壊すと、その蒸気は失われる（放出の演出はない）。
- `tools/generate_ponder_language.py` を実行すると、今回と無関係なPonder（配管材質・気体配管・核融合保守・ウラン）の翻訳ページ分割も変わる。既存の翻訳ファイルが `ponder_lessons.json` と同期していなかったためで、今回は新しいシーンのキーだけを反映した。`docs/quality/ponder-rebuild/coverage.json` も同じ理由で再生成していない。

## 次回の注意

- タンク系のMixinでCreateのメソッドに注入するときは、途中の `return` の有無をバイトコードで確認する（`TAIL` は最後の `return` だけ）。
- ボイラーの蒸気を受ける装置は、標準の流体ハンドラーで受け取ればよい。配管をたどる独自処理を追加しない。
