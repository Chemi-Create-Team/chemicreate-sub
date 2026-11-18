# ふた付き槽の直接回転入力・積み上げ・上面テクスチャ 引き継ぎレポート

- 作業ベース：`0ebd83a7`（作業開始前に最新の `main` へ更新）。
- 実装コミット：`742ae05c33c2f8f7872ffc8bd5637f1951ecbe09`。
- 対象：ふた付き槽、撹拌反応槽、触媒充填槽、反応生成物分離塔、ガス遠心分離機。
- 環境：Java 21、Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10。

## 結果と要求への対応

4種類の独立した駆動部を廃止し、ふた付き槽そのものにCreateの回転動力を入れて工程を動かす構造にした。既存の最新ベースに含まれていた側面のはしご、槽アイテムを持ったときのハッチ抑止、同サイズの段を一括設置する操作も、新しいブロックエンティティで維持・確認した。

| 要求 | 最終的な挙動 |
| --- | --- |
| 側面1面にはしご | 蝶番と反対側の外壁に、上下に連続するはしごを1列だけ付ける。3×3では面の中央、2×2では中央に隣接する片方の列。実際に登れる。 |
| 槽を持って設置するとハッチが開く問題 | 主手・副手のどちらかにふた付き槽があれば、素手側のフォールバック操作でもハッチを開かない。両手が空なら従来どおり開閉できる。 |
| 2×2・3×3の上への一括積み上げ | 既存の幅に合わせ、1段分の不足セルをまとめて設置する。下面への増設も同様。個数不足や設置不能セルがあれば、最初の1個も置かずアイテムも使わない。 |
| 4種類の駆動部を削除 | 本体・アイテム・クラフト・モデル等の登録を削除。槽に入れた内部部品が装置の種類を決める。 |
| 側面か下から回転入力 | 外側面または最下段の下面のシャフトをCreateの回転ネットワークにつなぐ。上面とはしご部分、八角槽の斜めの角壁には水平シャフトを設けない。 |
| 2×2・3×3の上面がつながる外観 | 添付画像に合わせて、外周枠とハッチ周りの補強縁を大きな1枚の天板として描く。中央の1マス分のハッチは開閉する。 |

一括設置を避けるときはスニークする。側面への通常設置と1×1の槽は単体配置のまま。Createの対称ワンドをホットバーに持つ場合は、ワンド側の操作と重複しないよう一括配置を行わない。

## 構造と操作

槽は幅1～3マス、高さ1～4段で連結する。工程装置として使うのは2×2か3×3。最下段の北西のブロックが槽と工程のコントローラーになり、各段に同じ種類の内部部品を1個ずつ入れる。

| 装置 | 内部部品のID | 有効段数 | 1段あたりの応力係数 |
| --- | --- | --- | --- |
| 撹拌反応槽 | `reactor_impeller` | 1～4 | 16 SU/RPM |
| 触媒充填槽 | `reactor_catalyst_basket` | 1～4 | 16 SU/RPM |
| 反応生成物分離塔 | `reactor_tray_stack` | 2～4 | 8 SU/RPM |
| ガス遠心分離機 | `reactor_rotor_column` | 2～4 | 24 SU/RPM |

内部部品で槽の任意の場所を右クリックすると、最も下の空き段へ入る。レンチでは最も上の部品から取り出す。部品がない場合の通常レンチ操作は、回転軸を `Y → X → Z → Y` と切り替える。設置時にはクリックした面の軸に合わせる。レンチで取り出す操作と軸変更を同時には行わない。

触媒充填槽は、槽のどの部材も触媒で右クリックして1個を投入できる。満杯なら消費しない。両手を空にしてスニークし、右クリックすると触媒を回収する。主手が空で副手にアイテムを持つ場合も、空手側の処理が副手の操作を奪わない。

全段に同じ種類の内部部品がそろい、槽自身の貯蔵用タンクが空なら形成する。撹拌反応槽とガス遠心分離機の3×3は、形成時に四隅が切れた八角形になる。2×2の外形は角型のまま。

形成した槽の配管・アイテムハンドラーは、コントローラーが所有する原料・生成物・触媒のバッファを公開する。通常の貯蔵用タンクと工程バッファを混ぜない。内部部品を入れた直後やハッチを開いた後に、以前取得した貯蔵用ポートを使い続けても裏側のタンクへ流体を入れられないようにした。

撹拌反応槽の加熱源は槽の真下に置くブレイズバーナー。底面のシャフトとバーナーが重なる場合は側面から回転を入れる。ハッチ開放、32 RPM未満、コントローラーへのレッドストーン信号、構造不成立、原料不足、必要圧力不足、出力空き容量不足などは従来の工程条件に従って停止する。

## 回転数・処理速度・応力の計算

実効回転数には、槽を構成するブロックへ実際に届いたCreateの回転数を使う。回転方向の正負は処理速度に影響しない。

```text
32 RPM未満：処理停止
進捗倍率 = min(4, abs(RPM) / 32)
1回の並列バッチ数 <= 有効段数
応力係数 = 装置の1段あたりの係数 × 有効段数
Createネットワーク上の負荷 = 応力係数 × abs(RPM)
```

32 / 64 / 128 RPMで進捗はそれぞれ1 / 2 / 4倍。128 RPMを超えても進捗倍率は4倍で止まるが、応力負荷はCreateの回転数に従って増える。例えば触媒充填槽1段を128 RPMで回すと、応力係数は16 SU/RPM、ネットワークの負荷は2,048 SU。4段なら64 SU/RPM、8,192 SUとなる。材料と出力容量が足りる場合、4段は1サイクルにつき最大4バッチを処理する。

各部材へ応力を重複計上せず、選ばれた1個の入力部材だけが槽全体の負荷を持つ。側面部材から入力した場合も、その部材の実効速度でコントローラーが動く。過負荷で実効速度が0になっても、回転元が残っている入力部材に負荷を残し、未接続のコントローラーへ負荷が逃げないようにした。複数の回転入力の速度を足して処理を加速する機能はない。

既存の化学レシピの投入量・生成量・所要進捗・触媒寿命は変更していない。

## 外観・案内

2×2用の32×32、3×3用の48×48の天板テクスチャを追加した。ブロック1個あたり16 pxの密度を維持し、同じ小さい絵を各ブロックに繰り返す代わりに、槽全体で1枚の模様になるようUVを割り当てる。3×3の八角形では天板も同じ外形に切り取る。ハッチ、裏面、縁は従来の銀色の部材を使う。

側面・下面には接続可能な場所だけ軸受と回転シャフトを表示し、撹拌翼と遠心ロータは入力された実回転数に合わせて動く。隣接するCreateの軸と接続した場合は端をブロック境界に合わせ、外周面が重なるのを避ける。ゴーグル表示は同じコントローラーへ再帰的に委譲しないよう修正した。

Ponderの旧駆動部を槽へ直接入力する構造へ変換し、2×2の積み上げと回転入力の場面を追加した。JEIの工程入口、構造ガイド、表示リンク、翻訳、クラフト生成スクリプトも新しい槽と内部部品へ合わせた。熱交換器と硝酸吸収塔の現役部品は維持する。既存PNGは変更せず、新規の天板2枚だけを追加した。天板2枚と軸受モデルは生成スクリプトからバイト一致で再生成できることを確認した。

## 保存・解体・所有元の制約

槽は `ReactorMachineBlockEntity` を継承し、Createの `IMultiBlockEntityContainer.Fluid` を実装して連結する。貯蔵用タンクと別に、原料・生成物・触媒・投入アイテム・進捗をコントローラーに保存する。旧ふた付き槽の `Controller` / `Size` / `Height` / `TankContent` / `Internal` も引き続き読む。読み込み後は構造を再検証してから工程ポートを有効にする。

工程用流体、触媒、投入・生成アイテムのどれかが残っているコントローラーは、別の槽へ吸収して所有元を移すことができない。Create標準のタンク結合だけでは別管理の工程在庫が移らず、元のブロックが非コントローラーになると内容物へ届かなくなるため、結合候補を事前に拒否する。

- 元のコントローラーを維持できる通常の上への増設は可能。
- 下への増設や槽同士の結合でコントローラーが変わる場合、工程の中身がある槽は再所属しない。置いたブロックが旧槽と一体化することは保証しない。
- 移動を伴う増設をする前に、流体と触媒・投入・生成アイテムを取り出す。貯蔵槽の流体の連結・分配とは別の制約。
- コントローラーを壊すと、内部部品・触媒・投入・生成アイテムはドロップし、工程の残存流体は放出される。周囲のブロックを爆破する処理は追加していない。
- 内部部品だけを外した場合は工程在庫を保持する。保存・再読み込み後に同じ内部部品を戻せば投入品へ再びアクセスできる。

`hasProcessContents()` の移動抑止条件は在庫の有無であり、進捗値だけで移動を禁止するものではない。移動可能な空の槽の下面増設は配置テストで確認している。

## 削除IDと既存ワールドへの影響

今回削除した登録IDは、いずれも `create_heavy_indusstry` 名前空間の次の4つ。

```text
reactor_stirred
reactor_vessel
reactor_separator
reactor_centrifuge
```

対応するブロック・アイテム・レシピ・ブロック状態・モデル・ルートテーブル・翻訳・タグ参照を削除した。`reactor_impeller` / `reactor_catalyst_basket` / `reactor_tray_stack` / `reactor_rotor_column` と槽本体は残す。`heat_exchanger_support` は現役の熱交換器、`reactor_separator_core` は硝酸吸収塔の棚段なので削除対象に含めない。

削除IDを槽へ変換するリマップや、旧駆動部の工程NBTを新しいコントローラーへ移す処理は実装していない。既存ワールドの旧駆動部・手持ちアイテムは欠落登録の対象となり、駆動部に保存されていた流体・触媒・進捗も自動では引き継がれない。旧設備のワールドは更新前のコピーで内容物を回収し、槽と内部部品で組み直す必要がある。通常のふた付き槽を同じIDのまま扱うことと、削除された駆動部の移行は区別する。

## 主要ファイル

| ファイル | 役割 |
| --- | --- |
| `src/main/java/net/createplant/block/LiddedVesselBlock.java` | 側面・下面の回転接続、軸変更、ハッチ操作、内部部品の操作 |
| `src/main/java/net/createplant/block/entity/LiddedVesselBlockEntity.java` | 連結・保存、入力部材の選択、工程所有、重複しない応力、ポート |
| `src/main/java/net/createplant/block/entity/ReactorMachineBlockEntity.java` | 既存工程処理、実効RPM、加熱位置、工程在庫の保存 |
| `src/main/java/net/createplant/block/ReactorStructure.java` | 2×2・3×3の形成条件と段数、内部部品の確認 |
| `src/main/java/net/createplant/item/LiddedVesselItem.java` | 上下への1段一括設置と全セルの事前検査 |
| `src/main/java/net/createplant/mixin/ThermalConnectivityMixin.java` | 中身のある工程コントローラーの移動抑止、熱流体の連結・分配対応 |
| `src/main/java/net/createplant/client/LiddedVesselRenderer.java` | 連続天板、ハッチ、軸受、シャフト、内部部品の回転描画 |
| `src/main/resources/assets/create_heavy_indusstry/textures/block/lidded_vessel/top_2.png` / `top_3.png` | 2×2・3×3専用の天板 |
| `src/main/java/net/createplant/registry/CEReactor.java` | 4駆動部の登録廃止と現役部品の維持 |
| `src/main/java/net/createplant/ponder/LiddedVesselPonderScenes.java`、関連Ponder NBT・生成スクリプト | 2×2の積み上げ・回転入力と既存工程の直接入力化 |
| `src/main/java/net/createplant/compat/jei/CreatePlantJeiPlugin.java`、`guide/ReactorGuide.java` | 工程を調べる入口と構造案内 |
| `src/main/java/net/createplant/thermal/{PhaseTank,PassiveTank,PressureVesselBehaviour,HotSurfaces}.java` | 従来FluidTank継承に依存していた熱・圧力・ポート判定の対応 |
| `docs/pipes-and-tanks.md`、`docs/reaction-plant.md` | プレイヤー向けの操作・構造説明 |

## 検証結果

初期の統合GameTestは**51件すべて合格**（7.632秒）。その後に追加した八角壁・手動触媒操作の回帰2件と、通常タンク・温度・二相・圧力・受動熱・熱制御を含め、最終の**122件もすべて合格**（5.241秒）。2026-11-08実行、ログ：`/workspace/outputs/vessel-upgrade/final-regression.log`。

```sh
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Dcreateplant.gameTestClasses=LiddedVesselGameTests,LiddedVesselPlacementGameTests,DriveLessVesselGameTests,ReactorGameTests,MalformedDataGameTests,CraftingRecipeGameTests,SurvivalReachabilityGameTests' bash gradlew --no-daemon --max-workers=2 runGameTestServer
```

確認範囲：

- 新しい配置テスト9件：2×2上面、3×3下面、副手、個数不足・固体妨害、エンティティ衝突、Adventure権限、Creative消費、スニーク・単体・側面、幅1/2/3と4方向の実際のはしご登攀。
- 直接回転入力：Createのモーターで底面・側面から128 RPM、切断時の停止、応力の入力部材への計上、過負荷時の負荷保持。
- 八角形の角壁への水平入力拒否・下面入力維持、平らな側面の入力。実プレイヤーの触媒投入・満杯時の非消費・副手優先・素手スニーク回収。
- 在庫と連結：中身のある所有元の維持、内部部品を外して保存・復元、古いポートによる貯蔵タンクへの混入防止、ゴーグル表示の有限性。
- 既存工程：各段の内部部品・並列処理・触媒等の反応装置テスト、異常NBT、4駆動部の登録廃止、クラフトとサバイバル到達性。

下面配置の初回失敗は、試験用構造の実際の石床が予定配置領域に重なったことが原因だった。全9セルの障害物拒否は正しく動作しており、製品側の制約を緩めず、当該fixtureだけ1段上げて空気である事前条件を追加した。修正後の統合51件で合格した。

| 確認 | 状態 |
| --- | --- |
| 最終関連GameTest | 122/122合格。新しい試験ワールドを `/tmp` に作成し実行。 |
| 流水テストの単独実行 | 2/2合格（772.1 ms）。`water-isolated.log`。 |
| 全体GameTest | 422件を開始したが未完了。進捗表示299合格・1失敗の後、5分以上判定が進まず終了した。詳細は下記。 |
| 実クライアント・ネイティブ描画・Ponder表示 | RTX 4060 / OpenGL 4.6。114シーンの日本語検査、対象6画面と反対側2画面の保存成功。終了時のGPUクラッシュは下記。 |
| 最終build | 成功。`FluidPhase` 25、`HaberBoschProcess` 1,019、`ReactorPhysicsTest` 1,670チェックと炉制御テストも合格。 |
| Ponder静的検査 | 追加90シーン、454対訳字幕、131部品リンクと構造ターゲットを検査成功。旧駆動部IDがPonder構造に残っていない。 |
| 実装コミット | 冒頭に記載。公開先は現在の作業ブランチ `main`。 |

全体実行の失敗は `WaterResistanceGameTests.nonOccludingEquipmentSurvivesFlowingWater` の超耐圧パイプ。対象コードと試験はベースから変更しておらず、[従来の同種記録](lidded-vessel-hatch.md)と同様、単独では再現しなかった。試験は32×8×32の構造に対して候補90番目以降をZ32外へ配置するため、他試験との干渉が疑われる。ただし今回の消失原因を確定したものではなく、パイプ実装の修正には広げていない。

全体実行はチャンク保存でたびたび長く止まり、途中のスレッドダンプではサーバーが `ChunkMap.save/isExistingChunkFull`、IOWorkerが `RegionFile.write/pwrite` で待っていた。最終の5分以上の進捗停止で終了し、全件合格とは扱わない。通常タンク等への影響は上記の対象を絞った122件で確認した。

再現時の保存先変更には、ModDev Gradle 2.0.141の `RunModel.gameDirectory` を一時init scriptで `/tmp/createplant-vessel-regression-20261108` に設定した。製品のGradle設定には変更を加えていない。init script：`/tmp/createplant-vessel-tests.gradle`。クラス指定は上の7クラスに `TankGameTests,TankTemperatureGameTests,TwoPhaseGameTests,PressureGameTests,PassiveHeatGameTests,ThermalControlGameTests` を加えた。流水の2件は `WaterResistanceGameTests` だけを別の新しい保存先で実行した。

### 実クライアントの確認範囲と成果物

同じアイテムに多数のPonderが登録されているため、開発用検査に `item@scene_id` の指定を追加した。初回のインデックス指定は別の工程を選んだので採用せず、次のシーンIDで撮り直し、選択結果をログに出した。

- `lidded_vessel@lidded_vessel`、`lidded_vessel@lidded_vessel_2x2`
- `reactor_catalyst_basket@plant_reactor_plant_vessel`
- `reactor_impeller@plant_reactor_plant_stirred`
- `reactor_tray_stack@plant_reactor_plant_separation`
- `reactor_rotor_column@plant_reactor_plant_centrifuge`

2×2・3×3の連続天板と中央ハッチ、はしごが1面にあること、角型／八角型の外形、旧駆動部がないことを確認した。対象画像にmissing textureや不自然な透過穴は見つからなかった。回転ネットワークの動作・切断時の停止・過負荷は上記の実モーターを使うGameTestで確認し、Ponder画像だけを実ネットワーク運転の証拠とはしていない。

実クライアントは `CLIENT QUALITY PASSED` と画像・JSON保存の後、いずれも `libEGL_nvidia.so.0` のSIGSEGV、終了コード134になった。この環境で従来から記録のある終了時の問題で、`runClient` の正常終了とは扱わない。ログ：`client-final.log`、`client-ladder.log`。

成果物は `/workspace/outputs/vessel-upgrade/` に保存した（すべて1ファイル8 MiB未満）。

- `create-heavy-indusstry-0.1.0-vessel-upgrade.jar`：配布JAR、約4.5 MiB。
- `vessel-2x2-front.png`、`vessel-3x3-front.png`：天板の見える側。
- `vessel-2x2-ladder.png`、`vessel-3x3-ladder.png`：はしご側。
- `catalyst-vessel.png`、`stirred-vessel.png`、`separator-vessel.png`、`centrifuge-vessel.png`：4装置。
- `client-quality-final.json`、`client-quality-ladder.json`、各試験ログ、一時init scriptのコピー。

### 画像の閲覧先

Discordへの添付送信ツールはこの作業環境にないため、ローカルパスの提示は添付送信にならなかった。撮影済みの2枚を、非公開設定を変えずにリポジトリの [画像ページ](../quality/vessel-upgrade/README.md) にも保存した。GitHubでこのリポジトリへの閲覧権限を持つアカウントから表示できる。

未ロードチャンクの実際のアンロード、対称ワンドによる複製、外部プラグインが設置イベント途中でワールドを変更する場合の補償トランザクションは、今回の配置テストでは確認していない。大規模配置のMSPT・描画負荷の比較計測はしていない。
