# 空バーナー型電熱線ヒーターと工業用加熱器の廃止

2026-11-13。実装コミット：`5345a976a829ea5224e71797554b31f7abbdf74b`。

## 実装内容

電熱線ヒーターの外装を、Create 6.0.10の空のブレイズバーナーと同じかごへ変更した。Create本体の `create:block/blaze_burner/block` をNeoForgeのcompositeモデルの子として参照するため、外装メッシュ・テクスチャは等倍のまま。かごの実OBJの外形は16×15×16ピクセルで、追加した発熱線・セラミック支持・銅端子・ダイヤルもその範囲に収めた。ダイヤル指針の回転中心とブロック形状を合わせた。

出力0～100％、0.1％刻み・Shift中1％刻み、隣接給電・設定共有、有限熱、レッドストーン停止は維持する。発熱線は停止・通常加熱・強加熱で色が変わる。新しい画像は生成せず、かごはCreate本体、内部部材は既存の電熱線ヒーターの材質を使用する。

工業用加熱器の以下の登録と専用処理を削除した。

- `industrial_heater`：外装・配置アイテム。
- `industrial_heater_burner`：形成時のバーナー・EE機器。
- `industrial_heater_chimney`：煙突。
- 組立・外装モデル・専用配置・温度設定・出口圧力設定・燃料/電気による加熱処理。
- 専用レシピ、ドロップ、タグ、クリエイティブタブ、専用Ponder3件、旧設備だけを対象とするテスト・クライアント確認、専用生成器。

共有クラスから旧加熱器の分岐を取り除き、チューブパイプドタンク・熱交換器・ジャケット付きパイプ・流量調節器・小型加熱器は残した。EEと燃料電池の結合試験は現行の40 Ω電熱線ヒーターへ変更した。既存の汎用燃料発熱量の計算と、その化学的な値を確認するテストは維持した。

旧設備を指していたJEI・製造工程概略は現行設備へ更新した。圧力手引きは電熱線ヒーターと槽へ変更し、転炉ガスの手引きは着火済みフレア先端への配管へ変更した。対応する構造NBT、翻訳、生成元も更新した。

主要ファイルは `tools/generate_electric_utilities.py`、`ElectricHeaterBlock.java`、`ElectricHeaterRenderer.java`、`CEHeating.java`、`CEReactor.java`、`ElectroDevices.java`、`ReactorMachineBlockEntity.java`、`ThermalEquipment.java`、`MachineReadings.java` とPonderアセット。現在の操作は [electric-utilities.md](../electric-utilities.md)、熱交換設備は [thermal-equipment.md](../thermal-equipment.md) を参照。

## 最小限の検証

ユーザー指定により、実クライアント起動、スクリーンショット、長時間試験、全GameTestは実行していない。

- `build runGameTestServer`：関連 **8/8件合格**。GameTest 1.224秒、Gradle全体42秒。
- 内訳：`HeaterGroupGameTests` 2件（隣接設定・実EE給電と切断/再接続）、`ThermalAssemblyGameTests` 2件（廃止登録がないこと・熱交換器各方向）、`HeatingGameTests` 1件（現行熱交換器の熱と流体保存）、`CircuitGameTests` 3件（EE電源・電解セル・燃料電池）。
- 日英中2,547キー、Ponder言語139シーンに合格。
- Ponder構造検査：95追加シーン、504説明、237部品リンク、27製造工程を確認。
- 元のOBJから外形を読み、3種類の発熱線モデルの追加部材が外形内に収まることを確認。
- 有効なレシピ・タグ・モデル・Ponderメタデータ・構造NBTに旧3 IDが残っていないことを確認。
- 最後の構造・説明更新後にJARを再作成。`git diff --check` 合格。

```sh
env JAVA_HOME=/workspace/.createplant-jdk21 PATH=/workspace/.createplant-jdk21/bin:$PATH JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 bash gradlew --no-daemon -I docs/quality/heater-output/server.gradle -DtestClasses=HeaterGroupGameTests,ThermalAssemblyGameTests,HeatingGameTests,CircuitGameTests build runGameTestServer
```

## 制約と引き継ぎ

旧3 IDのブロック・アイテムと内部在庫を移行する互換処理は追加していない。**既存ワールドを更新する前に、旧設備の中身を回収すること。** 廃止する指示に従って登録を取り除いているため、そのまま更新すると旧設備は失われる。

外観の実描画、compositeモデルの表示、各方向の見え方・ダイヤルの視認性はユーザー側のデバッグ待ち。形状データの確認を実画面の確認として扱わない。製造プラント全体の連続運転や、既存ワールドのロードも未確認。過去の試験報告は履歴として残しており、そこに記された旧設備は現行仕様ではない。

再生成は `tools/generate_electric_utilities.py` を使う。廃止した2つの専用生成器を復活させず、Createのかごの子モデルへスケール変換を加えないこと。電熱線ヒーターの電力・熱収支や隣接共有を変更する必要はない。
