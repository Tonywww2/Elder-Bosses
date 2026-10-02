# 约定之王竞技场制作

当前资源合同见 [竞技场规范](../../../docs/arenas/promised-consort-arena.md)。

`production_manifest.json` 固定两个构建目标的模板与哈希；`refinement.json` 描述当前雕刻修订。
`refine_arena.py` 默认核对，`--write` 更新模板；`refine_materials.py` 生成当前 16 像素材质。
网格与原始几何制作输入仍用于重建固定建筑。预览和验证输出写 `build`。
