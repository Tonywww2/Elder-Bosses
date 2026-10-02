# Elder Bosses

Minecraft Boss 模组，支持 1.20.1 Forge 与 1.21.1 NeoForge。

## 项目结构

| 目录 | 内容 |
| --- | --- |
| `src/main/java` | 共通战斗代码、客户端渲染、网络与平台适配 |
| `src/main/resources/assets/elder_bosses` | 模型、动画、贴图、声音、语言与战斗资源 |
| `src/main/resources/data` | 数据包资源：战利品、配方、标签、结构与世界生成 |
| `tools/consort` | 约定之王当前骨架、模型绑定、原作解包与资源生成工具 |
| `tools/malenia` | 腐败女神当前 Blockbench 工程、资源制作与检查 |
| `tools/items` | 物品美术源文件 |
| `tools/shared` | 共通工具、配置生成与现有检查 |
| `docs` | 当前配置、战斗、竞技场和制作规范 |
| `versions` | Stonecutter 的两个构建目标 |
| `build` | 可再生成的预览、报告、临时产物与交付包 |

运行资源只从 `src/main/resources` 打包。原作解包输入位于本地
`docs/assets/reference`及`tools/consort/rig`的原件子目录，已通过`.gitignore`排除，
不进入Git提交和发布JAR。具体目录见[资源工具说明](tools/consort/README.md)。约定之王的动画和特效合同直接生成到运行资源目录，
工具目录不再保留一套部署副本。原始网格输入仍用于重建当前腰部蒙皮和双刀绑定。

## 构建

```powershell
.\gradlew.bat :1.20.1-forge:build :1.21.1-neoforge:build
```

Gradle 使用 Java 25；Forge 目标 Java 17，NeoForge 目标 Java 21。
活动源码视图在 `stonecutter.gradle.kts` 中选择。跨版本代码置于 `platforms` 包。
发布时保留已有必要依赖；JEI、Curios 和 Configured 不内嵌于模组 JAR。

## 当前战斗

- 约定之王使用当前模型与原作骨架：80 个 HKX 姿态、81 个 TAE 动作段、22 个技能入口。
  休眠、战斗和预览共用当前资源；旧项目动画银行已经移除。
- 身体升降、腰部蒙皮、双刀挂点、米凯拉头发和分身共用当前骨架。
- 地面技能预警与刀光分开绘制。简单范围和轨迹判定可在主配置中切换。
- 二阶段转场保留动画与字幕。大荒星陨默认在完成后的 1200 tick 冷却结束时重复释放，落点固定在场地中央。
- 腐败女神保留当前双阶段模型、腐败机制、瞬间防御、字幕与音乐。
- 祭坛献祭绑定唯一 Boss。约定之王在神门前高坡出生，死亡后释放场地占用。

## 配置与数据包

唯一配置文件为 `config/elder_bosses-common.toml`。中英说明和 Configured 展示均从当前定义生成。
将 `promised_consort.debug.action_broadcast` 或 `malenia.debug.action_broadcast` 设为 `true` 开启技能播报。
装备和基础属性修改后重启；战斗配置在下一场战斗捕获。

击败掉落使用实体战利品表，由数据包覆盖；没有击败奖励配置。
Forge 使用 `data/elder_bosses/loot_tables/entities/`，NeoForge 使用 `data/elder_bosses/loot_table/entities/`。
项目不迁移旧配置，也不自动改写玩家配置或存档。

## 文档

- [当前文档入口](docs/README.md)
- [主配置规范与完整示例](docs/config/README.md)
- [约定之王技能配置](docs/config/promised-consort-source-skills.md)
- [约定之王资源制作](tools/consort/README.md)
- [当前验收项目](tools/consort/ACCEPTANCE.md)
