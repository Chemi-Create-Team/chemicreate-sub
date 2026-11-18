# 施設のサイズ・炉心高選択 引き継ぎ

2026-11-15。実装コミット：`a3ae66f29074798139226a86bbe92ec977ec736c`。作業開始時のmainは`860b8695`で、pull時に追加更新はなかった。

## 変更内容

施設建設機の右側パネルへ寸法の選択を追加した。

| 設備 | 選択肢 | 実際の外形 |
| --- | --- | --- |
| 核分裂炉 | 炉心直径5・7・9・11・13 m、炉心高3・7 m | 遮蔽込み直径9・11・13・15・17 m、全高11・29 m |
| 核融合炉 | 直径11・15・19 m | 各高さ8 m |
| 冷却塔 | 直径17・25・33 m | 各高さ24・36・48 m |

画面下部に実際の幅×奥行×全高を表示する。設備ごとのサイズと核分裂炉の高さを独立して保持・保存する。選択を変えると前方の半透明予定図、内部概略図、素材一覧が同じ寸法へ更新される。停止と飛翔ブロック着地後だけ変更でき、サーバーが設備・サイズ・高さの範囲とプレイヤーの操作距離を検証する。

核分裂炉は既存`ReactorPhysics.Layout`の小型・標準構造を使い、上部管、下部給水構造、キャップ、遮蔽の出口と駆動部も高さへ追従する。単に描画だけを短くする処理ではない。燃料棒は炉心高に対応する既存のものを使用する。

核融合炉は内側プラズマ半径を3・5・7ブロックとして、従来の2×2チャンネルを維持しながら水平断面を広げる。周囲の容器、ブランケット、磁場コイル、支持、配管口、真空ポンプ、クレーンの保守位置、ガイド、プラズマ描画、描画範囲も実寸を使用する。コントローラーは保存済み幅を優先し、最大3候補の構造を検証して実際の寸法を判定する。

冷却塔は半径8・12・16と高さ24・36・48の3候補。喉部の高さは`round(height × 26 / 36)`、喉部半径は`radius × 7 / 12`、上端半径は`radius × 8.5 / 12`として従来の双曲面式へ渡す。配管口を各半径へ合わせる。既存の2048セル単位の検証を維持し、候補が不一致なら次の寸法を検証する。未ロードの候補により小さい設備の検証が止まらないよう、未形成時は次候補へ進む。強制チャンクロードは追加しない。蒸気位置、構造変更の検出範囲、描画範囲も実寸に合わせる。

核融合炉・冷却塔は建設機から独立して寸法を判定するため、手組みと建設機撤去後にも対応する。両設備の定格処理量とタンク容量は従来どおり。核分裂炉の出力・内容積は既存の炉心高に応じた計算を利用する。

## 主要ファイル

Javaは`src/main/java/net/createplant/`からの相対パス。

- `assembly/AssemblyPlan.java`：寸法の範囲、全構造の生成、サイズ・炉心高・向きを含む概略図ファイル名。
- `block/entity/FacilityConstructorBlockEntity.java`：設備ごとの選択、高さ、保存、予定図と内部概略図。
- `inventory/FacilityConstructorMenu.java`、`client/FacilityConstructorScreen.java`：設備・直径・炉心高のボタン、外形寸法、サーバー検証。
- `fusion/FusionStructure.java`：3寸法の上限付き固定ジオメトリキャッシュ、プラズマ経路、実構造判定。
- `block/entity/FusionReactorControllerBlockEntity.java`、`client/FusionReactorRenderer.java`、`guide/FusionGuide.java`、`block/entity/CraneDriveBlockEntity.java`：検出幅に従う接続・保守・表示。
- `block/CoolingTowerStructure.java`、`block/entity/CoolingTowerBlockEntity.java`：塔の寸法別生成と分割検証、保存、既存流体搬送。
- `ponder/AssemblyPonderScenes.java`、`tools/data/ponder_lessons.json`、3言語のlang：炉心高と3設備のサイズ説明。Ponderでは小型核分裂炉の予定図へ切り替える工程も追加。
- `gametest/AssemblyGameTests.java`、`client/FacilityConstructorClientCheck.java`：寸法・形成・保存・実建設・GUIの検証。

新しい登録ID、ブロック種、画像、依存関係は追加していない。従来のサイズの生成構造と利用中の設備を維持する。既存の施設建設機に追加NBTがなければ、炉心高7 m、核融合炉11 m、冷却塔25 mへ戻す。

## 検証

- `bash gradlew --no-daemon build`成功。既存の炉物理1668チェックも成功。
- `bash gradlew --no-daemon -I tools/compact_fuel_checks.gradle -Dcreateplant.testClasses=AssemblyGameTests,FusionGameTests,PowerPlantGameTests,CompactRbmkGameTests runGameTestServer`：36件すべて成功。
- 建設機の9テストでは、直径・高さの独立設定とNBT往復、設備切替時の保持、旧NBTの既定値、不正・建設中の操作拒否、核分裂炉の最小・最大直径×2高さの形成とキャップ接続、核融合炉3サイズ×4方向の形成・閉じたプラズマ経路・配管能力・保守位置、冷却塔3サイズの自動判定・実ポートからの給排水・冷却・再読込後の再形成・外殻撤去時の停止を確認。
- 直径19 mの核融合炉を有限のチェスト建材と火薬で、実際の概略図砲tick・飛翔ブロックにより完成させた。建材の正確な消費と完成構造を検証した。同時テストの構造と干渉しない高さに配置し、開始範囲が空であることも前提として検証する。時間短縮のためtickを同期的に進めている。
- `validate_plant_tutorials.py`、`validate_ponder_language.py`、`validate_translations.py`成功。142 Ponder、3言語4685キー。
- 実クライアントで右クリックからGUIを開き、実ボタンと通常のメニューパケットで「核分裂炉13 m・炉心高3 m→核融合炉19 m→冷却塔33 m」を選択し、切替後の全設定保持を確認。`facility-constructor-client-check.json`の`passed`と`rememberedDimensions`がtrue。
- 日本語GUIの炉心高ボタンと外形17×17×11 m、対応する半透明予定図、更新したPonderの寸法説明を実画面で確認。`mc-debug glxinfo -B`でNVIDIA RTX 4060の直接描画を確認。画像・JSON・ログ・配布JARをワークスペース`outputs/`へ保存。

## 制約と次回の注意

- 大小設備の定格出力差、冷却塔の処理量差は今回の変更に含めていない。定格を変える場合は熱収支・補機・燃料・冷却系もまとめて検証する必要がある。
- 冷却塔全体と核分裂炉全体の実時間での建設完走、チャンクを実際にアンロードした後の建設再開、独立した複数クライアント間の遅延、長時間のプロセス再起動試験は未実施。全建設の検証対象は核融合炉19 m。
- 最大サイズが複数台ある場合のTPS・フレーム時間の比較ベンチマークは未実施。キャッシュと検証予算を維持したことを計測済みの性能改善と扱わない。
- 共通外観検証にある既存の素材分類9項目の問題は[前回レポート](facility-constructor.md)を参照。この作業では該当する素材登録・画像を変更していない。
- 寸法を増やす場合はGUIの最大5ボタンとパケット範囲、構造判定の候補上限、描画・プラズマ・保守位置、概略図ファイル名、保存値の範囲、既存サイズの形成テストを同時に更新する。
