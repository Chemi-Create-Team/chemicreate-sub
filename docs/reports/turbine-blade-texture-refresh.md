# 蒸気タービン翼テクスチャ更新レポート

## 実装コミット

- 実装コミット：`a69295aef6e99a8549ee0d95761c8bc7000e3641`
- 実装日：2026-11-06
- 対象：Minecraft 1.21.1 / NeoForge 21.1.219 / Create 6.0.10 / Java 21

この文書自身を追加するコミットIDは、コミット前の文書へ自己参照として記録できないため、上記にはテクスチャ本体のコミットを記載する。

## 実装内容

添付された実機蒸気タービン写真を形状資料として使い、既存のラジエーター上部ファンと同じ放射状の読みやすさを維持しながら、次の4アセットを更新した。

| アセット | 表現 |
| --- | --- |
| 高圧段ブロック翼 | 短く細い鍛造翼。暗い根元、明るい前縁、翼幅を横切る銀色の反射帯 |
| 低圧段ブロック翼 | 長く幅広い扇状翼。実機写真のねじれを、斜めに移動する反射帯として16×32へ縮約 |
| 高圧段アイコン | 従来の十字型ではなく、細い単翼のシルエット |
| 低圧段アイコン | 従来の十字型ではなく、幅広く湾曲した単翼のシルエット |

写真そのものを縮小貼付せず、Createの低彩度金属、硬いピクセル境界、16 px密度へ描き直した。ブロック側は既存の回転メッシュと組み合わさり、高圧段から低圧段へ翼高が増す実機ローターとして読める。生成は`tools/generate_rotating_models.py`へ集約し、再実行しても同じPNGになる。

## 変更ファイル

- `tools/generate_rotating_models.py`
- `src/main/resources/assets/create_heavy_indusstry/textures/block/turbine/high_pressure_blade.png`
- `src/main/resources/assets/create_heavy_indusstry/textures/block/turbine/low_pressure_blade.png`
- `src/main/resources/assets/create_heavy_indusstry/textures/item/high_pressure_turbine_blade.png`
- `src/main/resources/assets/create_heavy_indusstry/textures/item/low_pressure_turbine_blade.png`

## 画像生成による設計補助

組み込みの`imagegen`を`stylized-concept`用途で使い、添付写真と既存プロジェクトのテクスチャ一覧から、翼幅と反射帯の方向を検討する4象限コンセプトを作成した。生成画像を直接ゲームへ入れず、最終アセットは既存の決定的ピクセル生成器で再作図した。

最終プロンプト：

```text
Use case: stylized-concept
Asset type: Minecraft Create-mod pixel-art turbine blade texture reference sheet
Primary request: Design a square four-quadrant sprite reference for industrial steam-turbine blades, based on the real turbine photo and matching the supplied existing Create-style pixel textures.
Input images: Image 1: engineering shape reference for radial twisted steam-turbine blades; Image 2: current project palette and pixel-density reference.
Subject: four unlabeled equal quadrants: high-pressure blade material, low-pressure blade material, high-pressure inventory blade icon, low-pressure inventory blade icon.
Style/medium: deliberately low-resolution Minecraft pixel art; hard pixel clusters; no antialiasing or smooth gradients.
Color palette: restrained steel with charcoal edges, medium cool gray, muted warm-gray roots, and limited silver highlights.
Materials/textures: forged steel; hard-edged reflection bands; darker blade root; thin bright leading edge; high-pressure blade compact and narrow; low-pressure blade broad, long, fan-like, and twisted like the photo.
Constraints: no text, logos, watermark, photo background, or soft shadow; clear silhouettes at 16x16; avoid white clipping; use the photo only as engineering reference.
```

- コンセプト：`/workspace/outputs/turbine-blade-texture-concept.png`
- ゲーム内写真：`/workspace/outputs/turbine-blades-in-game.png`

## 検証結果

| 検証 | 結果 |
| --- | --- |
| 生成器を2回実行した前後のSHA-256比較 | 4テクスチャすべて一致 |
| `python3 tools/validate_visual_identity.py` | 成功 |
| `python3 tools/test_ci_validators.py` | 11テストすべて成功 |
| `./gradlew --no-daemon build`（Java 21） | 成功。FluidPhase 25件、HaberBoschProcess 1,019件、ReactorPhysics 1,670件、原子炉制御テストを含む |
| `git diff --check` | 成功 |
| `mc-debug glxinfo -B` | NVIDIA GeForce RTX 4060、直接描画、OpenGL 4.6を確認 |
| `mc-debug ... runClient` | 実際の回転メッシュ上で高圧・低圧翼を確認し、HUDなしのゲーム内写真を保存 |

クライアントログにあるMojang DNS、`libflite`、OpenAL、外部Modの欠落音・互換モデル警告は実行環境または既存依存Mod由来であり、今回更新したタービンテクスチャの読み込み失敗は出ていない。終了時はGUIの「Save and Quit to Title」で全ディメンションの保存完了と`Minecraft: Stopping!`を確認した。その後、X11開発ラッパーがopcode 150のX Errorで終了コード1を返したが、撮影・ワールド保存・アセット検証はその前に完了している。

## 中断原因

Discordから作業中に新しいメッセージが届くと、実行中ターンとその配下の長時間ツールセッションが終了する。このため、画像生成中とMinecraft撮影中に`turn_aborted`が発生した。テクスチャ生成、ビルド、Minecraft描画のクラッシュではない。再実行時はJava 21を`/workspace/.createplant-jdk21`へ固定して、一時領域消失の影響を除いた。
