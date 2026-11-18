# 高圧・低圧タービン翼テクスチャ分離レポート

## 実装コミット

- 実装コミット：`bbb14c6241ccc39e38c2286f52e42c4e14bbb804`
- 実装日：2026-11-06
- 対象：Minecraft 1.21.1 / NeoForge 21.1.219 / Create 6.0.10 / Java 21

この文書自身を追加するコミットIDはコミット前に記録できないため、上記にはテクスチャ本体のコミットを記載する。

## 実装内容

前回の明るい銀色翼を高圧タービン専用として維持し、添付された実機ローター写真を形状・材質資料として低圧タービン専用テクスチャを描き直した。

| 区分 | 表現 | 寸法 |
| --- | --- | ---: |
| 高圧ブロック翼 | 明るい短翼、狭い反射帯、暖色寄りの翼根。前回アセットを変更せず維持 | 16×16 |
| 低圧ブロック翼 | 暗い長尺鍛造鋼、強いねじれを示す交差反射、銀色前縁、拘束帯、翼根固定溝 | 16×32 |
| 低圧アイテム翼 | ブロック翼と同じ暗い鋼色と鋭い前縁を持つ長翼シルエット | 16×16 |

低圧ブロック翼は既存と同じ16×32で、モデルの外径、40枚の円周翼列、段数、ブロック状態、レシピ、保存形式を変更していない。`SteamTurbineRenderer`の割当も、高圧が`high_pressure_blade`、低圧が`low_pressure_blade`のままである。

高圧ブロック翼のSHA-256は更新前後とも`2be9a3fad5f10979ca02976e3fabb13223bf1f83185b8553731dccbd79937699`で一致した。低圧ブロック翼は`eb2e33d8cef648095b5b4f2807b9a8e176f1531db2574eff80ac4ab631e804f8`となった。

## 画像

- ゲーム内比較：`/workspace/outputs/turbine-pressure-stage-textures-in-game.png`
  - 1270×790、613,434 bytes
  - SHA-256：`42ec45486b1d0f3dd90bff40b7f08701224059a1ed06927c2f25b50373b9de26`
- 低圧翼テクスチャ最近傍拡大：`/workspace/outputs/low-pressure-turbine-blade-texture.png`
  - 256×512、2,177 bytes
  - SHA-256：`fc9815f61002dc269e5a51ef6953d544527aaf93228ed5695dd01a09ff2f94e6`

同一展示軸上で、前方の高圧段が明るい銀色、最大径の低圧段が暗い鋼色として区別できることを確認した。低圧側の反射帯は翼のねじれに沿って段階的に移動し、密な翼列でも単一色の円盤には見えない。

## 検証結果

| 検証 | 結果 |
| --- | --- |
| 生成器再実行前後のSHA-256比較 | 高圧・低圧のブロック翼とアイテム翼4点がすべて一致 |
| PNG寸法検査 | 高圧16×16、低圧16×32を確認 |
| `python3 tools/validate_visual_identity.py` | 成功 |
| `python3 tools/test_ci_validators.py` | 11テストすべて成功 |
| `python3 -m py_compile tools/generate_rotating_models.py` | 成功 |
| `./gradlew --no-daemon build`（Java 21） | 成功。FluidPhase 25件、HaberBoschProcess 1,019件、ReactorPhysics 1,670件、原子炉制御試験を含む |
| `git diff --check` | 成功 |
| `mc-debug glxinfo -B` | NVIDIA GeForce RTX 4060、直接描画、OpenGL 4.6 |
| RTX 4060実クライアント | 両テクスチャの同時描画、HUDなしPNG保存、全ディメンション保存まで成功 |

今回のテクスチャに関する欠落・読込・描画例外はない。MojangサービスのDNS、`libflite`、OpenAL、外部依存Modの欠落音・互換モデル警告は既存の開発環境由来である。正常なワールド保存と`Minecraft: Stopping!`の後、開発用X11画面の終了時にNVIDIA EGLドライバ内でSIGSEGVが再現したが、テクスチャの読み込み・描画中には発生していない。
