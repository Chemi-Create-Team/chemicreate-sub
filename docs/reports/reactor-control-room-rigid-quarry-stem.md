# 原子炉物理制御卓とクァーリー剛体ドリルステム 完了レポート

## 実装コミット

- `19f7ebb56f357f055038916c370bb88a8649c784` (`Add physical reactor controls and rigid quarry drill stem`)
- 完了レポート後に更新された `origin/main` (`3165f210de2d4a235b3202256e9e294c8e8c8b2d`) は、非強制マージ `019d14be57735cc8a241516f0595ff6a7c0eb992` で取り込んだ。

## 実装内容

### クァーリー

- クァーリーのメカニカルドリルを吊っていたチェーンを廃止し、四隅の鋼製ガイド、中央駆動軸、赤錆色のカラーを持つ角型ケリーステムへ置き換えた。
- ステムは掘削深度に合わせてテレスコープ状に移動するが、全深度で連続した剛体支持柱として描画する。旧 `quarry_chain` モデルとテクスチャは削除した。
- 原子炉保守用の固定長マストとはモデル、テクスチャ、配色を共有せず、既存の赤錆色クァーリー桁・台車と組み合わせた。

### 原子炉制御卓

- 独自GUIを追加せず、炉心マップ、既存100段階制御棒コントローラー、6連計器パネル、ガード付きAZ-5本体からなるワールド内2×2操作卓を追加した。
- 完成した操作卓は、64ブロック以内で最も近い形成済み炉心へ接続する。形成済み炉心レジストリだけを参照し、ブロック全探索や遠方チャンクの読み込みは行わない。
- 炉心マップは実在する制御棒を位置灯として描画する。クリックした1本を選択し、下の0～100レバーでその棒だけの目標深度を設定する。スニーククリックで個別目標を解除し、従来のキャップ／リンク目標へ戻す。
- 個別目標は炉心コントローラーへ保存する。上限は最大炉心の `13 × 13 × 4 = 676` 本で、存在しなくなった棒の目標は炉心再形成時に除去する。
- AZ-5は押下後に制御卓側でラッチし、個別目標とキャップ目標の全てに優先して全制御棒を5秒で全挿入する。スニーク操作で制御卓ラッチを解除するが、炉心側のレッドストーンAZ-5入力は独立して残る。
- 計器は熱出力、反応度、最高燃料温度、冷却水温度、ボイド率、平均制御棒挿入率の6種類。クライアント側で各tickに目標との差の18%を補間し、同期の間も針を滑らかに動かす。
- 計器正規化は、熱出力を `log10(1 + MW) / 3`、反応度を `0.5 + dollar / 8`、最高燃料温度を0～1200 ℃相当、冷却水温度を0～300 ℃相当、ボイド率と平均挿入率を0～1として表示範囲へ制限する。
- サーバーは5tickごとに炉心を採取し、量子化した値と炉心マップの指紋が変わった場合だけ同期する。各棒はマップX、マップY、実深度、個別目標を各1 byteへ詰める。
- ゴーグルは最大4行に収め、Create Display Linkへ従来の炉心値、冷却水温度、選択棒の実深度／個別目標を公開した。
- 低彩度の緑灰色鋼、真鍮、電子管、赤いAZ-5を16×16基準の専用テクスチャで表現した。Create部品を材料にする3レシピ、ドロップ、日英翻訳、Ponderシーンを追加した。

## 主要ファイル

- `src/main/java/net/createplant/block/entity/ReactorControlConsoleBlockEntity.java`: 物理操作卓の接続、選択、計測、同期、ゴーグル情報
- `src/main/java/net/createplant/client/ReactorControlConsoleRenderer.java`: 棒位置灯、選択リング、6本の針、AZ-5点灯
- `src/main/java/net/createplant/nuclear/ReactorCore.java`: 棒ごとの永続目標と制御卓AZ-5ラッチ
- `src/main/java/net/createplant/client/CraneRenderer.java`: クァーリー剛体ステム描画
- `tools/generate_rbmk_assets.py` / `tools/generate_crane_assets.py`: 再生成可能なモデル、テクスチャ、データ、Ponder資産
- `src/main/java/net/createplant/gametest/RbmkGameTests.java`: 個別棒制御とAZ-5全棒優先の回帰テスト

## 検証結果

- JDK 21、NeoForge 21.1.219、Create 6.0.10で `clean build` 成功。FluidPhase 25件、HaberBoschProcess 1019件、ReactorPhysics 44件も成功した。上流マージ後にも同じ `clean build` を再実行して成功した。
- 実装コミット時点のGameTestは `241 GAME TESTS COMPLETE IN 37.37 min`、`All 241 required tests passed :)`。新テストで同一2×2束の2本を装荷し、片方だけの63%目標、AZ-5による両方の全挿入、ラッチ解除を確認した。合格結果の出力後、大規模爆発テスト由来のワールド保存が長時間続いたためプロセスは手動終了したが、結果行は圧縮ログへ保存済みである。上流マージ後は `clean build` と実クライアント検査を再実行した。
- リソースJSON 1,288件を全件解析し、`git diff --check`も成功した。
- NVIDIA GeForce RTX 4060、OpenGL 4.6.0 NVIDIA 595.99.02、ハードウェア直接描画で実クライアントを起動した。マージ前は日本語Ponder 76シーン、上流マージ後は追加シーンを含む `CLIENT QUALITY PASSED: 77 scenes, ja_jp` となり、ランタイム翻訳も成功した。原子炉制御卓とクァーリーの画像を目視確認した。
- クライアント検査はPASSと全ワールド保存の後、環境側 `libEGL_nvidia.so.0` の終了処理でSIGSEGV、終了値134になった。描画検査や保存前の失敗ではない。

## 既知の既存事項と未検証範囲

- 全体用 `validate_ponder_language.py` は、今回未変更の古い `pressure_relief` ヘッダー一覧を理由に失敗する。
- `validate_plant_tutorials.py` は、今回未変更の `reactor_plant_vessel` と `reactor_plant_vessel_integration` がメタデータ `[9, 8, 9]` に対して実体 `[9, 7, 9]` であるため失敗する。
- 最大676本を配置した炉心での長時間サーバープロファイル、実マルチプレイヤーでの同時操作、旧ワールドを跨ぐ再ロードの手動確認は行っていない。探索、同期、保存には本数・距離上限を設け、自動テストと単体クライアントで対象経路を確認した。

## 成果物

| ファイル | SHA-256 |
| --- | --- |
| `create-heavy-indusstry-0.1.0.jar` | `8a92db46261d9645c90e095eb0ae58de5d51ba479ccf96c2560631bd72302e19` |
| `reactor-control-room.png` | `f29a7b0148c8c2f9131c4103a6fb5644608fddca8fe5bb57c5b43f68d8316134` |
| `quarry-rigid-drill-stem.png` | `d9a77cf3e092ad5ff0fd71876962cbe310c6816322e69556e1be792dcef167b7` |
| `client-quality-result-ja_jp.json` | `7fb8194e763d5abdc787432bb54c36443744a2dd381d96be110d8dd15c401b2e` |
| `gametest-241-pass.log.gz` | `93afd47a2aafd7d0d1512c8af46f06df1db4c04e6a018668045609cf24203720` |

成果物は `/workspace/outputs` に保存した。
