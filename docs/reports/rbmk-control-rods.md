# 引き継ぎ：RBMK炉心と制御棒システム

実装コミット：`a3ab746`、高密度化・円形炉心・Ponder修正 `1910162`（ブランチ `work`）。設計の正本は [docs/rbmk-reactor.md](../rbmk-reactor.md)。

## ユーザーと決めた範囲

| 論点 | 決定 |
| --- | --- |
| 深度の設定 | レッドストーンのアナログ信号（0～15）のみ。独自GUI・数値入力は作らない |
| 駆動とマルチブロック | Createのスティッキー・メカニカルピストンが制御棒ブロックを実際に動かす。駆動装置はギアシフトとして回転を制御する |
| 反応度 | 本格的な炉心まで作る（燃料・冷却水・ボイド・キセノン・炉心崩壊） |
| 仕様第4項 | 続きなし。黒鉛先端が水を押し出すポジティブスクラム効果として解釈 |

## 追加修正（ユーザー指摘への対応）

- 燃料チャネルブロック1つ＝燃料棒4本＋そのブロック自体の黒鉛減速材。ペレットは燃料棒1本に1個（1ブロック4個）、出力・熱容量・冷却・水量は4本分。黒鉛だけの列は任意で、燃料を薄めて出力を下げる。上面は燃料キャップ4つ、制御棒チャネルは赤いキャップ。
- 炉心は長方形（3～12）に加え、直径12までの円形を許可。円形は円柱の漏れと中心距離の径方向重みを使う。
- Ponderが見切れていたため、炉心2段・直径7の円形にして全高を16→13ブロックへ詰め、縮尺を0.55にした。**クライアントでの表示は未確認。**
- GameTest追加：直径12の円形炉心の形成と、円でも長方形でもない平面形の拒否。テンプレートを15×16×15へ拡大。
- 挿入深度は1ブロック単位ではない：目標は移動距離×信号/15を1/16ブロックに丸めた位置で、実測で3.4375ブロック（信号13・移動4）に停止・保持することをGameTestで確認済み。分解能の上限はレッドストーンの16段階。

## 実装内容

- ブロック：黒鉛減速材、燃料チャネル、制御棒チャネル（当たり判定なし）、制御棒吸収体、黒鉛ディスプレーサ、制御棒駆動装置。アイテム：使用済み燃料ペレット。
- 主要ファイル
  - `nuclear/ReactorPhysics.java`：点動特性・キセノン・反応度項・ボイド（ゲーム非依存、単体テスト対象）
  - `nuclear/ReactorCoreStructure.java`：炉心の直方体走査と故障理由
  - `nuclear/ReactorCore.java`：コントローラーの毎tick計算、制御棒位置の読み取り、AZ-5、同期
  - `nuclear/ReactorCores.java`：BlockEntityの無い黒鉛の変更通知と未形成炉心の再走査条件
  - `block/entity/FuelChannelBlockEntity.java`：ペレット・水・燃料温度・ポート・ゴーグル
  - `block/entity/ControlRodDriveBlockEntity.java`：深度の閉ループ制御、SCRAM増速
  - `mixin/ControlRodPistonStopMixin.java`、`mixin/MechanicalPistonAccessor.java`
  - `client/ControlRodDriveRenderer.java`（針メーター）、`ponder/RbmkPonderScenes.java`（2シーン）
  - `tools/generate_rbmk_assets.py`：16×16テクスチャ、モデル、ブロックステート、ルート、タグ、レシピ、日英翻訳、Ponder NBT、GameTestテンプレート
- 計算方法の要点は設計記録の「物理モデル」「制御棒駆動装置」を参照。

## 調査で判明したCreateの挙動（次回の注意）

1. ピストンは停止・反転時に `Math.round(offset * 16) / 16`（整数除算）で整数ブロックへ切り捨てる。駆動装置に回されるピストンだけ1/16ブロック丸めに直すMixinを入れた。Create更新時は `LinearActuatorBlockEntity.onSpeedChanged` の `offset` 書き込みを確認すること。
2. シーケンシャルギアシフトの `SequenceContext`（TURN_DISTANCE）でピストンを止めると、停止tickに誤って衝突扱いになり整数位置へ戻る現象をGameTestで再現した。このため駆動装置は `SequenceContext` を使わず、目標の1 tick手前から16 RPM相当へ減速して自分で止める。
3. 下向きのコントラプションは障害物の1ブロック手前で止まる。制御棒列の下は2ブロック空ける必要がある（Ponder・説明・テストもこの前提）。
4. 下向きメカニカルピストンは正の回転で伸長する（`getMovementSpeed` が `-movementModifier` を掛ける）。
5. RotationPropagatorは回転方向の矛盾や頻繁な速度変化（flicker 128超）でブロックを破壊する。駆動装置は必ず切り離してから再接続し、指令変更は移動1回につき最大3回。

## テスト結果

- `./gradlew build`：成功（`reactorPhysicsTest` 18件、既存の単体テストも成功）。
- `./gradlew runGameTestServer`：全217件成功（追加修正後）。追加5件（下記＋円形炉心）：
  - `coreShapesAndFaults`：形成、コントローラー位置、列の混在・外側接触・制御棒列なしの故障理由
  - `coolantAndFuelPortsConserve`：シミュレーションで在庫不変、水のみ受け入れ、容量、ペレット4個上限、新燃料は抜けない、加熱で蒸気が優先して出る、潜熱付き、ボイド発生
  - `driveHoldsFractionalDepthAndScrams`：信号13で3.4375ブロックに停止し、回転停止後も端数位置を保持、SCRAMで全挿入
  - `azFiveFromAnyChannelInsertsLinkedRods`：燃料チャネルへの信号でAZ-5、連携した棒の吸収体が列を満たす、反応度が負
- テクスチャは拡大一覧画像で確認した。

## 未検証・制約

- **クライアント未起動。** モデルの向き、針の回転方向、ディスプレーサや吸収体の見え方、Ponderシーンの再生、SplitShaftVisualによる軸の描画は実画面で確認していない。
- 炉心の連続運転（臨界到達、出力平衡、キセノンの谷、ボイドによる暴走、炉心崩壊の爆発）はゲーム内で確認していない。物理は単体テストのみ。
- 保存・再読込、チャンク境界、実際のCreateパイプ・ファンネル・ポンプでの接続は未確認（ハンドラーはGameTestで直接呼んだ）。
- 制御棒吸収体のレシピは鉄板と石炭ブロックで代用（ホウ素・炭化ホウ素の素材が未実装）。使用済み燃料の再処理は無い。
- 崩壊熱、黒鉛温度係数、チャネル圧力（大気圧飽和で近似）、制御棒チャネルの冷却水は扱っていない。
- Ponderは延長ポールを1本伸ばした状態（ヘッドがブロック）で描いている。実際の組立は全引抜（ヘッドがピストン内）なので、見た目が1ブロック分異なる。
- 制御棒チャネルは1ブロックに1本（燃料の4本に対して密度は1/4）。
- JEIカテゴリは追加していない。zh_cnの翻訳は無い。
- 多数の炉心を置いた時のMSPTは未計測。

## 範囲外で見つけた不具合

- `tools/generate_ponder_language.py` と `tools/validate_ponder_language.py` は、言語ファイルに `create_heavy_indusstry.ponder.shared.pressure_relief.title` が無いため本変更前から失敗する。今回のPonderの見出しキーは生成ツール側で直接書いている。
