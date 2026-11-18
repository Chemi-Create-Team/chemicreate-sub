# クァーリークレーン専用外観

- 実装コミット：`89e0364c`
- 対象ブランチ：`work`
- Minecraft 1.21.1 / NeoForge 21.1.219 / Create 6.0.10

## 実装内容

添付された赤い天井クレーンを方向性の基準にし、原子炉保守用クレーンとクァーリー用クレーンの外観を分離した。Imagegenで参考画像と既存クレーンを比較する方向性シートを作成したが、それをテクスチャとして直接使用せず、最終アセットは生成スクリプトで再現できる16×16のMinecraft用ピクセルアートとして実装した。

- クァーリー用に、赤錆色の斜材入り橋桁、暗色鋼の小型トロリー／ホイスト、黒鉄のチェーン、専用グラップル、赤錆色の駆動部を追加した。
- 原子炉保守用の黄土色橋桁、淡色の固定長マスト、既存駆動部は変更していない。
- Createのメカニカルドリル、ガントリーシャフト、共通支持脚はそのまま使う。参考画像の識別要素は移動する上部構造へ絞り、Createアドオンとして装飾を増やしすぎない構成にした。
- `CraneRenderer`はモードに応じて橋桁、端台車、トロリー、チェーン／マスト、吊り具を選ぶ。採掘ヘッドには引き続きCreateの実モデルを使う。
- 駆動部のブロックステートに`quarry`を追加し、ブロックエンティティの採掘モードとサーバー側で同期する。既存ワールドの状態は`false`で読み、次のサーバーtickで保存済みモードへ同期する。
- Ponderでも採掘への切替時にブロックステートを切り替え、静的な駆動部モデルを含めた専用外観を確認できるようにした。
- `tools/generate_crane_assets.py`から専用モデル6点とテクスチャ9点を生成する。日英のPonder説明と`docs/gantry-crane.md`もチェーン／専用配色へ更新した。

## テストと確認

- JDK 21、JEIなしのオフライン依存構成で`clean build`成功。
  - FluidPhase：25件成功
  - HaberBoschProcess：1,019件成功
  - ReactorPhysics：43件成功
- `CraneGameTests`と`FuelPoolGameTests`を分離実行し、関連GameTest 14件すべて成功。採掘動作に加えて、採掘モード時に駆動部が`quarry=true`になることも検証した。
- 現行仕様の最小5×5炉心に合わせてクレーン試験設備を更新し、拡張した炉心と燃料プールが重ならないようプールを移動した。燃料移送の回帰試験も成功した。
- GameTestは`All 14 required tests passed`まで到達した後、開発用サーバーがワールド保存で停止しなかったため手動終了した。全アサーションの成功は確認済みだが、その実行ではプロセスの通常終了までは確認していない。
- `mc-debug glxinfo -B`でNVIDIA RTX 4060のハードウェア描画を確認した。
- 実クライアントで日本語Ponder全74シーンの検査が`CLIENT QUALITY PASSED`。原子炉保守シーンと採掘シーンを撮影し、採掘側だけが赤錆色の橋桁・暗色チェーン・専用トロリー／駆動部になることを目視確認した。
- クァーリー用モデル／テクスチャの欠落警告なし。PNG 9点はすべて16×16 RGBA。
- 生成対象42ファイルを2回連続で生成し、全SHA-256が一致した。

## 主なファイル

- `src/main/java/net/createplant/client/CraneRenderer.java`
- `src/main/java/net/createplant/block/CraneDriveBlock.java`
- `src/main/java/net/createplant/block/entity/CraneDriveBlockEntity.java`
- `src/main/resources/assets/create_heavy_indusstry/models/block/crane/quarry_*.json`
- `src/main/resources/assets/create_heavy_indusstry/textures/block/crane/quarry_*.png`
- `tools/generate_crane_assets.py`
