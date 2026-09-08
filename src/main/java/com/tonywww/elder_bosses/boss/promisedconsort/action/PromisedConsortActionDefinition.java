package com.tonywww.elder_bosses.boss.promisedconsort.action;

import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase;
import com.tonywww.elder_bosses.combat.action.ActionTimeline;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record PromisedConsortActionDefinition(
        PromisedConsortActionId id,
        ActionTimeline timeline,
        double weight,
        int cooldownTicks,
        Set<PromisedConsortPhase> availablePhases,
        boolean hyperArmorActive,
        String cooldownGroup
) {
    public PromisedConsortActionDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(availablePhases, "availablePhases");
        Objects.requireNonNull(cooldownGroup, "cooldownGroup");
        if (!Double.isFinite(weight) || weight < 0.0 || cooldownTicks < 0) {
            throw new IllegalArgumentException("invalid action weight or cooldown");
        }
        if (availablePhases.isEmpty() || cooldownGroup.isBlank()) {
            throw new IllegalArgumentException("action requires phases and a cooldown group");
        }
        availablePhases = Collections.unmodifiableSet(EnumSet.copyOf(availablePhases));
    }

    public boolean isAvailableIn(PromisedConsortPhase phase) {
        return availablePhases.contains(Objects.requireNonNull(phase, "phase"));
    }
}
