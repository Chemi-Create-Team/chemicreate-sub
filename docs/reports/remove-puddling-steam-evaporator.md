# パドル炉・蒸気蒸発器・アスファルト等の削除と往復式の描画変更

## 削除したもの
- パドル炉：炉床・作業扉・パドル棒、構造テンプレート、Ponder、ガイド、テスト、生成ツール。パドル鉄とそのレシピ（ベイシン＋ミキサー、メカニカルプレス）は残した。
- 蒸気蒸発・気液分離（`steam_separator`、`steam_drum_segment`）：`Kind.EVAPORATOR`とその分岐、Ponder（steam_generation/recovery/expansion/loop/ports/heat_budget/faults/monitoring）、蒸発器を使うゲームテスト、`SteamClientCheck`。過熱器・復水器・燃焼/回収/冷却モジュール・煙道・圧力ヘッドは残した。
- アスファルト・油頁岩：ブロック、レシピ、鉱石生成、Ponder（asphalt_roads）、翻訳。
- 工業用加熱器の5ブロック（本体・チャンバー・コイル・煙道・屋根）：クリエイティブタブから外しただけで、登録・レシピ・データは残している。

## 変更
- 気体パイプ（`gas_pipe`）の見た目は蒸気設備煙道（`steam/steel`・`steam/frame`）のテクスチャ。
- 往復式コンプレッサーの描画（`ReciprocatingCompressorRenderer`）を、Create Diesel Generators大型ディーゼルエンジンのピストン・連桿・クランクのモデルと動きにした。ピストンは(1−sinθ)/4＋0.4375で半ブロック上下し、連桿は−cosθに沿って最大23°傾く。クランクは−θ＋90°で回る。

## 確認と未検証
- ゲームテスト244件成功。実クライアントのPonderで、往復式エンジンの描画（2つの時点）を確認した。
- 未確認：実ワールドでの動き、4方向・天井向きの配置、フライホイールなど周辺ブロックとの見た目の合い。アイテム/インベントリのモデルは従来のCreate蒸気エンジン基準のまま。
- `docs/reports`の旧い記録（steam-stage3など）は、削除前の仕様のまま。
