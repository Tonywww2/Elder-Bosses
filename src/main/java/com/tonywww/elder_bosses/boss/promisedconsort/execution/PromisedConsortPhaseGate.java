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
}