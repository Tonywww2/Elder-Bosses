package com.tonywww.elder_bosses.combat.geometry;

import java.util.Objects;

public record DirectionalRectangle(
        Vec2 origin,
        Vec2 forward,
        double length,
        double width
) implements HorizontalShape {
    public DirectionalRectangle {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(forward, "forward");
        if (forward.isZero()) {
            throw new IllegalArgumentException("forward must be non-zero");
        }
        forward = forward.normalized();
        length = Geometry2D.requireNonNegativeFinite(length, "length");
        width = Geometry2D.requireNonNegativeFinite(width, "width");
    }

    @Override
    public boolean contains(double x, double z) {
        Geometry2D.requireFinite(x, "x");
        Geometry2D.requireFinite(z, "z");
        double offsetX = x - origin.x();
        double offsetZ = z - origin.z();
        double forwardDistance = offsetX * forward.x() + offsetZ * forward.z();
        double lateralDistance = -offsetX * forward.z() + offsetZ * forward.x();
        return forwardDistance >= 0.0
                && forwardDistance <= length
                && Math.abs(lateralDistance) <= width * 0.5;
    }

    @Override
    public double minX() {
        return origin.x() - boundsRadius();
    }

    @Override
    public double minZ() {
        return origin.z() - boundsRadius();
    }

    @Override
    public double maxX() {
        return origin.x() + boundsRadius();
    }

    @Override
    public double maxZ() {
        return origin.z() + boundsRadius();
    }

    private double boundsRadius() {
        return Math.hypot(length, width * 0.5);
    }
}