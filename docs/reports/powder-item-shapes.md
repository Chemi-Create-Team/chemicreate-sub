# 粉体アイテムの火薬形状統一

2026-11-09のユーザー指定により、現在登録された粉体28種を、Minecraft 1.21.1の火薬と同じ16×16の輪郭と粒の陰影配置に揃えました。既に火薬を参照していたウラン粉4種は、そのモデルと実行時の着色を維持しています。残る24種は各物質の従来の基準色を保ち、火薬と同じ88画素の形へ描き直しました。

## 対象

| 区分 | ID |
| --- | --- |
| 金属酸化物・顔料 | `alumina`, `titanium_dioxide`, `zirconia`, `nickel_oxide`, `molybdenum_trioxide`, `chromium_oxide`, `iron_oxide_red`, `nickel_titanium_yellow`, `molybdenum_blue` |
| ウラン工程の粉 | `yellowcake`, `enriched_uranyl_fluoride` |
| 既存の火薬参照・着色を保持 | `uranium_trioxide`, `uranium_dioxide`, `uranium_tetrafluoride`, `enriched_uranium_dioxide` |
| 再処理・MOX原料 | `plutonium_dioxide`, `depleted_uranium_dioxide`, `mox_powder` |
| その他の化学粉 | `sulfur_dust`, `ammonium_nitrate`, `ammonium_sulfate`, `anfo`, `salt`, `pvc_resin`, `borax`, `gypsum` |
| 設置可能な粉体の手持ち表示 | `triiron_tetroxide_pile`, `spent_triiron_tetroxide_pile` |

四酸化三鉄と使用済み四酸化三鉄は、インベントリ内の立方体モデルを粉体スプライトへ変更しました。設置後のブロックの画像・モデル・落下・充填・脱硫・再生処理は変更していません。硝安とANFOは登録・表示名・工程上で成形ペレットや顆粒として区別されていないため、化学粉に含めています。

## 保持したもの

登録された非バケツの材料・部品100種を、粉28種と保持72種に分類しました。全IDと個別の保持理由は[粉体一覧](../../tools/powder_items.json)に記録しています。採掘した鉱物塊・蛍石、触媒の成形部材、ウラン/MOXの成形・焼結ペレット、急冷したポリマーのペレット・棒、ガラスフリット、石綿繊維、金属地金・板、被覆管片、樹脂塊などは従来の形を保持しています。

## 生成元と確認

[powder_assets.py](../../tools/powder_assets.py)が形状・着色・モデルの共通処理です。[vanilla_gunpowder_shape.json](../../tools/vanilla_gunpowder_shape.json)には実際のMinecraft 1.21.1の火薬PNGから採った透明輪郭と7段階の陰影位置を記録しています。物質色は従来の生成器の基準色、既存ウラン4種は実際のJava着色登録が正本です。9個の設備別生成器も同じ処理を呼ぶよう更新しました。

```sh
python3 tools/generate_powder_items.py
python3 tools/generate_powder_items.py --check
python3 tools/validate_visual_identity.py
python3 tools/test_ci_validators.py
```

生成・検証にはPillowもゲーム起動も不要です。モデル・PNG生成の一致、登録一覧と分類の一致、28種すべての火薬輪郭・粒の陰影、実行時着色、既存アイテム間の重複を確認します。新たな未分類の粉を登録した場合と、粉の透明画素を壊した場合に検査が失敗する回帰も含め、15件の検証器テストが合格しました。

比較画像は`/workspace/outputs/powder-shapes/powder-before-after.png`、静的監査データは同ディレクトリの`powder-audit.json`です。非対象の既存画像・モデル1806件は変更前とバイナリ一致を確認しました。

実クライアント確認用には既存の開発用quality checkへ`-Dcreateplant.powderItemsClientCheck=true`を追加します。バニラのチェスト画面に火薬と全28粉を並べ、実アイテムモデルを`screenshots/quality-powder-items.png`へ保存し、閉じて従来のPonder確認へ進みます。製品側のGUIやメニュー登録は追加していません。

統合後にビルドとRTX 4060の実クライアントで確認しました。バニラのチェストへ火薬と全28粉を並べ、実モデルの描画を撮影しています。[ゲーム内表示](../quality/powders-compact-sounds/powder-items.png)、[変更前後](../quality/powders-compact-sounds/powder-before-after.png)、[登録・保護対象の監査](../quality/powders-compact-sounds/powder-audit.json)を保存しました。実装コミットIDと全体の試験結果は[統合報告](powders-compact-reactor-sounds.md)を参照してください。
