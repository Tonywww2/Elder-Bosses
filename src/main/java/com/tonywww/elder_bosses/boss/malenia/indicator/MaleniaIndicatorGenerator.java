package com.tonywww.elder_bosses.boss.malenia.indicator;

import com.tonywww.elder_bosses.boss.malenia.config.MaleniaSkillConfigSnapshot;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.boss.malenia.execution.MaleniaActionPlan;
import com.tonywww.elder_bosses.boss.malenia.execution.MaleniaActionPlan.ScheduledIntent;
import com.tonywww.elder_bosses.boss.malenia.execution.MaleniaServerIntent;
import com.tonywww.elder_bosses.boss.malenia.execution.MaleniaServerIntent.HitSpec;
import com.tonywww.elder_bosses.boss.malenia.runtime.MaleniaActionSnapshot;
import com.tonywww.elder_bosses.combat.damage.DamageChannel;
import com.tonywww.elder_bosses.network.IndicatorSnapshotPacket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.tonywww.elder_bosses.boss.malenia.indicator.MaleniaIndicatorFrame.InstantGuardCueSnapshot;
import static com.tonywww.elder_bosses.boss.malenia.indicator.MaleniaIndicatorSnapshot.IndicatorState;
import static com.tonywww.elder_bosses.boss.malenia.indicator.MaleniaIndicatorSnapshot.SegmentSlot;
import static com.tonywww.elder_bosses.boss.malenia.indicator.MaleniaIndicatorSnapshot.StyleRole;

public final class MaleniaIndicatorGenerator {
    private static final int IMMINENT_TICKS = 4;
    private static final double DISTANCE_EPSILON = 1.0E-6;

    private MaleniaIndicatorGenerator() {
    }

    public static MaleniaIndicatorFrame generate(
            MaleniaActionPlan plan,
            MaleniaActionSnapshot action,
            MaleniaSkillConfigSnapshot skillConfig,
            Context context
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(skillConfig, "skillConfig");
        Objects.requireNonNull(context, "context");
        if (plan.actionId() != action.actionId()) {
            throw new IllegalArgumentException("plan and action snapshot must use the same action id");
        }
        if (action.actionTick() >= plan.totalTicks()) {
            throw new IllegalArgumentException("action snapshot is outside the plan timeline");
        }

        long serverGameTick = Math.addExact(action.startGameTick(), action.actionTick());
        if (!plan.enabled()) {
            return new MaleniaIndicatorFrame(serverGameTick, List.of(), List.of(), List.of());
        }

        ActionMapping mapping = mappingFor(action.actionId());
        List<SegmentBatch> batches = batches(segments(plan, skillConfig, mapping));
        int currentIndex = currentBatchIndex(batches, action.actionTick());
        if (currentIndex < 0) {
            return new MaleniaIndicatorFrame(serverGameTick, List.of(), List.of(), List.of());
        }

        List<MaleniaIndicatorSnapshot> current = new ArrayList<>();
        List<MaleniaIndicatorSnapshot> next = new ArrayList<>();
        List<InstantGuardCueSnapshot> guardCues = new ArrayList<>();
        appendBatch(
                batches.get(currentIndex),
                SegmentSlot.CURRENT,
                action,
                skillConfig,
                context,
                mapping,
                current,
                guardCues
        );
        if (currentIndex + 1 < batches.size()) {
            appendBatch(
                    batches.get(currentIndex + 1),
                    SegmentSlot.NEXT,
                    action,
                    skillConfig,
                    context,
                    mapping,
                    next,
                    guardCues
            );
        }
        return new MaleniaIndicatorFrame(serverGameTick, current, next, guardCues);
    }

    public static String movementPathKey(String segmentId) {
        Objects.requireNonNull(segmentId, "segmentId");
        return segmentId + ".movement";
    }

    private static void appendBatch(
            SegmentBatch batch,
            SegmentSlot slot,
            MaleniaActionSnapshot action,
            MaleniaSkillConfigSnapshot skillConfig,
            Context context,
            ActionMapping mapping,
            List<MaleniaIndicatorSnapshot> output,
            List<InstantGuardCueSnapshot> guardCues
    ) {
        for (Segment segment : batch.segments()) {
            if (action.actionTick() < segment.startTick()) {
                continue;
            }
            IndicatorState state = stateAt(action.actionTick(), segment);
            resolvedShape(segment, action, skillConfig, context, mapping)
                    .map(shape -> snapshot(segment, slot, state, action, context, shape, ""))
                    .ifPresent(output::add);
            movementShape(segment, action, skillConfig, context, mapping)
                    .map(shape -> snapshot(
                            segment,
                            slot,
                            state,
                            action,
                            context,
                            shape,
                            ".movement"
                    ))
                    .ifPresent(output::add);
            if (segment.hit() != null && segment.hit().instantGuardEligible()) {
                guardCues.add(new InstantGuardCueSnapshot(
                        context.bossEntityId(),
                        indicatorId(action, segment, ".instant_guard"),
                        action.actionId(),
                        action.sequence(),
                        segment.index(),
                        slot,
                        state,
                        gameTick(action, segment.lockTick()),
                        gameTick(action, segment.activeTick()),
                        gameTick(action, segment.endTick())
                ));
            }
        }
    }

    private static MaleniaIndicatorSnapshot snapshot(
            Segment segment,
            SegmentSlot slot,
            IndicatorState state,
            MaleniaActionSnapshot action,
            Context context,
            ResolvedShape shape,
            String suffix
    ) {
        return new MaleniaIndicatorSnapshot(
                context.bossEntityId(),
                indicatorId(action, segment, suffix),
                segment.id() + suffix,
                action.actionId(),
                action.sequence(),
                segment.index(),
                slot,
                state,
                suffix.isEmpty() ? styleFor(segment) : StyleRole.MOVEMENT_DASHED,
                shape.origin(),
                shape.yawDegrees(),
                shape.geometry(),
                gameTick(action, segment.startTick()),
                gameTick(action, segment.lockTick()),
                gameTick(action, segment.activeTick()),
                gameTick(action, segment.endTick())
        );
    }

    private static Optional<ResolvedShape> resolvedShape(
            Segment segment,
            MaleniaActionSnapshot action,
            MaleniaSkillConfigSnapshot skillConfig,
            Context context,
            ActionMapping mapping
    ) {
        MaleniaServerIntent intent = segment.intent();
        Direction facing = facingAt(segment, action, context);
        Optional<IndicatorPoint> resolvedOrigin = originFor(segment, action, context, mapping);
        if (resolvedOrigin.isEmpty()) {
            return Optional.empty();
        }
        IndicatorPoint origin = resolvedOrigin.get();
        if (intent instanceof MaleniaServerIntent.HitSector sector) {
            double arcDegrees = sector.arcDegrees().orElse(
                    skillConfig.singleSlash().arcDegrees()
            );
            return Optional.of(new ResolvedShape(
                    origin,
                    facing.yawDegrees(),
                    new IndicatorGeometry.Sector(sector.range(), arcDegrees)
            ));
        }
        if (intent instanceof MaleniaServerIntent.HitCapsule capsule) {
            double width = capsule.width().orElse(skillConfig.thrust().width());
            return Optional.of(new ResolvedShape(
                    origin,
                    facing.yawDegrees(),
                    new IndicatorGeometry.Capsule(capsule.length(), width)
            ));
        }
        if (intent instanceof MaleniaServerIntent.HitCircle circle) {
            return Optional.of(new ResolvedShape(
                    origin,
                    facing.yawDegrees(),
                    new IndicatorGeometry.Circle(circle.radius())
            ));
        }
        if (intent instanceof MaleniaServerIntent.HitAnnulus annulus) {
            return Optional.of(new ResolvedShape(
                    origin,
                    facing.yawDegrees(),
                    new IndicatorGeometry.Annulus(
                            annulus.innerRadius(),
                            annulus.outerRadius()
                    )
            ));
        }
        if (intent instanceof MaleniaServerIntent.RotZone zone) {
            return Optional.of(new ResolvedShape(
                    origin,
                    facing.yawDegrees(),
                    new IndicatorGeometry.Zone(zone.radius(), zone.durationTicks())
            ));
        }
        if (intent instanceof MaleniaServerIntent.IndicatorOnlyZone zone) {
            return Optional.of(new ResolvedShape(
                origin,
                facing.yawDegrees(),
                new IndicatorGeometry.Zone(zone.radius(), zone.durationTicks())
            ));
        }
        if (intent instanceof MaleniaServerIntent.Grab grab) {
            return Optional.of(new ResolvedShape(
                    origin,
                    facing.yawDegrees(),
                    new IndicatorGeometry.Capsule(grab.length(), grab.width())
            ));
        }
        if (intent instanceof MaleniaServerIntent.WaterfowlBurst burst) {
            int index = burst.burstIndex();
            double maxTravel = skillConfig.waterfowlDance().burstMaxTravel().get(index);
            String pointId = "waterfowl_burst_" + (index + 1);
            Optional<List<IndicatorPoint>> points = pathFor(
                    segment,
                    action,
                    context,
                    segment.id(),
                    maxTravel,
                    Optional.ofNullable(context.lockedPoints().get(pointId))
                        .or(() -> context.trackingTarget()),
                    true
            );
            return points.map(path -> new ResolvedShape(
                    path.get(0),
                    directionOf(path, facing).yawDegrees(),
                    new IndicatorGeometry.Path(burst.width(), path)
            ));
        }
        if (intent instanceof MaleniaServerIntent.PhantomStrike phantom) {
            String pointId = phantom.attackKind() == MaleniaServerIntent.PhantomAttackKind.BOSS_DIVE
                ? "boss_dive"
                : "phantom_" + (phantom.strikeIndex() + 1);
            Optional<IndicatorPoint> phantomOrigin = phantom.attackKind()
                == MaleniaServerIntent.PhantomAttackKind.BOSS_DIVE
                ? Optional.of(context.bossPosition())
                : Optional.ofNullable(context.lockedPoints().get(pointId + "_origin"));
            Optional<IndicatorPoint> target = Optional.ofNullable(context.lockedPoints().get(pointId));
            Optional<List<IndicatorPoint>> points = phantomOrigin.flatMap(start ->
                target.map(end -> List.of(start, end))
            );
            if (points.isEmpty()) {
                return Optional.empty();
            }
            List<IndicatorPoint> path = points.get();
            double length = path.get(0).horizontalDistanceTo(path.get(path.size() - 1));
            if (length <= DISTANCE_EPSILON) {
                return Optional.empty();
            }
            return Optional.of(new ResolvedShape(
                    path.get(0),
                    directionOf(path, facing).yawDegrees(),
                    new IndicatorGeometry.Capsule(length, phantom.width())
            ));
        }
        return Optional.empty();
    }

    private static Optional<ResolvedShape> movementShape(
            Segment segment,
            MaleniaActionSnapshot action,
            MaleniaSkillConfigSnapshot skillConfig,
            Context context,
            ActionMapping mapping
    ) {
        if (mapping == ActionMapping.RUNNING_PATH && segment.id().equals("slash")) {
            Optional<IndicatorPoint> target = Optional.ofNullable(
                    context.lockedPoints().get("running_target")
            ).or(() -> context.trackingTarget());
            return movementPath(
                    segment,
                    action,
                    context,
                    skillConfig.runningSlash().range(),
                    target,
                    facingAt(segment, action, context)
            );
        }
        if (mapping == ActionMapping.RETREAT_PATH && segment.id().equals("slash")) {
            Direction facing = facingAt(segment, action, context);
            IndicatorPoint origin = context.bossPosition();
            double distance = skillConfig.retreatSlash().retreatDistance();
            IndicatorPoint retreatTarget = new IndicatorPoint(
                    origin.x() - facing.x() * distance,
                    origin.y(),
                    origin.z() - facing.z() * distance
            );
            return movementPath(
                    segment,
                    action,
                    context,
                    distance,
                    Optional.of(retreatTarget),
                    new Direction(-facing.x(), -facing.z())
            );
        }
        return Optional.empty();
    }

    private static Optional<ResolvedShape> movementPath(
            Segment segment,
            MaleniaActionSnapshot action,
            Context context,
            double maxTravel,
            Optional<IndicatorPoint> target,
            Direction fallbackDirection
    ) {
        return pathFor(
                segment,
                action,
                context,
                movementPathKey(segment.id()),
                maxTravel,
                target,
                false
        ).map(path -> new ResolvedShape(
                path.get(0),
                directionOf(path, fallbackDirection).yawDegrees(),
                new IndicatorGeometry.Path(0.0, path)
        ));
    }

    private static Optional<List<IndicatorPoint>> pathFor(
            Segment segment,
            MaleniaActionSnapshot action,
            Context context,
            String pathKey,
            double maxTravel,
            Optional<IndicatorPoint> target,
            boolean requiredWhenActive
    ) {
        List<IndicatorPoint> serverPath = context.serverPathsBySegmentId().get(pathKey);
        if (serverPath != null) {
            requirePathWithin(serverPath, maxTravel, pathKey);
            return Optional.of(serverPath);
        }
        if (action.actionTick() >= segment.activeTick()) {
            if (requiredWhenActive) {
                throw new IllegalArgumentException(
                        "active segment " + segment.id() + " requires an authoritative server path"
                );
            }
            return Optional.empty();
        }
        return target.map(point -> directPath(context.bossPosition(), point, maxTravel));
    }

    private static List<IndicatorPoint> directPath(
            IndicatorPoint origin,
            IndicatorPoint target,
            double maxTravel
    ) {
        double distance = origin.horizontalDistanceTo(target);
        if (distance <= maxTravel || distance <= DISTANCE_EPSILON) {
            return List.of(origin, target);
        }
        double ratio = maxTravel / distance;
        return List.of(origin, new IndicatorPoint(
                origin.x() + (target.x() - origin.x()) * ratio,
                origin.y() + (target.y() - origin.y()) * ratio,
                origin.z() + (target.z() - origin.z()) * ratio
        ));
    }

    private static void requirePathWithin(
            List<IndicatorPoint> path,
            double maxTravel,
            String pathKey
    ) {
        double length = 0.0;
        for (int index = 1; index < path.size(); index++) {
            length += path.get(index - 1).horizontalDistanceTo(path.get(index));
        }
        if (length > maxTravel + DISTANCE_EPSILON) {
            throw new IllegalArgumentException(
                    "server path " + pathKey + " exceeds its configured travel range"
            );
        }
    }

    private static Optional<IndicatorPoint> originFor(
            Segment segment,
            MaleniaActionSnapshot action,
            Context context,
            ActionMapping mapping
    ) {
        IndicatorPoint explicit = context.segmentOrigins().get(segment.id());
        if (explicit != null) {
            return Optional.of(explicit);
        }
        if (mapping == ActionMapping.AEONIA) {
            if (action.actionTick() >= segment.activeTick()) {
                if (segment.persistent() && action.actionTick() > segment.activeTick()) {
                    return Optional.empty();
                }
                return Optional.of(context.bossPosition());
            }
            IndicatorPoint impact = context.lockedPoints().get("aeonia_impact");
            if (impact != null) {
                return Optional.of(impact);
            }
            if (action.actionTick() >= segment.lockTick()) {
                return Optional.empty();
            }
            return context.trackingTarget();
        }
        return Optional.of(context.bossPosition());
    }

    private static Direction facingAt(
            Segment segment,
            MaleniaActionSnapshot action,
            Context context
    ) {
        if (action.actionTick() >= segment.lockTick() && context.lockedFacing().isPresent()) {
            return context.lockedFacing().get();
        }
        return context.currentFacing();
    }

    private static Direction directionOf(List<IndicatorPoint> path, Direction fallback) {
        IndicatorPoint start = path.get(0);
        IndicatorPoint end = path.get(path.size() - 1);
        double deltaX = end.x() - start.x();
        double deltaZ = end.z() - start.z();
        if (Math.hypot(deltaX, deltaZ) <= DISTANCE_EPSILON) {
            return fallback;
        }
        return new Direction(deltaX, deltaZ);
    }

    private static StyleRole styleFor(Segment segment) {
        return segment.hit() == null
                || segment.hit().channel() == DamageChannel.SCARLET_ROT
                ? StyleRole.SCARLET_ROT_DARK_RED
                : StyleRole.PHYSICAL_SILVER;
    }

    private static IndicatorState stateAt(int actionTick, Segment segment) {
        if (actionTick >= segment.endTick()) {
            return IndicatorState.EXPIRED;
        }
        if (segment.persistent() && actionTick >= segment.activeTick()) {
            return IndicatorState.PERSISTENT;
        }
        if (actionTick >= segment.activeTick()) {
            return IndicatorState.ACTIVE;
        }
        if (segment.activeTick() - actionTick <= IMMINENT_TICKS) {
            return IndicatorState.IMMINENT;
        }
        if (actionTick >= segment.lockTick()) {
            return IndicatorState.LOCKED;
        }
        return IndicatorState.TRACKING;
    }

    private static int currentBatchIndex(List<SegmentBatch> batches, int actionTick) {
        if (batches.isEmpty()) {
            return -1;
        }
        int index = 0;
        while (index + 1 < batches.size()
                && actionTick >= batches.get(index + 1).activeTick()) {
            index++;
        }
        while (index < batches.size()
                && actionTick >= batches.get(index).endTick()
                + IndicatorSnapshotPacket.EXPIRED_FADE_TICKS) {
            index++;
        }
        return index < batches.size() ? index : -1;
    }

    private static List<SegmentBatch> batches(List<Segment> segments) {
        Map<Integer, List<Segment>> grouped = new LinkedHashMap<>();
        for (Segment segment : segments) {
            grouped.computeIfAbsent(segment.activeTick(), ignored -> new ArrayList<>())
                    .add(segment);
        }
        List<SegmentBatch> batches = new ArrayList<>(grouped.size());
        for (Map.Entry<Integer, List<Segment>> entry : grouped.entrySet()) {
            int endTick = entry.getValue().stream()
                    .mapToInt(Segment::endTick)
                    .max()
                    .orElseThrow();
            batches.add(new SegmentBatch(entry.getKey(), endTick, entry.getValue()));
        }
        return List.copyOf(batches);
    }

    private static List<Segment> segments(
            MaleniaActionPlan plan,
            MaleniaSkillConfigSnapshot skillConfig,
            ActionMapping mapping
    ) {
        Map<String, MutableSegment> segments = new LinkedHashMap<>();
        for (ScheduledIntent scheduled : plan.intents()) {
            MaleniaServerIntent intent = scheduled.intent();
            HitSpec hit = hitOf(intent);
            if (hit == null && !(intent instanceof MaleniaServerIntent.IndicatorOnlyZone)) {
                continue;
            }
            String segmentId = segmentId(intent, hit);
            MutableSegment segment = segments.computeIfAbsent(
                    segmentId,
                    ignored -> new MutableSegment(
                            segments.size(),
                            segmentId,
                            intent,
                            hit
                    )
            );
            segment.accept(scheduled.actionTick(), intent);
        }

        if (mapping == ActionMapping.WATERFOWL
                && segments.size() != skillConfig.waterfowlDance().burstCount()) {
            throw new IllegalArgumentException("waterfowl plan must expose every configured burst");
        }
        if (mapping == ActionMapping.PHANTOMS
                && segments.size() != skillConfig.scarletPhantoms().phantomCount() + 2) {
            throw new IllegalArgumentException(
                    "phantom plan must expose its warning pool, all phantoms, and boss dive"
            );
        }

        int startTick = mapping == ActionMapping.AEONIA
                ? skillConfig.scarletAeonia().telegraphStartTick()
                : 0;
        if (mapping == ActionMapping.AEONIA && !skillConfig.tuning(MaleniaActionId.SCARLET_AEONIA).componentStages().isEmpty()) {
            startTick = plan.intents().stream().filter(scheduled -> scheduled.intent() instanceof MaleniaServerIntent.LockPoint point
            && point.pointId().equals("aeonia_impact")).mapToInt(ScheduledIntent::actionTick).findFirst().orElse(0) + 1;
        }
        int telegraphStart = startTick;
        return segments.values().stream()
                .map(segment -> segment.freeze(
                Math.min(telegraphStart, segment.firstTick),
                        lockTick(plan, segment)
                ))
                .sorted(Comparator.comparingInt(Segment::activeTick)
                        .thenComparingInt(Segment::index))
                .toList();
    }

    private static int lockTick(MaleniaActionPlan plan, MutableSegment segment) {
        String pointId = pointIdFor(segment.intent);
        int lockTick = -1;
        for (ScheduledIntent scheduled : plan.intents()) {
            if (scheduled.actionTick() > segment.firstTick) {
                break;
            }
            if (segment.intent instanceof MaleniaServerIntent.PhantomStrike phantom
                    && scheduled.intent() instanceof MaleniaServerIntent.LockPhantom lockPhantom
                    && phantom.strikeIndex() == lockPhantom.phantomIndex()) {
                lockTick = scheduled.actionTick();
            } else if (scheduled.intent() instanceof MaleniaServerIntent.LockPoint lockPoint
                    && lockPoint.pointId().equals(pointId)) {
                lockTick = scheduled.actionTick();
            } else if (pointId.isEmpty()
                    && scheduled.intent() instanceof MaleniaServerIntent.LockFacing) {
                lockTick = scheduled.actionTick();
            }
        }
        return lockTick >= 0 ? lockTick : segment.firstTick;
    }

    private static String pointIdFor(MaleniaServerIntent intent) {
        if (intent instanceof MaleniaServerIntent.WaterfowlBurst burst) {
            return "waterfowl_burst_" + (burst.burstIndex() + 1);
        }
        if (intent instanceof MaleniaServerIntent.HitCircle
                || intent instanceof MaleniaServerIntent.RotZone) {
            return "aeonia_impact";
        }
        if (intent instanceof MaleniaServerIntent.PhantomStrike phantom
                && phantom.attackKind() == MaleniaServerIntent.PhantomAttackKind.BOSS_DIVE) {
            return "boss_dive";
        }
        return "";
    }

    private static HitSpec hitOf(MaleniaServerIntent intent) {
        if (intent instanceof MaleniaServerIntent.HitSector value) {
            return value.hit();
        }
        if (intent instanceof MaleniaServerIntent.HitCapsule value) {
            return value.hit();
        }
        if (intent instanceof MaleniaServerIntent.HitCircle value) {
            return value.hit();
        }
        if (intent instanceof MaleniaServerIntent.HitAnnulus value) {
            return value.hit();
        }
        if (intent instanceof MaleniaServerIntent.RotZone value) {
            return value.hit();
        }
        if (intent instanceof MaleniaServerIntent.Grab value) {
            return value.hit();
        }
        if (intent instanceof MaleniaServerIntent.WaterfowlBurst value) {
            return value.hit();
        }
        if (intent instanceof MaleniaServerIntent.PhantomStrike value) {
            return value.hit();
        }
        return null;
    }

    private static String segmentId(MaleniaServerIntent intent, HitSpec hit) {
        if (intent instanceof MaleniaServerIntent.IndicatorOnlyZone zone) {
            return zone.zoneId();
        }
        if (intent instanceof MaleniaServerIntent.PhantomStrike phantom) {
            return phantom.attackKind() == MaleniaServerIntent.PhantomAttackKind.BOSS_DIVE
                    ? "boss_dive"
                    : "phantom_" + (phantom.strikeIndex() + 1);
        }
        return hit.hitIdSuffix();
    }

    private static String indicatorId(
            MaleniaActionSnapshot action,
            Segment segment,
            String suffix
    ) {
        return action.sequence()
                + ":" + action.actionId().serializedName()
                + ":" + segment.id()
                + suffix;
    }

    private static long gameTick(MaleniaActionSnapshot action, int actionTick) {
        return Math.addExact(action.startGameTick(), actionTick);
    }

    private static ActionMapping mappingFor(MaleniaActionId actionId) {
        return switch (actionId) {
            case SINGLE_SLASH -> ActionMapping.STANDARD;
            case DOUBLE_SLASH -> ActionMapping.STANDARD;
            case RAPID_SLASHES -> ActionMapping.STANDARD;
            case RUNNING_SLASH -> ActionMapping.RUNNING_PATH;
            case UPWARD_COMBO -> ActionMapping.STANDARD;
            case KICK -> ActionMapping.STANDARD;
            case THRUST -> ActionMapping.STANDARD;
            case GRAB_IMPALE -> ActionMapping.STANDARD;
            case RETREAT_SLASH -> ActionMapping.RETREAT_PATH;
            case WATERFOWL_DANCE -> ActionMapping.WATERFOWL;
            case SCARLET_AEONIA -> ActionMapping.AEONIA;
            case SCARLET_PLUNGE -> ActionMapping.STANDARD;
            case FLYING_SLASH -> ActionMapping.STANDARD;
            case SCARLET_PHANTOMS -> ActionMapping.PHANTOMS;
            case WINGED_SWEEP -> ActionMapping.STANDARD;
        };
    }

    public record Direction(double x, double z) {
        public Direction {
            if (!Double.isFinite(x) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("direction components must be finite");
            }
            double length = Math.hypot(x, z);
            if (length <= DISTANCE_EPSILON) {
                throw new IllegalArgumentException("direction must not be zero");
            }
            x /= length;
            z /= length;
        }

        public double yawDegrees() {
            return Math.toDegrees(Math.atan2(-x, z));
        }
    }

    /**
     * All values are captured on the server. Path keys are segment ids; harmless movement
     * companions use {@link #movementPathKey(String)}. Segment origins freeze persistent areas.
     */
    public record Context(
            int bossEntityId,
            IndicatorPoint bossPosition,
            Direction currentFacing,
            Optional<Direction> lockedFacing,
            Map<String, IndicatorPoint> lockedPoints,
            Optional<IndicatorPoint> trackingTarget,
            Map<String, List<IndicatorPoint>> serverPathsBySegmentId,
            Map<String, IndicatorPoint> segmentOrigins
    ) {
        public Context {
            if (bossEntityId < 0) {
                throw new IllegalArgumentException("bossEntityId must be non-negative");
            }
            Objects.requireNonNull(bossPosition, "bossPosition");
            Objects.requireNonNull(currentFacing, "currentFacing");
            lockedFacing = Objects.requireNonNull(lockedFacing, "lockedFacing");
            lockedPoints = Map.copyOf(Objects.requireNonNull(lockedPoints, "lockedPoints"));
            trackingTarget = Objects.requireNonNull(trackingTarget, "trackingTarget");
            serverPathsBySegmentId = copyPaths(serverPathsBySegmentId);
            segmentOrigins = Map.copyOf(Objects.requireNonNull(
                    segmentOrigins,
                    "segmentOrigins"
            ));
        }

        private static Map<String, List<IndicatorPoint>> copyPaths(
                Map<String, List<IndicatorPoint>> paths
        ) {
            Objects.requireNonNull(paths, "serverPathsBySegmentId");
            Map<String, List<IndicatorPoint>> copy = new LinkedHashMap<>();
            for (Map.Entry<String, List<IndicatorPoint>> entry : paths.entrySet()) {
                String key = Objects.requireNonNull(entry.getKey(), "server path key");
                List<IndicatorPoint> path = List.copyOf(Objects.requireNonNull(
                        entry.getValue(),
                        "server path"
                ));
                if (path.size() < 2) {
                    throw new IllegalArgumentException(
                            "server path " + key + " must contain at least two points"
                    );
                }
                path.forEach(point -> Objects.requireNonNull(point, "server path point"));
                copy.put(key, path);
            }
            return Map.copyOf(copy);
        }
    }

    private enum ActionMapping {
        STANDARD,
        RUNNING_PATH,
        RETREAT_PATH,
        WATERFOWL,
        AEONIA,
        PHANTOMS
    }

    private record ResolvedShape(
            IndicatorPoint origin,
            double yawDegrees,
            IndicatorGeometry geometry
    ) {
        private ResolvedShape {
            Objects.requireNonNull(origin, "origin");
            if (!Double.isFinite(yawDegrees)) {
                throw new IllegalArgumentException("yawDegrees must be finite");
            }
            Objects.requireNonNull(geometry, "geometry");
        }
    }

    private record Segment(
            int index,
            String id,
            MaleniaServerIntent intent,
            HitSpec hit,
            int startTick,
            int lockTick,
            int activeTick,
            int endTick,
            boolean persistent
    ) {
    }

    private record SegmentBatch(int activeTick, int endTick, List<Segment> segments) {
        private SegmentBatch {
            segments = List.copyOf(segments);
        }
    }

    private static final class MutableSegment {
        private final int index;
        private final String id;
        private final MaleniaServerIntent intent;
        private final HitSpec hit;
        private int firstTick = Integer.MAX_VALUE;
        private int endTick;
        private boolean persistent;

        private MutableSegment(
                int index,
                String id,
                MaleniaServerIntent intent,
                HitSpec hit
        ) {
            this.index = index;
            this.id = id;
            this.intent = intent;
            this.hit = hit;
        }

        private void accept(int actionTick, MaleniaServerIntent value) {
            firstTick = Math.min(firstTick, actionTick);
            if (value instanceof MaleniaServerIntent.RotZone zone) {
                endTick = Math.max(endTick, Math.addExact(actionTick, zone.durationTicks()));
                persistent = true;
            } else if (value instanceof MaleniaServerIntent.IndicatorOnlyZone zone) {
                endTick = Math.max(endTick, Math.addExact(actionTick, zone.durationTicks()));
                persistent = true;
            } else {
                endTick = Math.max(endTick, Math.addExact(actionTick, 1));
            }
        }

        private Segment freeze(int startTick, int lockTick) {
            return new Segment(
                    index,
                    id,
                    intent,
                    hit,
                    startTick,
                    lockTick,
                    firstTick,
                    endTick,
                    persistent
            );
        }
    }
}