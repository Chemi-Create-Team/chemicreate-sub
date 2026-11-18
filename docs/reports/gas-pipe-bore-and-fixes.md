# 気体パイプの容積・太い気体パイプ・往復式コンプレッサー吐出弁ほか 完了レポート

作業日：2026-11-10。実装コミット：`5bdfe0378826b24c51ef8869a7fa3b156fd414ba`。

## 変更内容

| 依頼 | 対応 |
| --- | --- |
| ふた付き槽の窓から見える水が薄い | 点検窓を有効にした槽で流体の頂点アルファを1/3にしていた処理（`1c205b1e`）を外し、Createのタンクと同じ濃さに戻した |
| クァーリーの支柱に鉄骨を使う | 専用ブロック「クレーンの脚」を廃止し、Createの鉄骨（`create:metal_girder`）を脚にした |
| フレアスタック先端部の色 | 先端部の胴を、白い蒸気煙道用鋼板から専用の鈍い鋼色（`block/flare/steel`）へ変更した |
| パイプ1ブロック50 mBの容量と状態方程式 | 気体パイプの実容積を1ブロック50 mBにし、在庫・温度・容積から圧力を求める。流量も圧力と容積から決まる |
| 太いパイプ・流量4倍 | ブロック全体を占める「太い気体パイプ」を追加。実容積200 mB、通す気体は4倍 |
| 往復式コンプレッサーの加圧／パイプ側の判定 | シリンダーが「真上のエンジン」しか見ておらず、側面に付けたエンジンの設定圧を無視していた不具合を修正した |

### クレーンの脚 → Createの鉄骨

- `CraneStructure.scan`：レール端の下の鉄骨を数え、3〜32段なら脚、3段未満の鉄骨か上面が平らなブロックなら梁支持（脚0段）とする。33段以上は不成立。4隅の高さが揃う条件は従来どおり。
- `CraneDriveBlockEntity`：枠組みの自動建設は鉄骨を格納庫から使って設置する。搬入できる補給品も鉄骨へ変更。採掘では鉄骨とガントリーシャフトを掘らない。
- `crane_leg` の登録・アイテム・モデル・テクスチャ・レシピ・ルートテーブル・タグ・翻訳を削除した。Ponder（門型クレーン、採掘場、核融合炉の保守）の構造と文言、`tools/generate_crane_assets.py`・`tools/generate_plant_systems.py` も鉄骨へ更新した。
- 既存ワールドに置かれた `crane_leg` は登録が無くなるため消える。本格運用前の方針（旧型を残さない）に従った。

### 気体パイプ

- `GasPipeBlock` に太さ（`large`）を持たせ、同じ `GasPipeBlockEntity` を共有する。太い管は当たり判定がブロック全体で、接続面にフランジ、非接続面に胴板を描く（`blockstates/large_gas_pipe.json` のmultipart）。
- 容積：`GasPipeBlockEntity.VOLUME = 50`、太い管は `VOLUME × LARGE_BORES(4) = 200`。圧力は `FluidPhase.pressure(在庫, 容積)`（理想気体、温度は在庫の温度）。
- 流量：`GasNetworks.passes` を追加。

```text
1回の圧力均し（10 tick）で容器に出入りできる質量 [kg]
  = P × (つないだ管の容積の合計 [mB] × 10 tick) / 1e6 × M / (R × T)
P：送り側の圧力（固定圧の吐出はその設定圧、通常は容器と均し後圧力の高いほう）
T：流れる気体の温度、M：モル質量
```

  - 細い管は実体積50 mB/t、太い管は200 mB/t。同じ管でも圧力が高いほど多くの気体が通る。
  - 均しの目標圧力・保存則（出した量＝入れた量、余りは送り元へ戻す）は従来どおり。固定圧の吐出（エアーフィルター、往復式シリンダー）にも同じ上限を掛ける。
  - 制約：上限は容器とつながる管で決まる。配管の途中にある細い区間は絞りとして計算しない。
- 太い気体パイプのレシピ：気体パイプ4本（2×2）→ 1本。ゴーグルに「容積・気体」の行を追加（4行以内）。
- Ponder「気体パイプと管の太さ」（`plant/gas_pipes`）を追加。気体パイプ・太い気体パイプから開く。生成器は `tools/generate_large_gas_pipe.py`。

### 往復式コンプレッサー

- 原因：`ReciprocatingCylinderBlockEntity.valveOpen` / `openDischarge` が `worldPosition.above()` のエンジンだけを参照していた。Ponderどおり側面へ取り付けると、吐出弁が常に開き（Createポンプで設定圧未満の気体が抜ける）、気体パイプ網からは吐出口が存在しない扱い（`openDischarge()==0`）になっていた。
- 修正：6面のうち、このシリンダーへ取り付いたエンジン（クランク方向で判定）の設定圧の最大値を使う。エンジンが無い場合に弁が開く挙動は従来どおり。

## 主な実装箇所

| ファイル | 役割 |
| --- | --- |
| `client/LiddedVesselRenderer.java` | 窓付き槽の流体の濃さ |
| `block/CraneStructure.java` / `block/entity/CraneDriveBlockEntity.java` / `registry/CECrane.java` | 鉄骨の脚 |
| `tools/generate_flare_assets.py`、`models/block/flare/tip.json`、`textures/block/flare/steel.png` | フレア先端部の鋼色 |
| `block/GasPipeBlock.java` / `block/entity/GasPipeBlockEntity.java` / `registry/CEGasPipes.java` | 太さ・容積 |
| `transport/GasNetworks.java` | 管の太さによる流量上限 |
| `block/entity/ReciprocatingCylinderBlockEntity.java` | 吐出弁が側面のエンジンを認識 |
| `gametest/GasPipeGameTests.java` | 追加・変更した試験 |

## 確認

- `./gradlew compileJava`：成功。
- `./gradlew runGameTestServer`（`-Dcreateplant.gameTestClasses` で対象クラスを限定）：
  - `GasPipeGameTests` 10件すべて合格（最終コードで実行）。追加した `aLargeGasPipeHoldsAndPassesFourTimesTheGas`（容積50／200 mB、保存、状態方程式どおりの圧力、同じ時間に通る気体が4倍）、`aSideMountedEngineKeepsTheDischargeValveShutBelowItsSetPressure`（側面エンジンの設定圧未満では吐出が閉じ、到達後はその圧力で開く）を含む。`aReciprocatingCompressorDeliversTheSetPressureThroughAGasPipe` は側面取り付けへ変更し、タンクが設定6 barになることを確認した。
  - `ReciprocatingCompressorGameTests`、`CraneGameTests`、`PlantTutorialGameTests`、`SurvivalReachabilityGameTests`、`GasBellGameTests`、`GasSafetyLoadGameTests`、`PowerPlantGameTests`：全件合格（太い気体パイプの試験を直す前の実行。その後の変更は試験コードのみ）。
  - 不合格2件は変更前のコミット `1e0801a2` でも同じ内容で不合格になることを確認した。今回は修正していない。
    - `CraftingRecipeGameTests.theRefiningEquipmentNeedsNoRefinedMetal`：「tagged: 10」（期待8）。
    - `FlareGameTests.realGasPipeRuptureReleasesInventoryOnceAndTorchIgnites`：「Overpressure bursts the real pipe and releases exactly one cloud」。

## 未確認事項

- ゲーム画面での目視確認は行っていない：窓越しの水の濃さ、フレア先端部の色、太い気体パイプのモデル・接続面の向き、Ponderの表示、鉄骨の脚の見た目。
- 流量上限による性能（tick時間）の変化は計測していない。計算は既存の10 tickごとの均しに、ノード1個につき数回の乗算を足しただけである。
- 専用サーバーの単独起動、他MOD同梱環境での確認は行っていない。

## 次回の注意

- テクスチャ生成器（`generate_crane_assets.py` など）は、白化前へ復元済みの画像と一致しないものがある。全体を再実行せず、必要な関数だけを呼ぶこと（今回は `ponder()`、`texture('steel')`、`models()` のみ）。
- 気体の流量を配管途中の絞りまで扱う場合は、`GasNetworks` の一括均しを区間ごとの計算へ変える必要がある。
