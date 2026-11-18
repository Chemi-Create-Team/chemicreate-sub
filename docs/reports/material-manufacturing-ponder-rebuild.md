# 物質製造Ponderの再編完了レポート

作業日 2026-11-11

実装コミット `4c3cfb39a1d84e00a40b15e282939603f368bfea`

作業開始時のmain `202d0257` を基準に、ウラン系を除く物質製造の旧教材20本を、最終製品ごとの25系列・29シーンへ再編した。合計147工程のうち119工程はゲームに読み込まれた実レシピや設備の処理定数から投入量・生成量を取得する。150種類のアイテムを入口として登録した。

## 教材の構成

中間素材からも、その素材を使う製造系列の全章を開ける。長い系列は同じ入口を共有する2章構成とした。系列の区切りは原料の共用、最終製品、装置の切替に基づく実装上の判断である。

| 系列 | 工程数 | 説明する製造内容 |
| --- | --- | --- |
| 空気分離 | 6 | ろ過、圧縮、冷却、14段分留、酸素の気化、窒素の液化 |
| 水素 | 3 | 鉢の列と電極、電力供給、水の電解、ガス回収 |
| アンモニア | 5 | 合成ガス、予熱、触媒床、冷却、分離と循環 |
| 硝酸・肥料 | 7 | アンモニア酸化、NO分離・酸化、吸収、硝酸塩・硫酸塩肥料 |
| 硫酸 | 7 | 硫黄、接触法、硫化水素の回収、木材の炭化 |
| 塩素・ソーダ | 8 | 岩塩、塩水電解、隔膜、塩酸、漂白液、石けん |
| 樹脂・成形品 | 14 | 原油分留からオレフィン、重合、樹脂粒、板、工業用樹脂・ガラスまでの2章 |
| 合成ゴム | 4 | ゴム、ガスケット、ベルト、合成素材からの革 |
| PVC | 4 | 二塩化エチレン、塩化ビニル、PVC樹脂、配管 |
| 燃料精製 | 7 | 軽油の脱硫、重油の水素化分解、冷却・分留・ガス回収 |
| 冷媒 | 8 | 不凍液と冷凍サイクルの2章、液化ガス、製氷 |
| アルミニウム | 7 | ボーキサイト、硫酸法・バイヤー法、電解炉、板 |
| チタン | 6 | チタン鉄鉱、酸化物、電解炉、板 |
| ジルコニウム | 5 | ジルコン、ジルコニア、電解炉、耐火材 |
| ニッケル | 4 | 鉱石、焙焼、水素還元、板 |
| クロム | 3 | 鉱石、アルミニウム還元、ステンレス鋼への利用 |
| モリブデン | 3 | 鉱石、焙焼、水素還元 |
| 鋼材 | 7 | 蛍石の融剤、パドル鉄、5回プレス、転炉、ステンレス鋼と板 |
| 顔料 | 6 | 酸化鉄、クロム緑、ニッケルチタン黄、モリブデン青、染料 |
| 建材 | 8 | セメント・コンクリート・防食床と、ホウ砂・ガラスフリットの2章 |
| 水処理 | 10 | 蒸留・精製水と、排水処理・汚泥利用の2章 |
| 触媒 | 7 | 6種類のカートリッジの製作、鉄触媒の再生 |
| 発破薬 | 2 | 既存のANFOレシピと採掘用発破薬 |
| 重水素 | 3 | 重水濃縮、重水電解、分離水の回収 |
| 液体ヘリウム | 3 | 回収原料の分離、コールドボックス、保冷循環 |

ウラン製造・再処理と、直近の触媒バスケット、分留塔、吸収棚段、1×1遠心分離機、制御盤、往復式圧縮機などの設備教材を除外対象とした。照合した対象外カタログ69件は比較一致し、保護対象の翻訳110キーも日英中それぞれで比較一致した。ウラン鉱物を扱う既存の地質ヘリウム回収と、炉内照射によるトリチウム製造も除外した。

## 実装

- `ManufacturingRecipe` が読み込まれたCreateレシピ、圧力反応レシピ、転炉レシピ、`ProcessRecipe`、電解設備の定数を参照する。繰り返された原料を合算し、転炉の大口投入量、確率付き生成物、連続加工の回数を保持する。
- `ManufacturingPonderScenes` が、原料、処理中、生成物の状態を順に描く。工程に合わせて実寸の設備へ切り替え、ミキサー、プレス、撹拌翼、遠心ローター、圧縮機、冷却ファンなどの既存描画を使用する。鋼材の連続加工は同じ加工品へ5回のプレス動作を行う。
- 3×3ふた付き槽、1×1遠心分離槽、14段分留塔、5×5×3電解炉など、現行の設備形状を使用する。槽は底面中央から駆動し、吸収棚段は回転不要として扱う。
- 精製水の教材は、槽の上部から蒸気を分離し、ラジエーターの反対側の側面出口から2段の受槽へ送る。受槽の下側液体口へスマート流体パイプを直結し、精製水を指定して製品タンクへ抽出する。入力・受槽・製品の表示もこの順序に合わせた。
- 空気分留の数量表示は気体換算mBであることを明記した。液体酸素の体積を計算する密度比と、塔内の数量の意味を説明する。
- コールドボックスはヘリウム入口、冷却水、温水出口、断熱した液体出口を示す。窒素や他の液化ガスは既存の相変化・熱交換器で説明する。
- 日英中に147工程と29題名を登録した。長い説明は画面上側へ配置し、Ponderの操作ボタンとの重なりを修正した。
- 置換した教材の登録、構造、翻訳と、再生成で復活させる旧スクリプト・翻訳断片を整理した。設備教材や通常アセットを兼ねる生成スクリプトは、製造Ponderに該当する部分を整理した。

### 主要ファイル

- [ManufacturingPonderScenes.java](../../src/main/java/net/createplant/ponder/ManufacturingPonderScenes.java)
- [ManufacturingRecipe.java](../../src/main/java/net/createplant/ponder/ManufacturingRecipe.java)
- [PlantTutorialCatalog.java](../../src/main/java/net/createplant/ponder/PlantTutorialCatalog.java)
- [ElectrolysisPonderPlugin.java](../../src/main/java/net/createplant/ponder/ElectrolysisPonderPlugin.java)
- [tutorials.json](../../src/main/resources/assets/create_heavy_indusstry/ponder/tutorials.json)
- [generate_manufacturing_ponder.py](../../tools/generate_manufacturing_ponder.py)
- [ManufacturingPonderGameTests.java](../../src/main/java/net/createplant/gametest/ManufacturingPonderGameTests.java)

## 確認結果

Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10、Java 21で確認した。実クライアントは `mc-debug` で起動し、`glxinfo -B` でNVIDIA GeForce RTX 4060、NVIDIA 595.99.02、ハードウェア描画を確認した。

| 確認 | 結果 |
| --- | --- |
| `build` と製造Ponder用GameTest | 成功、27秒 |
| `ManufacturingPonderGameTests` | 5件合格、失敗0件、355.3 ms |
| 構造・工程データの静的検証 | 29章、25系列、176翻訳キー、対象座標すべて合格 |
| 翻訳の静的検証 | 日英中の29題名・147工程が合格 |
| 日本語の実クライアント | 全29章を読み込み・撮影、最終レイアウトで代表3章を再確認 |
| 英語・中国語の実クライアント | 各29章のコンパイル・実行時翻訳が合格、各3章を撮影 |
| 再生成の決定性 | カタログ、29構造、3言語の計33ファイルが再生成前後で一致 |
| Python生成スクリプト | 構文検査合格 |
| `git diff --check` | 合格 |

GameTestは登録済みアイテム・BlockState、工程の座標、実レシピの解決、系列内の入口共有、3言語の実行時キー、確率・連続加工回数・材料数を検証する。精製水の教材については、実在するラジエーター側面ポートと、受槽へ直接接続する抽出フィルターの配置も検証する。

機械可読の結果とクライアントの記録を保存した。[試験結果](../quality/material-ponder/results.json)、[日本語](../quality/material-ponder/client-quality-result-ja_jp.json)、[英語](../quality/material-ponder/client-quality-result-en_us.json)、[中国語](../quality/material-ponder/client-quality-result-zh_cn.json)

中国語画面はcomputer-use-linux MCPで撮影した。[実クライアント画面](../quality/material-ponder/ponder-cn.jpg)

## 再確認の手順

Java 21を使用する。

```sh
bash gradlew --no-daemon -I docs/quality/material-ponder/server.gradle build runGameTestServer
python3 tools/validate_plant_tutorials.py --manufacturing
python3 tools/validate_ponder_language.py --manufacturing
mc-debug bash gradlew --no-daemon -I docs/quality/material-ponder/client.gradle runClient
```

クライアントの言語は `-DqualityLanguage=en_us` または `-DqualityLanguage=zh_cn` で選べる。`-DqualityTargets=ammonia_gas_bucket@plant_manufacturing_ammonia` の形式で撮影対象を指定できる。再生成スクリプトは `nbtlib` と既存の画像生成用Python環境を使用する。構造の編集元は `tools/data/manufacturing/` と、参照している現役の設備Ponderである。

## 未確認事項・既存の不整合

- Ponderは実レシピに基づいた投入・処理・生成の状態を段階表示する。実ワールドで全製造系列を連続稼働させる試験、サバイバルでの全工程完走、マルチプレイでの確認は今回実施していない。
- バケツアイテムが登録されていない中間ガスは、系列の説明・実レシピの物質名・数量に統合した。これらの気体自体にはアイテムからPonderを開く入口が存在しない。実在する製品・原料・触媒アイテムから該当系列を開ける。
- 中国語のPonder本文は全章分を用意した。一部の既存素材名は英語へフォールバックする。実画面で `cold_synthesis_mixture` の英語表示を確認した。
- 既存の `PlantTutorialGameTests.everyTutorialUsesRegisteredComponentsAndValidBlockStates` は、除外対象の `uranium_preparation` にある未登録の `create_heavy_indusstry:uranium_hexafluoride_bucket` で失敗した。全体向け `validate_plant_tutorials.py` も同じアイテムのモデル不足で失敗する。
- 全体向け `validate_ponder_language.py` では、既存の `ammonia_separator` の中国語訳が1キーだけ存在し、残る10キーが不足している。製造Ponderの検証範囲は `--manufacturing` で再現できる。
- 全GameTestの一括実行は行っていない。上記の製造Ponder用5件と、調査中に実行した既存Ponder検証の結果を記載した。

今後この製造教材を編集するときは、共通カタログと生成元を更新し、生成後の構造だけを手編集して差異を作らないようにする。実行時の投入・生成表示には2引数の `%s` を使うため、翻訳検証とクライアント検証の両方で確認する。
