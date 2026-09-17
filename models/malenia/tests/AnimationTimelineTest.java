import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.boss.malenia.sync.MaleniaAnimationTimeline;
import com.tonywww.elder_bosses.client.render.MaleniaPoseTransition;
import com.tonywww.elder_bosses.client.render.MaleniaAnimationClock;
import com.tonywww.elder_bosses.combat.action.ActionStage;
import com.tonywww.elder_bosses.combat.action.ActionTimeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AnimationTimelineTest {
    public static void main(String[] arguments) throws Exception {
        int assertions = checkComponentConfiguration();
        for (MaleniaActionId action : MaleniaActionId.values()) {
            int[] durations = MaleniaAnimationTimeline.durations(action);
            List<ActionStage> stages = new ArrayList<>();
            List<ActionStage> slower = new ArrayList<>();
            for (int index = 0; index < durations.length; index += 3) {
                stages.add(new ActionStage(durations[index], durations[index + 1], durations[index + 2]));
                slower.add(new ActionStage(durations[index] * 2, durations[index + 1] * 2, durations[index + 2] * 2));
            }
            ActionTimeline normal = ActionTimeline.ofStages(stages.toArray(ActionStage[]::new));
            ActionTimeline stretched = ActionTimeline.ofStages(slower.toArray(ActionStage[]::new));
            for (double tick = 0; tick <= normal.totalTicks(); tick += 0.5) {
                equal(tick, MaleniaAnimationTimeline.sample(tick, action, normal, Map.of()));
                equal(tick, MaleniaAnimationTimeline.sample(tick * 2, action, stretched, Map.of()));
                assertions += 2;
            }
        }
        ActionTimeline waterfowl = ActionTimeline.ofStages(new ActionStage(40, 84, 42));
        Map<Integer, Integer> bursts = Map.of(30, 22, 56, 46, 60, 50, 76, 62, 80, 66, 96, 78, 100, 82);
        for (Map.Entry<Integer, Integer> entry : bursts.entrySet()) {
            equal(entry.getValue(), MaleniaAnimationTimeline.sample(entry.getKey(), MaleniaActionId.WATERFOWL_DANCE, waterfowl, bursts));
            assertions++;
        }
        equal(0, MaleniaAnimationTimeline.sample(-1, MaleniaActionId.WATERFOWL_DANCE, waterfowl, bursts));
        equal(142, MaleniaAnimationTimeline.sample(1000, MaleniaActionId.WATERFOWL_DANCE, waterfowl, bursts));
        equal(0, MaleniaPoseTransition.weight(-1, 2));
        equal(0.5, MaleniaPoseTransition.weight(1, 2));
        equal(1, MaleniaPoseTransition.weight(2, 2));
        equal(1, MaleniaPoseTransition.weight(1, 0));
        equal(Math.toRadians(-5), MaleniaPoseTransition.rotation((float) Math.toRadians(1435), (float) Math.toRadians(-5), 0.5F));
        equal(Math.toRadians(-180), MaleniaPoseTransition.rotation((float) Math.toRadians(179), (float) Math.toRadians(-179), 0.5F));
        equal(8, MaleniaPoseTransition.position(2, 8, 1));
        equal(5, MaleniaPoseTransition.position(2, 8, 0.5F));
        MaleniaAnimationClock clock = new MaleniaAnimationClock();
        equal(10, clock.sample("single_slash", 5, 10, 11, 20.1));
        equal(10.8, clock.sample("single_slash", 5, 10, 11, 20.9));
        equal(10.95, clock.sample("single_slash", 5, 10, 11, 21.05));
        equal(11, clock.sample("single_slash", 5, 10, 11, 24.0));
        equal(11, clock.sample("single_slash", 5, 11, 12, 24.1));
        equal(11.5, clock.sample("single_slash", 5, 11, 12, 24.6));
        equal(0, clock.sample("single_slash", 6, 0, 1, 25));
        equal(0, clock.sample("stunned", 6, 0, 1, 26));
        equal(0, clock.sample("stunned", 6, 0, 1, 26));
        check("idle_phase_one", clock.locomotion("idle_phase_one", 0));
        check("idle_phase_one", clock.locomotion("walk", 1));
        check("idle_phase_one", clock.locomotion("strafe_left", 2));
        check("idle_phase_one", clock.locomotion("walk", 3));
        check("idle_phase_one", clock.locomotion("walk", 6));
        check("walk", clock.locomotion("walk", 7));
        check("walk", clock.locomotion("strafe_left", 8));
        check("strafe_left", clock.locomotion("strafe_left", 12));
        clock.advanceGait("walk", 0, 0, 0);
        equal(0, clock.gaitTime("walk"));
        clock.advanceGait("walk", 1, 1, 0);
        equal(16, clock.gaitTime("walk"));
        clock.advanceGait("walk_back", 2, 1, 0);
        equal(18, clock.gaitTime("walk_back"));
        clock.advanceGait("walk_back", 2, 2, 0);
        equal(18, clock.gaitTime("walk_back"));
        clock.advanceGait("walk_back", 10, 3, 0);
        equal(18, clock.gaitTime("walk_back"));
        clock.advanceGait("walk_back", 11, 20, 0);
        equal(18, clock.gaitTime("walk_back"));
        clock.advanceGait("single_slash", 12, 21, 0);
        equal(-1, clock.gaitTime("single_slash"));
        equal(16, clock.gaitTime("walk"));
        clock.advanceGait("walk", 13, 21, 0);
        equal(16, clock.gaitTime("walk"));
        clock.advanceGait("walk", 14, 21.25, 0);
        equal(20, clock.gaitTime("walk"));
        clock.advanceGait("run", 15, 21.25, 0);
        equal(12.5, clock.gaitTime("run"));
        clock.advanceGait("run", 16, 21.385, 0);
        equal(13.5, clock.gaitTime("run"));
        System.out.println("Animation timeline, transition, network-clock and gait assertions passed: " + (assertions + 39));
    }

    private static int checkComponentConfiguration() throws Exception {
        var spec = com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig.SPEC;
        var config = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        spec.correct(config);
        spec.setConfig(config);
        var defaults = com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig.VALUES.maleniaSkillSnapshot();
        var catalog = new com.tonywww.elder_bosses.boss.malenia.action.MaleniaActionCatalog(defaults);
        int checks = 0;
        for (MaleniaActionId action : MaleniaActionId.values()) {
            var plan = com.tonywww.elder_bosses.boss.malenia.execution.MaleniaSkillEventPlanner.createPlan(defaults, action);
            equal(catalog.get(action).timeline().totalTicks(), plan.totalTicks());
            if (config.contains("malenia.skills." + action.serializedName() + ".cast_speed_multiplier")) throw new AssertionError("Obsolete Malenia speed field");
            var components = defaults.tuning(action).componentStages();
            if (!components.isEmpty()) {
                String prefix = "malenia.skills." + action.serializedName() + ".components.";
                List<Integer> windup = new ArrayList<>(), active = new ArrayList<>(), recovery = new ArrayList<>();
                for (int index = 0; index < components.size(); index++) {
                    windup.add(components.get(index).windupTicks() + 3 + index);
                    active.add(components.get(index).activeTicks() + 2 + index);
                    recovery.add(components.get(index).recoveryTicks() + 4);
                }
                config.set(prefix + "windup_ticks", windup);
                config.set(prefix + "active_ticks", active);
                config.set(prefix + "recovery_ticks", recovery);
            }
            checks += 2;
        }
        spec.afterReload();
        var custom = com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig.VALUES.maleniaSkillSnapshot();
        var customCatalog = new com.tonywww.elder_bosses.boss.malenia.action.MaleniaActionCatalog(custom);
        for (MaleniaActionId action : MaleniaActionId.values()) {
            var components = custom.tuning(action).componentStages();
            if (components.isEmpty()) continue;
            var timeline = customCatalog.get(action).timeline();
            if (!timeline.stages().equals(components)) throw new AssertionError("Configured components are not the runtime timeline: " + action);
            var plan = com.tonywww.elder_bosses.boss.malenia.execution.MaleniaSkillEventPlanner.createPlan(custom, action);
            equal(timeline.totalTicks(), plan.totalTicks());
            if (!plan.intents().stream().allMatch(intent -> intent.actionTick() >= 0 && intent.actionTick() < timeline.totalTicks()))
                throw new AssertionError("An intent lies outside its configured action: " + action);
            var reference = ActionTimeline.ofStages(com.tonywww.elder_bosses.boss.malenia.config.MaleniaSkillConfigSnapshot.defaultComponentStages(action).toArray(ActionStage[]::new));
            for (int stage = 0; stage < components.size(); stage++) {
                equal(reference.activeStartTick(stage), MaleniaAnimationTimeline.sample(timeline.activeStartTick(stage), action, timeline, Map.of()));
                checks++;
            }
            double previous = -1;
            for (double tick = 0; tick <= timeline.totalTicks(); tick += 0.25) {
                double sample = MaleniaAnimationTimeline.sample(tick, action, timeline, Map.of());
                if (sample < previous) throw new AssertionError("Component animation moved backwards: " + action);
                previous = sample;
                checks++;
            }
        }
        Class<?> nbt = com.tonywww.elder_bosses.boss.malenia.config.MaleniaConfigNbt.class;
        var write = nbt.getDeclaredMethod("writeTunings", Map.class);
        var read = nbt.getDeclaredMethod("readTunings", net.minecraft.nbt.CompoundTag.class, int.class);
        write.setAccessible(true);
        read.setAccessible(true);
        Object encoded = write.invoke(null, custom.tunings());
        if (!custom.tunings().equals(read.invoke(null, encoded, 12))) throw new AssertionError("Component timing lost in NBT roundtrip");
        return checks + 1;
    }

    private static void check(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void equal(double expected, double actual) {
        if (Math.abs(expected - actual) > 0.00001) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
}