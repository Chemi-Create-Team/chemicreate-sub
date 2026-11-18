# 外装テクスチャ方針の文書化 完了レポート

- 実装コミットID：`4260700a6c3c50f541aa1ac04f3b59ce52ba069b`
- 対象：`DESIGN.md`の「基本的なデザインルール」に「外装面の描き方」を追加

## 実装内容

- Create 6.0.10の`andesite_casing`・`brass_casing`・`copper_casing`の画素の明るさを1行ずつ調べ、外枠1px、左上が明るい面取り1px、中央12×12の暗い内面という共通の構成を確認した。
- この構成を、外枠・面取り・内面・色数・真鍮と銅の明部の基準として`DESIGN.md#casing-grammar`に記載した。
- 共通外装（鋼・鋳鉄・真鍮・銅）は、既存の監査（`docs/quality/texture-audit-after.json`）どおり意図的に共有する素材とした。そのうえで、同じ画像を別名のファイルとしてコピーして増やすことは禁止した。
- 生成スクリプトでテクスチャを作る場合は、スクリプト側でこの構成を実装することを明記した。

テクスチャ、モデル、生成スクリプト、コードは変更していない。

## 根拠にした観察

主なマルチブロックのテクスチャを、Createの外装と8倍に拡大して並べ、色数・平均彩度・明暗差を比べた。

| 対象 | 観察 |
| --- | --- |
| Createの外装 | 1面あたり15〜20色。真鍮の明色は面取りだけ |
| `gas_compressor/steel`、`plant/steel_panel`など | 62色前後で、1px単位のノイズが入る。白に近い |
| `tank/ammonia_separator`、`tank_casing` | 137色、284色 |
| `reactor/brass`、`chemical/brass` | 平均彩度0.64〜0.69の明るい金色で、面全体が塗られている |
| `turbine/*`、`fractionating_column/*` | 外枠がなく、縦じまだけの構成 |
| `furnace/insulator`、`furnace/pipe` | 白から灰色への横グラデーション |

## 方針に合っていない既存テクスチャ（今回は未修正）

- 共通外装として共有されているが、別名のファイルとしてコピーされているもの。
  - 鋳鉄：`chemical/cast_iron`、`plant/electrolyzer_frame`、`plant/power_terminal`、`plant/steam_joints`、`pressure/frame`、`relief_scrubber/frame`、`steam/frame`
  - 鋼：`chemical/stainless`、`plant/steam_jacket`、`plant/steel_panel`、`pressure/steel`、`relief_scrubber/steel`、`steam/steel`
  - 真鍮：`chemical/brass`、`pressure/bronze`、`relief_scrubber/brass`。`bronze`という名前だが中身は真鍮と同じ。
- 外枠・面取りがない、または色数が多すぎるもの：上の「根拠にした観察」の表の各テクスチャ。
- 比較的方針に近いもの：`fusion/console_*`、`absorption_tower/flange`、`absorption_tower/pipe`、`turbine/glass`。

## 確認結果

- 文書のみの変更のため、ビルドとゲームの起動は行っていない。
- 追加したアンカー`#casing-grammar`と、参照先`docs/quality/texture-audit-after.json`が存在することを確認した。
- `git diff --check`：成功。

## 次回の注意

- 共通外装4種を、まず生成スクリプトで描き直すと、それを共有する圧力系・化学系・蒸気系・スクラバーの見た目がまとめて揃う。コピーされたファイルを1つに集約する場合は、参照しているモデルJSONも同じ作業で書き換える。
- 描き直した後は、実際のクライアントでCreateの外装と並べて確認する。`tools/validate_visual_identity.py`で、意図しない同一外観がないことも確認する。
- 色数24色以下という基準は、観察にもとづく目安。Create公式の規格として扱わない。
