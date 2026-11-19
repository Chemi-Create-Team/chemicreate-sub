# Quarryの既存逆向きベルト修復

記録日: 2026-11-19 JST

対象: `Chemi-Create-Team/chemicreate-sub`

実装コミット: `dbf10f59c8f3660394641124d3e5bbf1c82c0152`

基点: `3309a05f61df5367b127d4eb91d98c28abb63c47`

## 原因と変更

既存の水平ベルトが搬出方向と逆を向くと、完成判定に合わず `belt_blocked` で停止していた。実際の在庫コントローラーも想定した終端と反対側にあり、搬出待ちの判定から漏れることがあった。

`QuarryBeltLayout` で両端、長さ、水平状態、向き、各ベルトのコントローラーとインデックスを照合する。搬出路の一つの区間に完全に一致する逆向きベルトだけを、その場で反転する。範囲外へ延びるベルトや別の所属を持つベルトは変更しない。

在庫はCreateのNBT保存処理で挿入待ちを確定してから移す。長さを `n` とすると、現在位置と前回位置は `n - position`、挿入区間は `n - 1 - index` に変換し、処理順も反転する。アイテムの成分、数量、処理状態、横方向の位置は維持する。旧コントローラーの在庫を空にし、各部品の公開ハンドラーと回転接続を更新する。

`QuarryDriveBlockEntity.buildBelt` は既存ベルトを障害物として扱う前に修復を試す。部材消費、採掘セルや層の巻き戻しは行わない。搬出待ちの判定は区間内の実際のコントローラーを確認し、逆向き区間の搬送品も数える。

関連ファイル:

- `src/main/java/net/createplant/block/QuarryBeltLayout.java`
- `src/main/java/net/createplant/block/entity/QuarryDriveBlockEntity.java`
- `src/main/java/net/createplant/gametest/QuarryBeltRepairGameTests.java`
- `src/main/resources/data/create_refinery_n_reactors/structure/test_quarry_belt_repair.nbt`
- `src/main/java/net/createplant/ponder/CranePonderScenes.java`、3言語の字幕、`tools/data/ponder_lessons.json`
- `docs/quarry.md`

従来の描画上限、搬送バックログ、同期方法は変更していない。

## 確認

Java 21.0.12、Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10を使用。Createの実際の解決先はModrinth `UjX6dr61`、ソースコミット `ac0c444d9828da3453ae8cc65338e8de063286fb`。ベルト、在庫、アイテム位置、回転接続のAPIをこの版のソースと実JARで照合した。

`build` と専用サーバー上の `QuarryBeltRepairGameTests,QuarryGameTests,QuarrySpeedGameTests`、計21件が成功した。ローカルのGradle initで `createplant.gameTestClasses` にこの3クラスを指定した。通常の `runGameTestServer` でも新規3件は登録対象になる。

- 長さ64、既定ベルト上限20で末端が5ブロックになる構成を再現。
- 原鉄23個と挿入待ちの原金17個の数量、名前、処理状態、Createが計算するワールド位置を修復前後と再読み込み後に照合。
- プーリー、ケーシング、各BlockEntity、隣の正しいベルトを保持。再修復は発生しない。
- いずれかの端から外へ延びる別ベルトを採用せず、内容物も保持。
- `Cell=1000`、`belt_blocked` の保存状態を256 RPMで再開し、通常のtickで末端を修復。全ベルトと駆動部の保存・再読み込み後、元の次セルを実際に採掘。
- 既存の名前付きアメジスト31個が修復・再読み込みを経て全数残り、回転する標準ベルト上を西へ移動。
- 隣接設備の別ベルトとエメラルド9個を保持。
- 既存の鉱石回収24/24、保存・復元576/576、大量搬送186928個の回帰テストも成功。

試験用構造の領域は80×64×80。既存の20ブロック幅の試験領域を越える設備による干渉を避けるため、専用テンプレートを追加した。

`validate_plant_tutorials.py` と `validate_ponder_language.py` は成功。157シーンの字幕と3言語の導線を確認した。Windowsでは `python -X utf8` を使用する。

## 既存の失敗と未確認事項

`validate_visual_identity.py` は既存の `models/item/sulfur_scrubber_liquor_bucket.json` が `neutralized_wastewater_bucket` の画像を参照するため43行目で失敗する。検査スクリプトと該当モデルは基点mainと同一。`test_ci_validators.py` もこの失敗により15件中9件が失敗した。基点mainの[Actions実行](https://github.com/Chemi-Create-Team/chemicreate-sub/actions/runs/44532136018)もビルド後の外観検査で失敗している。今回のQuarry修正の範囲外として残した。

実サーバーのワールドへの導入、再起動、その座標での復旧は未実施。クライアント画面でのPonder表示は未確認。既存描画最適化の性能を再計測した作業ではない。

## 次回の注意

Create更新時は在庫NBT、位置の基準、コントローラーの所属、ブロック更新による再初期化を再確認する。ベルトコネクターで既存ベルトを作り直すと物資を失うため、修復には使用しない。物資保存の回帰テストを維持する。
