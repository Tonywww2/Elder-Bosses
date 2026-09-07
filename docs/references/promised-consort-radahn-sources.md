# 约定之王拉塔恩：资料与图片台账

> 最后核对：2026-09-07  
> 目的：区分原作事实、社区整理与本项目原创改编，并记录本地研究图的版权状态。

## 1. 证据分级

| 等级 | 定义 | 使用方式 |
| --- | --- | --- |
| A | Bandai Namco / FromSoftware 官方页面或补丁说明 | 可用于确认官方措辞、版本调整和宣传视觉 |
| B | 游戏内物品描述、可重复观察的战斗行为、社区 Wiki 数据页 | 用于技能结构、阈值、韧性和视觉细节；实现前仍需实机复核 |
| C | 攻略作者的命名、主观安全位和战术总结 | 只作为设计启发，不当作精确原作数值 |
| D | 本项目原创改编 | Minecraft 数值、tick 时序、范围、多人规则、技术架构和原创台词 |

## 2. 核心来源

| 来源 | 等级 | 本项目采用的信息 | 限制 |
| --- | --- | --- | --- |
| [Promised Consort Radahn - wiki.gg](https://eldenring.wiki.gg/wiki/Promised_Consort_Radahn) | B | 约 65% 转阶段、第一/二阶段韧性 125/120、招式结构、25% 大荒星陨；环境段称竞技场为 `very wide area`，地表粗糙、灰白，远端为神之门 | 社区维护页面；未提供竞技场绝对尺寸，招式名并非全部为官方中文名 |
| [Shadow of the Erdtree 官方页](https://en.bandainamcoent.eu/elden-ring/elden-ring/shadow-of-the-erdtree) | A | 米凯拉舍弃一切并等待约定之王的世界观措辞、官方宣传视觉 | 宣传页不提供精确战斗参数 |
| [Patch 1.14 官方说明](https://en.bandainamcoent.eu/elden-ring/news/elden-ring-patch-notes-version-114) | A | 2024-09-10 调整最终 Boss 开场行为、部分动作、伤害、精力伤害、非武器攻击范围，并改善攻击特效可见性 | 官方未逐项披露调整值；不可据此推算数值 |
| [神与王的追忆](https://eldenring.wiki.gg/wiki/Remembrance_of_a_God_and_a_Lord) | B | 米凯拉因拉塔恩的力量与温柔选择其为王；掉落主题 | 页面文本为社区转录的游戏内描述 |
| [拉塔恩的大剑（王）](https://eldenring.wiki.gg/wiki/Greatsword_of_Radahn_(Lord)) | B | 年轻拉塔恩的黑铁双大剑、狮鬃装饰、王者连舞 | 武器玩家版数值不直接套用 Boss |
| [拉塔恩的大剑（光）](https://eldenring.wiki.gg/wiki/Greatsword_of_Radahn_(Light)) | B | 光速斩、双剑外观与圣光追击 | 武器玩家版数值不直接套用 Boss |
| [Unique Skill: Promised Consort](https://eldenring.wiki.gg/wiki/Unique_Skill:_Promised_Consort) | B | 双剑先后斩击、延迟光柱、旋转与十字落地的动作层次 | 页面帧数据用于节奏参考，不按 30 FPS 直接换算 Minecraft tick |
| [Unique Skill: Lightspeed Slash](https://eldenring.wiki.gg/wiki/Unique_Skill:_Lightspeed_Slash) | B | 发光、跃进下劈、延迟多次光击的结构 | 玩家战技与 Boss 版本存在差异 |
| [Light of Miquella](https://eldenring.wiki.gg/wiki/Light_of_Miquella) | B | 光环先生成，约 3 秒后主光柱爆发，随后出现较小光柱 | Minecraft 改编缩短时长并扩大可读边界 |
| [Circlet of Light](https://eldenring.wiki.gg/wiki/Circlet_of_Light) | B | 神性回归时的光冠、正在消散的视觉和慈悲纪元意象 | 只用于造型与掉落主题 |
| [Enir-Ilim](https://eldenring.wiki.gg/wiki/Enir-Ilim) | B | 塔之地的遗迹迷宫与 DLC 主线终段；由螺旋阶梯和多层升降路径构成，神之门是核心地标 | 用于确定接近路线、远景建筑和神之门主轴，不换算原作尺寸 |
| [Gate of Divinity](https://eldenring.wiki.gg/wiki/Gate_of_Divinity) | B | 约定之王战场的核心地标与最终区域视觉锚点 | 页面正文信息有限，具体形态主要依据文件页截图观察 |

## 3. 对用户初始资料的校正

| 初始资料 | 核对结果 | 文档处理 |
| --- | --- | --- |
| 约 50% 生命进入二阶段 | 当前资料页记录约 65% | 用户已确认采用 65% |
| 韧性固定 120 | 资料页列第一阶段 125、第二阶段 120 | 只保留为原作事实；Minecraft 实现改用实际生命损失按比例累计的硬直值 |
| 二阶段所有派生强制附加激光 | 原作表述更接近「大多数一阶段攻击伴随光柱」 | 设计为所有实体剑击产生回响，纯重力和特殊状态技能不机械追加 |
| 「彗星冲击」与「大荒星陨」可能混称 | 资料页区分 Light of Miquella、Promised Consort 与 Consort's Meteor Rain | 文档分别使用米凯拉之光、王者连舞、大荒星陨三个动作 ID |
| 右手侧 1:30-3:00 最安全 | 属于攻略型站位总结 | 保留为二阶段光柱布置原则，但不是永久无风险区 |
| 半径 28 足以表达原作场地 | 只比马莲尼亚半径 26 大 7.7%，与 `very wide area` 的文字描述及全景图净空不符 | 调整为半径 40；相对马莲尼亚半径比 1.54、面积比约 2.37 |

### 3.1 竞技场尺寸调查边界

公开 Wiki、区域页和现有文件页均未提供可复核的世界坐标、碰撞边界或绝对米数；公开搜索和可访问地图资料中也未找到可靠测量。因此本文不声称“原作半径为某个精确数字”。

当前 Minecraft 半径根据两条证据确定：一是环境文字从马莲尼亚的 `wide cavern` 提升到约定之王的 `very wide area`；二是 `07/08-gate-of-divinity-grace` 与 `09-malenia-arena-grace` 等全景图中，以赐福/角色高度、重复铺地模块、边界栏杆间距和门体基座作为多个尺度锚点，得到约定之王可战斗净空半径约为马莲尼亚 1.45-1.70 倍的宽松区间。采用 40:26（1.54）位于该区间中部偏保守位置，同时为 16 格突贯、半径 13 格星陨外环和大范围圣光留下足够换位空间。由于镜头 FOV 与位置不同，该估算不使用单张截图的裸像素比例。

## 4. 本地图片台账

`01`-`06` 为 640 像素研究预览，新增竞技场资料 `07`-`12` 为 1280×720 预览。文件页中的 `License|game`、Fairuse 或空缺模板只用于记录来源状态，不表示模组获得再分发授权；所有图片仅供内部研究，不受 wiki.gg 页面正文的 CC BY-SA 许可覆盖。

| 本地文件 | 研究用途 | 来源文件页 | 原图信息 |
| --- | --- | --- | --- |
| `01-phase-one-silhouette.jpg` | 第一阶段整体、双剑和红鬃剪影 | [Promised Consort Boss.jpg](https://eldenring.wiki.gg/wiki/File:Promised_Consort_Boss.jpg) | 2560×1440，页面标记 copyrighted/fair use |
| `02-phase-two-silhouette.jpg` | 米凯拉附身后的双人轮廓 | [Consort of Miquella Boss.jpg](https://eldenring.wiki.gg/wiki/File:Consort_of_Miquella_Boss.jpg) | 2560×1440，页面标记 copyrighted/fair use |
| `03-omen-horns.jpg` | 手臂与腿部的角质增生 | [Radahn Omen Horns.jpg](https://eldenring.wiki.gg/wiki/File:Radahn_Omen_Horns.jpg) | 1920×1080 游戏截图 |
| `04-gate-arena.jpg` | 神之门、灰白地面和竞技场纵深 | [Promised Consort Radahn Gate.jpg](https://eldenring.wiki.gg/wiki/File:Promised_Consort_Radahn_Gate.jpg) | 2560×1440，页面标记 copyrighted/fair use |
| `05-radahn-closeup.jpg` | 面甲、红发、甲片和旧金层次 | [Promised Consort Radahn CloseUp.jpg](https://eldenring.wiki.gg/wiki/File:Promised_Consort_Radahn_CloseUp.jpg) | 2560×1440，页面标记 copyrighted/fair use |
| `06-miquella-appearance.jpg` | 白金长发、四臂、光环和附身关系 | [Miquella Appearance.jpg](https://eldenring.wiki.gg/wiki/File:Miquella_Appearance.jpg) | 3840×2160 游戏截图 |
| `07-gate-of-divinity-grace.png` | 场心石板、灰白覆盖层、外围残骸脊和门体基座 | [ER Site of Grace Gate of Divinity.png](https://eldenring.wiki.gg/wiki/File:ER_Site_of_Grace_Gate_of_Divinity.png) | 1920×1080；文件页标记 `License|game`；本地 1280×720 |
| `08-gate-of-divinity-grace-2.png` | 神之门正面全景、中央狭缝、低边界栏杆和金色天空 | [ER Site of Grace Gate of Divinity 2.png](https://eldenring.wiki.gg/wiki/File:ER_Site_of_Grace_Gate_of_Divinity_2.png) | 1920×1080；文件页标记 `License|game`；本地 1280×720 |
| `09-gate-of-divinity-wide.jpg` | 战斗中门缝逆光、地表残骸和角色尺度对照 | [Gate of Divinity.jpg](https://eldenring.wiki.gg/wiki/File:Gate_of_Divinity.jpg) | 1920×1080 游戏截图；文件页未声明自由许可；本地 1280×720 |
| `10-gate-of-divinity-key-art.png` | 神之门有机侵蚀表面、不对称双体量和中央短阶 | [Gate of Divinity Shadow of the Erdtree.png](https://eldenring.wiki.gg/wiki/File:Gate_of_Divinity_Shadow_of_the_Erdtree.png) | 1920×1080 游戏图像；文件页未声明自由许可；本地 1280×720 |
| `11-divine-gate-staircase.png` | 神之门前宽阶梯、窄踏步、栏杆和开裂铺地 | [ER Site of Grace Divine Gate Front Staircase.png](https://eldenring.wiki.gg/wiki/File:ER_Site_of_Grace_Divine_Gate_Front_Staircase.png) | 1920×1080；文件页标记 `License|game`；本地 1280×720 |
| `12-enir-ilim-overview.png` | 恩尼尔·伊利姆高拱、细柱、悬空体量与金色天空 | [ER location Enir-Ilim.png](https://eldenring.wiki.gg/wiki/File:ER_location_Enir-Ilim.png) | 1920×1080 游戏截图；文件页未声明自由许可；本地 1280×720 |

缩略图通过 wiki.gg MediaWiki API 的 `imageinfo` 获取；新增素材核对日期为 2026-09-06。保留文件页链接而不把缩略图 URL 当作永久来源，因为媒体缓存查询参数可能变化。

## 5. 版权与发布边界

- 《ELDEN RING》及相关人物、美术和截图版权归其权利人所有。本项目文档不声称拥有这些内容。
- wiki.gg 的页面正文通常以 CC BY-SA 4.0 提供，但单独标记为 copyrighted/fair use 的游戏截图不因此变为自由素材。
- `docs/assets/reference/` 必须从 Gradle `processResources`、发布 JAR、资源包和宣传素材中排除。
- 正式模型、纹理、动画、音效、图标和竞技场结构必须原创或具有明确可再分发许可，并另建资产许可证台账。
- Boss 台词只借鉴原作的角色关系和叙事情境，正式字幕文本为本项目原创；不制作对白录音，不得把文档台词标注成原作引文。拉塔恩战吼、呼吸和受击等非语言音效必须原创或具有明确授权。
- 发布前若项目仍直接使用拉塔恩、米凯拉等受保护名称与角色形象，应由项目所有者自行评估同人模组发布平台规则与权利风险。

## 6. 仍需实机核验

社区资料足以形成实现稿，但下列事项在编码完成前应通过当前原作版本录像逐帧或实机确认：阶段阈值的精确触发条件、每个基础连段是否都产生第二阶段光柱、不同分身斩的数量与间隔、1.14 之后开场动作权重、原作韧性恢复的精确速率。核验结果若与本文冲突，应先更新此台账，再调整战斗规格；Minecraft 的按伤害累计硬直机制属于独立改编。