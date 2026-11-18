# 高圧蒸気タービンPonderの表示修正

2026-10-26。実装コミット：`6eb5381fc84f3237d764dfc9c9446e11619ad5b0`。文書コミットはその後に保存。

## 変更

- `ponder/ElectrolysisPonderScenes.java`：元の組立シーンに欠けていた床を補完。軸、筐体、端部、配管を異なる範囲として順に公開する。既存シーンの重なる範囲への追加描画を廃止。
- `ponder/PlantPonderScenes.java`：追加3シーンの構造を配置してから一度だけ表示し、同じ全体範囲の反復表示を停止。
- `HighPressureTurbinePonderElement.java`、`SteamPipePonderElement.java`：通常描画と別に重ねる常時最大照度の独自描画を削除。既存BlockEntityと標準モデルの描画経路を使う。

タービン本体のモデル・テクスチャ・構造判定・熱力学計算は変更していない。組立→基本運転→循環連携→安全説明という各シーンの内容も維持。

## 確認

Gradle `build runClient` 成功。実クライアントで日本語Ponder全111シーンのコンパイル、タービン4シーンの描画・撮影、ゴーグル最大4行と既存表示検査を実施。構造・対訳検証スクリプト2本と `git diff --check` 成功。

画像は `run/screenshots/quality-ja_jp-high_pressure_turbine_blade@0.png`〜`@3.png`。ログは `/tmp/turbine-ponder-shipping.log`。これらは生成物でGitへ追加しない。

今回はクライアントのPonder表示だけの変更なので、物理・流体の全GameTest再実行は行っていない。英語ロケールの別起動、全カメラ角度・全フレームでの目視は未検証。今後も同じ位置を複数のWorldSectionと独自描画の双方に所有させないこと。
