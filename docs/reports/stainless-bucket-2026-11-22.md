# ステンレスバケツとステンレス鋼板のテクスチャ

2026-11-22。ユーザー指定：
- 「ステンレスバケツを追加して。レシピは普通のバケツの鉄をステンレスにしたやつ」
- 「耐性はステンレス準拠で」
- 「ステンレスの板のテクスチャをcreateの鉄の板のテクスチャを変えたやつにして」

実装コミット：本レポートと同じコミット

## 仕様
- 新アイテム`stainless_steel_bucket`（ステンレスバケツ、16個まで重なる）。レシピはバケツと同じV字で、材料は`#c:ingots/stainless_steel`3個。
- 入れられる液体は、ステンレスタンクの等級（`TankGrade.STAINLESS`）が「安全」と判定するものと同じ。
  - 液体だけ（気体は不可）。
  - 低温〜超高温（溶岩も可）。
  - 加圧まで。
  - 耐食性は酸・ハロゲン・塩基。熱酸・フッ化物は入らない。
- 汲む方法：ワールドの液体源（このMODの液体、水、溶岩、他MODの液体）、Createの注液口・アイテム排液口など液体ハンドラー、ディスペンサー。
- 満たしたバケツは、その液体の通常のバケツアイテムに目印のデータ部品`stainless_bucket`が付いたもの。空にすると（設置・液体ハンドラー・クラフトの残り・ディスペンサーのどれでも）ステンレスバケツに戻る。
- 見た目：
  - 満たしたバケツは、ステンレスの外装に液体の色の中身（`item/state_stainless_bucket`）で描く。
  - 色の付かない液体（溶岩など）は、液体ブロックの地図色を使う。
  - ツールチップに「ステンレスバケツ」と表示する。
- 空のバケツのツールチップは、ステンレスタンクと同じ定格（温度・圧力の目盛り、Shiftで範囲と耐食性）を表示する。

## 実装
- `item/StainlessBuckets`（判定・印付け・空にしたときの戻り先）、`item/StainlessBucketItem`（ワールドからの汲み取り）、`item/StainlessBucketWrapper`（空のバケツの液体ハンドラー）。
- `WorldLiquidBlock.pickup`を判定関数で受けるようにし、`pickupStainless`を追加した。
- 空にしたときにステンレスへ戻す箇所：
  - `FluidBucketWrapperMixin.setFluid`
  - `ProcessBucketWrapper.setFluid`
  - `StatefulBucketItemMixin`（`getEmptySuccessItem`と`getCraftingRemainingItem`）
  - `ProcessBucketItem.emptyBucket(ItemStack)`
  - ディスペンサー（このMODの液体バケツ、バニラの水・溶岩バケツ）
- クライアント：
  - `StatefulBucketModelMixin`でモデルを差し替える。
  - `StainlessBucketColorMixin`（ItemColors）で中身に色を付ける。色の処理がない他MODのバケツにも効く。
  - `PipeQualityOverlay`でツールチップを表示する。
- テクスチャ：
  - バケツは`tools/generate_world_liquids.py`で、バニラのバケツをステンレスの鋼塊・鋼板と同じ配色（つや消しの横筋）に変えたもの。
  - ステンレス鋼板は`tools/generate_stainless_steel.py`で、Createの`iron_sheet`を同じ配色に変えたもの。以前の元絵はプラスチック板。
- クリエイティブタブ：耐食合金バケツの隣に置いた。
- Ponder「バケツと設置できる液体」：
  - ステンレス鋼塊とステンレスバケツの2場面を追加した（日英中、`tools/data/ponder_lessons.json`にも）。
  - ステンレスバケツのPonder対象に追加した。
- 翻訳（日英中）：アイテム名「ステンレスバケツ」「Stainless Steel Bucket」「不锈钢桶」。

## 確認
- GameTest：全723件合格。
  - 追加：ステンレスバケツの等級（熱酸・蒸気を拒否、塩基・水を受け入れ）。
  - 追加：シミュレーションで中身が変わらないこと。
  - 追加：液体ハンドラー・バニラの水バケツのハンドラー・設置・クラフトの残りでステンレスに戻ること。
  - 追加：ディスペンサーでの汲み取り・注ぎ、印付きの水バケツ。
  - 書き換え：`MixtureGameTests`の槽の気体出口のテスト。mainに入った`0215b80`（気体を混合物のまま出す）と矛盾していたため、上下の口がどちらも混合物を比例して出す形にした。名前は`anEmptyTankAndItsOutletsGiveTheWholeGasMixture`。
  - 書き換え：同テストのゴーグル行数の確認を「6行以上」にした（前回の4行制限撤廃で行が増えたため）。
- 実クライアント（Xvfb）：ホットバーで次を確認した。
  - 空のステンレスバケツ、苛性ソーダ・水・溶岩入りのステンレスバケツ（それぞれ白・青・赤の中身）。
  - 耐食合金バケツ、鉄バケツ、新しいステンレス鋼板・鋼塊との違い。

## 未確認・注意
- Ponderの実際の再生は見ていない。`tools/validate_ponder_language.py`は、変更前から`guide.manufacturing_pvc.step_4`で失敗する（今回の変更とは無関係）。
- 他MODの液体バケツをディスペンサーで注いだ場合は、そのMODの処理のまま鉄バケツが残る。バニラの水・溶岩とこのMODの液体は、ステンレスに戻る。
- 手に持ってワールドから汲む操作と、Createの注液口・アイテム排液口を通す操作は、実際の画面では試していない（液体ハンドラーとしてはGameTestで確認済み）。
- 生成スクリプトの全体を実行すると、今回と無関係なバケツのテクスチャや翻訳の並びが変わる。そのため今回はステンレス関連の関数だけを実行した。
