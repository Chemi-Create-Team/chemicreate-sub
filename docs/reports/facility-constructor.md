# 施設建設機 実装引き継ぎ

2026-11-15。実装コミット：`788f229721e09ae923c43591ece8d52d2dd5782b`。アセット出典・ライセンス表記：`35b8aee9`。作業前に最新mainの`bfec507c`をfast-forwardで取り込み、その3種類の組立基準ブロックを置き換えた。

## 実装と操作

`create_refinery_n_reactors:facility_constructor`を追加。Create 6.0.10の概略図砲の架台・砲身・発射処理・燃料・素材一覧・隣接在庫探索を再利用し、背面に3つの表示を備えた操作部を追加した。右クリック画面の右側で原子炉・核融合炉・冷却塔を選ぶ。原子炉の炉心直径は既存の推奨値5・7・9・11・13 m。設備を切り替えても直径を保持する。

建設機の正面に、実際のモデルとテクスチャを不透明度72/255で描く。完成済みのブロックは消え、障害物は赤く表示する。既存の白い単色ボックス描画は削除した。配置・レンチでの90度回転・選択変更に追従する。概略図砲が自分の周囲2ブロックへ配置しないため、最前列は建設機の3ブロック先。施設の基礎は建設機と同じ高さ。冷却塔は中心位置を正面へ移し、入出口の固定方位は既存の構造検証に合わせて維持する。

選択はサーバー側で検証する。停止中かつ発射済みブロックがすべて着地した場合だけ設備・サイズ・向きを変更できる。内部で生成した配置済み概略図はGUIから取得できず、外部のItemHandlerにも公開しない。公開スロットは素材一覧入力・出力・火薬の3つ。建材は隣接インベントリから消費する。完成時に空の概略図を生成せず、設定画面を開くだけでは建設や燃料消費を始めない。停止中も本・クリップボードへの素材一覧出力は動く。

`rbmk_assembly_base`、`fusion_assembly_base`、`cooling_tower_assembly_base`の登録・アイテム・レシピ・モデル・翻訳・旧Ponderを削除した。旧基準ブロックを置いた既存ワールドではそのブロックが失われる。施設本体の構成ブロックは維持する。ユーザーが指定した建設機GUIの例外をDESIGN.mdへ記録した。

## 主要ファイルと処理

Javaは`src/main/java/net/createplant/`からの相対パス。

- `block/FacilityConstructorBlock.java`：水平4方向の配置、回転、取り外し、内部アイテムの非ドロップ。
- `block/entity/FacilityConstructorBlockEntity.java`：設備選択・直径・保存・内部概略図・停止時の素材一覧・完成状態。標準概略図砲の動作を継承する。
- `inventory/FacilityConstructorMenu.java`、`client/FacilityConstructorScreen.java`：標準メニューのスロット番号を維持し、概略図の2スロットを操作不可にする。右の設備・直径ボタンは標準のメニューパケットでサーバーへ送る。
- `assembly/AssemblyPlan.java`、`AssemblySchematic.java`：既存のRBMK・核融合炉・冷却塔の完全な構造と必要な空間を、選択・サイズ・向きに応じてCreate形式へ変換する。
- `client/FacilityConstructorRenderer.java`：Createの`SchematicLevel`と`ShadedBlockSbbBuilder`を用いた半透明モデル。20tick間隔で設置状態を確認し、変更された16立方区画のメッシュだけを再生成する。未ロードチャンクを強制ロードしない。表示距離96ブロック、有限の表示範囲、弱参照キャッシュ、モデル再読込時の破棄を持つ。砲身と発射物は標準Renderer/Flywheel Visualを再利用する。
- `client/StructureGuideRenderer.java`：建設予定範囲に旧自動ガイドを重ねない。
- `registry/CEAssembly.java`、`CECreativeModeTabs.java`：新ブロック・アイテム・BlockEntity・メニュー・標準ItemHandlerの登録。
- `ponder/AssemblyPonderScenes.java`：配置・半透明予定図・設備選択・建材・素材一覧・開始・停止を説明する新Ponder。核融合炉の構造は実際のAssemblyPlanと共有する。
- `tools/generate_facility_constructor.py`：解決済みCreate JARのモデル・GUIを基に操作部とGUI画像を生成。レシピ・ドロップ・タグ・Ponder構造も生成する。元JARのライセンス表記を`META-INF/licenses/create-assets.md`へ同梱し、Createのコードとアセットのライセンスを区別する。再生成は`python3 tools/generate_facility_constructor.py --create-jar <Create 6.0.10 JAR>`。
- `gametest/AssemblyGameTests.java`、`client/FacilityConstructorClientCheck.java`：対象機能の回帰確認。クライアント確認は`-Dcreateplant.facilityConstructorClientCheck=true`のときだけ有効。

Ponder原稿・実行時キー・日本語/英語/中国語を同時更新した。利用方法は[施設建設機](../facility-constructor.md)。

## 検証結果

- Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10、Java 21。解決済みCreateおよび内包Catnipの逆コンパイルソースで再利用APIと初期化・発射・素材一覧処理を確認。
- `bash gradlew --no-daemon build`：成功。既存の炉物理テスト1668チェックも成功。
- `bash gradlew --no-daemon -I tools/compact_fuel_checks.gradle -Dcreateplant.testClasses=AssemblyGameTests runGameTestServer`：5件すべて成功。3設備の構造形成、5サイズ×4方向の予定範囲、設定値の保存と復元、不正値拒否、建設中の設定ロック、内部概略図へのアクセス拒否、3公開スロット、燃料を入れた停止状態、素材一覧の生成・搬出、レンチ90度回転、新Ponder構造を確認。
- 同サーバーテストで、核融合炉の建材を必要数だけチェストへ供給し、実際の概略図砲tickと飛翔ブロックを最後まで進めた。火薬消費・建材のちょうど全量消費・実際の完成構造・空の概略図を生成しないことを確認。テスト時間短縮のため実tick処理を同期的に進めており、実時間の長時間建設試験ではない。
- `validate_plant_tutorials.py`、`validate_ponder_language.py`、`validate_translations.py`：成功。142 Ponder、3言語4675翻訳キー。
- `mc-debug glxinfo -B`：NVIDIA GeForce RTX 4060、direct rendering有効。
- 実クライアントで右クリックからGUIを開き、実ボタンのコールバックと通常のサーバーパケットで「核融合炉→原子炉5 m→13 m→冷却塔→核融合炉→原子炉13 m」の反映を確認。`run/facility-constructor-client-check.json`の`passed`がtrue。GUI、前方のテクスチャ付き予定図、Ponderを日本語画面で確認し、ワークスペース`outputs/`へ画像を保存した。
- `validate_visual_identity.py`は既存の素材分類9項目で失敗する。対象は`heated_unfinished_steel`、`high_pressure_turbine_unit`、`iron_pipe`、`irradiated_cartridge`、`irradiation_cartridge`、`jacketed_pipe`、`low_pressure_turbine_unit`、`pig_iron`、`steel_slag`。前作業で変更前`0a497ff6`にも同じ失敗を確認済み。この作業では対象外の分類を変更していない。

## 制約・次回の注意

- RBMK13 mと冷却塔の全量実時間建設、実際のチャンクアンロードを挟んだ建設再開、独立した複数クライアントの遅延、JEIなしの画面は未検証。3設備の生成構造はサーバーで検証し、有限材料による全建設は核融合炉で検証した。
- 選択のNBT往復と標準概略図砲の保存処理は確認したが、建設途中のプロセス再起動まで含む長時間試験は行っていない。
- 多数台設置時の比較フレーム時間・TPSのベンチマークは未実施。キャッシュ構造の説明を、計測済みの性能改善と解釈しないこと。
- Create更新時はメニューのスロット番号、標準設定パケットの型判定、SmartBlockEntityの初期化と停止中tick、素材一覧処理、SchematicLevelの座標系とFlywheel Visual登録を確認する。
- 構造変更時はAssemblyPlanと構造検証側を揃える。冷却塔の入出口を回転させる場合は固定方位の構造検証も変更する必要がある。
