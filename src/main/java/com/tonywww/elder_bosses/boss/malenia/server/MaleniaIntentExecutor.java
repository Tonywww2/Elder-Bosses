package com.tonywww.elder_bosses.boss.malenia.server;

import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.boss.malenia.execution.MaleniaServerIntent;
import com.tonywww.elder_bosses.boss.malenia.execution.MaleniaServerIntent.HitSpec;
import com.tonywww.elder_bosses.boss.malenia.runtime.MaleniaActionSnapshot;
import com.tonywww.elder_bosses.combat.damage.DamageChannel;
import com.tonywww.elder_bosses.combat.damage.ModDamageSources;
import com.tonywww.elder_bosses.combat.geometry.Annulus;
import com.tonywww.elder_bosses.combat.geometry.Capsule;
import com.tonywww.elder_bosses.combat.geometry.Circle;
import com.tonywww.elder_bosses.combat.geometry.HorizontalShape;
import com.tonywww.elder_bosses.combat.geometry.Sector;
import com.tonywww.elder_bosses.combat.geometry.Vec2;
import com.tonywww.elder_bosses.combat.hit.HitId;
import com.tonywww.elder_bosses.combat.hit.HitRegistry;
import com.tonywww.elder_bosses.combat.hit.PerTargetHitCounter;
import com.tonywww.elder_bosses.combat.hit.ShieldBlockProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MaleniaIntentExecutor {
    private static final double POSITION_EPSILON_SQUARED = 1.0E-6;
    private static final double GRAB_ANCHOR_MARGIN = 1.0 / 16.0;
    private static final double MAX_HORIZONTAL_TRAVEL_PER_TICK = 1.15;
    private static final double LOCKED_POINT_ARRIVAL_MARGIN = 0.5;
    private static final double ENTITY_COLLISION_MARGIN = 0.05;
    private static final int WATERFOWL_AIRBORNE_END_TICK_EXCLUSIVE = 100;
    // Matches the documented 3-block underfoot rot-pool radius; no config field exists.
    private static final double SCARLET_PHANTOM_SPAWN_RADIUS = 3.0;
    private static final double HALF_CIRCLE_RADIANS = Math.PI;

    private final GeometryDefaults geometryDefaults;
    private final InstantGuardHandler instantGuardHandler;
    private final BlockedHitHandler blockedHitHandler;
    private final int maxRotZones;
    private final HitRegistry hitRegistry = new HitRegistry();
    private final PerTargetHitCounter hitCounter = new PerTargetHitCounter();
    private final Map<String, Vec3> lockedPoints = new HashMap<>();
    private final Map<String, Integer> groupIndices = new HashMap<>();
    private final Map<String, Integer> occurrenceIndices = new HashMap<>();
    private final Map<String, ActiveRotZone> activeRotZones = new LinkedHashMap<>();
    private final Map<MaleniaServerIntent, Double> movementTravel = new IdentityHashMap<>();
    private final Map<MaleniaServerIntent.MoveToward, MoveTowardProgress> moveTowardProgress =
            new IdentityHashMap<>();
    private final Set<ShieldDamageKey> appliedWaterfowlShieldDamage = new HashSet<>();

    private long activeActionSequence = -1L;
    private MaleniaActionId activeActionId;
    private long lastProcessedSequence = -1L;
    private long lastProcessedGameTick = Long.MIN_VALUE;
    private Vec2 lockedFacing;
    private Vec2 tickMovementStart;
    private Vec2 tickMovementEnd;
    private boolean movementRecorded;
    private double horizontalTravelThisTick;
    private boolean verticalControlActive;
    private UUID grabbedPlayerId;
    private double grabLeashDistance;
    private HitSpec grabImpaleHit;
    private HitSpec grabThrowHit;
    private long grabImpaleGameTick = Long.MIN_VALUE;
    private long grabThrowGameTick = Long.MIN_VALUE;
    private Vec3 actionStartPosition;
    private double actionStartY = Double.NaN;
    private LivingEntity gravityControlledBoss;

    public MaleniaIntentExecutor(GeometryDefaults geometryDefaults) {
        this(geometryDefaults, InstantGuardHandler.NONE, BlockedHitHandler.NONE, Integer.MAX_VALUE);
    }

    public MaleniaIntentExecutor(
            GeometryDefaults geometryDefaults,
            InstantGuardHandler instantGuardHandler
    ) {
        this(geometryDefaults, instantGuardHandler, BlockedHitHandler.NONE, Integer.MAX_VALUE);
    }

    public MaleniaIntentExecutor(
            GeometryDefaults geometryDefaults,
            InstantGuardHandler instantGuardHandler,
            int maxRotZones
    ) {
        this(geometryDefaults, instantGuardHandler, BlockedHitHandler.NONE, maxRotZones);
    }

    public MaleniaIntentExecutor(
            GeometryDefaults geometryDefaults,
            InstantGuardHandler instantGuardHandler,
            BlockedHitHandler blockedHitHandler,
            int maxRotZones
    ) {
        this.geometryDefaults = Objects.requireNonNull(geometryDefaults, "geometryDefaults");
        this.instantGuardHandler = Objects.requireNonNull(instantGuardHandler, "instantGuardHandler");
        this.blockedHitHandler = Objects.requireNonNull(blockedHitHandler, "blockedHitHandler");
        if (maxRotZones < 0) {
            throw new IllegalArgumentException("maxRotZones must be non-negative");
        }
        this.maxRotZones = maxRotZones;
    }

    public List<HitOutcome> tick(
            LivingEntity boss,
            MaleniaActionSnapshot snapshot,
            List<? extends MaleniaServerIntent> intents
    ) {
        Objects.requireNonNull(boss, "boss");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(intents, "intents");
        ServerLevel level = requireServerLevel(boss);
        prepareAction(boss, snapshot);
        long gameTick = level.getGameTime();
        if (lastProcessedSequence == snapshot.sequence()
            && lastProcessedGameTick == gameTick) {
            return List.of();
        }
        lastProcessedSequence = snapshot.sequence();
        lastProcessedGameTick = gameTick;

        List<HitOutcome> outcomes = new ArrayList<>();
        releaseVerticalControl(boss);
        maintainGrab(level, boss);
        resolveGrabFollowUps(level, boss, snapshot, outcomes);
        resetMovementTrace(boss);
        applyWaterfowlGravityControl(boss, snapshot);
        for (MaleniaServerIntent intent : intents) {
            handleIntent(
                    level,
                    boss,
                    snapshot,
                    Objects.requireNonNull(intent, "intent"),
                    outcomes
            );
        }
        tickRotZones(level, boss, outcomes);
        applyLockedFacing(boss);
        return List.copyOf(outcomes);
    }

    public List<HitOutcome> tickPersistentEffects(LivingEntity boss) {
        Objects.requireNonNull(boss, "boss");
        ServerLevel level = requireServerLevel(boss);
        List<HitOutcome> outcomes = new ArrayList<>();
        releaseVerticalControl(boss);
        resetMovementTrace(boss);
        tickRotZones(level, boss, outcomes);
        return List.copyOf(outcomes);
    }

    public Optional<Vec2> lockedFacing() {
        return Optional.ofNullable(lockedFacing);
    }

    public Map<String, Vec3> lockedPoints() {
        return Map.copyOf(lockedPoints);
    }

    public Optional<MovementTrace> movementTrace() {
        if (!movementRecorded || tickMovementStart == null || tickMovementEnd == null) {
            return Optional.empty();
        }
        return Optional.of(new MovementTrace(tickMovementStart, tickMovementEnd));
    }

    public List<PersistentZoneSnapshot> persistentZones() {
        return activeRotZones.values().stream()
                .map(zone -> new PersistentZoneSnapshot(
                        zone.zoneId(),
                        zone.center(),
                        zone.radius(),
                        zone.startedGameTick(),
                        Math.addExact(zone.startedGameTick(), zone.durationTicks()),
                        zone.hit().channel()
                ))
                .toList();
    }

    public Optional<UUID> grabbedPlayerId() {
        return Optional.ofNullable(grabbedPlayerId);
    }

    public int activeRotZoneCount() {
        return activeRotZones.size();
    }

    public void releaseGrabbedPlayer() {
        releaseGrab();
    }

    public void clearActionTransients() {
        clearActionState();
        activeActionSequence = -1L;
        activeActionId = null;
        lastProcessedSequence = -1L;
        lastProcessedGameTick = Long.MIN_VALUE;
    }

    public void clear() {
        clearActionTransients();
        activeRotZones.clear();
    }

    private void prepareAction(LivingEntity boss, MaleniaActionSnapshot snapshot) {
        if (activeActionSequence == snapshot.sequence() && activeActionId == snapshot.actionId()) {
            return;
        }
        clearActionState();
        activeActionSequence = snapshot.sequence();
        activeActionId = snapshot.actionId();
        actionStartPosition = boss.position();
        actionStartY = boss.getBoundingBox().minY;
    }

    private void clearActionState() {
        releaseGravityControl();
        hitRegistry.clear();
        if (activeActionSequence >= 0L) {
            hitCounter.clearAction(activeActionSequence);
        }
        lockedPoints.clear();
        groupIndices.clear();
        occurrenceIndices.clear();
        movementTravel.clear();
        moveTowardProgress.clear();
        appliedWaterfowlShieldDamage.clear();
        lockedFacing = null;
        tickMovementStart = null;
        tickMovementEnd = null;
        movementRecorded = false;
        horizontalTravelThisTick = 0.0;
        actionStartPosition = null;
        actionStartY = Double.NaN;
        releaseGrab();
    }

    private void applyWaterfowlGravityControl(
            LivingEntity boss,
            MaleniaActionSnapshot snapshot
    ) {
        if (snapshot.actionId() != MaleniaActionId.WATERFOWL_DANCE
                || snapshot.actionTick() >= WATERFOWL_AIRBORNE_END_TICK_EXCLUSIVE) {
            releaseGravityControl();
            return;
        }
        if (gravityControlledBoss != boss) {
            releaseGravityControl();
            gravityControlledBoss = boss;
        }
        boss.setNoGravity(true);
        Vec3 velocity = boss.getDeltaMovement();
        boss.setDeltaMovement(velocity.x, 0.0, velocity.z);
    }

    private void releaseGravityControl() {
        if (gravityControlledBoss == null) {
            verticalControlActive = false;
            return;
        }
        gravityControlledBoss.setNoGravity(false);
        gravityControlledBoss = null;
        verticalControlActive = false;
    }

    private void handleIntent(
            ServerLevel level,
            LivingEntity boss,
            MaleniaActionSnapshot snapshot,
            MaleniaServerIntent intent,
            List<HitOutcome> outcomes
    ) {
        if (intent instanceof MaleniaServerIntent.LockFacing) {
            lockFacing(level, boss, snapshot);
        } else if (intent instanceof MaleniaServerIntent.LockPoint lockPoint) {
            lockPoint(level, snapshot, lockPoint);
        } else if (intent instanceof MaleniaServerIntent.LockPhantom lockPhantom) {
            lockPhantom(level, snapshot, lockPhantom);
        } else if (intent instanceof MaleniaServerIntent.MoveToward moveToward) {
            moveToward(boss, moveToward);
        } else if (intent instanceof MaleniaServerIntent.MoveVertical moveVertical) {
            moveVertical(boss, moveVertical);
        } else if (intent instanceof MaleniaServerIntent.HoldVertical) {
            holdVertical(boss);
        } else if (intent instanceof MaleniaServerIntent.MoveAway moveAway) {
            moveAway(level, boss, snapshot, moveAway);
        } else if (intent instanceof MaleniaServerIntent.HitSector hitSector) {
            outcomes.addAll(hitSector(level, boss, snapshot, hitSector));
        } else if (intent instanceof MaleniaServerIntent.HitCapsule hitCapsule) {
            outcomes.addAll(hitCapsule(level, boss, snapshot, hitCapsule));
        } else if (intent instanceof MaleniaServerIntent.HitCircle hitCircle) {
            outcomes.addAll(hitCircle(level, boss, snapshot, hitCircle));
        } else if (intent instanceof MaleniaServerIntent.HitAnnulus hitAnnulus) {
            outcomes.addAll(hitAnnulus(level, boss, snapshot, hitAnnulus));
        } else if (intent instanceof MaleniaServerIntent.RotZone rotZone) {
            createRotZone(boss, snapshot, rotZone);
        } else if (intent instanceof MaleniaServerIntent.IndicatorOnlyZone) {
            return;
        } else if (intent instanceof MaleniaServerIntent.Grab grab) {
            grab(level, boss, snapshot, grab, outcomes);
        } else if (intent instanceof MaleniaServerIntent.HitGrabbed hitGrabbed) {
            hitGrabbed(level, boss, snapshot, hitGrabbed.hit()).ifPresent(outcomes::add);
        } else if (intent instanceof MaleniaServerIntent.Release) {
            releaseGrab();
        } else if (intent instanceof MaleniaServerIntent.WaterfowlBurst waterfowlBurst) {
            outcomes.addAll(hitWaterfowlBurst(level, boss, snapshot, waterfowlBurst));
        } else if (intent instanceof MaleniaServerIntent.PhantomStrike phantomStrike) {
            outcomes.addAll(hitPhantomStrike(level, boss, snapshot, phantomStrike));
        }
    }

    private void lockFacing(
            ServerLevel level,
            LivingEntity boss,
            MaleniaActionSnapshot snapshot
    ) {
        LivingEntity target = resolveTarget(level, boss, snapshot);
        if (target != null) {
            Vec2 towardTarget = horizontalDirection(boss.position(), target.position());
            if (!towardTarget.isZero()) {
                lockedFacing = towardTarget;
                return;
            }
        }
        lockedFacing = currentFacing(boss);
    }

    private void lockPoint(
            ServerLevel level,
            MaleniaActionSnapshot snapshot,
            MaleniaServerIntent.LockPoint intent
    ) {
        LivingEntity target = resolveTarget(level, null, snapshot);
        if (target != null) {
            Vec3 point = target.position();
            if (intent.projectToSurface()) {
                BlockPos surface = level.getHeightmapPos(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        BlockPos.containing(point)
                );
                point = new Vec3(point.x, surface.getY(), point.z);
            }
            lockedPoints.put(intent.pointId(), point);
        }
    }

    private void lockPhantom(
            ServerLevel level,
            MaleniaActionSnapshot snapshot,
            MaleniaServerIntent.LockPhantom intent
    ) {
        String pointId = phantomPointId(intent.phantomIndex());
        lockedPoints.put(
                pointId + "_origin",
                phantomSpawnPoint(snapshot.seed(), intent.phantomIndex(), intent.phantomCount())
        );
        LivingEntity target = resolveTarget(level, null, snapshot);
        if (target != null) {
            lockedPoints.put(pointId, target.position());
        }
    }

    private void moveToward(LivingEntity boss, MaleniaServerIntent.MoveToward intent) {
        Vec2 before = horizontalPosition(boss);
        Vec3 target = lockedPoints.get(intent.pointId());
        if (target != null) {
            if (intent.includeVertical()) {
                holdVertical(boss);
            }
            Vec2 direction = horizontalDirection(boss.position(), target);
            if (!direction.isZero()) {
                lockedFacing = direction;
            }
            Vec3 offset = target.subtract(boss.position());
            Vec3 movement = intent.includeVertical()
                    ? offset
                    : new Vec3(offset.x, 0.0, offset.z);
            double distance = movement.length();
            if (distance > 0.0) {
                MoveTowardProgress progress = moveTowardProgress.computeIfAbsent(
                    intent,
                    ignored -> new MoveTowardProgress(
                        Math.min(distance, intent.maxTravel()),
                        intent.travelTicks()
                    )
                );
                double travel = Math.min(distance, progress.nextBudget());
                Vec3 beforeMove = boss.position();
                moveControlled(
                        boss,
                        movement.scale(travel / distance),
                        intent,
                        intent.maxTravel()
                );
                progress.recordTravel(boss.position().distanceTo(beforeMove));
            }
        }
        recordMovement(before, horizontalPosition(boss));
    }

    private void moveVertical(
            LivingEntity boss,
            MaleniaServerIntent.MoveVertical intent
    ) {
        Vec2 before = horizontalPosition(boss);
        holdVertical(boss);
        moveControlled(
                boss,
                new Vec3(0.0, intent.perTickBudget(), 0.0),
                intent,
                intent.maxTravel()
        );
        recordMovement(before, horizontalPosition(boss));
    }

    private void holdVertical(LivingEntity boss) {
        if (gravityControlledBoss != boss) {
            releaseGravityControl();
            gravityControlledBoss = boss;
        }
        Vec3 movement = boss.getDeltaMovement();
        boss.setDeltaMovement(movement.x, 0.0, movement.z);
        boss.setNoGravity(true);
        boss.fallDistance = 0.0F;
        verticalControlActive = true;
    }

    private void releaseVerticalControl(LivingEntity boss) {
        if (!verticalControlActive) {
            return;
        }
        Vec3 movement = boss.getDeltaMovement();
        boss.setDeltaMovement(movement.x, 0.0, movement.z);
        boss.fallDistance = 0.0F;
        releaseGravityControl();
    }

    private void moveAway(
            ServerLevel level,
            LivingEntity boss,
            MaleniaActionSnapshot snapshot,
                MaleniaServerIntent.MoveAway intent
    ) {
        Vec2 before = horizontalPosition(boss);
        LivingEntity target = resolveTarget(level, boss, snapshot);
        Vec2 away = target == null
                ? facing(boss).scale(-1.0)
                : horizontalDirection(target.position(), boss.position());
        if (!away.isZero()) {
            moveControlled(
                boss,
                new Vec3(
                    away.x() * intent.perTickBudget(),
                    0.0,
                    away.z() * intent.perTickBudget()
                ),
                intent,
                intent.maxTravel()
            );
        }
        recordMovement(before, horizontalPosition(boss));
    }

    private List<HitOutcome> hitSector(
            ServerLevel level,
            LivingEntity boss,
            MaleniaActionSnapshot snapshot,
            MaleniaServerIntent.HitSector intent
    ) {
        double arcDegrees = intent.arcDegrees().orElse(geometryDefaults.sectorArcDegrees());
        Sector sector = new Sector(
                boss.getX(),
                boss.getZ(),
                facing(boss).x(),
                facing(boss).z(),
                intent.range(),
                Math.toRadians(arcDegrees * 0.5)
        );
        return hitLivingEntities(level, boss, snapshot, intent.hit(), StrikeVolume.atBoss(sector, boss));
    }

    private List<HitOutcome> hitCapsule(
            ServerLevel level,
            LivingEntity boss,
            MaleniaActionSnapshot snapshot,
            MaleniaServerIntent.HitCapsule intent
    ) {
        double width = intent.width().orElse(geometryDefaults.capsuleWidth());
        Capsule capsule = intent.endPointId().isPresent()
                ? lockedPathCapsule(intent.endPointId().get(), intent.length(), width)
                : forwardCapsule(boss, intent.length(), width);
        if (capsule == null) {
            return List.of();
        }
        return hitLivingEntities(level, boss, snapshot, intent.hit(), StrikeVolume.atBoss(capsule, boss));
    }

    private Capsule lockedPathCapsule(String pointId, double maxRange, double width) {
        Vec3 target = lockedPoints.get(pointId);
        if (target == null || actionStartPosition == null) {
            return null;
        }
        Vec2 start = new Vec2(actionStartPosition.x, actionStartPosition.z);
        Vec2 targetPosition = new Vec2(target.x, target.z);
        Vec2 offset = targetPosition.subtract(start);
        double distance = offset.length();
        Vec2 end = distance > maxRange
                ? start.add(offset.scale(maxRange / distance))
                : targetPosition;
        return new Capsule(start, end, width * 0.5);
    }

    private List<HitOutcome> hitCircle(
            ServerLevel level,
            LivingEntity boss,
            MaleniaActionSnapshot snapshot,
            MaleniaServerIntent.HitCircle intent
    ) {
        Vec3 center = resolveCenter(boss, intent.centerPointId());
        if (center == null) {
            return List.of();
        }
        if (intent.centerPointId().isPresent()
                && !withinLockedPointArrivalRadius(boss, center)) {
            return List.of();
        }
        Circle circle = new Circle(center.x, center.z, intent.radius());
        StrikeVolume volume = intent.centerPointId().isPresent()
            ? StrikeVolume.atPoint(circle, center, boss)
            : StrikeVolume.atBoss(circle, boss);
        return hitLivingEntities(level, boss, snapshot, intent.hit(), volume);
    }

    private List<HitOutcome> hitAnnulus(
            ServerLevel level,
            LivingEntity boss,
            MaleniaActionSnapshot snapshot,
            MaleniaServerIntent.HitAnnulus intent
    ) {
        Annulus annulus = new Annulus(
                boss.getX(),
                boss.getZ(),
                intent.innerRadius(),
                intent.outerRadius()
        );
        return hitLivingEntities(level, boss, snapshot, intent.hit(), StrikeVolume.atBoss(annulus, boss));
    }

    private void createRotZone(
            LivingEntity boss,
            MaleniaActionSnapshot snapshot,
            MaleniaServerIntent.RotZone intent
    ) {
        if (maxRotZones == 0) {
            return;
        }
        Vec3 center = resolveCenter(boss, intent.centerPointId());
        if (center == null) {
            return;
        }
        String zoneId = snapshot.sequence()
                + ":" + snapshot.actionId().serializedName()
                + ":" + intent.hit().hitIdSuffix();
        if (activeRotZones.containsKey(zoneId)) {
            return;
        }
        if (activeRotZones.size() >= maxRotZones) {
            Iterator<ActiveRotZone> iterator = activeRotZones.values().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        Circle circle = new Circle(center.x, center.z, intent.radius());
        StrikeVolume volume = intent.centerPointId().isPresent()
            ? StrikeVolume.atPoint(circle, center, boss)
            : StrikeVolume.atBoss(circle, boss);
        activeRotZones.put(zoneId, new ActiveRotZone(
                zoneId,
                snapshot.sequence(),
                snapshot.actionTick(),
                boss.level().getGameTime(),
                intent.durationTicks(),
                intent.intervalTicks(),
                intent.hit(),
                center,
                intent.radius(),
                volume
        ));
    }

    private void tickRotZones(
            ServerLevel level,
            LivingEntity boss,
            List<HitOutcome> outcomes
    ) {
        long gameTick = level.getGameTime();
        Iterator<ActiveRotZone> iterator = activeRotZones.values().iterator();
        while (iterator.hasNext()) {
            ActiveRotZone zone = iterator.next();
            long age = gameTick - zone.startedGameTick();
            if (age > 0L
                    && age <= zone.durationTicks()
                    && age % zone.intervalTicks() == 0L) {
                outcomes.addAll(hitLivingEntities(
                        level,
                        boss,
                        zone.actionSequence(),
                        zone.actionTick(),
                        "zone:" + zone.zoneId() + ":" + age,
                        zone.hit(),
                        zone.volume()
                ));
            }
            if (age >= zone.durationTicks()) {
                iterator.remove();
            }
        }
    }

    private void grab(
            ServerLevel level,
            LivingEntity boss,
            MaleniaActionSnapshot snapshot,
            MaleniaServerIntent.Grab intent,
            List<HitOutcome> outcomes
    ) {
        if (grabbedPlayerId != null) {
            return;
        }
        Capsule capsule = forwardCapsule(boss, intent.length(), intent.width());
        StrikeVolume volume = StrikeVolume.atBoss(capsule, boss);
        List<ServerPlayer> candidates = matchingPlayers(level, boss, volume);
        for (ServerPlayer candidate : candidates) {
            Optional<HitOutcome> outcome = applyHit(
                    boss,
                    candidate,
                    snapshot.sequence(),
                    snapshot.actionTick(),
                    occurrenceKey(snapshot, intent.grabHit()),
                    intent.grabHit()
            );
            if (outcome.isPresent()) {
                grabbedPlayerId = candidate.getUUID();
                grabLeashDistance = intent.length() + boss.getBbWidth() + candidate.getBbWidth();
                grabImpaleHit = intent.impaleHit();
                grabThrowHit = intent.throwHit();
                grabImpaleGameTick = Math.addExact(level.getGameTime(), intent.impaleDelayTicks());
                grabThrowGameTick = Math.addExact(level.getGameTime(), intent.throwDelayTicks());
                outcomes.add(outcome.get());
                maintainGrab(level, boss);
                return;
            }
        }
    }

    private void resolveGrabFollowUps(
            ServerLevel level,
            LivingEntity boss,
            MaleniaActionSnapshot snapshot,
            List<HitOutcome> outcomes
    ) {
        if (grabbedPlayerId == null) {
            return;
        }
        long gameTick = level.getGameTime();
        if (gameTick == grabImpaleGameTick) {
            hitGrabbed(level, boss, snapshot, grabImpaleHit).ifPresent(outcomes::add);
        }
        if (gameTick == grabThrowGameTick) {
            hitGrabbed(level, boss, snapshot, grabThrowHit).ifPresent(outcomes::add);
            releaseGrab();
        } else if (gameTick > grabThrowGameTick) {
            releaseGrab();
        }
    }

    private Optional<HitOutcome> hitGrabbed(
            ServerLevel level,
            LivingEntity boss,
            MaleniaActionSnapshot snapshot,
            HitSpec hit
    ) {
        ServerPlayer player = resolveGrabbed(level);
        if (player == null) {
            releaseGrab();
            return Optional.empty();
        }
        return applyHit(
                boss,
                player,
                snapshot.sequence(),
                snapshot.actionTick(),
                occurrenceKey(snapshot, hit),
                hit
        );
    }

    private void maintainGrab(ServerLevel level, LivingEntity boss) {
        ServerPlayer player = resolveGrabbed(level);
        if (player == null) {
            releaseGrab();
            return;
        }
        double spacing = (boss.getBbWidth() + player.getBbWidth()) * 0.5 + GRAB_ANCHOR_MARGIN;
        Vec2 facing = facing(boss);
        Vec3 anchor = new Vec3(
                boss.getX() + facing.x() * spacing,
                boss.getY(),
                boss.getZ() + facing.z() * spacing
        );
        Vec3 offset = anchor.subtract(player.position());
        if (offset.lengthSqr() > grabLeashDistance * grabLeashDistance) {
            releaseGrab();
            return;
        }
        double distance = offset.length();
        if (offset.lengthSqr() <= POSITION_EPSILON_SQUARED) {
            return;
        }
        double maxStep = Math.max(boss.getBbWidth(), player.getBbWidth());
        Vec3 step = distance > maxStep ? offset.scale(maxStep / distance) : offset;
        if (!level.noCollision(player, player.getBoundingBox().move(step))) {
            releaseGrab();
            return;
        }
        Vec3 before = player.position();
        player.move(MoverType.SELF, step);
        Vec3 actual = player.position().subtract(before);
        if (actual.distanceToSqr(step) > POSITION_EPSILON_SQUARED) {
            releaseGrab();
        }
    }

        private List<HitOutcome> hitWaterfowlBurst(
            ServerLevel level,
            LivingEntity boss,
            MaleniaActionSnapshot snapshot,
            MaleniaServerIntent.WaterfowlBurst intent
    ) {
        return hitLivingEntities(
            level,
            boss,
            snapshot,
            intent.hit(),
                movementPathVolume(boss, intent.width())
        );
    }

    private List<HitOutcome> hitPhantomStrike(
            ServerLevel level,
            LivingEntity boss,
            MaleniaActionSnapshot snapshot,
            MaleniaServerIntent.PhantomStrike intent
    ) {
        if (intent.attackKind() == MaleniaServerIntent.PhantomAttackKind.BOSS_DIVE) {
            if (!lockedPoints.containsKey("boss_dive")) {
                return List.of();
            }
            return hitLivingEntities(
                    level,
                    boss,
                    snapshot,
                    intent.hit(),
                    movementPathVolume(boss, intent.width())
            );
        }

        String pointId = phantomPointId(intent.strikeIndex());
        Vec3 origin = lockedPoints.get(pointId + "_origin");
        Vec3 target = lockedPoints.get(pointId);
        if (origin == null || target == null) {
            return List.of();
        }
        Capsule capsule = new Capsule(
                new Vec2(origin.x, origin.z),
                new Vec2(target.x, target.z),
                intent.width() * 0.5
        );
        return hitLivingEntities(
            level,
            boss,
            snapshot,
            intent.hit(),
            StrikeVolume.betweenPoints(capsule, origin, target, boss.getBbHeight())
        );
    }

    private StrikeVolume movementPathVolume(LivingEntity boss, double width) {
        Vec2 end = movementRecorded ? tickMovementEnd : horizontalPosition(boss);
        Vec2 start = movementRecorded ? tickMovementStart : end;
        double radius = width * 0.5;
        HorizontalShape shape = horizontalDistanceSquared(start, end) <= POSITION_EPSILON_SQUARED
                ? new Circle(end, radius)
                : new Capsule(start, end, radius);
        return StrikeVolume.fromActionStart(shape, boss, actionStartY);
    }

    private static String phantomPointId(int phantomIndex) {
        return "phantom_" + (phantomIndex + 1);
    }

    private Vec3 phantomSpawnPoint(long actionSeed, int index, int phantomCount) {
        Vec3 center = Objects.requireNonNull(actionStartPosition, "actionStartPosition");
        long layoutSeed = mixSeed(actionSeed);
        double seedOffset = unitInterval(layoutSeed) * (Math.PI * 2.0);
        int pairCount = phantomCount / 2;
        int pairIndex = index / 2;
        double offset = pairCount == 0 || index >= pairCount * 2
            ? 0.0
            : HALF_CIRCLE_RADIANS * 0.5 * (pairCount - pairIndex) / pairCount;
        boolean positiveFirst = (layoutSeed & 1L) == 0L;
        boolean positiveSide = (index % 2 == 0) == positiveFirst;
        double angle = seedOffset + (positiveSide ? offset : -offset);
        return new Vec3(
                center.x + Math.cos(angle) * SCARLET_PHANTOM_SPAWN_RADIUS,
                center.y,
                center.z + Math.sin(angle) * SCARLET_PHANTOM_SPAWN_RADIUS
        );
    }

    private static long mixSeed(long seed) {
        long mixed = seed + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    private static double unitInterval(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }

    private List<HitOutcome> hitLivingEntities(
            ServerLevel level,
            LivingEntity boss,
            MaleniaActionSnapshot snapshot,
            HitSpec hit,
            StrikeVolume volume
    ) {
        return hitLivingEntities(
                level,
                boss,
                snapshot.sequence(),
                snapshot.actionTick(),
                occurrenceKey(snapshot, hit),
                hit,
                volume
        );
    }

    private List<HitOutcome> hitLivingEntities(
            ServerLevel level,
            LivingEntity boss,
            long actionSequence,
            int actionTick,
            String occurrenceKey,
            HitSpec hit,
            StrikeVolume volume
    ) {
        List<HitOutcome> outcomes = new ArrayList<>();
        for (LivingEntity target : matchingLivingEntities(level, boss, volume)) {
            applyHit(
                    boss,
                target,
                    actionSequence,
                    actionTick,
                    occurrenceKey,
                    hit
            ).ifPresent(outcomes::add);
        }
        return outcomes;
    }

        private List<LivingEntity> matchingLivingEntities(
            ServerLevel level,
            LivingEntity boss,
            StrikeVolume volume
    ) {
        List<LivingEntity> targets = level.getEntitiesOfClass(
            LivingEntity.class,
                volume.bounds(),
            target -> validHitTarget(level, boss, target)
                && containsTarget(volume.shape(), target)
        );
        targets.sort(Comparator.comparingDouble(boss::distanceToSqr));
        return targets;
    }

    private List<ServerPlayer> matchingPlayers(
            ServerLevel level,
            LivingEntity boss,
            StrikeVolume volume
    ) {
        List<ServerPlayer> players = level.getEntitiesOfClass(
                ServerPlayer.class,
                volume.bounds(),
                player -> validTarget(level, player)
                        && containsTarget(volume.shape(), player)
        );
        players.sort(Comparator.comparingDouble(boss::distanceToSqr));
        return players;
    }

    private Optional<HitOutcome> applyHit(
            LivingEntity boss,
            LivingEntity target,
            long actionSequence,
            int actionTick,
            String occurrenceKey,
            HitSpec hit
    ) {
        int occurrenceIndex = indexFor(occurrenceIndices, occurrenceKey);
        if (!hitRegistry.claim(new HitId(actionSequence, occurrenceIndex), target.getUUID())) {
            return Optional.empty();
        }
        int groupIndex = indexFor(groupIndices, hit.hitIdSuffix());
        if (!hitCounter.claim(
                actionSequence,
                groupIndex,
                target.getUUID(),
                hit.maxHitsPerTarget()
        )) {
            return Optional.empty();
        }

        double evaluatedDamage = hit.damage().evaluate(
                boss.getAttributeValue(Attributes.ATTACK_DAMAGE)
        );
        float attemptedDamage = (float) Math.min(evaluatedDamage, Float.MAX_VALUE);
        DamageSource source = damageSource(boss, hit);
        float healthBefore = target.getHealth();
        float absorptionBefore = target.getAbsorptionAmount();
        Optional<InstantGuardResult> instantGuardResult = hit.instantGuardEligible()
            && target instanceof ServerPlayer player
            ? instantGuardHandler.tryInstantGuard(player, hit, attemptedDamage)
            : Optional.empty();
        if (instantGuardResult.isPresent()) {
            InstantGuardResult guardResult = instantGuardResult.get();
            double damageMultiplier = guardResult.damageMultiplier();
            float reducedDamage = (float) Math.min(
                attemptedDamage * damageMultiplier,
                Float.MAX_VALUE
            );
            if (damageMultiplier > 0.0) {
                boolean hurtAccepted;
                try (ShieldBlockProbe.Scope ignored = ShieldBlockProbe.begin(
                        target,
                        source,
                        ShieldBlockProbe.Policy.OVERRIDE_BLOCK
                )) {
                    hurtAccepted = target.hurt(source, reducedDamage);
                }
                if (!hurtAccepted) {
                    return Optional.empty();
                }
            }
            float healthDamage = Math.max(0.0F, healthBefore - target.getHealth());
            float absorptionDamage = Math.max(
                0.0F,
                absorptionBefore - target.getAbsorptionAmount()
            );
            guardResult.afterDamage().apply(healthDamage);
            return Optional.of(new HitOutcome(
                target.getUUID(),
                actionSequence,
                actionTick,
                hit.hitIdSuffix(),
                hit.channel(),
                HitOutcome.ContactType.BLOCKED,
                attemptedDamage,
                healthDamage,
                absorptionDamage,
                hit.rotBuildup(),
                hit.healProfile(),
                !target.isAlive(),
                true
            ));
        }
        boolean suppressShieldDamage = target instanceof ServerPlayer
            && suppressesVanillaShieldDamage(activeActionId, hit.hitIdSuffix());
        BlockedHitPreparation blockedHitPreparation = suppressShieldDamage
            ? blockedHitHandler.prepare((ServerPlayer) target, activeActionId)
            : ignored -> {
            };
        boolean blocked;
        float blockedDamage;
        ShieldBlockProbe.Policy blockPolicy = suppressShieldDamage
            ? ShieldBlockProbe.Policy.SUPPRESS_SHIELD_DAMAGE
            : ShieldBlockProbe.Policy.OBSERVE;
        try (ShieldBlockProbe.Scope blockProbe = ShieldBlockProbe.begin(
            target,
            source,
            blockPolicy
        )) {
            target.hurt(source, attemptedDamage);
            blocked = blockProbe.blocked();
            blockedDamage = blockProbe.actualBlockedDamage();
        }
        if (blocked && blockedDamage > 0.0F && shouldApplyConfiguredShieldDamage(
            actionSequence,
            hit.hitIdSuffix(),
            target.getUUID()
        )) {
            blockedHitPreparation.apply(blockedDamage);
        }
        float healthDamage = Math.max(0.0F, healthBefore - target.getHealth());
        float absorptionDamage = Math.max(
                0.0F,
                absorptionBefore - target.getAbsorptionAmount()
        );
        HitOutcome.ContactType contactType;
        if (healthDamage > 0.0F || absorptionDamage > 0.0F) {
            contactType = HitOutcome.ContactType.DAMAGED;
        } else if (blocked) {
            contactType = HitOutcome.ContactType.BLOCKED;
        } else if (attemptedDamage == 0.0F) {
            contactType = HitOutcome.ContactType.CONTACT;
        } else {
            return Optional.empty();
        }
        boolean killedTarget = contactType == HitOutcome.ContactType.DAMAGED
            && !target.isAlive();
        return Optional.of(new HitOutcome(
                target.getUUID(),
                actionSequence,
                actionTick,
                hit.hitIdSuffix(),
                hit.channel(),
                contactType,
                attemptedDamage,
                healthDamage,
                absorptionDamage,
                hit.rotBuildup(),
                hit.healProfile(),
                killedTarget,
                hit.instantGuardEligible()
        ));
    }

        private static boolean suppressesVanillaShieldDamage(
            MaleniaActionId actionId,
            String hitIdSuffix
        ) {
        return actionId == MaleniaActionId.KICK
            || actionId == MaleniaActionId.WATERFOWL_DANCE
            && hitIdSuffix.startsWith("burst_");
        }

        private boolean shouldApplyConfiguredShieldDamage(
            long actionSequence,
            String hitIdSuffix,
            UUID targetId
        ) {
        if (activeActionId != MaleniaActionId.WATERFOWL_DANCE) {
            return activeActionId == MaleniaActionId.KICK;
        }
        return hitIdSuffix.startsWith("burst_")
            && appliedWaterfowlShieldDamage.add(new ShieldDamageKey(
                actionSequence,
                hitIdSuffix,
                targetId
            ));
        }

    private static DamageSource damageSource(LivingEntity boss, HitSpec hit) {
        return switch (hit.channel()) {
            case PHYSICAL -> boss.damageSources().mobAttack(boss);
            case MAGIC -> ModDamageSources.maleniaMagic(
                boss.level().registryAccess(),
                boss,
                boss
            );
            case SCARLET_ROT -> ModDamageSources.scarletRot(
                    boss.level().registryAccess(),
                    boss,
                    boss
            );
        };
    }

    private static boolean containsTarget(HorizontalShape shape, LivingEntity target) {
        double targetRadius = target.getBbWidth() * 0.5;
        if (shape instanceof Annulus annulus) {
            return new Annulus(
                    annulus.center(),
                    Math.max(0.0, annulus.innerRadius() - targetRadius),
                    annulus.outerRadius() + targetRadius
            ).contains(target.getX(), target.getZ());
        }
        if (shape instanceof Circle circle) {
            return new Circle(circle.center(), circle.radius() + targetRadius)
                    .contains(target.getX(), target.getZ());
        }
        if (shape instanceof Capsule capsule) {
            return new Capsule(capsule.start(), capsule.end(), capsule.radius() + targetRadius)
                    .contains(target.getX(), target.getZ());
        }
        return shape.contains(target.getX(), target.getZ());
    }

    private Capsule forwardCapsule(LivingEntity boss, double length, double width) {
        Vec2 facing = facing(boss);
        return new Capsule(
                boss.getX(),
                boss.getZ(),
                boss.getX() + facing.x() * length,
                boss.getZ() + facing.z() * length,
                width * 0.5
        );
    }

    private Vec2 facing(LivingEntity boss) {
        return lockedFacing == null ? currentFacing(boss) : lockedFacing;
    }

    private static Vec2 currentFacing(LivingEntity boss) {
        Vec3 look = boss.getLookAngle();
        return new Vec2(look.x, look.z).normalizedOr(new Vec2(0.0, 1.0));
    }

    private static Vec2 horizontalDirection(Vec3 start, Vec3 end) {
        return new Vec2(end.x - start.x, end.z - start.z).normalized();
    }

    private static Vec2 horizontalPosition(LivingEntity entity) {
        return new Vec2(entity.getX(), entity.getZ());
    }

    private Vec3 resolveCenter(LivingEntity boss, Optional<String> pointId) {
        return pointId.isPresent() ? lockedPoints.get(pointId.get()) : boss.position();
    }

    private static boolean withinLockedPointArrivalRadius(
            LivingEntity boss,
            Vec3 point
    ) {
        double allowedDistance = boss.getBbWidth() * 0.5 + LOCKED_POINT_ARRIVAL_MARGIN;
        return boss.position().distanceToSqr(point) <= allowedDistance * allowedDistance;
    }

    private static double horizontalDistanceSquared(Vec2 start, Vec2 end) {
        double deltaX = end.x() - start.x();
        double deltaZ = end.z() - start.z();
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    private void resetMovementTrace(LivingEntity boss) {
        Vec2 position = horizontalPosition(boss);
        tickMovementStart = position;
        tickMovementEnd = position;
        movementRecorded = false;
        horizontalTravelThisTick = 0.0;
    }

    private void recordMovement(Vec2 before, Vec2 after) {
        if (!movementRecorded) {
            tickMovementStart = before;
        }
        tickMovementEnd = after;
        movementRecorded = true;
    }

    private void moveControlled(
            LivingEntity entity,
            Vec3 requestedMovement,
            MaleniaServerIntent intent,
            double maxTravel
    ) {
        double traveled = movementTravel.getOrDefault(intent, 0.0);
        double remainingTravel = Math.max(0.0, maxTravel - traveled);
        if (remainingTravel == 0.0 || requestedMovement.lengthSqr() == 0.0) {
            return;
        }
        Vec3 movement = requestedMovement;
        double requestedDistance = movement.length();
        if (requestedDistance > remainingTravel) {
            movement = movement.scale(remainingTravel / requestedDistance);
        }
        double horizontalDistance = Math.hypot(movement.x, movement.z);
        double remainingHorizontal = Math.max(
                0.0,
                MAX_HORIZONTAL_TRAVEL_PER_TICK - horizontalTravelThisTick
        );
        if (horizontalDistance > remainingHorizontal) {
            movement = movement.scale(remainingHorizontal / horizontalDistance);
        }
        movement = clipMovementAgainstTargets(entity, movement);
        Vec3 before = entity.position();
        entity.move(MoverType.SELF, movement);
        Vec3 actualMovement = entity.position().subtract(before);
        movementTravel.put(intent, Math.min(maxTravel, traveled + actualMovement.length()));
        horizontalTravelThisTick += Math.hypot(actualMovement.x, actualMovement.z);
    }

    private static Vec3 clipMovementAgainstTargets(LivingEntity entity, Vec3 movement) {
        if (!(entity.level() instanceof ServerLevel level)
                || movement.lengthSqr() < POSITION_EPSILON_SQUARED) {
            return movement;
        }
        AABB entityBounds = entity.getBoundingBox();
        AABB sweptBounds = entityBounds.expandTowards(movement).inflate(ENTITY_COLLISION_MARGIN);
        Vec3 start = entityBounds.getCenter();
        Vec3 end = start.add(movement);
        double movementLength = movement.length();
        double maximumDistance = movementLength;
        double horizontalInflation = entity.getBbWidth() * 0.5 + ENTITY_COLLISION_MARGIN;
        double verticalInflation = entity.getBbHeight() * 0.5 + ENTITY_COLLISION_MARGIN;
        for (LivingEntity target : level.getEntitiesOfClass(
                LivingEntity.class,
                sweptBounds,
                candidate -> validHitTarget(level, entity, candidate)
        )) {
            AABB expandedTarget = target.getBoundingBox().inflate(
                    horizontalInflation,
                    verticalInflation,
                    horizontalInflation
            );
            if (expandedTarget.contains(start)) {
                continue;
            }
            Optional<Vec3> collision = expandedTarget.clip(start, end);
            if (collision.isPresent()) {
                maximumDistance = Math.min(
                        maximumDistance,
                        start.distanceTo(collision.get())
                );
            }
        }
        return maximumDistance < movementLength
                ? movement.scale(maximumDistance / movementLength)
                : movement;
    }

    private void applyLockedFacing(LivingEntity boss) {
        if (lockedFacing == null) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-lockedFacing.x(), lockedFacing.z()));
        boss.setYRot(yaw);
        boss.setYHeadRot(yaw);
        boss.setYBodyRot(yaw);
    }

    private LivingEntity resolveTarget(
            ServerLevel level,
            LivingEntity boss,
            MaleniaActionSnapshot snapshot
    ) {
        if (snapshot.targetId().isEmpty()) {
            return null;
        }
        net.minecraft.world.entity.Entity entity = level.getEntity(snapshot.targetId().get());
        if (!(entity instanceof LivingEntity target)) {
            return null;
        }
        if (boss == null) {
            return target.level() == level && target.isAlive() && !target.isRemoved()
                    ? target
                    : null;
        }
        return validHitTarget(level, boss, target) ? target : null;
    }

    private ServerPlayer resolveGrabbed(ServerLevel level) {
        if (grabbedPlayerId == null) {
            return null;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(grabbedPlayerId);
        return validTarget(level, player) ? player : null;
    }

    private static boolean validTarget(ServerLevel level, ServerPlayer player) {
        return player != null
                && player.level() == level
                && player.isAlive()
                && !player.isRemoved()
            && !player.isCreative()
                && !player.isSpectator();
    }

        private static boolean validHitTarget(
            ServerLevel level,
            LivingEntity boss,
            LivingEntity target
        ) {
        return target != boss
            && target.level() == level
            && target.isAlive()
            && !target.isRemoved()
            && !(target instanceof Enemy)
            && !boss.isAlliedTo(target)
            && !target.isAlliedTo(boss)
            && (!(target instanceof Player player)
                || (!player.isCreative() && !player.isSpectator()));
        }

    private void releaseGrab() {
        grabbedPlayerId = null;
        grabLeashDistance = 0.0;
        grabImpaleHit = null;
        grabThrowHit = null;
        grabImpaleGameTick = Long.MIN_VALUE;
        grabThrowGameTick = Long.MIN_VALUE;
    }

    private static ServerLevel requireServerLevel(LivingEntity boss) {
        if (!(boss.level() instanceof ServerLevel level)) {
            throw new IllegalStateException("Malenia intents may only execute on a server level");
        }
        return level;
    }

    private static String occurrenceKey(MaleniaActionSnapshot snapshot, HitSpec hit) {
        return snapshot.actionTick() + ":" + hit.hitIdSuffix();
    }

    private static int indexFor(Map<String, Integer> indices, String key) {
        Integer existing = indices.get(key);
        if (existing != null) {
            return existing;
        }
        int index = indices.size();
        indices.put(key, index);
        return index;
    }

    public record GeometryDefaults(double sectorArcDegrees, double capsuleWidth) {
        public GeometryDefaults {
            if (!Double.isFinite(sectorArcDegrees)
                    || sectorArcDegrees <= 0.0
                    || sectorArcDegrees > 360.0) {
                throw new IllegalArgumentException(
                        "sectorArcDegrees must be finite and in (0, 360]"
                );
            }
            if (!Double.isFinite(capsuleWidth) || capsuleWidth <= 0.0) {
                throw new IllegalArgumentException("capsuleWidth must be finite and positive");
            }
        }
    }

    public record MovementTrace(Vec2 start, Vec2 end) {
        public MovementTrace {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
        }
    }

    public record PersistentZoneSnapshot(
            String id,
            Vec3 center,
            double radius,
            long startedGameTick,
            long endGameTick,
            DamageChannel hitChannel
    ) {
        public PersistentZoneSnapshot {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(center, "center");
            if (!Double.isFinite(radius) || radius <= 0.0) {
                throw new IllegalArgumentException("radius must be finite and positive");
            }
            if (startedGameTick < 0L || endGameTick <= startedGameTick) {
                throw new IllegalArgumentException("zone timeline must be non-negative and ordered");
            }
            Objects.requireNonNull(hitChannel, "hitChannel");
        }
    }

    @FunctionalInterface
    public interface InstantGuardHandler {
        InstantGuardHandler NONE = (player, hit, attemptedDamage) -> Optional.empty();

        Optional<InstantGuardResult> tryInstantGuard(
                ServerPlayer player,
                HitSpec hit,
                float attemptedDamage
        );
    }

    @FunctionalInterface
    public interface BlockedHitHandler {
        BlockedHitHandler NONE = (player, actionId) -> blockedDamage -> {
        };

        BlockedHitPreparation prepare(
                ServerPlayer player,
                MaleniaActionId actionId
        );
    }

    @FunctionalInterface
    public interface BlockedHitPreparation {
        void apply(float blockedDamage);
    }

    @FunctionalInterface
    public interface AfterDamageHandler {
        void apply(float healthDamage);
    }

    public record InstantGuardResult(double damageMultiplier, AfterDamageHandler afterDamage) {
        public InstantGuardResult {
            if (!Double.isFinite(damageMultiplier)
                    || damageMultiplier < 0.0
                    || damageMultiplier > 1.0) {
                throw new IllegalArgumentException(
                        "damageMultiplier must be finite and between 0 and 1"
                );
            }
            Objects.requireNonNull(afterDamage, "afterDamage");
        }
    }

    private record ShieldDamageKey(
            long actionSequence,
            String hitIdSuffix,
            UUID targetId
    ) {
        private ShieldDamageKey {
            if (actionSequence < 0L) {
                throw new IllegalArgumentException("actionSequence must be non-negative");
            }
            Objects.requireNonNull(hitIdSuffix, "hitIdSuffix");
            Objects.requireNonNull(targetId, "targetId");
        }
    }

    private record StrikeVolume(HorizontalShape shape, double minY, double maxY) {
        private StrikeVolume {
            Objects.requireNonNull(shape, "shape");
            if (!Double.isFinite(minY) || !Double.isFinite(maxY) || maxY <= minY) {
                throw new IllegalArgumentException("vertical bounds must be finite and ordered");
            }
        }

        private static StrikeVolume atBoss(HorizontalShape shape, LivingEntity boss) {
            AABB bounds = boss.getBoundingBox();
            return new StrikeVolume(shape, bounds.minY, bounds.maxY);
        }

        private static StrikeVolume fromActionStart(
            HorizontalShape shape,
            LivingEntity boss,
            double actionStartY
        ) {
            AABB bounds = boss.getBoundingBox();
            double minY = Double.isFinite(actionStartY)
                ? Math.min(actionStartY, bounds.minY)
                : bounds.minY;
            return new StrikeVolume(shape, minY, bounds.maxY);
        }

        private static StrikeVolume atPoint(
                HorizontalShape shape,
                Vec3 point,
                LivingEntity boss
        ) {
            return new StrikeVolume(shape, point.y, point.y + boss.getBbHeight());
        }

        private static StrikeVolume betweenPoints(
            HorizontalShape shape,
            Vec3 first,
            Vec3 second,
            double height
        ) {
            double minY = Math.min(first.y, second.y);
            double maxY = Math.max(first.y, second.y) + height;
            return new StrikeVolume(shape, minY, maxY);
        }

        private AABB bounds() {
            return new AABB(
                    shape.minX(),
                    minY,
                    shape.minZ(),
                    shape.maxX(),
                    maxY,
                    shape.maxZ()
            );
        }
    }

    private static final class MoveTowardProgress {
        private final double initialDistance;
        private int remainingTicks;
        private double traveled;

        private MoveTowardProgress(double initialDistance, int remainingTicks) {
            this.initialDistance = initialDistance;
            this.remainingTicks = remainingTicks;
        }

        private double nextBudget() {
            if (remainingTicks <= 0) {
                return 0.0;
            }
            return Math.max(0.0, initialDistance - traveled) / remainingTicks--;
        }

        private void recordTravel(double distance) {
            traveled = Math.min(initialDistance, traveled + distance);
        }
    }

    private record ActiveRotZone(
            String zoneId,
            long actionSequence,
            int actionTick,
            long startedGameTick,
            int durationTicks,
            int intervalTicks,
            HitSpec hit,
            Vec3 center,
            double radius,
            StrikeVolume volume
    ) {
        private ActiveRotZone {
            Objects.requireNonNull(zoneId, "zoneId");
            Objects.requireNonNull(hit, "hit");
            Objects.requireNonNull(center, "center");
            Objects.requireNonNull(volume, "volume");
        }
    }
}