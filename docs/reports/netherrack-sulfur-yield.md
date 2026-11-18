# ネザーラック破砕からの硫黄粉回収

実装コミット `8010c5a7`。サバイバル試験で、CreateとCreate-Plantにネザーラックを入力とする破砕レシピが1件ずつあり、破砕ホイールではシンダー粉のレシピが選ばれていた。[修正前のサバイバル試験](survival-playtest.md)

Create 6.0.10の `create:crushing/netherrack` に、硫黄粉1個の独立した30%抽選を追加した。シンダー粉は1個確定で、50%でもう1個得られる。処理時間の設定値は250で、実際の所要tickは回転数と投入数に応じて変わる。硫黄粉専用の重複レシピは廃止し、Ponderも同じレシピを参照するようにした。[破砕レシピ](../../src/main/resources/data/create/recipe/crushing/netherrack.json)

Ex Nihilo: Sequentiaがある場合は、Createの互換レシピに同じ30%の硫黄粉を追加する。この経路の主生成物は砕いたネザーラック。両方のJSONをCreate 6.0.10と比較し、硫黄粉の追加枠とレシピIDごとの有効条件を確認した。Ex Nihilo: Sequentiaを導入した実機運転は未確認。[互換レシピ](../../src/main/resources/data/create/recipe/crushing/compat/exnihilosequentia/netherrack.json)

| 8 RPMの破砕ホイール | ネザーラック | シンダー粉 | 硫黄粉 | 所要tick |
| --- | ---: | ---: | ---: | ---: |
| 1回目 | 8 | 12 | 5 | 1,136 |
| 2回目 | 8 | 13 | 3 | 1,128 |
| 3回目 | 8 | 13 | 1 | 1,112 |

水車で回した破砕ホイールからデポ、ホッパー、チェストを通して、シンダー粉38個と硫黄粉9個を回収した。既存のサバイバル確認もステンレス鋼板30枚まで到達した。対象GameTest 6件とGradleビルドが成功し、日本語クライアントのJEIで両方の生成物を確認した。この作業の実機確認は硫黄粉の回収まで。[JEIの画面](../quality/survival-playtest/jei-netherrack-combined.png)
