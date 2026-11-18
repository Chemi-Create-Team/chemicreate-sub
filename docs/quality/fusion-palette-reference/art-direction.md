# 核融合炉・閃緑岩ケーシングの配色資料

ユーザーが提示した「Create Palettes」（`file-0-3a6e49da256645cfa11060df3ebf9ca6.png`）を参考にする。元の画像の画素をゲームのテクスチャへ転載せず、4段階の素材色、面取り、暗い内面、部品ごとの差し色をピクセル描画へ落とし込んだ。

`design-study.png` は組込の **imagegen** ツールで作った意匠の参考案であり、ゲーム内スクリーンショットや配布テクスチャではない。ゲーム用の正本は `tools/generate_fusion_assets.py` と `tools/generate_diorite_casing.py`。実際に適用した画像は `src/main/resources/assets/create_refinery_n_reactors/textures/block/fusion/` と同階層の `diorite_casing*.png` にある。

## imagegenへ渡したプロンプト

```text
Use case: stylized-concept. Asset type: pixel-art material study for a Minecraft Create addon, NOT a photograph or a 3D render. Reference image is the supplied Create Palettes guide; match its restrained material ramps and pixel scale. Draw a clean 4-column by 2-row sheet of eight DIFFERENT front-facing square block texture design studies, no text or labels, no perspective. Each tile should look like genuine chunky 16x16 Minecraft pixels enlarged with nearest-neighbor: NO smoothing, gradients, fine noise, gloss, modern photorealism. Outer frame 1 pixel, top/left light bevel, inset shaded center; only 4 main tones per material, at most 20 colors per tile. Arrange: top row reinforced gray andesite and steel support with small brass corner fasteners; toroidal superconducting copper coil in dark teal insulation with silver clamps; copper central solenoid with gray retaining straps; vacuum vessel with raised gray pressure plate, copper gasket and dark recessed central seam. Bottom row olive-gray breeding blanket with copper cooling channel; charcoal tungsten divertor tile with a few steel edges; off-white diorite casing with chunky subtle stone flecks, gray-green andesite alloy frame and tiny brass fasteners; round fluid port with dark bore, steel flange and blue inlet arrow. Balanced Create casing style with actual colored accents, modest contrast, not flat monochrome, not over-detailed. Separate tiles with equal neutral gray gutters. Reference sheet for deterministic in-game pixel texture implementation.
```

## 実クライアントでの確認画像

- [通常の核融合炉](reactor.jpg)
- [部材・ポートとCreate素材](materials.jpg)
- [制御卓と電動発電機](consoles.jpg)
- [閃緑岩の単体・3×3接続壁](diorite.jpg)

すべてNVIDIA RTX 4060で表示した実クライアントをcomputer-use-linux MCPで撮影したもの。制御卓の左右は固定した起動・停止モデルの比較展示であり、実プラントの運転結果ではない。
