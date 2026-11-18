# ChatGPT引き継ぎ：鉄の流体パイプ

- 実装コミット：本レポートと同じコミット。途中経過はユーザーのコミット`dd775c7`。
- 対象：Minecraft 1.21.1 / NeoForge 21.1.219 / Create 6.0.10-281、ブランチ `work`。

## 依頼と方針

依頼は「Createの流体パイプと接続・描画の仕様が全く同じで、テクスチャだけが違う鉄の流体パイプ」。ユーザーの選択で、レンチやケーシングで変形しても鉄のまま往復するよう、ガラス版とケース入り版も用意した。

| ブロックID | 名前（日／英） | Createの元 |
| --- | --- | --- |
| `iron_fluid_pipe` | 鉄の流体パイプ／Iron Fluid Pipe | `fluid_pipe` |
| `iron_glass_fluid_pipe` | ガラス付き鉄の流体パイプ／Glass Iron Fluid Pipe | `glass_fluid_pipe` |
| `iron_encased_fluid_pipe` | ケース入り鉄の流体パイプ／Encased Iron Fluid Pipe | `encased_fluid_pipe` |

## 実装

- **ブロック**：`block/Iron*FluidPipeBlock.java`。Createのブロッククラスを継承し、Createが`AllBlocks`の銅パイプを直接指定している箇所だけを上書きした。処理内容はCreate 6.0.10のソースと同じ。上書きした箇所は次のとおり。
  - レンチ変形：パイプ→ガラス、ガラス→パイプ、ケース入り→パイプ。
  - ガラスへの銅ケーシングの適用。
  - ピックブロック、設計図の必要アイテム、BlockEntity型。
- **BlockEntity**：Createの`FluidPipeBlockEntity`と`StraightPipeBlockEntity`を、そのまま自前の型（`CETransport`）で登録した。
- **ケーシング**：Createと同じく銅ケーシングで包む。`EncasingRegistry`に鉄パイプとケース入り鉄パイプの組を登録した。ケースの見た目も銅ケーシングのまま。
- **描画**（`client/IronFluidPipeModels`）：blockstateはCreateのJSONをそのまま写し、Createのモデルを参照する。
  - Createと同じく`PipeAttachmentModel`で包み、ケース入りは銅ケーシングのCTも適用する。
  - 外側のラッパーが、Createのパイプスプライト4種を鉄版に差し替える（`pipes`、`pipes_connected`、`glass_fluid_pipe`、`encased_pipe`）。実行時に付く縁・排出口・ブラケットも差し替わる。
  - 差し替えたquadは元quadの同一性でキャッシュする。Createのquadは焼き込み時に一度だけ作られるので、キャッシュの大きさには上限がある。
  - ガラス版は、Createと同じ描画レイヤー（cutout mipped）、`TransparentStraightPipeRenderer`、`GlassPipeVisual`を登録した。ケース同士のつながり（`CASING_CONNECTIVITY`）も登録した。
- **テクスチャ**（`tools/generate_iron_pipe_assets.py`）：Createの4枚をjarから読み、銅色の画素を明るさの順に、添付画像から採取した鉄の色の段階へ置き換えた。暗い赤の管内は暗灰色にした。
  - 形と陰影の画素はCreateと同じ。CreateはMITライセンス。
  - 同じスクリプトで、blockstate、アイテムモデル、レシピ、ドロップも生成する。
- **レシピ**：Createの流体パイプの2種（`SCS`と縦の`S/C/S`、4個）を、鉄板と鉄インゴットに置き換えた。既存レシピとの衝突はない。
- **ドロップとタグ**：
  - ドロップ：3種とも鉄の流体パイプを落とす（Createと同じ）。
  - 採掘タグ：つるはしは3種、斧はケース入りのみ。
- **このMOD内の扱い**：Createの銅パイプと同じ扱いにした。材質は`PipeMaterial.COPPER`相当。
  - 摩耗（`PipeWearBehaviour`）、放熱（`PassiveHeat`のPIPE_UA）、ツールチップ、ゴーグル表示。
  - Create Display Link：MOD内の全BlockEntityと同じく自動で対象になる。
- **プロパティ**：Createの銅パイプと同じく、銅ブロック基準の硬さ。色と音だけを鉄（METAL）にした。

## テスト結果

- `./gradlew build runGameTestServer`：成功。必須テスト296件がすべて合格した。追加した4件は次のとおり。
  - 鉄パイプ、ガラス版、ケース入り版それぞれで、実際のCreateポンプが1000 mBを損失なく運ぶ。
  - レンチとケーシングで往復しても鉄のまま。ピックブロックも鉄パイプになる。
- 実画面（`-Dcreateplant.clientQualityCheck=true -Dcreateplant.ironPipeClientCheck=true`）：同じ配管をCreateの銅（奥）と鉄（手前）で組み、2方向から撮影した。
  - 開放端の縁、直線、分岐の箱、ガラス窓、ケース入り、曲がりの形が銅と一致し、色だけが違うことを確認した。
  - 最初はガラス窓が黒く描画された。原因は描画レイヤーで、Createと同じcutout mippedを設定して修正した。
- `tools/validate_*.py`：10本中9本が合格した。`validate_visual_identity.py`は圧縮機の既存の問題で失敗し、今回の変更とは無関係。

## 未検証・注意

- **ガラス窓の液体表示：** 液体が流れている状態は、画面では確認していない。運搬そのものはGameTestで確認済み。
- **ブラケット：** 鉄パイプにブラケットを付けた状態は、画面では確認していない。
- **確認範囲：** 確認したのは日本語クライアントだけ。
- **Createの更新時：** Createのパイプのクラスやテクスチャが変わったら、`Iron*FluidPipeBlock`の上書き箇所と生成スクリプトを見直す。
