package com.tonywww.elder_bosses.combat.action;

public record SkillTuning(double castSpeedMultiplier, double rangeMultiplier) {
    public static final SkillTuning NEUTRAL = new SkillTuning(1.0, 1.0);

    public SkillTuning {
        requirePositiveFinite(castSpeedMultiplier, "castSpeedMultiplier");
        requirePositiveFinite(rangeMultiplier, "rangeMultiplier");
    }

    public int scaleTicks(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must be non-negative");
        }
        if (ticks == 0) {
            return 0;
        }
        return Math.max(1, (int) Math.round(ticks / castSpeedMultiplier));
    }

    public double scaleRange(double distance) {
        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException("distance must be finite and non-negative");
        }
        return distance * rangeMultiplier;
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
