# 円形沈殿池の寸法拡張と浸出工程の統合（2026-11-17）

実装コミット：`8a03562d86f719a36947870faef105ff519893e1`。リモートの電解槽の外観変更（`38ccc4f2`まで）を取り込んだmain上で実装した。レポートは実装の後続コミットで保存する。

## 実装内容

- 沈殿池の槽壁・床は通常の立方体で円形に配置する。面取りのBlockState、衝突形状、角モデルを撤去。
- 直径は7〜30を1ブロック刻みで指定でき、偶数にも対応。高さは3・4を独立して指定する。
- 施設建設機に直径の＋／−操作を追加。サーバーで範囲と操作可能状態を検証し、選択・高さ・建設計画を保存する。生成概略図のキーにも高さを含める。
- 専用浸出槽を削除し、鉱石4種と廃水の計5レシピを既存のふた付き槽の撹拌反応へ移動。鉱石は加熱必須、廃水は加熱不要。鉄・銅・亜鉛・錫が対象で、金は対象外。
- JEIは既存の撹拌反応カテゴリを使用。Ponderはふた付き槽→直径14・高さ4の円形沈殿池の実際の接続・処理例へ更新。関連する登録、翻訳、旧レシピ型・部品・アセットを削除。

## 主要ファイルと計算

`block/WetProcessStructure.java` が構造判定、施設建設機、Ponder照合の正本。直径Dでローカルx・zを `−floor((D−1)/2)..floor(D/2)` とし、奇数ではoffset=0、偶数ではoffset=1とする。

```text
(2x−offset)² + (2z−offset)² ≤ D²
```

この範囲に床を敷き、上下左右のいずれかが範囲外になるセルを外周壁にする。内部は空気。偶数の幾何中心はブロック境界なので、駆動部は中央4セルの一つになる。高さは床から駆動部までの物理ブロック数。

`block/entity/WetProcessBlockEntity.java` は入口の床位置と越流堰で寸法候補を絞って形成を判定する。変更位置の索引、チャンクのロード確認、世代付きCapabilityを継続使用する。形成後の毎tick全探索や強制チャンクロードは追加していない。高さを保存・同期し、描画の深さにも使用する。

`assembly/AssemblyPlan.java`、`FacilityConstructorBlockEntity.java`、`inventory/FacilityConstructorMenu.java`、`client/FacilityConstructorScreen.java` が寸法操作・概略図・半透明表示を接続する。既存の概略図砲による印刷・資材供給は維持。

`registry/CEWetProcessing.java` と `WetProcessPartBlock.java` には沈殿池の現役部品のみ残す。既存の沈殿池の流体バンク・アイテムスロットの保存位置は維持し、在庫を切り詰めない。

`tools/generate_wet_processing.py` が関連アセット、5件の撹拌反応、固液分離レシピ、翻訳、Ponder構造を生成する。字幕の正本は `tools/data/ponder_lessons.json`。

## 回収量

ふた付き槽で砕いた鉱石3個＋希硫酸750 mB＋圧搾空気または酸素250 mB→スラリー4000 mB。沈殿池の4バッチで精製液3000 mB＋尾鉱泥8個。電解採取でインゴット9個＋希硫酸750 mBを回収する。従来の3倍回収と酸の循環を維持。

## 検証

- `clean build runGameTestServer` 成功。関連5クラス、42件すべて成功（5.391秒）。[実行要約](../quality/circular-clarifier/test-summary.txt)。
- 192組（24直径×2高さ×4方向）の寸法、立方体形状、建設計画の一致を検証。
- 実ワールドで直径7・8・29・30×高さ3・4の形成、直径14の4方向、内部障害物・誤ったポート、旧ハンドラー停止、解体・BlockItemによる復元、在庫保存を検証。
- ふた付き槽の標準Capabilityから鉱石・酸・気体を投入し、4金属について9インゴットと再生酸750 mBまでの有限バッチ収支を確認。既存撹拌反応と施設建設機の回帰テストも成功。
- 翻訳4998キー、Ponder149タイトル、追加94シーン・27製造系統の検証が成功。
- NVIDIA RTX 4060／OpenGL 4.6でPonderの構造・モデル・状態表示の解決を確認。施設建設機では41段階の実際のボタン操作で全直径、高さ4、切替後の設定保持を確認。[実行方法・画像・JSON](../quality/circular-clarifier/README.md)。
- Jar内に5件の新しい撹拌レシピがあり、削除した浸出クラス・駆動部・角モデルがないことを確認。

## 制約と次回の注意

- 大型化で処理速度や流体バンク上限を変更していない。容量は従来どおり `min(64000,(直径−2)²×(高さ−2)×4000)` mB。大規模台数の性能比較は未実施。
- 削除した専用浸出槽の7部品は旧登録や互換エイリアスを残さない。既存ワールドのこれらのブロックは失われる。新しい槽で組み直す必要がある。
- 既存の施設建設機はサイズをインデックス保存しているため、直径候補の追加後に沈殿池の選択寸法を再確認する。既存の沈殿池本体は幅と在庫を保持し、未保存の高さは3になる。
- 検証完了後のクライアント終了で、既知のNVIDIA EGLドライバー `libEGL_nvidia.so.0` のSIGSEGVが発生しJavaが134終了。実行中の検証成功と通常終了の成功を区別する。音声デバイスがないため聴取は未確認。
- `validate_visual_identity.py` は今回対象外の未分類8項目で失敗：high_pressure_turbine_unit、iron_pipe、irradiated_cartridge、irradiation_cartridge、jacketed_pipe、low_pressure_turbine_unit、pig_iron、steel_slag。今回変更したモデルの欠落は実クライアントでは検出されなかった。
- Create Electro Energeticsの `duplex_wire_segment` モデルと `altitude_sensor_*` テクスチャ欠落などの既存依存Mod警告は変更していない。
- 古い `docs/quality/wet-processing/` の結果と旧完了報告は当時の記録。今回の確認結果は `circular-clarifier/` に保存した。

## 統合後の追加確認

公開直前の電解槽の動力・外観変更も取り込み、統合ソース `49acd60498f7496afb0ce48cb1376110bf2fd61d` で再ビルドした。電解槽のテストも加えた関連6クラス・57件がすべて成功。翻訳とPonderの検証も再度成功。下記Jarはこの統合版。

## 配布物

`Create-Plant-circular-clarifier.jar`：6994229 bytes。

SHA-256：`36976732a4229fcc75bdad358dff39244c640064c10109883ee52b4297ade858`。
