package com.tonywww.elder_bosses.boss.promisedconsort.execution;

import com.tonywww.elder_bosses.boss.promisedconsort.action.PromisedConsortActionCatalog;
import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortCombatConfigSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSkillConfigSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase;
import com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortActionSnapshot;
import com.tonywww.elder_bosses.combat.action.ActionPhase;
import com.tonywww.elder_bosses.combat.action.SkillTuning;
import com.tonywww.elder_bosses.combat.damage.DamageFormula;
import com.tonywww.elder_bosses.combat.geometry.Annulus;
import com.tonywww.elder_bosses.combat.geometry.Capsule;
import com.tonywww.elder_bosses.combat.geometry.Circle;
import com.tonywww.elder_bosses.combat.geometry.DirectionalRectangle;
import com.tonywww.elder_bosses.combat.geometry.HorizontalShape;
import com.tonywww.elder_bosses.combat.geometry.Sector;
import com.tonywww.elder_bosses.combat.geometry.Vec2;
import com.tonywww.elder_bosses.combat.hit.HitId;
import com.tonywww.elder_bosses.combat.hit.HitRegistry;
import com.tonywww.elder_bosses.combat.hit.PerTargetHitCounter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class PromisedConsortActionExecutor {
    private static final double MAX_CONTROLLED_MOVEMENT_PER_TICK = 1.25;
    private static final double DEFAULT_SECTOR_DEGREES = 120.0;
    private static final double DEFAULT_HEIGHT_MARGIN = 2.0;

    private final Host host;
    private final PromisedConsortActionCatalog catalog;
    private final PromisedConsortCombatConfigSnapshot config;
    private final HitRegistry hitRegistry = new HitRegistry();
    private final PerTargetHitCounter hitCounter = new PerTargetHitCounter();
    private final Map<String, Integer> hitIndices = new HashMap<>();
    private final Map<String, Vec3> lockedPoints = new HashMap<>();
    private final Map<String, Hazard> hazards = new LinkedHashMap<>();
    private final Map<String, PromisedConsortAttackPlan.Strike> telegraphs = new LinkedHashMap<>();
    private final Set<String> processedEvents = new java.util.HashSet<>();

    private long activeSequence = -1L;
    private Vec2 lockedFacing;
    private SkillTuning activeTuning = SkillTuning.NEUTRAL;
    private PromisedConsortActionSnapshot executingAction;
    private List<PromisedConsortActionSoundPlan.TimedCue> soundPlan;

    public PromisedConsortActionExecutor(
            Host host,
            PromisedConsortActionCatalog catalog,
            PromisedConsortCombatConfigSnapshot config
    ) {
        this.host = Objects.requireNonNull(host, "host");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.config = Objects.requireNonNull(config, "config");
    }

    public List<PromisedConsortHitOutcome> tick(PromisedConsortActionSnapshot action) {
        Objects.requireNonNull(action, "action");
        prepareAction(action);
        executingAction = action;
        activeTuning = catalog.skill(action).tuning();
        if(action.rangedCounter() && action.actionId() != PromisedConsortActionId.SPIRAL_ASSAULT
            && action.actionId() != PromisedConsortActionId.GRAVITY_DIVE || action.actionId()==PromisedConsortActionId.GRAVITY_REPRISAL) {
            if(!prepareRangedPath(action)) return tickPersistentHazards();
        } else repositionForNextStrike(action);
        if (action.actionId() == PromisedConsortActionId.LIGHT_OF_MIQUELLA) moveHolyFlight(action);
        if (isLion(action) && catalog.timeline(action).activeStartTick(0) >= 4 && !moveLionClaw(action)) return tickPersistentHazards();
        if (action.actionId() == PromisedConsortActionId.GRAVITY_METEOR && catalog.timeline(action).stages().size() > 4
            && !moveMeteorSummon(action)) return tickPersistentHazards();
        if (!action.rangedCounter() && action.actionId() == PromisedConsortActionId.LIGHTSPEED_DASH
            && catalog.timeline(action).stages().size() > 2 && !moveLightspeedBody(action)) return tickPersistentHazards();
        if ((action.actionId() == PromisedConsortActionId.CROSS_LEAP_COMBO || action.actionId() == PromisedConsortActionId.SPIRAL_ASSAULT
            || action.actionId() == PromisedConsortActionId.GRAVITY_DIVE)
            && !moveCrossLeap(action)) return tickPersistentHazards();
        Vec3 predictionOrigin = host.boss().position();
        updateTelegraphs(action, predictionOrigin);
        spawnAnnouncedClones(action);
        List<PromisedConsortHitOutcome> outcomes = new ArrayList<>();
        executeAction(action, outcomes);
        if(lockedPoints.containsKey("ranged_cancelled")) return List.copyOf(outcomes);
        tickActionSounds(action);
        updateTelegraphs(action, predictionOrigin);
        spawnAnnouncedClones(action);
        tickHazards(outcomes);
        return List.copyOf(outcomes);
    }

    public List<PromisedConsortHitOutcome> tickPersistentHazards() {
        List<PromisedConsortHitOutcome> outcomes = new ArrayList<>();
        tickHazards(outcomes);
        return List.copyOf(outcomes);
    }

    private static boolean isLion(PromisedConsortActionSnapshot action) {
        return action.actionId() == PromisedConsortActionId.LION_CLAW || action.actionId() == PromisedConsortActionId.LION_CLAW_DOUBLE;
    }

    public void prepareOpeningLion(PromisedConsortActionSnapshot action) {
        prepareAction(action);
        lockedPoints.put("lion_opening", Vec3.ZERO);
    }

    public boolean openingLion() {
        return lockedPoints.containsKey("lion_opening") && !lockedPoints.containsKey("lion_cancelled");
    }

    private boolean moveLionClaw(PromisedConsortActionSnapshot action) {
        if (lockedPoints.containsKey("lion_cancelled")) return false;
        if (!once(action, "lion_move:" + action.actionTick())) return true;
        int impact = catalog.timeline(action).activeStartTick(0);
        var path = openingLion() ? PromisedConsortLionClawPath.opening(impact, config.instantGuard().defaultCueLeadTicks())
                : PromisedConsortLionClawPath.create(impact, config.instantGuard().defaultCueLeadTicks(), 6, PromisedConsortLionClawPath.NORMAL_MAXIMUM_STEP);
        Vec3 origin = lockedPoints.computeIfAbsent("lion_origin", unused -> host.boss().position());
        lockedPoints.putIfAbsent("lion_lock", new Vec3(path.lockTick(), 0, 0));
        if (!lockedPoints.containsKey("lion_frozen")) {
            LivingEntity target = host.currentTarget().orElse(null);
            if (target == null || !target.isAlive() || !host.insideArena(target)) return cancelLionClaw();
            Vec3 destination = path.destination(origin, target.position(), openingLion() ? 64 : 12,
                    PromisedConsortLionClawPath.TARGET_OVERSHOOT);
            var support = host.serverLevel().clip(new net.minecraft.world.level.ClipContext(destination.add(0, 2, 0), destination.add(0, -3, 0),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, host.boss()));
            if (support.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || support.getDirection() != net.minecraft.core.Direction.UP
                    || !host.serverLevel().getBlockState(support.getBlockPos()).getFluidState().isEmpty()) return cancelLionClaw();
            destination = new Vec3(destination.x, support.getLocation().y, destination.z);
            destination = PromisedConsortGroundMovement.destination(host.serverLevel(), host.boss(), destination, destination);
            if (destination == null) return cancelLionClaw();
            if (destination.subtract(host.combatCenter()).horizontalDistance() > config.arena().logicalRadius()
                    || !host.serverLevel().noCollision(host.boss(), host.boss().getBoundingBox().move(destination.subtract(host.boss().position())).deflate(0.001))) return cancelLionClaw();
            lockedPoints.put("lion_end", destination);
            lockedFacing = direction(origin, destination).normalizedOr(facing());
            host.faceControlled(lockedFacing);
            if (action.actionTick() >= path.lockTick()) lockedPoints.put("lion_frozen", destination);
        }
        if (action.actionTick() < path.takeoffTick() || action.actionTick() > impact) return true;
        lockedPoints.putIfAbsent("lion_flight_gravity", new Vec3(host.boss().isNoGravity() ? 1 : 0, 0, 0));
        host.boss().setNoGravity(true);
        host.boss().setDeltaMovement(Vec3.ZERO);
        Vec3 destination = path.at(origin, lockedPoints.get("lion_end"), action.actionTick());
        boolean terrainCorrection = false;
        if (!host.serverLevel().noCollision(host.boss(), host.boss().getBoundingBox().move(destination.subtract(host.boss().position())).deflate(0.001))) {
            destination = PromisedConsortGroundMovement.destination(host.serverLevel(), host.boss(), host.boss().position(), destination);
            if (destination == null) return cancelLionClaw();
            terrainCorrection = true;
        }
        Vec3 remaining = destination.subtract(host.boss().position());
        if (remaining.length() > path.maximumStep() + 0.01) return cancelLionClaw();
        int steps = Math.max(1, (int) Math.ceil(remaining.length() / 0.25));
        if (terrainCorrection && !moveAlongGround(destination)) return cancelLionClaw();
        for (int index = 0; !terrainCorrection && index < steps; index++) {
            Vec3 step = remaining.scale(1.0 / steps);
            if (!host.serverLevel().noCollision(host.boss(), host.boss().getBoundingBox().move(step).deflate(0.001))) return cancelLionClaw();
            host.moveControlled(step);
        }
        if (host.boss().position().distanceTo(destination) > 0.02) return cancelLionClaw();
        if (action.actionTick() == impact) {
            lockedPoints.put("lion_landed", destination);
            releaseFlight();
        }
        return true;
    }

    private boolean cancelLionClaw() {
        lockedPoints.put("lion_cancelled", Vec3.ZERO);
        releaseFlight();
        cancelPendingHazards();
        if (host.boss() instanceof com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity boss) boss.cancelRangedAction();
        return false;
    }

    private boolean moveCrossLeap(PromisedConsortActionSnapshot action) {
        if (lockedPoints.containsKey("cross_cancelled")) return false;
        if (!once(action, "cross_move:" + action.actionTick())) return true;
        var timeline = catalog.timeline(action);
        var skill = catalog.skill(action);
        boolean advance = action.actionId() == PromisedConsortActionId.SPIRAL_ASSAULT;
        boolean dive = action.actionId() == PromisedConsortActionId.GRAVITY_DIVE;
        int contactOffset = (advance || dive) && action.rangedCounter() ? skill.integerList("ranged_counter.attack_event_offsets").get(0) : 0;
        if (advance && lockedPoints.containsKey("advance_landed") || dive && lockedPoints.containsKey("dive_landed")) return true;
        boolean opening = advance || dive || action.actionTick() <= timeline.activeStartTick(0);
        if (!opening && (action.actionTick() < timeline.stageStartTick(4) || action.actionTick() > timeline.activeStartTick(4))) return true;
        var path = advance ? PromisedConsortCrossLeapPath.advance(timeline, contactOffset)
            : dive ? PromisedConsortCrossLeapPath.gravityDive(timeline, contactOffset)
            : opening ? PromisedConsortCrossLeapPath.opening(timeline, skill.number("leap_height"))
                : PromisedConsortCrossLeapPath.finisher(timeline, skill.number("leap_height"));
        String prefix = advance ? "advance" : dive ? "dive" : opening ? "cross_opening" : "cross_finisher";
        var boss = host.boss();
        Vec3 origin = lockedPoints.computeIfAbsent(prefix + "_origin", unused -> boss.position());
        Vec3 end = lockedPoints.getOrDefault(prefix + "_end", origin);
        if (!lockedPoints.containsKey(prefix + "_frozen")) {
            if (opening) {
                var target = host.currentTarget().orElse(null);
                if (target == null || !target.isAlive() || !host.insideArena(target)) return cancelCrossLeap();
                double distance = advance || dive ? path.maximumDistance(action.rangedCounter() ? skill.number("ranged_counter.max_forward_distance") : 16)
                    : skill.number("leap_distance");
                end = advance ? path.advanceDestination(origin, target.position(), new Vec3(facing().x(), 0, facing().z()), distance)
                    : path.destination(origin, target.position(), distance, dive ? 1.5 : preferredSeparation(boss.getBbWidth(), target.getBbWidth()));
                lockedFacing = direction(origin, target.position()).normalizedOr(facing());
                host.faceControlled(lockedFacing);
            }
            if (!host.serverLevel().hasChunkAt(net.minecraft.core.BlockPos.containing(end))) return cancelCrossLeap();
            var support = host.serverLevel().clip(new net.minecraft.world.level.ClipContext(end.add(0, 1, 0), end.add(0, -2, 0),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, boss));
            if (support.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || support.getDirection() != net.minecraft.core.Direction.UP
                    || !host.serverLevel().getBlockState(support.getBlockPos()).getFluidState().isEmpty()) return cancelCrossLeap();
            end = new Vec3(end.x, support.getLocation().y, end.z);
            if (!path.supports(origin, end) || Math.abs(end.y - origin.y) > 1) return cancelCrossLeap();
            lockedPoints.put(prefix + "_end", end);
            if (action.actionTick() >= path.takeoffTick()) {
                Vec3 previous = origin;
                for (int tick = path.takeoffTick() + 1; tick <= path.landingTick(); tick++) {
                    Vec3 point = advance ? path.advanceAt(origin, end, tick) : path.at(origin, end, tick);
                    int segments = Math.max(1, (int) Math.ceil(previous.distanceTo(point) / 0.25));
                    for (int index = 1; index <= segments; index++) {
                        if (!crossLeapClear(previous.lerp(point, (double) index / segments))) return cancelCrossLeap();
                    }
                    previous = point;
                }
                lockedPoints.put(prefix + "_frozen", end);
            }
        }
        if (action.actionTick() < path.takeoffTick()) return true;
        lockedPoints.putIfAbsent("cross_flight_gravity", new Vec3(boss.isNoGravity() ? 1 : 0, 0, 0));
        boss.setNoGravity(true);
        boss.setDeltaMovement(Vec3.ZERO);
        Vec3 destination = advance ? path.advanceAt(origin, end, action.actionTick()) : path.at(origin, end, action.actionTick());
        Vec3 movement = destination.subtract(boss.position());
        if (movement.length() > 2.01) return cancelCrossLeap();
        int segments = Math.max(1, (int) Math.ceil(movement.length() / 0.25));
        for (int index = 0; index < segments; index++) {
            Vec3 step = movement.scale(1.0 / segments);
            if (!crossLeapClear(boss.position().add(step))) return cancelCrossLeap();
            host.moveControlled(step);
        }
        if (boss.position().distanceTo(destination) > 0.02) return cancelCrossLeap();
        if (action.actionTick() == path.landingTick()) {
            if (dive) {
                var support = host.serverLevel().clip(new net.minecraft.world.level.ClipContext(destination.add(0, 0.1, 0), destination.add(0, -0.1, 0),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, boss));
                if (support.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || support.getDirection() != net.minecraft.core.Direction.UP
                    || Math.abs(support.getLocation().y - destination.y) > 0.02) return cancelCrossLeap();
            }
            lockedPoints.put(prefix + "_landed", destination);
            releaseFlight();
        }
        return true;
    }

    private boolean crossLeapClear(Vec3 point) {
        return point.subtract(host.combatCenter()).horizontalDistance() <= config.arena().logicalRadius()
                && host.serverLevel().hasChunkAt(net.minecraft.core.BlockPos.containing(point))
                && host.serverLevel().noCollision(host.boss(), host.boss().getBoundingBox().move(point.subtract(host.boss().position())).deflate(0.001));
    }

    private boolean cancelCrossLeap() {
        lockedPoints.put("cross_cancelled", Vec3.ZERO);
        releaseFlight();
        cancelPendingHazards();
        if (host.boss() instanceof com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity boss) boss.cancelRangedAction();
        return false;
    }

    private void moveHolyFlight(PromisedConsortActionSnapshot action) {
        if (!once(action, "holy_flight:" + action.actionTick())) return;
        var skill = catalog.skill(action);
        Vec3 origin = lockedPoints.computeIfAbsent("holy_flight_origin", unused -> host.boss().position());
        lockedPoints.putIfAbsent("holy_flight_gravity", new Vec3(host.boss().isNoGravity() ? 1 : 0, 0, 0));
        host.boss().setNoGravity(true);
        host.boss().setDeltaMovement(Vec3.ZERO);
        int total = catalog.timeline(action).totalTicks();
        double height = holyFlightHeight(action.actionTick(), total,
                skill.numbers().getOrDefault("flight_height", 12.0), skill.integers().getOrDefault("flight_ascent_ticks", 16),
                skill.integers().getOrDefault("flight_descent_ticks", 20));
        Vec3 movement = holyFlightStep(host.boss().getY(), origin.y + height,
                step -> host.serverLevel().noCollision(host.boss(), host.boss().getBoundingBox().move(step)));
        if (movement.lengthSqr() > 0) host.moveControlled(movement);
    }

    private boolean moveMeteorSummon(PromisedConsortActionSnapshot action) {
        var sequence = PromisedConsortMeteorSequence.from(catalog.timeline(action));
        if (!sequence.usable()) return true;
        if (lockedPoints.containsKey("meteor_cast_cancelled")) return false;
        if (!once(action, "meteor_cast_move:" + action.actionTick())) return true;
        Vec3 origin = lockedPoints.computeIfAbsent("meteor_cast_origin", unused -> host.boss().position());
        if (action.actionTick() >= sequence.crestTick() && action.actionTick() < sequence.landingLockTick()) {
            var entity = action.targetId() == null ? null : host.serverLevel().getEntity(action.targetId());
            if (entity instanceof LivingEntity target && target.isAlive() && host.insideArena(target)) {
                lockedFacing = direction(host.boss().position(), target.position()).normalizedOr(facing());
                host.faceControlled(lockedFacing);
            }
        }
        if (action.actionTick() >= sequence.groundTick()) lockedPoints.computeIfAbsent("meteor_cast_crest",
            unused -> sequence.crest(origin, new Vec3(facing().x(), 0, facing().z())));
        if (action.actionTick() >= sequence.landingLockTick() && !lockedPoints.containsKey("meteor_cast_end")) {
            LivingEntity target = host.currentTarget().orElse(null);
            Vec3 end = action.phase() == PromisedConsortPhase.PHASE_TWO && target != null && target.isAlive() && host.insideArena(target)
                    ? sequence.landing(origin, target.position(), preferredSeparation(host.boss().getBbWidth(), target.getBbWidth())) : origin;
            var support = host.serverLevel().clip(new net.minecraft.world.level.ClipContext(end.add(0, 2, 0), end.add(0, -3, 0),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, host.boss()));
            if (support.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || support.getDirection() != net.minecraft.core.Direction.UP
                    || !host.serverLevel().getBlockState(support.getBlockPos()).getFluidState().isEmpty()) return cancelMeteorSummon();
            end = new Vec3(end.x, support.getLocation().y, end.z);
            if (end.subtract(host.combatCenter()).horizontalDistance() > config.arena().logicalRadius()
                    || !host.serverLevel().noCollision(host.boss(), host.boss().getBoundingBox().move(end.subtract(host.boss().position())).deflate(0.001))) return cancelMeteorSummon();
            lockedPoints.put("meteor_cast_end", end);
            lockedFacing = direction(origin, end).normalizedOr(facing());
            host.faceControlled(lockedFacing);
        }
        if (action.actionTick() < sequence.riseTick() || action.actionTick() > sequence.landingTick()) return true;
        lockedPoints.putIfAbsent("meteor_cast_gravity", new Vec3(host.boss().isNoGravity() ? 1 : 0, 0, 0));
        host.boss().setNoGravity(true);
        host.boss().setDeltaMovement(Vec3.ZERO);
        Vec3 destination = sequence.at(origin, lockedPoints.getOrDefault("meteor_cast_crest", origin.add(0, sequence.height(), 0)),
            lockedPoints.getOrDefault("meteor_cast_end", origin), action.actionTick());
        Vec3 remaining = destination.subtract(host.boss().position());
        if (remaining.length() > 1.26) return cancelMeteorSummon();
        int steps = Math.max(1, (int) Math.ceil(remaining.length() / 0.25));
        for (int index = 0; index < steps; index++) {
            Vec3 step = remaining.scale(1.0 / steps);
            if (!host.serverLevel().noCollision(host.boss(), host.boss().getBoundingBox().move(step).deflate(0.001))) return cancelMeteorSummon();
            host.moveControlled(step);
        }
        if (host.boss().position().distanceTo(destination) > 0.02) return cancelMeteorSummon();
        if (action.actionTick() == sequence.landingTick()) {
            lockedPoints.put("meteor_cast_landed", destination);
            releaseFlight();
        }
        return true;
    }

    private boolean cancelMeteorSummon() {
        lockedPoints.put("meteor_cast_cancelled", Vec3.ZERO);
        releaseFlight();
        cancelPendingHazards();
        if (host.boss() instanceof com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity boss) boss.cancelRangedAction();
        return false;
    }

    public static Vec3 holyFlightStep(double currentY, double desiredY, Predicate<Vec3> clear) {
        Vec3 movement = new Vec3(0, Math.max(-1.25, Math.min(1.25, desiredY - currentY)), 0);
        return clear.test(movement) ? movement : Vec3.ZERO;
    }

    public static double holyFlightHeight(double tick, int totalTicks, double configuredHeight, int ascentTicks, int descentTicks) {
        if (!Double.isFinite(configuredHeight) || configuredHeight < 0 || configuredHeight > 32 || ascentTicks < 1 || descentTicks < 1 || totalTicks < 1) {
            throw new IllegalArgumentException("Invalid holy flight curve");
        }
        double end = Math.max(1, totalTicks - 1);
        double rise = Math.min(ascentTicks, end * 0.5), fall = Math.min(descentTicks, end * 0.5);
        double height = Math.min(configuredHeight, Math.min(rise, fall) * 0.75);
        double progress = Math.min(Math.max(0, tick / rise), Math.max(0, (end - tick) / fall));
        progress = Math.min(1, progress);
        return height * progress * progress * (3 - 2 * progress);
    }

    public void releaseFlight() {
        for (String key : List.of("holy_flight_gravity", "lion_flight_gravity", "meteor_cast_gravity", "cross_flight_gravity")) {
            Vec3 previous = lockedPoints.remove(key);
            if (previous != null) {
                host.boss().setNoGravity(previous.x != 0);
                host.boss().setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    private void tickActionSounds(PromisedConsortActionSnapshot action) {
        if (host.boss() instanceof com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity boss
                && boss.activeActionSequence() != action.sequence()) return;
        if (soundPlan == null) soundPlan = PromisedConsortActionSoundPlan.schedule(action, catalog.timeline(action), catalog.skill(action));
        for (var event : soundPlan) {
            PromisedConsortActionSoundPlan.dispatch(List.of(event.cue()), processedEvents, "sound:" + action.sequence() + ":",
                    action.actionTick(), event.tick(), event.tick() + 1L,
                    cue -> host.playActionSound(cue, host.boss().position(), facing()));
        }
    }

    private void playImpactSounds(PromisedConsortActionSnapshot action, String occurrence, PromisedConsortAttackPlan.Strike strike) {
        if (action == null) return;
        ShapeData shape = ShapeData.from(strike.shape());
        PromisedConsortActionSoundPlan.dispatch(PromisedConsortActionSoundPlan.impact(action.actionId(), occurrence), processedEvents,
                "sound:" + action.sequence() + ":", host.gameTime(), strike.activeTick(), strike.endTick(),
                cue -> host.playActionSound(cue, new Vec3(shape.origin().x(), strike.baseY(), shape.origin().z()), shape.direction()));
    }

    public void cancelPendingHazards() {
        hazards.values().removeIf(hazard -> host.gameTime() < hazard.activeTick());
        telegraphs.clear();
    }

    public void clearAll() {
        hazards.clear();
        clearAction();
    }

    public List<HazardSnapshot> hazardSnapshots() {
        return hazards.values().stream().map(Hazard::snapshot).toList();
    }

    public List<PromisedConsortAttackPlan.Strike> telegraphs() {
        return List.copyOf(telegraphs.values());
    }

    private void updateTelegraphs(PromisedConsortActionSnapshot action, Vec3 predictionOrigin) {
        long now = host.gameTime();
        for (var candidate : PromisedConsortAttackPlan.create(action, catalog.skill(action),
            catalog.timeline(action), predictionOrigin, facing(), lockedPoints,
                config.instantGuard().defaultCueLeadTicks())) {
            if (candidate.endTick() <= now) continue;
            var previous = telegraphs.get(candidate.id());
            Vec3 frozen = lockedPoints.get("attack_origin:" + candidate.id());
            Vec3 direction = lockedPoints.get("attack_direction:" + candidate.id());
            if (frozen != null && direction != null) {
                candidate = PromisedConsortAttackPlan.relocate(candidate, frozen, new Vec2(direction.x, direction.z));
                if (previous == null) previous = candidate;
            }
            candidate = candidate.trackFrom(previous, now);
            telegraphs.put(candidate.id(), candidate);
            if (now >= candidate.lockTick() && frozen == null) {
                ShapeData data = ShapeData.from(candidate.shape());
                lockedPoints.put("attack_origin:" + candidate.id(), new Vec3(data.origin().x(), candidate.baseY(), data.origin().z()));
                lockedPoints.put("attack_direction:" + candidate.id(), new Vec3(data.direction().x(), 0, data.direction().z()));
            }
        }
        telegraphs.values().removeIf(strike -> now >= strike.endTick() + 3);
    }

    private PromisedConsortAttackPlan.Strike announced(String occurrence) {
        if (executingAction != null && executingAction.actionId() == PromisedConsortActionId.RING_OF_LIGHT && occurrence.equals("ring")) {
            return telegraphs.get("ring_" + executingAction.phaseTick());
        }
        return telegraphs.get(occurrence);
    }

    private void spawnAnnouncedClones(PromisedConsortActionSnapshot action) {
        long now = host.gameTime();
        for (var cue : PromisedConsortAttackPlan.cloneCues(List.copyOf(telegraphs.values()))) {
            if (now >= cue.appearTick() && now < cue.strike().activeTick() && once(action, "visual:" + cue.strike().id())) {
                ShapeData shape = ShapeData.from(cue.strike().shape());
                host.spawnVisualClone(action, cue.index(), cue.count(),
                        new Vec3(shape.origin().x(), cue.strike().baseY(), shape.origin().z()),
                        cue.strike().shape() instanceof Circle ? facing() : shape.direction(), cue.strike().activeTick());
            }
        }
    }

    public List<PersistentHazard> persistentHazards() {
        long now = host.gameTime();
        return hazards.values().stream()
                .map(hazard -> PersistentHazard.from(hazard, now))
                .toList();
    }

        public PersistentState persistentState() {
        Map<String, PersistentPoint> points = new HashMap<>();
        lockedPoints.forEach((key, value) -> points.put(
            key,
            new PersistentPoint(value.x, value.y, value.z)
        ));
        return new PersistentState(
            activeSequence,
            lockedFacing,
            points,
            processedEvents,
            hitIndices,
            hitRegistry.persistentClaims(),
            hitCounter.persistentCounts(),
            persistentHazards()
        );
        }

        public void restoreState(PersistentState state) {
        Objects.requireNonNull(state, "state");
        soundPlan = null;
        activeSequence = state.activeSequence();
        telegraphs.clear();
        lockedFacing = state.lockedFacing();
        lockedPoints.clear();
        state.lockedPoints().forEach((key, value) -> lockedPoints.put(
            key,
            new Vec3(value.x(), value.y(), value.z())
        ));
        processedEvents.clear();
        processedEvents.addAll(state.processedEvents());
        hitIndices.clear();
        hitIndices.putAll(state.hitIndices());
        hitRegistry.restoreClaims(state.hitClaims());
        hitCounter.restoreCounts(state.hitCounts());
        restoreHazards(state.hazards());
        }

    public void restoreHazards(List<PersistentHazard> restored) {
        hazards.clear();
        long now = host.gameTime();
        for (PersistentHazard state : restored) {
            HorizontalShape shape = state.createShape();
            hazards.put(state.id(), new Hazard(
                    state.id(),
                    state.actionSequence(),
                    shape,
                    state.baseY(),
                    state.height(),
                    now,
                    now + state.remainingToActiveTicks(),
                    now + state.remainingToEndTicks(),
                        state.hit(),
                        new java.util.HashSet<>(state.hitTargets()),
                        restoredSoundEvents(state.activationSoundPlayed(), state.remainingToActiveTicks())
            ));
        }
    }

    public static Set<String> restoredSoundEvents(Boolean played, long remainingToActiveTicks) {
        boolean alreadyPlayed = played == null ? remainingToActiveTicks == 0 : played;
        return new java.util.HashSet<>(alreadyPlayed ? Set.of("activation") : Set.of());
    }

    public Map<String, Vec3> lockedPoints() {
        return Map.copyOf(lockedPoints);
    }

    public Vec2 lockedFacing() {
        return facing();
    }

    private void repositionForNextStrike(PromisedConsortActionSnapshot action) {
        int stage = PromisedConsortAttackPlan.repositionStage(action.actionId(), catalog.get(action.actionId()).timeline(),
                action.actionTick(), config.instantGuard().defaultCueLeadTicks());
        LivingEntity target = host.currentTarget().orElse(null);
        if (stage < 0 || target == null || !target.isAlive() || !host.insideArena(target)
                || !once(action, "reposition:" + action.actionTick())) return;
        String budgetKey = "reposition_budget:" + stage;
        double spent = lockedPoints.getOrDefault(budgetKey, Vec3.ZERO).x;
        boolean rangedTarget = host.boss() instanceof com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity boss
            && boss.isRangedTarget(target);
        double maximum = config.targeting().rangedCounter().segmentAdjustmentBudget(
            config.targeting().maxSegmentPursuitDistance(), rangedTarget);
        double separation = preferredSeparation(host.boss().getBbWidth(), target.getBbWidth());
        Vec3 before = host.boss().position();
        RepositionStep step = repositionStep(before, target.position(), facing(), separation, maximum - spent);
        lockedFacing = step.facing();
        lockedPoints.put("target", target.position());
        host.faceControlled(lockedFacing);
        if (step.movement().lengthSqr() > 0) host.moveControlled(step.movement());
        Vec3 travelled = host.boss().position().subtract(before);
        host.setPursuing(Math.hypot(travelled.x, travelled.z) > 0.0001);
        lockedPoints.put(budgetKey, new Vec3(spent + Math.hypot(travelled.x, travelled.z), 0, 0));
    }

    public static double preferredSeparation(double bossWidth, double targetWidth) {
        return Math.max(4.0, (bossWidth + targetWidth) * 0.5 + 1.5);
    }

    public static boolean shouldApproach(double distance, double separation, boolean approaching) {
        return distance > separation + (approaching ? 0.0 : 1.0);
    }

    public static RepositionStep repositionStep(Vec3 position, Vec3 target, Vec2 facing, double separation, double remaining) {
        Vec2 offset = new Vec2(target.x - position.x, target.z - position.z);
        Vec2 forward = facing.normalizedOr(new Vec2(0, 1));
        if (offset.isZero()) return new RepositionStep(forward, Vec3.ZERO);
        Vec2 desired = offset.normalized();
        double turn = Math.atan2(forward.x() * desired.z() - forward.z() * desired.x(),
                forward.x() * desired.x() + forward.z() * desired.z());
        forward = rotate(forward, Math.max(-20, Math.min(20, Math.toDegrees(turn))));
        double distance = Math.max(0, Math.min(0.25, Math.min(remaining, offset.length() - separation)));
        if (forward.x() * desired.x() + forward.z() * desired.z() < 0.5) distance = 0;
        return new RepositionStep(forward, new Vec3(forward.x() * distance, 0, forward.z() * distance));
    }

    public record RepositionStep(Vec2 facing, Vec3 movement) {
    }

    private void prepareAction(PromisedConsortActionSnapshot action) {
        if (activeSequence == action.sequence()) {
            return;
        }
        clearAction();
        activeSequence = action.sequence();
        LivingEntity target = host.currentTarget().orElse(null);
        lockedFacing = target == null
                ? currentFacing(host.boss())
                : direction(host.boss().position(), target.position());
        if (lockedFacing.isZero()) {
            lockedFacing = currentFacing(host.boss());
        }
        if (target != null) {
            lockedPoints.put("target", target.position());
        }
    }

    private void clearAction() {
        releaseFlight();
        if (activeSequence >= 0L) {
            hitCounter.clearAction(activeSequence);
        }
        activeSequence = -1L;
        telegraphs.clear();
        executingAction = null;
        soundPlan = null;
        lockedFacing = null;
        hitRegistry.clear();
        hitIndices.clear();
        lockedPoints.clear();
        processedEvents.clear();
    }

    private void executeAction(
            PromisedConsortActionSnapshot action,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        PromisedConsortSkillConfigSnapshot.Skill skill = catalog.skill(action);
        activeTuning = skill.tuning();
        if(host.boss() instanceof com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity boss) boss.tickRangedDefense(action);
        if(action.rangedCounter() && action.actionId() != PromisedConsortActionId.SPIRAL_ASSAULT
            && action.actionId() != PromisedConsortActionId.GRAVITY_DIVE) { executeRangedDash(action,skill,outcomes); return; }
        if (action.actionTick() == 0) {
            lockTarget("target");
        }
        switch (action.actionId()) {
            case GRAVITY_DIVE -> gravityDive(action, skill, outcomes);
            case L_COMBO_CROSS -> comboSectors(action, skill, "cross", outcomes);
            case L_COMBO_BLOODFLAME -> bloodflame(action, skill, outcomes);
            case R_COMBO_CROSS -> comboSectors(action, skill, "right_cross", outcomes);
            case R_COMBO_LEFT_TWIN -> comboSectors(action, skill, "left_twin", outcomes);
            case R_COMBO_TEMPEST -> tempest(action, skill, outcomes);
            case R_COMBO_EARTHHEAVE -> earthheave(action, skill, outcomes);
            case LION_CLAW -> lionClaw(action, skill, false, outcomes);
            case LION_CLAW_DOUBLE -> lionClaw(action, skill, true, outcomes);
            case STARCALLER_CRY -> starcaller(action, skill, outcomes);
            case GRAVITY_METEOR -> gravityMeteor(action, skill, outcomes);
            case STOMP -> stomp(action, skill, outcomes);
            case CROSS_SLASH -> crossSlash(action, skill, outcomes);
            case SPIRAL_ASSAULT -> spiral(action, skill, outcomes);
            case LIGHT_OF_MIQUELLA -> lightOfMiquella(action, skill, outcomes);
            case RING_OF_LIGHT -> ringOfLight(action, skill, outcomes);
            case LIGHTSPEED_SLASH -> lightspeedSlash(action, skill, outcomes);
            case LIGHTSPEED_DASH -> lightspeedDash(action, skill, outcomes);
            case LIGHTSPEED_SIDE_DASH -> lightspeedSideDash(action, skill, outcomes);
            case PROMISED_CONSORT, CROSS_LEAP_COMBO -> promisedConsort(action, skill, outcomes);
            case ENHANCED_EARTHHEAVE -> enhancedEarthheave(action, skill, outcomes);
            case CONSORT_METEOR -> consortMeteor(action, skill, outcomes);
            case GRAVITY_BULWARK, GRAVITY_REFLECTION -> { }
            case GRAVITY_REPRISAL -> executeReprisal(action,skill,outcomes);
        }
    }

    private boolean prepareRangedPath(PromisedConsortActionSnapshot action) {
        var skill=catalog.skill(action);
        var timeline=catalog.timeline(action);
        int lock=action.rangedCounter()?Math.max(0,timeline.activeStartTick(0)-skill.integer("ranged_counter.target_lock_lead_ticks"))
                :timeline.activeStartTick(1);
        if(lockedPoints.containsKey("ranged_cancelled")) return false;
        if(lockedPoints.containsKey("ranged_frozen")) return true;
        LivingEntity target=action.targetId()==null?null:host.serverLevel().getPlayerByUUID(action.targetId());
        if(target==null || !target.isAlive() || !host.insideArena(target)) {
            cancelRangedPath();
            return false;
        }
        Vec3 origin=host.boss().position();
        Vec2 desired=direction(origin,target.position());
        if(action.rangedCounter()) {
            double turn=Math.toDegrees(Math.atan2(facing().x()*desired.z()-facing().z()*desired.x(),facing().x()*desired.x()+facing().z()*desired.z()));
            double maximum=skill.number("ranged_counter.turn_rate_degrees_per_tick");
            lockedFacing=rotate(facing(),Math.max(-maximum,Math.min(maximum,turn)));
        } else lockedFacing=desired;
        host.faceControlled(facing());
        Vec3 forward=new Vec3(facing().x(),0,facing().z());
        var bounds=host.boss().getBoundingBox();
        java.util.function.Predicate<Vec3> clear=point->point.subtract(host.combatCenter()).horizontalDistance()<=config.arena().logicalRadius()
                && host.serverLevel().noCollision(host.boss(),bounds.move(point.subtract(origin)));
        if(action.rangedCounter()) {
            Vec3[] previousGround = {origin};
            Map<Vec3, Vec3> groundPoints = new java.util.HashMap<>();
            groundPoints.put(origin, origin);
            clear = point -> {
                if (point.subtract(host.combatCenter()).horizontalDistance() > config.arena().logicalRadius()) return false;
                Vec3 ground = PromisedConsortGroundMovement.destination(host.serverLevel(), host.boss(), previousGround[0], point);
                if (ground == null) return false;
                previousGround[0] = ground;
                groundPoints.put(point, ground);
                return true;
            };
            var path=com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedPath.create(action.actionId(),skill,timeline,
                    origin,origin.add(forward.scale(target.position().subtract(origin).horizontalDistance())),forward,clear);
            lockedPoints.put("ranged_origin",path.origin());
            lockedPoints.put("ranged_corner",groundPoints.getOrDefault(path.corner(),path.corner()));
            lockedPoints.put("ranged_end",groundPoints.getOrDefault(path.end(),path.end()));
        } else {
            double width=activeTuning.scaleRange(skill.number("counterattack.width"));
            double height=activeTuning.scaleRange(skill.number("counterattack.height"));
            Vec3 end=com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedPath.clip(origin,
                    origin.add(forward.scale(activeTuning.scaleRange(skill.number("counterattack.length")))),point->
                        point.subtract(host.combatCenter()).horizontalDistance()<=config.arena().logicalRadius()
                        && host.serverLevel().noCollision(new net.minecraft.world.phys.AABB(point.x-width/2,point.y+0.05,point.z-width/2,
                            point.x+width/2,point.y+height,point.z+width/2)));
            lockedPoints.put("ranged_origin",origin); lockedPoints.put("ranged_end",end);
        }
        lockedPoints.put("target",target.position());
        if(action.actionTick()>=lock) lockedPoints.put("ranged_frozen",origin);
        return true;
    }

    private void cancelRangedPath() {
        lockedPoints.put("ranged_cancelled",Vec3.ZERO);
        cancelPendingHazards();
        if(host.boss() instanceof com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity boss) boss.cancelRangedAction();
    }

    private void executeRangedDash(PromisedConsortActionSnapshot action,PromisedConsortSkillConfigSnapshot.Skill skill,List<PromisedConsortHitOutcome> outcomes) {
        if(action.actionPhase()!=ActionPhase.ACTIVE || !lockedPoints.containsKey("ranged_frozen")) return;
        var timeline=catalog.timeline(action);
        var path=new com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedPath(
                lockedPoints.get("ranged_origin"),lockedPoints.get("ranged_corner"),lockedPoints.get("ranged_end"));
        Vec3 before=host.boss().position();
        if(once(action,"ranged_move:"+action.actionTick())) {
            Vec3 destination=path.at(action.actionId(),timeline,action.actionTick());
            Vec3 remaining=destination.subtract(before).multiply(1,0,1);
            double maximum=action.actionId()==PromisedConsortActionId.LIGHTSPEED_SIDE_DASH && action.stageIndex()<3
                    ?skill.number("ranged_counter.max_lateral_per_tick"):skill.number("ranged_counter.max_forward_per_tick");
            if(remaining.length()>maximum+0.001) { cancelRangedPath(); return; }
            int steps=Math.max(1,(int)Math.ceil(remaining.length()/0.25));
            for(int step=0;step<steps;step++) {
                Vec3 movement=remaining.scale(1.0/steps);
                if (!moveAlongGround(host.boss().position().add(movement))) { cancelRangedPath(); return; }
            }
            if(destination.subtract(host.boss().position()).horizontalDistance()>0.01) { cancelRangedPath(); return; }
            if(action.actionId()==PromisedConsortActionId.LIGHTSPEED_SIDE_DASH && action.stageIndex()==3) {
                lockedFacing=direction(path.corner(),path.end());
                host.faceControlled(lockedFacing);
            }
        }
        int index=action.stageIndex();
        int offset=skill.integerList("ranged_counter.attack_event_offsets").get(index);
        String occurrence; String damage; Kind kind=Kind.PHYSICAL; boolean sweep=false;
        switch(action.actionId()) {
            case GRAVITY_DIVE -> { occurrence=index==0?"sword":"impact"; damage=index==0?"sword_damage":"impact_damage"; }
            case SPIRAL_ASSAULT -> { occurrence=index==0?"spin":"slam"; damage=index==0?"spin_damage":"slam_damage"; sweep=index==0; }
            case LIGHTSPEED_DASH -> {
                occurrence=index<4?"clone_"+index:index==4?"body":"trail";
                damage=index<4?"clone_damage":index==4?"body_damage":"trail_damage";
                kind=index==4?Kind.PHYSICAL:Kind.HOLY; sweep=index==4;
            }
            case LIGHTSPEED_SIDE_DASH -> { occurrence=index<3?"clone_"+index:"body"; damage=index<3?"clone_damage":"body_damage"; kind=index<3?Kind.HOLY:Kind.PHYSICAL; }
            default -> throw new IllegalStateException("Unsupported ranged dash");
        }
        if(action.phaseTick()<offset || !sweep && action.phaseTick()!=offset) return;
        var strike=announced(occurrence);
        if(strike==null) return;
        var travelled=host.boss().getBoundingBox().minmax(host.boss().getBoundingBox().move(before.subtract(host.boss().position()))).inflate(
                activeTuning.scaleRange(skill.numbers().getOrDefault("width",3.0))*0.65,0.5,activeTuning.scaleRange(skill.numbers().getOrDefault("width",3.0))*0.65);
        boolean movingSweep=sweep;
        double baseY = kind == Kind.PHYSICAL ? Math.min(before.y, host.boss().getY()) : strike.baseY();
        double height = host.boss().getBbHeight() + (kind == Kind.PHYSICAL ? Math.abs(host.boss().getY() - before.y) : 0);
        hit(action.sequence(),occurrence,volume(strike.shape(),baseY,height),hit(skill.damage(damage),kind,kind==Kind.PHYSICAL),
                target->!movingSweep || target.getBoundingBox().intersects(travelled),outcomes);
        if(kind==Kind.PHYSICAL && !(action.actionId()==PromisedConsortActionId.SPIRAL_ASSAULT && index==0)
            && !(action.actionId()==PromisedConsortActionId.GRAVITY_DIVE && index==1)
            && once(action,"ranged_echo:"+occurrence)) {
            double echoRange=action.actionId()==PromisedConsortActionId.LIGHTSPEED_SIDE_DASH?4
                :action.actionId()==PromisedConsortActionId.SPIRAL_ASSAULT?3.5:skill.number("range");
            scheduleSwordEcho(action,occurrence,echoRange);
        }
    }

    private void executeReprisal(PromisedConsortActionSnapshot action,PromisedConsortSkillConfigSnapshot.Skill skill,List<PromisedConsortHitOutcome> outcomes) {
        if(action.stageIndex()!=2 || action.actionPhase()!=ActionPhase.ACTIVE) return;
        var strike=announced("reprisal");
        if(strike==null) return;
        Vec3 origin=lockedPoints.getOrDefault("ranged_origin",host.boss().position()),end=lockedPoints.getOrDefault("ranged_end",origin);
        int active=catalog.timeline(action).stages().get(2).activeTicks();
        double from=(double)action.phaseTick()/active,to=(double)(action.phaseTick()+1)/active;
        Vec3 start=origin.lerp(end,from),finish=origin.lerp(end,to);
        var wave=new DirectionalRectangle(new Vec2(start.x,start.z),facing(),Math.max(0.001,start.distanceTo(finish)),activeTuning.scaleRange(skill.number("counterattack.width")));
        double multiplier=host.boss() instanceof com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity boss?boss.rangedReturnMultiplier():1;
        var base=skill.damage("counterattack.damage");
        hit(action.sequence(),"reprisal",volume(strike.shape(),strike.baseY(),activeTuning.scaleRange(skill.number("counterattack.height"))),
                hit(new DamageFormula(base.flat()*multiplier,base.attackRatio()*multiplier),Kind.PHYSICAL,true,skill.integer("counterattack.max_hits_per_target")),
                target->wave.contains(target.getX(),target.getZ()),outcomes);
    }

    private int activeTicks(PromisedConsortActionSnapshot action) {
        return catalog.get(action.actionId()).timeline().stages().get(action.stageIndex()).activeTicks();
    }

    private static int ticks(PromisedConsortSkillConfigSnapshot.Skill skill, int ticks) {
        return skill.tuning().scaleTicks(ticks);
    }

    private void gravityDive(
            PromisedConsortActionSnapshot action,
            PromisedConsortSkillConfigSnapshot.Skill skill,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        Vec3 landing = lockedPoints.get("dive_landed");
        if (landing == null) return;
        var timeline = catalog.timeline(action);
        int swordTick = timeline.activeStartTick(0) + (action.rangedCounter() ? skill.integerList("ranged_counter.attack_event_offsets").get(0) : 0);
        int impactTick = timeline.stages().size() > 1 ? timeline.activeStartTick(1)
            + (action.rangedCounter() ? skill.integerList("ranged_counter.attack_event_offsets").get(1) : 0) : swordTick + ticks(skill, 2);
        if (action.actionTick() == swordTick) {
            hitCircleAt(action, "sword", landing, skill.number("range") * 0.65,
                hit(skill.damage("sword_damage"), Kind.PHYSICAL, true), outcomes);
            scheduleSwordEcho(action, "sword", skill.number("range"));
        }
        if (action.actionTick() == impactTick) hitCircleAt(action, "impact", landing, skill.number("range"),
            hit(skill.damage("impact_damage"), Kind.PHYSICAL, false), outcomes);
    }

    private void comboSectors(
            PromisedConsortActionSnapshot action,
            PromisedConsortSkillConfigSnapshot.Skill skill,
            String prefix,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        if (action.actionPhase() != ActionPhase.ACTIVE) {
            return;
        }
        DamageFormula damage = skill.damageList("damage").get(action.stageIndex());
        hitSector(action, prefix + "_" + action.stageIndex(), skill.number("range"),
                action.actionId() == PromisedConsortActionId.R_COMBO_CROSS ? 140.0 : DEFAULT_SECTOR_DEGREES,
                hit(damage, Kind.PHYSICAL, true), outcomes);
    }

    private void bloodflame(
            PromisedConsortActionSnapshot action,
            PromisedConsortSkillConfigSnapshot.Skill skill,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        var timeline = catalog.get(action.actionId()).timeline();
        if (timeline.stages().size() > 2 && action.stageIndex() > 1) return;
        if (!activeStart(action)) {
            return;
        }
        if (action.stageIndex() == 0) {
            hitCapsule(action, "thrust", skill.number("thrust_range"), 1.2,
                    hit(skill.damage("thrust_damage"), Kind.PHYSICAL, true), outcomes);
            return;
        }
        hitSector(action, "sweep", skill.number("sweep_range"), DEFAULT_SECTOR_DEGREES,
                hit(skill.damage("sweep_damage"), Kind.PHYSICAL, true), outcomes);
        scheduleHazard(
                action,
                "bloodflame",
                rectangle(skill.number("sweep_range"), 1.5),
                timeline.stages().size() > 2 ? timeline.activeStartTick(2) - action.actionTick() : ticks(skill, skill.integer("burst_tick")),
                timeline.stages().size() > 2 ? timeline.activeEndTick(2) - action.actionTick() : skill.integer("fissure_lifetime_ticks"),
                hit(skill.damage("burst_damage"), Kind.FIRE, false)
        );
    }

    private void tempest(
            PromisedConsortActionSnapshot action,
            PromisedConsortSkillConfigSnapshot.Skill skill,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        if (action.stageIndex() < 3 && activeStart(action)) {
            hitSector(action, "opening_" + action.stageIndex(), skill.number("range"),
                    DEFAULT_SECTOR_DEGREES,
                    hit(skill.damage("opening_damage"), Kind.PHYSICAL, true), outcomes);
        } else if (action.stageIndex() >= 3 && action.actionPhase() == ActionPhase.ACTIVE) {
            if (catalog.get(action.actionId()).timeline().stages().size() > 4) {
                if (activeStart(action)) {
                    String occurrence = "tempest_" + (action.stageIndex() - 3);
                    hitAnnulus(action, occurrence, 0.9, skill.number("range"),
                            hit(skill.damage("tempest_damage"), Kind.PHYSICAL, true), outcomes);
                    scheduleSwordEcho(action, occurrence, skill.number("range"));
                }
                return;
            }
            int activeTicks = activeTicks(action);
            int tempestHits = Math.min(activeTicks, Math.max(1, skill.integer("tempest_hits")));
            for (int index = 0; index < tempestHits; index++) {
                if (action.phaseTick() != index * activeTicks / tempestHits) {
                    continue;
                }
                hitAnnulus(action, "tempest_" + index, 0.9, skill.number("range"),
                        hit(skill.damage("tempest_damage"), Kind.PHYSICAL, true), outcomes);
                scheduleSwordEcho(action, "tempest_" + index, skill.number("range"));
            }
        }
    }

    private void earthheave(
            PromisedConsortActionSnapshot action,
            PromisedConsortSkillConfigSnapshot.Skill skill,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        if (!activeStart(action)) {
            return;
        }
        if (action.stageIndex() < 3) {
            hitSector(action, "opening_" + action.stageIndex(), 4.2, DEFAULT_SECTOR_DEGREES,
                    hit(skill.damage("opening_damage"), Kind.PHYSICAL, true), outcomes);
        } else if (action.stageIndex() == 3) {
            hitCircle(action, "slam", 4.0,
                    hit(skill.damage("slam_damage"), Kind.PHYSICAL, true), outcomes);
            scheduleSwordEcho(action, "slam", 4.0);
        } else {
            hitRectangle(action, "fissure", skill.number("range"), 5.0,
                    hit(skill.damage("fissure_damage"), Kind.PHYSICAL, false), outcomes);
        }
    }

    private void lionClaw(
            PromisedConsortActionSnapshot action,
            PromisedConsortSkillConfigSnapshot.Skill skill,
            boolean followup,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        if (!activeStart(action)) {
            return;
        }
        if (lockedPoints.containsKey("lion_origin")) {
            if (!lockedPoints.containsKey("lion_landed") || host.boss().position().distanceTo(lockedPoints.get("lion_end")) > 0.05) return;
        } else moveTowardLocked("target", 6.0);
        hitCircle(action, followup ? "double" : "slam", skill.number("range"),
                hit(skill.damage(followup ? "double_damage" : "damage"), Kind.PHYSICAL, true),
                outcomes);
        scheduleSwordEcho(action, followup ? "double" : "slam", skill.number("range"));
    }

    private void starcaller(
            PromisedConsortActionSnapshot action,
            PromisedConsortSkillConfigSnapshot.Skill skill,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        if (action.actionPhase() != ActionPhase.ACTIVE) {
            return;
        }
        boolean components = catalog.get(action.actionId()).timeline().stages().size() > 1;
        if (!components || action.stageIndex() == 0 || activeStart(action)) pullTargets(skill.number("pull_radius"), skill.number("max_pull_per_tick"));
        if (components ? action.stageIndex() == 1 && activeStart(action) : action.phaseTick() == activeTicks(action) - 1) {
            if (action.phase() == PromisedConsortPhase.PHASE_TWO) {
                hitCapsuleFacing(action, "clone_cross_0", host.boss().position(), facing(),
                        skill.number("impact_radius") * 2.0, 1.6,
                        hit(skill.damage("clone_damage"), Kind.HOLY, false), outcomes);
                hitCapsuleFacing(action, "clone_cross_1", host.boss().position(),
                        new Vec2(-facing().z(), facing().x()),
                        skill.number("impact_radius") * 2.0, 1.6,
                        hit(skill.damage("clone_damage"), Kind.HOLY, false), outcomes);
            }
            hitGroundImpact(
                action,
                "impact",
                skill.number("impact_radius"),
                skill.number("jump_avoid_height"),
                hit(skill.damage("impact_damage"), Kind.PHYSICAL, false),
                outcomes
            );
            hitAnnulus(action, "spikes", 1.5, skill.number("impact_radius") + 2.0,
                    hit(skill.damage("spike_damage"), Kind.MAGIC, false), outcomes);
        }
    }

    private void gravityMeteor(
            PromisedConsortActionSnapshot action,
            PromisedConsortSkillConfigSnapshot.Skill skill,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        var timeline = catalog.get(action.actionId()).timeline();
        if (timeline.stages().size() > 1) {
            var sequence = PromisedConsortMeteorSequence.from(timeline);
            if (sequence.usable()) {
                if (action.actionTick() == sequence.riseTick() && once(action, "meteor_held_rocks")) {
                    host.prepareGravityRocks(action, sequence.riseTick(), timeline);
                    lockedPoints.put("meteor_rocks_prepared", Vec3.ZERO);
                }
                if (action.actionTick() == sequence.groundTick()) {
                    hitSector(action, "meteor_ground", skill.numbers().getOrDefault("sword_range", 4.5), 140,
                            hit(skill.damage().getOrDefault("sword_damage", new DamageFormula(4, 0.8)), Kind.PHYSICAL, true), outcomes);
                }
                if (action.phase() == PromisedConsortPhase.PHASE_TWO && action.actionTick() == sequence.landingTick()
                        && lockedPoints.containsKey("meteor_cast_landed")) {
                    hitCircleAt(action, "meteor_body", lockedPoints.get("meteor_cast_landed"), skill.numbers().getOrDefault("body_range", 4.5),
                            hit(skill.damage().getOrDefault("body_damage", new DamageFormula(5, 0.8)), Kind.PHYSICAL, true), outcomes);
                    scheduleSwordEcho(action, "meteor_body", skill.numbers().getOrDefault("body_range", 4.5));
                }
            }
            int rocks = timeline.stages().size() - 4;
            if (action.phase() == PromisedConsortPhase.PHASE_TWO
                    && action.actionTick() == Math.max(0, timeline.activeStartTick(rocks) - 5)) lockTarget("clone_meteor_0");
            if (!activeStart(action)) return;
            if (action.stageIndex() < rocks) {
                if (!lockedPoints.containsKey("meteor_rocks_prepared")) host.spawnGravityRock(action, action.stageIndex(), skill.number("projectile_health"),
                    skill.integer("projectile_lifetime_ticks"), skill.number("max_turn_degrees_per_tick"), skill.damage("damage"),
                    skill.integer("max_hits_per_target"), rocks);
            }
            else if (action.phase() == PromisedConsortPhase.PHASE_TWO) {
                int clone = action.stageIndex() - rocks;
                String pointId = "clone_meteor_" + clone;
                hitCircleAt(action, pointId, lockedPoints.getOrDefault(pointId, host.boss().position()), skill.number("clone_radius"),
                    hit(skill.damage("clone_damage"), Kind.HOLY, false), outcomes);
                if (clone < 3) lockTarget("clone_meteor_" + (clone + 1));
            }
            return;
        }
        boolean cloneSequence = action.phase() == PromisedConsortPhase.PHASE_TWO;
        if (cloneSequence
                && action.actionPhase() == ActionPhase.ACTIVE
                && action.phaseTick() == Math.max(0, activeTicks(action) - ticks(skill, 5))) {
            lockTarget("clone_meteor_0");
        }
        if (cloneSequence
                && action.actionPhase() == ActionPhase.RECOVERY
                && action.phaseTick() % ticks(skill, 5) == 0
                && action.phaseTick() <= ticks(skill, 15)) {
            int clone = action.phaseTick() / ticks(skill, 5);
            String pointId = "clone_meteor_" + clone;
            Vec3 point = lockedPoints.getOrDefault(pointId, host.boss().position());
            hitCircleAt(action, pointId, point, skill.number("clone_radius"),
                    hit(skill.damage("clone_damage"), Kind.HOLY, false), outcomes);
            if (clone < 3) {
                lockTarget("clone_meteor_" + (clone + 1));
            }
            return;
        }
        if (action.actionPhase() != ActionPhase.ACTIVE) {
            return;
        }
        int interval = Math.max(1, activeTicks(action) / skill.integer("projectile_count"));
        if (action.phaseTick() % interval == 0
                && action.phaseTick() / interval < skill.integer("projectile_count")) {
            host.spawnGravityRock(
                    action,
                    action.phaseTick() / interval,
                    skill.number("projectile_health"),
                    skill.integer("projectile_lifetime_ticks"),
                    skill.number("max_turn_degrees_per_tick"),
                    skill.damage("damage"),
                    skill.integer("max_hits_per_target"),
                    skill.integer("projectile_count")
            );
        }
    }

    private void stomp(
            PromisedConsortActionSnapshot action,
            PromisedConsortSkillConfigSnapshot.Skill skill,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        if (activeStart(action)) {
            hitRectangle(action, "stomp", skill.number("forward_range"), skill.number("width"),
                    hit(skill.damage("damage"), Kind.PHYSICAL, false), outcomes);
        }
    }

    private void crossSlash(
            PromisedConsortActionSnapshot action,
            PromisedConsortSkillConfigSnapshot.Skill skill,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        if (catalog.get(action.actionId()).timeline().stages().size() > 1) {
            if (!activeStart(action)) return;
            if (action.stageIndex() == 0) hitSector(action, "sword", skill.number("sword_range"), 140.0,
                hit(skill.damage("sword_damage"), Kind.PHYSICAL, true), outcomes);
            else hitCapsule(action, "debris", skill.number("debris_range"), 2.0, hit(skill.damage("debris_damage"), Kind.PHYSICAL, false), outcomes);
            return;
        }
        if (activeStart(action)) {
            hitSector(action, "sword", skill.number("sword_range"), 140.0,
                    hit(skill.damage("sword_damage"), Kind.PHYSICAL, true), outcomes);
        }
        if (activeTick(action, ticks(skill, 2))) {
            hitCapsule(action, "debris", skill.number("debris_range"), 2.0,
                    hit(skill.damage("debris_damage"), Kind.PHYSICAL, false), outcomes);
        }
    }

    private void spiral(
            PromisedConsortActionSnapshot action,
            PromisedConsortSkillConfigSnapshot.Skill skill,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        if (action.actionPhase() != ActionPhase.ACTIVE || !lockedPoints.containsKey("advance_landed")) return;
        var timeline = catalog.timeline(action);
        boolean opening = action.stageIndex() == 0 && (timeline.stages().size() > 1 || action.phaseTick() == 0);
        int offset = action.rangedCounter() ? skill.integerList("ranged_counter.attack_event_offsets").get(action.stageIndex())
                : timeline.stages().size() == 1 && !opening ? activeTicks(action) - 1 : 0;
        if (action.phaseTick() != offset) return;
        Vec3 landing = lockedPoints.get("advance_landed");
        if (opening) hitSector(action, "spin", skill.number("range"), 140,
                hit(skill.damage("spin_damage"), Kind.PHYSICAL, true), outcomes);
        else {
            hitCircleAt(action, "slam", landing, 3.5, hit(skill.damage("slam_damage"), Kind.PHYSICAL, true), outcomes);
            scheduleSwordEcho(action, "slam", 3.5);
        }
    }

    private void lightOfMiquella(
            PromisedConsortActionSnapshot action,
            PromisedConsortSkillConfigSnapshot.Skill skill,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        var timeline = catalog.get(action.actionId()).timeline();
        if (timeline.stages().size() > 1 && action.stageIndex() != 0) return;
        if (action.actionPhase() != ActionPhase.ACTIVE) {
            return;
        }
        Vec3 center = lockedPoints.getOrDefault("target", host.boss().position());
        hitCircleAt(action, "main", center, skill.number("radius"),
                hit(skill.damage("main_damage"), Kind.HOLY, false), outcomes);
        if (!activeStart(action)) {
            return;
        }
        int afterglows = timeline.stages().size() > 1 ? timeline.stages().size() - 1 : skill.integer("afterglow_count");
        for (int index = 0; index < afterglows; index++) {
            double angle = randomUnit(action.seed(), index) * Math.PI * 2.0;
            double radius = randomUnit(action.seed() ^ 0x9E3779B97F4A7C15L, index)
                    * activeTuning.scaleRange(skill.number("radius"));
            Vec3 point = center.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            int activeDelay = timeline.stages().size() > 1 ? timeline.activeStartTick(index + 1) - action.actionTick()
                : activeTicks(action) + ticks(skill, index + 4);
            scheduleHazardAt(
                    action,
                    "afterglow_" + index,
                    new Circle(point.x, point.z, activeTuning.scaleRange(1.0)),
                    point.y,
                    2.5,
                    activeDelay,
                    activeDelay + (timeline.stages().size() > 1 ? timeline.stages().get(index + 1).activeTicks() : 1),
                    hit(skill.damage("afterglow_damage"), Kind.HOLY, false)
            );
        }
    }

    private void ringOfLight(
            PromisedConsortActionSnapshot action,
            PromisedConsortSkillConfigSnapshot.Skill skill,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        if (action.actionPhase() != ActionPhase.ACTIVE) {
            return;
        }
        double progress = (action.phaseTick() + 1.0) / activeTicks(action);
        double outer = skill.number("inner_radius")
                + (skill.number("outer_radius") - skill.number("inner_radius")) * progress;
        double inner = Math.max(0.0, outer - 1.5);
        hitAnnulus(action, "ring", inner, outer,
                hit(skill.damage("damage"), Kind.HOLY, false, 1), outcomes);
    }

    private void lightspeedSlash(
            PromisedConsortActionSnapshot action,
            PromisedConsortSkillConfigSnapshot.Skill skill,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        if (catalog.get(action.actionId()).timeline().stages().size() > 1) {
            stagedLightspeed(action, skill, outcomes);
            return;
        }
        if (action.actionPhase() != ActionPhase.ACTIVE) {
            return;
        }
        int cloneCount = skill.integer("clone_count");
        int interval = Math.max(1, activeTicks(action) / (cloneCount + 1));
        if (action.phaseTick() % interval == 0 && action.phaseTick() / interval < cloneCount) {
            int clone = action.phaseTick() / interval;
            hitCapsule(action, "clone_" + clone, 10.0, 1.6,
                    hit(skill.damage("clone_damage"), Kind.HOLY, false), outcomes);
        }
        if (action.phaseTick() == activeTicks(action) - 1) {
            hitCapsule(action, "body", 8.0, 2.0,
                    hit(skill.damage("body_damage"), Kind.PHYSICAL, true), outcomes);
        }
    }

    private void lightspeedDash(
            PromisedConsortActionSnapshot action,
            PromisedConsortSkillConfigSnapshot.Skill skill,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        if (catalog.get(action.actionId()).timeline().stages().size() > 1) {
            stagedLightspeed(action, skill, outcomes);
            return;
        }
        if (action.actionPhase() != ActionPhase.ACTIVE) {
            return;
        }
        moveForward(Math.min(MAX_CONTROLLED_MOVEMENT_PER_TICK,
                skill.number("range") / activeTicks(action)));
        int cloneInterval = ticks(skill, 4);
        if (action.phaseTick() % cloneInterval == 0
                && action.phaseTick() < ticks(skill, 16)) {
            int clone = action.phaseTick() / cloneInterval;
            hitCapsule(action, "clone_" + clone, skill.number("range"), skill.number("width"),
                    hit(skill.damage("clone_damage"), Kind.HOLY, false), outcomes);
        }
        if (action.phaseTick() == activeTicks(action) - 1) {
            hitCapsule(action, "body", skill.number("range"), skill.number("width"),
                    hit(skill.damage("body_damage"), Kind.PHYSICAL, true), outcomes);
            scheduleHazard(action, "trail", rectangle(skill.number("range"), skill.number("width")),
                    ticks(skill, 2), ticks(skill, 3),
                    hit(skill.damage("trail_damage"), Kind.HOLY, false));
        }
    }

    private void lightspeedSideDash(
            PromisedConsortActionSnapshot action,
            PromisedConsortSkillConfigSnapshot.Skill skill,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        if (catalog.get(action.actionId()).timeline().stages().size() > 1) {
            stagedLightspeed(action, skill, outcomes);
            return;
        }
        if (action.actionPhase() != ActionPhase.ACTIVE) {
            return;
        }
        moveSide(6.0 / activeTicks(action));
        int cloneCount = skill.integer("clone_count");
        int interval = Math.max(1, activeTicks(action) / (cloneCount + 1));
        if (action.phaseTick() % interval == 0 && action.phaseTick() / interval < cloneCount) {
            int clone = action.phaseTick() / interval;
            hitCapsule(action, "clone_" + clone, 9.0, 1.6,
                    hit(skill.damage("clone_damage"), Kind.HOLY, false), outcomes);
        }
        if (action.phaseTick() == activeTicks(action) - 1) {
            hitSector(action, "body", 4.0, 140.0,
                    hit(skill.damage("body_damage"), Kind.PHYSICAL, true), outcomes);
        }
    }

    private boolean moveLightspeedBody(PromisedConsortActionSnapshot action) {
        if (lockedPoints.containsKey("lightspeed_cancelled")) return false;
        var path = PromisedConsortLightspeedPath.from(catalog.timeline(action), config.instantGuard().defaultCueLeadTicks());
        if (!path.usable()) return true;
        if (!once(action, "lightspeed_move:" + action.actionTick())) return true;
        var boss = host.boss();
        Vec3 origin = lockedPoints.computeIfAbsent("lightspeed_origin", unused -> boss.position());
        if (!lockedPoints.containsKey("lightspeed_frozen")) {
            var target = host.currentTarget().orElse(null);
            if (target == null || !target.isAlive() || !host.insideArena(target)) return cancelLightspeedBody();
            lockedFacing = direction(origin, target.position()).normalizedOr(facing());
            host.faceControlled(lockedFacing);
            Vec3 end = origin.add(lockedFacing.x() * catalog.skill(action).number("range"), 0,
                    lockedFacing.z() * catalog.skill(action).number("range"));
            lockedPoints.put("lightspeed_end", end);
            if (action.actionTick() >= path.lockTick()) {
                if (!path.supports(origin.distanceTo(end))) return cancelLightspeedBody();
                int segments = Math.max(1, (int) Math.ceil(origin.distanceTo(end) / 0.25));
                if (segments > 1024) return cancelLightspeedBody();
                Vec3 previousGround = origin;
                for (int index = 0; index <= segments; index++) {
                    Vec3 point = origin.lerp(end, (double) index / segments);
                    if (point.subtract(host.combatCenter()).horizontalDistance() > config.arena().logicalRadius()
                        || !host.serverLevel().hasChunkAt(net.minecraft.core.BlockPos.containing(point))) return cancelLightspeedBody();
                    Vec3 ground = PromisedConsortGroundMovement.destination(host.serverLevel(), boss, previousGround, point);
                    if (ground == null) return cancelLightspeedBody();
                    previousGround = ground;
                }
                end = previousGround;
                lockedPoints.put("lightspeed_end", end);
                lockedPoints.put("lightspeed_frozen", end);
            }
        }
        if (action.actionTick() <= path.departureTick() || action.actionTick() > path.impactTick()) return true;
        Vec3 destination = path.at(origin, lockedPoints.get("lightspeed_end"), action.actionTick());
        Vec3 movement = destination.subtract(boss.position()).multiply(1, 0, 1);
        if (movement.length() > 8) return cancelLightspeedBody();
        int segments = Math.max(1, (int) Math.ceil(movement.length() / 0.25));
        for (int index = 0; index < segments; index++) {
            Vec3 step = movement.scale(1.0 / segments);
            if (!moveAlongGround(boss.position().add(step))) return cancelLightspeedBody();
        }
        boss.setDeltaMovement(Vec3.ZERO);
        if (boss.position().subtract(destination).horizontalDistance() > 0.02) return cancelLightspeedBody();
        if (action.actionTick() == path.impactTick()) lockedPoints.put("lightspeed_arrived", boss.position());
        return true;
    }

    private boolean cancelLightspeedBody() {
        lockedPoints.put("lightspeed_cancelled", Vec3.ZERO);
        cancelPendingHazards();
        if (host.boss() instanceof com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity boss) boss.cancelRangedAction();
        return false;
    }

    private void stagedLightspeed(PromisedConsortActionSnapshot action, PromisedConsortSkillConfigSnapshot.Skill skill,
                                  List<PromisedConsortHitOutcome> outcomes) {
        if (action.actionPhase() != ActionPhase.ACTIVE) return;
        var timeline = catalog.get(action.actionId()).timeline();
        boolean dash = action.actionId() == PromisedConsortActionId.LIGHTSPEED_DASH;
        boolean side = action.actionId() == PromisedConsortActionId.LIGHTSPEED_SIDE_DASH;
        int clones = timeline.stages().size() - (dash ? 2 : 1);
        int index = action.stageIndex();
        if (index <= clones) {
            int movementTicks = timeline.activeTicksBetween(0, timeline.activeEndTick(clones));
            if (dash && !lockedPoints.containsKey("lightspeed_origin")) moveForward(Math.min(MAX_CONTROLLED_MOVEMENT_PER_TICK, skill.number("range") / movementTicks));
            else if (side) moveSide(6.0 / movementTicks);
        }
        if (!activeStart(action)) return;
        if (index < clones) hitCapsule(action, "clone_" + index, dash ? skill.number("range") : side ? 9.0 : 10.0,
            dash ? skill.number("width") : 1.6, hit(skill.damage("clone_damage"), Kind.HOLY, false), outcomes);
        else if (index == clones) {
            if (dash && lockedPoints.containsKey("lightspeed_origin") && !lockedPoints.containsKey("lightspeed_arrived")) return;
            if (side) hitSector(action, "body", 4.0, 140.0, hit(skill.damage("body_damage"), Kind.PHYSICAL, true), outcomes);
            else hitCapsule(action, "body", dash ? skill.number("range") : 8.0, dash ? skill.number("width") : 2.0,
                hit(skill.damage("body_damage"), Kind.PHYSICAL, true), outcomes);
            if (dash) {
                int delay = timeline.activeStartTick(clones + 1) - action.actionTick();
                scheduleHazard(action, "trail", rectangle(skill.number("range"), skill.number("width")), delay,
                    delay + timeline.stages().get(clones + 1).activeTicks(), hit(skill.damage("trail_damage"), Kind.HOLY, false));
            }
        }
    }

    private void promisedConsort(
            PromisedConsortActionSnapshot action,
            PromisedConsortSkillConfigSnapshot.Skill skill,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        if (catalog.get(action.actionId()).timeline().stages().size() > 1) {
            if (!activeStart(action)) return;
            int index = action.stageIndex();
                if (action.actionId() == PromisedConsortActionId.CROSS_LEAP_COMBO
                    && (index == 0 && !lockedPoints.containsKey("cross_opening_landed")
                    || index == 4 && !lockedPoints.containsKey("cross_finisher_landed"))) return;
            double openingRange = skill.numbers().getOrDefault("opening_range", 4.2);
            double spinRange = skill.numbers().getOrDefault("spin_range", 4.5);
            double finisherRange = skill.numbers().getOrDefault("finisher_range", 5.0);
            if (index < 2) hitSector(action, "opening_" + index, openingRange, 140.0, hit(skill.damage("opening_damage"), Kind.PHYSICAL, true), outcomes);
            else if (index < 4) {
                String occurrence = "spin_" + (index - 2);
                hitAnnulus(action, occurrence, 0.9, spinRange, hit(skill.damage("spin_damage"), Kind.PHYSICAL, true), outcomes);
                scheduleSwordEcho(action, occurrence, spinRange);
            } else if (index == 4) {
                hitCircle(action, "finisher", finisherRange, hit(skill.damage("finisher_damage"), Kind.PHYSICAL, true), outcomes);
                scheduleSwordEcho(action, "finisher", finisherRange);
            } else if (index < 7) hitCapsuleFacing(action, "clone_return_" + (index - 5), host.boss().position(), rotate(facing(), index == 5 ? 45 : -45),
                10.0, 1.6, hit(skill.damage("clone_damage"), Kind.HOLY, false), outcomes);
            else scheduleHazard(action, "holy_ring", new Annulus(new Vec2(host.boss().getX(), host.boss().getZ()),
                activeTuning.scaleRange(2.0), activeTuning.scaleRange(7.0)), 0, 1, hit(skill.damage("holy_ring_damage"), Kind.HOLY, false));
            return;
        }
        if (action.actionPhase() != ActionPhase.ACTIVE) {
            return;
        }
        int tick = action.phaseTick();
        if (tick == 0 || tick == ticks(skill, 8)) {
            hitSector(action, "opening_" + tick, 4.2, 140.0,
                    hit(skill.damage("opening_damage"), Kind.PHYSICAL, true), outcomes);
        } else if (tick == ticks(skill, 18) || tick == ticks(skill, 28)) {
            hitAnnulus(action, "spin_" + tick, 0.9, 4.5,
                    hit(skill.damage("spin_damage"), Kind.PHYSICAL, true), outcomes);
            scheduleSwordEcho(action, "spin_" + tick, 4.5);
        } else if (tick == ticks(skill, 42)) {
            hitCircle(action, "finisher", 5.0,
                    hit(skill.damage("finisher_damage"), Kind.PHYSICAL, true), outcomes);
            scheduleSwordEcho(action, "finisher", 5.0);
        } else if (tick == ticks(skill, 44) || tick == ticks(skill, 47)) {
            int clone = tick == ticks(skill, 44) ? 0 : 1;
            Vec2 diagonal = rotate(facing(), tick == ticks(skill, 44) ? 45.0 : -45.0);
            hitCapsuleFacing(action, "clone_return_" + clone, host.boss().position(),
                diagonal, 10.0, 1.6,
                hit(skill.damage("clone_damage"), Kind.HOLY, false), outcomes);
        } else if (tick == ticks(skill, 50)) {
            scheduleHazard(action, "holy_ring", new Annulus(
                    new Vec2(host.boss().getX(), host.boss().getZ()),
                    activeTuning.scaleRange(2.0),
                    activeTuning.scaleRange(7.0)),
                0, 1, hit(skill.damage("holy_ring_damage"), Kind.HOLY, false));
        }
    }

    private void enhancedEarthheave(
            PromisedConsortActionSnapshot action,
            PromisedConsortSkillConfigSnapshot.Skill skill,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        var timeline = catalog.get(action.actionId()).timeline();
        if (timeline.stages().size() > 1 && action.stageIndex() != 0) return;
        if (!activeStart(action)) {
            return;
        }
        hitCircle(action, "slam", skill.number("radius"),
                hit(skill.damage("slam_damage"), Kind.PHYSICAL, true), outcomes);
        scheduleSwordEcho(action, "slam", skill.number("radius"));
        hitRectangle(action, "fissure", skill.number("radius") + 2.0, 5.0,
                hit(skill.damage("fissure_damage"), Kind.PHYSICAL, false), outcomes);
        for (int index = 0; index < 4; index++) {
            scheduleHazard(action, "light_" + index,
                    rectangle(skill.number("radius") + 2.0 + index * 1.5, 0.8),
                    timeline.stages().size() > 1 ? timeline.activeStartTick(index + 1) - action.actionTick() : ticks(skill, 3 + index * 2),
                    timeline.stages().size() > 1 ? timeline.activeEndTick(index + 1) - action.actionTick() : ticks(skill, 4 + index * 2),
                    hit(skill.damage("light_damage"), Kind.HOLY, false));
        }
    }

    private void consortMeteor(
            PromisedConsortActionSnapshot action,
            PromisedConsortSkillConfigSnapshot.Skill skill,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        var timeline = catalog.get(action.actionId()).timeline();
        boolean components = timeline.stages().size() > 1;
        int lockTick = components ? timeline.activeStartTick(2) : ticks(skill, 91);
        int impactTick = components ? timeline.activeStartTick(3) : ticks(skill, 121);
        if (action.actionTick() == lockTick) {
            lockPredictedPoint("meteor", skill.integer("prediction_sample_ticks"),
                    skill.integer("prediction_lead_ticks"),
                Math.max(0.0, config.arena().logicalRadius()
                    - activeTuning.scaleRange(skill.number("outer_radius") + 2.0)));
        }
        if (action.actionTick() == impactTick && once(action, "impact")) {
            Vec3 point = lockedPoints.getOrDefault("meteor", host.combatCenter());
            hitRadialBands(
                action,
                point,
                skill.number("core_radius"),
                skill.number("outer_radius"),
                hit(skill.damage("core_damage"), Kind.PHYSICAL, false),
                hit(skill.damage("outer_damage"), Kind.PHYSICAL, false),
                outcomes
            );
            scheduleHazardAt(action, "aftershock",
                    new Annulus(
                        new Vec2(point.x, point.z),
                        activeTuning.scaleRange(skill.number("outer_radius")),
                        activeTuning.scaleRange(skill.number("outer_radius") + 2.0)
                    ),
                    point.y, 3.0, components ? timeline.activeStartTick(4) - impactTick : ticks(skill, 2),
                    components ? timeline.activeEndTick(4) - impactTick : ticks(skill, 3),
                    hit(skill.damage("aftershock_damage"), Kind.HOLY, false));
        }
    }

    private void scheduleSwordEcho(
            PromisedConsortActionSnapshot action,
            String hitId,
            double range
    ) {
        if (action.phase() != PromisedConsortPhase.PHASE_TWO || !config.lightEcho().enabled()) {
            return;
        }
        int maximumColumns = Math.min(
                config.lightEcho().maxLogicalColumns(),
                config.performance().maxLogicalLightColumns()
        );
        if (maximumColumns <= 0) {
            return;
        }
        long activeEchoes = hazards.keySet().stream().filter(id -> id.contains(":echo_")).count();
        if (activeEchoes >= maximumColumns) {
            Iterator<Map.Entry<String, Hazard>> iterator = hazards.entrySet().iterator();
            while (iterator.hasNext() && activeEchoes >= maximumColumns) {
                if (iterator.next().getKey().contains(":echo_")) {
                    iterator.remove();
                    activeEchoes--;
                }
            }
        }
        int activeTicks = catalog.timeline(action)
            .stages().get(action.stageIndex()).activeTicks();
        long delay = activeTicks - action.phaseTick()
            + config.lightEcho().delayTicks()
            + config.lightEcho().telegraphTicks();
        DirectionalRectangle echo = rectangle(Math.max(1.0, range) * PromisedConsortAttackPlan.MELEE_REACH_MULTIPLIER
            * PromisedConsortAttackPlan.closeImpactMultiplier(action.actionId()), config.lightEcho().width());
        scheduleHazardAt(
                action,
                "echo_" + hitId,
                echo,
                host.boss().getY(),
                config.lightEcho().height(),
                Math.toIntExact(delay),
                Math.toIntExact(delay + config.lightEcho().activeTicks()),
                hit(config.lightEcho().damage(), Kind.HOLY, false)
        );
    }

    private void scheduleHazard(
            PromisedConsortActionSnapshot action,
            String id,
            HorizontalShape shape,
            int activeDelay,
            int expiryDelay,
            PromisedConsortHitSpec hit
    ) {
        scheduleHazardAt(action, id, shape, host.boss().getY(), DEFAULT_HEIGHT_MARGIN,
                activeDelay, expiryDelay, hit);
    }

    private void scheduleHazardAt(
            PromisedConsortActionSnapshot action,
            String id,
            HorizontalShape shape,
            double baseY,
            double height,
            int activeDelay,
            int expiryDelay,
            PromisedConsortHitSpec hit
    ) {
        String hazardId = action.sequence() + ":" + id;
        if (hazards.containsKey(hazardId)) {
            return;
        }
        long now = host.gameTime();
        var announced = announced(id);
        long startTick = now;
        long activeTick = now + Math.max(1, activeDelay);
        long endTick = activeTick + Math.max(1, expiryDelay - activeDelay);
        if (announced != null) {
            shape = announced.shape();
            baseY = announced.baseY();
            startTick = announced.startTick();
            activeTick = announced.activeTick();
            endTick = announced.endTick();
        }
        if (startTick >= activeTick) {
            long duration = Math.max(1, endTick - activeTick);
            startTick = now;
            activeTick = now + Math.max(1, config.instantGuard().defaultCueLeadTicks());
            endTick = activeTick + duration;
        }
        hazards.put(hazardId, new Hazard(
                hazardId,
                action.sequence(),
                shape,
                baseY,
                height,
                startTick,
                activeTick,
                Math.max(activeTick + 1, endTick),
                hit,
                new java.util.HashSet<>(),
                new java.util.HashSet<>()
        ));
    }

    private void tickHazards(List<PromisedConsortHitOutcome> outcomes) {
        long gameTick = host.gameTime();
        Iterator<Hazard> iterator = hazards.values().iterator();
        while (iterator.hasNext()) {
            Hazard hazard = iterator.next();
            if (gameTick >= hazard.activeTick() && gameTick < hazard.endTick()) {
                if (PromisedConsortActionSoundPlan.claim(hazard.soundEvents(), "activation", gameTick, hazard.activeTick(), hazard.endTick())) {
                    String occurrence = hazard.id().substring(hazard.id().indexOf(':') + 1);
                    var cue = PromisedConsortActionSoundPlan.effect(occurrence);
                    if (cue != null) {
                        ShapeData shape = ShapeData.from(hazard.shape());
                        host.playActionSound(cue, new Vec3(shape.origin().x(), hazard.baseY(), shape.origin().z()), shape.direction());
                    }
                }
                hitHazard(hazard, outcomes);
            }
            if (gameTick >= hazard.endTick()) {
                iterator.remove();
            }
        }
    }

    private void hitHazard(Hazard hazard, List<PromisedConsortHitOutcome> outcomes) {
        List<LivingEntity> targets = matchingTargets(hazard.volume());
        host.prepareBossHitTargets(targets);
        for (LivingEntity target : targets) {
            if (!hazard.hitTargets().add(target.getUUID())) {
                continue;
            }
            host.damageTarget(target, hazard.actionSequence(), hazard.hit()).ifPresent(outcomes::add);
        }
    }

    private void hitRadialBands(
            PromisedConsortActionSnapshot action,
            Vec3 center,
            double coreRadius,
            double outerRadius,
            PromisedConsortHitSpec coreHit,
            PromisedConsortHitSpec outerHit,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        var core = announced("core");
        var outer = announced("outer");
        if (core == null || outer == null || core.startTick() >= core.activeTick()) return;
        Circle coreShape = (Circle) core.shape();
        Annulus outerShape = (Annulus) outer.shape();
        center = new Vec3(coreShape.center().x(), core.baseY(), coreShape.center().z());
        coreRadius = coreShape.radius();
        outerRadius = outerShape.outerRadius();
        StrikeVolume volume = volume(
                new Circle(center.x, center.z, outerRadius),
                center.y,
                host.boss().getBbHeight()
        );
        List<LivingEntity> targets = matchingTargets(volume);
        host.prepareBossHitTargets(targets);
        int coreIndex = hitIndices.computeIfAbsent("meteor_core", ignored -> hitIndices.size());
        int outerIndex = hitIndices.computeIfAbsent("meteor_outer", ignored -> hitIndices.size());
        for (LivingEntity target : targets) {
            double distance = Math.hypot(target.getX() - center.x, target.getZ() - center.z);
            PromisedConsortHitSpec selected = distance <= coreRadius ? coreHit : outerHit;
            int occurrenceIndex = distance <= coreRadius ? coreIndex : outerIndex;
            if (!hitRegistry.claim(new HitId(action.sequence(), occurrenceIndex), target.getUUID())) {
                continue;
            }
            host.damageTarget(target, action.sequence(), selected).ifPresent(outcomes::add);
        }
    }

    private void hitSector(
            PromisedConsortActionSnapshot action,
            String id,
            double range,
            double degrees,
            PromisedConsortHitSpec hit,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        double echoRange = range;
        range = activeTuning.scaleRange(range);
        Sector shape = new Sector(
                host.boss().getX(), host.boss().getZ(),
                facing().x(), facing().z(), range, Math.toRadians(degrees * 0.5)
        );
        hit(action.sequence(), id, volume(shape, host.boss().getY(), host.boss().getBbHeight()),
                hit, outcomes);
        scheduleSwordEcho(action, id, echoRange);
    }

    private void hitCapsule(
            PromisedConsortActionSnapshot action,
            String id,
            double length,
            double width,
            PromisedConsortHitSpec hit,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        double echoRange = length;
        length = activeTuning.scaleRange(length);
        width = activeTuning.scaleRange(width);
        Vec2 start = new Vec2(host.boss().getX(), host.boss().getZ());
        Vec2 end = start.add(facing().scale(length));
        hit(action.sequence(), id, volume(new Capsule(start, end, width * 0.5),
                host.boss().getY(), host.boss().getBbHeight()), hit, outcomes);
        if (hit.instantGuardEligible()) {
            scheduleSwordEcho(action, id, echoRange);
        }
    }

    private void hitCircle(
            PromisedConsortActionSnapshot action,
            String id,
            double radius,
            PromisedConsortHitSpec hit,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        hitCircleAt(action, id, host.boss().position(), radius, hit, outcomes);
    }

    private void hitCircleAt(
            PromisedConsortActionSnapshot action,
            String id,
            Vec3 center,
            double radius,
            PromisedConsortHitSpec hit,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        radius = activeTuning.scaleRange(radius);
        hit(action.sequence(), id, volume(new Circle(center.x, center.z, radius),
                center.y, host.boss().getBbHeight()), hit, outcomes);
    }

        private void hitGroundImpact(
            PromisedConsortActionSnapshot action,
            String id,
            double radius,
            double jumpAvoidHeight,
            PromisedConsortHitSpec hit,
            List<PromisedConsortHitOutcome> outcomes
        ) {
        radius = activeTuning.scaleRange(radius);
        StrikeVolume volume = volume(
            new Circle(host.boss().getX(), host.boss().getZ(), radius),
            host.boss().getY(),
            host.boss().getBbHeight()
        );
        hit(
            action.sequence(),
            id,
            volume,
            hit,
            target -> jumpAvoidHeight <= 0.0
                || !host.serverLevel().noCollision(
                target,
                target.getBoundingBox().move(0.0, -jumpAvoidHeight, 0.0)
            ),
            outcomes
        );
        }

    private void hitAnnulus(
            PromisedConsortActionSnapshot action,
            String id,
            double inner,
            double outer,
            PromisedConsortHitSpec hit,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        hitAnnulusAt(action, id, host.boss().position(), inner, outer, hit, outcomes);
    }

    private void hitAnnulusAt(
            PromisedConsortActionSnapshot action,
            String id,
            Vec3 center,
            double inner,
            double outer,
            PromisedConsortHitSpec hit,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        inner = activeTuning.scaleRange(inner);
        outer = activeTuning.scaleRange(outer);
        hit(action.sequence(), id, volume(new Annulus(new Vec2(center.x, center.z), inner, outer),
                center.y, host.boss().getBbHeight()), hit, outcomes);
    }

    private void hitRectangle(
            PromisedConsortActionSnapshot action,
            String id,
            double length,
            double width,
            PromisedConsortHitSpec hit,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        hit(action.sequence(), id, volume(rectangle(length, width),
                host.boss().getY(), host.boss().getBbHeight()), hit, outcomes);
    }

        private void hitCapsuleFacing(
            PromisedConsortActionSnapshot action,
            String id,
            Vec3 center,
            Vec2 direction,
            double length,
            double width,
            PromisedConsortHitSpec hit,
            List<PromisedConsortHitOutcome> outcomes
        ) {
        length = activeTuning.scaleRange(length);
        width = activeTuning.scaleRange(width);
        Vec2 half = direction.normalizedOr(new Vec2(0.0, 1.0)).scale(length * 0.5);
        Vec2 origin = new Vec2(center.x, center.z);
        hit(action.sequence(), id, volume(
            new Capsule(origin.subtract(half), origin.add(half), width * 0.5),
            center.y,
            host.boss().getBbHeight()
        ), hit, outcomes);
        }

    private void hit(
            long actionSequence,
            String occurrence,
            StrikeVolume volume,
            PromisedConsortHitSpec spec,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        hit(actionSequence, occurrence, volume, spec, ignored -> true, outcomes);
        }

        private void hit(
            long actionSequence,
            String occurrence,
            StrikeVolume volume,
            PromisedConsortHitSpec spec,
            Predicate<LivingEntity> targetFilter,
            List<PromisedConsortHitOutcome> outcomes
        ) {
            var announced = announced(occurrence);
            if (announced == null || announced.startTick() >= announced.activeTick()
                || host.gameTime() < announced.activeTick() || host.gameTime() >= announced.endTick()) return;
            playImpactSounds(executingAction, occurrence, announced);
            double height = volume.maximumY() - volume.minimumY() - DEFAULT_HEIGHT_MARGIN * 2;
            volume = volume(announced.shape(), announced.baseY(), height);
        PromisedConsortHitSpec resolvedSpec = new PromisedConsortHitSpec(
            occurrence,
            spec.damage(),
            spec.damageKind(),
            spec.instantGuardEligible(),
            spec.maxHitsPerTarget()
        );
        int occurrenceIndex = hitIndices.computeIfAbsent(occurrence, ignored -> hitIndices.size());
        List<LivingEntity> targets = matchingTargets(volume).stream()
            .filter(targetFilter)
            .toList();
        host.prepareBossHitTargets(targets);
        for (LivingEntity target : targets) {
            if (!hitRegistry.claim(new HitId(actionSequence, occurrenceIndex), target.getUUID())) {
                continue;
            }
                int groupIndex = hitIndices.computeIfAbsent("group:" + resolvedSpec.hitId(),
                    ignored -> hitIndices.size());
            if (!hitCounter.claim(actionSequence, groupIndex, target.getUUID(),
                    resolvedSpec.maxHitsPerTarget())) {
                continue;
            }
                host.damageTarget(target, actionSequence, resolvedSpec).ifPresent(outcomes::add);
        }
    }

    private List<LivingEntity> matchingTargets(StrikeVolume volume) {
        List<LivingEntity> targets = host.serverLevel().getEntitiesOfClass(
                LivingEntity.class,
                volume.bounds(),
                target -> target != host.boss()
                        && target.isAlive()
                        && !target.isRemoved()
                        && host.insideArena(target)
                        && !host.isAttackImmune(target)
                        && intersectsY(target, volume)
                        && contains(volume.shape(), target)
        );
        targets.sort(Comparator.comparingDouble(host.boss()::distanceToSqr));
        return targets;
    }

    private void pullTargets(double radius, double maximumStep) {
        radius = activeTuning.scaleRange(radius);
        StrikeVolume volume = volume(
                new Circle(host.boss().getX(), host.boss().getZ(), radius),
                host.boss().getY(),
                host.boss().getBbHeight() + DEFAULT_HEIGHT_MARGIN
        );
        List<LivingEntity> targets = matchingTargets(volume);
        host.prepareBossHitTargets(targets);
        for (LivingEntity target : targets) {
            Vec3 offset = host.boss().position().subtract(target.position());
            Vec3 horizontal = new Vec3(offset.x, 0.0, offset.z);
            if (horizontal.lengthSqr() == 0.0) {
                continue;
            }
            double step = Math.min(maximumStep, horizontal.length());
            Vec3 movement = horizontal.normalize().scale(step);
            target.setDeltaMovement(
                    target.getDeltaMovement().add(movement.x, 0.0, movement.z)
            );
            target.hurtMarked = true;
        }
    }

    private void moveTowardLocked(String pointId, double maximumTravel) {
        maximumTravel = activeTuning.scaleRange(maximumTravel);
        Vec3 target = lockedPoints.get(pointId);
        if (target == null) {
            return;
        }
        Vec3 offset = target.subtract(host.boss().position());
        double horizontalLength = Math.hypot(offset.x, offset.z);
        if (horizontalLength == 0.0) {
            return;
        }
        double travel = Math.min(Math.min(maximumTravel, MAX_CONTROLLED_MOVEMENT_PER_TICK),
                horizontalLength);
        moveAlongGround(host.boss().position().add(offset.x / horizontalLength * travel, 0.0,
            offset.z / horizontalLength * travel));
    }

    private void moveForward(double distance) {
        distance = Math.min(
                MAX_CONTROLLED_MOVEMENT_PER_TICK,
                activeTuning.scaleRange(distance)
        );
        moveAlongGround(host.boss().position().add(facing().x() * distance, 0.0, facing().z() * distance));
    }

    private void moveSide(double distance) {
        distance = activeTuning.scaleRange(distance);
        Vec2 direction = sideDashDirection(facing());
        double checked = Math.min(Math.abs(distance), MAX_CONTROLLED_MOVEMENT_PER_TICK);
        moveAlongGround(host.boss().position().add(direction.x() * checked, 0.0, direction.z() * checked));
    }

    private boolean moveAlongGround(Vec3 destination) {
        var boss = host.boss();
        Vec3 origin = boss.position();
        if (destination.subtract(host.combatCenter()).horizontalDistance() > config.arena().logicalRadius()) return false;
        Vec3 ground = PromisedConsortGroundMovement.destination(host.serverLevel(), boss, origin, destination);
        if (ground == null) return false;
        double rise = ground.y - origin.y;
        if (rise > 0) host.moveControlled(new Vec3(0, rise, 0));
        Vec3 horizontal = ground.subtract(boss.position()).multiply(1, 0, 1);
        int steps = Math.max(1, (int) Math.ceil(horizontal.length() / 0.25));
        for (int index = 0; index < steps; index++) host.moveControlled(horizontal.scale(1.0 / steps));
        if (rise < 0) host.moveControlled(new Vec3(0, rise, 0));
        return boss.position().distanceTo(ground) < 0.02;
    }

    public static Vec2 sideDashDirection(Vec2 facing) {
        Vec2 forward = facing.normalizedOr(new Vec2(0, 1));
        return new Vec2(forward.x() - forward.z(), forward.z() + forward.x()).normalizedOr(forward);
    }

    private void lockTarget(String id) {
        host.currentTarget().ifPresent(target -> lockedPoints.put(id, target.position()));
    }

    private void lockPredictedPoint(
            String id,
            int sampleTicks,
            int leadTicks,
            double maximumCenterDistance
    ) {
        host.predictedTargetPoint(sampleTicks, leadTicks).ifPresent(predicted -> {
            Vec3 point = predicted;
            Vec3 center = host.combatCenter();
            Vec3 horizontal = new Vec3(point.x - center.x, 0.0, point.z - center.z);
            if (horizontal.length() > maximumCenterDistance) {
                horizontal = horizontal.normalize().scale(maximumCenterDistance);
                point = new Vec3(center.x + horizontal.x, point.y, center.z + horizontal.z);
            }
            lockedPoints.put(id, point);
        });
    }

    private DirectionalRectangle rectangle(double length, double width) {
        return new DirectionalRectangle(
                new Vec2(host.boss().getX(), host.boss().getZ()).subtract(facing().scale(PromisedConsortAttackPlan.REAR_REACH)),
                facing(),
            activeTuning.scaleRange(length) + PromisedConsortAttackPlan.REAR_REACH,
            activeTuning.scaleRange(width)
        );
    }

    private Vec2 facing() {
        return lockedFacing == null ? currentFacing(host.boss()) : lockedFacing;
    }

    private static Vec2 currentFacing(LivingEntity entity) {
        Vec3 look = entity.getLookAngle();
        return new Vec2(look.x, look.z).normalizedOr(new Vec2(0.0, 1.0));
    }

    private static Vec2 direction(Vec3 from, Vec3 to) {
        return new Vec2(to.x - from.x, to.z - from.z).normalized();
    }

    private static Vec2 rotate(Vec2 direction, double degrees) {
        double radians = Math.toRadians(degrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return new Vec2(
                direction.x() * cosine - direction.z() * sine,
                direction.x() * sine + direction.z() * cosine
        );
    }

    private static StrikeVolume volume(HorizontalShape shape, double baseY, double height) {
        return new StrikeVolume(shape, baseY - DEFAULT_HEIGHT_MARGIN,
                baseY + height + DEFAULT_HEIGHT_MARGIN);
    }

    private static boolean intersectsY(LivingEntity target, StrikeVolume volume) {
        return target.getBoundingBox().maxY >= volume.minimumY()
                && target.getBoundingBox().minY <= volume.maximumY();
    }

    private static boolean contains(HorizontalShape shape, LivingEntity target) {
        double radius = target.getBbWidth() * 0.5;
        if (shape instanceof Circle circle) {
            return new Circle(circle.center(), circle.radius() + radius)
                    .contains(target.getX(), target.getZ());
        }
        if (shape instanceof Annulus annulus) {
            return new Annulus(annulus.center(), Math.max(0.0, annulus.innerRadius() - radius),
                    annulus.outerRadius() + radius).contains(target.getX(), target.getZ());
        }
        if (shape instanceof Capsule capsule) {
            return new Capsule(capsule.start(), capsule.end(), capsule.radius() + radius)
                    .contains(target.getX(), target.getZ());
        }
        return shape.contains(target.getX(), target.getZ());
    }

    private static boolean activeStart(PromisedConsortActionSnapshot action) {
        return action.actionPhase() == ActionPhase.ACTIVE && action.phaseTick() == 0;
    }

    private static boolean activeTick(PromisedConsortActionSnapshot action, int phaseTick) {
        return action.actionPhase() == ActionPhase.ACTIVE && action.phaseTick() == phaseTick;
    }

    private boolean once(PromisedConsortActionSnapshot action, String event) {
        return processedEvents.add(action.sequence() + ":" + event);
    }

    private static PromisedConsortHitSpec hit(
            DamageFormula damage,
            Kind kind,
            boolean instantGuard
    ) {
        return hit(damage, kind, instantGuard, 1);
    }

    private static PromisedConsortHitSpec hit(
            DamageFormula damage,
            Kind kind,
            boolean instantGuard,
            int maxHits
    ) {
        return new PromisedConsortHitSpec(
                kind.name().toLowerCase(),
                damage,
                switch (kind) {
                    case PHYSICAL -> PromisedConsortHitSpec.DamageKind.PHYSICAL;
                    case MAGIC -> PromisedConsortHitSpec.DamageKind.MAGIC;
                    case HOLY -> PromisedConsortHitSpec.DamageKind.HOLY;
                    case FIRE -> PromisedConsortHitSpec.DamageKind.FIRE;
                },
                instantGuard,
                maxHits
        );
    }

    private static double randomUnit(long seed, int index) {
        long value = seed + 0x9E3779B97F4A7C15L * (index + 1L);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return ((value ^ (value >>> 31)) >>> 11) * 0x1.0p-53;
    }

    public interface Host {
        ServerLevel serverLevel();

        LivingEntity boss();

        long gameTime();

        Vec3 combatCenter();

        Optional<? extends LivingEntity> currentTarget();

        Optional<Vec3> predictedTargetPoint(int sampleTicks, int leadTicks);

        boolean isAttackImmune(LivingEntity target);

        boolean insideArena(LivingEntity target);

        void prepareBossHitTargets(List<LivingEntity> targets);

        Optional<PromisedConsortHitOutcome> damageTarget(
                LivingEntity target,
                long actionSequence,
                PromisedConsortHitSpec hit
        );

        void moveControlled(Vec3 movement);

        default void playActionSound(PromisedConsortActionSoundPlan.Cue cue, Vec3 position, Vec2 direction) {
        }

        default void setPursuing(boolean pursuing) {
        }

        default void faceControlled(Vec2 direction) {
            float yaw = (float) Math.toDegrees(Math.atan2(-direction.x(), direction.z()));
            boss().setYRot(yaw);
            boss().setYHeadRot(yaw);
            boss().setYBodyRot(yaw);
        }

        void spawnGravityRock(
                PromisedConsortActionSnapshot action,
                int projectileIndex,
                double health,
                int lifetimeTicks,
                double turnDegreesPerTick,
                DamageFormula damage,
                int maxHitsPerTarget,
                int projectileCount
        );

        default void prepareGravityRocks(PromisedConsortActionSnapshot action, int riseTick, com.tonywww.elder_bosses.combat.action.ActionTimeline timeline) {
        }

        void spawnVisualClone(
                PromisedConsortActionSnapshot action,
                int cloneIndex,
                int cloneCount
        );

        default void spawnVisualClone(PromisedConsortActionSnapshot action, int cloneIndex, int cloneCount,
                          Vec3 origin, Vec2 direction, long impactTick) {
            spawnVisualClone(action, cloneIndex, cloneCount);
        }
    }

    public record HazardSnapshot(
            String id,
            HorizontalShape shape,
            double minimumY,
            double maximumY,
            long startTick,
            long activeTick,
            long endTick,
            PromisedConsortHitSpec hit
    ) {
    }

    public record PersistentState(
            long activeSequence,
            Vec2 lockedFacing,
            Map<String, PersistentPoint> lockedPoints,
            Set<String> processedEvents,
            Map<String, Integer> hitIndices,
            List<HitRegistry.PersistentClaim> hitClaims,
            List<PerTargetHitCounter.PersistentCount> hitCounts,
            List<PersistentHazard> hazards
    ) {
        public PersistentState {
            if (activeSequence < -1L) {
                throw new IllegalArgumentException("activeSequence must be at least -1");
            }
            lockedPoints = lockedPoints == null ? Map.of() : Map.copyOf(lockedPoints);
            processedEvents = processedEvents == null ? Set.of() : Set.copyOf(processedEvents);
            hitIndices = hitIndices == null ? Map.of() : Map.copyOf(hitIndices);
            hitClaims = hitClaims == null ? List.of() : List.copyOf(hitClaims);
            hitCounts = hitCounts == null ? List.of() : List.copyOf(hitCounts);
            hazards = hazards == null ? List.of() : List.copyOf(hazards);
        }
    }

    public record PersistentPoint(double x, double y, double z) {
    }

    public record PersistentHazard(
            String id,
            long actionSequence,
            ShapeKind shapeKind,
            double originX,
            double originZ,
            double directionX,
            double directionZ,
            List<Double> dimensions,
            double baseY,
            double height,
            long remainingToActiveTicks,
            long remainingToEndTicks,
                PromisedConsortHitSpec hit,
                Set<UUID> hitTargets,
                Boolean activationSoundPlayed
    ) {
            public PersistentHazard(String id, long actionSequence, ShapeKind shapeKind, double originX, double originZ,
                        double directionX, double directionZ, List<Double> dimensions, double baseY, double height,
                        long remainingToActiveTicks, long remainingToEndTicks, PromisedConsortHitSpec hit, Set<UUID> hitTargets) {
                this(id, actionSequence, shapeKind, originX, originZ, directionX, directionZ, dimensions, baseY, height,
                    remainingToActiveTicks, remainingToEndTicks, hit, hitTargets, null);
            }

        public PersistentHazard {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(shapeKind, "shapeKind");
            dimensions = List.copyOf(dimensions);
            Objects.requireNonNull(hit, "hit");
            hitTargets = Set.copyOf(Objects.requireNonNull(hitTargets, "hitTargets"));
            if (actionSequence < 0L || remainingToActiveTicks < 0L
                    || remainingToEndTicks <= 0L
                    || remainingToEndTicks <= remainingToActiveTicks) {
                throw new IllegalArgumentException("invalid persisted hazard timing");
            }
        }

        private static PersistentHazard from(Hazard hazard, long now) {
            ShapeData shape = ShapeData.from(hazard.shape());
            return new PersistentHazard(
                    hazard.id(),
                    hazard.actionSequence(),
                    shape.kind(),
                    shape.origin().x(),
                    shape.origin().z(),
                    shape.direction().x(),
                    shape.direction().z(),
                    shape.dimensions(),
                    hazard.baseY(),
                    hazard.height(),
                    Math.max(0L, hazard.activeTick() - now),
                    Math.max(1L, hazard.endTick() - now),
                        hazard.hit(),
                        hazard.hitTargets(),
                        hazard.soundEvents().contains("activation")
            );
        }

        private HorizontalShape createShape() {
            Vec2 origin = new Vec2(originX, originZ);
            Vec2 direction = new Vec2(directionX, directionZ);
            return switch (shapeKind) {
                case CIRCLE -> new Circle(origin, dimensions.get(0));
                case ANNULUS -> new Annulus(origin, dimensions.get(0), dimensions.get(1));
                case CAPSULE -> new Capsule(
                        origin,
                        origin.add(direction.scale(dimensions.get(0))),
                        dimensions.get(1)
                );
                case SECTOR -> new Sector(origin, direction, dimensions.get(0), dimensions.get(1));
                case RECTANGLE -> new DirectionalRectangle(
                        origin,
                        direction,
                        dimensions.get(0),
                        dimensions.get(1)
                );
            };
        }
    }

    public enum ShapeKind {
        CIRCLE,
        ANNULUS,
        CAPSULE,
        SECTOR,
        RECTANGLE
    }

    private record ShapeData(ShapeKind kind, Vec2 origin, Vec2 direction, List<Double> dimensions) {
        private static ShapeData from(HorizontalShape shape) {
            if (shape instanceof Circle circle) {
                return new ShapeData(ShapeKind.CIRCLE, circle.center(), new Vec2(0.0, 1.0),
                        List.of(circle.radius()));
            }
            if (shape instanceof Annulus annulus) {
                return new ShapeData(ShapeKind.ANNULUS, annulus.center(), new Vec2(0.0, 1.0),
                        List.of(annulus.innerRadius(), annulus.outerRadius()));
            }
            if (shape instanceof Capsule capsule) {
                Vec2 offset = capsule.end().subtract(capsule.start());
                return new ShapeData(ShapeKind.CAPSULE, capsule.start(),
                        offset.normalizedOr(new Vec2(0.0, 1.0)),
                        List.of(offset.length(), capsule.radius()));
            }
            if (shape instanceof Sector sector) {
                return new ShapeData(ShapeKind.SECTOR, sector.center(), sector.forward(),
                        List.of(sector.radius(), sector.halfAngleRadians()));
            }
            if (shape instanceof DirectionalRectangle rectangle) {
                return new ShapeData(ShapeKind.RECTANGLE, rectangle.origin(), rectangle.forward(),
                        List.of(rectangle.length(), rectangle.width()));
            }
            throw new IllegalArgumentException("unsupported persisted hazard shape " + shape);
        }
    }

    private record StrikeVolume(HorizontalShape shape, double minimumY, double maximumY) {
        private StrikeVolume {
            Objects.requireNonNull(shape, "shape");
        }

        private AABB bounds() {
            return new AABB(shape.minX(), minimumY, shape.minZ(),
                    shape.maxX(), maximumY, shape.maxZ());
        }
    }

    private record Hazard(
            String id,
            long actionSequence,
            HorizontalShape shape,
            double baseY,
            double height,
            long startTick,
            long activeTick,
            long endTick,
            PromisedConsortHitSpec hit,
            Set<UUID> hitTargets,
            Set<String> soundEvents
    ) {
        private Hazard {
            hitTargets = Objects.requireNonNull(hitTargets, "hitTargets");
            soundEvents = Objects.requireNonNull(soundEvents, "soundEvents");
        }

        private StrikeVolume volume() {
            return PromisedConsortActionExecutor.volume(shape, baseY, height);
        }

        private HazardSnapshot snapshot() {
                return new HazardSnapshot(
                    id,
                    shape,
                    baseY,
                    baseY + height,
                    startTick,
                    activeTick,
                    endTick,
                    hit
                );
        }
    }

    private enum Kind {
        PHYSICAL,
        MAGIC,
        HOLY,
        FIRE
    }
}
