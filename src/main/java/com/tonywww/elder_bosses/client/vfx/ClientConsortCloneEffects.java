package com.tonywww.elder_bosses.client.vfx;

/** Clone model visibility; holy decoration is rendered by ClientConsortHolyColumns. */
public final class ClientConsortCloneEffects {
    private ClientConsortCloneEffects() {}

    public static float opacity(double time, long appear, long impact, long end) {
        if (time < appear || time >= end || end <= appear) return 0;
        double rise = smooth((time - appear) / Math.max(1.0, Math.min(2.0, impact - appear)));
        double fadeStart = Math.min(end - 1.0, Math.max(impact + 1.0, end - 5.0));
        double fall = 1 - smooth((time - fadeStart) / Math.max(1.0, end - fadeStart));
        return (float) (rise * fall);
    }

    public static double smooth(double value) {
        value = Math.max(0, Math.min(1, value));
        return value * value * (3 - 2 * value);
    }

}
