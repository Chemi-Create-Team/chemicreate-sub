# 履歴：撤回した白い外装と閃緑岩ケーシング

> **全体白化は撤回済みです。** 2026-11-09の後続指定「白っぽくしたの全部戻して、閃緑岩ケーシングは残して」により、既存設備の外装は白化前の`c26c2198`へ復元し、新しい閃緑岩ケーシングを保持します。以下は撤回前の実装と検証の記録です。現行の外観・生成手順として使わず、[現在の方針](../quality/block-surfaces.md)と[ケーシングの使い方](../diorite-casing.md)を参照してください。

実装コミット：`9358d64d`（Add diorite casing and a white industrial exterior palette）。作業日：2026-11-09。開始時は`c26c2198`で、リモートと一致する状態から作業しました。

## 実装

当時のユーザー指定により、外装の主面を白系へ変更しました。その時点の方針を`DESIGN.md`と`docs/quality/block-surfaces.md`へ反映していました。既存の形状、UV、透過部分、機械の動作は維持しました。

- 新ブロック`create_heavy_indusstry:diorite_casing`はCreate標準`CasingBlock`。閃緑岩1個へ安山岩合金1個を手適用して作れます。同じ標準レシピをDeployerが利用します。登録、クリエイティブタブ、採掘・Create casingタグ、ドロップ、英日表示、Ponderを追加しました。
- CreateのCT APIを使い、隣接面の枠を連結します。本体16×16、OMNIDIRECTIONALシート128×128。256通りの隣接状態を47種類の有効タイルへ対応させ、内角も描きます。Createの原画像は転用していません。インデックス規則のコード出典とMIT通知は`tools/create_casing_ct.py`・`tools/licenses/Create-MIT.txt`にあります。
- mod側の既存5レシピは、新しい`machine_casings`タグにより安山岩・閃緑岩両ケーシングを受け付けます。対象は小型コンプレッサー、統合圧縮機構、パルス抽出塔、核融合炉計器卓、原子炉制御卓区画。Create本体のレシピは変更していません。既存4生成器のレシピ定義も更新済みです。
- 既存外装PNG74枚を変更し、新規画像3枚を追加。共通白塗装1枚を194モデルから参照します。対象は槽・タンク、炉制御卓、核融合炉外装、タービン車室、ラジエーター、塔、クレーン、加熱・圧縮設備、被覆配管等。石目と白塗装を分け、暗い枠・継ぎ目と銅・真鍮の差し色を保持しました。
- 地金・原料、流体、燃料、羽根・軸・内部部品、コイル、計器・警告灯の色を保護。レビューで見つけた摺動クロスヘッド2モデルと陽極ハブ2モデルへの塗装は除外し、原本と完全一致させました。
- 新PonderのCreateタンクでは、BEの`Window`が未設定だと水描画が抑止される点を修正。窓を有効化し、実8,000 mB在庫から標準の液面を初期化しています。実在庫・液面を検査し、最終実画面で青い水を確認しました。

## 当時の主なファイルと再現性

登録は`registry/CEBlocks.java`・`CEItems.java`・`CECreativeModeTabs.java`、クライアントCTは`client/CasingModels.java`。Ponderは`ponder/DioriteCasingPonderScenes.java`と`tools/generate_diorite_casing_ponder.py`です。

当時の外装生成元は`tools/generate_white_factory.py`と`tools/white_factory_materials.json`でした。この2ファイルは全体白化の撤回に伴い削除済みです。以下のコマンドは当時の記録です。Pillowと、原本`c26c219873700d8aa1d99ae5c85f864d7bd39e0e`を含むGit履歴を使い、固定原本から選別した塗装面を再配色していました。

```sh
python3 tools/generate_white_factory.py
python3 tools/generate_white_factory.py --check
```

当時は個別設備の旧生成器を実行した後に、この全体白化処理を実行していました。実モデルの素材参照を調べて対象を選び、既存モデルの形状・UV等は変更前と同一であることを機械的に比較しました。現在のケーシング画像は`tools/generate_diorite_casing.py`で専用に生成します。

## 確認結果

| 確認 | 結果 |
| --- | --- |
| Java 21 / Gradle `build` | 成功。最終は5秒、関連compile・resources・jar・checkを完了 |
| 既存の数値テスト | FluidPhase 25、HaberBoschProcess 1,019、ReactorPhysics 1,670、ReactorControlすべて合格 |
| Audit/Crafting GameTest | 11件すべて合格。専用サーバー読込、全modレシピの読込、実サバイバル右クリックの変換と1個消費、Deployerレシピ変換、既存5枠への両ケーシング互換を検査 |
| 生成元との一致 | `--check`合格。94出力対象を照合（原本から画素が変わらない対象も含む） |
| 資産比較 | 既存変更PNG74枚の寸法・アルファ保持。194モデルは素材参照以外同一。原料アイテムPNGと指定した軸・羽根・内部素材等は原本とbyte一致 |
| CT配置規則 | 256隣接状態、47有効タイル、同じindexでの描画マスク衝突なし |
| Ponder静的検査 | `validate_plant_tutorials.py`・`validate_ponder_language.py`合格 |
| アイテム識別・素材検査 | `validate_visual_identity.py`合格。既存の許可済み共通部品以外の完成アイテム重複なし |
| 実クライアント | NVIDIA RTX 4060でハードウェア描画。118シーンの登録・日本語表示検査合格 |
| 実ワールド撮影 | ケーシング単体/2×2壁とCreate比較、2×2×2槽、圧力/極低温タンク、2×2×14分留塔、完全構造のトカマクを7視点で撮影。実BEの結合状態・窓・炉形成を照合 |
| Ponder撮影 | 新ケーシング、RBMK制御卓、タービン、小型コンプレッサーを撮影。タンク窓修正後に新ケーシングを再撮影し、実8,000 mBの水表示を確認 |
| 独立レビュー | 対象選別、再生成、裸機構の除外、CT初期化、専用サーバー境界、Ponder、opt-in境界を確認。未解決の明確な不具合なし |

GameTestは一時の新規ディレクトリへ絞って実行しました。再現コマンドと7視点・Ponder画像は[画像と確認手順](../quality/white-factory/README.md)にあります。`tools/white_factory_checks.gradle`でウィンドウ寸法とGameTestクラス絞り込みを設定できます。

## 当時の確認範囲と制約

実クライアントは画像・結果JSONと`CLIENT QUALITY PASSED`を保存した後、終了処理で環境既知の`libEGL_nvidia.so.0+0xb56ae` SIGSEGV（exit 134）を起こしました。過去の`vessel-basin-operations.md`等と同じ終了時障害です。描画検査の合格をクライアントプロセス全体の正常終了とは扱っていません。

今回の変更は外観とケーシング追加です。全機械の長時間稼働、ネットワーク性能、既存の核融合プラントの運転試験を再実施したという意味ではありません。展示設備は停止状態で撮影しています。CTの全隣接状態は規則を検査し、実ワールドでは単体と2×2壁を描画確認しています。

`WhiteThemeWorldClientCheck`は`createplant.clientQualityCheck`と`createplant.whiteThemeWorldCheck`を両方有効にした開発時だけ動き、新規の使い捨てワールドを作ります。通常プレイには干渉しません。撮影時の落下と出力先作成不足は修正済みです。

ビルド済みJARとセッションログは`/workspace/outputs/white-theme/`へ保存しました。利用者向けにはローカルパスだけを案内せず、リポジトリ上の画像ページを案内してください。
