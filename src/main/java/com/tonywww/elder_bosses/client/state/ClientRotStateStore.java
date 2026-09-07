package com.tonywww.elder_bosses.client.state;

import com.tonywww.elder_bosses.network.PlayerRotSnapshotPacket;
import com.tonywww.elder_bosses.player.PlayerRotSnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.Minecraft;

public final class ClientRotStateStore {
    private static final double EMPTY_CAPACITY = 100.0;
    private static final Map<Integer, PlayerRotSnapshotPacket> SNAPSHOTS = new HashMap<>();
    private static Snapshot hudSnapshot = Snapshot.empty(0L);

    private ClientRotStateStore() {
    }

    public static Snapshot snapshot() {
        return hudSnapshot;
    }

    public static void update(PlayerRotSnapshot state) {
        Objects.requireNonNull(state, "state");
        updateHudSnapshot(hudSnapshot.playerEntityId(), state);
    }

    private static void updateHudSnapshot(int playerEntityId, PlayerRotSnapshot state) {
        int previousDurationTicks = hudSnapshot.playerEntityId() == playerEntityId
            ? hudSnapshot.activeDurationTicks()
            : 0;
        int activeDurationTicks = state.active()
            ? Math.max(state.remainingActiveTicks(), previousDurationTicks)
                : 0;
        hudSnapshot = new Snapshot(playerEntityId, state, activeDurationTicks);
    }

    public static void update(PlayerRotSnapshotPacket snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || snapshot.playerEntityId() != minecraft.player.getId()) {
            return;
        }
        SNAPSHOTS.put(snapshot.playerEntityId(), snapshot);
        updateHudSnapshot(snapshot.playerEntityId(), snapshot.snapshot());
    }

    public static Optional<PlayerRotSnapshotPacket> get(int playerEntityId) {
        return Optional.ofNullable(SNAPSHOTS.get(playerEntityId));
    }

    public static List<PlayerRotSnapshotPacket> snapshots() {
        return List.copyOf(SNAPSHOTS.values());
    }

    public static void onTrackingEnd(int playerEntityId) {
        SNAPSHOTS.remove(playerEntityId);
        if (hudSnapshot.playerEntityId() == playerEntityId) {
            hudSnapshot = Snapshot.empty(hudSnapshot.state().gameTick());
        }
    }

    public static void onDimensionChanged() {
        clear();
    }

    public static void onDisconnect() {
        clear();
    }

    public static void clear() {
        SNAPSHOTS.clear();
        hudSnapshot = Snapshot.empty(0L);
    }

    public static void clear(long gameTick) {
        SNAPSHOTS.clear();
        hudSnapshot = Snapshot.empty(Math.max(0L, gameTick));
    }

    public record Snapshot(int playerEntityId, PlayerRotSnapshot state, int activeDurationTicks) {
        public Snapshot {
            Objects.requireNonNull(state, "state");
            if (activeDurationTicks < 0) {
                throw new IllegalArgumentException("activeDurationTicks must be non-negative");
            }
        }

        public static Snapshot empty(long gameTick) {
            return new Snapshot(-1, PlayerRotSnapshot.inactive(gameTick, EMPTY_CAPACITY), 0);
        }
    }
}