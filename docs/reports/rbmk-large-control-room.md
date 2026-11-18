# RBMK大型制御室実装レポート

## 基準とコミット

- 最新化した基準コミット：`f0c924eac0ff2c07ff7f6df55648a0fc9d64402a`
- 実装コミット：`d8fca2f4f5dab13bc7758a349a647b966d1ffbb8`
- 作業ブランチ：`work`（検証後に `main` へ反映）

作業開始時のローカルはリモート `main` より22コミット遅れていた。最新 `main` へリベースし、クリエイティブタブ、日英翻訳、採掘タグ、テクスチャ監査の衝突を、最新側の追加要素と今回の制御卓を両方残す形で解消した。

## 実装した制御室

- 壁面を合計6×3ブロックとし、オペレーター視点の左側に3×3の円形炉心マップ、右側に3×3の計器盤を配置した。
- 炉心マップは48×48 pxの連続図柄を9枚へ分割した。細かな角形ランプの円形配列と、中央付近の赤・黄・白の発光区画を再現している。
- 計器盤は1ブロック4個、合計36個の丸型アナログメーターを持つ。針は出力、反応度、最高燃料温度、冷却水温度、ボイド率、平均制御棒挿入率の実測値に同期する。
- 操作卓を幅6×奥行2ブロックの大型構造にした。傾斜面、区画別ボタン群、表示灯、木製記録面、赤いAZ-5表示、`221`表示、左側の黒白キャビネットを個別テクスチャと段差モデルで構成した。
- 構造全体は、操作卓12ブロックと壁面18ブロックの合計30ブロック。AZ-5本体をマスターとし、残り29部品の向きと位置が揃った場合だけ動作する。
- 3×3炉心マップのクリック位置を全体座標へ変換し、実炉心の制御棒を1本ずつ選択できる。100段階コントローラー、選択解除、AZ-5の全挿入とラッチ解除は既存の実炉心処理へ接続した。
- Createの真鍮縁、暗色鋼板、赤い表示灯、機械的な針を基調にし、参照画像の密度を保ちつつCreateアドオンとして周囲に馴染む配色へ整理した。

## 教材・回帰試験

- Ponderの制御室シーンを30ブロック構造へ更新し、構築、計器、炉心マップ選択、100段階レバー、AZ-5、ゴーグル読取を日英で説明する。
- GameTestは全壁面タイルが同じマスターへ接続すること、3×3座標が連続すること、選択した1本だけが動くこと、AZ-5が全棒を5秒で全挿入することを確認する。
- アセット生成器からブロックステート、モデル、テクスチャ、レシピ、ルートテーブル、翻訳、Ponder NBTを再現可能にした。

## 検証結果

- Java 21 / NeoForge 21.1.219 / Create 6.0.10：`gradlew build` 成功。
- 単体検証：FluidPhase 25件、Haber-Bosch 1,019件、ReactorPhysics 44件が合格。
- NeoForge GameTest：必須258件すべて合格。合格確定後のテストワールド保存だけがMinecraftのチャンクFuture待ちで停止したため、テスト用プロセスを終了した。
- Ponder検証：71追加シーン、日英334組の説明、85部品リンク、全構造ターゲットが合格。
- 言語検証：実行時Ponder見出し76件と全共有説明が日英とも合格。
- ビジュアル監査：完成品163件、意図しない同一外観0件。
- CI検証器の自己テスト：11件合格。
- NVIDIA GeForce RTX 4060 / OpenGL 4.6 / Direct Renderingで日本語クライアントを起動し、品質チェック84シーンが合格した。最終画像で炉心マップが左、計器盤が右、操作卓が手前に表示されることを確認した。
- 品質判定、画像、JSON、ワールド保存の完了後、終了処理で既知のNVIDIA `libEGL_nvidia.so.0` SIGSEGVが発生した。描画判定と保存成果物には影響しない。

## 成果物

- `/workspace/outputs/Create-Plant-RBMK-control-room.jar`
  - SHA-256：`683447901cf003e54bc8d1d22d8425b0f5fa7629724dda18e53c0b067a4d864d`
- `/workspace/outputs/reactor-control-room.png`
  - SHA-256：`e07fc09ac753cf11eee2916e4cd63fdb53a4426181ebc391bb39f9de659e1791`
- `/workspace/outputs/reactor-control-room-client-check.json`
  - SHA-256：`06a075ce971714c93de72b41bbea09a56469b8a6945a8a5fec556cce01b619bf`
- `/workspace/outputs/reactor-control-room-texture-preview.png`
- `/workspace/outputs/gametest-258-pass.log.gz`
  - SHA-256：`cbee36798b7c86c987cf3c23d5f05ad55739980a28051dc18e0975844334ac9f`

## 主な変更箇所

- `ReactorControlConsoleBlockEntity`：30ブロック構造、部品座標、形成判定、実炉心接続。
- `ReactorConsolePanelBlock`：3×3タイル座標とクリック位置の全体座標化。
- `ReactorControlConsoleRenderer`：3×3炉心ランプ、36本の実測針、AZ-5発光。
- `ReactorConsoleDeskBlock`：6×2操作卓用の列・奥行き状態。
- `RbmkGameTests`：大型制御室、個別制御、AZ-5の回帰試験。
- `RbmkPonderScenes`、`ElectrolysisPonderPlugin`：大型制御室教材。
- `tools/generate_rbmk_assets.py`：全モデル、連続テクスチャ、翻訳、レシピ、Ponder構造の生成。
