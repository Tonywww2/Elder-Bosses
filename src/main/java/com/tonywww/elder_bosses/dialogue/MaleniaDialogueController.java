package com.tonywww.elder_bosses.dialogue;

import com.tonywww.elder_bosses.boss.malenia.config.MaleniaCombatConfigSnapshot;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaCombatState;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaPhase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MaleniaDialogueController {
    private static final Comparator<QueuedLine> QUEUE_ORDER = Comparator
            .comparingInt((QueuedLine line) -> line.event().priority())
            .reversed()
            .thenComparingLong(QueuedLine::sequence);

    private final MaleniaCombatConfigSnapshot.Dialogue config;
    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Set<DialogueEvent> handledEvents = EnumSet.noneOf(DialogueEvent.class);
    private final Map<DialogueEvent, Long> scheduledEvents = new EnumMap<>(DialogueEvent.class);
    private final List<QueuedLine> queuedLines = new ArrayList<>();
    private ActiveLine activeLine;
    private long nextSequence;

    public MaleniaDialogueController(MaleniaCombatConfigSnapshot.Dialogue config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public static MaleniaDialogueController restore(
            MaleniaCombatConfigSnapshot.Dialogue config,
            PersistentState state
    ) {
        Objects.requireNonNull(state, "state");
        MaleniaDialogueController controller = new MaleniaDialogueController(config);
        controller.participants.addAll(state.participants());
        controller.handledEvents.addAll(state.handledEvents());
        controller.scheduledEvents.putAll(state.scheduledEvents());
        controller.queuedLines.addAll(state.queuedLines());
        controller.queuedLines.sort(QUEUE_ORDER);
        while (controller.queuedLines.size() > config.maxQueuedLines()) {
            controller.queuedLines.remove(controller.queuedLines.size() - 1);
        }
        controller.activeLine = state.activeLine().orElse(null);
        controller.nextSequence = state.nextSequence();
        return controller;
    }

    public List<DialogueEmission> tick(
            MaleniaCombatState state,
            int stateTick,
            long gameTime,
            Collection<UUID> participantIds
    ) {
        Objects.requireNonNull(state, "state");
        addParticipants(participantIds);
        if (!config.enabled()) {
            return List.of();
        }

        if (activeLine != null
                && gameTime >= activeLine.startGameTick() + config.subtitleDurationTicks()) {
            activeLine = null;
        }

        List<DialogueEvent> candidates = new ArrayList<>();
        fixedEvent(state, stateTick).ifPresent(candidates::add);
        for (DialogueEvent event : DialogueEvent.values()) {
            Long dueGameTick = scheduledEvents.get(event);
            if (dueGameTick != null && dueGameTick <= gameTime) {
                scheduledEvents.remove(event);
                candidates.add(event);
            }
        }
        candidates.sort(Comparator.comparingInt(DialogueEvent::priority).reversed());

        List<DialogueEmission> emissions = new ArrayList<>(1);
        for (DialogueEvent event : candidates) {
            accept(event, gameTime, emissions);
        }
        if (emissions.isEmpty() && activeLine == null && !queuedLines.isEmpty()) {
            QueuedLine queuedLine = queuedLines.remove(0);
            emit(queuedLine.event(), gameTime, emissions);
        }
        return List.copyOf(emissions);
    }

    public boolean recordPlayerDefeat(UUID playerId, MaleniaPhase phase, long gameTime) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(phase, "phase");
        if (!config.enabled() || !participants.contains(playerId)) {
            return false;
        }
        DialogueEvent event = phase == MaleniaPhase.PHASE_ONE
                ? DialogueEvent.PLAYER_DEFEATED_P1
                : DialogueEvent.PLAYER_DEFEATED_P2;
        if (handledEvents.contains(event) || scheduledEvents.containsKey(event)) {
            return false;
        }
        scheduledEvents.put(event, gameTime + config.playerDefeatDelayTicks());
        return true;
    }

    public boolean isParticipant(UUID playerId) {
        return participants.contains(Objects.requireNonNull(playerId, "playerId"));
    }

    public Optional<ActiveLine> activeLine() {
        return Optional.ofNullable(activeLine);
    }

    public PersistentState persistentState() {
        return new PersistentState(
                participants,
                handledEvents,
                scheduledEvents,
                queuedLines,
                Optional.ofNullable(activeLine),
                nextSequence
        );
    }

    private void addParticipants(Collection<UUID> participantIds) {
        Objects.requireNonNull(participantIds, "participantIds");
        for (UUID participantId : participantIds) {
            participants.add(Objects.requireNonNull(participantId, "participantId"));
        }
    }

    private Optional<DialogueEvent> fixedEvent(MaleniaCombatState state, int stateTick) {
        if (state == MaleniaCombatState.INTRO && stateTick == config.introWarningTick()) {
            return Optional.of(DialogueEvent.INTRO_WARNING);
        }
        if (state == MaleniaCombatState.TRANSITION
                && stateTick == config.transitionReleaseTick()) {
            return Optional.of(DialogueEvent.TRANSITION_RELEASE);
        }
        if (state == MaleniaCombatState.DEFEATED && stateTick == config.defeatedTick()) {
            return Optional.of(DialogueEvent.DEFEATED);
        }
        return Optional.empty();
    }

    private void accept(
            DialogueEvent event,
            long gameTime,
            List<DialogueEmission> emissions
    ) {
        if (!handledEvents.add(event)) {
            return;
        }
        if (activeLine == null) {
            emit(event, gameTime, emissions);
            return;
        }
        if (emissions.isEmpty() && event.priority() > activeLine.event().priority()) {
            emit(event, gameTime, emissions);
            return;
        }
        queue(event);
    }

    private void queue(DialogueEvent event) {
        if (config.maxQueuedLines() == 0) {
            return;
        }
        queuedLines.add(new QueuedLine(event, nextSequence++));
        queuedLines.sort(QUEUE_ORDER);
        if (queuedLines.size() > config.maxQueuedLines()) {
            queuedLines.remove(queuedLines.size() - 1);
        }
    }

    private void emit(
            DialogueEvent event,
            long gameTime,
            List<DialogueEmission> emissions
    ) {
        activeLine = new ActiveLine(event, gameTime);
        emissions.add(new DialogueEmission(event, gameTime, participants));
    }

    public record DialogueEmission(
            DialogueEvent event,
            long startGameTick,
            Set<UUID> participantIds
    ) {
        public DialogueEmission {
            Objects.requireNonNull(event, "event");
            participantIds = Set.copyOf(participantIds);
        }
    }

    public record ActiveLine(DialogueEvent event, long startGameTick) {
        public ActiveLine {
            Objects.requireNonNull(event, "event");
        }
    }

    public record QueuedLine(DialogueEvent event, long sequence) {
        public QueuedLine {
            Objects.requireNonNull(event, "event");
        }
    }

    public record PersistentState(
            Set<UUID> participants,
            Set<DialogueEvent> handledEvents,
            Map<DialogueEvent, Long> scheduledEvents,
            List<QueuedLine> queuedLines,
            Optional<ActiveLine> activeLine,
            long nextSequence
    ) {
        public PersistentState {
            participants = Set.copyOf(participants);
            handledEvents = Set.copyOf(handledEvents);
            scheduledEvents = Map.copyOf(scheduledEvents);
            queuedLines = List.copyOf(queuedLines);
            activeLine = Objects.requireNonNull(activeLine, "activeLine");
            if (nextSequence < 0L) {
                throw new IllegalArgumentException("nextSequence must be non-negative");
            }
        }
    }
}