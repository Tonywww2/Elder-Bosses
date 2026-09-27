package com.tonywww.elder_bosses.client.audio;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntToDoubleFunction;

/** Packet-driven encounter selection, independent of the audio device and Minecraft classes. */
public final class BossMusicState {
    public static final int STALE_TICKS = 120;
    private final Map<Integer, Encounter> encounters = new HashMap<>();
    private int selected = -1;

    public void observe(int id, String boss, String phase, String state, boolean visible, float health, long tick) {
        if (!"elder_bosses:promised_consort".equals(boss)) return;
        if (!visible || health <= 0 || "dormant".equals(state) || "defeated".equals(state)) {
            remove(id);
        } else {
            int musicPhase = "phase_two".equals(phase) || "transition".equals(state) ? 2 : 1;
            encounters.put(id, new Encounter(id, musicPhase, tick));
        }
    }

    public Encounter select(long tick, IntToDoubleFunction distanceSquared, double range) {
        encounters.values().removeIf(e -> tick < e.lastSeen() || tick - e.lastSeen() > STALE_TICKS);
        double limit = range * range;
        Encounter current = encounters.get(selected);
        if (current != null && eligible(distanceSquared.applyAsDouble(selected), limit)) return current;
        Encounter best = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (Encounter encounter : encounters.values()) {
            double distance = distanceSquared.applyAsDouble(encounter.entityId());
            if (eligible(distance, limit) && (distance < nearest
                    || distance == nearest && (best == null || encounter.entityId() < best.entityId()))) {
                best = encounter;
                nearest = distance;
            }
        }
        selected = best == null ? -1 : best.entityId();
        return best;
    }

    private static boolean eligible(double distance, double limit) {
        return Double.isFinite(distance) && distance >= 0 && distance <= limit;
    }

    public void remove(int id) { encounters.remove(id); if (selected == id) selected = -1; }
    public void clear() { encounters.clear(); selected = -1; }
    public record Encounter(int entityId, int phase, long lastSeen) {}
}
