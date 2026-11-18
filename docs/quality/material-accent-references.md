# TFMG・Create Decoの材質と差し色の参照

この調査を適用した外観変更は、後続のユーザー指定により取り消しました。現在は[元の外観](block-surfaces.md)を保持しています。以下は調査時の記録です。

2026-11-08。鋼材調の更新後に設備が灰色へ寄り過ぎたため、ユーザー指定の2 MODとCreate本体の原画像、現行モデルの材質割当を調べました。以下の適用方針は、このプロジェクトの判断です。

## 調べた一次資料

| 参照 | 確認した版とコミット | 主な画像・割当 |
| --- | --- | --- |
| [Create: The Factory Must Grow](https://github.com/DrMango14/Create-The_Factory_Must_Grow/tree/e77ec11a406ce1bc9e5dbf87b7135bf638df6997) | Minecraft 1.21.1 / TFMG 1.2.3 / `e77ec11a406ce1bc9e5dbf87b7135bf638df6997` | `steel_casing`、`heavy_machinery_casing`、`heavy_gearbox`、`steel_fluid_tank`、`brass_pump`、`steel_pump`、`steel_gauge`、`copper_spool` |
| [Create Deco](https://github.com/talrey/CreateDeco/tree/996780bed4549d7b1d0dec65f06d5129e69330ba) | Minecraft 1.21.1 / Create Deco 2.1.2 / `996780bed4549d7b1d0dec65f06d5129e69330ba` | `palettes/plate_metal`、`palettes/hull`、`palettes/support`、`palettes/sheet_metal`のindustrial iron・brass・copper |
| Create本体 | 実際の依存Create 6.0.10 / Modrinth `UjX6dr61` | `industrial_iron_block`、`andesite_casing`、`brass_casing`、`copper_casing` |

TFMG・Create Deco計38枚の出典URL、コミット、SHA-256、ライセンス原文、モデル参照を作業時の参照資料に保存しました。[原画像の出典一覧](material-accent-provenance.json)にもURLとハッシュを記録しています。TFMGの現行steel gearboxは`heavy_gearbox`と`heavy_machinery_casing`を使います。旧`steel_gearbox.png`を現行機器の根拠にはしていません。

## 観察した構成と適用

- TFMGの鋼材ケーシングは、冷たい灰色の枠と大きな茶色の内面を組み合わせています。真鍮ポンプは取付部全体が暖色で、計器には広い象牙色の盤面があります。鋼の骨格、内張り、軸受、計器を別の材質として描く構成を参考にします。
- Create Decoは同じ板・支持材・外板の形を、鉄の灰色、真鍮の金色、銅の橙色で明確に描き分けています。材質の違いを明度と色相の両方で示します。
- Create本体の銅ケーシングも、幅のある銅色の枠と灰色の内面を持ちます。差し色をボルト数点へ限定せず、部材として連続する領域へ配分します。
- このMODでは圧力板や軸を鋼材色に保ち、保持帯・軸受を真鍮、熱交換配管・巻線を銅、制御・保守カバーを青や緑の塗装とします。クレーンは黄土色の塗装部材と鋼の走行面、真鍮の駆動部を分けます。既存の危険縞、表示灯、ポート記号は維持します。
- 2px単位の粒、左上の明縁と右下の影、外枠と凹面、概ね24色以下の配分を維持し、完成設備を通常のゲーム内距離で確認します。粒や色数の検査だけで見た目の完成とは判断しません。

## 素材の扱い

TFMGの[LICENCE.md](https://github.com/DrMango14/Create-The_Factory_Must_Grow/blob/e77ec11a406ce1bc9e5dbf87b7135bf638df6997/LICENCE.md)はコードとアセットを分け、アセットをAll Rights Reservedとしています。Create Decoの[ルートLICENSE](https://github.com/talrey/CreateDeco/blob/996780bed4549d7b1d0dec65f06d5129e69330ba/LICENSE)はCC0-1.0です。両方とも外観構成の参考として調べ、このMODには原画像や切り出した画素を収録していません。生成元から描いた独自のテクスチャを使います。
