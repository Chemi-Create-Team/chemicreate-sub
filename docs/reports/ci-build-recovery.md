# CIビルド復旧の引き継ぎ

日付：2026-11-03。実装コミット：`d3dd1ec96c72d8aeeadcccb22dc884d3ce482239`。
最終検証基点：main `3165f210de2d4a235b3202256e9e294c8e8c8b2d`。元の修正基点は`569d6dff8fb98e114cc04d32b8aebe6545601dc0`。並行作業のゲージ警報・蒸気凝縮ループ・Ponder完成状態表示・翻訳を保持した。

## 原因

[失敗ジョブ](https://github.com/Chemi-Create-Team/Create-Plant/actions/runs/41654043571/job/125979502518)と同じJava 21.0.13 / Gradle 8.14.3でPKIXエラーを再現した。
公式Mavenの `*.createmod.net` 証明書は2026-11-03 07:23:28 UTCに失効していた。OpenSSLは中間証明書を含むチェーンを受信して期限切れ（コード10）を報告し、curlも証明書検証エラー60で失敗した。信頼ストアへの追加やTLS検証の無効化は行っていない。

## 依存の取得

- Createは同じ6.0.10リリースの[公式Modrinth配布](https://modrinth.com/mod/create/version/UjX6dr61)、固定ID `UjX6dr61` を使用する。
- [公式の公開処理](https://github.com/Creators-of-Create/Create/blob/mc1.21.1/dev/build.gradle)が指定するプロジェクトID `LNytGWDc` を確認した。配布JARのGit-Hashは[公式6.0.10タグ](https://github.com/Creators-of-Create/Create/releases/tag/mc1.21.1-6.0.10)と同じ `ac0c444d9828da3453ae8cc65338e8de063286fb`。
- Modrinth APIのSHA512と実ファイルが一致した：`11cc8fc049d2f67f6548c7abfada6b82a3adb5c7ca410a742de04bbca76e03862c518721b88d806f6e6d768a4d68531fdb903a85859b25d1484d550cc7bafd4b`。
- このJARのJar-in-Jarに含まれるPonder `1.0.82+mc1.21.1`、Flywheel `1.0.6`、Registrate `MC1.21-1.3.0+67` をコンパイル時にも使う。実行時の同梱ライブラリと一致し、追加の開発用MODを解決しない。
- 元のMaven表記 `6.0.10-281` から公式6.0.10配布への切替であり、別のCreateリリースへの更新ではない。旧Maven JARとのバイト単位の同一性は、証明書失効のため未確認。
- 空のGradleキャッシュから `clean build --info` を完走し、ログに `maven.createmod.net` へのリクエストがないことを確認した。

## 後続の検証エラー

依存解決後、以前は実行されなかった3検証にも不整合があった。

- 反応槽2シーンの高さを実NBTの7へ合わせ、統合シーンの冷却と観測の説明を分けて3ステップにした。熱交換器拡張の指示先を実在する角の支持架台へ修正し、生成元にも反映した。
- 削除済みpressure_reliefのヘッダー生成を除去し、必須教材の対象を現行装置IDへ合わせた。
- バケツモデルを登録情報と照合し、17種の過不足・画像寸法・固有性を検証する。DESIGN.mdの液体バケツの規則は6種に厳密適用し、登録型がGasBucketItemの11種は現行の気体容器として扱う。画像は変更していない。
- PNGの5種類の標準行フィルターを復元して画素を検証する。完成品の比較に実行時のウラン粉末の着色を含め、SequencedAssemblyItemの製造途中品を完成品から除外する。
- 既存生成元が共通形状を使うlarge_compressorとcompressor_mechanismだけを明示的な共通部品として記録する。第三のアイテムが同じ形状になれば検証は失敗する。
- 読み取り入力だけをメモリ上で変更する11件の回帰テストをCIへ追加した。実在しない指示先、翻訳欠落、完成品重複、バケツの過不足、液体バケツの金属部変更、着色の欠落・重複、共通部品への別アイテム混入を拒否することを確認した。

## 実行した確認

Java 21.0.13、Gradle Wrapper 8.14.3。各Gradleコマンドは `--no-daemon --max-workers=2` で実行した。

| コマンド | 結果 |
| --- | --- |
| `bash gradlew build` | 成功。Haber-Bosch、FluidPhase、ReactorPhysicsを含む |
| 空キャッシュで `bash gradlew --info clean build` | 成功。失効Mavenへの接続なし |
| `python3 tools/validate_plant_tutorials.py` | 65シーン、293対訳、72部品リンクと全指示先が成功 |
| `python3 tools/validate_ponder_language.py` | 70実行時ヘッダーと日英共有文が成功 |
| `python3 tools/validate_visual_identity.py` | 156完成品、想定外の重複0、液体バケツ6・気体容器11 |
| `python3 tools/test_ci_validators.py` | 11件成功 |
| `bash gradlew runGameTestServer` | 最新main上で244件すべて成功 |
| `bash gradlew -PwithJei=false runGameTestServer` | 最新main上で244件すべて成功 |

PNG復元はNone/Sub/Up/Average/Paethおよび混在行の固定入力でも確認した。
ローカルはPython 3.12、GitHub ActionsはPython 3.14。回帰テストのPath.glob差し替えはcase_sensitive・recurse_symlinksを標準実装へ転送し、3.14のrglobからも呼び出せる。
ローカル実行ログと差分はUbuntu作業ディレクトリの `../evidence/` に保存した。GitHub上の実行結果は修正PRのChecksで確認する。

## 制約と次回の注意

GameTestは専用サーバーの検証であり、クライアント画面・描画性能の目視検証ではない。
TLSの更新待ちや独自の証明書例外へ戻さず、Create更新時は公式配布ID・ハッシュ・同梱3ライブラリのバージョンを確認する。
