package com.tonywww.elder_bosses.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record IndicatorSnapshotPacket(
        int bossEntityId,
        String indicatorId,
        SegmentSlot slot,
        StyleRole styleRole,
        Semantic semantic,
        IndicatorState state,
        ShapeType shapeType,
        Point anchor,
        float directionYawDegrees,
        List<Float> ranges,
        List<Point> pathPoints,
        long startTick,
        long lockTick,
        long activeTick,
        long endTick,
        boolean instantGuardCue,
        int cuePulseCount,
        int cueRgb
) {
    public static final int MAX_INDICATOR_ID_LENGTH = 128;
    public static final int MAX_RANGES = 8;
    public static final int MAX_PATH_POINTS = 96;
    public static final int MAX_CUE_PULSE_COUNT = 64;
    public static final int DEFAULT_CUE_PULSE_COUNT = 3;
    public static final int DEFAULT_CUE_RGB = 0xFF2020;
    public static final int EXPIRED_FADE_TICKS = 3;

    public IndicatorSnapshotPacket {
        if (bossEntityId < 0) {
            throw new IllegalArgumentException("bossEntityId must be non-negative");
        }
        Objects.requireNonNull(indicatorId, "indicatorId");
        if (indicatorId.isEmpty()
                || indicatorId.length() > MAX_INDICATOR_ID_LENGTH
                || !indicatorId.matches("[a-z0-9_:.-]+")) {
            throw new IllegalArgumentException("indicatorId must be a stable lowercase identifier");
        }
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(styleRole, "styleRole");
        Objects.requireNonNull(semantic, "semantic");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(shapeType, "shapeType");
        Objects.requireNonNull(anchor, "anchor");
        if (!Float.isFinite(directionYawDegrees)) {
            throw new IllegalArgumentException("directionYawDegrees must be finite");
        }
        ranges = List.copyOf(Objects.requireNonNull(ranges, "ranges"));
        pathPoints = List.copyOf(Objects.requireNonNull(pathPoints, "pathPoints"));
        validateRanges(shapeType, ranges);
        validatePath(shapeType, pathPoints);
        if (startTick < 0L || startTick > lockTick || lockTick > activeTick || activeTick > endTick) {
            throw new IllegalArgumentException("indicator timing must be non-negative and ordered");
        }
        if (instantGuardCue) {
            if (cuePulseCount < 1 || cuePulseCount > MAX_CUE_PULSE_COUNT) {
                throw new IllegalArgumentException("cuePulseCount is outside the supported range");
            }
            if (cueRgb < 0 || cueRgb > 0xFFFFFF) {
                throw new IllegalArgumentException("cueRgb must be a 24-bit RGB color");
            }
        } else if (cuePulseCount != 0 || cueRgb != 0) {
            throw new IllegalArgumentException("cue fields must be zero when no cue is present");
        }
    }

    public IndicatorSnapshotPacket(
            int bossEntityId,
            String indicatorId,
            SegmentSlot slot,
            StyleRole styleRole,
            Semantic semantic,
            IndicatorState state,
            ShapeType shapeType,
            Point anchor,
            float directionYawDegrees,
            List<Float> ranges,
            List<Point> pathPoints,
            long startTick,
            long lockTick,
            long activeTick,
            long endTick,
            boolean instantGuardCue
    ) {
        this(
                bossEntityId,
                indicatorId,
                slot,
                styleRole,
                semantic,
                state,
                shapeType,
                anchor,
                directionYawDegrees,
                ranges,
                pathPoints,
                startTick,
                lockTick,
                activeTick,
                endTick,
                instantGuardCue,
                instantGuardCue ? DEFAULT_CUE_PULSE_COUNT : 0,
                instantGuardCue ? DEFAULT_CUE_RGB : 0
        );
    }

    public void write(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeVarInt(bossEntityId);
        buffer.writeUtf(indicatorId, MAX_INDICATOR_ID_LENGTH);
        buffer.writeVarInt(slot.id());
        buffer.writeVarInt(styleRole.id());
        buffer.writeVarInt(semantic.id());
        buffer.writeVarInt(state.id());
        buffer.writeVarInt(shapeType.id());
        anchor.write(buffer);
        buffer.writeFloat(directionYawDegrees);
        buffer.writeVarInt(ranges.size());
        for (float range : ranges) {
            buffer.writeFloat(range);
        }
        buffer.writeVarInt(pathPoints.size());
        for (Point point : pathPoints) {
            point.write(buffer);
        }
        buffer.writeLong(startTick);
        buffer.writeLong(lockTick);
        buffer.writeLong(activeTick);
        buffer.writeLong(endTick);
        buffer.writeBoolean(instantGuardCue);
        buffer.writeVarInt(cuePulseCount);
        buffer.writeVarInt(cueRgb);
    }

    public static IndicatorSnapshotPacket read(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        int bossEntityId = buffer.readVarInt();
        String indicatorId = buffer.readUtf(MAX_INDICATOR_ID_LENGTH);
        SegmentSlot slot = SegmentSlot.fromId(buffer.readVarInt());
        StyleRole styleRole = StyleRole.fromId(buffer.readVarInt());
        Semantic semantic = Semantic.fromId(buffer.readVarInt());
        IndicatorState state = IndicatorState.fromId(buffer.readVarInt());
        ShapeType shapeType = ShapeType.fromId(buffer.readVarInt());
        Point anchor = Point.read(buffer);
        float directionYawDegrees = buffer.readFloat();
        int rangeCount = readCount(buffer, MAX_RANGES, "ranges");
        List<Float> ranges = new ArrayList<>(rangeCount);
        for (int index = 0; index < rangeCount; index++) {
            ranges.add(buffer.readFloat());
        }
        int pathPointCount = readCount(buffer, MAX_PATH_POINTS, "pathPoints");
        List<Point> pathPoints = new ArrayList<>(pathPointCount);
        for (int index = 0; index < pathPointCount; index++) {
            pathPoints.add(Point.read(buffer));
        }
        long startTick = buffer.readLong();
        long lockTick = buffer.readLong();
        long activeTick = buffer.readLong();
        long endTick = buffer.readLong();
        boolean instantGuardCue = buffer.readBoolean();
        int cuePulseCount = buffer.readVarInt();
        int cueRgb = buffer.readVarInt();
        return new IndicatorSnapshotPacket(
                bossEntityId,
                indicatorId,
            slot,
            styleRole,
            semantic,
            state,
                shapeType,
                anchor,
                directionYawDegrees,
                ranges,
                pathPoints,
                startTick,
                lockTick,
                activeTick,
                endTick,
                instantGuardCue,
                cuePulseCount,
                cueRgb
        );
    }

    private static int readCount(FriendlyByteBuf buffer, int maximum, String name) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(name + " count is outside the supported range: " + count);
        }
        return count;
    }

    private static void validateRanges(ShapeType shapeType, List<Float> ranges) {
        if (ranges.size() > MAX_RANGES || ranges.size() != shapeType.rangeCount()) {
            throw new IllegalArgumentException(
                    shapeType + " requires exactly " + shapeType.rangeCount() + " range values"
            );
        }
        for (int index = 0; index < ranges.size(); index++) {
            Float range = ranges.get(index);
            boolean mayBeZero = shapeType == ShapeType.PATH
                    || shapeType == ShapeType.ANNULUS && index == 0;
            if (range == null
                    || !Float.isFinite(range)
                    || mayBeZero && range < 0.0F
                    || !mayBeZero && range <= 0.0F) {
                throw new IllegalArgumentException("ranges contain an invalid value for " + shapeType);
            }
        }
        if (shapeType == ShapeType.SECTOR && ranges.get(1) > 360.0F) {
            throw new IllegalArgumentException("sector angle must not exceed 360 degrees");
        }
        if (shapeType == ShapeType.ANNULUS && ranges.get(0) > ranges.get(1)) {
            throw new IllegalArgumentException("annulus inner radius must not exceed outer radius");
        }
    }

    private static void validatePath(ShapeType shapeType, List<Point> pathPoints) {
        if (pathPoints.size() > MAX_PATH_POINTS) {
            throw new IllegalArgumentException("pathPoints exceeds " + MAX_PATH_POINTS);
        }
        if (shapeType == ShapeType.PATH) {
            if (pathPoints.size() < 2) {
                throw new IllegalArgumentException("PATH requires at least two pathPoints");
            }
        } else if (!pathPoints.isEmpty()) {
            throw new IllegalArgumentException("pathPoints are only valid for PATH indicators");
        }
    }

    public enum ShapeType {
        SECTOR(0, 2),
        CAPSULE(1, 2),
        CIRCLE(2, 1),
        ANNULUS(3, 2),
        PATH(4, 1),
        ZONE(5, 1);

        private final int id;
        private final int rangeCount;

        ShapeType(int id, int rangeCount) {
            this.id = id;
            this.rangeCount = rangeCount;
        }

        public int id() {
            return id;
        }

        public int rangeCount() {
            return rangeCount;
        }

        private static ShapeType fromId(int id) {
            for (ShapeType value : values()) {
                if (value.id == id) {
                    return value;
                }
            }
            throw new IllegalArgumentException("unknown indicator shape id: " + id);
        }
    }

    public enum SegmentSlot {
        CURRENT(0),
        NEXT(1);

        private final int id;

        SegmentSlot(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        private static SegmentSlot fromId(int id) {
            for (SegmentSlot value : values()) {
                if (value.id == id) {
                    return value;
                }
            }
            throw new IllegalArgumentException("unknown indicator slot id: " + id);
        }
    }

    public enum StyleRole {
        PHYSICAL_SILVER(0, 0xC7CDD4, false),
        SCARLET_ROT_DARK_RED(1, 0x741B24, false),
        MOVEMENT_DASHED(2, 0xAAB5BC, true);

        private final int id;
        private final int rgb;
        private final boolean dashed;

        StyleRole(int id, int rgb, boolean dashed) {
            this.id = id;
            this.rgb = rgb;
            this.dashed = dashed;
        }

        public int id() {
            return id;
        }

        public int rgb() {
            return rgb;
        }

        public boolean dashed() {
            return dashed;
        }

        private static StyleRole fromId(int id) {
            for (StyleRole value : values()) {
                if (value.id == id) {
                    return value;
                }
            }
            throw new IllegalArgumentException("unknown indicator style role id: " + id);
        }
    }

    public enum IndicatorState {
        TRACKING(0),
        LOCKED(1),
        IMMINENT(2),
        ACTIVE(3),
        PERSISTENT(4),
        EXPIRED(5);

        private final int id;

        IndicatorState(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        private static IndicatorState fromId(int id) {
            for (IndicatorState value : values()) {
                if (value.id == id) {
                    return value;
                }
            }
            throw new IllegalArgumentException("unknown indicator state id: " + id);
        }
    }

    public enum Semantic {
        PHYSICAL(0),
        GRAVITY(1),
        BLOODFLAME(2),
        HOLY(3),
        SCARLET_ROT(4),
        MOVEMENT(5);

        private final int id;

        Semantic(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        private static Semantic fromId(int id) {
            for (Semantic value : values()) {
                if (value.id == id) {
                    return value;
                }
            }
            throw new IllegalArgumentException("unknown indicator semantic id: " + id);
        }
    }

    public record Point(double x, double y, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("point coordinates must be finite");
            }
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeDouble(x);
            buffer.writeDouble(y);
            buffer.writeDouble(z);
        }

        private static Point read(FriendlyByteBuf buffer) {
            return new Point(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        }
    }
}