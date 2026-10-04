# Common 配置结构

本项目只注册一个 common 配置文件：

`config/elder_bosses-common.toml`

Forge 1.20.1 使用 `ForgeConfigSpec`，NeoForge 1.21.1 使用对应的 `ModConfigSpec`，两端都以 `ModConfig.Type.COMMON` 注册相同语义的配置树。不得再注册 `malenia-server.toml`、`radahn-server.toml`、独立 client 配置或第二个 common 配置。

## Configured界面与中英说明（2026-10-01）

客户端安装官方 [Configured](https://www.curseforge.com/minecraft/mc-mods/configured) 后，在主菜单的“模组 / Mods”中选择 **Elder Bosses**，打开“配置 / Config”并选择 common 配置。界面标题和悬浮说明跟随游戏语言：简体中文使用中文，英语使用英文。直接编辑TOML时，同一文件同时显示中文与英文注释。

| 游戏版本 | 加载器 | Configured版本 | 官方文件 |
| --- | --- | --- | --- |
| 1.20.1 | Forge | 2.2.3 | [configured-forge-1.20.1-2.2.3.jar](https://www.curseforge.com/minecraft/mc-mods/configured/files/5180900) |
| 1.21.1 | NeoForge | 2.6.3 | [configured-neoforge-1.21.1-2.6.3.jar](https://www.curseforge.com/minecraft/mc-mods/configured/files/7276577) |

默认build仅生成本项目的模组JAR。需要配置界面时，客户端自行安装上表中的官方Configured JAR。Configured是可选的客户端工具，专用服务器使用本项目主配置即可。原生配置规范直接提供翻译信息，无需Framework。

### 阅读方式

- 当前4416个可编辑项、418个分组。中英文标题与说明已缩短，单位保留在字段标题中。
- 当前默认数值采用本次run主配置，debug默认关闭。新增的命中免疫间隔默认10tick，米凯拉之光默认无敌；配置示例与原生规范保持一致。
- 先调整基础属性、参战与恢复、地面指示器、音效、特效外观等常用分组。拉塔恩`skills.hit_detection`选择命中模式，`skills.grab`控制重复魅惑秒杀。
- 拉塔恩`skills.entries.技能代号`控制选招，例如`skills.entries.swing_combo`和`skills.entries.consort_meteor`。`animations.a编号`、`attacks.a编号`、`projectiles.a编号`、`ground_areas.a编号_e序号`显示项目内部代号。
- 时间标明tick或秒，距离标明格，角度标明度，倍率1表示原值；原BulletParam时间字段用秒，动画与战斗窗口使用游戏tick（20tick＝1秒）。
- 装备与基础属性需要重启，客户端设置重载生效，战斗设置在下一场生效。编辑界面保存后，当前已开始的战斗仍使用其开战快照。

完整双语默认值见 [主配置示例](elder-bosses-common.example.toml)。仍只注册`config/elder_bosses-common.toml`。32项原复合值展开为标量子项，以便Configured编辑；默认战斗数值和原判定约束保留。验证和构建不修改玩家的游戏配置或存档。

### 已移除的入口

固定的物品和方块标签、提示音资源、陨石方块、未使用的预留项、旧选择器参数、内部状态标记以及与动画无关的空参数不再写入主配置。保留伤害、时间、范围、冷却、位移、音量与特效等实际生效数值。当前规范与示例均按新结构生成，不提供旧字段别名或迁移；开发验证不改写游戏配置。

### 公式与距离档位

伤害、回血公式使用独立的`flat`和`attack_ratio`字段；多个公式使用固定顺序的`stage1`、`stage2`等分组。距离分档使用`distance_bands.stage1`至`stage4`，每档有`max_distance`和`multiplier`，最后一档距离为`-1`表示覆盖剩余距离。当前版本不添加旧列表字段或配置迁移。

```toml
[malenia.skills.single_slash.damage]
flat = 1.0
attack_ratio = 0.45

[promised_consort.stagger.distance_bands.stage1]
max_distance = 4.0
multiplier = 1.0
```

上例展示字段格式，技能的准确默认数值以完整示例为准。Configured中这些值均可直接编辑，运行时仍生成原来的公式和距离分档对象。

### 维护与最小检查

展示目录为`assets/elder_bosses/config/presentation.json`，只包含打包的名称和说明，不是额外的用户配置文件。`LocalizedConfigBuilder`为原生Builder添加翻译键和双语注释；两个语言文件使用`config.elder_bosses.<完整配置路径>`与`.tooltip`键。

字段结构改动后，用`tools/shared/config/ConfigPresentationCheck.java --export`导出实际注册规范，更新`tools/shared/config/registered_defaults.json`，再运行`python tools/shared/config/build_presentation.py`。它生成资源、两种语言和完整TOML示例。`--check`检查两个加载器的实际翻译键、注释、分组、默认值与范围，并在独立的`build/configured`目录输出验证文件，不启动游戏。

交付使用默认build生成的模组JAR，输出位于各版本的`build/libs`目录。

## 女武神弹反窗口（V15）

`[malenia.instant_guard]` 使用 `window_ticks = 8` 控制攻击前的举盾窗口；窗口延续至该攻击段结束。玩家在窗口内举盾即满足弹反条件，不再使用启动 tick、结束 tick 和重新准备间隔。

新增 `cue_effect_enabled = true` 控制红白光圈。关闭光圈不影响弹反判定或提示音，光圈也不依赖地面范围指示器及一般技能特效的开关。详细行为与当前位移默认值见 [V15 实现记录](../bosses/malenia-blade-of-miquella.md)。

## 配置树

2026-09-30：拉塔恩受伤改为Minecraft原版结算，移除其`damage_routing`、`resistance`、`source_multiplier`、`status`及专用入伤类型。新增`[promised_consort.targeting].ranged_damage_distance = 9.0`，按伤害来源玩家到Boss碰撞箱的水平距离补充远程判断；有效生命伤害可触发原作Shoot反应。当前机制与性能优化详见[原作技能配置](promised-consort-source-skills.md)。

项目规范见 [AGENTS.md](../../AGENTS.md)：不要求兼容任何旧配置或旧配置快照。字段、结构和默认值按当前版本直接调整，不增加旧字段别名、自动迁移或历史默认值回退。配置样例和测试同步采用当前设计。

两个 Boss 的 `damage_conversion_ratio` 默认均为1.0；当前run预设的 `capacity_health_ratio` 为玛莲妮亚0.5、拉塔恩0.125。`rapid_window_ticks = 240` 和 `rapid_fraction = 0.25` 控制快速失衡保护：若 12 秒内冲到架势上限的 25%，当次增量停在 25%／50%／75% 档位，后续根据近期伤害速率动态降低转化比例，并以约每 12 秒 25% 的速度继续积累。两个门槛分别可调；已开始的战斗使用开战时快照。

| 根节 | 读取侧 | 用途 |
| --- | --- | --- |
| `[equipment.*]` | 服务端权威，登录时同步客户端 | 四件饰品的全部加成、两件武器的伤害/攻速/耐久/附魔能力；重启游戏或服务器生效 |
| `[indicators]` | 客户端本地 | 技能指示器启用、透明度、透视边框倍率、距离和几何预算 |
| `[skill_vfx]` | 客户端本地 | 全技能粒子特效启用、每 Boss 每 tick 预算和渲染距离 |
| `[malenia.*]` | 服务端权威 | 腐败女神属性、技能、硬直、瞬防和台词数值；场地实现参数固定在代码中 |
| `[promised_consort.*]` | 服务端权威 | 约定之王的属性、参与者、生命周期、伤害、技能、硬直、表现预算；原作技能运行时已接入 |

`indicators.opacity` 和 `skill_vfx.*` 只改变本地渲染，不同步到服务端，也不能影响命中范围或时机。Boss 的基础最大生命、攻击力、移动速度、追踪距离和击退抗性在实体属性注册时读取 common 配置，作为该类型的默认值；修改这些默认值需要重启游戏或服务器。开战、阶段切换、读档和战斗重置不会用配置覆盖实体属性。KubeJS `EntityEvents.spawned` 等脚本可在生成时修改基础属性，之后的战斗使用实体当前的属性值。技能、机制和演出配置仍在开战时生成快照；客户端只接收所需的权威状态。

玛莲妮亚一阶段的生命池以开战时实体的实际最大生命为基准；二阶段按 `phase_two_health / phase_one_health` 保持配置的两阶段比例，并乘以同一个实际最大生命。两阶段的开始比例及多人生命加成仍按战斗机制配置计算。拉塔恩的 `health_per_extra_player` 作为独立的多人属性修饰作用于实际基础生命，重置时移除，不改写脚本设置的基础值。玛莲妮亚的霸体击退抗性也使用临时修饰，结束后恢复脚本设置的基础值。

所有技能的时间配置使用绝对游戏 tick，20 tick 为 1 秒；不再提供 `cast_speed_multiplier`，也不再对填写的 tick 做百分比缩放。`windup_ticks` 是前摇，`active_ticks` 是释放中，`recovery_ticks` 是后摇。玛莲妮亚单段填写整数，多段使用固定顺序的等长数组；拉塔恩改为逐TAE段配置，允许小数tick，段数量与原事件顺序固定。

`range_multiplier` 继续独立控制命中半径、长度、宽度、受控移动和对应预警，不改变时间。伤害公式、命中次数、冷却、回血及持续区域的独立存续配置不因这次机制变更而重设。释放阶段不等于每 tick 都新增一次伤害，同一个命中 ID 仍遵守去重与每目标上限。

## 装备与饰品属性

四件饰品的 6 个加成，以及奉献义手刀、年轻狮子大剑各自的 4 个武器数值，均可在 `config/elder_bosses-common.toml` 修改。默认配置如下：

```toml
[equipment.weapons.consecrated_prosthetic_blade]
attack_damage = 9.0
attack_speed = 1.6
durability = 2300
enchantability = 15

[equipment.weapons.young_lion_greatsword]
attack_damage = 12.0
attack_speed = 1.0
durability = 2400
enchantability = 15

[equipment.curios.golden_needle]
rot_capacity_bonus = 40.0

[equipment.curios.consecrated_prosthetic_blade]
attack_speed_bonus = 0.1

[equipment.curios.young_lion_greatsword]
attack_damage_bonus = 0.1
knockback_resistance_bonus = 0.1

[equipment.curios.circlet_of_fading_light]
armor_bonus = 2.0
luck_bonus = 1.0
```

- `attack_damage`、`attack_speed` 是主手持武器时的总值，已包含玩家基础的 1 伤害和 4 攻速；支持小数。
- 饰品的 `attack_damage_bonus`、`attack_speed_bonus` 是比例，`0.10` 表示 +10%，`0.25` 表示 +25%。`knockback_resistance_bonus = 0.10` 表示增加 10 个百分点的击退抗性。
- `rot_capacity_bonus`、`armor_bonus`、`luck_bonus` 是直接加值。饰品加成设为 `0` 可关闭对应效果，不改变可佩戴槽位或合成配方。
- `durability` 是最大耐久，作用于已存在和新制作的武器；已有损耗不会因为改配置而清零。`enchantability` 影响附魔台品质，设为 `0` 时不再提供附魔台选项，已有附魔保留。

允许范围：武器伤害 1–1,000,000，攻速 0.01–20，耐久 1–1,000,000，附魔能力 0–1,000；金针容量加成 0–1,000,000，攻速/攻击比例加成 0–10，击退抗性加成 0–1，光冠护甲 0–1,000，幸运 −1,024–1,024。超出范围由加载器校正并记录日志，缺失项自动补齐默认值；Minecraft 或其他模组仍可能限制最终属性值。

**修改后重启游戏或服务器。** 这组数值在启动配置加载完毕后生成快照，文件热重载不会在正在使用的武器或饰品上替换加成。多人游戏以服务端快照为准，登录时同步客户端的物品提示、耐久和饰品数值；断开连接恢复本地配置。单人内置服务器的数值不会被客户端快照覆盖。此同步不要求安装 Curios，武器本体属性也始终生效。

依旧只使用一个 common 配置文件。两种追忆和其他材料不获得饰品属性；翼盔目前保持原有固定值。槽位数量由 Curios 数据包定义。

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

## 拉塔恩原作技能

当前只运行重做后的原作Act01–21／30及其条件连段。旧26个项目组合和4个远程变体已移除运行、配置和命名测试入口。逐段绝对时间、伤害／判定／弹丸／效果数值、地形与锁点定义见 [当前技能配置规范](promised-consort-source-skills.md)。不注册旧技能字段或旧快照兼容分支。

指示器复用既有显示规范，近战预警与实际判定使用同一锁定轨迹。神门前高坡出生使用新结构的 `boss_spawn`，开场20010、转阶段20011及大荒星陨3021／3024使用原状态。

## 单次技能测试

```text
/elderbosses test promised_consort source_animation 3010 1
/elderbosses test promised_consort source_act 5 1
/elderbosses test promised_consort source_act 17 2
/elderbosses test promised_consort source_act 20 2
/kill @e[type=elder_bosses:promised_consort,tag=elder_bosses_skill_test]
```

`source_animation`无技能伤害；`source_act`执行原作入口和条件后续，测试实例60秒清理。阶段参数为1或2，可追加出生位置。旧按项目技能名的拉塔恩测试命令不再注册。

## 玛莲妮亚组成段

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

## 示例与重载

[elder-bosses-common.example.toml](elder-bosses-common.example.toml) 是完整当前样例；不自动修改用户实际配置或存档。

- `indicators.*`、`skill_vfx.*`、`boss_music.*` 为客户端本地显示值，重载后应用。
- Boss技能、参与者、生命周期、伤害机制在下一场战斗使用新快照；进行中的实例继续使用已保存快照。
- 基础实体属性与 `equipment.*` 修改后重启游戏或服务器；开战快照不覆盖脚本生成时设置的实际实体属性。
- 调试输出开关实时读取；数据包免疫标签按数据包重载规则生效。
- 结构模板只影响新生成竞技场，不重铺已有建筑或改写保存的绑定。
- 配置不增加TAE事件或分身槽次；非法值由加载器规范校正并记录日志。

## 约定之王战斗音乐

### 拉塔恩动作音效

`[promised_consort.nonverbal_audio]` 的 `enabled`、`volume`、`pitch` 控制六类旧项目原创动作音色，进入开战配置快照。当前触发接入原作TAE的声音、刀光、FXR事件和投射物出生／爆发；时间调整跟随逐段 `windup_ticks/active_ticks/recovery_ticks`。双刀各有动画声源，分身使用自己的位置和更轻的混音；同tick密集圣光共享音效预算，避免柱群叠加响度。音色和原事件绑定固定，音量、音高倍率仍使用此节。

`intro_roar_tick`、`victory_roar_delay_ticks`、`hurt_cooldown_ticks` 保留为未制作人声的预留字段，不影响动作音效。`instant_guard.*` 提示和下列 `boss_music.*` 音乐仍使用各自开关、音量及音高／渐变配置。

### 战斗音乐

`[boss_music]` 是唯一 common 配置内的客户端本地设置，不进入战斗快照：

```toml
[boss_music]
enabled = true
volume = 0.85
distance = 96.0
fade_ticks = 40
```

音量范围 0—1，另乘 Minecraft“音乐”和主音量滑块；距离范围 16—256 格，淡入淡出范围 1—200 tick。默认 40 tick 即 2 秒。关闭或静音会立即停止本模组音乐。修改不影响技能、伤害及服务器参加资格。

服务器发送的 HUD 受众资格决定音乐候选。开战播放无合唱循环版，转阶段切到合唱版；同时遇到多只 Boss 时保持当前有效对象，失效后才选择最近对象。脱战、击败、玩家死亡或离开范围时淡出，切换维度和断线立即清理。超过 120 个未暂停客户端 tick 没有状态更新时取消资格；音频资源重载后可重试播放。播放期间抑制其他 Music 类别声音，刀击、瞬防等其他类别保持原行为。

音源为 Matthew Pablo 的《Blackmoor Colossus》CC BY 3.0 循环版本，已规范化响度并加入 8 毫秒循环边界淡化，非原游戏录音改编。试听入口见 [BGM 试听页](../../tools/consort/audio/bgm/audition.html)，完整署名随资源打包。离线状态/渐变/资源检查 391 项通过，实机听感、暂停恢复和音乐冲突仍待用户验收。


### 拉塔恩地面范围与命中模式（2026-09-30）

`[promised_consort.skills.hit_detection].simple_ranges` 默认为 `true`，使用简单地面范围；设为 `false` 使用当前原姿态轨迹判定。两版均显示逐段匹配的地面预警。全部招式的地面范围、轨迹、弹体和投技捕获范围统一由 `promised_consort.skills.range_percent` 控制，100为当前基准；逐段角度、尺寸和技能速度为内部基准。接近与短暂退让距离继续配置。完整说明见[原作技能配置](promised-consort-source-skills.md)。当前拉塔恩技能子树为1820项，主配置合计2337项。


### 技能播报与数据包掉落（2026-10-01）

`[malenia.debug]` 保留 `state_output` 和 `action_broadcast`；`[promised_consort.debug]` 保留 `action_broadcast`。开关默认关闭，实时读取；技能播报包含新技能流程中的本体动作开始和结束。

主配置和战斗快照已移除全部 `promised_consort.rewards` 字段。约定之王的死亡演出结束后调用原版实体掉落流程，物品由 `elder_bosses:entities/promised_consort` 战利品表控制；默认表为 1 个神与王的追忆及 4–8 个神门碎片。经验沿用原版实体流程，不再手动发放配置奖励。

`[promised_consort.meteor].repeat_mode`默认`cooldown_forced`。总血量65%进入二阶段；首次星陨的`general.meteor_health_ratio = 0.85`直接表示总体剩余生命比例，在一阶段剩余85%时锁血触发。触发后立即免疫伤害，直到落地收招完毕回到普通战斗；重复星陨也在整个演出期间免疫。首次之后两个阶段均可在冷却结束后释放。`once`为每场一次，`weighted`为冷却后按权重选招；`skills.entries.consort_meteor.cooldown_ticks`默认1200，即60秒，从收招完成后开始；0关闭后续重复。入场传送、下降和落点均限制在场内；落点固定为绑定场地`arena_center`，无绑定时使用开战中心。
