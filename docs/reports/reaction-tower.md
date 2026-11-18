# 反応塔（reaction_tower）完了レポート

## 実装内容
- Createの流体タンクと同じ形状・接続（1×1／2×2／3×3、テクスチャ接続、窓、3×3の上に置くと3×3で設置）を使う追加タンク。`PlantFluidTankBlock`／`PlantTankItem`をそのまま再利用。
- 受け入れ流体は黒鋼液体タンクと同じ（気体以外）。圧力容器上限は通常タンクと同じ16。

## 主要ファイル
- `registry/CETransport.java`（登録）、`block/entity/PlantFluidTankBlockEntity.java`（液体Capability）、`client/PlantTankModels.java`（接続テクスチャ）、`ElectrolysisClient.java`（タンク描画）、`registry/CECreativeModeTabs.java`
- `tools/generate_reaction_tower.py`：テクスチャ・モデル・blockstate・レシピ・ルートテーブルの生成
- 翻訳は`block.create_heavy_indusstry.reaction_tower`（反応塔／Reaction Tower）

## テクスチャ
参照画像の暗い鋼板、パネルの縁取り、リベット、青と赤の計器窓、下部の炉扉を16px化。炎の発光は描かず、炉の開口は暗いまま。8pxパネルで継ぎ目なく接続し、炉扉は面の最下段のみ。

## 確認
- `runGameTestServer`：283件すべて成功（`ThermalControlGameTests`の2×2・3×3結合にREACTION_TOWERを追加）。
- 未確認：実画面での見た目と接続の目視、専用サーバー以外の起動。

## 注意
- 参照画像は縦長の1面絵で、16pxの再現は近似。細部の調整が必要なら`tools/generate_reaction_tower.py`の`P`と`cell`を編集。
- レシピ（鉄板・かまど・ガラス板）は仮。
- 実装コミット：24b0d3e

## 設置仕様の変更
- 反応塔は2×2の上に置いても1ブロックだけ設置（層の同時設置を削除）。3×3の上では3×3の層を一括設置（`PlantTankItem.bulkPlaces`、`PlantTankItemMixin`）。他のプラントタンクは従来どおり。
- 設置・破壊音をCreateのBasin・外装と同じ`SoundType.NETHERITE_BLOCK`に変更（反応塔のみ）。
- 確認：`runGameTestServer`283件成功（2×2単体設置の判定を追加）。音は未聴取。
- 実装コミット（設置仕様変更）：7472923

## テクスチャ差し替え
- 反応塔の8枚（側面・上面・内面・窓2種・接続3種）を、提供された`Create_Black_Tank_All_Textures`の`fluid_tank*`へ置き換えた（ファイル名のみ`reaction_tower*`へ変更、内容は無改変）。`tools/generate_reaction_tower.py`はテクスチャ描画を削除し、モデル等の生成だけにした。素材の権利はCreate原作者に帰属する旨が同梱READMEにある。
- Ponderの実クライアント撮影で、3×3の層が連結して描画されることを確認。ゲーム内の通常ワールドでの見た目は未確認。
