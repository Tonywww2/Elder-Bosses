# 女武神二阶段外观、音频与腐败计时 V16

本轮按用户最终指定值，将腐败改为停止积累后 **120 tick** 开始衰减，之后每 **40 tick** 衰减一次。每次减少量仍为 8；再次正数积累会重新开始等待，零积累不重置计时。代码默认值、配置快照、NBT、完整示例和用户指定的 `run/config/elder_bosses-common.toml` 已同步。伤害与其他用户配置保持原值。

当前字段为 `decay_delay_ticks`、`decay_interval_ticks`、`decay_per_interval`，不提供旧字段兼容。

## 二阶段外观

对照[用户提供的视频](https://www.bilibili.com/video/BV1mT4y1Y7RG)中约 4:36–4:44 的转阶段画面，以及[二阶段截图](https://win.gg/news/malenia-in-elden-ring-is-the-ultimate-bait-and-switch-character/)，本轮以苍白皮肤、铜红长发、金色义肢、不对称暗红腐败和浅色菌片作为主要辨识特征。

- 二阶段采用 `malenia_phase_two.geo.json` 与 `malenia_phase_two.png`；本体和分身走同一资源选择。
- 去除二阶段的整块躯干腐败覆盖，改为胸前、髋部、左肩、前臂、膝部和背部的阶梯菌片、根系与暗红衬层。保持方块化轮廓。
- 新的八区材质包含皮肤、腐败皮肤、红发、旧金、腐败硬壳、菌片、银色刀刃和根系。纹理由 imagegen 生成，原图直接作为图集使用；构建脚本只处理 UV 和几何，没有对生成图再次缩放或拼贴。
- 两阶段共享 92 根骨骼和全部 40 段动画，保留方块手、关节轴心与握刀位置。二阶段变体为 155 方块 / 751 面；一阶段保持原资源。
- 转阶段动画第 71 tick（原盔甲消失时）切换外观。翅膀仍由现有着色器生成，背部新增实体根系用于衔接。

可编辑文件：`models/malenia/malenia_phase_two.bbmodel`。从当前一阶段源模型重建变体：

```text
node models/malenia/scripts/build_phase_two.js
node --test models/malenia/tests/phase_two.test.js
node models/shared/current_assets.js malenia
```

修改公共骨骼、动画或一阶段导出后，需要重新运行变体构建脚本，确保两个编辑工程一致。

## 招架成功反馈

新增原创合成 `entity.malenia.parry_success`：低频盾体冲击、短促噪声瞬态和多组非整数倍金属共鸣，约 0.85 秒、单声道、预加载，支持空间定位。

只有服务器确认的 `INSTANT_GUARD` 接触包会触发，沿用包时效校验和去重。成功时显示 10 tick 金白扩散光圈与 20 个火花；玩家本人成功时，将战斗音乐短暂压低 65%，随后恢复。红白光圈仍表示可招架窗口，金白扩散表示成功。

成功音效不依赖窗口提示开关；`skill_vfx.enabled` 控制成功光圈和火花。伤害及普通格挡反馈仍保留原有音色。

## 战斗音乐

使用 Sascha Ende 的 [Invasion](https://ende.app/en/song/206-invasion)，根据作者的[许可说明](https://ende.app/en/standard-license)按 [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)使用和改编。

原曲的弦乐、铜管、打击乐及合唱为选择依据：一阶段截取推进段，二阶段截取更强烈的段落。两个循环均为 66 秒，使用 2 秒首尾交叉淡化、8 毫秒接缝渐变，分别标准化到 -21 / -19 LUFS。没有采样原游戏录音。

音乐由现有 `[boss_music]` 开关、音量、距离和淡变时间统一控制，遵循原版音乐音量滑块。进入战斗播放一阶段音乐，转阶段切换二阶段；击败、死亡、离开范围、卸载实体或断开连接后退出。场上只选择一个 Boss 的音乐，转场最多存在两条淡变音轨。

音源、改编方法、哈希和音频检查见 `models/malenia/audio/manifest.json`；署名随两平台 JAR 打包到 `META-INF/BGM-CREDITS.txt`。可打开[浏览器试听页](../../models/malenia/audio/audition.html)检查两个阶段与招架音效。

## 验证

- `ScarletRotCadenceCheck`：真实实体腐败数据，验证 119/120/159/160 tick 边界、再次积累重置、零输入、跨 tick 补算、同 tick 重复观察和非默认配置往返；21 项通过。
- `CombatReadabilityCheck`：招架窗口与提示窗口一致、提示开关不影响判定、当前配置 NBT 往返；152 项通过。
- `BossMusicCheck`：两位 Boss 的音乐选择、切阶段、退出、音量淡变、音频哈希与两平台资源；407 项通过。
- Node 检查：变体骨骼/动画/方块手与当前工程保持一致，UV 不越界，编辑器与运行资源一致。
- 真正 OpenGL 编译及绘制：六个二阶段身体/翅膀视角、分身、红白窗口与金白成功光圈。输出在 `build/malenia-v16/previews/`。
- 两平台 Gradle 构建通过，每个平台的 11 项关键资源与源文件逐字节一致，未打包测试场景。完整记录见 `models/malenia/phase_two_feedback_validation.json`。没有进行真实游戏内视觉与听感验收；数值音频检查不代表已试听混音效果。
