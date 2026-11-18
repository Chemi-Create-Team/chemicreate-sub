# 蒸気タービン円周翼列高密度化レポート

## 実装コミット

- 実装コミット：`7f271d27e3a991f329b61b32d6ca66a83e7ea5d4`
- 実装日：2026-11-06
- 対象：Minecraft 1.21.1 / NeoForge 21.1.219 / Create 6.0.10 / Java 21

この文書自身を追加するコミットIDはコミット前に記録できないため、上記にはメッシュ本体のコミットを記載する。

## 変更内容

添付された実機蒸気タービン写真を形状資料として、1段あたり8枚だった大型ファン状の回転メッシュを、連続した円周翼列へ変更した。

| 段 | 変更前 | 変更後 | メッシュ面数 |
| --- | ---: | ---: | ---: |
| 高圧段 | 8枚 | 36枚 | 54 → 408 |
| 低圧段 | 8枚 | 40枚 | 54 → 448 |

各翼は単純な直方体ではなく、根元・中間・先端の3断面を持つ2区間の立体翼とした。半径方向の湾曲、回転方向の後退角、軸方向のねじれ、先端へ向かう薄肉化を個別に持たせ、斜めから見たときも前縁・翼面・先端面を判別できる。四角いハブも16面の円筒形へ変更した。

既存の高圧・低圧テクスチャ、回転軸、外径、ブロック状態、レシピ、保存形式は変更していない。`blades`ブロック状態は従来どおり軸方向の段数を表し、今回追加した円周方向枚数とは分離されている。生成JSONには`circumferential_blade_count`と`radial_blade_segments`を記録し、`tools/generate_rotating_models.py`から決定的に再生成できる。

## ゲーム内確認

- HUDなしの実クライアント画像：`/workspace/outputs/turbine-blades-dense-in-game.png`
- 画像サイズ：1270 × 790、589,605 bytes
- SHA-256：`14637e928c34792ac0feeecab73e627bc38aeb5b4dc4b9e38339a1d161c330ef`

最大径の低圧段40枚と高圧段36枚を同じ軸上へ並べ、翼間隔、ハブへの接続、段ごとの外径、テクスチャ向きに欠損がないことを確認した。クライアントログに`Failed to load render mesh`、描画例外、今回のアセットに関する欠落テクスチャはない。

## 検証結果

| 検証 | 結果 |
| --- | --- |
| 生成器を再実行した前後のSHA-256比較 | 2メッシュと2ブロックテクスチャがすべて一致 |
| メッシュ構造検査 | 36/40枚、408/448面、全座標が有限値、全法線が単位長 |
| `python3 tools/validate_visual_identity.py` | 成功 |
| `python3 tools/test_ci_validators.py` | 11テストすべて成功 |
| `python3 -m py_compile tools/generate_rotating_models.py` | 成功 |
| `./gradlew --no-daemon build`（Java 21） | 成功。FluidPhase 25件、HaberBoschProcess 1,019件、ReactorPhysics 1,670件、原子炉制御試験を含む |
| `git diff --check` | 成功 |
| `mc-debug glxinfo -B` | NVIDIA GeForce RTX 4060、直接描画、OpenGL 4.6 |
| RTX 4060実クライアント | ワールド読み込み、メッシュ描画、HUDなしPNG保存、全ディメンション保存まで成功 |

MojangサービスのDNS、`libflite`、OpenAL、外部依存Modの欠落音・互換モデル警告は既存の開発環境由来である。ワールド終了時に全ディメンションの保存完了を確認した後、タイトル画面からウィンドウを閉じる際にNVIDIA EGLドライバ内で終了時SIGSEGVが発生した。これは`Minecraft: Stopping!`後のネイティブ描画コンテキスト破棄で発生しており、タービンメッシュの読み込み・描画中には発生していない。
