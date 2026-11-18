# 液体設置・耐食合金バケツ・硫酸炭化の実装報告

実施日: 2026-11-09

実装コミットID: `5dae60af39e6623d3cb5f58479d55af841bc4ad0`

## 実装内容

- 自Modの液体47種をバケツで設置・回収できるようにした。液源から流水が広がる。気体の既存の放出操作は維持する。
- ニッケル3個・クロム1個・モリブデン1個から耐食合金バケツ1個を作る。腐食性液体にはこの容器を要求し、通常バケツから無料で金属が変わることを防ぐ。クラフト・液体ハンドラー・ディスペンサーでも容器を維持する。
- 最新のユーザー指示に従い、設置した液体には温度情報を保存しない。回収すると、その採取場所の `PassiveHeat.ambient` の温度を設定する。温度情報を持つバケツ・タンク内の移送では情報を保持する。
- 硫酸1000 mBを木材へ直接使うか、硫酸液源を隣接させると、木材1個を炭化木材へ置換する。1液源を消費し、流水では開始しない。600 tick（ロード中のゲーム内時間30秒）で4段階に黒くなり、完成品を壊すと原木由来は木炭4個、板材由来は1個を落とす。
- 炭化途中の破壊では元の木材1個を返し、硫酸を返さない。進捗・元材は保存する。未ロード中に強制的に処理を進めない。
- 腐食性の酸・アルカリに接触した生物へ毎秒2 HPの防具貫通ダメージを与える。実際の液面との接触を判定し、同じ周期に複数マスから重複して加算しない。金属腐食区分にある塩水は、この接触熱傷の対象から除く。
- Createの開放パイプ・ホースプーリーからも有限量を汲める。独自液体を無限液源扱いせず、シミュレーション、異種液体の拒否、部分汲み取りの残量、温度を扱う。
- 操作と材質制限を説明するPonderを2シーン追加し、英語・日本語・中国語の翻訳を追加した。バケツはバニラの輪郭・取っ手・陰影を維持し、耐食容器の金属だけを控えめに区別した。

確定仕様は [world-liquids.md](../specs/world-liquids.md) に記録した。

## 主な実装箇所

| 対象 | 主なファイル |
| --- | --- |
| 液体・容器・能力登録 | `registry/CEWorldFluids.java`、`registry/PlantFluid.java` |
| バケツ操作・不変の保存形式 | `item/ProcessBucketItem.java`、`AlloyBucketItem.java`、`ProcessBucketWrapper.java`、`BucketFluidContents.java` |
| ワールドの液体と環境温度 | `block/WorldLiquidBlock.java`、`thermal/WorldLiquidTemperature.java` |
| 炭化の消費・進捗・ドロップ | `chemistry/WoodCarbonization.java`、`block/CharredWoodBlock.java`、`block/entity/CharredWoodBlockEntity.java` |
| 接触ダメージ | `safety/CorrosiveContact.java`、`tools/generate_corrosive_damage.py` |
| Createとの移送境界 | `mixin/WorldLiquid*Mixin.java`、`mixin/NaturalWaterPipeMixin.java` |
| アセットとPonder | `tools/generate_world_liquids.py`、`ponder/WorldLiquidsPonderScenes.java`、`tools/generate_ponder_language.py` |

既存の液体・気体ID、既存6種の液体バケツIDを維持した。旧容器専用クラス `ContainedFluidBucketItem` は置換して削除した。新しい温度保存コンポーネントは可変の `FluidStack` を直接保持せず、流体Holderとコンポーネント差分の不変レコードを保存する。既存の温度なしバケツも1000 mBの内容物として読める。

## 確認結果

- `build` 成功。既存の物性・制御・物理計算の検査を含む。
- 関連GameTest 71件が全件合格（1.541分）。対象は `WorldLiquidGameTests`、`WorldLiquidTransferGameTests`、`WoodCarbonizationGameTests`、`CorrosiveContactGameTests`、`CraftingRecipeGameTests`、`SurvivalReachabilityGameTests`、`PassiveHeatGameTests`、`ThermalGameTests`、`RefrigerationGameTests`、`VesselFoulingGameTests`。
- 最終レビュー後に保存Codec・実ディスペンサー・異種残液の試験を4件追加し、液体と移送の2クラス計12件を最新版で再実行して全件合格した（1.392秒、ビルド込み27秒）。通常水の準備手順をCreateの実採取動作に合わせて修正した。重複を除く関連GameTestの確認対象は75件。
- `validate_plant_tutorials.py`、`validate_ponder_language.py`、`validate_visual_identity.py`、`test_ci_validators.py` 合格。CI検証器自身の回帰試験は13件。通常容器と耐食容器の金属部分の不正な改変も検出する。
- RTX 4060の実クライアントで日本語Ponder 120シーンの生成・翻訳を確認し、新規2シーンを撮影して目視確認した。液面、容器、炭化完成品は欠落テクスチャなく表示された。
- 初回は全ディメンション保存後の終了処理で `libEGL_nvidia.so.0` のネイティブSIGSEGVが発生し、`runClient` は終了コード134で失敗した。`__GL_THREADED_OPTIMIZATIONS=0` を付けた再実行では、RTX 4060による描画、`CLIENT QUALITY PASSED`、画像・結果JSONの保存、ワールド保存、プロセスの正常終了まで成功した（55秒）。Modのコードや確認処理を省略して回避していない。

初回の試験で、可変のバケツ保存値、流水試験の開いた足場、到達性検査の未収録工程を検出して修正した。到達性検査には既存の相変化と洗浄廃液の生成条件を追加した。実際の加工レシピを変更して合格させたものではない。最終レビューで発見した異種液体がホース内部に残る場合の境界と、ディスペンサーの既存動作への委譲も修正した。通常の水・溶岩だけを扱うホースには新処理を適用しない。

作業開始時と完了前にリモート更新を確認した。初回に `ab92d034`、完了前に機械テクスチャ復元・デザイン規約更新の2コミットを含む `2800f42a` まで取り込んだ。いずれも競合せず、最新版でビルド・検証スクリプト・クライアント確認を行った。

## 制約・未検証事項と次回の注意

- Mod全体の長時間GameTestは実行していない。今回変更した登録、移送、炭化、危険性、温度、到達性に関係する試験を選んだ。
- 温度の破棄は仕様である。設置して回収すると冷却・加熱済みの液体も採取場所の常温になる。タンク・配管内部での温度保存と混同しないこと。
- 今回は設置液体の自然蒸発・凝固、濃度、防護装備、周囲の建材への広域腐食を追加していない。
- 原木・板材はタグに従い、竹・ネザー材・剥いだ原木も対象とする。他ModのBlockEntityを持つ木材は内容物消失を防ぐため対象外とする。
- 耐食バケツの材料となる金属には既存の酸処理工程がある。最初の酸は既存配管・タンクで製造でき、バケツを初回製造の前提にしない。
- Createの流体ハンドラーへのMixinは自Mod液体に範囲を限定する。Create更新時には内部バッファ、シミュレーション、実際の採取位置の環境温度、無限液源判定を再確認する。
- 既存GameTestワールドに以前の原子炉ブロックIDが残っているため、ロード時に旧IDの警告が出る。原子炉の実装や移行処理は今回の変更範囲外であり、修正していない。
- このGPUコンテナのクライアント確認には `__GL_THREADED_OPTIMIZATIONS=0 mc-debug ...` を使う。初回の終了時異常は `run/hs_err_pid3866.log` に記録した。環境変数なしの終了問題が恒久的に直ったとは扱わない。
