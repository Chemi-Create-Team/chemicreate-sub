# 横置き管束ボイラーの外装と断面サイズ

基準コミット：`0c826d34`。実装コミットID：`4c75764263414aea9f6351d11aed77c0157b0a6e`。

## 変更

ユーザー指定に合わせ、外装とアイテムの描画を通常の横型流体タンクと共通化しました。ブロックの全48状態は、通常タンクと同じCreateのタンクモデル・回転を参照します。側面と端面の連結テクスチャも同じ`HorizontalTankCTBehaviour`で描画します。専用の開放フレームモデル4点と管テクスチャ1点は撤去し、生成器も通常タンクの定義を使う形へ変更しました。

断面1×1・2×2・3×3で、X・Z両軸に延ばせます。長さは3区間以上で、3区間なら2×2は12部材、3×3は27部材です。蒸発・過熱の処理は既存のものを利用し、断面2・3では下段末端の外向き面から蒸気を取り出せます。加熱で350 °C、超加熱で650 °C、20 barの仕様です。

Ponderに断面2×2と3×3の組立例を追加し、通常タンクの外装・給水・末端の蒸気出口を示します。Ponderの仮想ブロックにも、実際の形成時と同じcontroller・幅・長さを与えています。

## 確認

- 通常の横型タンクとの全48状態・アイテムモデルの一致を確認。
- `TubularBoilerSizeGameTests`を4件追加。断面2/3×X/Z両軸の実5区間を形成し、controller・容量・全セル所属・実Blaze Cake加熱・並列管の650 °C/20 bar・質量/熱量・SIMULATE・給排水・出口満杯・再開・保存復元・切断後の停止を確認。
- 既存の出荷・管束11件と実Create給水/蒸気搬出1件を含む、関連16件すべて合格。
- ビルド、Ponder構造と英日翻訳、全アイテムの外観監査、15件のCI検証が合格。外観監査にはユーザー指定の通常横型タンクとの意図的な共用を記録。
- RTX 4060を使う実クライアントで131シーンの確認が成功。1×1・2×2・3×3の管束ボイラーと通常の横型タンクを描画して、外装と連結を確認。GitHubの[画像ギャラリー](../quality/industrial-expansion/README.md)へ新しい3画像を掲載。

関連する実装は`client/PlantTankModels.java`、`ponder/PlantPonderScenes.java`、`tools/generate_industrial_expansion.py`と新サイズ試験です。既存のボイラー処理を変更せず、サイズ別試験で対応を確認しています。

再実行は`tools/white_factory_checks.gradle`を利用して、`createplant.gameTestClasses=IndustrialExpansionGameTests,TubularBoilerSizeGameTests,TubularBoilerFlowGameTests`でGameTestを実行します。実クライアントとサーバーのGradleビルドは順番に実行してください。
