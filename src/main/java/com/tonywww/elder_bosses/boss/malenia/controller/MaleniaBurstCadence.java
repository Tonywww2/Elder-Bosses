package com.tonywww.elder_bosses.boss.malenia.controller;

/** Same burst/link/rest policy as Radahn; a cancelled action never consumes a link. */
public final class MaleniaBurstCadence {
    private int remaining;

    public void start(long seed) {
        if (remaining == 0) remaining = 2 + (int) Long.remainderUnsigned(seed, 3);
    }

    public boolean complete() {
        if (remaining == 0) return true;
        return --remaining == 0;
    }

    public boolean mayShortenRecovery() { return remaining > 1; }
    public int remaining() { return remaining; }
    public void reset() { remaining = 0; }
}
