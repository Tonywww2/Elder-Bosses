package com.tonywww.elder_bosses.combat.action;

public record ActionStage(int windupTicks, int activeTicks, int recoveryTicks) {
    public ActionStage {
        if (windupTicks < 0 || activeTicks < 0 || recoveryTicks < 0) {
            throw new IllegalArgumentException("action stage durations must be non-negative");
        }
        if (windupTicks + activeTicks + recoveryTicks == 0) {
            throw new IllegalArgumentException("action stage must contain at least one tick");
        }
    }

    public int totalTicks() {
        return Math.addExact(Math.addExact(windupTicks, activeTicks), recoveryTicks);
    }
}