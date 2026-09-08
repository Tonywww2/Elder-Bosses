package com.tonywww.elder_bosses.boss.promisedconsort.runtime;

import com.tonywww.elder_bosses.boss.promisedconsort.action.PromisedConsortActionCatalog;
import com.tonywww.elder_bosses.boss.promisedconsort.action.PromisedConsortActionDefinition;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class PromisedConsortCooldowns {
    private final PromisedConsortActionCatalog catalog;
    private final Map<String, Long> readyAtByGroup = new HashMap<>();

    public PromisedConsortCooldowns(PromisedConsortActionCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public boolean isEligible(
            PromisedConsortActionId actionId,
            PromisedConsortPhase phase,
            long gameTick
    ) {
        PromisedConsortActionDefinition definition = catalog.get(actionId);
        return definition.isAvailableIn(phase)
                && catalog.skillConfig().get(actionId).enabled()
                && gameTick >= readyAtByGroup.getOrDefault(definition.cooldownGroup(), 0L);
    }

    public void recordStarted(PromisedConsortActionId actionId, long gameTick) {
        PromisedConsortActionDefinition definition = catalog.get(actionId);
        readyAtByGroup.put(
                definition.cooldownGroup(),
                saturatedAdd(gameTick, definition.cooldownTicks())
        );
    }

    public int remainingTicks(PromisedConsortActionId actionId, long gameTick) {
        long readyAt = readyAtByGroup.getOrDefault(catalog.get(actionId).cooldownGroup(), 0L);
        return readyAt <= gameTick ? 0 : (int) Math.min(Integer.MAX_VALUE, readyAt - gameTick);
    }

    public void clear() {
        readyAtByGroup.clear();
    }

    public void delayAll(int ticks) {
        if (ticks <= 0) {
            return;
        }
        readyAtByGroup.replaceAll((group, readyAt) -> saturatedAdd(readyAt, ticks));
    }

    public Map<String, Long> readyAtByGroup() {
        return Map.copyOf(readyAtByGroup);
    }

    public void restore(Map<String, Long> values) {
        readyAtByGroup.clear();
        values.forEach((group, readyAt) -> {
            if (group != null && !group.isBlank() && readyAt != null && readyAt >= 0L) {
                readyAtByGroup.put(group, readyAt);
            }
        });
    }

    private static long saturatedAdd(long value, int increment) {
        return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }
}
