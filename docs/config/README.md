# Common 配置结构

本项目只注册一个 common 配置文件：

`config/elder_bosses-common.toml`

Forge 1.20.1 使用 `ForgeConfigSpec`，NeoForge 1.21.1 使用对应的 `ModConfigSpec`，两端都以 `ModConfig.Type.COMMON` 注册相同语义的配置树。不得再注册 `malenia-server.toml`、`radahn-server.toml`、独立 client 配置或第二个 common 配置。

## 配置树

| 根节 | 读取侧 | 用途 |
| --- | --- | --- |
| `[indicators]` | 客户端本地 | 技能指示器启用、透明度、透视边框倍率、距离和几何预算 |
| `[skill_vfx]` | 客户端本地 | 全技能粒子特效启用、每 Boss 每 tick 预算和渲染距离 |
| `[malenia.*]` | 服务端权威 | 腐败女神属性、技能、硬直、瞬防和台词数值；`arena` 子树当前仅预留 |
| `[promised_consort.*]` | 服务端权威 | 约定之王的属性、参与者、生命周期、伤害、技能、硬直、表现预算和奖励；在本轮实装中接入 |

`indicators.opacity` 和 `skill_vfx.*` 只改变本地渲染，不同步到服务端，也不能影响命中范围或时机。Boss 数值由服务端读取并在开战时生成快照；客户端只接收战斗、特效和指示器所需的权威状态。

所有技能的时间配置使用绝对游戏 tick，20 tick 为 1 秒；不再提供 `cast_speed_multiplier`，也不再对填写的 tick 做百分比缩放。`windup_ticks` 是前摇，`active_ticks` 是释放中，`recovery_ticks` 是后摇。单段技能填写整数，多段技能填写等长数组，数组同一索引构成该组成段的三个阶段。顺序固定，不能通过增删数组项改变技能次数。

`range_multiplier` 继续独立控制命中半径、长度、宽度、受控移动和对应预警，不改变时间。伤害公式、命中次数、冷却、回血及持续区域的独立存续配置不因这次机制变更而重设。释放阶段不等于每 tick 都新增一次伤害，同一个命中 ID 仍遵守去重与每目标上限。

## 按Boss控制范围指示器

原有指示器只有总开关，现已在同一个common配置的 `[indicators]` 中加入两个独立字段，默认都开启：

```toml
[indicators]
enabled = true
malenia_enabled = true
promised_consort_enabled = true
```

`malenia_enabled=false` 隐藏腐败女神的范围填充、边框与范围辅助线；`promised_consort_enabled=false` 对拉塔恩生效。另一个Boss不受影响。原有总开关 `enabled=false` 仍优先于两个Boss开关，保持原全局关闭行为。

这是客户端本地的显示配置，不改变伤害判定、服务端预警数据或选招。单独关闭某个Boss的范围显示不会清空共享指示器缓存，仍保留瞬防红色脉冲和独立瞬防音；技能本身的刀轨、重力/圣光等特效继续由原有表现配置控制。显示开关不进入开战快照，客户端配置加载后由渲染时读取；这不是服务端强制所有玩家关闭显示的选项，也没有新增配置文件注册。

## 拉塔恩远程反制

**后续强化已实现（2026-09-17）**：默认9格进入/5格退出，远程目标段间调整总预算默认乘2，四种追击反制权重额外乘2，两倍率可配置。每tick步速、攻击判定范围与冷却不变；双加载器编译和离线专项通过，尚未入世界验收。[历史交接](../../chats/2026-09-17-promised-consort-ranged-c6f3b72b/handoff.md)保留实施前状态与用户裁定证据。

**选招与间隔强化（2026-09-17）**：六组左右连斩默认9格起降至10%权重、16格起归零。当前目标满足远程资格时，组内额外空闲默认乘0.5；后续参考重制已采用下文的2至4招攻击组，组后喘息不乘此倍率。阶段后摇压缩由独立攻击组规则控制，冷却和强制恢复不变。

2026-09-17 已接入 `ranged_counter_v1`：四种原技能的反制变体和三种独立防御。数值已获用户确认作为首版默认，**不等于动作与游戏行为已验收**。用户选择自行复测，本轮不启动或操作客户端；新防御动画的 Blockbench 可视审查也尚未完成。

实际字段以 [common 示例](elder-bosses-common.example.toml) 与 [配置定义](../../src/main/java/com/tonywww/elder_bosses/platforms/config/PromisedConsortConfigValues.java) 为准。[早期设计草案](promised-consort-ranged-counter-draft.md)保留设计理由，但其 TOML 中的固定策略字段不都是现行接口。示例与两份开发配置已同步：仅迁移仍为旧默认14/10及技能最低距离14的字段，补齐缺项，保留其他自定义值；各自备份为 `.pre-ranged-followup-v2`，早期 `.pre-ranged-counter-v1` 备份也保留。迁移不是自动启动逻辑；其他安装中的已有TOML不会仅因源码默认值更新而改写。

### 远程资格与分类

`[promised_consort.ranged_counter]` 的资格始终采用“或”：同一玩家**长期远离**，或**在远处造成有效伤害**。资格记录按参战玩家隔离，用于针对当前目标的选招和段间调整预算；不改变伤害本身的类型。

| 字段 | 默认 | 含义 |
| --- | --- | --- |
| `enabled` | `true` | 整组远程反制开关 |
| `enter_distance` / `exit_distance` | `9` / `5` | 水平中心距离；进入远处与回到近身的阈值 |
| `far_dwell_ticks` | `100` | 距离至少9格时累计；大于5且小于9格时暂停，回到5格及以内清空远离计时 |
| `segment_adjustment_budget_multiplier` | `2` | 范围0至16；当前目标具备远程资格时，普通连招段间调整总预算的倍率，不改变步速 |
| `pursuit_selection_weight_multiplier` | `2` | 范围0至16；四种可用追击反制的额外权重倍率，与每技能倍率和路径可达率相乘 |
| `melee_suppression_distance` | `9` | 范围0至256；六组近身连斩开始降权的水平中心距离，含边界 |
| `melee_zero_weight_distance` | `16` | 范围0至256，且不得小于降权起点；六组连斩归零距离，含边界 |
| `distant_melee_weight_multiplier` | `0.1` | 范围0至1；两门槛之间的权重倍率，0直接禁选，不是固定10%的选中概率 |
| `pursuit_idle_multiplier` | `0.5` | 范围0至16；合格远程目标下，两招间额外空闲的倍率，结果向上取整 |
| `far_damage_window_ticks` / `far_damage_threshold` | `80` / `1` | 在远处造成的实际生命损失窗口与累计阈值 |
| `threat_window_ticks` | `80` | 远程威胁保留窗口；实际保留期还取各防御技能观察窗口的最大值 |
| `global_cooldown_ticks` | `60` | 反制变体或防御结束/取消后的公共冷却 |
| `defense_shared_cooldown_ticks` | `100` | 三防御共享冷却 |
| `max_consecutive_defenses` | `1` | 连续防御上限，完成非防御攻击后重置 |

选招时目标必须仍在进入距离以外；起手后版本、目标与阶段固定，不能靠来回跨阈值改变正在施放的技能。普通攻击仍可被选择，不会因资格成立立即打断当前动作。

新权重只影响 `spiral_assault`、`gravity_dive`、`lightspeed_dash`、`lightspeed_side_dash` 的合格反制候选；目标不合格、变体禁用或公共冷却未结束时，普通版权重不受此倍率影响。原有 `selector.ranged_weight_multiplier` 及其他情境加权保留。将新倍率设为1可取消对应额外强化；预算倍率0禁止合格远程目标的段间前移，权重倍率0使合格反制候选权重为0，均不改变其他技能。

两个倍率进入开战快照及NBT；已有战斗读取时只补缺失倍率为2，保留已保存距离和倍率，包括0，不强制迁移存档中的14/10。配置修改应通过重新开战或新实体验证。默认技能最低距离为9，后续自定义进入距离时应一并检查各技能的独立最低距离。

### 近身连斩降权与追击间隔

本次仅降权 `left_combo_cross`、`left_combo_bloodflame`、`right_combo_cross`、`right_combo_left_twin`、`right_combo_tempest`、`right_combo_earthheave` 六组。默认水平中心距离小于9格时倍率1，大于等于9且小于16格时倍率0.1，大于等于16格时倍率0。使用当前距离，不要求先累计远程伤害或远离计时，避免远程资格尚未成立时空放连斩。`ranged_counter.enabled=false` 会一并关闭此降权。四种追击冲刺、狮子斩/衍生、踏地及其他技能的原权重与分支规则不变；本次未添加狮子斩突进版本。

`pursuit_idle_multiplier` 根据当前目标的远程资格影响两招之间的组内额外等待，结果向上取整。旧版6至14 tick逐招空闲已由下方攻击组替代：默认组内0至2 tick，远程为0至1 tick；组后24至36 tick喘息不缩短。倍率1保留组内等待，0取消组内额外等待，但不能绕过冷却、阶段限制或强制恢复。此倍率自身不改变动画或段内追步。

四个新字段均进入开战快照；旧NBT只给缺项补默认9/16/0.1/0.5，已有自定义值与显式0保留，先前9/5资格门槛和两项追击倍率不重写。示例与两加载器开发配置已仅补缺项并校验其余解析值不变，备份后缀为 `.pre-ranged-selection-v3`。本轮专项覆盖默认/自定义/禁用、16种NBT缺项组合、实际选择器和控制器间隔、Boss开关组合及实际填充/边框顶点；双加载器编译通过，真实世界中的战斗节奏和开关观感仍待用户复测。

`[promised_consort.ranged_counter.classification]` 包含：

- `accept_owned_projectile_entity=true`：识别合法玩家归属的弹射物伤害。
- `damage_type_tags=["minecraft:is_projectile"]`：内置的远程标签入口。
- `additional_damage_type_ids`、`additional_damage_type_tags`：追加远程伤害类型或标签，默认空。
- `excluded_damage_type_ids`、`excluded_damage_type_tags`：额外排除名单，默认空。

固定排除标签 [`elder_bosses:non_ranged`](../../src/main/resources/data/elder_bosses/tags/damage_type/non_ranged.json) 默认 `values=[]`，始终优先于弹射物实体识别和所有包含名单。检查使用原始伤害类型，不使用路由后的Boss内部伤害类型。排除伤害不贡献远处伤害资格、威胁或蓄能，也不被本组技能减免、吸收或反射；原有抗性、免疫和参战规则仍生效。标签不影响长期远离分支。魔法伤害不会仅因属于魔法就被判为远程。

### 四种冲刺变体

配置路径为 `[promised_consort.skills.<技能ID>.ranged_counter]`，与普通版共享原技能ID和冷却组。前摇、释放、后摇三数组与原组件顺序一致；`attack_event_offsets` 是各释放段内的命中偏移，不允许增删伤害事件。

| 技能 | 首前摇 | 前进总上限 | 每tick上限 |
| --- | --- | --- | --- |
| `spiral_assault` | 22 tick | 24格 | 2格 |
| `gravity_dive` | 24 tick | 24格 | 3格 |
| `lightspeed_dash` | 28 tick | 30格 | 2.5格 |
| `lightspeed_side_dash` | 22 tick | 20格，另有8格横移 | 前进2.5格、横移2格 |

各变体均可设置 `enabled`、`selection_weight_multiplier`、`min_target_distance`、`max_target_distance`、`windup_ticks`、`active_ticks`、`recovery_ticks`、`attack_event_offsets`、`max_forward_distance`、`max_forward_per_tick`、`stop_distance`、`target_lock_lead_ticks`、`minimum_warning_ticks` 和 `turn_rate_degrees_per_tick`。侧突另有 `max_lateral_distance`、`max_lateral_per_tick`。

移动预算是最终格数，不乘普通技能的 `range_multiplier`；原有伤害数值、命中宽度和落地半径仍继承普通技能。路径在锁点前预测，锁定后不追踪；可达性影响选招权重。移动分步碰撞，途中阻挡或锁定前目标失效会取消本次动作，不让终点攻击隔空命中。长距离是上限，不保证每次走满。二阶段原有刀后圣光保留，延迟随本次阶段计算。

### 三种防御

| 技能ID | 组成阶段，前摇/释放/后摇 | 单技能冷却 | 核心行为 |
| --- | --- | --- | --- |
| `gravity_bulwark` | `10/36/18` | 180 tick | 有效段远程减伤80% |
| `gravity_reflection` | `8/16/20` | 220 tick | 有效段反射已适配的箭；半径4格 |
| `gravity_reprisal` | `12/30/0 > 10/1/0 > 2/6/26` | 300 tick | 蓄能、瞄准、回击；只有第一段释放期吸收 |

三者都位于各自技能小节，提供 `enabled`、`phases=[1,2]`、`weight`、`cooldown_ticks`、`hyper_armor_active=false`、`min_target_distance=9`、`max_target_distance=40`、`defense_arc_degrees=360`。单技能冷却与公共冷却都从防御结束或取消时起算。前后摇不防御，不中断其他攻击强行开启护盾。

- 壁障：`ranged_damage_reduction` 控制减伤比例；`melee_damage_multiplier=1` 保持近战伤害。`trigger.threat_window_ticks=60`、`minimum_attack_attempts=3` 与 `minimum_threat_damage=12` 构成“攻击次数或威胁量”的条件；`condition_weight_multiplier=1.5` 调整合格时的权重。
- 反镜：`intercept_radius`、`defense_arc_degrees` 控制拦截形状；`speed_multiplier=1`、`max_projectile_speed=3` 和 `remaining_lifetime_cap_ticks=60` 控制反射运动。`max_reflections_per_cast=8`、`max_reflections_per_tick=2`、`max_scanned_projectiles_per_tick=64` 限制预算。`trigger.scan_radius=24`、`incoming_prediction_ticks=20`、`minimum_incoming_projectiles=1`、`condition_weight_multiplier=2` 控制情境选择。`supported_entity_ids` 默认仅普通箭与光灵箭，`excluded_entity_ids` 默认空；填写任意模组实体不会自动产生适配器。
- 蓄返：阶段使用 `.components` 下的三个等长数组，固定三段；`.absorption.full_charge_damage=40`，`minimum_return_multiplier=1`、`maximum_return_multiplier=2`。`.counterattack` 下提供 `length=32`、`width=4`、`height=4`、`minimum_warning_ticks=10`，以及固定校验为1的 `max_hits_per_target`；`.counterattack.damage` 使用 `flat=4`、`attack_ratio=0.8`。`range_multiplier` 在技能根小节，只缩放回击长宽高。`trigger.threat_window_ticks=80`、`minimum_threat_damage=20`、`condition_weight_multiplier=2` 控制进入候选池的条件。近战倍率另由根小节 `melee_damage_multiplier=1` 控制。

蓄能使用抗性与来源倍率后、护甲及吸收生命前的合格伤害量 `A`，上限为 `C`。回击基础伤害 `B=flat+attack_ratio×BossAttack`，最终公式：

$$D=B\left[m_{\min}+(m_{\max}-m_{\min})\min\left(\frac{\max(A,0)}{C},1\right)\right]$$

默认吸收0/20/40点时分别回击1/1.5/2倍；这不是额外增加100%至200%。多人蓄能合计，但仅释放一次波。波前按回击段推进，单目标只命中一次，实际伤害仍经过玩家的防御规则。

### 固定策略与验证限制

以下是当前固定策略，不提供草案中的同名开关：水平距离、OR资格、当前目标仍须远离、近身重置计时、只在选招节点启用、施放时固定版本、保护合法参战来源、不兼容弹射物正常通过、不复制与不重复反射、反射瞄准当前发射者位置后不追踪、无蓄能仍回击基础伤害、满蓄不提前结束、打断清空蓄能、锁定前丢失目标则取消、回击受地形及场地约束。已失去合法玩家归属的箭当前不进入反射候选；不承诺草案中的“失去发射者后反向发射”分支。

新技能可通过既有单次技能指令测试，反制冲刺另支持显式 `ranged` 分支，注意 `ranged` 在阶段数字之前：

```text
/elderbosses test promised_consort gravity_bulwark 1
/elderbosses test promised_consort gravity_reflection 1
/elderbosses test promised_consort gravity_reprisal 2
/elderbosses test promised_consort spiral_assault ranged 1
/elderbosses test promised_consort gravity_dive ranged 1
/elderbosses test promised_consort lightspeed_dash ranged 2
/elderbosses test promised_consort lightspeed_side_dash ranged 2
```

反制冲刺测试默认在面前24格生成，普通技能和防御测试默认6格；阶段后可继续提供坐标。测试须选择已加载且有支撑的空地，二阶段专属技能仍必须填 `2`，禁用技能/禁用变体不能强行施放。该入口只为验收跳过远程资格等待，不替代正常AI触发测试；攻击仍会造成伤害，结束后清理测试实体。

新动画独立存于 [防御动画资源](../../src/main/resources/assets/elder_bosses/animations/entity/promised_consort_ranged.animation.json)，原43段动画保持不变；三种防御的连续视觉效果、反射箭实际伤害与多人/地形边界仍需入世界验收。离线测试使用局部注册表初始化，没有经过完整Forge客户端启动流程。最新结果与证据边界见 [实机与离线台账](../../models/promised_consort/runtime_validation.json) 的 `ranged_counter_v1_2026_09_17`。

## 连续攻击与喘息

本轮按参考重制采用2至4个完整技能为一组，狮子斩衍生计入母招，不额外消耗一个名额。冷却、目标合法性仍限制下一招，不能为了凑满组数强制施放冷却中的技能。

```toml
[promised_consort.selector.burst]
minimum_skills = 2
maximum_skills = 4
minimum_link_ticks = 0
maximum_link_ticks = 2
minimum_rest_ticks = 24
maximum_rest_ticks = 36
chain_recovery_ticks = 6
```

最小/最大招数允许2至16，链接等待允许0至200 tick，喘息允许1至1200 tick，组内末后摇下限允许1至200 tick，各最小值不得大于最大值。默认组内近身招式在最后释放段结束后保留至少6 tick恢复；只对六组左右连斩、十字斩和踏地生效，且只在本组后面还有技能、目标有效、无强制恢复时提前结束最后后摇。中间段、伤害窗口、分身和延迟区域不截短；每组最后一招保留完整后摇，再额外喘息24至36 tick。大招、防御、腾空招式不应用后摇提前结束。

狮子斩衍生可与母招连贯，其他立即反应分支只能在组内触发，不能绕过组后喘息。强制恢复优先于分支和后摇压缩，取消、硬直与切换脚本重置攻击组。攻击组剩余招数和等待会存档，恢复时不重抽组长或跳过喘息。旧开战快照缺少攻击组配置时补上述默认值，已有值保留；三份示例/开发配置仅补缺项，备份后缀 `.pre-reference-burst-v1`。

## 米凯拉之光升空

```toml
[promised_consort.skills.light_of_miquella]
flight_height = 12.0
flight_ascent_ticks = 16
flight_descent_ticks = 20
```

高度是实体脚底相对起飞点的实际位移，范围0至32格，不乘攻击范围倍率；升降时间范围1至1200 tick。默认16 tick内升至12格，主光释放时仍处高位，最后20 tick下降。当前模型小幅骨盆浮动保留，但不代替实体/碰撞盒位移。不新增无敌、不改变伤害与释放时间；上方有障碍时不强行穿越，极短技能总时长会限制可达高度以保留每tick最大1.25格的移动上限。

起飞点与原重力状态随动作存档。结束或取消解除悬停，未完成的落回交给正常重力，不能宣称所有地形都保证落在原点。旧技能快照缺少这些字段时使用12/16/20兼容默认；已保存的自定义字段保留。示例及两份开发配置各有 `.pre-holy-flight-v1` 备份，未改原攻击时序字段。

刀刃附魔、重力扭曲和触地碎屑沿用 `[skill_vfx]` 的客户端显示开关和距离预算；关闭Boss范围指示器不关闭这类动作特效。紫电与圣光贴附刀刃；局部重力扭曲先复制主场景颜色，再偏移采样背景，不仅是紫色叠层。当前仅支持正常主帧缓冲，遇到非主目标、MSAA、自定义视口或过大缓冲时跳过扭曲，保留其他特效；资源重载、离线和换维度清理缓冲。离屏GPU像素检查已通过，但Minecraft渲染管线、深度遮挡及第三方光影尚未实机验收。碎石取实际刀刃/扫掠与实体方块顶面的接触和材质，不按每次伤害虚构触地。

## 开场、狮子斩与重力陨石

绑定场地的Boss在神门前保持双刀触地待战，激活后进行60 tick举刀，再向合格参与者强制首发带紫电的狮子斩。仍使用 `lion_claw` 的原落地伤害，没有增加独立技能ID；该技能被禁用时不强制开场，未绑定实体和单招测试不走此流程。正常狮子斩及衍生增加斩地借力、实际腾空、空中锁点和落地判定；起手斩地只产生动作、声音和接触碎屑，不新增伤害。

```toml
[promised_consort.skills.gravity_meteor]
sword_range = 4.5
body_range = 4.5

[promised_consort.skills.gravity_meteor.sword_damage]
flat = 4.0
attack_ratio = 0.8

[promised_consort.skills.gravity_meteor.body_damage]
flat = 5.0
attack_ratio = 0.8
```

按已确认默认值，陨石起手增加一次 `4+0.8*BossAttack` 物理斩击；二阶段本体落地增加一次 `5+0.8*BossAttack` 物理斩击。两次基础范围默认4.5格，沿用已有近战范围扩展及技能范围倍率，不改原八块岩石的 `2+0.35*BossAttack` 伤害、可破坏生命和命中上限。三份配置仅补六个缺失叶字段，备份后缀 `.pre-reference-meteor-v1`；旧存档缺字段使用默认值，自定义值保留。

本体与地表材质岩石实际升空，岩石在悬浮中变为配置的蓄能方块，再按原事件时刻发射；悬浮时可被破坏、不能伤人，也不会因为提前被破坏而补生。取消或替换动作清除待发射岩石，不破坏地形。一阶段本体回到起点，二阶段锁定近处落点后斩落。碰撞或落点无支承会中断运动，不穿越障碍；极短狮子斩和无法容纳升空/落地的陨石配置走兼容路径，陨石末段恢复0或1 tick时不启用新序列。动作时钟保持原12个陨石组件，并给新增接触绑定精确姿态；游戏内位移、音效和视觉仍待用户验收。

## 普通光速突贯

2026-09-17修正 `lightspeed_dash` 普通版：本体在蓄光和前几道残影时保持原地，最后沿锁定直线贯穿，尾光使用同一路线的延迟圣光柱。没有新增技能ID或伤害公式，保留四次残影、本体一次、尾光一次和原组件时刻；已有远程反制变体使用独立路线，不受此修正替换。

路线在第一次残影前锁定，离开时刻为最后残影起始与本体命中前7 tick中较晚者，贯穿在原本体命中时刻完成。位移使用该技能基础 `range`，不额外叠加近战判定扩展；判定范围仍按原范围倍率处理。锁定后不换线，每0.25格检查路线，要求区块已加载、平坦有支承、无流体、未越过场地且不碰墙；不强载区块或改地形。自定义距离或时长导致峰值速度超过8格/tick，或距离超过256格时，在首次残影前取消；旧单阶段或无法形成贯穿窗口的配置保留兼容流程。

腿部本轮更新20段动画，地面招式联动骨盆换重心，腾空/踏地/冲刺分别加强抬膝和伸展；两种狮子斩保留上一批翻跃动作。没有修改伤害、时长或范围配置。动画与金发资源已同步两加载器，实机项目与5:15动作对应确认见[验收清单](../../models/promised_consort/REFERENCE_REWORK_TODO.md#本轮实机验收)。

## 十字跃袭连斩

新增独立二阶段技能 `cross_leap_combo`（十字跃袭连斩），不是替换 `promised_consort`（王者连舞）或 `lightspeed_dash`。用户已确认完整流程和以下项目默认值，数值不来自原作数据提取。

```toml
[promised_consort.skills.cross_leap_combo]
enabled = true
weight = 0.5
cooldown_ticks = 260
hyper_armor_active = true
range_multiplier = 1.45
leap_distance = 16.0
leap_height = 1.2
opening_range = 4.2
spin_range = 4.5
finisher_range = 5.0
windup_ticks = [27, 7, 6, 8, 28, 2, 2, 4]
active_ticks = [3, 3, 4, 4, 1, 1, 1, 1]
recovery_ticks = [4, 4, 4, 9, 1, 2, 2, 23]
```

伤害子节同属该技能，各含 `flat`、`attack_ratio`：`opening_damage=3/0.55`（2次交替斩）、`spin_damage=3/0.50`（2次双刀回旋）、`finisher_damage=6/0.95`（1次重斩）、`clone_damage=1/0.20`（2道分身）、`holy_ring_damage=2/0.35`（1次光环）。前五次为物理且支持瞬防，后续为圣属性来源。平跃本身不伤人，公共二阶段刀后回响照常保留。双刀同斩只有一次主体判定，但播放左右两次刀声。

默认共151 tick，12tick锁点并起跳、27tick落地接首刀，后续本体41/54/70/111，分身115/120，光环127。终结87至111tick实际升降，也采用 `leap_height`；不会只移动模型而把实体留在地面。`leap_distance`允许0至32格，高度0至4格，不乘攻击范围倍率；攻击基础范围允许0至256并沿用现有近战扩展。第1/5段前摇必须至少4tick，全部时序列表必须8项，释放每段至少1tick。

起跳前冻结落点、沿用约4格公共停靠距离，路径预检且移动中每0.25格检查碰撞，最大2格/tick；落点必须已加载、有无流体的实体顶面支承、在场地内且与起点高差不超过1格。配置距离过长或时序过短而无法满足速度上限时取消，不穿墙或强制加载。结束/取消恢复原重力；首刀/终结只有实际到达落点才触发。旧遭遇存档仅补缺失新技能，已有自定义值保留。三份配置各增加23缺项，备份后缀 `.pre-cross-leap-v1`。

新技能已编译并同步到两加载器，尚待用户实机验收：`/elderbosses test promised_consort cross_leap_combo 2`。默认6格生成点下平跃较短，水平视线、平坦空地可用 `... cross_leap_combo 2 ^ ^ ^14` 查看中距接近。详细验收项目见[任务清单](../../models/promised_consort/REFERENCE_REWORK_TODO.md#追加第11项十字跃袭连斩)。

## 拉塔恩追击距离

拉塔恩动作音效配置见下方“拉塔恩动作音效”；追击距离与音效播放速率互不影响。

```toml
[promised_consort.targeting]
max_segment_pursuit_distance = 3.0
```

该值是每个本体连段衔接期间的移动总上限，单位为格，范围0至16，默认3。设为0只关闭段间前移，仍可在锁定前转向；不会关闭普通寻路或技能自身的冲锋。每tick最多0.25格、下一段锁定点与约4格停靠距离仍然生效，因此不保证每段都走满配置距离，也不受 `range_multiplier` 额外放大。

远程反制启用且当前目标满足远程资格时，该基础预算再乘 `ranged_counter.segment_adjustment_budget_multiplier`，默认最多6格，普通近战目标仍为3格。普通连招也适用，不依赖动作的反制变体标记；不扩大技能自身冲刺预算。资格在段内变化时已消耗距离继续累计，保存恢复也不会重置预算；短衔接窗口仍可能无法走满6格。

此设置随开战快照保存，修改后应重新开战或生成新的测试实体。旧存档缺少该字段时补默认3格，已有字段的自定义值（包括0）保留。拉塔恩不再与其他实体发生物理推挤或阻挡，但保留被攻击的命中盒及地形碰撞，不使用零尺寸实体或全局穿墙。

## 拉塔恩动作音效

已将选定的六个 `+3 dB` OGG音色接入26个技能及4种远程变体，复用原有配置节，不新增common配置文件：

```toml
[promised_consort.nonverbal_audio]
enabled = true
volume = 1.0
pitch = 1.0
```

- `enabled` 控制新增的刀声、冲刺、踏地、圣光、重力防御及星陨音效。
- `volume` 是动作混音的统一倍率，默认1；0静音。每只刀的基础响度还按挥幅与上挑/下劈方向调整。
- `pitch` 是音效速率与音高的统一倍率，默认1；乘上逐刀系数后限制在0.5至2。不改变动画、技能tick、伤害窗口、冲刺移动速度或冷却。
- `intro_roar_tick`、`victory_roar_delay_ticks`、`hurt_cooldown_ticks` 仍为未实现人声的保留字段。本次不新增战吼、喘息或对白。

以上三个字段随既有开战快照保存；修改配置后重新开战或生成新的测试实体。关闭动作音效不会关闭 `[promised_consort.instant_guard]` 的原有瞬防提示，也不影响米凯拉字幕。

多段技能每次挥刀分别发声，双刀按左右刀各一次，同时挥动则同时发声，不人为延迟第二刀。当前本体共享刀轨窗口共48个接触、76只刀事件，分身另按其实际攻击事件播放。双刀的单刀音量按刀数分摊，仍有两次独立声音；空挥照常响，不按被命中玩家数重复。角度是当前动画中刀刃的转角和相对身体的升降方向，参数离线采样并绑定来源哈希，不是玩家看向Boss的角度。

辅助音效每Boss每tick每音色最多两次，刀声不受此上限或伤害冷却限制；盾面反馈另按每tick一次收敛。圣光区域只在真正激活时发一次，保存恢复不重复；旧存档已激活区域不补播起音。星陨主声由实际落地触发，内外两圈伤害不会各响一次；岩石过期或越界清理没有碰撞声。

普通动作音效空间传播上限48格，反镜24格，星陨64格；服务端固定范围与资源衰减配置一致，调音量不会放大广播范围。客户端仍可使用原版“敌对生物”音量调节。六种音色复用于全技能，不等于已制作每个高威胁技能的独立辨识音；实机混音、定位和瞬防可辨识度仍待用户入世界复测。

## 绝对 Tick

例如左起三连的默认三段为：

```toml
[promised_consort.skills.left_combo_cross]
windup_ticks = [11, 6, 11]
active_ticks = [2, 2, 3]
recovery_ticks = [1, 7, 28]
```

各段依次执行前摇、释放中、后摇，当前 `rhythm_v1` 接触起点为 `11/20/40`，总长 `71` tick。动画仍映射到原制作曲线的 `11/21/44` 接触姿势，不再要求实际 tick 等于制作 tick。只修改第二项就只改变第二个组成段及其后续事件的位置，动画、预警和执行器按同一阶段映射。组成段的前摇和后摇允许为 `0` 表示连续衔接，释放中至少 `1` tick；总长不得超过协议 tick 上限。无效段数或空释放段会报错，不会静默变成少打/多打一次。

### 拉塔恩组成段

全部数组位于 `[promised_consort.skills.<技能>]`。以下顺序同时定义配置数组与运行事件的对应关系，默认数量保留项目既有合同。

| 技能 | 组成段顺序 |
| --- | --- |
| `left_combo_cross` | 左切、右切、交叉压切 |
| `right_combo_cross` | 右切、双剑合刃展开 |
| `right_combo_left_twin` | 右下劈、左扫出、同左剑反切 |
| `right_combo_tempest` | 右、左、右开路刀、第一旋扫、第二旋扫 |
| `right_combo_earthheave` | 三次开路刀、入地、拔剑掀地 |
| `left_combo_bloodflame` | 左刺、同左剑横划、延迟爆燃 |
| `gravity_dive` | 剑击、重力冲击 |
| `cross_slash` | 双剑压切、前向地面冲击 |
| `spiral_assault` | 螺旋推进、落斩 |
| `starcaller_cry` | 拉扯、下砸与同时尖刺/二阶段交叉分身 |
| `gravity_meteor` | 八枚岩块逐次发射、四次二阶段分身；一阶段最后四段无分身伤害 |
| `light_of_miquella` | 主爆、八处余辉 |
| `lightspeed_slash` | 三个先行分身、本体落斩 |
| `lightspeed_side_dash` | 三个先行分身、本体横扫 |
| `lightspeed_dash` | 四个先行分身、本体贯穿、延迟光路 |
| `promised_consort` | 两刀、两旋扫、跃斩、两分身回切、光环 |
| `enhanced_earthheave` | 入地与裂地、四列圣光 |
| `consort_meteor` | 升空、离场等待、锁点后再入、落地核心/外环、余波 |
| `stomp`、`ring_of_light` | 单段整数前摇/释放中/后摇 |
| `lion_claw` | 本击使用三项整数；追击在同小节用 `double_windup_ticks`、`double_active_ticks`、`double_recovery_ticks` 独立设置 |

同时发生的复合效果仍放在同一组成段，不因配置拆分重复伤害。岩块寿命和可击碎追踪规则、圣光/血焰已生成区域的清理策略保持。螺旋和光速移动只累计相关释放段，不把阶段之间的前摇/后摇计入移动步数。

### 马莲尼亚组成段

基础单段技能和既有双连斩、上挑连段、飞行连斩仍使用技能小节的三项整数或数组。以下六招使用 `[malenia.skills.<技能>.components]` 内的三组数组，替代旧的整招三项时间及固定内部间隔，不同时保留两套生效时序。

| 技能 | 组成段顺序 |
| --- | --- |
| `rapid_slashes` | 三次快速刀、延迟终结刀 |
| `grab_impale` | 抓取窗口、贯穿、投掷；成功抓住后按后两段时刻执行 |
| `waterfowl_dance` | 四次突进刀群，各自前摇、释放窗口、后摇 |
| `scarlet_aeonia` | 升空、锁点悬停、俯冲、落地、绽放；腐败区域寿命仍独立配置 |
| `scarlet_plunge` | 刀击、腐败爆发 |
| `scarlet_phantoms` | 五个先行分身、本体俯冲 |

```toml
[malenia.skills.waterfowl_dance.components]
windup_ticks = [32, 4, 4, 4]
active_ticks = [14, 12, 12, 18]
recovery_ticks = [0, 0, 0, 42]
```

### 默认值状态

2026-09-18最新六项反馈：重力陨石 `windup_ticks` 首项默认50，替代86；仅等于旧默认的三份本地配置已迁移，其余自定义值保留。默认流程为12tick砍地、3tick停顿、15至31tick升空、持续朝向本次锁定目标悬浮瞄准、50tick首发投石，后续投石节拍和伤害次数不变。狮子斩改为瞄准来袭方向上玩家后方1.5格，正常飞行上限2格/tick，仍受最大距离和安全落点限制。跃击已完整独立重做，保留原ID、79制作tick和35/43接触。

圣光改为无图案光柱和较淡外晕，不修改方块光照或伤害范围。血焰改离散火粒与少量烟；踏地改真实地面材质碎粒，连续喷发8tick，默认18颗/tick总预算下理论最多144颗，实际受有效地面和共享预算限制。粒子表现仍遵守 `skill_vfx` 开关与粒子预算，玩法预警保持独立；不保留截图或旧模型副本。

2026-09-18追加反馈：`spiral_assault` 的显示名改为“跃进”，旧ID和配置节保持兼容；`spin_damage` 现在用于第一次落地斩击，`slam_damage` 为后续重斩。普通跃进最多16格、低弧高约2格；远程版本沿用 `ranged_counter.max_forward_distance`，同时受2格/tick飞行预算约束。起跳冻结地面落点，落地才执行攻击，保留两次命中及原伤害值，不再旋转穿行。狮子斩目标弧高增至6格，短时序时距离与高度按速度预算收敛。

六类左右连斩、王者连舞和十字跃袭的本体刀击在现有配置范围基础上再扩大15%，预警同步；非刀击血焰、分身和圣光余波不跟随。重力陨石的上升对应制作28至45tick（默认运行27至43tick），掀石与投石判定时刻不变。陨石渲染、碰撞体和附着特效统一放大35%；真实触地碎石大小放大1.6倍，粒子数量预算保留。以上是本次用户要求的项目调整，不是原游戏精确数值。

2026-09-16 用户要求加快释放、压缩停顿并允许同步改变真实时序。本轮 `rhythm_v1` 调整拉塔恩22招的默认阶段，完整新旧总长见[动作记录](../../models/promised_consort/MOTION_PLAN.md)。三份配置各更新52个时间字段，备份为 `.pre-rhythm-v1`；按整招原值比对，其他数值不变。马莲尼亚默认值本轮未改。22招完成数值检查和Blockbench播放，尚未获得本轮游戏内认可；下段批次认可属于更早版本，不能继承。不要把审查样片导出为正式动画，否则会与运行阶段映射叠加缩时。

默认值的目标是与参考原游戏的可见动作节奏一致，不是统一加速或统一变慢。拉塔恩此前六招保留，剩余16招已批量接入全身版本和绝对阶段表，用户已确认本批全部16招的Forge动作、节奏与可见同步可保留；其中8招有本次日志，另8招经澄清后有明确用户游戏内认可但无匹配日志。这不等于全部默认节奏已完成连续原帧校准、每招所有阶段或NeoForge实机验证。左起三连Forge历史认可、掀地起手一致性专项确认及十字斩Forge双阶段认可分别保留，详见 [动作记录](../../models/promised_consort/MOTION_PLAN.md)。当前16招完整时序在该记录的批次表与 [制作合同](../../models/promised_consort/scripts/motion_batch.js) 中；两套开发配置和示例仅迁移仍等于旧默认的时间字段，并保存 `.pre-motion-batch-v13` 备份，非时间设置未变。

全部组成段的数量和事件用途保持，前摇、释放中、后摇继续独立可配。完整连续参考视频对照与未覆盖的实机工作流仍待核实，不宣称默认值已全部精确匹配原作。30 fps 视频只能确认可见动作/特效的时间窗口，转换到20 TPS会有tick量化，不能据亮帧推断原作内部判定。当前星陨总长为243 tick，离场43、锁点163、警告约179、落地188、余波190；警告由再入段映射，制作侧仍为202 tick。实际落地前仍免伤，伤害、21.6/31.2/36格范围与事件次数保持。

### 迁移

新配置规范移除了百分比字段；旧存档内的倍率只兼容读取，不再乘除时间。不要仅删除字段后又手工把 tick 除以旧倍率。示例与开发配置有 `.pre-absolute-ticks`、`.pre-component-ticks`、`.pre-malenia-component-ticks` 备份。迁移使用 TOML 解析器，旧默认时序可自动转成组件表；自定义时序不会被静默覆盖，需按组成段顺序核对。

新战斗读取新配置。旧的已保存战斗/正在进行动作不代表新默认值验收，测试应新开战斗；马莲尼亚新快照格式保存具体组件表，旧快照仍支持读取。配置重载不能改变固定组成段数量或伤害类型。

`promised_consort.skills.consort_meteor.range_multiplier` 的新默认值为 `2.40`，基础 `core_radius = 9`、`outer_radius = 13` 不变。实际核心半径为 `9 × 2.40 = 21.6` 格，外环至 `13 × 2.40 = 31.2` 格，余波最外缘为 `(13 + 2) × 2.40 = 36` 格。这是用户最终确认的游戏内半径，替代此前“三倍”口径；仍可用上述配置调整。逻辑场地保持 40 格，锁点按完整余波半径约束，默认距场心最多 4 格。修改仅适用于新战斗，已保存的战斗快照仍使用原值；不要通过修改进行中的 NBT 来冒充新配置验收。

`malenia.debug.state_output` 默认关闭。开启后，每秒向 Malenia `follow_range` 范围内的玩家发送一条 action bar 状态，包含阶段、状态及持续 tick、当前技能及阶段、目标、阶段生命、硬直值和剩余回血预算。该开关实时读取，不写入战斗快照或实体 NBT。

## 招式广播调试

两位 Boss 各自提供 `debug.action_broadcast`，默认 `false`。在现有对应小节中将它设为 `true` 即可启用，不要重复声明同名 TOML 小节：

```toml
[malenia.debug]
state_output = false
action_broadcast = true

[promised_consort.debug]
action_broadcast = true
```

开关由服务端实时读取，配置重载后在下一次动作事件生效，不需要新开战斗；多人服务器需修改服务器配置。每次开始前摇、整招正常结束、取消或替换时，向 Boss 所在维度的所有在线玩家发送一组 `[Boss Debug]` 系统聊天消息，不受参与名单或距离限制，也不跨维度。不会每 tick 广播，也不会为每个伤害段重复发送整招开始消息。

- 第一行包含 Boss 名称与实体 ID、开始/结束类别、本地化招式名和配置 ID、动作序号 `seq`、已执行/总 tick、当前子段和时间轴阶段。
- 第二行包含战斗阶段/状态、状态持续 tick、生命、硬直、攻击力、动作目标与当前距离、Boss 坐标、范围倍率、各段前摇/释放中/后摇的绝对 tick、动作种子及世界 tick。
- 阶段时长直接来自配置，多个组成段用 ` > ` 分隔，不再显示施法倍率；结束消息的阶段取最后一个有效时间轴位置，整招已执行 tick 仍显示真实结束值。
- Malenia 附加显示 `heal_budget`；拉塔恩附加显示 `hazards` 与 `meteor_landed`。整招结束不意味着已生成的延迟危险区已结束，`hazards` 可帮助辨别这一点。
- 取消消息表示动作运行器被取消，不保证区分硬直、死亡、重置等每一种上层原因。读取或保存导致的实际取消也会记录；开关中途启用或恢复中的动作不补发过去的开始消息。
- `seq` 仅在单个 Boss 的动作运行器内配对，排查多只 Boss 时同时看实体 ID。该功能只读状态，不改变伤害、选招、冷却或现有 action bar 调试。

离线回归：先编译 Forge，再运行 `node models/promised_consort/tests/run_attack_plan_check.js ActionDebugCheck.java`，覆盖 37 个招式的开始/完成/取消去重、Malenia 替换顺序、星陨恢复和两个开关独立性。客户端验收仍需实际进入世界，不能只看主菜单。

## 单次技能测试指令

Forge 与 NeoForge 均注册管理员指令，要求权限等级 2、同维度内存活的非旁观玩家。目前支持拉塔恩的25个技能ID，包括三种远程防御；技能名可用Tab补全。下列为普通测试语法，四种冲刺的 `ranged` 分支见上方远程反制说明：

```text
/elderbosses test promised_consort <技能ID> [阶段1或2] [x y z]
```

省略阶段时使用一阶段；二阶段专属招式必须填 `2`，禁用技能不能测试。默认在玩家水平朝向前方 6 格、同脚底高度生成；可在阶段之后使用绝对坐标、`~` 相对坐标或 `^` 局部坐标指定生成位置。位置须已加载、位于世界边界内、无碰撞且脚下有支撑，建议选择平坦空地。测试实体始终朝向并以执行者为目标，创造模式可观察动作，但不用于证明伤害正常。

```text
/elderbosses test promised_consort right_combo_cross 1
/elderbosses test promised_consort right_combo_left_twin 1
/elderbosses test promised_consort right_combo_tempest 2
/elderbosses test promised_consort right_combo_earthheave 2
/elderbosses test promised_consort consort_meteor 2 ~ ~ ~6
```

每次新建临时 Boss，并读取当前服务端战斗与技能配置快照；不修改 TOML、旧战斗或已有实体。生成后等待 20 tick，让客户端加载模型，再按所选阶段执行一次完整动作，包括全部组成段及后摇；不进入随机选招、狮子斩自动追击或血量触发脚本。`lion_claw_double` 可单独指定，测试本击不会自动附带追击。

**攻击仍按正常配置造成伤害，范围与命中次数不改动。** 准备期击打不会触发普通开场；执行中仍可能受击或取消。动作结束、取消、测试实体死亡、观察者死亡/断线/切旁观/换维度/离开逻辑场地，或测试实体卸载时，清除临时 Boss 及其拥有的分身、岩块、持续危险区与指示器，不走掉落奖励流程。长寿命弹体和持续区域会在整招结束时截断，因此此指令不能替代持续效果完整寿命测试。测试 Boss 不写入存档，普通 Boss 不启用此分支。

中途可只清除带测试标签的实体，不要用无筛选的 Boss 类型批量清除：

```text
/kill @e[type=elder_bosses:promised_consort,tag=elder_bosses_skill_test]
```

开始与完成/取消提示不依赖 `debug.action_broadcast`；需要完整阶段日志时保持该开关开启。现有 [调试回归](../../models/promised_consort/tests/ActionDebugCheck.java)覆盖命令权限、25个技能路径、4种反制变体、阶段与坐标解析、补全及非玩家错误；实际生成、播放和清理必须入世界核验。

2026-09-15 Forge 实机已确认掀地一、二阶段生成、单次施放和自动清理，二阶段米凯拉可见；其他技能与中途取消路径、NeoForge 实机未逐项验收。用户同时指出起手挥砍与 Blockbench 不同，已定位到动画时间键序列化顺序问题，见 [动作记录](../../models/promised_consort/MOTION_PLAN.md)；指令可用不等于动作已认可。

## 约定之王配置合同

约定之王默认按 1-4 名原版终局玩家平衡，`promised_consort.general.max_active_players` 默认 4、合法范围 1-16。人数超过 4 时继续使用 `1 + health_per_extra_player × (N - 1)` 线性外推；AttributeFix 缺失时允许原版属性系统静默夹断超限生命。参与名单按本场累计唯一 UUID 计数，席位不因死亡、断线、越界或换维度而释放，最大生命只增不减。

`[promised_consort.encounter]` 使用枚举字符串和独立数值表达互斥策略。默认由场内玩家或可解析到该玩家的归属来源首次攻击唤醒；唤醒攻击与玩家首次登记攻击只登记、不造成伤害。Boss 首次命中未登记玩家时先批量登记本次命中的所有合格玩家、增加最大生命和当前生命差额，再结算伤害。名单已满后，额外玩家仍可成为目标并受到伤害，但不能登记或伤害 Boss。创造玩家默认不能登记、不能伤害 Boss 且不进入目标池；旁观玩家始终排除。

死亡、断线和越出 40 格逻辑边界默认使参与者永久退出本场；换维度者可在 100 tick 脱战宽限内返回。场内没有有效参与者时，Boss 完成当前动作后冻结；禁止新玩家在宽限期接力，合法返回者恢复战斗并清空全部技能冷却。宽限结束后 Boss 回冻结场心、回满生命并重置为 `DORMANT`。服务器重启默认恢复 NBT 战斗状态，但停服断线不获豁免；区块卸载默认重置，和平难度允许正常战斗，多场重叠默认允许。

`[promised_consort.incoming_damage]` 控制攻击者资格，默认只接受逻辑边界内玩家及其归属来源；管理员强制死亡来源可以绕过。具体免疫伤害类型不在 TOML 重复配置，只由数据包标签 `elder_bosses:promised_consort_immune` 决定。默认标签包含摔落、飞行撞墙、窒息、实体挤压、世界边界、溺水、仙人掌、甜浆果、钟乳石、火焰、熔岩、热地面、普通爆炸和玩家爆炸；数据包可以 `replace=false` 追加。Boss 技能可波及所有 `LivingEntity`，但 `elder_bosses:promised_consort_attack_immune` 中的实体类型绝对免疫；默认只包含约定之王本体。非玩家实体不会进入主目标选择器。

流血触发伤害通过标签进入物理通道并乘 0.50，霜冻触发伤害进入魔法通道并乘 0.50；睡眠标签伤害乘 0，中毒和凋零的实际生命伤害乘 0.35。本轮不实现流血、霜冻或睡眠积累系统，只处理外部伤害来源标签。

一阶段默认仍在65%血量转阶段；实际阈值为 `max(5%, phase_two_health_ratio)`，5%是最低保护线，不把默认65%改成5%。活跃一阶段的生命值写入、每tick检查及死亡兜底都会截断过量扣血、保留正生命并请求转阶段；硬直状态也不能绕过，旧 `phase_transition.damage_gate` 开关不能关闭这项防跳阶段保护。普通扣血在触发后免疫，按既有当前攻击结束/强制恢复流程进入转场，不必等到死亡。管理员强制死亡/清除和单招测试保留原行为。首次25%大荒星陨的可配置门控不变：默认截断跨阈值伤害并丢弃溢出，进入脚本前锁血免伤；`METEOR_SCRIPT` 实际落地前始终免疫普通伤害且全程不积累硬直，缩短配置免伤窗口不能提前解除保护。未加速时第121 tick先落地再结算；落地后继续遵守配置窗口。首次星陨完成后，3600 tick冷却结束会在当前 `ACTIVE` 结束后自动再次排队，不进入普通随机池。

2026-09-18斩击追加反馈：重力陨石开场采用与模型侧向刀路一致的侧方伤害扇区和预警；只隐藏空中石块 `rock_flight*` 与分身 `clone_meteor_*` 范围提示，不删除伤害、石块/分身表现或最终本体落地预警。跃进起跳提前至制作7tick，普通与远程时钟同步，35/43两次接触及79tick总长不变；跃进和重力劈跃落地前双刀交叉，落刀扩大并压低身体。强化掀地、光速斩本体和分身、跃进、重力劈跃、唤星终斩改为蓄势后的加速出刀，不改变原命中时刻。

2026-09-18全挥刀追加：全部48个本体挥刀窗口、6类分身和3类独立防御拨刀释放均采用蓄势后加速，上一轮已加速窗口不重复叠加。接触、总时长、伤害、配置及实体位移不变；不是缩短整个技能冷却或按固定倍率播放全段。重力跃斩 `gravity_dive` 在制作12至35tick改为直立绕竖直轴横转一圈，35tick转正交叉双刀、38tick落刀，移除原空翻。

2026-09-18最新空中反馈覆盖上段的跃斩姿态和位移描述：重力跃斩现在从制作12至38tick持续横转4圈，最后一圈直接落地收势，不再单独十字蓄力。普通/远程版都执行冻结落点的真实飞行，实际到达且重新确认地面支撑后才结算原剑击/冲击，不新增伤害事件。正常最多16格、远程沿用最大距离配置，弧高至多3.5格，仍受每tick2格、碰撞和场地约束。跃进改为起跳立即前扑，至少6格意图（速度预算可收敛）、目标前1.5格停靠，弧高至多水平距离的16%及原2格上限。两种狮子斩改为斩地后前空翻，制作10/8tick分别与实际离地对齐，32/26tick落地接触不变。

拉塔恩所有攻击统一绕过 Minecraft 受击后的 `invulnerableTime` 伤害冷却，包含剑击、圣光、血焰、岩块和脚本冲击；各攻击段仍遵守原来的命中次数上限，不会变成每 tick 重复伤害。盾牌、瞬防、创造模式和明确的无敌状态保持原有规则，调用完成后恢复受击冷却，不给其他攻击者制造额外窗口。

多段技能的子命中顺序由 Java 固定，`windup_ticks`、`active_ticks` 和 `recovery_ticks` 直接构成最终时间轴。可瞬防段若配置后的前摇短于提示提前量，该段自动取消瞬防资格。所有动作与状态 tick 从 0 开始。

`[promised_consort.visuals]` 只保留重力岩块模型与视觉分身策略并随战斗状态同步。旧的服务端占位粒子已经移除；两位 Boss 的全部技能由客户端动作快照驱动剑气、斩击、轨迹、孢子、腐败花环、重力涡流、圣光柱和冲击环，并由 `[skill_vfx]` 控制预算。`[indicators]` 仍只控制客户端本地通用指示器透明度、距离和几何预算。

`[promised_consort.rewards]` 默认掉落 1 个神与王的追忆、4-8 个神门残片和 500 经验，且不受抢夺影响。年轻狮子大剑与渐隐光冠当前只是无属性普通物品占位，不在 TOML 暴露无效装备数值；四件物品全部进入 Elder Bosses 创造模式标签页。

Malenia 的下列行为是程序不变量，不在 TOML 中重复开放：战斗必定在开战时生成快照；多人不会提高单次招式伤害，伤害倍率固定为 1.0；物理通道受护甲影响、魔法通道绕过护甲，由 `DamageChannel` 与伤害类型标签固定；硬直只按服务端确认的实际生命损失累计，同一命中 ID 的伤害分量先聚合，且只接受玩家及归属于玩家的来源；第二阶段开场生命比例只读取 `malenia.general.phase_two_start_ratio`；瞬间防御提示音固定为已注册的 `elder_bosses:malenia.instant_guard_cue`；净化物品固定为已注册的 `elder_bosses:golden_needle`；Malenia 自身的异常积累倍率、睡眠失衡和癫狂免疫使用固定规则。踢击与非玩家目标等仍需配置或设计裁决的项目保持原配置语义。

`malenia.performance.max_rot_zones` 是服务端权威的玩法上限，不是纯视觉性能选项。它限制每个 Malenia 实例同时活动的腐败区域数量；达到上限时创建新区域会移除最早的活动区域，设为 `0` 则不再创建腐败区域，因此会直接影响区域伤害与腐败积累。该值在开战时进入战斗快照，重载只影响下一场战斗。

猩红腐败适用于所有 `LivingEntity`。每个实体的积累容量只读取独立属性 `elder_bosses:scarlet_rot_capacity`，默认值为 100；该属性不属于 TOML，不写入战斗快照，也不随最大生命、当前生命、护甲、攻击力或其他属性缩放。`[malenia.scarlet_rot]` 只配置积累衰减、触发持续时间、伤害、治疗削弱、移动减速和净化规则。

Malenia 的奉献义手刀与无垢金翼盔使用物品注册代码中的固定占位数值，不属于 common 配置，也不参与热重载。后续平衡这些占位值需要修改注册代码并发布新版本。Promised Consort 的配置树不受此规则变更影响。

Boss 建筑不属于 TOML 配置。当前阶段不提供或加载建筑 NBT；建筑几何、方块、材料、空气清理区、片段偏移和锚点设计仍完整保留在竞技场文档中，等待后续资产。约定之王仍运行 40 格逻辑圆盘：首次开战位置和朝向冻结为场心与局部轴，严格使用开战点 Y；入场与回归使用文档相对偏移，阻挡时回退场心安全表面。默认关闭方块破坏；管理员显式开启后只依赖 `boss_breakable`/`arena_protected` 标签，当前没有快照恢复，也不输出警告。所有受控动作可以最多穿墙 24 tick，失败时回最近合法路径点，再回冻结场心。资产接入后，相同资源 ID 的数据包结构可以覆盖内置模板，但不能通过配置改写建筑内容。

GeckoLib、AttributeFix、实体、物品、创造栏、技能列表、状态图和资源类型属于构建期或注册期合同，不由 TOML 动态添加或移除。当前计划固定使用 GeckoLib Forge 4.8.4 / NeoForge 4.9.2，以及 AttributeFix Forge 21.0.5 / NeoForge 21.1.3；两者均加入开发构建依赖但不写入模组 metadata。缺少 GeckoLib 时允许类加载失败，缺少 AttributeFix 时允许原版静默夹断属性上限。

## 文档文件

[elder-bosses-common.example.toml](elder-bosses-common.example.toml) 是唯一完整样例，依次包含 `[indicators]`、`[malenia.*]` 和 `[promised_consort.*]`。`docs/config/` 不保留第二份 TOML，避免片段被误认为可单独加载的配置。

## 重载规则

- `indicators.*` 是客户端本地视觉值，配置重载后下一帧生效。
- `skill_vfx.*` 是客户端本地视觉值，配置重载后下一帧生效。
- 两位 Boss 的 `debug.action_broadcast` 及 Malenia 的 `debug.state_output` 实时读取，不写入本场战斗快照。
- Boss 战斗数值只应用于下一场战斗；进行中的实例继续使用开战快照。
- 约定之王的参与者、生命周期、来源资格、阈值门控、危险区、视觉档位和奖励策略均随下一场战斗快照生效。
- 伤害类型免疫和技能攻击免疫实体由数据包标签控制，不由 TOML 重载覆盖。
- Malenia 的固定注册 ID、伤害通道护甲规则、硬直输入规则、固定异常规则、腐败容量属性、多人单次伤害倍率和装备注册期占位值不参与配置重载。
- 建筑资产接入后，数据包结构重载只影响下一次新建竞技场，不重铺或修改已经放置的建筑。
- 配置重载不能增加技能、改变技能段、改变指示器形状映射或注入脚本。
- 非法值由配置规范修正并记录日志；`indicators.opacity` 限制在 0.10-1.00。