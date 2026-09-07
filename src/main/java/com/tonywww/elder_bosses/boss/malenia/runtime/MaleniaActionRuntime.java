package com.tonywww.elder_bosses.boss.malenia.runtime;

import com.tonywww.elder_bosses.boss.malenia.action.MaleniaActionCatalog;
import com.tonywww.elder_bosses.boss.malenia.action.MaleniaActionDefinition;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaPhase;
import com.tonywww.elder_bosses.combat.action.ActionPhase;
import com.tonywww.elder_bosses.combat.action.ActionWindow;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

public final class MaleniaActionRuntime {
    private final MaleniaActionCatalog catalog;
    private final ActionCleanup cleanup;
    private ActiveAction activeAction;
    private long nextSequence;
    private long lastObservedGameTick = -1L;

    public MaleniaActionRuntime(MaleniaActionCatalog catalog) {
        this(catalog, ActionCleanup.NONE);
    }

    public MaleniaActionRuntime(MaleniaActionCatalog catalog, ActionCleanup cleanup) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
    }

    private MaleniaActionRuntime(
            MaleniaActionCatalog catalog,
            PersistentState state,
            ActionCleanup cleanup
    ) {
        this(catalog, cleanup);
        nextSequence = state.nextSequence();
    }

    public static MaleniaActionRuntime restore(
            MaleniaActionCatalog catalog,
            PersistentState state,
            ActionCleanup cleanup
    ) {
        return new MaleniaActionRuntime(
                catalog,
                Objects.requireNonNull(state, "state"),
                cleanup
        );
    }

    public MaleniaActionSnapshot start(
            MaleniaActionId actionId,
            MaleniaPhase maleniaPhase,
            long gameTick,
            long seed,
            UUID targetId
    ) {
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(maleniaPhase, "maleniaPhase");
        MaleniaActionDefinition definition = requireDefinition(actionId);
        if (!definition.isAvailableIn(maleniaPhase)) {
            throw new IllegalArgumentException(actionId + " is not available in " + maleniaPhase);
        }

        advance(gameTick);
        if (activeAction != null) {
            endActive(ActionEndReason.REPLACED, gameTick);
        }

        long sequence = nextSequence;
        nextSequence = Math.incrementExact(nextSequence);
        activeAction = new ActiveAction(definition, sequence, gameTick, seed, targetId);
        return snapshotOf(activeAction, gameTick);
    }

    public Optional<ActionEnd> advance(long gameTick) {
        validateGameTick(gameTick);
        lastObservedGameTick = gameTick;
        if (activeAction == null) {
            return Optional.empty();
        }

        long elapsedTicks = gameTick - activeAction.startGameTick;
        if (elapsedTicks < activeAction.definition.timeline().totalTicks()) {
            return Optional.empty();
        }

        long completionTick = Math.addExact(
                activeAction.startGameTick,
                activeAction.definition.timeline().totalTicks()
        );
        return Optional.of(endActive(ActionEndReason.COMPLETED, completionTick));
    }

    public Optional<ActionEnd> cancel(long gameTick) {
        Optional<ActionEnd> completed = advance(gameTick);
        if (completed.isPresent() || activeAction == null) {
            return completed;
        }
        return Optional.of(endActive(ActionEndReason.CANCELLED, gameTick));
    }

    public Optional<MaleniaActionSnapshot> retarget(long gameTick, UUID targetId) {
        advance(gameTick);
        if (activeAction == null) {
            return Optional.empty();
        }
        activeAction.targetId = targetId;
        return Optional.of(snapshotOf(activeAction, gameTick));
    }

    public Optional<MaleniaActionSnapshot> snapshot(long gameTick) {
        advance(gameTick);
        if (activeAction == null) {
            return Optional.empty();
        }
        return Optional.of(snapshotOf(activeAction, gameTick));
    }

    public Optional<MaleniaActionDefinition> definition(long gameTick) {
        advance(gameTick);
        return activeAction == null ? Optional.empty() : Optional.of(activeAction.definition);
    }

    public OptionalInt actionTick(long gameTick) {
        Optional<MaleniaActionSnapshot> snapshot = snapshot(gameTick);
        return snapshot.isPresent()
                ? OptionalInt.of(snapshot.get().actionTick())
                : OptionalInt.empty();
    }

    public Optional<ActionWindow> window(long gameTick) {
        return snapshot(gameTick).map(MaleniaActionSnapshot::window);
    }

    public Optional<ActionPhase> phase(long gameTick) {
        return snapshot(gameTick).map(MaleniaActionSnapshot::phase);
    }

    public boolean isActive(long gameTick) {
        advance(gameTick);
        return activeAction != null;
    }

    public PersistentState prepareForPersistence(long gameTick) {
        cancel(gameTick);
        return new PersistentState(nextSequence);
    }

    private ActionEnd endActive(ActionEndReason reason, long endGameTick) {
        ActiveAction ended = activeAction;
        if (ended == null) {
            throw new IllegalStateException("no action is active");
        }
        activeAction = null;
        ActionEnd result = new ActionEnd(
                ended.definition.id(),
                ended.sequence,
                ended.startGameTick,
                ended.seed,
                Optional.ofNullable(ended.targetId),
                endGameTick,
                Math.toIntExact(endGameTick - ended.startGameTick),
                reason
        );
        cleanup.clearForActionChange(result);
        return result;
    }

    private MaleniaActionSnapshot snapshotOf(ActiveAction action, long gameTick) {
        int actionTick = Math.toIntExact(gameTick - action.startGameTick);
        ActionWindow window = action.definition.timeline().windowAt(actionTick);
        return new MaleniaActionSnapshot(
                action.definition.id(),
                action.sequence,
                action.startGameTick,
                action.seed,
                Optional.ofNullable(action.targetId),
                actionTick,
                window
        );
    }

    private void validateGameTick(long gameTick) {
        if (gameTick < 0L) {
            throw new IllegalArgumentException("gameTick must be non-negative");
        }
        if (gameTick < lastObservedGameTick) {
            throw new IllegalArgumentException("gameTick must not move backwards");
        }
    }

    private MaleniaActionDefinition requireDefinition(MaleniaActionId actionId) {
        MaleniaActionDefinition definition = catalog.get(actionId);
        if (definition == null) {
            throw new IllegalArgumentException("unknown Malenia action: " + actionId);
        }
        return definition;
    }

    private static final class ActiveAction {
        private final MaleniaActionDefinition definition;
        private final long sequence;
        private final long startGameTick;
        private final long seed;
        private UUID targetId;

        private ActiveAction(
                MaleniaActionDefinition definition,
                long sequence,
                long startGameTick,
                long seed,
                UUID targetId
        ) {
            this.definition = definition;
            this.sequence = sequence;
            this.startGameTick = startGameTick;
            this.seed = seed;
            this.targetId = targetId;
        }
    }

    public enum ActionEndReason {
        COMPLETED,
        CANCELLED,
        REPLACED
    }

    @FunctionalInterface
    public interface ActionCleanup {
        ActionCleanup NONE = endedAction -> {
        };

        void clearForActionChange(ActionEnd endedAction);
    }

    public record ActionEnd(
            MaleniaActionId actionId,
            long sequence,
            long startGameTick,
            long seed,
            Optional<UUID> targetId,
            long endGameTick,
            int elapsedTicks,
            ActionEndReason reason
    ) {
        public ActionEnd {
            Objects.requireNonNull(actionId, "actionId");
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(reason, "reason");
            if (sequence < 0L || startGameTick < 0L || endGameTick < startGameTick || elapsedTicks < 0) {
                throw new IllegalArgumentException("invalid action end state");
            }
        }

        public boolean completed() {
            return reason == ActionEndReason.COMPLETED;
        }
    }

    public record PersistentState(long nextSequence) {
        public PersistentState {
            if (nextSequence < 0L) {
                throw new IllegalArgumentException("nextSequence must be non-negative");
            }
        }
    }
}