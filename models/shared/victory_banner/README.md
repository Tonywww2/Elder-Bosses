# Boss victory banner

拉塔恩：`GOD SLAIN`；玛莲妮亚：`DEMIGOD FELLED`。英文横幅同时用于中文和英文客户端，文案可通过语言资源包覆盖。

- 中央暖金色 Cinzel 衬线字、暗色上下渐隐横带；随窗口尺寸和 GUI 缩放适配。
- 16 tick 淡入、64 tick 停留、30 tick 淡出，共 5.5 秒。暂停冻结，F1 隐藏 HUD 时隐藏。
- 服务端发送独立胜利事件：拉塔恩 `finishDefeat()` 移除前；玛莲妮亚完成谢幕和原版死亡计时、以 `KILLED` 移除时。阶段切换、卸载与普通 discard 不触发。
- 发给消失时 Boss 血条的受众，同维度有效玩家。玛莲妮亚最终死亡期间保留隐藏血条受众，直到移除。
- 客户端按实体 UUID 去重，连续胜利排队（最多 8 个待播）；切维度、退出世界清空，不因 Boss 结束追踪而清空。
- 每条横幅开始淡入时只播放一次 3.5 秒的原创胜利提示音；排队横幅等轮到自己才播放。低钟、D 小调人声和金属余响呼应原作氛围，未采样原游戏录音。可运行 `python models/shared/build_victory_sound.py` 重建，生成信息见 `models/shared/victory_audio.json`。
- Forge / NeoForge 网络协议版本升至 3；联机双方需要更新模组。

字体为未修改的 [Cinzel Regular](https://github.com/NDISCOVER/Cinzel)，来源、哈希及 OFL 授权随模组打包在 `META-INF/VICTORY-FONT-CREDITS.txt` 和 `META-INF/CINZEL-OFL.txt`。

检查（先用 Java 25 启动 Gradle）：

```powershell
.\gradlew.bat -I models/shared/check_classpath.init.gradle :1.20.1-forge:writeCheckClasspath :1.21.1-neoforge:compileJava :1.21.1-neoforge:processResources --offline --console=plain
node models/promised_consort/tests/run_attack_plan_check.js ../../shared/BossVictoryBannerCheck.java
```

实机验收：击败两个 Boss 的最终阶段，等身体消失再出现对应横幅和提示音；观察阶段切换与离开追踪不弹横幅；多人客户端同时显示；暂停、F1、高 GUI 缩放和切维度不留下残影。自动检查覆盖状态计时、音效启动、去重、队列、网络包往返、字体字形和两个加载器的资源输出，不能代替实机验收。
