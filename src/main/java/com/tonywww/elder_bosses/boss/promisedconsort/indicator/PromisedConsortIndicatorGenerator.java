package com.tonywww.elder_bosses.boss.promisedconsort.indicator;

import com.tonywww.elder_bosses.boss.promisedconsort.action.PromisedConsortActionCatalog;
import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortCombatConfigSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSkillConfigSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionExecutor.HazardSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortAttackPlan;
import com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortInstantGuardRules;
import com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortActionSnapshot;
import com.tonywww.elder_bosses.combat.action.ActionPhase;
import com.tonywww.elder_bosses.combat.action.ActionStage;
import com.tonywww.elder_bosses.combat.action.SkillTuning;
import com.tonywww.elder_bosses.combat.geometry.Annulus;
import com.tonywww.elder_bosses.combat.geometry.Capsule;
import com.tonywww.elder_bosses.combat.geometry.Circle;
import com.tonywww.elder_bosses.combat.geometry.DirectionalRectangle;
import com.tonywww.elder_bosses.combat.geometry.HorizontalShape;
import com.tonywww.elder_bosses.combat.geometry.Sector;
import com.tonywww.elder_bosses.combat.geometry.Vec2;
import com.tonywww.elder_bosses.network.IndicatorSnapshotPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PromisedConsortIndicatorGenerator {
    private final PromisedConsortActionCatalog catalog;
    private final PromisedConsortCombatConfigSnapshot config;

    public PromisedConsortIndicatorGenerator(
            PromisedConsortActionCatalog catalog,
            PromisedConsortCombatConfigSnapshot config
    ) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.config = Objects.requireNonNull(config, "config");
    }

    public List<IndicatorSnapshotPacket> create(
            int bossEntityId,
            Vec3 bossPosition,
            float bossYaw,
            PromisedConsortActionSnapshot action,
            Map<String, Vec3> lockedPoints,
            Vec2 lockedFacing,
            List<HazardSnapshot> hazards,
            long gameTick
    ) {
        Vec2 facing = lockedFacing == null ? new Vec2(-Math.sin(Math.toRadians(bossYaw)), Math.cos(Math.toRadians(bossYaw))) : lockedFacing;
        List<PromisedConsortAttackPlan.Strike> strikes = action == null ? List.of()
                : PromisedConsortAttackPlan.create(action, catalog.skillConfig().get(action.actionId()),
                catalog.get(action.actionId()).timeline(), bossPosition, facing, lockedPoints, config.instantGuard().defaultCueLeadTicks());
        return createAuthoritative(bossEntityId, action, strikes, hazards, gameTick);
    }

    public List<IndicatorSnapshotPacket> createAuthoritative(int bossEntityId, PromisedConsortActionSnapshot action,
            List<PromisedConsortAttackPlan.Strike> strikes, List<HazardSnapshot> hazards, long gameTick) {
        List<IndicatorSnapshotPacket> packets = new ArrayList<>();
        long next = strikes.stream().filter(strike -> strike.endTick() > gameTick)
                .mapToLong(PromisedConsortAttackPlan.Strike::activeTick).min().orElse(Long.MAX_VALUE);
        if (action != null) {
            for (var strike : strikes) {
                if (strike.startTick() > gameTick || strike.endTick() <= gameTick || strike.startTick() >= strike.activeTick()) continue;
                if (hazards.stream().anyMatch(hazard -> hazard.id().equals(action.sequence() + ":" + strike.id()))) continue;
                Shape shape = shape(strike.shape(), strike.baseY());
                Timing timing = new Timing(strike.startTick(), strike.lockTick(), strike.activeTick(), strike.endTick());
                packets.add(packet(bossEntityId, "hazard:" + action.sequence() + ":" + strike.id(),
                    strike.activeTick() <= Math.max(gameTick, next) ? IndicatorSnapshotPacket.SegmentSlot.CURRENT : IndicatorSnapshotPacket.SegmentSlot.NEXT,
                        strike.style(), semantic(strike.style()), state(action, timing), shape, shape.yawDegrees(), timing,
                        strike.instantGuard() && strike.activeTick() - strike.startTick() >= config.instantGuard().defaultCueLeadTicks()));
            }
        }
        for (var hazard : hazards) packets.add(hazardPacket(bossEntityId, hazard, gameTick));
        return List.copyOf(packets);
    }

        public List<IndicatorSnapshotPacket> createTransitionImpact(int bossEntityId, AABB bounds,
            double groundY, long startTick, long activeTick, long gameTick) {
        if (gameTick < startTick || gameTick >= activeTick + 1 || startTick >= activeTick) return List.of();
        Shape shape = shape(new DirectionalRectangle(
            new Vec2((bounds.minX + bounds.maxX) / 2.0, bounds.minZ), new Vec2(0.0, 1.0),
            bounds.maxZ - bounds.minZ, bounds.maxX - bounds.minX), groundY);
        var state = gameTick >= activeTick ? IndicatorSnapshotPacket.IndicatorState.ACTIVE
            : activeTick - gameTick <= 4 ? IndicatorSnapshotPacket.IndicatorState.IMMINENT
            : IndicatorSnapshotPacket.IndicatorState.LOCKED;
        return List.of(packet(bossEntityId, "transition:" + startTick,
            IndicatorSnapshotPacket.SegmentSlot.CURRENT, IndicatorSnapshotPacket.StyleRole.HOLY_IVORY,
            IndicatorSnapshotPacket.Semantic.HOLY, state, shape, shape.yawDegrees(),
            new Timing(startTick, startTick, activeTick, activeTick + 1), false));
        }

    private PromisedConsortActionSnapshot indicatorAction(
            PromisedConsortActionSnapshot action
    ) {
        if (action == null || action.actionPhase() != ActionPhase.RECOVERY) {
            return action;
        }
        if (action.actionId() == PromisedConsortActionId.GRAVITY_METEOR
                && action.phase() == com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase.PHASE_TWO
            && action.phaseTick() <= ticks(
                catalog.skillConfig().get(action.actionId()),
                15
            )) {
            return action;
        }
        int nextStage = action.stageIndex() + 1;
        if (nextStage >= catalog.get(action.actionId()).timeline().stages().size()) {
            return null;
        }
        return new PromisedConsortActionSnapshot(
                action.actionId(),
                action.phase(),
                action.sequence(),
                action.startGameTick(),
                action.actionTick(),
                ActionPhase.WINDUP,
                nextStage,
                0,
                action.seed(),
                action.targetId()
        );
    }

    private void appendActionPackets(
            List<IndicatorSnapshotPacket> packets,
            int bossEntityId,
            Vec3 bossPosition,
            float bossYaw,
            PromisedConsortActionSnapshot action,
            Map<String, Vec3> lockedPoints,
            Vec2 lockedFacing
    ) {
        PromisedConsortSkillConfigSnapshot.Skill skill = catalog.skillConfig().get(action.actionId());
        Timing timing = timing(action, skill);
        Vec3 target = lockedPoints.getOrDefault("target", bossPosition);
        float yaw = yawDegrees(lockedFacing, bossYaw);
        if (appendCompositeActionPackets(
                packets, bossEntityId, bossPosition, target, yaw, action, skill, timing,
                lockedPoints
        )) {
            return;
        }
        Shape shape = switch (action.actionId()) {
            case GRAVITY_DIVE -> circle(target, skill.number("range"));
            case L_COMBO_CROSS, R_COMBO_CROSS, R_COMBO_LEFT_TWIN ->
                    sector(bossPosition, skill.number("range"),
                            action.actionId() == PromisedConsortActionId.R_COMBO_CROSS ? 140.0 : 120.0);
            case L_COMBO_BLOODFLAME -> action.stageIndex() == 0
                    ? capsule(bossPosition, skill.number("thrust_range"), 1.2)
                    : sector(bossPosition, skill.number("sweep_range"), 120.0);
            case R_COMBO_TEMPEST -> action.stageIndex() == 3
                    ? annulus(bossPosition, 0.9, skill.number("range"))
                    : sector(bossPosition, skill.number("range"), 120.0);
            case R_COMBO_EARTHHEAVE -> action.stageIndex() == 4
                    ? rectangle(bossPosition, skill.number("range"), 5.0)
                    : action.stageIndex() == 3
                    ? circle(bossPosition, 4.0)
                    : sector(bossPosition, 4.2, 120.0);
            case LION_CLAW, LION_CLAW_DOUBLE -> circle(target, skill.number("range"));
            case STARCALLER_CRY -> circle(bossPosition, skill.number("pull_radius"));
            case GRAVITY_METEOR -> circle(target, 1.0);
            case STOMP -> rectangle(bossPosition, skill.number("forward_range"), skill.number("width"));
            case CROSS_SLASH -> sector(bossPosition, skill.number("sword_range"), 140.0);
            case SPIRAL_ASSAULT -> path(bossPosition, yaw, skill.number("range"), skill.number("width"));
            case LIGHT_OF_MIQUELLA -> circle(target, skill.number("radius"));
            case RING_OF_LIGHT -> annulus(bossPosition,
                    skill.number("inner_radius"), skill.number("outer_radius"));
            case LIGHTSPEED_SLASH -> path(bossPosition, yaw, 8.0, 1.6);
            case LIGHTSPEED_DASH -> path(bossPosition, yaw,
                    skill.number("range"), skill.number("width"));
                case LIGHTSPEED_SIDE_DASH, PROMISED_CONSORT, ENHANCED_EARTHHEAVE,
                    CONSORT_METEOR -> throw new IllegalStateException(
                    "composite action must be handled before the simple shape switch"
                );
        };
        IndicatorSnapshotPacket.StyleRole style = style(action.actionId());
        boolean instantGuard = PromisedConsortInstantGuardRules.eligible(
            action.actionId(),
            action.stageIndex()
        )
                && timing.activeTick() - timing.startTick()
                >= config.instantGuard().defaultCueLeadTicks();
        packets.add(packet(
                bossEntityId,
                action.sequence() + ":" + action.actionId().serializedName()
                        + ":" + action.stageIndex(),
                IndicatorSnapshotPacket.SegmentSlot.CURRENT,
                style,
                semantic(style),
                state(action, timing),
                tunedShape(shape, skill.tuning()),
                yaw,
                timing,
                instantGuard
        ));
    }

        private boolean appendCompositeActionPackets(
            List<IndicatorSnapshotPacket> packets,
            int bossEntityId,
            Vec3 bossPosition,
            Vec3 target,
            float yaw,
            PromisedConsortActionSnapshot action,
            PromisedConsortSkillConfigSnapshot.Skill skill,
            Timing stageTiming,
            Map<String, Vec3> lockedPoints
        ) {
        List<TimedShape> shapes = switch (action.actionId()) {
            case GRAVITY_METEOR -> {
            if (action.phase() != com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase.PHASE_TWO) {
                yield null;
            }
            int activeTicks = activeTicks(action);
            long recoveryStart = stageTiming.activeTick() + activeTicks;
            if (action.actionPhase() == ActionPhase.ACTIVE) {
                int leadStart = Math.max(0, activeTicks - ticks(skill, 5));
                Vec3 point = lockedPoints.get("clone_meteor_0");
                if (action.phaseTick() < leadStart || point == null) {
                    yield null;
                }
                appendShapePacket(packets, bossEntityId, action, "clone_meteor_0",
                    IndicatorSnapshotPacket.SegmentSlot.NEXT,
                    IndicatorSnapshotPacket.StyleRole.CLONE_GOLD,
                    circle(point, skill.number("clone_radius")), yaw,
                    new Timing(recoveryStart - 5L, recoveryStart - 5L,
                        recoveryStart, recoveryStart + 1L), false);
                yield List.of();
            }
            if (action.actionPhase() != ActionPhase.RECOVERY) {
                yield null;
            }
            int cloneInterval = ticks(skill, 5);
            int currentClone = Math.min(3, (action.phaseTick() + cloneInterval - 1) / cloneInterval);
            for (int clone = currentClone; clone <= Math.min(3, currentClone + 1); clone++) {
                Vec3 point = lockedPoints.get("clone_meteor_" + clone);
                if (point == null) {
                    continue;
                }
                long activeTick = recoveryStart + (long) clone * cloneInterval;
                long lockTick = activeTick - (clone == 0 ? 1L : cloneInterval);
                appendShapePacket(packets, bossEntityId, action, "clone_meteor_" + clone,
                    clone == currentClone
                        ? IndicatorSnapshotPacket.SegmentSlot.CURRENT
                        : IndicatorSnapshotPacket.SegmentSlot.NEXT,
                    IndicatorSnapshotPacket.StyleRole.CLONE_GOLD,
                    circle(point, skill.number("clone_radius")), yaw,
                    new Timing(lockTick, lockTick, activeTick, activeTick + 1L), false);
            }
            yield List.of();
            }
            case STARCALLER_CRY -> {
            long activeTick = stageTiming.activeTick()
                + activeTicks(action) - 1L;
            Timing impactTiming = new Timing(
                stageTiming.startTick(),
                activeTick,
                activeTick,
                activeTick + 1L
            );
            appendShapePacket(packets, bossEntityId, action, "pull",
                IndicatorSnapshotPacket.SegmentSlot.CURRENT,
                IndicatorSnapshotPacket.StyleRole.GRAVITY_PURPLE,
                circle(bossPosition, skill.number("pull_radius")), yaw,
                stageTiming, false);
            if (action.phase() == com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase.PHASE_TWO) {
                appendShapePacket(packets, bossEntityId, action, "clone_cross_0",
                    IndicatorSnapshotPacket.SegmentSlot.NEXT,
                    IndicatorSnapshotPacket.StyleRole.CLONE_GOLD,
                    capsule(bossPosition, skill.number("impact_radius") * 2.0, 1.6),
                    yaw, impactTiming, false);
                appendShapePacket(packets, bossEntityId, action, "clone_cross_1",
                    IndicatorSnapshotPacket.SegmentSlot.NEXT,
                    IndicatorSnapshotPacket.StyleRole.CLONE_GOLD,
                    capsule(bossPosition, skill.number("impact_radius") * 2.0, 1.6),
                    yaw + 90.0F, impactTiming, false);
            }
            appendShapePacket(packets, bossEntityId, action, "impact",
                IndicatorSnapshotPacket.SegmentSlot.NEXT,
                IndicatorSnapshotPacket.StyleRole.PHYSICAL_GOLD,
                circle(bossPosition, skill.number("impact_radius")), yaw,
                impactTiming, false);
            yield List.of();
            }
            case LIGHTSPEED_SLASH -> lightspeedSlashShapes(bossPosition, yaw, skill);
            case LIGHTSPEED_SIDE_DASH -> {
            packets.add(packet(
                bossEntityId,
                sequenceId(action, "movement"),
                IndicatorSnapshotPacket.SegmentSlot.CURRENT,
                IndicatorSnapshotPacket.StyleRole.MOVEMENT_DASHED,
                IndicatorSnapshotPacket.Semantic.MOVEMENT,
                state(action, stageTiming),
                        tunedShape(path(bossPosition, yaw + 90.0F, 6.0, 1.6), skill.tuning()),
                yaw + 90.0F,
                stageTiming,
                false
            ));
            yield lightspeedSideDashShapes(bossPosition, yaw, skill);
            }
            case PROMISED_CONSORT -> promisedConsortShapes(bossPosition, skill);
            case ENHANCED_EARTHHEAVE -> List.of(
                new TimedShape("slam", 0, circle(bossPosition, skill.number("radius")),
                    IndicatorSnapshotPacket.StyleRole.PHYSICAL_GOLD, true),
                new TimedShape("fissure", 0,
                    rectangle(bossPosition, skill.number("radius") + 2.0, 5.0),
                    IndicatorSnapshotPacket.StyleRole.PHYSICAL_GOLD, false)
            );
            case CONSORT_METEOR -> {
            Vec3 point = lockedPoints.getOrDefault("meteor", bossPosition);
            long start = action.startGameTick();
                Timing impact = new Timing(
                    start,
                    start + ticks(skill, 110),
                    start + ticks(skill, 121),
                    start + ticks(skill, 122)
                );
                Timing aftershock = new Timing(
                    start,
                    start + ticks(skill, 110),
                    start + ticks(skill, 123),
                    start + ticks(skill, 124)
                );
            appendShapePacket(packets, bossEntityId, action, "core",
                IndicatorSnapshotPacket.SegmentSlot.CURRENT,
                IndicatorSnapshotPacket.StyleRole.PHYSICAL_GOLD,
                circle(point, skill.number("core_radius")), yaw, impact, false);
            appendShapePacket(packets, bossEntityId, action, "outer",
                IndicatorSnapshotPacket.SegmentSlot.CURRENT,
                IndicatorSnapshotPacket.StyleRole.PHYSICAL_GOLD,
                annulus(point, skill.number("core_radius"), skill.number("outer_radius")),
                yaw, impact, false);
            appendShapePacket(packets, bossEntityId, action, "aftershock",
                IndicatorSnapshotPacket.SegmentSlot.NEXT,
                IndicatorSnapshotPacket.StyleRole.HOLY_IVORY,
                annulus(point, skill.number("outer_radius"),
                    skill.number("outer_radius") + 2.0),
                yaw, aftershock, false);
            yield List.of();
            }
            default -> null;
        };
        if (shapes == null) {
            return false;
        }
        appendTimedShapes(packets, bossEntityId, action, yaw, stageTiming, shapes);
        return true;
        }

        private static List<TimedShape> lightspeedSlashShapes(
            Vec3 bossPosition,
            float yaw,
            PromisedConsortSkillConfigSnapshot.Skill skill
        ) {
        int cloneCount = skill.integer("clone_count");
        int activeTicks = activeTicks(skill);
        int interval = Math.max(1, activeTicks / (cloneCount + 1));
        List<TimedShape> shapes = new ArrayList<>();
        for (int index = 0; index < cloneCount; index++) {
            shapes.add(new TimedShape("clone_" + index, index * interval,
                capsule(bossPosition, 10.0, 1.6),
                IndicatorSnapshotPacket.StyleRole.CLONE_GOLD, false));
        }
        shapes.add(new TimedShape("body", activeTicks - 1,
            capsule(bossPosition, 8.0, 2.0),
            IndicatorSnapshotPacket.StyleRole.PHYSICAL_GOLD, true));
        return List.copyOf(shapes);
        }

        private static List<TimedShape> lightspeedSideDashShapes(
            Vec3 bossPosition,
            float yaw,
            PromisedConsortSkillConfigSnapshot.Skill skill
        ) {
        int cloneCount = skill.integer("clone_count");
        int activeTicks = activeTicks(skill);
        int interval = Math.max(1, activeTicks / (cloneCount + 1));
        List<TimedShape> shapes = new ArrayList<>();
        for (int index = 0; index < cloneCount; index++) {
            shapes.add(new TimedShape("clone_" + index, index * interval,
                capsule(bossPosition, 9.0, 1.6),
                IndicatorSnapshotPacket.StyleRole.CLONE_GOLD, false));
        }
        shapes.add(new TimedShape("body", activeTicks - 1,
            sector(bossPosition, 4.0, 140.0),
            IndicatorSnapshotPacket.StyleRole.PHYSICAL_GOLD, true));
        return List.copyOf(shapes);
        }

        private static List<TimedShape> promisedConsortShapes(
            Vec3 bossPosition,
            PromisedConsortSkillConfigSnapshot.Skill skill
        ) {
        return List.of(
            new TimedShape("opening_0", 0, sector(bossPosition, 4.2, 140.0),
                IndicatorSnapshotPacket.StyleRole.PHYSICAL_GOLD, true),
            new TimedShape("opening_8", ticks(skill, 8), sector(bossPosition, 4.2, 140.0),
                IndicatorSnapshotPacket.StyleRole.PHYSICAL_GOLD, true),
            new TimedShape("spin_18", ticks(skill, 18), annulus(bossPosition, 0.9, 4.5),
                IndicatorSnapshotPacket.StyleRole.PHYSICAL_GOLD, true),
            new TimedShape("spin_28", ticks(skill, 28), annulus(bossPosition, 0.9, 4.5),
                IndicatorSnapshotPacket.StyleRole.PHYSICAL_GOLD, true),
                new TimedShape("finisher", ticks(skill, 42), circle(bossPosition, 5.0),
                IndicatorSnapshotPacket.StyleRole.PHYSICAL_GOLD, true),
            new TimedShape("clone_return_0", ticks(skill, 44), capsule(bossPosition, 10.0, 1.6),
                IndicatorSnapshotPacket.StyleRole.CLONE_GOLD, false, 45.0F),
            new TimedShape("clone_return_1", ticks(skill, 47), capsule(bossPosition, 10.0, 1.6),
                IndicatorSnapshotPacket.StyleRole.CLONE_GOLD, false, -45.0F),
            new TimedShape("holy_ring", ticks(skill, 50), annulus(bossPosition, 2.0, 7.0),
                IndicatorSnapshotPacket.StyleRole.HOLY_IVORY, false)
        );
        }

        private void appendTimedShapes(
            List<IndicatorSnapshotPacket> packets,
            int bossEntityId,
            PromisedConsortActionSnapshot action,
            float yaw,
            Timing stageTiming,
            List<TimedShape> shapes
        ) {
        int elapsed = action.actionPhase() == ActionPhase.WINDUP ? -1 : action.phaseTick();
        int currentOffset = shapes.stream()
            .mapToInt(TimedShape::activeOffset)
            .filter(offset -> offset >= elapsed)
            .min()
            .orElse(Integer.MAX_VALUE);
        int nextOffset = shapes.stream()
            .mapToInt(TimedShape::activeOffset)
            .filter(offset -> offset > currentOffset)
            .min()
            .orElse(Integer.MAX_VALUE);
        for (TimedShape shape : shapes) {
            IndicatorSnapshotPacket.SegmentSlot slot;
            if (shape.activeOffset() == currentOffset) {
            slot = IndicatorSnapshotPacket.SegmentSlot.CURRENT;
            } else if (shape.activeOffset() == nextOffset) {
            slot = IndicatorSnapshotPacket.SegmentSlot.NEXT;
            } else {
            continue;
            }
            long activeTick = stageTiming.activeTick() + shape.activeOffset();
            Timing timing = new Timing(
                stageTiming.startTick(),
                activeTick,
                activeTick,
                activeTick + 1
            );
            appendShapePacket(packets, bossEntityId, action, shape.id(), slot, shape.style(),
                    shape.shape(), yaw + shape.yawOffset(), timing, shape.instantGuard());
        }
        }

        private void appendShapePacket(
            List<IndicatorSnapshotPacket> packets,
            int bossEntityId,
            PromisedConsortActionSnapshot action,
            String suffix,
            IndicatorSnapshotPacket.SegmentSlot slot,
            IndicatorSnapshotPacket.StyleRole style,
            Shape shape,
            float yaw,
            Timing timing,
            boolean instantGuard
        ) {
        packets.add(packet(
            bossEntityId,
            sequenceId(action, suffix),
            slot,
            style,
            semantic(style),
            state(action, timing),
            tunedShape(shape, catalog.skillConfig().get(action.actionId()).tuning()),
            yaw,
            timing,
            instantGuard
        ));
        }

        private static String sequenceId(PromisedConsortActionSnapshot action, String suffix) {
        return action.sequence() + ":" + action.actionId().serializedName() + ":" + suffix;
        }

    private IndicatorSnapshotPacket hazardPacket(
            int bossEntityId,
            HazardSnapshot hazard,
            long gameTick
    ) {
        Shape shape = shape(hazard.shape(), hazard.minimumY());
        IndicatorSnapshotPacket.StyleRole style = switch (hazard.hit().damageKind()) {
            case PHYSICAL -> IndicatorSnapshotPacket.StyleRole.PHYSICAL_GOLD;
            case MAGIC -> IndicatorSnapshotPacket.StyleRole.GRAVITY_PURPLE;
            case HOLY -> IndicatorSnapshotPacket.StyleRole.HOLY_IVORY;
            case FIRE -> IndicatorSnapshotPacket.StyleRole.BLOODFLAME_RED;
        };
        IndicatorSnapshotPacket.IndicatorState state = gameTick >= hazard.activeTick()
                ? IndicatorSnapshotPacket.IndicatorState.PERSISTENT
                : hazard.activeTick() - gameTick <= 4
                ? IndicatorSnapshotPacket.IndicatorState.IMMINENT
                : IndicatorSnapshotPacket.IndicatorState.LOCKED;
        return packet(
                bossEntityId,
                "hazard:" + hazard.id(),
                IndicatorSnapshotPacket.SegmentSlot.CURRENT,
                style,
                semantic(style),
                state,
                shape,
                shape.yawDegrees(),
                new Timing(hazard.startTick(), hazard.startTick(), hazard.activeTick(), hazard.endTick()),
                false
        );
    }

    private Timing timing(
            PromisedConsortActionSnapshot action,
            PromisedConsortSkillConfigSnapshot.Skill skill
    ) {
        if (action.actionId() == PromisedConsortActionId.CONSORT_METEOR) {
            long start = action.startGameTick();
            return new Timing(
                start,
                start + ticks(skill, 91),
                start + ticks(skill, 121),
                start + catalog.get(action.actionId()).timeline().totalTicks()
            );
        }
        List<ActionStage> stages = catalog.get(action.actionId()).timeline().stages();
        int stageStart = 0;
        for (int index = 0; index < action.stageIndex(); index++) {
            stageStart += stages.get(index).totalTicks();
        }
        ActionStage stage = stages.get(action.stageIndex());
        long start = action.startGameTick() + stageStart;
        long active = start + stage.windupTicks();
        return new Timing(start, active, active, active + stage.activeTicks());
    }

    private static IndicatorSnapshotPacket.IndicatorState state(
            PromisedConsortActionSnapshot action,
            Timing timing
    ) {
        long gameTick = action.startGameTick() + action.actionTick();
        if (gameTick >= timing.activeTick()) {
            return IndicatorSnapshotPacket.IndicatorState.ACTIVE;
        }
        if (timing.activeTick() - gameTick <= 4) {
            return IndicatorSnapshotPacket.IndicatorState.IMMINENT;
        }
        return gameTick >= timing.lockTick()
                ? IndicatorSnapshotPacket.IndicatorState.LOCKED
                : IndicatorSnapshotPacket.IndicatorState.TRACKING;
    }

    private IndicatorSnapshotPacket packet(
            int bossEntityId,
            String indicatorId,
            IndicatorSnapshotPacket.SegmentSlot slot,
            IndicatorSnapshotPacket.StyleRole style,
            IndicatorSnapshotPacket.Semantic semantic,
            IndicatorSnapshotPacket.IndicatorState state,
            Shape shape,
            float yaw,
            Timing timing,
            boolean instantGuard
    ) {
            long lockTick = instantGuard
                ? Math.max(timing.startTick(),
                timing.activeTick() - config.instantGuard().defaultCueLeadTicks())
                : timing.lockTick();
        return new IndicatorSnapshotPacket(
                bossEntityId,
                indicatorId,
                slot,
                style,
                semantic,
                state,
                shape.type(),
                new IndicatorSnapshotPacket.Point(shape.anchor().x, shape.anchor().y, shape.anchor().z),
                yaw,
                shape.ranges(),
                shape.pathPoints(),
                timing.startTick(),
                lockTick,
                timing.activeTick(),
                timing.endTick(),
                instantGuard,
                instantGuard ? config.instantGuard().cuePulseCount() : 0,
                instantGuard ? parseColor(config.instantGuard().redCueColor()) : 0
        );
    }

    private static Shape shape(HorizontalShape shape, double y) {
        Vec3 anchor;
        List<Float> ranges;
        IndicatorSnapshotPacket.ShapeType type;
        float yaw = 0.0F;
        if (shape instanceof Circle circle) {
            anchor = new Vec3(circle.center().x(), y, circle.center().z());
            ranges = List.of((float) circle.radius());
            type = IndicatorSnapshotPacket.ShapeType.CIRCLE;
        } else if (shape instanceof Annulus annulus) {
            anchor = new Vec3(annulus.center().x(), y, annulus.center().z());
            ranges = List.of((float) annulus.innerRadius(), (float) annulus.outerRadius());
            type = IndicatorSnapshotPacket.ShapeType.ANNULUS;
        } else if (shape instanceof Capsule capsule) {
            anchor = new Vec3(capsule.start().x(), y, capsule.start().z());
            double length = capsule.start().subtract(capsule.end()).length();
            ranges = List.of((float) length, (float) (capsule.radius() * 2.0));
            type = IndicatorSnapshotPacket.ShapeType.CAPSULE;
            yaw = yawDegrees(capsule.end().subtract(capsule.start()), 0.0F);
        } else if (shape instanceof Sector sector) {
            anchor = new Vec3(sector.center().x(), y, sector.center().z());
            ranges = List.of((float) sector.radius(),
                    (float) Math.toDegrees(sector.halfAngleRadians() * 2.0));
            type = IndicatorSnapshotPacket.ShapeType.SECTOR;
            yaw = yawDegrees(sector.forward(), 0.0F);
        } else if (shape instanceof DirectionalRectangle rectangle) {
            anchor = new Vec3(rectangle.origin().x(), y, rectangle.origin().z());
            ranges = List.of((float) rectangle.length(), (float) rectangle.width());
            type = IndicatorSnapshotPacket.ShapeType.RECTANGLE;
            yaw = yawDegrees(rectangle.forward(), 0.0F);
        } else {
            throw new IllegalArgumentException("unsupported hazard shape " + shape);
        }
        return new Shape(type, anchor, ranges, List.of(), yaw);
    }

    private static Shape sector(Vec3 anchor, double radius, double degrees) {
        return new Shape(IndicatorSnapshotPacket.ShapeType.SECTOR, anchor,
                List.of((float) radius, (float) degrees), List.of(), 0.0F);
    }

    private static Shape capsule(Vec3 anchor, double length, double width) {
        return new Shape(IndicatorSnapshotPacket.ShapeType.CAPSULE, anchor,
                List.of((float) length, (float) width), List.of(), 0.0F);
    }

    private static Shape circle(Vec3 anchor, double radius) {
        return new Shape(IndicatorSnapshotPacket.ShapeType.CIRCLE, anchor,
                List.of((float) radius), List.of(), 0.0F);
    }

    private static Shape annulus(Vec3 anchor, double inner, double outer) {
        return new Shape(IndicatorSnapshotPacket.ShapeType.ANNULUS, anchor,
                List.of((float) inner, (float) outer), List.of(), 0.0F);
    }

    private static Shape rectangle(Vec3 anchor, double length, double width) {
        return new Shape(IndicatorSnapshotPacket.ShapeType.RECTANGLE, anchor,
                List.of((float) length, (float) width), List.of(), 0.0F);
    }

    private static Shape path(Vec3 anchor, float yaw, double length, double width) {
        double radians = Math.toRadians(yaw);
        Vec3 end = anchor.add(-Math.sin(radians) * length, 0.0, Math.cos(radians) * length);
        return new Shape(
                IndicatorSnapshotPacket.ShapeType.PATH,
                anchor,
                List.of((float) width),
                List.of(
                        new IndicatorSnapshotPacket.Point(anchor.x, anchor.y, anchor.z),
                        new IndicatorSnapshotPacket.Point(end.x, end.y, end.z)
                ),
                yaw
        );
    }

    private int activeTicks(PromisedConsortActionSnapshot action) {
        return catalog.get(action.actionId())
                .timeline()
                .stages()
                .get(action.stageIndex())
                .activeTicks();
    }

    private static int activeTicks(PromisedConsortSkillConfigSnapshot.Skill skill) {
        return ticks(skill, skill.integer("active_ticks"));
    }

    private static int ticks(PromisedConsortSkillConfigSnapshot.Skill skill, int ticks) {
        return skill.tuning().scaleTicks(ticks);
    }

    private static Shape tunedShape(Shape shape, SkillTuning tuning) {
        List<Float> ranges = switch (shape.type()) {
            case SECTOR -> List.of(
                    (float) tuning.scaleRange(shape.ranges().get(0)),
                    shape.ranges().get(1)
            );
            case CAPSULE, ANNULUS, RECTANGLE -> shape.ranges().stream()
                    .map(value -> (float) tuning.scaleRange(value))
                    .toList();
            case CIRCLE, ZONE, PATH -> List.of(
                    (float) tuning.scaleRange(shape.ranges().get(0))
            );
        };
        List<IndicatorSnapshotPacket.Point> pathPoints = shape.pathPoints().stream()
                .map(point -> new IndicatorSnapshotPacket.Point(
                        shape.anchor().x + (point.x() - shape.anchor().x) * tuning.rangeMultiplier(),
                        shape.anchor().y + (point.y() - shape.anchor().y) * tuning.rangeMultiplier(),
                        shape.anchor().z + (point.z() - shape.anchor().z) * tuning.rangeMultiplier()
                ))
                .toList();
        return new Shape(shape.type(), shape.anchor(), ranges, pathPoints, shape.yawDegrees());
    }

    private static IndicatorSnapshotPacket.StyleRole style(PromisedConsortActionId actionId) {
        return switch (actionId) {
            case GRAVITY_DIVE, STARCALLER_CRY, GRAVITY_METEOR ->
                    IndicatorSnapshotPacket.StyleRole.GRAVITY_PURPLE;
            case L_COMBO_BLOODFLAME -> IndicatorSnapshotPacket.StyleRole.BLOODFLAME_RED;
            case LIGHT_OF_MIQUELLA, RING_OF_LIGHT, LIGHTSPEED_SLASH, LIGHTSPEED_DASH,
                    LIGHTSPEED_SIDE_DASH, PROMISED_CONSORT, ENHANCED_EARTHHEAVE,
                    CONSORT_METEOR -> IndicatorSnapshotPacket.StyleRole.HOLY_IVORY;
            default -> IndicatorSnapshotPacket.StyleRole.PHYSICAL_GOLD;
        };
    }

    private static IndicatorSnapshotPacket.Semantic semantic(
            IndicatorSnapshotPacket.StyleRole style
    ) {
        return switch (style) {
            case GRAVITY_PURPLE -> IndicatorSnapshotPacket.Semantic.GRAVITY;
            case BLOODFLAME_RED -> IndicatorSnapshotPacket.Semantic.BLOODFLAME;
            case HOLY_IVORY, CLONE_GOLD -> IndicatorSnapshotPacket.Semantic.HOLY;
            case MOVEMENT_DASHED -> IndicatorSnapshotPacket.Semantic.MOVEMENT;
            case PHYSICAL_SILVER, PHYSICAL_GOLD -> IndicatorSnapshotPacket.Semantic.PHYSICAL;
            case SCARLET_ROT_DARK_RED -> IndicatorSnapshotPacket.Semantic.SCARLET_ROT;
        };
    }

    private static float yawDegrees(Vec2 facing, float fallback) {
        if (facing == null || facing.isZero()) {
            return fallback;
        }
        return (float) Math.toDegrees(Math.atan2(-facing.x(), facing.z()));
    }

    private static int parseColor(String value) {
        try {
            return Integer.parseInt(value.substring(1), 16);
        } catch (RuntimeException exception) {
            return IndicatorSnapshotPacket.DEFAULT_CUE_RGB;
        }
    }

    private record Timing(long startTick, long lockTick, long activeTick, long endTick) {
    }

        private record TimedShape(
            String id,
            int activeOffset,
            Shape shape,
            IndicatorSnapshotPacket.StyleRole style,
                boolean instantGuard,
                float yawOffset
        ) {
            private TimedShape(
                    String id,
                    int activeOffset,
                    Shape shape,
                    IndicatorSnapshotPacket.StyleRole style,
                    boolean instantGuard
            ) {
                this(id, activeOffset, shape, style, instantGuard, 0.0F);
            }
        }

    private record Shape(
            IndicatorSnapshotPacket.ShapeType type,
            Vec3 anchor,
            List<Float> ranges,
            List<IndicatorSnapshotPacket.Point> pathPoints,
            float yawDegrees
    ) {
        private Shape(
                IndicatorSnapshotPacket.ShapeType type,
                Vec3 anchor,
                List<Float> ranges,
                List<IndicatorSnapshotPacket.Point> pathPoints
        ) {
            this(type, anchor, ranges, pathPoints, 0.0F);
        }
    }
}
