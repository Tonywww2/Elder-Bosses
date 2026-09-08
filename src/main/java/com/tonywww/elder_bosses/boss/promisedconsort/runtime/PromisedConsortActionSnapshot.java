package com.tonywww.elder_bosses.boss.promisedconsort.runtime;

import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase;
import com.tonywww.elder_bosses.combat.action.ActionPhase;

import java.util.Objects;
import java.util.UUID;

public record PromisedConsortActionSnapshot(
        PromisedConsortActionId actionId,
        PromisedConsortPhase phase,
        long sequence,
        long startGameTick,
        int actionTick,
        ActionPhase actionPhase,
        int stageIndex,
        int phaseTick,
        long seed,
        UUID targetId
) {
    public PromisedConsortActionSnapshot {
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(actionPhase, "actionPhase");
        if (sequence < 0L || startGameTick < 0L || actionTick < 0 || stageIndex < 0
                || phaseTick < 0) {
            throw new IllegalArgumentException("action snapshot counters must be non-negative");
        }
    }
}
