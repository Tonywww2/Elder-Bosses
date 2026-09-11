package com.tonywww.elder_bosses.network;

import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaCombatState;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaPhase;
import com.tonywww.elder_bosses.dialogue.DialogueEvent;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

public record MaleniaCombatSnapshotPacket(
        int entityId,
        MaleniaPhase phase,
        MaleniaCombatState combatState,
    long stateStartGameTime,
        MaleniaActionId actionId,
    long actionSequence,
        int actionTick,
        long actionStartGameTime,
        long seed,
        double actionRangeMultiplier,
        int targetEntityId,
        float phaseHealth,
        float phaseMaxHealth,
        float stagger,
        float staggerCapacity,
        float healBudget,
        boolean wings,
        DialogueEvent dialogueEvent,
        long dialogueStartTick,
        int subtitleDurationTicks
) {
    private static final int MAX_PHASE_NAME_LENGTH = 24;
    private static final int MAX_STATE_NAME_LENGTH = 32;
    private static final int MAX_ACTION_ID_LENGTH = 48;

    public MaleniaCombatSnapshotPacket {
        if (entityId < 0) {
            throw new IllegalArgumentException("entityId must be non-negative");
        }
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(combatState, "combatState");
        if (stateStartGameTime < 0L) {
            throw new IllegalArgumentException("stateStartGameTime must be non-negative");
        }
        if (actionTick < -1 || actionTick > NetworkLimits.MAX_TICKS) {
            throw new IllegalArgumentException("actionTick is outside the supported range");
        }
        boolean hasAction = actionId != null;
        if (hasAction && (actionSequence < 0L || actionTick < 0)) {
            throw new IllegalArgumentException(
                    "actionId, actionSequence, and actionTick must describe the same action state"
            );
        }
        if (!hasAction && (actionSequence != -1L || actionTick != -1)) {
            throw new IllegalArgumentException("an empty action must use -1 sequence and tick");
        }
        if (actionStartGameTime < 0L) {
            throw new IllegalArgumentException("actionStartGameTime must be non-negative");
        }
        if (!hasAction && (actionStartGameTime != 0L || seed != 0L)) {
            throw new IllegalArgumentException("an empty action must use zero time and seed");
        }
        if (!Double.isFinite(actionRangeMultiplier) || actionRangeMultiplier <= 0.0) {
            throw new IllegalArgumentException("actionRangeMultiplier must be finite and positive");
        }
        if (targetEntityId < -1) {
            throw new IllegalArgumentException("targetEntityId must be -1 or non-negative");
        }
        requireRange(phaseHealth, phaseMaxHealth, "phaseHealth", "phaseMaxHealth");
        requireRange(stagger, staggerCapacity, "stagger", "staggerCapacity");
        requireNonNegativeFinite(healBudget, "healBudget");
        if (dialogueStartTick < 0L) {
            throw new IllegalArgumentException("dialogueStartTick must be non-negative");
        }
        if (dialogueEvent == null && dialogueStartTick != 0L) {
            throw new IllegalArgumentException("an empty dialogue must use a zero start tick");
        }
        if (subtitleDurationTicks < 0 || subtitleDurationTicks > NetworkLimits.MAX_TICKS) {
            throw new IllegalArgumentException("subtitleDurationTicks is outside the supported range");
        }
    }

    public void write(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeVarInt(entityId);
        buffer.writeUtf(phase.serializedName(), MAX_PHASE_NAME_LENGTH);
        buffer.writeUtf(combatState.serializedName(), MAX_STATE_NAME_LENGTH);
        buffer.writeLong(stateStartGameTime);
        buffer.writeUtf(actionId == null ? "" : actionId.serializedName(), MAX_ACTION_ID_LENGTH);
        buffer.writeLong(actionSequence);
        buffer.writeVarInt(actionTick + 1);
        buffer.writeLong(actionStartGameTime);
        buffer.writeLong(seed);
        buffer.writeDouble(actionRangeMultiplier);
        buffer.writeInt(targetEntityId);
        buffer.writeFloat(phaseHealth);
        buffer.writeFloat(phaseMaxHealth);
        buffer.writeFloat(stagger);
        buffer.writeFloat(staggerCapacity);
        buffer.writeFloat(healBudget);
        buffer.writeBoolean(wings);
        buffer.writeVarInt(dialogueEvent == null ? 0 : dialogueEvent.id() + 1);
        buffer.writeLong(dialogueStartTick);
        buffer.writeVarInt(subtitleDurationTicks);
    }

    public static MaleniaCombatSnapshotPacket read(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        int entityId = buffer.readVarInt();
        MaleniaPhase phase = readPhase(buffer.readUtf(MAX_PHASE_NAME_LENGTH));
        MaleniaCombatState combatState = readCombatState(buffer.readUtf(MAX_STATE_NAME_LENGTH));
        long stateStartGameTime = buffer.readLong();
        MaleniaActionId actionId = readActionId(buffer.readUtf(MAX_ACTION_ID_LENGTH));
        long actionSequence = buffer.readLong();
        int actionTick = buffer.readVarInt() - 1;
        long actionStartGameTime = buffer.readLong();
        long seed = buffer.readLong();
        double actionRangeMultiplier = buffer.readDouble();
        int targetEntityId = buffer.readInt();
        float phaseHealth = buffer.readFloat();
        float phaseMaxHealth = buffer.readFloat();
        float stagger = buffer.readFloat();
        float staggerCapacity = buffer.readFloat();
        float healBudget = buffer.readFloat();
        boolean wings = buffer.readBoolean();
        DialogueEvent dialogueEvent = readDialogueEvent(buffer.readVarInt());
        long dialogueStartTick = buffer.readLong();
        int subtitleDurationTicks = buffer.readVarInt();
        return new MaleniaCombatSnapshotPacket(
                entityId,
                phase,
                combatState,
            stateStartGameTime,
                actionId,
            actionSequence,
                actionTick,
                actionStartGameTime,
                seed,
                actionRangeMultiplier,
                targetEntityId,
                phaseHealth,
                phaseMaxHealth,
                stagger,
                staggerCapacity,
                healBudget,
                wings,
                dialogueEvent,
                dialogueStartTick,
                subtitleDurationTicks
        );
    }

    private static MaleniaPhase readPhase(String serializedName) {
        for (MaleniaPhase phase : MaleniaPhase.values()) {
            if (phase.serializedName().equals(serializedName)) {
                return phase;
            }
        }
        throw new IllegalArgumentException("unknown Malenia phase: " + serializedName);
    }

    private static MaleniaCombatState readCombatState(String serializedName) {
        for (MaleniaCombatState state : MaleniaCombatState.values()) {
            if (state.serializedName().equals(serializedName)) {
                return state;
            }
        }
        throw new IllegalArgumentException("unknown Malenia combat state: " + serializedName);
    }

    private static MaleniaActionId readActionId(String serializedName) {
        if (serializedName.isEmpty()) {
            return null;
        }
        for (MaleniaActionId actionId : MaleniaActionId.values()) {
            if (actionId.serializedName().equals(serializedName)) {
                return actionId;
            }
        }
        throw new IllegalArgumentException("unknown Malenia action id: " + serializedName);
    }

    private static DialogueEvent readDialogueEvent(int encodedId) {
        if (encodedId == 0) {
            return null;
        }
        return DialogueEvent.fromId(encodedId - 1).orElseThrow(
                () -> new IllegalArgumentException("unknown dialogue event id: " + (encodedId - 1))
        );
    }

    private static void requireRange(float value, float maximum, String valueName, String maximumName) {
        requireNonNegativeFinite(maximum, maximumName);
        requireNonNegativeFinite(value, valueName);
        if (value > maximum) {
            throw new IllegalArgumentException(valueName + " must not exceed " + maximumName);
        }
    }

    private static void requireNonNegativeFinite(float value, String name) {
        if (!Float.isFinite(value) || value < 0.0F) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}