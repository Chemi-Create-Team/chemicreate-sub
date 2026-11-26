# 産業用アームの実装評価（2026-11-26）

評価対象は `9cac54b43c8c252f666b792e7b967912073a2a13`。Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10。

**単独ラインの組立と搬送は実用段階に入っている。複数ラインを近接させる運用や、作業中の再読み込みには物資の扱いに課題が残る。**

## 実装できていること

- アーム4台で鉄ブロック3個の枠組み、銅の配線、両端の溶接を3段階で進め、磁気コアメモリをファンとホッパー経由でチェストに回収できた。材料補給後の連続生産と、無動力時に材料を取らないことも確認できた。[テストワールドと操作手順](../playtests/magnetic-core-memory-line.md)
- 一つの工程を複数のアームが奪い合わず、同じ段階の別工程は並行して進む。組立ジグでは消費済みの配線工程をアームとジグの保存データから再開できた。デポと、実際に流れている8 RPMのCreateベルト上でも金床を溶接加工できた。
- 工具交換、動力、アイテムと液体の搬送はCreateの操作に沿っている。レシピはJSONで追加できる。外観はCreateの色・爪・関節を引き継ぎ、腕だけを延長して3×3のジグに届くようにしている。Ponderの既存画像では、ジグの継ぎ目とデポ上の金床に工具が向くことを確認した。[組立ジグの画像](../quality/industrial-arm/weld-jig.png)・[デポの画像](../quality/industrial-arm/weld-depot.png)

## 再現した不具合

| 優先度 | 症状と再現条件 |
| --- | --- |
| 高 | 磁気コアメモリの配線中に鉄枠3個をすべて撤去しても、完成品が1個出荷された。配置済み工程の状態だけで完成が決まり、枠の現物はそこで再確認されない。完成判定時に同じ種類のブロックがあれば削除する処理も、この状態を参照する。[組立ジグの完成処理](../../src/main/java/net/createplant/block/entity/AssemblyJigBlockEntity.java#L219) |
| 高 | 爪の届かないチェストから鉄ブロック3個を取り、組立用チェストの鉄ブロック1個は最初の枠が完成した時点で残っていた。別チェストをグリッパーから `(-5, -1, -5)` の位置に置くと再現した。周囲の収納を順に走査し、アイテムを抜いてから爪が届くか確認する処理が原因。近接する複数ラインで在庫を分けて運用しづらい。[材料取得処理](../../src/main/java/net/createplant/block/entity/IndustrialArmBlockEntity.java#L331) |
| 高 | 白のコンクリートパウダーへのデポ噴霧中にアームとデポを保存・再読み込みすると、消費済みの水50 mBは戻らず、90 tick後も白のコンクリートパウダーのままだった。ベルト・デポ用の加工は作業開始時に液体を差し引く一方、仕掛中の工程は保存しない。[材料の引き落とし](../../src/main/java/net/createplant/block/entity/IndustrialArmBlockEntity.java#L239)・[保存処理](../../src/main/java/net/createplant/block/entity/IndustrialArmBlockEntity.java#L467) |

## コードレビューで見えた課題

- **停止理由が分かりにくい。** ゴーグルで工具や工程は分かるが、材料不足、液体不足、アームの到達範囲外がいずれも「作業待ち」になる。配置や補給を修正するには原因の表示が必要。[作業の探索](../../src/main/java/net/createplant/block/entity/IndustrialArmBlockEntity.java#L195)・[ゴーグル表示](../../src/main/java/net/createplant/block/entity/IndustrialArmBlockEntity.java#L430)
- **多数設置時の負荷は未計測。** 稼働可能で待機中のアームは、通常10 tickごとに最大1,331座標をジグの候補として調べる。ジグで手が空いた場合は、追加でベルト・デポも探す。稼働中の進捗は最短2 tick間隔で同期する。台数を増やす場合に計測したい経路。[アームの探索](../../src/main/java/net/createplant/block/entity/IndustrialArmBlockEntity.java#L195)・[ジグの同期](../../src/main/java/net/createplant/block/entity/AssemblyJigBlockEntity.java#L197)
- **ガイドの実装欄に古い説明が残っている。** 関節角は現在 `ArmPose.angles` で直接計算しているが、ガイド末尾にはCreateの非公開フィールドをリフレクションで読むとある。[ガイド](../industrial-arm.md#実装)・[関節計算](../../src/main/java/net/createplant/arm/ArmPose.java)

## 検証範囲

Gradle `check` が成功。専用サーバーでは、既存のアーム・磁気コアメモリ・サバイバル到達性のGameTest 11件が通った。実際のブロックを置いた一時的な検証4件では、上の不具合3件を再現し、動くベルト上での金床加工1件を確認した。再読み込みはブロックエンティティの保存データを使った再生成で検証した。最新版クライアントでの描画の再テストと、多数設置時のTPS・描画負荷の計測は未実施。今回コミットする変更は評価報告書のみ。
