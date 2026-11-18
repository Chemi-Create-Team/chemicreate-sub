# 原子炉制御盤の復元確認

2026-11-08。「原子炉の制御盤とかももどして」という追加指示に対し、制御卓、操作机、炉心マップ、計器盤、記録計、自動制御盤、制御棒操作器とリンクの参照を確認した。

これらの外観は前回の実装`a314e1507e13a5907fb10d34ca884fdfa41848ca`ですでに復元されている。今回は追加のテクスチャ・モデル・動作変更は行っていない。

- RBMK画像111枚の寸法・RGBA画素が全ブロック更新前の`6c10f6fc`と一致することを独立監査で確認した。ランプ2枚のPNGバイト差は圧縮方式の差のみ。
- RBMKと制御盤の部分モデル103件が、材質参照を含め`6c10f6fc`と一致。関連8種類のBlockState・アイテムモデルも一致する。
- 動的なランプ・針・記録計の描画は復元済みRBMK画像を参照し、更新後の汎用反応炉材質への依存はない。
- 配布済み`create-heavy-indusstry-texture-restore-a314e150.jar`の111画像・103モデルが、復元済みソースとバイト一致。SHA-256は`70f2df4f3effc14af087f6c88cf1a96bc26ae83138bfebbcb5f62a6e1c62b995`。

確認結果は`docs/quality/reactor-console-restore-check.json`。今回の変更は確認記録のみのため、前回の成功済みビルドとPonder表示確認を再利用した。画像は前回の復元版を実クライアントで表示したもの。全運転状態を新たに総当たりしたものではない。

前回の原子炉制御盤画像：`/workspace/outputs/texture-restore-ponder/quality-ja_jp-reactor_control_console.png`。全体の復元範囲・制約は[指定設備の復元報告](selected-texture-restore.md)を参照。
