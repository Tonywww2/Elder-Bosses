package com.tonywww.elder_bosses.boss.promisedconsort.execution;

public final class PromisedConsortPhaseGate {
    private PromisedConsortPhaseGate() {
    }

    public static float threshold(float maximumHealth, double configuredRatio) {
        double ratio = Double.isFinite(configuredRatio) ? Math.max(0.05, Math.min(1, configuredRatio)) : 0.05;
        return (float) Math.max(Math.ulp(maximumHealth), maximumHealth * ratio);
    }

    public static float protect(float requestedHealth, float currentHealth, float maximumHealth, double configuredRatio) {
        if (Float.isNaN(requestedHealth)) return currentHealth;
        return Math.max(threshold(maximumHealth, configuredRatio), Math.min(maximumHealth, requestedHealth));
    }

    public static boolean meteorAvailable(boolean enabled, boolean pending, boolean triggered, long now, long readyAt) {
        return enabled && (pending || triggered && now >= readyAt);
    }

    public static float meteorThreshold(float maximumHealth, double remainingHealthRatio) {
        double ratio=Double.isFinite(remainingHealthRatio)?Math.max(.01,Math.min(1,remainingHealthRatio)):.85;
        return (float)Math.max(Math.ulp(maximumHealth),maximumHealth*ratio);
    }

    /** A large hit must stop at the first unconsumed health gate it crosses. */
    public static boolean meteorThresholdReached(float health,float maximumHealth,double meteorRatio,
                                                 boolean phaseProtected,double phaseTwoRatio) {
        float meteor=meteorThreshold(maximumHealth,meteorRatio);
        return health<=meteor && (!phaseProtected || meteor>=threshold(maximumHealth,phaseTwoRatio));
    }

    public static long meteorReadyAt(String repeatMode,long now,long cooldownTicks) {
        return "once".equals(repeatMode) || cooldownTicks<=0?Long.MAX_VALUE:Math.addExact(now,cooldownTicks);
    }
}
