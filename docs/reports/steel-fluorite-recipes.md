# 鋼材原料・蛍石・フッ素製造の調整

作業日: 2026-11-12

実装コミットID: 704d7c5cb0a1db4c747e947824350a79d684a0e6

## 確定した仕様

鉄鉱石ブロックを使うパドル鉄の別レシピを削除し、転炉の酸素製鋼も鉄インゴットと鉄の原石だけで行う。会話で「粗鉄」と呼んでいたものはバニラの鉄の原石 `minecraft:raw_iron` であり、専用素材ではない。ユーザー確認に従い、採掘による入手方法と既存の配合量を維持する。

| 工程 | 入力 | 出力 |
| --- | --- | --- |
| 通常のパドル鉄 | 鉄インゴット4、鉄の原石2 | パドル鉄4 |
| 融剤入りパドル鉄 | 上記と蛍石1 | パドル鉄5 |
| 転炉の酸素製鋼 | 鉄インゴット48、鉄の原石24、酸素64,000 mB | 鋼材ブロック64、転炉ガス128,000 mB |

パドル鉄から鋼材への5回プレス、転炉の800 tick、液化酸素の代用、AOD精錬は維持する。蛍石削除の追加依頼は、確認時にユーザーから撤回されたため、融剤入りの増産レシピを残した。

## 蛍石の入手

独立した蛍石鉱脈は存在しない。ユーザーが選んだ50％を、方解石の粉砕・石臼加工の両方へ適用した。基準加工時間250、骨粉1個の75％副産物は維持する。

石臼にはCreate 6.0.10の `create:milling/calcite` が既に存在する。同じ入力で競合する別レシピを追加せず、このレシピへ蛍石1個・50％の副産物を追加した。依存JARの元レシピと照合し、方解石・加工時間・骨粉の条件が変わっていないことを確認した。

## 蛍石からフッ素

既に実装されている次の2工程を再利用し、実機のGameTestで一貫して製造できることを確認した。直接変換レシピや新設備は追加していない。

1. 加熱した撹拌槽: 蛍石1個＋硫酸250 mB → フッ化水素1,000 mB。気体の取り出しは槽の上面を使う。
2. 電解セル: フッ化水素2 mB → 水素1 mB＋フッ素1 mB。電力と極板を使う。

したがって蛍石1個分から水素500 mB・フッ素500 mBを回収できる。Ponder「蛍石からフッ素を作る」を蛍石へ追加し、石臼、撹拌槽、電解セルの組立と給電、気体の回収先を英・日・中で説明した。フッ化水素・フッ素には登録済みバケツアイテムがないため、存在しないバケツからの導線は作らない。

鋼材Ponderにも石臼の代替工程を追加した。電解工程のPonder表示量は、実機と同じ `ElectrolysisProcess` から取得する。生成対象を絞った場合は既存の章順を保つ。

## 主な変更ファイルと再生成

- `tools/generate_material_use_recipes.py`: パドル鉄の通常・融剤入りを生成し、旧鉱石ブロック版2件を削除する。
- `tools/generate_converter.py`: 酸素製鋼の原料を鉄の原石へ限定する。
- `tools/generate_uranium_assets.py`: `fluorite_recipes()` が粉砕とCreateの石臼レシピを生成する。
- `tools/generate_manufacturing_ponder.py`: `manufacturing_steel` と `manufacturing_fluorine` の章を生成する。
- `tools/generate_converter_ponder.py`: 日本語の原料名を「鉄の原石」へ修正する。
- `ManufacturingRecipe.java`: フッ化水素の電解を表示し、既存電解工程も共通の実処理データを参照する。
- `CraftingRecipeGameTests`、`ManufacturingPonderGameTests`、`ModularPortsGameTests`: 原料、競合、出現確率、Ponder、一貫製造を確認する。

変更対象の生成は、前3スクリプトの `mixing()`、`recipes_and_data()`、`fluorite_recipes()` と、転炉Ponderの `generate()`、製造Ponderの `generate({'manufacturing_steel', 'manufacturing_fluorine'})` を呼んだ。原子炉等の無関係なアセットは再生成していない。3言語の元のLFを維持した。

## 確認結果

対象: Minecraft 1.21.1 / NeoForge 21.1.219 / Create 6.0.10 / CEE 1.1.4 / CDG 1.3.15 / Java 21。

- `bash gradlew --no-daemon --max-workers=2 build runGameTestServer`: 合格。`JAVA_TOOL_OPTIONS` に `-XX:ActiveProcessorCount=4 -Dcreateplant.gameTestClasses=CraftingRecipeGameTests,ManufacturingPonderGameTests,ConverterGameTests,ModularPortsGameTests` を指定し、関連30件すべて合格。最終実行32秒、GameTest本体2.282秒。
- 読み込まれた実レシピで、鉱石ブロック版の不在、鉄の原石の数量維持、両加工機の方解石レシピが各1件であること、蛍石50％・骨粉75％・基準時間250を確認した。
- 実際の撹拌槽の標準Capabilityから原料を入れ、上面からフッ化水素1,000 mBを回収して電解セルへ投入した。500回の通電処理で原料が空になり、水素・フッ素がそれぞれ500 mBになることを確認した。
- `validate_plant_tutorials.py`: 合格。追加Ponder100シーン、製造27系列。
- `validate_ponder_language.py`: 合格。138シーンの実行時キーと3言語。
- `validate_visual_identity.py`: 合格。
- `test_ci_validators.py`: 15件合格。
- `mc-debug glxinfo -B`: NVIDIA GeForce RTX 4060、直接描画を確認。
- `mc-debug bash gradlew --no-daemon --max-workers=2 runClient`: 日本語の実クライアントで鋼材・フッ素のPonder 2シーン、3表示対象を確認し、正常終了。石臼と電解セルの描画をComputer Useで目視確認した。[実行結果](../quality/steel-fluorite-client.json)と[Computer Use撮影画像](../quality/steel-fluorite-ponder.jpg)を保存した。
- `git diff --check`: 合格。作業前とコミット前の `git fetch origin main` で新しいリモートコミットがないことを確認した。

初回試験では、新しいPonderに存在しない気体バケツを指定したことと、試験が気体を側面から取り出そうとしたことにより2件失敗した。参照を登録済みの蛍石へ絞り、試験とPonderの回収先を槽の上面へ修正した後、上記30件を再実行して合格した。

## 未検証事項と次回の注意

全件GameTest、長時間の工場運転、任意MODの全組合せは、必要最小限の検証という指定に従って省略した。英語・中国語はリソースと実行時キーの自動検証で確認し、実クライアントの目視は日本語だけ行った。Ponderの気体生成量と、実ワールドでの標準ポートによる入出力は別々に検証しており、両設備をCreateパイプで接続した長時間の連続運転は未検証。

登録ID・保存形式は変更していない。鉱石ブロックを使っていた鋼材ラインでは原料を鉄の原石へ差し替える必要がある。石臼はCreate名前空間の既存レシピを置換するため、外部データパックでも同じIDを書き換えている場合は優先順位を確認する。

試験ログには旧 `reaction_tower` BlockEntityのスキップ警告と、クライアント起動にはCDGの `andesite_girder_strut` 戦利品テーブルの読込エラーがある。今回の関連試験とクライアント確認は合格したが、これらの範囲外の発生元は調査・修正していない。
