# 拉塔恩远程反制设计草案

设计日期：2026-09-16；实施状态更新：2026-09-17。**用户已确认全部建议作为首版默认，运行时已接入；实机和新防御动画可视验收仍未完成。**

本文件保留初始设计及当时的待确认表，不作为当前配置字段清单。实际说明见 [远程反制配置](README.md#拉塔恩远程反制)，实际 TOML 见 [common 示例](elder-bosses-common.example.toml)。四种冲刺变体、三种防御、OR资格及 `non_ranged` 排除已实现；原43段动画、开发配置和存档未由本次反制工作覆盖，防御动画使用独立资源。客户端按用户要求不操作；上一轮Blockbench连接失败，新防御动画仍未完成可视审查。

草案中 `distance_metric`、`requires_ranged_target`、`snapshot_variant_on_action_start`、各类 `*_policy`、`cooldown_group` 等策略字段部分实现为固定规则，不能直接照抄本文件全部TOML。回击 `range_multiplier` 实际位于技能根小节，反射只接纳仍有合法玩家归属的受支持箭，不提供失去发射者后的反向回击。数值方案的批准不等于这些实施差异或游戏表现已获验收。

## 1. 已确认与待确认

用户已确认：

- 远程玩家判定采用“长期处于远距离”**或**“处于远距离并且造成伤害”，任一满足即可，不只看手持物品。
- 新增伤害类型标签 `elder_bosses:non_ranged`，默认空；标签内伤害不记为远程，排除优先于其他远程类型匹配。
- 强化螺旋突击、重力旋坠、光速突刺和光速侧突；本轮不包含狮子斩。
- 新增三种独立防御技能：远程减伤、弹射物反射、吸收远程伤害后回击。
- 屏障减免远程伤害；反射处理可识别弹射物且不复制；吸收完全抵消有效期远程伤害，以防御前、抗性结算后的伤害蓄能；近战正常受伤。
- 吸收回击的**最终伤害为基础伤害的1至2倍**，不是额外增加100%至200%。
- 所有能力需要详细配置；本轮先给完整设计与配置草案。

以下均为建议，未经确认不得作为定稿实现：

| 待确认项 | 本稿建议 |
| --- | --- |
| 全部初始数值 | 采用本稿表格与 TOML，后续按实机调整 |
| 三种防御的阶段与方向 | 两阶段均可用，360度保护；近战伤害与现有硬直规则保留 |
| 蓄能统计的精确位置 | 自定义抗性和来源倍率后、护甲及吸收生命前的伤害量 |
| 吸收回击形状 | 一次锁定方向的重力冲击波，固定最大长度与宽度，不追踪 |
| 无蓄能或被打断 | 无蓄能也以1倍基础伤害回击；蓄能被打断则清空且不回击 |
| 反射不兼容对象 | 不反射、不删除，正常进入原伤害逻辑；不给隐含免疫 |
| 防御之间的限制 | 共用冷却，同一Boss不得连续释放两次防御技能 |

## 2. 远程资格与远程伤害分开判定

### 2.1 每名玩家独立记录

仅记录同维度、存活、仍具参战资格的玩家；当前目标是谁，就读取谁的记录。不能因玩家甲长期远离而强化针对近战玩家乙的攻击。

建议按水平中心距离统计，进入远距离的阈值为14格，退出阈值为10格。滞回只防止边界反复切换，不增加技能攻击范围。

1. **持续远离**：处于远距离状态累计满100 tick，且当前距离仍至少14格，获得资格。不要求造成过伤害，因此持续后退或等待也会触发。
2. **远处造成伤害**：近80 tick内，玩家在至少14格处对Boss实际造成的生命损失累计达到1点，且当前距离仍至少14格，获得资格。距离按伤害发生时取值；被格挡、免疫、注册入战吞掉或伤害类型属于 `elder_bosses:non_ranged` 的伤害不计入。

两条已确认按“或”组合，不再提供切换为“且”的配置。以下距离和时间仍是建议值：距离10至14格时保持距离状态，但暂停远离计时，也不允许新选反制技能；距离降至10格及以下时清空远离计时。近期伤害按窗口自然过期，不把返回近身的玩家永久标记为远程。

远程伤害资格中的“造成伤害”按**参战玩家归属与发生距离**判断，在先排除 `elder_bosses:non_ranged` 后，可以包含玩家归属的持续伤害，不强行要求弹射物。这个判断只影响AI资格，不能直接用于拦截伤害。标签不影响第一条远离计时，因此只使用排除伤害的玩家仍可能因为长期远离获得资格。

### 2.2 可以防御的远程攻击

防御技能先检查原始 `DamageSource` 的伤害类型：属于 `elder_bosses:non_ranged` 时直接排除远程分类，不能先把伤害路由为Boss内部伤害类型再检查标签。其余只处理合法参战来源，满足下列一种且不在排除列表的伤害：

- 直接伤害实体是可确认归属的 `Projectile`。
- 伤害类型属于 `minecraft:is_projectile`。
- 经配置额外登记为远程攻击的伤害类型或标签，例如没有弹射物实体的射线法术。

固定排除标签和其他排除项优先于包含项，即使同时属于 `minecraft:is_projectile`、由 `Projectile` 造成或被额外远程名单包含，也不改变排除结果。标签内伤害不贡献远程攻击次数、威胁累计或蓄能，不因本组远程防御而减伤、吸收或反射；原有抗性、免疫和参战规则照常结算，不等于强制按近战渠道计算。其余建议默认排除强制死亡、管理伤害、自伤、环境伤害、近战以及中毒等持续状态伤害；具体伤害类型依据项目注册表和兼容目标核实后登记。**不能把所有 `MAGIC` 都当作远程**，否则魔法近战也会被误挡。

标签定义位于 [non_ranged.json](../../src/main/resources/data/elder_bosses/tags/damage_type/non_ranged.json)，Forge 1.20.1与NeoForge 1.21.1均使用 `data/elder_bosses/tags/damage_type/non_ranged.json`。默认不包含任何伤害类型，也不嵌套其他标签：

```json
{
	"replace": false,
	"values": []
}
```

服主可用数据包在同名标签的 `values` 中加入伤害类型ID或嵌套标签，例如 `"minecraft:arrow"`；这里只是填写示例，默认资源仍为空。标签是分类排除规则，不是伤害免疫标签。对反射的预测拦截，适配器也必须识别原弹射物将产生的伤害类型，不能根据实体类型跳过排除检查；无法确定类型的对象保持不支持。

AI远程资格与防御分类互不替代。技能开始后，可保护Boss免受其他合法玩家的远程攻击；不是只挡当前目标，也不会因攻击者临时靠近就把箭当作近战。

未被适配的法术不会宣称受支持。允许通过伤害类型ID和标签追加分类，反射还需要实体行为适配，不能只凭标签改变任意模组实体。

## 3. 四种强化冲刺

### 3.1 公共规则

- 在动作开始时固定目标UUID、远程资格、强化版本、独立阶段和移动预算，并随动作保存。过程中不因距离变化切换普通/强化版本。
- 使用各技能小节中的 `ranged_counter`，不增加全局百分比施法速度。前摇、释放和后摇仍是绝对 tick。
- 强化版与普通版共享单技能及技能组冷却；另受反远程公共冷却约束，不允许切换版本绕过冷却。
- 前摇末4 tick锁定目标点或方向，锁定后不追踪；完整路径预警从对应准备段开始，保留至少6 tick可见预警。无法满足警告时间时，不提前造成伤害。
- 更长的是**水平追击预算及对应纵向路径**，不是横向宽度或落地爆炸半径。普通 `range_multiplier` 的原有命中宽度/半径设置保留；新增移动预算为最终格数，不再被它二次放大。
- 所有实际移动、落点、路径判定和预警共用一次受控路径计算。保留地形、场地边界和碰撞策略；不以瞬移或穿墙冒充冲刺。
- 不增加单招伤害系数、伤害ID、分身数量或每目标命中上限。持续移动窗口变长也不意味着每tick增加伤害。
- 配置距离是上限，不保证在障碍或场地限制下走满；安全路径无法达到目标时降低该技能权重，不临时扩展判定半径补偿。

### 3.2 建议阶段与距离

以下普通前摇来自当前 `rhythm_v1`。三元组为前摇/释放/后摇。新阶段是反制专用值，不替换普通技能默认。

| 技能 | 现行首前摇 | 反制组成段建议 | 强化位移建议 | 保留内容 |
| --- | --- | --- | --- | --- |
| 螺旋突击 `spiral_assault` | 34 | `22/12/0 > 0/1/26` | 前进最多24格，最多2格/tick | 原螺旋及末端下砸，原命中宽度和落地半径 |
| 重力旋坠 `gravity_dive` | 36 | `24/8/1 > 3/1/26` | 前进最多24格，最多3格/tick | 螺旋接近、末端剑击与随后重力冲击 |
| 光速突刺 `lightspeed_dash` | 44 | `28/2/2 > 1/2/2 > 1/2/2 > 1/2/3 > 4/4/2 > 3/1/17` | 前进最多30格，最多2.5格/tick | 四分身、本体贯穿、延迟光路，原宽度 |
| 光速侧突 `lightspeed_side_dash` | 34 | `22/2/2 > 3/2/2 > 3/2/4 > 6/8/12` | 横移最多8格、最后一段前追最多20格；分别2与2.5格/tick | 三分身与右剑本体扫击，原扫击扇形大小 |

为了真实走远，部分释放窗口比普通版长。压迫感来自更短的准备和更快、更远的接近，不是无条件压缩每个阶段。

### 3.3 执行与命中时刻

- **螺旋突击**：螺旋沿锁定走廊高速推进，结束后在实际落点下砸；原有螺旋HitId沿实际扫掠路径去重。路径全长须在首次生效前完成预警。
- **重力旋坠**：现行实现仅在剑击时调用移动，必须增加专用连续俯冲执行。第一段释放0至7 tick位移，偏移7处剑击；第二段按独立前摇3 tick后触发重力冲击。动画落地接触同步改到实际抵达时刻，不能仍在移动开始时播放落地。
- **光速突刺**：原四分身顺序保留；本体4 tick移动窗口使用同一贯穿HitId，光路在后续独立段激活。分身路径、本体实际运动和光路边界均使用冻结后的权威路径，不追着玩家移动预警。
- **光速侧突**：现行主要为横移。建议前三段只横向调整，最后一段加入前追，释放偏移7处才执行本体扇形扫击；前追本身不额外伤害。必须先完成真实落点预测，再绘制那里的扫击扇形。

动作建议：螺旋突击缩短收刃停顿、加快躯干卷转和前倾；旋坠以快速展开接落地缓冲；光速突刺压低重心并完整送出双刃；侧突增加明确的横移后前扑。四种仍使用可区分的原动作，不统一替换为同一段冲锋。

## 4. 三种远程防御技能

### 4.1 重力壁障 `gravity_bulwark`

双剑在身前交叉，重心下沉，肩后与两侧撑开重力屏障。保护范围是Boss自身，不是吞掉场内全部弹射物的地图屏障。

建议 `10/36/18` tick，冷却180 tick，权重1.0，远程伤害减免80%。仅中间36 tick生效，前后摇不保护；近战照常伤害并遵守原硬直机制，不额外霸体。

适用情形：当前目标有远程资格，近60 tick远程攻击尝试至少3次，或威胁累计至少12点。低成本防守持续射击；不反射、不蓄能、不附送范围攻击。

玩家反制：近身进攻，或暂停射击等待屏障结束。屏障消退动作与保护结束tick一致，残余碎光不表示继续免伤。

### 4.2 引力反镜 `gravity_reflection`

先张开双剑，再反向划出围绕自身的引力折返层。接近的弹射物在接触折返层时反向返回，必须能看出转向，不靠在玩家身上凭空生成伤害。

建议 `8/16/20` tick，冷却220 tick，权重1.2，拦截半径4格，预计20 tick内会进入该范围的合格弹射物使技能进入候选。

- 只反射朝向Boss、可确认敌对玩家归属、已适配的弹射物；同一个实体只反射一次，不复制实体或生成重复伤害。
- 默认转向发射者的拦截时位置；之后不继续追踪。发射者不可用时只反向原速度，不自动攻击无关玩家。
- 保留已适配弹射物的伤害载荷，速度倍率建议1.0、最高3格/tick；生命期最多再保留60 tick，原本更短则不延长。
- 每次技能最多反射8个、每tick最多2个；超过预算或不兼容对象正常通过，不静默删除，也不改造成免疫。
- 玩家可通过停火、无弹射物法术或近战应对；本技能不自动减免射线、爆炸、持续伤害。
- 初版保守支持普通箭和光灵箭。三叉戟、烟花、药水、带回收逻辑或自定义引导的模组弹射物需单独适配后启用。
- 反射后当前伤害归属改为Boss，原发射者保留在审计字段；同步方向、速度和姿态，防止客户端看见旧轨迹。玩家防具、盾牌和瞬防规则仍适用。
- 反射标记由服务端写入并持久化；已经反射的对象不得再次被任意同类反射技能接走，避免两个Boss无限互弹。

### 4.3 星核蓄返 `gravity_reprisal`

双剑收至胸前形成引力核心，受到远程攻击时核心变亮并增大；吸收结束后有独立瞄准段，随后向锁定方向推出一道重力冲击波。

建议三段：

| 组成段 | 前摇 | 释放 | 后摇 | 功能 |
| --- | --- | --- | --- | --- |
| 蓄能 | 12 | 30 | 0 | 只有30 tick释放窗口完全吸收远程伤害 |
| 瞄准 | 10 | 1 | 0 | 不再吸收；提前绘制回击范围，释放起点锁定方向 |
| 回击 | 2 | 6 | 26 | 沿锁定走廊推出冲击波，随后完整收势 |

建议冷却300 tick、权重0.8；当前目标有远程资格且近80 tick远程攻击威胁累计至少20点时参与选择。吸收不会延长计时；达到满蓄值仍等到窗口结束，不突然提前回击。

回击建议为水平长32格、完整宽4格、高4格的走廊，沿释放6 tick推进，单一HitId、每目标至多一次；伤害只在实际波前经过的区域生效，不能在波到达前伤害远端玩家。预警显示冻结走廊，攻击形体覆盖同一走廊；波不追踪、不转弯、不增加额外余波伤害。

建议普通护甲生效的物理重力伤害，沿用现有伤害渠道；不额外申请圣属性或新的绕甲规则。默认碰墙停止并受场地约束，判定与可见效果同时截断。

#### 回击公式

记 `A` 为本次蓄能窗口累计吸收量，`C` 为满蓄阈值，`B` 为基础伤害：

$$
B = \mathrm{flat} + \mathrm{attack\_ratio} \times \mathrm{BossAttack}
$$

$$
q = \min(\max(A, 0)/C, 1),\qquad D = B\,[m_{\min}+(m_{\max}-m_{\min})q]
$$

用户已确认 `m_min=1.0`、`m_max=2.0`。建议 `C=40`，`flat=4`、`attack_ratio=0.8`，计量单位均为Minecraft生命点。示例若Boss攻击力为20，则基础伤害20点，吸收0/20/40点分别产生20/30/40点回击；实际玩家承伤还按现有防御链结算。

满蓄后累计量钳制到 `C`，后续合格远程伤害仍免疫，但不再增伤。来自多名合法参战玩家的吸收量合计，最终只朝当前锁定目标回击，不向每个攻击者分别释放一遍。

统计顺序建议为：来源及参战检查 → 原有无敌/强制死亡规则 → 远程分类 → 自定义抗性及来源倍率 → 本技能吸收 → 原本的护甲/生命结算。已经被原无敌或免疫规则拦掉的伤害不能蓄能，非有限或非正值拒绝进入计量；近战不计量也不免疫。

无蓄能仍回击1倍属于本稿建议，可改成 `skip`。目标在锁定前失效则取消回击；锁定后目标移动不影响冻结路径。被硬直、死亡、转阶段或战斗重置取消时清空蓄能和待释放事件，不免费回击；已经释放的波按原有危险区生命周期处理。

## 5. 情境选择与公共约束

不是看到箭就立即中断当前攻击，也不是技能冷却好了就必放。只在现有动作选择节点、当前目标有远程资格时进入候选池。

1. 观察到预计即将进入拦截区的可反射弹射物：反镜获得2倍条件权重。
2. 近期远程威胁累计达到蓄能阈值：蓄返获得2倍条件权重。
3. 近期远程攻击密集但不适合反射：壁障获得1.5倍条件权重。
4. 持续远离但没有射击证据：优先提高强化冲刺权重，不让Boss原地空放防御技能。
5. 路径可达、冷却就绪的强化冲刺与合格防御按权重选择，不承诺固定循环或读玩家尚未发出的输入。

三防御共享100 tick冷却，反远程公共冷却60 tick；均从技能结束或取消时起算，不能以打断绕过。一次防御后必须完成至少一个普通攻击或反制冲刺，才允许再次防御。入场、转阶段、星陨脚本、死亡和原有硬直优先，不插入即时防御。

“威胁”与“实际受伤”分开记录：防御减免前可识别的合法远程攻击尝试用于三招情境权重；实际生命损失用于玩家远处造成伤害的资格。一次伤害链只登记一次，不允许重复回调重复积累威胁或蓄能。

## 6. 配置草案

全部仍放在唯一的 common 配置中。`ranged_counter` 是现有技能的强化分支，三种新技能拥有独立小节，不新增第二份配置。下列值全部是建议默认，尚未实现。

### 6.1 资格与公共选择

```toml
[promised_consort.ranged_counter]
enabled = true
distance_metric = "horizontal"
enter_distance = 14.0
exit_distance = 10.0
far_dwell_ticks = 100
far_damage_window_ticks = 80
far_damage_threshold = 1.0
require_current_far_distance = true
reset_dwell_on_close = true
pause_dwell_in_hysteresis_band = true
global_cooldown_ticks = 60
defense_shared_cooldown_ticks = 100
max_consecutive_defenses = 1
reset_defense_chain_on = "completed_attack"
activation_policy = "next_selection_only"
snapshot_variant_on_action_start = true
debug_events = false

[promised_consort.ranged_counter.classification]
accept_owned_projectile_entity = true
damage_type_tags = ["minecraft:is_projectile"]
additional_damage_type_ids = []
additional_damage_type_tags = []
excluded_damage_type_ids = []
excluded_damage_type_tags = []
require_participant_owner = true
exclude_forced_death = true
exclude_self_damage = true
exclude_status_damage = true
exclude_environmental_damage = true
```

资格两条固定按“或”组合。`elder_bosses:non_ranged` 是始终检查的排除标签，默认内容为空；`excluded_*` 则是额外可配置的排除项，留空或移除条目不会关闭固定标签。安全例外中的强制死亡和管理伤害不是关闭上述开关就能被免疫，仍须保留原有不可绕过的生命周期规则。弹射物适配表和明确的近战分类也不能被额外包含标签强行覆盖。

### 6.2 四种冲刺强化

下列 `attack_event_offsets` 与组成段等长，`-1` 表示本段不产生独立命中。它只移动现有事件，不允许借数组增删事件；有效偏移必须小于对应释放时长。数组顺序沿用原技能，不做自由脚本执行。

```toml
[promised_consort.skills.spiral_assault.ranged_counter]
enabled = true
selection_weight_multiplier = 2.0
min_target_distance = 14.0
max_target_distance = 32.0
windup_ticks = [22, 0]
active_ticks = [12, 1]
recovery_ticks = [0, 26]
attack_event_offsets = [0, 0]
max_forward_distance = 24.0
max_forward_per_tick = 2.0
stop_distance = 4.0
target_lock_lead_ticks = 4
minimum_warning_ticks = 6
turn_rate_degrees_per_tick = 12.0

[promised_consort.skills.gravity_dive.ranged_counter]
enabled = true
selection_weight_multiplier = 2.0
min_target_distance = 14.0
max_target_distance = 32.0
windup_ticks = [24, 3]
active_ticks = [8, 1]
recovery_ticks = [1, 26]
attack_event_offsets = [7, 0]
max_forward_distance = 24.0
max_forward_per_tick = 3.0
stop_distance = 4.0
target_lock_lead_ticks = 4
minimum_warning_ticks = 6
turn_rate_degrees_per_tick = 12.0

[promised_consort.skills.lightspeed_dash.ranged_counter]
enabled = true
selection_weight_multiplier = 2.0
min_target_distance = 14.0
max_target_distance = 38.0
windup_ticks = [28, 1, 1, 1, 4, 3]
active_ticks = [2, 2, 2, 2, 4, 1]
recovery_ticks = [2, 2, 2, 3, 2, 17]
attack_event_offsets = [0, 0, 0, 0, 0, 0]
max_forward_distance = 30.0
max_forward_per_tick = 2.5
stop_distance = 4.0
target_lock_lead_ticks = 4
minimum_warning_ticks = 6
turn_rate_degrees_per_tick = 16.0

[promised_consort.skills.lightspeed_side_dash.ranged_counter]
enabled = true
selection_weight_multiplier = 1.5
min_target_distance = 14.0
max_target_distance = 30.0
windup_ticks = [22, 3, 3, 6]
active_ticks = [2, 2, 2, 8]
recovery_ticks = [2, 2, 4, 12]
attack_event_offsets = [0, 0, 0, 7]
max_lateral_distance = 8.0
max_lateral_per_tick = 2.0
max_forward_distance = 20.0
max_forward_per_tick = 2.5
stop_distance = 4.0
target_lock_lead_ticks = 4
minimum_warning_ticks = 6
turn_rate_degrees_per_tick = 16.0
```

`target_lock_lead_ticks` 相对对应移动释放起点；分身自己的原有锁点与接触规则保留。转向只发生在锁定前。距离之外的伤害、宽度、落地半径和阶段限制继承原技能；两种光速技能仍只在二阶段使用。需要独立冷却时可新增明确字段，不复用 `range_multiplier` 改时间或预算。

### 6.3 重力壁障

```toml
[promised_consort.skills.gravity_bulwark]
enabled = true
phases = [1, 2]
weight = 1.0
cooldown_ticks = 180
cooldown_group = "ranged_defense"
hyper_armor_active = false
windup_ticks = 10
active_ticks = 36
recovery_ticks = 18
requires_ranged_target = true
min_target_distance = 14.0
max_target_distance = 40.0
defense_arc_degrees = 360.0
ranged_damage_reduction = 0.80
protect_from_all_participants = true
melee_damage_multiplier = 1.0

[promised_consort.skills.gravity_bulwark.trigger]
mode = "any"
threat_window_ticks = 60
minimum_attack_attempts = 3
minimum_threat_damage = 12.0
condition_weight_multiplier = 1.5
```

减伤先后顺序与蓄能共用同一个远程防御入口，不能在 `hurt` 和 `actuallyHurt` 各减一次。方向不是360度时，按来袭速度或伤害来源点判断，方向未知的非实体法术不推断为正面命中。

### 6.4 引力反镜

```toml
[promised_consort.skills.gravity_reflection]
enabled = true
phases = [1, 2]
weight = 1.2
cooldown_ticks = 220
cooldown_group = "ranged_defense"
hyper_armor_active = false
windup_ticks = 8
active_ticks = 16
recovery_ticks = 20
requires_ranged_target = true
min_target_distance = 14.0
max_target_distance = 40.0
intercept_radius = 4.0
defense_arc_degrees = 360.0
speed_multiplier = 1.0
max_projectile_speed = 3.0
remaining_lifetime_cap_ticks = 60
aim_policy = "shooter_position_at_intercept"
missing_owner_policy = "reverse_velocity"
unsupported_policy = "pass_through"
max_reflections_per_cast = 8
max_reflections_per_tick = 2
max_scanned_projectiles_per_tick = 64
prevent_rereflection = true
supported_entity_ids = ["minecraft:arrow", "minecraft:spectral_arrow"]
additional_entity_tags = []
excluded_entity_ids = []

[promised_consort.skills.gravity_reflection.trigger]
scan_radius = 24.0
incoming_prediction_ticks = 20
minimum_incoming_projectiles = 1
condition_weight_multiplier = 2.0
```

检测须使用上一位置到下一预测位置的线段与拦截区域相交，而不是只看当前实体在不在圆内，否则高速箭会穿过扫描区。预测到达时刻早于前摇结束则降低反镜权重，不偷缩短前摇或提前反射；超过扫描预算不宣称必定拦下全部弹射物。

`supported_entity_ids` 是适配器启用名单，不是自动兼容名单。附魔、穿透、燃烧、药效、拾取、所有者免碰撞和其他模组的伤害回调必须按类型核实；不安全的载荷保留为不支持，不通过强制清除载荷冒称保留原攻击。

### 6.5 星核蓄返

```toml
[promised_consort.skills.gravity_reprisal]
enabled = true
phases = [1, 2]
weight = 0.8
cooldown_ticks = 300
cooldown_group = "ranged_defense"
hyper_armor_active = false
requires_ranged_target = true
min_target_distance = 14.0
max_target_distance = 40.0
defense_arc_degrees = 360.0
protect_from_all_participants = true
melee_damage_multiplier = 1.0

[promised_consort.skills.gravity_reprisal.components]
windup_ticks = [12, 10, 2]
active_ticks = [30, 1, 6]
recovery_ticks = [0, 0, 26]

[promised_consort.skills.gravity_reprisal.absorption]
full_charge_damage = 40.0
minimum_return_multiplier = 1.0
maximum_return_multiplier = 2.0
damage_measure = "post_resistance_pre_armor"
zero_charge_policy = "base_counterattack"
full_charge_policy = "hold_until_window_end"
interrupted_policy = "discard_charge"
target_lost_before_lock_policy = "cancel_counterattack"

[promised_consort.skills.gravity_reprisal.counterattack]
shape = "advancing_rectangle"
range_multiplier = 1.0
length = 32.0
width = 4.0
height = 4.0
lock_component_index = 1
lock_active_offset_ticks = 0
minimum_warning_ticks = 10
max_hits_per_target = 1
damage_channel = "physical"
blockable = true
instant_guard_eligible = true
stop_at_terrain = true
respect_arena_boundary = true

[promised_consort.skills.gravity_reprisal.counterattack.damage]
flat = 4.0
attack_ratio = 0.8

[promised_consort.skills.gravity_reprisal.trigger]
threat_window_ticks = 80
minimum_threat_damage = 20.0
condition_weight_multiplier = 2.0
```

组件索引从0开始，蓄能/瞄准/回击固定三段，不能增删数组改变技能用途；锁点与吸收结束分别由对应段计算，不再提供一组会冲突的整招总时间。形状和生命期由回击段控制，修改回击持续期只改变推进速度，不增加命中次数。`range_multiplier` 在这个新回击中只缩放长宽高，不缩放伤害、计量阈值和蓄能时长。

### 6.6 参数验证与兼容

| 参数族 | 约束 |
| --- | --- |
| 前摇/后摇、冷却、观察窗口 | 非负整数；释放至少1；整招总长不超过现有协议上限 |
| 距离 | 有限非负值；建议配置上限256格；进入阈值大于退出阈值，技能最远距离大于等于最近距离 |
| 每tick位移 | 有限正值；建议上限8格；连续路径必须分步碰撞，不能直接跨过地形 |
| 减伤 | 0至1；1表示完全减免，0表示不减伤 |
| 蓄能阈值 | 有限且大于0；不得除0、无穷增长或使用负伤害充能 |
| 回击倍率 | 有限非负值，最大值不小于最小值；本稿默认1和2，允许服主有意调整 |
| 重击偏移 | `0 <= offset < active_ticks`；事件数量固定，不允许无效偏移静默丢失命中 |
| 转向/防御角度 | 有限值；转向0至180度/tick，防御角度大于0且不超过360度 |
| 预算与名单 | 正整数有限上限；ID语法有效且已注册；未知枚举值报错；没有适配器的反射实体不得启用 |
| 配置迁移 | 新字段补默认，不覆盖普通技能时序、自定义数值或既有 `.pre-rhythm-v1` 等备份 |

所有服务端参数进入开战快照；动作开始保存强化分支及本次冻结参数，配置热重载不改变正在进行的动作。客户端只使用服务端同步的状态、锁点、阶段与显示用蓄能比例，不能据本地配置计算免伤或回击伤害。客户端显示预算可以影响装饰密度，不能缩小权威预警范围。

存档至少保留每目标远离累计与窗口年龄、公共冷却、连续防御次数、当前变体、各段进度、锁点、剩余位移、吸收量及回击是否已释放。恢复不能重新蓄能或重复回击；防御前摇和后摇不会被错误恢复成免疫。老存档无字段时使用空状态；已有战斗快照的兼容补齐须测试，不能用新的默认值覆盖用户旧配置。

## 7. 表现与验收计划

三种新防御需要独立动作和可区分的效果，不能只增加被动概率：壁障是交叉持剑稳固防守，反镜是短促外拨与弹射物折返，蓄返是闭合聚光、独立瞄准和向前释放。两阶段可改变光色与米凯拉辅助表现，但规则与计量相同；不得用仅铺满指示器的发光平面代替动作接触和实际攻击形体。

实施后的必要验收：

1. 资格：长期远离但不攻击、远处短时造成伤害、近战后退、回到近身、目标切换和多玩家记录分离；固定“或”的四种真值组合均验证。排除标签默认空、非空和嵌套标签均测试；重叠远程包含名单时仍优先排除，被排除伤害不能积累伤害资格/威胁/蓄能，也不能被反射，但不能禁用长期远离资格。
2. 冲刺：普通与强化时序隔离，真实移动达到预算，路径碰撞与预警同源，移动开始不提前触发终点剑击；伤害、次数、宽度和半径保持。
3. 壁障：仅有效段减伤，近战/环境/管理伤害不误挡，合法远程法术分类与双重减免回归。
4. 反镜：高速入射、掠过、远离方向、预算耗尽、原发射者消失、重复反射、穿透箭、跨存档和不支持弹射物；不复制、不额外制造实体。
5. 蓄返：吸收0/半阈值/满阈值/超阈值分别为1/1.5/2/2倍，多人合计，取消与保存恢复不重复回击，波前与实际命中同tick。
6. 配置：每个可调阶段实际影响动画/事件/移动，防御比例、阈值、冷却和条件确实生效，普通技能自定义值不被迁移覆盖。
7. 两加载器编译与资源检查后，分别**启动客户端并实际进入世界**，观察三招及四种强化冲刺；仅服务端、主菜单、离线几何或脚本播放均不算完成。

现有入口参考：[选择器](../../src/main/java/com/tonywww/elder_bosses/boss/promisedconsort/selection/PromisedConsortSkillSelector.java)、[技能目录](../../src/main/java/com/tonywww/elder_bosses/boss/promisedconsort/action/PromisedConsortActionCatalog.java)、[执行器](../../src/main/java/com/tonywww/elder_bosses/boss/promisedconsort/execution/PromisedConsortActionExecutor.java)、[伤害解析](../../src/main/java/com/tonywww/elder_bosses/boss/promisedconsort/damage/PromisedConsortIncomingDamageResolver.java)。现有选择器的远距离加权不等于本稿反制已经实现；原22招的素材、默认节奏和历史验收继续分别保留。