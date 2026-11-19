# 既存GameTest失敗8件の解消

実装コミットは `f412082a2fc310507ba0f91b2c32aae7220dbbe0`。作業ベースは `4c1861c`（`main`、`69ab73b` に文書2件を加えたもの）。その後、`472fffa`（ボイラー蒸気の通常流体化）を含む `main`（`35acc52`）を統合した。

## 判断と変更

8件はいずれも変更前から失敗していた。DESIGN.md・AGENTS.md・各仕様書と照合し、コードが誤っているものはコードを、仕様変更に追随していない試験は試験を直した。

| 試験 | 原因 | 正しかった側 | 変更 |
| --- | --- | --- | --- |
| `aNativeMechanicalPumpKeepsIncompatibleGasAtTheSource`（GasBell） | 気体の供給元に耐圧タンクを使っていた。保管用液体タンクは気体を拒否する（DESIGN.md「液体タンクと脈動抑制管」、docs/wet-processing.md） | コード | 供給元を縦置きの脈動抑制管（`gas_receiver`）に変更 |
| `aNativePumpReachesTheSecondFeedAndConservesGas`（VesselBasin） | 同上（耐圧タンクに水素） | コード | 供給元を横置きの脈動抑制管に変更 |
| `aPumpFeedsCompressedAirFromATankIntoTheColdBox`（Distillation） | Create標準液体タンクに圧縮空気。充填量0 | コード | 供給元を横置きの脈動抑制管に変更 |
| `aPumpLineOnTwoCasingFacesLosesNoSteam`（TurbineThermal） | 標準液体タンクが蒸気を拒否（充填0）。供給元を直した後も1 tickで1000 mB減った。原因は試験の配管が既定状態（6方向接続）のまま置かれ、`(13,4,8)` の4面と `(13,6,8)` の上面がCreateの開放端になり、ポンプが外へ放出していたこと。ロータとケーシングの在庫は変わらず、タービン側の処理は正しい | コード | 供給元を縦置きの脈動抑制管にし、各配管を実際の接続面だけを持つ状態で設置 |
| `everyCurrentItemHasExactlyOneAppropriateTab`（CreativeCatalog） | 「UF6にバケツがない」ことを確認していた。docs/process-removal.mdで全気体にガス入りバケツを追加し、DESIGN.mdはガス入りバケツでクリエイティブタンクを設定する | コード | UF6の4種が共通の`GasBucketItem`であることの確認に置換 |
| `everyRegisteredEntryHasAssetsTranslationsAndLoot`（Audit） | 湿式処理のスラリー5種の液体ブロック `block.create_refinery_n_reactors.*_slurry` に翻訳がない | 試験 | `tools/generate_wet_processing.py` が液体と同じ名前でブロック名を書き出すよう修正し、`translations()` を再実行（3言語×5キー） |
| `nonOccludingEquipmentSurvivesFlowingWater`（WaterResistance） | 1×1×2のガス遠心分離機の下半分だけを置き、その上に水を置いていた。上半分がない下半分はドアと同じく消える（DESIGN.md「ガス遠心分離機は1×1×2」） | コード | `DOUBLE_BLOCK_HALF`を持つブロックは上半分も置き、水を上半分の北側に置く。上下両方の残存を確認 |
| `aFouledProcessStopsThenResumesAfterOneManualBrush`（VesselFouling） | 側面のX軸受けにモーターを付けていた。ふた付き槽の回転入力は底面中央1か所だけ（DESIGN.md 281行、`DriveLessVesselGameTests.sideMotorsCannotDriveTheVesselButTheBottomCentreCan`）。回転数0で再開しなかった | コード | モーターを底面中央の下に上向きで設置。前半の「実際に駆動されている」主張を回転数≠0で確認するようにした |

製品コード（Java）は変更していない。変更は試験7クラス、翻訳3ファイル、生成スクリプト1件、Ponder集計1件。

## Ponderの言語ページと集計

- `tools/generate_ponder_language.py` を実行した。lesson原文（`tools/data/ponder_lessons.json` と言語ファイルの原文キー）はすでに現行仕様だったが、表示される`lesson_pages`が旧文のままだった8レッスン（`guide.pipe_materials`、`guide.gas_pipes`、`guide.pipe_pressure`、`guide.uranium_preparation`、`fusion_maintenance.safe`、`guide.tubular_boiler`／`_2x2`／`_3x3`）を再分割した。旧ページには「高圧ガスはステンレス管」「フッ化物の気体はニッケル合金管」など、DESIGN.md「気体・蒸気は等級を保ったままどの配管でも運べます」と矛盾する文が残っていた。
- 生成器がキーを出し直すため、3言語ファイルのキー順も並び替わった。内容の変更は上記8レッスンと追加のスラリー5キーだけであることをJSON比較で確認した。
- `docs/quality/ponder-rebuild/coverage.json` を `python3 tools/validate_ponder_language.py --report` で再生成した。140→153シーン。撤去済みの `catalyst_basket`、`compact_centrifuge`、`electric_heater`、`plant_absorption_trays`、`plant_manufacturing_purified_water`、`water_quality` が消え、現行の19シーンが加わった。
- `docs/quality/ponder-rebuild/coverage.md` は手書きの改訂記録（140シーン、日本語題名付き）で生成器がないため変更していない。JSONとシーン数が一致しない。

## 確認

| 確認 | 結果 |
| --- | --- |
| 影響8クラスのGameTest（`AuditGameTests`、`CreativeCatalogGameTests`、`WaterResistanceGameTests`、`VesselFoulingGameTests`、`GasBellGameTests`、`VesselBasinGameTests`、`DistillationGameTests`、`TurbineThermalGameTests`） | 70件すべて合格 |
| 全体の `runGameTestServer`（実装コミット時点の作業ツリー） | 必須627件すべて合格 |
| 全体の `runGameTestServer`（`main` 統合後） | 必須630件すべて合格 |
| `validate_ponder_language.py`、`validate_translations.py`、`validate_plant_tutorials.py` | 合格 |
| `validate_visual_identity.py`、`test_ci_validators.py` | 失敗。作業前のベースでも同じ失敗（下記） |

クラス指定の実行には、スクラッチ領域の一時init scriptで `gameTestServer` の `createplant.gameTestClasses` と実行ディレクトリを設定した。製品のGradle設定は変更していない。

## 未確認・範囲外

- ゲーム内（クライアント）でのPonder表示は未確認。
- `validate_visual_identity.py` が `models/item/sulfur_scrubber_liquor_bucket.json` の `layer0` が `neutralized_wastewater_bucket` を指している点で失敗し、`test_ci_validators.py` の9件も同じ原因で失敗する。作業前から存在し、今回の範囲外のため未修正。
- `coverage.md` の更新は未実施（上記）。

## 次回の注意

- 気体を扱う試験の供給元には、保管用液体タンク（Create標準・耐圧・ステンレス・保冷・横置き）を使わず、脈動抑制管・ガスコンテナ・ガスホルダーを使う。
- 試験で配管を `setBlock` する場合、既定状態は全方向接続で、隣接更新がない面は開放端として流体を放出する。接続面を指定した状態で置く。
- `main` 統合時、3言語ファイルは生成物のため手で衝突を解かず、`main` 側を基に `generate_wet_processing.translations()` と `generate_ponder_language.py` を再実行した。`main` が削除した蒸気出口のゴーグル文言2キーは削除のまま、Ponderは154シーンになった（`coverage.json` も再生成）。
