# 既存外装の復元と閃緑岩ケーシング

2026-11-09の最新のユーザー指定「白っぽくしたの全部戻して、閃緑岩ケーシングは残して」を現在の外装方針とします。既存設備の全体白化は撤回し、新しい閃緑岩ケーシングを維持します。[DESIGN.md](../../DESIGN.md#casing-grammar)にも同じ方針を記載しています。

- 既存ブロックの画像とモデルの素材参照は、白化前の`c26c2198`を基準に復元します。
- 閃緑岩ケーシングは白い石面・金属枠・差し色を保ち、登録、レシピ、接続テクスチャ、Ponderと既存の製作レシピへの代用を維持します。
- モデルの形状・UV、接続箇所、操作、機械の機能を維持します。

新ケーシングの画像の正本は[tools/generate_diorite_casing.py](../../tools/generate_diorite_casing.py)です。Pillowを使えるPython環境で実行します。出力は`diorite_casing.png`と`diorite_casing_connected.png`の2点だけで、Git履歴や旧マニフェストには依存しません。

```sh
python3 tools/generate_diorite_casing.py
python3 tools/generate_diorite_casing.py --check
```

全体白化の生成器とマニフェストは撤去済みです。[白化時の画像・手順](white-factory/README.md)と[白化時の報告](../reports/white-factory-theme.md)は撤回前の履歴で、現在の既存設備の外観を示すものではありません。新ケーシングの操作とPonderは[使い方](../diorite-casing.md)を参照してください。

復元結果と現在の実画面は[白化撤回・ケーシング保持の報告](../reports/restore-white-theme-keep-diorite.md)に記録しています。

以前の安山岩合金調・鋼材調・TFMG/Create Decoを参考にした改修、および[2026-11-08の全テクスチャ復元](../reports/full-texture-restore.md)も、その時点の作業記録として残します。
