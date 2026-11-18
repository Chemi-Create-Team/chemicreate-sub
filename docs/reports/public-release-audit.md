# 公開リポジトリへ移す前の確認レポート

- 日付：2026-11-18
- 実装コミット：このレポートと同じコミット（`git log -1 -- docs/reports/public-release-audit.md`で確認）

## 指示

パブリックリポジトリへ移すので、公開して大丈夫か確認する。

## 確認した範囲と結果

| 項目 | 方法 | 結果 |
| --- | --- | --- |
| 秘密情報（APIキー・トークン・秘密鍵・パスワード） | 追跡中の全ファイルと全履歴の差分を、GitHub・OpenAI・AWS・Slackのトークンや秘密鍵などの形で検索 | なし。CIは`github.token`（自動発行）だけを使う |
| 個人のメールアドレス | 追跡中のファイルとコミットの作者・コミッター | ファイル内はなし（`codex@local.invalid`などの架空のものだけ）。**コミット5件の作者が個人のGmailアドレス**（作者名`Mhikeneko`）。ほかのメンバーはGitHubのnoreplyアドレス |
| 個人のパス | `/home/…`・`/Users/…`・`C:\Users\…` | なし |
| 大きなファイル・配布物 | 全履歴のjar・zip・ワールドデータ・最大のファイル | jar・zip・ワールドデータはなし（Gradle Wrapperのjarを除く）。最大は2.7 MBの画像。履歴全体は約106 MB |
| Minecraft本体のテクスチャの複製 | Minecraft 1.21.1のクライアントjarの全テクスチャ（2,953枚）と、追跡中・履歴中の全PNG（1,532枚）をSHA-256で比較 | **1枚あった**：`textures/block/turbine/glass.png`がバニラの`glass.png`と同一。今回削除し、タービンの窓はMinecraft本体の`minecraft:block/glass`を参照するように直した（下記） |
| Create本体のテクスチャの複製 | Create 6.0.10のテクスチャ（1,251枚）と比較 | 同一のものはなし。ただしCreateのテクスチャを色替え・加工した画像は複数ある（生成スクリプトがCreateのjarから読んで加工） |
| ほかのMODのテクスチャ（TFMG・Create Deco） | `docs/quality/material-accent-provenance.json`に記録された38枚のハッシュと、全履歴のPNGを比較 | 同一のものはなし（参照の記録だけ。TFMGのアセットはAll Rights Reserved） |
| 参考写真 | `docs/quality`の画像の出所 | 写真に見えるものはゲーム内の撮影と、生成した参考画像（レポートに記載済み）。外部の写真ファイルは見つからなかった |
| ライセンス | `gradle.properties`・`neoforge.mods.toml` | `mod_license=MIT`と宣言しているが、**リポジトリにLICENSEファイルがない** |

## 修正したこと

- `textures/block/turbine/glass.png`（バニラのガラスと同一）を削除。`client/HighPressureTurbinePartRenderer.java`はMinecraftの`block/glass`を直接参照し、`models/block/empty.json`の参照も`minecraft:block/glass`にした。絵は同じ。
- 確認：`compileJava`成功。クライアントでPonder「断面を並べて作る蒸気タービン」を開き、テクスチャ欠けがないことを確認（窓の拡大表示は未確認）。

## 公開前に決めてほしいこと（未対応）

1. **コミット作者の個人メールアドレス**：5件のコミットに個人のGmailが作者として残っている。公開すると誰でも見られる。消すには履歴の書き換え（強制プッシュ、ほかのメンバーの手元の履歴も作り直し）が必要。今後のコミットはGitHubのnoreplyアドレスにする設定を推奨。
2. **LICENSEファイル**：MITと宣言しているなら、著作権者名を入れたLICENSEファイルを置く。
3. **Createの加工テクスチャの表示**：CreateはMITなので加工・再配布はできるが、MITの著作権表示を含める必要がある（例：`LICENSE`か`NOTICE`に「一部のテクスチャはCreate（© simibubi、MIT）を加工」）。
4. **コミットメッセージのセッションリンク**：127件のコミットに`claude.ai/code/session_…`のリンクがある。ログインしないと開けないもので秘密ではないが、気になるなら知っておく。
5. **開発用の文書**：`docs/specs/`のCodex依頼文・内部の作業記録・`AGENTS.md`なども公開される。問題がなければそのままでよい。
6. `docs/quality/quarry-drive-ranges/assemble.log`はビルドのログで、個人情報は含まない（`.gitignore`の`*.log`より前に追加されたもの）。
