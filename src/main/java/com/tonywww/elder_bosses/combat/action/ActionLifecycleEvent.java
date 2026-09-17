package com.tonywww.elder_bosses.combat.action;

import java.util.UUID;

public record ActionLifecycleEvent(
        String actionId,
        long sequence,
        long startGameTick,
        long gameTick,
        long seed,
        UUID targetId,
        ActionTimeline timeline,
        Outcome outcome
) {
    public long elapsedTicks() {
        return gameTick - startGameTick;
    }

    public enum Outcome {
        STARTED,
        COMPLETED,
        CANCELLED,
        REPLACED
    }
}