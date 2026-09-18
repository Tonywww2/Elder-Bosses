import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSkillConfigSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase;
import com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortAttackPlan;
import com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionExecutor;
import com.tonywww.elder_bosses.boss.promisedconsort.indicator.PromisedConsortIndicatorGenerator;
import com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortActionSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortAnimationTimeline;
import com.tonywww.elder_bosses.combat.action.ActionPhase;
import com.tonywww.elder_bosses.combat.action.ActionStage;
import com.tonywww.elder_bosses.combat.action.ActionTimeline;
import com.tonywww.elder_bosses.combat.geometry.*;
import com.tonywww.elder_bosses.boss.promisedconsort.action.PromisedConsortActionCatalog;
import com.tonywww.elder_bosses.combat.hit.HitId;
import com.tonywww.elder_bosses.combat.hit.HitRegistry;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class AttackPlanCheck {
    private static int assertions;
    private static int strikes;

    public static void main(String[] arguments) throws Exception {
        checkZeroWindupSegmentWarning();
        checkComboReposition();
        checkPursuitGait();
        checkRearCoverage();
        checkLayeredEffects();
        checkBatchConfiguredPlans();
        Method projection = PromisedConsortIndicatorGenerator.class.getDeclaredMethod("shape", HorizontalShape.class, double.class);
        projection.setAccessible(true);
        Vec3 origin = new Vec3(10.25, 70, -24.5);
        Vec2 facing = new Vec2(0.6, 0.8);
        Map<String, Vec3> points = Map.of("target", origin.add(6, 0, 8), "meteor", origin.add(-6, 0, 9),
                "clone_meteor_0", origin.add(1, 0, 4), "clone_meteor_1", origin.add(4, 0, 2),
                "clone_meteor_2", origin.add(-1, 0, 3), "clone_meteor_3", origin.add(-4, 0, 2));
        for (double speed : new double[]{1.0, 1.2, 1.35, 1.5, 1.75, 2.0}) {
            for (double scale : new double[]{0.5, 1.0, 1.8, 2.4}) {
                for (PromisedConsortActionId action : PromisedConsortActionId.values()) {
                    if(action.rangedDefense()) continue;
                    for (PromisedConsortPhase phase : PromisedConsortPhase.values()) {
                    var skill = skill(speed, scale, action);
                    int[] durations = PromisedConsortAnimationTimeline.durations(action);
                    ActionStage[] stages = new ActionStage[durations.length / 3];
                    for (int index = 0; index < stages.length; index++) {
                        stages[index] = new ActionStage(skill.tuning().scaleTicks(durations[index * 3]),
                                skill.tuning().scaleTicks(durations[index * 3 + 1]), skill.tuning().scaleTicks(durations[index * 3 + 2]));
                    }
                    var snapshot = new PromisedConsortActionSnapshot(action, phase, 1, 100, 0, ActionPhase.WINDUP, 0, 0, 42, null);
                    List<PromisedConsortAttackPlan.Strike> plan = PromisedConsortAttackPlan.create(snapshot, skill, ActionTimeline.ofStages(stages), origin, facing, points, 6);
                    require(!plan.isEmpty(), "Missing telegraph for " + action);
                        if (action == PromisedConsortActionId.LION_CLAW || action == PromisedConsortActionId.LION_CLAW_DOUBLE) {
                        Circle slam = (Circle) find(plan, action == PromisedConsortActionId.LION_CLAW ? "slam" : "double").shape();
                        require(Math.abs(slam.radius() - skill.number("range") * scale * 1.3 * 1.5) < 0.00001,
                            "Lion claw and followup must receive the larger impact radius");
                        }
                        if (action == PromisedConsortActionId.GRAVITY_DIVE) {
                        require(Math.abs(((Circle) find(plan, "sword").shape()).radius() - skill.number("range") * scale * 0.65 * 1.3 * 1.5) < 0.00001,
                            "Gravity dive sword radius is too small");
                        require(Math.abs(((Circle) find(plan, "impact").shape()).radius() - skill.number("range") * scale * 1.5) < 0.00001,
                            "Gravity dive second impact missed the radius increase");
                        }
                        if (action == PromisedConsortActionId.STOMP) {
                        DirectionalRectangle stomp = (DirectionalRectangle) find(plan, "stomp").shape();
                        require(Math.abs(stomp.length() - (skill.number("forward_range") * scale * 1.3 * 1.5 + 0.8)) < 0.00001
                            && Math.abs(stomp.width() - skill.number("width") * scale * 1.3 * 1.5) < 0.00001,
                            "Stomp length and width must both increase while preserving rear reach");
                        }
                    require(plan.stream().map(PromisedConsortAttackPlan.Strike::id).distinct().count() == plan.size(), "Duplicate attack occurrence " + action);
                    checkCloneBudget(plan, action + "/" + phase + "/" + speed);
                    for (var cue : PromisedConsortAttackPlan.cloneCues(plan)) {
                        require(cue.appearTick() >= cue.strike().lockTick(), "Clone appears before the attack position is frozen");
                        require(cue.appearTick() < cue.strike().activeTick(), "Clone has no visible preparation before damage");
                        require(PromisedConsortAnimationTimeline.cloneTick(cue.appearTick(), cue.appearTick(), cue.strike().activeTick()) == 0,
                                "Clone does not begin in its authored preparation pose");
                        require(PromisedConsortAnimationTimeline.cloneTick(cue.strike().activeTick(), cue.appearTick(), cue.strike().activeTick()) == 4,
                                "Clone contact pose is not aligned to the authoritative damage tick");
                        double previousCloneTick = 0;
                        for (double now = cue.appearTick(); now <= cue.strike().activeTick() + 16; now += 0.25) {
                            double cloneTick = PromisedConsortAnimationTimeline.cloneTick(now, cue.appearTick(), cue.strike().activeTick());
                            require(cloneTick >= previousCloneTick && cloneTick < 12, "Clone clock reversed or exceeded its clip");
                            previousCloneTick = cloneTick;
                        }
                    }
                    if (action == PromisedConsortActionId.L_COMBO_CROSS || action == PromisedConsortActionId.R_COMBO_CROSS
                            || action == PromisedConsortActionId.R_COMBO_LEFT_TWIN) {
                        HitRegistry registry = new HitRegistry();
                        UUID target = new UUID(1, 2);
                        for (int stageIndex = 0; stageIndex < stages.length; stageIndex++) {
                            var strike = plan.get(stageIndex);
                            require(strike.endTick() - strike.activeTick() == stages[stageIndex].activeTicks(),
                                    "Sword contact window ends before the configured active stage: " + action + "/" + stageIndex);
                            for (int activeTick = 0; activeTick < stages[stageIndex].activeTicks(); activeTick++) {
                                require(registry.claim(new HitId(snapshot.sequence(), stageIndex), target) == (activeTick == 0),
                                        "A sword segment damages one target more than once across its contact window");
                            }
                        }
                    }
                    for (var strike : plan) {
                        strikes++;
                        require(strike.startTick() < strike.activeTick(), "No warning before " + action + "/" + strike.id());
                        require(strike.startTick() <= strike.lockTick() && strike.lockTick() < strike.activeTick() && strike.activeTick() < strike.endTick(), "Invalid timing");
                        Object encoded = projection.invoke(null, strike.shape(), strike.baseY());
                        HorizontalShape decoded = decode(encoded);
                        double yaw = Math.toRadians((float) field(encoded, "yawDegrees"));
                        var restored = PromisedConsortAttackPlan.relocate(strike, (Vec3) field(encoded, "anchor"),
                            new Vec2(-Math.sin(yaw), Math.cos(yaw)));
                        require(Math.abs(strike.shape().minX() - restored.shape().minX()) < 0.0001
                            && Math.abs(strike.shape().maxZ() - restored.shape().maxZ()) < 0.0001,
                            "Frozen attack geometry differs after restoring " + action + "/" + strike.id());
                        var late = strike.observedAt(strike.activeTick());
                        require(late.startTick() == late.activeTick(), "Unannounced strike must be rejected by the hit gate");
                        require(Math.abs(strike.shape().minX() - decoded.minX()) < 0.0001 && Math.abs(strike.shape().maxZ() - decoded.maxZ()) < 0.0001, "Indicator bounds differ: " + action + "/" + strike.id());
                        Random random = new Random(42);
                        for (int sample = 0; sample < 120; sample++) {
                            double horizontal = strike.shape().minX() - 1 + random.nextDouble() * (strike.shape().maxX() - strike.shape().minX() + 2);
                            double depth = strike.shape().minZ() - 1 + random.nextDouble() * (strike.shape().maxZ() - strike.shape().minZ() + 2);
                            require(strike.shape().contains(horizontal, depth) == decoded.contains(horizontal, depth), "Indicator footprint differs: " + action + "/" + strike.id());
                        }
                    }
                    if (action == PromisedConsortActionId.STARCALLER_CRY) {
                        if (phase == PromisedConsortPhase.PHASE_TWO) {
                            Capsule cross = (Capsule) find(plan, "clone_cross_0").shape();
                            require(Math.abs((cross.start().x() + cross.end().x()) / 2 - origin.x) < 0.0001, "Clone must be centered, not forward-only");
                        } else {
                            require(plan.stream().noneMatch(strike -> strike.id().startsWith("clone_cross_")), "Phase one must not announce holy clones");
                        }
                        require(find(plan, "spikes").shape() instanceof Annulus, "Missing gravity spikes warning");
                    }
                    if (action == PromisedConsortActionId.LION_CLAW) {
                        Circle landing = (Circle) find(plan, "slam").shape();
                        require(landing.center().subtract(new Vec2(origin.x, origin.z)).length() <= 1.25001, "Landing marker differs from controlled movement");
                    }
                    if (action == PromisedConsortActionId.GRAVITY_DIVE) {
                        var leap = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortCrossLeapPath.gravityDive(ActionTimeline.ofStages(stages), 0);
                        var destination = leap.destination(origin, points.getOrDefault("target", origin), leap.maximumDistance(16), 1.5);
                        Circle landing = (Circle) find(plan, "sword").shape();
                        require(landing.center().subtract(new Vec2(destination.x, destination.z)).length() < 0.0001, "Gravity landing prediction differs from actual flight destination");
                    }
                    if (action == PromisedConsortActionId.RING_OF_LIGHT) {
                        Annulus last = (Annulus) plan.get(plan.size() - 1).shape();
                        require(Math.abs(last.outerRadius() - 11 * scale) < 0.0001, "Wrong final ring range");
                        require(plan.size() == stages[0].activeTicks(), "Ring must have each actual damage band");
                    }
                        if (action == PromisedConsortActionId.SPIRAL_ASSAULT) {
                        var leap = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortCrossLeapPath.advance(ActionTimeline.ofStages(stages), 0);
                        var destination = leap.advanceDestination(origin, points.getOrDefault("target", origin), new Vec3(facing.x(), 0, facing.z()), 16);
                        Vec2 expected = new Vec2(destination.x, destination.z);
                        Circle landing = (Circle) find(plan, "slam").shape();
                        require(landing.center().subtract(expected).length() < 0.0001, "Advance landing prediction differs from the leap destination");
                        Sector opening = (Sector) find(plan, "spin").shape();
                        require(opening.center().subtract(expected.subtract(facing.scale(PromisedConsortAttackPlan.REAR_REACH))).length() < 0.0001,
                            "Advance opening slash is not located at landing");
                        int sampledTick = Math.max(0, stages[0].windupTicks() - 6);
                        var beforeImpact = new PromisedConsortActionSnapshot(action, snapshot.phase(), snapshot.sequence(), snapshot.startGameTick(),
                            sampledTick, ActionPhase.WINDUP, 0, sampledTick, snapshot.seed(), null);
                        var laterPlan = PromisedConsortAttackPlan.create(beforeImpact, skill, ActionTimeline.ofStages(stages), origin, facing, points, 6);
                        require(((Circle) find(laterPlan, "slam").shape()).center().subtract(expected).length() < 0.0001,
                            "Advance endpoint must remain stable during windup");
                        }
                    if (action == PromisedConsortActionId.CONSORT_METEOR) {
                        Circle core = (Circle) find(plan, "core").shape();
                        Annulus outer = (Annulus) find(plan, "outer").shape();
                        Annulus aftershock = (Annulus) find(plan, "aftershock").shape();
                        require(core.radius() == outer.innerRadius(), "Meteor bands disagree");
                        require(outer.outerRadius() == aftershock.innerRadius(), "Meteor aftershock must meet the outer band");
                        if (scale == 2.4) {
                            require(Math.abs(core.radius() - 21.6) < 0.0001, "Wrong configured meteor core");
                            require(Math.abs(outer.outerRadius() - 31.2) < 0.0001, "Wrong configured meteor outer band");
                            require(Math.abs(aftershock.outerRadius() - 36.0) < 0.0001, "Meteor must end at the user-confirmed 36-block radius");
                            require(aftershock.outerRadius() < 40.0, "Configured meteor must leave space inside the arena");
                        }
                        require(find(plan, "core").startTick() == 100 + skill.tuning().scaleTicks(91), "Meteor warning must start at locked prediction");
                        require(find(plan, "aftershock").activeTick() == 100 + skill.tuning().scaleTicks(121) + skill.tuning().scaleTicks(2), "Meteor aftershock scaling differs from executor");
                    }
                    if (action == PromisedConsortActionId.GRAVITY_METEOR) {
                        int expectedClones = 0;
                        for (int phaseTick = 0; phase == PromisedConsortPhase.PHASE_TWO && phaseTick < stages[0].recoveryTicks(); phaseTick++) {
                            if (phaseTick % skill.tuning().scaleTicks(5) == 0 && phaseTick <= skill.tuning().scaleTicks(15)) expectedClones++;
                        }
                        require(plan.stream().filter(strike -> strike.id().startsWith("clone_meteor_")).count() == expectedClones,
                                "Gravity meteor must not announce an extra clone after scaled recovery cutoff");
                    }
                    if (action == PromisedConsortActionId.LIGHTSPEED_DASH) {
                        int expectedClones = 0;
                        for (int phaseTick = 0; phaseTick < stages[0].activeTicks(); phaseTick++) {
                            if (phaseTick % skill.tuning().scaleTicks(4) == 0 && phaseTick < skill.tuning().scaleTicks(16)) expectedClones++;
                        }
                        require(plan.stream().filter(strike -> strike.id().startsWith("clone_")).count() == expectedClones,
                                "Lightspeed clones must match scaled execution offsets");
                    }
                    }
                }
            }
        }
        checkMeteorPresentation();
        require(PromisedConsortAnimationTimeline.completedCloneIndex(20, new long[]{20, 21, 24}) == -1,
            "Visual budget must preserve pending contacts and contacts on the current tick");
        require(PromisedConsortAnimationTimeline.completedCloneIndex(20, new long[]{19, 12, 17}) == 1,
            "Visual budget must yield the oldest completed contact first");
        require(PromisedConsortAnimationTimeline.completedCloneIndex(20, new long[0]) == -1,
            "Empty clone list must not produce a replacement");
        System.out.println("Attack plan passed: " + assertions + " checks; " + strikes + " strikes; 22 actions, 2 phases, 6 speeds, 4 range scales; meteor presentation timing and clone budget");
    }

    private static void checkComboReposition() {
        double separation = PromisedConsortActionExecutor.preferredSeparation(1.9, 0.6);
        require(separation == 4.0, "Normal combat spacing must stop before face-to-face contact");
        require(!PromisedConsortActionExecutor.shouldApproach(4.0, separation, true), "Approach overshoots the stop distance");
        require(!PromisedConsortActionExecutor.shouldApproach(4.9, separation, false), "Stopped approach jitters inside the dead band");
        require(PromisedConsortActionExecutor.shouldApproach(5.1, separation, false), "Approach does not resume outside the dead band");
        require(PromisedConsortActionExecutor.shouldApproach(4.5, separation, true), "Approach stops before reaching its destination");
        require(PromisedConsortActionExecutor.repositionStep(Vec3.ZERO, new Vec3(0, 0, 3.9), new Vec2(0, 1), separation, 1.5)
            .movement().equals(Vec3.ZERO), "Combo pursuit ignores combat spacing");
        var action = PromisedConsortActionId.L_COMBO_CROSS;
        var timeline = ActionTimeline.ofStages(new ActionStage(11, 3, 1), new ActionStage(6, 3, 9), new ActionStage(11, 4, 36));
        Vec3 position = Vec3.ZERO;
        Vec2 facing = new Vec2(0, 1);
        double travelled = 0;
        for (int tick = 0; tick < timeline.totalTicks(); tick++) {
            int stage = PromisedConsortAttackPlan.repositionStage(action, timeline, tick, 6);
            if (stage < 0) continue;
            require(tick >= timeline.activeEndTick(stage - 1) && tick < timeline.activeStartTick(stage), "Reposition overlaps a strike");
            var step = PromisedConsortActionExecutor.repositionStep(position, new Vec3(8, 0, 4), facing, 1.5, 1.5 - travelled);
            require(step.movement().length() <= 0.250001 && step.movement().y == 0, "Unbounded pursuit step");
            double change = Math.acos(Math.min(1, facing.x() * step.facing().x() + facing.z() * step.facing().z()));
            require(change <= Math.toRadians(20.001), "Instant pursuit turn");
            facing = step.facing();
            travelled += step.movement().length();
            position = position.add(step.movement());
        }
        require(travelled > 0 && travelled <= 1.500001 && facing.x() > 0.5, "Pursuit did not move and retarget");
        require(PromisedConsortActionExecutor.repositionStep(Vec3.ZERO, Vec3.ZERO, facing, 1.5, 1.5).movement().equals(Vec3.ZERO), "Overlapping target causes movement");
        require(PromisedConsortActionExecutor.repositionStep(Vec3.ZERO, new Vec3(0, 0, 1), new Vec2(0, 1), 1.5, 1.5).movement().equals(Vec3.ZERO), "Pursuit runs through a close target");
        var shortStages = ActionTimeline.ofStages(new ActionStage(8, 3, 0), new ActionStage(0, 3, 0));
        for (int tick = 0; tick < shortStages.totalTicks(); tick++) require(PromisedConsortAttackPlan.repositionStage(action, shortStages, tick, 6) == -1,
                "Back-to-back active stages must not invent movement time");
        for (int tick = 0; tick < timeline.totalTicks(); tick++) require(PromisedConsortAttackPlan.repositionStage(PromisedConsortActionId.CROSS_SLASH, timeline, tick, 6) == -1,
                "Delayed debris must not reposition the body");
    }

    private static void checkPursuitGait() {
        var gait = new com.tonywww.elder_bosses.client.render.PursuitGait();
        require(gait.sample(0, 0, 0, 0, true).weight() == 0, "Gait needs an initial position");
        var moved = gait.sample(1, 0, 0, 0.2, true);
        require(moved.tick() > 0 && moved.weight() > 0, "Short pursuit does not animate feet");
        require(gait.sample(1, 0, 0, 0.2, true).equals(moved), "Multiple render passes advance the gait");
        var stopped = gait.sample(2, 0, 0, 0.2, true);
        require(stopped.tick() == moved.tick(), "Stationary feet keep walking");
        require(gait.sample(3, 0, 0, 0.3, false).weight() == 0, "Attack outside pursuit is overwritten");
        require(gait.sample(4, 10, 0, 10, true).weight() == 0, "Teleport triggers walking");
        require(gait.sample(5, 10, 3, 10, true).weight() == 0, "Airborne movement triggers walking");
        require(gait.sample(20, 10, 3, 10.1, true).weight() == 0, "Long render gap accumulates walking");
        var up = new org.joml.Vector3f(0, 1, 0);
        var original = new org.joml.Vector3f(0, -20, 4);
        var lowered = new org.joml.Vector3f(0, -21, 3);
        var correction = com.tonywww.elder_bosses.client.render.PursuitGait.supportCorrection(original, lowered, up);
        var corrected = correction.transform(new org.joml.Vector3f(lowered));
        require(corrected.y >= original.y - 0.0001 && Math.abs(corrected.length() - lowered.length()) < 0.0001,
            "Pursuit support sinks or stretches the leg");
        var raised = new org.joml.Vector3f(0, -18, 6);
        require(com.tonywww.elder_bosses.client.render.PursuitGait.supportCorrection(original, raised, up).angle() == 0,
            "Support correction removes the swing-foot lift");
        var track = new com.tonywww.elder_bosses.client.render.PursuitGait.RotationTrack(new double[]{0, 10, 40},
            new float[][]{{0, 0, 0}, {1, 2, 3}, {0, 0, 0}});
        require(track.at(5)[1] == 1 && track.at(40)[1] == 0, "Gait curve interpolation or loop endpoint differs");
    }

    private static void checkRearCoverage() {
        var expanded = (Circle) PromisedConsortAttackPlan.expandMelee(new Circle(2, 3, 4));
        require(Math.abs(expanded.radius() - 5.2) < 0.000001 && expanded.center().equals(new Vec2(2, 3)),
            "Melee impact expansion changes its landing point or misses the requested increase");
        for (var action : List.of(PromisedConsortActionId.L_COMBO_CROSS, PromisedConsortActionId.L_COMBO_BLOODFLAME,
                PromisedConsortActionId.STOMP, PromisedConsortActionId.R_COMBO_TEMPEST, PromisedConsortActionId.RING_OF_LIGHT)) {
            int[] durations = PromisedConsortAnimationTimeline.durations(action);
            List<ActionStage> stages = new ArrayList<>();
            for (int index = 0; index < durations.length; index += 3) stages.add(new ActionStage(durations[index], durations[index + 1], durations[index + 2]));
            var snapshot = new PromisedConsortActionSnapshot(action, PromisedConsortPhase.PHASE_ONE, 1, 100, 0, ActionPhase.WINDUP, 0, 0, 42, null);
            var plan = PromisedConsortAttackPlan.create(snapshot, skill(1, 1, action), ActionTimeline.ofStages(stages.toArray(ActionStage[]::new)),
                    Vec3.ZERO, new Vec2(0, 1), Map.of(), 6);
            for (var strike : plan) {
                if (strike.id().startsWith("ring_") && !strike.id().equals("ring_0")) continue;
                require(strike.shape().contains(0, -0.4), "Close rear blind spot: " + action + "/" + strike.id());
            }
            if (action == PromisedConsortActionId.L_COMBO_CROSS) {
                double front = skill(1, 1, action).number("range") * PromisedConsortAttackPlan.MELEE_REACH_MULTIPLIER * 1.15;
                require(plan.get(0).shape().contains(0, front - 0.001) && !plan.get(0).shape().contains(0, front + 0.001), "Rear reach changed forward range");
            }
        }
    }

    private static void checkZeroWindupSegmentWarning() {
        var actionId = PromisedConsortActionId.L_COMBO_CROSS;
        var snapshot = new PromisedConsortActionSnapshot(actionId, PromisedConsortPhase.PHASE_ONE,
                1, 100, 0, ActionPhase.WINDUP, 0, 0, 42, null);
        var timeline = ActionTimeline.ofStages(new ActionStage(8, 3, 4), new ActionStage(0, 3, 4), new ActionStage(0, 4, 6));
        var plan = PromisedConsortAttackPlan.create(snapshot, skill(1, 1, actionId), timeline,
                Vec3.ZERO, new Vec2(0, 1), Map.of(), 6);
        for (int index = 0; index < timeline.stages().size(); index++) {
            var strike = plan.get(index);
            require(strike.activeTick() == 100 + timeline.activeStartTick(index), "Warning changed absolute contact tick");
            require(strike.activeTick() - strike.startTick() >= 6, "A zero-windup segment has no advance warning");
            var moved = strike.withShape(new Circle(2, 3, 4), 0);
            var locked = moved.trackFrom(strike, strike.lockTick());
            require(locked.shape().equals(moved.shape()), "Lock tick discarded the final pursuit step");
            require(locked.startTick() == strike.startTick(), "Tracking resets the advance warning");
            var afterLock = strike.withShape(new Circle(8, 9, 4), 0).trackFrom(locked, strike.lockTick() + 1);
            require(afterLock.equals(locked), "Locked attack follows a moving target");
        }
    }

    private static void checkCloneBudget(List<PromisedConsortAttackPlan.Strike> plan, String context) {
        List<PromisedConsortAttackPlan.CloneCue> visible = new ArrayList<>();
        for (var cue : PromisedConsortAttackPlan.cloneCues(plan).stream()
                .sorted(Comparator.comparingLong(PromisedConsortAttackPlan.CloneCue::appearTick)).toList()) {
            visible.removeIf(previous -> previous.appearTick() + 16 <= cue.appearTick());
            if (visible.size() >= 4) {
                int replacement = PromisedConsortAnimationTimeline.completedCloneIndex(cue.appearTick(),
                        visible.stream().mapToLong(previous -> previous.strike().activeTick()).toArray());
                require(replacement >= 0, "Default clone budget cannot preserve all pending contacts: " + context);
                require(visible.get(replacement).strike().activeTick() < cue.appearTick(), "Replaced a clone before its contact");
                visible.remove(replacement);
            }
            require(visible.size() < 4, "Default clone budget drops an announced contact: " + context + "/" + cue.strike().id());
            visible.add(cue);
        }
    }

    private static void checkLayeredEffects() {
        var pose = new com.mojang.blaze3d.vertex.PoseStack().last();
        List<Vec3> vertices = new ArrayList<>();
        Class<?> consumerType = com.mojang.blaze3d.vertex.VertexConsumer.class;
        var consumer = (com.mojang.blaze3d.vertex.VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
                consumerType.getClassLoader(), new Class<?>[]{consumerType}, (proxy, method, arguments) -> {
                    if (arguments != null) for (Object argument : arguments) if (argument instanceof Number number) {
                        require(Double.isFinite(number.doubleValue()), "Non-finite effect vertex attribute");
                    }
                    if (method.getName().equals("vertex") && arguments.length == 4) {
                        vertices.add(new Vec3(((Number) arguments[1]).doubleValue(), ((Number) arguments[2]).doubleValue(),
                                ((Number) arguments[3]).doubleValue()));
                    }
                    return method.getReturnType() == consumerType ? proxy : null;
                });
        var center = new Vec3(2, 70, 4);
        com.tonywww.elder_bosses.client.vfx.ClientConsortMeteorRenderer.shockDome(consumer, pose, center, 9, 8, 0xFFE7A8, 0.7F);
        require(vertices.size() == 960, "Meteor shell must have real curved surface geometry");
        for (Vec3 vertex : vertices) {
            double horizontal = Math.hypot(vertex.x - center.x, vertex.z - center.z);
            require(horizontal <= 9.00001 && vertex.y >= 70 && vertex.y <= 78.00001, "Meteor shell exceeded its bounds");
        }
        require(vertices.stream().anyMatch(vertex -> vertex.y > 77.99), "Meteor shell remains a flat ground quad");
        vertices.clear();
        com.tonywww.elder_bosses.client.vfx.ClientConsortEnergyRenderer.ringWall(consumer, pose, center, 8, 3, 0xFFFFFF, 0.8F, 48);
        require(vertices.size() == 192 && vertices.stream().anyMatch(vertex -> vertex.y == 73), "Holy ring is missing its vertical wall");
        vertices.clear();
        com.tonywww.elder_bosses.client.vfx.ClientConsortEnergyRenderer.pillar(consumer, pose, center, center.add(0, 20, 0), 1, 12, 0xFFFFFF, 1);
        require(vertices.size() == 8, "Pillars must use crossed planes, including the overhead camera case");
        require(vertices.stream().anyMatch(vertex -> vertex.x != center.x) && vertices.stream().anyMatch(vertex -> vertex.z != center.z),
                "Crossed light pillar collapses to one plane");
        for (var style : List.of(com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.StyleRole.HOLY_IVORY,
                com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.StyleRole.CLONE_GOLD,
                com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.StyleRole.BLOODFLAME_RED,
                com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.StyleRole.PHYSICAL_GOLD)) {
            var snapshot = new com.tonywww.elder_bosses.network.IndicatorSnapshotPacket(1, "hazard:1:stomp",
                    com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.SegmentSlot.CURRENT, style,
                    com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.Semantic.PHYSICAL,
                    com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.IndicatorState.ACTIVE,
                    com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.ShapeType.CIRCLE,
                    new com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.Point(0, 70, 0),
                    0, List.of(5.0F), List.of(), 100, 110, 116, 117, false);
            long end = com.tonywww.elder_bosses.client.vfx.ClientConsortEnergyRenderer.visualEndTick(snapshot);
            require(end > 117 && end <= 139, "Layered effect does not have a bounded independent lifetime");
            require(snapshot.activeTick() == 116 && snapshot.endTick() == 117, "Effect lifetime changed the damage window");
        }
    }

    private static void checkMeteorPresentation() throws Exception {
        Vec3 bladeRoot = new Vec3(2, 3, 4), bladeTip = new Vec3(2, 3, 9);
        Vec3 trailTip = com.tonywww.elder_bosses.client.vfx.ClientConsortBladeTrails.trailTip(bladeRoot, bladeTip);
        require(Math.abs(trailTip.distanceTo(bladeRoot) - 6.9) < 0.00001 && trailTip.x == bladeTip.x && trailTip.y == bladeTip.y,
            "Expanded sword trail must extend along the actual blade, not displace its grip");
        require(com.tonywww.elder_bosses.client.vfx.ClientConsortMeteorRenderer.impactEnvelope(-1) == 0,
            "Meteor impact flash begins before damage");
        require(com.tonywww.elder_bosses.client.vfx.ClientConsortMeteorRenderer.impactEnvelope(0) == 1,
            "Meteor landing lacks an immediate flash");
        require(com.tonywww.elder_bosses.client.vfx.ClientConsortMeteorRenderer.impactEnvelope(20) > 0.2,
            "Meteor aftermath ends with the one-tick damage window");
        require(com.tonywww.elder_bosses.client.vfx.ClientConsortMeteorRenderer.impactEnvelope(40) == 0,
            "Meteor aftermath does not expire");
        Class<?> renderer = Class.forName("com.tonywww.elder_bosses.client.vfx.ClientConsortMeteorRenderer");
        Method charge = renderer.getDeclaredMethod("charge", double.class);
        Method returning = renderer.getDeclaredMethod("returnProgress", double.class);
        Method presentation = renderer.getDeclaredMethod("presentationTick", double.class);
        charge.setAccessible(true);
        returning.setAccessible(true);
        presentation.setAccessible(true);
        require((double) presentation.invoke(null, 183.0) == 91.0, "New meteor lock must enter the locked presentation interval");
        require((double) presentation.invoke(null, 202.0) == 110.0, "New meteor warning must start visible reentry");
        require((double) returning.invoke(null, (double) presentation.invoke(null, 212.0)) == 1.0,
            "New meteor visual trajectory must finish on the configured landing");
        double previousPresentation = 0;
        for (double tick = 0; tick <= 280; tick += 0.25) {
            double current = (double) presentation.invoke(null, tick);
            require(current >= previousPresentation && current <= 150, "New meteor presentation clock reverses");
            previousPresentation = current;
        }
        require((double) charge.invoke(null, 60.0) == 0.0, "Meteor must have a quiet airborne interval");
        require((double) charge.invoke(null, 76.0) == 0.0, "Meteor charge must begin after the airborne pause");
        require((double) charge.invoke(null, 116.0) == 1.0, "Meteor must reach full brightness before landing");
        require((double) returning.invoke(null, 110.0) == 0.0, "Meteor return must not precede the locked approach");
        require((double) returning.invoke(null, 121.0) == 1.0, "Meteor visual return must meet the actual landing time");
        double previousCharge = 0.0;
        double previousReturn = 0.0;
        for (double tick = 0; tick <= 121; tick += 0.25) {
            double actualCharge = (double) charge.invoke(null, tick);
            double actualReturn = (double) returning.invoke(null, tick);
            require(actualCharge >= previousCharge && actualCharge <= 1.0, "Meteor brightness reverses or exceeds its bound");
            require(actualReturn >= previousReturn && actualReturn <= 1.0, "Meteor approach reverses or overshoots");
            previousCharge = actualCharge;
            previousReturn = actualReturn;
        }
    }

        private static void checkBatchConfiguredPlans() throws Exception {
        var parser = new com.electronwill.nightconfig.toml.TomlParser();
        for (String file : List.of("docs/config/elder-bosses-common.example.toml", "run/config/elder_bosses-common.toml",
            "versions/1.21.1-neoforge/run/config/elder_bosses-common.toml")) {
            var document = parser.parse(java.nio.file.Files.readString(java.nio.file.Path.of(file)));
            Number distance = document.get("promised_consort.targeting.max_segment_pursuit_distance");
            require(distance != null && Double.isFinite(distance.doubleValue()) && distance.doubleValue() >= 0 && distance.doubleValue() <= 16,
                "Missing or invalid pursuit setting in " + file);
        }
        var builder = new net.minecraftforge.common.ForgeConfigSpec.Builder();
        var values = new com.tonywww.elder_bosses.platforms.config.PromisedConsortConfigValues(builder);
        var spec = builder.build();
        var before = parser.parse(java.nio.file.Files.readString(java.nio.file.Path.of(
            "models/promised_consort/tests/fixtures/retiming_compatibility.toml")));
        var after = parser.parse(java.nio.file.Files.readString(java.nio.file.Path.of(
            "docs/config/elder-bosses-common.example.toml")));
        spec.setConfig(before);
        var oldSkills = values.skillSnapshot();
        var oldCatalog = new PromisedConsortActionCatalog(oldSkills);
        spec.setConfig(after);
        var newSkills = values.skillSnapshot();
        require(values.combatSnapshot().targeting().maxSegmentPursuitDistance() == 3.0, "Default pursuit distance must be 3 blocks");
        for (double distance : new double[]{0, 3, 6, 16}) {
            after.set("promised_consort.targeting.max_segment_pursuit_distance", distance);
            spec.setConfig(after);
            var combat = values.combatSnapshot();
            require(combat.targeting().maxSegmentPursuitDistance() == distance, "Configured pursuit distance not loaded");
            var saved = com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.write(combat, newSkills);
            require(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.read(saved).orElseThrow()
                .combat().targeting().maxSegmentPursuitDistance() == distance, "Pursuit distance does not survive save/resume");
            var legacy = com.google.gson.JsonParser.parseString(saved.getString("Combat")).getAsJsonObject();
            legacy.getAsJsonObject("targeting").remove("maxSegmentPursuitDistance");
            saved.putString("Combat", legacy.toString());
            require(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt.read(saved).orElseThrow()
                .combat().targeting().maxSegmentPursuitDistance() == 3.0, "Legacy snapshot lost the default pursuit distance");
            Vec3 position = Vec3.ZERO;
            for (int tick = 0; tick < 100; tick++) position = position.add(PromisedConsortActionExecutor.repositionStep(position,
                new Vec3(0, 0, 100), new Vec2(0, 1), 4, distance - position.z).movement());
            require(Math.abs(position.z - distance) < 0.00001, "Configured pursuit budget not enforced");
        }
        after.set("promised_consort.targeting.max_segment_pursuit_distance", 3.0);
        spec.setConfig(after);
        var newCatalog = new PromisedConsortActionCatalog(newSkills);
        var indicator = new PromisedConsortIndicatorGenerator(newCatalog, values.combatSnapshot());
        var points = Map.of("target", new Vec3(0,0,6), "meteor", Vec3.ZERO,
            "clone_meteor_0", new Vec3(0,0,5), "clone_meteor_1", new Vec3(1,0,5),
            "clone_meteor_2", new Vec3(-1,0,5), "clone_meteor_3", new Vec3(2,0,5));
        for (PromisedConsortActionId action : PromisedConsortActionId.values()) {
            if(action.rangedDefense()) continue;
            var skill = newSkills.get(action);
            var old = oldSkills.get(action);
            require(skill.numbers().equals(old.numbers()) && skill.damage().equals(old.damage()),
                "Batch retiming changed range or damage values: " + action);
            var timeline = newCatalog.get(action).timeline();
            var record = action == PromisedConsortActionId.CROSS_LEAP_COMBO ? com.google.gson.JsonParser.parseString("""
                {"duration_ticks":151,"stage_ticks":[[27,3,4],[7,3,4],[6,4,4],[8,4,9],[28,1,1],[2,1,2],[2,1,2],[4,1,23]]}
                """).getAsJsonObject() : com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(
                "models/promised_consort/tests/fixtures/action_timings.json"))).getAsJsonObject().getAsJsonObject(action.serializedName());
            var stages = record.getAsJsonArray("stage_ticks");
            require(stages.size() == timeline.stages().size(), "Configured batch stage count differs from animation: " + action);
            for (int index = 0; index < stages.size(); index++) {
                var expected = stages.get(index).getAsJsonArray();
                var actual = timeline.stages().get(index);
                require(actual.windupTicks() == expected.get(0).getAsInt() && actual.activeTicks() == expected.get(1).getAsInt()
                    && actual.recoveryTicks() == expected.get(2).getAsInt(), "Configured batch timing differs from animation: " + action);
            }
            require(timeline.totalTicks() == record.get("duration_ticks").getAsInt(), "Batch animation total differs: " + action);
            for (PromisedConsortPhase phase : PromisedConsortPhase.values()) {
            var snapshot = new PromisedConsortActionSnapshot(action, phase, 1,100,0,ActionPhase.WINDUP,0,0,42,null);
            var oldPlan = PromisedConsortAttackPlan.create(snapshot, old, oldCatalog.get(action).timeline(), Vec3.ZERO,new Vec2(0,1),points,6);
            var newPlan = PromisedConsortAttackPlan.create(snapshot, skill, timeline, Vec3.ZERO,new Vec2(0,1),points,6);
            require(oldPlan.stream().map(PromisedConsortAttackPlan.Strike::id).sorted().toList()
                .equals(newPlan.stream().map(PromisedConsortAttackPlan.Strike::id).sorted().toList()),
                "Batch retiming changed actual configured attack IDs/count: " + action + "/" + phase);
            for (var strike : newPlan) {
                var previous = find(oldPlan, strike.id());
                require(strike.shape().getClass() == previous.shape().getClass(), "Rhythm changed attack shape type: " + action + "/" + strike.id());
                require(Math.abs((strike.shape().maxX() - strike.shape().minX()) - (previous.shape().maxX() - previous.shape().minX())) < 0.00001
                    && Math.abs((strike.shape().maxZ() - strike.shape().minZ()) - (previous.shape().maxZ() - previous.shape().minZ())) < 0.00001,
                    "Rhythm changed actual configured attack dimensions: " + action + "/" + strike.id());
            }
            checkCloneBudget(newPlan, "actual_batch/" + action + "/" + phase);
                for (var strike : newPlan) {
                long warningTick = strike.activeTick() - 1;
                int elapsed = Math.toIntExact(warningTick - snapshot.startGameTick());
                if (elapsed < 0) continue;
                var window = timeline.windowAt(elapsed);
                var warningAction = new PromisedConsortActionSnapshot(action, phase, snapshot.sequence(), snapshot.startGameTick(),
                    elapsed, window.phase(), window.stageIndex(), timeline.phaseTickAt(elapsed), snapshot.seed(), null);
                var packets = indicator.createAuthoritative(19, warningAction, newPlan, List.of(), warningTick);
                var packet = packets.stream().filter(value -> value.indicatorId().equals("hazard:1:" + strike.id())).findFirst();
                if (action == PromisedConsortActionId.GRAVITY_METEOR
                    && (strike.id().startsWith("rock_flight") || strike.id().startsWith("clone_meteor_"))) {
                    require(packet.isEmpty(), "Airborne meteor range indicator remains: " + strike.id());
                    continue;
                }
                require(packet.isPresent(), "Missing per-release indicator packet: " + action + "/" + strike.id());
                require(packet.get().startTick() < packet.get().activeTick()
                    && packet.get().activeTick() == strike.activeTick(), "Indicator packet changed contact time");
                require(packet.get().lockTick() == strike.lockTick(), "Cue replaced the authoritative indicator lock");
                checkAttackVolume(packet.get());
                long trackingTick = strike.lockTick() - 1;
                if (trackingTick >= strike.startTick()) {
                    int trackingElapsed = Math.toIntExact(trackingTick - snapshot.startGameTick());
                    var trackingWindow = timeline.windowAt(trackingElapsed);
                    var trackingAction = new PromisedConsortActionSnapshot(action, phase, snapshot.sequence(), snapshot.startGameTick(),
                        trackingElapsed, trackingWindow.phase(), trackingWindow.stageIndex(), timeline.phaseTickAt(trackingElapsed), snapshot.seed(), null);
                    var tracking = indicator.createAuthoritative(19, trackingAction, newPlan, List.of(), trackingTick).stream()
                        .filter(value -> value.indicatorId().equals("hazard:1:" + strike.id())).findFirst().orElseThrow();
                    require(tracking.state() == com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.IndicatorState.TRACKING,
                        "Moving indicator appears locked before pursuit ends");
                }
                }
            }
        }
        }

    private static void checkAttackVolume(com.tonywww.elder_bosses.network.IndicatorSnapshotPacket packet) {
        var mesh = com.tonywww.elder_bosses.client.indicator.ClientIndicatorGeometry.create(packet, 48);
        List<Vec3> vertices = new ArrayList<>();
        Class<?> consumerType = com.mojang.blaze3d.vertex.VertexConsumer.class;
        var consumer = (com.mojang.blaze3d.vertex.VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
            consumerType.getClassLoader(), new Class<?>[]{consumerType}, (proxy, method, arguments) -> {
                if (method.getName().equals("vertex") && arguments.length == 4) vertices.add(new Vec3(
                    ((Number) arguments[1]).doubleValue(), ((Number) arguments[2]).doubleValue(), ((Number) arguments[3]).doubleValue()));
                return method.getReturnType() == consumerType ? proxy : null;
            });
        for (float progress : new float[]{0, 0.5F, 0.999F}) {
            vertices.clear();
            com.tonywww.elder_bosses.client.vfx.ClientConsortEnergyRenderer.attackVolume(consumer,
                new com.mojang.blaze3d.vertex.PoseStack().last(), packet, progress, packet.styleRole().rgb(), 48);
            require(vertices.size() == (mesh.fills().size() + mesh.borders().size()) * 4, "Missing attack surface: " + packet.indicatorId());
            int index = 0;
            for (var fill : mesh.fills()) for (var expected : List.of(fill.first(), fill.second(), fill.third(), fill.fourth())) {
                Vec3 actual = vertices.get(index++);
                require(Math.abs(actual.x - expected.x()) < 0.0001 && Math.abs(actual.z - expected.z()) < 0.0001,
                    "Attack surface does not cover indicator mesh: " + packet.indicatorId());
                require(actual.y > expected.y(), "Attack surface remains flat on indicator: " + packet.indicatorId());
            }
        }
    }

        private static PromisedConsortAttackPlan.Strike find(List<PromisedConsortAttackPlan.Strike> strikes, String id) {
        return strikes.stream().filter(strike -> strike.id().equals(id)).findFirst().orElseThrow();
    }

    private static Object field(Object object, String name) throws Exception {
        Method method = object.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(object);
    }

    @SuppressWarnings("unchecked")
    private static HorizontalShape decode(Object encoded) throws Exception {
        Vec3 point = (Vec3) field(encoded, "anchor");
        Vec2 center = new Vec2(point.x, point.z);
        List<Float> ranges = (List<Float>) field(encoded, "ranges");
        double yaw = Math.toRadians((float) field(encoded, "yawDegrees"));
        Vec2 forward = new Vec2(-Math.sin(yaw), Math.cos(yaw));
        return switch (field(encoded, "type").toString()) {
            case "CIRCLE" -> new Circle(center, ranges.get(0));
            case "ANNULUS" -> new Annulus(center, ranges.get(0), ranges.get(1));
            case "CAPSULE" -> new Capsule(center, center.add(forward.scale(ranges.get(0))), ranges.get(1) / 2.0);
            case "SECTOR" -> new Sector(center, forward, ranges.get(0), Math.toRadians(ranges.get(1) / 2.0));
            case "RECTANGLE" -> new DirectionalRectangle(center, forward, ranges.get(0), ranges.get(1));
            default -> throw new IllegalArgumentException("Unexpected shape");
        };
    }

    private static PromisedConsortSkillConfigSnapshot.Skill skill(double speed, double scale, PromisedConsortActionId action) {
        Map<String, Double> numbers = new HashMap<>();
        numbers.put("range", 4.7); numbers.put("thrust_range", 5.1); numbers.put("sweep_range", 4.2);
        numbers.put("pull_radius", 12.0); numbers.put("impact_radius", 6.0); numbers.put("clone_radius", 3.5);
        numbers.put("forward_range", 5.0); numbers.put("width", 2.5); numbers.put("sword_range", 4.0);
        numbers.put("debris_range", 7.0); numbers.put("radius", 8.0); numbers.put("inner_radius", 3.0);
        numbers.put("outer_radius", action == PromisedConsortActionId.CONSORT_METEOR ? 13.0 : 11.0); numbers.put("core_radius", 9.0);
        numbers.put("leap_distance", 16.0); numbers.put("leap_height", 1.2);
        return new PromisedConsortSkillConfigSnapshot.Skill(true, 1.0, 100, true, speed, scale, numbers,
                Map.of("burst_tick", 16, "fissure_lifetime_ticks", 24, "tempest_hits", 2, "clone_count", 3, "afterglow_count", 8),
                Map.of(), Map.of(), Map.of(), Map.of());
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}