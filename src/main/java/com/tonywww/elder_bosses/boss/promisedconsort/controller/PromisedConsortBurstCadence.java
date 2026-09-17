package com.tonywww.elder_bosses.boss.promisedconsort.controller;

import java.util.Objects;
import java.util.Random;

public final class PromisedConsortBurstCadence {
    private final Settings settings;
    private int remaining;

    public PromisedConsortBurstCadence(Settings settings) {
        this.settings = Objects.requireNonNull(settings);
    }

    public void start(long seed) {
        if (remaining == 0) remaining = sample(seed, settings.minimumSkills(), settings.maximumSkills());
    }

    public Delay complete(long seed) {
        if (remaining <= 0) throw new IllegalStateException("No active attack burst");
        remaining--;
        boolean breathing = remaining == 0;
        return new Delay(breathing
                ? sample(seed, settings.minimumRestTicks(), settings.maximumRestTicks())
                : sample(seed, settings.minimumLinkTicks(), settings.maximumLinkTicks()), breathing);
    }

    public int remaining() {
        return remaining;
    }

    public boolean mayShortenRecovery() {
        return remaining > 1;
    }

    public void reset() {
        remaining = 0;
    }

    public void restore(int savedRemaining) {
        if (savedRemaining < 0 || savedRemaining > settings.maximumSkills()) throw new IllegalArgumentException("Invalid saved burst count");
        remaining = savedRemaining;
    }

    private static int sample(long seed, int minimum, int maximum) {
        return minimum + new Random(seed).nextInt(maximum - minimum + 1);
    }

    public record Delay(int ticks, boolean breathing) {
        public int scaled(double pursuitMultiplier, boolean rangedTarget) {
            if (!Double.isFinite(pursuitMultiplier) || pursuitMultiplier < 0 || pursuitMultiplier > 16) {
                throw new IllegalArgumentException("Invalid pursuit multiplier");
            }
            return breathing || !rangedTarget ? ticks : (int) Math.ceil(ticks * pursuitMultiplier);
        }
    }

    public record Settings(int minimumSkills, int maximumSkills, int minimumLinkTicks, int maximumLinkTicks,
                           int minimumRestTicks, int maximumRestTicks, int chainRecoveryTicks) {
        public Settings {
            if (minimumSkills < 2 || maximumSkills < minimumSkills || maximumSkills > 16
                    || minimumLinkTicks < 0 || maximumLinkTicks < minimumLinkTicks || maximumLinkTicks > 200
                    || minimumRestTicks < 1 || maximumRestTicks < minimumRestTicks || maximumRestTicks > 1200
                    || chainRecoveryTicks < 1 || chainRecoveryTicks > 200) {
                throw new IllegalArgumentException("Invalid attack burst settings");
            }
        }

        public static Settings defaults() {
            return new Settings(2, 4, 0, 2, 24, 36, 6);
        }
    }
}