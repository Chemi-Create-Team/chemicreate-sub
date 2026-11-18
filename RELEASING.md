# GitHub Actionsから配布する

`v`で始まるバージョンタグをプッシュすると、GitHub Actionsがビルド・検証後にGitHub Releaseを自動作成します。通常のブランチへのpushやPull Requestでは、ビルド・テストだけを実行します。

## リリースの手順

1. `gradle.properties` の `mod_version` をリリースするバージョンに設定し、変更をコミット・pushします。
2. そのコミットに、`mod_version` と一致するタグを付けてpushします。たとえば `mod_version=0.1.0` なら次の操作です。

```sh
git tag -a v0.1.0 -m "Release v0.1.0"
git push origin refs/tags/v0.1.0
```

タグは、このリリース用ワークフローを含むコミットに付けてください。タグのpush自体は、この設定変更では実行しません。通常はmainへレビュー・取り込みしたコミットを使います。

3. GitHubの「Actions」で `Build and Release` のタグ実行が成功するのを待ちます。
4. リポジトリの「Releases」から成果物をダウンロードします。

## 添付されるファイル

- 配布用のNeoForge MOD JAR
- `create-plant-release.zip`：同じJARだけを格納したZIP
- `SHA256SUMS`：JARとZIPそれぞれのSHA-256

自動生成の変更履歴とともに公開します。`0.2.0-beta.1` などのバージョンでは、同名の `v0.2.0-beta.1` タグを使い、プレリリースとして公開します。非公開リポジトリのReleaseは、リポジトリを閲覧できる利用者だけが取得できます。

## 検証と権限

公開前に次をすべて実行します。

- タグと `mod_version` の一致
- Java 21でのビルドと付属チェック
- 7本の資源・モデル・思案データ検証
- JEIあり・なしの専用サーバーGameTest
- JARのMOD ID・バージョン・依存関係・実行クラス・CRC
- ZIP内のJARと元のJARの一致、公開ジョブへ転送したファイルのSHA-256

通常のビルドは `contents: read`、Releaseを作成するジョブだけは `contents: write` を要求します。GitHub Actionsが発行する `GITHUB_TOKEN` を使うため、個人のPATをSecretsへ追加する必要はありません。組織のポリシーでActionsや書き込み権限が禁止されている場合は、管理者による許可が必要です。

公開済みReleaseは再実行しても上書きしません。同じタグの下書きが残っている場合も自動上書きせず、明示的なエラーで停止します。その場合は既存の下書きと失敗ログを確認してください。成果物を修正する場合はバージョンを上げて新しいタグを付けます。既存のタグを書き換えたり、強制pushする必要はありません。

ローカルで公開せずに梱包を確認する場合は、ビルド後に次を実行できます。

```sh
python3 tools/prepare_release.py --tag v0.1.0
```

生成先はGit管理対象外の `build/release/` です。Python 3.11以降を使用してください。
