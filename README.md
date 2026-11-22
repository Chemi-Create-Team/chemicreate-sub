# Create: Refinery 'n' Reactors

リポジトリ名はCreate-Plant、mod IDは`create_refinery_n_reactors`です。

旧mod ID `create_heavy_indusstry` からの自動移行はありません。旧IDで保存したワールドや外部データパックは、そのままでは互換になりません。

Minecraft **1.21.1** / **NeoForge** 向けの **Create 6.0.10** アドオンです。Java 21が必要です。開発・動作検証にはNeoForge 21.1.219を使用しています。[Create Diesel Generators **1.21.1-1.3.15**](https://modrinth.com/mod/create-diesel-generators/version/UoPH8lO1)が必須、JEIは任意です。CDGはこのMODのJARに同梱していません。改修後の電力基盤であるCreate: Electro Energetics 1.21.1-1.1.4も引き続き必須です。

圧縮、加熱、反応、冷却、分離を担当する共通設備を、Createのシャフト・ポンプ・配管・バルブ・タンクで組み合わせて工場を作れます。原油・ガソリン・軽油はCDGの流体を共有し、CDGの採掘機・エンジンと直接接続できます。

- [ゲーム内の説明書と進行の実績（最初に何を作るか）](docs/manual.md)
- [モジュール式設備の組み立て・工程・効率・検証範囲](INDUSTRIAL_SYSTEMS.md)
- [小型・大型コンプレッサーの構造・性能・入出力](docs/gas-compressor.md)
- [金属の鉱石と精錬・溶融塩電解炉](docs/metals.md)
- [電力（CEEの回路への接続と各設備の消費電力）](docs/electric-power.md)
- [電動ポンプ](docs/electric-utilities.md)
- [鉛蓄電池とリチウムイオン電池](docs/batteries.md)
- [配管とタンク（材質・腐食・保冷）](docs/pipes-and-tanks.md)
- [冷却塔・復水器・PID制御・核融合保守・MOXとガラス固化](docs/power-plant-expansion.md)
- [設備と工程の思案画面](PONDER_GUIDE.md)
- [金属テクスチャ・配管材質と劣化の設定](docs/quality/transport-and-assets.md)
- [従来の化学設備・肥料](DEVELOPMENT_CHEMISTRY.md)
- [石油化学と実用的な生産物](PETROCHEMISTRY.md)
- [CDG統合の仕様・燃料挙動・検証結果](docs/diesel-generators-integration.md)
- [Create: Deep Seas・Sable・Create Aeronautics との連携（任意、潜水艦の耐圧殻）](docs/deepseas-compat.md)
- [GitHub Actionsでのビルドとタグによるリリース](RELEASING.md)

Java 21で `./gradlew build`（Windowsでは `gradlew.bat build`）を実行すると、配布用JARを `build/libs/` に生成します。

専用サーバーの検証は `./gradlew runGameTestServer`、JEIなしの構成は `./gradlew -PwithJei=false runGameTestServer` です。

## 工場をつなぐ追加工程

- [排水処理](docs/water-treatment.md)：既存の槽で中和・沈殿・ろ過し、洗浄排水を再利用。
- [施設建設機](docs/facility-constructor.md)：設備選択と原子炉のサイズ設定、半透明プレビューと概略図砲方式の建設。
- [アイテム保管庫ハッチ](docs/vault-hatch.md)：保管庫に取り付け、チェスト形式で中身を閲覧・出し入れ。
- [工業製品の出荷](docs/industrial-dispatch.md)：Create保管庫・ファンネル・パッケージ・列車から大量注文を納品。
- [核融合燃料](docs/fusion-fuel-production.md)：重水素・初回三重水素・液体ヘリウムの製造と回収。
- [セメントと工業建材](docs/industrial-building-materials.md)：既存加工設備からコンクリート・防食床・配管架台を生産。
- [横型タンクのボイラー](docs/horizontal-tank-boiler.md)：バーナーの上の横型タンクが、加熱で高圧蒸気、超加熱で過熱蒸気を生成。

温度・圧力と流体ラインの現行手順は[等級ガイド](docs/thermal-grades.md)、移行結果は[段階別進捗表](docs/specs/thermal-grades-plan.md)を参照してください。
