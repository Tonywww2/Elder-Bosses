# Common 配置结构

本项目只注册一个 common 配置文件：

`config/elder_bosses-common.toml`

Forge 1.20.1 使用 `ForgeConfigSpec`，NeoForge 1.21.1 使用对应的 `ModConfigSpec`，两端都以 `ModConfig.Type.COMMON` 注册相同语义的配置树。不得再注册 `malenia-server.toml`、`radahn-server.toml`、独立 client 配置或第二个 common 配置。

## 配置树

| 根节 | 读取侧 | 用途 |
| --- | --- | --- |
| `[indicators]` | 客户端本地 | 技能指示器启用、透明度、透视边框倍率、距离和几何预算 |
| `[malenia.*]` | 服务端权威 | 腐败女神属性、技能、硬直、瞬防和台词数值；`arena` 子树当前仅预留 |
| `[promised_consort.*]` | 尚未接入 | 约定之王的未来配置 schema，当前运行时不注册或读取 |

`indicators.opacity` 只改变本地渲染，不同步到服务端，也不能影响命中范围或时机。Boss 数值由服务端读取并在开战时生成快照；客户端只接收战斗和指示器所需的权威状态。

Malenia 的下列行为是程序不变量，不在 TOML 中重复开放：战斗必定在开战时生成快照；多人不会提高单次招式伤害，伤害倍率固定为 1.0；物理通道受护甲影响、魔法通道绕过护甲，由 `DamageChannel` 与伤害类型标签固定；硬直只按服务端确认的实际生命损失累计，同一命中 ID 的伤害分量先聚合，且只接受玩家及归属于玩家的来源；第二阶段开场生命比例只读取 `malenia.general.phase_two_start_ratio`；瞬间防御提示音固定为已注册的 `elder_bosses:malenia.instant_guard_cue`；净化物品固定为已注册的 `elder_bosses:golden_needle`；Malenia 自身的异常积累倍率、睡眠失衡和癫狂免疫使用固定规则。踢击与非玩家目标等仍需配置或设计裁决的项目保持原配置语义。

`malenia.performance.max_rot_zones` 是服务端权威的玩法上限，不是纯视觉性能选项。它限制每个 Malenia 实例同时活动的腐败区域数量；达到上限时创建新区域会移除最早的活动区域，设为 `0` 则不再创建腐败区域，因此会直接影响区域伤害与腐败积累。该值在开战时进入战斗快照，重载只影响下一场战斗。

猩红腐败适用于所有 `LivingEntity`。每个实体的积累容量只读取独立属性 `elder_bosses:scarlet_rot_capacity`，默认值为 100；该属性不属于 TOML，不写入战斗快照，也不随最大生命、当前生命、护甲、攻击力或其他属性缩放。`[malenia.scarlet_rot]` 只配置积累衰减、触发持续时间、伤害、治疗削弱、移动减速和净化规则。

Malenia 的奉献义手刀与无垢金翼盔使用物品注册代码中的固定占位数值，不属于 common 配置，也不参与热重载。后续平衡这些占位值需要修改注册代码并发布新版本。Promised Consort 的配置树不受此规则变更影响。

Boss 建筑不属于 TOML 配置。当前阶段不提供或加载建筑 NBT；建筑几何、方块、材料、空气清理区、片段偏移和锚点设计仍完整保留在竞技场文档中，等待后续资产。`[malenia.arena]` 与 `[promised_consort.arena]` 暂作为后续接入预留，只保存逻辑战斗半径、卸载策略、方块破坏、恢复和穿墙等运行参数。资产接入后，相同资源 ID 的数据包结构可以覆盖内置建筑，但不能通过配置改写建筑内容。

## 文档文件

[elder-bosses-common.example.toml](elder-bosses-common.example.toml) 是唯一完整样例，依次包含 `[indicators]`、`[malenia.*]` 和 `[promised_consort.*]`。`docs/config/` 不保留第二份 TOML，避免片段被误认为可单独加载的配置。

## 重载规则

- `indicators.*` 是客户端本地视觉值，配置重载后下一帧生效。
- Boss 战斗数值只应用于下一场战斗；进行中的实例继续使用开战快照。
- Malenia 的固定注册 ID、伤害通道护甲规则、硬直输入规则、固定异常规则、腐败容量属性、多人单次伤害倍率和装备注册期占位值不参与配置重载。
- 建筑资产接入后，数据包结构重载只影响下一次新建竞技场，不重铺或修改已经放置的建筑。
- 配置重载不能增加技能、改变技能段、改变指示器形状映射或注入脚本。
- 非法值由配置规范修正并记录日志；`indicators.opacity` 限制在 0.10-1.00。