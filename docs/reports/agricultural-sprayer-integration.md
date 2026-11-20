# 農薬散布機の実装と検証

対象は Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10。実装コミットは `9162b64e0efb930b8822cc27496a1699d6bcb7d1`。

## 使い方

1. **からくり** — 散布機とCreateの流体タンクを同じ回転アーム、ガントリーキャリッジ、またはトロッコに接着する。装置のノズルを作物へ向けて、搭載タンクに液体を入れる。からくりが動く間、ノズルの向きに沿って前方1〜3ブロックの作物に散布する。
2. **据え置き** — 背面をCreateの流体パイプやポンプに接続し、レッドストーン信号を入れる。背面から残液を回収して液体を切り替えられる。エンジニアのゴーグルに残量とレッドストーン状態が表示される。
3. **液体とレシピ** — JEIの散布カテゴリに液体、消費量、効果を表示する。希釈農薬の流体またはバケツにRを押すとCreateのミキサーで行う希釈が、Uを押すと散布が表示される。原液には散布レシピがなく、架空の作物保護剤を水で希釈して使う。
4. **Ponder** — 散布機から回転ベアリング、ガントリー、据え置きの組み立てと操作を確認できる。

## 数値と処理

| 項目 | 挙動 |
| --- | --- |
| 液体肥料 | 20 mB/株。既存のリン酸塩系液体肥料から既存のNPK処理を適用し、作物の成長を1段階進める。対象が一般の骨粉対応ブロックの場合はバニラの骨粉処理を使う |
| 希釈農薬 | 15 mB/株。成長段階は変えず、効果中に成熟した作物を収穫すると通常の収穫物を1個追加する。既存のNPKと併用可能 |
| 保護効果 | 最大24,000ゲームtick。収穫か植え替えで終了。効果中の再散布は追加消費なし |
| 供給 | 移動時はCreateの搭載タンクを直接使用。据え置きは背面から容量250 mBの内部槽へ送る |
| 作動周期 | 移動時はノズル位置が次のブロックへ入ったときと、ノズルが同じブロック内で動いている間は最短5 tick間隔。回転による先端の移動も対象。据え置きは信号がある間、10 tick間隔 |
| 遮断 | 固体の壁は噴霧を遮る。作物のない場所や処理できない対象には消費なし。耕地を検知した場合は直上の作物を確認する |
| 演出 | 実際に液体を消費したとき、真鍮ノズルから霧と噴霧音が出る |

からくりの液体残量はCreateが管理する。そのため、据え置き槽とからくりの搭載液体は別の在庫になる。据え置き槽はブロックエンティティとして保存し、通常の回収時にドロップしたブロックへ内容を引き継ぐ。保護効果は既存の作物データへ追加し、従来のNPK肥料データを引き続き読み込める。

## 主な実装

- [ブロックと背面取入口](../../src/main/java/net/createplant/block/AgriculturalSprayerBlock.java)、[据え置き制御](../../src/main/java/net/createplant/block/entity/AgriculturalSprayerBlockEntity.java)、[Createの移動処理](../../src/main/java/net/createplant/agriculture/AgriculturalSprayerMovement.java)
- [対象と消費の判定](../../src/main/java/net/createplant/agriculture/AgriculturalSpraying.java)、[作物の保護・収穫・保存](../../src/main/java/net/createplant/agriculture/FertilizedCrops.java)
- [散布レシピの定義](../../src/main/java/net/createplant/recipe/SprayingRecipe.java)、[データパックの既定値](../../src/main/resources/data/create_refinery_n_reactors/recipe/spraying/)、[JEI](../../src/main/java/net/createplant/compat/jei/CreatePlantJeiPlugin.java)
- [モデル・16×16テクスチャ・Ponder生成](../../tools/generate_agricultural_sprayer_assets.py)、[Ponderの進行](../../src/main/java/net/createplant/ponder/AgriculturalSprayerPonderScenes.java)
- [農薬散布機のGameTest](../../src/main/java/net/createplant/gametest/AgriculturalSprayerGameTests.java)

## 検証

- Gradleビルド、専用サーバーで全679件のGameTestに成功。新規6件で回転、ガントリー、トロッコ、Create標準ポンプ、遮蔽物、希釈と豊作、消費量、解体時の液体残量を確認。ミックス6,004チェック、原子炉物理1,668チェックおよび原子炉制御テストも成功。
- RTX 4060の日本語クライアントで、両流体と原液のR/U、ミキサーへの導線、散布の効果表示、Ponderの3場面を確認した。この環境では音声デバイスが使用できないため、噴霧音の実再生は未確認。
- 日本語、英語、簡体字中国語の翻訳キーと169件のPonderヘッダーを検証した。
- ガントリーとトロッコのGameTestはCreateの実際のからくりを組み立て、テスト内で位置を変えて散布を判定した。長距離を自走する農場や、他MOD独自の作物・搭載タンクとの相互運用は未測定。

## 配布

配布用JARは 8,380,355 バイト。Discordの8 MiB制限に収めるため、既存の開発用GameTest 7クラスとその内部クラスを省いた。通常のGradleビルドは既存テストを含み、JAR内の使用するコード・資産は通常ビルドとバイト単位で一致する。

SHA-256 `9c2a35429555cec48d15f3258529b9c54d6e942dda2d5f4745935b949822e296`
