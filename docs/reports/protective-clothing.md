# 原子炉作業服 完了レポート

実装コミットは、このレポートを含むコミット。

## 実装内容

RBMK運転員の写真（白い帽子、白衣とネクタイ、胸の線量計バッジ、濃色のズボン、青いシューズカバー）を参考に、防具4部位を追加した。

| ID | 名称 | 線量の軽減 | 防御力 | レシピ |
| --- | --- | --- | --- | --- |
| `protective_cap` | 原子炉作業帽 | 15% | 1 | 白い羊毛5、プラスチックシート1 |
| `protective_coat` | 原子炉作業用白衣 | 40% | 2 | 白い羊毛6、青の染料1、プラスチックシート1 |
| `protective_trousers` | 原子炉作業ズボン | 25% | 1 | 白い羊毛4、青い羊毛2、プラスチックシート1 |
| `protective_shoe_covers` | 原子炉作業用シューズカバー | 10% | 1 | プラスチックシート2、空色の染料2 |

耐久値は革防具と同じ、修理素材は白い羊毛。ツールチップに軽減率を表示する。

## 計算方法

`Radiation.expose` が、受ける線量に `1 - 着用部位の軽減率の合計` を掛ける。全部位で線量は10%になり、耐久値は放射線では減らない。

全部位を着ても無敵にはならない。3.2 GW・漏れ100%・距離5 mでは0.33 Sv/sが0.033 Sv/sになり、致死量8 Svまで約24秒だったものが約240秒になる。実機の白衣にガンマ線を止める力はなく、軽減率はゲーム用の値である。

## 主要ファイル

- `item/ProtectiveClothingItem.java`、`registry/CENuclear.java`、`nuclear/Radiation.java`、`registry/CECreativeModeTabs.java`
- `tools/generate_protective_clothing_assets.py`：着用テクスチャ、アイコン、モデル、レシピ、防具タグを生成する
- `data/minecraft/tags/item/{head,chest,leg,foot}_armor.json`：防具エンチャントの対象にする
- 白衣の裾は胴の防具では脚に描けないため、ズボン側のテクスチャに描いている
- `client/ProtectiveCapModel.java`：帽子を布の薄さで描く専用モデル。バニラの防具モデルは帽子の層が頭から1.5ピクセル離れるが、これを0.6ピクセルにして、帽子の層だけに描く。スキンの帽子層（0.5ピクセル）を貫通させないため、これより頭に近づけていない

## テスト結果

- `gradlew build`：成功（FluidPhase 25、HaberBosch 1,019、ReactorPhysics 27）。
- GameTest `protectiveClothingReducesDose`：全GameTestの実行で失敗に含まれず（裸100%、白衣のみ60%、全部位10%を確認）。
- 全GameTest：2件失敗（`actualcreatepumptransfersbetweensmallcompressors`、`actualacidflowwarnslosespressureandbreaksonlythepipewhenenabled`）。変更を外した `ead26dd` でも同じ2件が失敗するため、今回の変更が原因ではない。
- 実クライアント：全部位と一部の着用時の前後の外観、ホットバーのアイコン、レシピ4件の読み込み、防具タグ、ツールチップのキーと値を確認。一時的な確認コードで撮影し、コミットしていない。[正面](../quality/protective-clothing/front.png)、[背面](../quality/protective-clothing/back.png)
- `tools/validate_visual_identity.py`：バケツ数の検査（13個を想定）で停止した。今回はバケツを追加していない。防具アイコンまでは検査が進んでいない。

## 未検証・制約

- 実際の炉のそばで着用して線量が減る経路は、実クライアントでは未確認。`irradiate` から呼ばれる `expose` をGameTestで確認した。
- 作業台での実際のクラフト、JEIでの表示、マルチプレイヤーは未確認。
- 燃料プールなど炉以外の放射線源は未実装で、軽減の対象は炉の放射線のみ。
- Ponderは追加していない。

## 次回の注意

テクスチャを変更するときは生成スクリプトを編集して再生成する。軽減率を変更するときは、GameTestと本レポート、`docs/rbmk-reactor.md` も更新する。
