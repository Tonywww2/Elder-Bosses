package com.tonywww.elder_bosses.boss.malenia.sync;

import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaCombatState;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaPhase;
import com.tonywww.elder_bosses.boss.malenia.runtime.MaleniaActionSnapshot;
import com.tonywww.elder_bosses.dialogue.DialogueEvent;
import com.tonywww.elder_bosses.network.MaleniaCombatSnapshotPacket;

import java.util.Objects;
import java.util.Optional;

public final class MaleniaSyncSnapshotFactory {
    private MaleniaSyncSnapshotFactory() {
    }

    public static MaleniaCombatSnapshotPacket create(Host host) {
        Objects.requireNonNull(host, "host");
        MaleniaPhase phase = resolvePhase(host.phaseSerializedName());
        MaleniaCombatState combatState = resolveCombatState(host.combatStateSerializedName());
        Optional<MaleniaActionSnapshot> action = Objects.requireNonNull(
                host.currentActionSnapshot(),
                "host.currentActionSnapshot()"
        );
        Optional<DialogueSnapshot> dialogue = Objects.requireNonNull(
                host.dialogueSnapshot(),
                "host.dialogueSnapshot()"
        );

        MaleniaActionSnapshot currentAction = action.orElse(null);
        DialogueSnapshot currentDialogue = dialogue.orElse(null);
        return new MaleniaCombatSnapshotPacket(
                host.entityId(),
                phase,
                combatState,
                host.stateStartGameTime(),
                currentAction == null ? null : currentAction.actionId(),
                currentAction == null ? -1L : currentAction.sequence(),
                currentAction == null ? -1 : currentAction.actionTick(),
                currentAction == null ? 0L : currentAction.startGameTick(),
                currentAction == null ? 0L : currentAction.seed(),
                host.targetEntityId(),
                host.phaseHealth(),
                host.phaseMaxHealth(),
                host.stagger(),
                host.staggerCapacity(),
                host.healBudget(),
                host.wingsVisible(),
                currentDialogue == null ? null : currentDialogue.event(),
                currentDialogue == null ? 0L : currentDialogue.startGameTime(),
                host.subtitleDurationTicks()
        );
    }

    private static MaleniaPhase resolvePhase(String serializedName) {
        Objects.requireNonNull(serializedName, "host.phaseSerializedName()");
        for (MaleniaPhase phase : MaleniaPhase.values()) {
            if (phase.serializedName().equals(serializedName)) {
                return phase;
            }
        }
        throw new IllegalArgumentException("unknown Malenia phase: " + serializedName);
    }

    private static MaleniaCombatState resolveCombatState(String serializedName) {
        Objects.requireNonNull(serializedName, "host.combatStateSerializedName()");
        for (MaleniaCombatState state : MaleniaCombatState.values()) {
            if (state.serializedName().equals(serializedName)) {
                return state;
            }
        }
        throw new IllegalArgumentException("unknown Malenia combat state: " + serializedName);
    }

    public interface Host {
        int entityId();

        String phaseSerializedName();

        String combatStateSerializedName();

        long stateStartGameTime();

        Optional<MaleniaActionSnapshot> currentActionSnapshot();

        int targetEntityId();

        float phaseHealth();

        float phaseMaxHealth();

        float stagger();

        float staggerCapacity();

        float healBudget();

        boolean wingsVisible();

        Optional<DialogueSnapshot> dialogueSnapshot();

        int subtitleDurationTicks();
    }

    public record DialogueSnapshot(DialogueEvent event, long startGameTime) {
        public DialogueSnapshot {
            Objects.requireNonNull(event, "event");
            if (startGameTime < 0L) {
                throw new IllegalArgumentException("startGameTime must be non-negative");
            }
        }
    }
}