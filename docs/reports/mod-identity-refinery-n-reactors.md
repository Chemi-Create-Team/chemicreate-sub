# Create: Refinery 'n' Reactors への改名

実施日: 2026-11-12。開始点: `60bae8ce`。実装コミット: `89354871d33aa2147c056ab53214c27c168020de`。

ユーザー指定に従い、表示名を `Create: Reactor 'n' Refinery` から **Create: Refinery 'n' Reactors**、mod IDを `create_heavy_indusstry` から **`create_refinery_n_reactors`** へ変更した。リポジトリ名Create-PlantとJavaパッケージ `net.createplant` は維持した。バージョンは0.1.0のまま。

## 変更内容

- `gradle.properties` の表示名・mod ID、`CreatePlant.MOD_ID` を更新した。
- `settings.gradle` の配布名を `create-refinery-n-reactors` にし、JARを `create-refinery-n-reactors-0.1.0.jar` とした。CIの設定ファイルは既存のまま維持した。リリース自体は発行していない。
- `src/main/resources/assets/create_refinery_n_reactors/` と `data/create_refinery_n_reactors/` へ移し、モデル、テクスチャ参照、音、レシピ、タグ、ワールド生成、戦利品、進捗、Ponderの参照を更新した。Mixin設定2ファイルとmodアイコンのファイル名・メタデータも揃えた。
- Javaの翻訳キー・固定リソースパス、日英中の翻訳キーとクリエイティブタブの名称を更新した。機械の処理・数値・外観は変更していない。
- 生成・検証スクリプト、入力用構造ファイル、開発用Gradle設定、現行の説明書、AGENTS.mdを更新した。過去の完了報告と試験結果は当時の記録として保持した。

リソース2,969ファイルの配置を変更した。153個のNBTを型に沿って読み、138個の名前空間を更新した。文字列の長さ情報も再構築し、全153個について新IDを旧IDへ逆変換した展開後データが元のNBTと完全一致することを確認した。ソースとtoolsの3,819ファイルについて、変更がID・名称の置換だけであることを照合した。バイナリの画像・音声等641ファイルはバイト単位で一致する。これらの数は[名前空間監査](../quality/mod-rename/namespace-audit.json)に保存した。

## 検証

| 確認 | 結果 |
| --- | --- |
| `clean build` | 完了。Java 21のコンパイルと既存の単体試験を実行し、新名称のJARを生成した |
| 全体GameTest | 変更前637件中636件合格、変更後も同じ。新たな失敗なし |
| 翻訳 | 日英中各2,550キーの整合を確認 |
| Ponder言語 | 140シーンの見出しと共有説明を確認 |
| Ponder構造 | 98追加シーン、518組の説明、239部品リンクと構造ターゲットを確認 |
| 音リソース | 11種類合格 |
| JAR | メタデータの表示名・mod ID、Mixin2種、アイコン、assets/dataを照合。展開したNBTとクラスも含め旧ID参照0件 |
| 実クライアント | RTX 4060で新規ワールドを起動。Ponder全140シーンのコンパイル・日本語の実行時翻訳を確認し、制御盤・電解セル・ガスホルダー・熱交換器の4シーンを表示した |

[クライアント結果](../quality/mod-rename/client-result-ja_jp.json)を保存した。確認完了後の終了処理で、以前の作業でも記録している `libEGL_nvidia.so.0` のSIGSEGVが再発し、Gradleの `runClient` は終了コード134で失敗した。`CLIENT QUALITY PASSED: 140 scenes, ja_jp` と終了障害を区別する。

全体GameTestは変更前後とも `VesselFoulingGameTests.aFouledProcessStopsThenResumesAfterOneManualBrush` が失敗した。槽を清掃した後の反応再開・汚れ量を検査する既存の失敗であり、今回変更していない。このため `runGameTestServer` を含む一括コマンドの終了コードは1。ビルド部分の完了と全体試験の失敗を区別する。

`validate_visual_identity.py` は未分類素材5件（`heated_unfinished_steel`、`industrial_heater`、`jacketed_pipe`、`pig_iron`、`steel_slag`）で失敗する。`test_ci_validators.py` も同じ検査を呼ぶため15テスト中5件が失敗する。改名前のコミットから別ディレクトリへ展開したソースでも、同じ項目・同じ件数で失敗することを確認した。今回の改名と無関係なので修正していない。

[変更前後のGameTest比較](../quality/mod-rename/server-comparison.json)、[JAR監査](../quality/mod-rename/jar-audit.json)、[検証ログ抜粋](../quality/mod-rename/verification.txt)を保存した。

再実行:

```sh
env JAVA_HOME=/workspace/.createplant-jdk21 \
  PATH=/workspace/.createplant-jdk21/bin:$PATH \
  JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2 \
  bash gradlew --no-daemon -I docs/quality/mod-rename/server.gradle clean build runGameTestServer

mc-debug bash gradlew --no-daemon -I docs/quality/mod-rename/client.gradle runClient
python3 tools/validate_translations.py
python3 tools/validate_ponder_language.py
python3 tools/validate_plant_tutorials.py
python3 tools/validate_machine_sounds.py
```

クライアントもJava 21を必要とする。Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10、Electro Energetics 1.21.1-1.1.4、CDG 1.21.1-1.3.15で確認した。

## 既存ワールド・外部データへの影響

**旧IDからの自動移行・別名登録は実装していない。** ブロック、アイテム、流体、BlockEntity、エンティティ、コンポーネント、レシピなどの名前空間が変わるため、旧IDで保存したワールドをそのまま引き継げる変更ではない。旧IDを参照する外部データパック、リソースパック、スクリプト、コマンド、Mod固有設定も更新が必要になる。既存ワールドの移行試験は行っていない。

サーバー試験とクライアント確認は新規ワールドで行い、以前のワールドは開いていない。旧ワールドを使う場合は原本を保管し、別途移行作業を行う必要がある。READMEにも互換性の変更を記した。

今回の変更に工程・物理計算・機械操作の追加はない。Ponderは登録先と構造データのIDを移し、説明内容は維持した。Deep Seas同時導入時の実起動、旧ワールドの自動移行、外部パック全般との互換性は未確認。

初回のプッシュはGitHub OAuthの `workflow` 権限不足で拒否された。mod本体の改名に不要なCI成果物ラベル・リリース見出しの変更を取り消し、ワークフローを変更前と同一に戻した。元のローカルコミットは保持している。実装・JAR・試験結果への影響はない。
