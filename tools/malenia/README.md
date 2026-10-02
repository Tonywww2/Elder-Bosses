# 腐败女神资源制作

当前可编辑工程为 `malenia.bbmodel` 与 `malenia_phase_two.bbmodel`，共用 92 根骨骼和 40 段动画。
当前几何、动画和贴图导出位于本目录的 `geo`、`animations`、`textures`，由 `current_assets.json` 固定哈希和运行目标。
这些是当前制作输入与导出，保留以支持 Blockbench 增量编辑。

## 入口

- [战斗规格](../../docs/bosses/malenia-blade-of-miquella.md)
- [技能设计](../../docs/bosses/malenia-skill-design.md)
- [竞技场](../../docs/arenas/malenia-arena.md)
- [当前素材来源](SOURCES.md)
- `scripts/export_assets.js`：导出当前 Blockbench 工程。
- `scripts/sync_animations.js`：同步编辑器动画与运行资源。
- `scripts/sync_shader_wings.js`：维护着色器腐败翼合同。
- `scripts/validate_assets.js`：核对当前模型、UV、动画键和运行副本。
- `arena`：当前圣树洞厅结构生成工具与原始输入。
- `tests` 与 `motion`：现有定向检查。

独立 Node 工具按脚本位置解析工作区。在 Blockbench 中执行脚本前，设置
`globalThis.elderBossesRoot` 为项目根目录，或使用环境变量 `ELDER_BOSSES_ROOT`。
不再依赖旧机器的绝对路径。

生成的预览和报告放入 `build`；共享资源核对报告为 `build/asset-validation/malenia/assets.json`。
当前双阶段外观、着色器腐败翼、瞬防光圈和原创音源以运行资源为准，不保留过时版本文档和验收副本。
