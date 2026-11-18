# ふた付き槽の外形・点検窓・はしご位置

- 作業ベース：`e5360190`。
- 実装コミット：`c4bb06426e6f4113283c1f75ecf1cf5aefa1a3c5`。
- 環境：Java 21、Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10、NVIDIA RTX 4060。

## 実装

撹拌翼・遠心ロータなどの内部部品によって3×3の槽が八角形になる仕様を削除した。閉じた槽の形状と当たり判定は常に四角形。八角形用BlockState、形状JSON、角モデル8個と描画分岐を削除した。開いた中央ハッチ、内部部品の動き、工程・搬送・保管の仕組みは既存処理を使用する。

点検窓は解決済みCreate 6.0.10のタンクモデルを参照し、槽全体の面中央に8 px幅、最下段・最上段に4 pxの縁を置く。単段・最下段・中間段・最上段のUVを使い分け、Create自身の `fluid_tank_window_single` / `fluid_tank_window` スプライトを使用する。2×2ではブロック境界にまたがる1枚の窓になる。入力シャフトのある面は従来どおり金属壁を保つ。内部の実在庫の流体・アイテムと、極低温時の霜も描画する。

はしごは蝶番の反対側の面を外から見て左端に配置する。設置方角に対する位置は次のとおり。座標は槽の北西端からの相対座標、`w`は幅。

| はしごの面 | x | z |
| --- | --- | --- |
| 北 | w−1 | 0 |
| 南 | 0 | w−1 |
| 西 | 0 | 0 |
| 東 | w−1 | w−1 |

既存ワールドの中央のはしごは、読み込み時にLADDERだけを更新する。コントローラーが未ロードなら保留し、強制ロードしない。内部部品の再配置や在庫の再配分は行わない。旧八角形の状態属性は削除済みのため、新しい四角いモデル・形状を使用する。

Ponderの関連22構造から旧属性を除き、はしご位置を更新した。英日字幕と生成元も四角い槽の説明に合わせた。既存外板のテクスチャは再生成していない。

## 主要ファイル

- `LiddedVesselBlock` / `ReactorStructure` / `ReactorMachineBlockEntity`：外形変更の削除と四角い角への動力接続。
- `LiddedVesselBlockEntity`：はしごの位置・保存済み配置の更新。
- `LiddedVesselWindowModel` / `LiddedVesselRenderer` / `LiddedVesselCTBehaviour`：窓の開口、Create素材、霜の描画。
- `LiddedVesselPlacementGameTests` / `DriveLessVesselGameTests` / `ReactorGameTests`：向き・保存・各内部部品の回帰確認。
- `VesselWorldClientCheck` / `VesselClientCheck`：2×2・3×3の実ワールド表示と、窓を外板が塞いでいないことの確認。
- `tools/generate_lidded_vessel*.py`、関連Ponder生成元、`ponder/plant/*.nbt`：新しい外形・はしごを再現。

## 確認結果

- `build`：成功。
- 関連GameTest：**55件すべて合格**。4種類の内部部品で四角い外形・角の入力軸を確認。幅1～3・全4方向・4段のはしご位置と実際の登攀、旧配置の読み込み、未ロード時の保留、内部部品・水量・熱・アイテムの維持を確認。既存の反応・Basin搬送・排水処理試験も含む。
- 実クライアント：窓なし2×2、窓付き2×2、霜付き2×2、窓付き3×3を生成し、各槽の32,000 mB、鉄8・金4、2段の撹拌翼と停止状態の同期を確認。窓付きセルの描画モデルを調べ、窓の開口を外板が覆わないことを検証した。初回確認で発見したガラスと霜のバッファ切替エラーは描画順を分けて修正し、再実行は成功。
- クライアントの既存検証：日本語の131 Ponderシーンをコンパイル・見出し確認し、既定8対象の描画も成功。
- Ponder静的検証：追加98シーン、英日498組の字幕、148リンク、全構造ターゲットが合格。言語検証は105見出し・共通字幕が合格。
- 外観の静的検証とCI検証用テスト15件：合格。`git diff --check`：合格。

GameTestの再実行例（テスト用ワールドは一時ディレクトリ）：

```sh
bash gradlew --no-daemon --max-workers=2 \
  -I tools/white_factory_checks.gradle \
  -Dcreateplant.whiteTestDir=/tmp/createplant-square-vessels-verified \
  -Dcreateplant.gameTestClasses=LiddedVesselGameTests,LiddedVesselPlacementGameTests,DriveLessVesselGameTests,ReactorGameTests,VesselBasinGameTests,WaterTreatmentGameTests \
  build runGameTestServer
```

## 制約・次回の注意

今回の実画面は高さ2段・正面側からの確認。高さ1・3・4段や全方向の見た目をすべて撮影したものではない。はしごの全方向・4段はGameTestで確認済み。全MOD設備の長時間運転・性能比較や、ユーザーの既存ワールド自体の読み込み試験は実施していない。

[ゲーム内画像](../quality/lidded-vessel-square/README.md)

## 再実行：2026-11-09 12:37–12:38 UTC

ユーザーの再実行依頼により、最新の `main`（`2100847f`）でビルド・関連GameTest・実クライアント確認を再実行した。

- ビルド成功、関連55件すべて合格（GameTest実行時間4.162秒）。
- 実ワールドの4槽で、四角い外形、左端のはしご、窓の開口、流体・アイテム・撹拌翼の同期、極低温の霜を再確認。32,000 mBの保管量も各槽で維持。
- 日本語131 Ponderシーンの見出し・共通字幕と、既定8対象の描画確認が成功。
- Ponder構造・言語の静的検証も再度合格。新たな不具合は見つからず、実装コードの修正は不要だった。
- ギャラリーの画像を今回の実ワールド撮影へ更新した。確認範囲・制約は上記と同じ。
