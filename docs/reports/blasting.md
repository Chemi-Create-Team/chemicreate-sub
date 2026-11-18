# ANFO爆薬と発破薬（A3）レポート

## 基準と実装コミット

- 作業ブランチ：`main`
- 作業開始時の基準コミット：`be6f1f5`
- 実装コミット：`9a48d3a`

## 依頼と決定事項

- 依頼：A3（産業用爆薬と発破採掘）。硝酸アンモニウムと軽油から ANFO を作る。
- ユーザーの決定：発破は新しい「発破薬ブロック」で行う。門型クレーン（別メンバーの担当）には手を入れない。

## 実装内容

- アイテム `anfo`（ANFO爆薬）：ミキサーで硝酸アンモニウム3（タグ `c:fertilizers/ammonium_nitrate`）＋軽油25 mB（流体タグ `c:diesel`。CDGの軽油と低硫黄軽油）→ 3個。
- ブロック `blasting_charge`（発破薬、`block/BlastingChargeBlock.java`）：
  - 作業台で ANFO4・紙4・レッドストーンダスト1 → 1個。
  - レッドストーン信号で `LIT` になり、40 tick 後に起爆する。点火中は雷管の先が光り、煙が出る。
  - 起爆：中心から距離3以下のブロックのうち、ツルハシかシャベルで掘る・爆発耐性9以下・ブロックエンティティなし・壊せるもの、を `Block.dropResources` で落としてから消す。
  - そのあと、ブロックを壊さない爆発（強さ3）で音・煙・生き物へのダメージを出す。落ちたアイテムは爆風で壊れないようにした（`ExplosionDamageCalculator.shouldDamageEntity`）。
  - 半径内のほかの発破薬は、4 tick 後に起爆する（連鎖）。
- 見た目（`tools/generate_blasting.py`）：
  - クラフト紙の薬包4本に赤い印刷の帯、鋼の締め帯、木の蓋、銅の雷管の16pxテクスチャ。
  - 点火中のモデル（雷管の先が光る）、ANFO爆薬のアイコン（白い粒）。
- Ponder「ANFOで発破採掘する」（`tools/generate_blasting_ponder.py`、4手順）：岩肌の足元の発破薬、レバーとレッドストーン、ANFOを作るミキサー。
- 文書：新規 `docs/blasting.md`。`INDUSTRIAL_SYSTEMS.md`、`DEVELOPMENT_CHEMISTRY.md`、`PONDER_GUIDE.md` を更新した。

## 同じ作業で直したもの

- 燃料電池（A4）：負荷がないとき、回路計算の誤差で端子電圧が400 Vをわずかに超え、「逆流」と判定して切り離していた（ほかのテストで回路がある GameTest で見つかった）。±0.01 A 未満の電流は誤差として扱うようにした（`FuelCellBlockEntity.BACKFED_AMPS`）。
- 日本語の Ponder 字幕の折り返し：「2 / 秒後」のように、数字と助数詞の間で改行されていた。数字の直後では改行しないようにした（`client/JapaneseLineBreak.java`）。
- GameTest `MaterialEquipmentGameTests.hotPipesAndTanksBurnWhatTouchesThemButTheCryogenicTankDoesNot`：たまに失敗していた（3回に1回程度）。ブタが生成位置から動き、位置で「どのタンクの前か」を決めていたため。ブタの AI を止め、どのタンクの前かを生成時に記録するようにした。

## テスト

| 確認 | 結果 |
| --- | --- |
| `bash gradlew build runGameTestServer` | 293件すべて合格（2回続けて実行） |
| CIの検証スクリプト4本 | 合格 |
| 実クライアント（日本語） | Ponder全95場面の題名が合格。「ANFOで発破採掘する」を撮影し、モデルと字幕（「2秒後」がつながること）を目視した |

- 新規 `BlastingGameTests.aChargeBreaksTheRockInReachIntoItemsAndSetsOffTheNext`：点火、導火線の時間、半径内の石と鉄鉱石が丸石と鉄の原石になること、連鎖、チェスト・黒曜石・半径外が残ること、ANFOのレシピの読み込み。

## 未検証・制約

- 実ワールドでの発破（大きな岩盤、水や溶岩のある所、チャンクの境界）は試していない。
- 流体（水・溶岩）は壊す対象にしていないので、そのまま残る。
- 爆風によるダメージの大きさは、バニラの強さ3の爆発と同じ。プレイヤーで試していない。
- 門型クレーンとの連携はしていない（別メンバーの担当のため）。

## 次回の注意

- 爆発で物を落とすときは、爆風でアイテムが壊れないよう、落とす順番と `ExplosionDamageCalculator` に気をつける。
