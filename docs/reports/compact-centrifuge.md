# 1×1ガス遠心分離機・旧圧縮機撤去の完了レポート

- 日付：2026-11-10
- 実装コミット：`68c78eefd9b1dad6f1f083302eb56d81ac66bd7d`
- 最新mainとの統合コミット：`7f9acb70753b76cc87be9544e58f19a5a99aca3d`（`45737be8`の吸収棚段統合を取り込み）
- 作業開始時にmainを取得し、`9c656a7d`まで同期した。
- 対象：Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10、Java 21。

## 実装

遠心ロータ柱は**1×1のふた付き槽専用**とした。最小1段、最大4段で、各段に1本ずつ入れる。四角い槽の中に、既存の金属素材を使う円筒ローター、上下の軸受と支持部を収めた。旧3×3の4本ローター構成は構造判定・装荷経路・描画・モデル・Ponderから撤去した。

側面をCreateのレンチで右クリックすると点検窓になり、同期された実際の入力速度に従って円筒が回る。装荷済みの隣接する1×1列は横に結合しない。回転は最下段の底面1か所だけから入力し、32 RPM以上で処理する。応力は24 SU/RPM×段数で、処理速度の既存上限4倍を維持した。

| 接続面 | 動作 |
| --- | --- |
| 側面 | 原料を搬入。別の搬出配管から第2製品以降の劣化物・副製品を回収。原料を吸い戻さず、主製品も搬出しない |
| 最上段の上面 | 濃縮物などの第1製品だけを搬出。原料を受け入れない |
| 下面 | 回転軸専用。形成済み遠心分離機の流体ハンドラーは非公開 |

Create標準の流体ハンドラー、パイプ、ポンプを使用する。既存のガス配管網にも同じ面別規則を適用した。構造・内部部品の変更や再読込では以前のハンドラーを失効させ、隠れた貯蔵在庫への搬入を防ぐ。

1×1槽の上面は、貯蔵状態を含めて**ハッチのない固定ふた**に変更した。開閉操作を行わず、アイテムモデルの取っ手も取り除いた。2×2・3×3の槽の中央ハッチと、触媒・撹拌槽は維持する。

通常ウラン濃縮、小型炉用追加濃縮、重水濃縮、ヘリウム回収の4レシピの原料・製品・時間を維持した。ゲーム内の通常濃縮は1,000 mB→主製品125 mB＋劣化物875 mB。液体を扱う重水レシピも、上面から主製品、側面から副製品を搬出できる。

小型コンプレッサー、大型コンプレッサー、統合圧縮機構の登録、実装、モデル、レシピ、翻訳、タグ、専用Ponderを削除した。利用先がなくなった専用の空／潤滑油／モリブデングリースカートリッジも関連製造工程・画像・Ponderごと撤去した。共通素材、潤滑油そのもの、往復式コンプレッサーとシリンダー、ほかの化学設備は残している。共有の境界圧力照会は`PressureSections`へ移し、他設備の既存計算を維持した。

## Ponder・表示

専用の`compact_centrifuge`を新設し、遠心ロータ柱から最初に開けるようにした。槽、ローター装荷、点検窓、底面動力、側面原料、上面濃縮物、側面劣化物、観測、停止、4段の拡張を説明する。日英中の字幕と見出しを用意した。JEIの構造表示も新しいシーンを参照する。

既存の内部部品展示、重水濃縮、ヘリウム回収、アンモニア冷凍の説明も更新した。重水・ヘリウムの実際のPonder構造には1×1列と分離した製品配管を置き、冷凍サイクルでは撤去した圧縮機を現役の往復式へ置き換えた。生成器からも旧寸法・旧装置が戻らないよう修正した。

[実クライアント画像と再実行用設定](../quality/compact-centrifuge/README.md)

## 検証結果

`build`成功。同梱の物理・反応・制御の検証タスクも完了した。単独変更の先行検証では、下表の183件の合格を確認した。最新mainとの統合後は、吸収棚段の硝酸試験3件を加えた**186件を一括実行し、すべて合格**した。リポジトリの全GameTestの一括実行結果を意味しない。

| 範囲 | 件数・結果 |
| --- | --- |
| 槽・反応・ガイド・気体配管・保存・往復式圧縮・有毒ガス・硝酸プラントの回帰 | 93件。初回92件合格、硝酸統合1件は下記の試験設備変更後に合格 |
| 製造レシピ・サバイバル到達性 | 11件合格 |
| 新しい1×1遠心分離機 | 9件合格 |
| 圧力・凍結・熱制御・タービン・既存製品・精製水 | 70件合格 |

専用品撤去後にも、遠心分離機・製造・到達性・既存製品の24件を再実行し、すべて合格した。これらは上の183件に含め、重複加算していない。

新しい9件で確認した内容：

- 1～4段の形成、3×3での装荷拒否と運転無効化、旧データのローター回収。
- 最上面だけの主製品搬出、側面の原料／劣化物、下面の流体接続拒否、ガス配管網の同じ面別規則。
- 隣接する装荷済み1×1列の独立、単独・積み上げ・貯蔵状態での固定ふた。
- 実際に接続した底面クリエイティブモーターで32 RPM、1段24 SU/RPM。停止後は0 RPMで在庫を保持。
- Create標準ポンプと配管による850 tickの搬送：**側面原料2,000 mB→上面250 mB＋側面1,750 mB、総量2,000 mB**。
- NBT保存・再読込で二重生成なし。70 tick進んだ途中のバッチも1回分だけ完了する。
- 主製品満杯で原料を消費しない。重水の主製品に10 mBだけ空きを作ると、水1,000 mB→重水10 mB＋副製品990 mBを1回だけ実行する。

槽の底面駆動は実際のCreate回転網を使った。搬送試験のポンプには試験コードから128 RPMを与えており、サバイバル動力源を含む工場全体の応力設計まで確認したものではない。

硝酸統合試験は、往復式圧縮機2段の実際の吸入・吐出・仕事計算を使用する構成へ変更した。この既存装置の設定上限は10 barで、旧大型機の40 bar設定は使えない。空気を全量ため込む旧試験を改め、20万mBの有限圧力予備を保ちながら順次分留した。空気842,000 mBを処理し、コールドボックスは開始6.5447／終了6.2489 bar、最終硝酸200 mBとNO循環100 mBを得た。機械の定数を試験のために引き上げていない。

実クライアントではNVIDIA RTX 4060のハードウェア描画を確認し、専用シーンと重水・ヘリウムの計3シーンを日本語で描画した。専用シーンでは実際のクライアントBlockEntityを照合し、幅1、点検窓あり、ハッチなし、稼働32 RPM／停止0 RPMを確認した。

追加の検証：

- 最終統合後のPonder検証：83シーン、418組の字幕、136コンポーネント参照と構造内の旧ブロック除去を確認。ローターを保存している構造について1×1・固定ふたも検査。
- モデル・材質識別の検証：合格。内部モデルの再生成一致、固定ふたのアイテムモデルと生成器の一致、Python構文検査も確認。
- 変更したCI識別性テスト1件：合格。
- `git diff --check`：合格。

## 最新mainとの統合

プッシュ前の取得で、別作業の`45737be8`（独立した吸収塔を3×3ふた付き槽の吸収棚段へ統合）が追加されていた。これをマージし、吸収棚段は3×3・高さ2～4段・回転不要、遠心ローターは1×1・高さ1～4段・底面駆動として両方を維持した。`ReactorModule`、硝酸プラントの試験設備、ガイド、翻訳、モデル生成器の競合を解消した。新しくアイテムになった吸収棚段を、粉ではない成形部品として識別性検査へ登録した。

統合後に22クラス・186件の関連GameTestを一括実行し、ビルドも成功した。さらに専用遠心分離機と吸収棚段の2シーンを実クライアントで再描画し、日本語字幕・見出し、遠心ローターの稼働／停止状態を確認した。先行する重水・ヘリウムの確認と合わせて4種類のシーンを描画した。

独立吸収塔の廃止はこのmain更新によるもの。今回の遠心分離機の変更で元に戻したり、上書きしたりしていない。

## 主要ファイル

- `block/entity/LiddedVesselBlockEntity.java`、`block/LiddedVesselBlock.java`：装荷幅、動力、固定ふた、流体接続とハンドラー失効。
- `block/ReactorStructure.java`、`chemistry/ReactorModule.java`、`guide/ReactorGuide.java`：1～4段の判定と組立ガイド。
- `client/LiddedVesselRenderer.java`、内部モデル、`tools/generate_lidded_vessel.py`：1本の円筒と軸受、実速度による描画。
- `transport/GasNetworks.java`：面別ガス搬送。
- `ponder/CompactCentrifugePonderScenes.java`、`tools/generate_compact_centrifuge_ponder.py`：専用の説明と構造・翻訳。
- `gametest/CompactCentrifugeGameTests.java`、`GasStorageGameTests.java`、既存関連試験：機能と回帰確認。

## 制約・未確認事項

- 新規ワールドとNBT単位の保存・再読込を確認した。実プレイヤーの既存ワールド全体の更新・再起動、数百台の負荷、専用サーバーへ複数クライアントが参加する運転は未確認。
- 旧圧縮機と専用品の自動置換は行わない。更新前に旧設備から内容物を回収すること。保存済み3×3槽のローターは回収可能だが、その構成で再運転はしない。
- レシピ量と速度は既存のゲーム設定であり、実機の濃縮能力や同位体収率に校正していない。
- 往復式圧縮機の既存の発熱近似は変更していない。`plan`で使用する`projectedPressure(..., true)`は吸入側の投影圧力を返すため、吐出側に対する発熱の校正には既存の懸念がある。今回の硝酸試験の合格を、圧縮熱の物理的妥当性を保証する結果として扱わない。
- 既存の検証スクリプト2件は不合格のまま：`validate_machine_sounds.py`は定義11種類に対して10種類を要求する固定条件で停止。`validate_ponder_language.py`は既存の`ponder.create_heavy_indusstry.catalyst_basket.title`等で停止。いずれも開始時のHEADに原因が存在することを確認し、今回の範囲外として修正していない。
- 最初の広範囲試験はワールドのリージョン書込（`IOWorker-chunk`の`pwrite0`）待ちで停止した。試験用保存先を`/tmp`へ変更して回避し、上記の結果を得た。通常プレイの保存処理を変更したものではない。

## 再実行

Java 21でリポジトリのGradle Wrapperを使う。

```sh
bash gradlew --no-daemon build
bash gradlew --no-daemon -I docs/quality/compact-centrifuge/verification.gradle runGameTestServer
bash gradlew --no-daemon -I docs/quality/compact-centrifuge/verification.gradle \
  -DtestClasses=ReactorGameTests,DriveLessVesselGameTests,VesselBasinGameTests,StructureGuideGameTests,LiddedVesselGameTests,VesselBoilingGameTests,GasPipeGameTests,GasStorageGameTests,ReciprocatingCompressorGameTests,NitricPlantIntegrationGameTests,ToxicGasGameTests,CompactCentrifugeGameTests,PressureGameTests,PressureAndFreezingGameTests,PressureStageTwoGameTests,ThermalControlGameTests,TurbineThermalGameTests,ProductUseGameTests,PurifiedWaterGameTests,CraftingRecipeGameTests,SurvivalReachabilityGameTests,NitricAcidGameTests \
  runGameTestServer
python3 tools/validate_plant_tutorials.py
```

`verification.gradle`は試験クラスを指定し、試験ワールドを`/tmp`へ保存するための設定のみ。通常のゲーム起動やレシピには影響しない。全量検証を主張せず、今回の対象と各制約を引き継ぐこと。
