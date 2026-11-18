# アイテム保管庫ハッチ 実装引き継ぎ

2026-11-15。実装コミット：`da83d1f3`（`Add an item vault hatch with paged chest access and Ponder`）。リモート`32024d3b`までの核融合炉・抽出塔・冷却塔の変更を保持して統合。

## 実装

`create_refinery_n_reactors:vault_hatch`を追加。Createのアイテム保管庫の6面へ配置し、右クリックで54スロットのチェスト形式の画面を開く。矢印で全ページへ移動し、通常クリック・Shiftクリックで実際の在庫を操作する。カーソルに物を持っている間はページを変えない。未使用の末尾スロットへは入れられない。

在庫の所有・保存はCreateの保管庫のまま。ハッチ自身には在庫を設けず、保管庫の`IItemHandlerModifiable`をページ単位で公開する。Createの結合・分割等でハンドラーの同一性が変わった場合、全構成チャンクがロードされていない場合、距離8ブロックを超えた場合は操作を拒否し、Minecraft標準のメニュー有効性確認で閉じる。チャンクの強制ロードは行わない。

ページ数は`ceil(総スロット数 / 54)`、実在庫のスロットは`ページ番号 × 54 + 表示スロット`。表示中の54スロットとプレイヤー在庫だけを標準メニュー同期で扱う。Shiftクリックでスタックが直接変更される経路でも`Slot.setChanged()`から保管庫の更新・保存へ通知する。

ふたは閲覧中だけ開く。Minecraftの`ContainerOpenersCounter`で複数閲覧者、5tick再確認、開閉音を扱い、保存時の開状態はロード後に再確認する。閉じている間は継続tick処理を持たない。レンチでスニーク右クリックすると取り外せる。

ユーザー指定のチェスト形式の閲覧を、このブロックに限ったGUI方針の例外としてDESIGN.mdに記録。標準チェスト画像・スロット処理を再利用する。通常の設備GUIへ例外を広げない。

## 主要ファイル

- `block/VaultHatchBlock.java`：6方向の配置・形状、支持判定、操作、レンチ。
- `block/entity/VaultHatchBlockEntity.java`：メニューを開く処理、閲覧者とふたの状態。
- `inventory/VaultHatchMenu.java`：共有在庫、ページ、出し入れ、接続先の有効性判定。
- `client/VaultHatchScreen.java`：標準チェスト画像、ページ矢印。
- `registry/CEVault.java`：ブロック・アイテム・BlockEntity・Menuの登録。
- `ponder/VaultHatchPonderScenes.java`：取付、閲覧、ページ、搬送、取り外し。保管庫のコントローラー情報もシーンへ設定。
- `tools/generate_vault_hatch.py`：16×16のテクスチャ、閉・開モデル、レシピ、ドロップ、Ponder構造。共通の鋼材テクスチャを蝶番・取っ手に使用。
- `tools/data/ponder_lessons.json`と3言語のlang：日本語・英語・中国語。
- `gametest/VaultHatchGameTests.java`：サーバー上の共有在庫、ページ、保存、構造変更、支持、複数閲覧者。
- `client/VaultHatchClientCheck.java`：任意起動の実画面確認用ワールド。通常プレイでは無効。

Javaのパスは`src/main/java/net/createplant/`からの相対パス。

## 検証

- Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10、Java 21。解決済みCreate JARの逆コンパイルソースとNeoForge/Minecraftソースで使用APIを確認。
- `bash gradlew build`：成功。最新main取り込み後も再ビルド成功。
- `bash gradlew -I tools/compact_fuel_checks.gradle -Dcreateplant.testClasses=VaultHatchGameTests runGameTestServer`：3件成功。ページ2、付加情報の保持、Shift挿入・取り出し、無効ページと末尾スロット、外部在庫変更、Create保存データの再読込、古い接続先への操作拒否、ハッチ撤去時の在庫保持、配置支持、複数閲覧者を確認。
- `validate_plant_tutorials.py`、`validate_ponder_language.py`、`validate_translations.py`：成功。最新main統合後は4638翻訳キー。
- `mc-debug glxinfo -B`：NVIDIA GeForce RTX 4060、direct rendering有効。
- `mc-debug bash gradlew --no-daemon ... runClient`：日本語の実画面でハッチから開く経路、54スロット、ページ切替、モデル、Ponderを確認。ワークスペースの`outputs/`に画面と配布JARを保存。
- `validate_visual_identity.py`：既存の粉末・素材分類検証で失敗。変更前の`0a497ff6`を別ディレクトリへ展開して同じ9項目で失敗することを確認。対象は`heated_unfinished_steel`、`high_pressure_turbine_unit`、`iron_pipe`、`irradiated_cartridge`、`irradiation_cartridge`、`jacketed_pipe`、`low_pressure_turbine_unit`、`pig_iron`、`steel_slag`。ハッチの追加による失敗ではなく、対象外の分類は変更していない。

## 制約と次回の注意

- チャンク境界での実際のアンロード・再ロード、独立した複数クライアント間の通信遅延、JEIなし、Deep Seasや移動コントラプションとの組合せは未検証。サーバーテストではJEIありの構成を使用。
- 新しい保存インベントリや既存IDの変更はない。Create更新時は保管庫の結合ハンドラーと無効化、`SlotItemHandler`の変更通知を再確認する。
- ふたの開閉は標準トラップドアのような2状態のモデル切替。ハッチ越しに保管庫の外装を切り欠く描画は追加していない。
- 生成スクリプトのモデルと実際の形状をセットで維持する。Ponder文言の変更はlesson原稿と各言語の実行時キーを揃える。
