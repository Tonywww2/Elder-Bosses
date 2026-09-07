package com.tonywww.elder_bosses.client.state;

import com.tonywww.elder_bosses.network.MaleniaCombatSnapshotPacket;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaCombatState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ClientBossStateStore {
    private static final Map<Integer, MaleniaCombatSnapshotPacket> SNAPSHOTS = new HashMap<>();
    private static Snapshot hudSnapshot = Snapshot.empty();

    private ClientBossStateStore() {
    }

    public static Snapshot snapshot() {
        refreshHudSnapshot();
        return hudSnapshot;
    }

    public static void update(Snapshot state) {
        hudSnapshot = Objects.requireNonNull(state, "state");
    }

    public static void update(MaleniaCombatSnapshotPacket snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!isHudEligible(snapshot)) {
            SNAPSHOTS.remove(snapshot.entityId());
            refreshHudSnapshot();
            return;
        }
        SNAPSHOTS.put(snapshot.entityId(), snapshot);
        refreshHudSnapshot();
    }

    public static Optional<MaleniaCombatSnapshotPacket> get(int entityId) {
        return Optional.ofNullable(SNAPSHOTS.get(entityId));
    }

    public static List<MaleniaCombatSnapshotPacket> snapshots() {
        return List.copyOf(SNAPSHOTS.values());
    }

    public static void onTrackingEnd(int entityId) {
        clear(entityId);
    }

    public static void onDimensionChanged() {
        clear();
    }

    public static void onDisconnect() {
        clear();
    }

    public static void clear() {
        SNAPSHOTS.clear();
        hudSnapshot = Snapshot.empty();
    }

    public static void clear(int entityId) {
        SNAPSHOTS.remove(entityId);
        if (hudSnapshot.entityId() == entityId) {
            refreshHudSnapshot();
        }
    }

    private static void refreshHudSnapshot() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            hudSnapshot = Snapshot.empty();
            return;
        }

        MaleniaCombatSnapshotPacket nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (MaleniaCombatSnapshotPacket snapshot : SNAPSHOTS.values()) {
            if (!isHudEligible(snapshot)) {
                continue;
            }
            Entity boss = minecraft.level.getEntity(snapshot.entityId());
            if (boss == null || !boss.isAlive()) {
                continue;
            }

            double distance = minecraft.player.distanceToSqr(boss);
            if (distance < nearestDistance
                    || distance == nearestDistance
                    && (nearest == null || snapshot.entityId() < nearest.entityId())) {
                nearest = snapshot;
                nearestDistance = distance;
            }
        }
        hudSnapshot = nearest == null ? Snapshot.empty() : Snapshot.from(nearest);
    }

    private static boolean isHudEligible(MaleniaCombatSnapshotPacket snapshot) {
        return snapshot.combatState() != MaleniaCombatState.DEFEATED
                && snapshot.combatState() != MaleniaCombatState.DORMANT;
    }

    public record Snapshot(
            int entityId,
            double stagger,
            double staggerCapacity,
            int dialogueEventId,
            long dialogueEventStartTick,
            int subtitleDurationTicks
    ) {
        public Snapshot {
            if (!Double.isFinite(stagger) || stagger < 0.0) {
                throw new IllegalArgumentException("stagger must be finite and non-negative");
            }
            if (!Double.isFinite(staggerCapacity) || staggerCapacity < 0.0) {
                throw new IllegalArgumentException("staggerCapacity must be finite and non-negative");
            }
            if (dialogueEventStartTick < 0L) {
                throw new IllegalArgumentException("dialogueEventStartTick must be non-negative");
            }
            if (subtitleDurationTicks < 0) {
                throw new IllegalArgumentException("subtitleDurationTicks must be non-negative");
            }
        }

        public static Snapshot empty() {
            return new Snapshot(-1, 0.0, 0.0, -1, 0L, 0);
        }

        public static Snapshot from(MaleniaCombatSnapshotPacket snapshot) {
            return new Snapshot(
                    snapshot.entityId(),
                    snapshot.stagger(),
                    snapshot.staggerCapacity(),
                    snapshot.dialogueEvent() == null ? -1 : snapshot.dialogueEvent().id(),
                    snapshot.dialogueStartTick(),
                    snapshot.subtitleDurationTicks()
            );
        }

        public boolean visible() {
            return entityId >= 0;
        }
    }
}