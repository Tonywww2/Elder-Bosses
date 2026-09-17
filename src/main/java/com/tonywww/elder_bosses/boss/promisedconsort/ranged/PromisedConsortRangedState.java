package com.tonywww.elder_bosses.boss.promisedconsort.ranged;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PromisedConsortRangedState {
    private final Map<UUID, PlayerHistory> players = new HashMap<>();
    private final Rules rules;

    public PromisedConsortRangedState(Rules rules) {
        this.rules = java.util.Objects.requireNonNull(rules);
    }

    public void observe(UUID player, long tick, double distance) {
        if (!Double.isFinite(distance) || distance < 0) return;
        PlayerHistory history = players.computeIfAbsent(player, unused -> new PlayerHistory());
        if (history.lastTick == tick) return;
        if (tick < history.lastTick) history.farTicks = 0;
        if (distance <= rules.exitDistance()) history.farTicks = 0;
        else if (distance >= rules.enterDistance()) {
            history.farTicks = history.lastTick < 0 || tick == history.lastTick + 1 ? Math.min(rules.dwellTicks(), history.farTicks + 1) : 1;
        }
        history.lastTick = tick;
        prune(history, tick);
    }

    public void damage(UUID player, long tick, double distance, double healthLoss, boolean excluded) {
        if (excluded || !Double.isFinite(distance) || distance < rules.enterDistance() || !positive(healthLoss)) return;
        PlayerHistory history = players.computeIfAbsent(player, unused -> new PlayerHistory());
        add(history.damage, tick, healthLoss);
        prune(history, tick);
    }

    public void threat(UUID player, long tick, double amount, boolean excluded) {
        if (excluded || !positive(amount)) return;
        PlayerHistory history = players.computeIfAbsent(player, unused -> new PlayerHistory());
        add(history.threats, tick, amount);
        prune(history, tick);
    }

    public boolean qualifies(UUID player, long tick, double distance) {
        if (!Double.isFinite(distance) || distance < rules.enterDistance()) return false;
        PlayerHistory history = players.get(player);
        if (history == null) return false;
        prune(history, tick);
        return history.farTicks >= rules.dwellTicks() && history.lastTick == tick
                || total(history.damage, tick, rules.damageWindowTicks()) >= rules.damageThreshold();
    }

    public double threatDamage(UUID player, long tick, int window) {
        PlayerHistory history = players.get(player);
        return history == null ? 0 : total(history.threats, tick, window);
    }

    public int threatAttempts(UUID player, long tick, int window) {
        PlayerHistory history = players.get(player);
        return history == null ? 0 : history.threats.stream().filter(event -> event.tick > tick - window && event.tick <= tick)
                .mapToInt(Event::attempts).sum();
    }

    public void retain(Set<UUID> eligiblePlayers) {
        players.keySet().retainAll(eligiblePlayers);
    }

    public void clear() {
        players.clear();
    }

    public List<SavedPlayer> save(long tick) {
        return players.entrySet().stream().map(entry -> {
            PlayerHistory history = entry.getValue();
            prune(history, tick);
            return new SavedPlayer(entry.getKey(), history.farTicks, history.lastTick == tick,
                    saved(history.damage, tick), saved(history.threats, tick));
        }).toList();
    }

    public void restore(List<SavedPlayer> saved, long tick) {
        players.clear();
        if (saved == null) return;
        for (SavedPlayer player : saved) {
            if (player == null || player.player() == null) continue;
            PlayerHistory history = new PlayerHistory();
            history.farTicks = Math.max(0, Math.min(rules.dwellTicks(), player.farTicks()));
            history.lastTick = player.observed() ? tick - 1 : -1;
            restoreEvents(history.damage, player.damage(), tick);
            restoreEvents(history.threats, player.threats(), tick);
            prune(history, tick);
            players.put(player.player(), history);
        }
    }

    public static double returnMultiplier(double absorbed, double capacity, double minimum, double maximum) {
        if (!Double.isFinite(absorbed) || !positive(capacity) || !Double.isFinite(minimum) || !Double.isFinite(maximum)
                || minimum < 0 || maximum < minimum) throw new IllegalArgumentException("Invalid absorption formula");
        return minimum + (maximum - minimum) * Math.max(0, Math.min(1, absorbed / capacity));
    }

    private static boolean positive(double value) {
        return Double.isFinite(value) && value > 0;
    }

    private static void add(Deque<Event> events, long tick, double amount) {
        if (!events.isEmpty() && events.getLast().tick == tick) {
            Event old = events.removeLast();
            events.addLast(new Event(tick, Math.min(Float.MAX_VALUE, old.amount + amount), old.attempts + 1));
        } else events.addLast(new Event(tick, amount, 1));
    }

    private void prune(PlayerHistory history, long tick) {
        history.damage.removeIf(event -> event.tick <= tick - rules.damageWindowTicks() || event.tick > tick);
        history.threats.removeIf(event -> event.tick <= tick - rules.threatWindowTicks() || event.tick > tick);
    }

    private static double total(Deque<Event> events, long tick, int window) {
        return events.stream().filter(event -> event.tick > tick - window && event.tick <= tick).mapToDouble(Event::amount).sum();
    }

    private static List<SavedEvent> saved(Deque<Event> events, long tick) {
        return events.stream().map(event -> new SavedEvent(tick - event.tick, event.amount, event.attempts)).toList();
    }

    private static void restoreEvents(Deque<Event> events, List<SavedEvent> saved, long tick) {
        if (saved == null) return;
        for (SavedEvent event : saved) if (event != null && event.age() >= 0 && positive(event.amount()) && event.attempts() > 0) {
            events.addLast(new Event(tick - event.age() - 1, event.amount(), event.attempts()));
        }
    }

    public record Rules(double enterDistance, double exitDistance, int dwellTicks, int damageWindowTicks,
                        double damageThreshold, int threatWindowTicks) {
        public Rules {
            if (!Double.isFinite(enterDistance) || !Double.isFinite(exitDistance) || exitDistance < 0 || enterDistance <= exitDistance
                    || dwellTicks < 1 || damageWindowTicks < 1 || !positive(damageThreshold) || threatWindowTicks < 1) {
                throw new IllegalArgumentException("Invalid ranged qualification rules");
            }
        }
    }

    public record SavedEvent(long age, double amount, int attempts) {}
    public record SavedPlayer(UUID player, int farTicks, boolean observed, List<SavedEvent> damage, List<SavedEvent> threats) {}
    private record Event(long tick, double amount, int attempts) {}
    private static final class PlayerHistory {
        private long lastTick = -1;
        private int farTicks;
        private final Deque<Event> damage = new ArrayDeque<>();
        private final Deque<Event> threats = new ArrayDeque<>();
    }
}