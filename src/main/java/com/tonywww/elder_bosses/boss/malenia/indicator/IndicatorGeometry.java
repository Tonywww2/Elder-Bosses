package com.tonywww.elder_bosses.boss.malenia.indicator;

import java.util.List;
import java.util.Objects;

public sealed interface IndicatorGeometry permits
        IndicatorGeometry.Sector,
        IndicatorGeometry.Capsule,
        IndicatorGeometry.Circle,
        IndicatorGeometry.Annulus,
        IndicatorGeometry.Path,
        IndicatorGeometry.Zone {

    ShapeType shapeType();

    enum ShapeType {
        SECTOR,
        CAPSULE,
        CIRCLE,
        ANNULUS,
        PATH,
        ZONE
    }

    record Sector(double radius, double arcDegrees) implements IndicatorGeometry {
        public Sector {
            requirePositive(radius, "radius");
            requirePositive(arcDegrees, "arcDegrees");
            if (arcDegrees > 360.0) {
                throw new IllegalArgumentException("arcDegrees must not exceed 360");
            }
        }

        @Override
        public ShapeType shapeType() {
            return ShapeType.SECTOR;
        }
    }

    record Capsule(double length, double width) implements IndicatorGeometry {
        public Capsule {
            requirePositive(length, "length");
            requirePositive(width, "width");
        }

        @Override
        public ShapeType shapeType() {
            return ShapeType.CAPSULE;
        }
    }

    record Circle(double radius) implements IndicatorGeometry {
        public Circle {
            requirePositive(radius, "radius");
        }

        @Override
        public ShapeType shapeType() {
            return ShapeType.CIRCLE;
        }
    }

    record Annulus(double innerRadius, double outerRadius) implements IndicatorGeometry {
        public Annulus {
            requireNonNegative(innerRadius, "innerRadius");
            requirePositive(outerRadius, "outerRadius");
            if (innerRadius > outerRadius) {
                throw new IllegalArgumentException("innerRadius must not exceed outerRadius");
            }
        }

        @Override
        public ShapeType shapeType() {
            return ShapeType.ANNULUS;
        }
    }

    record Path(double width, List<IndicatorPoint> points) implements IndicatorGeometry {
        public Path {
            requireNonNegative(width, "width");
            points = List.copyOf(Objects.requireNonNull(points, "points"));
            if (points.size() < 2) {
                throw new IllegalArgumentException("path must contain at least two points");
            }
            points.forEach(point -> Objects.requireNonNull(point, "path point"));
        }

        @Override
        public ShapeType shapeType() {
            return ShapeType.PATH;
        }
    }

    record Zone(double radius, int durationTicks) implements IndicatorGeometry {
        public Zone {
            requirePositive(radius, "radius");
            if (durationTicks <= 0) {
                throw new IllegalArgumentException("durationTicks must be positive");
            }
        }

        @Override
        public ShapeType shapeType() {
            return ShapeType.ZONE;
        }
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}