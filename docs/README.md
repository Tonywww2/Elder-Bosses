# 当前项目文档

## 共通系统

- [项目开发规范](../AGENTS.md)
- [原作Boss迁移规范与约定之王实施记录](migration/source-boss-migration.md)
- [主配置规范](config/README.md)与[完整示例](config/elder-bosses-common.example.toml)
- [字幕与非语言音效](dialogue/boss-voice-lines.md)
- [技能范围指示器](indicators/boss-skill-indicators.md)

## 约定之王

- [当前战斗规格](bosses/promised-consort-radahn.md)
- [技能配置与原作动作段](config/promised-consort-source-skills.md)
- [当前资源工具](../tools/consort/README.md)与[验收项目](../tools/consort/ACCEPTANCE.md)
- [竞技场合同](arenas/promised-consort-arena.md)
- [美术规范](art/promised-consort-radahn-art-bible.md)
- [参考来源](references/promised-consort-radahn-sources.md)

## 腐败女神

- [当前战斗规格](bosses/malenia-blade-of-miquella.md)
- [技能设计](bosses/malenia-skill-design.md)
- [当前资源工具](../tools/malenia/README.md)
- [圣树竞技场](arenas/malenia-arena.md)
- [美术规范](art/malenia-art-bible.md)
- [参考来源](references/malenia-sources.md)

## 目录规则

保留当前规范与必要的源资料。已完成阶段的计划、版本说明、旧候选和重复验收记录不作为当前规范保存。
运行模型、动画、特效和合同置于 `src/main/resources`；制作工具置于 `tools`；可再生成产物置于 `build`。
项目只使用一个 common 配置；掉落、配方、集合标签和建筑资源按 Minecraft 数据包结构组织。
