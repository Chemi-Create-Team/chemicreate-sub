# 共通タンク構成の円形沈殿池

Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10。実装コミットは `085082e220a22c9125217232de801d92febcc179`。

## 構成と操作

- 施設建設機に「円形タンク（タンク構成）」を追加した。通常タンクと沈殿池の新規プランはタンク構成ブロックで外壁と床を組む。どちらも直径7〜29を2ブロック刻みで選択し、高さは床込みで3・4ブロックを選択する。寸法はそれぞれ独立して保存する。
- 変換は床中央を下向きの底面流入口に替え、縁と同じ高さの中央にセンタードライブを置く。正面の外周最上段から上澄みをパイプに出し、背面の外周最下段からファンネルで汚泥を取り出す。汚泥口は凝集剤を受け付ける。既存の越流堰と汚泥排出口も対応する。
- 変換中は通常タンクの残液を隣接する床ブロックに保持する。中央を戻して通常タンクに再形成すると取り出せる。沈殿池側の内容物は従来どおりドライブに保存する。
- 既存の奇数サイズの旧槽壁も有効。偶数の既設沈殿池は奇数サイズに組み直す必要がある。建設機に保存されていた偶数指定は直下の奇数へ移行する。

## 実装箇所と検証

- [共通形状](../../src/main/java/net/createplant/block/WetProcessStructure.java)、[沈殿池の形成](../../src/main/java/net/createplant/block/entity/WetProcessBlockEntity.java)、[タンク側の入出力](../../src/main/java/net/createplant/block/entity/TankCasingBlockEntity.java)
- [施設建設機のプラン](../../src/main/java/net/createplant/assembly/AssemblyPlan.java)、[寸法の保存](../../src/main/java/net/createplant/block/entity/FacilityConstructorBlockEntity.java)、[GameTest](../../src/main/java/net/createplant/gametest/WetProcessingGameTests.java)
- 関連GameTest 34件が全件合格。通常タンクの形成、中央2部品による変換、実際のパイプ接続、沈殿池の加工、通常タンクへ戻したときの残液保持、建設機の寸法保存を確認した。
- RTX 4060のクライアントで建設機を開き、沈殿池の全12直径と高さ4、円形タンクの直径9、高さ3を選び、選択を切り替えても寸法が保持されることを確認した。Ponderの構造・翻訳を検証した。最大径の施設建設機による全ブロック発射は検証範囲外。

## 配布

配布JARは 8,383,922 バイト。8 MiBの上限に収めるため、既存の開発用GameTest 20組のクラス29ファイルを省いた。通常ビルドには全テストが含まれる。

SHA-256 `479d29e271d5d3b7cf03091e1aca59358e05f846da34b0cd94b1b9fec0075c79`
