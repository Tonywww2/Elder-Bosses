package com.tonywww.elder_bosses.combat.geometry;

import java.util.Objects;

public record Capsule(Vec2 start, Vec2 end, double radius) implements HorizontalShape {
    public Capsule {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        radius = Geometry2D.requireNonNegativeFinite(radius, "radius");
    }

    public Capsule(
            double startX,
            double startZ,
            double endX,
            double endZ,
            double radius
    ) {
        this(new Vec2(startX, startZ), new Vec2(endX, endZ), radius);
    }

    @Override
    public boolean contains(double x, double z) {
        return Geometry2D.distanceToSegment(
                x,
                z,
                start.x(),
                start.z(),
                end.x(),
                end.z()
        ) <= radius;
    }

    public Vec2 closestPoint(double x, double z) {
        return Geometry2D.closestPointOnSegment(
                x,
                z,
                start.x(),
                start.z(),
                end.x(),
                end.z()
        );
    }

    public Vec2 closestPoint(Vec2 point) {
        Objects.requireNonNull(point, "point");
        return closestPoint(point.x(), point.z());
    }

    @Override
    public double minX() {
        return Geometry2D.lowerBound(Math.min(start.x(), end.x()), radius);
    }

    @Override
    public double minZ() {
        return Geometry2D.lowerBound(Math.min(start.z(), end.z()), radius);
    }

    @Override
    public double maxX() {
        return Geometry2D.upperBound(Math.max(start.x(), end.x()), radius);
    }

    @Override
    public double maxZ() {
        return Geometry2D.upperBound(Math.max(start.z(), end.z()), radius);
    }
}