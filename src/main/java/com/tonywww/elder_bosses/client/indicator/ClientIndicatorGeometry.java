package com.tonywww.elder_bosses.client.indicator;

import com.tonywww.elder_bosses.network.IndicatorSnapshotPacket;

import java.util.ArrayList;
import java.util.List;

public final class ClientIndicatorGeometry {
    private ClientIndicatorGeometry() {
    }

    public static Mesh create(IndicatorSnapshotPacket snapshot, int maxSegmentsPerShape) {
        int segmentLimit = Math.max(3, maxSegmentsPerShape);
        MeshBuilder builder = new MeshBuilder();
        switch (snapshot.shapeType()) {
            case SECTOR -> appendSector(builder, snapshot, segmentLimit);
            case CAPSULE -> appendCapsule(builder, snapshot, segmentLimit);
            case CIRCLE, ZONE -> appendCircle(builder, snapshot, segmentLimit);
            case ANNULUS -> appendAnnulus(builder, snapshot, segmentLimit);
            case PATH -> appendPath(builder, snapshot, segmentLimit);
            case RECTANGLE -> appendRectangle(builder, snapshot);
        }
        return builder.build();
    }

    private static void appendSector(
            MeshBuilder builder,
            IndicatorSnapshotPacket snapshot,
            int segmentLimit
    ) {
        double radius = snapshot.ranges().get(0);
        double arcDegrees = snapshot.ranges().get(1);
        int segments = Math.min(segmentLimit, Math.max(3, (int) Math.ceil(arcDegrees / 7.5)));
        List<Vertex> polygon = new ArrayList<>(segments + 2);
        Vertex anchor = vertex(snapshot.anchor());
        polygon.add(anchor);
        for (int index = 0; index <= segments; index++) {
            double offset = -arcDegrees * 0.5 + arcDegrees * index / segments;
            polygon.add(directedPoint(snapshot, radius, offset));
        }
        for (int index = 1; index + 1 < polygon.size(); index++) {
            builder.addQuad(anchor, polygon.get(index), polygon.get(index + 1), polygon.get(index + 1));
        }
        builder.addLoop(polygon);
    }

    private static void appendCapsule(
            MeshBuilder builder,
            IndicatorSnapshotPacket snapshot,
            int segmentLimit
    ) {
        double length = snapshot.ranges().get(0);
        double radius = snapshot.ranges().get(1) * 0.5;
        int segments = segmentLimit;
        List<Vertex> polygon = new ArrayList<>(segments);
        for (int index = 0; index < segments; index++) {
            double angle = Math.PI * 2.0 * index / segments;
            double forwardCenter = Math.sin(angle) >= 0.0 ? length : 0.0;
            polygon.add(localPoint(
                    snapshot,
                    Math.cos(angle) * radius,
                    forwardCenter + Math.sin(angle) * radius
            ));
        }
        builder.addFan(polygon);
        builder.addLoop(polygon);
    }

    private static void appendCircle(
            MeshBuilder builder,
            IndicatorSnapshotPacket snapshot,
            int segmentLimit
    ) {
        List<Vertex> ring = ring(
                snapshot.anchor(),
                snapshot.ranges().get(0),
            segmentLimit
        );
        builder.addFan(ring);
        builder.addLoop(ring);
    }

    private static void appendAnnulus(
            MeshBuilder builder,
            IndicatorSnapshotPacket snapshot,
            int segmentLimit
    ) {
        double innerRadius = snapshot.ranges().get(0);
        double outerRadius = snapshot.ranges().get(1);
        int segments = segmentLimit;
        List<Vertex> outer = ring(snapshot.anchor(), outerRadius, segments);
        if (innerRadius == outerRadius) {
            builder.addLoop(outer);
            return;
        }
        if (innerRadius == 0.0) {
            builder.addFan(outer);
            builder.addLoop(outer);
            return;
        }

        List<Vertex> inner = ring(snapshot.anchor(), innerRadius, segments);
        for (int index = 0; index < segments; index++) {
            int next = (index + 1) % segments;
            builder.addQuad(outer.get(index), outer.get(next), inner.get(next), inner.get(index));
        }
        builder.addLoop(outer);
        builder.addLoop(inner);
    }

    private static void appendPath(
            MeshBuilder builder,
            IndicatorSnapshotPacket snapshot,
            int segmentLimit
    ) {
        List<IndicatorSnapshotPacket.Point> points = sampledPathPoints(
                snapshot.pathPoints(),
                segmentLimit
        );
        double width = snapshot.ranges().get(0);
        if (width == 0.0) {
            for (int index = 0; index + 1 < points.size(); index++) {
                builder.addLine(vertex(points.get(index)), vertex(points.get(index + 1)));
            }
            return;
        }

        double halfWidth = width * 0.5;
        for (int index = 0; index + 1 < points.size(); index++) {
            IndicatorSnapshotPacket.Point from = points.get(index);
            IndicatorSnapshotPacket.Point to = points.get(index + 1);
            double deltaX = to.x() - from.x();
            double deltaZ = to.z() - from.z();
            double length = Math.hypot(deltaX, deltaZ);
            if (length == 0.0) {
                continue;
            }
            double offsetX = -deltaZ / length * halfWidth;
            double offsetZ = deltaX / length * halfWidth;
            Vertex fromLeft = new Vertex(from.x() + offsetX, from.y(), from.z() + offsetZ);
            Vertex fromRight = new Vertex(from.x() - offsetX, from.y(), from.z() - offsetZ);
            Vertex toRight = new Vertex(to.x() - offsetX, to.y(), to.z() - offsetZ);
            Vertex toLeft = new Vertex(to.x() + offsetX, to.y(), to.z() + offsetZ);
            builder.addQuad(fromLeft, fromRight, toRight, toLeft);
            builder.addLoop(List.of(fromLeft, fromRight, toRight, toLeft));
            builder.addAccent(vertex(from), vertex(to));
        }
    }

        private static void appendRectangle(
            MeshBuilder builder,
            IndicatorSnapshotPacket snapshot
        ) {
        double length = snapshot.ranges().get(0);
        double width = snapshot.ranges().get(1);
        double halfWidth = width * 0.5;
        List<Vertex> polygon = List.of(
            localPoint(snapshot, -halfWidth, 0.0),
            localPoint(snapshot, halfWidth, 0.0),
            localPoint(snapshot, halfWidth, length),
            localPoint(snapshot, -halfWidth, length)
        );
        builder.addFan(polygon);
        builder.addLoop(polygon);
        }

    private static List<IndicatorSnapshotPacket.Point> sampledPathPoints(
            List<IndicatorSnapshotPacket.Point> points,
            int segmentLimit
    ) {
        if (points.size() <= segmentLimit + 1) {
            return points;
        }
        List<IndicatorSnapshotPacket.Point> sampled = new ArrayList<>(segmentLimit + 1);
        int lastIndex = points.size() - 1;
        for (int index = 0; index <= segmentLimit; index++) {
            int sourceIndex = (int) Math.round((double) lastIndex * index / segmentLimit);
            sampled.add(points.get(sourceIndex));
        }
        return sampled;
    }

    private static List<Vertex> ring(
            IndicatorSnapshotPacket.Point center,
            double radius,
            int segments
    ) {
        List<Vertex> points = new ArrayList<>(segments);
        for (int index = 0; index < segments; index++) {
            double angle = Math.PI * 2.0 * index / segments;
            points.add(new Vertex(
                    center.x() + Math.cos(angle) * radius,
                    center.y(),
                    center.z() + Math.sin(angle) * radius
            ));
        }
        return points;
    }

    private static Vertex directedPoint(
            IndicatorSnapshotPacket snapshot,
            double distance,
            double yawOffsetDegrees
    ) {
        double yaw = Math.toRadians(snapshot.directionYawDegrees() + yawOffsetDegrees);
        return new Vertex(
                snapshot.anchor().x() - Math.sin(yaw) * distance,
                snapshot.anchor().y(),
                snapshot.anchor().z() + Math.cos(yaw) * distance
        );
    }

    private static Vertex localPoint(
            IndicatorSnapshotPacket snapshot,
            double rightDistance,
            double forwardDistance
    ) {
        double yaw = Math.toRadians(snapshot.directionYawDegrees());
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        return new Vertex(
                snapshot.anchor().x() + rightX * rightDistance + forwardX * forwardDistance,
                snapshot.anchor().y(),
                snapshot.anchor().z() + rightZ * rightDistance + forwardZ * forwardDistance
        );
    }

    private static Vertex vertex(IndicatorSnapshotPacket.Point point) {
        return new Vertex(point.x(), point.y(), point.z());
    }

    public record Vertex(double x, double y, double z) {
    }

    public record Quad(Vertex first, Vertex second, Vertex third, Vertex fourth) {
    }

    public record Line(Vertex from, Vertex to) {
    }

    public record Mesh(List<Quad> fills, List<Line> borders, List<Line> accents) {
        public Mesh {
            fills = List.copyOf(fills);
            borders = List.copyOf(borders);
            accents = List.copyOf(accents);
        }
    }

    private static final class MeshBuilder {
        private final List<Quad> fills = new ArrayList<>();
        private final List<Line> borders = new ArrayList<>();
        private final List<Line> accents = new ArrayList<>();

        private void addFan(List<Vertex> polygon) {
            if (polygon.size() < 3) {
                return;
            }
            Vertex center = center(polygon);
            for (int index = 0; index < polygon.size(); index++) {
                Vertex current = polygon.get(index);
                Vertex next = polygon.get((index + 1) % polygon.size());
                addQuad(center, current, next, next);
            }
        }

        private void addQuad(Vertex first, Vertex second, Vertex third, Vertex fourth) {
            fills.add(new Quad(first, second, third, fourth));
        }

        private void addLoop(List<Vertex> points) {
            for (int index = 0; index < points.size(); index++) {
                addLine(points.get(index), points.get((index + 1) % points.size()));
            }
        }

        private void addLine(Vertex from, Vertex to) {
            borders.add(new Line(from, to));
        }

        private void addAccent(Vertex from, Vertex to) {
            accents.add(new Line(from, to));
        }

        private Mesh build() {
            return new Mesh(fills, borders, accents);
        }

        private static Vertex center(List<Vertex> points) {
            double x = 0.0;
            double y = 0.0;
            double z = 0.0;
            for (Vertex point : points) {
                x += point.x();
                y += point.y();
                z += point.z();
            }
            double divisor = points.size();
            return new Vertex(x / divisor, y / divisor, z / divisor);
        }
    }
}