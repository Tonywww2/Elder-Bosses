package com.tonywww.elder_bosses.client.render;

public final class MaleniaPoseTransition {
    private MaleniaPoseTransition() {
    }

    public static float weight(double elapsed, double duration) {
        if (duration <= 0.0) {
            return 1.0F;
        }
        double fraction = Math.max(0.0, Math.min(1.0, elapsed / duration));
        return (float) (fraction * fraction * (3.0 - 2.0 * fraction));
    }

    public static float rotation(float previous, float target, float weight) {
        double difference = Math.IEEEremainder(previous - target, Math.PI * 2.0);
        return (float) (target + difference * (1.0 - weight));
    }

    public static float position(float previous, float target, float weight) {
        return target + (previous - target) * (1.0F - weight);
    }
}