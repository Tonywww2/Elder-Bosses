import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.tonywww.elder_bosses.boss.malenia.action.MaleniaActionCatalog;
import com.tonywww.elder_bosses.boss.malenia.config.MaleniaConfigProvider;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaPhase;
import com.tonywww.elder_bosses.boss.malenia.runtime.MaleniaActionRuntime;
import com.tonywww.elder_bosses.boss.promisedconsort.action.PromisedConsortActionCatalog;
import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigProvider;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase;
import com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortActionRuntime;
import com.tonywww.elder_bosses.combat.action.ActionLifecycleEvent;
import com.tonywww.elder_bosses.client.vfx.ClientConsortBladeTrails;
import com.tonywww.elder_bosses.platforms.command.PlatformSkillTestCommands;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ActionDebugCheck {
    private static int assertions;
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RETARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");

    public static void main(String[] arguments) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var bootstrap = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrap.setAccessible(true);
        bootstrap.setBoolean(null, true);
        net.minecraft.core.registries.BuiltInRegistries.bootStrap();
        if (List.of(arguments).contains("--sync-meteor-rhythm")) {
            syncMeteorRhythm();
            return;
        }
        if (List.of(arguments).contains("--check-ground-debris")) {
            checkGroundDebris();
            System.out.println("Ground debris passed: " + assertions + " checks; swept blade, actual voxel top faces and bounded sample continuity");
            return;
        }
        for (String path : List.of("docs/config/elder-bosses-common.example.toml", "run/config/elder_bosses-common.toml",
                "versions/1.21.1-neoforge/run/config/elder_bosses-common.toml")) {
            try (var reader = Files.newBufferedReader(Path.of(path))) {
                CommentedConfig configuration = new TomlParser().parse(reader);
                require(configuration.get("malenia.debug.action_broadcast") instanceof Boolean, "Missing Malenia broadcast flag in " + path);
                require(configuration.get("promised_consort.debug.action_broadcast") instanceof Boolean, "Missing Consort broadcast flag in " + path);
            }
        }
        CommentedConfig defaults = CommentedConfig.inMemory();
        ElderBossesCommonConfig.SPEC.correct(defaults);
        ElderBossesCommonConfig.SPEC.setConfig(defaults);
        require(!ElderBossesCommonConfig.VALUES.maleniaDebugActionBroadcast(), "Malenia debug defaults on");
        require(!ElderBossesCommonConfig.VALUES.promisedConsortDebugActionBroadcast(), "Consort debug defaults on");
        AtomicBoolean maleniaEnabled = new AtomicBoolean(false);
        AtomicBoolean consortEnabled = new AtomicBoolean(false);
        MaleniaConfigProvider.installDebugActionBroadcast(maleniaEnabled::get);
        PromisedConsortConfigProvider.installDebugActionBroadcast(consortEnabled::get);
        require(!MaleniaConfigProvider.debugActionBroadcastEnabled() && !PromisedConsortConfigProvider.debugActionBroadcastEnabled(), "Disabled debug provider");
        maleniaEnabled.set(true);
        require(MaleniaConfigProvider.debugActionBroadcastEnabled() && !PromisedConsortConfigProvider.debugActionBroadcastEnabled(), "Boss switches must be independent");
        maleniaEnabled.set(false);
        consortEnabled.set(true);
        require(!MaleniaConfigProvider.debugActionBroadcastEnabled() && PromisedConsortConfigProvider.debugActionBroadcastEnabled(), "Live debug switch not observed");
        consortEnabled.set(false);

        MaleniaActionCatalog malenia = new MaleniaActionCatalog(ElderBossesCommonConfig.VALUES.maleniaSkillSnapshot());
        PromisedConsortActionCatalog consort = new PromisedConsortActionCatalog(ElderBossesCommonConfig.VALUES.promisedConsortSkillSnapshot());
        checkMalenia(malenia);
        checkConsort(consort);
        for (double ratio : new double[]{0, 0.01, 0.05, 0.65, Double.NaN}) {
            float gate = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.threshold(1000, ratio);
            require(gate >= 50, "Phase transition threshold must never be below five percent");
            for (float health : new float[]{50, 1, 0, -100000, -Float.MAX_VALUE, Float.NEGATIVE_INFINITY}) {
                float safe = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.protect(health, 1000, 1000, ratio);
                require(Float.isFinite(safe) && safe >= gate, "Extreme incoming health loss bypasses the phase gate");
            }
        }
        checkComboReach(consort);
        checkBurstCadence();
        checkChainRecovery(consort);
        checkBurstPersistence(consort);
        checkHolyFlight(consort);
        checkLionPath();
        checkGroundMovement();
        checkLionPlan(consort);
        checkMeteorSequence(consort);
        checkCrossLeap(consort);
        checkAdvance(consort);
        checkGravityDiveFlight(consort);
        checkLightspeedPath(consort);
        checkBladeEnchantment();
        checkSkillTestCommands();
        if (List.of(arguments).contains("--sync-burst-configs")) syncBurstConfigs(true);
        if (List.of(arguments).contains("--check-burst-configs")) syncBurstConfigs(false);
        if (List.of(arguments).contains("--sync-flight-configs")) syncFlightConfigs(true);
        if (List.of(arguments).contains("--check-flight-configs")) syncFlightConfigs(false);
        if (List.of(arguments).contains("--sync-meteor-configs")) syncMeteorConfigs(true);
        if (List.of(arguments).contains("--check-meteor-configs")) syncMeteorConfigs(false);
        if (List.of(arguments).contains("--sync-cross-leap-configs")) syncCrossLeapConfigs(true);
        if (List.of(arguments).contains("--check-cross-leap-configs")) syncCrossLeapConfigs(false);
        System.out.println("Action debug passed: " + assertions + " checks; start/end/cancel/replace/resume, " + PromisedConsortActionId.values().length + " Consort command routes and four ranged variants");
    }

    private static void checkAdvance(PromisedConsortActionCatalog catalog) {
        var id = PromisedConsortActionId.SPIRAL_ASSAULT;
        Vec3 origin = new Vec3(0, 64, 0);
        for (boolean ranged : new boolean[]{false, true}) {
            var runtime = new PromisedConsortActionRuntime(catalog);
            var action = runtime.start(id, PromisedConsortPhase.PHASE_TWO, 100, 42, TARGET, ranged);
            var skill = catalog.skill(action);
            var timeline = catalog.timeline(action);
            int offset = ranged ? skill.integerList("ranged_counter.attack_event_offsets").get(0) : 0;
            var path = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortCrossLeapPath.advance(timeline, offset);
            Vec3 end = path.advanceDestination(origin, new Vec3(0, 64, 40), new Vec3(0, 0, 1), ranged ? 24 : 16);
            require(path.supports(origin, end) && end.z >= 12 && path.height() <= 2, "Advance is not a bounded low medium-distance leap");
            Vec3 previous = origin;
            for (int tick = 0; tick <= path.landingTick(); tick++) {
                Vec3 point = path.advanceAt(origin, end, tick);
                require(point.distanceTo(previous) <= 2.001 && point.y <= 66.001, "Advance arc exceeds speed/height");
                previous = point;
            }
            require(previous.equals(end), "Advance does not land at its locked destination");
            for (double distance : new double[]{0, 2, 4, 8, 16, 40}) {
                Vec3 nearEnd = path.advanceDestination(origin, origin.add(0, 0, distance), new Vec3(0, 0, 1), ranged ? 24 : 16);
                Vec3 launch = path.advanceAt(origin, nearEnd, path.takeoffTick() + 1).subtract(origin);
                require(nearEnd.z >= Math.min(6, path.maximumDistance(ranged ? 24 : 16)), "Close target collapses forward leap");
                require(launch.z > Math.abs(launch.y) * 2, "Advance starts vertically instead of lunging forward");
                require(path.supports(origin, nearEnd), "Forward launch exceeds the existing travel budget");
            }
            require(Math.abs(com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortAnimationTimeline.sample(path.takeoffTick(),
                action, timeline, skill) - 7) < 0.00001, "Advance lift does not coincide with its faster authored takeoff");
            var sound = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionSoundPlan.schedule(action, timeline, skill)
                .stream().filter(cue -> cue.cue().key().equals("leap")).toList();
            require(sound.size() == 1 && sound.get(0).tick() == path.takeoffTick(), "Advance rush sound plays at landing instead of takeoff");
            var points = java.util.Map.of("advance_origin", origin, "advance_end", end);
            var plan = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortAttackPlan.create(action, skill, timeline,
                origin, new com.tonywww.elder_bosses.combat.geometry.Vec2(0, 1), points, 6);
            require(plan.size() == 2 && plan.get(0).id().equals("spin") && plan.get(1).id().equals("slam"), "Advance changed the two persisted attack IDs");
            for (int index = 0; index < plan.size(); index++) {
                var strike = plan.get(index);
                int hitOffset = ranged ? skill.integerList("ranged_counter.attack_event_offsets").get(index) : 0;
                require(strike.activeTick() == 100 + timeline.activeStartTick(index) + hitOffset, "Advance changed a damage contact time");
                require(strike.lockTick() == 100 + path.takeoffTick() && strike.baseY() == end.y, "Advance warning is not frozen at the ground landing");
            }
            var airbornePlan = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortAttackPlan.create(action, skill, timeline,
                origin.add(0, 2, 6), new com.tonywww.elder_bosses.combat.geometry.Vec2(0, 1), points, 6);
            require(airbornePlan.equals(plan), "Advance warning follows airborne body instead of locked landing");
        }
        require(Math.abs(com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortGravityRockEntity.COLLISION_SIZE - 1.0125F) < 1.0e-6,
            "Rock registration size does not match its shared 35 percent enlargement");
        var lion = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortLionClawPath.create(32, 6, 6, 1.25);
        require(lion.height() >= 5, "Lion claw is not visibly higher than advance");
    }

    private static void checkGravityDiveFlight(PromisedConsortActionCatalog catalog) {
        Vec3 origin = new Vec3(0, 64, 0);
        for (boolean ranged : new boolean[]{false, true}) {
            var runtime = new PromisedConsortActionRuntime(catalog);
            var action = runtime.start(PromisedConsortActionId.GRAVITY_DIVE, PromisedConsortPhase.PHASE_TWO, 100, 42, TARGET, ranged);
            var skill = catalog.skill(action);
            var timeline = catalog.timeline(action);
            int offset = ranged ? skill.integerList("ranged_counter.attack_event_offsets").get(0) : 0;
            var path = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortCrossLeapPath.gravityDive(timeline, offset);
            Vec3 end = path.destination(origin, origin.add(0, 0, 12), path.maximumDistance(ranged ? 24 : 16), 1.5);
            require(path.supports(origin, end), "Gravity dive exceeds flight budget");
            Vec3 previous = origin;
            for (int tick = 0; tick <= path.landingTick(); tick++) {
                Vec3 point = path.at(origin, end, tick);
                require(point.distanceTo(previous) <= 2.001, "Gravity dive flight step exceeds collision budget");
                if (tick > path.takeoffTick() && tick < path.landingTick()) require(point.y > origin.y, "Gravity dive lands before the final spinning contact");
                previous = point;
            }
            require(previous.equals(end), "Gravity dive misses its actual landing point");
            require(Math.abs(com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortAnimationTimeline.sample(path.takeoffTick(), action, timeline, skill) - 12) < 0.00001,
                "Gravity spin does not start at actual takeoff");
            require(Math.abs(com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortAnimationTimeline.sample(path.landingTick(), action, timeline, skill) - 38) < 0.00001,
                "Gravity spin completion does not match landing");
            var points = java.util.Map.of("dive_origin", origin, "dive_end", end);
            var plan = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortAttackPlan.create(action, skill, timeline,
                origin, new com.tonywww.elder_bosses.combat.geometry.Vec2(0, 1), points, 6);
            require(plan.size() == 2 && plan.get(0).id().equals("sword") && plan.get(1).id().equals("impact"), "Gravity dive added a separate finishing attack");
            for (var strike : plan) require(strike.baseY() == end.y && strike.lockTick() == 100 + path.takeoffTick(), "Gravity warning is not grounded and frozen at launch");
            require(plan.equals(com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortAttackPlan.create(action, skill, timeline,
                origin.add(0, 3, 4), new com.tonywww.elder_bosses.combat.geometry.Vec2(0, 1), points, 6)), "Gravity warning moves with the spinning body");
        }
    }

    private static void checkComboReach(PromisedConsortActionCatalog catalog) {
        var id = PromisedConsortActionId.L_COMBO_CROSS;
        var runtime = new PromisedConsortActionRuntime(catalog);
        var action = runtime.start(id, PromisedConsortPhase.PHASE_ONE, 100, 42, TARGET);
        var skill = catalog.skill(action);
        var plan = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortAttackPlan.create(action, skill, catalog.timeline(action),
            Vec3.ZERO, new com.tonywww.elder_bosses.combat.geometry.Vec2(0, 1), java.util.Map.of(), 6);
        require(plan.size() == 3, "Combo reach adjustment added a hit");
        double expected = skill.number("range") * skill.rangeMultiplier() * 1.3 * 1.15 + 0.8;
        for (var strike : plan) {
            var sector = (com.tonywww.elder_bosses.combat.geometry.Sector) strike.shape();
            require(Math.abs(sector.radius() - expected) < 1.0e-8, "Combo reach did not expand with its authoritative warning");
        }
        require(com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortAttackPlan.comboReachMultiplier(PromisedConsortActionId.GRAVITY_METEOR) == 1,
            "Combo reach leaked into projectile or gravity areas");
    }

        private static void checkCrossLeap(PromisedConsortActionCatalog catalog) {
            var id = PromisedConsortActionId.CROSS_LEAP_COMBO;
            var skill = catalog.skillConfig().get(id);
            var definition = catalog.get(id);
            require(definition.isAvailableIn(PromisedConsortPhase.PHASE_TWO) && !definition.isAvailableIn(PromisedConsortPhase.PHASE_ONE), "Cross leap must be P2-only");
            require(definition.cooldownTicks() == 260 && definition.weight() == 0.5 && definition.cooldownGroup().equals("cross_leap_combo"), "Cross leap shares old cooldown/config");
            var timeline = definition.timeline();
            require(timeline.totalTicks() == 151 && timeline.stages().size() == 8, "Cross leap stage timeline mismatch");
            var opening = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortCrossLeapPath.opening(timeline, skill.number("leap_height"));
            var finisher = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortCrossLeapPath.finisher(timeline, skill.number("leap_height"));
            var origin = new Vec3(0, 64, 0);
            var end = opening.destination(origin, new Vec3(0, 64, 30), skill.number("leap_distance"), 4);
            require(end.equals(new Vec3(0, 64, 16)), "Cross leap distance cap differs");
            require(opening.destination(origin, new Vec3(0, 64, 10), 16, 4).equals(new Vec3(0, 64, 6)), "Cross leap ignores four-block stop");
            for (var path : List.of(opening, finisher)) {
                var destination = path == opening ? end : origin;
                require(path.supports(origin, destination), "Default flight exceeds speed budget");
                var previous = origin;
                for (int tick = 0; tick <= path.landingTick(); tick++) {
                    var current = path.at(origin, destination, tick);
                    require(current.distanceTo(previous) <= 2 && current.y >= 64 && current.y <= 65.2 + 0.00001, "Cross leap movement bound failed");
                    if (tick <= path.takeoffTick()) require(current.equals(origin), "Cross leap moves before lock/takeoff");
                    previous = current;
                }
                require(previous.equals(destination), "Cross leap misses exact landing");
            }
            var runtime = new PromisedConsortActionRuntime(catalog);
            var action = runtime.start(id, PromisedConsortPhase.PHASE_TWO, 100, 42, TARGET);
            var points = new java.util.HashMap<String, Vec3>();
            points.put("cross_opening_end", end);
            points.put("cross_finisher_end", origin);
            var plan = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortAttackPlan.create(action, skill, timeline,
                    origin, new com.tonywww.elder_bosses.combat.geometry.Vec2(0, 1), points, 6);
            require(plan.size() == 8 && plan.stream().filter(strike -> strike.instantGuard()).count() == 5, "Cross leap contact/guard counts differ");
            int[] contacts = {27,41,54,70,111,115,120,127};
            for (int index = 0; index < contacts.length; index++) {
                var strike = plan.get(index);
                require(strike.activeTick() == 100 + contacts[index], "Cross leap contact timing differs");
                require(com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortAnimationTimeline.sample(timeline.activeStartTick(index), id,
                        timeline, skill.tuning()) == contacts[index], "Cross leap pose/hit clock differs");
            }
            require(plan.get(0).lockTick() == 100 + opening.takeoffTick() && plan.get(4).lockTick() == 100 + finisher.takeoffTick(), "Leap telegraph lock differs from path lock");
            require(plan.get(0).baseY() == end.y && plan.get(4).baseY() == origin.y, "Flight warning projected above landing ground");
                points.put("cross_opening_end", new Vec3(12, 65, 0));
                points.put("cross_finisher_end", new Vec3(-5, 65, 0));
                var movedPlan = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortAttackPlan.create(action, skill, timeline,
                    origin.add(0, 1.2, 0), new com.tonywww.elder_bosses.combat.geometry.Vec2(1, 0), points, 6);
                for (int index : new int[]{0,4}) require(movedPlan.get(index).trackFrom(plan.get(index), plan.get(index).lockTick() + 1).equals(plan.get(index)),
                    "Jump warning retargets or rises after its takeoff lock");
                for (var formula : java.util.Map.of("opening_damage", new double[]{3,0.55}, "spin_damage", new double[]{3,0.50},
                    "finisher_damage", new double[]{6,0.95}, "clone_damage", new double[]{1,0.20}, "holy_ring_damage", new double[]{2,0.35}).entrySet()) {
                require(skill.damage(formula.getKey()).equals(new com.tonywww.elder_bosses.combat.damage.DamageFormula(formula.getValue()[0], formula.getValue()[1])),
                    "Approved cross-leap damage differs: " + formula.getKey());
                }
            require(com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortAnimationTimeline.swordWindows(id).stream()
                    .mapToInt(window -> Integer.bitCount(window.sides())).sum() == 8, "Cross leap dual swords need eight sound cues");
            var tag = com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.write(ElderBossesCommonConfig.VALUES.promisedConsortCombatSnapshot(), catalog.skillConfig());
            var serialized = com.google.gson.JsonParser.parseString(tag.getString("Skills")).getAsJsonObject();
            serialized.getAsJsonObject("skills").remove("CROSS_LEAP_COMBO");
            tag.putString("Skills", serialized.toString());
            var restored = com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.read(tag, catalog.skillConfig()).orElseThrow();
            require(restored.skills().get(id).equals(skill), "Old encounter did not acquire missing new-skill config");
            for (var old : PromisedConsortActionId.values()) if (old != id) require(restored.skills().get(old).equals(catalog.skillConfig().get(old)), "Old encounter skill changed");
                var full = com.google.gson.JsonParser.parseString(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt
                    .write(restored.combat(), restored.skills()).getString("Skills")).getAsJsonObject();
                var savedSkill = full.getAsJsonObject("skills").getAsJsonObject("CROSS_LEAP_COMBO");
                savedSkill.addProperty("enabled", false);
                savedSkill.addProperty("weight", 0);
                savedSkill.getAsJsonObject("numbers").addProperty("leap_distance", 0);
                tag.putString("Skills", full.toString());
                var custom = com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.read(tag, catalog.skillConfig()).orElseThrow().skills().get(id);
                require(!custom.enabled() && custom.weight() == 0 && custom.number("leap_distance") == 0, "Saved custom/zero cross-leap values were overwritten");
                savedSkill.getAsJsonObject("numbers").addProperty("leap_height", 5);
                tag.putString("Skills", full.toString());
                require(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.read(tag, catalog.skillConfig()).isEmpty(), "Invalid leap height accepted");
                savedSkill.getAsJsonObject("numbers").addProperty("leap_height", 1.2);
                savedSkill.getAsJsonObject("integerLists").getAsJsonArray("windup_ticks").set(0, new com.google.gson.JsonPrimitive(2));
                tag.putString("Skills", full.toString());
                require(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.read(tag, catalog.skillConfig()).isEmpty(), "Too-short leap windup accepted");
                var savedPoints = java.util.Map.of("cross_opening_origin", new com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionExecutor.PersistentPoint(0,64,0),
                    "cross_opening_end", new com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionExecutor.PersistentPoint(0,64,16),
                    "cross_opening_frozen", new com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionExecutor.PersistentPoint(0,64,16),
                    "cross_flight_gravity", new com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionExecutor.PersistentPoint(0,0,0),
                    "cross_cancelled", new com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionExecutor.PersistentPoint(0,0,0));
                var savedExecutor = new com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionExecutor.PersistentState(action.sequence(),
                    new com.tonywww.elder_bosses.combat.geometry.Vec2(0,1), savedPoints, java.util.Set.of(action.sequence() + ":cross_move:17"), java.util.Map.of(), List.of(), List.of(), List.of());
                require(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.readActionExecutor(
                    com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.writeActionExecutor(savedExecutor)).orElseThrow().equals(savedExecutor),
                    "Cross-leap flight/frozen/gravity/cancel state lost in serialization");
            com.tonywww.elder_bosses.client.vfx.ClientBossVfxController.clear();
            System.out.println("Cross leap: P2 independent config, bounded two flights, eight contacts/five guards/eight blades, locks, legacy snapshot and client initialization passed");
        }

        private static void syncCrossLeapConfigs(boolean write) throws Exception {
            syncConfigFields(write, "promised_consort.skills.cross_leap_combo", List.of("enabled", "weight", "cooldown_ticks", "hyper_armor_active", "range_multiplier",
                    "leap_distance", "leap_height", "opening_range", "spin_range", "finisher_range", "windup_ticks", "active_ticks", "recovery_ticks",
                    "opening_damage.flat", "opening_damage.attack_ratio", "spin_damage.flat", "spin_damage.attack_ratio",
                    "finisher_damage.flat", "finisher_damage.attack_ratio", "holy_ring_damage.flat", "holy_ring_damage.attack_ratio",
                    "clone_damage.flat", "clone_damage.attack_ratio"), ".pre-cross-leap-v1");
        }

        private static void checkLightspeedPath(PromisedConsortActionCatalog catalog) {
            for (var facing : List.of(new com.tonywww.elder_bosses.combat.geometry.Vec2(0, 1),
                    new com.tonywww.elder_bosses.combat.geometry.Vec2(1, 0),
                    new com.tonywww.elder_bosses.combat.geometry.Vec2(0, -1),
                    new com.tonywww.elder_bosses.combat.geometry.Vec2(-1, 0))) {
                var diagonal = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionExecutor.sideDashDirection(facing);
                require(Math.abs(Math.hypot(diagonal.x(), diagonal.z()) - 1) < 1.0e-8, "Side dash changes its travel budget");
                require(diagonal.x() * facing.x() + diagonal.z() * facing.z() > 0.7, "Side dash has no forward component");
                require(-diagonal.x() * facing.z() + diagonal.z() * facing.x() > 0.7, "Side dash lost its lateral direction");
            }
        var id = PromisedConsortActionId.LIGHTSPEED_DASH;
        var timeline = catalog.get(id).timeline();
        var path = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortLightspeedPath.from(timeline, 6);
        require(path.usable() && path.supports(16) && !path.supports(257) && !path.supports(Double.NaN), "Invalid lightspeed route budget");
        var origin = new Vec3(2, 64, 3);
        var end = origin.add(0, 0, 16);
        var previous = origin;
        for (int tick = 0; tick <= timeline.totalTicks(); tick++) {
            var current = path.at(origin, end, tick);
            if (tick <= path.departureTick()) require(current.equals(origin), "Body drifts during clone charge");
            require(current.distanceTo(previous) <= 8 && current.y == origin.y, "Unbounded or vertical lightspeed movement");
            previous = current;
        }
        require(previous.equals(end) && path.at(origin, end, path.impactTick()).equals(end), "Lightspeed body misses contact destination");
        var runtime = new PromisedConsortActionRuntime(catalog);
        var action = runtime.start(id, PromisedConsortPhase.PHASE_TWO, 100, 42, TARGET);
        var points = java.util.Map.of("lightspeed_origin", origin, "lightspeed_end", end);
        var plan = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortAttackPlan.create(action, catalog.skill(action), timeline,
            origin, new com.tonywww.elder_bosses.combat.geometry.Vec2(0, 1), points, 6);
        var moved = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortAttackPlan.create(action, catalog.skill(action), timeline,
            end, new com.tonywww.elder_bosses.combat.geometry.Vec2(0, 1), points, 6);
        require(plan.equals(moved), "Lightspeed prediction drifts with body movement");
        require(plan.size() == 6 && plan.stream().filter(strike -> strike.id().startsWith("clone_")).count() == 4,
            "Lightspeed original four clones/body/trail count changed");
        for (int index = 0; index < plan.size(); index++) {
            var strike = plan.get(index);
            require(strike.activeTick() == 100 + timeline.activeStartTick(index), "Lightspeed event timing changed");
            require(strike.lockTick() == 100 + path.lockTick() && strike.startTick() <= strike.lockTick(), "Lightspeed does not share early route lock");
            var rotated = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortAttackPlan.create(action, catalog.skill(action), timeline,
                end, new com.tonywww.elder_bosses.combat.geometry.Vec2(1, 0), points, 6).get(index);
            require(rotated.trackFrom(strike, strike.lockTick() + 1).equals(strike), "Locked lightspeed route retargets");
        }
        require(plan.get(4).instantGuard() && !plan.get(5).instantGuard(), "Lightspeed guard channels changed");
        System.out.println("Lightspeed route: charge holds, final body rush, shared frozen six-event path and budget passed");
        }

        private static void checkBurstCadence() {
        var settings = com.tonywww.elder_bosses.boss.promisedconsort.controller.PromisedConsortBurstCadence.Settings.defaults();
        var observed = new java.util.HashSet<Integer>();
        for (long seed = 0; seed < 64; seed++) {
            var burst = new com.tonywww.elder_bosses.boss.promisedconsort.controller.PromisedConsortBurstCadence(settings);
            burst.start(seed);
            int count = burst.remaining();
            observed.add(count);
            require(count >= 2 && count <= 4, "Burst must contain two to four skills");
            burst.start(seed + 1);
            require(burst.remaining() == count, "Linked branch rerolled the current burst");
            for (int completed = 1; completed <= count; completed++) {
                require(burst.mayShortenRecovery() == (completed < count), "Final skill must retain recovery");
                var restored = new com.tonywww.elder_bosses.boss.promisedconsort.controller.PromisedConsortBurstCadence(settings);
                restored.restore(burst.remaining());
                var delay = burst.complete(seed + completed);
                require(delay.equals(restored.complete(seed + completed)), "Restored burst changed next delay");
                require(delay.breathing() == (completed == count), "Breathing occurs before or after the burst boundary");
                if (delay.breathing()) {
                    require(delay.ticks() >= 24 && delay.ticks() <= 36, "Wrong breathing duration");
                    require(delay.scaled(0, true) == delay.ticks() && delay.scaled(0.5, true) == delay.ticks(), "Ranged pursuit shortened breathing");
                } else {
                    require(delay.ticks() >= 0 && delay.ticks() <= 2, "Wrong link duration");
                    require(delay.scaled(0.5, true) == (delay.ticks() + 1) / 2, "Ranged multiplier not applied to links");
                }
                require(delay.scaled(0.5, false) == delay.ticks(), "Ordinary link changed");
            }
            burst.start(seed + 100);
            burst.reset();
            require(burst.remaining() == 0, "Cancellation/reset retains a stale burst");
        }
        require(observed.equals(java.util.Set.of(2, 3, 4)), "Burst sampling never selects some lengths");
        System.out.println("Burst cadence: 2..4 skills, 0..2 links, 24..36 rest, ranged scaling and restore passed");
    }

    private static void checkChainRecovery(PromisedConsortActionCatalog catalog) {
        for (var action : PromisedConsortActionId.values()) {
            if (!com.tonywww.elder_bosses.boss.promisedconsort.controller.PromisedConsortCombatController.chainable(action)) continue;
            var runtime = new PromisedConsortActionRuntime(catalog);
            List<ActionLifecycleEvent> events = new ArrayList<>();
            runtime.setLifecycleListener(events::add);
            runtime.start(action, PromisedConsortPhase.PHASE_TWO, 100, 42, TARGET);
            var timeline = catalog.get(action).timeline();
            int finalRecovery = timeline.activeEndTick(timeline.stages().size() - 1);
            int end = Math.min(timeline.totalTicks(), finalRecovery + 6);
            for (int tick = 0; tick < end; tick++) {
                require(runtime.finishRecovery(100 + tick, 6).isEmpty(), "Chaining cuts a hit/intermediate stage/required recovery: " + action);
            }
            require(runtime.finishRecovery(100 + end, 6).orElseThrow().completed(), "Chain recovery did not complete");
            require(events.size() == 2 && events.get(1).outcome() == ActionLifecycleEvent.Outcome.COMPLETED, "Chaining must complete, not cancel");
            require(runtime.finishRecovery(101 + end, 6).isEmpty(), "Chain completion repeated");
        }
        require(!com.tonywww.elder_bosses.boss.promisedconsort.controller.PromisedConsortCombatController.chainable(PromisedConsortActionId.GRAVITY_METEOR)
                && !com.tonywww.elder_bosses.boss.promisedconsort.controller.PromisedConsortCombatController.chainable(PromisedConsortActionId.GRAVITY_REPRISAL),
                "Aerial and defensive actions must retain full duration");
        System.out.println("Chain recovery: intermediate/hit windows retained; final recovery completion once");
    }

        private static void checkBurstPersistence(PromisedConsortActionCatalog catalog) {
        var combat = ElderBossesCommonConfig.VALUES.promisedConsortCombatSnapshot();
        var settings = combat.selector().burst();
        var tag = com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.write(combat, catalog.skillConfig());
        var json = com.google.gson.JsonParser.parseString(tag.getString("Combat")).getAsJsonObject();
        json.getAsJsonObject("selector").remove("burst");
        tag.putString("Combat", json.toString());
        require(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.read(tag).orElseThrow()
            .combat().selector().burst().equals(settings), "Old encounter did not receive burst defaults");
        var partial = new com.google.gson.JsonObject();
        partial.addProperty("minimumLinkTicks", 0);
        partial.addProperty("maximumLinkTicks", 0);
        partial.addProperty("chainRecoveryTicks", 9);
        json.getAsJsonObject("selector").add("burst", partial);
        tag.putString("Combat", json.toString());
        var restored = com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.read(tag).orElseThrow().combat().selector().burst();
        require(restored.maximumLinkTicks() == 0 && restored.chainRecoveryTicks() == 9 && restored.maximumSkills() == 4,
            "Partial burst snapshot lost explicit values");
        partial.addProperty("maximumSkills", 1);
        tag.putString("Combat", json.toString());
        require(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.read(tag).isEmpty(), "Invalid burst range accepted");
        long[] now = {100};
        int[] forcedRecovery = {0};
        int[] normalTargetQueries = {0};
        var hostType = com.tonywww.elder_bosses.boss.promisedconsort.controller.PromisedConsortCombatController.Host.class;
        var host = (com.tonywww.elder_bosses.boss.promisedconsort.controller.PromisedConsortCombatController.Host) java.lang.reflect.Proxy.newProxyInstance(
            hostType.getClassLoader(), new Class[]{hostType}, (proxy, method, arguments) -> switch (method.getName()) {
                case "gameTime" -> now[0];
                case "visibleEligibleTargets" -> { normalTargetQueries[0]++; yield List.of(); }
                case "lockedActionTarget" -> java.util.Optional.empty();
                case "phase" -> PromisedConsortPhase.PHASE_TWO;
                case "combatState" -> com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortCombatState.PHASE_2;
                    case "consumeForcedRecoveryTicks" -> { int value = forcedRecovery[0]; forcedRecovery[0] = 0; yield value; }
                    case "hasForcedRecovery" -> forcedRecovery[0] > 0;
                case "actionHit" -> true;
                case "actionBlocked", "isRangedTarget", "canSelectWeightedMeteor" -> false;
                default -> throw new AssertionError("Unexpected controller host call " + method.getName());
            });
        var runtime = new PromisedConsortActionRuntime(catalog);
        var controller = new com.tonywww.elder_bosses.boss.promisedconsort.controller.PromisedConsortCombatController(host, combat, catalog, runtime,
            new com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortCooldowns(catalog),
            new com.tonywww.elder_bosses.boss.promisedconsort.selection.PromisedConsortSkillSelector(catalog, combat.selector()));
        controller.force(PromisedConsortActionId.STOMP, PromisedConsortPhase.PHASE_TWO);
        var lastInGroup = new net.minecraft.nbt.CompoundTag();
        lastInGroup.putInt("RemainingSkills", 1);
        lastInGroup.putLong("WaitTicks", 0);
        controller.restoreCadence(lastInGroup);
        controller.tick();
        now[0] += catalog.get(PromisedConsortActionId.STOMP).timeline().totalTicks();
        require(controller.tick().actionEnded().orElseThrow().completed(), "Controller did not finish final skill");
        var saved = controller.saveCadence();
        require(saved.getInt("RemainingSkills") == 0 && saved.getLong("WaitTicks") >= 24 && saved.getLong("WaitTicks") <= 36,
            "Controller did not schedule group breathing");
        controller.tick();
        require(controller.saveCadence().equals(saved), "Repeated controller tick changed burst boundary");
        now[0] += 1000;
        controller.restoreCadence(saved);
        require(controller.saveCadence().equals(saved), "Controller restore did not rebase the full breathing interval");
        controller.clearCooldowns();
        require(controller.saveCadence().getLong("WaitTicks") == 0, "Debug reset retained waiting time");
        controller.force(PromisedConsortActionId.STOMP, PromisedConsortPhase.PHASE_TWO);
        var forcedState = new net.minecraft.nbt.CompoundTag();
        forcedState.putInt("RemainingSkills", 3);
        controller.restoreCadence(forcedState);
        forcedRecovery[0] = 18;
        controller.tick();
        now[0] += catalog.get(PromisedConsortActionId.STOMP).timeline().totalTicks();
        controller.tick();
        require(controller.saveCadence().getLong("WaitTicks") == 18 && controller.saveCadence().getInt("RemainingSkills") == 0,
            "Burst swallowed forced recovery or retained a interrupted chain");
        forcedState.putLong("WaitTicks", 4000);
        controller.restoreCadence(forcedState);
        require(controller.saveCadence().getLong("WaitTicks") == 4000, "Restored custom forced wait was shortened");
        now[0]++;
        var participant = java.util.UUID.fromString("e03c16fc-c178-4387-958a-d5575e840c27");
        var opening = controller.forceOpeningLion(participant);
        int queriesBefore = normalTargetQueries[0];
        require(opening.actionId() == PromisedConsortActionId.LION_CLAW && opening.targetId().equals(participant), "Opening did not bind participant");
        for (int tick = 0; tick < 16; tick++) {
            var current = controller.tick().action().orElseThrow();
            require(current.targetId().equals(participant) && current.sequence() == opening.sequence(), "Opening replaced its locked participant");
            now[0]++;
        }
        require(normalTargetQueries[0] == queriesBefore, "Opening retargeted through empty ordinary visibility list");
        controller.cancel();
        require(runtime.snapshot(now[0]).isEmpty(), "Opening did not cancel");
        System.out.println("Burst persistence: legacy/partial settings, controller group boundary and wait rebase passed");
        System.out.println("Opening controller: participant retained outside ordinary target list; cancel passed");
        }

        private static void checkSkillTestCommands() throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        PlatformSkillTestCommands.register(dispatcher);
        CommandSourceStack operator = commandSource(2);
        CommandSourceStack player = commandSource(1);
        var root = dispatcher.getRoot().getChild("elderbosses");
        require(root.canUse(operator) && !root.canUse(player), "Skill tests must require permission level 2");
        require(root.getChild("test").getChild("promised_consort").getChildren().size()
                == PromisedConsortActionId.values().length, "Skill command coverage differs from action IDs");
        for (PromisedConsortActionId action : PromisedConsortActionId.values()) {
            String command = "elderbosses test promised_consort " + action.serializedName();
            for (String suffix : List.of("", " 1", " 2", " 1 ~ ~ ~", " 2 ^ ^ ^6", " 2 10 64 -20")) {
                var parsed = dispatcher.parse(command + suffix, operator);
                require(!parsed.getReader().canRead() && parsed.getExceptions().isEmpty()
                        && parsed.getContext().getCommand() != null, "Valid skill test rejected: " + command + suffix);
            }
            for (String suffix : List.of(" 0", " 3", " -1", " 1.5", " 2 ~ ~", " 2 ^ ~ ^", " 2 ~ ~ ~ extra")) {
                var parsed = dispatcher.parse(command + suffix, operator);
                require(parsed.getReader().canRead() || !parsed.getExceptions().isEmpty(),
                        "Invalid skill-test arguments accepted: " + command + suffix);
            }
            var denied = dispatcher.parse(command, player);
            require(denied.getReader().canRead(), "Non-operator can parse privileged test command");
            boolean ranged=java.util.Set.of(PromisedConsortActionId.GRAVITY_DIVE,PromisedConsortActionId.SPIRAL_ASSAULT,
                    PromisedConsortActionId.LIGHTSPEED_DASH,PromisedConsortActionId.LIGHTSPEED_SIDE_DASH).contains(action);
            for(String suffix:List.of(" ranged"," ranged 2"," ranged 2 ~ ~ ~24")) {
                var parsed=dispatcher.parse(command+suffix,operator);
                require((!parsed.getReader().canRead() && parsed.getExceptions().isEmpty())==ranged,"Wrong ranged command availability: "+action);
            }
        }
        require(dispatcher.parse("elderbosses test promised_consort unknown", operator).getReader().canRead(),
                "Unknown skill should be rejected");
        var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse(
                "elderbosses test promised_consort right_combo_", operator)).get().getList();
        require(suggestions.size() == 4, "Missing right-combo completions");
        try {
            dispatcher.execute("elderbosses test promised_consort left_combo_cross", operator);
            throw new AssertionError("Console source must not spawn an observerless test boss");
        } catch (CommandSyntaxException expected) {
            require(expected.getRawMessage() != null, "Missing player-only error");
        }
    }

    private static void checkHolyFlight(PromisedConsortActionCatalog catalog) {
        require(com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionExecutor.holyFlightStep(64, 76, movement -> false).equals(Vec3.ZERO),
            "Blocked holy flight moved through ceiling");
        require(com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionExecutor.holyFlightStep(64, 76, movement -> true).equals(new Vec3(0, 1.25, 0)),
            "Holy ascent exceeds vertical step budget");
        require(com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionExecutor.holyFlightStep(65, 64, movement -> true).equals(new Vec3(0, -1, 0)),
            "Holy descent overshoots origin");
        var action = PromisedConsortActionId.LIGHT_OF_MIQUELLA;
        var timeline = catalog.get(action).timeline();
        int total = timeline.totalTicks();
        require(com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionExecutor.holyFlightHeight(0, total, 12, 16, 20) == 0, "Holy flight starts above origin");
        require(com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionExecutor.holyFlightHeight(16, total, 12, 16, 20) == 12, "Holy flight misses configured height");
        require(com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionExecutor.holyFlightHeight(timeline.activeStartTick(0), total, 12, 16, 20) >= 10,
                "Boss returns into easy melee before primary light release");
        for (int duration : new int[]{1, 10, 30, total, 400}) for (double height : new double[]{0, 6, 12, 32}) {
            double previous = 0;
            for (int tick = 0; tick < duration; tick++) {
                double actual = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionExecutor.holyFlightHeight(tick, duration, height, 16, 20);
                require(actual >= 0 && actual <= height && Math.abs(actual - previous) <= 1.25, "Holy flight violates movement/height bound");
                previous = actual;
            }
            require(previous == 0, "Holy flight does not return to original ground plane");
        }
        System.out.println("Holy flight: 12-block rise in16ticks, elevated release, bounded curve and ground return passed");
    }

    private static void checkGroundMovement() {
        Vec3 origin = new Vec3(0, 64, 0), next = new Vec3(0, 64, 0.25);
        for (double height : new double[]{64, 64.5, 65, 63.5, 62.75}) {
            Vec3 result = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortGroundMovement.step(origin, next, height, point -> true);
            require(result != null && result.y == height && result.z == next.z, "Ground step lost slope or horizontal progress");
        }
        for (double height : new double[]{65.1, 62.7, Double.NaN, Double.POSITIVE_INFINITY}) require(
            com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortGroundMovement.step(origin, next, height, point -> true) == null,
            "Ground step accepts a wall, cliff or invalid terrain");
        require(com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortGroundMovement.step(origin, next, 65,
            point -> point.z != 0) == null, "Ground step crosses a low ceiling");
        require(com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortGroundMovement.step(origin, next, 64.5,
            point -> point.z == 0) == null, "Ground step crosses an obstructed destination");
        var body = new net.minecraft.world.phys.AABB(-0.3, 64, -0.3, 0.3, 66, 0.3);
        var floor = net.minecraft.world.phys.shapes.Shapes.box(-2, 63, -2, 2, 64, 2);
        var slab = net.minecraft.world.phys.shapes.Shapes.box(-1, 64, 0.5, 1, 64.5, 1.5);
        var shapes = List.of(floor, slab);
        double support = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortGroundMovement.supportHeight(body.move(0, 0, 0.25), 64, shapes);
        require(support == 64.5, "Wide footprint misses the slab edge before its center reaches it");
        Vec3 step = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortGroundMovement.step(origin, next, support,
            point -> shapes.stream().flatMap(shape -> shape.toAabbs().stream()).noneMatch(box -> box.intersects(body.move(point.subtract(origin)).deflate(0.001))));
        require(step != null && step.y == 64.5, "Real slab geometry blocks the supported step-up route");
        require(!Double.isFinite(com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortGroundMovement.supportHeight(body, 64, List.of())),
            "No supporting terrain is treated as walkable");
    }

    private static void checkLionPath() {
        var aimed = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortLionClawPath.create(30, 6, 6, 2);
        for (Vec3 forward : List.of(new Vec3(0, 0, 1), new Vec3(1, 0, 0), new Vec3(0, 0, -1), new Vec3(-1, 0, 0))) {
            Vec3 target = forward.scale(8), landing = aimed.destination(Vec3.ZERO, target, 12, 1.5);
            require(Math.abs(landing.subtract(target).dot(forward) - 1.5) < 0.00001, "Lion must lock behind the target along its approach");
        }
        var opening = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortLionClawPath.opening(30, 6);
        var gate = new Vec3(0, 64, -30);
        var far = opening.destination(gate, new Vec3(0, 64, 36), 64, 4);
        require(far.distanceTo(new Vec3(0, 64, 36)) < 7, "Opening rush cannot approach a far arena player");
        Vec3 previousOpening = gate;
        for (int tick = 0; tick <= 30; tick++) {
            Vec3 current = opening.at(gate, far, tick);
            require(current.distanceTo(previousOpening) <= opening.maximumStep(), "Opening rush exceeds per-tick budget");
            previousOpening = current;
        }
        for (int impact : new int[]{12, 25, 30, 48}) for (double step : new double[]{1.25, 4}) for (double height : new double[]{4, 6}) {
            var path = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortLionClawPath.create(impact, 6, height, step);
            var origin = new Vec3(10, 64, -5);
            var destination = path.destination(origin, origin.add(0, 0, 60), step == 4 ? 48 : 12, 4);
            require(path.takeoffTick() < path.lockTick() && path.lockTick() < impact, "Lion locks before takeoff or after impact");
            require(path.at(origin, destination, path.lockTick()).y > origin.y, "Lion lock is not airborne");
            require(path.at(origin, destination, 0).equals(origin), "Lion moves before takeoff");
            var previous = origin;
            for (int tick = 1; tick <= impact; tick++) {
                var current = path.at(origin, destination, tick);
                require(current.distanceTo(previous) <= step + 0.00001, "Lion trajectory exceeds movement budget");
                require(current.y >= origin.y && current.y <= origin.y + height, "Lion trajectory height invalid");
                previous = current;
            }
            require(previous.equals(destination), "Lion does not arrive at locked landing point");
            require(path.destination(origin, origin.add(0, 0, 2), 48, 1.5).z > origin.z, "Lion retains the old stop-before-target rule");
        }
        System.out.println("Lion claw path: airborne lock, bounded normal/opening movement, stop distance and exact landing passed");
    }

    private static void checkLionPlan(PromisedConsortActionCatalog catalog) {
        for (var action : List.of(PromisedConsortActionId.LION_CLAW, PromisedConsortActionId.LION_CLAW_DOUBLE)) {
            var runtime = new PromisedConsortActionRuntime(catalog);
            var snapshot = runtime.start(action, PromisedConsortPhase.PHASE_TWO, 100, 42, TARGET);
            var timeline = catalog.timeline(snapshot);
            var origin = new Vec3(0, 64, -25);
            var path = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortLionClawPath.create(timeline.activeStartTick(0), 6, 4, 1.25);
            var landing = new Vec3(0, 64, -13);
            var points = new java.util.HashMap<String, Vec3>();
            points.put("target", new Vec3(0, 64, 15));
            points.put("lion_end", landing);
            points.put("lion_lock", new Vec3(path.lockTick(), 0, 0));
            var plan = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortAttackPlan.create(snapshot, catalog.skill(snapshot), timeline,
                    origin, new com.tonywww.elder_bosses.combat.geometry.Vec2(0, 1), points, 6);
            require(plan.size() == 1, "Preparation ground cut added an unapproved damage event");
            var strike = plan.get(0);
            var circle = (com.tonywww.elder_bosses.combat.geometry.Circle) strike.shape();
            require(circle.center().x() == landing.x && circle.center().z() == landing.z && strike.baseY() == landing.y, "Telegraph and landing center differ");
            require(strike.lockTick() == 100 + path.lockTick() && strike.activeTick() == 100 + path.impactTick(), "Lion telegraph lock/impact clock differs");
            points.put("lion_end", new Vec3(15, 64, 15));
            var candidate = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortAttackPlan.create(snapshot, catalog.skill(snapshot), timeline,
                    origin, new com.tonywww.elder_bosses.combat.geometry.Vec2(1, 0), points, 6).get(0);
            require(candidate.trackFrom(strike, strike.lockTick() + 1).equals(strike), "Lion landing telegraph retargets after airborne lock");
        }
        System.out.println("Lion telegraph: shared landing/airborne lock, frozen after lock and one original damage event passed");
    }

    private static void checkMeteorSequence(PromisedConsortActionCatalog catalog) {
        var waiting = com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortGravityRockEntity.HeldPhase.WAIT;
        var launch = com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortGravityRockEntity.HeldPhase.LAUNCH;
        var cancel = com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortGravityRockEntity.HeldPhase.CANCEL;
        for (long tick : new long[]{0, 50, 89, 90, 91, 120}) {
            require(com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortGravityRockEntity.heldPhase(tick, 90, true, 4, 4)
                == (tick < 90 ? waiting : launch), "Held rock launches before its own event");
            require(com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortGravityRockEntity.heldPhase(tick, 90, false, 4, 4) == cancel
                && com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortGravityRockEntity.heldPhase(tick, 90, true, 5, 4) == cancel,
                "Cancelled or replaced cast releases old held rocks");
        }
        var timeline = catalog.get(PromisedConsortActionId.GRAVITY_METEOR).timeline();
        for (int recovery : new int[]{0, 1}) {
            var stages = new java.util.ArrayList<>(timeline.stages());
            var last = stages.get(stages.size() - 1);
            stages.set(stages.size() - 1, new com.tonywww.elder_bosses.combat.action.ActionStage(last.windupTicks(), last.activeTicks(), recovery));
            var shortTimeline = com.tonywww.elder_bosses.combat.action.ActionTimeline.ofStages(
                stages.toArray(com.tonywww.elder_bosses.combat.action.ActionStage[]::new));
            require(!com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortMeteorSequence.from(shortTimeline).usable(),
                "Meteor without landing recovery must use legacy sequence");
        }
        var sequence = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortMeteorSequence.from(timeline);
        require(sequence.usable(), "Default meteor sequence is not ordered");
        require(timeline.activeStartTick(0) == 50 && sequence.groundTick() == 12 && sequence.riseTick() == 15
            && sequence.crestTick() == 31, "Meteor must cut, pause briefly, rise, then aim before launching");
        require(com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortAnimationTimeline.sample(sequence.riseTick(),
            PromisedConsortActionId.GRAVITY_METEOR, timeline, com.tonywww.elder_bosses.combat.action.SkillTuning.NEUTRAL) == 28,
            "Meteor starts flight before or after its authored upstroke");
        require(com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortAnimationTimeline.sample(sequence.crestTick(),
            PromisedConsortActionId.GRAVITY_METEOR, timeline, com.tonywww.elder_bosses.combat.action.SkillTuning.NEUTRAL) == 45,
            "Meteor crest does not match the raised blade pose");
        require(sequence.groundTick() < sequence.riseTick() && sequence.crestTick() < timeline.activeStartTick(0), "Rocks fire before ground cut/ascent");
        var origin = new Vec3(0, 64, 0);
        var landing = sequence.landing(origin, new Vec3(0, 64, 30), 4);
        for (Vec3 forward : List.of(new Vec3(0, 0, 1), new Vec3(1, 0, 0), new Vec3(0, 0, -1), new Vec3(-1, 0, 0))) {
            Vec3 crest = sequence.crest(origin, forward);
            Vec3 airborne = sequence.at(origin, crest, origin, (sequence.riseTick() + sequence.crestTick()) * 0.5);
            require(airborne.subtract(origin).dot(forward) > 1 && airborne.y > origin.y + 1, "Meteor ascent is vertical instead of forward/up");
            require(sequence.at(origin, crest, origin, sequence.crestTick()).equals(crest), "Meteor does not reach forward crest");
            Vec3 prior = origin;
            for (int tick = 0; tick <= sequence.landingTick(); tick++) {
                Vec3 point = sequence.at(origin, crest, origin, tick);
                require(point.distanceTo(prior) <= 1.25, "Diagonal meteor path exceeds its movement budget");
                prior = point;
            }
            require(prior.equals(origin), "Phase-one meteor does not complete its landing");
        }
        var previous = origin;
        for (int tick = 0; tick < timeline.totalTicks(); tick++) {
            var point = sequence.at(origin, landing, tick);
            require(point.distanceTo(previous) <= 1.25, "Meteor flight exceeds step budget");
            previous = point;
        }
        require(previous.equals(landing), "Meteor body remains airborne after final contact");
        require(sequence.at(origin, landing, timeline.activeStartTick(0)).y >= 70, "Meteor volley lacks airborne boss");
        for (var phase : PromisedConsortPhase.values()) {
            var runtime = new PromisedConsortActionRuntime(catalog);
            var action = runtime.start(PromisedConsortActionId.GRAVITY_METEOR, phase, 100, 42, TARGET);
            var plan = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortAttackPlan.create(action, catalog.skill(action), timeline,
                origin, new com.tonywww.elder_bosses.combat.geometry.Vec2(0, 1), java.util.Map.of("meteor_cast_origin", origin, "meteor_cast_end", landing), 6);
            require(plan.stream().filter(strike -> strike.id().equals("meteor_ground")).count() == 1, "Meteor must have exactly one ground slash");
            require(plan.stream().filter(strike -> strike.id().equals("meteor_body")).count() == (phase == PromisedConsortPhase.PHASE_TWO ? 1 : 0), "Meteor final body slash phase/count differs");
            require(plan.stream().filter(strike -> strike.id().startsWith("rock_flight_")).count() == 8, "Meteor rewrite changed rock count");
                var skill = catalog.skill(action);
                require(skill.damage("sword_damage").equals(new com.tonywww.elder_bosses.combat.damage.DamageFormula(4, 0.8))
                    && skill.damage("body_damage").equals(new com.tonywww.elder_bosses.combat.damage.DamageFormula(5, 0.8)), "Approved new meteor damage defaults differ");
                require(skill.damage("damage").equals(new com.tonywww.elder_bosses.combat.damage.DamageFormula(2, 0.35))
                    && skill.integer("max_hits_per_target") == 3, "Old rock damage or cap changed");
                var ground = plan.stream().filter(strike -> strike.id().equals("meteor_ground")).findFirst().orElseThrow();
                require(ground.shape().contains(4, 0) && !ground.shape().contains(0, 4), "Meteor opening hit is not directed toward the authored side after entity yaw conversion");
                var generator = new com.tonywww.elder_bosses.boss.promisedconsort.indicator.PromisedConsortIndicatorGenerator(catalog,
                    ElderBossesCommonConfig.VALUES.promisedConsortCombatSnapshot());
                var warning = generator.createAuthoritative(1, action, plan, List.of(), ground.lockTick());
                require(warning.stream().anyMatch(packet -> packet.indicatorId().endsWith(":meteor_ground")), "Meteor ground cut has no warning");
                for (var strike : plan) {
                    var packets = generator.createAuthoritative(1, action, plan, List.of(), strike.lockTick());
                    require(packets.stream().noneMatch(packet -> packet.indicatorId().contains(":rock_flight") || packet.indicatorId().contains(":clone_meteor_")),
                        "Meteor airborne range indicator was not removed");
                }
                require(ground.activeTick() == 100 + sequence.groundTick() && ground.activeTick() < 100 + sequence.riseTick(), "Ground slash occurs after takeoff");
                if (phase == PromisedConsortPhase.PHASE_TWO) {
                var body = plan.stream().filter(strike -> strike.id().equals("meteor_body")).findFirst().orElseThrow();
                require(body.activeTick() == 100 + sequence.landingTick() && body.lockTick() == 100 + sequence.landingLockTick(), "Body landing lock clock differs");
                var bounds = (com.tonywww.elder_bosses.combat.geometry.Circle) body.shape();
                require(bounds.center().x() == landing.x && bounds.center().z() == landing.z && body.baseY() == landing.y, "Body landing warning is not on actual ground destination");
                }
        }
        System.out.println("Meteor sequence: ground cut, ascent, aerial volley and bounded final landing passed");
    }

    private static void checkGroundDebris() {
        var contacts = new java.util.HashSet<String>();
        require(!com.tonywww.elder_bosses.client.vfx.ClientConsortBladeTrails.claimGroundContact(contacts, "left:1", false, false, false), "Wall contact emitted ground chips");
        require(!com.tonywww.elder_bosses.client.vfx.ClientConsortBladeTrails.claimGroundContact(contacts, "left:1", true, true, false), "Air emitted ground chips");
        require(!com.tonywww.elder_bosses.client.vfx.ClientConsortBladeTrails.claimGroundContact(contacts, "left:1", true, false, true), "Fluid emitted stone chips");
        require(com.tonywww.elder_bosses.client.vfx.ClientConsortBladeTrails.claimGroundContact(contacts, "left:1", true, false, false), "Real ground contact lost");
        require(!com.tonywww.elder_bosses.client.vfx.ClientConsortBladeTrails.claimGroundContact(contacts, "left:1", true, false, false), "High FPS repeated a ground contact");
        require(com.tonywww.elder_bosses.client.vfx.ClientConsortBladeTrails.claimGroundContact(contacts, "right:1", true, false, false)
            && com.tonywww.elder_bosses.client.vfx.ClientConsortBladeTrails.claimGroundContact(contacts, "left:2", true, false, false), "Second blade/next swing lost debris");
        int[] rays = {0};
        java.util.function.BiFunction<Vec3, Vec3, net.minecraft.world.phys.BlockHitResult> clip = (start, end) -> {
            rays[0]++;
            return net.minecraft.world.phys.shapes.Shapes.block().clip(start, end, net.minecraft.core.BlockPos.ZERO);
        };
        var oldRoot = new Vec3(-1.5, 1.5, -0.25);
        var oldTip = new Vec3(2.5, 1.5, -0.25);
        var root = new Vec3(-1.5, 0.5, 1.75);
        var tip = new Vec3(2.5, 0.5, 1.75);
        require(clip.apply(root, tip) == null && clip.apply(oldTip, tip) == null, "Fixture must reproduce old blade-middle omission");
        rays[0] = 0;
        var hit = ClientConsortBladeTrails.groundContact(root, tip, oldRoot, oldTip, 0.5, true, clip);
        require(hit != null && hit.getDirection() == net.minecraft.core.Direction.UP && !hit.isInside()
            && Math.abs(hit.getLocation().y - 1) < 1.0e-8, "Swept blade middle missed actual block top");
        require(rays[0] > 2 && rays[0] <= 34, "Swept blade ray budget is not bounded");
        require(ClientConsortBladeTrails.groundContact(tip, root, oldTip, oldRoot, 0.5, true, clip) != null, "Reversed blade lost swept contact");
        for (double gap : new double[]{0, -0.1, 1.001, Double.NaN, Double.POSITIVE_INFINITY}) {
            rays[0] = 0;
            require(ClientConsortBladeTrails.groundContact(root, tip, oldRoot, oldTip, gap, true, clip) == null, "Stale/reversed/nonfinite frame emitted debris");
            require(rays[0] == 1, "Invalid sample gap still swept the blade");
        }
        require(ClientConsortBladeTrails.groundContact(root, tip, oldRoot, oldTip, 0.5, false, clip) == null, "Different swing bridged inactive time");
        require(ClientConsortBladeTrails.groundContact(root, tip, oldRoot.add(10, 0, 0), oldTip, 0.5, true, clip) == null, "Root teleport generated a swept contact");
        require(ClientConsortBladeTrails.groundContact(root, tip, oldRoot, oldTip.add(10, 0, 0), 0.5, true, clip) == null, "Tip teleport generated a swept contact");
        require(ClientConsortBladeTrails.groundContact(root, tip, null, null, 0.5, true, clip) == null, "Missing previous markers generated debris");
        rays[0] = 0;
        require(ClientConsortBladeTrails.groundContact(new Vec3(Double.NaN, 1, 1), tip, oldRoot, oldTip, 0.5, true, clip) == null
            && rays[0] == 0, "Nonfinite blade reached world raycast");
        var directRoot = new Vec3(0.5, 2, 0.5);
        var directTip = new Vec3(0.5, 0.5, 0.5);
        require(ClientConsortBladeTrails.groundContact(directRoot, directTip, null, null, 0, false, clip) != null, "Current blade contact requires a previous frame");
        require(ClientConsortBladeTrails.groundContact(new Vec3(-1, 0.5, 0.5), directTip, null, null, 0, false, clip) == null, "Wall face treated as ground");
        require(ClientConsortBladeTrails.groundContact(new Vec3(0.5, -1, 0.5), directRoot, null, null, 0, false, clip) == null, "Bottom face treated as ground");
        require(ClientConsortBladeTrails.groundContact(directTip, directRoot, null, null, 0, false, clip) == null, "Inside block contact treated as a top impact");
        require(ClientConsortBladeTrails.groundContact(root.add(0, 3, 0), tip.add(0, 3, 0), oldRoot.add(0, 3, 0), oldTip.add(0, 3, 0), 0.5, true, clip) == null,
            "Airborne blade emitted ground debris");
        var slab = net.minecraft.world.phys.shapes.Shapes.box(0, 0, 0, 1, 0.5, 1);
        var slabHit = ClientConsortBladeTrails.groundContact(root.add(0, -0.5, 0), tip.add(0, -0.5, 0), oldRoot.add(0, -0.5, 0), oldTip.add(0, -0.5, 0), 0.5, true,
            (start, end) -> slab.clip(start, end, net.minecraft.core.BlockPos.ZERO));
        require(slabHit != null && Math.abs(slabHit.getLocation().y - 0.5) < 1.0e-8, "Slab contact snapped to full-block height");
        rays[0] = 0;
        ClientConsortBladeTrails.groundContact(root.scale(10), tip.scale(10), oldRoot.scale(10).add(0, 0, 20), oldTip.scale(10).add(0, 0, 20), 0.5, true, clip);
        require(rays[0] == 1, "Oversized frame travel reached sweep sampling");
        rays[0] = 0;
        var longRoot = new Vec3(-20, 3, -0.25);
        var longTip = new Vec3(20, 3, -0.25);
        ClientConsortBladeTrails.groundContact(longRoot.add(0, 0, 1), longTip.add(0, 0, 1), longRoot, longTip, 0.5, true, clip);
        require(rays[0] == 34, "Long blade escaped the 34-ray cap");
    }

    private static void checkBladeEnchantment() {
        checkGroundDebris();
        int stompFragments = 0;
        for (int elapsed = -1; elapsed <= 24; elapsed++) {
            int count = com.tonywww.elder_bosses.client.vfx.ClientConsortEnergyRenderer.impactParticleCount(true, elapsed, 18);
            require(count >= 0 && count <= 18 && ((elapsed >= 0 && elapsed < 8) == (count > 0)), "Stomp material particles escape their budget or time window");
            stompFragments += count;
            require(com.tonywww.elder_bosses.client.vfx.ClientConsortEnergyRenderer.impactParticleCount(false, elapsed, 0) == 0, "Flame ignores zero particle budget");
        }
        require(stompFragments == 144, "Stomp lacks a sustained material-fragment burst");
        var landing = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionSoundPlan.meteorLanding();
        require(landing.size() == 2 && landing.get(0).sound() == com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionSoundPlan.Sound.STOMP
            && landing.get(1).sound() == com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionSoundPlan.Sound.METEOR,
            "Meteor landing must contain distinct fracture and explosion layers");
        var played = new java.util.HashSet<String>();
        var emitted = new java.util.ArrayList<com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionSoundPlan.Cue>();
        for (int repeat = 0; repeat < 3; repeat++) com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionSoundPlan.dispatch(
            landing, played, "land:", 20, 20, 21, emitted::add);
        require(emitted.size() == 2, "Repeated landing dispatch multiplies sound layers");
        var gravity = com.tonywww.elder_bosses.client.vfx.ClientConsortBladeTrails.Enchantment.GRAVITY;
        var holy = com.tonywww.elder_bosses.client.vfx.ClientConsortBladeTrails.Enchantment.HOLY;
        var none = com.tonywww.elder_bosses.client.vfx.ClientConsortBladeTrails.Enchantment.NONE;
        require(com.tonywww.elder_bosses.client.vfx.ClientConsortBladeTrails.enchantment(null, false, true) == gravity, "Intro lacks gravity blades");
        require(com.tonywww.elder_bosses.client.vfx.ClientConsortBladeTrails.enchantment(PromisedConsortActionId.GRAVITY_METEOR, true, false) == gravity, "Gravity blade replaced by generic phase-two gold");
        require(com.tonywww.elder_bosses.client.vfx.ClientConsortBladeTrails.enchantment(PromisedConsortActionId.LIGHTSPEED_DASH, true, false) == holy, "Holy dash lacks blade light");
        require(com.tonywww.elder_bosses.client.vfx.ClientConsortBladeTrails.enchantment(null, true, false) == none, "Idle entity retains enchantment");
        var root = Vec3.ZERO;
        for (Vec3 tip : List.of(new Vec3(0, 4, 0), new Vec3(0, 0, 4), new Vec3(2, 3, 4))) {
            var first = com.tonywww.elder_bosses.client.vfx.ClientConsortBladeTrails.lightningPoints(root, tip, 10, -1);
            var second = com.tonywww.elder_bosses.client.vfx.ClientConsortBladeTrails.lightningPoints(root, tip, 12, -1);
            require(first.size() == 13 && first.get(0).equals(root) && first.get(12).distanceTo(tip) < 0.00001, "Lightning detaches from blade endpoints");
            require(!first.equals(second), "Gravity lightning is static");
            for (int index = 0; index < first.size(); index++) require(first.get(index).distanceTo(root.lerp(tip, index / 12.0)) < 0.21, "Lightning escapes blade envelope");
        }
        int[] vertices = {0};
        var type = com.mojang.blaze3d.vertex.VertexConsumer.class;
        var consumer = (com.mojang.blaze3d.vertex.VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("vertex") && arguments.length == 4) {
                        vertices[0]++;
                        for (int axis = 1; axis <= 3; axis++) require(Double.isFinite(((Number) arguments[axis]).doubleValue()), "Nonfinite attached effect vertex");
                    }
                    return method.getReturnType() == type ? proxy : null;
                });
        for (var effect : List.of(gravity, holy, none)) {
            vertices[0] = 0;
            com.tonywww.elder_bosses.client.vfx.ClientConsortBladeTrails.enchantedBlade(consumer, new com.mojang.blaze3d.vertex.PoseStack().last(),
                    root, new Vec3(0, 4, 0), new Vec3(0, 8, 0), effect, 12, 1);
            require((vertices[0] > 0) == (effect != none), "Blade enchantment geometry not emitted/disabled");
        }
        for (boolean held : new boolean[]{false, true}) {
            vertices[0] = 0;
            com.tonywww.elder_bosses.client.vfx.ClientConsortEnergyRenderer.rockAura(consumer, new com.mojang.blaze3d.vertex.PoseStack().last(),
                    root, new Vec3(3, 3, 3), 20, held);
            require(vertices[0] > 0 && vertices[0] < 1200, "Rock aura mesh is missing or unbounded");
        }
            vertices[0] = 0;
            com.tonywww.elder_bosses.client.vfx.ClientConsortGravityDistortion.billboard(consumer,
                new com.mojang.blaze3d.vertex.PoseStack().last(), 1.6F);
            require(vertices[0] == 4, "Gravity distortion billboard must have exactly four finite vertices");
        System.out.println("Blade enchantments: gravity/holy gates, moving attached arcs and finite real geometry passed");
    }

    private static void syncFlightConfigs(boolean write) throws Exception {
        syncConfigFields(write, "promised_consort.skills.light_of_miquella",
                List.of("flight_height", "flight_ascent_ticks", "flight_descent_ticks"), ".pre-holy-flight-v1");
    }

    private static void syncMeteorRhythm() throws Exception {
        String key = "promised_consort.skills.gravity_meteor.windup_ticks";
        for (String file : List.of("docs/config/elder-bosses-common.example.toml", "run/config/elder_bosses-common.toml",
                "versions/1.21.1-neoforge/run/config/elder_bosses-common.toml")) {
            Path path = Path.of(file);
            String text = Files.readString(path);
            var before = new TomlParser().parse(text);
            List<Integer> ticks = before.get(key);
            if (ticks == null || ticks.isEmpty() || ticks.get(0) != 86) {
                System.out.println("Preserved current/custom meteor windup: " + file);
                continue;
            }
            var updated = new ArrayList<>(ticks);
            updated.set(0, 50);
            var matcher = java.util.regex.Pattern.compile("(?ms)(^\\s*\\[promised_consort\\.skills\\.gravity_meteor\\]\\s*\\R(?:(?!^\\s*\\[).)*?^\\s*windup_ticks\\s*=\\s*\\[\\s*)86(?=\\s*,)").matcher(text);
            require(matcher.find(), "Cannot locate the exact meteor windup assignment: " + file);
            String replacement = text.substring(0, matcher.end() - 2) + "50" + text.substring(matcher.end());
            var parsed = new TomlParser().parse(replacement);
            require(parsed.get(key).equals(updated), "Meteor timing replacement differs");
            parsed.set(key, ticks);
            require(parsed.equals(before), "Meteor migration changes an unrelated config value");
            require(Files.readString(path).equals(text), "Config changed while migrating");
            Files.writeString(path, replacement);
            System.out.println("Meteor windup86->50, other config values retained: " + file);
        }
        Path fixture = Path.of("models/promised_consort/tests/fixtures/action_timings.json");
        var document = com.google.gson.JsonParser.parseString(Files.readString(fixture)).getAsJsonObject();
        var profile = document.getAsJsonObject("gravity_meteor");
        var first = profile.getAsJsonArray("stage_ticks").get(0).getAsJsonArray();
        if (first.get(0).getAsInt() == 86) {
            first.set(0, new com.google.gson.JsonPrimitive(50));
            profile.addProperty("duration_ticks", profile.get("duration_ticks").getAsInt() - 36);
            for (var value : profile.getAsJsonArray("knots")) {
                var pair = value.getAsJsonArray();
                int tick = pair.get(1).getAsInt();
                if (tick >= 86) pair.set(1, new com.google.gson.JsonPrimitive(tick - 36));
            }
            Files.writeString(fixture, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(document) + "\n");
        }
    }

    private static void syncMeteorConfigs(boolean write) throws Exception {
        syncConfigFields(write, "promised_consort.skills.gravity_meteor",
                List.of("sword_range", "body_range", "sword_damage.flat", "sword_damage.attack_ratio", "body_damage.flat", "body_damage.attack_ratio"),
                ".pre-reference-meteor-v1");
    }

    private static void syncBurstConfigs(boolean write) throws Exception {
        syncConfigFields(write, "promised_consort.selector.burst",
                List.of("minimum_skills", "maximum_skills", "minimum_link_ticks", "maximum_link_ticks", "minimum_rest_ticks", "maximum_rest_ticks", "chain_recovery_ticks"),
                ".pre-reference-burst-v1");
    }

    private static void syncConfigFields(boolean write, String prefix, List<String> fields, String backupSuffix) throws Exception {
        var defaults = CommentedConfig.inMemory();
        ElderBossesCommonConfig.SPEC.correct(defaults);
        var parser = new TomlParser();
        for (String file : List.of("docs/config/elder-bosses-common.example.toml", "run/config/elder_bosses-common.toml",
                "versions/1.21.1-neoforge/run/config/elder_bosses-common.toml")) {
            Path target = Path.of(file);
            String original = Files.readString(target);
            var before = parser.parse(original);
            var updated = parser.parse(original);
            var added = new ArrayList<String>();
            boolean sectionExists = before.contains(prefix);
            for (String field : fields) {
                String key = prefix + "." + field;
                if (!updated.contains(key)) { updated.set(key, defaults.get(key)); added.add(key); }
            }
            if (!write) require(added.isEmpty(), "Burst configuration is not current: " + file);
            if (added.isEmpty()) continue;
            String output = new com.electronwill.nightconfig.toml.TomlWriter().writeToString(updated);
            var parsed = parser.parse(output);
            require(parsed.equals(updated), "Burst TOML round trip differs");
            if (sectionExists) {
                added.forEach(parsed::remove);
                for (String field : fields) if (field.contains(".")) {
                    String parent = prefix + "." + field.substring(0, field.lastIndexOf('.'));
                    if (!before.contains(parent)) parsed.remove(parent);
                }
            }
            else parsed.remove(prefix);
            require(parsed.equals(before), "Burst migration changed existing/custom configuration");
            Path backup = Path.of(file + backupSuffix);
            require(!Files.exists(backup), "Preserve prior burst backup");
            require(Files.readString(target).equals(original), "Config changed during migration");
            Files.copy(target, backup);
            Files.writeString(target, output);
            System.out.println(prefix + ": " + added.size() + " missing fields added, other values retained: " + file);
        }
        if (!write) System.out.println("All three configs are current for " + prefix + "; no repeated migration required");
    }

    private static CommandSourceStack commandSource(int permission) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, permission,
                "skill-test", Component.literal("skill-test"), null, null);
    }

    private static void checkMalenia(MaleniaActionCatalog catalog) {
        for (MaleniaActionId action : MaleniaActionId.values()) {
            List<ActionLifecycleEvent> events = new ArrayList<>();
            MaleniaActionRuntime runtime = new MaleniaActionRuntime(catalog);
            MaleniaActionRuntime baseline = new MaleniaActionRuntime(catalog);
            runtime.setLifecycleListener(events::add);
            int duration = catalog.get(action).timeline().totalTicks();
            require(runtime.start(action, MaleniaPhase.PHASE_TWO, 100, 42, TARGET)
                    .equals(baseline.start(action, MaleniaPhase.PHASE_TWO, 100, 42, TARGET)), "Malenia start changed");
            checkEvent(events.get(0), action.serializedName(), ActionLifecycleEvent.Outcome.STARTED, 0, 100, duration, TARGET);
            runtime.retarget(100, RETARGET);
            baseline.retarget(100, RETARGET);
            for (int tick = 0; tick < duration; tick++) {
                require(runtime.snapshot(100 + tick).equals(baseline.snapshot(100 + tick)), "Malenia state changed by observer");
                runtime.snapshot(100 + tick);
                require(events.size() == 1, "Malenia broadcasts on every snapshot");
            }
            require(runtime.advance(100 + duration).equals(baseline.advance(100 + duration)), "Malenia end changed");
            require(events.size() == 2, "Malenia completion missing");
            checkEvent(events.get(1), action.serializedName(), ActionLifecycleEvent.Outcome.COMPLETED, duration, 100, duration, RETARGET);
            runtime.cancel(101 + duration);
            runtime.snapshot(101 + duration);
            require(events.size() == 2, "Malenia repeated completed event");
            runtime.start(action, MaleniaPhase.PHASE_TWO, 200 + duration, 43, null);
            runtime.cancel(201 + duration);
            runtime.cancel(201 + duration);
            require(events.size() == 4, "Malenia cancel must be emitted once");
            checkEvent(events.get(3), action.serializedName(), ActionLifecycleEvent.Outcome.CANCELLED, 1, 200 + duration, duration, null);
        }
        List<ActionLifecycleEvent> events = new ArrayList<>();
        MaleniaActionRuntime runtime = new MaleniaActionRuntime(catalog);
        runtime.setLifecycleListener(events::add);
        runtime.start(MaleniaActionId.SINGLE_SLASH, MaleniaPhase.PHASE_ONE, 500, 7, TARGET);
        runtime.start(MaleniaActionId.DOUBLE_SLASH, MaleniaPhase.PHASE_ONE, 501, 8, RETARGET);
        require(events.size() == 3 && events.get(1).outcome() == ActionLifecycleEvent.Outcome.REPLACED
                && events.get(2).outcome() == ActionLifecycleEvent.Outcome.STARTED, "Replacement event order");
        require(events.get(0).sequence() == events.get(1).sequence()
                && events.get(2).sequence() != events.get(1).sequence(), "Replacement sequence identity");
        runtime.prepareForPersistence(502);
        require(events.size() == 4 && events.get(3).outcome() == ActionLifecycleEvent.Outcome.CANCELLED, "Persistence cancellation not reported");
    }

    private static void checkConsort(PromisedConsortActionCatalog catalog) {
        for (PromisedConsortActionId action : PromisedConsortActionId.values()) {
            List<ActionLifecycleEvent> events = new ArrayList<>();
            PromisedConsortActionRuntime runtime = new PromisedConsortActionRuntime(catalog);
            PromisedConsortActionRuntime baseline = new PromisedConsortActionRuntime(catalog);
            runtime.setLifecycleListener(events::add);
            int duration = catalog.get(action).timeline().totalTicks();
            require(runtime.start(action, PromisedConsortPhase.PHASE_TWO, 100, 42, TARGET)
                    .equals(baseline.start(action, PromisedConsortPhase.PHASE_TWO, 100, 42, TARGET)), "Consort start changed");
            checkEvent(events.get(0), action.serializedName(), ActionLifecycleEvent.Outcome.STARTED, 0, 100, duration, TARGET);
            runtime.retarget(RETARGET);
            baseline.retarget(RETARGET);
            for (int tick = 0; tick < duration; tick++) {
                require(runtime.snapshot(100 + tick).equals(baseline.snapshot(100 + tick)), "Consort state changed by observer");
                runtime.advance(100 + tick);
                require(events.size() == 1, "Consort broadcasts on every advance");
            }
            require(runtime.advance(100 + duration).equals(baseline.advance(100 + duration)), "Consort end changed");
            require(events.size() == 2, "Consort completion missing");
                checkEvent(events.get(1), action.serializedName(), ActionLifecycleEvent.Outcome.COMPLETED, duration, 100, duration,
                    action.rangedDefense()?TARGET:RETARGET);
            runtime.cancel(101 + duration);
            runtime.advance(101 + duration);
            require(events.size() == 2, "Consort repeated completed event");
            runtime.start(action, PromisedConsortPhase.PHASE_TWO, 200 + duration, 43, null);
            runtime.cancel(201 + duration);
            runtime.cancel(201 + duration);
            require(events.size() == 4, "Consort cancellation must be emitted once");
            checkEvent(events.get(3), action.serializedName(), ActionLifecycleEvent.Outcome.CANCELLED, 1, 200 + duration, duration, null);
        }
        PromisedConsortActionRuntime runtime = new PromisedConsortActionRuntime(catalog);
        runtime.start(PromisedConsortActionId.CONSORT_METEOR, PromisedConsortPhase.PHASE_TWO, 900, 13, TARGET);
        var saved = runtime.persistentState();
        PromisedConsortActionRuntime restored = PromisedConsortActionRuntime.restore(catalog, saved);
        List<ActionLifecycleEvent> events = new ArrayList<>();
        restored.setLifecycleListener(events::add);
        require(restored.snapshot(910).equals(runtime.snapshot(910)), "Observer breaks restored action");
        require(events.isEmpty(), "Restored action falsely announced as a new cast");
        restored.advance(900 + catalog.get(PromisedConsortActionId.CONSORT_METEOR).timeline().totalTicks());
        require(events.size() == 1 && events.get(0).sequence() == saved.activeSequence()
                && events.get(0).seed() == 13 && events.get(0).outcome() == ActionLifecycleEvent.Outcome.COMPLETED, "Restored meteor end missing");
    }

    private static void checkEvent(ActionLifecycleEvent event, String action, ActionLifecycleEvent.Outcome outcome,
                                   long elapsed, long start, int duration, UUID target) {
        require(event.actionId().equals(action) && event.outcome() == outcome, "Wrong action or outcome");
        require(event.startGameTick() == start && event.elapsedTicks() == elapsed && event.gameTick() == start + elapsed, "Wrong diagnostic event time");
        require(event.timeline().totalTicks() == duration, "Wrong scaled timeline");
        require(java.util.Objects.equals(event.targetId(), target), "Wrong diagnostic target");
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}