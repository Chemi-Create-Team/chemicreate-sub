# 炭化木材の所持クラッシュの再現調査

実施日: 2026-11-10

状態: **現行の所持・操作試験は正常。ユーザー報告の条件は未再現で、原因や解消済みかは未確定。**

調査用実装コミットID: f31c4c3d0aaba6a9e74efc6e896f3f1ddc34f772

操作試験拡張コミットID: 5ae01edf585f7dc24d113f6465010a44ff9c6eaf

## ユーザーから確認できたこと

- 対象は、破壊時に得られる木炭ではなく、黒い立方体の「炭化木材」ブロック。
- 正確な発生条件は不明。クラッシュログは取得できない。
- 追加MOD一覧の画像を受領した。フォルダーは93ファイルと表示されている。
- 画像のCreate Plantのファイル名は `create-heavy-indusstry-0.1.1.jar`。現行ソースの `mod_version` は `0.1.0`。ファイル名だけでは中身の差やビルド元を判断できず、入手元は不明。
- テストは他のメンバーが行ったため、詳細な条件は不明。所持中の別の操作が引き金の可能性があり、報告後の更新で解消した可能性もある、との追加説明があった。

## 標準構成で実施した再現試験

最初の標準構成の調査の基準は `22bc24b6`。Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10、CEE 1.1.4、CDG 1.3.15、JEIを含むこのリポジトリの標準構成を使用した。この初回調査の開始時と当該記録の保存前に `git fetch origin main` で更新がないことを確認した。

実クライアントを2回起動し、以下の経路を実際のゲーム描画とサーバー同期を通して確認した。

| 条件 | 結果 |
| --- | --- |
| メインハンド64個・オフハンド1個を持つ | 正常 |
| 一人称・三人称で描画する | 正常 |
| オフハンドだけに炭化木材を持つ | 正常 |
| インベントリを開く | 正常 |
| 通常・詳細ツールチップを生成する | 正常 |
| 軸・原木由来・炭化段階のBlockStateデータを付けて持つ | 正常 |
| 元の木材・残り時間を含むBlockEntityデータを付けて持つ | 正常 |
| 上記保存データ付きの炭化木材を実際に設置する | 正常 |
| サバイバルへ切り替えて所持する | 正常 |
| 炭化木材のPonderを開く・日本語全136シーンを検査する | 正常 |

2回目は `runClient` が1分37秒で正常終了し、条件別の画像と `charred-wood-client-result.json` を保存した。`build` も6秒で成功し、`git diff --check` は合格した。GPUコンテナの既知の終了時問題を避けるため `__GL_THREADED_OPTIMIZATIONS=0 mc-debug ...` を使用した。既存のローカルクラッシュログには今回の炭化木材に該当する記録はなかった。

## 追加した調査用コード

- `client/CharredWoodClientCheck.java`: 新規ワールドで所持・描画・説明文・保存データ・設置を順に確認する、明示指定時だけ有効な実機検査。
- `client/ClientQualityCheck.java`: 上記検査を `createplant.charredWoodClientCheck` から呼び出す。通常プレイには適用しない。

実行例:

```sh
__GL_THREADED_OPTIMIZATIONS=0 \
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Dcreateplant.clientQualityCheck=true -Dcreateplant.charredWoodClientCheck=true -Dcreateplant.clientQualityTargets=charred_wood' \
mc-debug bash gradlew --no-daemon --max-workers=2 runClient
```

JDK 21と、日本語の開発用 `run/options.txt` を使う。全件GameTestは実行していない。今回の調査では、実際に報告されたクライアント側の所持・描画経路の確認を優先した。

## 次に必要な情報

受領した一覧の主要な描画・最適化・表示系MODを導入して追試した。元のjar、設定、ワールド、操作手順が揃った完全再現ではない。追加MODが原因だと断定したものではない。

クラッシュの原因を裏付ける例外や再現手順がまだないため、炭化木材のゲーム側の登録・モデル・BlockEntity・炭化動作を変更して修正済みと扱うことはしていない。今回のコミットは調査と再現検査の追加であり、バグ修正のコミットではない。

## MOD一覧受領後の追試

追試前のコードは `fa38ab0d`。`origin/main` に更新がないことを確認した。外部MODはローカルの `run/mods` と既存の `-PwithDeepSeas=true` で導入し、製品の依存関係は変更していない。

| 追加したMOD | 版 |
| --- | --- |
| Sodium / Iris / Sodium Extra | 0.8.13 / 1.8.14-beta.1 / 0.9.5 |
| Jade / Untranslated Items | 15.10.6 / 1.21.1-1.19 |
| ModernFix / Lithium / FerriteCore | 5.27.27 / 0.15.4 / 7.0.3 |
| EntityCulling | 1.11.3 |
| Quark / Zeta | 4.1-488 / 1.1-40 |
| Supplementaries / Moonlight | 4.0.2 / 3.7.2 |
| Create: Copycats+ / FramedBlocks | 3.0.9 / 10.6.3 |
| Sable / Create Aeronautics / Create Deep Seas | 2.0.6 / 1.3.2 / 3.4.7 |
| JEI / GuideME | 19.64.0.455 / 21.1.22 |

同梱依存を含む起動ログ上の登録数は44（Minecraft・NeoForgeを含む）。これは画像の93ファイルすべてを試したという意味ではない。KubeJSのスクリプト、シェーダーパック、追加のリソースパック、元のワールドは再現していない。

試験側のNeoForgeは `21.1.252` とした。Quarkが同版以上、Supplementariesが `21.1.247` 以上を要求するためであり、プロジェクト標準の `21.1.219` は変更していない。実クライアントはRTX 4060、Java 21、ヒープ上限3 GiB、描画・シミュレーション距離4、シェーダー無効で実行した。

拡張前の所持検査は、この構成で全8条件と炭化木材のPonder 1シーンが合格し、`runClient` は1分51秒で正常終了した。

### 操作を加えた試験

`client/CharredWoodClientCheck.java` を拡張し、既存の所持・表示・保存データ試験に次を追加した。通常プレイでは無効で、明示的な開発用システムプロパティを指定したときだけ動く。

- 空に向けた攻撃・右クリックをMinecraftのキーバインド処理へ流す。
- サバイバルで設置し、1個だけ消費することを確認する。
- 炭化木材を手に持ったままブロックを継続採掘し、破壊完了を確認する。
- 手に持ったままチェストを開閉し、スニーク時にはその上へ設置する。
- オフハンドの最後の1個を設置し、空になる遷移を確認する。
- Createの作業鉢を右クリックする。
- 動物を攻撃し、サーバー側で体力が減ったことを確認する。
- インベントリ内で移し替え、Shiftクリックでホットバーへ戻す。
- Q操作に対応する経路で1個投棄し、同じデータのアイテムがサーバーに生成され、回収後の個数が戻ることを確認する。
- 炭化木材のPonderを表示する。

上記操作を含む `checks` 全19項目が成功し、`completed: true` を確認した。続く炭化木材のPonder 1シーンの描画・翻訳検査も成功し、`runClient` は1分43秒で正常終了した。ブロック設置、採掘完了、最後の1個の消費、サーバー側の体力減少、投棄・回収後の個数、インベントリ移動を条件として検査している。作業鉢については右クリック処理で例外が出ないことの確認であり、加工レシピの試験ではない。

**現行コードとこの追加MOD構成ではクラッシュ未再現。報告後の変更で解消済みという可能性はあるが、元のビルド・操作条件が不明なため、その変更や原因は断定できない。** ゲーム側の機能を推測で書き換えず、再発時に同じ操作を再試験できる形で保存した。

追試用に入れた外部jarを退避し、開発用の画面設定も元に戻した。その後、標準NeoForge `21.1.219` と標準の依存構成で次を実行した。

```sh
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Dcreateplant.gameTestClasses=WoodCarbonizationGameTests' \
bash gradlew --no-daemon --max-workers=2 build runGameTestServer
```

ビルドと炭化関連GameTest 5件が成功し、全コマンドは49秒で正常終了した。試験内容は30秒の炭化・原木の軸保持・木炭の個数、酸1源1箇所の消費、流動酸と非木材の除外、途中保存の復元、途中破壊時の元木材回収と通常設置時の完了状態。全件GameTestや、変更のないアセット・翻訳の検証は繰り返していない。`git diff --check` も成功した。

### 終了前に取り込んだ変更

コミット前の再fetchで `4c406b77`、`13ffe878`、`ed7a41e9`、`40825f8d` の4件を確認した。密閉槽・分留・物性・Ponder等の変更を `git rebase origin/main` で競合なく取り込んだ。更新された `DESIGN.md` の密閉反応槽と分留塔の規則も確認した。炭化木材のブロック・BlockEntity・登録への変更はなかった。

取り込み後も標準構成の `build runGameTestServer` と炭化関連5件が成功し、コマンド全体は36秒で正常終了した。追加MOD構成でも全19項目と炭化木材のPonder 1シーンが再び合格し、2分4秒で正常終了した。最終成果物には取り込み後のログ・結果・画面と、標準NeoForge向けにビルドした現行jarを保存した。

### 試験環境で発生した別の失敗

- NeoForge `21.1.219` では追加したQuark・Supplementariesの依存条件を満たさず、起動前に停止した。
- 新しい依存の取得時に直接の名前解決が失敗したため、ローカルのGradleプロキシ設定で取得した。
- ヒープ上限が標準の約1.5 GiBのとき、ワールドへ入る前に `OutOfMemoryError: Java heap space` が発生した。炭化木材の所持・操作試験へ到達しておらず、ユーザー報告の再現ではない。3 GiBへ増やし、FerriteCoreなどを含む上記構成で再実行した。

これらを炭化木材の不具合の原因や修正として扱わない。

併用ログにはSableの `Unknown block: copycats:copycat_catwalk` や、CEEの `electroenergetics:block/duplex_wire_segment` のモデル欠落も記録された。炭化木材の試験は継続できた。これらのブロックの物性・描画は今回の範囲外であり、変更・検証していない。

### 再実行時の注意

表の外部jarを `run/mods` へ置く。JEIも同フォルダーから指定版を読み込むため、標準JEIとの二重導入を避ける。JDK 21と `lang:ja_jp` の開発用設定を用意する。

```sh
__GL_THREADED_OPTIMIZATIONS=0 \
JAVA_TOOL_OPTIONS='-Xms256m -Xmx3G -XX:ActiveProcessorCount=4 -Dcreateplant.clientQualityCheck=true -Dcreateplant.charredWoodClientCheck=true -Dcreateplant.clientQualityTargets=charred_wood -Dcreateplant.clientQualityScenes=wood_carbonization' \
mc-debug bash gradlew --no-daemon --max-workers=2 \
  -Pneo_version=21.1.252 -PwithDeepSeas=true -PwithJei=false runClient
```

実行開始時に結果ファイルを未完了へリセットし、全操作が終わった場合だけ `charred-wood-client-result.json` の `completed` が `true` になる。各操作の確認結果は `checks` に保存する。途中停止した古い結果を成功扱いしないこと。プロキシが必要な環境では、別途その環境のGradleネットワーク設定を使用する。
