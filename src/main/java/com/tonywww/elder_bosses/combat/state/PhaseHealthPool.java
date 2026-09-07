package com.tonywww.elder_bosses.combat.state;

import java.util.Objects;

public final class PhaseHealthPool {
    public static final int DEFAULT_MAXIMUM_PLAYERS = 4;
    public static final double DEFAULT_HEALTH_PER_EXTRA_PLAYER = 0.50;
    public static final double DEFAULT_SECOND_PHASE_START_RATIO = 0.80;
    private static final double SCALING_VALIDATION_ULPS_PER_PLAYER = 4.0;

    private final double phaseOneBaseMaximum;
    private final double phaseTwoBaseMaximum;
    private final int maximumPlayers;
    private final double healthPerExtraPlayer;
    private final double secondPhaseStartRatio;

    private Phase activePhase = Phase.ONE;
    private double phaseOneMaximum;
    private double phaseOneCurrent;
    private int phaseOneScaledPlayers;
    private double phaseTwoMaximum;
    private double phaseTwoCurrent;
    private int phaseTwoScaledPlayers;

    public PhaseHealthPool(
            double phaseOneBaseMaximum,
            double phaseTwoBaseMaximum,
            int initialPlayerCount,
            int maximumPlayers,
            double healthPerExtraPlayer,
            double secondPhaseStartRatio
    ) {
        this.phaseOneBaseMaximum = requirePositiveFinite(phaseOneBaseMaximum, "phaseOneBaseMaximum");
        this.phaseTwoBaseMaximum = requirePositiveFinite(phaseTwoBaseMaximum, "phaseTwoBaseMaximum");
        if (maximumPlayers < 1) {
            throw new IllegalArgumentException("maximumPlayers must be positive");
        }
        if (initialPlayerCount < 1 || initialPlayerCount > maximumPlayers) {
            throw new IllegalArgumentException("initialPlayerCount must be between 1 and maximumPlayers");
        }
        this.maximumPlayers = maximumPlayers;
        this.healthPerExtraPlayer = requireNonNegativeFinite(
                healthPerExtraPlayer,
                "healthPerExtraPlayer"
        );
        this.secondPhaseStartRatio = requireRatio(secondPhaseStartRatio, "secondPhaseStartRatio");

        this.phaseOneMaximum = scaledMaximum(phaseOneBaseMaximum, initialPlayerCount);
        this.phaseOneCurrent = phaseOneMaximum;
        this.phaseOneScaledPlayers = initialPlayerCount;
        this.phaseTwoMaximum = scaledMaximum(phaseTwoBaseMaximum, initialPlayerCount);
        this.phaseTwoCurrent = phaseTwoMaximum * secondPhaseStartRatio;
        this.phaseTwoScaledPlayers = initialPlayerCount;
    }

    private PhaseHealthPool(PersistentState state) {
        this.phaseOneBaseMaximum = state.phaseOneBaseMaximum();
        this.phaseTwoBaseMaximum = state.phaseTwoBaseMaximum();
        this.maximumPlayers = state.maximumPlayers();
        this.healthPerExtraPlayer = state.healthPerExtraPlayer();
        this.secondPhaseStartRatio = state.secondPhaseStartRatio();
        this.activePhase = state.activePhase();
        this.phaseOneMaximum = state.phaseOneMaximum();
        this.phaseOneCurrent = state.phaseOneCurrent();
        this.phaseOneScaledPlayers = state.phaseOneScaledPlayerCount();
        this.phaseTwoMaximum = state.phaseTwoMaximum();
        this.phaseTwoCurrent = state.phaseTwoCurrent();
        this.phaseTwoScaledPlayers = state.phaseTwoScaledPlayerCount();
    }

    public static PhaseHealthPool withDefaultScaling(
            double phaseOneBaseMaximum,
            double phaseTwoBaseMaximum,
            int initialPlayerCount
    ) {
        return new PhaseHealthPool(
                phaseOneBaseMaximum,
                phaseTwoBaseMaximum,
                initialPlayerCount,
                DEFAULT_MAXIMUM_PLAYERS,
                DEFAULT_HEALTH_PER_EXTRA_PLAYER,
                DEFAULT_SECOND_PHASE_START_RATIO
        );
    }

    public static PhaseHealthPool restore(PersistentState state) {
        return new PhaseHealthPool(requireValidPersistentState(state));
    }

    public PersistentState persistentState() {
        return new PersistentState(
                activePhase,
                phaseOneBaseMaximum,
                phaseOneMaximum,
                phaseOneCurrent,
                phaseOneScaledPlayers,
                phaseTwoBaseMaximum,
                phaseTwoMaximum,
                phaseTwoCurrent,
                phaseTwoScaledPlayers,
                maximumPlayers,
                healthPerExtraPlayer,
                secondPhaseStartRatio
        );
    }

    public Phase activePhase() {
        return activePhase;
    }

    public PhaseSnapshot activeSnapshot() {
        return snapshot(activePhase);
    }

    public PhaseSnapshot snapshot(Phase phase) {
        Objects.requireNonNull(phase, "phase");
        return switch (phase) {
            case ONE -> new PhaseSnapshot(
                    phase,
                    phaseOneBaseMaximum,
                    phaseOneMaximum,
                    phaseOneCurrent,
                    phaseOneScaledPlayers
            );
            case TWO -> new PhaseSnapshot(
                    phase,
                    phaseTwoBaseMaximum,
                    phaseTwoMaximum,
                    phaseTwoCurrent,
                    phaseTwoScaledPlayers
            );
        };
    }

    public HealthChange damage(double requestedDamage) {
        double requested = requireNonNegativeFinite(requestedDamage, "requestedDamage");
        double before = currentHealth();
        double after = Math.max(0.0, before - requested);
        setCurrentHealth(after);
        return new HealthChange(requested, before - after, before, after);
    }

    public HealthChange heal(double requestedHealing) {
        double requested = requireNonNegativeFinite(requestedHealing, "requestedHealing");
        double before = currentHealth();
        double after = Math.min(currentMaximum(), before + requested);
        setCurrentHealth(after);
        return new HealthChange(requested, after - before, before, after);
    }

    public ScalingChange increaseCurrentPhaseForPlayerCount(int observedPlayerCount) {
        if (observedPlayerCount < 1 || observedPlayerCount > maximumPlayers) {
            throw new IllegalArgumentException("observedPlayerCount must be between 1 and maximumPlayers");
        }
        int previousPlayerCount = currentScaledPlayers();
        double previousMaximum = currentMaximum();
        double previousCurrent = currentHealth();
        if (observedPlayerCount <= previousPlayerCount) {
            return new ScalingChange(
                    activePhase,
                    previousPlayerCount,
                    previousPlayerCount,
                    previousMaximum,
                    previousMaximum,
                    previousCurrent,
                    previousCurrent
            );
        }

        double increase = baseMaximum(activePhase)
                * healthPerExtraPlayer
            * (observedPlayerCount - previousPlayerCount);
        double newMaximum = requirePositiveFinite(previousMaximum + increase, "scaledMaximum");
        double newCurrent = requireNonNegativeFinite(previousCurrent + increase, "scaledCurrent");
        setCurrentMaximum(newMaximum);
        setCurrentHealth(newCurrent);
        setCurrentScaledPlayers(observedPlayerCount);
        return new ScalingChange(
                activePhase,
                previousPlayerCount,
            observedPlayerCount,
                previousMaximum,
                newMaximum,
                previousCurrent,
                newCurrent
        );
    }

    public PhaseSnapshot startSecondPhase(int observedPlayerCount) {
        if (activePhase == Phase.TWO) {
            throw new IllegalStateException("second phase has already started");
        }
        if (phaseOneCurrent > 0.0) {
            throw new IllegalStateException("first phase must be depleted before starting second phase");
        }
        if (observedPlayerCount < 1 || observedPlayerCount > maximumPlayers) {
            throw new IllegalArgumentException("observedPlayerCount must be between 1 and maximumPlayers");
        }
        activePhase = Phase.TWO;
        if (observedPlayerCount > phaseTwoScaledPlayers) {
            phaseTwoMaximum = scaledMaximum(phaseTwoBaseMaximum, observedPlayerCount);
            phaseTwoScaledPlayers = observedPlayerCount;
        }
        phaseTwoCurrent = phaseTwoMaximum * secondPhaseStartRatio;
        return activeSnapshot();
    }

    public boolean isCurrentPhaseDepleted() {
        return currentHealth() == 0.0;
    }

    public double currentMaximum() {
        return activePhase == Phase.ONE ? phaseOneMaximum : phaseTwoMaximum;
    }

    public double currentHealth() {
        return activePhase == Phase.ONE ? phaseOneCurrent : phaseTwoCurrent;
    }

    private double scaledMaximum(double baseMaximum, int playerCount) {
        double multiplier = 1.0 + healthPerExtraPlayer * (playerCount - 1);
        return requirePositiveFinite(baseMaximum * multiplier, "scaledMaximum");
    }

    private double baseMaximum(Phase phase) {
        return phase == Phase.ONE ? phaseOneBaseMaximum : phaseTwoBaseMaximum;
    }

    private int currentScaledPlayers() {
        return activePhase == Phase.ONE ? phaseOneScaledPlayers : phaseTwoScaledPlayers;
    }

    private void setCurrentMaximum(double maximum) {
        if (activePhase == Phase.ONE) {
            phaseOneMaximum = maximum;
        } else {
            phaseTwoMaximum = maximum;
        }
    }

    private void setCurrentHealth(double current) {
        if (activePhase == Phase.ONE) {
            phaseOneCurrent = current;
        } else {
            phaseTwoCurrent = current;
        }
    }

    private void setCurrentScaledPlayers(int playerCount) {
        if (activePhase == Phase.ONE) {
            phaseOneScaledPlayers = playerCount;
        } else {
            phaseTwoScaledPlayers = playerCount;
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

    private static double requireRatio(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in (0, 1]");
        }
        return value;
    }

    private static PersistentState requireValidPersistentState(PersistentState state) {
        PersistentState requiredState = Objects.requireNonNull(state, "state");
        validatePersistentState(
                requiredState.activePhase(),
                requiredState.phaseOneBaseMaximum(),
                requiredState.phaseOneMaximum(),
                requiredState.phaseOneCurrent(),
                requiredState.phaseOneScaledPlayerCount(),
                requiredState.phaseTwoBaseMaximum(),
                requiredState.phaseTwoMaximum(),
                requiredState.phaseTwoCurrent(),
                requiredState.phaseTwoScaledPlayerCount(),
                requiredState.maximumPlayers(),
                requiredState.healthPerExtraPlayer(),
                requiredState.secondPhaseStartRatio()
        );
        return requiredState;
    }

    private static void validatePersistentState(
            Phase activePhase,
            double phaseOneBaseMaximum,
            double phaseOneMaximum,
            double phaseOneCurrent,
            int phaseOneScaledPlayerCount,
            double phaseTwoBaseMaximum,
            double phaseTwoMaximum,
            double phaseTwoCurrent,
            int phaseTwoScaledPlayerCount,
            int maximumPlayers,
            double healthPerExtraPlayer,
            double secondPhaseStartRatio
    ) {
        Objects.requireNonNull(activePhase, "activePhase");
        double validatedPhaseOneBaseMaximum = requirePositiveFinite(
                phaseOneBaseMaximum,
                "phaseOneBaseMaximum"
        );
        double validatedPhaseTwoBaseMaximum = requirePositiveFinite(
                phaseTwoBaseMaximum,
                "phaseTwoBaseMaximum"
        );
        if (maximumPlayers < 1) {
            throw new IllegalArgumentException("maximumPlayers must be positive");
        }
        double validatedHealthPerExtraPlayer = requireNonNegativeFinite(
                healthPerExtraPlayer,
                "healthPerExtraPlayer"
        );
        requireRatio(secondPhaseStartRatio, "secondPhaseStartRatio");
        validatePersistentPhase(
                "phaseOne",
                validatedPhaseOneBaseMaximum,
                phaseOneMaximum,
                phaseOneCurrent,
                phaseOneScaledPlayerCount,
                maximumPlayers,
                validatedHealthPerExtraPlayer
        );
        validatePersistentPhase(
                "phaseTwo",
                validatedPhaseTwoBaseMaximum,
                phaseTwoMaximum,
                phaseTwoCurrent,
                phaseTwoScaledPlayerCount,
                maximumPlayers,
                validatedHealthPerExtraPlayer
        );
    }

    private static void validatePersistentPhase(
            String phaseName,
            double baseMaximum,
            double maximum,
            double current,
            int scaledPlayerCount,
            int maximumPlayers,
            double healthPerExtraPlayer
    ) {
        double validatedMaximum = requirePositiveFinite(maximum, phaseName + "Maximum");
        double validatedCurrent = requireNonNegativeFinite(current, phaseName + "Current");
        if (validatedCurrent > validatedMaximum) {
            throw new IllegalArgumentException(
                    phaseName + "Current must be between 0 and " + phaseName + "Maximum"
            );
        }
        if (scaledPlayerCount < 1 || scaledPlayerCount > maximumPlayers) {
            throw new IllegalArgumentException(
                    phaseName + "ScaledPlayerCount must be between 1 and maximumPlayers"
            );
        }

        double multiplier = 1.0 + healthPerExtraPlayer * (scaledPlayerCount - 1);
        double expectedMaximum = requirePositiveFinite(
                baseMaximum * multiplier,
                phaseName + "ExpectedMaximum"
        );
        if (validatedMaximum == expectedMaximum) {
            return;
        }

        // Scaling may reach the same formula through repeated additions, so allow only bounded roundoff.
        double relativeTolerance = SCALING_VALIDATION_ULPS_PER_PLAYER
                * Math.ulp(1.0)
                * scaledPlayerCount;
        double tolerance = Math.max(
                Math.ulp(expectedMaximum) * SCALING_VALIDATION_ULPS_PER_PLAYER,
                expectedMaximum * relativeTolerance
        );
        if (Math.abs(validatedMaximum - expectedMaximum) > tolerance) {
            throw new IllegalArgumentException(
                    phaseName + "Maximum must match the scaled base maximum within floating-point tolerance"
            );
        }
    }

    public enum Phase {
        ONE,
        TWO
    }

    public record PersistentState(
            Phase activePhase,
            double phaseOneBaseMaximum,
            double phaseOneMaximum,
            double phaseOneCurrent,
            int phaseOneScaledPlayerCount,
            double phaseTwoBaseMaximum,
            double phaseTwoMaximum,
            double phaseTwoCurrent,
            int phaseTwoScaledPlayerCount,
            int maximumPlayers,
            double healthPerExtraPlayer,
            double secondPhaseStartRatio
    ) {
        public PersistentState {
            validatePersistentState(
                    activePhase,
                    phaseOneBaseMaximum,
                    phaseOneMaximum,
                    phaseOneCurrent,
                    phaseOneScaledPlayerCount,
                    phaseTwoBaseMaximum,
                    phaseTwoMaximum,
                    phaseTwoCurrent,
                    phaseTwoScaledPlayerCount,
                    maximumPlayers,
                    healthPerExtraPlayer,
                    secondPhaseStartRatio
            );
        }
    }

    public record PhaseSnapshot(
            Phase phase,
            double baseMaximum,
            double maximum,
            double current,
            int scaledPlayerCount
    ) {
    }

    public record HealthChange(double requested, double applied, double before, double after) {
    }

    public record ScalingChange(
            Phase phase,
            int previousPlayerCount,
            int appliedPlayerCount,
            double previousMaximum,
            double newMaximum,
            double previousCurrent,
            double newCurrent
    ) {
        public boolean changed() {
            return appliedPlayerCount > previousPlayerCount;
        }
    }
}