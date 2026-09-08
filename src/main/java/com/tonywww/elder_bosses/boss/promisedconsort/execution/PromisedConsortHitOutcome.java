package com.tonywww.elder_bosses.boss.promisedconsort.execution;

import java.util.Objects;
import java.util.UUID;

public record PromisedConsortHitOutcome(
        UUID targetId,
        long actionSequence,
        String hitId,
        ContactType contactType,
        float healthDamage,
        boolean killedTarget
) {
    public PromisedConsortHitOutcome {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(hitId, "hitId");
        Objects.requireNonNull(contactType, "contactType");
        if (actionSequence < 0L || !Float.isFinite(healthDamage) || healthDamage < 0.0F) {
            throw new IllegalArgumentException("invalid hit outcome");
        }
    }

    public enum ContactType {
        CONTACT,
        BLOCKED,
        INSTANT_GUARDED,
        DAMAGED
    }
}
