# 旧方式ガス圧縮機と旧名の羽根の削除 レポート

## 基準と実装コミット

- 作業ブランチ：`main`
- 作業開始時の基準コミット：`1587f72`
- 実装コミット：`5383246`

## 依頼

- 全機能の確認で見つかった、旧方式（クランクケース式）ガス圧縮機の食い違いを、新方式（小型・大型コンプレッサー）に合わせる。
- 互換用に残しているアイテム・ブロックは削除してよい。

## 削除したもの

### 旧方式ガス圧縮機（クランクケース、低圧・高圧シリンダー、中間冷却器）

| 種類 | 内容 |
| --- | --- |
| Java | ブロック3クラス、BlockEntity3クラス、`CompressorCrankcaseRenderer`、試験ワールド `CompressorPlaytestWorld`、`CompressorGameTests`（4件）。`CECompressor` には安全弁だけが残る |
| 参照の整理 | `CreatePlant`（能力の登録）、`ElectrolysisClient`（描画・試験ワールド）、`MachineReadings`（ディスプレイリンクの読み取り。旧専用の `compressor_flow_1`・`compressor_length_0/1` も削除）、JEIのカテゴリ「ガス圧縮機」「中間冷却器」とそのレシピ表示 |
| 流体 | 段間合成ガスと高温段間合成ガス。旧方式だけが作っていたもの。物性・危険性・冷却時の別名・反応塔の受け入れからも削除 |
| アセット | blockstate 4、ブロックモデル96、アイテムモデル4、`compressor_shapes.json` の旧形状 |
| データ | ルートテーブル4、旧部品から新部品への変換レシピ4、ツルハシのタグ |
| Ponder | 「二段式ガス圧縮機を組み立てる」「大型の二段式ガス圧縮機を組み立てる」（構造NBTと生成スクリプト `generate_crankcase_ponder.py`） |
| 翻訳 | 日英それぞれ70件（中国語1件） |
| 文書・ツール | `docs/gas-compressor-legacy.md`、`docs/compressor-playtest.md`、`tools/create_compressor_playtest.ps1` |

テクスチャ8枚は新方式の圧縮機と共有しているので残した。新方式の往復動部（クロスヘッド）のモデルも残し、その登録を `GasCompressorRenderer` へ移した。

### 旧名の羽根 `turbine_blade`

- 低圧タービンが互換で受け付けていた旧アイテム（表示名は低圧タービン羽根と同じ）。
- 削除した範囲：アイテム、モデル、テクスチャ、翻訳、Ponderの部品一覧、`SteamTurbineBlock.acceptsBlade` の互換分岐。

### 使われていない字幕

- 新方式の圧縮機・冷却・加熱のPonderに、手順数より多い字幕キーが残っていた。日英それぞれ17件で、旧部品の説明を含むものもあった。
- 大型コンプレッサーの手順2から、旧部品への言及を削った。

## 新方式に合わせた文書

- `INDUSTRIAL_SYSTEMS.md`：ガス圧縮機の行、吸入・吐出の位置、アンモニアの工程を、小型・大型コンプレッサーの説明に置き換えた。
- `docs/gas-compressor.md`、`README.md`、`PONDER_GUIDE.md`、`PETROCHEMISTRY.md`：旧方式への言及とリンクを削除した。
- 生成スクリプト：
  - `generate_compressor_assets.py` は、共有のテクスチャ・形状と合成ガスの混合レシピだけを作る。
  - `generate_unified_compressor_assets.py` は、旧レシピ・旧表示名を書かない。
  - `generate_uranium_assets.py` から、JEI旧カテゴリの翻訳を除いた。

## 既存ワールドへの影響

- 旧方式の圧縮機を置いたワールドを開くと、そのブロックは消え、中のガスと潤滑カートリッジも失われる。
- 段間合成ガスを入れたタンクも、その流体が消える。
- ユーザーの指示（互換用は削除してよい）に基づく。

## テスト

| 確認 | 結果 |
| --- | --- |
| `bash gradlew build runGameTestServer`、`-PwithJei=false runGameTestServer` | 両方で267件すべて合格（旧方式のテスト4件を削除） |
| CIの検証スクリプト4本 | 合格 |
| 実クライアントの品質チェック | Ponder全88シーンの字幕・タイトルが合格。自MODのモデル読み込み警告なし |
| 監査GameTest | 削除後も、登録内容・翻訳・ルートテーブル・クリエイティブタブに欠けなし |

## 未検証・制約

- 旧方式の圧縮機を置いた実際のワールドを読み込む確認はしていない。消えたブロックの在庫は戻らない。
- 過去の完了レポートは当時の記録として書き換えていない。旧方式に触れている箇所がある。
