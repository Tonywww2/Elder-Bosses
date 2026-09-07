package com.tonywww.elder_bosses.network;

import com.tonywww.elder_bosses.player.PlayerRotSnapshot;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

public record PlayerRotSnapshotPacket(int playerEntityId, PlayerRotSnapshot snapshot) {
    public PlayerRotSnapshotPacket {
        if (playerEntityId < 0) {
            throw new IllegalArgumentException("playerEntityId must be non-negative");
        }
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.remainingActiveTicks() > NetworkLimits.MAX_TICKS) {
            throw new IllegalArgumentException("remainingActiveTicks is outside the supported range");
        }
    }

    public void write(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeVarInt(playerEntityId);
        buffer.writeLong(snapshot.gameTick());
        buffer.writeDouble(snapshot.buildup());
        buffer.writeDouble(snapshot.capacity());
        buffer.writeBoolean(snapshot.active());
        buffer.writeVarInt(snapshot.remainingActiveTicks());
        buffer.writeDouble(snapshot.healingMultiplier());
        buffer.writeDouble(snapshot.movementSpeedMultiplier());
    }

    public static PlayerRotSnapshotPacket read(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        int playerEntityId = buffer.readVarInt();
        long gameTick = buffer.readLong();
        double buildup = buffer.readDouble();
        double capacity = buffer.readDouble();
        boolean active = buffer.readBoolean();
        int remainingActiveTicks = buffer.readVarInt();
        double healingMultiplier = buffer.readDouble();
        double movementSpeedMultiplier = buffer.readDouble();
        return new PlayerRotSnapshotPacket(
                playerEntityId,
                new PlayerRotSnapshot(
                        gameTick,
                        buildup,
                        capacity,
                        active,
                        remainingActiveTicks,
                        healingMultiplier,
                        movementSpeedMultiplier
                )
        );
    }
}