# ChatGPT引き継ぎ：鋼材ブロックのガスホルダー球

- 実装コミット：`3dff621`（`origin/work` の `6d6962b` へリベース済み）
- レポートは上記の検証済みコードを指す。レポート保存のみのコミットはその後に作成する。
- 対象：Minecraft 1.21.1 / NeoForge 21.1.219 / Create 6.0.10-281、ブランチ `work`。

## 実装した範囲

Createの鋼材ブロック（`create:industrial_iron_block`）で中空のピクセル球を組むと、気体専用の貯蔵設備になる。コントローラーはない。開放円筒形タンク（タンク構成ブロック）と同じ構成で、所有者・世代・液体の受け渡しを実装した。

- `block/GasHolderStructure.java`：球の判定。直径ごとの球殻オフセットを事前計算し、設置位置を含む候補の中から最小の球を探す。
- `block/GasHolderShellBlock.java`／`block/entity/GasHolderShellBlockEntity.java`：成立時に鋼材ブロックと置き換える外殻。所有者、流体ハンドラー、ゴーグル表示、状態メッセージ、同期を持つ。
- `event/GasHolderEvents.java`：鋼材ブロックの設置を `NeighborNotifyEvent` で検知する。ワールドごとのキューに入れ、tick末に1 tickあたり最大8件を探索する。
- `thermal/FluidThermal.add`：同じ流体で温度だけが違う搬入を、熱容量で混合して受け入れる。ガスホルダーと開放型タンクの両方で使う。
- 登録・リソース：`CEBlocks.GAS_HOLDER_SHELL`、`CEBlockEntityTypes.GAS_HOLDER_SHELL`、Capability登録とイベント登録（`CreatePlant`）を追加した。リソースは次のとおり。
  - blockstate：Createの `industrial_iron_block` モデルを参照する。
  - loot：Createの鋼材ブロックを落とす。
  - タグ：`mineable/pickaxe` と、Createの `wrench_pickup`。
  - 日英翻訳。
  - Ponder `gas_holder`（`gas_tank` に登録）。
- `GasHolderGameTests.java`：5件のGameTestを追加した。`TankClientCheck` にガスホルダーの実画面確認を追加した。
- 文書：[gas-holder.md](../gas-holder.md)、[tank.md](../tank.md)（温度混合の記述）。

## 形状と計算

- **球の判定：** 各軸で `d=2c-(直径-1)` とし、`dx²+dy²+dz² ≤ 直径²` を満たすセルが球。開放型タンクの円の判定を3次元にしたもの。
- **球殻の条件：** 外側に面を持つセル。鋼材ブロックか変換済みの外殻でなければならない。
- **内部の条件：** 球殻以外の球セルは空気でなければならない。
- **球の外：** 判定しない。他のブロックに触れていても成立する。
- **ユーザーの画像：** 等角投影の画像をボクセルで当てはめ、直径8の上の規則の球と一致することを確認した。280セル、うち球殻144。
- **対応する直径：** 4〜16。探索候補の総数を3,472に抑えるための上限。
- **容量：** 球全体（内部と球殻）1ブロックにつき8,000 mB。開放型タンクと同じ規則。直径8で2,240,000 mB。
- **圧力：** 表示は `FluidPhase.pressure(内容物, 容量)`（絶対圧）。

## 外殻を置き換える理由

Createのパイプ接続・流体ネットワーク・ポンプ・パイプのリム描画は、BlockEntityを持つブロックの流体Capabilityしか見ない（`FluidPropagator.hasFluidCapability`、`FlowSource.FluidHandler`、`PumpBlockEntity`）。鋼材ブロックにはBlockEntityがない。そこで成立時に、BlockEntityを持つ見た目が同じ `gas_holder_shell` へ置き換える。Createへのミックスインは追加していない。外殻はCreateのモデル・材質・音・道具の条件をそのまま使い、壊すと鋼材ブロックに戻る。破損中も外殻のまま残るため、修理すると同じホルダーとして再び成立する。

## 検証結果

- Gradle `build`：成功（ユニットテストも成功）。
- リベース後のGameTest：284件すべて成功。ガスホルダー専用テストは次のとおり。
  - 成立と全球殻の変換、容量、液体の拒否、気体の受け入れと圧力。
  - 破損中の気体保持と、鋼材ブロックでの修理による再成立。
  - 所有者を撤去したときの気体の受け渡しと、1回だけの再吸収。
  - 内部に物がある球と、中まで詰まった球の不成立。
  - 実際に回転させたメカニカルポンプとCreateパイプで、ガスタンクから1,000 mB以上を総量を減らさずに搬入。
- 温度の不一致による搬入停止を修正した。修正前はポンプで32 mBしか入らなかった。開放型タンクにも同じ問題があり、同時に直した。
- Ponder検証スクリプトと日英翻訳の検証：成功。
- クライアントでの確認：
  - 実画面で、成立・容量2,240,000 mB・成立後に置いたパイプの接続（東面がtrue、Createのフランジ表示）を確認した。
  - 外殻が鋼材ブロックと同じ外観であることを確認した。
  - Ponder `gas_holder` の表示を撮影した。

## 制約・未検証

1. 鋼材ブロックの検知は隣接更新に依存する。隣接通知なしで置かれたブロックは、別の更新が届くまで検知されない。変換済みの外殻は右クリックや所有者の定期検証で再判定される。
2. 破裂上限・安全弁はない。圧力は表示のみで、容量で量を制限する。
3. 錆びた鋼材ブロック（`weathered_iron_block`）は球殻として使えない。
4. 直径17以上には対応していない。
5. Create製Contraptionで外殻を動かす操作、他MODによる一括設置、マルチプレイヤーでの同時操作、大規模設置時のMSPTは測定していない。探索費用は候補数からの見積もりで、計測値ではない。
6. ゴーグルの実画面表示（4行の内容）は撮影していない。右クリック時のメッセージもGameTest外では確認していない。

## 次の開発で注意

- 外殻ブロックはアイテムを持たない。クリエイティブタブやレシピへ追加しない。
- 球殻の材質を増やす場合は、`GasHolderStructure.isMaterial` と、イベント側の検知対象を両方変える。
- 開放型タンク・ガスホルダーは `FluidThermal.add`／`compatible` で流体を比較する。厳密な `isSameFluidSameComponents` に戻すと、温度が変わる搬入が止まる。
- Ponder生成スクリプト `generate_plant_tutorials.py` は、コミット済みの `tutorials.json`・翻訳と同期していない（`battery_buffer` などを生成する）。全体を再生成せず、対象シーンだけを反映すること。
