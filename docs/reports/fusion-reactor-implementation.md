# 25×25 D-T核融合炉 実装完了レポート

## 実装コミット

- 実装コミット：`03debb9580d3a9cd7aa9940040aee015240bdb51`
- 実装日：2026-11-06
- 対象：Minecraft 1.21.1 / NeoForge 21.1.219 / Create 6.0.10 / Java 21

この文書自身を追加するコミットIDは、コミット前の文書へ自己参照として記録できないため、上記には機能本体のコミットを記載する。

## 実装範囲

添付されたトカマク断面図を、プラズマ、第一壁、ブランケット、ダイバータ、超電導磁石、熱交換系の位置関係を読む資料として使い、数値はITERとIAEAの一次資料から決めた。炉の内部はRBMK型ではなくD-Tトカマクとして構成し、RBMK時代の設備を思わせる操作感は独立した3×2ブロックの物理制御卓へ反映した。独自GUIは追加していない。

固定寸法は25×25×17ブロックで、次の要素を1 mグリッドへ縮約している。

| 要素 | 実装 |
| --- | --- |
| 外殻 | 強化安山岩クライオスタット。Createの安山岩ケーシングを基調に、ステンレス折板、暗色圧力フレーム、真鍮締結部を描いた専用テクスチャ |
| 超電導磁石 | 16本のD形トロイダル磁場コイルと中央ソレノイド。液体He 4.2 K、軸上5.3 T、最大11.8 T、磁場蓄積51 GJ |
| 炉内 | 二重壁トロイダル真空容器、リチウム鉛増殖ブランケット、下部タングステンダイバータ、描画されるトロイダルプラズマ |
| 真空 | 基底圧1×10⁻⁵ Pa。32 RPM以上で回す専用真空ポンプと燃焼時の燃料圧表示 |
| 定格 | 核融合熱500 MW、既定設定80パーセントで400 MW、プラズマ150 MK、Q=10として外部加熱50 MW |
| 冷却 | 200 °C設計の水を最大200,000 mB/t受け、250 °Cを設計上限として排出。フラッシュドラムから約216 °Cで戻る基準設備でも、5並列枝で80パーセント点の核融合熱と外部加熱を約246 °Cで連続除去できる |
| 燃料・排気 | D₂入口、T₂入口、He灰出口。入力と生成物は有限在庫で、出力口の閉塞時は消費しない |
| 極低温 | 4.2 K液体He入口、He蒸発ガス出口。液体1 mBから室温気体753 mBとして有限収支を取る |
| 増殖 | 交換式リチウム鉛ブランケットカートリッジ、T増殖比1.05、有限寿命 |
| 制御 | 真空、極低温、磁石、プラズマ、出力設定、保護付き緊急磁場ダンプを扱う3×2物理卓。Display Linkで炉1基と結ぶ |

構造内の固定部品数は、固定貫通口で置換する前の強化外殻2,238、TFコイル516、中央ソレノイド117、真空容器432、ブランケット480、ダイバータ336である。実機ITERの18本のTFコイルは、Minecraftの整数グリッド上で重なりを避け、内部を読み取れる16本へ縮約した。

## 調査とゲーム内対応

- ITERの核融合出力500 MWと約1.5億°Cは、IAEAの[Inside the ITER Machine](https://nucleus.iaea.org/sites/connect/FUSEpublic/SitePages/Inside-the-ITER-Machine.aspx)を基準にした。
- Nb₃Sn/NbTi磁石、約4 K、5.3 T、11.8 T、51 GJはITERの[Magnet System](https://www.iter.org/machine/magnets)を基準にした。
- 4 K級He冷凍系と4.2～4.5 K液体HeはITERの[Cryogenics](https://www.iter.org/machine/supporting-systems/cryogenics)を基準にした。
- 二重壁真空容器はITERの[Vacuum Vessel](https://www.iter.org/machine/vacuum-vessel)、基底圧は[ITER Vacuum Handbook](https://www.iter.org/sites/default/files/media/2025-01/itr-24-012-ok.pdf)を基準にした。
- ブランケット、ダイバータ、冷却、燃料供給はITERの[Components](https://www.iter.org/components)、[Divertor](https://www.iter.org/machine/divertor)、[Cooling Water System](https://www.iter.org/machine/supporting-systems/cooling-water)、[Fuelling](https://www.iter.org/machine/supporting-systems/fuelling)を基準にした。
- 少量の炉内燃料は条件喪失時に核融合を維持できないため、核分裂炉型の爆発は実装していない。異常時に扱う主要な蓄積エネルギーは磁場51 GJであり、プラズマ停止と磁場ダンプを行う。この考え方は[ITER FAQ](https://www.iter.org/faqs)に合わせた。

詳細な寸法、部品数、操作手順、物質・熱収支は`docs/FUSION_REACTOR.md`を正本とする。

## 計算方法

293.15 K、101,325 Paの理想気体として、D₂またはT₂ 1 mBの物質量を

`n = PV / RT = 4.158×10⁻⁵ mol`

とした。D₂/T₂は各分子から核を2個ずつ供給し、1反応17.6 MeVなので、D₂ 1 mBとT₂ 1 mBの組は約141.19 MJを発生する。この収支から80パーセントの400 MWではD₂とT₂をそれぞれ約0.14166 mB/t消費する。

水の熱容量を4.18 J/(mL·K)とすると、200→250 °Cの設計差では400 MWの核融合熱だけなら約95,694 mB/t、40 MWの外部加熱を含めると約105,263 mB/tを必要とする。実際の基準フラッシュドラムでは入口が約216 °Cで平衡し、5並列枝が運ぶ約174,000 mB/tなら440 MWを除去する出口は約246 °Cとなる。定格口の200→250 °C除熱能力は836 MWである。

He灰は標準状態で燃料片側体積の2倍、増殖T₂は消費T₂体積の1.05倍として蓄積する。液体Heは密度0.125 kg/L、293 KのHeガスは約0.000166 kg/Lとして753倍の膨張比を用いる。

## 操作・安全・保存

運転順序は、真空排気、液体He冷却、磁石充電、D₂/T₂・水・ブランケットの確認、プラズマ加熱である。制御卓は真空、極低温、磁石、プラズマ、出力±10パーセント、保護付き緊急ダンプの6面を持つ。

- 真空、極低温、磁場、D₂、T₂、ブランケット、冷却水、加熱電力のいずれかを失うと、そのtickで核融合出力をゼロにする。
- コイルが6.5 Kへ達するとクエンチを保持し、プラズマを停止して磁場エネルギーを熱とHe蒸発へ制御放出する。ブロック爆発は起こさない。
- 冷却水出口の満杯とブランケット650 Kでも燃焼を止める。
- 制御盤破壊時は保持流体と磁場を大気側へ放出し、ブランケットをドロップする。T₂放出は既存の低強度内部被ばく雲として扱う。
- 流体、温度、真空、磁場、出力設定、カートリッジ寿命、計算端数をNBTへ保存し、再読込後も収支を維持する。
- 形成・解体時に7流体ポートのCapabilityを無効化し、隣接Create配管へ更新を通知する。
- 未形成時の全探索を避け、構造は制御盤だけが40 tickごとに検証する。同期は20 tickごとに抑えた。

## 主要ファイル

- `src/main/java/net/createplant/fusion/FusionPhysics.java`：定格、燃料、熱、水、He、磁場のSI計算。
- `src/main/java/net/createplant/fusion/FusionStructure.java`：25×25×17の固定構造と貫通口、向き、部品ロール。
- `src/main/java/net/createplant/block/entity/FusionReactorControllerBlockEntity.java`：有限在庫、始動順、燃焼、停止、クエンチ、NBT保存。
- `src/main/java/net/createplant/block/entity/FusionControlConsoleBlockEntity.java`：3×2物理卓とDisplay Linkの対応。
- `src/main/java/net/createplant/compat/electro/FusionMagnetDevice.java`：Create Electro Energeticsの2端子負荷。磁石充電0.002 Ω、加熱0.0032 Ωとして400 V時80 MW/50 MWを扱う。
- `src/main/java/net/createplant/client/FusionReactorRenderer.java`：同期状態から描く発光トロイダルプラズマ。
- `src/main/java/net/createplant/ponder/FusionPonderScenes.java`：密閉外殻、内部カットモデル、配管、制御卓、運転手順を示すPonder。
- `src/main/java/net/createplant/gametest/FusionGameTests.java`：物理式、構造、連続運転、保存、異常停止、制御卓のGameTest。
- `src/main/java/net/createplant/registry/CEFusion.java`：ブロック、流体、バケツ、ブロックエンティティの登録。
- `tools/generate_fusion_assets.py`：モデル、テクスチャ、レシピ、loot、タグ、翻訳、Ponder構造、GameTestテンプレートの決定的生成。

## 検証結果

| 検証 | 結果 |
| --- | --- |
| `python3 tools/validate_ponder_language.py` | 成功。Ponder見出し89件を検証 |
| `python3 tools/validate_plant_tutorials.py` | 成功。追加シーン84件、対字幕404件、部品リンク121件を検証 |
| `python3 tools/validate_visual_identity.py` | 成功。完成品232件、同一画像群なし、バニラ本体バケツ7件、気体容器14件 |
| `python3 tools/test_ci_validators.py` | 11テストすべて成功 |
| `./gradlew --no-daemon --max-workers=2 build` | 成功。FluidPhase 25件、HaberBoschProcess 1,019件、Reactor control、ReactorPhysics 1,670件を含む |
| `runGameTestServer`を`FusionGameTests`へ限定 | 必須3件すべて成功、2.337秒 |
| クライアント品質検査 | `CLIENT QUALITY PASSED: 100 scenes, en_us`。RTX 4060で実炉カットモデル、稼働プラズマ、Ponder、物理卓を撮影 |
| `git diff --check` | 成功 |

GameTestでは正しい全構造を形成し、80パーセント・400 MWで12,000 tick（ゲーム内10分相当）を手動進行した。D₂/T₂消費、He灰、T増殖、水の循環、ブランケット寿命、温度、保存と再読込を確認した。その後、7 Kクエンチで爆発しないこと、コイル欠損で即停止すること、制御盤撤去時に在庫を無言で消失させないことを確認した。別テストで3×2制御卓の成立を確認した。

生成物は`build/libs/create-heavy-indusstry-0.1.0.jar`、4,073,807 bytes、SHA-256 `523f506bc6a8da5d9eb32138b3ee50d7ddcfd60badec8911adff2d164cec6edb`である。

クライアントは品質検査完了と正常なゲーム停止の後、環境側NVIDIA EGLライブラリ`libEGL_nvidia.so.0`の終了処理でSIGSEGVになりGradle終了コード134となった。品質検査と画像保存はその前に完了している。ログに残るCDGのloot、Electro Energeticsのモデル・音、ALSA、ナレーター、Mojang DNSの警告は既存または外部Mod・実行環境由来であり、今回追加した核融合炉アセットの欠落は出ていない。

## 未実装・未検証・制約

- D₂、T₂、液体Heの初期製造・液化設備は今回追加していない。登録済み流体とバケツは、初回試運転では外部設備またはクリエイティブ供給が必要になる。T増殖は始動後のT消費を補うだけで、無から初期装荷を作らない。
- Create Electro Energeticsの負荷デバイス登録とロードは確認したが、実際のCEE発電・送電網から50 MWを連続供給する端から端までのGameTestはない。運転GameTestは`supplyElectrical`試験フックから400 V・50 MWを注入した。
- 12,000 tickはサーバーの自動tickを実時間で10分待った試験ではなく、tick処理を手動進行した決定的シミュレーションである。数時間、複数炉、マルチプレイでの性能測定は未実施。
- Minecraftの1 m整数グリッドへ納めるため、18本の実機TFコイルを16本に縮約し、複雑なポート形状も固定貫通口へまとめた。
- プラズマMHD、詳細な中性子輸送、構造有限要素解析、配管の真空コンダクタンスは再現していない。運転インターロックと有限の物質・熱・磁場収支を、ゲームで操作可能な忠実度にまとめたモデルである。
- 稼働プラズマは撮影済みだが、全方位、全シェーダー、全描画距離での見た目は網羅していない。

## 次回の注意

- 初期燃料・液体Heの生産線を追加する場合も、既存の物性・相変化・反応プラントを優先し、炉だけの無限供給処理を追加しない。
- 電力条件を変更すると、Q、冷却水、連続運転点が連動する。`FusionPhysics`の値だけを変えず、式、Ponder、翻訳、GameTest、本文書の表を同時に更新する。
- 構造を変更すると、生成スクリプト、Ponder NBT、組立ガイド、GameTestの全構造、部品数の文書が連動する。
- クエンチを爆発へ置き換えない。D-T燃焼そのものは条件喪失で止まり、51 GJ磁場の制御放出が主な異常処理である。
