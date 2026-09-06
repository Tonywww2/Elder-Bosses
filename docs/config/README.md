# Common 配置结构

本项目只注册一个 common 配置文件：

`config/elder_bosses-common.toml`

Forge 1.20.1 使用 `ForgeConfigSpec`，NeoForge 1.21.1 使用对应的 `ModConfigSpec`，两端都以 `ModConfig.Type.COMMON` 注册相同语义的配置树。不得再注册 `malenia-server.toml`、`radahn-server.toml`、独立 client 配置或第二个 common 配置。

## 配置树

| 根节 | 读取侧 | 用途 |
| --- | --- | --- |
| `[indicators]` | 客户端本地 | 技能指示器启用、透明度、透视边框倍率、距离和几何预算 |
| `[malenia.*]` | 服务端权威 | 腐败女神属性、技能、硬直、瞬防、场地、台词和奖励数值 |
| `[promised_consort.*]` | 服务端权威 | 约定之王属性、技能、硬直、瞬防、场地、台词和奖励数值 |

`indicators.opacity` 只改变本地渲染，不同步到服务端，也不能影响命中范围或时机。Boss 数值由服务端读取并在开战时生成快照；客户端只接收战斗和指示器所需的权威状态。

## 文档文件

[elder-bosses-common.example.toml](elder-bosses-common.example.toml) 是唯一完整样例，依次包含 `[indicators]`、`[malenia.*]` 和 `[promised_consort.*]`。`docs/config/` 不保留第二份 TOML，避免片段被误认为可单独加载的配置。

## 重载规则

- `indicators.*` 是客户端本地视觉值，配置重载后下一帧生效。
- Boss 战斗数值只应用于下一场战斗；进行中的实例继续使用开战快照。
- 配置重载不能增加技能、改变技能段、改变指示器形状映射或注入脚本。
- 非法值由配置规范修正并记录日志；`indicators.opacity` 限制在 0.10-1.00。