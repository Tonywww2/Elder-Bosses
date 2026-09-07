package com.tonywww.elder_bosses.boss.malenia.runtime;

import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaPhase;

import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

public record MaleniaCooldownSnapshot(
        Map<MaleniaActionId, Integer> actionRemainingTicks,
        int highThreatRemainingTicks,
        Map<MaleniaPhase, Integer> waterfowlPhaseGateRemainingTicks,
        OptionalInt phaseTwoOpeningRemainingTicks,
        boolean phaseOneWaterfowlStarted
) {
    public MaleniaCooldownSnapshot {
        actionRemainingTicks = immutableNonNegativeMap(actionRemainingTicks, "actionRemainingTicks");
        waterfowlPhaseGateRemainingTicks = immutableNonNegativeMap(
                waterfowlPhaseGateRemainingTicks,
                "waterfowlPhaseGateRemainingTicks"
        );
        Objects.requireNonNull(phaseTwoOpeningRemainingTicks, "phaseTwoOpeningRemainingTicks");
        if (highThreatRemainingTicks < 0) {
            throw new IllegalArgumentException("highThreatRemainingTicks must be non-negative");
        }
        if (phaseTwoOpeningRemainingTicks.isPresent()
                && phaseTwoOpeningRemainingTicks.getAsInt() < 0) {
            throw new IllegalArgumentException("phaseTwoOpeningRemainingTicks must be non-negative");
        }
    }

    private static <K> Map<K, Integer> immutableNonNegativeMap(Map<K, Integer> source, String name) {
        Objects.requireNonNull(source, name);
        for (Map.Entry<K, Integer> entry : source.entrySet()) {
            Objects.requireNonNull(entry.getKey(), name + " key");
            Integer remainingTicks = Objects.requireNonNull(entry.getValue(), name + " value");
            if (remainingTicks < 0) {
                throw new IllegalArgumentException(name + " values must be non-negative");
            }
        }
        return Map.copyOf(source);
    }
}