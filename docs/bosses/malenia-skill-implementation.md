# 马莲妮娅技能首批实装

> 本文为 2026-09-26 首批记录。当前动作、时序与状态机以 [V13 记录](malenia-motion-v13.md)为准。

2026-09-26。对应[技能设计 R1](malenia-skill-design.md)，本次交付可运行的表现基础和艾奥尼亚花苞停顿原型。完整设计仍有后续制作项，未标为实机验收完成。

## 已接入

| 内容 | 实际行为 |
| --- | --- |
| 刀轨 | 渲染后采样现有 `blade_root` / `blade_tip` 世界坐标，银白刀带与暖色细刃；最多 24 个截面，普通尾迹 3 tick，四连 / 水鸟 2 tick。 |
| 有效段 | 刀轨依据服务端重映射后的动画时间开启；踢击、艾奥尼亚、抓空、抓取中断和待机不出刀光。不同斩击窗口不跨段连接。 |
| 水鸟 | 保留四个真实窗口 `[32,46)`、`[50,62)`、`[66,78)`、`[82,100)`，命中上限仍为 `2 / 2 / 2 / 1`；分段挥刀声与退光已接入。 |
| 幻影 | 当前五次攻击使用 V12 模型的独立骨架副本，分别采样现有 `phantom_slash` / `phantom_thrust` 动画；6 tick 锁定飞行映射到专用接触姿势。没有新增实体、AI 或伤害。 |
| 幻影路径 | 服务端指示器几何改为带真实 XYZ 端点的 `PATH`，保留发射高度与锁定落点。客户端不读取玩家实时位置来改飞行方向。 |
| 艾奥尼亚 | 默认落地冲击仍为第 49 tick；开花改为第 74 tick，总长 170 tick。新增三层、22 片折面花瓣、暗色根脉、短暂亮芯、分开的冲击 / 绽放波与稀疏孢子。 |
| 世界花域 | 从持久腐败区快照读取中心、半径、生效和结束时刻；Boss 后续移动不会拖走花域，区域结束立即清理。关闭技能特效时仍保留本体花模型。 |
| 接触反馈 | 新增 `MaleniaHitFeedbackPacket`，服务端实际伤害、盾挡、成功瞬防分别触发短闪、粒子和原版音效。挥空不生成接触反馈。位置取目标朝向 Boss 的近侧表面。 |
| 生命周期 | 动作切换、打断、瞬移采样跳变会切断刀带；离开追踪、换维度和断线清理状态。持久花域独立于普通动作生命周期。 |
| 预算 | 粒子共享 `skill_vfx.particle_budget_per_boss_per_tick`；遵循技能特效开关与距离。每帧最多 2 个完整细节幻影，其他保留简化骨架模型。声音每 Boss 每 tick 最多 2 个，全局最多 8 个。 |

所有新渲染入口位于范围提示开关判断之前，因此关闭地面指示器不会关闭刀轨、幻影或花域。旧 Malenia 通用粒子分支不再接收战斗快照；约定之王的现有分支保留。

## 当前配置规范

本项目不要求兼容旧配置，见 [项目规范](../../AGENTS.md)。当前组件值及验证入口见 [V13 记录](malenia-motion-v13.md)与[配置样例](../config/elder-bosses-common.example.toml)。

新增接触反馈使网络协议从 4 升至 5，联机两端需要使用同一版模组。Forge 继续使用 Java 17，NeoForge 使用 Java 21。Forge 的 mclib 已有运行时依赖，本次补充编译期声明，用于读取 GeckoLib 烘焙关键帧中的 `IValue`。

## 验证与复现

- Forge 1.20.1、NeoForge 1.21.1：编译与资源处理通过。
- `SkillEffectsCheck.java`：艾奥尼亚落地 / 开花语义点及倍速组件映射、水鸟窗口 / 命中上限、五条幻影 XYZ 路径、预览取消、自然消散、过期动作包、持久区清理、反馈编码往返、骨架隔离。
- `AnimationTimelineTest.java`：全部动作原有时间映射、组件配置、动画网络时钟、过渡和步态回归。
- `SkillVisualCheck.java`：真实 shader 编译，实际幻影骨架与纹理、花瓣几何的六帧离屏渲染；预览输出至 `build/malenia-skill-previews/skill-effects.png`。该检查不等于进入 Minecraft 战斗。

本轮技能检查通过 5980 项，原有动画与时钟检查通过 8105 项。两个测试包已构建，并核对新增类、shader 及 V12 资源均已入包；文件路径和 SHA-256 见[机器可读验证记录](../../models/malenia/skill_implementation_validation.json)。

使用项目已配置的 JDK 与 Gradle 缓存：

```powershell
.\gradlew.bat -I models/shared/check_classpath.init.gradle :1.20.1-forge:writeCheckClasspath :1.21.1-neoforge:compileJava :1.21.1-neoforge:processResources --offline
node models/promised_consort/tests/run_attack_plan_check.js ../../malenia/tests/SkillEffectsCheck.java
node models/promised_consort/tests/run_attack_plan_check.js ../../malenia/tests/AnimationTimelineTest.java
node models/promised_consort/tests/run_attack_plan_check.js ../../malenia/tests/SkillVisualCheck.java
```

复用现有 Java 检查启动器；检查本身位于 `models/malenia/tests/`。离屏检查需要可用的 OpenGL 环境。

## 后续验收

本次未重做全部 15 招的本体动画，未加入六拍幻影候选、额外连段 AI、镜头震动或原创技能音频。水鸟的四轮重心细修、额外交叉剑风、各招专属发力和收势、二阶段招式差异仍需继续制作。

仍需在游戏内检查第一 / 第三人称、坡地、墙边、低帧率、持盾 / 瞬防、被打断、两只 Boss、四人联机、延迟和资源包重载。着重检查：刀光与刀锋的屏幕对齐、幻影在攻击路径中出现和到达的节奏、花瓣对玩家视野的遮挡，以及自定义时长 / 范围下的判定一致性。
