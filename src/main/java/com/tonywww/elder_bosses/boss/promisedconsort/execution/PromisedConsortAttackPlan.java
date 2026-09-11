package com.tonywww.elder_bosses.boss.promisedconsort.execution;

import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSkillConfigSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase;
import com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortActionSnapshot;
import com.tonywww.elder_bosses.combat.action.ActionStage;
import com.tonywww.elder_bosses.combat.action.ActionTimeline;
import com.tonywww.elder_bosses.combat.geometry.Annulus;
import com.tonywww.elder_bosses.combat.geometry.Capsule;
import com.tonywww.elder_bosses.combat.geometry.Circle;
import com.tonywww.elder_bosses.combat.geometry.DirectionalRectangle;
import com.tonywww.elder_bosses.combat.geometry.HorizontalShape;
import com.tonywww.elder_bosses.combat.geometry.Sector;
import com.tonywww.elder_bosses.combat.geometry.Vec2;
import com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.StyleRole;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PromisedConsortAttackPlan {
    private final PromisedConsortActionSnapshot action;
    private final PromisedConsortSkillConfigSnapshot.Skill skill;
    private final Vec3 position;
    private final Vec2 facing;
    private final Map<String, Vec3> points;
    private final int lead;
    private final List<Strike> strikes = new ArrayList<>();

    private PromisedConsortAttackPlan(PromisedConsortActionSnapshot action,
                                     PromisedConsortSkillConfigSnapshot.Skill skill,
                                     Vec3 position, Vec2 facing, Map<String, Vec3> points, int lead) {
        this.action = action;
        this.skill = skill;
        this.position = position;
        this.facing = facing.normalizedOr(new Vec2(0, 1));
        this.points = points;
        this.lead = Math.max(1, lead);
    }

    public static List<Strike> create(PromisedConsortActionSnapshot action, PromisedConsortSkillConfigSnapshot.Skill skill,
                                      ActionTimeline timeline, Vec3 position, Vec2 facing,
                                      Map<String, Vec3> points, int lead) {
        var plan = new PromisedConsortAttackPlan(action, skill, position, facing, points, lead);
        int start = 0;
        for (int index = 0; index < timeline.stages().size(); index++) {
            ActionStage stage = timeline.stages().get(index);
            plan.stage(index, start, stage);
            start += stage.totalTicks();
        }
        return List.copyOf(plan.strikes);
    }

    private void stage(int index, int start, ActionStage stage) {
        int hit = start + stage.windupTicks(), active = stage.activeTicks();
        Vec3 target = points.getOrDefault("target", position);
        switch (action.actionId()) {
            case GRAVITY_DIVE -> {
                Vec3 landing = points.getOrDefault("attack_origin:sword", lunge(target, number("range")));
                add("sword", circle(landing, number("range") * 0.65), landing.y, start, hit, hit + 1, StyleRole.PHYSICAL_GOLD, true);
                add("impact", circle(landing, number("range")), landing.y, start, hit + ticks(2), hit + ticks(2) + 1, StyleRole.GRAVITY_PURPLE, false);
            }
            case L_COMBO_CROSS, R_COMBO_CROSS, R_COMBO_LEFT_TWIN -> {
                String prefix = switch (action.actionId()) {
                    case L_COMBO_CROSS -> "cross_";
                    case R_COMBO_CROSS -> "right_cross_";
                    default -> "left_twin_";
                };
                add(prefix + index, sector(position, number("range"), action.actionId() == PromisedConsortActionId.R_COMBO_CROSS ? 140 : 120), start, hit, StyleRole.PHYSICAL_GOLD, true);
            }
            case L_COMBO_BLOODFLAME -> {
                if (index == 0) add("thrust", capsule(position, facing, number("thrust_range"), 1.2, false), start, hit, StyleRole.PHYSICAL_GOLD, true);
                else {
                    add("sweep", sector(position, number("sweep_range"), 120), start, hit, StyleRole.PHYSICAL_GOLD, true);
                    add("bloodflame", rectangle(position, number("sweep_range"), 1.5), position.y, start,
                            hit + ticks(skill.integer("burst_tick")), hit + Math.max(ticks(skill.integer("burst_tick")) + 1, skill.integer("fissure_lifetime_ticks")), StyleRole.BLOODFLAME_RED, false);
                }
            }
            case R_COMBO_TEMPEST -> {
                if (index < 3) add("opening_" + index, sector(position, number("range"), 120), start, hit, StyleRole.PHYSICAL_GOLD, true);
                else {
                    int count = Math.min(active, Math.max(1, skill.integer("tempest_hits")));
                    for (int strike = 0; strike < count; strike++) add("tempest_" + strike, annulus(position, 0.9, number("range")), start, hit + strike * active / count, StyleRole.PHYSICAL_GOLD, true);
                }
            }
            case R_COMBO_EARTHHEAVE -> {
                if (index < 3) add("opening_" + index, sector(position, 4.2, 120), start, hit, StyleRole.PHYSICAL_GOLD, true);
                else if (index == 3) add("slam", circle(position, 4), start, hit, StyleRole.PHYSICAL_GOLD, true);
                else add("fissure", rectangle(position, number("range"), 5), start, hit, StyleRole.PHYSICAL_GOLD, false);
            }
            case LION_CLAW, LION_CLAW_DOUBLE -> {
                Vec3 landing = lunge(target, 6);
                add(action.actionId() == PromisedConsortActionId.LION_CLAW ? "slam" : "double", circle(landing, number("range")), landing.y, start, hit, hit + 1, StyleRole.PHYSICAL_GOLD, true);
            }
            case STARCALLER_CRY -> {
                add("pull", circle(position, number("pull_radius")), position.y, start, hit, hit + active, StyleRole.MOVEMENT_DASHED, false);
                add("impact", circle(position, number("impact_radius")), start, hit + active - 1, StyleRole.PHYSICAL_GOLD, false);
                add("spikes", annulus(position, 1.5, number("impact_radius") + 2), start, hit + active - 1, StyleRole.GRAVITY_PURPLE, false);
                if (action.phase() == PromisedConsortPhase.PHASE_TWO) {
                    add("clone_cross_0", capsule(position, facing, number("impact_radius") * 2, 1.6, true), start, hit + active - 1, StyleRole.CLONE_GOLD, false);
                    add("clone_cross_1", capsule(position, new Vec2(-facing.z(), facing.x()), number("impact_radius") * 2, 1.6, true), start, hit + active - 1, StyleRole.CLONE_GOLD, false);
                }
            }
            case GRAVITY_METEOR -> {
                double flightDistance = Math.hypot(target.x - position.x, target.z - position.z);
                add("rock_flight", capsule(position, facing, Math.max(1, flightDistance / skill.rangeMultiplier()), 0.8, false),
                        position.y, start, hit, hit + active, StyleRole.MOVEMENT_DASHED, false);
                if (action.phase() == PromisedConsortPhase.PHASE_TWO) {
                    for (int clone = 0; clone < 4; clone++) {
                        Vec3 point = points.get("clone_meteor_" + clone);
                        if (point != null && clone * ticks(5) <= ticks(15) && clone * ticks(5) < stage.recoveryTicks()) add("clone_meteor_" + clone, circle(point, number("clone_radius")), point.y,
                                Math.max(start, hit + active + clone * ticks(5) - ticks(5)), hit + active + clone * ticks(5), hit + active + clone * ticks(5) + 1, StyleRole.CLONE_GOLD, false);
                    }
                }
            }
            case STOMP -> add("stomp", rectangle(position, number("forward_range"), number("width")), start, hit, StyleRole.PHYSICAL_GOLD, false);
            case CROSS_SLASH -> {
                add("sword", sector(position, number("sword_range"), 140), start, hit, StyleRole.PHYSICAL_GOLD, true);
                add("debris", capsule(position, facing, number("debris_range"), 2, false), start, hit + ticks(2), StyleRole.PHYSICAL_GOLD, false);
            }
            case SPIRAL_ASSAULT -> {
                Vec3 opening = movingOrigin(hit, hit, active, true, false);
                add("spin", capsule(opening, facing, number("range"), number("width"), false), opening.y,
                    start, hit, hit + 1, StyleRole.PHYSICAL_GOLD, true);
                Vec3 landing = movingOrigin(hit + active - 1, hit, active, true, false);
                add("slam", circle(landing, 3.5), landing.y, start, hit + active - 1, hit + active, StyleRole.PHYSICAL_GOLD, true);
            }
            case LIGHT_OF_MIQUELLA -> {
                add("main", circle(target, number("radius")), target.y, start, hit, hit + active, StyleRole.HOLY_IVORY, false);
                for (int area = 0; area < skill.integer("afterglow_count"); area++) {
                    double angle = randomUnit(action.seed(), area) * Math.PI * 2;
                    double distance = randomUnit(action.seed() ^ 0x9E3779B97F4A7C15L, area) * range(number("radius"));
                    Vec3 point = target.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
                    int onset = hit + active + ticks(area + 4);
                    add("afterglow_" + area, circle(point, 1), point.y, start, onset, onset + 1, StyleRole.HOLY_IVORY, false);
                }
            }
            case RING_OF_LIGHT -> {
                for (int step = 0; step < active; step++) {
                    double outer = number("inner_radius") + (number("outer_radius") - number("inner_radius")) * (step + 1.0) / active;
                    add("ring_" + step, annulus(position, Math.max(0, outer - 1.5), outer), start, hit + step, StyleRole.HOLY_IVORY, false);
                }
            }
            case LIGHTSPEED_SLASH, LIGHTSPEED_SIDE_DASH, LIGHTSPEED_DASH -> {
                boolean dash = action.actionId() == PromisedConsortActionId.LIGHTSPEED_DASH;
                boolean side = action.actionId() == PromisedConsortActionId.LIGHTSPEED_SIDE_DASH;
                int count = dash ? Math.max(1, (Math.min(active, ticks(16)) + ticks(4) - 1) / ticks(4)) : skill.integer("clone_count");
                int interval = dash ? ticks(4) : Math.max(1, active / (count + 1));
                for (int clone = 0; clone < count && clone * interval < active; clone++) {
                    Vec3 origin = movingOrigin(hit + clone * interval, hit, active, dash, side);
                    add("clone_" + clone, capsule(origin, facing, dash ? number("range") : side ? 9 : 10, dash ? number("width") : 1.6, false), origin.y, start, hit + clone * interval, hit + clone * interval + 1, StyleRole.CLONE_GOLD, false);
                }
                Vec3 origin = movingOrigin(hit + active - 1, hit, active, dash, side);
                add("body", side ? sector(origin, 4, 140) : capsule(origin, facing, dash ? number("range") : 8, dash ? number("width") : 2, false), origin.y, start, hit + active - 1, hit + active, StyleRole.PHYSICAL_GOLD, true);
                if (dash) add("trail", rectangle(origin, number("range"), number("width")), origin.y, start, hit + active - 1 + ticks(2), hit + active - 1 + Math.max(ticks(2) + 1, ticks(3)), StyleRole.HOLY_IVORY, false);
            }
            case PROMISED_CONSORT -> {
                for (int offset : new int[]{0, 8}) add("opening_" + ticks(offset), sector(position, 4.2, 140), start, hit + ticks(offset), StyleRole.PHYSICAL_GOLD, true);
                for (int offset : new int[]{18, 28}) add("spin_" + ticks(offset), annulus(position, 0.9, 4.5), start, hit + ticks(offset), StyleRole.PHYSICAL_GOLD, true);
                add("finisher", circle(position, 5), start, hit + ticks(42), StyleRole.PHYSICAL_GOLD, true);
                add("clone_return_0", capsule(position, rotate(45), 10, 1.6, true), start, hit + ticks(44), StyleRole.CLONE_GOLD, false);
                add("clone_return_1", capsule(position, rotate(-45), 10, 1.6, true), start, hit + ticks(47), StyleRole.CLONE_GOLD, false);
                add("holy_ring", annulus(position, 2, 7), start, hit + ticks(50), StyleRole.HOLY_IVORY, false);
            }
            case ENHANCED_EARTHHEAVE -> {
                add("slam", circle(position, number("radius")), start, hit, StyleRole.PHYSICAL_GOLD, true);
                add("fissure", rectangle(position, number("radius") + 2, 5), start, hit, StyleRole.PHYSICAL_GOLD, false);
                for (int area = 0; area < 4; area++) add("light_" + area, rectangle(position, number("radius") + 2 + area * 1.5, 0.8), position.y,
                        start, hit + ticks(3 + area * 2), hit + Math.max(ticks(3 + area * 2) + 1, ticks(4 + area * 2)), StyleRole.HOLY_IVORY, false);
            }
            case CONSORT_METEOR -> {
                Vec3 point = points.get("meteor");
                if (point == null) return;
                int impact = ticks(121), warning = ticks(91);
                add("core", circle(point, number("core_radius")), point.y, warning, impact, impact + 1, StyleRole.PHYSICAL_GOLD, false);
                add("outer", annulus(point, number("core_radius"), number("outer_radius")), point.y, warning, impact, impact + 1, StyleRole.PHYSICAL_GOLD, false);
                add("aftershock", annulus(point, number("outer_radius"), number("outer_radius") + 2), point.y, warning,
                        impact + ticks(2), impact + Math.max(ticks(2) + 1, ticks(3)), StyleRole.HOLY_IVORY, false);
            }
        }
    }

    private Vec3 movingOrigin(int event, int activeStart, int activeTicks, boolean dash, boolean side) {
        int steps = Math.max(0, event - Math.max(action.actionTick(), activeStart) + 1);
        double distance = (dash ? travel(number("range"), activeTicks) : side ? Math.min(1.25, range(6.0 / activeTicks)) : 0) * steps;
        return position.add((side ? -facing.z() : facing.x()) * distance, 0, (side ? facing.x() : facing.z()) * distance);
    }

    private double travel(double distance, int activeTicks) {
        return Math.min(1.25, range(Math.min(1.25, distance / activeTicks)));
    }

    private Vec3 lunge(Vec3 target, double maximum) {
        Vec3 offset = target.subtract(position).multiply(1, 0, 1);
        return offset.lengthSqr() < 1.0E-8 ? position : position.add(offset.normalize().scale(Math.min(offset.length(), Math.min(1.25, range(maximum)))));
    }

    private void add(String id, HorizontalShape shape, int start, int hit, StyleRole style, boolean guard) {
        add(id, shape, position.y, start, hit, hit + 1, style, guard);
    }

    private void add(String id, HorizontalShape shape, double height, int start, int hit, int end, StyleRole style, boolean guard) {
        long origin = action.startGameTick();
        strikes.add(new Strike(id, shape, height, origin + start, origin + Math.max(start, hit - lead), origin + hit, origin + end, style, guard));
    }

    private double number(String key) { return skill.number(key); }
    private double range(double value) { return skill.tuning().scaleRange(value); }
    private int ticks(int value) { return skill.tuning().scaleTicks(value); }
    private HorizontalShape circle(Vec3 center, double radius) { return new Circle(center.x, center.z, range(radius)); }
    private HorizontalShape annulus(Vec3 center, double inner, double outer) { return new Annulus(new Vec2(center.x, center.z), range(inner), range(outer)); }
    private HorizontalShape sector(Vec3 center, double radius, double angle) { return new Sector(new Vec2(center.x, center.z), facing, range(radius), Math.toRadians(angle / 2)); }
    private HorizontalShape rectangle(Vec3 center, double length, double width) { return new DirectionalRectangle(new Vec2(center.x, center.z), facing, range(length), range(width)); }
    private HorizontalShape capsule(Vec3 center, Vec2 direction, double length, double width, boolean centered) {
        Vec2 start = new Vec2(center.x, center.z);
        Vec2 offset = direction.normalizedOr(facing).scale(range(length));
        return new Capsule(centered ? start.subtract(offset.scale(0.5)) : start, centered ? start.add(offset.scale(0.5)) : start.add(offset), range(width) / 2);
    }
    private Vec2 rotate(double degrees) {
        double angle = Math.toRadians(degrees);
        return new Vec2(facing.x() * Math.cos(angle) - facing.z() * Math.sin(angle), facing.x() * Math.sin(angle) + facing.z() * Math.cos(angle));
    }
    private static double randomUnit(long seed, int index) {
        long value = seed + 0x9E3779B97F4A7C15L * (index + 1L);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return ((value ^ (value >>> 31)) >>> 11) * 0x1.0p-53;
    }

    public static Strike relocate(Strike strike, Vec3 origin, Vec2 direction) {
        HorizontalShape shape = strike.shape();
        Vec2 center = new Vec2(origin.x, origin.z);
        if (shape instanceof Circle circle) shape = new Circle(center, circle.radius());
        else if (shape instanceof Annulus annulus) shape = new Annulus(center, annulus.innerRadius(), annulus.outerRadius());
        else if (shape instanceof Capsule capsule) shape = new Capsule(center,
                center.add(direction.scale(capsule.end().subtract(capsule.start()).length())), capsule.radius());
        else if (shape instanceof Sector sector) shape = new Sector(center, direction, sector.radius(), sector.halfAngleRadians());
        else if (shape instanceof DirectionalRectangle rectangle) shape = new DirectionalRectangle(center, direction, rectangle.length(), rectangle.width());
        return strike.withShape(shape, origin.y);
    }

    public record Strike(String id, HorizontalShape shape, double baseY, long startTick, long lockTick,
                         long activeTick, long endTick, StyleRole style, boolean instantGuard) {
        public Strike withShape(HorizontalShape shape, double baseY) {
            return new Strike(id, shape, baseY, startTick, lockTick, activeTick, endTick, style, instantGuard);
        }

        public Strike observedAt(long tick) {
            long start = Math.min(activeTick, Math.max(startTick, tick));
            return new Strike(id, shape, baseY, start, Math.max(start, lockTick), activeTick, endTick, style, instantGuard);
        }
    }
}