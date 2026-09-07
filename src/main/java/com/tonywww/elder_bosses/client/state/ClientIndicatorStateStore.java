package com.tonywww.elder_bosses.client.state;

import com.tonywww.elder_bosses.network.IndicatorSnapshotPacket;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ClientIndicatorStateStore {
    private static final Map<IndicatorKey, IndicatorHistory> SNAPSHOTS = new LinkedHashMap<>();
    private static int maxActiveIndicators = Integer.MAX_VALUE;

    private ClientIndicatorStateStore() {
    }

    public static boolean update(IndicatorSnapshotPacket snapshot, long gameTick) {
        Objects.requireNonNull(snapshot, "snapshot");
        removePastRetention(gameTick);
        IndicatorKey key = new IndicatorKey(snapshot.bossEntityId(), snapshot.indicatorId());
        if (gameTick >= 0L && pastRetention(snapshot, gameTick)) {
            SNAPSHOTS.remove(key);
            return false;
        }
        IndicatorHistory previousHistory = SNAPSHOTS.get(key);
        SNAPSHOTS.put(key, previousHistory == null
            ? IndicatorHistory.initial(snapshot, gameTick)
            : previousHistory.advance(snapshot, gameTick));
        trimToCapacity(gameTick);
        return SNAPSHOTS.containsKey(key);
    }

    public static void setMaxActiveIndicators(int maximum, long gameTick) {
        maxActiveIndicators = Math.max(1, maximum);
        removePastRetention(gameTick);
        trimToCapacity(gameTick);
    }

    public static Optional<IndicatorSnapshotPacket> get(int bossEntityId, String indicatorId) {
        return Optional.ofNullable(SNAPSHOTS.get(new IndicatorKey(bossEntityId, indicatorId)))
                .map(IndicatorHistory::current);
    }

    public static List<IndicatorSnapshotPacket> snapshots() {
        return SNAPSHOTS.values().stream()
                .map(IndicatorHistory::current)
                .toList();
    }

    public static List<IndicatorSnapshotPacket> activeSnapshots(long gameTick, float partialTick) {
        if (gameTick < 0L) {
            return List.of();
        }
        removePastRetention(gameTick);
        return SNAPSHOTS.values().stream()
                .filter(history -> history.current().startTick() <= gameTick)
                .map(history -> history.snapshotForRender(gameTick, partialTick))
                .sorted(Comparator
                        .comparingInt(IndicatorSnapshotPacket::bossEntityId)
                        .thenComparing(IndicatorSnapshotPacket::indicatorId))
                .toList();
    }

    public static void remove(int bossEntityId, String indicatorId) {
        SNAPSHOTS.remove(new IndicatorKey(bossEntityId, indicatorId));
    }

    public static void onTrackingEnd(int bossEntityId) {
        SNAPSHOTS.keySet().removeIf(key -> key.bossEntityId() == bossEntityId);
    }

    public static void onDimensionChanged() {
        clear();
    }

    public static void onDisconnect() {
        clear();
    }

    public static void clear() {
        SNAPSHOTS.clear();
    }

    private static void removePastRetention(long gameTick) {
        if (gameTick < 0L) {
            return;
        }
        SNAPSHOTS.entrySet().removeIf(entry -> pastRetention(entry.getValue().current(), gameTick));
    }

    private static boolean pastRetention(IndicatorSnapshotPacket snapshot, long gameTick) {
        long fadeEndTick = snapshot.endTick() > Long.MAX_VALUE
                - IndicatorSnapshotPacket.EXPIRED_FADE_TICKS
                ? Long.MAX_VALUE
                : snapshot.endTick() + IndicatorSnapshotPacket.EXPIRED_FADE_TICKS;
        return gameTick >= fadeEndTick;
    }

    private static void trimToCapacity(long gameTick) {
        Comparator<IndicatorSnapshotPacket> retentionPriority = Comparator
            .<IndicatorSnapshotPacket>comparingInt(
                snapshot -> phaseUrgency(snapshot, gameTick)
            )
                .thenComparing(Comparator.comparingLong(
                        IndicatorSnapshotPacket::activeTick
                ).reversed());
        while (SNAPSHOTS.size() > maxActiveIndicators) {
            Map.Entry<IndicatorKey, IndicatorHistory> leastUrgent = null;
            for (Map.Entry<IndicatorKey, IndicatorHistory> entry : SNAPSHOTS.entrySet()) {
                if (leastUrgent == null
                        || retentionPriority.compare(
                        entry.getValue().current(),
                        leastUrgent.getValue().current()
                ) < 0) {
                    leastUrgent = entry;
                }
            }
            if (leastUrgent == null) {
                return;
            }
            SNAPSHOTS.remove(leastUrgent.getKey());
        }
    }

    private static int phaseUrgency(IndicatorSnapshotPacket snapshot, long gameTick) {
        if (gameTick >= 0L && gameTick >= snapshot.endTick()) {
            return 1;
        }
        return switch (snapshot.state()) {
            case ACTIVE, PERSISTENT -> 5;
            case IMMINENT -> 4;
            case LOCKED -> 3;
            case TRACKING -> 2;
            case EXPIRED -> 1;
        };
    }

    private static IndicatorSnapshotPacket interpolate(
            IndicatorSnapshotPacket previous,
            IndicatorSnapshotPacket current,
            double progress
    ) {
        List<Float> ranges = java.util.stream.IntStream.range(0, current.ranges().size())
                .mapToObj(index -> (float) lerp(
                        previous.ranges().get(index),
                        current.ranges().get(index),
                        progress
                ))
                .toList();
        List<IndicatorSnapshotPacket.Point> pathPoints = java.util.stream.IntStream
                .range(0, current.pathPoints().size())
                .mapToObj(index -> interpolatePoint(
                        previous.pathPoints().get(index),
                        current.pathPoints().get(index),
                        progress
                ))
                .toList();
        return new IndicatorSnapshotPacket(
                current.bossEntityId(),
                current.indicatorId(),
                current.slot(),
                current.styleRole(),
                current.semantic(),
                current.state(),
                current.shapeType(),
                interpolatePoint(previous.anchor(), current.anchor(), progress),
                interpolateYaw(previous.directionYawDegrees(), current.directionYawDegrees(), progress),
                ranges,
                pathPoints,
                current.startTick(),
                current.lockTick(),
                current.activeTick(),
                current.endTick(),
                current.instantGuardCue(),
                current.cuePulseCount(),
                current.cueRgb()
        );
    }

    private static IndicatorSnapshotPacket.Point interpolatePoint(
            IndicatorSnapshotPacket.Point previous,
            IndicatorSnapshotPacket.Point current,
            double progress
    ) {
        return new IndicatorSnapshotPacket.Point(
                lerp(previous.x(), current.x(), progress),
                lerp(previous.y(), current.y(), progress),
                lerp(previous.z(), current.z(), progress)
        );
    }

    private static float interpolateYaw(float previous, float current, double progress) {
        double delta = (current - previous) % 360.0;
        if (delta > 180.0) {
            delta -= 360.0;
        } else if (delta < -180.0) {
            delta += 360.0;
        }
        return (float) (previous + delta * progress);
    }

    private static double lerp(double previous, double current, double progress) {
        return previous + (current - previous) * progress;
    }

    private record IndicatorHistory(
            IndicatorSnapshotPacket previous,
            long previousReceivedTick,
            IndicatorSnapshotPacket current,
            long currentReceivedTick
    ) {
        private static IndicatorHistory initial(IndicatorSnapshotPacket snapshot, long gameTick) {
            return new IndicatorHistory(null, gameTick, snapshot, gameTick);
        }

        private IndicatorHistory advance(IndicatorSnapshotPacket snapshot, long gameTick) {
            return new IndicatorHistory(current, currentReceivedTick, snapshot, gameTick);
        }

        private IndicatorSnapshotPacket snapshotForRender(long gameTick, float partialTick) {
            if (gameTick >= current.endTick() || !canInterpolate()) {
                return current;
            }
            long intervalTicks = currentReceivedTick - previousReceivedTick;
            double renderTick = gameTick + Math.max(0.0F, Math.min(1.0F, partialTick));
            double progress = Math.max(
                    0.0,
                    Math.min(1.0, (renderTick - currentReceivedTick) / intervalTicks)
            );
            if (progress >= 1.0) {
                return current;
            }
            return interpolate(previous, current, progress);
        }

        private boolean canInterpolate() {
            return previous != null
                    && previousReceivedTick >= 0L
                    && currentReceivedTick > previousReceivedTick
                    && previous.state() == IndicatorSnapshotPacket.IndicatorState.TRACKING
                    && current.state() == IndicatorSnapshotPacket.IndicatorState.TRACKING
                    && previous.shapeType() == current.shapeType()
                    && previous.ranges().size() == current.ranges().size()
                    && previous.pathPoints().size() == current.pathPoints().size();
        }
    }

    private record IndicatorKey(int bossEntityId, String indicatorId) {
        private IndicatorKey {
            Objects.requireNonNull(indicatorId, "indicatorId");
        }
    }
}