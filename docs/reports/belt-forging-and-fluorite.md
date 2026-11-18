# ベルト鍛造と蛍石製法の追加

実施日: 2026-11-12。開始点: `fe891bc0`。実装コミット: `fe1cd56ebd21c62a9a69c42f823157c688a8ed3a`。

ユーザーの鍛造・霊魂洗浄・混合の提案を実装した。任意の確認への回答が届かなかったため、既存製法へ追加し、後半2製法の完成品は直前の話題に合わせて蛍石と解釈して進めた。既存製法の置換や、後半の完成品について明示の回答を得たという扱いにはしていない。

## 仕様

- 加熱したCreateの鉢で鉄インゴット1個＋木炭1個→銑鉄1個。
- 銑鉄→メカニカルプレス→溶岩ファン→折り返しプレス、を3周。計9工程で鋼鉄インゴット1個が必ず完成する。
- 最後のプレスだけ、製鋼スラグ1個が25%で追加される。鋼鉄との二者択一にはしない。
- 未完成鋼は赤熱色の専用アイテム。Createのシーケンストアセンブリ用Componentで進捗・保存・次工程のツールチップを扱う。
- 閃緑岩1個を魂の炎／魂の焚き火によるファンで霊魂洗浄すると蛍石1個。
- 骨粉1個＋砂利1個＋水250 mBを、加熱せずCreateの鉢で混合すると蛍石1個。
- パドル鉄の5回プレス、転炉、既存の蛍石入手ルートは維持。Createの鉄板製造、一般の熱風製錬、他Modの既存レシピも維持する。

鉄インゴットをシーケンスの最初のプレスへ直接指定すると、Createがそのシーケンスを鉄板プレスより優先してしまう。ご提案の代替入力である銑鉄を追加し、鉄板製造との競合を避けた。新しい加工機械・GUI・搬送網は追加していない。

説明は[操作ガイド](../belt-forging.md)と、銑鉄・未完成鋼・鋼鉄・スラグ／蛍石の専用Ponderで確認できる。アイテムモデルは既存のMinecraftインゴット／原鉄のスプライトとクライアントの色指定を再利用し、他の素材の外観は変更していない。

## 主要な実装

| ファイル | 役割 |
| --- | --- |
| `recipe/ForgingBlastingRecipe.java` | 標準シーケンスに組み込む再加熱工程。CreateのIAssemblyRecipeを実装 |
| `mixin/ForgingBlastingMixin.java` | CreateのBlastingTypeに、未完成鋼だけのシーケンス照会を追加 |
| `recipe/ForgingPressingRecipe.java` | 標準プレスとして処理し、未完成の間はスラグを出さず、完成時にだけ副産物を残す |
| `registry/CERecipeTypes.java` | 再加熱の型と折り返しプレス用Serializer。プレスのRecipeTypeはCreate標準のまま |
| `compat/jei/Forging*SubCategory.java` | 標準シーケンス表示内のファン・溶岩と、折り返し時のスラグ確率 |
| `registry/CEItems.java`、`client/ForgingItemColors.java` | 銑鉄、未完成鋼、スラグの登録と外観 |
| `ponder/ForgingPonderScenes.java` | 実際の配置に合わせた鍛造・蛍石の2シーン |
| `tools/generate_forging_assets.py` | 4レシピ、モデル、構造、日英中の説明の再生成 |

Create 6.0.10の標準[シーケンス対応条件](https://github.com/Creators-of-Create/Create/blob/ac0c444d9828da3453ae8cc65338e8de063286fb/src/main/java/com/simibubi/create/content/processing/sequenced/SequencedRecipe.java)では、ProcessingRecipeかつIAssemblyRecipeの工程が必要。[通常の熱風加工](https://github.com/Creators-of-Create/Create/blob/ac0c444d9828da3453ae8cc65338e8de063286fb/src/main/java/com/simibubi/create/content/kinetics/fan/processing/AllFanProcessingTypes.java)はバニラの製錬・溶鉱レシピを照会し、シーケンスを照会しない。このため、未完成鋼に限定した2メソッドのMixinを使った。

標準のシーケンス完成品リストは重み付きの択一抽選なので、そこへスラグを加える方法は使わない。折り返しプレスの第二出力に確率を定義し、未完成品が出る途中の折り返しでは第二出力を除き、鋼鉄が出る最終回だけ残す。既存のプレス・ファンの加工時間、ベルト処理、ファン加工キャッシュのリセットを再利用する。

到達性検査は、シーケンスの親入力だけでなく各工程の投入素材・流体と中間品・副産物も辿るように更新した。新素材を除外リストへ足して検査を回避する方法は使っていない。

## 検証

| 確認 | 結果 |
| --- | --- |
| `build` | 成功。既存の物理・流体・制御のチェックも合格 |
| `ForgingGameTests` | 5件合格。9工程の順番と保存、鉄板・通常の熱風製錬の回帰、鋼鉄とスラグの独立排出、霊魂洗浄、鉢での正確な消費 |
| `ForgingLineGameTests` | 1件合格。実チェスト→ファンネル→ベルト→プレス6台・溶岩ファン3台→ベルトファンネル→チェストの一連の動作 |
| 完成処理256回 | 鋼鉄256個、スラグ69個。固定seedの独立した副産物抽選で鋼鉄が減らないことを確認 |
| 全GameTest | 642件中641件合格。既知の槽清掃試験1件のみ失敗 |
| 翻訳 | 日英中2,558キー。重複・空欄・書式引数の不一致なし |
| Ponder | 141シーンの翻訳・構造参照検査に合格 |
| 専用サーバー | 上記試験で共通処理・新Serializer・Mixinの読み込みと実行を確認 |
| 実クライアント | NVIDIA RTX 4060で起動。3モデル、赤熱色、同期された9工程、標準ツールチップ、JEI工程3種を確認。Ponder全141シーンのコンパイル・実行時翻訳確認と、新規2シーンの描画を完了 |

全体試験で残る失敗は `VesselFoulingGameTests.aFouledProcessStopsThenResumesAfterOneManualBrush`。[以前の報告](plant-maintenance-and-carbon-oxides.md)でも、試験が旧側面動力入力を使っていることと、変更前での再現が記録されている。今回その設備の処理は変更していない。最初の全体試験では到達性検査がシーケンス内の新しい中間品・副産物を拾えず失敗したが、上記の検査更新後の全体再実行では解消した。

実機ラインは、ベルト1 RPM、プレス128 RPM、ファン256 RPM、標準ファン加工時間で確認した。ファンはベルトの1ブロック上に置き、溶岩の下面と側面を囲み、前面に鉄格子を置く。出力側もベルトの上面のファンネルを使用する。回転は試験用のクリエイティブモーターで供給した。

クライアント内の確認は完了したが、終了時に既知の `libEGL_nvidia.so.0` 内のSIGSEGVが起き、Gradleの `runClient` は終了コード134で失敗する。`CLIENT QUALITY PASSED` の到達と終了障害を分けて記録し、正常終了とは扱わない。

[確認ログ抜粋](../quality/forging/verification.txt)、[クライアントのレシピ確認](../quality/forging/client-recipe-result.json)、[Ponder確認結果](../quality/forging/client-ponder-result-ja_jp.json)を保存した。画面はComputer Useで取得した実クライアントの[新素材](../quality/forging/ingots.png)・[鍛造Ponder](../quality/forging/steel-forging.png)・[蛍石Ponder](../quality/forging/fluorite-sources.png)。鍛造Ponderのベルトの回転方向と、蛍石Ponderの表示倍率は実画面に合わせて修正した。

長いライン試験は通常の全試験から除外し、既存の `@ExplicitGameTest` とクラス指定で実行する。

```sh
bash gradlew --no-daemon -I docs/quality/forging/server.gradle build runGameTestServer
bash gradlew --no-daemon -I docs/quality/forging/server.gradle -DtestClasses= -DtestDir=/tmp/createplant-forging-all runGameTestServer
mc-debug bash gradlew --no-daemon -I docs/quality/forging/client.gradle runClient
python3 tools/generate_forging_assets.py
python3 tools/validate_translations.py
python3 tools/validate_ponder_language.py
python3 tools/validate_plant_tutorials.py
```

## 制約と未確認事項

- 鍛造の第三工程は、提案にあった再プレス方式を採用。水による焼き入れ工程・専用の湯気効果は追加していない。
- 新素材の赤熱は外観と工程進捗の表現で、放置による冷却・結晶組織・炭素濃度・現実の熱収支を計算するものではない。蛍石の2製法もゲーム内の入手経路として追加した。
- スラグの回収・仕分けはCreateの搬送で行う。二次利用のレシピは追加していない。
- JEIの3工程のサブカテゴリ生成と同期は確認した。JEI画面を手で開いたときの配置の目視確認は未実施。
- 標準の送風時間・1 RPMで1バッチの実機ラインを確認した。大規模な並列工場、異なるサーバー設定での最適速度、長時間の連続稼働は未測定。
