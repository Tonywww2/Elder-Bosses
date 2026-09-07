package com.tonywww.elder_bosses.boss.malenia.indicator;

import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.boss.malenia.indicator.MaleniaIndicatorSnapshot.IndicatorState;
import com.tonywww.elder_bosses.boss.malenia.indicator.MaleniaIndicatorSnapshot.SegmentSlot;

import java.util.List;
import java.util.Objects;

public record MaleniaIndicatorFrame(
        long serverGameTick,
        List<MaleniaIndicatorSnapshot> current,
        List<MaleniaIndicatorSnapshot> next,
        List<InstantGuardCueSnapshot> instantGuardCues
) {
    public MaleniaIndicatorFrame {
        if (serverGameTick < 0L) {
            throw new IllegalArgumentException("serverGameTick must be non-negative");
        }
        current = copySnapshots(current, SegmentSlot.CURRENT, "current");
        next = copySnapshots(next, SegmentSlot.NEXT, "next");
        instantGuardCues = List.copyOf(Objects.requireNonNull(
                instantGuardCues,
                "instantGuardCues"
        ));
        instantGuardCues.forEach(cue -> Objects.requireNonNull(cue, "instant guard cue"));
    }

    public record InstantGuardCueSnapshot(
            int bossEntityId,
            String indicatorId,
            MaleniaActionId actionId,
            long actionSequence,
            int segmentIndex,
            SegmentSlot slot,
            IndicatorState state,
            long lockGameTick,
            long activeGameTick,
            long endGameTick
    ) {
        public static final int RGB = 0xFF2020;

        public InstantGuardCueSnapshot {
            if (bossEntityId < 0) {
                throw new IllegalArgumentException("bossEntityId must be non-negative");
            }
            Objects.requireNonNull(indicatorId, "indicatorId");
            Objects.requireNonNull(actionId, "actionId");
            if (actionSequence < 0L || segmentIndex < 0) {
                throw new IllegalArgumentException("guard cue identity is invalid");
            }
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(state, "state");
            if (lockGameTick < 0L
                    || activeGameTick < 0L
                    || endGameTick <= activeGameTick
                    || lockGameTick > activeGameTick) {
                throw new IllegalArgumentException("guard cue timeline is invalid");
            }
        }
    }

    private static List<MaleniaIndicatorSnapshot> copySnapshots(
            List<MaleniaIndicatorSnapshot> snapshots,
            SegmentSlot expectedSlot,
            String name
    ) {
        List<MaleniaIndicatorSnapshot> copy = List.copyOf(Objects.requireNonNull(snapshots, name));
        for (MaleniaIndicatorSnapshot snapshot : copy) {
            Objects.requireNonNull(snapshot, name + " snapshot");
            if (snapshot.slot() != expectedSlot) {
                throw new IllegalArgumentException(name + " snapshot has the wrong slot");
            }
        }
        return copy;
    }
}