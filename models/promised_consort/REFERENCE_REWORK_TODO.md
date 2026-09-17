# Radahn Reference Rework

## 单版本整理后的状态

2026-09-18：旧快照、候选工程和全部生成预览已按用户要求删除。下文旧文件名、哈希与截图观察仅为历史文字，不表示图片仍存在或旧重建命令可用。
当前入口见[current_assets.json](current_assets.json)与[维护说明](../README.md)，未完成项见[runtime_validation.json](runtime_validation.json)的 `remaining`。
截图按需重新生成，不在Git中保存；清理不等于全招制作或世界验收完成。

Date: 2026-09-17. Status: user reported "no problems" after the previous delivery; retain that revision. Additional item11 requested after the earlier work. This general feedback does not establish every technical acceptance subcase or exact original move naming.

Reference: https://www.bilibili.com/video/BV1tyeyenEBB

## 当前任务：全身发力再审查

2026-09-17反馈：当前动作仍缺少原作的冲击力，要求检查全部招式，尤其斩击，仔细分析原作后再联动躯干与四肢调整。这不是对上一轮单招总体认可的撤销，也不能继续用“均已实装、只剩验收”描述本次新要求。2026-09-18继续指令后，当前状态为**十招修改已正式接入；全招原片细查与动态复核未完成，尚未最终验收**。下方旧候选段落保留其当时状态，不代表当前未部署。

### 2026-09-18右起交叉正式细化

已完成右起交叉专用发力修订并接入正式资源，不只是候选：第一刀增加反向蓄势和胸肩前压；20至32tick保留侧身高位蓄势；41tick终结扩大转身下压，46至60tick保留低位余势。骨盆与腿部联动，使用原有脚步轨迹及脚掌方向，不补造参考遮挡下的脚步。总长75tick、接触11/41、68至75tick恢复、双臂局部握持、其余43段、几何、贴图和玩法保留。

- 601个1/8tick支撑样本通过，最大脚轨迹差0.005631模型单位，脚掌方向差0.011677度，最大半tick等效关节变化8.351811度。两次蓄势到接触的头颈位移约17.02→28.47、21.10→31.89；这些是制作测量，不是原作力量或3D角度数据。最低刀尖Y为6.785424，仍在模型地面以上，未据此宣布这招触地碎石覆盖完成。
- 查看唯一一张七时刻双阶段前后对照 (historical artifact removed)，192585字节、28格，制作tick6/11/26/32/41/46/60。前压、持续高位蓄势、终结下沉和低位制动可见，采样未见明显握持脱离；人物较小，不能排除完整动作中的手臂/披风/金发穿插。Blockbench151姿态、1812位置与1208附身相对矩阵检查通过。主视频精确对应仍未建立，本轮不新增原片目视结论。
- 实际GeckoLib44段/737811个变换值通过；现有全库追步测试修复旧22招台账缺项，改调用生产配置快照、动作目录与动画时钟，7872组支撑辅助函数压力样本通过。真实模型权重另测48片段/21360次，低姿态6284次保留、高姿态78次关闭，均通过；不是完整实体渲染或世界验收。
- 音效按新刀路重测，48个接触/76次本体刀声、六类分身、49种合法阶段/变体合同保持。双加载器资源处理、模型/贴图/动画/音效字节一致性通过。v2全库重新独立渲染44段、37张细节图、72步态和3项前向出刀检查，见当前全库记录 (historical artifact removed)，旧v1图像未覆盖。完整资产门槛通过1685953项断言、16500个编辑器通道；全库生成并不代表逐图目视通过。

当前整合为 `force_chain_integration_v2`，正式动画SHA256 `6ad3090e0f05184223224d64ed03090cb5cb917a64b062ed2daf323255f39598`，制作工程 `9622d7761c470feb238d08c0c665f5c08ab404771f5c9b7fc997592e242289d0`，上版保存在 `.pre-force-chain-right-cross-v1`。`refine_force_chain.js --check-current-force-chain` 同时验证历史阶段和新增右起替换；不能重跑旧阶段覆盖当前成果。

剩余全招发力细化与逐招触地姿态仍未完成，不是只剩世界验收。用户原先要求的“逐招原片细查后再改”和单轮一图限制仍同时有效：本轮图像额度已用于右起模型对照，下一招需要独立原片观察，不能用已有数值审计替代或批量套这招曲线。未操作Minecraft，未修改安全规则。

### 2026-09-18实装缺口续作

- **已补齐代码缺口**：原触地碎石只检查当前刀根至刀尖、上一帧刀尖至当前刀尖，刀身中段越过窄凸起时会漏判。现以真实刀根/刀尖沿刀长采样扫掠，目标间距0.25格、最多32段，连同当前刀刃最多34次射线/刀/帧；优先当前刀刃命中，只接受实际碰撞形状的非内部顶面命中，保留当地材质、无流体、每刀/每窗口去重与原粒子数量上限。不延长物理刀刃，不用发光刀轨碰地，不删除方块或改变伤害。
- **已补齐连续性保护**：上一样本必须来自同一动作、同一挥刀窗口和同一侧，动画时钟不得倒退，采样间隔须大于0且不超过1tick，两端移动均不超过4格；否则只检查当前刀刃，不连出虚假的长扫掠。首帧直接触地仍生效。此检测是有界线性采样，不保证所有极薄形状或低帧率下的旋转弧线都被覆盖。
- **验证**：真实Minecraft `VoxelShape` 的方块和半砖测试复现原中段漏判，并验证顶面高度、墙/底面/内部命中、空中刀路、无效/旧样本、跳变和34射线上限，专项33项通过；现有动作回归19859项通过，Forge/NeoForge离线编译通过。未进世界，实际地形、帧率和视觉效果仍待用户复测。
- **仍有未完成制作**：全26招尚未完成各自的参考发力细化；八招原本只恢复了被抵消的上身位移，后续右起交叉已单独增加转体/下压，其余七招不因此视为完成。动画中的刀刃若尚未实际触地，检测扩展不会凭空出碎石，仍需逐招确认触地姿态与刀路。双阶段/远程叠层、附身穿插和世界验收也未完成。
- **右起依据已补看**：右起交叉57个隔帧样本 (historical artifact removed)，补充参考A的96.600至100.333秒，原帧2898至3010每2帧取1帧、最大间隔0.067秒。可见第一刀余势后侧身高位蓄势，再转身前下压带出双刀、低持制动；玩家与披风遮挡部分脚步。后续已完成上方正式细化，但不把补充参考当成指定主视频的精确3D复刻。

### 2026-09-18正式整合v1历史

- 左起交叉v4已替换正式片段；八招恢复被反向补偿抵消的上身重心传递；王者连舞40至84tick旋扫的两侧大腿、脚踝采用同分支欧拉角与609个自适应中间关键帧，保留每个原关键姿态。其余34段、模型、贴图、伤害、时长和命中合同未改。音效按最终刀路重新测量，六个+3dB声音文件不变。
- 八招共2628个样本，脚轨迹变化为0；恢复的整体位移约2.95至6.84模型单位，不是额外放大转体角度。旋扫插值角误差降至0.05度以内，脚踝相对关键帧连线的最大偏差3.504→0.326模型单位；不据此宣称全部脚掌无滑动或所有招式力度足够。
- 26招、48个刀接触重新审计，无此前的上身位移抵消。实际GeckoLib44段/732411值、19584组追步保护、21414次真实模型权重、79811条时间映射断言、49种合法阶段音效合同通过。双加载器离线资源处理及动画/几何/贴图/音效字节一致性通过；没有操作Minecraft。
- Blockbench完成2092个前后姿态采样、25104次骨骼位置和16736次附身矩阵检查。查看了十招同机位前后对照 (historical artifact removed)，328613字节，每招一个时刻、两阶段共40格。左起下压明显，八招位移修复较细微，不能当作同等转体增幅；这不是全动作连续目视、原片复核或表面穿插验收。20个临时审查副本经内容核对后移除，原审查工程45段保留。
- 总资产制作检查已按版本衔接：先独立验证发幕扩展，再用对应历史快照检查旧原型，验证后续修改链，最后逐键检查当前44段。`node models/promised_consort/scripts/validate_assets.js --authoring-only` 通过1674880项断言、16500个当前编辑器通道。续作已完成独立全库采样渲染：44段、626个姿态、37张细节图、72个步态样本、3项前向出刀检查，见全库渲染记录 (historical artifact removed)。序列按当前刀窗口与九个均匀时刻采样，固定每招机位和画面尺度，输出至独立目录，不覆盖历史预览。完整 `node models/promised_consort/scripts/validate_assets.js` 现通过1675153项断言，原 `Stale geometry previews` 阻塞已解除；渲染和字节校验不是逐图目视认可。
- 本次续作发现并修复两张细节图的旧时刻：`lion_impact` 从22改为当前落地刀32tick，`consort_finisher` 从68改为当前终结111tick。新增时序校验先准确拒绝旧图，再通过修复后的图；两张新图及更新总览使用新文件名，旧图和44段序列保留。修复模式重复执行不改报告或图像。正式动画、制作工程、运行资源和玩法本次没有更改。

正式动画SHA256为 `af6bc159372c056e0249859c1185e5712b198d47e976d6e23046faa05a3326c0`，制作工程为 `a6b0d9bbc20151487c30a71c7377636b86802e23ea30f96180ecb7bd4528fc9e`。三个阶段备份依次为 `.pre-force-chain-left-v4`、`.pre-force-chain-ground-transfer-v1`、`.pre-force-chain-spin-interpolation-v1`。当前复查用 `node models/promised_consort/scripts/refine_force_chain.js --check-current-force-chain`；旧候选生成及旧阶段重应用有保护，不可重跑覆盖后续工作。详见[当前整合检查记录](runtime_validation.json)。

仍需：其余招式独立原片细查、已生成全库图的逐招目视与连续多角度动作复核、动态手臂/披风/发幕穿插和触地碎石覆盖，以及用户操作客户端进世界验收。44段制作片段不等于26个技能及所有阶段/远程变体都完成视觉检查，三种独立防御片段也不在这44段内。用户要求连续推进，不再逐招等待确认；图像工具每轮一图的安全规则仍有效。本次续作的终端状态结果意外附带多张图像，此后只用文字工具，未新增原片或模型目视结论，未修改规则文件或假报全招完成。

- 已对26个技能、48个挥刀接触完成模型空间运动排查，见逐招运动审计 (historical artifact removed)。这只是数值筛查，不是26招的原视频细查。四个远程变体的运行时叠层、六类分身和二阶段附身仍需结合各自时钟复核。
- 确认前一轮9个地面招式把骨盆新增前移用 `body.position` 反向抵消：左起交叉、血焰、右起交叉、右起左双斩、掀地连段、唤星、十字斩、圣光环、强化掀地。在骨盆相对旧版移动超过1模型单位的样本中，头颈相对旧版位移几乎为零。这说明步法修订没有同时增加上身推进，不代表原动作上身完全不动，也不证明这是全部力量感不足的唯一原因。
- 本轮仅亲自查看了一张左起交叉连续图 (historical artifact removed)：既有补充参考A `BV1W3YLe4Eek` 的100.533333至104.733333秒，42帧、每0.1秒一帧、680904字节。不是本轮主参考 `BV1tyeyenEBB` 的逐帧核对；原片30fps，每两个中间帧未显示，细到1至2原帧的传力先后不能由该图确认。其他25招没有在这次重新逐帧目视，不将历史观察笔记计为新审查。

### 首招观察：左起交叉连段

下表时间为上述补充片段的相对时间，需加100.533333秒。阶段边界是取样观察范围，不是原作伤害判定帧。

| 相对时间 | 可见证据 | 对当前制作的意义 | 不可据此确定 |
| --- | --- | --- | --- |
| 0.0至0.6秒 | 第一刀展开，胸肩轮廓由正向转侧，头部/肩线随挥刀前压，非持刀手用于平衡 | 蓄势与挥过分开，胸肩和腰胯共同送出第一刀，不能只转前臂 | 镜头运动下的精确身体位移、髋关节角度、脚底滑动量 |
| 0.7至1.1秒 | 反向刀弧接续，胸肩换向，第一刀余势变成下一刀蓄势 | 连斩之间不回待机，左右旋转应接力，不在每刀后重新站直 | 手、肩、胯谁领先1至2原帧；刀弧不是可靠内部命中时钟 |
| 1.2至1.6秒 | 双刃回带、身体从低位收拢并升起，末击的蓄势区别于前两刀 | 先收剑、再立躯干蓄势，不能把第三刀直接接成水平横扫 | 遮挡下胸腹的绝对弯曲角度 |
| 1.7至2.0秒 | 抬右膝、交叉双剑，躯干相对直立；不是左脚再往前走一步 | 当前步法抹去了终结前高抬膝，应恢复右膝悬停与左侧支撑 | 左脚足底精确接地、躯干质心投影和完整重心动力学 |
| 2.1至2.3秒 | 腿落下、身体迅速前下压、双刀向下外侧展开，随后出现尘浪 | 落脚、压身与双刀释放应联动，躯干不能通过反向位置通道停在原处 | 刀尖是否每帧实际触地、尘土是否等于额外物理接触 |
| 2.4至2.9秒 | 飞石/尘浪大量遮挡身体 | 不从遮挡帧凭空补脚步或新接触；保持已确认动作连续性 | 本体与玩家受击移动、腿部与刃口细节 |
| 3.0至3.8秒 | 双刀低持展开，身体保持余势后缓慢恢复 | 需要低位制动与恢复，不把全部收势压成瞬间弹回 | 原作可受击恢复窗、AI下一招等待数值 |

**首招隔离候选**：候选动画 (historical artifact removed)、对照制作工程 (historical artifact removed)、检查记录 (historical artifact removed)。工程保留原44段并追加 `animation.promised_consort.left_combo_cross_force_chain_v1`，不会替换正式片段。

候选重新编排胸腹转向、骨盆移动、右膝抬起/停留/落脚、左脚支撑与低位收势，不再把上身前移抵消。控制数值是按可见方向制作的项目候选，不是视频测得的3D关节数据。保持84tick与11/21/44三次命中，伤害/范围/次数/服务端位移不改。两把刀的局部握持链和模型比例不变，但世界空间刀路会随躯干改变，需重新视觉检查及部署后音效测量。

候选离线结果：实际GeckoLib解析1片段53628个变换值；673个1/8tick样本验证双脚目标与脚掌朝向，最大插值偏差0.0442模型单位、脚掌倾斜约0.0083度；半tick等效关节增量最高24.984度。右脚在末击蓄势时抬高15模型单位，随后骨盆下降5.5、头部下降10.748模型单位，低位余势保持；这些是候选的机械检查，不是原作精确数值或视觉质量证明。当前候选刀尖最低仍高于地面17.055模型单位，不能据此宣称补齐了实际刀刃触地碎石。

运行层检查：`PromisedConsortModel.applyPursuitGait` 是腿/裙甲加性层，不直接修改躯干；它保留脚掌方向并避免降低原脚踝高度。后续压力检查复现了高膝再次被抬高的干扰，已加入按原姿态高差控制的追步权重，见下方“主参考连续帧与高膝保护”；这不是移除服务端追击。

### 剩余审查顺序

1. 在指定主视频定位左起交叉的对应完整动作，并补看蓄力/释放/制动原帧；同机位同尺度对照原作、当前版和候选。当前候选仅是验证方向，不应先批量实装。
2. 逐招审查右起交叉、右起左双斩、血焰、双旋风和掀地；特别区分同手回切、两次旋扫间停顿、入地后拔起的相反发力。每招单独记录时间段、躯干与脚步依据、遮挡和不确定项。
3. 检查十字斩/唤星/强化掀地、狮子斩及追击、重力跃劈/螺旋/陨石；真实实体飞行与模型姿态叠加核对，不把低空动作当大幅骨盆位移。
4. 二阶段三种光速、王者连舞、新十字跃袭、米凯拉之光、光环、大荒星陨；附身/长发可能遮挡的部位需要侧面观察。三种原创防御无独立原作对应，按各自护持/吸收/释放语义检查，不能伪称视频还原。
5. 通过源片与候选播放后，才增量更新制作和正式动画、相关刀轨窗口/逐刀声音、当前版本全量检查；最后由用户进世界验收。本轮不启动或操作客户端。

**当前完成边界（2026-09-18更新）**：用户启动Blockbench后连接已恢复，隔离工程已更新至候选v2，不需要用户重启。正式动画仍为 `050c289bc169de4f7071f4b5a0ff02d0fa6e4de82c597a675fe5af2414875228`；正式模型、两贴图、76刀音效绑定及配置未改。生产Java仅新增客户端高膝追步保护，未改服务端移动、伤害或时钟。已完成的检查与尚未完成的全招原作分析、候选视觉审查和正式动画实装分别如下；本节优先于下方旧任务“完成待验收”的阶段性描述。

### Blockbench恢复后的进展

- 隔离工程保留原44段、追加1段候选，125骨骼、620方块、512贴图载入正常。先前18个关键骨点与实际Blockbench坐标一致。已查看一张192513字节、24格的同尺度前后对照 (historical artifact removed)，包含一阶段前斜面与侧面、6/11/21/38/44/60tick；候选的右膝蓄势、第二刀前压和低位收势更清楚，前两刀躯干变化仍偏克制。这是静态观察，不是连续动作认可。
- 上轮实时播放调用被用户取消；报告虽写有四条计时完成记录，但前两条只有1帧，后两条最大帧间隔751/600ms，**四条均不能计作连续播放通过**。原始数据保留，未自动重播。`review_force_chain.js` 现要求每秒至少20次渲染且最大间隔不超过250ms，遇到停顿中止，不再把计时结束当作播放通过；正常/不足帧/停顿测试已通过。
- 改用确定性半tick检查实际Blockbench姿态：169个二阶段姿态，肘弯55.365至138.075度，腕部最大绝对角约30/22.0055/15度，握柄仍归属于对应手骨；1352次米凯拉身体、四手和发梢相对胸腔的变换检查通过。它验证的是相对变换和关节限制，不能证明不存在手臂/金发穿插。记录见Blockbench审查报告 (historical artifact removed)。
- 指定主视频 `BV1tyeyenEBB` 的74至106秒已作一次半秒定位检查：64帧定位图 (historical artifact removed)，994755字节，时间标签需加74秒。74至80.5秒混有开场/切镜与重力动作，81秒后进入近身动作；约94.5秒可见高抬膝和双刀蓄勢、随后前下压，作为左起交叉终结的**待核对候选**。半秒采样不足以确认前两刀身份、精确传力顺序或完整招式边界，不据此直接改动画。
- 已提取主视频92至97秒全部150原帧、10页，30fps最大相邻时间间隔0.034秒，见[原帧台账](../../docs/assets/reference/promised-consort-radahn/motion-v10/force_primary_cross_candidate/study.json)。这10页本批尚未目视；不是10页审查已完成。本轮唯一图像检查额度用于上述主视频定位图，后续应从这段连续原帧继续，不再重复大范围粗略总览。
- 已为候选补上追步叠层检查，使用三份实际配置，当前三份均为同一71tick运行时序；每个运行时刻映射回84tick制作动画，再扫过步态相位和0/0.5/1权重。共19584组，其中2880组覆盖右膝蓄势；不压低原脚踝高度、脚掌方向保留、窗口关闭后权重归零，均通过。
- **追步增量风险未解决**：压力测试最大脚踝附加路程14.010892模型单位、额外高度9.345821模型单位。右膝蓄势最坏组合为运行33.75tick/制作37.75tick、步态30tick、权重1，脚踝附加路程9.784284模型单位。它可能改变候选的单脚支撑节奏，不能仅因“没压低脚”就宣称协调。这里是可能的步态组合，不是游戏已观察到的轨迹，也未包括最终渲染中全部欧拉角往返。具体案例见追步压力测试 (historical artifact removed)；尚未因此改生产追步算法。

下一批先对92至97秒连续原帧确认刀序、抬膝蓄势和落脚时点，再对照候选及记录的追步最坏姿态；其他25招仍需按各自参考分批细查，不能复制此招的曲线。当前不处于全招优化完成或用户实机验收阶段。

### 主参考连续帧与高膝保护

本批实际查看96帧连续核心段 (historical artifact removed)，699527字节，主视频92.4至95.6秒、原帧2772至2867，未抽帧。每帧从1280×720裁取 `[240,40,800,640]` 后缩至320×256；帧号与相对时间均标注，时间需加92.4秒。150帧全图超过1MiB，未查看；原10页也没有逐页查看，不能把两者计入本批审查数量。

- 片段从前一刀余势开始，起手身份未完整显示；披风和玩家遮挡胸肩与部分双臂，因此不能确认完整前两刀及精确肩胯传力先后。
- 后半段可见高膝蓄势、持刀短暂保持、落脚时躯干前下压、双刃向下外侧展开，随后尘土遮住下肢。这里支持“蓄势保持后再集中释放”的动作组织，不支持从亮帧或裁外刀尖反推新判定。
- 候选v2仅把终结抬膝提前至制作35tick到位并保持至40tick，再于44tick落脚出刀；前两刀候选曲线与11/21/44命中时刻不改。这是根据可见阶段的制作适配，不是从视频提取的精确角度或帧长。首招在主视频中的完整刀序归属仍未完全确认，不将其发布为完整复刻。
- 候选文件路径保持不变，修订为 `force_chain_left_cross_v2`，SHA256 `0e2e99a5db6e337d3a20649571a97502b8dee9acf3d3abffdfb6be8fd2328cb6`；上一候选保存在 `.pre-primary-hold-v2` 备份。673个插值支撑样本、首尾全骨架一致、实际GeckoLib53628值通过；新版Blockbench169姿态/1352附身相对矩阵检查单独按哈希记录，旧截图/取消播放未重绑为v2认可。

已生成当前版与候选对照视频 (historical artifact removed)：左右分别当前/候选，上排一阶段侧面、下排二阶段前斜面。按帧索引离线渲染107帧，每秒30帧，正确将71tick运行时钟映射到84tick制作动画；每帧哈希、时间和四格非空检查通过，编码后解码仍为107帧，视频大小913148字节。它不含音效、实体位移或追步叠层；编码时长3.5667秒包含向完整帧取整，不是改了技能时长。采用等比画幅，不重试被取消的实时计时循环。导出完整不等于助手观看了连续视频，更不是用户验收。

**已实装的客户端修正**：当原招式两脚高度差小于腿长15%时保留原追步权重；15%至35%之间按平滑曲线衰减，大于等于35%时关闭本帧腿部/裙甲附加步态。高度来自叠加之前的招式骨骼，避免附加动作反过来影响权重；左右对称，不按某个技能ID硬编码，不改正常走跑动画或服务端追击速度/预算。该阈值是保护已制作高膝姿态的工程规则，不是原作身体参数。

验证：双加载器编译通过。同一19584组候选压力测试中，抬膝段附加脚踝位移/高度从约9.78/9.35模型单位降至0，4608组高膝保持保护检查通过，脚掌方向和禁用权重检查保留；低姿态区间最大附加移动仍为14.010892模型单位，未宣称所有步法观感都修好了。另用真实GeoBone和模型权重入口，对44段正式+3段防御+1候选共48片段检查21414次权重，6301个低姿态保持原权重、78个高姿态关闭附加步态。测试不等同于完整游戏实体渲染或实机验收。

接下来仍需：核对主视频中的正确招式边界，观看v2对照视频评估节奏及穿插，再逐招推进其余25个动作。正式全身动画尚未部署，未启动Minecraft，不要求现在将全部技能重新验收。

### 起手区间复核与分批约束

用户明确选择“保留单图限制，继续分轮推进”。图像安全规则不作修改，不再反复询问是否放宽；全招原片细查与模型视觉对照需跨轮完成，不能宣称在单轮内完成了未查看的招式。

本批只查看主视频起手96帧 (historical artifact removed)：785760字节、89.6至92.8秒、帧2688至2783、完整画幅缩至400×225，没有抽帧或左右裁切。与上一批2772至2867帧重叠12帧，两次共192个显示帧位、180个不同原帧，覆盖89.6至95.6秒；不将重叠部分重复计为独立证据。

| 区间（近似） | 观察 | 本次结论 |
| --- | --- | --- |
| 89.6至90.6秒 | 高位持刀，肩带与身体保持蓄势，另一侧肢体张开平衡 | 这是持续的高位准备，不能仅凭左右剑的位置认作两次交替攻击 |
| 90.6至91.2秒 | 躯干迅速前下压、刀路下落，随后出现显著扬石 | 一次清楚的重下压释放；低位承重与下刀同向，不是只有手臂摆动 |
| 91.2至92.8秒 | 保持低位余势后逐步起身，尘土与披风遮挡部分肢体 | 恢复与下一次蓄势分开看；不能从尘土中的残留刀影计追加攻击 |

该检查**没有显示预期的“两次交替开路斩”**，因此撤销“89.6至95.6秒是完整左起交叉连段”的工作假设，不再把它作为候选前两刀的确定主参考。候选依据中的补充视频A仍独立保留；主视频只支持已看清的重斩发力组织，不据此确认完整技能ID或增加判定。

本批未改变候选关键帧、正式动画、模型、音效或生产代码，只记录此反证。`refine_force_chain.js --check --record-research` 在候选一致性和正式资源哈希通过后，仅更新来源字段；它不刷新候选工程UUID或重导对照视频。下一批优先利用已生成的v2对照视频进行候选视觉判断，另行定位主视频的正确连招边界，避免反复扩展这段不匹配的时间窗。

### v2对照复核与v3前两刀候选

已查看18时刻侧面动作对照 (historical artifact removed)，277930字节，来自已保存的v2固定帧视频，第0/6/.../102帧，间隔0.2秒；每个时刻成对显示当前版与候选。高膝保持、落脚和低位终结可区分，但前两刀躯干对比仍偏克制，不足以宣布满足“更有冲击力”的新目标。图像中的人物偏小，部分肢体重叠；这不是连续播放、二阶段穿插或精确原片还原验收。

已制作隔离v3候选，仅调整0至30制作tick的前两刀：扩大胸肩反向收紧与腰胯换向，蓄势后更明确地前送、下沉，再把挥后侧向余势交给反向一刀。脚目标保持原候选，腿部IK与裙甲随新骨盆重算；没有给躯干位移添加反向补偿。30tick以后全部骨骼的旋转/位移曲线与v2逐值相同，包括35至40tick高膝保持、44tick双刀终结与恢复。总长84、接触11/21/44不变；这些幅度是基于已有补充参考和候选不足作出的制作适配，不是主视频89.6至95.6秒的招式身份结论。

- 第一刀蓄势到接触的胸腔方向变化从约67.23度增至102.57度，头颈起终点间移动从16.04增至25.48模型单位；第二刀对应79.56→101.56度、12.41→19.01模型单位。它们只是候选变换的测量，不能据此宣称实际力量或视觉质量更好。
- `force_chain_left_cross_v3` 候选SHA256为 `af539e25672ea982929503dbddd777395278926c34b9cd7ab9554fe38d8c468d`，沿用原候选文件路径；v2动画、工程与报告保留在 `.pre-torso-drive-v3`。正式44段、模型与贴图、音效不变。
- 673个插值支撑样本、最大脚目标误差0.0442模型单位、半tick等效关节变化上限24.984度、首尾姿态及30tick后保护均通过。实际GeckoLib53628值、高膝追步19584组合、实际GeoBone权重21414次检查通过；高膝保持段没有附加脚踝位移。Blockbench另按v3哈希保存169姿态与1352附身相对矩阵检查，不继承v2截图认可。
- 新固定帧v3对照视频 (historical artifact removed)已生成，107帧、30fps，按71tick运行时钟映射84tick制作动画。初始近机位会裁刀，已被边界检查拦截；最终先扫描整段当前版与候选的头、脚、刀尖及二阶段头部，共用固定机位并留余量，每帧继续检查关键点在画面内。没有逐帧缩放。等比画面、全部帧哈希/时间/非空检查和编码后107帧解码通过。旧v2视频未覆盖。

v3视频SHA256为 `5447532ee22deb0f2f7c336d23e68d642a490f588d6882998e7e6d379c9dc7d6`，1072595字节；报告见视频校验记录 (historical artifact removed)。它不含音效、实体位移或追步叠层，尚未目视检查v3或由用户认可。该候选不替代其他25招的单独原作分析；下一批应先检查v3姿态与节奏，而不是重复生成同一视频或再次扩大已被否定的主视频区间。当前仍非最终验收阶段。

### 用户追加增幅：v4候选

用户反馈“躯干移动的幅度还可以更大”，因此v4进一步放大三次出刀的躯干动作，不仅是前两刀：蓄势更立起并反向收紧，释放增加腰胯侧向转移、胸肩换向和前下压，终结及后续余势更低。脚目标、35至40tick抬膝保持、84tick制作时长和11/21/44接触不变，腿部以现有IK重新求解。v3“30tick后曲线不改”的局部限制已被这次明确反馈扩展为也加强终结躯干，未改变脚的运动轨迹或玩法。

相对v3，三刀从蓄势到接触的头颈起终点位移分别约增加32%、40%、55%（25.48→33.56、19.01→26.65、26.61→41.25模型单位）；终结骨盆下降由5.5增至8、头部下降约17.89模型单位。这些是候选变换测量，不能当作原作力量数值。脚部插值误差最高0.051、脚掌倾角0.0131度、半tick等效关节变化仍不超过24.984度；刀尖最低Y约4.894，仍在模型地面上方，不据此宣称刀刃触地碎石完成。

候选修订为 `force_chain_left_cross_v4`，SHA256 `d4a8b8d769e35663d5fa704eb5e6ed9aefdc98162585a977991a524207201ed7`，文件路径仍为原隔离候选；v3保存在 `.pre-torso-amplitude-v4` 备份，原44段正式动画及音效不变。实际GeckoLib53628值、673支撑采样、19584追步组合和21414模型权重检查通过；Blockbench169实际姿态与1352附身相对矩阵检查通过。高膝段附加脚踝移动仍为0。

已生成固定帧v4对照视频 (historical artifact removed)，1010720字节、107帧/30fps，按71tick运行时钟映射84tick制作动画，旧v3视频不覆盖。已查看一张327799字节的六时刻v4对照 (historical artifact removed)：帧9/17/30/48/60/78，分别含一阶段侧面和二阶段前斜面，左右为正式当前版和v4候选。可见候选的肩腰扭转、出刀前压和终结下沉更明显；这些静态样本没有显示握持脱离，但不能排除完整动作中的手臂/披风/金发穿插。报告见视频与静态观察记录 (historical artifact removed)，未标为用户认可。

本次完成当前候选的追加增幅，不等于全26招动作优化完成；正式动画未替换，也未启动Minecraft。既有主视频招式归属限制仍保留，不因用户要求更大幅度就把错误匹配改为已确认。

## Boundaries

- User authorized this video's public page/API access and workspace-only research cache. No authentication, paid-quality bypass, or redistribution of video/audio frames in runtime assets.
- User authorized changing phase ticks and motion curves to match the reference. Preserve existing damage values until explicit decisions on new contacts/skills.
- Do not operate the Minecraft client. Offline checks are not in-world acceptance.
- Preserve current arena geometry/anchors, model proportions, unrelated edits, six deployed sound assets, per-blade sound rules, per-boss indicator switches, and ranged qualification/selection settings unless a listed task requires a confirmed change.
- Never rebuild current model from historical full-model generators. Keep authoring/formal/loader hashes linked when changing assets.
- Image inspection is limited to one image-producing result per assistant turn by repository safety instructions. Extracted frames are not automatically reviewed frames.

## Checklist

Each row tracks reference / implementation / offline verification / world review separately.

| User Item | Task | Reference | Implementation | Offline | World |
| --- | --- | --- | --- | --- | --- |
| 1 | Grounded Divine Gate waiting, gravity sword pickup, opening gravity rush/lion strike, purple blade lightning (~01:10) | Additional30frame opening/lion overview inspected; cuts/occlusion, not native-frame calibration | IMPLEMENTED: grounded dormant pickup pose,60tick pickup, eligible participant forced opening lion trajectory and purple blades; visual acceptance pending | Both compiles; arena4203; opener target/controller and trajectory tests; editor/export checks | Not run |
| 2 | Larger, reference-shaped leg travel during all swordwork phases; planted foot and support consistency | Existing contact/lift ledger; not newly calibrated native-frame poses | IMPLEMENTED:20clips, ten grounded stepping/weight-shift and ten action-specific lift/aerial windows; previous two lion clips retained. Pelvis reach solved, torso compensation retains blade contacts; no server timing/damage changes | Dedicated20clip/editor check;1656midpoints; actualGeckoLib43clips/649536values; both-loader resources. Inherited promised_consort ground-spin joint jumps remain unmodified and require visual attention | Not run |
| 3 | Distinct stone debris where a physical blade actually contacts ground (~01:30 and other contacts) | Exact all-skill ground-contact ledger pending | Contact implementation extended: current blade plus bounded blade-length sweep, same-swing/frame continuity guards, actual voxel top faces, local material and per-hand/window dedupe. Full authored ground-contact coverage still partial | Real voxel/slab regression33; full ActionDebug19859; both compiles passed. No world raycast/render acceptance | Not run |
| 4 | Genuine local scene distortion for gravity; purple lightning and holy blade attachment | Overview and user direction; detailed visual review pending | IMPLEMENTED: separate scene-color copy, local refractive billboards around active gravity boss/rocks, dark core and ring; purple/holy blade layers retained. Main-target-only fallback | Actual GPU shader compile/pixel test:7558/8158 shifted annulus pixels,5593 animated; zero/outside unchanged. Both compiles; pipeline/world acceptance pending | Not run |
| 5 | Gravity meteor: enchant swords, ground/player sweep, boss+rocks rise, rocks transform, aerial swing launches, phase-two body landing strike (~02:35) | Overview only; dense sequence review pending | IMPLEMENTED: initial4+0.8*Attack cut, real boss/rock rise, surface-block to charged item, original eight launches/four clones, P2 body5+0.8*Attack landing. Original rock damage/caps retained | Sequence/held-state/count/damage/config checks; exact21/144 contact clock mapping; four-clip asset checks | Not run |
| 6 | Lion claw: ground slash takeoff, aerial target lock, rotating second arc and landing (~02:45) | Opening/lion overview inspected;163..172.5 contains cuts/CG | IMPLEMENTED for normal/double: preparatory ground cut, takeoff, airborne lock, rotation and collision-checked landing; original single landing damage retained | Paths, shared frozen warning/landing and one damage event; pose continuity and editor/export equality | Not run |
| 7 | Light of Miquella: faster/higher actual takeoff, protected from easy melee (~05:50) | Prior overview shows high hover; precise original height not inferred | IMPLEMENTED: server rise/hover/descent;12blocks/16rise ticks/20descent ticks; collision gate, saved origin, gravity cleanup on cancel/end; original damage unchanged | Curve+blocked movement tests; defaults/config migration; both compiles passed | Not run |
| 8 | Bursts of 2-4 complete skills, then a distinct breathing interval | User-approved adaptation, not inferred video AI probabilities | IMPLEMENTED: controller groups,0..2link/24..36rest; selected final recovery6ticks within groups; full final-skill recovery; forced recovery wins; persistence | ActionDebug12106 overall; existing attack/audio/ranged regressions; both compiles and three config migrations passed | Not run |
| 9 | Phase-two Miquella hair covers more back/shoulder silhouette | One189610-byte six-panel software before/after textured bind-pose projection inspected; not game or Blockbench | IMPLEMENTED:80existing articulated hair cubes widened,125bones/620cubes retained; arms/body/nonhair/UV/texture/animation unchanged | Hair-only protection and both-loader hashes passed; static hair-projection samples back1164->1418,shoulder499->527; animation overlap and texel appearance pending | Not run |
| 10 | Identify and implement the skill around 05:15 | One237518-byte24frame sequence312.5..316.333 at1/6s inspected:gold charge, pass-through, delayed columns and distant recovery. Glare obscures exact clone count | Existing lightspeed_dash corrected to charge hold then frozen-route body rush; original4clones/body/trail retained. User reported no problems after testing; original formal naming/counts not claimed | Previous offline checks retained; actual Forge three P2 casts completed92ticks each and cleaned up | Forge P2 passed; no claim for every old checklist subcase |
| 11 | Additional move around05:17:both blades raised in a cross, horizontal leap toward player, then consecutive slashes | One343061-byte32frame sequence316.5..324.25 at0.25s inspected; cross lift, approach, slashes and airborne finisher visible, glare occludes some contacts | IMPLEMENTED independent cross_leap_combo; user selected separate skill/full five body hits+two clones+ring and approved defaults. Original43clips and old skills preserved | Debug19835;Attack806160/4320;Timeline79769;Sound49cases48contacts/76blades;GeckoLib44clips705252values;both compiles/resources passed | Pending new-skill test |

## Latest User Feedback

- 2026-09-17: User said "没有问题" after the footwork/lightspeed delivery and requested the separate05:17cross-blade leap/combo after prior tasks. Record general user approval and retain current assets/behavior; do not infer exact phase coverage, numeric damage, multiplayer, shaderpack or save/restore acceptance from this short feedback. Assistant still must not operate the Minecraft client.
- Forge log corroboration for the previous delivery:17:39:37.144 world login;three P2 ordinary lightspeed_dash tests (#418,#651,#874) completed92ticks and cleaned up at17:39:49.269,17:39:58.118,17:41:01.557;normal exit17:41:03.741 and all dimensions saved17:41:05.607. This verifies that test slice in-world with user's positive feedback, not all prior checklist items or the newly added cross_leap_combo.

## 追加第11项：十字跃袭连斩

用户明确选择“另做独立技能”，并确认完整五次本体判定、两道分身和末尾光环；采用同类招式默认值。新ID为 `cross_leap_combo`，中文名“十字跃袭连斩”，仅二阶段，独立权重0.5、冷却260 tick。不替换 `promised_consort`（王者连舞）、`lightspeed_dash` 或其他已认可技能。

流程：十字高举蓄势 → 起跳前锁点 → 低空平跃接近 → 两次交替斩 → 两次双刀回旋 → 实际腾空重斩 → 两道分身追击 → 末尾光环。默认151 tick，本体接触27/41/54/70/111，分身115/120，光环127。平跃自身无接触伤害；双刀同时挥动算一次本体判定、两次刀声。公共二阶段刀后圣光回响保留，额外回响不计入这八个主体阶段。

| 独立配置 | 默认 |
| --- | --- |
| leap_distance / leap_height | 16格 / 1.2格；不受攻击范围倍率放大 |
| opening_range / spin_range / finisher_range | 4.2 / 4.5 / 5格；沿用范围倍率1.45和已有近战扩展 |
| opening_damage | 每次3 + 0.55×BossAttack，2次物理 |
| spin_damage | 每次3 + 0.50×BossAttack，2次物理 |
| finisher_damage | 6 + 0.95×BossAttack，1次物理 |
| clone_damage | 每道1 + 0.20×BossAttack，2次圣属性来源 |
| holy_ring_damage | 2 + 0.35×BossAttack，1次圣属性来源 |

起手默认12tick起跳/冻结落点，27tick落地接首刀；终结默认87tick起跳、111tick落地，终结原地升降也用 leap_height。停靠沿用约4格公共分离距离。路径最大每tick2格、每0.25格做碰撞检查，落点必须有无流体的实体顶面支承、已加载、在场地内，允许落点与起点高差至多1格；完整飞行弧线在起跳时预检，移动中再检查。中断释放无重力覆盖并取消动作，不执行远处落地伤害。过短/过远配置无法在速度上限内到达时取消，不强制穿墙或强载区块。

新枚举追加到末尾，旧ID顺序不变。八段判定、选招、独立冷却组、瞬防、本体动画、圣光特效、两分身动画与声音、命令补全、中英文名称均已接入。旧遭遇快照通过既有fallback仅补缺失新技能，已有自定义值保留；锁点/飞行/重力/取消标记沿用既有动作持久化格式。三份配置各补23字段，备份 `.pre-cross-leap-v1`，重复检查通过。数值和阶段时长是用户批准的项目适配，不是提取原作伤害数据。

新片段由 `scripts/build_cross_leap.js` 增量制作，旧43段逐键保留，模型、125骨骼、620方块和两张贴图不变。十字姿态通过现有肩/肘可达方向选择，未改变手腕；两刀在模型高度105.17079附近交叉，37个四分之一tick保持采样通过。新增片段最大半tick关节变化20.46111度；连斩素材源于已有动作并对新片段单独重组，旋转段膝脚约束不继承旧片段的突跳。没有新的模型图像或Blockbench实播审查，这些数值不代表视觉认可。

当前动画SHA256 `050c289bc169de4f7071f4b5a0ff02d0fa6e4de82c597a675fe5af2414875228`，制作工程 `4ce226f77e2469eadcfc5156f1187e2f60eab8650b08dc3dc603e93eef4ae3b7`，金发几何仍为 `2ba7aa4f976936ed2584052de8f15b89b4b3339f1c8e52c29d05ba1f994601e2`。正式44段动画由实际GeckoLib解析705252个变换值通过；旧43段、非动画工程字段、两版本资源哈希均通过保护检查。音效增加五接触/八刀声，总48接触/76刀声，六个OGG音色与六类分身剖面保留。

离线完成：新招/存档/非法配置/锁点专项19835断言，全攻击计划806160/4320事件采样，时钟79769，音效49合法阶段/变体组合，远程与独立指示器回归，双加载器编译与资源处理。数据序列化测试不是完整世界保存重进模拟，旧全量视觉/预览门槛未重绑为新招认可。新技能仍需用户进世界测试；助手未操作客户端。

### 新技能验收入口

```mcfunction
/elderbosses test promised_consort cross_leap_combo 2
```

先在平坦空地观察完整流程。默认测试Boss只在前方约6格，平跃会因约4格停靠而较短；要观察中距接近，保持视线水平后可指定更远生成点：

```mcfunction
/elderbosses test promised_consort cross_leap_combo 2 ^ ^ ^14
```

- 十字高举清楚，两刀交叉；平跃主要向前，不演成狮子斩翻滚。
- 到达冻结落点才出首刀，随后2交替斩、2双刀回旋、腾空重斩、2分身和光环完整连贯。
- 刀刃圣光、逐刀声音、瞬防与预警对应；空中移动时首刀/终结预警留在地面锁点。
- 墙边或途中障碍可中断，不穿墙、不留悬浮实体或幽灵伤害；生存模式另检查实际命中，创造模式只验动作。
- 正常二阶段遭遇能独立选出此技能，禁用该节后不再出现；原王者连舞与光速突贯仍保持已认可行为。

权限要求2；每次等上一招清理后再执行。阶段1应被拒绝。单招测试会清理残余危险区，完整寿命、多人和读档行为仍需正常遭遇复核。反馈请带加载器、ID、现象与视频对照点；新招尚未打勾验收。

## Order

1. Decode the supplied video; identify exact intervals, especially the opening and 05:15. Capture timing and coverage evidence, including ambiguity and occlusion.
2. Agree on new attack contacts, unknown skill mechanics and burst/rest values. No inferred Elden Ring damage numbers or world distances.
3. Gate opening and targeted movement corrections: opening, gravity meteor, lion claw, Light of Miquella. Complete one behavior slice and focused tests before the next.
4. Per-skill leg motion, ground-contact events, per-blade audio remeasurement. Do not stretch an entire animation indiscriminately.
5. Debris, gravity distortion and blade enchantments; retain readable instant guard and configurable range indicators. Verify actual framebuffer sampling if claiming distortion.
6. Burst/rest selection, new skill and complete runtime/client profile registration.
7. Hair coverage revision preserving unrelated geometry and animation transforms.
8. Both-loader compile/resources, asset/clock/attack/audio/arena checks, then user-led world review.

## Current Local Evidence

- Before this task, PromisedConsortEntity.beginEncounter set a bound boss to standingAnchor("boss_spawn"). It now reuses holdArenaDormantPosition for bound activation.
- tickIntro holds a bound boss at gate-ground position during the60tick pickup. A bound, non-test encounter then starts lion_claw against an eligible participant when that skill is enabled; unbound/test behavior is retained.
- Arena binding now keeps dormant Boss on phase_return, facing arena_center. The latest grounded dormant policy must not be reverted.
- First falsifiable opening hypothesis: the jump from phase_return to boss_spawn causes the wrong high-altitude opening. A focused arena/intro check must reject any bound activation that leaves the gate before its pickup stage, and verify all four arena rotations.
- Existing gravity_meteor, lion_claw and light_of_miquella are concrete starting points, not evidence they already match the new reference.
- Existing documents for previous videos and prior visual acceptances are historical baselines, not acceptance of this revision.

## Verification Gates

- Every code edit: relevant narrow check first, then adjacent followup work.
- Every modified attack: correct stage/lock/contact count, cancellation/target loss, terrain/arena bounds, persistence, per-blade sound alignment.
- New action: catalog, selector, commands, NBT fallback, client VFX/animation/audio registrations and bilingual names all covered.
- Animation assets: actual GeckoLib deserializer, numeric key ordering, feet/support and grip checks. Update sound-angle profile source hashes only after deliberate remeasurement.
- Visual effects: no fake contact debris, bounded particles/meshes, viewport/depth/cancel handling, no perpetual effects. Real distortion requires actual scene texture sampling and fallback behavior.
- Runtime acceptance: user actually enters world; no borrowed logs or main-menu-only success.

## Progress Log

- 2026-09-17: User approved video research and phase/movement edits; damage values preserved pending new-event decisions.
- 2026-09-17: Source page identified; decoder quality/frame rate and actual frames not yet verified.
- 2026-09-17: Public stream verified: 1280x720, 30/1 fps, quality64, duration478s; source SHA256 0bac42a722784aac8ac11242015c15728f5280144209ecc275613d459a9c99b6. Title says pre-nerf; no 4K or current-patch claim.
- 2026-09-17: Decoded 308-324s with existing --dense runner: 240 samples / 15 pages, max gap0.067s. These pages are NOT individually inspected.
- 2026-09-17: Inspected exactly one 637747-byte overview containing 36 frames. Row times: [66,68,70,72,74,76], [87,89,91,93,95,97], [151,153,155,157,159,161], [163,165,167,169,171,173], [309,312,315,318,321,324], [346,349,352,355,358,361]. Absolute PTS labels. Includes a black transition frame at68s and occluded frames; not full-sequence calibration.
- 2026-09-17: Gate-ground entry slice compiled on Forge and NeoForge; ArenaContractCheck passed4203 existing binding/rotation/resource checks. No game launched, no frame/model changes, no new skills, no damage changes.

## Research Files

- [Public source metadata](.tools/reference-video/BV1tyeyenEBB_p1.json) and cached video remain under ignored .tools.
- 36-frame overview (historical artifact removed).
- [05:15 dense timing ledger](../../docs/assets/reference/promised-consort-radahn/motion-v10/reference_rework_0515/study.json).

## Approved Defaults (User Requested Implementation)

1. Meteor initial sword contact 4+0.8*BossAttack and phase-two body landing 5+0.8*BossAttack, each one new configurable event; original rock damage/caps retained. These are project defaults, not video-extracted damage values. Implemented; world review pending.
2. Lion claw initial ground cut: motion/ground-debris only, existing landing damage count retained; opening gravity rush reuses lion-claw landing formula. Implemented; world review pending.
3. Burst cadence: 2-4 complete skills, lion followup grouped with parent; extra idle0-2ticks and rest24-36ticks. Selected ground combos keep at least6ticks of final recovery within the burst, full recovery on its last skill. Only intra-burst links use ranged idle scaling. Seven settings exposed under selector.burst; implemented and offline verified.

- 2026-09-17 continuation: Viewed one518436-byte 30-frame sequence at309..323.5s with0.5s spacing. Distinct sequences: bloodflame up to312.5; golden charged pass-through/rush around313..316; separate near-melee combination from318. Exact identity/counts of313..316 remain under review; do not merge the following combo into it or claim native-frame inspection.

## Implementation Batch 2026-09-17 05:08-05:23

- Cadence settings in selector.burst; defaults2/4/0/2/24/36/6. Snapshot fallback handles absent/partial old settings. Controller saves RemainingSkills/WaitTicks, rebases waiting on load; cancellation clears the group, lion followup shares its parent slot. Forced recovery is checked before shortening recovery and before branch selection. Cooldown eligibility remains authoritative, so an unavailable next skill may still cause waiting.
- Recovery shortening is restricted to six left/right combos, cross_slash and stomp. Only their final RECOVERY after all active stages is eligible; no attack/animation key changes. The last skill retains full recovery before the group rest.
- Holy flight uses saved action origin, smoothed vertical curve and a1.25-block maximum step; defaults rise12blocks by16ticks, descend across last20ticks. Very short configured actions clamp achievable height to retain the movement bound. Solid obstruction blocks the step; no forced phasing/invulnerability. Stop/cancel restores original gravity, remaining descent is normal physics if interrupted.
- Blade attachment uses existing tracked roots/tips even outside slash trail windows. Charge samples do not create false swing trails. Gravity has24 short arc segments per blade plus two coating bands; holy has coating plus four moving glints. No scene-refraction claim.
- Debris is client visual only: actual collider-top contact of blade line or swept tip, solid/fluid-free material, one burst per blade/window. Counts bounded by half existing particle budget per blade (max16). No block destruction or synthetic contact position. Existing blade windows may still need reference-led expansion alongside future animation work.
- Configs: three files each added7burst fields with .pre-reference-burst-v1 backups; each added3flight fields with .pre-holy-flight-v1 backups. Custom values protected and repeat-checks passed.
- Validation: ActionDebugCheck12106, AttackPlanCheck731917/3936strikes, ActionSoundPlanCheck48legalphase/variantcases, RangedCounterCheck with selection/indicator regression, Forge+NeoForgecompile all passed. Existing animations/sounds/arena resources unchanged. No client, Blockbench, full world entity-tick simulation, GPU visual review or new visual acceptance.
- NEXT: study/rebuild opening/lion/meteor trajectories and animations, then missing313..316skill; remaining large leg motion, Miquella hair, real scene distortion. Do not mark the whole ten-item task done.

## Implementation Batch 2026-09-17 14:40-16:14

- Supersedes the previous NEXT line for opening/lion/meteor/distortion only. Items2,9,10 and complete per-skill debris/visual review remain pending.
- Inspected one586120-byte30frame reference sheet:69..73.5s and163..172.5s at0.5s spacing.69s/169.5..170s are black,170.5+ is CG; do not infer one continuous attack across these cuts. No new model image was inspected.
- Bound dormant Boss freezes intro tick0 with blades on ground;60tick pickup precedes forced opening lion. Opening uses existing lion ID/damage, eligible participant resolution outside ordinary target-list radius,64block maximum horizontal approach and collision substeps. Disabled lion and skill tests do not force an opener.
- Normal/double lion adds physical ascent, airborne frozen target and exact landed-hit gate. Visual initial cuts at authored8/6 have no new damage; original32/26 landing contacts retained. Blocked/invalid routes cancel, without distant hits. Timelines too short for the path retain legacy fallback.
- Meteor holds surface-material rocks while rising, transforms them to the configured charged block, then releases at original eight event ticks. Held rocks remain destructible, never damage while held, persist remaining timing and discard on owner/action cancellation. No terrain deletion. P1 returns to origin; P2 freezes a nearby landing target and adds the approved body hit.
- Meteor defaults:sword_range/body_range4.5,sword_damage4+0.8*Attack,body_damage5+0.8*Attack. Three configs added six missing leaf fields with .pre-reference-meteor-v1 backups. Old values protected. Missing legacy snapshot fields use defaults. Unusable flight sequences, including last recovery0/1, use legacy behavior.
- New animation revision reference_opening_lion_meteor_v2 changes intro/lion_claw/lion_claw_double/gravity_meteor;39clips and model geometry/textures protected. Animation SHA2561c837cf74c3b509d919048af892c4eb8f638e19a83b63af4f74c37339d39fe3b. Editor/export checks pass; actual GeckoLib43clips/644178values passed. Max sampled joint step35.58375deg/half-tick is a diagnostic, not visual acceptance.
- Meteor clock adds authored21/35/63/144 at default runtime21/34/61/131. Original12component ticks retained, including custom durations. The added exact anchors fix rounding-based contact drift. Audio remeasured from current animation:43body contacts/68blades plus six clone profiles; six +3dB OGG files unchanged.
- Gravity distortion copies main scene color to a distinct texture, then depth-tests masked world billboards (max24) with real displaced scene sampling. Resizes with viewport; skips unsupported/non-main/MSAA targets and oversized buffers; optional shader/capture failure retains other effects. Dimension change/logout/reload frees capture. GPU test is an isolated OpenGL context, not the game pipeline or shaderpack acceptance.
- Offline:ActionDebug19020;Timeline74403;AttackPlan734007/3936legacy strikes (earlier in batch);Sound48legalcases;Arena4203;Ranged selection/indicator regression; both compile/resources and processed audio hashes passed. Dedicated asset checker passed; historical full validate_assets visual/prototype gate has NOT been updated/passed for these four clips. Historical preview hashes and acceptance remain revision-scoped.
- Client operations:none. User-led in-world movement, collisions, multiplayer, rendering/performance, audio and new visual acceptance remain pending.

## Hair And Footwork Continuation 2026-09-17

- Hair coverage implemented by scripts/refine_hair_coverage.js. Ten existing three-section locks keep their skeleton/pivots/rotation/UV;80cubes get wider cross-sections, tapered tips retained. No new cubes, texture rewrite or arm/body changes. Baseline backups:.pre-hair-coverage-v1. Current geometry SHA2562ba7aa4f976936ed2584052de8f15b89b4b3339f1c8e52c29d05ba1f994601e2; animation remains1c837cf74c3b509d919048af892c4eb8f638e19a83b63af4f74c37339d39fe3b.
- Static projection grid increases back coverage21.8% and shoulder coverage5.6%; these measure hair projection, not body surface occlusion or full-pose coverage. One1200x800/189610byte comparison inspected, before/after back/rear45/front45; gaps smaller and ends still separated. Original UVs mean wider texels on widened sections; dynamic overlaps/style acceptance require review. Blockbench MCP unavailable, no restart attempted. No Minecraft operations.
- Both processResources tasks and refine_hair_coverage --check --processed passed; actual geometry/animation/twoPNG hashes matched. Audio tool binds whole bbmodel hash, so deploy_audio --deploy remeasured current source;68bodyblades and six unchanged+3dB samples passed. Historical full geometry/visual validators remain revision-scoped and have not passed this revision; do not reuse old acceptance or rerun historical full builders.
- Ten ground-skill candidate scripts/refine_ground_footwork.js is NOT APPLIED: leg-only stepping with planted support, no pelvis changes. Dry run failures:invalid Vector3.add fixed; recovery timing fixed; final unreachable left-foot target38.29755 remained. Three attempts reached the repair limit; ask user whether to couple pelvis/body weight shift or reduce stride. No animation files/backups/report for this candidate have been written. Existing four-clips and server timings remain unchanged.
- Item10 remains unresolved:313..316golden pass-through needs further reference identification, not the318+combo. No new action ID or guessed damage. This turn's sole image was used for hair review.

## Footwork And Lightspeed Delivery 2026-09-17

- Supersedes the blocked candidate above. User requested continuing through implementation until user acceptance. Proceeded with coupled pelvis/foot support, while compensating body translation to protect head/blade world paths. No hit values, durations, geometry, textures or existing22skill IDs changed by footwork.
- scripts/refine_ground_footwork.js --all --apply changes20clips: ten grounded skills (six left/right combos, cross_slash,starcaller_cry,ring_of_light,enhanced_earthheave) plus stomp,gravity_dive,spiral_assault,gravity_meteor,light_of_miquella,lightspeed_slash,lightspeed_dash,lightspeed_side_dash,promised_consort,consort_meteor. Previous two lion-claw revisions retained;23other clips protected.
- Ground targets use single-foot5.5unit lifting and5..11unit approach, then separate recovery steps; pelvis shifts within16model units to satisfy both leg lengths, not by rescaling bones. Tempest support area/knees/foot yaw rotate with its two turns and do not receive extra straight-line lean. These rotating samples are not counted as fixed sole contacts. Root/body compensation and start/end fading preserve original endpoints; no new terrainIK or full center-of-mass simulation claimed.
- Interpolation audit1656midpoints:maximum upper-body marker drift0.00101568model units, planted midpoint drift0.050975, ground maximum joint change34.50894deg/half-tick. Original promised_consort ground-spin change89.69531deg/half-tick and original maximum pelvis step3.22345 are retained, not fixed or silently accepted. New changes cannot introduce/increase an over-limit step. Ten aerial/lift windows add up to25..38degrees at knees, fade to zero before original landing/rest contacts. These are event-led adaptations, not full new reference-frame calibration.
- Current animation SHA25639aec7a8697b8a6af2001b98a195abc922fd8d2d74953e3f6095cb65a7964cb4;project7f4411f00cb4dc6246230f951a5ebb87af12ce3f7b0eff7bf42a700acef96b15;geometry2ba7aa4f976936ed2584052de8f15b89b4b3339f1c8e52c29d05ba1f994601e2. Backups.pre-ground-footwork-v1. Do not rerun --apply unnecessarily because editor keyframe UUIDs regenerate. Current export/animation/motion metadata has current_footwork overlay with visual/world false.
- Actual312.5..316.5 video study uses one1200x1020/237518byte24frame image build/ai-previews/consort-0515-detail.jpg, relative labels plus312.5s. Around313..314.3 charge,314.5..314.8 pass-through,315+delayed light columns then distant recovery. No exact original skill-name or clone-count claim. No new source pages/model images inspected this turn.
- Existing ordinary LIGHTSPEED_DASH previously moved the body during every clone ACTIVE tick while its authored clip held a charge. It now uses PromisedConsortLightspeedPath:lock before first clone,departure at max(lastclone start,body hit-7),impact at originalbody hit. One origin/facing shared by four clones, body and tail light; delayed columns already existed in holyLayers. Original damages/ranges/caps and six event times retained. Ranged variant and legacy unusable/single-stage paths retained. No speculative duplicate skillID added; correspondence to requested5:15 must be confirmed in user acceptance.
- Route check before first clone samples0.25block segments, requires loaded flat supported/fluid-free ground, arena bounds and collision clearance; no chunk forcing/block deletion. Requested travel uses configured base range, with maximum256 and smoothstep peak speed<=8blocks/tick; unsupported custom timelines/ranges cancel before release. During actual rush,<=0.25substeps and exact destination gate prevent body damage on an interrupted arrival. Origin/end/frozen/arrival markers use existing saved lockedPoints; full unload/world restore behavior remains an in-world test, not proven by helper tests.
- Gates passed:both compile/resources;ActionDebug19198;AttackPlan734007/3936legacystrikes;Timeline74403;ActionSound48legalphasecases43contacts/68blades;same ranged selection/indicator regression;actualGeckoLib43clips649536transform values;footwork --all --check;hair --check --geometry-only --processed;audio --check --processed. Six+3dB OGG files unchanged;angle profiles remeasured for current source. No runtimeJava/gameplay config migration in this batch.
- Historical full validate_assets preview/prototype gates are not current-revision acceptance. Current checks are the named incremental/model/runtime checks above; previous preview hashes were not rebound. No new model playback visual review possible in this turn's image budget; prior Blockbench MCP unavailable. No Minecraft client operation performed. Current implementation is ready for user-led visual/gameplay acceptance, not declared an exact replica or complete world verification.

## 本轮实机验收

使用当前工作区开发客户端进入测试世界；本轮已编译并同步资源，没有修改或替换任何已有JAR。助手不会启动、停止或控制客户端。主菜单不算验收；可先在Forge检查，NeoForge需单独复核。

| 顺序 | 操作 | 通过标准 |
| --- | --- | --- |
| 1 | 单招 `/elderbosses test promised_consort lightspeed_dash 2` | 蓄光及前几道残影期间本体不向前滑，最后贯穿既定直线，随后同一路线出圣光柱；确认它是否正是视频5:15所指动作 |
| 2 | 依次测六组左右连斩、十字斩、唤星、光环、强化掀地 | 可见迈脚、膝盖弯曲和骨盆换重心；支撑脚不明显滑动，双旋风双腿随转身，不反拧；刀路和瞬防时机不变 |
| 3 | 测踏地、三种光速动作、重力突刺/螺旋、约定之王、两种陨石与米凯拉之光 | 收腿/抬膝与动作类型相符，落地脚不悬空或穿地；特别观察约定之王前两次转身的原有腿部跳变是否仍明显 |
| 4 | `/elderbosses test promised_consort lion_claw 1` 与 `lion_claw_double 2` | 起手斩地不额外伤人；离地后锁点，落斩预警与实际落点一致；被墙阻挡不能远处命中 |
| 5 | `/elderbosses test promised_consort gravity_meteor 1` 与 `gravity_meteor 2` | 斩地后本体和岩石升空，岩石变为蓄能材质再依次发射；悬浮岩石能击碎且不补生，二阶段本体最后落斩 |
| 6 | `/elderbosses test promised_consort light_of_miquella 2`；近看双刀与背面金发 | 实体快速升高，刀刃附光；重力区域背景真的弯曲且墙后不过度露出；金发覆盖肩背、动态不明显穿手臂 |
| 7 | 正常场地祭台召唤并首攻；正常打一组而非单招测试 | 神门地面待战、60tick重力举刀、紫电首发狮子斩；完整2..4招后才明显喘息；单招指令不验证这两个流程 |
| 8 | 生存/可受伤状态、墙边/落差、取消/击杀、保存退出后重进；两名玩家；开关Boss指示器 | 判定和预警一致，取消无幽灵伤害/残留悬浮，存档恢复不复制岩石；多人锁线不跳目标；关范围仍保留瞬防与动作特效 |

技能测试需要权限2；每条指令等待上一招清理完再执行，默认在玩家前方生成独立临时Boss。创造模式可看动作，但不能验证伤害。单招结束会截断长寿命危险区，因此存档恢复、完整投射物寿命和2..4招节奏需正常遭遇战。不要为查看开场而在已有正式存档中重复强制生成Boss。

反馈请包含加载器、技能ID、阶段、是否普通/远程变体、现象或视频时间点；对第1项明确回答“就是5:15那招”或指出差别。第三方光影/Fabulous等不支持主帧缓冲的管线可能回退为无折射，这是已知兼容边界，默认渲染管线的扭曲仍需实测。全部项目当前均未打勾，不继承旧版本认可。