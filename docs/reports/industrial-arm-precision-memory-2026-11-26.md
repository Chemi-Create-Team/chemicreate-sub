# 産業用アームの先端と磁気メモリの表示

実装コミット `33ae720c653fb7824057056c3d626b5603bb2e49`

- `IndustrialArmRenderer` と `models/block/industrial_arm` に、細い手首、ピンセット状の爪、伸縮軸、溶接トーチを実装した。コア配置時は爪が開閉し、配線時は主アームと補助アームが線の端を持つ。
- `IndustrialArmBlockEntity` と `ArmPose.wristClearance` は、水平方向の距離とアームの最大到達距離から手首を持ち上げられる高さを求める。持ち上げ量は最大0.44マスで、クライアント上で補間し、細い軸を同じ分だけ伸ばす。
- `Trajectory` と `ArmWorkDraw` は、作業軌道をもとに配線の端を描く。磁気メモリの未完成品と完成品は既存の16×16に合わせ、フェライトの輪、銅線、センス線、基板端子を描き直した。寸法と作業中の見え方は[装置の説明](../industrial-arm.md)に記録した。

## 検証

- `gradlew build` は成功。アーム関連の GameTest 14件が通過し、最大到達距離付近で手首の持ち上げ量が制限されることも確認した。
- Minecraft のテストワールドで鉄板をデポに置き、配線中の見え方と搬出チェストに完成品が1個入ることを確認した。[配線中の画像](../quality/industrial-arm/precision-wiring.jpg) · [新しいテクスチャ](../quality/industrial-arm/precision-memory-textures.png)
- 確認には標準描画を使用した。外部シェーダーと Ponder の手動再生は未確認。検証環境ではワールド保存後、クライアント終了時に NVIDIA EGL (`libEGL_nvidia.so.0`) で `SIGSEGV` が発生した。
