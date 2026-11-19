# プロセスガスの検知・回収と洗浄

実装コミットは `093465db34d9cf39eca56a5aca552e98f84e0733`。検証環境は Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10、JDK 21。

## 実装

- `ProcessGasIncidents` は、架空の汚染をディメンション別のSavedDataに保存し、チャンク内の発生地点で管理する。1チャンクあたり最大16地点、各地点はゲーム内で最大1,000 mB相当。発生地点に白煙を表示し、20 tickごとに5 mB相当を自然希釈する。濃度は残量を0〜100に換算する。プレイヤーへの効果は白煙と警報。
- `FluidRoutes` と `PipeGasFlow` は、直前の10 tickで高圧蒸気の流れが確認された管の位置が空気になったとき、250 mB相当を記録する。`FlareTipBlockEntity` は、蒸気を実際に排出した分を排気口上の空気ブロックへ記録する。
- `GasDetectorBlock` は、10 tickごとに半径4ブロック内の最大濃度を0〜15へ換算する。既存ガス雲の信号と比較して最大値を出し、信号が変わったときにコンパレーターを更新する。警報が検知側から止まったときに供給を再開したい場合は、電動ポンプの標準レッドストーン入力に接続できる。
- `GasRecoveryDuctBlockEntity` は、真上の下向きケース入りファンが32 RPM以上で回ると、10 tickにつき最大250 mBを回収する。格子面のすぐ外側から半径4ブロック内で、空気の通り道がある最寄りの地点から回収する。4,000 mBまで貯蔵し、丸いノズル側の流体ハンドラーからだけ搬出できる。設置中のNBTと破壊時のドロップに残量を保存する。
- 大型吸収塔は最下段の未処理プロセスガス250 mBと最上段の水250 mBを使い、100 tickで中和済み廃水250 mBを下段に出す。既存の `ProcessRecipe` とJEIの吸収塔カテゴリーを再利用する。回収した気体はCreateのアイテム注液口でバケツに移せる。
- 新しいダクトとバケツのモデル、日英中の翻訳、監視・回収のPonderを同梱した。`tools/generate_process_gas_recovery.py` でモデル、レシピ、構造NBTを再生成できる。設置手順は [プロセスガスの回収](../toxic-gas.md#プロセスガスの回収) に記載した。

## 検証

- 関連GameTestは33件すべて合格。新規4件で、警報ランプと供給ポンプの連動、壁で遮られる回収、Createのパイプとポンプを通した吸収塔への供給、廃水の出力、ダクトのNBTとドロップ、高圧蒸気管の破損、フレアからの排気を確認した。既存のサバイバル到達性とクリエイティブ用バケツの監査も合格した。
- 全体は627件中619件合格。変更前の `5d7c784820a76516514078a7a6e3fc867026dc1c` では623件中615件合格で、失敗は下記の同じ8件だった。
- Gradleビルド、JARの展開、翻訳とPonderの静的検証が完了。NVIDIA GeForce RTX 4060で起動したクライアントで、回収ダクトと大型吸収塔の日本語Ponder 2シーンを確認した。
- 専用サーバーのワールドに汚染のSavedDataファイルが書き込まれた。ワールドを再起動してからの濃度と、設備を大量設置した場合の負荷は未検証。

| 変更前から失敗するGameTest | 確認対象 |
| --- | --- |
| `afouledprocessstopsthenresumesafteronemanualbrush` | 汚れた装置の清掃後の再始動 |
| `anativemechanicalpumpkeepsincompatiblegasatthesource` | 受入不可の気体を供給側に保持 |
| `anativepumpreachesthesecondfeedandconservesgas` | 既存の槽の第2原料への気体供給 |
| `apumpfeedscompressedairfromatankintothecoldbox` | コールドボックスへの圧縮空気供給 |
| `apumplineontwocasingfaceslosesnosteam` | タービンの配管を2面につないだときの蒸気収支 |
| `everycurrentitemhasexactlyoneappropriatetab` | 既存バケツのクリエイティブタブ |
| `everyregisteredentryhasassetstranslationsandloot` | 既存スラリーブロックの翻訳 |
| `nonoccludingequipmentsurvivesflowingwater` | ガス遠心分離機の水流耐性 |
