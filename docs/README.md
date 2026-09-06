# Elder Bosses 设计文档

本目录保存 Boss 实现前的战斗、美术、资料与配置基线。

## 文档入口

### 共通系统

- [Boss 台词字幕与非语言音效规范](dialogue/boss-voice-lines.md)
- [Boss 技能指示器设计规范](indicators/boss-skill-indicators.md)
- [唯一 Common 配置结构](config/README.md)
- [Common 配置样例](config/elder-bosses-common.example.toml)

### 约定之王

- [可实现战斗规格](bosses/promised-consort-radahn.md)
- [美术制作规范](art/promised-consort-radahn-art-bible.md)
- [竞技场程序构造规范](arenas/promised-consort-arena.md)
- [资料与图片台账](references/promised-consort-radahn-sources.md)

### 腐败女神（马莲尼亚）

- [可实现战斗规格](bosses/malenia-blade-of-miquella.md)
- [美术制作规范](art/malenia-art-bible.md)
- [竞技场程序构造规范](arenas/malenia-arena.md)
- [资料与图片台账](references/malenia-sources.md)

## 已确认口径

- 平衡目标：原版终局装备的 1-4 名玩家。
- 目标平台：Minecraft 1.20.1 Forge 与 Minecraft 1.21.1 NeoForge。
- 伤害格式：任何伤害分量都必须表示为「固定值 + Boss 攻击力百分比」。
- 程序边界：招式、阶段、时间轴、位移、判定与竞技场构造由 Java 程序控制，不新增自定义招式或竞技场数据包类型。
- 配置策略：项目只注册 `config/elder_bosses-common.toml`；生命、多人缩放、抗性、选招权重、时序、判定、伤害、硬直、瞬间防御、场地尺寸、破坏预算、奖励数值与特效预算均位于该 common 配置。
- 指示器：技能范围由服务端权威状态驱动；地面填充受遮挡，低透明边框可透视；全局透明度由 common 配置中的 `indicators.opacity` 控制。
- 硬直口径：Boss 实际生命损失默认按 75% 转为硬直值，上限为最大生命的 10%，并应用四段距离倍率。
- 瞬间防御：玩家举盾后第 3-6 tick 命中特定攻击段才成功；对应攻击必须同时显示红色光效并播放独立提示音。
- 台词策略：关键对白只显示本项目原创字幕，不制作对白录音或口型；战吼、呼吸、受击和技能提示等非语言音效保留，并使用原创或授权素材。
- 正式身份：`elder_bosses:malenia` 显示为「腐败女神」；`elder_bosses:promised_consort` 显示为「约定之王」。

## 素材使用警告

`assets/reference/` 中的《艾尔登法环》截图是低分辨率内部研究资料，版权归原权利人所有。它们不得进入发布 JAR、宣传图、Modrinth/CurseForge 页面或可分发资源包。正式发布资产必须重新原创，参考图仅用于识别轮廓、材质关系、动作节奏和色彩层级。