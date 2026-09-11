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
        activeTuning = catalog.skillConfig().get(action.actionId()).tuning();
        Vec3 predictionOrigin = host.boss().position();
        updateTelegraphs(action, predictionOrigin);
        List<PromisedConsortHitOutcome> outcomes = new ArrayList<>();
        executeAction(action, outcomes);
        updateTelegraphs(action, predictionOrigin);
        tickHazards(outcomes);
        return List.copyOf(outcomes);
    }

    public List<PromisedConsortHitOutcome> tickPersistentHazards() {
        List<PromisedConsortHitOutcome> outcomes = new ArrayList<>();
        tickHazards(outcomes);
        return List.copyOf(outcomes);
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
        for (var candidate : PromisedConsortAttackPlan.create(action, catalog.skillConfig().get(action.actionId()),
                catalog.get(action.actionId()).timeline(), predictionOrigin, facing(), lockedPoints,
                config.instantGuard().defaultCueLeadTicks())) {
            if (candidate.endTick() <= now) continue;
            var previous = telegraphs.get(candidate.id());
            Vec3 frozen = lockedPoints.get("attack_origin:" + candidate.id());
            Vec3 direction = lockedPoints.get("attack_direction:" + candidate.id());
            if (frozen != null && direction != null) {
                candidate = PromisedConsortAttackPlan.relocate(candidate, frozen, new Vec2(direction.x, direction.z));
                if (previous == null) previous = candidate;
            }
            if (previous == null) {
                candidate = candidate.observedAt(now);
            } else if (now >= previous.lockTick()) {
                candidate = previous;
            } else {
                candidate = candidate.observedAt(previous.startTick());
            }
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
                        new java.util.HashSet<>(state.hitTargets())
            ));
        }
    }

    public Map<String, Vec3> lockedPoints() {
        return Map.copyOf(lockedPoints);
    }

    public Vec2 lockedFacing() {
        return facing();
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
        if (activeSequence >= 0L) {
            hitCounter.clearAction(activeSequence);
        }
        activeSequence = -1L;
        telegraphs.clear();
        executingAction = null;
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
        PromisedConsortSkillConfigSnapshot.Skill skill = catalog.skillConfig().get(action.actionId());
        activeTuning = skill.tuning();
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
            case PROMISED_CONSORT -> promisedConsort(action, skill, outcomes);
            case ENHANCED_EARTHHEAVE -> enhancedEarthheave(action, skill, outcomes);
            case CONSORT_METEOR -> consortMeteor(action, skill, outcomes);
        }
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
        if (activeStart(action)) {
            moveTowardLocked("target", skill.number("range"));
            hitCircle(action, "sword", skill.number("range") * 0.65,
                    hit(skill.damage("sword_damage"), Kind.PHYSICAL, true), outcomes);
                scheduleSwordEcho(action, "sword", skill.number("range"));
        }
        if (activeTick(action, ticks(skill, 2))) {
            hitCircle(action, "impact", skill.number("range"),
                    hit(skill.damage("impact_damage"), Kind.PHYSICAL, false), outcomes);
        }
    }

    private void comboSectors(
            PromisedConsortActionSnapshot action,
            PromisedConsortSkillConfigSnapshot.Skill skill,
            String prefix,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        if (!activeStart(action)) {
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
                ticks(skill, skill.integer("burst_tick")),
                skill.integer("fissure_lifetime_ticks"),
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
        } else if (action.stageIndex() == 3 && action.actionPhase() == ActionPhase.ACTIVE) {
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
        moveTowardLocked("target", 6.0);
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
        pullTargets(skill.number("pull_radius"), skill.number("max_pull_per_tick"));
        if (action.phaseTick() == activeTicks(action) - 1) {
            if (action.phase() == PromisedConsortPhase.PHASE_TWO) {
                host.spawnVisualClone(action, 0, 2);
                host.spawnVisualClone(action, 1, 2);
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
            host.spawnVisualClone(action, clone, 4);
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
        if (action.actionPhase() == ActionPhase.ACTIVE) {
            moveForward(Math.min(MAX_CONTROLLED_MOVEMENT_PER_TICK,
                    skill.number("range") / activeTicks(action)));
            if (action.phaseTick() == 0) {
                hitCapsule(action, "spin", skill.number("range"), skill.number("width"),
                        hit(skill.damage("spin_damage"), Kind.PHYSICAL, true), outcomes);
            }
            if (action.phaseTick() == activeTicks(action) - 1) {
                hitCircle(action, "slam", 3.5,
                        hit(skill.damage("slam_damage"), Kind.PHYSICAL, true), outcomes);
                scheduleSwordEcho(action, "slam", 3.5);
            }
        }
    }

    private void lightOfMiquella(
            PromisedConsortActionSnapshot action,
            PromisedConsortSkillConfigSnapshot.Skill skill,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        if (action.actionPhase() != ActionPhase.ACTIVE) {
            return;
        }
        Vec3 center = lockedPoints.getOrDefault("target", host.boss().position());
        hitCircleAt(action, "main", center, skill.number("radius"),
                hit(skill.damage("main_damage"), Kind.HOLY, false), outcomes);
        if (!activeStart(action)) {
            return;
        }
        for (int index = 0; index < skill.integer("afterglow_count"); index++) {
            double angle = randomUnit(action.seed(), index) * Math.PI * 2.0;
            double radius = randomUnit(action.seed() ^ 0x9E3779B97F4A7C15L, index)
                    * activeTuning.scaleRange(skill.number("radius"));
            Vec3 point = center.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            int activeDelay = activeTicks(action) + ticks(skill, index + 4);
            scheduleHazardAt(
                    action,
                    "afterglow_" + index,
                    new Circle(point.x, point.z, activeTuning.scaleRange(1.0)),
                    point.y,
                    2.5,
                    activeDelay,
                    activeDelay + 1,
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
        if (action.actionPhase() != ActionPhase.ACTIVE) {
            return;
        }
        int cloneCount = skill.integer("clone_count");
        int interval = Math.max(1, activeTicks(action) / (cloneCount + 1));
        if (action.phaseTick() % interval == 0 && action.phaseTick() / interval < cloneCount) {
            int clone = action.phaseTick() / interval;
            host.spawnVisualClone(action, clone, cloneCount);
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
        if (action.actionPhase() != ActionPhase.ACTIVE) {
            return;
        }
        moveForward(Math.min(MAX_CONTROLLED_MOVEMENT_PER_TICK,
                skill.number("range") / activeTicks(action)));
        int cloneInterval = ticks(skill, 4);
        if (action.phaseTick() % cloneInterval == 0
                && action.phaseTick() < ticks(skill, 16)) {
            int clone = action.phaseTick() / cloneInterval;
            host.spawnVisualClone(action, clone, 4);
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
        if (action.actionPhase() != ActionPhase.ACTIVE) {
            return;
        }
        moveSide(6.0 / activeTicks(action));
        int cloneCount = skill.integer("clone_count");
        int interval = Math.max(1, activeTicks(action) / (cloneCount + 1));
        if (action.phaseTick() % interval == 0 && action.phaseTick() / interval < cloneCount) {
            int clone = action.phaseTick() / interval;
            host.spawnVisualClone(action, clone, cloneCount);
            hitCapsule(action, "clone_" + clone, 9.0, 1.6,
                    hit(skill.damage("clone_damage"), Kind.HOLY, false), outcomes);
        }
        if (action.phaseTick() == activeTicks(action) - 1) {
            hitSector(action, "body", 4.0, 140.0,
                    hit(skill.damage("body_damage"), Kind.PHYSICAL, true), outcomes);
        }
    }

    private void promisedConsort(
            PromisedConsortActionSnapshot action,
            PromisedConsortSkillConfigSnapshot.Skill skill,
            List<PromisedConsortHitOutcome> outcomes
    ) {
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
            host.spawnVisualClone(action, clone, 2);
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
                    ticks(skill, 3 + index * 2), ticks(skill, 4 + index * 2),
                    hit(skill.damage("light_damage"), Kind.HOLY, false));
        }
    }

    private void consortMeteor(
            PromisedConsortActionSnapshot action,
            PromisedConsortSkillConfigSnapshot.Skill skill,
            List<PromisedConsortHitOutcome> outcomes
    ) {
        if (action.actionTick() == ticks(skill, 91)) {
            lockPredictedPoint("meteor", skill.integer("prediction_sample_ticks"),
                    skill.integer("prediction_lead_ticks"),
                config.arena().logicalRadius()
                    - activeTuning.scaleRange(skill.number("outer_radius")));
        }
        if (action.actionTick() == ticks(skill, 121) && once(action, "impact")) {
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
                    point.y, 3.0, ticks(skill, 2), ticks(skill, 3),
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
        int activeTicks = catalog.get(action.actionId()).timeline()
            .stages().get(action.stageIndex()).activeTicks();
        long delay = activeTicks - action.phaseTick()
            + config.lightEcho().delayTicks()
            + config.lightEcho().telegraphTicks();
        DirectionalRectangle echo = rectangle(Math.max(1.0, range), config.lightEcho().width());
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
                new java.util.HashSet<>()
        ));
    }

    private void tickHazards(List<PromisedConsortHitOutcome> outcomes) {
        long gameTick = host.gameTime();
        Iterator<Hazard> iterator = hazards.values().iterator();
        while (iterator.hasNext()) {
            Hazard hazard = iterator.next();
            if (gameTick >= hazard.activeTick() && gameTick < hazard.endTick()) {
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
        host.moveControlled(new Vec3(offset.x / horizontalLength * travel, 0.0,
                offset.z / horizontalLength * travel));
    }

    private void moveForward(double distance) {
        distance = Math.min(
                MAX_CONTROLLED_MOVEMENT_PER_TICK,
                activeTuning.scaleRange(distance)
        );
        host.moveControlled(new Vec3(facing().x() * distance, 0.0, facing().z() * distance));
    }

    private void moveSide(double distance) {
        distance = activeTuning.scaleRange(distance);
        Vec2 facing = facing();
        double checked = Math.min(Math.abs(distance), MAX_CONTROLLED_MOVEMENT_PER_TICK);
        host.moveControlled(new Vec3(-facing.z() * checked, 0.0, facing.x() * checked));
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
                new Vec2(host.boss().getX(), host.boss().getZ()),
                facing(),
            activeTuning.scaleRange(length),
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

        void spawnVisualClone(
                PromisedConsortActionSnapshot action,
                int cloneIndex,
                int cloneCount
        );
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
                Set<UUID> hitTargets
    ) {
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
                        hazard.hitTargets()
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
            Set<UUID> hitTargets
    ) {
        private Hazard {
            hitTargets = Objects.requireNonNull(hitTargets, "hitTargets");
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
