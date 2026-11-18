# 鋼鉄ブロックの暗色化と四隅のネジ

作業日: 2026-11-12

実装コミットID: 085bff5a933dcae83792d9833137d9ccb06a401e

## 実装

Create 6.0.10の解決済みJARにある `industrial_iron_block.png` と上面のテクスチャを参考に、鋼鉄ブロックを黒寄りの低彩度の鋼板へ変更した。

- 中央の横向きの継ぎ目を削除し、1枚の板として描く。
- 四隅へ対称な2×2ピクセルのネジ頭を配置する。
- 外枠・面取り・内側の影を残し、暗くしても金属の縁を判別できるようにする。

生成元は `tools/generate_metal_blocks.py` の `steel()`。`textures({'steel_block'})` だけを呼び、画像は16×16のRGBAで生成した。PNGのハッシュ比較で、変わった画像が鋼鉄ブロック1枚だけであることを確認した。鋼鉄インゴット、ステンレス鋼、チタンの画像は変更していない。

鋼鉄ブロックを参照する転炉の炉体・ガスホルダーの外殻等へ同じ見た目が適用される。登録ID・保存形式・レシピ・動作仕様は維持するため、既存ブロックの交換は不要。

## 確認

- 生成画像が16×16、RGBA、全ピクセル不透明であることを確認。
- `validate_visual_identity.py`: 合格。最新変更の統合後は完成品345種、意図しない同一画像なし。
- `test_ci_validators.py`: 統合時の分類登録を補完した後、15件合格。
- `bash gradlew --no-daemon --max-workers=2 jar`: 初回9秒、最新変更の統合後も42秒で合格。
- `mc-debug glxinfo -B`: NVIDIA GeForce RTX 4060、直接描画を確認。
- `mc-debug bash gradlew --no-daemon --max-workers=2 runClient`: 初回1分20秒、統合後も1分14秒で正常終了。日本語Ponder「転炉で鋼を作る」を実クライアントで表示し、初回は鋼鉄ブロックのアイコンと炉体、統合後も炉体の暗色鋼板を目視した。
- Computer Useで[変更後の画面](../quality/dark-steel-ponder.jpg)を撮影。掲載画像は初回の表示確認時のもの。[クライアント確認結果](../quality/dark-steel-client.json)は最新変更の統合後の結果を保存した。
- `git diff --check`: 合格。作業前とコミット前に `git fetch origin main` を行った。

表示確認には `createplant.clientQualityTargets=steel_block@plant_converter`、`createplant.clientQualityScenes=plant_converter,guide.converter`、`createplant.clientQualitySeek=200` を使用した。

## 最新変更の取り込み

プッシュ前に放射線機能の5コミット（取り込み先 `ee09b95b`）を検出し、リベースで取り込んだ。競合はなく、鋼鉄の生成元と画像はリベース前後で一致した。最新版でJARを作り直し、画像検証と実クライアント確認を再実行した。

統合直後の画像検証では、追加された個人線量計とサーベイメーターが素材分類表に未登録だった。`tools/powder_items.json` の粉末以外の器具へ2種を登録し、生成された画像監査結果も更新した。計測器や放射線の動作コードは変更していない。

## 未検証事項・次回の注意

画像と生成処理だけの変更なので、GameTestと全件試験は実行していない。ゲーム内の目視は日本語・通常の描画条件で行い、全シェーダー・他リソースパックとの組合せは未検証。

後から画像を調整する場合も生成元を修正し、対象を `steel_block` だけに絞って再生成すること。
