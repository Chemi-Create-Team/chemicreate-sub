# 湿式浸出設備・液体保管制限の実装報告（2026-11-17）

実装コミット：`e514792440e0a7148a51851260a6d659e81ae0d8`

## 対象と確認した仕様

- 吸収塔（小）、超耐圧パイプ、超耐圧ポンプを撤去。
- 標準液体配管の等級はユーザー回答「Create標準だけ」に従い、銅管を常温〜高温・常圧へ。他材質の等級は維持。
- 気体制限はユーザー回答「保管タンクに限定」に従う。ステンレス・耐圧・保冷・横置き・Create標準の液体保管では拒否し、脈動抑制管・ガスコンテナ・ガスホルダーと工程専用気体入口を維持。クリエイティブ液体タンクも配管搬入／バケツ選択で気体を拒否。
- 添付の[湿式浸出・沈降仕様](../specs/wet-leaching-and-clarification.md)を実装。ID案`mhic:`は実際のmod IDへ読み替え。

## 実装

[操作・接続・計算の正本](../wet-processing.md)に詳細を記載。

大型浸出攪拌槽は3×3×5、5×5×7、7×7×9。耐酸鋼の底・外殻、強化窓、上部駆動、底のスパージャー、酸液入口、鉱石入口、スラリー出口を組み合わせる。角を面取りして円筒状にし、槽壁は手組みでも自動で形状を整える。上面のCreate軸で最低32 RPM、推奨64〜128 RPM、16×RPM SU。停止・低速が60秒続くとスラリーが固結して搬出停止。64 RPM以上で10秒、またはレンチ整備で復帰。

円形沈殿池は7×7×3、9×9×3、11×11×3。中央底面から搬入し、外周上部から上澄み、底から泥を搬出する。1〜4 RPMで清澄化、4 RPM超〜17 RPM未満で不純物混入、17 RPM以上は同じスラリーを同量で越流させる。0 RPMでも沈降するが泥を取り出せず、64個で詰まる。応力8×RPM SU。凝集剤1個で1バッチを200 tickから100 tickに短縮。

鉄・銅・亜鉛・錫の砕いた鉱石3個＋希硫酸750 mB＋圧搾空気／酸素250 mBからスラリー4000 mB。沈降4バッチで精製液3000 mB＋泥8個、既存電解で金属9個＋再生酸750 mB。金は対象外。廃水から凝集懸濁廃水を作り、水と尾鉱泥へ分離する工程も追加。泥は標準プレスで粘土・砂、加熱ミキサーで石英も回収でき、凝集剤はポリエチレン・スライム・水から作る。

スラリー5種類は密度1400・粘度3500・流動更新20 tick・拡散距離2。不透明テクスチャと移動抵抗を持ち、全種類のバケツ・翻訳を登録。不純物は`suspended_impurity`（0〜1000）で保存・同期。精製液同士は量で加重平均し、電解の仕事を通常×(1＋不純物/1000)、最大2倍へ増やす。金属と酸の量は維持する。最大仕事を充電できるよう、電解バッファ最終部分の充電停止条件も修正。

新機械は既存の施設建設機へ設備とサイズ選択を追加し、資材一覧・建設・実モデルの半透明表示を共用。JEIに2種類のJSONレシピ、Ponderに全工程と高速時の巻き上げ、標準マニュアルに新ルートを追加。ゴーグルは4行、詳細はディスプレイリンク。インペラ・邪魔板・気泡、中央筒・回転トラス・スクレーパー、液面と二層表現、既存機械音を実装。

## 主要ファイル

Javaのパスは`src/main/java/net/createplant/`以下。

- `registry/CEWetProcessing.java`：構成部材、駆動部、流体・アイテム、Capability。
- `block/WetProcessStructure.java`：検証・施設建設機で共有する有限の配置定義。
- `block/entity/WetProcessBlockEntity.java`：処理、回転数、固結、有限在庫、保存、世代付き搬送ハンドラー。
- `event/WetProcessChanges.java`：内部の空間を含めた変更位置の索引。形成済み設備を毎tick全探索しない。
- `recipe/LeachingRecipe.java`、`ClarifyingRecipe.java`、`data/.../recipe/leaching/`、`clarifying/`：JSON工程。
- `client/WetProcessRenderer.java`、`tools/generate_wet_processing.py`：モデル・16px素材・アニメーション・教材生成。
- `thermal/TankGrade.java`、`transport/PipeMaterial.java`、`mixin/CreativeLiquidTankMixin.java`：保管・配管等級。
- `thermal/FluidThermal.java`、`block/entity/ElectrolysisControllerBlockEntity.java`：不純物の混合と電解仕事。
- `assembly/AssemblyPlan.java`、施設建設機のBlockEntity・Menu・Screen：選択と建設。

原料・生成物は駆動部だけが所有し、入力・出力・容量・シミュレーションを確認してからバッチを確定する。構造を壊すと既存のハンドラーを無効化し、チャンクを勝手にロードしない。壁の撤去だけでは在庫は駆動部に残る。駆動部回収時は中身・進捗・固結をブロックアイテムに保持し、通常のBlockItem配置で復元する。

発電用の旧主管はステンレス鋼管へ置き換え、既存の512流量係数をステンレス鋼管の通常・ガラス・被覆形態で維持。旧型を参照する教材・生成元・テストも更新。大型吸収塔は残し、共用していたBlockEntity保存IDは維持する。

実画面でJEIのCreateプラグインが成分未設定の混合液サンプルを噴霧器へ問い合わせ、例外で登録を中断する既存問題を確認した。流体フィルター互換性として無効なサンプルを拒否し、有効な混合液の扱いは維持。修正後の起動ではこの例外が出ないことを確認。

## 検証

- Java 21、Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10（固定依存UjX6dr61）。
- `bash gradlew --no-daemon clean build`：成功。配布用Jarの再生成と中身の監査も成功、旧小型吸収塔・超耐圧配管／ポンプのアセットなし、浸出／沈降各5レシピと翻訳を同梱。Jarは8 MiB未満。
- 専用GameTestServer：最終 **96/96件成功**。17クラスを対象に、新設備、全寸法・向き、固結と実Create動力、RPM別の出力、凝集剤、保存・回収・再配置、古いハンドラー無効化、不純物と実電解仕事、タンク／配管／ポンプ、既存施設建設機、サバイバル到達性、レシピ競合、教材を確認。
- `validate_translations.py`：5018キー、3言語の対応と書式を確認。
- `validate_ponder_language.py`：149見出し、3言語、ページ当たり最大2文を確認。
- `validate_plant_tutorials.py`：94教材・27製造系列、構成部材とターゲットを確認。
- `mc-debug glxinfo -B`：NVIDIA RTX 4060の直接描画、OpenGL 4.6 / NVIDIA 595.99.02。
- 実クライアント：日本語Ponderの2設備を描画し、実配置定義との一致、欠落モデルがないこと、各構成部材からのクライアント側ゴーグル参照先を検証。`CLIENT QUALITY PASSED`を記録。JEIの噴霧器由来の例外も再発しない。
- 施設建設機の実GUI：18ステップで従来3設備と新2設備の切替、幅・高さ・サイズと選択保持を確認。`FACILITY CONSTRUCTOR CLIENT CHECK PASSED`を記録。
- スクリーンショットはMCPの実画面キャプチャで`wet-processing-preview.png`、`wet-facility-menu.png`をoutputsへ保存。自動スクリーンショットは無効化。再現用のGradle設定とJSON結果は[検証記録](../quality/wet-processing/)に保存。
- `validate_visual_identity.py`は後述の既存8項目で停止。GPU終了時の既知問題と音声未確認も後述。正常な`runClient`終了まで確認済みとはしない。

## 制約・引き継ぎ

- 削除した小型吸収塔・超耐圧配管／ポンプ、旧`ore_sludge`は旧登録を残していない。更新前の回収・整理が必要。旧直接浸出は二段階工程に置き換えた。大型吸収塔の保存IDは維持。
- 新設備の寸法は上記のプリセット。浸出槽の高さは幅に対応し、任意の幅・高さを独立設定する画面ではない。
- 尾鉱はアイテム方式。不純物コンポーネントは精製液の清澄度であり、汎用混合流体の絶対mB成分を置き換えない。スラリーの比率はJSONの1000→750 mB＋泥2個を用いる。
- 複数人接続、長時間の大量設備負荷、実際のチャンクアンロードを繰り返す試験は未実施。TPSや描画性能の改善を計測したという主張はしない。
- `validate_visual_identity.py`は以前からの未分類8項目（high_pressure_turbine_unit、iron_pipe、irradiated_cartridge、irradiation_cartridge、jacketed_pipe、low_pressure_turbine_unit、pig_iron、steel_slag）で停止。今回の尾鉱泥・凝集剤は分類済み、旧鉱滓項目は撤去済み。
- 依存MODの既知警告として、CDGのandesite_girder_strutのloot登録、CEEのduplex_wire_segment／altitude_sensorのモデル・テクスチャ不足が起動ログに残る。今回追加した設備のモデル欠落とは区別する。
- GPUの起動・稼働・Ponder確認後に、ワールド保存は完了するが、終了処理の`libEGL_nvidia.so.0`で既知のSIGSEGVが再発する。Gradleの`runClient`終了コードを正常終了とは報告しない。音声デバイスがないため、効果音の聴取は未確認。

配布Jar：`create-refinery-n-reactors-0.1.0-wet-processing.jar`（7023025 bytes）、SHA-256：`e197d9dbfb146c4cd9acb3b058fda800a6f16f27f5c1107b2130d48c1c84f5de`。
