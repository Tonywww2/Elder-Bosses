# 马莲妮娅 V13：连续攻击、动作发力与开花轨迹

2026-09-27。当前实现对应本轮用户要求；配置政策见 [AGENTS.md](../../AGENTS.md)，不提供旧配置迁移或兼容分支。

## 当前行为

- 参考拉塔恩的状态机，一轮 2–4 个技能，组内间隔 2 tick；普通剑术完成最后有效段后至少保留 6 tick 收势再衔接。组末保留完整后摇，再使用 `phase_one_idle_*` / `phase_two_idle_*` 配置停顿。目标、冷却和高威胁规则继续约束选招。
- 抓取、水鸟、上挑落斩、空中攻击、幻影和开花保留完整收尾，避免截掉放手、落地或反击窗口。打断和阶段变化清空连放计数。
- 一阶段交替观察和缓步压近，近身停住，不再因进入 4 格距离而每 tick 机械后退。二阶段缩短观察、积极接近；连放结束的停顿期间停止寻路。
- 40 段动画的右臂统一单向肘屈曲，前臂侧扭限制在小幅范围；移除刀刃朝向向前臂转嫁的四元数补偿。刀柄、掌面和握指保持刚性连接，左手保留抓前张开、抓后闭合、投掷时放开的关节动作。
- 全部 15 个攻击及幻影动作按出手窗口烘焙加速，保留命中语义点；髋、胸、身体侧移和重心下压扩大，披风与头发延迟采样同一加速后的动作。

## 水鸟乱舞

| 阶段 | 默认服务端 tick | 动作与位移 |
| --- | --- | --- |
| 提膝架刀 | 0–31 | 收膝起跳，高位持刀；t22 锁定第一段 |
| 第一突进 | 32–49 | 压身爆发并穿过锁点，三次不同切面的斩击姿态 |
| 悬停换向 | 50–61 | 刀风退去，t58 重新锁点 |
| 第二突进 | 62–73 | 交换腿部重心，更短促的追击 |
| 悬停换向 | 74–81 | t78 重新锁点 |
| 第三突进 | 82–101 | 更长的交叉追斩，随后收势 |
| 余势停顿 | 102–109 | t106 转向最后余斩 |
| 原地余斩 | 110–115 | 无服务端突进；预警是实际宽度对应的圆形 |
| 落地收刀 | 116–141 | 解除浮空控制，保留完整后摇 |

每目标命中上限仍为 `2 / 2 / 2 / 1`。身体不再连续旋转三圈；穿越的是锁定位置，锁点后不追踪玩家的新位置。方块碰撞仍约束实际路径。霸体、重力、刀光、预警均跟随当前组件与动作时间。

参考已缓存的[原作动作展示](https://www.bilibili.com/video/BV13e4y127eg/)约 01:35–01:42，以及[用户提供的战斗录像](https://www.bilibili.com/video/BV1mT4y1Y7RG/)。观察依据是提膝蓄势、三次追击中的停顿与最后余斩；本表为 Minecraft 改编节奏，不宣称原作精确帧数。

## 艾奥尼亚

从阶段转换结束的位置连续起飞，删除进入二阶段时向 `aeonia_opening` 锚点的 `setPos`。默认上升 26 tick，悬停 17 tick，俯冲 18 tick；t61 冲击，t86 绽放，总长 182 tick。动画作者时钟为 166 tick，绽放语义点 t70。

位移采用逐段累计曲线，包含加速与减速，单次受控三维位移最多 0.95 格。范围调节保留同一位移指令的身份，避免每 tick 重置进度。落点先限制可达距离，碰撞后的实际位置再成为冲击、花瓣和持续伤害区的共同锚点。网络位置更新间隔改为 1 tick。

闭合花苞降低折叠角度；世界花瓣向上展开，花缘高于花心，修正向下扣成伞形的轮廓。默认腐败区仍在绽放后持续 84 tick，伤害公式不变。

## 复现与验证范围

```text
node models/malenia/scripts/build_animations.js
node models/malenia/scripts/sync_animations.js
node models/shared/current_assets.js malenia
node models/malenia/tests/gait.test.js
node models/malenia/tests/hand_pose.test.js
node models/malenia/tests/reference_motion.test.js
node models/malenia/tests/motion_force.test.js
gradlew.bat :1.20.1-forge:runMaleniaMotionTest -I models/malenia/motion/gametest.init.gradle --offline
```

`MotionCadenceCheck` 检查 2–4 招、组末收势、中途后摇不可截断，以及各段位移指令身份；`AnimationTimelineTest` 和 `SkillEffectsCheck` 检查配置映射、命中/特效时序与网络生命周期。`MotionVisualCheck` 使用真实 GeckoLib 模型和关键帧离屏绘制，花形由 `SkillVisualCheck` 直接调用运行时代码绘制。

独立 Forge GameTest 世界已通过连续上升、俯冲加速、单 tick 位移上限、落地点与花域重合、三次水鸟累计移动、段间与最终余斩静止检查。夹具不打入发行 JAR。客户端联机观感、实际战斗难度和复杂地形下的所有招式尚未人工实机验收。
