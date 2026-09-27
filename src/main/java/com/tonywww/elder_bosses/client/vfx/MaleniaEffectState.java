package com.tonywww.elder_bosses.client.vfx;

import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.network.IndicatorSnapshotPacket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Receives authoritative geometry independently of the optional indicator renderer. */
public final class MaleniaEffectState {
    private static final int MAX_SEGMENTS = 256;
    private final Map<Key, Segment> segments = new LinkedHashMap<>();
    private final Map<Integer, Long> sequences = new java.util.HashMap<>();

    public void observe(IndicatorSnapshotPacket packet, long now) {
        Identity identity = identity(packet.indicatorId());
        if (identity == null || identity.segment().contains(".") || now < 0) return;
        Key key = new Key(packet.bossEntityId(), packet.indicatorId());
        Segment previous = segments.get(key);
        if (!persistent(packet) && (previous == null || !previous.persistent())
                && identity.sequence() < sequences.getOrDefault(packet.bossEntityId(), -1L)) return;
        if (packet.state() == IndicatorSnapshotPacket.IndicatorState.EXPIRED) {
            // Cancellation must not turn a preview into an impact. Natural expiry retains only a short visual tail.
            if (previous == null || packet.endTick() < previous.packet().activeTick() || previous.persistent()) segments.remove(key);
            else segments.put(key, new Segment(previous.packet(), identity, Math.min(previous.expires(), packet.endTick() + 6)));
        } else {
            segments.put(key, new Segment(packet, identity, packet.endTick() + (persistent(packet) ? 0 : 6)));
        }
        prune(now);
        while (segments.size() > MAX_SEGMENTS) segments.remove(segments.keySet().iterator().next());
    }

    public void action(int bossId, long sequence) {
        if (sequence >= 0) sequences.merge(bossId, sequence, Math::max);
        // Geometry is sent before the combat snapshot. Do not discard the next action's early packets.
        segments.entrySet().removeIf(entry -> entry.getKey().bossId() == bossId && !entry.getValue().persistent()
                && (sequence < 0 || entry.getValue().identity().sequence() < sequence));
    }

    public void prune(long now) { segments.values().removeIf(segment -> now >= segment.expires()); }
    public List<Segment> segments() { return List.copyOf(segments.values()); }
    public void remove(int bossId) { segments.keySet().removeIf(key -> key.bossId() == bossId); sequences.remove(bossId); }
    public void clear() { segments.clear(); sequences.clear(); }

    private static boolean persistent(IndicatorSnapshotPacket packet) {
        return packet.state() == IndicatorSnapshotPacket.IndicatorState.PERSISTENT;
    }

    public static Identity identity(String id) {
        String[] parts = id.split(":", 3);
        if (parts.length != 3) return null;
        try {
            long sequence = Long.parseLong(parts[0]);
            MaleniaActionId action = java.util.Arrays.stream(MaleniaActionId.values()).filter(value -> value.serializedName().equals(parts[1])).findFirst().orElse(null);
            return sequence < 0 || action == null ? null : new Identity(sequence, action, parts[2]);
        } catch (NumberFormatException ignored) { return null; }
    }

    private record Key(int bossId, String id) {}
    public record Identity(long sequence, MaleniaActionId action, String segment) {}
    public record Segment(IndicatorSnapshotPacket packet, Identity identity, long expires) {
        public boolean persistent() { return MaleniaEffectState.persistent(packet); }
    }
}
