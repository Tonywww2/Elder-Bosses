package com.tonywww.elder_bosses.combat.geometry;

import java.util.Objects;

public record Sector(Vec2 center, Vec2 forward, double radius, double halfAngleRadians)
        implements HorizontalShape {
    public Sector {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(forward, "forward");
        if (forward.isZero()) {
            throw new IllegalArgumentException("forward must be non-zero");
        }
        forward = forward.normalized();
        radius = Geometry2D.requireNonNegativeFinite(radius, "radius");
        halfAngleRadians = Geometry2D.requireNonNegativeFinite(
                halfAngleRadians,
                "halfAngleRadians"
        );
        if (halfAngleRadians > Math.PI) {
            throw new IllegalArgumentException("halfAngleRadians must not exceed pi");
        }
    }

    public Sector(
            double centerX,
            double centerZ,
            double forwardX,
            double forwardZ,
            double radius,
            double halfAngleRadians
    ) {
        this(
                new Vec2(centerX, centerZ),
                new Vec2(forwardX, forwardZ),
                radius,
                halfAngleRadians
        );
    }

    @Override
    public boolean contains(double x, double z) {
        Geometry2D.requireFinite(x, "x");
        Geometry2D.requireFinite(z, "z");
        double offsetX = x - center.x();
        double offsetZ = z - center.z();
        double distance = Math.hypot(offsetX, offsetZ);
        if (distance > radius) {
            return false;
        }
        if (distance == 0.0 || halfAngleRadians == Math.PI) {
            return true;
        }

        double scale = Math.max(Math.abs(offsetX), Math.abs(offsetZ));
        double scaledX = offsetX / scale;
        double scaledZ = offsetZ / scale;
        double inverseLength = 1.0 / Math.sqrt(scaledX * scaledX + scaledZ * scaledZ);
        double directionX = scaledX * inverseLength;
        double directionZ = scaledZ * inverseLength;
        double cross = forward.x() * directionZ - forward.z() * directionX;
        double dot = forward.x() * directionX + forward.z() * directionZ;
        return Math.atan2(Math.abs(cross), dot) <= halfAngleRadians;
    }

    public double directionAngleRadians() {
        return Geometry2D.normalizeRadians(Math.atan2(forward.z(), forward.x()));
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