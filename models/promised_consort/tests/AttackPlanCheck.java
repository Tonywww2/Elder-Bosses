import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSkillConfigSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase;
import com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortAttackPlan;
import com.tonywww.elder_bosses.boss.promisedconsort.indicator.PromisedConsortIndicatorGenerator;
import com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortActionSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortAnimationTimeline;
import com.tonywww.elder_bosses.combat.action.ActionPhase;
import com.tonywww.elder_bosses.combat.action.ActionStage;
import com.tonywww.elder_bosses.combat.action.ActionTimeline;
import com.tonywww.elder_bosses.combat.geometry.*;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class AttackPlanCheck {
    private static int assertions;
    private static int strikes;

    public static void main(String[] arguments) throws Exception {
        Method projection = PromisedConsortIndicatorGenerator.class.getDeclaredMethod("shape", HorizontalShape.class, double.class);
        projection.setAccessible(true);
        Vec3 origin = new Vec3(10.25, 70, -24.5);
        Vec2 facing = new Vec2(0.6, 0.8);
        Map<String, Vec3> points = Map.of("target", origin.add(6, 0, 8), "meteor", origin.add(-6, 0, 9),
                "clone_meteor_0", origin.add(1, 0, 4), "clone_meteor_1", origin.add(4, 0, 2),
                "clone_meteor_2", origin.add(-1, 0, 3), "clone_meteor_3", origin.add(-4, 0, 2));
        for (double speed : new double[]{1.0, 1.35, 1.5, 2.0}) {
            for (double scale : new double[]{0.5, 1.0, 1.8}) {
                for (PromisedConsortActionId action : PromisedConsortActionId.values()) {
                    for (PromisedConsortPhase phase : PromisedConsortPhase.values()) {
                    var skill = skill(speed, scale);
                    int[] durations = PromisedConsortAnimationTimeline.durations(action);
                    ActionStage[] stages = new ActionStage[durations.length / 3];
                    for (int index = 0; index < stages.length; index++) {
                        stages[index] = new ActionStage(skill.tuning().scaleTicks(durations[index * 3]),
                                skill.tuning().scaleTicks(durations[index * 3 + 1]), skill.tuning().scaleTicks(durations[index * 3 + 2]));
                    }
                    var snapshot = new PromisedConsortActionSnapshot(action, phase, 1, 100, 0, ActionPhase.WINDUP, 0, 0, 42, null);
                    List<PromisedConsortAttackPlan.Strike> plan = PromisedConsortAttackPlan.create(snapshot, skill, ActionTimeline.ofStages(stages), origin, facing, points, 6);
                    require(!plan.isEmpty(), "Missing telegraph for " + action);
                    require(plan.stream().map(PromisedConsortAttackPlan.Strike::id).distinct().count() == plan.size(), "Duplicate attack occurrence " + action);
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
                    if (action == PromisedConsortActionId.LION_CLAW || action == PromisedConsortActionId.GRAVITY_DIVE) {
                        Circle landing = (Circle) find(plan, action == PromisedConsortActionId.LION_CLAW ? "slam" : "sword").shape();
                        require(landing.center().subtract(new Vec2(origin.x, origin.z)).length() <= 1.25001, "Landing marker differs from controlled movement");
                    }
                    if (action == PromisedConsortActionId.RING_OF_LIGHT) {
                        Annulus last = (Annulus) plan.get(plan.size() - 1).shape();
                        require(Math.abs(last.outerRadius() - 11 * scale) < 0.0001, "Wrong final ring range");
                        require(plan.size() == stages[0].activeTicks(), "Ring must have each actual damage band");
                    }
                        if (action == PromisedConsortActionId.SPIRAL_ASSAULT) {
                        double step = Math.min(1.25, skill.tuning().scaleRange(Math.min(1.25, skill.number("range") / stages[0].activeTicks())));
                        Vec2 expected = new Vec2(origin.x, origin.z).add(facing.scale(step * stages[0].activeTicks()));
                        Circle landing = (Circle) find(plan, "slam").shape();
                        require(landing.center().subtract(expected).length() < 0.0001, "Spiral prediction must not count windup as movement");
                        Capsule opening = (Capsule) find(plan, "spin").shape();
                        require(opening.start().subtract(new Vec2(origin.x, origin.z).add(facing.scale(step))).length() < 0.0001,
                            "Spiral opening must include the movement before its first hit");
                        int sampledTick = Math.max(0, stages[0].windupTicks() - 6);
                        var beforeImpact = new PromisedConsortActionSnapshot(action, snapshot.phase(), snapshot.sequence(), snapshot.startGameTick(),
                            sampledTick, ActionPhase.WINDUP, 0, sampledTick, snapshot.seed(), null);
                        var laterPlan = PromisedConsortAttackPlan.create(beforeImpact, skill, ActionTimeline.ofStages(stages), origin, facing, points, 6);
                        require(((Circle) find(laterPlan, "slam").shape()).center().subtract(expected).length() < 0.0001,
                            "Spiral endpoint must remain stable during windup");
                        }
                    if (action == PromisedConsortActionId.CONSORT_METEOR) {
                        Circle core = (Circle) find(plan, "core").shape();
                        Annulus outer = (Annulus) find(plan, "outer").shape();
                        require(core.radius() == outer.innerRadius(), "Meteor bands disagree");
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
        System.out.println("Attack plan passed: " + assertions + " checks; " + strikes + " strikes; 22 actions, 2 phases, 4 speeds, 3 range scales");
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

    private static PromisedConsortSkillConfigSnapshot.Skill skill(double speed, double scale) {
        Map<String, Double> numbers = new HashMap<>();
        numbers.put("range", 4.7); numbers.put("thrust_range", 5.1); numbers.put("sweep_range", 4.2);
        numbers.put("pull_radius", 12.0); numbers.put("impact_radius", 6.0); numbers.put("clone_radius", 3.5);
        numbers.put("forward_range", 5.0); numbers.put("width", 2.5); numbers.put("sword_range", 4.0);
        numbers.put("debris_range", 7.0); numbers.put("radius", 8.0); numbers.put("inner_radius", 3.0);
        numbers.put("outer_radius", 11.0); numbers.put("core_radius", 9.0);
        return new PromisedConsortSkillConfigSnapshot.Skill(true, 1.0, 100, true, speed, scale, numbers,
                Map.of("burst_tick", 16, "fissure_lifetime_ticks", 24, "tempest_hits", 2, "clone_count", 3, "afterglow_count", 8),
                Map.of(), Map.of(), Map.of(), Map.of());
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}