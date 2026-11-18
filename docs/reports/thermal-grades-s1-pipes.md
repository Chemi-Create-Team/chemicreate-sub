# 温度・圧力の等級化：等級の定義と配管（S1・S2の配管）

## 実装内容

- 温度5段階・圧力3段階の等級を定義した（`thermal/Grade`）。流体の等級は、`transport/FluidHazards`の表の値から求める。
- 配管素材ごとに、運べる温度の範囲・圧力の等級・耐食性を決めた（`transport/PipeMaterial`）。表は`docs/specs/thermal-grades-codex-spec.md`の2章。
- Createとこのmodのすべての配管が、素材に合わない流体を受け付けない（`mixin/GradePipeMixin`）。Createのバルブ・スマートパイプと同じ`canPullFluidFrom`で拒否する。ポンプ・タンクは対象外。
- 削除したもの：
  - 配管の摩耗・凍結・熱い面のやけど・漏れ・破裂と、配管の圧力の記録（`PipeWearBehaviour`、`HotSurfaces`、`PipeGasLeak`）。
  - 摩耗のテクスチャと、使わなくなった翻訳。
  - 物理確認用のRBMKプレイテスト3つ。
- ゴーグルと説明の表示：
  - 流体：「高温・加圧」のような等級で表示する。
  - 配管：運べる流体の等級と耐食性を表示する。
- ボイラー蒸気を運ぶ振る舞いの付与は、`BoilerSteamOutput.attachToCreate`へ移した。
- 続きの作業はCodexが行う。仕様書：`docs/specs/thermal-grades-codex-spec.md`。

## 主要ファイル

`thermal/Grade.java`、`transport/FluidHazards.java`、`transport/PipeMaterial.java`、`mixin/GradePipeMixin.java`、`client/PipeQualityOverlay.java`、`block/entity/BoilerSteamOutput.java`、`tools/generate_grades.py`

## 確認

- GameTest全体を実行し、失敗は既知の汚れの試験1件だけだった。
- 新しい試験`aPipeRefusesAFluidItsMaterialCannotCarry`：銅パイプは硝酸を通さず、1 mBも失われない。ステンレス管に替えると1000 mBすべてが流れる。
- フッ化物（ウラン六フッ化物）はニッケル合金管でしか運べなくなったため、遠心分離の試験の配管をニッケル合金管に変えた。
- 摩耗・凍結・やけど・破裂を確かめる試験は、対象の仕組みごと削除した。
- 翻訳の検証は合格した。
- 未確認：実際の画面での配管のゴーグル表示。説明・Ponder・文書はまだ旧ルールのままで、更新はE段階で行う。

## コミット

35b2c43（等級の定義）、bf910f6（配管）
