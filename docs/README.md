# Elder Bosses 设计文档

本目录保存 Boss 实现前的战斗、美术、资料与配置基线。

## 文档入口

### 共通系统

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
- [可实现战斗规格](bosses/malenia-blade-of-miquella.md)
- [美术制作规范](art/malenia-art-bible.md)
- [竞技场 NBT 结构规范](arenas/malenia-arena.md)
- [资料与图片台账](references/malenia-sources.md)

## 已确认口径

- 平衡目标：两位 Boss 的默认数值面向原版终局装备的 1-4 名玩家；约定之王允许将参与上限配置到 16，并继续线性外推耐久，但 5 人以上不属于默认平衡保证。
- 目标平台：Minecraft 1.20.1 Forge 与 Minecraft 1.21.1 NeoForge。
- 伤害格式：任何伤害分量都必须表示为「固定值 + Boss 攻击力百分比」。
- 腐败女神资产：当前 [sculpted_relief_v9](../models/malenia/README.md)，91 根骨骼、465 个 cube、40 段动画。新增胸前叶饰、中脊、眉冠、肩甲及扣饰的真实凸起和斜面，并沿翼冠原不透明轮廓补足羽片厚度。所有原体块、UV、骨点、身体比例与 `clustered_transition_v1` 贴图保持不变；保留此前的[人工动作适配](../models/malenia/MOTION_PLAN.md)，不是解包动作重建。
- 约定之王资产：当前 [sculpted_relief_v11](../models/promised_consort/README.md)，125 根骨骼、620 个 cube、43 段动画。新增 55 个胸甲、胸徽、冠脊及肩甲装饰体块，仍在原细节预算内。两对手臂上臂、前臂各为 v8 的 3/5，以及拉塔恩和装备 1.20 倍、米凯拉主体 1.00 倍比例继续保留，不重复缩放；原图集、UV 和动画未改。骨骼剑光与范围 GLSL 沿用此前实现。
- 本轮装饰状态：两套新增几何已完成资产保护检查、代表性近景与动作审查、双平台资源处理。Forge 加载新资源并正常退出，但没有入世界记录，外观反馈被跳过；NeoForge 本轮未启动客户端。新装饰实机外观尚未验收，不沿用上轮杂色贴图认可，详见[腐败女神记录](../models/malenia/runtime_validation.json)与[约定之王记录](../models/promised_consort/runtime_validation.json)。完整逐招、多人和战斗验收仍独立。
- 竞技场建筑暂缓：当前实装不创建竞技场 NBT、不实现建筑放置链路，也不生成临时替代建筑；40 格逻辑边界和相对锚点仍可在普通世界运行。模型与建筑设计保留在 `art/` 与 `arenas/` 文档中。
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