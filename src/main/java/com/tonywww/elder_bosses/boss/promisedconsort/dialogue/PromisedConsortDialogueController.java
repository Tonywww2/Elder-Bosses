package com.tonywww.elder_bosses.boss.promisedconsort.dialogue;

import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortCombatConfigSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PromisedConsortDialogueController {
    private static final Comparator<QueuedLine> QUEUE_ORDER = Comparator
            .comparingInt((QueuedLine line) -> line.event().priority())
            .reversed()
            .thenComparingLong(QueuedLine::sequence);

    private final PromisedConsortCombatConfigSnapshot.Dialogue config;
    private final Set<PromisedConsortDialogueEvent> handledEvents =
            EnumSet.noneOf(PromisedConsortDialogueEvent.class);
    private final List<QueuedLine> queuedLines = new ArrayList<>();
    private ActiveLine activeLine;
    private long nextSequence;

    public PromisedConsortDialogueController(
            PromisedConsortCombatConfigSnapshot.Dialogue config
    ) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public static PromisedConsortDialogueController restore(
            PromisedConsortCombatConfigSnapshot.Dialogue config,
            PersistentState state
    ) {
        Objects.requireNonNull(state, "state");
        PromisedConsortDialogueController controller =
                new PromisedConsortDialogueController(config);
        controller.handledEvents.addAll(state.handledEvents());
        controller.queuedLines.addAll(state.queuedLines());
        controller.queuedLines.sort(QUEUE_ORDER);
        while (controller.queuedLines.size() > config.maxQueuedLines()) {
            controller.queuedLines.remove(controller.queuedLines.size() - 1);
        }
        controller.activeLine = state.activeLine();
        controller.nextSequence = state.nextSequence();
        return controller;
    }

    public void offer(PromisedConsortDialogueEvent event, long gameTick) {
        Objects.requireNonNull(event, "event");
        if (!config.enabled() || !handledEvents.add(event)) {
            return;
        }
        if (activeLine == null
                || event.priority() > activeLine.event().priority()
                || event == PromisedConsortDialogueEvent.PHASE_TWO_VOW
                && activeLine.event() == PromisedConsortDialogueEvent.TRANSITION_CALL) {
            activeLine = new ActiveLine(event, gameTick);
            return;
        }
        if (config.maxQueuedLines() == 0) {
            return;
        }
        queuedLines.add(new QueuedLine(event, nextSequence++));
        queuedLines.sort(QUEUE_ORDER);
        if (queuedLines.size() > config.maxQueuedLines()) {
            queuedLines.remove(queuedLines.size() - 1);
        }
    }

    public void tick(long gameTick, boolean endPhaseTwoVow) {
        if (activeLine != null && (gameTick >= activeLine.startGameTick()
                + config.subtitleDurationTicks()
                || endPhaseTwoVow
                && activeLine.event() == PromisedConsortDialogueEvent.PHASE_TWO_VOW)) {
            activeLine = null;
        }
        if (activeLine == null && !queuedLines.isEmpty()) {
            QueuedLine next = queuedLines.remove(0);
            activeLine = new ActiveLine(next.event(), gameTick);
        }
    }

    public ActiveLine activeLine() {
        return activeLine;
    }

    public PersistentState persistentState() {
        return new PersistentState(handledEvents, queuedLines, activeLine, nextSequence);
    }

    public record ActiveLine(PromisedConsortDialogueEvent event, long startGameTick) {
        public ActiveLine {
            Objects.requireNonNull(event, "event");
            if (startGameTick < 0L) {
                throw new IllegalArgumentException("startGameTick must be non-negative");
            }
        }
    }

    public record QueuedLine(PromisedConsortDialogueEvent event, long sequence) {
        public QueuedLine {
            Objects.requireNonNull(event, "event");
            if (sequence < 0L) {
                throw new IllegalArgumentException("sequence must be non-negative");
            }
        }
    }

    public record PersistentState(
            Set<PromisedConsortDialogueEvent> handledEvents,
            List<QueuedLine> queuedLines,
            ActiveLine activeLine,
            long nextSequence
    ) {
        public PersistentState {
            handledEvents = handledEvents == null ? Set.of() : Set.copyOf(handledEvents);
            queuedLines = queuedLines == null ? List.of() : List.copyOf(queuedLines);
            if (nextSequence < 0L) {
                throw new IllegalArgumentException("nextSequence must be non-negative");
            }
        }
    }
}