# 汎用多成分混合流体システムの実装レポート

- 実装日：2026-11-17
- 実装コミット：`b5c18360dafa7b5e0706412673cfed7ee882ee73`
- 上流取り込み後のビルドコミット：`719fad1ddba57284b5b519be2a6a9eea5f6c5204`（上流 `823fa61e` の温度計修正、`481a006b` の硫酸製造ルートを維持）
- 環境：Minecraft 1.21.1 / NeoForge 21.1.219 / Create 6.0.10 / Java 21
- 成果物：`create-refinery-n-reactors-0.1.0-chemical-mixtures.jar`、6,949,915 bytes
- SHA-256：`c396faeb6fb6b4247a9d65d910fce6346e88de7e63d64e608f7491c5f95794d2`

## 実装内容

添付仕様の汎用混合液を実装しました。内部部品のない既存のふた付き槽で異種液体を混ぜ、Createの通常パイプ・ポンプ・タンクで保持・輸送できます。専用機械・GUIは追加していません。処理用内部部品のある槽では既存の個別入力を維持し、既に生成された混合液は共通のキャリアとして扱います。

`MixtureContents` の不変マップを量の正本とし、成分IDごとの整数mBをData Component `mixture_contents` に保存します。0以下を正規化し、int範囲を超える合計は拒否します。分割時はlongで積を計算する最大剰余法を使い、同点はID順で決定します。抜き出し側を配分して残り側は各成分から差し引くため、両方の合計だけでなく各成分の量も保存します。

## 新規クラス

| クラス | 役割 |
| --- | --- |
| `mixture/MixtureContents` | 整数mB・正規化・merge/split・Codec/StreamCodec |
| `mixture/MixtureData` | 各成分の熱・放射性核種・外部ModのData Componentパッチ |
| `mixture/MixtureFluid` | キャリアのSource/Flowingと限定的な型識別 |
| `mixture/MixtureHelper` | ラップ、純粋流体への復元、液体判定、成分の復元、混合、分割 |
| `mixture/MixtureFluidTank` | 異種流体の充填・抜き出し・シミュレーション・標準保存契約 |
| `mixture/MixtureTransfer` | ネイティブタンクとの共通充填処理 |
| `mixture/MixtureGoggleHelper` | 全成分の名前・mB・割合と4行以内の概要 |
| `registry/CEMixtures` | 流体型・source/flowing・2つのData Component登録 |
| `client/MixtureClient` | 成分本来の色のmB加重平均、水色フォールバック、上限256件の再読込対応キャッシュ |
| `compat/display/MixtureDisplaySource` | Create Display Linkへ全成分を出力 |
| `ponder/MixturePonderScenes` | 槽への投入、混色、配管輸送、表示を教えるPonder |
| `mixin/FluidTankAccess` | ネイティブ在庫の更新と通知を各1回だけ行うアクセサ |
| `mixin/MixtureStackMixin` | nativeコピー・縮小に絶対量を追従 |
| `mixin/MixtureTankMixin` | nativeタンクへ成分比の異なるキャリアを充填 |
| `mixin/MixturePipeConnectionMixin` | キャリアの配管ルーティング判定 |
| `mixin/MixtureFlowSourceMixin` | 1mBプレビューで欠ける成分を元の全組成で検査 |
| `client/MixtureClientCheck` | フラグ付き起動限定の有限量によるネイティブ表示検証 |
| `gametest/MixtureGameTests` | 保存・ネットワーク・輸送・分割・表示の8件 |
| `src/test/.../MixtureContentsTest` | 乱数ケースを含む6004項目の量保存検証 |

## 既存コードとの接続

- `CreatePlant`：登録の入口。成分は永続CodecとネットワークStreamCodecの両方を登録。
- `GradeFluidTank` / `ThermalFluidTank` / `LiddedVesselBlockEntity`：既存在庫・温度情報・混合操作・4行ゴーグルへの接続。
- `NativeFluidNetworkMixin` / `GradePipeMixin` / `GradeConnectivityMixin` / `GradeTankMixin`：Createの分岐、連結、容量変更、解体・再結合での成分保存。FluidStack全体の比較・ハッシュは変更せず、キャリアの配管判定だけに限定。
- `PipeMaterial` / `TankGrade` / `FluidHazards` / `PressureValveBlockEntity`：各成分の材質・温度・圧力制限を検査。少量の酸などを混ぜても制限を回避できない。
- `RadiationSources`：個別成分の核種と量を参照して放射線量を集計。既存保存形式も標準FluidStackのコンポーネントとして復元。
- `CEDisplays`：既存槽・通常／各等級タンクで「混合液の成分」を選択可能。
- `WorldLiquidOpenEndHandlerMixin`：ワールドに置けないキャリアを開放端で消費しない。
- `ElectrolysisPonderPlugin`、Ponderの言語生成元、混合液の専用生成スクリプト：日本語・英語・中国語の使い方と再生成可能な構造を追加。

## 確認結果

- `clean build` 成功。混合液単体6004項目、原子炉物理1668項目、原子炉制御の既存検証に成功。
- 関連GameTest 83件成功。水・自Mod・CDG流体、連続fill/drain、SIMULATE、満杯、1mB連続抽出、Codec保存、熱／核種、通常Createポンプと分岐、マルチブロックの解体・再結合、Display Linkを検証。
- 熱／核種を含むFluidStack全体のStreamCodec比較を追加した後、混合液8件を再実行して成功。
- 上流の硫酸ルートを取り込み、翻訳・Ponderの競合を両方のキーを保持して解消。再ビルドと、混合液・電解槽・吸収塔・反応槽・バケツ監査の51件が成功。
- 翻訳4967キー×3言語、149 Ponderヘッダーとページ別字幕、既存構造検証が成功。
- RTX 4060上のネイティブクライアントで、4成分×2500mBを通常ポンプからCreateタンクへ輸送。サーバーとクライアントの全量・全成分一致、日本語名、25.0%表示、混色 `0xFFA7D1DB`、新Ponderの実際の描画を確認。
- ネイティブ検証結果の保存とワールド保存後、NVIDIA EGL終了処理でSIGSEGV（134）。ゲーム内の検証は成功したが、runClientプロセスの正常終了は未達。関連ログを成果物に添付。

根拠・再実行手順・画像は `docs/quality/chemical-mixtures/` を参照してください。

## 今後の拡張点・制約

反応・蒸留は `MixtureHelper.extractContents` / `components` で在庫を読み、`MixtureContents.split` / `merge` で物量を確定し、`createStack` で残量・生成物を戻せます。装置のレシピ再評価は `MixtureFluidTank` の `onContentsChanged` へ接続できます。新しい反応や混合物蒸留のレシピ自体は今回の仕様に含まれず、追加していません。

純粋流体への自動復元は `unwrapIfPure` / `unwrapPureOutput` で選べます。気体は混合対象外です。キャリアにバケツやワールド流体ブロックはありません。温度・核種には既存の同一流体の混合則を使い、同一IDで互換性のない外部Mod独自のプロパティ同士は、情報を失わないよう充填を拒否します。異なるIDのパッチは保持します。

大規模設備の性能測定、全外部Modの独自コンポーネントの組合せ、音声、他描画Modとの共存は未検証です。既存全体のモデル品質監査は今回の水系スプライトの変更範囲外です。Create更新時は `NativeFluidNetworkMixin` の2番目のsetAmountと、タンク分割時の容量調整が補完分割のままであることを重点確認してください。
