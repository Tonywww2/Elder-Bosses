package com.tonywww.elder_bosses.combat.status;

import java.util.Objects;

public final class ScarletRotState {
    private static final long UNSET_TICK = -1L;

    private final int decayDelayTicks;
    private final int decayIntervalTicks;
    private final double decayPerInterval;
    private final int activeDurationTicks;
    private final int damageIntervalTicks;
    private final double activeHealingMultiplier;
    private final double activeMovementSpeedMultiplier;
    private final double honeyBuildupReduction;

    private double buildup;
    private long lastObservedGameTick = UNSET_TICK;
    private long nextDecayTick = UNSET_TICK;
    private long activeUntilTick = UNSET_TICK;
    private long nextDamagePulseTick = UNSET_TICK;
    private int pendingDamagePulses;
    private boolean rebaseRequired;

    public ScarletRotState(
            int decayDelayTicks,
            int decayIntervalTicks,
            double decayPerInterval,
            int activeDurationTicks,
            int damageIntervalTicks,
            double healingReduction,
            double movementSpeedReduction,
            double honeyBuildupReduction
    ) {
        validateConfiguration(
                decayDelayTicks,
                decayIntervalTicks,
                decayPerInterval,
                activeDurationTicks,
                damageIntervalTicks,
                healingReduction,
                movementSpeedReduction,
                honeyBuildupReduction
        );
        this.decayDelayTicks = decayDelayTicks;
        this.decayIntervalTicks = decayIntervalTicks;
        this.decayPerInterval = decayPerInterval;
        this.activeDurationTicks = activeDurationTicks;
        this.damageIntervalTicks = damageIntervalTicks;
        this.activeHealingMultiplier = 1.0 - healingReduction;
        this.activeMovementSpeedMultiplier = 1.0 - movementSpeedReduction;
        this.honeyBuildupReduction = honeyBuildupReduction;
    }

    private ScarletRotState(PersistentState state) {
        this(
                state.decayDelayTicks(),
                state.decayIntervalTicks(),
                state.decayPerInterval(),
                state.activeDurationTicks(),
                state.damageIntervalTicks(),
                state.healingReduction(),
                state.movementSpeedReduction(),
                state.honeyBuildupReduction()
        );
        buildup = state.buildup();
        nextDecayTick = state.remainingDecayTicks();
        activeUntilTick = state.remainingActiveTicks();
        nextDamagePulseTick = state.remainingDamagePulseTicks();
        rebaseRequired = true;
    }

    public static ScarletRotState restore(PersistentState state) {
        return new ScarletRotState(requireValidPersistentState(state));
    }

    public boolean addBuildup(double amount, double capacity, long gameTick) {
        double checkedAmount = requireNonNegativeFinite(amount, "amount");
        double checkedCapacity = requirePositiveFinite(capacity, "capacity");
        advanceInternal(gameTick);
        if (active() || pendingDamagePulses > 0) {
            return false;
        }
        if (buildup >= checkedCapacity) {
            trigger(gameTick);
            return true;
        }
        if (checkedAmount == 0.0) {
            return false;
        }

        double increasedBuildup = buildup + checkedAmount;
        if (!Double.isFinite(increasedBuildup) || increasedBuildup >= checkedCapacity) {
            trigger(gameTick);
            return true;
        }

        long scheduledDecayTick = addTicks(gameTick, decayDelayTicks, "decay tick");
        buildup = increasedBuildup;
        nextDecayTick = scheduledDecayTick;
        return false;
    }

    public double applyHoney(long gameTick) {
        return reduceBuildup(honeyBuildupReduction, gameTick);
    }

    public double reduceBuildup(double amount, long gameTick) {
        double checkedAmount = requireNonNegativeFinite(amount, "amount");
        advanceInternal(gameTick);
        double appliedReduction = Math.min(checkedAmount, buildup);
        buildup -= appliedReduction;
        if (buildup == 0.0) {
            nextDecayTick = UNSET_TICK;
        }
        return appliedReduction;
    }

    public boolean cleanse(long gameTick) {
        advanceInternal(gameTick);
        boolean changed = buildup > 0.0 || active() || pendingDamagePulses > 0;
        buildup = 0.0;
        nextDecayTick = UNSET_TICK;
        clearActiveState();
        pendingDamagePulses = 0;
        return changed;
    }

    public int tick(long gameTick, double capacity) {
        applyCapacity(capacity, gameTick);
        return advance(gameTick);
    }

    public void observe(long gameTick) {
        advanceInternal(gameTick);
    }

    public int advance(long gameTick) {
        advanceInternal(gameTick);
        int damagePulses = pendingDamagePulses;
        pendingDamagePulses = 0;
        return damagePulses;
    }

    public boolean active() {
        return activeUntilTick != UNSET_TICK;
    }

    public boolean idle() {
        return buildup == 0.0 && !active() && pendingDamagePulses == 0;
    }

    public double buildup() {
        return buildup;
    }

    public double healingMultiplier() {
        return active() ? activeHealingMultiplier : 1.0;
    }

    public double movementSpeedMultiplier() {
        return active() ? activeMovementSpeedMultiplier : 1.0;
    }

    public Snapshot snapshot(long gameTick, double capacity) {
        applyCapacity(capacity, gameTick);
        advanceInternal(gameTick);
        int remainingActiveTicks = active()
                ? Math.toIntExact(activeUntilTick - gameTick)
                : 0;
        return new Snapshot(
                gameTick,
                buildup,
                capacity,
                active(),
                remainingActiveTicks,
                pendingDamagePulses,
                healingMultiplier(),
                movementSpeedMultiplier()
        );
    }

    public PersistentState persistentState() {
        long remainingDecayTicks = remainingTicks(nextDecayTick, "decay tick");
        long remainingActiveTicks = remainingTicks(activeUntilTick, "active end tick");
        long remainingDamagePulseTicks = remainingTicks(
                nextDamagePulseTick,
                "damage pulse tick"
        );
        return new PersistentState(
                decayDelayTicks,
                decayIntervalTicks,
                decayPerInterval,
                activeDurationTicks,
                damageIntervalTicks,
                1.0 - activeHealingMultiplier,
                1.0 - activeMovementSpeedMultiplier,
                honeyBuildupReduction,
                buildup,
                remainingDecayTicks,
                remainingActiveTicks,
                remainingDamagePulseTicks
        );
    }

    private void trigger(long gameTick) {
        long scheduledActiveUntilTick = addTicks(gameTick, activeDurationTicks, "active end tick");
        long scheduledDamagePulseTick = addTicks(gameTick, damageIntervalTicks, "damage pulse tick");
        buildup = 0.0;
        nextDecayTick = UNSET_TICK;
        activeUntilTick = scheduledActiveUntilTick;
        nextDamagePulseTick = scheduledDamagePulseTick;
    }

    private void applyCapacity(double capacity, long gameTick) {
        double checkedCapacity = requirePositiveFinite(capacity, "capacity");
        advanceInternal(gameTick);
        if (!active() && buildup >= checkedCapacity) {
            trigger(gameTick);
        }
    }

    private void advanceInternal(long gameTick) {
        validateGameTick(gameTick);
        if (rebaseRequired) {
            rebase(gameTick);
            lastObservedGameTick = gameTick;
            rebaseRequired = false;
            return;
        }
        if (gameTick == lastObservedGameTick) {
            return;
        }

        if (active()) {
            advanceActiveState(gameTick);
        } else {
            advanceDecay(gameTick);
        }
        lastObservedGameTick = gameTick;
    }

    private void rebase(long gameTick) {
        if (nextDecayTick != UNSET_TICK) {
            nextDecayTick = addRelativeTicks(gameTick, nextDecayTick, "decay tick");
        }
        if (activeUntilTick != UNSET_TICK) {
            activeUntilTick = addRelativeTicks(gameTick, activeUntilTick, "active end tick");
        }
        if (nextDamagePulseTick != UNSET_TICK) {
            nextDamagePulseTick = addRelativeTicks(
                    gameTick,
                    nextDamagePulseTick,
                    "damage pulse tick"
            );
        }
    }

    private void advanceActiveState(long gameTick) {
        if (gameTick > activeUntilTick) {
            clearActiveState();
            pendingDamagePulses = 0;
            return;
        }
        long pulseLimit = Math.min(gameTick, activeUntilTick);
        if (nextDamagePulseTick != UNSET_TICK && nextDamagePulseTick <= pulseLimit) {
            pendingDamagePulses = 1;
            long followingPulseTick = addTicks(gameTick, damageIntervalTicks, "damage pulse tick");
            if (followingPulseTick > activeUntilTick) {
                nextDamagePulseTick = UNSET_TICK;
            } else {
                nextDamagePulseTick = followingPulseTick;
            }
        }

        if (gameTick >= activeUntilTick) {
            clearActiveState();
        }
    }

    private void advanceDecay(long gameTick) {
        if (buildup == 0.0 || nextDecayTick == UNSET_TICK || gameTick < nextDecayTick) {
            return;
        }

        long dueDecayCount = (gameTick - nextDecayTick) / decayIntervalTicks + 1L;
        double totalDecay = decayPerInterval * dueDecayCount;
        if (!Double.isFinite(totalDecay) || totalDecay >= buildup) {
            buildup = 0.0;
            nextDecayTick = UNSET_TICK;
            return;
        }

        buildup -= totalDecay;
        nextDecayTick = addIntervals(
                nextDecayTick,
                dueDecayCount,
                decayIntervalTicks,
                "decay tick"
        );
    }

    private void clearActiveState() {
        activeUntilTick = UNSET_TICK;
        nextDamagePulseTick = UNSET_TICK;
    }

    private void validateGameTick(long gameTick) {
        if (gameTick < 0L) {
            throw new IllegalArgumentException("gameTick must be non-negative");
        }
        if (lastObservedGameTick != UNSET_TICK && gameTick < lastObservedGameTick) {
            throw new IllegalArgumentException("gameTick must not move backwards");
        }
    }

    private long remainingTicks(long scheduledTick, String name) {
        if (scheduledTick == UNSET_TICK || rebaseRequired) {
            return scheduledTick;
        }
        if (lastObservedGameTick == UNSET_TICK || scheduledTick < lastObservedGameTick) {
            throw new IllegalStateException(name + " must not precede the last observed game tick");
        }
        return scheduledTick - lastObservedGameTick;
    }

    private static long addTicks(long gameTick, int ticks, String name) {
        try {
            return Math.addExact(gameTick, ticks);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " exceeds the supported game tick range", exception);
        }
    }

    private static long addRelativeTicks(long gameTick, long ticks, String name) {
        try {
            return Math.addExact(gameTick, ticks);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " exceeds the supported game tick range", exception);
        }
    }

    private static long addIntervals(long tick, long count, int interval, String name) {
        try {
            return Math.addExact(tick, Math.multiplyExact(count, (long) interval));
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " exceeds the supported game tick range", exception);
        }
    }

    private static void validateConfiguration(
            int decayDelayTicks,
            int decayIntervalTicks,
            double decayPerInterval,
            int activeDurationTicks,
            int damageIntervalTicks,
            double healingReduction,
            double movementSpeedReduction,
            double honeyBuildupReduction
    ) {
        if (decayDelayTicks < 0) {
            throw new IllegalArgumentException("decayDelayTicks must be non-negative");
        }
        if (decayIntervalTicks <= 0) {
            throw new IllegalArgumentException("decayIntervalTicks must be positive");
        }
        requireNonNegativeFinite(decayPerInterval, "decayPerInterval");
        if (activeDurationTicks <= 0) {
            throw new IllegalArgumentException("activeDurationTicks must be positive");
        }
        if (damageIntervalTicks <= 0 || damageIntervalTicks > activeDurationTicks) {
            throw new IllegalArgumentException(
                    "damageIntervalTicks must be positive and no greater than activeDurationTicks"
            );
        }
        requireReduction(healingReduction, "healingReduction");
        requireReduction(movementSpeedReduction, "movementSpeedReduction");
        requireNonNegativeFinite(honeyBuildupReduction, "honeyBuildupReduction");
    }

    private static PersistentState requireValidPersistentState(PersistentState state) {
        PersistentState requiredState = Objects.requireNonNull(state, "state");
        validatePersistentState(
                requiredState.decayDelayTicks(),
                requiredState.decayIntervalTicks(),
                requiredState.decayPerInterval(),
                requiredState.activeDurationTicks(),
                requiredState.damageIntervalTicks(),
                requiredState.healingReduction(),
                requiredState.movementSpeedReduction(),
                requiredState.honeyBuildupReduction(),
                requiredState.buildup(),
                requiredState.remainingDecayTicks(),
                requiredState.remainingActiveTicks(),
                requiredState.remainingDamagePulseTicks()
        );
        return requiredState;
    }

    private static void validatePersistentState(
            int decayDelayTicks,
            int decayIntervalTicks,
            double decayPerInterval,
            int activeDurationTicks,
            int damageIntervalTicks,
            double healingReduction,
            double movementSpeedReduction,
            double honeyBuildupReduction,
            double buildup,
            long remainingDecayTicks,
            long remainingActiveTicks,
            long remainingDamagePulseTicks
    ) {
        validateConfiguration(
                decayDelayTicks,
                decayIntervalTicks,
                decayPerInterval,
                activeDurationTicks,
                damageIntervalTicks,
                healingReduction,
                movementSpeedReduction,
                honeyBuildupReduction
        );
        double checkedBuildup = requireNonNegativeFinite(buildup, "buildup");
        requireOptionalNonNegativeTicks(remainingDecayTicks, "remainingDecayTicks");
        requireOptionalPositiveTicks(remainingActiveTicks, "remainingActiveTicks");
        requireOptionalPositiveTicks(remainingDamagePulseTicks, "remainingDamagePulseTicks");
        boolean active = remainingActiveTicks != UNSET_TICK;
        if (active) {
            if (remainingActiveTicks > activeDurationTicks) {
                throw new IllegalArgumentException(
                        "remainingActiveTicks must not exceed activeDurationTicks"
                );
            }
            if (checkedBuildup != 0.0 || remainingDecayTicks != UNSET_TICK) {
                throw new IllegalArgumentException("an active state cannot retain buildup or a decay tick");
            }
            if (remainingDamagePulseTicks != UNSET_TICK
                    && (remainingDamagePulseTicks > damageIntervalTicks
                        || remainingDamagePulseTicks > remainingActiveTicks)) {
                throw new IllegalArgumentException(
                        "remainingDamagePulseTicks must be within the pulse interval and active duration"
                );
            }
        } else if (remainingDamagePulseTicks != UNSET_TICK) {
            throw new IllegalArgumentException("an inactive state cannot have a damage pulse tick");
        }

        if (!active && checkedBuildup == 0.0 && remainingDecayTicks != UNSET_TICK) {
            throw new IllegalArgumentException("an empty buildup cannot have a decay tick");
        }
        if (!active && checkedBuildup > 0.0 && (
                remainingDecayTicks == UNSET_TICK
        )) {
            throw new IllegalArgumentException("buildup must have a remaining decay time");
        }
        if (!active && checkedBuildup > 0.0
                && remainingDecayTicks > Math.max(decayDelayTicks, decayIntervalTicks)) {
            throw new IllegalArgumentException("remainingDecayTicks exceeds the configured delay");
        }
    }

    private static void requireOptionalNonNegativeTicks(long ticks, String name) {
        if (ticks < UNSET_TICK) {
            throw new IllegalArgumentException(name + " must be -1 or non-negative");
        }
    }

    private static void requireOptionalPositiveTicks(long ticks, String name) {
        if (ticks != UNSET_TICK && ticks <= 0L) {
            throw new IllegalArgumentException(name + " must be -1 or positive");
        }
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

    private static double requireReduction(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and between 0 and 1");
        }
        return value;
    }

    public record Snapshot(
            long gameTick,
            double buildup,
            double capacity,
            boolean active,
            int remainingActiveTicks,
            int pendingDamagePulses,
            double healingMultiplier,
            double movementSpeedMultiplier
    ) {
        public Snapshot {
            if (gameTick < 0L) {
                throw new IllegalArgumentException("gameTick must be non-negative");
            }
            double checkedCapacity = requirePositiveFinite(capacity, "capacity");
            double checkedBuildup = requireNonNegativeFinite(buildup, "buildup");
            if (!active && checkedBuildup >= checkedCapacity) {
                throw new IllegalArgumentException("inactive buildup must be below capacity");
            }
            if (remainingActiveTicks < 0 || active != (remainingActiveTicks > 0)) {
                throw new IllegalArgumentException(
                        "remainingActiveTicks must be positive exactly while active"
                );
            }
            if (pendingDamagePulses < 0) {
                throw new IllegalArgumentException("pendingDamagePulses must be non-negative");
            }
            requireReduction(healingMultiplier, "healingMultiplier");
            requireReduction(movementSpeedMultiplier, "movementSpeedMultiplier");
            if (!active && (healingMultiplier != 1.0 || movementSpeedMultiplier != 1.0)) {
                throw new IllegalArgumentException("inactive multipliers must equal 1");
            }
        }
    }

    public record PersistentState(
            int decayDelayTicks,
            int decayIntervalTicks,
            double decayPerInterval,
            int activeDurationTicks,
            int damageIntervalTicks,
            double healingReduction,
            double movementSpeedReduction,
            double honeyBuildupReduction,
            double buildup,
            long remainingDecayTicks,
            long remainingActiveTicks,
            long remainingDamagePulseTicks
    ) {
        public PersistentState {
            validatePersistentState(
                    decayDelayTicks,
                    decayIntervalTicks,
                    decayPerInterval,
                    activeDurationTicks,
                    damageIntervalTicks,
                    healingReduction,
                    movementSpeedReduction,
                    honeyBuildupReduction,
                    buildup,
                    remainingDecayTicks,
                    remainingActiveTicks,
                    remainingDamagePulseTicks
            );
        }
    }
}