# 反応塔・気液分離器の圧力逃がし 実装レポート

## 基準と実装コミット

- 作業ブランチ：`main`
- 作業開始時の基準コミット：`a313073`（作業中に取り込んだ他メンバーの変更 `a46b12e` までと、競合はなかった）
- 実装コミット：`2245f1eef7cd83935750f0100a2e9ef8f4839850`、`513b9f71ac9e925dae9760db7364f39492f950ad`（読み込み済みの塔の壁に持ち主を設定）

## 問題

- ハーバーボッシュ反応塔と気液分離器は、中身をFluidStackではなく気体のモル数（`HaberBoschBed`、`AmmoniaSeparator`）で持ち、`BedGas` で圧力容器に見せている。
- `BedGas.isEmpty()` が常にtrueのため、`PressureVesselBehaviour.relieve` が最初の判定で空を返し、安全弁が一切放出できなかった。
- 反応塔の壁（側面）の部品は持ち主（`ownerPos`）を知らず、側面に付けた安全弁は容器を見つけられなかった。

## 実装内容

- **`BedGas`**：放出の処理（`Vent`）を受け取る。`relieve(setPa, maximumKg, action)` は、現在の圧力と1 mol減らした後の圧力の差から、設定圧力まで下げるモル数を求めて装置へ渡す（どちらの装置も圧力はモル数に比例）。共通の `amount()` で、設定圧力に届くよう切り上げたmBを、保有量と質量の上限で切り詰める。
- **`PressureVesselBehaviour.relieve`**：`BedGas` なら最初にその放出へ回す。安全弁側は変更していない（2 kg/sの上限、配管への回収、大気への放出は既存のまま）。
- **反応塔**：上面中央の排出と同じ高温合成混合ガス（アンモニア分率付き、触媒層の温度）として放出し、`drainFraction` で各成分を同じ割合で減らす。
- **気液分離器**：気相（水素・窒素・アンモニア蒸気）を同じ割合で減らす `AmmoniaSeparator.ventGas` を追加し、冷えた合成混合ガス（蒸気の分率付き）として放出する。液体は残す。
- **反応塔の部品**：`assign` が上下面だけでなく、壁を含む構造全体に持ち主を設定するようにした（`structureParts` と同じ部品の一覧を共通化）。既存ワールドで形成済みのまま読み込まれた塔も、読み込み後の最初の構造検査で一度だけ全部品に持ち主を設定する。保存形式は変えていない。
- **Ponder**：反応塔（`chemistry/haber_bosch_tower`）と気液分離器（`chemistry/ammonia_separator`）に、安全弁を付ける手順を1つずつ追加した。生成元は `tools/generate_haber_bosch_ponder.py`・`tools/generate_ammonia_separator_ponder.py`。変更前の生成元を実行し直すと、配置データ（展開後）と翻訳が今のものと一致することを確認してから変更した。
- 文書：`docs/pressure.md` の安全弁の項に追記した。

## テスト

| 確認 | 結果 |
| --- | --- |
| `bash gradlew build` | 成功 |
| `bash gradlew runGameTestServer`、`-PwithJei=false` でも | どちらも252件すべて成功 |
| 新規：`aReliefValveOnTheTowerVentsItDownToTheSetPoint` | 壁に付けた安全弁（設定200 bar）が、約270 barの反応塔を大気へ放出して200 barまで下げ、それ以上は下げない |
| 新規：`towerReliefLetsOutTheMolesItTakes` | SIMULATEは状態を変えない。放出は塔と同じ混合ガス・アンモニア分率。減ったモル数と放出量のモル数が一致し、設定圧力まで下がる。質量上限を守る |
| 新規：`separatorReliefVentsTheGasSpaceAndKeepsTheLiquid` | 放出は気相の組成どおり。減ったモル数と放出量が一致し、液体は変わらず、設定圧力まで下がる |
| CIと同じ4つの検証スクリプト | 成功 |
| 実画面のPonder（日本語） | 全77シーンの品質チェックに合格し、2シーンの安全弁の手順を撮影。字幕の改行位置を見て文言を調整した |

## 未検証・制約

- 放出したガスを配管で別の容器へ回収する経路は、既存の安全弁の処理をそのまま使い、今回の装置での実地確認はしていない（GameTestは大気への放出だけ）。
- 反応塔・分離器の加熱や反応による圧力上昇の速さと、安全弁の2 kg/sの上限の釣り合いは、ゲーム内の運転で確認していない。

## 次回の注意

- 気体をモル数で持つ装置を新しく作るときは、`BedGas` の4番目の引数で放出の処理を渡す。渡さないと安全弁が効かない。
