# ふた付き槽の安全マージン・統合制御盤・往復式圧縮機

実施日：2026-11-10。対象：Minecraft 1.21.1 / NeoForge 21.1.219 / Create 6.0.10 / Java 21。

実装コミット：[`3af0177e`](https://github.com/Chemi-Create-Team/Create-Plant/commit/3af0177e16bb7142b0296f080cc8832f6b5f2556)。

## 実装内容

### ふた付き槽の過圧

`PressureVesselBehaviour.inspect` のうち、ふた付き槽だけを変更した。以前の「定格の2倍で即破裂」「少し超えても約3回の検査で破裂」を廃止し、過圧の程度と続いた時間で損傷を蓄積する。

- 検査周期：20 tick。定格を超えた検査1回の損傷増分は `min(10, 4 × (圧力 / 定格 − 1))`。
- 破裂閾値60、既存の損傷保存を維持。減圧後も自動回復しない。
- 健全な16 bar槽で一定圧を保つ場合の計算上の目安は、17 barで約240秒、24 barで約30秒、32 barで約15秒。既存損傷・検査の位相・流出入で変わる。
- 内蔵の自動停止や自動排気は追加していない。既存の外付け安全弁が必要で、閉塞や能力不足のまま運転すれば最終的に破裂する。
- ほかの設備の2倍圧即時破裂条件、沸騰・潜熱・ハッチ開放時の実際の減圧は変更しない。時間と損傷の係数はゲーム上の猶予であり、実機の材料安全率の校正ではない。

### 吸収塔と棚段

作業開始時の最新mainには既に吸収塔の統合実装が入っていた（元の実装は `45737be8`、開始HEADは `8355c2f6`）。これを維持して動作を再確認した。独立した `absorption_tower` は登録・レシピ・外装・Ponder参照に残していない。

- 3×3・高さ2～4段のふた付き槽へ、各段1個の吸収棚段 `reactor_separator_core` を入れる。
- 回転不要。1段につき100 tickで二酸化窒素300 mB＋精製水100 mB → 硝酸200 mB＋一酸化窒素100 mB。
- 側面・下面から硝酸、上面のハッチ以外から一酸化窒素を回収。普通の水では規格外溶液になる。
- 専用Ponder `plant_absorption_trays` を利用し、安全マージンと継続過圧の説明を日英中で更新した。新しい同機能のPonderを重複追加してはいない。

### 1種類のブロックで組む原子炉制御盤

`reactor_control_console` 1種類のブロック／アイテムだけを62個使う。6×2の操作卓と中央揃えの10×5の壁を、すべて操作者側へ向けて配置する。順番は自由で、全体が正しく並んだ場合だけ位置に応じてモデルと役割が決まる。主制御器1個だけが処理し、ほかの61区画は表示と操作を委譲する。

廃止した登録は `reactor_console_desk`、`reactor_core_map`、`reactor_instrument_panel`、`reactor_chart_recorder`、`reactor_automatic_control` の5種類。対応するクラス、BlockEntity、レンダラー、アイテム、レシピ、ルート、モデル、不要テクスチャ、翻訳、Ponder登録も削除した。炉側のキャップ、制御棒リンク、単独コントローラーと、核融合炉の制御盤は対象外として維持する。

| 部分 | 内容 |
| --- | --- |
| 炉心図 | 燃料温度の8段階表示、制御棒の選択灯、近距離で各棒の実挿入率 |
| 流量計壁 | 実在する装荷済みチャネル束ごとの実測流量、独立した針とガラス |
| 大型計器10個 | 熱出力、反応度、最高燃料温度、最高表面温度、平均冷却水温、最高出口水温、黒鉛温度、ボイド率、平均挿入率、キセノン平衡比 |
| 操作卓 | 25個の実物ボタン、AZ-5の開閉カバー、2本の紙記録計、警報灯とモード確認灯、実測／目標表示 |
| 標準観測 | 各区画で4行のゴーグル表示。Create標準Display Linkで追加のモード・目標・警報数・記録点数・総流量・最高表面温度を出力 |

選択棒は炉心図またはPREV/NEXTで選び、±1、±5、±10、0％、100％の実物ボタンで操作する。LOCAL/ALL LOCで個別指定を解除する。モードのLOCALは既存のキャップ／リンクへ戻し、MANは一括目標を±1/±5で動かす。AUTOは既存の `ReactorAutomaticControl.nextTarget` を10 tickごとに実行する。連打で周期を短縮しない。

カバーを開いてからAZ-5を押すと全挿入をラッチし、独立したRESETで卓のラッチだけを解除する。閉じたカバー上への最初のクリックではカバーだけを開く。ACKは警報の確認、TESTは2秒の点灯試験、PAPERは48秒分の紙記録の消去。専用Screen/Menuやスクロール設定UIはない。

構造の破損やリンク喪失では一括制御の所有権を解放するが、炉心のAZ-5ラッチは残す。別々の炉から同じ卓へDisplay Linkを向ける既存の競合拒否も維持する。強制チャンクロードはしない。構造は変更時に検証し、通常の値は所有者が5 tickごとにサンプリングする。物理部品と文字は描画をまとめ、文字描画によるモデル用バッファの無効化を避けた。

外観は[ChNPP公式の3号機制御室パノラマ](https://chnpp.gov.ua/panorama/360/bshu.html)を参考資料とし、炉心配置図・角型計器・警報灯・緊急ボタン・操作卓をゲーム内の実測値へ対応させた。既存16×16材質の立体モデルで作り、写真をテクスチャへ貼っていない。レシピ1回で4ユニット。Ponder `rbmk_control_room` を同じ1種類のブロック構造へ作り直した。

### 往復式コンプレッサー

既存のシリンダー、取付面、軸位置、配管面、圧縮条件を維持し、Createの白色Steam EngineモデルとDiesel Generatorsの可動モデル参照を置き換えた。鋳鉄ガイド、鋼製クロスヘッド・ピストン棒、真鍮軸受、連接棒、円板状のクランク、冷却リブ付きシリンダーを固有形状にした。共有の16×16鋳鉄・鋼・真鍮材質を使い、細い部材のUVも寸法に合わせた。

[Burckhardt Compressionの資料](https://www.burckhardtcompression.com/products/process-gas-compressors/)を機構の参考とする。クランク半径0.25 m、連接棒1.5 mのスライダークランクを実際のCreate軸角度から計算し、行程0.5 mで動かす。連接棒の両端がクランクピンとクロスヘッドに追従する。床・壁・天井・対向取付を維持し、衝突形状と描画範囲も機構に合わせた。Ponderの駆動部・回転説明を日英中で更新した。

## 確認結果

`build` 成功。関連する61件のGameTestが全件合格し、最終の表示項目追加と状態整理後に影響する35件を再実行して全件合格した。形成時の重複更新を抑えた最終版では原子炉・制御盤の20件も再合格した。重複分は合計件数に足していない。[実行結果抜粋](../quality/plant-console/test-result.txt)。

| 確認 | 結果 |
| --- | --- |
| 17 barの槽 | 200 tickで存続し損傷は0より大きく5未満。保存・再読込で損傷が一致。400 tickでも存続 |
| 32.1 barの槽 | 80 tickで存続し、380 tickまでに破裂。2倍圧での即時破裂なし |
| 24 barの槽 | 安全弁なし／閉塞時に680 tickまでに破裂。有限の猶予を確認 |
| 実際の安全弁 | 17 barから85 tick以内に15.01 bar以下へ減圧して槽が存続 |
| 沸騰とハッチ | 潜熱・質量・熱量の保存、圧力での沸点上昇、実際の開放減圧が回帰合格 |
| 吸収棚段 | 2段へ原料900/300 mBを入れ、100 tickで硝酸400 mBとNO 200 mB、原料300/100 mBが残存。普通の水の規格外化、1段の拒否、回転不要、ハッチ停止が合格 |
| 制御盤4方向 | 不足・向き違いを拒否。任意の設置順で形成し、62区画すべてが同じ所有者へ委譲 |
| 実物ボタン | 全25ボタンの当たり判定を4方向で確認。操作でMenuが開かない |
| 制御・AZ-5 | 個別63％、MANの95％、個別解除後の一括目標表示、LOCALの所有権解除、カバー、AZ-5全挿入、独立RESET、ACKで故障を解除しないことが合格 |
| 解体・保存 | 区画破損で一括制御解除・AZ-5保持。モード・ガード・48点の記録順を再読込で保持。不正な記録長も48点へ制限 |
| 往復圧縮機 | 既存の実配管・駆動・圧力のGameTestが合格。専用サーバーのクラス読み込みも合格 |
| Ponder構造検証 | 83シーン、418組の説明、136の部品導線と構造ターゲットが合格 |
| 実クライアント | RTX 4060 / OpenGL 4.6のハードウェア描画。制御盤の数値・針・記録紙・ボタンと東向きの描画、圧縮機の床／壁／天井／対向取付と回転を確認 |
| 停止確認 | 床置き圧縮機のCreative Motorを撤去。実軸の `Speed` が `0.0f` となり、機構も停止。他の駆動中の組は回転を継続 |

日本語・英語・中国語それぞれで3本のPonderを実クライアントから開き、タイトル・説明・モデル表示の確認が全言語で合格した。[日本語](../quality/plant-console/ponder-ja_jp.json)・[英語](../quality/plant-console/ponder-en_us.json)・[中国語](../quality/plant-console/ponder-zh_cn.json)の結果を保存した。全体のGameTestは実行していない。以前報告されたRBMKと無関係の失敗3件へは変更を加えていない。

## 画像と再実行

画像はMinecraftの実画面をComputer Use Linux MCPで取得した。制御盤の画像はPonderと同じ説明用プレビュー値であり、実炉を612 MWで連続運転した証拠ではない。圧縮機の機構は実際のCreative Motorで32 RPMを入力した。長時間の発電所運転試験は今回の対象ではない。

- [制御盤全景](../quality/plant-console/reactor-console-overview.png)
- [制御盤の操作面](../quality/plant-console/reactor-console-buttons.png)
- [往復式コンプレッサー](../quality/plant-console/reciprocating-compressor.png)
- [床・壁・天井の配置](../quality/plant-console/reciprocating-mounts.png)

```sh
bash gradlew --no-daemon build -I docs/quality/compact-centrifuge/verification.gradle \
  -DtestClasses=ReactorConsoleGameTests,RbmkGameTests,NuclearSavedValueGameTests,DisplayIntegrationGameTests,ReciprocatingCompressorGameTests,VesselBoilingGameTests,PressureGameTests,NitricAcidGameTests \
  -DtestDir=/tmp/createplant-console-acceptance runGameTestServer

mc-debug bash gradlew --no-daemon -I docs/quality/plant-console/client.gradle -DqualityLanguage=ja_jp runClient
# qualityLanguage は en_us / zh_cn でも実行する。
mc-debug bash gradlew --no-daemon -I docs/quality/plant-console/world.gradle runClient
mc-debug bash gradlew --no-daemon -I docs/quality/plant-console/compressor.gradle runClient
```

この環境ではJava 21を指定し、`JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2`、`__GL_THREADED_OPTIMIZATIONS=0`、`ALSOFT_DRIVERS=null` を使用した。

## 主要ファイルと制約

- 槽：`thermal/PressureVesselBehaviour.java`、`gametest/VesselBoilingGameTests.java`。
- 制御盤：`block/ReactorControlConsoleBlock.java`、`nuclear/ReactorConsoleStructure.java`、`nuclear/ReactorConsoleButton.java`、`block/entity/ReactorControlConsoleBlockEntity.java`、`client/ReactorControlConsoleRenderer.java`。
- 圧縮機：`client/ReciprocatingCompressorRenderer.java`、`block/ReciprocatingCompressorBlock.java`、`tools/generate_reciprocating_compressor.py`。
- アセット・学習：`tools/generate_reactor_console.py`、更新したRBMK・圧縮機・吸収棚段の各Ponder生成ツールと日英中翻訳。
- 操作の正本：[原子炉](../rbmk-reactor.md)、[反応プラント](../reaction-plant.md)、[圧縮機](../gas-compressor.md)、[DESIGN.md](../../DESIGN.md)。

既存の炉心物理・通常燃料・765 MW校正は変更しない。AUTOの既知の冷態起動時の過大出力も未修正で、今回はその制御則を物理ボタンへ移した。旧5種の制御盤の保存ブロックは自動変換しないため、旧ワールドではユニットで組み直す。大規模な制御盤多数設置時の性能測定や実機制御室の完全再現は行っていない。

別件として、既存の `ReciprocatingCompressorBlockEntity.plan()` は `projectedPressure(..., true)` を呼び、シリンダー側の `true` は吸込室を選ぶ。吐出室へ追加した後の圧力を解くという当該メソッドの説明とは整合しない。熱力学校正への実際の影響は今回未計測で、外観変更に含めて式を変更していない。
