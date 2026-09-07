package com.tonywww.elder_bosses.combat.action;

public record ActionWindow(
        ActionPhase phase,
        int startTickInclusive,
        int endTickExclusive,
        int stageIndex
) {
    public ActionWindow {
        if (startTickInclusive < 0 || endTickExclusive <= startTickInclusive) {
            throw new IllegalArgumentException("action window must be a non-empty forward interval");
        }
        if (stageIndex < 0) {
            throw new IllegalArgumentException("stageIndex must be non-negative");
        }
    }

    public boolean contains(int actionTick) {
        return actionTick >= startTickInclusive && actionTick < endTickExclusive;
    }
}