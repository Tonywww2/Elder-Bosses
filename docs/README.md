# Elder Bosses 设计文档

本目录保存 Boss 实现前的战斗、美术、资料与配置基线。

## 文档入口

### 共通系统

- [项目开发规范](../AGENTS.md)
- [Boss 台词字幕与非语言音效规范](dialogue/boss-voice-lines.md)
- [Boss 技能指示器设计规范](indicators/boss-skill-indicators.md)
- [唯一 Common 配置结构](config/README.md)
- [Common 配置样例](config/elder-bosses-common.example.toml)

### 约定之王

- [GeckoLib 模型与动画工程](../models/promised_consort/README.md)
- [现阶段实装计划](implementation/promised-consort-plan.md)
- [可实现战斗规格](bosses/promised-consort-radahn.md)
- [美术制作规范](art/promised-consort-radahn-art-bible.md)
- [竞技场 NBT 结构规范](arenas/promised-consort-arena.md)
- [资料与图片台账](references/promised-consort-radahn-sources.md)

### 腐败女神（马莲尼亚）

- [GeckoLib 模型与动画工程](../models/malenia/README.md)
- [技能设计 R2：动作、力量感与视觉效果](bosses/malenia-skill-design.md)
- [V14 着色器腐败翼与阶段名称](bosses/malenia-shader-wings-v14.md)
- [V15 位移、投技、弹反光圈与视觉可读性](bosses/malenia-combat-readability-v15.md)
- [V16 二阶段外观、招架反馈、BGM 与腐败 120/40 tick](bosses/malenia-phase-two-feedback-v16.md)
- [技能首批实装与验收记录](bosses/malenia-skill-implementation.md)
- [本轮视频分析与代码差异](references/malenia-skill-video-analysis.md)
- [可实现战斗规格](bosses/malenia-blade-of-miquella.md)
- [美术制作规范](art/malenia-art-bible.md)
- [圣树根部洞厅 R1：建筑、建造命令与验收](arenas/malenia-arena.md)
- [资料与图片台账](references/malenia-sources.md)

## 已确认口径

- 平衡目标：两位 Boss 的默认数值面向原版终局装备的 1-4 名玩家；约定之王允许将参与上限配置到 16，并继续线性外推耐久，但 5 人以上不属于默认平衡保证。
- 目标平台：Minecraft 1.20.1 Forge 与 Minecraft 1.21.1 NeoForge。
- 伤害格式：任何伤害分量都必须表示为「固定值 + Boss 攻击力百分比」。
- 女武神资产：当前 [phase_two_feedback_v16](bosses/malenia-phase-two-feedback-v16.md)，共享 92 根骨骼、40 段动画；一阶段 385 个方块，二阶段变体 155 个方块。保留方块手、连续披风和着色器腐败翼，二阶段新增苍白皮肤、不对称菌片与独立材质。新增招架成功音效、金白光圈和两阶段战斗 BGM；腐败停止积累后 120 tick 首次衰减，之后每 40 tick 衰减一次。一阶段名称为“米凯拉的锋刃”，二阶段为“腐败女神”。最新哈希以[当前资产合同](../models/malenia/current_assets.json)为准；构建、计时与离屏渲染检查通过，客户端实机视听尚未验收。
- 约定之王资产：当前 [sculpted_relief_v11](../models/promised_consort/README.md)，125 根骨骼、620 个 cube、43 段动画。新增 55 个胸甲、胸徽、冠脊及肩甲装饰体块，仍在原细节预算内。两对手臂上臂、前臂各为 v8 的 3/5，以及拉塔恩和装备 1.20 倍、米凯拉主体 1.00 倍比例继续保留，不重复缩放；原图集、UV 和动画未改。骨骼剑光与范围 GLSL 沿用此前实现。
- 本轮装饰状态：两套新增几何已完成资产保护检查、代表性近景与动作审查、双平台资源处理。Forge 加载新资源并正常退出，但没有入世界记录，外观反馈被跳过；NeoForge 本轮未启动客户端。新装饰实机外观尚未验收，不沿用上轮杂色贴图认可，详见[腐败女神记录](../models/malenia/runtime_validation.json)与[约定之王记录](../models/promised_consort/runtime_validation.json)。完整逐招、多人和战斗验收仍独立。
- 约定之王竞技场已接入固定 NBT 自然生成、祭台召唤和存档绑定；当前 v10 细化材质、神门扶壁及入口残柱，保留 40 格战区和 v9 深基础。位置保护、地形恢复及 v10 实机验收仍待完成，详见竞技场专项文档。
- 女武神圣树洞厅已接入黑森林概率生成、祈愿石献祭召唤、唯一 Boss 存档绑定、场地范围与脱战重置。R2 使用含水淤泥半砖、三种更密集的白花丛与原创 16×16 材质。地下洞厅由地表树根入口及八段折返楼梯连通，自然版本为双版本 19 片 NBT；管理员命令保留 18 片洞厅版本。配置、旧场地兼容和验收见专项文档；封门与建筑破坏恢复尚未接入。
- 最终程序边界：招式、阶段、时间轴、位移与判定由 Java 程序控制；后续提供的 Boss 建筑使用原版数据包 NBT 结构模板，Java 只负责模板放置、标记解析、战斗边界、破坏记录与恢复。
- 配置策略：项目只注册 `config/elder_bosses-common.toml`；生命、多人缩放、参与者与生命周期策略、抗性、选招权重、时序、判定、伤害、硬直、瞬间防御、逻辑场地规则、破坏预算、奖励数值与占位表现预算均位于该 common 配置。互斥策略使用可校验枚举字符串。建筑几何、方块、材料和正式锚点属于 NBT 资源合同，不可由 TOML 改写；伤害与实体免疫集合由数据包标签控制。
- 指示器：技能范围由服务端权威状态驱动；地面填充受遮挡，低透明边框可透视；全局透明度由 common 配置中的 `indicators.opacity` 控制。
- 竞技场尺度：腐败女神逻辑半径 26 格；约定之王逻辑半径 40 格。后者半径约为前者 1.54 倍、面积约为 2.37 倍。
- 硬直口径：Boss 实际生命损失默认按 75% 转为硬直值，上限为最大生命的 10%，并应用四段距离倍率。
- 腐败口径：所有 `LivingEntity` 都能积累猩红腐败；容量只由独立属性 `elder_bosses:scarlet_rot_capacity` 决定，默认 100，独立于生命和其他属性，不属于 common 配置或战斗快照。
- 瞬间防御：玩家举盾后第 3-6 tick 命中特定攻击段才成功；对应攻击必须同时显示红色光效并播放独立提示音。
- 台词策略：关键对白只显示本项目原创字幕，不制作对白录音或口型。约定之王本轮只实装独立瞬防提示音；战吼、呼吸、受击和倒地事件及配置先保留为待原创或授权音频到位后的合同。
- 正式身份：`elder_bosses:malenia` 显示为「腐败女神」；`elder_bosses:promised_consort` 显示为「约定之王」。

## 素材使用警告

`assets/reference/` 中的《艾尔登法环》截图是低分辨率内部研究资料，版权归原权利人所有。它们不得进入发布 JAR、宣传图、Modrinth/CurseForge 页面或可分发资源包。正式发布资产必须重新原创，参考图仅用于识别轮廓、材质关系、动作节奏和色彩层级。
