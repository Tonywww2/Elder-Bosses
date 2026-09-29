package com.tonywww.elder_bosses.client.hud;

import com.tonywww.elder_bosses.network.BossDefeatedPacket.Victory;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.UUID;

/** Tick-driven so pausing freezes the animation; world time changes cannot replay it. */
public final class BossVictoryBannerState {
    public static final int FADE_IN_TICKS = 16;
    public static final int HOLD_TICKS = 64;
    public static final int FADE_OUT_TICKS = 30;
    public static final int DURATION_TICKS = FADE_IN_TICKS + HOLD_TICKS + FADE_OUT_TICKS;
    private static final int MAX_QUEUED = 8;
    private static final int MAX_REMEMBERED = 128;
    private final ArrayDeque<Victory> pending = new ArrayDeque<>();
    private final LinkedHashSet<UUID> seen = new LinkedHashSet<>();
    private Victory current;
    private int age;
    private boolean started;

    public void offer(UUID bossUuid, Victory victory) {
        Objects.requireNonNull(bossUuid, "bossUuid");
        Objects.requireNonNull(victory, "victory");
        if (!seen.add(bossUuid)) return;
        if (seen.size() > MAX_REMEMBERED) seen.remove(seen.iterator().next());
        if (current == null) {
            current = victory;
            age = 0;
            started = true;
        } else if (pending.size() < MAX_QUEUED) {
            pending.addLast(victory);
        }
    }

    public void tick() {
        if (current != null && ++age >= DURATION_TICKS) {
            current = pending.pollFirst();
            age = 0;
            started = current != null;
        }
    }

    public boolean consumeStart() {
        boolean result = started;
        started = false;
        return result;
    }

    public Frame frame(float partialTick) {
        if (current == null) return new Frame(null, 0.0F);
        float time = age + Math.max(0.0F, Math.min(1.0F, partialTick));
        float opacity = Math.min(time / FADE_IN_TICKS,
                (DURATION_TICKS - time) / FADE_OUT_TICKS);
        opacity = Math.max(0.0F, Math.min(1.0F, opacity));
        // Smooth both edges without overshoot or a flash on the first frame.
        return new Frame(current, opacity * opacity * (3.0F - 2.0F * opacity));
    }

    public void clear() {
        pending.clear();
        seen.clear();
        current = null;
        age = 0;
        started = false;
    }

    public record Frame(Victory victory, float opacity) {
        public boolean visible() {
            return victory != null && opacity > 0.0F;
        }
    }
}
