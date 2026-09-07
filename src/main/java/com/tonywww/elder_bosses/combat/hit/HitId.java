package com.tonywww.elder_bosses.combat.hit;

public record HitId(long actionSequence, int segmentIndex) {
    public HitId {
        if (actionSequence < 0L) {
            throw new IllegalArgumentException("actionSequence must be non-negative");
        }
        if (segmentIndex < 0) {
            throw new IllegalArgumentException("segmentIndex must be non-negative");
        }
    }
}