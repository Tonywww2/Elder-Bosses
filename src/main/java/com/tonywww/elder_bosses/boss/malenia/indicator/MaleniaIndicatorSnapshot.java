package com.tonywww.elder_bosses.boss.malenia.indicator;

import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;

import java.util.Objects;

public record MaleniaIndicatorSnapshot(
        int bossEntityId,
        String indicatorId,
        String segmentId,
        MaleniaActionId actionId,
        long actionSequence,
        int segmentIndex,
        SegmentSlot slot,
        IndicatorState state,
        StyleRole styleRole,
        IndicatorPoint origin,
        double yawDegrees,
        IndicatorGeometry geometry,
        long startGameTick,
        long lockGameTick,
        long activeGameTick,
        long endGameTick
) {
    public MaleniaIndicatorSnapshot {
        if (bossEntityId < 0) {
            throw new IllegalArgumentException("bossEntityId must be non-negative");
        }
        requireIdentifier(indicatorId, "indicatorId");
        requireIdentifier(segmentId, "segmentId");
        Objects.requireNonNull(actionId, "actionId");
        if (actionSequence < 0L) {
            throw new IllegalArgumentException("actionSequence must be non-negative");
        }
        if (segmentIndex < 0) {
            throw new IllegalArgumentException("segmentIndex must be non-negative");
        }
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(styleRole, "styleRole");
        Objects.requireNonNull(origin, "origin");
        if (!Double.isFinite(yawDegrees)) {
            throw new IllegalArgumentException("yawDegrees must be finite");
        }
        Objects.requireNonNull(geometry, "geometry");
        if (startGameTick < 0L
                || lockGameTick < 0L
                || activeGameTick < 0L
                || endGameTick <= activeGameTick
                || startGameTick > activeGameTick
                || lockGameTick > activeGameTick) {
            throw new IllegalArgumentException("indicator timeline is invalid");
        }
    }

    public enum SegmentSlot {
        CURRENT,
        NEXT
    }

    public enum IndicatorState {
        TRACKING,
        LOCKED,
        IMMINENT,
        ACTIVE,
        PERSISTENT,
        EXPIRED
    }

    public enum StyleRole {
        PHYSICAL_SILVER(0xC7CDD4, false),
        SCARLET_ROT_DARK_RED(0x741B24, false),
        MOVEMENT_DASHED(0xAAB5BC, true);

        private final int rgb;
        private final boolean dashed;

        StyleRole(int rgb, boolean dashed) {
            this.rgb = rgb;
            this.dashed = dashed;
        }

        public int rgb() {
            return rgb;
        }

        public boolean dashed() {
            return dashed;
        }
    }

    private static void requireIdentifier(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[a-z0-9_:.-]+")) {
            throw new IllegalArgumentException(name + " must be a stable lowercase identifier");
        }
    }
}