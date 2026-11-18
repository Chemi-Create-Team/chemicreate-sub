# 伸縮式ガスホルダーの復元

実施日: 2026-11-12。開始点: `322e81c0`。実装コミット: `9a5f70c42eb970709f400ae602a31947c94786d3`。

前回の撤去で伸縮式まで削除したのは誤りだった。ユーザーの「伸縮式ガスホルダーは戻して。消してはダメです」という訂正に従い、削除前の `28a33dd7` から伸縮式と必要な連携を復元した。旧球形方式の撤去、電磁石の標準レシピ、添付画像を使う鉱物テクスチャは維持している。

## 復元内容

- 白い金属外装とCreate標準タンク風の接続モデル。幅1×1～7×7、高さ2～8段。
- ガス量で内側から順に上下する3段の筒。可動部の描画、CreateのContraption・衝突判定。
- 側面・下面からの標準配管接続、同じ幅の層を一括で積む操作、窓の切替、パージ、障害物による停止。
- 有限の貯蔵量・一定圧力・搬入搬出、汚染混合ガスと混合反応、フレア処理、保存・同期。
- レシピ、ドロップ、採掘タグ、クリエイティブタブ、ゴーグル・Display Linkの出力、専用Ponderと日英中の翻訳。
- 対応する生成スクリプトと、伸縮式のGameTest・実クライアント確認コード。

主要な復元ファイルは `registry/CEGasBell.java`、`block/GasHolderBlock.java`、`block/entity/GasHolderBlockEntity.java`、`block/GasHolderGeometry.java`、`contraptions/GasHolderSleeve*.java`、`client/GasHolderRenderer.java`、`chemistry/WasteGas.java`、`ponder/GasBellPonderScenes.java`。共通のガス配管・圧力・物性・計器にも必要な照会を戻した。登録ID、保存キー、容量・伸縮・物性の計算方法は削除前と同じ。

`DESIGN.md` に、廃止対象は旧球形だけで伸縮式を廃止してはいけないことを追記した。現行の操作文書と設備一覧を直し、[前回の撤去レポート](gas-holder-retirement-and-mineral-sprites.md)には訂正を明記した。過去の試験ログは当時の結果として残している。

## 検証

| 確認 | 結果 |
| --- | --- |
| `build` | 成功 |
| 関連GameTest | 136件すべて合格。伸縮式24件、負荷試験1件、配管・安全弁・毒性・通常タンク・計器・レシピ・設備一覧・ガイド・Ponderの回帰を含む |
| 7×7を16基 | 高さ2、可動筒48体。搬入・排出を繰り返して合計6,272,000 mBを保持。全基の形成・全筒の生存も確認 |
| 既存変更の保持 | 鉱物関連32ファイルは直前のコミットと一致。電磁石の上書きJSONなし。旧球形のコード・登録は復活していない |
| 配布JAR・外観 | 伸縮式・可動筒・汚染ガス・レシピ・Ponderを収録。外観関連37ファイルは削除前とバイト単位で一致 |
| 翻訳・Ponder検査 | 日英中2,545キー、140シーン。参照・翻訳の整合を確認 |
| 実クライアント | NVIDIA RTX 4060で7×7×3を確認。1,176,000 mBを投入し、3体の筒が0→2.5→5→7.5→0 mと同期して伸縮し、全量を回収。Ponder全140シーンのコンパイル・実行時翻訳と専用シーンの表示を完了 |

[負荷試験の生データ](../quality/gas-holder-restore/load-result.json)と[JAR・外観監査](../quality/gas-holder-restore/jar-audit.json)を保存した。負荷試験のループ時間には同時に存在する試験設備やGCなども含まれ、単独設備の性能比較ではない。

確認範囲は上記の関連136件。今回、通常の全GameTestは再実行していない。前回記録した無関係の槽清掃試験は変更していない。

実画面の[途中状態](../quality/gas-holder-restore/partially-extended.png)・[満充填状態](../quality/gas-holder-restore/fully-extended.png)、[クライアントの数値結果](../quality/gas-holder-restore/client-holder-result.json)、[Ponder確認結果](../quality/gas-holder-restore/client-ponder-result-ja_jp.json)、[試験ログ抜粋](../quality/gas-holder-restore/verification.txt)を保存した。画像はComputer Useで取得した。排出後の0 mへの収納は同期位置・空在庫の自動確認で検証した。

クライアント内の確認は完了したが、終了時には既知の `libEGL_nvidia.so.0` のSIGSEGVが再発し、`runClient` のGradleタスクは終了コード134で失敗した。確認結果と終了障害を分けて記録する。

再現用コマンド:

```sh
bash gradlew --no-daemon -I docs/quality/gas-holder-restore/server.gradle build runGameTestServer
mc-debug bash gradlew --no-daemon -I docs/quality/gas-holder-restore/client.gradle runClient
python3 tools/validate_translations.py
python3 tools/validate_ponder_language.py
python3 tools/validate_plant_tutorials.py
```

Java 21、NeoForge 21.1.219、Create 6.0.10、Electro Energetics 1.21.1-1.1.4を使用した。サーバーの確認タスクは今回の関連18クラスを指定し、通常は除外される `GasBellLoadGameTests` も実行する。

## 制約・未確認事項

- ユーザーの実ワールドは開いていない。保存形式の互換性は復元したコードと保存・再読込試験で確認するが、実セーブデータの復旧作業は行っていない。
- 旧球形、外付けガイドレール、旧基点、接着屋根方式は復活させていない。
- この復元は削除前の動作を戻す変更であり、新しい物理モデルや性能改善は加えていない。
