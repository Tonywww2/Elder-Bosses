import com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedState;
import java.util.UUID;

public class RangedCounterCheck {
    public static void main(String[] arguments) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var bootstrapState = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapState.setAccessible(true);
        bootstrapState.setBoolean(null, true);
        net.minecraft.core.registries.BuiltInRegistries.bootStrap();
        checkClientVfxInitialization();
        var rules = new PromisedConsortRangedState.Rules(14, 10, 100, 80, 1, 80);
        var state = new PromisedConsortRangedState(rules);
        UUID distant = new UUID(0, 1), shooter = new UUID(0, 2), excluded = new UUID(0, 3);
        for (int tick = 0; tick < 99; tick++) state.observe(distant, tick, 14);
        require(!state.qualifies(distant, 98, 14), "Dwell qualified too early");
        state.observe(distant, 99, 14);
        state.observe(distant, 99, 14);
        require(state.qualifies(distant, 99, 14), "Dwell alone must qualify");
        require(!state.qualifies(shooter, 99, 14), "Player histories leaked");
        state.damage(shooter, 99, 14, 1, false);
        require(state.qualifies(shooter, 99, 14), "Damage alone must qualify");
        state.damage(excluded, 99, 30, 100, true);
        state.threat(excluded, 99, 100, true);
        require(!state.qualifies(excluded, 99, 30), "Excluded damage qualified");
        require(state.threatDamage(excluded, 99, 80) == 0 && state.threatAttempts(excluded, 99, 80) == 0, "Excluded threat counted");
        require(!state.qualifies(shooter, 99, 12), "Near target must not qualify");
        require(!state.qualifies(shooter, 179, 14), "Expired damage qualified");
        state.observe(distant, 100, 10);
        state.observe(distant, 101, 14);
        require(!state.qualifies(distant, 101, 14), "Close approach did not reset dwell");
        state.threat(shooter, 101, 5, false);
        state.threat(shooter, 101, 7, false);
        require(state.threatDamage(shooter, 101, 80) == 12 && state.threatAttempts(shooter, 101, 80) == 2, "Threat aggregation differs");
        var restored = new PromisedConsortRangedState(rules);
        restored.restore(state.save(101), 1000);
        require(restored.threatDamage(shooter, 1000, 80) == 12, "Saved threat did not rebase");
        require(restored.threatDamage(shooter, 1079, 80) == 0, "Restored threat lifetime extended");
        for (int tick = 102; tick < 202; tick++) state.observe(excluded, tick, 30);
        require(state.qualifies(excluded, 201, 30), "Exclusion must not disable dwell branch");
        for (double amount : new double[]{0, 20, 40, 80}) require(
                PromisedConsortRangedState.returnMultiplier(amount, 40, 1, 2) == 1 + Math.min(amount / 40, 1), "Return multiplier differs");
        checkConfiguredActions();
        checkDistanceSelectionDefaults();
        checkDistanceSelectionConfiguration();
        checkBossIndicators();
        checkIndicatorRendering();
        if(java.util.Arrays.asList(arguments).contains("--sync-example")) syncExample();
        if(java.util.Arrays.asList(arguments).contains("--sync-followup-configs")) syncFollowupConfigs(true);
        if(java.util.Arrays.asList(arguments).contains("--check-followup-configs")) syncFollowupConfigs(false);
        if(java.util.Arrays.asList(arguments).contains("--sync-selection-configs")) syncConfigs(true, true);
        if(java.util.Arrays.asList(arguments).contains("--check-selection-configs")) syncConfigs(false, true);
        System.out.println("Ranged qualification, exclusion, expiry, persistence and 1..2x return checks passed");
    }

    private static void checkBossIndicators() {
        var spec = com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig.SPEC;
        var document = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        spec.correct(document);
        spec.setConfig(document);
        var defaults = com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig.VALUES.indicators();
        require(defaults.maleniaEnabled() && defaults.promisedConsortEnabled(), "Missing boss indicator defaults");
        var migrationSource = new com.electronwill.nightconfig.toml.TomlWriter().writeToString(document);
        var migration = new com.electronwill.nightconfig.toml.TomlParser().parse(migrationSource);
        for (String key : selectionConfigKeys()) migration.remove(key);
        migration.set("indicators.malenia_enabled", false);
        migration.set("promised_consort.ranged_counter.pursuit_idle_multiplier", 0.0);
        require(selectionChanges(migration, document).size() == 4, "Migration must only add missing selection/display fields");
        require(Boolean.FALSE.equals(migration.get("indicators.malenia_enabled"))
            && ((Number) migration.get("promised_consort.ranged_counter.pursuit_idle_multiplier")).doubleValue() == 0,
            "Migration overwrote explicit false or zero");
        require(selectionChanges(migration, document).isEmpty(), "Selection migration is not idempotent");
        for (boolean global : new boolean[]{true, false}) for (boolean malenia : new boolean[]{true, false})
        for (boolean consort : new boolean[]{true, false}) {
            document.set("indicators.enabled", global);
            document.set("indicators.malenia_enabled", malenia);
            document.set("indicators.promised_consort_enabled", consort);
            spec.setConfig(document);
            var config = com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig.VALUES.indicators();
            require(config.rangeEnabled(false) == (global && malenia), "Malenia indicator gate differs");
            require(config.rangeEnabled(true) == (global && consort), "Consort indicator gate differs");
            require(config.opacity() == defaults.opacity() && config.renderDistance() == defaults.renderDistance(), "Boss toggle changed other indicator settings");
        }
        var legacy = new com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig.IndicatorValues(true, 0.6, 0.35, 96, 0.02, 64, 96);
        require(legacy.rangeEnabled(false) && legacy.rangeEnabled(true), "Old IndicatorValues constructor changed defaults");
        System.out.println("Boss indicator config: both default on, independent toggles and global override passed");
    }

    private static void checkIndicatorRendering() throws Exception {
        var renderer = com.tonywww.elder_bosses.client.indicator.ClientIndicatorRenderer.class;
        var configType = com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig.IndicatorValues.class;
        var packetType = com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.class;
        var pose = new com.mojang.blaze3d.vertex.PoseStack().last();
        var consortBorder = renderer.getDeclaredMethod("drawConsortBorder", com.mojang.blaze3d.vertex.VertexConsumer.class,
                com.mojang.blaze3d.vertex.PoseStack.Pose.class, packetType, boolean.class, long.class, configType);
        var borders = renderer.getDeclaredMethod("drawBorders", com.mojang.blaze3d.vertex.PoseStack.Pose.class,
                net.minecraft.client.renderer.MultiBufferSource.BufferSource.class, java.util.List.class,
                net.minecraft.client.renderer.RenderType.class, boolean.class, long.class, configType);
        var fills = renderer.getDeclaredMethod("drawFills", com.mojang.blaze3d.vertex.PoseStack.Pose.class,
                net.minecraft.client.renderer.MultiBufferSource.BufferSource.class, java.util.List.class, long.class, configType, boolean.class);
        consortBorder.setAccessible(true); borders.setAccessible(true); fills.setAccessible(true);
        int[] vertices = {0};
        var consumerType = com.mojang.blaze3d.vertex.VertexConsumer.class;
        var consumer = (com.mojang.blaze3d.vertex.VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
                consumerType.getClassLoader(), new Class[]{consumerType}, (proxy, method, arguments) -> {
                    if (method.getName().equals("vertex")) vertices[0]++;
                    return method.getReturnType() == consumerType ? proxy : null;
                });
        var buffers = new net.minecraft.client.renderer.MultiBufferSource.BufferSource(new com.mojang.blaze3d.vertex.BufferBuilder(256), java.util.Map.of()) {
            @Override public com.mojang.blaze3d.vertex.VertexConsumer getBuffer(net.minecraft.client.renderer.RenderType type) { return consumer; }
        };
        for (boolean enabled : new boolean[]{false, true}) for (boolean cue : new boolean[]{false, true}) {
            var snapshot = new com.tonywww.elder_bosses.network.IndicatorSnapshotPacket(1, "test",
                    com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.SegmentSlot.CURRENT,
                    com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.StyleRole.values()[0],
                    com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.Semantic.values()[0],
                    com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.IndicatorState.values()[0],
                    com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.ShapeType.CIRCLE,
                    new com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.Point(0, 0, 0), 0,
                    java.util.List.of(4.0F), java.util.List.of(), 100, 105, 110, 115, cue);
            var config = new com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig.IndicatorValues(true, 0.6, 0.35, 96, 0.02, 64, 96, enabled, enabled);
            for (boolean consort : new boolean[]{false, true}) {
                vertices[0] = 0;
                fills.invoke(null, pose, buffers, java.util.List.of(snapshot), 106L, config, consort);
                require((vertices[0] > 0) == enabled, "Boss range fill gate ignored");
                vertices[0] = 0;
                if (consort) consortBorder.invoke(null, consumer, pose, snapshot, false, 106L, config);
                else borders.invoke(null, pose, buffers, java.util.List.of(snapshot), null, true, 106L, config);
                require((vertices[0] > 0) == (enabled || cue), "Range toggle lost instant guard or retained disabled border");
                vertices[0] = 0;
                if (consort) consortBorder.invoke(null, consumer, pose, snapshot, true, 106L, config);
                else borders.invoke(null, pose, buffers, java.util.List.of(snapshot), null, false, 106L, config);
                require((vertices[0] > 0) == enabled, "Disabled secondary border still renders");
            }
        }
        System.out.println("Real indicator drawing: both boss range fills/borders gated, instant-guard vertices retained");
    }

    private static void checkDistanceSelectionDefaults() {
        var config = com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortRangedConfig.defaults();
        var suppressed = java.util.EnumSet.of(
                com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.L_COMBO_CROSS,
                com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.L_COMBO_BLOODFLAME,
                com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.R_COMBO_CROSS,
                com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.R_COMBO_LEFT_TWIN,
                com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.R_COMBO_TEMPEST,
                com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.R_COMBO_EARTHHEAVE);
        for (var action : com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.values()) {
            for (double distance : new double[]{0, 5, 8.999, 9, 15.999, 16, 40}) {
                double expected = !suppressed.contains(action) || distance < 9 ? 1 : distance < 16 ? 0.1 : 0;
                require(config.meleeWeightMultiplier(action, distance) == expected, "Distance suppression scope or boundary differs: " + action);
            }
        }
        for (int base = 6; base <= 14; base++) {
            require(config.idleTicks(base, true) == (base + 1) / 2, "Pursuit idle must halve with ceiling rounding");
            require(config.idleTicks(base, false) == base, "Ordinary target idle changed");
            int tick = base - 6;
            require(com.tonywww.elder_bosses.boss.promisedconsort.controller.PromisedConsortCombatController.selectionDelay(tick, 0, true, config)
                == (base + 1) / 2, "Controller did not apply pursuit interval");
            require(com.tonywww.elder_bosses.boss.promisedconsort.controller.PromisedConsortCombatController.selectionDelay(tick, 0, false, config)
                == base, "Controller changed ordinary idle interval");
            require(com.tonywww.elder_bosses.boss.promisedconsort.controller.PromisedConsortCombatController.selectionDelay(tick, 18, true, config)
                == 18, "Pursuit shortened forced recovery");
        }
        System.out.println("Six-combo 9/16 distance gates and 0.5 pursuit idle defaults passed; lion claws unchanged");
    }

        private static void checkDistanceSelectionConfiguration() throws Exception {
        var builder = new net.minecraftforge.common.ForgeConfigSpec.Builder();
        var values = new com.tonywww.elder_bosses.platforms.config.PromisedConsortConfigValues(builder);
        var spec = builder.build();
        var document = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        spec.correct(document);
        spec.setConfig(document);
        var combat = values.combatSnapshot();
        var defaults = combat.targeting().rangedCounter();
        var skills = values.skillSnapshot();
        var catalog = new com.tonywww.elder_bosses.boss.promisedconsort.action.PromisedConsortActionCatalog(skills);
        var selector = new com.tonywww.elder_bosses.boss.promisedconsort.selection.PromisedConsortSkillSelector(catalog, combat.selector());
        var weight = selector.getClass().getDeclaredMethod("weight",
            com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.class,
            com.tonywww.elder_bosses.boss.promisedconsort.selection.PromisedConsortSkillSelector.Context.class);
        weight.setAccessible(true);
        for (double distance : new double[]{8.999, 9, 15.999, 16, 40}) for (var action : com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.values()) {
            var phase = com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase.PHASE_TWO;
            var before = new com.tonywww.elder_bosses.boss.promisedconsort.selection.PromisedConsortSkillSelector.Context(
                phase, distance, false, 1, 0, true, java.util.Set.of(action));
            var after = new com.tonywww.elder_bosses.boss.promisedconsort.selection.PromisedConsortSkillSelector.Context(
                phase, distance, false, 1, 0, true, java.util.Set.of(action), java.util.Map.of(action, defaults.meleeWeightMultiplier(action, distance)));
            require(Math.abs((double) weight.invoke(selector, action, after) - (double) weight.invoke(selector, action, before)
                * defaults.meleeWeightMultiplier(action, distance)) < 0.00001, "Selector lost distance multiplier: " + action);
            if (defaults.meleeWeightMultiplier(action, distance) == 0) require(selector.select(after, 42).isEmpty(), "Zero-weight combo selected");
        }
        String prefix = "promised_consort.ranged_counter.";
        document.set(prefix + "melee_suppression_distance", 12.0);
        document.set(prefix + "melee_zero_weight_distance", 24.0);
        document.set(prefix + "distant_melee_weight_multiplier", 0.25);
        document.set(prefix + "pursuit_idle_multiplier", 1.5);
        spec.setConfig(document);
        var customCombat = values.combatSnapshot();
        var custom = customCombat.targeting().rangedCounter();
        var combo = com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.L_COMBO_CROSS;
        require(custom.meleeWeightMultiplier(combo, 11.99) == 1 && custom.meleeWeightMultiplier(combo, 12) == 0.25
            && custom.meleeWeightMultiplier(combo, 24) == 0 && custom.idleTicks(7, true) == 11, "Custom selection settings ignored");
        require(defaults.meleeSuppressionDistance() == 9 && defaults.pursuitIdleMultiplier() == 0.5, "Open encounter snapshot mutated");
        var customSaved = com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.write(customCombat, skills);
        require(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.read(customSaved).orElseThrow().combat().equals(customCombat),
            "Custom selection settings did not survive NBT");
        document.set(prefix + "distant_melee_weight_multiplier", 0.0);
        document.set(prefix + "pursuit_idle_multiplier", 0.0);
        spec.setConfig(document);
        require(values.combatSnapshot().targeting().rangedCounter().meleeWeightMultiplier(combo, 12) == 0
            && values.combatSnapshot().targeting().rangedCounter().idleTicks(7, true) == 0, "Explicit zero ignored");
        document.set(prefix + "enabled", false);
        spec.setConfig(document);
        var disabled = values.combatSnapshot().targeting().rangedCounter();
        require(disabled.meleeWeightMultiplier(combo, 100) == 1 && disabled.idleTicks(7, true) == 7, "Disabled ranged system changed selection");
        String[] fields = {"meleeSuppressionDistance", "meleeZeroWeightDistance", "distantMeleeWeightMultiplier", "pursuitIdleMultiplier"};
        double[] oldValues = {0, 32, 0, 0}, fallback = {9, 16, 0.1, 0.5};
        for (int missing = 0; missing < 16; missing++) {
            var saved = customSaved.copy();
            var json = com.google.gson.JsonParser.parseString(saved.getString("Combat")).getAsJsonObject();
            var ranged = json.getAsJsonObject("targeting").getAsJsonObject("rangedCounter");
            for (int index = 0; index < fields.length; index++) {
            if ((missing & (1 << index)) != 0) ranged.remove(fields[index]);
            else ranged.addProperty(fields[index], oldValues[index]);
            }
            saved.putString("Combat", json.toString());
            var restored = com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.read(saved).orElseThrow().combat().targeting().rangedCounter();
            double[] actual = {restored.meleeSuppressionDistance(), restored.meleeZeroWeightDistance(), restored.distantMeleeWeightMultiplier(), restored.pursuitIdleMultiplier()};
            for (int index = 0; index < fields.length; index++) require(actual[index] == ((missing & (1 << index)) != 0 ? fallback[index] : oldValues[index]),
                "Missing-field migration changed custom or zero value: " + fields[index]);
            require(restored.segmentAdjustmentBudgetMultiplier() == custom.segmentAdjustmentBudgetMultiplier()
                && restored.pursuitSelectionWeightMultiplier() == custom.pursuitSelectionWeightMultiplier(), "Old pursuit multipliers changed");
        }
        double[] high = {257, 257, 1.1, 17};
        for (int index = 0; index < fields.length; index++) for (double invalid : new double[]{-1, high[index], Double.NaN, Double.POSITIVE_INFINITY}) {
            var saved = customSaved.copy();
            var json = com.google.gson.JsonParser.parseString(saved.getString("Combat")).getAsJsonObject();
            json.getAsJsonObject("targeting").getAsJsonObject("rangedCounter").addProperty(fields[index], invalid);
            saved.putString("Combat", json.toString());
            require(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.read(saved).isEmpty(), "Invalid selection setting accepted");
        }
        var invalidOrder = customSaved.copy();
        var json = com.google.gson.JsonParser.parseString(invalidOrder.getString("Combat")).getAsJsonObject();
        json.getAsJsonObject("targeting").getAsJsonObject("rangedCounter").addProperty("meleeZeroWeightDistance", 5);
        invalidOrder.putString("Combat", json.toString());
        require(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.read(invalidOrder).isEmpty(), "Reversed distance gates accepted");
        System.out.println("Selection integration: real selector weights, controller delays, custom/zero/disabled snapshots and 16 legacy-field combinations passed");
        }

        private static void checkClientVfxInitialization() throws Exception {
        com.tonywww.elder_bosses.client.vfx.ClientBossVfxController.clear();
        var profileField = com.tonywww.elder_bosses.client.vfx.ClientBossVfxController.class.getDeclaredField("CONSORT_PROFILES");
        profileField.setAccessible(true);
        var profiles = (java.util.Map<?, ?>) profileField.get(null);
        require(profiles.keySet().equals(java.util.EnumSet.allOf(
                com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.class)),
                "Client VFX initialization must cover every registered action");
        for (var action : com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.values()) {
            Object profile = profiles.get(action);
            require(profile != null, "Missing client VFX profile: " + action);
            if (!action.rangedDefense()) continue;
            var style = profile.getClass().getDeclaredMethod("style");
            style.setAccessible(true);
            require(((Enum<?>) style.invoke(profile)).name().equals("SHADER_ONLY"),
                    "Ranged defense must retain its dedicated shader renderer: " + action);
        }
        com.tonywww.elder_bosses.client.vfx.ClientBossVfxController.onTrackingEnd(42);
        com.tonywww.elder_bosses.client.vfx.ClientBossVfxController.clear();
        System.out.println("Client VFX initialization and cleanup passed: " + profiles.size() + " actions, dedicated defense shaders");
    }

    private static void checkConfiguredActions() {
        var builder=new net.minecraftforge.common.ForgeConfigSpec.Builder();
        var values=new com.tonywww.elder_bosses.platforms.config.PromisedConsortConfigValues(builder);
        var spec=builder.build();
        var document=com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        spec.correct(document);
        require(spec.isCorrect(document),"Ranged defaults rejected by ConfigSpec");
        checkFollowupMigration(document);
        spec.setConfig(document);
        var skills=values.skillSnapshot();
        var combat=values.combatSnapshot();
        var ranged=combat.targeting().rangedCounter();
        require(ranged.enterDistance()==9 && ranged.exitDistance()==5,"Ranged snapshot must use approved 9/5 thresholds");
        require(ranged.equals(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortRangedConfig.defaults()),
            "Ranged snapshot and compatibility defaults differ");
        require(ranged.segmentAdjustmentBudgetMultiplier()==2 && ranged.pursuitSelectionWeightMultiplier()==2,"Ranged multipliers must default to two");
        for(var action:com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.values()) {
            var skill=skills.get(action);
            if(skill.hasRangedCounter()) require(skill.number("ranged_counter.min_target_distance")==9,"Dash minimum still blocks 9-block targets: "+action);
            if(action.rangedDefense()) require(skill.number("min_target_distance")==9,"Defense minimum still blocks 9-block targets: "+action);
        }
        var catalog=new com.tonywww.elder_bosses.boss.promisedconsort.action.PromisedConsortActionCatalog(skills);
        checkPursuitEnhancements(ranged,skills);
        checkNewThresholdQualification(ranged);
        checkDamageAndInterception(skills,combat.targeting().rangedCounter());
        com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigProvider.installSkills(values::skillSnapshot);
        var saved=com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.write(combat,skills);
        require(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.read(saved).orElseThrow().combat().equals(combat),
            "Combat NBT roundtrip changed values");
        checkRangedConfigPersistence(saved);
        require(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.read(saved).orElseThrow().skills().equals(skills),"Skill NBT roundtrip changed values");
        var old=com.google.gson.JsonParser.parseString(saved.getString("Skills")).getAsJsonObject();
        for(var action:com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.values()) {
            if(action.rangedDefense()) old.getAsJsonObject("skills").remove(action.name());
        }
        saved.putString("Skills",old.toString());
        require(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.read(saved,skills).isPresent(),"Legacy missing defenses did not migrate");
        for(var action:com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.values()) {
            var skill=skills.get(action);
            if(!skill.hasRangedCounter() && !action.rangedDefense()) continue;
            boolean variant=skill.hasRangedCounter();
            var runtime=new com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortActionRuntime(catalog);
            UUID target=new UUID(4,5);
            var snapshot=runtime.start(action,com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase.PHASE_TWO,100,42,target,variant);
            var timeline=catalog.timeline(snapshot);
            runtime.retarget(new UUID(6,7));
                require(runtime.snapshot(101).orElseThrow().targetId().equals(target),"Ranged target changed mid-action");
                var cooldowns=new com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortCooldowns(catalog);
                cooldowns.recordStarted(action,100);
                cooldowns.recordDefenseEnded(action,100+timeline.totalTicks());
                if(action.rangedDefense()) require(cooldowns.remainingTicks(action,100+timeline.totalTicks())==skill.cooldownTicks(),
                    "Defense individual cooldown does not start on completion/cancellation");
            var restored=com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortActionRuntime.restore(catalog,
                    com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.readAction(
                            com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.writeAction(runtime.persistentState())).orElseThrow());
            require(restored.snapshot(101).orElseThrow().rangedCounter()==variant,"Saved ranged variant lost");
            var origin=net.minecraft.world.phys.Vec3.ZERO;
            var end=new net.minecraft.world.phys.Vec3(0,0,24);
            var variantSkill=catalog.skill(snapshot);
            if(variant) {
                double previous=-1;
                for(double tick=0;tick<=timeline.totalTicks();tick+=0.25) {
                    double pose=com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortAnimationTimeline.sample(tick,snapshot,timeline,variantSkill);
                    require(Double.isFinite(pose) && pose>=previous,"Ranged animation reverses: "+action+" @ "+tick);
                    previous=pose;
                }
                for(int stage=0;stage<timeline.stages().size();stage++) {
                    int contact=timeline.activeStartTick(stage)+variantSkill.integerList("ranged_counter.attack_event_offsets").get(stage);
                    double expected=com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortAnimationTimeline.sample(
                            timeline.activeStartTick(stage),action,timeline,variantSkill.tuning());
                    double actual=com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortAnimationTimeline.sample(contact,snapshot,timeline,variantSkill);
                    require(Math.abs(actual-expected)<0.00001,"Contact pose not aligned to ranged event: "+action+" / "+stage);
                }
                var path=com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedPath.create(action,variantSkill,timeline,
                        origin,new net.minecraft.world.phys.Vec3(0,0,40),new net.minecraft.world.phys.Vec3(0,0,1),point->true);
                end=path.end();
                require(path.at(action,timeline,timeline.totalTicks()-1).distanceTo(end)<0.000001,"Dash did not reach configured path end");
                require(end.distanceTo(path.corner())>15,"Ranged dash is still limited to old 1.25-block displacement");
                var blocked=com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedPath.create(action,variantSkill,timeline,
                        origin,new net.minecraft.world.phys.Vec3(0,0,40),new net.minecraft.world.phys.Vec3(0,0,1),point->point.z<5);
                require(blocked.end().z<5,"Ranged path passed through obstacle");
            }
            var plan=com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortAttackPlan.create(snapshot,variantSkill,timeline,
                    origin,new com.tonywww.elder_bosses.combat.geometry.Vec2(0,1),java.util.Map.of("ranged_origin",origin,"ranged_corner",origin,"ranged_end",end),6);
            require(plan.isEmpty()==(action.rangedDefense() && action!=com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.GRAVITY_REPRISAL),"Defense emits fake damage or reprisal has none");
            for(var strike:plan) require(strike.startTick()<strike.lockTick() && strike.lockTick()<strike.activeTick() && strike.activeTick()<strike.endTick(),"Ranged warning order invalid");
            require(restored.advance(100+timeline.totalTicks()).orElseThrow().completed(),"Ranged action did not finish");
        }
        System.out.println("Configured variants, authoritative paths, defense plans and NBT checks passed");
        document.set("promised_consort.ranged_counter.segment_adjustment_budget_multiplier",3.0);
        document.set("promised_consort.ranged_counter.pursuit_selection_weight_multiplier",0.0);
        document.set("promised_consort.ranged_counter.enter_distance",12.0);
        document.set("promised_consort.ranged_counter.exit_distance",7.0);
        document.set("promised_consort.skills.gravity_dive.ranged_counter.min_target_distance",17.0);
        spec.setConfig(document);
        var custom=values.combatSnapshot();
        require(custom.targeting().rangedCounter().segmentAdjustmentBudgetMultiplier()==3
            && custom.targeting().rangedCounter().pursuitSelectionWeightMultiplier()==0,"Configured multipliers did not enter snapshot");
        require(custom.targeting().rangedCounter().enterDistance()==12 && custom.targeting().rangedCounter().exitDistance()==7
            && values.skillSnapshot().get(com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.GRAVITY_DIVE)
            .number("ranged_counter.min_target_distance")==17,"Custom distances overwritten");
        require(ranged.segmentAdjustmentBudgetMultiplier()==2 && ranged.pursuitSelectionWeightMultiplier()==2
            && ranged.enterDistance()==9,"Existing encounter snapshot changed with configuration");
        var customSaved=com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.write(custom,values.skillSnapshot());
        require(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.read(customSaved).orElseThrow().combat().equals(custom),
            "Custom and zero multipliers did not survive NBT");
        checkPursuitEnhancements(custom.targeting().rangedCounter(),values.skillSnapshot());
        document.set("promised_consort.ranged_counter.segment_adjustment_budget_multiplier",0.0);
        document.set("promised_consort.ranged_counter.pursuit_selection_weight_multiplier",3.0);
        spec.setConfig(document);
        checkPursuitEnhancements(values.combatSnapshot().targeting().rangedCounter(),values.skillSnapshot());
        document.set("promised_consort.ranged_counter.enabled",false);
        spec.setConfig(document);
        checkPursuitEnhancements(values.combatSnapshot().targeting().rangedCounter(),values.skillSnapshot());
        document.set("promised_consort.skills.gravity_bulwark.ranged_damage_reduction",0.35);
        spec.setConfig(document);
        var customBulwark=values.skillSnapshot().get(com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.GRAVITY_BULWARK);
        require(Math.abs(com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedDefense.resolveDamage(
            com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.GRAVITY_BULWARK,0,true,true,20,0,customBulwark).damage()-13)<0.00001,
            "Configured reduction does not affect actual resolver");
        document.set("promised_consort.skills.gravity_dive.ranged_counter.attack_event_offsets",java.util.List.of(8,0));
        spec.setConfig(document);
        boolean rejected=false;
        try {values.skillSnapshot();} catch(IllegalArgumentException exception) {rejected=true;}
        require(rejected,"Out-of-window configured contact silently accepted");
    }

    private static void checkNewThresholdQualification(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortRangedConfig config) {
        var state=new PromisedConsortRangedState(config.rules());
        UUID shooter=new UUID(20,1), distant=new UUID(20,2), near=new UUID(20,3);
        state.damage(shooter,99,9,1,false);
        state.damage(near,99,8.99,1,false);
        require(state.qualifies(shooter,99,9) && !state.qualifies(near,99,9),"Damage threshold boundary is not 9 blocks");
        require(config.segmentAdjustmentBudget(3,state.qualifies(shooter,99,9))==6,"Qualified damage target did not gain pursuit budget");
        require(config.segmentAdjustmentBudget(3,state.qualifies(near,99,9))==3,"Unqualified target gained pursuit budget");
        for(int tick=0;tick<100;tick++) state.observe(distant,tick,9);
        require(state.qualifies(distant,99,9),"Dwell target did not qualify at 9 blocks");
        state.observe(distant,100,5.01);
        state.observe(distant,101,9);
        require(state.qualifies(distant,101,9),"Dwell history cleared above 5-block exit threshold");
        state.observe(distant,102,5);
        state.observe(distant,103,9);
        require(!state.qualifies(distant,103,9),"Dwell history did not clear at 5-block exit threshold");
        require(config.segmentAdjustmentBudget(3,state.qualifies(shooter,99,8.99))==3,"Target below current far distance gained budget");
    }

    private static void checkPursuitEnhancements(
            com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortRangedConfig config,
            com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSkillConfigSnapshot skills) {
        for(boolean qualified:new boolean[]{false,true}) for(double base:new double[]{0,3,16}) {
            double expected=base*(config.enabled()&&qualified?config.segmentAdjustmentBudgetMultiplier():1);
            double budget=config.segmentAdjustmentBudget(base,qualified);
            require(budget==expected,"Segment total budget multiplier differs");
            var position=net.minecraft.world.phys.Vec3.ZERO;
            for(int tick=0;tick<256;tick++) {
                var step=com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionExecutor.repositionStep(
                        position,new net.minecraft.world.phys.Vec3(0,0,100),new com.tonywww.elder_bosses.combat.geometry.Vec2(0,1),4,budget-position.z);
                require(step.movement().length()<=0.25,"Multiplier increased per-tick movement speed");
                position=position.add(step.movement());
                if(tick==7) require(position.z==Math.min(2,budget),"Short adjustment interval guarantees full budget or changes speed");
            }
            require(Math.abs(position.z-budget)<0.00001,"Segment travel did not respect total budget");
            require(com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionExecutor.repositionStep(
                    net.minecraft.world.phys.Vec3.ZERO,new net.minecraft.world.phys.Vec3(0,0,4),
                    new com.tonywww.elder_bosses.combat.geometry.Vec2(0,1),4,budget).movement().lengthSqr()==0,"Multiplier removed 4-block stop");
        }
        int variants=0;
        for(var action:com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.values()) {
            var skill=skills.get(action);
            if(skill.hasRangedCounter()) variants++;
            for(boolean variant:new boolean[]{false,true}) for(double reachable:new double[]{0,6,24,30}) {
                double expected=config.enabled()&&variant&&skill.hasRangedCounter()
                        ?skill.number("ranged_counter.selection_weight_multiplier")*config.pursuitSelectionWeightMultiplier()*Math.min(1,reachable/24):1;
                double actual=com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedDefense.pursuitWeight(config,skill,variant,24,reachable);
                require(Math.abs(actual-expected)<0.00001,"Pursuit weight stacking, reachability or ordinary-target gate differs: "+action);
            }
        }
        require(variants==4,"Pursuit multiplier leaked to other skills");
        var spiral=skills.get(com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.SPIRAL_ASSAULT);
        require(com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedDefense.pursuitWeight(config,spiral,true,0,0)
                ==(config.enabled()?0:1),"Zero-length path has invalid selection weight");
    }

    private static void checkRangedConfigPersistence(net.minecraft.nbt.CompoundTag original) {
        for(boolean explicitNull:new boolean[]{false,true}) {
            var saved=original.copy();
            var combat=com.google.gson.JsonParser.parseString(saved.getString("Combat")).getAsJsonObject();
            var targeting=combat.getAsJsonObject("targeting");
            if(explicitNull) targeting.add("rangedCounter",com.google.gson.JsonNull.INSTANCE);
            else targeting.remove("rangedCounter");
            saved.putString("Combat",combat.toString());
            require(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.read(saved).orElseThrow()
                    .combat().targeting().rangedCounter().equals(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortRangedConfig.defaults()),
                    "Legacy absent/null ranged configuration lost defaults");
        }
        for(int missing=0;missing<4;missing++) {
            var saved=original.copy();
            var combat=com.google.gson.JsonParser.parseString(saved.getString("Combat")).getAsJsonObject();
            var ranged=combat.getAsJsonObject("targeting").getAsJsonObject("rangedCounter");
            ranged.addProperty("enterDistance",14);
            ranged.addProperty("exitDistance",10);
            ranged.addProperty("segmentAdjustmentBudgetMultiplier",0);
            ranged.addProperty("pursuitSelectionWeightMultiplier",3);
            if((missing&1)!=0) ranged.remove("segmentAdjustmentBudgetMultiplier");
            if((missing&2)!=0) ranged.remove("pursuitSelectionWeightMultiplier");
            saved.putString("Combat",combat.toString());
            var restored=com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.read(saved).orElseThrow().combat().targeting().rangedCounter();
            require(restored.segmentAdjustmentBudgetMultiplier()==((missing&1)!=0?2:0)
                    && restored.pursuitSelectionWeightMultiplier()==((missing&2)!=0?2:3),"Missing multiplier fill overwrote saved values");
            require(restored.enterDistance()==14 && restored.exitDistance()==10,"Existing encounter thresholds were rewritten");
        }
        for(String field:java.util.List.of("segmentAdjustmentBudgetMultiplier","pursuitSelectionWeightMultiplier")) {
            for(double invalid:new double[]{-1,17,Double.NaN,Double.POSITIVE_INFINITY}) {
                var saved=original.copy();
                var combat=com.google.gson.JsonParser.parseString(saved.getString("Combat")).getAsJsonObject();
                combat.getAsJsonObject("targeting").getAsJsonObject("rangedCounter").addProperty(field,invalid);
                saved.putString("Combat",combat.toString());
                require(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.read(saved).isEmpty(),"Invalid persisted multiplier accepted: "+field);
            }
        }
    }

    private static void checkDamageAndInterception(
            com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSkillConfigSnapshot skills,
            com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortRangedConfig config) {
        var arrow=source(java.util.Set.of("minecraft:is_projectile"));
        var excluded=source(java.util.Set.of("minecraft:is_projectile","elder_bosses:non_ranged"));
        require(com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedDamage.ranged(arrow,config),"Arrow is not ranged");
        require(!com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedDamage.ranged(excluded,config),"Excluded tag lost priority");
        require(!com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedDamage.ranged(source(java.util.Set.of("elder_bosses:magic")),config),"Magic melee classified as ranged");
        var bulwark=com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.GRAVITY_BULWARK;
        var reprisal=com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.GRAVITY_REPRISAL;
        for(boolean active:new boolean[]{false,true}) for(boolean ranged:new boolean[]{false,true}) {
            var result=com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedDefense.resolveDamage(bulwark,0,active,ranged,20,0,skills.get(bulwark));
            require(Math.abs(result.damage()-(active&&ranged?4:20))<0.00001,"Bulwark phase or reduction differs");
            for(int stage=0;stage<3;stage++) {
                result=com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedDefense.resolveDamage(reprisal,stage,active,ranged,20,30,skills.get(reprisal));
                boolean absorb=active&&ranged&&stage==0;
                require(result.damage()==(absorb?0:20) && result.absorbed()==(absorb?40:30),"Absorption window or cap differs");
            }
        }
        var center=net.minecraft.world.phys.Vec3.ZERO;
        require(com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedDefense.intersectsSphere(
                new net.minecraft.world.phys.Vec3(0,0,-10),new net.minecraft.world.phys.Vec3(0,0,10),center,4),"Fast projectile skipped shield");
        require(!com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedDefense.intersectsSphere(
                new net.minecraft.world.phys.Vec3(4.1,0,-10),new net.minecraft.world.phys.Vec3(4.1,0,10),center,4),"Shield radius expanded");
        var vertices=new java.util.ArrayList<net.minecraft.world.phys.Vec3>();
        var type=com.mojang.blaze3d.vertex.VertexConsumer.class;
        var consumer=(com.mojang.blaze3d.vertex.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(
            type.getClassLoader(),new Class[]{type},(proxy,method,arguments)->{
                if(method.getName().equals("vertex") && arguments.length==4) vertices.add(new net.minecraft.world.phys.Vec3(
                    ((Number)arguments[1]).doubleValue(),((Number)arguments[2]).doubleValue(),((Number)arguments[3]).doubleValue()));
                return method.getReturnType()==type?proxy:null;
            });
        for(double arc:new double[]{90,180,360}) {
            vertices.clear();
            com.tonywww.elder_bosses.client.vfx.ClientConsortEnergyRenderer.defenseSurface(consumer,
                new com.mojang.blaze3d.vertex.PoseStack().last(),center,4,4,0,arc,0xFFFFFF,0.5F);
            require(vertices.size()==1024,"Shield mesh missing faces");
            for(var vertex:vertices) {
            require(Math.abs(vertex.length()-4)<0.00001,"Shield mesh radius differs from interception sphere");
            if(vertex.horizontalDistance()>0.0001 && arc<360) require(vertex.z/vertex.horizontalDistance()>=Math.cos(Math.toRadians(arc/2))-0.00001,
                "Shield visible outside configured arc");
            }
        }
        System.out.println("Actual DamageSource tags, defense phase gates, capped absorption and swept interception checks passed");
    }

    @SuppressWarnings("unchecked")
    private static net.minecraft.world.damagesource.DamageSource source(java.util.Set<String> tags) {
        var key=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                com.tonywww.elder_bosses.platforms.PlatformResourceLocation.parse("minecraft:arrow"));
        var holder=(net.minecraft.core.Holder<net.minecraft.world.damagesource.DamageType>)java.lang.reflect.Proxy.newProxyInstance(
                RangedCounterCheck.class.getClassLoader(),new Class[]{net.minecraft.core.Holder.class},(proxy,method,arguments)->switch(method.getName()) {
                    case "is" -> arguments[0] instanceof net.minecraft.tags.TagKey<?> tag ? tags.contains(tag.location().toString()) : arguments[0].equals(key);
                    case "unwrapKey" -> java.util.Optional.of(key);
                    case "value" -> new net.minecraft.world.damagesource.DamageType("test",0.1F);
                    case "kind" -> net.minecraft.core.Holder.Kind.REFERENCE;
                    case "isBound", "canSerializeIn" -> true;
                    case "tags" -> java.util.stream.Stream.empty();
                    case "toString" -> "test_damage_source";
                    default -> null;
                });
        return new net.minecraft.world.damagesource.DamageSource(holder);
    }

    private static java.util.LinkedHashMap<String,Object> followupChanges(
            com.electronwill.nightconfig.core.CommentedConfig updated,
            com.electronwill.nightconfig.core.CommentedConfig defaults) {
        var changed=new java.util.LinkedHashMap<String,Object>();
        var sections=new java.util.ArrayList<String>();
        sections.add("promised_consort.ranged_counter");
        var oldDefaults=new java.util.LinkedHashMap<String,Double>();
        oldDefaults.put("promised_consort.ranged_counter.enter_distance",14.0);
        oldDefaults.put("promised_consort.ranged_counter.exit_distance",10.0);
        for(var action:com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.values()) {
            String prefix="promised_consort.skills."+action.serializedName();
            if(action.rangedDefense()) sections.add(prefix);
            else if(defaults.contains(prefix+".ranged_counter")) sections.add(prefix+".ranged_counter");
            else continue;
            oldDefaults.put(prefix+(action.rangedDefense()?".":".ranged_counter.")+"min_target_distance",14.0);
        }
        for(String section:sections) if(!updated.contains(section)) {
            changed.put(section,null);
            updated.set(section,com.electronwill.nightconfig.core.CommentedConfig.copy(
                    (com.electronwill.nightconfig.core.CommentedConfig)defaults.get(section)));
        }
        for(var entry:oldDefaults.entrySet()) {
            Object current=updated.get(entry.getKey());
            if(current instanceof Number number && number.doubleValue()==entry.getValue()) {
                changed.put(entry.getKey(),current);
                updated.set(entry.getKey(),defaults.get(entry.getKey()));
            }
        }
        for(String field:java.util.List.of("segment_adjustment_budget_multiplier","pursuit_selection_weight_multiplier")) {
            String key="promised_consort.ranged_counter."+field;
            if(!updated.contains(key)) {
                changed.put(key,null);
                updated.set(key,defaults.get(key));
            }
        }
        return changed;
    }

    private static void checkFollowupMigration(com.electronwill.nightconfig.core.CommentedConfig defaults) {
        var parser=new com.electronwill.nightconfig.toml.TomlParser();
        var writer=new com.electronwill.nightconfig.toml.TomlWriter();
        var custom=parser.parse(writer.writeToString(defaults));
        custom.set("promised_consort.ranged_counter.enter_distance",12.0);
        custom.set("promised_consort.ranged_counter.exit_distance",7.0);
        custom.set("promised_consort.ranged_counter.segment_adjustment_budget_multiplier",0.0);
        custom.set("promised_consort.ranged_counter.pursuit_selection_weight_multiplier",3.0);
        custom.set("promised_consort.skills.gravity_dive.ranged_counter.min_target_distance",17.0);
        var expected=parser.parse(writer.writeToString(custom));
        require(followupChanges(custom,defaults).isEmpty() && custom.equals(expected),"Migration changed custom configuration");
        custom.set("promised_consort.ranged_counter.enter_distance",14.0);
        custom.set("promised_consort.ranged_counter.exit_distance",10.0);
        custom.set("promised_consort.skills.gravity_dive.ranged_counter.min_target_distance",14.0);
        custom.remove("promised_consort.ranged_counter.segment_adjustment_budget_multiplier");
        require(followupChanges(custom,defaults).size()==4,"Migration did not replace only old defaults and missing fields");
        require(((Number)custom.get("promised_consort.ranged_counter.enter_distance")).doubleValue()==9
                && ((Number)custom.get("promised_consort.ranged_counter.exit_distance")).doubleValue()==5
                && ((Number)custom.get("promised_consort.skills.gravity_dive.ranged_counter.min_target_distance")).doubleValue()==9
                && ((Number)custom.get("promised_consort.ranged_counter.segment_adjustment_budget_multiplier")).doubleValue()==2
                && ((Number)custom.get("promised_consort.ranged_counter.pursuit_selection_weight_multiplier")).doubleValue()==3,
                "Migration replacements or custom multiplier differ");
        require(followupChanges(custom,defaults).isEmpty(),"Migration is not idempotent");
    }

    private static void syncFollowupConfigs(boolean write) throws Exception {
        syncConfigs(write, false);
    }

    private static java.util.List<String> selectionConfigKeys() {
        return java.util.List.of("promised_consort.ranged_counter.melee_suppression_distance",
                "promised_consort.ranged_counter.melee_zero_weight_distance", "promised_consort.ranged_counter.distant_melee_weight_multiplier",
                "promised_consort.ranged_counter.pursuit_idle_multiplier", "indicators.malenia_enabled", "indicators.promised_consort_enabled");
    }

    private static java.util.LinkedHashMap<String, Object> selectionChanges(
            com.electronwill.nightconfig.core.CommentedConfig updated, com.electronwill.nightconfig.core.CommentedConfig defaults) {
        var changed = new java.util.LinkedHashMap<String, Object>();
        for (String key : selectionConfigKeys()) if (!updated.contains(key)) {
            changed.put(key, null);
            updated.set(key, defaults.get(key));
        }
        return changed;
    }

    private static void syncConfigs(boolean write, boolean selectionUpdate) throws Exception {
        var defaults=com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig.SPEC.correct(defaults);
        var parser=new com.electronwill.nightconfig.toml.TomlParser();
        for(String file:java.util.List.of("docs/config/elder-bosses-common.example.toml","run/config/elder_bosses-common.toml",
                "versions/1.21.1-neoforge/run/config/elder_bosses-common.toml")) {
            var path=java.nio.file.Path.of(file);
            String source=java.nio.file.Files.readString(path);
            var original=parser.parse(source);
            var updated=parser.parse(source);
            var changed=selectionUpdate ? selectionChanges(updated,defaults) : followupChanges(updated,defaults);
            if(!write) require(changed.isEmpty(),"Configuration still needs followup migration: "+file);
            if(changed.isEmpty()) continue;
            String output=new com.electronwill.nightconfig.toml.TomlWriter().writeToString(updated);
            var roundtrip=parser.parse(output);
            require(roundtrip.equals(updated),"Followup TOML serialization changed values: "+file);
            changed.forEach((key,value)->{if(value==null) roundtrip.remove(key); else roundtrip.set(key,value);});
            require(roundtrip.equals(original),"Followup migration changed protected configuration: "+file);
            var backup=java.nio.file.Path.of(file+(selectionUpdate ? ".pre-ranged-selection-v3" : ".pre-ranged-followup-v2"));
            require(!java.nio.file.Files.exists(backup),"Preserve existing followup backup: "+file);
            require(java.nio.file.Files.readString(path).equals(source),"Configuration changed during migration: "+file);
            java.nio.file.Files.copy(path,backup);
            java.nio.file.Files.writeString(path,output);
            System.out.println("Followup config: "+changed.size()+" changed fields/sections in "+file+"; other values preserved");
        }
        if(!write) System.out.println("All three " + (selectionUpdate ? "selection/indicator" : "followup") + " configurations are current; migration is idempotent");
    }

    private static void syncExample() throws Exception {
        var builder=new net.minecraftforge.common.ForgeConfigSpec.Builder();
        new com.tonywww.elder_bosses.platforms.config.PromisedConsortConfigValues(builder);
        var defaults=com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        builder.build().correct(defaults);
        var path=java.nio.file.Path.of("docs/config/elder-bosses-common.example.toml");
        String source=java.nio.file.Files.readString(path);
        var parser=new com.electronwill.nightconfig.toml.TomlParser();
        var original=parser.parse(source);
        var updated=parser.parse(source);
        var additions=new java.util.ArrayList<String>();
        var paths=new java.util.ArrayList<String>();
        paths.add("promised_consort.ranged_counter");
        for(var action:com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.values()) {
            if(action.rangedDefense()) paths.add("promised_consort.skills."+action.serializedName());
            else if(defaults.contains("promised_consort.skills."+action.serializedName()+".ranged_counter")) paths.add("promised_consort.skills."+action.serializedName()+".ranged_counter");
        }
        for(String key:paths) if(!updated.contains(key)) { updated.set(key,defaults.get(key)); additions.add(key); }
        if(additions.isEmpty()) return;
        String output=new com.electronwill.nightconfig.toml.TomlWriter().writeToString(updated);
        var roundtrip=parser.parse(output);
        require(roundtrip.equals(updated),"Example TOML roundtrip changed new values");
        additions.forEach(roundtrip::remove);
        require(roundtrip.equals(original),"Example update changed original fields");
        var backup=java.nio.file.Path.of(path+".pre-ranged-counter-v1");
        require(!java.nio.file.Files.exists(backup),"Preserve existing ranged backup");
        require(java.nio.file.Files.readString(path).equals(source),"Example changed during sync");
        java.nio.file.Files.copy(path,backup);
        java.nio.file.Files.writeString(path,output);
        System.out.println("Example configuration: "+additions.size()+" new sections; original fields preserved; development configs untouched");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
