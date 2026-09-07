package com.tonywww.elder_bosses.combat.geometry;

import java.util.Objects;

public record Annulus(Vec2 center, double innerRadius, double outerRadius)
        implements HorizontalShape {
    public Annulus {
        Objects.requireNonNull(center, "center");
        innerRadius = Geometry2D.requireNonNegativeFinite(innerRadius, "innerRadius");
        outerRadius = Geometry2D.requireNonNegativeFinite(outerRadius, "outerRadius");
        if (innerRadius > outerRadius) {
            throw new IllegalArgumentException("innerRadius must not exceed outerRadius");
        }
    }

    public Annulus(double centerX, double centerZ, double innerRadius, double outerRadius) {
        this(new Vec2(centerX, centerZ), innerRadius, outerRadius);
    }

    @Override
    public boolean contains(double x, double z) {
        Geometry2D.requireFinite(x, "x");
        Geometry2D.requireFinite(z, "z");
        double distance = Math.hypot(x - center.x(), z - center.z());
        return distance >= innerRadius && distance <= outerRadius;
    }

    @Override
    public double minX() {
        return Geometry2D.lowerBound(center.x(), outerRadius);
    }

    @Override
    public double minZ() {
        return Geometry2D.lowerBound(center.z(), outerRadius);
    }

    @Override
    public double maxX() {
        return Geometry2D.upperBound(center.x(), outerRadius);
    }

    @Override
    public double maxZ() {
        return Geometry2D.upperBound(center.z(), outerRadius);
    }
}