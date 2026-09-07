package com.tonywww.elder_bosses.boss.malenia.runtime;

import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.combat.action.ActionPhase;
import com.tonywww.elder_bosses.combat.action.ActionWindow;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record MaleniaActionSnapshot(
        MaleniaActionId actionId,
        long sequence,
        long startGameTick,
        long seed,
        Optional<UUID> targetId,
        int actionTick,
        ActionWindow window
) {
    public MaleniaActionSnapshot {
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(window, "window");
        if (sequence < 0L) {
            throw new IllegalArgumentException("sequence must be non-negative");
        }
        if (startGameTick < 0L) {
            throw new IllegalArgumentException("startGameTick must be non-negative");
        }
        if (!window.contains(actionTick)) {
            throw new IllegalArgumentException("window must contain actionTick");
        }
    }

    public ActionPhase phase() {
        return window.phase();
    }
}