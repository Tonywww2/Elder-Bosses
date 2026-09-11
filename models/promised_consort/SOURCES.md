# 约定之王资产来源

研究日期：2026-09-10。资料仅用于形状、姿势和招式结构，不提供模型、动画或角色再分发授权。

| 来源 | 本轮用途 | 限制 |
| --- | --- | --- |
| [战斗规格](../../docs/bosses/promised-consort-radahn.md) | 默认时长、动作 ID、阶段与命中窗口 | Minecraft 原创改编，不是原作帧数 |
| [美术规范](../../docs/art/promised-consort-radahn-art-bible.md) | 双阶段轮廓、材质、骨架和比例 | 本工程据此原创制作 |
| [已有图片台账](../../docs/references/promised-consort-radahn-sources.md) | 宽肩、红鬃、曲刃、角质、附身关系 | 原作截图仅限内部研究 |
| [Promised Consort Radahn - wiki.gg](https://eldenring.wiki.gg/wiki/Promised_Consort_Radahn) | 铲地举岩、合剑引力、翻身下砸、光速斩结构 | 社区文字，不当作精确动作数据 |
| [Unique Skill: Promised Consort - wiki.gg](https://eldenring.wiki.gg/wiki/Unique_Skill:_Promised_Consort) | 双斩、旋身、跃起和十字落地 | 玩家战技不同于 Boss，不照搬伤害和帧数 |
| [苍星化羽：法环 DLC 全招式，约定之王](https://www.bilibili.com/video/BV1W3YLe4Eek/) | 实际解码公开低清流，观察剑术与二阶段姿态 | 2024-08-10 发布，早于 1.14；不声称反映补丁后精确节奏 |
| [NaNa_娜娜米：米塔恩二阶段招式详解](https://www.bilibili.com/video/BV1sdFFevEXe/) | 新增全片总览及 197-207 秒密集研究帧 | 2025-01-25 发布，但发布时间本身不能证明录像采用的游戏版本 |
| [米凯拉建模分解展示](https://www.bilibili.com/video/BV1HJ4m1M7jD/) | 多角度形体、倾斜躯干、头发和附身关系 | KG-Area21 上传，页面注明 BonfireVN 原作与 `https://youtu.be/gcKufm8d6kA`；本轮只读取 B 站公开低清流，不是标题所称 4K60 |
| [DSAnimStudio](https://github.com/Meowmaritus/DSAnimStudio) | 核对原生 ANIBND/CHRBND 动画和事件查看能力 | 仅阅读工具文档，需要本地游戏数据；没有取得或导入拉塔恩原作动作数据 |
| [-撩人心-：拉塔恩全招式处理方法](https://www.bilibili.com/video/BV1Qy411q7Uq/) | 搜索到的额外拆解资料 | 本轮仅核对页面信息，没有解码，不算实际观察到的姿态证据 |
| 用户指定的本地 MC_Dev_Skills | GeckoLib 双版本接口和入世界验证约束 | 只读引用，没有修改外部 Skill 库 |

## 实际研究帧

视频 `BV1W3YLe4Eek`，作者苍星化羽，长度 602 秒，匿名公开流质量 16。

| 图像 | 原视频区间 | 采样间隔 |
| --- | --- | --- |
| [总览](../../docs/assets/reference/promised-consort-radahn/motion-v1/moveset_overview.jpg) | 0-600 秒 | 20 秒 |
| [旧版早段参考](../../docs/assets/reference/promised-consort-radahn/motion-v1/phase_one_swordwork.jpg) | 65-113 秒 | 2 秒；包含开场过场，不能全部标成剑术 |
| [二阶段姿态](../../docs/assets/reference/promised-consort-radahn/motion-v1/phase_two_choreography.jpg) | 350-506 秒 | 6 秒 |

图中时标相对于片段起点。对应 JSON 记录链接、作者、区间、间隔和取得时间。[取帧脚本](scripts/reference_frames.js)使用工作区现有 FFmpeg，仅输出低清研究图，不绕过登录或访问限制。

## 新增密集研究

本轮请求清晰度 32，公开接口实际均返回 16。提高的是时间采样密度，不是源画面分辨率。

| 图像 | 视频与区间 | 采样间隔 |
| --- | --- | --- |
| [二阶段总览](../../docs/assets/reference/promised-consort-radahn/motion-v2/phase_two_breakdown_overview.jpg) | `BV1sdFFevEXe`，0-314 秒 | 12 秒 |
| [连舞密集片段](../../docs/assets/reference/promised-consort-radahn/motion-v2/consort_dance_dense.jpg) | `BV1sdFFevEXe`，197-207 秒 | 0.25 秒 |
| [米凯拉多角度](../../docs/assets/reference/promised-consort-radahn/motion-v2/miquella_model_breakdown.jpg) | `BV1HJ4m1M7jD`，0-175 秒 | 7 秒 |
| [一阶段连斩](../../docs/assets/reference/promised-consort-radahn/motion-v2/phase_one_combo_dense.jpg) | `BV1W3YLe4Eek`，113-123 秒 | 0.125 秒 |
| [开场轮廓](../../docs/assets/reference/promised-consort-radahn/motion-v2/sword_arc_dense.jpg) | `BV1W3YLe4Eek`，65-73 秒 | 0.125 秒；最初选错区间，核看后确认为过场，不作剑术证据 |

四臂和独立光环继续服从项目设定，不因单帧遮挡擅自增减。没有可复用的授权原作骨骼曲线或精确帧表，本轮仍是视频指导的原创人工适配。

## 原创与发布

全部几何、像素纹样和关键帧由本工程制作脚本生成，参考用于人工适配，不是动作重定向或逐帧重建。未使用截图投影、原作贴图取样、解包模型、动作数据或对白音频；未导入第三方模型。

Three.js 只用于制作侧数学，不作为模组运行库打包。运行导出采用明确的四文件白名单，不复制参考目录。光环遮罩留在制作工程，游戏使用无自发光降级材质。角色知识产权和同人发布规则仍需项目所有者独立评估。