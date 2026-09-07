package com.tonywww.elder_bosses.combat.state;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public final class HealingBudget {
    public static final double DEFAULT_ACTION_CAP = 16.0;
    public static final int DEFAULT_WINDOW_TICKS = 100;
    public static final double DEFAULT_BASE_WINDOW_CAP = 36.0;
    public static final double DEFAULT_WINDOW_CAP_PER_EXTRA_PLAYER = 0.15;
    public static final int DEFAULT_MAXIMUM_PLAYERS = 4;

    private final double actionCap;
    private final int windowTicks;
    private final double baseWindowCap;
    private final double windowCapPerExtraPlayer;
    private final int maximumPlayers;
    private final Deque<HealingEntry> windowEntries = new ArrayDeque<>();

    private long lastObservedGameTick = -1L;
    private long activeActionSequence = -1L;
    private double activeActionCap;
    private double activeActionConsumed;
    private double windowConsumed;

    public HealingBudget(
            double actionCap,
            int windowTicks,
            double baseWindowCap,
            double windowCapPerExtraPlayer,
            int maximumPlayers
    ) {
        this.actionCap = requireNonNegativeFinite(actionCap, "actionCap");
        if (windowTicks <= 0) {
            throw new IllegalArgumentException("windowTicks must be positive");
        }
        this.windowTicks = windowTicks;
        this.baseWindowCap = requireNonNegativeFinite(baseWindowCap, "baseWindowCap");
        this.windowCapPerExtraPlayer = requireNonNegativeFinite(
                windowCapPerExtraPlayer,
                "windowCapPerExtraPlayer"
        );
        if (maximumPlayers < 1) {
            throw new IllegalArgumentException("maximumPlayers must be positive");
        }
        this.maximumPlayers = maximumPlayers;
    }

    public static HealingBudget withDefaults() {
        return new HealingBudget(
                DEFAULT_ACTION_CAP,
                DEFAULT_WINDOW_TICKS,
                DEFAULT_BASE_WINDOW_CAP,
                DEFAULT_WINDOW_CAP_PER_EXTRA_PLAYER,
                DEFAULT_MAXIMUM_PLAYERS
        );
    }

    public static HealingBudget restore(PersistentState state, long gameTick) {
        PersistentState checkedState = requireValidPersistentState(state);
        requireNonNegativeGameTick(gameTick);
        HealingBudget budget = new HealingBudget(
                checkedState.actionCap(),
                checkedState.windowTicks(),
                checkedState.baseWindowCap(),
                checkedState.windowCapPerExtraPlayer(),
                checkedState.maximumPlayers()
        );
        for (WindowEntryState entry : checkedState.windowEntries()) {
            budget.windowEntries.addLast(new HealingEntry(gameTick - entry.ageTicks(), entry.amount()));
            budget.windowConsumed += entry.amount();
        }
        budget.lastObservedGameTick = gameTick;
        return budget;
    }

    public PersistentState persistentState(long gameTick) {
        validateGameTick(gameTick);
        expireWindow(gameTick);
        lastObservedGameTick = gameTick;

        List<WindowEntryState> entries = new ArrayList<>(windowEntries.size());
        for (HealingEntry entry : windowEntries) {
            entries.add(new WindowEntryState(Math.toIntExact(gameTick - entry.gameTick()), entry.amount()));
        }
        return new PersistentState(
                actionCap,
                windowTicks,
                baseWindowCap,
                windowCapPerExtraPlayer,
                maximumPlayers,
                entries
        );
    }

    public void beginAction(long actionSequence) {
        beginAction(actionSequence, actionCap);
    }

    public void beginAction(long actionSequence, double actionCapOverride) {
        if (actionSequence < 0L) {
            throw new IllegalArgumentException("actionSequence must be non-negative");
        }
        if (activeActionSequence >= 0L) {
            throw new IllegalStateException("an action is already active");
        }
        double checkedActionCap = requireNonNegativeFinite(actionCapOverride, "actionCapOverride");
        activeActionSequence = actionSequence;
        activeActionCap = Math.min(actionCap, checkedActionCap);
        activeActionConsumed = 0.0;
    }

    public ActionSummary endAction(long actionSequence) {
        requireActiveAction(actionSequence);
        ActionSummary summary = new ActionSummary(
                activeActionSequence,
                activeActionConsumed,
                activeActionCap
        );
        activeActionSequence = -1L;
        activeActionCap = 0.0;
        activeActionConsumed = 0.0;
        return summary;
    }

    public void abortAction() {
        activeActionSequence = -1L;
        activeActionCap = 0.0;
        activeActionConsumed = 0.0;
    }

    public HealingGrant consumeHighestCandidate(
            long actionSequence,
            long gameTick,
            int playerCount,
            double healingHeadroom,
            double... candidateHealing
    ) {
        requireActiveAction(actionSequence);
        validateGameTick(gameTick);
        int checkedPlayerCount = requirePlayerCount(playerCount);
        double checkedHeadroom = requireNonNegativeFinite(healingHeadroom, "healingHeadroom");
        double candidate = highestCandidate(candidateHealing);
        expireWindow(gameTick);

        double actionRemaining = Math.max(0.0, activeActionCap - activeActionConsumed);
        double currentWindowCap = windowCap(checkedPlayerCount);
        double windowRemaining = Math.max(0.0, currentWindowCap - windowConsumed);
        double granted = Math.min(
                checkedHeadroom,
                Math.min(candidate, Math.min(actionRemaining, windowRemaining))
        );
        if (granted > 0.0) {
            activeActionConsumed += granted;
            windowConsumed += granted;
            windowEntries.addLast(new HealingEntry(gameTick, granted));
        }
        lastObservedGameTick = gameTick;
        return new HealingGrant(
                actionSequence,
                gameTick,
                checkedPlayerCount,
                candidate,
                granted,
                Math.max(0.0, activeActionCap - activeActionConsumed),
                Math.max(0.0, currentWindowCap - windowConsumed)
        );
    }

    public BudgetSnapshot snapshot(long gameTick, int playerCount) {
        validateGameTick(gameTick);
        int checkedPlayerCount = requirePlayerCount(playerCount);
        expireWindow(gameTick);
        lastObservedGameTick = gameTick;
        double currentWindowCap = windowCap(checkedPlayerCount);
        double currentActionCap = activeActionSequence >= 0L ? activeActionCap : actionCap;
        return new BudgetSnapshot(
                activeActionSequence,
            currentActionCap,
                activeActionConsumed,
            Math.max(0.0, currentActionCap - activeActionConsumed),
                windowConsumed,
                currentWindowCap,
                Math.max(0.0, currentWindowCap - windowConsumed)
        );
    }

    public double windowCap(int playerCount) {
        int checkedPlayerCount = requirePlayerCount(playerCount);
        double multiplier = 1.0 + windowCapPerExtraPlayer * (checkedPlayerCount - 1);
        return requireNonNegativeFinite(baseWindowCap * multiplier, "scaledWindowCap");
    }

    public void clear() {
        windowEntries.clear();
        lastObservedGameTick = -1L;
        activeActionSequence = -1L;
        activeActionCap = 0.0;
        activeActionConsumed = 0.0;
        windowConsumed = 0.0;
    }

    private void expireWindow(long gameTick) {
        long oldestIncludedTick = gameTick - windowTicks + 1L;
        while (!windowEntries.isEmpty() && windowEntries.peekFirst().gameTick() < oldestIncludedTick) {
            windowConsumed -= windowEntries.removeFirst().amount();
        }
        if (windowConsumed < 0.0 && windowConsumed > -1.0e-9) {
            windowConsumed = 0.0;
        }
    }

    private void requireActiveAction(long actionSequence) {
        if (activeActionSequence < 0L) {
            throw new IllegalStateException("no action is active");
        }
        if (actionSequence != activeActionSequence) {
            throw new IllegalArgumentException("actionSequence does not match the active action");
        }
    }

    private void validateGameTick(long gameTick) {
        if (gameTick < 0L) {
            throw new IllegalArgumentException("gameTick must be non-negative");
        }
        if (gameTick < lastObservedGameTick) {
            throw new IllegalArgumentException("gameTick must not move backwards");
        }
    }

    private int requirePlayerCount(int playerCount) {
        if (playerCount < 1 || playerCount > maximumPlayers) {
            throw new IllegalArgumentException("playerCount must be between 1 and maximumPlayers");
        }
        return playerCount;
    }

    private static double highestCandidate(double[] candidateHealing) {
        if (candidateHealing == null) {
            throw new NullPointerException("candidateHealing");
        }
        double highest = 0.0;
        for (double candidate : candidateHealing) {
            highest = Math.max(highest, requireNonNegativeFinite(candidate, "candidateHealing"));
        }
        return highest;
    }

    private static double requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
        return value;
    }

    private static double requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }

    private static long requireNonNegativeGameTick(long gameTick) {
        if (gameTick < 0L) {
            throw new IllegalArgumentException("gameTick must be non-negative");
        }
        return gameTick;
    }

    private static PersistentState requireValidPersistentState(PersistentState state) {
        PersistentState checkedState = Objects.requireNonNull(state, "state");
        validatePersistentState(
                checkedState.actionCap(),
                checkedState.windowTicks(),
                checkedState.baseWindowCap(),
                checkedState.windowCapPerExtraPlayer(),
                checkedState.maximumPlayers(),
                checkedState.windowEntries()
        );
        return checkedState;
    }

    private static List<WindowEntryState> validatePersistentState(
            double actionCap,
            int windowTicks,
            double baseWindowCap,
            double windowCapPerExtraPlayer,
            int maximumPlayers,
            List<WindowEntryState> windowEntries
    ) {
        requireNonNegativeFinite(actionCap, "actionCap");
        if (windowTicks <= 0) {
            throw new IllegalArgumentException("windowTicks must be positive");
        }
        double checkedBaseWindowCap = requireNonNegativeFinite(baseWindowCap, "baseWindowCap");
        double checkedWindowCapPerExtraPlayer = requireNonNegativeFinite(
                windowCapPerExtraPlayer,
                "windowCapPerExtraPlayer"
        );
        if (maximumPlayers < 1) {
            throw new IllegalArgumentException("maximumPlayers must be positive");
        }
        double maximumWindowCap = requireNonNegativeFinite(
                checkedBaseWindowCap
                        * (1.0 + checkedWindowCapPerExtraPlayer * (maximumPlayers - 1)),
                "maximumWindowCap"
        );

        Objects.requireNonNull(windowEntries, "windowEntries");
        List<WindowEntryState> copiedEntries = new ArrayList<>(windowEntries.size());
        int previousAgeTicks = Integer.MAX_VALUE;
        double consumed = 0.0;
        for (int index = 0; index < windowEntries.size(); index++) {
            WindowEntryState entry = Objects.requireNonNull(
                    windowEntries.get(index),
                    "windowEntries[" + index + "]"
            );
            if (entry.ageTicks() >= windowTicks) {
                throw new IllegalArgumentException("window entry age must be less than windowTicks");
            }
            if (entry.ageTicks() > previousAgeTicks) {
                throw new IllegalArgumentException("window entries must be ordered from oldest to newest");
            }
            previousAgeTicks = entry.ageTicks();
            consumed = requireNonNegativeFinite(consumed + entry.amount(), "windowConsumed");
            copiedEntries.add(entry);
        }
        if (consumed > maximumWindowCap) {
            throw new IllegalArgumentException("windowConsumed must not exceed the maximum window cap");
        }
        return List.copyOf(copiedEntries);
    }

    private record HealingEntry(long gameTick, double amount) {
    }

    public record WindowEntryState(int ageTicks, double amount) {
        public WindowEntryState {
            if (ageTicks < 0) {
                throw new IllegalArgumentException("ageTicks must be non-negative");
            }
            requirePositiveFinite(amount, "amount");
        }
    }

    public record PersistentState(
            double actionCap,
            int windowTicks,
            double baseWindowCap,
            double windowCapPerExtraPlayer,
            int maximumPlayers,
            List<WindowEntryState> windowEntries
    ) {
        public PersistentState {
            windowEntries = validatePersistentState(
                    actionCap,
                    windowTicks,
                    baseWindowCap,
                    windowCapPerExtraPlayer,
                    maximumPlayers,
                    windowEntries
            );
        }
    }

    public record HealingGrant(
            long actionSequence,
            long gameTick,
            int playerCount,
            double highestCandidate,
            double granted,
            double actionRemaining,
            double windowRemaining
    ) {
    }

    public record ActionSummary(long actionSequence, double consumed, double cap) {
    }

    public record BudgetSnapshot(
            long activeActionSequence,
            double actionCap,
            double actionConsumed,
            double actionRemaining,
            double windowConsumed,
            double windowCap,
            double windowRemaining
    ) {
        public boolean hasActiveAction() {
            return activeActionSequence >= 0L;
        }
    }
}