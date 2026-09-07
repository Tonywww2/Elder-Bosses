package com.tonywww.elder_bosses.combat.status;

public record ScarletRotSnapshot(
        long gameTick,
        double buildup,
        double capacity,
        boolean active,
        int remainingActiveTicks,
        double healingMultiplier,
        double movementSpeedMultiplier
) {
    public ScarletRotSnapshot {
        if (gameTick < 0L) {
            throw new IllegalArgumentException("gameTick must be non-negative");
        }
        if (!Double.isFinite(capacity) || capacity <= 0.0) {
            throw new IllegalArgumentException("capacity must be finite and positive");
        }
        if (!Double.isFinite(buildup) || buildup < 0.0) {
            throw new IllegalArgumentException("buildup must be finite and non-negative");
        }
        if (!active && buildup >= capacity) {
            throw new IllegalArgumentException("inactive buildup must be below capacity");
        }
        if (remainingActiveTicks < 0 || active != (remainingActiveTicks > 0)) {
            throw new IllegalArgumentException(
                    "remainingActiveTicks must be positive exactly while active"
            );
        }
        requireMultiplier(healingMultiplier, "healingMultiplier");
        requireMultiplier(movementSpeedMultiplier, "movementSpeedMultiplier");
        if (!active && (healingMultiplier != 1.0 || movementSpeedMultiplier != 1.0)) {
            throw new IllegalArgumentException("inactive multipliers must equal 1");
        }
    }

    public static ScarletRotSnapshot inactive(long gameTick, double capacity) {
        return new ScarletRotSnapshot(gameTick, 0.0, capacity, false, 0, 1.0, 1.0);
    }

    private static void requireMultiplier(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and between 0 and 1");
        }
    }
}