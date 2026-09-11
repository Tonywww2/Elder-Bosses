package com.tonywww.elder_bosses.boss.malenia.sync;

import com.tonywww.elder_bosses.boss.malenia.indicator.IndicatorGeometry;
import com.tonywww.elder_bosses.boss.malenia.indicator.MaleniaIndicatorFrame;
import com.tonywww.elder_bosses.boss.malenia.indicator.MaleniaIndicatorSnapshot;
import com.tonywww.elder_bosses.network.IndicatorSnapshotPacket;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class MaleniaIndicatorPacketMapper {
    private MaleniaIndicatorPacketMapper() {
    }

    public static List<IndicatorSnapshotPacket> toPackets(
            MaleniaIndicatorFrame frame,
            int cuePulseCount,
            int cueRgb,
            int cueLeadTicks
    ) {
        Objects.requireNonNull(frame, "frame");
        if (cuePulseCount < 1 || cuePulseCount > IndicatorSnapshotPacket.MAX_CUE_PULSE_COUNT) {
            throw new IllegalArgumentException("cuePulseCount is outside the supported range");
        }
        if (cueRgb < 0 || cueRgb > 0xFFFFFF) {
            throw new IllegalArgumentException("cueRgb must be a 24-bit RGB color");
        }
        if (cueLeadTicks < 0) {
            throw new IllegalArgumentException("cueLeadTicks must be non-negative");
        }
        Set<String> cueIds = new HashSet<>();
        for (MaleniaIndicatorFrame.InstantGuardCueSnapshot cue : frame.instantGuardCues()) {
            cueIds.add(cue.indicatorId().replace(".instant_guard", ""));
        }
        List<IndicatorSnapshotPacket> packets = new ArrayList<>(
                frame.current().size() + frame.next().size()
        );
        appendPackets(packets, frame.current(), cueIds, cuePulseCount, cueRgb, cueLeadTicks);
        appendPackets(packets, frame.next(), cueIds, cuePulseCount, cueRgb, cueLeadTicks);
        return List.copyOf(packets);
    }

    public static int parseHexColor(String value) {
        Objects.requireNonNull(value, "value");
        String normalized = value.trim();
        if (!normalized.matches("#[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("color must use #RRGGBB format");
        }
        return Integer.parseInt(normalized.substring(1), 16);
    }

    private static void appendPackets(
            List<IndicatorSnapshotPacket> packets,
            List<MaleniaIndicatorSnapshot> snapshots,
            Set<String> cueIds,
            int cuePulseCount,
            int cueRgb,
            int cueLeadTicks
    ) {
        for (MaleniaIndicatorSnapshot snapshot : snapshots) {
            packets.add(toPacket(
                    snapshot,
                    cueIds.contains(snapshot.indicatorId()),
                    cuePulseCount,
                    cueRgb,
                    cueLeadTicks
            ));
        }
    }

    private static IndicatorSnapshotPacket toPacket(
            MaleniaIndicatorSnapshot snapshot,
            boolean instantGuardCue,
            int cuePulseCount,
            int cueRgb,
            int cueLeadTicks
    ) {
        GeometryPacket geometry = geometryPacket(snapshot.geometry());
        long lockTick = instantGuardCue
                ? Math.max(snapshot.startGameTick(), snapshot.activeGameTick() - cueLeadTicks)
            : Math.max(snapshot.startGameTick(), snapshot.lockGameTick());
        return new IndicatorSnapshotPacket(
                snapshot.bossEntityId(),
                snapshot.indicatorId(),
                mapPacketSlot(snapshot.slot()),
                mapPacketStyle(snapshot.styleRole()),
                semanticFor(snapshot.styleRole()),
                mapPacketState(snapshot.state()),
                geometry.shapeType(),
                new IndicatorSnapshotPacket.Point(
                        snapshot.origin().x(),
                        snapshot.origin().y(),
                        snapshot.origin().z()
                ),
                (float) snapshot.yawDegrees(),
                geometry.ranges(),
                geometry.pathPoints(),
                snapshot.startGameTick(),
                lockTick,
                snapshot.activeGameTick(),
                snapshot.endGameTick(),
                instantGuardCue,
                instantGuardCue ? cuePulseCount : 0,
                instantGuardCue ? cueRgb : 0
        );
    }

    private static GeometryPacket geometryPacket(IndicatorGeometry geometry) {
        if (geometry instanceof IndicatorGeometry.Sector sector) {
            return new GeometryPacket(
                    IndicatorSnapshotPacket.ShapeType.SECTOR,
                    List.of((float) sector.radius(), (float) sector.arcDegrees()),
                    List.of()
            );
        }
        if (geometry instanceof IndicatorGeometry.Capsule capsule) {
            return new GeometryPacket(
                    IndicatorSnapshotPacket.ShapeType.CAPSULE,
                    List.of((float) capsule.length(), (float) capsule.width()),
                    List.of()
            );
        }
        if (geometry instanceof IndicatorGeometry.Circle circle) {
            return new GeometryPacket(
                    IndicatorSnapshotPacket.ShapeType.CIRCLE,
                    List.of((float) circle.radius()),
                    List.of()
            );
        }
        if (geometry instanceof IndicatorGeometry.Annulus annulus) {
            return new GeometryPacket(
                    IndicatorSnapshotPacket.ShapeType.ANNULUS,
                    List.of((float) annulus.innerRadius(), (float) annulus.outerRadius()),
                    List.of()
            );
        }
        if (geometry instanceof IndicatorGeometry.Path path) {
            return new GeometryPacket(
                    IndicatorSnapshotPacket.ShapeType.PATH,
                    List.of((float) path.width()),
                    path.points().stream()
                            .map(point -> new IndicatorSnapshotPacket.Point(
                                    point.x(), point.y(), point.z()
                            ))
                            .toList()
            );
        }
        if (geometry instanceof IndicatorGeometry.Zone zone) {
            return new GeometryPacket(
                    IndicatorSnapshotPacket.ShapeType.ZONE,
                    List.of((float) zone.radius()),
                    List.of()
            );
        }
        throw new IllegalArgumentException("unsupported indicator geometry: " + geometry);
    }

    private static IndicatorSnapshotPacket.SegmentSlot mapPacketSlot(
            MaleniaIndicatorSnapshot.SegmentSlot slot
    ) {
        return switch (slot) {
            case CURRENT -> IndicatorSnapshotPacket.SegmentSlot.CURRENT;
            case NEXT -> IndicatorSnapshotPacket.SegmentSlot.NEXT;
        };
    }

    private static IndicatorSnapshotPacket.StyleRole mapPacketStyle(
            MaleniaIndicatorSnapshot.StyleRole styleRole
    ) {
        return switch (styleRole) {
            case PHYSICAL_SILVER -> IndicatorSnapshotPacket.StyleRole.PHYSICAL_SILVER;
            case SCARLET_ROT_DARK_RED -> IndicatorSnapshotPacket.StyleRole.SCARLET_ROT_DARK_RED;
            case MOVEMENT_DASHED -> IndicatorSnapshotPacket.StyleRole.MOVEMENT_DASHED;
        };
    }

    private static IndicatorSnapshotPacket.Semantic semanticFor(
            MaleniaIndicatorSnapshot.StyleRole styleRole
    ) {
        return switch (styleRole) {
            case PHYSICAL_SILVER -> IndicatorSnapshotPacket.Semantic.PHYSICAL;
            case SCARLET_ROT_DARK_RED -> IndicatorSnapshotPacket.Semantic.SCARLET_ROT;
            case MOVEMENT_DASHED -> IndicatorSnapshotPacket.Semantic.MOVEMENT;
        };
    }

    private static IndicatorSnapshotPacket.IndicatorState mapPacketState(
            MaleniaIndicatorSnapshot.IndicatorState state
    ) {
        return switch (state) {
            case TRACKING -> IndicatorSnapshotPacket.IndicatorState.TRACKING;
            case LOCKED -> IndicatorSnapshotPacket.IndicatorState.LOCKED;
            case IMMINENT -> IndicatorSnapshotPacket.IndicatorState.IMMINENT;
            case ACTIVE -> IndicatorSnapshotPacket.IndicatorState.ACTIVE;
            case PERSISTENT -> IndicatorSnapshotPacket.IndicatorState.PERSISTENT;
            case EXPIRED -> IndicatorSnapshotPacket.IndicatorState.EXPIRED;
        };
    }

    private record GeometryPacket(
            IndicatorSnapshotPacket.ShapeType shapeType,
            List<Float> ranges,
            List<IndicatorSnapshotPacket.Point> pathPoints
    ) {
    }
}