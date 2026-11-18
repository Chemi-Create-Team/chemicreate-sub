# クァーリークレーンの走行キャリッジ

- 実装コミット：`385771c1`
- 対象ブランチ：`work`
- Minecraft 1.21.1 / NeoForge 21.1.219 / Create 6.0.10

## 実装内容

クァーリー用橋桁の端台車とCreateのガントリーシャフトの間に見えていた空間を、走行キャリッジで埋めた。Create 6.0.10のガントリーキャリッジを形状の基準にし、専用ブロックや別の描画処理は増やさず、既存の`quarry_end_truck`部分モデルを下へ延長した。

- シャフト上面まで届く赤錆色の鞍金具を追加した。
- シャフトの左右へ、前後2輪ずつ計4輪の暗色ピニオンハウジングを配置した。
- 車輪中心は抑えた真鍮色、外装はクァーリー橋桁と同じ赤錆色・暗色鋼とし、原子炉用端台車は変更していない。
- 端台車と一体の部分モデルなので、橋桁の移動・向きに追従し、常に両方の走行レール上へ描画される。
- `tools/generate_crane_assets.py`から16×16 RGBAの`quarry_carriage.png`と更新済み端台車モデルを再生成できる。

## 主なファイル

- `tools/generate_crane_assets.py`
- `src/main/resources/assets/create_heavy_indusstry/models/block/crane/quarry_end_truck.json`
- `src/main/resources/assets/create_heavy_indusstry/textures/block/crane/quarry_carriage.png`
- `docs/gantry-crane.md`

## 確認結果

- JDK 21、JEIなしのオフライン依存構成で`clean build`成功。
  - FluidPhase：25件成功
  - HaberBoschProcess：1,019件成功
  - ReactorPhysics：43件成功
- モデル8要素の座標がMinecraftの許容範囲`-16..32`内であることと、JSON・テクスチャ参照を静的確認した。
- 生成対象43ファイルを2回連続で生成し、全SHA-256が一致した。追加PNGは16×16 RGBA。
- NVIDIA RTX 4060のハードウェア描画で実クライアントを起動し、日本語Ponder全74シーンが`CLIENT QUALITY PASSED`。採掘用クレーンの拡大画面で、鞍金具がシャフト上面へ接し、4輪ハウジングがシャフトを左右から挟むことを目視確認した。
- クァーリー用モデル／テクスチャの欠落警告なし。
- 品質検査とワールド保存の完了後、クライアント終了時にNVIDIAの`libEGL_nvidia.so`でSIGSEGVが発生し、`runClient`自体は終了コード134になった。画面検査の合格と保存はクラッシュ前に完了しており、今回のモデル読込エラーではない。

## 未変更・未検証

- 原子炉保守用端台車には今回のキャリッジ外観を追加していない。
- 実プレイ中に長時間往復させる目視試験は行っていない。移動計算・当たり判定・保存処理には変更がない。
