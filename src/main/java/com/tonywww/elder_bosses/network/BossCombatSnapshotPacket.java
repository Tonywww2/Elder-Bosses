package com.tonywww.elder_bosses.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

public record BossCombatSnapshotPacket(
        int entityId,
        String bossId,
        String phase,
        String combatState,
        long stateStartGameTime,
        String actionId,
        long actionSequence,
        int actionTick,
        long actionStartGameTime,
        long actionSeed,
        int targetEntityId,
        float health,
        float maximumHealth,
        float stagger,
        float staggerCapacity,
        int specialFlags,
        boolean hudVisible,
        String dialogueSpeakerKey,
        String dialogueTextKey,
        long dialogueStartTick,
        int subtitleDurationTicks
) {
    private static final int MAX_ID_LENGTH = 128;

    public BossCombatSnapshotPacket {
        if (entityId < 0 || targetEntityId < -1 || actionTick < -1
                || actionSequence < -1L || stateStartGameTime < 0L
                || actionStartGameTime < 0L || dialogueStartTick < 0L
                || subtitleDurationTicks < 0 || subtitleDurationTicks > NetworkLimits.MAX_TICKS) {
            throw new IllegalArgumentException("invalid boss snapshot counters");
        }
        requireText(bossId, "bossId");
        requireText(phase, "phase");
        requireText(combatState, "combatState");
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(dialogueSpeakerKey, "dialogueSpeakerKey");
        Objects.requireNonNull(dialogueTextKey, "dialogueTextKey");
        requireRange(health, maximumHealth, "health", "maximumHealth");
        requireRange(stagger, staggerCapacity, "stagger", "staggerCapacity");
        if (actionId.isEmpty() != (actionTick < 0 || actionSequence < 0L)) {
            throw new IllegalArgumentException("action fields must describe the same state");
        }
        if (dialogueTextKey.isEmpty() != dialogueSpeakerKey.isEmpty()) {
            throw new IllegalArgumentException("dialogue keys must both be empty or both be present");
        }
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeUtf(bossId, MAX_ID_LENGTH);
        buffer.writeUtf(phase, MAX_ID_LENGTH);
        buffer.writeUtf(combatState, MAX_ID_LENGTH);
        buffer.writeLong(stateStartGameTime);
        buffer.writeUtf(actionId, MAX_ID_LENGTH);
        buffer.writeLong(actionSequence);
        buffer.writeVarInt(actionTick + 1);
        buffer.writeLong(actionStartGameTime);
        buffer.writeLong(actionSeed);
        buffer.writeInt(targetEntityId);
        buffer.writeFloat(health);
        buffer.writeFloat(maximumHealth);
        buffer.writeFloat(stagger);
        buffer.writeFloat(staggerCapacity);
        buffer.writeVarInt(specialFlags);
        buffer.writeBoolean(hudVisible);
        buffer.writeUtf(dialogueSpeakerKey, MAX_ID_LENGTH);
        buffer.writeUtf(dialogueTextKey, MAX_ID_LENGTH);
        buffer.writeLong(dialogueStartTick);
        buffer.writeVarInt(subtitleDurationTicks);
    }

    public static BossCombatSnapshotPacket read(FriendlyByteBuf buffer) {
        return new BossCombatSnapshotPacket(
                buffer.readVarInt(),
                buffer.readUtf(MAX_ID_LENGTH),
                buffer.readUtf(MAX_ID_LENGTH),
                buffer.readUtf(MAX_ID_LENGTH),
                buffer.readLong(),
                buffer.readUtf(MAX_ID_LENGTH),
                buffer.readLong(),
                buffer.readVarInt() - 1,
                buffer.readLong(),
                buffer.readLong(),
                buffer.readInt(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readUtf(MAX_ID_LENGTH),
                buffer.readUtf(MAX_ID_LENGTH),
                buffer.readLong(),
                buffer.readVarInt()
        );
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException(name + " must be a non-empty bounded string");
        }
    }

    private static void requireRange(float value, float maximum, String name, String maximumName) {
        if (!Float.isFinite(value) || !Float.isFinite(maximum)
                || value < 0.0F || maximum < 0.0F || value > maximum) {
            throw new IllegalArgumentException(name + " must be within " + maximumName);
        }
    }
}
