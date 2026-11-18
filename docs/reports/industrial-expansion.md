# 排水・出荷・核融合燃料・建材・管束ボイラーの実装

基準：`f7d2d607`。実装コミットID：`89ac56166617e5db8ed510489f7da25df4ab3723`。

## 実装したもの

| 要望 | 実装と利用方法 |
| --- | --- |
| 排水処理 | 既存撹拌槽3段で中和・凝集沈殿・ろ過。1000 mBの排水を通常水1000 mB＋有限汚泥へ戻し、汚泥を建材工程へ利用 |
| 受注・納品 | Create保管庫に出荷票を付け、通常のファンネル・開梱機・列車・配管から6種の注文を納品。有限量を出荷してエメラルド報酬、Display Link、選択切替・レッドストーン停止 |
| 核融合燃料 | 重水の濃縮と既存電解槽のD₂生成、実RBMKへのLiターゲット装填・照射とT₂抽出、Heの初回回収・精製、回収Heの4.2 K液化 |
| 工業建材 | 既存の超加熱鉢・粉砕・混合・プレス等からセメント、コンクリート、階段・ハーフ、防食床・配管架台を生産 |
| 横向きタンクのボイラー | 新しい管束入り横タンクを3区間以上で組み、前段で蒸発・末端で過熱。350/650 °C・20 barの乾いた蒸気を標準流体能力から搬出 |

新しい独自GUIはありません。加工・保管・搬送は既存設備を再利用し、追加した機能部材には実際の役割を持たせています。既存ブロックの画像・音源の変更は0件で、新規の素材と管束モデルだけを追加しました。

詳細・数値・資料写真の出典・操作は[排水処理](../water-treatment.md)、[出荷](../industrial-dispatch.md)、[核融合燃料](../fusion-fuel-production.md)、[建材](../industrial-building-materials.md)、[管束ボイラー](../tubular-boiler.md)を参照。

## 確認結果

| 確認 | 結果 |
| --- | --- |
| ビルド | 合格。配布JARを生成 |
| 新機能のGameTest | 25件すべて合格：出荷・管束11、実管束配管1、排水3、建材3、核融合燃料7 |
| 既存機能を含むGameTest | 505件中504件合格。全登録監査1件だけ既知の翻訳不足で失敗 |
| 物理・反応 | FluidPhase 25、HaberBosch 1019、ReactorPhysics 1670チェック、ReactorControlが合格 |
| 静的検証 | Ponder構造・翻訳・全348アイテムの外観・粉32種の形状と分類・15件のCI検証が合格 |
| 実クライアント | RTX 4060のハードウェア描画。129シーンの実行時翻訳と、新5機能のPonder描画が成功 |
| サバイバル到達性 | D₂・T₂・He・液体Heを監査対象へ戻し、実際の電解・照射・冷凍設備を必要条件にして合格 |

新規試験では、1,000 mBの有限給水を実Createポンプ・配管で搬入し、650 °Cの蒸気として搬出、停止、再開する間の総量1,000 mBを毎tick検査しています。冷凍機は有限仕事・冷却水への放熱、753対1のHe体積比、1,000 mB初期充填、実Create動力網での過負荷停止・容量復帰、保存を検査しました。排水は温度・物量・熱量を保持し、出力満杯でも入力を消費しません。

## 試験で見つけて直した点

- Heの初期充填でガスがちょうど空になると空流体の温度を読んでいたため、原料待ちとして停止するよう修正。
- RBMKの一部未ロード時に古い出力値でターゲット照射が進み得たため、直近の有効な炉計算と必要チャンクを確認。
- 幅2・3の管束で上面が塞がれる問題を避け、下段の末端外向き面にも蒸気出口を用意。
- 新ブロック増加で耐水監査の旧配置が構造範囲を超えていたため、32×8×32内の2層へ修正。新出荷票も水流で破壊されない物性に設定。
- 外部の標準slab/stairsモデルの異なる親形状を同一画像と誤判定していた外観監査を修正。アイコンへ意味のない違いを加えず、実3D形状で判定。

## 残る既存の問題と検証の限界

`AuditGameTests.everyRegisteredEntryHasAssetsTranslationsAndLoot`は、既存のワールド配置用液体ブロック44種類に英日名88キーが無いため失敗します。基準`f7d2d607`の言語ファイルでも全88キーが未定義であることを照合済みです。今回の新登録の欠落は0件です。対象外の既存翻訳を変更していません。

原料・同位体・交易の数量と機器の寸法はゲーム用の近似で、実際の設備設計や核種収率を再現しません。短時間の標準銅管搬送試験は材料損傷を無効化していません。650 °Cの連続運転には、既存の耐熱・耐圧配管を使います。大規模工場でのFPS/MSPT比較や、通常オーディオ機器での実聴は未実施です。

試験用ワールドのディスク保存待ちが発生したため、最終GameTestは`/tmp`のtmpfsへ移して実行しました。実クライアントとサーバーの同時ビルドは共通のクラス出力に競合したため、最終確認は順番に実行しています。

## 主要ファイルと再実行

- 登録：`CEWaterTreatment`、`CEFusionFuel`、`CEBuildingMaterials`、`CEIndustrialExpansion`
- 操作・処理：`IndustrialDispatchBlockEntity`、`TubularBoilerBlockEntity`、`CryogenicColdBoxBlockEntity`、`BasinElectrolysis`、`FuelChannelBlockEntity`
- 共通反応：`PressureReactionRecipe.conserveHeat`と既存槽のsnapshot確定。省略時falseで既存レシピの動作を維持
- 生成元：`tools/generate_{water_treatment,fusion_fuel,building_materials,industrial_expansion}.py`。共通の翻訳・Ponder・粉・タグは`tools/integrate_industrial_expansion.py`で統合
- [検証データ](../quality/industrial-expansion/validation.json)、[実クライアント結果](../quality/industrial-expansion/client-result.json)

```sh
JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 bash gradlew --no-daemon --max-workers=2 \
  -I tools/white_factory_checks.gradle \
  -Dcreateplant.whiteTestDir=/tmp/createplant-industrial-verified runGameTestServer
bash gradlew --no-daemon --max-workers=2 build fluidPhaseTest reactorPhysicsTest reactorControlTest
```

ログ、配布JAR、SHA-256、新5機能の実クライアント画像は`/workspace/outputs/industrial-expansion/`と同階層の`industrial-expansion-*.log`へ保存しています。生成器を再実行したら統合器と静的検証を再実行してください。

## GitHubで確認できる画像

[新しい5設備の画像ギャラリー](../quality/industrial-expansion/README.md)へ、上記の実クライアント画像5枚を追加しました。出力元PNGとのSHA-256一致、画像寸法1260×740、各8 MiB以下、Markdownの相対リンクを確認しています。今回は文書と撮影済み画像の追加です。
