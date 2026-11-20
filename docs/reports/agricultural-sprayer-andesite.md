# 農薬散布機の安山岩ケーシング風外装

実装コミットは `1f6519b29820673e41e5fa8a47f075a5d6de5293`。

筐体の側面と天面に木板をはめ込み、灰色の面取り枠で囲んだ。枠の明暗と木の色をCreate本体の安山岩ケーシングに寄せ、正面は真鍮ノズルで識別できるようにした。生成元は [農薬散布機の資産生成](../../tools/generate_agricultural_sprayer_assets.py)。同じ16×16テクスチャ内に側面用と天面用の模様を配置し、[ブロックモデル](../../src/main/resources/assets/create_refinery_n_reactors/models/block/agricultural_sprayer.json) のUVで使い分けた。

Gradleビルドに成功。モデルの12要素の座標と16×16の画像寸法を比較した。Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10のクライアントで、安山岩ケーシングと並べた設置状態と回転アームのPonderを確認した。

配布用JARは 8,380,889 バイト。8 MiB制限に合わせた既存の配布版と同じ開発用GameTestクラスを省いた。保持した全ファイルが通常ビルドとバイト単位で一致することを検証した。

SHA-256 `9ebaa79a5a24e2b5e5a175db2b95c90f11a66348d8840dbe8940f4e2c6893e55`
