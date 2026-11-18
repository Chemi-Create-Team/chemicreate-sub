# 水蒸気ガス化炉の単一ブロック化

実装コミットは `b3e14fa`。組立と配管の手順は[水蒸気ガス化炉](../entrained-gasifier.md)に掲載した。

## 実装内容

- 5種類の部品（ケーシング、耐火炉壁、供給ノズル、耐火炉床、観察窓）を、1種類の「水蒸気ガス化炉」ブロック `steam_gasifier` に置き換えた。3×3で6段、54個を並べると形成される。
- 形成時、各ブロックのblockstate `cell`（0＝単体、1～54＝炉内の位置）を設定し、位置に応じたモデルを表示する。単体ブロックは反応塔の外殻と天板を白い鉄色に塗り直したテクスチャ。
  - 1段目：白い鉄の基礎。各面中央に蒸気入口フランジ。
  - 2～5段目：リベット付きの鉄枠と、段の継ぎ目の鉄帯で囲んだ耐火レンガの反応層。レンガはこの区間だけ。3・4段目の各面中央に点検窓。
  - 6段目：反応塔の天板（3×3の接続テクスチャ）を白くした天板。中央に反応塔と同じ22 pxの投入口ハッチと取っ手、正面の辺の中央にガス出口フランジ。
- 点検窓は奥に赤熱コークスを描く。炉温800℃以上の間は `lit=true` で発光モデル（`neoforge_data` の最大光量）とブロック光9を使い、冷えると暗いコークスに戻る。切り替えは温度が閾値をまたいだ時だけで、点検窓の8ブロックだけ状態を変える。
- 投入は天面9ブロックの上面から。燃料はアイテムタグ `gasifier_fuels`（石炭、木炭、炭素粉、任意の `#c:coal_coke`）。水蒸気は最下段の各面中央の外向き面から。ガス出口は正面の天面フランジの上面。正面は、天面中央のブロックを置いたときにプレイヤーが立っていた側。
- 反応は800℃で開始し、熱源の加熱上限は1000℃。ノズル数による速度倍率は廃止し、40 tickで燃料1個・水蒸気250 mBから混合気体500 mB。

## 主要ファイル

- `block/GasifierBlock.java`：`cell`・`facing`・`lit`のblockstate、設置時の形成予約、未形成時だけのレンチ回転、底面中央だけのticker。
- `block/entity/GasifierBlockEntity.java`：形成判定、外観の設定と解除、面ごとの入出力、点検窓の発光、旧在庫の回収口。
- `registry/CEGasifier.java`、`registry/CELegacyIds.java`：新ブロックの登録と、旧5部品IDの別名。
- `tools/generate_gasifier.py`：テクスチャ・55＋12モデル・multipart blockstate・レシピ・タグ・Ponder配置の生成。
- `ponder/GasifierPonderScenes.java`、`tools/data/ponder_lessons.json`：組立から運転停止までのPonder。

## 計算方法

- `cell = 1 + 段 × 9 + (dx + 1) × 3 + (dz + 1)`（dx、dzは底面中央からの差）。底面中央は5。
- 側面のUVは、段の区間テクスチャ（帯48×16、レンガ48×64、天板48×48）を、外から見た列と段で16 pxずつ切り出す。外側の面だけを描き、炉の内側の面は生成しない。

## 形成と解体

- 新しく置かれたブロックは1 tick後に、自身を含みうる54通りの底面中央候補を軽い条件で絞り、候補で形成を試す。形成済みでない単体ブロックはtickしない。
- ブロックを1個でも外すと、残りのブロックは単体の外観に戻り、搬送ハンドラーを無効化する。チャンクのアンロードでは外観を変えず、論理上の形成だけを解除し、再読込後に底面中央が再検証する。
- 自分のblockstate変更は構造変更として扱わない。外観の更新は `UPDATE_CLIENTS` だけで行う。

## 既存ワールド

- 旧5部品のブロックとアイテムは `steam_gasifier` として読み込まれる（`facing`は維持、`cell=0`）。BlockEntity型 `entrained_gasifier` と保存形式は同じで、在庫は底面中央に残る。
- 旧炉は2・4・5段目の中央が空洞なので、埋めると形成される。旧合成ガスは正面の出口、旧酸化剤は北、冷却水は西、温水は東、急冷水は南の蒸気入口、旧スラグは最下段北側中央の北面か底面中央の下から回収できる。
- Display Linkの `gasifier_nozzles` と `gasifier_multiplier` を削除した。これらを選んでいた表示は値を表示しなくなる。

## 確認結果

- Gradleの`compileJava`が成功。
- 専用サーバーGameTest：`GasifierGameTests`（7件：形成と外観・ポート、旧ID読込、外部加熱と1バッチ、加熱停止と再開、出口満杯、ポンプ搬出と冷却・貯蔵、旧在庫の回収）と、`LegacyNamespaceGameTests`、`SurvivalReachabilityGameTests`、`CraftingRecipeGameTests`、`PlantTutorialGameTests`、`ManufacturingPonderGameTests`、`DisplayIntegrationGameTests`の計32件が成功。
- `validate_translations.py`、`validate_ponder_language.py`、`validate_plant_tutorials.py` が成功。
- `validate_visual_identity.py` と `test_ci_validators.py` は、変更前のコードでも同じ既存項目（ferrochrome等の未分類アイテム）で失敗する。今回の変更による失敗ではない。

## 未検証事項と次回の注意

- 多数の炉を同時稼働させた負荷計測と、外部Mod独自の搬送設備による試験は行っていない。
- 実際のゲーム画面での見た目（向き、点検窓の発光、天板のハッチとフランジ）は未確認。この環境のソフトウェア描画クライアントはリソース読込の途中で進まなくなったため撮影できなかった。GPUのあるクライアントで `docs/quality/steam-gasifier-block/client.gradle` を使って確認する。
- 非正方形のテクスチャ（48×16、48×64）を区間テクスチャとして使っている。
