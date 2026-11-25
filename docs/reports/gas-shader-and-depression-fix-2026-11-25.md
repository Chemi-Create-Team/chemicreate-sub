# シェーダーパック使用時のガス描画とくぼみの挙動を修正

実装コミットは `e13bdc8ac5d9c14545e5ccc166674efd09bd6ab4`。

## 実装

- [GasCloudRenderer.java](../../src/main/java/net/createplant/client/GasCloudRenderer.java) で、床のもやと蒸気をMinecraft標準の半透明エンティティレイヤーへ切り替えた。Irisの共有エンティティバッファではレイヤー指定の `endBatch` が描画を実行しないため、床のもやはパーティクル描画直後に専用バッファを送出する。
- [GroundGasField.java](../../src/main/java/net/createplant/cloud/GroundGasField.java) と [GasCloud.java](../../src/main/java/net/createplant/cloud/GasCloud.java) で、放出元から受け取った累計量を追跡する。囲まれた場所では同じ高さ全体にガスがたまり、上に空いているマスの数 × 1,000 mBに達すると次の段を予約する。上の段も時間と処理予算に従って展開し、その高さの出口から横に流れる。
- [仕様書](../toxic-gas.md) と有毒ガスのPonderを更新した。旧セーブの放出元は残量を初期値に使う。

## 検証

- Java 21でGradle `check` と専用サーバーの関連GameTest 29件が成功した。少量が底に残ること、追加放出による上昇、幅2×2のくぼみで一角だけあふれないこと、たまったガスが開口部から流れることを確認した。長い通路での拡散・希釈と窒息、ガス配管や放出負荷の既存テストも成功した。
- Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10、NVIDIA RTX 4060で、Iris 1.8.14-beta.1、Sodium 0.8.13、Complementary Reimagined r5.5.1を使った。5×5・深さ4のくぼみへ放出した900 mBの色付きガスは底に見えた。同じ画面で隣の蒸気は上昇した。シェーダーパックを無効に切り替えても、両方を確認できた。
- 同じ試験で放出42 tick後の濃度を計測すると、底は0.861、縁と外側の床は0.000だった。他のシェーダーパックと他のGPUでの描画は未確認。
