package com.tonywww.elder_bosses.boss.promisedconsort.runtime;

import com.tonywww.elder_bosses.boss.promisedconsort.action.PromisedConsortActionCatalog;
import com.tonywww.elder_bosses.boss.promisedconsort.action.PromisedConsortActionDefinition;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase;
import com.tonywww.elder_bosses.combat.action.ActionWindow;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PromisedConsortActionRuntime {
    private final PromisedConsortActionCatalog catalog;
    private ActiveAction active;
    private long nextSequence;

    public PromisedConsortActionRuntime(PromisedConsortActionCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public static PromisedConsortActionRuntime restore(
            PromisedConsortActionCatalog catalog,
            PersistentState state
    ) {
        Objects.requireNonNull(state, "state");
        PromisedConsortActionRuntime runtime = new PromisedConsortActionRuntime(catalog);
        runtime.nextSequence = state.nextSequence();
        if (state.activeActionId() != null) {
            PromisedConsortActionDefinition definition = catalog.get(state.activeActionId());
            runtime.active = new ActiveAction(
                    definition,
                    state.activeSequence(),
                    state.activePhase(),
                    state.startGameTick(),
                    state.seed(),
                    state.targetId()
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
                        active.targetId
                );
    }

    public PromisedConsortActionSnapshot start(
            PromisedConsortActionId actionId,
            PromisedConsortPhase phase,
            long gameTick,
            long seed,
            UUID targetId
    ) {
        if (active != null) {
            throw new IllegalStateException("an action is already active");
        }
        PromisedConsortActionDefinition definition = catalog.get(actionId);
        if (!definition.isAvailableIn(phase)) {
            throw new IllegalArgumentException(actionId + " is not available in " + phase);
        }
        active = new ActiveAction(definition, nextSequence++, phase, gameTick, seed, targetId);
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
        ActionEnd end = new ActionEnd(active.definition.id(), active.sequence, gameTick, true);
        active = null;
        return Optional.of(end);
    }

    public Optional<ActionEnd> cancel(long gameTick) {
        if (active == null) {
            return Optional.empty();
        }
        ActionEnd end = new ActionEnd(active.definition.id(), active.sequence, gameTick, false);
        active = null;
        return Optional.of(end);
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
                active.targetId
        ));
    }

    public void retarget(UUID targetId) {
        if (active != null) {
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
            UUID targetId
    ) {
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

        private ActiveAction(
                PromisedConsortActionDefinition definition,
                long sequence,
                PromisedConsortPhase phase,
                long startGameTick,
                long seed,
                UUID targetId
        ) {
            this.definition = definition;
            this.sequence = sequence;
            this.phase = phase;
            this.startGameTick = startGameTick;
            this.seed = seed;
            this.targetId = targetId;
        }
    }
}
