package com.tonywww.elder_bosses.boss.malenia.indicator;

public record IndicatorPoint(double x, double y, double z) {
    public IndicatorPoint {
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
    }

    public double horizontalDistanceTo(IndicatorPoint other) {
        double deltaX = other.x - x;
        double deltaZ = other.z - z;
        return Math.hypot(deltaX, deltaZ);
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}