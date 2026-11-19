# スライム冷却液の調製・分留

実装コミットは `6ba31f833ed0685dd4e0b61483bdfc84891825d6`。

## 工程

架空のスライム冷却液を作る工程を、既存の加熱撹拌槽と分留塔で構成した。バッチ量と進捗はゲーム内の設定。

| 工程 | 入力 | 出力 | 進捗 |
| --- | --- | --- | --- |
| 冷却原液の調製 | 水1,000 mB、スライムボール1個、グロウストーンダスト1個 | スライム冷却原液1,000 mB | 100 tick |
| 分留 | スライム冷却原液1,000 mB | 回収水250 mB、スライム冷却液750 mB | 80 tick |

撹拌槽の底面中央を32 RPM以上で駆動し、ふたを閉めて底面の隅をブレイズバーナーで加熱する。固体はファンネル、水は液体パイプで供給する。スマート液体パイプに原液入りバケツを指定して搬出する。

分留塔の最小構成は2×2・高さ2段。上段東面から原液を搬入し、上段北面から水、下段北面から冷却液を搬出する。回収水は通常の `minecraft:water` で、撹拌槽へ戻すか別の設備へ送れる。両方の出力に空きができてから処理が確定する。温まった冷却液はラジエーターで量を保ったまま冷やして、熱交換器へ戻せる。

## 主要な実装

- `recipe/pressure_reaction/prepare_slime_coolant.json` が原液を調製する。`chemistry/ProcessRecipe.java` と `thermal/FluidMaterials.java` が分留量と留分順を定義する。
- `registry/CEProcessFluids.java` が原液・冷却液・温まった冷却液を登録する。`thermal/ThermalStreams.java` の液体冷却材の組を熱交換器・ラジエーター・JEIが参照する。
- `tools/generate_manufacturing_ponder.py` と `tools/data/ponder_lessons.json` が日本語・英語・中国語のPonderを生成する。`python3 tools/generate_manufacturing_ponder.py manufacturing_slime_coolant` で個別に再生成できる。

## 検証

- Java 21、Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10でGradle `build` が成功した。
- 専用サーバーで `ReactorGameTests`、`ColumnConsolidationGameTests`、`ThermalControlGameTests`、`ManufacturingPonderGameTests`、`SurvivalReachabilityGameTests`、`CreativeCatalogGameTests`、`AuditGameTests` の45件が合格した。新規試験では加熱待ち、二つの留分の液量、熱交換後の冷却用変換を確認した。実ワールドで機械ポンプを介した新工程一式の通し運転は未確認。
- `validate_plant_tutorials.py`、`validate_ponder_language.py`、`validate_translations.py` が合格した。全体の外観検証は既存の `sulfur_scrubber_liquor_bucket.json` の画像参照に起因して失敗した。追加したバケツ3種のモデル参照と16×16の画像は個別に確認した。
- RTX 4060で動作する日本語クライアントで、冷却液入りバケツからPonderを開き、2段塔と日本語タイトルの描画を確認した。
