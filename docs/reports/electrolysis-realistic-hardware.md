# 電解セル・コントローラーの高精細化 完了レポート

外装・地板と計器配置は、後続の[タンク外装への調整](electrolysis-tank-panel.md)で更新しました。全面に写実的な金属を使う指定は撤回され、現在は計器と操作部へ限定しています。

日付：2026-11-11 UTC。実装コミット：`5e9c7f5502b9a65d293c8db2dea4b096e9c7f2bc`。この後の記録用コミットで実装IDを追記した。

作業開始時の `c24e6f6f` からoriginを確認し、先に `52fdb9eb` の制御盤変更を取り込んだ。その主電源・ラッチ式非常停止・負荷ボタンと計器配置を引き継いでいる。対象は `electrolysis_cell` と `electrolysis_cell_controller` の2種類。今回のユーザー明示指定をDESIGN.mdへ記録し、この2種類だけ16px原則の例外とした。

## 実装内容

- ステンレスの細かな擦り傷・油汚れ、真鍮のくすみと緑青、黒いベークライト、鋼の小さな錆を高精細化。金属アトラスは1024×1024、4素材各512×512。盤面4枚はそれぞれ512×512。
- 専用JSONモデルで、金属板の厚み、側面の継ぎ目と締付ねじ、真鍮ベゼル、角形メーターの黒い筐体、碍子、立体的なブレーカーレバー、丸い非常停止ボタンを作成。接続したセルの内壁は従来どおり消える。
- 側面のぞき窓を完全に撤去し、金属製点検パネルで閉鎖。内部は上面ハッチから確認する。ハッチ・電極・実在庫に比例する液面を維持し、閉じて見えない内部の描画を省く。
- 電圧0〜500 V、電流0〜320 A、電力0〜128 kWの印刷目盛りを実際の針の範囲に対応させた。電極は0〜100%の装填率を示し、数値は実数／容量を併記するため、短い構成でも128本表示の目盛りにはならない。
- BERの針・ブレーカー・液面管・内部液面を実データへ滑らかに追従させる。減衰係数は `1−exp(−Δtick/3.5)`。描画用の状態は所有者ごとに共有し、同じフレームで4区画・セルから繰り返し補間しない。架空の正弦波で針を震わせる旧描画は廃止した。
- ガラスに視点角度で強度が変わる反射マスクを重ね、液面管とニキシー管は曲面を分割して描く。数値は高精細な専用文字アトラスを使用する。
- ニキシー管は灰色の非点灯陰極、立体の台座・金具、ガラス面と橙色のエミッシブ文字を分離。`entityTranslucentEmissive` とFULL_BRIGHTを使い、給電中のみ発光する。主電源切断とハッチ開放で消灯する。
- 電解槽専用Ponderと水素製造Ponderの日英中の説明を「盤の液面管・上面ハッチで確認」へ更新した。塩素アルカリ・重水素も同じ新型電解槽のモデルを使う。

2×2断面・長さ1〜8、4方向、4つの制御盤区画、入出力面、CEEの実抵抗・消費仕事、レシピ、流体／電極の保存、インターロック、非常停止の解除手順に変更はない。専用GUIは追加していない。サーバーのBlock／BlockEntity／配管処理にも変更を入れていない。

## 生成元と主要ファイル

材質生成には **imagegenスキルのbuilt-in `image_gen.imagegen`** を使用した。CLI/APIの代替実行は使用していない。ユーザー添付の角形電気計器、真鍮の丸形圧力計、赤緑ボタンの写真を形状・質感の参考にし、写真そのものはテクスチャとして配布しない。丸形計器はこの装置で計測している電力を表示する。

選択した生成画像を [material-source.png](../../tools/data/electrolysis/material-source.png) に保存した。返された元画像は1254×1254で、ゲームのミップマップに適した1024×1024へLanczosで再サンプルする。生成元を保存しているため、再生成時にAIへ再問い合わせする必要はない。

[生成用プロンプトと出力一覧](../../tools/data/electrolysis/prompt.json)も同梱した。再現用プロンプトは次のとおり。

> Use case: game-asset. Create a production-quality square PBR-style ALBEDO material atlas in a flat orthographic view. Exactly four equal quadrants, no margins, text, objects, screws, perspective, baked directional glare or shadows. Top left: matte brushed warm neutral stainless steel, fine grain, tiny scratches, oil and fingerprints. Top right: satin aged subdued brass with tarnish and sparse oxidation. Bottom left: black charcoal bakelite/enamel, fine scratches and oil. Bottom right: aged dark steel with mill scale, fine pitting and restrained rust covering less than fifteen percent. Neutral even light, usable continuous material patches, photographic detail and a restrained Create industrial palette. Request 2048 by 2048 pixels; the built-in tool determines the actual returned image size.

計器の目盛り・数字は画像生成に任せず、実測範囲から正確に作図した。出力先は `src/main/resources/assets/create_heavy_indusstry/` 以下。

| ファイル | 役割 |
| --- | --- |
| `tools/electrolysis_hardware.py` | 高精細材質・文字盤・ガラス・カスタムJSON形状の書き出し |
| `tools/generate_electrolysis_cell.py` | 既存の生成入口。レシピ・翻訳・ドロップを維持 |
| `textures/block/electrolysis_cell/materials.png` | ステンレス・真鍮・ベークライト・鋼のアトラス |
| 同 `panel_0.png`〜`panel_3.png` | 512pxの計器文字盤と銘板 |
| 同 `nixie_e.png`、`nixie_cathodes.png`、`glass_reflection.png` | 発光数字、非点灯陰極、ガラス反射 |
| 同 `readout.png`、`porcelain.png` | 数値文字と碍子材質 |
| `models/block/electrolysis_cell/` | 36種類の本体・接続・盤面・可動部モデル |
| `client/ElectrolysisDisplayState.java` | クライアント描画だけの共有減衰状態。WeakHashMapで所有者を保持し続けない |
| `client/ElectrolysisControllerRenderer.java` | 指針・レバー・液面管・ガラス・発光数字のBER |
| `client/ElectrolysisCellRenderer.java` | ハッチ、実流体、装填枚数に合う電極 |
| `client/ElectrolysisClientCheck.java` | 通常起動では実行されない実ワールド確認fixture |

この2ブロックの旧16px材質6枚を削除した。他のブロックの材質や共通描画は変えていない。

## 確認結果

Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10、Create: Electro Energetics 1.1.4、Java 21。`mc-debug glxinfo -B` でNVIDIA RTX 4060のハードウェア描画を確認した。エクスポーターはPillow 13.0.0とDejaVuフォントを使用した。

| 確認 | 結果 |
| --- | --- |
| 対象・接続先の既存GameTest | **47件すべて合格** |
| 日本語の実クライアント | 実ワールドfixtureと4つのPonderが合格 |
| 英語の実クライアント | 同じく合格。一時排出した原料の返送も確認 |
| 中国語の実クライアント | 同じく合格。最終画像7枚を撮影 |
| ハッチの実操作 | 開放すると反応・消費電力・気泡が止まり、閉じると再開 |
| 補間 | 実ポートで液面を下げた際の中間値、停止直後の針の減衰をクライアントで観測 |
| 夜間と主電源OFF | 発光数字と運転灯の点灯・消灯、レバーの移動を確認 |
| JSON／PNG | 36モデルの材質参照・座標範囲が有効。ゲーム用PNGは2の累乗サイズ |
| 最終ビルド | `bash gradlew --no-daemon build` 成功 |

最後の中国語実行は、2×2×8の32セル＋4制御盤、通常極板128枚、最初に水26,000 mB、CEEの48 Vクリエイティブ電源を通常の銅線2本で接続した。電圧や電力をテスト用に代入せず、実際のCEE回路から得ている。運転開始後の110 tick時点は以下のとおり。

| 計測 | 値 |
| --- | --- |
| 端子間電圧 | 46.69974899 V |
| 実電力 | 1,744.69324449 W |
| そのtickの処理 | 2サイクル |
| 水 | 25,640 mB |
| 水素 | 240 mB |
| 酸素 | 120 mB |
| 水＋生成物 | **26,000 mB** |

撮影用の48 V運転であり、400 V定格での長時間負荷試験ではない。実行開始タイミングによって110 tick時点の累積反応数は多少変わるが、水3→水素2＋酸素1の保存を満たす。その後、同じ実ポートで水16,000 mBを一時排出して液面の補間を観測し、取り出した同じFluidStackを16,000 mB返送した。検査時には電極の隙間の実流体を上面ハッチから確認できた。

原本の数値とログ要約、全景・閉鎖側面・開ハッチ・内部・制御盤・夜間・停止時の画像は[確認資料](../quality/electrolysis-realistic/README.md)に保存した。画像はComputer Useで撮影した実クライアント画面で、生成した完成予想図ではない。旧[盤面変更レポート](electrolysis-controller-panel.md)の外観と振動描画は今回の内容が置き換える。

生成スクリプトを再実行し、36モデルと10テクスチャの全46ファイルがバイト単位で同一になることを確認した。最終JAR内のモデル・テクスチャもソースと一致する。出力したJARは8,106,673 bytes、SHA-256は `6e431298db5c3ee03a89258eb637d5a68024967f9fc762ac0d62864af4c1330c`。

## 制約・未確認事項と引き継ぎ

- 写実的なアルベドと3D形状をバニラ描画で表現している。ガラス反射は視点に応じたハイライトであり、環境の映り込みを計算するレイトレーシング／SSRではない。発光は数字とそのハローの描画で、周囲ブロックへの動的照明は追加していない。
- PBRシェーダー向けの法線・粗さ・金属度マップ、特定の外部シェーダーパックとの組合せは未確認。
- 今回は外観とクライアント補間の変更。多人数サーバー、大量設置時の描画性能比較、定格長時間運転、関係しない全GameTestは再実行していない。性能向上の実測主張はしない。
- 外部MOD側の既存警告（CEEの `duplex_wire_segment` モデル、Diesel Generatorsの `andesite_girder_strut` lootなど）は残る。この2ブロックのモデル欠落は発生していない。
- 保存形式・物性・レシピ・配管・電気計算を変える作業には広げない。今回の高精細化を他ブロックの16px原則の例外として流用しない。
