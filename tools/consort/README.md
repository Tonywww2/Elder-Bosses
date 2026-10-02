# 约定之王资源工具

完整迁移过程、已废弃方案、当前约定及全量重建输入缺口见[原作Boss迁移规范](../../docs/migration/source-boss-migration.md)。

## 当前来源

- `mesh/base.geo.json`：仍用于绑定的原项目网格与 UV 输入。
- `rig`：当前源关节、HKX 转换记录、玩家投技与特效解包输入。
- `geo/source_rig_calibration.geo.json`：绑定与腰部蒙皮计算使用的校准骨架。
- `inputs/source_parameter_supplement.json`：补充参数输入。
- `scripts`：当前提取、转换、网格绑定、合同和贴图生成工具。
- `arena`：当前竞技场制作输入和生成工具。
- `audio`：当前原创音源与制作输入。
- `tests/SourceTimelineCheck.java`：现有时间轴与战斗专项检查。

`rig/player_grab_source`、`rig/source_map`、`rig/source_sfx_geometry`、
`rig/source_sfx_textures`、`rig/source_shared_sfx`为本地解包原件目录，由Git忽略。
全新检出仍可直接build；重新提取、转换相关资源时需用本地原作输入和对应提取脚本恢复这些目录。

## 唯一运行资源

| 类型 | `src/main/resources/assets/elder_bosses/` 下的位置 |
| --- | --- |
| 模型 | `geo/entity/promised_consort.geo.json` |
| 动画 | `animations/entity/promised_consort/source_*.animation.json` |
| 实体贴图 | `textures/entity/promised_consort/` |
| 特效贴图 | `textures/particle/promised_consort/` |
| 战斗、特效与玩家姿态合同 | `boss/promised_consort/` |
| 挂点与骨架映射 | `boss/promised_consort/rig/` |

80 个姿态支持 81 个 TAE 段和 22 个技能入口。模型的 Master、身体 Root、Pelvis 位移与旋转保留；
实体位移由服务端处理，客户端只移除重复的 Master 平移。腰部采用当前仿射蒙皮控制，双刀使用源装备关节。
详见 [身体移动规则](rig/BODY_MOTION_POLICY.md)、[技能配置](../../docs/config/promised-consort-source-skills.md)和[验收项目](ACCEPTANCE.md)。

## 生成流程

普通配置或时间轴调整无需重新转换动画。按修改范围执行相应脚本：

全量重建`source_contracts`前须恢复`inventory/source_ai_functions.json`或从原AI重新生成可靠索引；当前该输入缺失。现有运行资源的默认build不执行此生成脚本。

```powershell
python tools/consort/scripts/build_source_contracts.py
python tools/consort/scripts/build_runtime_contracts.py
python tools/consort/scripts/build_combat_contracts.py
python tools/consort/scripts/build_fxr_contracts.py
python tools/consort/scripts/build_ground_areas.py
python tools/consort/scripts/build_source_config.py
python tools/shared/config/build_presentation.py
python tools/consort/scripts/validate_assets.py
```

这些脚本直接更新当前运行资源；最后一个脚本核对资源并输出 `build/asset-validation/consort/resources.json`。
`paths.py` 统一输出目录。未发布的参数和原作解包输入保留在工具目录，不混入 JAR。

仅在 HKX、骨架或绑定改变时，依次使用 `convert_source_bank.py`、`bind_model.py`、
`bake_mesh_skin.py`、`settle_recovery.py`；后者将3006、3025最后1.2秒归入源待机姿势，再根据修改后的骨架重算腰部仿射蒙皮。攻击窗口、总时长与Master位移保留，报告位于`rig/recovery_settle.json`。原作目录只读。
原作工具依赖的 Python 库与解包程序使用本地已安装环境；原游戏文件和私人参考视频不发布。

米凯拉的腰腹、头发、头部及四手由`refine_miquella.py`生成。需要单独调整这些网格时运行
`python tools/consort/scripts/refine_miquella.py`，无需重烘焙动画或腰部蒙皮。
当前为34束错落、收尖的长发，配脸侧与后颈短发；头部使用统一五官坐标和收窄下颌，
四手保留60个原指节挂点并采用分层掌心和渐细指尖。原骨架、动画与贴图文件保持不变。
骨盆至胸部之间补齐三段随脊柱弯曲的腰腹及髋部接片，各段重叠1模型单位，连接上衣和下袍。
`rig/hair_volume.json`记录当前米凯拉网格生成结果。

## 预览和报告

骨架核对由 `build_rig_preview.py` 生成，位置为 `build/asset-previews/consort`。
预览输入由转换器按需生成。检查日志、包内清单和临时编译输出属于 `build`，不保留多份历史验收报告。
