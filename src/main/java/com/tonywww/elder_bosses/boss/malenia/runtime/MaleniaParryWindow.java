package com.tonywww.elder_bosses.boss.malenia.runtime;

/** The cue and the shield raise share this attack-relative interval, without shield startup delay. */
public record MaleniaParryWindow(int startInclusive, int endExclusive) {
    public boolean accepts(long raisedActionTick, int contactTick) {
        return raisedActionTick >= startInclusive && raisedActionTick < endExclusive
                && contactTick >= raisedActionTick && contactTick < endExclusive;
    }
}
