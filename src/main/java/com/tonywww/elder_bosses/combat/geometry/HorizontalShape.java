package com.tonywww.elder_bosses.combat.geometry;

import java.util.Objects;

public sealed interface HorizontalShape permits Annulus, Capsule, Circle, DirectionalRectangle, Sector {
    boolean contains(double x, double z);

    default boolean contains(Vec2 point) {
        Objects.requireNonNull(point, "point");
        return contains(point.x(), point.z());
    }

    double minX();

    double minZ();

    double maxX();

    double maxZ();
}