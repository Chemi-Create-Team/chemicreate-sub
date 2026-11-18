# 電解セルの電極ヘッド：上から電極板が降りる形にした完了レポート

- 日付：2026-11-16
- 実装コミット：このレポートと同じコミット（`git log -1 -- docs/reports/electrolysis-electrode-head.md`で確認）
- 設計：[電極ヘッドの設計](../specs/electrolysis-electrode-head.md)
- 説明：[フィルタープレス型電解槽](../filter-press-electrolysis.md)

> **追記（同日）**：続く指示でセルを1列・1段にした。セル1×1×長さ1～8、各セルにヘッド1個（電極板16枚、最大128枚は同じ）、制御盤は縦2個（下：ダイヤル、上：計器とSTART/STOP、区画番号は`part`0・1）。1セルの各槽を4,000 mBにして、長さあたりの容量を保った。陰極ガスは小さいX側（軸Xなら小さいZ側）の側面、陽極ガスは反対の側面、原料は底面と奥の端面、苛性ソーダ・再生酸は奥の端面から。ポンプで底面から給液するテスト、両側面の気体、出口満杯、制御盤の2区画の操作を新しい形で確認した。

## 指示

電解セルの上に機械を置き、電極板が上から槽へ降りる形にする。電源は上に挿す。ヘッドはCreateの回転で上下させ、気体の電解と金属の電解採取の両方に使い、金属はヘッドを上げて取り出す（いずれもユーザー確認済み）。

## 実装

| 部分 | 変更 |
| --- | --- |
| 電極ヘッド（新規、`electrolysis_electrode_head`） | 上段の各セルの上に1個ずつ置く。電極板（通常・ニッケル合わせて8枚）、アスベスト隔膜、電解採取の金属を持つ。上面から回転を受け、同じスタックの隣のヘッドへ同じ速さで回転を渡す（Createの独自伝達`propagateRotationTo`）。応力1個4 SU/RPM |
| 上下 | STARTで下降、STOPで上昇。回転しているときだけ動き、32 RPMで16 tick。全ヘッドが下がりきるまで電解しない（停止理由`raised`）。位置と目標はサーバーが持ち、変化時だけ同期してクライアントは補間する |
| 出し入れ | 上がりきっているときだけ。手で入れる、しゃがんで素手で1枚戻す、素手で金属を取る。ファンネルには電極板・隔膜の搬入と金属の搬出だけを公開する（極板は抜き取らない） |
| 電源 | 制御盤右上区画の奥にあたるヘッド（端子ヘッド、`terminal=true`）の上面に2端子。CEEの装置登録をヘッドへ移し、制御盤は電気装置でなくした |
| 電解セル | 点検ハッチ（`OPEN`）、電極と金属の在庫、セル側面の金属出口を削除。上段の天面は開き、ヘッドが乗ると塞がる。気体は上段の外側面から |
| 構造 | ヘッドがそろわないと形成しない。最大はセル32＋ヘッド16＋制御盤4 |

### 主要ファイル

- `block/ElectrodeHeadBlock.java`、`block/entity/ElectrodeHeadBlockEntity.java`、`client/ElectrodeHeadRenderer.java`（回る軸と、位置に合わせて下がる電極板）
- `block/ElectrolysisStructure.java`（ヘッドの位置・端子ヘッド）、`block/entity/ElectrolysisControllerBlockEntity.java`（ヘッドの在庫・運転条件・START/STOPでの上下）、`block/ElectrolysisControllerBlock.java`、`compat/electro/ElectrolysisCellDevice.java`、`compat/electro/ElectroDevices.java`
- `block/ElectrolysisCellBlock.java`、`block/entity/ElectrolysisCellBlockEntity.java`、`client/ElectrolysisCellRenderer.java`、`client/ElectrolysisDisplayState.java`
- `tools/electrolysis_hardware.py`（ヘッドのモデル2種とブロック状態、ハッチのモデル削除、制御盤から端子を除去）、`tools/generate_electrolysis_cell.py`（レシピ・ルート・タグ・翻訳）、`tools/generate_electrolysis_ponder.py`（Ponder構造にヘッドとモーター）
- Ponder：`ElectrolysisCellPonderScenes`（ヘッドの設置・電極板・回転・端子ヘッドへの配線・START/STOPでの上下を追加）、`MetalRecoveryPonderScenes`（電解採取：ヘッドを上げて金属を取る）、製造Ponderの水素・塩素アルカリ・重水素・フッ素の章

### クラフト

電極ヘッド：鉄板2・軸1・銅板2・安山岩ケーシング1で2個（作業台）。

## 確認

- `build`成功。`validate_ponder_language.py`、`validate_plant_tutorials.py`、`validate_translations.py`、`validate_machine_sounds.py`合格。`validate_visual_identity.py`は以前からの未分類8件で失敗したまま（今回の追加分はなし）。
- GameTest：関係する25クラス111件を実行し、以前からの2件（`RecipeVisibilityGameTests.cooledInputsAndAllManufacturingRecipesRemainValid`、`CreativeCatalogGameTests.everyCurrentItemHasExactlyOneAppropriateTab`）以外すべて合格。
- 新規・更新した主なテスト：
  - 実際のクリエイティブモーターを1個のヘッドの上に置くと、3マスのスタックの6個すべてが回り、STARTで全部下がり、STOPで上がる。
  - 上がったヘッドの上のファンネルが電極板を入れ、下がると同じポートが閉じる。ファンネルは極板を抜き取らない。
  - 1個でも上がりかけのヘッドがあると、原料と電力を使わず`raised`で止まり、下ろすと再開する。
  - ヘッドを外すと形成しない。保存・再読込でヘッドの位置・極板・金属を保つ。
  - 電解採取の金属は下がっている間は取れず、STOP後にヘッド上のファンネルで3個出る。
  - 端子ヘッドへ配線した電池で電解が動く。8マスのスタックでヘッド16個・端子ヘッド1個。
- 実画面（Xvfb、日本語）：Ponder「フィルタープレス型電解槽」と「電解採取」を撮影し、ヘッドの列、上のモーター、端子ヘッドへの配線、運転中に極板が槽へ下がり停止後にヘッドへ上がることを確認した。

## 制約と未確認事項

- テストでは、ヘッドの移動を`move(32)`で直接進めている。Createの回転網を通した動きは、モーターを使う1件だけで確認した。
- ヘッドのモデルは自作の仮のもの。仕上げが必要ならFableへの依頼を検討する。
- `client/ElectrolysisClientCheck`（実ワールドでの電解クライアント確認）と`client/LoadClientCheck`は、ヘッドとモーターを置く形に直したが、今回は実行していない。
- 端子ヘッドがスタックから外れたあとも`terminal`のままになり、電線が残る（電力は流れない）。
- 旧ワールドの電解セルに入っていた電極板・金属は失われ、制御盤の端子につないだ電線は無効になる（本格運用前のため移行処理なし）。
- 性能の比較計測はしていない。ヘッドは移動中だけ位置を進め、同期は動き始めと止まったときだけ送る。

## 次回の注意

- テストでヘッドに手で回転数を設定してから`tick()`を呼ぶと、Createの回転伝達が速度の矛盾としてヘッドを壊す。動きだけを確かめるなら`move(rpm)`を使うこと。
- 製造Ponderの章は名前を指定して再生成すること（全章の再生成は無関係な章を書き換える）。

## テクスチャ（追記）

ユーザーの指示で、Create風に作り直した。ふた付き槽の銀色の板（`reactor/silver/shell`）の溝の部分を地にし、つながっていない縁にCreateの安山岩ケーシングの2ピクセルの縁取りを重ねる（`tools/electrolysis_hardware.py`の`andesite_frame`・`silver_field`。安山岩ケーシングはGradleが解決したCreateのjarから読む）。電解セルの外装・上縁・制御盤の地板と、吸収塔の側面（段の継ぎ目に縁取り）がこの作り方で、吸収塔の天板は縁を安山岩ケーシングの色にした。Ponderの実画面で、吸収塔の大小と電解セル・電解採取の見た目を確認した。
