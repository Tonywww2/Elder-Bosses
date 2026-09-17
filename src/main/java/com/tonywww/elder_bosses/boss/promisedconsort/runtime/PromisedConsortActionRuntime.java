package com.tonywww.elder_bosses.boss.promisedconsort.runtime;

import com.tonywww.elder_bosses.boss.promisedconsort.action.PromisedConsortActionCatalog;
import com.tonywww.elder_bosses.boss.promisedconsort.action.PromisedConsortActionDefinition;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase;
import com.tonywww.elder_bosses.combat.action.ActionLifecycleEvent;
import com.tonywww.elder_bosses.combat.action.ActionWindow;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class PromisedConsortActionRuntime {
    private final PromisedConsortActionCatalog catalog;
    private Consumer<ActionLifecycleEvent> lifecycleListener = event -> {};
    private ActiveAction active;
    private long nextSequence;

    public PromisedConsortActionRuntime(PromisedConsortActionCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public void setLifecycleListener(Consumer<ActionLifecycleEvent> listener) {
        lifecycleListener = Objects.requireNonNull(listener, "listener");
    }

    public static PromisedConsortActionRuntime restore(
            PromisedConsortActionCatalog catalog,
            PersistentState state
    ) {
        Objects.requireNonNull(state, "state");
        PromisedConsortActionRuntime runtime = new PromisedConsortActionRuntime(catalog);
        runtime.nextSequence = state.nextSequence();
        if (state.activeActionId() != null) {
            PromisedConsortActionDefinition definition = catalog.get(state.activeActionId(), state.rangedCounter());
            runtime.active = new ActiveAction(
                    definition,
                    state.activeSequence(),
                    state.activePhase(),
                    state.startGameTick(),
                    state.seed(),
                    state.targetId(),
                    state.rangedCounter()
            );
        }
        return runtime;
    }

    public PersistentState persistentState() {
        return active == null
                ? new PersistentState(nextSequence, null, -1L, null, 0L, 0L, null)
                : new PersistentState(
                        nextSequence,
                        active.definition.id(),
                        active.sequence,
                        active.phase,
                        active.startGameTick,
                        active.seed,
                        active.targetId,
                        active.rangedCounter
                );
    }

    public PromisedConsortActionSnapshot start(
            PromisedConsortActionId actionId,
            PromisedConsortPhase phase,
            long gameTick,
            long seed,
            UUID targetId
    ) {
            return start(actionId, phase, gameTick, seed, targetId, false);
            }

            public PromisedConsortActionSnapshot start(PromisedConsortActionId actionId, PromisedConsortPhase phase,
                long gameTick, long seed, UUID targetId, boolean rangedCounter) {
        if (active != null) {
            throw new IllegalStateException("an action is already active");
        }
        PromisedConsortActionDefinition definition = catalog.get(actionId, rangedCounter);
        if (!definition.isAvailableIn(phase)) {
            throw new IllegalArgumentException(actionId + " is not available in " + phase);
        }
        active = new ActiveAction(definition, nextSequence++, phase, gameTick, seed, targetId, rangedCounter);
        publishLifecycle(active, gameTick, ActionLifecycleEvent.Outcome.STARTED);
        return snapshot(gameTick).orElseThrow();
    }

    public Optional<ActionEnd> advance(long gameTick) {
        if (active == null) {
            return Optional.empty();
        }
        int elapsed = elapsed(gameTick);
        if (elapsed < active.definition.timeline().totalTicks()) {
            return Optional.empty();
        }
        return complete(gameTick);
    }

    public Optional<ActionEnd> finishRecovery(long gameTick, int minimumRecoveryTicks) {
        if (minimumRecoveryTicks < 1) throw new IllegalArgumentException("Recovery must keep at least one tick");
        if (active == null) return Optional.empty();
        int elapsed = elapsed(gameTick);
        var timeline = active.definition.timeline();
        if (elapsed >= timeline.totalTicks()) return advance(gameTick);
        var window = timeline.windowAt(elapsed);
        if (window.phase() != com.tonywww.elder_bosses.combat.action.ActionPhase.RECOVERY
                || window.stageIndex() != timeline.stages().size() - 1
                || elapsed - window.startTickInclusive() < minimumRecoveryTicks) return Optional.empty();
        return complete(gameTick);
    }

    private Optional<ActionEnd> complete(long gameTick) {
        ActionEnd end = new ActionEnd(active.definition.id(), active.sequence, gameTick, true);
        ActiveAction ended = active;
        active = null;
        publishLifecycle(ended, gameTick, ActionLifecycleEvent.Outcome.COMPLETED);
        return Optional.of(end);
    }

    public Optional<ActionEnd> cancel(long gameTick) {
        if (active == null) {
            return Optional.empty();
        }
        ActionEnd end = new ActionEnd(active.definition.id(), active.sequence, gameTick, false);
        ActiveAction ended = active;
        active = null;
        publishLifecycle(ended, gameTick, ActionLifecycleEvent.Outcome.CANCELLED);
        return Optional.of(end);
    }

    private void publishLifecycle(ActiveAction action, long gameTick, ActionLifecycleEvent.Outcome outcome) {
        lifecycleListener.accept(new ActionLifecycleEvent(action.definition.id().serializedName(), action.sequence,
                action.startGameTick, gameTick, action.seed, action.targetId, action.definition.timeline(), outcome));
    }

    public Optional<PromisedConsortActionSnapshot> snapshot(long gameTick) {
        if (active == null) {
            return Optional.empty();
        }
        int actionTick = elapsed(gameTick);
        if (actionTick >= active.definition.timeline().totalTicks()) {
            return Optional.empty();
        }
        ActionWindow window = active.definition.timeline().windowAt(actionTick);
        return Optional.of(new PromisedConsortActionSnapshot(
                active.definition.id(),
                active.phase,
                active.sequence,
                active.startGameTick,
                actionTick,
                window.phase(),
                window.stageIndex(),
                actionTick - window.startTickInclusive(),
                active.seed,
                active.targetId,
                active.rangedCounter
        ));
    }

    public void retarget(UUID targetId) {
        if (active != null && !active.rangedCounter && !active.definition.id().rangedDefense()) {
            active.targetId = targetId;
        }
    }

    public boolean isActive() {
        return active != null;
    }

    private int elapsed(long gameTick) {
        if (gameTick < active.startGameTick) {
            throw new IllegalArgumentException("gameTick must not precede action start");
        }
        return Math.toIntExact(gameTick - active.startGameTick);
    }

    public record ActionEnd(
            PromisedConsortActionId actionId,
            long sequence,
            long endGameTick,
            boolean completed
    ) {
    }

    public record PersistentState(
            long nextSequence,
            PromisedConsortActionId activeActionId,
            long activeSequence,
            PromisedConsortPhase activePhase,
            long startGameTick,
            long seed,
            UUID targetId,
            boolean rangedCounter
    ) {
        public PersistentState(long nextSequence, PromisedConsortActionId activeActionId, long activeSequence,
                PromisedConsortPhase activePhase, long startGameTick, long seed, UUID targetId) {
            this(nextSequence, activeActionId, activeSequence, activePhase, startGameTick, seed, targetId, false);
        }
        public PersistentState {
            if (nextSequence < 0L || activeSequence < -1L || startGameTick < 0L) {
                throw new IllegalArgumentException("invalid persisted action counters");
            }
            boolean empty = activeActionId == null;
            if (empty && (activePhase != null || activeSequence != -1L
                    || startGameTick != 0L || seed != 0L || targetId != null)
                    || !empty && (activePhase == null || activeSequence < 0L)) {
                throw new IllegalArgumentException("persisted action fields must agree");
            }
        }
    }

    private static final class ActiveAction {
        private final PromisedConsortActionDefinition definition;
        private final long sequence;
        private final PromisedConsortPhase phase;
        private final long startGameTick;
        private final long seed;
        private UUID targetId;
        private final boolean rangedCounter;

        private ActiveAction(
                PromisedConsortActionDefinition definition,
                long sequence,
                PromisedConsortPhase phase,
                long startGameTick,
                long seed,
                UUID targetId,
                boolean rangedCounter
        ) {
            this.definition = definition;
            this.sequence = sequence;
            this.phase = phase;
            this.startGameTick = startGameTick;
            this.seed = seed;
            this.targetId = targetId;
            this.rangedCounter = rangedCounter;
        }
    }
}
