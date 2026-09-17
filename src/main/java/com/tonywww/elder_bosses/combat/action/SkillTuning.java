package com.tonywww.elder_bosses.combat.action;

public record SkillTuning(double castSpeedMultiplier, double rangeMultiplier, java.util.List<ActionStage> componentStages) {
    public static final SkillTuning NEUTRAL = new SkillTuning(1.0, 1.0);

    public SkillTuning {
        requirePositiveFinite(castSpeedMultiplier, "castSpeedMultiplier");
        requirePositiveFinite(rangeMultiplier, "rangeMultiplier");
        castSpeedMultiplier = 1.0;
        componentStages = java.util.List.copyOf(componentStages);
        if (!componentStages.isEmpty()) {
            ActionTimeline timeline = ActionTimeline.ofStages(componentStages.toArray(ActionStage[]::new));
            if (timeline.totalTicks() > com.tonywww.elder_bosses.network.NetworkLimits.MAX_TICKS) throw new IllegalArgumentException("Component timing exceeds the tick limit");
            if (componentStages.stream().anyMatch(stage -> stage.activeTicks() < 1)) throw new IllegalArgumentException("Component release must contain at least one tick");
        }
    }

    public SkillTuning(double castSpeedMultiplier, double rangeMultiplier) {
        this(castSpeedMultiplier, rangeMultiplier, java.util.List.of());
    }

    public SkillTuning(double rangeMultiplier) {
        this(1.0, rangeMultiplier);
    }

    public int scaleTicks(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must be non-negative");
        }
        return ticks;
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
