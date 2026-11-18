# 引き継ぎ：開放型の使用済み燃料プール

実装コミット：`97482158e919506ac61619166bf83d278e4ab38e`（`work`）。操作と仕様は [燃料プール](../fuel-pool.md) を参照。

## 実装

- 既存の`fuel_pool`を格子状のラック床へ変更。2×2～9×9セルと、高さ16のCreate工業用鉄壁で形成する開放水槽。各セルは4本の14ブロック燃料棒と16,000 mBの冷却水を保持する。
- アイテムの上下ハンドラー、下面の標準流体ハンドラー、門型クレーンを維持。形成中は全ラックが同じ流体ハンドラーを公開する。流体のシミュレーションは内容物を変更しない。
- 入出力で水量・熱量を保存して各セルへ混合水を均等配分する。在庫照会と排出プレビュー・実排出の流体成分を一致させ、Createが同じ流体として扱えるようにした。
- 使用済み1本当たり1,000 Wのゲーム用残留熱。20 tickごとに一度だけ加熱し、100 ℃の余剰熱を2,260 J/mBで蒸発へ回す。90 ℃以上または水位15,000 mB/セル未満では新規装荷を止める。
- 壁破損・障害物・未ロードを検出して受け入れを止める。壊れた床の棒は一度だけドロップし、そのセルの水は放出扱いとする。他セルの在庫は保持する。
- クレーンは水面へ吊り具を下ろす。搬送中に状態が変わった場合も棒を保持し、格納庫が満杯の新燃料を消失させない。
- 貯水量による水面、14段の棒、16×16の専用ラックテクスチャ、日本語・英語Ponder、最大4行のゴーグル、標準Display Linkの測定項目を追加。
- ビルドを妨げていた削除済みクラスへの未使用import 2行を`SharedMachineCapabilityGameTests`から削除。

## 主要ファイル

| ファイル | 役割 |
| --- | --- |
| `nuclear/FuelPoolStructure.java` | 有界な構造探索、共有キャッシュ、無効化 |
| `block/entity/FuelPoolBlockEntity.java` | セルごとの物資保存、共有給排水、熱量、状態表示 |
| `block/FuelPoolBlock.java` | 床形状、破壊時の処理 |
| `block/entity/CraneDriveBlockEntity.java` | 水面の作業位置、再検証、搬送中の棒の保持 |
| `client/FuelPoolRenderer.java` | 水面と棒の描画 |
| `compat/display/MachineReadings.java` | 新旧燃料棒数、ラック数、残留熱などの測定 |
| `ponder/FuelPoolPonderScenes.java` | 組立・配管・冷却の説明 |
| `gametest/FuelPoolGameTests.java` | 水量・熱量・保存・構造・標準ポンプの10テスト |
| `tools/generate_fuel_pool_assets.py` | モデル、テクスチャ、レシピ、翻訳、Ponder構造の再生成 |
| `tools/test_fuel_pool.ps1` | 燃料プール10件＋クレーン5件の分離実行 |

Javaのパスは`src/main/java/net/createplant/`からの相対パス。開発用クライアントの再現確認は`-Dcreateplant.clientQualityCheck=true -Dcreateplant.fuelPoolClientCheck=true -Dcreateplant.clientQualityTargets=fuel_pool -Dcreateplant.clientQualityLanguage=ja_jp`を使用した。

## 検証結果

2026-11-03（JST）、Java 21、NeoForge 21.1.219、Create 6.0.10-281で確認。

- `gradlew.bat clean build`：成功。既存の単体チェックも成功。
- クリーンビルド後の`tools/test_fuel_pool.ps1 -SkipBuild`：**15/15成功**。給排水・水量と熱量の保存・シミュレーション・混合温度・最大9×9・過大サイズ拒否・破損と修復・保存と再ロード・沸騰・燃料回収・クレーン連携を含む。
- 検証用Minecraftクライアント：**73 Ponderシーンの日本語実行時翻訳と描画チェック成功**。5×7ラック、560,000 mB、使用済み燃料入りの実ワールドを生成し、俯瞰・真上・側面とPonderを撮影して確認。
- アセット生成スクリプトの構文、`git diff --check`：成功。クリーンJARに旧プールの未使用テクスチャと調査用トレースコードが含まれないことを確認。

全体234件を同時実行した検証は全成功ではない。`createPumpConnectsUnderRackAndTransfersRealWater`、変更していない`actualCreatePumpTransfersBetweenSmallCompressors`と`actualAcidFlowWarnsLosesPressureAndBreaksOnlyThePipeWhenEnabled`が失敗した。燃料プールとクレーンだけを分離した15件は複数回成功しており、全体での破壊試験・設定変更等との干渉の有無は未確定。これを機能単体の失敗や全体互換性の合格へ読み替えないこと。

分離スクリプトはビルド出力の対象外GameTestクラスだけを一時的に探索対象外へ移し、`finally`で復元する。ソース・テストアノテーション・テンプレートは変更しない。実行後に残存ファイルがないことも確認した。別のGradleタスクで同じ出力を再生成しながらGameTestサーバーを起動するとテンプレート読込が競合するため、検証は順番に実行すること。

## 制約と次回の注意

- 水面は実際の流体在庫の描画。泳げるバニラ水源ではない。
- 時間依存の崩壊熱モデル、プール由来の被曝、再処理は未実装。一定熱源を外部設備で冷却するゲーム上の設備として扱う。
- 旧`fuel_pool`の登録IDと`Slots`は保持するが、旧1ブロック構成は壁と給水を追加して組み直す必要がある。
- 二相式タンクの上面は蒸気出口。液体給水は底面または液体側面から取り出す。試験のパイプは全接続方向を明示し、意図しない開口へ流さない。
- 大量設備のTPS・フレーム時間の比較、実際のチャンクアンロード／再ロードを伴う長時間試験、マルチプレイヤー操作は未検証。最大サイズの構造・容量確認を性能試験と解釈しないこと。
- Gitの作成者設定が空だったため、今回のコミットだけ`Codex <codex@local.invalid>`を指定した。グローバル設定は変更していない。
