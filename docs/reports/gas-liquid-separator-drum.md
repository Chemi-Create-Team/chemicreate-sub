# 気液分離ドラムの実装完了レポート

実装日：2026-11-17。仕様は `docs/specs/gas-liquid-separator-drum.md`。実装コミットは `6cb550649a14dcaad3a477b90e61e86cc3fb08b5`、作業開始時のmainは `c00ecc9faf0bc7a04040ffe8ad2d946718917440` です。

## 実装した動作

`ammonia_separator` を幅1・高さ2～6段の縦型ドラムにしました。同じ高さで横2×2に並べても4本の独立した列になり、7段目は別のドラムになります。1段だけでは動かず、従来の「短すぎる」表示を出します。1段につき1バッチを同時に処理し、既存レシピの60 tick周期を維持します。

各在庫は1段あたり16,000 mBの基準容量を維持します。入力は最上段の4側面、気体出力は最上段の上面、液体出力は最下段の下面です。冷えた合成混合ガス18,000 mBを2段で処理すると、60 tickで液体アンモニア2 mBと循環原料16,000 mBになります。水・蒸気の別バッファ、熱・高圧ガスの既存の扱い、レシピ、クラフト材料・個数、破壊時のガス放出は変更していません。反応塔にも変更はありません。

## 外観と描画

対辺14 pxの正八角形の胴、各段のリブ、上下3 pxの段付き鏡板、6×6 pxのノズル、8×8 pxのフランジを生成しました。フランジはブロック上面16 px・下面0 pxのCreate配管位置にそろえ、上段側面には4面とも入口の座板を置いています。胴は4本の交差する矩形を使って八角形の外周を構成し、不要な内部の側面を作りません。

正面の幅2 px・高さ1～15 pxのガラス管と金具は静的モデルです。最下段の制御ブロックのBERだけが、液体アンモニアまたは水の実在庫と全容量の比率を読み、下から各段のガラス内を元流体のテクスチャで満たします。空なら液体を描きません。液量の同期は既存の `syncNeeded` に接続し、描画から在庫を変更しません。

正面は設置時のプレイヤー側を `Front` へ保存し、制御ブロックの値を各段の `facing` に反映します。再読込と積み増しでも維持します。通常のレンチでは窓へ切り替えず、Createの `SHAPE` はplainを保ちます。アイテムは単独の上下鏡板付きドラムです。

## 主要ファイル

| ファイル | 変更内容 |
| --- | --- |
| `block/AmmoniaSeparatorBlock.java` | 正面、配置、窓切替の無効化、胴と鏡板に近い選択・衝突形状 |
| `block/entity/AmmoniaSeparatorBlockEntity.java` | 最大幅1・高さ6、Frontの保存、制御ブロックの正面、液面計に必要な量・流体の照会 |
| `client/AmmoniaSeparatorRenderer.java` | 制御ブロックから1回だけ描く液面。Createの流体描画APIを利用 |
| `ElectrolysisClient.java` | 分離器専用BERの登録 |
| `client/PlantTankModels.java` | 分離器のCreate接続テクスチャを撤去し、ふた付き槽の既存キャップを独立した共有パスへ変更 |
| `tools/generate_ammonia_separator.py` | 4種類の終端モデル、16通りの向き付きvariants、アイテムモデル、16×16の灰色外装生成 |
| `tools/generate_ammonia_separator_ponder.py` | 1×1×3の構造、上段側面・天面・底面の配管、通常ポンプとCreateタンク |
| `ponder/AmmoniaSeparatorPonderScenes.java` | 新寸法・ポート・液面の上昇と下降を示すシーン |
| `tools/generate_manufacturing_ponder.py` / `tools/data/ponder_lessons.json` | 製造ガイドの座標・寸法説明と3言語の正本 |
| `gametest/SeparatorDrumGameTests.java` | 構造、処理、ポート、正面、アイテム配置、液面の4件 |
| `gametest/FusionGameTests.java` | 3×3×6を前提にした既存の冷却ライン試験用設備を1×1×6へ修正 |
| `client/ClientQualityCheck.java` / `docs/quality/separator-drum/client.gradle` | 検証フラグ時のPonder実画面・在庫確認。通常プレイへの動作追加なし |

`PlantTankItem` と `GradeConnectivityMixin` は既存の実装を再利用しています。解決済みCreate 6.0.10の `IMultiBlockEntityContainer`、`FluidTankBlockEntity`、`FluidTankBlock`、`FluidTankItem` のソースを確認しました。実際の登録アイテムでの積み増しも試験しています。

## リソースと依存の整理

旧Createタンク型モデル24個を削除し、single・bottom・middle・topの4モデルへ置き換えました。使わなくなった分離器テクスチャ6枚も削除しました。元の `ammonia_separator.png` と `_top.png` は原子力設備の銀色外装の生成元としてバイト単位で維持しています。

既存のふた付き槽も分離器の `_top_connected.png` を共有していたため、同じピクセルを `tools/data/lidded_vessel_cap_connected.png` へ保存し、`textures/block/lidded_vessel/cap_connected.png` として再出力します。既存キャップの見た目を変えずに参照を切り離しました。完成Jarに旧モデル・廃止テクスチャが残らないこと、処理済み翻訳とカタログが梱包されることを確認済みです。

製造ガイドは構造データを比較し、実際に分離器を使用するアンモニア章の構造を更新しました。採掘用爆薬など今回の寸法変更で内容が変わらない章は維持しています。復水器Ponderは分離器を部品の学習入口として参照するだけで、旧幅の分離器を配置していません。

## 検証結果

- `compileJava` と `clean build`、最終成果物の `build` は成功しました。既存の混合液単体6004項目、原子炉物理1668項目、原子炉制御も成功しています。
- 新規4件＋既存Haber-Bosch4件：8／8成功。
- 仕様指定の関連クラスと有毒ガスを含む8クラス：23件中22件成功。失敗は仕様書に既知として記載された `cooledInputsAndAllManufacturingRecipesRemainValid` の `All five named cooling streams are included` です。当該テストと冷却レシピは変更していません。
- 翻訳ソース4915キー×3言語、148 Ponderヘッダー、構造・字幕・製造導線のバリデーターが成功。
- 全体の `validate_visual_identity.py` は既存の未分類8件で停止しました。対象名とログは `verification.json` および添付ログへ記録しています。分離器以外の分類修正は行っていません。
- RTX 4060の実画面で、日本語の分離器Ponderとアンモニア製造ガイドを確認。高液位28,000／48,000 mB（約58%）、低液位8,000／48,000 mB（約17%）、製造ガイドの液体アンモニア1／48,000 mBを確認しました。表示対象3つの構造・正面・液位をクライアントから記録しています。
- `CLIENT QUALITY PASSED` と結果ファイルの保存、ワールド保存は完了しました。その後の終了時にNVIDIAの `libEGL_nvidia.so.0` でSIGSEGV（134）が発生したため、runClientコマンド自体は成功としません。

画像・再実行手順・JSONは `docs/quality/separator-drum/` にあります。画像はcomputer-use-linux MCPで取得した未加工の実画面です。

## 制約と次回の注意

旧データ移行は仕様どおり追加していません。1段のドラムは処理を開始しません。既存の反応塔、分離レシピの物量・時間、材料、最大6段という釣り合いの調整は範囲外です。

音声、他の描画Modとの共存、大規模な配置の性能、修正した核融合試験クラス全体の実行は未確認です。Ponderの撮影時刻を維持するための処理は `separatorDrumClientCheck` を指定した起動に限定されます。Create更新時は幅・高さの制限、窓の切替、構造変更による正面・Capabilityの更新を再確認してください。

## 成果物

`create-refinery-n-reactors-0.1.0-separator-drum.jar`（6,924,741 bytes）

SHA-256：`5caa404bb224b2f30eb0f64c94f852cbcc84f283c5973d39375f0f74ed0fe467`
