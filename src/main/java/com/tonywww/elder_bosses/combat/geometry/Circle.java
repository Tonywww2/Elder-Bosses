package com.tonywww.elder_bosses.combat.geometry;

import java.util.Objects;

public record Circle(Vec2 center, double radius) implements HorizontalShape {
    public Circle {
        Objects.requireNonNull(center, "center");
        radius = Geometry2D.requireNonNegativeFinite(radius, "radius");
    }

    public Circle(double centerX, double centerZ, double radius) {
        this(new Vec2(centerX, centerZ), radius);
    }

    @Override
    public boolean contains(double x, double z) {
        Geometry2D.requireFinite(x, "x");
        Geometry2D.requireFinite(z, "z");
        return Math.hypot(x - center.x(), z - center.z()) <= radius;
    }

    @Override
    public double minX() {
        return Geometry2D.lowerBound(center.x(), radius);
    }

    @Override
    public double minZ() {
        return Geometry2D.lowerBound(center.z(), radius);
    }

    @Override
    public double maxX() {
        return Geometry2D.upperBound(center.x(), radius);
    }

    @Override
    public double maxZ() {
        return Geometry2D.upperBound(center.z(), radius);
    }
}