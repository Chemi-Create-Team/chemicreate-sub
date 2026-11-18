# Task: 汎用多成分混合流体システム（Generic Chemical Mixture Fluid System）の実装

## 1. 概要・目的
Minecraft 1.21.1 / NeoForge / Create アドオン環境において、**あらゆる組み合わせの液体（バニラ流体・本Mod流体・他Mod流体）を任意の比率で混合・保持・輸送・描画できる汎用混合流体システム**を実装してください。

実装前に必ずリポジトリ内の既存コード（`build.gradle`、既存の `Fluids` / `DataComponents` / `BlockEntity` 登録クラス、Create連携クラス、既存テスト）を確認し、プロジェクトのパッケージ構成・命名規則・登録スタイル（`DeferredRegister` または `CreateRegistrate`）に完全に準拠させてください。

---

## 2. 技術要件・制約（NeoForge 1.21.1 準拠）
- **NBT禁止:** `FluidStack` の成分保持には旧来の `CompoundTag` ではなく、必ず NeoForge 1.21.1 の `DataComponentType`（`Codec` + `StreamCodec`）を使用すること。
- **絶対量（mB）ベース管理:** 浮動小数点の丸め誤差による液量消失・増殖を防ぐため、成分データは割合（`float`）ではなく**成分流体IDごとの絶対量（`int mB`）**で保持し、常に `FluidStack.getAmount() == 全成分のmB合計` を維持すること。
- **端数誤差ゼロの分割:** 混合液を一部抜き出す（`drain`）際は、**最大剰余法（Largest Remainder Method）**を用いて `1mB` の切り捨て誤差・総量ズレを完全に防ぐこと。
- **Create パイプ互換性:** Create の流体パイプ・ポンプ・タンクで輸送および保持が可能であること。また、純度が 100%（成分が1種類のみ）になった場合は元の純粋流体（`FluidStack`）へ自動復元（アンラップ）できるオプションを備えること。

---

## 3. 実装コンポーネント仕様

### 3.1 `MixtureContents`（Record / DataComponent データ構造）
- フィールド: `Map<ResourceLocation, Integer> amounts`（キー: 元となる単一流体のレジストリID、値: 含有量 mB）
- コンストラクタ正規化: `0mB` 以下のエントリを除外し、不変マップとして保持する。
- 必須メソッド:
  - `int totalAmount()`: 全成分の mB 合計を返す。
  - `MixtureContents merge(MixtureContents other)`: 2つの成分マップを合算した新しい `MixtureContents` を返す。
  - `SplitResult split(int extractAmount)`: 指定量（mB）を現在の比率通りに抽出し、`extracted`（抜き出し分）と `remaining`（残量分）のペアを返す。最大剰余法により `extracted.totalAmount() == extractAmount` および `extracted.totalAmount() + remaining.totalAmount() == this.totalAmount()` を厳密に保証する。
- `CODEC` および `STREAM_CODEC` を定義し、`DataComponents` に `mixture_contents` として登録する。

### 3.2 汎用混合流体 `chemical_mixture` の登録
- ベースとなる流体 `chemical_mixture`（Source / Flowing / FluidType）を登録する。
- バケツアイテムは不要（または必要に応じて無効化）とし、物理特性（密度・粘度など）は標準的な水溶液として設定する。

### 3.3 `MixtureHelper`（変換・復元・フィルタリングユーティリティ）
- `MixtureContents extractContents(FluidStack stack)`:
  - `stack` が `chemical_mixture` であれば保持している `MixtureContents` を返す。
  - 単一流体（水、希硫酸、他Modの液体など）であれば、その `Fluid` の `ResourceLocation` と `stack.getAmount()` から1成分の `MixtureContents` を生成して返す。
- `FluidStack createStack(MixtureContents contents, boolean unwrapIfPure)`:
  - `totalAmount() <= 0` の場合は `FluidStack.EMPTY` を返す。
  - `unwrapIfPure == true` かつ `contents.amounts().size() == 1` の場合、レジストリから元の単一流体を取得し、その純粋な `FluidStack` として復元して返す。
  - それ以外の場合は `chemical_mixture` の `FluidStack` を生成し、`MIXTURE_CONTENTS` コンポーネントをセットして返す。
- `boolean isMixableLiquid(FluidStack stack)`:
  - 気体（`FluidType#isLighterThanAir()` または本Mod独自の気体タグ・判定ロジックがある場合はそれに従う）を除外し、混合可能な液体かどうかを判定する。

### 3.4 `MixtureFluidTank`（汎用混合タンク・`IFluidHandler` 実装）
攪拌槽・浸出槽・反応タンク等の `BlockEntity` に組み込める汎用 `IFluidHandler` クラスを実装してください。
- コンストラクタ引数: `int capacity`, `boolean unwrapPureOutput`, `Runnable onContentsChanged`
- `fill(FluidStack resource, FluidAction action)`:
  - バニラや Create 標準の `FluidTank` と異なり、**コンポーネントが異なる `chemical_mixture` 同士や、単一流体と `chemical_mixture` の追加入力を拒否せず受け入れる**こと。
  - 空き容量（`capacity - contents.totalAmount()`）の範囲内で受け入れ、既存成分と新規成分を `merge()` して更新する。
- `drain(int maxDrain, FluidAction action)` / `drain(FluidStack resource, FluidAction action)`:
  - `split()` を用いて比率通りに抜き出し、`unwrapPureOutput` の設定に従って `FluidStack` を返す。
- NBT シリアライズ用メソッド（`readFromNBT` / `writeToNBT`、または `HolderLookup.Provider` 対応の `save` / `load`）を実装し、チャンク保存・同期時に成分データが維持されるようにする。

### 3.5 クライアント色ブレンド＆Create エンジニアゴーグル表示
1. **動的色ブレンド（`IClientFluidTypeExtensions`）:**
   - `chemical_mixture` の `getTintColor(FluidStack stack)` において、`MixtureContents` 内の各成分流体IDから元流体の色（`IClientFluidTypeExtensions.of(fluid).getTintColor(pureStack)`）を取得し、液量（mB）による RGB 加重平均で色をブレンドして返す。
   - 元流体の色がデフォルト白（`0xFFFFFFFF`）で区別しにくい場合（例: 水のバイオーム依存色など）は、水系フォールバック色（例: `0xFF3F76E4`）等を適切に扱う。
2. **エンジニアゴーグル表示ヘルパー（`MixtureGoggleHelper` 等）:**
   - Create の `IHaveGoggleInformation` を実装する `BlockEntity` から呼び出せるフォーマットメソッドを用意する。
   - 混合液の総量（mB）に加え、含有量の多い順に各成分の翻訳名（`FluidType#getDescription()`）、量（mB）、割合（%・小数第1位まで）を `List<Component>` へ整形して出力する。

---

## 4. テストおよび完了条件
1. **ユニットテストの実装:**
   - `MixtureContents` に対する単体テストを作成し、以下をすべて検証すること：
     - 異なる成分の `merge` 動作と `0mB` エントリの自動除外。
     - `split` による抜き出し時に、割り切れない比率（例: `333mB : 333mB : 334mB` から `100mB` 抽出など）でも総量誤差が `0mB` であること。
     - 全量抜き出し時の空状態遷移。
     - `MixtureHelper` による単一流体のラップおよび純度 100% 時の自動アンラップ動作。
     - `MixtureFluidTank` への異種流体連続 `fill` と `drain` の整合性。
2. **ビルド・テスト確認:**
   - 既存の Gradle ビルドおよびテストコマンド（`./gradlew test` 等）を実行し、コンパイルエラーや既存テストの破壊がないことを確認すること。
3. **最終報告:**
   - 作成したクラス一覧、既存コードとの接続ポイント、および今後の反応システム（混ぜた瞬間の化学反応や蒸留分離）をフックするための拡張ポイントを簡潔に報告すること。
