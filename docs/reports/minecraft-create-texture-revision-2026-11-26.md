# Create風×Minecraft準拠テクスチャ刷新（鉱石・マルチブロック・インゴット・粉末・バケツ除外）

実装コミット: `bc139f4f9faf93e490675d88d3163772ac21c32f`（基準: `origin/main` `6b6a7e3`）

## 変更内容

ユーザー承認（「インゴットと粉末とバケツの変更以外アプルーブ」「鉱石以外」「マルチブロックは不要」）に従い、対象範囲の **全251 PNGテクスチャ** をCreate 6.0の配色・面取り文法を維持しながらMinecraft 1.14+準拠へ統一した。

- **金属板・樹脂板（6件）**: Createの圧延板シルエットへ統一（`aluminum_sheet`, `nickel_sheet`, `stainless_steel_sheet`, `titanium_sheet`, `plastic_sheet`, `phenolic_board`）。
- **結晶・水和物塩・フレーク・樹脂・ポリマー・スラグ・汚泥（36件）**: 粉体と混同していた結晶・水和物・フレーク類を独立した多面体結晶・水和物塩・フレーク形状へ分離し、監査レポート（`docs/reports/icon-audit-2026-11-26.md`）の `alumina` と `sodium_aluminate_crystals` の完全一致を解消。これに合わせて非粉末15件を `tools/powder_items.json` の `powders` から `preserved_items` へ移し、`PowderItemsClientCheck.java` の一覧を整合させた。
- **触媒・ろ材・工業部品・電装（29件）**: Create風の真鍮/鋼鉄バンド付きハニカム触媒・リアクターコア・電線ボビン・ガスケット・溶接鉄骨・ボンベへ刷新。`main` の直前コミット `33ae720` で描き直された `incomplete_magnetic_core_memory.png`・`magnetic_core_memory.png` は上書きせず維持。
- **核燃料・照射・廃棄物容器（22件）**: 立体面取りセラミックペレット、斜め45°燃料棒、ボルト留めカートリッジへ刷新。`empty_waste_canister` と `vitrified_waste_canister` は `models/item/*.json` を専用2Dアイコンへ切り替え、監査レポートの空/充填済み重複を解消。
- **道具・保護服・ハザード防具・3DツールUV・装着防具・状態異常（26件）**: バニラ防具アイコン比率へ刷新。3Dツール64×64 UVアトラス5種および装着防具64×32 5種は既存UV配置を維持してCreate材質へ更新。
- **建材・共通外装・単体設備・配管・ポンプ・計装・産業用アーム（132件）**: 旧縦縞光沢グラデーションをCreate 6.0の面取り・1面24色以下基準へ更新。特殊UV配置（`sprayer_casing`, `gas_centrifuge/header`, `gas_centrifuge/band`, `material_pump/*`, `transport/stainless_steel_pipes*`, `vessel_stirrer/*`, `blasting_charge/cartridge_top`）はUV領域を保持。`models/item/industrial_arm.json` は専用の鋼鉄＋ハザード帯アイコン `item/industrial_arm.png` へ切り替え、Createの `mechanical_arm` との完全一致を解消。

## 対象外として無変更で維持したもの

- 鉱石ブロック・蛇紋岩・原石アイテム一式
- マルチブロック構造・構成部材（タービン羽根・エンド含む）一式
- インゴット・中間鋼材15種
- 粉末37種（`tools/powder_items.json` に残る真の粉末30種＋他7種の微粉末）
- 液体・気体・空バケツおよび状態オーバーレイ一式（計122種）
- `33ae720` で更新された `incomplete_magnetic_core_memory.png` / `magnetic_core_memory.png`

## 検証結果と未確認事項

- **PNG形式・色数・固有性チェック（実施・通過）**:
  - 変更した全251 PNGが8-bit RGBA (`color_type=6`) ヘッダーであることを確認。
  - 残る粉末30種が `tools/powder_items.json`・`PowderItemsClientCheck.java` およびバニラ火薬シルエットと完全一致することを確認。
  - `block/chemical/{stainless,cast_iron,brass,copper}.png` が各面15色（≤24色）かつ中央領域8色（≥8色）を満たすことを確認。
  - `block/zirconia_refractory_bricks.png` が9色（≥8色）・平均輝度174.7（>165）・目地行輝度126.0（<150）を満たすことを確認。
  - `alumina` ≠ `sodium_aluminate_crystals`、`empty_waste_canister` ≠ `vitrified_waste_canister`、`industrial_arm` の専用アイコン化を確認し、変更範囲での新規重複は0件。
- **未確認・環境制約**:
  - 作業環境には OpenJDK 21 JRE のみ（JDK 21 の `javac` なし）のため、ローカルでの `gradlew build` および実クライアント起動によるスクリーンショット撮影は未実施。PR上の GitHub Actions（Java 21）でのビルド・GameTest確認を前提とする。
  - `main` 側で既存だった `validate_visual_identity.py` の `StainlessBucketItem` 登録型チェック・`powder_items.json` の既存未登録アイテム差分・`validate_plant_tutorials.py` / `validate_ponder_language.py` の既存エラーは本PR範囲外のため変更していない。
