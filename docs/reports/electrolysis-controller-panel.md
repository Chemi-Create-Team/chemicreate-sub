# 電解セルコントローラーの盤面作り直し 完了レポート

外観と指針の描画は後続の[電解槽の高精細化](electrolysis-realistic-hardware.md)で更新しました。この文書の16×16材質と針の振動は当時の実装です。盤面の操作配置と非常停止の機能は維持しています。

作業日：2026-11-11。実装コミット：このレポートと同じコミット（`git log -1 -- docs/reports/electrolysis-controller-panel.md` で確認）。

## 変更内容

参考写真（配電盤用の角形メーター、真鍮枠の圧力計、ランプと押しボタンの操作盤）をもとに、2×2の制御盤を作り直した。専用GUIは無く、実物のレバーとボタンだけで操作する。

| 区画（正面から） | 操作 | 計器・表示 |
| --- | --- | --- |
| 左下 | 主電源ブレーカー（大きなレバー）。右クリックで上下し、給電を入・切 | 真鍮枠の丸形電力計と数値（kW）、緑の運転灯 |
| 右下 | 真鍮の非常停止ボタン（ラッチ式） | 電極残量の半円ダイヤル（赤→黄→緑）と枚数、黄・赤の警告ランプ |
| 左上 | 負荷 −25／+25 ボタン | 電圧計・電流計と数値、負荷設定 |
| 右上 | なし（CEEの2端子） | 原料・気体・苛性ソーダの液面管3本、生産量（mB/t）のニキシー表示 |

- 運転中は電圧計・電流計・電力計の針が小刻みに震える（クライアント側の描画だけで、通信量は増えない）。
- 非常停止は押すとロックしてブレーカーも切り、もう一度押すと解除する。ロック中はブレーカーを入れても通電しない。状態は保存する。停止理由 `estop` を追加した。
- 制御盤にコンパレーターを付けると、電極の残量（装填枚数÷最大枚数）を15～0で出力する。枚数が変わったときだけ隣接へ通知する。
- 黄ランプは残量1割以下で点滅、赤ランプは0枚で点灯する（0枚では従来どおり停止）。
- 旧「運転／停止」「試験／解除」「確認／緊急停止」ボタンと、その翻訳キー（`electrolysis.panel.N.left/right`）、計器試験の状態、`dial`・`frame` テクスチャ、`lamp`・`button` モデルは削除した。
- 外装：電解セルと制御盤の胴体を、Createの流体タンク（横型流体タンクと同じ）の銅板テクスチャ `create:block/fluid_tank`・`fluid_tank_top` へ変更。締付ボルトと取手は `create:block/brass_block`。セルの窓・電極・内部は従来どおり。
- Ponder「フィルタープレス型電解槽」と水素・塩素アルカリの製造系列の字幕（日英中）を新しい操作へ更新した。

## 要件との差

- 「電極板の消耗度（耐久値）」：現在の工程では極板そのものは消耗しない（消耗するのは隔膜のアスベストだけ）。極板に耐久値を新設するとレシピ・装填・自動化の釣り合いが変わるため、今回は追加していない。ダイヤル・ランプ・コンパレーターは「装填枚数÷最大枚数」を示す。耐久値を入れる場合は別作業とする。
- 「消費しているFE量」：この装置はFEではなくCEEの実回路から `P = V²/R` で仕事を受け取る。電力計はその実消費電力（kW）を示す。
- 応力計・速度計の盤面の埋め込み：回転動力を使わない装置なので、同じ意匠（真鍮枠の丸形・半円の文字盤と針）を電力計と電極ダイヤルに使った。
- 「リアルに」：1面16×16の密度（DESIGN.md）は守った。細部は1ピクセル浮かせた枠・可動の針・レバー・ボタン・文字で表している。

## 主な実装箇所

| ファイル | 役割 |
| --- | --- |
| `tools/generate_electrolysis_cell.py` | 盤面4枚の描画、浮き出し部、針・レバー・非常停止の部品、外装テクスチャの差し替え、翻訳 |
| `client/ElectrolysisControllerRenderer.java` | 針（震え）、レバー、ランプ、液面管、ニキシー表示、数値と銘板 |
| `block/ElectrolysisControllerBlock.java` | 区画ごとの操作範囲、コンパレーター出力 |
| `block/entity/ElectrolysisControllerBlockEntity.java` | 非常停止ラッチ、操作の割り当て、残量変化の通知 |
| `client/ElectrolysisClientCheck.java` | 4視点のスクリーンショットを自動保存（`run/screenshots/quality-electrolysis-*.png`） |
| `gametest/ElectrolysisCellGameTests.java` | ブレーカー・負荷・非常停止・コンパレーターの試験 |

## 確認

- `./gradlew -I docs/quality/filter-press-electrolysis/client.gradle runClient`（実クライアント、ja_jp）：成功。2×2×8・128枚の実ワールドでCEEの48 V電源から給電し、4視点を保存した。盤面は `docs/quality/filter-press-electrolysis/panel.png`、全体は `overview.png`。レバーが「入」で上がること、数値・銘板・ニキシー表示・液面・運転灯が正しい向きで読めることを画像で確認した。Ponder 4シーンの検査も合格。
- `runGameTestServer`（`ElectrolysisCellGameTests`・`ChlorAlkaliGameTests`・`CircuitGameTests`・`ModularPortsGameTests`・`FusionFuelGameTests`・`ProductUseGameTests`・`PlantTutorialGameTests`）：37件中36件合格。
  - 不合格1件は `PlantTutorialGameTests.everyTutorialUsesRegisteredComponentsAndValidBlockStates`（`uranium_hexafluoride_bucket` が存在しない）。今回は `tutorials.json` を変更しておらず、電解槽とは無関係。修正していない。

## 未確認事項

- 非常停止を押した状態、黄ランプの点滅、赤ランプの点灯、東西・南向きの盤面は、画面では見ていない（動作は試験で確認）。
- 英語・中国語での銘板の収まり。
- 性能の比較測定はしていない。追加した描画は制御盤4ブロック分で、サーバー側の処理は極板数が変わったときの隣接通知だけである。

## 次回の注意

- `tools/generate_electrolysis_cell.py` は `PYTHONUTF8=1` を付けて実行する（Windowsの既定コードページでは言語ファイルを読めない）。再実行すると既存PNGのバイト列だけが変わる（画素は同じ）ので、その差分はコミットしない。
