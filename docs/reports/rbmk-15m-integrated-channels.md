# RBMK 15 m炉体・統合チャネル実装レポート

## 実装コミット

- `5241cc1` `Rebuild RBMK core and handling systems`
- `origin/work`の防護服・蒸気タービン更新へrebaseし、防護服の登録、翻訳、線量軽減、GameTestを保持した。

## 実装内容

- 最大炉体を有効炉心直径13 m、周囲1 mのバイオロジカルシールドを含む外径15 mへ変更した。
- 1個の燃料チャネルブロックを4×4＝16区画へ分け、最大円形炉心を1,872本の細い圧力チャネルとして扱う。既定配置は燃料1,661本、制御棒211本である。
- 運転床は52×52相当の円形格子として描画する。燃料位置は灰色の口金付き蓋、制御棒位置は低彩度の赤・緑・青・黄の識別蓋、無効な円周外位置は非表示にした。外周1 mは明るい分割鋼板として区別した。
- 独立した制御棒チャネル、ウィンチ、制御棒コントラプションを廃止した。上部キャップの4×4区画をレンチで燃料／制御用に切り替え、同じ区画へ燃料棒または制御棒を装荷する。制御棒駆動指令もキャップへ統合した。
- 制御棒は吸収部7.0 m、接続部1.25 m、黒鉛ディスプレーサ4.5 mを連続した12.75 mの形状として計算・描画する。
- 炉心7 m、上部遮蔽体3 m、実寸15.5 mを切り上げた上部配管・サスペンション空間16 mとし、燃料集合体を26 mへ変更した。
- 門型クレーンの燃料取扱いマストを下部の太い部分5 m、上部の細い部分7 m、合計12 mとし、運転床と燃料プールに必要な高さへ上げた。4×4の正確な区画へ1本ずつ移動する。
- 燃料プールを深さ27 m、壁高さ28 m、ラック1枚16本、容量28,000 mB、受入水量27,000 mBへ変更した。
- 現行仕様書、Ponder、日英翻訳、レシピ、タグ、GameTestテンプレートと生成スクリプトを更新し、廃止部品の登録・モデル・テクスチャ・レシピ・ルートを削除した。

## 主なファイル

- `src/main/java/net/createplant/nuclear/ReactorCoreStructure.java`：13 m円形炉心、細分円マスク、1,872／211／1,661本の配置
- `src/main/java/net/createplant/nuclear/ReactorPhysics.java`：7＋3＋16 m、12.75 m制御棒、細分チャネル物理定数
- `src/main/java/net/createplant/block/entity/FuelChannelBlockEntity.java`：16区画の種別、装荷、冷却水、保存、構造変更
- `src/main/java/net/createplant/block/ReactorCapBlock.java`、`block/entity/RodCapBlockEntity.java`：4×4ワールド内操作と統合駆動
- `src/main/java/net/createplant/client/RodCapRenderer.java`：密集蓋、4色識別、上面スカイライト
- `src/main/java/net/createplant/client/FuelChannelRenderer.java`：26 m燃料集合体と連続制御棒
- `src/main/java/net/createplant/block/entity/CraneDriveBlockEntity.java`、`client/CraneClientCheck.java`：4×4区画保守と12 mマスト
- `src/main/java/net/createplant/nuclear/FuelPoolStructure.java`、`block/entity/FuelPoolBlockEntity.java`：27 mプール
- `tools/generate_rbmk_assets.py`、`generate_crane_assets.py`、`generate_fuel_pool_assets.py`：16×16基準のアセットとPonder生成元
- `docs/rbmk-reactor.md`、`docs/gantry-crane.md`、`docs/fuel-pool.md`：現行仕様

## 寸法・本数の計算

- 細分格子幅：13ブロック×4区画＝52区画。
- 円形境界を四回対称に選び、有効位置を1,872本にした。
- 制御棒は細分座標の3区画ごとの格子と中心付近2本で211本。燃料は1,872−211＝1,661本。
- 炉体外径：13 m＋左右の遮蔽1 mずつ＝15 m。
- 燃料集合体：炉心7 m＋遮蔽プラグ3 m＋サスペンション16 m＝26 m。
- 制御棒：7.0＋1.25＋4.5＝12.75 m。挿入行程は炉心と同じ7 m。
- クレーンマスト：太部5 m＋細部7 m＝12 m。
- プール：26 m集合体＋上部の水1 m＝27 m、床を含む壁は28 m。

## 検証結果

- `./gradlew --no-daemon build`：成功。FluidPhase 25件、Haber–Bosch 1,019件、ReactorPhysics 31件が成功した。
- 全リソースJSONを`jq empty`で検証：成功。
- `git diff --check`：成功。
- `mc-debug glxinfo -B`：NVIDIA GeForce RTX 4060、direct rendering有効。
- `-Dcreateplant.rbmkPlaytest=true`付きの実クライアント検証：15 m炉体を新規ワールドへ構築し、外径15 m、有効径13 m、炉心7 m、遮蔽3 m、上部空間16 m、集合体26 m、燃料1,661本、制御棒211本、装荷済み制御棒211本、全挿入率1.0をJSONで確認した。
- 同検証の斜視・側面・真上スクリーンショットで、円形の密集蓋、4色の制御棒識別蓋、明るい外周リング、欠損モデルが無いことを目視確認した。
- リモート更新との競合解決後に再度`build`を実行し、防護服との同時コンパイルと全上記ロジックテスト成功を確認した。

## 未検証・制約

- 全230件のNeoForge GameTestは完走していない。途中実行では今回追加した原子炉試験に失敗は見られなかったが、変更対象外の小型コンプレッサー／リリーフバルブ試験が失敗し、全バッチ完了前に停止した。
- 実クライアント検証はワールド構築、検査、撮影、保存まで成功した後、VirtualGL終了時に`libEGL_nvidia.so.0`でSIGSEGVとなり、Gradleの`runClient`終了コードだけは134になった。生成JSON・画像とゲームログに今回のモデル欠損や検証例外は無い。
- 最大炉体1基の描画は確認したが、複数炉の定常FPS、MSPT、メモリ割当量は計測していない。1,872本は個別ブロックにせず、137本の物理チャネル束と16区画データにまとめている。
- Ponder NBTは再生成・ロード時のモデル解決まで確認したが、全シーンを手動で最後まで再生していない。

## 次回の注意

- `tools/generate_rbmk_assets.py`を再実行する時は、既存の日英翻訳を読み込んで防護服キーを保持する。旧ウィンチ翻訳は生成時に削除される。
- `RodCapRenderer`の部分モデルはモデルベイク前に`RodCapRenderer.init()`で登録し、照明は固体キャップ内ではなく上面1ブロック上から取得する。
- 本数を変更する場合は、円形マスク、既定制御棒マスク、物理定格、GameTest、Ponder、クレーン巡回を同時に更新する。
