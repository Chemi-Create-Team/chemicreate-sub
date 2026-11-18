# 3×3ふた付き槽のCreate式連結上面・下面

- 作業ベース：`bbbc1247`。
- 実装コミット：`6b36f89d`。
- 環境：Java 21、Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10、NVIDIA RTX 4060。

## 仕上がり

3×3の上面と下面を、Createタンクの鉄灰色版に近い連結模様へ変更した。外枠・面取り・斜めの金属模様が槽全体につながり、下面もブロックごとの9枚の枠から大きな1枚の板になる。上面の中央1マスは従来の開閉ハッチで、閉じたときは周囲の模様につながる。

リポジトリにある鉄灰色・リベット付きのCreateタンク素材（`block/tank/ammonia_separator_top` と `_connected`）を、同じ鋼製圧力容器の共通の端板素材として参照する。由来は既存の[タンク素材の記録](haber-bosch-reaction.md)。新しい画像の複製や既存設備全体の再生成は行わない。

## 実装

- `PlantTankModels.VESSEL_CAP`：既存の鉄灰色タンク用の端面接続シートを共有。
- `LiddedVesselCTBehaviour`：3×3の端面にCreateの `RECTANGLE` 接続方式を適用。接続先は既存の同一槽の判定を使う。
- `LiddedVesselWindowModel`：3×3の床の外向き下面だけを端板素材へ差し替える。上端の旧縁の上向き面を取り除き、新しい天板との重複を防ぐ。はしご・床内面の描画は対象から除外する。
- `LiddedVesselRenderer`：天板の各16×16部分へ、Createの接続コンテキストで選んだ64×64シートの各タイルを割り当てる。中央ハッチにも中央タイルを使い、開閉時も外周の固定板を保つ。描画密度は1ブロック16 px。
- `tools/generate_lidded_vessel.py`：専用の旧3×3天板画像の生成を終了。不要になった `top_3.png` を削除した。
- `VesselWorldClientCheck`：比較用に本家Createの3×3タンクを組み、上面・下面の各セルを比較。上面、下面、ハッチ開放を撮影する。

2×2の外観、側面の素材・窓・はしご、中央だけの底面入力は既存仕様を使用する。Ponderも共通の描画を使うため、新しい端板で表示される。

## 確認

- 実クライアントで、Create本家の3×3タンクと、上面9セル・下面9セルの接続タイル番号が**全18か所で一致**することを確認した。比較対象には本家の実際に焼き込まれたモデルのUVを使用した。
- 上面・下面・中央ハッチ開放を撮影し、模様のつながりを目視確認。
- 窓なし2×2、窓付き2×2、霜付き2×2、窓付き3×3の実在庫（水／液体窒素32,000 mB・鉄8個・金4個）、形成状態、窓の開口、左側のはしご、底面中央だけの軸を確認。
- 日本語131 Ponderシーンの見出し・共通字幕検証と、既定の関連5対象の描画確認が成功。
- `build`、生成元のPython構文確認、`git diff --check`：成功。

実画面は北側に蝶番を置いた3×3・高さ2段を中心に確認した。全高さ・全方角の画像を撮影したものではない。今回の検証範囲はクライアント描画と接続用UV、ハッチ表示、ビルド。

再確認時は `ClientQualityCheck` の `createplant.clientQualityCheck=true`、`createplant.vesselWorldClientCheck=true`、`createplant.vesselDriveClientCheck=true` をクライアント起動用のsystemPropertyへ設定する。今回の起動設定・ログ・ビルド済みJAR・画像は `/workspace/outputs/vessel-tank-caps/` に保存。

[ゲーム内比較画像](../quality/lidded-vessel-tank-caps/README.md)
