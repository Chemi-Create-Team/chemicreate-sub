# 磁気コアメモリ組立ラインのデバッグ

実装コミットは `b62e788adcf8a946bca541954dd2ac54c2555568`。[配布ワールドと操作](../playtests/magnetic-core-memory-line.md)

## ライン

磁気コアメモリのレシピを選んだジグに、グリッパー、精密配線ヘッド、左右の溶接トーチを備えた産業用アーム4台を接続した。鉄ブロック3個を枠にして、中央の配線で銅インゴット1個を使い、左右の継ぎ目を溶接する。40 tick後に出た完成品をエンケースドファン、ホッパー、回収チェストで搬出する。アームとファンは64 RPM。

初回の配置ではジグとホッパーの間でアイテムが下へ落ちたため、間に床を追加した。風で押されるアイテムはガラスに当たってホッパーに入り、ガラスの下の回収チェストは開けられる。

## 修正

- [IndustrialArmBlockEntity.java](../../src/main/java/net/createplant/block/entity/IndustrialArmBlockEntity.java) は、実際に回転を受けているときに新しい工程を予約して材料を取る。途中で止まった工程は、回転が戻ると保持済みの材料で続く。
- アームは保持品と工程・進捗を、[AssemblyJigBlockEntity.java](../../src/main/java/net/createplant/block/entity/AssemblyJigBlockEntity.java) は工程の予約者を保存する。再読込後は同じ予約者の作業を再開し、消えたアームの予約は対応するチャンクが読み込まれてから解消する。旧形式で工程情報がない保持品はアームの位置に返す。

## 検証

- Java 21、Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10でGradle `check` と関連GameTest 11件が成功した。[MagneticMemoryLineGameTests.java](../../src/main/java/net/createplant/gametest/MagneticMemoryLineGameTests.java)
- 本物のアームと搬送ブロックを使い、鉄ブロック6個と銅インゴット2個から完成品2個を回収した。2回目の枠で銅が尽きて65 tick待機し、補給後に再開した。無動力時は材料を取り出さず、配線途中でアームとジグを保存データから読み込み直した場合も、追加の銅なしで完成した。
- NVIDIA RTX 4060でクライアントを起動し、日本語の表示と配線アームが関節を動かすことを確認した。配布用セーブの保存後に同じラインに2回分の材料を入れ、投入271 tick後に1個目、481 tick後に2個目を回収した。
- ZIPの整合性と初期状態を確認した。4台の工具と回転、未着手のジグ、空の投入・回収チェスト、3回分の試験材料、ゴーグルが保存されている。長時間の連続稼働とシェーダーパック環境は未確認。
