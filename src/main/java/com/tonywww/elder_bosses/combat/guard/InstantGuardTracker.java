package com.tonywww.elder_bosses.combat.guard;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class InstantGuardTracker {
    public static final int DEFAULT_WINDOW_START_TICK = 3;
    public static final int DEFAULT_WINDOW_END_TICK = 6;
    public static final int MINIMUM_RAISE_INTERVAL_TICKS = 4;
    public static final int DEFAULT_MINIMUM_RAISE_INTERVAL_TICKS = MINIMUM_RAISE_INTERVAL_TICKS;

    private static final double NORMAL_MULTIPLIER = 1.0;

    private final int windowStartTick;
    private final int windowEndTick;
    private final int minimumRaiseIntervalTicks;
    private final double blockedDamageMultiplier;
    private final double shieldDurabilityMultiplier;
    private final Map<UUID, GuardState> statesByTarget = new HashMap<>();

    private long lastObservedGameTick = -1L;

    public InstantGuardTracker(
            int windowStartTick,
            int windowEndTick,
            int minimumRaiseIntervalTicks,
            double blockedDamageMultiplier,
            double shieldDurabilityMultiplier
    ) {
        if (windowStartTick < 0) {
            throw new IllegalArgumentException("windowStartTick must be non-negative");
        }
        if (windowEndTick < windowStartTick) {
            throw new IllegalArgumentException("windowEndTick must not be before windowStartTick");
        }
        if (minimumRaiseIntervalTicks < MINIMUM_RAISE_INTERVAL_TICKS) {
            throw new IllegalArgumentException("minimumRaiseIntervalTicks must be at least 4");
        }
        if (!Double.isFinite(blockedDamageMultiplier)
            || blockedDamageMultiplier < 0.0
            || blockedDamageMultiplier > 1.0) {
            throw new IllegalArgumentException(
                "blockedDamageMultiplier must be finite and between 0 and 1"
            );
        }
        if (!Double.isFinite(shieldDurabilityMultiplier)
            || shieldDurabilityMultiplier < 0.0) {
            throw new IllegalArgumentException(
                "shieldDurabilityMultiplier must be finite and non-negative"
            );
        }
        this.windowStartTick = windowStartTick;
        this.windowEndTick = windowEndTick;
        this.minimumRaiseIntervalTicks = minimumRaiseIntervalTicks;
        this.blockedDamageMultiplier = blockedDamageMultiplier;
        this.shieldDurabilityMultiplier = shieldDurabilityMultiplier;
    }

    public void updateGuarding(
            UUID targetId,
            boolean guarding,
            int ticksUsingItem,
            long gameTick
    ) {
        Objects.requireNonNull(targetId, "targetId");
        if (ticksUsingItem < 0) {
            throw new IllegalArgumentException("ticksUsingItem must be non-negative");
        }
        validateGameTick(gameTick);

        GuardState state = statesByTarget.get(targetId);
        if (state == null) {
            if (guarding) {
                long inferredRaiseTick = Math.max(0L, gameTick - ticksUsingItem);
                statesByTarget.put(targetId, GuardState.raisedAt(inferredRaiseTick));
            }
            lastObservedGameTick = gameTick;
            return;
        }

        if (state.guarding() == guarding) {
            lastObservedGameTick = gameTick;
            return;
        }

        if (!guarding) {
            statesByTarget.put(targetId, state.lowered());
            lastObservedGameTick = gameTick;
            return;
        }

        boolean intervalSatisfied = gameTick - state.lastRaiseTick() >= minimumRaiseIntervalTicks;
        statesByTarget.put(targetId, state.raisedAt(gameTick, intervalSatisfied));
        lastObservedGameTick = gameTick;
    }

    public boolean isInWindow(UUID targetId, long gameTick) {
        Objects.requireNonNull(targetId, "targetId");
        validateGameTick(gameTick);
        lastObservedGameTick = gameTick;
        return isInWindowAt(statesByTarget.get(targetId), gameTick);
    }

    public InstantGuardResult resolve(UUID targetId, boolean skillEligible, long gameTick) {
        Objects.requireNonNull(targetId, "targetId");
        validateGameTick(gameTick);
        lastObservedGameTick = gameTick;

        boolean inWindow = isInWindowAt(statesByTarget.get(targetId), gameTick);
        if (skillEligible && inWindow) {
            return new InstantGuardResult(
                    true,
                    true,
                    blockedDamageMultiplier,
                    shieldDurabilityMultiplier
            );
        }
        return new InstantGuardResult(false, inWindow, NORMAL_MULTIPLIER, NORMAL_MULTIPLIER);
    }

    public int removeOffline(Collection<UUID> onlineTargetIds) {
        Objects.requireNonNull(onlineTargetIds, "onlineTargetIds");
        Set<UUID> retainedIds = new HashSet<>(onlineTargetIds.size());
        for (UUID targetId : onlineTargetIds) {
            retainedIds.add(Objects.requireNonNull(targetId, "onlineTargetId"));
        }

        int previousSize = statesByTarget.size();
        statesByTarget.keySet().retainAll(retainedIds);
        return previousSize - statesByTarget.size();
    }

    public void clear() {
        statesByTarget.clear();
        lastObservedGameTick = -1L;
    }

    private boolean isInWindowAt(GuardState state, long gameTick) {
        if (state == null || !state.guarding() || state.guardStartTick() < 0L) {
            return false;
        }
        long guardTick = gameTick - state.guardStartTick();
        return guardTick >= windowStartTick && guardTick <= windowEndTick;
    }

    private void validateGameTick(long gameTick) {
        if (gameTick < 0L) {
            throw new IllegalArgumentException("gameTick must be non-negative");
        }
        if (gameTick < lastObservedGameTick) {
            throw new IllegalArgumentException("gameTick must not move backwards");
        }
    }

    private record GuardState(boolean guarding, long guardStartTick, long lastRaiseTick) {
        private static GuardState raisedAt(long gameTick) {
            return new GuardState(true, gameTick, gameTick);
        }

        private GuardState raisedAt(long gameTick, boolean intervalSatisfied) {
            return new GuardState(true, intervalSatisfied ? gameTick : -1L, gameTick);
        }

        private GuardState lowered() {
            return new GuardState(false, -1L, lastRaiseTick);
        }
    }

    public record InstantGuardResult(
            boolean successful,
            boolean inWindow,
            double damageMultiplier,
            double shieldDurabilityMultiplier
    ) {
    }
}