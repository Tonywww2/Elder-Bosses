package com.tonywww.elder_bosses.combat.state;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class StaggerTracker<S> {
    public static final double DEFAULT_DAMAGE_CONVERSION_RATIO = 0.75;
    public static final double DEFAULT_CAPACITY_HEALTH_RATIO = 0.10;
    public static final int DEFAULT_SOURCE_DEDUPE_TICKS = 5;
    public static final int DEFAULT_DECAY_DELAY_TICKS = 120;
    public static final double DEFAULT_DECAY_PER_TICK = 1.5;
    public static final int DEFAULT_STUNNED_TICKS = 70;
    public static final int DEFAULT_IMMUNITY_TICKS = 80;
    public static final List<DistanceBand> DEFAULT_DISTANCE_BANDS = List.of(
            new DistanceBand(4.0, 1.00),
            new DistanceBand(8.0, 0.70),
            new DistanceBand(16.0, 0.40),
            new DistanceBand(Double.POSITIVE_INFINITY, 0.20)
    );

    private final double damageConversionRatio;
    private final double capacityHealthRatio;
    private final List<DistanceBand> distanceBands;
    private final int sourceDedupeTicks;
    private final int decayDelayTicks;
    private final double decayPerTick;
    private final int stunnedTicks;
    private final int immunityTicks;
    private final Map<S, Long> lastAcceptedTickBySource = new HashMap<>();

    private double phaseMaximumHealth;
    private double capacity;
    private double stagger;
    private long lastObservedGameTick = -1L;
    private long firstDecayTick = -1L;
    private long lastDecayProcessedTick = -1L;
    private long stunnedUntilExclusive = -1L;
    private long immunityUntilExclusive = -1L;
    private boolean pendingStun;

    public StaggerTracker(
            double phaseMaximumHealth,
            double damageConversionRatio,
            double capacityHealthRatio,
            List<DistanceBand> distanceBands,
            int sourceDedupeTicks,
            int decayDelayTicks,
            double decayPerTick,
            int stunnedTicks,
            int immunityTicks
    ) {
        this.phaseMaximumHealth = requirePositiveFinite(phaseMaximumHealth, "phaseMaximumHealth");
        this.damageConversionRatio = requireNonNegativeFinite(
                damageConversionRatio,
                "damageConversionRatio"
        );
        this.capacityHealthRatio = requirePositiveFinite(capacityHealthRatio, "capacityHealthRatio");
        this.distanceBands = validateDistanceBands(distanceBands);
        if (sourceDedupeTicks < 0) {
            throw new IllegalArgumentException("sourceDedupeTicks must be non-negative");
        }
        if (decayDelayTicks < 0) {
            throw new IllegalArgumentException("decayDelayTicks must be non-negative");
        }
        this.sourceDedupeTicks = sourceDedupeTicks;
        this.decayDelayTicks = decayDelayTicks;
        this.decayPerTick = requireNonNegativeFinite(decayPerTick, "decayPerTick");
        if (stunnedTicks <= 0 || immunityTicks <= 0) {
            throw new IllegalArgumentException("stunnedTicks and immunityTicks must be positive");
        }
        this.stunnedTicks = stunnedTicks;
        this.immunityTicks = immunityTicks;
        this.capacity = calculateCapacity(phaseMaximumHealth);
    }

    public static <S> StaggerTracker<S> withDefaults(double phaseMaximumHealth) {
        return new StaggerTracker<>(
                phaseMaximumHealth,
                DEFAULT_DAMAGE_CONVERSION_RATIO,
                DEFAULT_CAPACITY_HEALTH_RATIO,
                DEFAULT_DISTANCE_BANDS,
                DEFAULT_SOURCE_DEDUPE_TICKS,
                DEFAULT_DECAY_DELAY_TICKS,
                DEFAULT_DECAY_PER_TICK,
                DEFAULT_STUNNED_TICKS,
                DEFAULT_IMMUNITY_TICKS
        );
    }

    /** Restored trackers intentionally start with no per-source dedupe entries. */
    public static <S> StaggerTracker<S> restore(PersistentState state, long gameTick) {
        PersistentState checkedState = requireValidPersistentState(state);
        requireNonNegativeGameTick(gameTick);
        StaggerTracker<S> tracker = new StaggerTracker<>(
                checkedState.phaseMaximumHealth(),
                checkedState.damageConversionRatio(),
                checkedState.capacityHealthRatio(),
                checkedState.distanceBands(),
                checkedState.sourceDedupeTicks(),
                checkedState.decayDelayTicks(),
                checkedState.decayPerTick(),
                checkedState.stunnedTicks(),
                checkedState.immunityTicks()
        );
        tracker.stagger = checkedState.stagger();
        tracker.pendingStun = checkedState.pendingStun();
        tracker.lastObservedGameTick = gameTick;
        tracker.lastDecayProcessedTick = gameTick;
        if (tracker.stagger > 0.0 && !tracker.pendingStun) {
            tracker.firstDecayTick = addTicks(
                    gameTick,
                    checkedState.remainingDecayDelayTicks() + 1L,
                    "remainingDecayDelayTicks"
            );
        }
        if (checkedState.remainingStunnedTicks() > 0L) {
            tracker.stunnedUntilExclusive = addTicks(
                    gameTick,
                    checkedState.remainingStunnedTicks(),
                    "remainingStunnedTicks"
            );
            tracker.immunityUntilExclusive = addTicks(
                    tracker.stunnedUntilExclusive,
                    checkedState.remainingImmunityTicks(),
                    "remainingImmunityTicks"
            );
        } else if (checkedState.remainingImmunityTicks() > 0L) {
            tracker.stunnedUntilExclusive = gameTick;
            tracker.immunityUntilExclusive = addTicks(
                    gameTick,
                    checkedState.remainingImmunityTicks(),
                    "remainingImmunityTicks"
            );
        }
        tracker.lastAcceptedTickBySource.clear();
        return tracker;
    }

    public PersistentState persistentState(long gameTick) {
        advanceTo(gameTick);
        return new PersistentState(
                phaseMaximumHealth,
                stagger,
                damageConversionRatio,
                capacityHealthRatio,
                distanceBands,
                sourceDedupeTicks,
                decayDelayTicks,
                decayPerTick,
                stunnedTicks,
                immunityTicks,
                remainingDecayDelayTicksAt(gameTick),
                remainingStunnedTicksAt(gameTick),
                remainingImmunityTicksForPersistenceAt(gameTick),
                pendingStun
        );
    }

    public StaggerUpdate applyHealthLoss(
            S sourceKey,
            double actualHealthLoss,
            double horizontalDistance,
            long gameTick
    ) {
        return applyHealthLoss(sourceKey, actualHealthLoss, horizontalDistance, gameTick, false);
        }

            public StaggerUpdate applyHealthLoss(
                S sourceKey,
                double actualHealthLoss,
                double horizontalDistance,
                long gameTick,
                boolean deferStun
            ) {
        Objects.requireNonNull(sourceKey, "sourceKey");
        double checkedHealthLoss = requireNonNegativeFinite(actualHealthLoss, "actualHealthLoss");
        double checkedDistance = requireNonNegativeFinite(horizontalDistance, "horizontalDistance");
        advanceTo(gameTick);

        if (checkedHealthLoss == 0.0) {
            return update(StaggerResult.NO_HEALTH_LOSS, gameTick, 0.0, false);
        }
        if (isStunnedAt(gameTick)) {
            return update(StaggerResult.STUNNED, gameTick, 0.0, false);
        }
        if (isImmuneAt(gameTick)) {
            return update(StaggerResult.IMMUNE, gameTick, 0.0, false);
        }
        if (pendingStun) {
            return update(StaggerResult.PENDING_STUN, gameTick, 0.0, false);
        }

        Long lastSourceTick = lastAcceptedTickBySource.get(sourceKey);
        if (lastSourceTick != null && gameTick - lastSourceTick < sourceDedupeTicks) {
            return update(StaggerResult.DUPLICATE_SOURCE, gameTick, 0.0, false);
        }

        double rawIncrease = checkedHealthLoss
                * damageConversionRatio
                * distanceMultiplier(checkedDistance);
        if (rawIncrease == 0.0) {
            return update(StaggerResult.ZERO_INCREMENT, gameTick, 0.0, false);
        }
        double appliedIncrease = Math.min(rawIncrease, capacity - stagger);
        lastAcceptedTickBySource.put(sourceKey, gameTick);
        firstDecayTick = Math.addExact(gameTick, (long) decayDelayTicks + 1L);
        lastDecayProcessedTick = gameTick;
        stagger += appliedIncrease;

        if (stagger >= capacity) {
            if (deferStun) {
                stagger = capacity;
                pendingStun = true;
                firstDecayTick = -1L;
                lastAcceptedTickBySource.clear();
                return update(StaggerResult.PENDING_STUN, gameTick, appliedIncrease, false);
            }
            startStun(gameTick);
            return update(StaggerResult.TRIGGERED, gameTick, appliedIncrease, true);
        }
        return update(StaggerResult.APPLIED, gameTick, appliedIncrease, false);
    }

    public StaggerUpdate triggerPendingStun(long gameTick) {
        advanceTo(gameTick);
        if (!pendingStun) {
            throw new IllegalStateException("no stun is pending");
        }
        startStun(gameTick);
        return update(StaggerResult.TRIGGERED, gameTick, 0.0, true);
    }

    public StaggerSnapshot snapshot(long gameTick) {
        advanceTo(gameTick);
        return new StaggerSnapshot(
                gameTick,
                phaseMaximumHealth,
                stagger,
                capacity,
                stateAt(gameTick),
                remainingStunnedTicksAt(gameTick),
                remainingImmunityTicksAt(gameTick)
        );
    }

    public boolean isStunned(long gameTick) {
        advanceTo(gameTick);
        return isStunnedAt(gameTick);
    }

    public boolean isImmune(long gameTick) {
        advanceTo(gameTick);
        return isImmuneAt(gameTick);
    }

    public boolean canAccumulate(long gameTick) {
        advanceTo(gameTick);
        return !pendingStun && !isStunnedAt(gameTick) && !isImmuneAt(gameTick);
    }

    public StaggerState state(long gameTick) {
        advanceTo(gameTick);
        return stateAt(gameTick);
    }

    public void increasePhaseMaximumHealth(double newPhaseMaximumHealth) {
        double checkedMaximum = requirePositiveFinite(newPhaseMaximumHealth, "newPhaseMaximumHealth");
        if (checkedMaximum < phaseMaximumHealth) {
            throw new IllegalArgumentException("newPhaseMaximumHealth must not decrease");
        }
        phaseMaximumHealth = checkedMaximum;
        capacity = calculateCapacity(checkedMaximum);
        if (pendingStun) {
            stagger = capacity;
        }
    }

    public void resizePhaseMaximumHealth(double newPhaseMaximumHealth, long gameTick) {
        advanceTo(gameTick);
        phaseMaximumHealth = requirePositiveFinite(newPhaseMaximumHealth, "newPhaseMaximumHealth");
        capacity = calculateCapacity(phaseMaximumHealth);
        if (pendingStun) {
            stagger = capacity;
            return;
        }
        if (stagger < capacity) {
            return;
        }
        stagger = capacity;
        if (!isStunnedAt(gameTick) && !isImmuneAt(gameTick)) {
            pendingStun = true;
            firstDecayTick = -1L;
            lastAcceptedTickBySource.clear();
        }
    }

    public StaggerSnapshot resetForPhase(double newPhaseMaximumHealth, long gameTick) {
        advanceTo(gameTick);
        phaseMaximumHealth = requirePositiveFinite(newPhaseMaximumHealth, "newPhaseMaximumHealth");
        capacity = calculateCapacity(phaseMaximumHealth);
        stagger = 0.0;
        firstDecayTick = -1L;
        lastDecayProcessedTick = gameTick;
        stunnedUntilExclusive = -1L;
        immunityUntilExclusive = -1L;
        pendingStun = false;
        lastAcceptedTickBySource.clear();
        return snapshot(gameTick);
    }

    public void clearAccumulation(long gameTick) {
        advanceTo(gameTick);
        stagger = 0.0;
        firstDecayTick = -1L;
        lastDecayProcessedTick = gameTick;
        pendingStun = false;
        lastAcceptedTickBySource.clear();
    }

    public double distanceMultiplier(double horizontalDistance) {
        double checkedDistance = requireNonNegativeFinite(horizontalDistance, "horizontalDistance");
        for (DistanceBand band : distanceBands) {
            if (checkedDistance <= band.maximumDistance()) {
                return band.multiplier();
            }
        }
        throw new IllegalStateException("distance bands do not cover all distances");
    }

    private void advanceTo(long gameTick) {
        if (gameTick < 0L) {
            throw new IllegalArgumentException("gameTick must be non-negative");
        }
        if (gameTick < lastObservedGameTick) {
            throw new IllegalArgumentException("gameTick must not move backwards");
        }

        if (stagger > 0.0 && !pendingStun && firstDecayTick >= 0L) {
            long firstUnprocessedTick = Math.max(firstDecayTick, lastDecayProcessedTick + 1L);
            if (gameTick >= firstUnprocessedTick) {
                long elapsedDecayTicks = gameTick - firstUnprocessedTick + 1L;
                stagger = Math.max(0.0, stagger - elapsedDecayTicks * decayPerTick);
                if (stagger == 0.0) {
                    firstDecayTick = -1L;
                }
            }
        }

        lastDecayProcessedTick = gameTick;
        lastObservedGameTick = gameTick;
        if (sourceDedupeTicks == 0) {
            lastAcceptedTickBySource.clear();
        } else {
            lastAcceptedTickBySource.entrySet().removeIf(
                    entry -> gameTick - entry.getValue() >= sourceDedupeTicks
            );
        }
    }

    private double calculateCapacity(double maximumHealth) {
        return requirePositiveFinite(maximumHealth * capacityHealthRatio, "capacity");
    }

    private void startStun(long gameTick) {
        stagger = 0.0;
        firstDecayTick = -1L;
        lastDecayProcessedTick = gameTick;
        pendingStun = false;
        lastAcceptedTickBySource.clear();
        stunnedUntilExclusive = Math.addExact(gameTick, stunnedTicks);
        immunityUntilExclusive = Math.addExact(stunnedUntilExclusive, immunityTicks);
    }

    private StaggerUpdate update(
            StaggerResult result,
            long gameTick,
            double appliedIncrease,
            boolean triggered
    ) {
        return new StaggerUpdate(
                result,
                gameTick,
                appliedIncrease,
                stagger,
                capacity,
                triggered,
                stateAt(gameTick)
        );
    }

    private StaggerState stateAt(long gameTick) {
        if (pendingStun) {
            return StaggerState.PENDING_STUN;
        }
        if (isStunnedAt(gameTick)) {
            return StaggerState.STUNNED;
        }
        if (isImmuneAt(gameTick)) {
            return StaggerState.IMMUNE;
        }
        return StaggerState.ACCUMULATING;
    }

    private boolean isStunnedAt(long gameTick) {
        return stunnedUntilExclusive >= 0L && gameTick < stunnedUntilExclusive;
    }

    private boolean isImmuneAt(long gameTick) {
        return stunnedUntilExclusive >= 0L
                && gameTick >= stunnedUntilExclusive
                && gameTick < immunityUntilExclusive;
    }

    private long remainingStunnedTicksAt(long gameTick) {
        return isStunnedAt(gameTick) ? stunnedUntilExclusive - gameTick : 0L;
    }

    private long remainingImmunityTicksAt(long gameTick) {
        return isImmuneAt(gameTick) ? immunityUntilExclusive - gameTick : 0L;
    }

    private long remainingDecayDelayTicksAt(long gameTick) {
        if (stagger == 0.0 || pendingStun || firstDecayTick <= gameTick) {
            return 0L;
        }
        return firstDecayTick - gameTick - 1L;
    }

    private long remainingImmunityTicksForPersistenceAt(long gameTick) {
        if (isStunnedAt(gameTick)) {
            return immunityUntilExclusive - stunnedUntilExclusive;
        }
        return remainingImmunityTicksAt(gameTick);
    }

    private static List<DistanceBand> validateDistanceBands(List<DistanceBand> distanceBands) {
        Objects.requireNonNull(distanceBands, "distanceBands");
        if (distanceBands.size() != 4) {
            throw new IllegalArgumentException("distanceBands must contain exactly four bands");
        }
        List<DistanceBand> copiedBands = distanceBands.stream()
                .map(band -> Objects.requireNonNull(band, "distanceBand"))
                .toList();
        double previousMaximum = 0.0;
        for (int index = 0; index < copiedBands.size(); index++) {
            double maximumDistance = copiedBands.get(index).maximumDistance();
            boolean finalBand = index == copiedBands.size() - 1;
            if (finalBand && maximumDistance != Double.POSITIVE_INFINITY) {
                throw new IllegalArgumentException("the final distance band must end at positive infinity");
            }
            if (!finalBand && (!Double.isFinite(maximumDistance) || maximumDistance <= previousMaximum)) {
                throw new IllegalArgumentException("finite distance band maxima must be strictly increasing");
            }
            previousMaximum = maximumDistance;
        }
        return List.copyOf(copiedBands);
    }

    private static double requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
        return value;
    }

    private static double requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }

    private static long requireNonNegativeGameTick(long gameTick) {
        if (gameTick < 0L) {
            throw new IllegalArgumentException("gameTick must be non-negative");
        }
        return gameTick;
    }

    private static long addTicks(long gameTick, long ticks, String name) {
        try {
            return Math.addExact(gameTick, ticks);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " exceeds the supported tick range", exception);
        }
    }

    private static PersistentState requireValidPersistentState(PersistentState state) {
        PersistentState checkedState = Objects.requireNonNull(state, "state");
        validatePersistentState(
                checkedState.phaseMaximumHealth(),
                checkedState.stagger(),
                checkedState.damageConversionRatio(),
                checkedState.capacityHealthRatio(),
                checkedState.distanceBands(),
                checkedState.sourceDedupeTicks(),
                checkedState.decayDelayTicks(),
                checkedState.decayPerTick(),
                checkedState.stunnedTicks(),
                checkedState.immunityTicks(),
                checkedState.remainingDecayDelayTicks(),
                checkedState.remainingStunnedTicks(),
                checkedState.remainingImmunityTicks(),
                checkedState.pendingStun()
        );
        return checkedState;
    }

    private static void validatePersistentState(
            double phaseMaximumHealth,
            double stagger,
            double damageConversionRatio,
            double capacityHealthRatio,
            List<DistanceBand> distanceBands,
            int sourceDedupeTicks,
            int decayDelayTicks,
            double decayPerTick,
            int stunnedTicks,
            int immunityTicks,
            long remainingDecayDelayTicks,
            long remainingStunnedTicks,
            long remainingImmunityTicks,
            boolean pendingStun
    ) {
        double checkedPhaseMaximumHealth = requirePositiveFinite(
                phaseMaximumHealth,
                "phaseMaximumHealth"
        );
        double checkedCapacityHealthRatio = requirePositiveFinite(
                capacityHealthRatio,
                "capacityHealthRatio"
        );
        requireNonNegativeFinite(damageConversionRatio, "damageConversionRatio");
        validateDistanceBands(distanceBands);
        if (sourceDedupeTicks < 0) {
            throw new IllegalArgumentException("sourceDedupeTicks must be non-negative");
        }
        if (decayDelayTicks < 0) {
            throw new IllegalArgumentException("decayDelayTicks must be non-negative");
        }
        requireNonNegativeFinite(decayPerTick, "decayPerTick");
        if (stunnedTicks <= 0 || immunityTicks <= 0) {
            throw new IllegalArgumentException("stunnedTicks and immunityTicks must be positive");
        }

        double capacity = requirePositiveFinite(
                checkedPhaseMaximumHealth * checkedCapacityHealthRatio,
                "capacity"
        );
        double checkedStagger = requireNonNegativeFinite(stagger, "stagger");
        if (checkedStagger > capacity) {
            throw new IllegalArgumentException("stagger must not exceed capacity");
        }
        if (remainingDecayDelayTicks < 0L || remainingDecayDelayTicks > decayDelayTicks) {
            throw new IllegalArgumentException(
                    "remainingDecayDelayTicks must be between 0 and decayDelayTicks"
            );
        }
        if (remainingStunnedTicks < 0L || remainingStunnedTicks > stunnedTicks) {
            throw new IllegalArgumentException(
                    "remainingStunnedTicks must be between 0 and stunnedTicks"
            );
        }
        if (remainingImmunityTicks < 0L || remainingImmunityTicks > immunityTicks) {
            throw new IllegalArgumentException(
                    "remainingImmunityTicks must be between 0 and immunityTicks"
            );
        }

        boolean hasTimedState = remainingStunnedTicks > 0L || remainingImmunityTicks > 0L;
        if (remainingStunnedTicks > 0L && remainingImmunityTicks != immunityTicks) {
            throw new IllegalArgumentException(
                    "a persisted stun must retain the full configured immunity duration"
            );
        }
        if (hasTimedState && (checkedStagger != 0.0 || remainingDecayDelayTicks != 0L || pendingStun)) {
            throw new IllegalArgumentException(
                    "stunned or immune state cannot retain stagger, decay delay, or a pending stun"
            );
        }
        if (pendingStun) {
            if (checkedStagger != capacity || remainingDecayDelayTicks != 0L) {
                throw new IllegalArgumentException(
                        "pending stun must hold full stagger capacity with no decay delay"
                );
            }
        } else if (!hasTimedState && checkedStagger == capacity) {
            throw new IllegalArgumentException("full stagger capacity must be marked as pending stun");
        }
        if (checkedStagger == 0.0 && remainingDecayDelayTicks != 0L) {
            throw new IllegalArgumentException("zero stagger cannot retain a decay delay");
        }
    }

    public record DistanceBand(double maximumDistance, double multiplier) {
        public DistanceBand {
            if (Double.isNaN(maximumDistance) || maximumDistance <= 0.0) {
                throw new IllegalArgumentException("maximumDistance must be positive");
            }
            requireNonNegativeFinite(multiplier, "multiplier");
        }
    }

    public enum StaggerState {
        ACCUMULATING,
        PENDING_STUN,
        STUNNED,
        IMMUNE
    }

    public enum StaggerResult {
        APPLIED,
        TRIGGERED,
        PENDING_STUN,
        NO_HEALTH_LOSS,
        ZERO_INCREMENT,
        DUPLICATE_SOURCE,
        STUNNED,
        IMMUNE
    }

    public record PersistentState(
            double phaseMaximumHealth,
            double stagger,
            double damageConversionRatio,
            double capacityHealthRatio,
            List<DistanceBand> distanceBands,
            int sourceDedupeTicks,
            int decayDelayTicks,
            double decayPerTick,
            int stunnedTicks,
            int immunityTicks,
            long remainingDecayDelayTicks,
            long remainingStunnedTicks,
            long remainingImmunityTicks,
            boolean pendingStun
    ) {
        public PersistentState {
            distanceBands = validateDistanceBands(distanceBands);
            validatePersistentState(
                    phaseMaximumHealth,
                    stagger,
                    damageConversionRatio,
                    capacityHealthRatio,
                    distanceBands,
                    sourceDedupeTicks,
                    decayDelayTicks,
                    decayPerTick,
                    stunnedTicks,
                    immunityTicks,
                    remainingDecayDelayTicks,
                    remainingStunnedTicks,
                    remainingImmunityTicks,
                    pendingStun
            );
        }
    }

    public record StaggerUpdate(
            StaggerResult result,
            long gameTick,
            double appliedIncrease,
            double stagger,
            double capacity,
            boolean triggered,
            StaggerState state
    ) {
    }

    public record StaggerSnapshot(
            long gameTick,
            double phaseMaximumHealth,
            double stagger,
            double capacity,
            StaggerState state,
            long remainingStunnedTicks,
            long remainingImmunityTicks
    ) {
    }
}