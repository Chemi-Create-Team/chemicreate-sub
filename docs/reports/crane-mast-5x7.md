# 引き継ぎ：燃料取扱いクレーンの12マスマスト

実装コミット：`c50a564651c1d812ae9cbbe564e72fcb9ff43c3a`。対象ブランチ：`work`。既存仕様は[門型クレーン](../gantry-crane.md)と[燃料交換クレーン](crane-refuelling.md)を参照。

## 実装

- 原子炉保守用マストを、下側の太い部分5マスと上側の細い部分7マス、合計12マスへ延長した。
- 太い部分は既存のマストヘッド1マスと新しい10 px幅の中間モデル4マスで構成する。細い部分は既存の6 px幅モデル7マスを使う。
- 炉心より低いプールへさらに降ろす必要がある場合、12マスを超えた距離だけをマスト上端のロープとして描く。このため太い5マス・細い7マスの寸法は変わらない。
- 作業対象の上には最低12マスの高さを要求する。足りない炉心またはプールは操作せず、ゴーグルとDisplay Linkへクリアランス不足を表示する。
- 開発用ワールド、GameTest、通常クレーンPonder、燃料交換Ponder、RBMKプレイテストのレールと支持構造を新しい高さへ追従させた。
- 日本語・英語の説明とPonderテキストに5＋7マスおよび最低12マスの条件を追加した。

## 寸法と高さの計算

`CraneDriveBlockEntity`の作業深さは `railY - (targetY + 1)`。ヘッドが最下部の1マスを占めるため、12マスを完全に展開する最小作業深さは `12 - 1 = 11`。

| 部分 | 構成 | 長さ |
| --- | --- | ---: |
| 下部（太） | マストヘッド1＋太い中間モデル4 | 5マス |
| 上部（細） | 細い中間モデル7 | 7マス |
| 合計 | 下部＋上部 | 12マス |

燃料交換の開発用構造では炉上部キャップがy=24、レールがy=36なので、高さ差は12マス、内部深さは11。燃料プール水面は1マス低いため、追加の1マスだけロープになる。通常クレーンPonderはレールをy=5からy=14へ、燃料交換Ponderとクライアント検証はy=27からy=36へ上げた。

## 主要ファイル

| ファイル | 内容 |
| --- | --- |
| `block/entity/CraneDriveBlockEntity.java` | 5＋7の定数、12マスの作業クリアランス判定 |
| `client/CraneRenderer.java` | 太い4中間＋ヘッド、細い7中間、超過分ロープの描画 |
| `models/block/crane/mast_lower_segment.json` | 10 px幅の太いマスト中間モデル |
| `client/CraneClientCheck.java` | レール高、搬送検証結果、撮影位置 |
| `client/RbmkPlaytestWorld.java` | 高いレールと最大32段に収まる脚 |
| `gametest/CraneGameTests.java` | 炉心保守・梁支持テスト構造の高さ |
| `ponder/CranePonderScenes.java` | 2つのクレーンPonderの座標と画角 |
| `tools/generate_crane_assets.py` | モデル、翻訳、Ponder NBTの生成元 |
| `docs/gantry-crane.md` | プレイヤー向け寸法・設置条件 |

## 検証

2026-11-03、Java 21.0.13、Minecraft 1.21.1、NeoForge 21.1.219、Create 6.0.10-281、NVIDIA RTX 4060 / OpenGL 4.6。

- `bash ./gradlew --no-daemon build`：成功。FluidPhase 25件、HaberBoschProcess 1,019件、ReactorPhysics 27件も成功。
- `jq`による日英JSONと新旧マストモデルの構文・寸法検査：成功。太いモデル10 px幅、細いモデル6 px幅、いずれも高さ16 px。
- `git diff --check`：成功。
- 実クライアント統合検証：成功。結果は下部5、上部7、レールとキャップの高さ差12、新燃料2本を炉へ装荷、使用済み燃料1本をプールへ回収、吊り具の残留0、梁支持有効、燃料棒の観測移動量約15.49マス。
- クライアント品質検証：英語の実行時翻訳と全74 Ponderシーンの登録を確認。`crane_refuelling`と`gantry_crane`の両シーンで新しい高い構造と動くクレーンを描画・撮影した。
- 配布確認用JAR：`create-heavy-indusstry-crane-5x7-0.1.0.jar`、3.1 MiB、SHA-256 `836dd1ce92953d0dad00514a57d6c1cb7e56b071e933083b6450f3ee35f8ae00`。

## 未完走の検証と環境上の注意

- リポジトリ全239件のGameTestは実行を開始したが、仮想ディスクへのMinecraftリージョン書き込みが5分以上ブロックしたため完走していない。停止前に今回と無関係な既存の小型コンプレッサー試験 `actualcreatepumptransfersbetweensmallcompressors` が1件失敗した。クレーンの燃料往復は上記の実クライアント統合検証で確認した。
- クライアントは `CLIENT QUALITY PASSED` と結果ファイル保存後、終了処理中にNVIDIAの `libEGL_nvidia.so.0` でSIGSEGVとなり、Gradleの終了コードだけは134になった。ゲーム内検証とスクリーンショット保存はクラッシュ前に完了している。
- 既存の `tools/validate_ponder_language.py` は欠けている `pressure_relief` 見出し、`tools/validate_visual_identity.py` は既存バケット数の期待値で失敗する。今回変更した日英JSON、モデル参照、Ponderは個別検査と実クライアントで確認した。
- 長時間運転、チャンクのアンロード／再ロード、マルチプレイヤー同時操作は未検証。

## 成果物

確認用JARとスクリーンショットはワークスペースの`/workspace/outputs`に保存した。リポジトリへは生成結果を含む実装コミットと本レポートだけを追加する。
