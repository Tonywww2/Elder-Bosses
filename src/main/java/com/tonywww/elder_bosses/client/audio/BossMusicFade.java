package com.tonywww.elder_bosses.client.audio;

/** Tick-based linear envelope; configured duration is independent of the music volume. */
public final class BossMusicFade {
    private float gain;
    private int duration;
    private float fadeOutStep;
    private boolean retiring;

    public BossMusicFade(int ticks) { duration = Math.max(1, ticks); }
    public void retire(int ticks) {
        retiring = true;
        fadeOutStep = gain / Math.max(1, ticks);
    }
    public float tick(float volume) {
        gain = retiring ? Math.max(0, gain - fadeOutStep) : Math.min(1, gain + 1.0F / duration);
        if (retiring && gain < 0.00001F) gain = 0;
        return gain * volume;
    }
    public boolean finished() { return retiring && gain == 0; }
}
