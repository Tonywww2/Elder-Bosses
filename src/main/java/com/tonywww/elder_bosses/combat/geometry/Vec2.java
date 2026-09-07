package com.tonywww.elder_bosses.combat.geometry;

import java.util.Objects;

public record Vec2(double x, double z) {
    public static final Vec2 ZERO = new Vec2(0.0, 0.0);

    public Vec2 {
        Geometry2D.requireFinite(x, "x");
        Geometry2D.requireFinite(z, "z");
    }

    public Vec2 add(Vec2 other) {
        Objects.requireNonNull(other, "other");
        return new Vec2(x + other.x, z + other.z);
    }

    public Vec2 subtract(Vec2 other) {
        Objects.requireNonNull(other, "other");
        return new Vec2(x - other.x, z - other.z);
    }

    public Vec2 scale(double factor) {
        Geometry2D.requireFinite(factor, "factor");
        return new Vec2(x * factor, z * factor);
    }

    public double dot(Vec2 other) {
        Objects.requireNonNull(other, "other");
        return x * other.x + z * other.z;
    }

    public double cross(Vec2 other) {
        Objects.requireNonNull(other, "other");
        return x * other.z - z * other.x;
    }

    public double length() {
        return Math.hypot(x, z);
    }

    public double distanceTo(Vec2 other) {
        Objects.requireNonNull(other, "other");
        return Math.hypot(x - other.x, z - other.z);
    }

    public boolean isZero() {
        return x == 0.0 && z == 0.0;
    }

    public Vec2 normalized() {
        double scale = Math.max(Math.abs(x), Math.abs(z));
        if (scale == 0.0) {
            return ZERO;
        }

        double scaledX = x / scale;
        double scaledZ = z / scale;
        double inverseLength = 1.0 / Math.sqrt(scaledX * scaledX + scaledZ * scaledZ);
        return new Vec2(scaledX * inverseLength, scaledZ * inverseLength);
    }

    public Vec2 normalizedOr(Vec2 fallback) {
        Objects.requireNonNull(fallback, "fallback");
        return isZero() ? fallback.normalized() : normalized();
    }
}