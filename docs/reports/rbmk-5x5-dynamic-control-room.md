# RBMK 5×5動的制御室・流量計・自動制御 完了レポート

## 基準とコミット

- 同期した `origin/main`：`8e8c85aa0e677b5e007815014aeeb989cebe7f9b`
- 実装コミット：`496c468f516c86aa53c370b1009e85e846a52209`
- 作業ブランチ：`work`（検証後に `HEAD:main` へプッシュ）

実装前の基準からリモート `main` が6コミット進んでいたため、実装コミットを最新 `origin/main` へリベースした。電気設備をCreate: Electro Energeticsへ一本化した変更、ステンレス鋼と設備レシピの更新、今回追加した原子炉Display Link項目をすべて保持している。日英翻訳の競合は、最新側のステンレス鋼Ponderと今回の記録計・自動制御Ponderを両方残して解消した。

## 実装結果

- 炉心制御マップと原子炉計器パネルを、それぞれ3×3から5×5へ拡大した。壁面全体は10×5、操作卓6×2と合わせて62ブロックで形成する。
- 炉心制御マップはリンク先原子炉の実寸・円形配置へ追従し、装荷されている全燃料位置を8段階の緑～赤ランプで示す。制御棒の位置、実挿入率、選択枠、個別操作は維持した。
- 計器パネルは固定36計器を廃止し、燃料が入った2×2チャネル束だけを上面配置どおりに並べる。各計器は1ブロック面4×4相当の密度、鋼製ケース、独立した中心軸の針、半透明ガラスを持ち、実際の搬入・搬出量から得た流量 `mB/t` を表示する。
- 制御棒が引き抜かれた周囲へ局所出力を寄せ、実流量による冷却を燃料温度へ反映した。局所係数は炉心全体の平均出力を変えないよう正規化し、全体反応度は既存の制御棒価値で計算する。
- 黒鉛温度を炉心状態として追加し、Create標準Display Linkへ黒鉛温度、選択チャネル流量、出口水温を追加した。専用データリンクブロックは追加していない。
- 制御室へ接触させるペーパーチャート記録計を追加した。代表出口水温と黒鉛温度を1秒間隔、直近48点で保存し、青・黄褐色の打点として紙面へ描画する。
- 制御室へ接触させる自動制御システムを追加した。最高燃料温度、代表出口水温、平均実流量、出力、AZ-5から非個別制御棒の挿入目標を決める。複数盤の同時所有をリースで防ぎ、個別指令とAZ-5を優先する。
- 自動制御盤は精密機械6個、電子管2個、真鍮ケーシング1個を使う高コストレシピにした。記録計・自動制御盤とも独自GUIはなく、直接操作、ゴーグル、物理表示だけを使う。
- 制御室Ponderを5×5壁面、記録計、自動制御盤、流量計、Display Linkの現行仕様へ更新した。

## 起動・GameTest短縮

通常の全GameTest登録は変更せず、`runQuickReactorGameTestServer` だけが `RbmkGameTests` を選択する。使い捨ての `GameTestServer` に限り結果確定後のワールド保存を省き、テスト結果を返して直ちに終了する。通常クライアント、統合サーバー、専用サーバーの終了処理には影響しない。

最終確認では原子炉13件を4.317秒で実行し、Gradle起動を含む全体を28秒で終了した。既存の全GameTestタスクは網羅確認用として残している。

## 検証結果

- Java 21 / Minecraft 1.21.1 / NeoForge 21.1.219 / Create 6.0.10：`clean build` 成功（11秒）。
- 単体検証：FluidPhase 25件、Haber-Bosch 1,019件、ReactorPhysics 44件、ReactorControlが合格。
- 原子炉専用NeoForge GameTest：必須13件すべて合格、ゲーム内実行4.317秒、Gradle全体28秒。
- Ponder検証：追加76シーン、日英354組、部品リンク93件、全構造ターゲットが合格。
- Ponder言語検証：実行時見出し81件と全共有説明が日英とも合格。
- ビジュアル監査：完成品189件、意図しない同一外観0件。
- CI検証器自己テスト：11件合格。
- NVIDIA RTX 4060 / OpenGL 4.6 / Direct Renderingで実クライアントを起動し、5×5＋5×5壁面を描画した。結果JSONは燃料ドット121、流量計29、5×5両パネルを記録した。針・ケース・ガラスがパネル面に接続され、浮きがないことを画像で確認した。

品質確認クライアントは画像と結果JSONを保存してMinecraftの停止処理へ入った後、環境側の `libEGL_nvidia.so.0` でSIGSEGVになった。保存前のレンダリング、BlockEntity renderer、結果判定には例外がなく、このコンテナで既知のGPU終了時問題である。

## 成果物

- `/workspace/outputs/Create-Plant-RBMK-5x5.jar`（SHA-256：`e2e1739dc24416866d64110635bb18fbc62492d66c336187f8e4b24afebce8c2`）
- `/workspace/outputs/rbmk-control-room-5x5.png`（SHA-256：`f0f97cf1daad9e1d12e232dc9885721affc42b9e1536cca6963bdcc2db0fee62`）
- `/workspace/outputs/rbmk-control-room-5x5-result.json`（SHA-256：`d7b11bf0add7a92d1d298a198556c112a9bfd60a9e3624b802f86a37069c51fb`）

## 主なファイル

- `ReactorControlConsoleBlockEntity`：5×5壁面形成、動的燃料・制御棒・流量スナップショット、Create Display Link接続。
- `ReactorControlConsoleRenderer`：温度ランプ、ガラスケース付き流量計、独立軸の補間針。
- `FuelChannelBlockEntity`、`ReactorCore`、`ReactorPhysics`：実流量計測、局所制御棒加熱、黒鉛温度。
- `ReactorChartRecorderBlockEntity`、`ReactorChartRecorderRenderer`：48秒ペーパーチャート。
- `ReactorAutomaticControlBlockEntity`、`ReactorAutomaticControl`：隣接自動制御と競合防止。
- `MachineReadings`：Create標準Display Link用の追加原子炉測定値。
- `RbmkGameTests`、`ReactorControlTest`：5×5形成、流量、温度、記録、自動制御、AZ-5回帰試験。
- `tools/generate_rbmk_assets.py`：5×5モデル・テクスチャ・レシピ・Ponder構造の再生成。

## 未検証事項と次回の注意

- 今回の変更範囲は13件の原子炉専用GameTestで確認した。全設備を含む通常の全GameTestは、起動時間短縮の要求に合わせて最終リベース後には再実行していない。
- 計器とマップの座標は0～255へ量子化して4 Hzで同期し、クライアントで針だけを補間する。最大13×13炉心より大きな未対応構造へ上限を広げる場合は、重なりと通信量を再評価する。
