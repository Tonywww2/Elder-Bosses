package com.tonywww.elder_bosses.combat.geometry;

import java.util.Objects;

public final class Geometry2D {
    public static final double TAU = Math.PI * 2.0;

    private Geometry2D() {
    }

    public static double normalizeRadians(double angleRadians) {
        requireFinite(angleRadians, "angleRadians");
        double normalized = angleRadians % TAU;
        if (normalized < 0.0) {
            normalized += TAU;
        }
        return normalized == 0.0 ? 0.0 : normalized;
    }

    public static double normalizeSignedRadians(double angleRadians) {
        double normalized = normalizeRadians(angleRadians);
        return normalized >= Math.PI ? normalized - TAU : normalized;
    }

    public static Vec2 closestPointOnSegment(Vec2 point, Vec2 start, Vec2 end) {
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        return closestPointOnSegment(point.x(), point.z(), start.x(), start.z(), end.x(), end.z());
    }

    public static Vec2 closestPointOnSegment(
            double pointX,
            double pointZ,
            double startX,
            double startZ,
            double endX,
            double endZ
    ) {
        double parameter = segmentParameter(pointX, pointZ, startX, startZ, endX, endZ);
        if (parameter == 0.0) {
            return new Vec2(startX, startZ);
        }
        if (parameter == 1.0) {
            return new Vec2(endX, endZ);
        }
        return new Vec2(
                startX * (1.0 - parameter) + endX * parameter,
                startZ * (1.0 - parameter) + endZ * parameter
        );
    }

    public static double distanceToSegment(Vec2 point, Vec2 start, Vec2 end) {
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        return distanceToSegment(point.x(), point.z(), start.x(), start.z(), end.x(), end.z());
    }

    public static double distanceToSegment(
            double pointX,
            double pointZ,
            double startX,
            double startZ,
            double endX,
            double endZ
    ) {
        double parameter = segmentParameter(pointX, pointZ, startX, startZ, endX, endZ);
        double closestX = startX * (1.0 - parameter) + endX * parameter;
        double closestZ = startZ * (1.0 - parameter) + endZ * parameter;
        return Math.hypot(pointX - closestX, pointZ - closestZ);
    }

    static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    static double requireNonNegativeFinite(double value, String name) {
        requireFinite(value, name);
        if (value < 0.0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }

    static double lowerBound(double center, double radius) {
        double result = center - radius;
        return result == Double.NEGATIVE_INFINITY ? -Double.MAX_VALUE : result;
    }

    static double upperBound(double center, double radius) {
        double result = center + radius;
        return result == Double.POSITIVE_INFINITY ? Double.MAX_VALUE : result;
    }

    private static double segmentParameter(
            double pointX,
            double pointZ,
            double startX,
            double startZ,
            double endX,
            double endZ
    ) {
        requireFinite(pointX, "pointX");
        requireFinite(pointZ, "pointZ");
        requireFinite(startX, "startX");
        requireFinite(startZ, "startZ");
        requireFinite(endX, "endX");
        requireFinite(endZ, "endZ");

        double segmentX = endX - startX;
        double segmentZ = endZ - startZ;
        double segmentScale = Math.max(Math.abs(segmentX), Math.abs(segmentZ));
        if (segmentScale == 0.0) {
            return 0.0;
        }
        if (!Double.isFinite(segmentScale)) {
            return scaledSegmentParameter(pointX, pointZ, startX, startZ, endX, endZ);
        }

        double scaledSegmentX = segmentX / segmentScale;
        double scaledSegmentZ = segmentZ / segmentScale;
        double denominator = scaledSegmentX * scaledSegmentX + scaledSegmentZ * scaledSegmentZ;
        double parameter = ((pointX - startX) / segmentScale * scaledSegmentX
                + (pointZ - startZ) / segmentScale * scaledSegmentZ) / denominator;
        return clampSegmentParameter(parameter, pointX, pointZ, startX, startZ, endX, endZ);
    }

    private static double scaledSegmentParameter(
            double pointX,
            double pointZ,
            double startX,
            double startZ,
            double endX,
            double endZ
    ) {
        double coordinateScale = Math.max(
                Math.max(Math.abs(pointX), Math.abs(pointZ)),
                Math.max(
                        Math.max(Math.abs(startX), Math.abs(startZ)),
                        Math.max(Math.abs(endX), Math.abs(endZ))
                )
        );
        double scaledStartX = startX / coordinateScale;
        double scaledStartZ = startZ / coordinateScale;
        double segmentX = endX / coordinateScale - scaledStartX;
        double segmentZ = endZ / coordinateScale - scaledStartZ;
        double denominator = segmentX * segmentX + segmentZ * segmentZ;
        if (denominator == 0.0) {
            return closerEndpoint(pointX, pointZ, startX, startZ, endX, endZ);
        }
        double parameter = (
                (pointX / coordinateScale - scaledStartX) * segmentX
                        + (pointZ / coordinateScale - scaledStartZ) * segmentZ
        ) / denominator;
        return clampSegmentParameter(parameter, pointX, pointZ, startX, startZ, endX, endZ);
    }

    private static double clampSegmentParameter(
            double parameter,
            double pointX,
            double pointZ,
            double startX,
            double startZ,
            double endX,
            double endZ
    ) {
        if (Double.isNaN(parameter)) {
            return closerEndpoint(pointX, pointZ, startX, startZ, endX, endZ);
        }
        return Math.max(0.0, Math.min(1.0, parameter));
    }

    private static double closerEndpoint(
            double pointX,
            double pointZ,
            double startX,
            double startZ,
            double endX,
            double endZ
    ) {
        double startDistance = Math.hypot(pointX - startX, pointZ - startZ);
        double endDistance = Math.hypot(pointX - endX, pointZ - endZ);
        return startDistance <= endDistance ? 0.0 : 1.0;
    }
}