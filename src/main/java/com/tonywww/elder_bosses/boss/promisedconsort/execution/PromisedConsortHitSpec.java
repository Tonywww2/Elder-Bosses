package com.tonywww.elder_bosses.boss.promisedconsort.execution;

import com.tonywww.elder_bosses.combat.damage.DamageFormula;

import java.util.Objects;

public record PromisedConsortHitSpec(
        String hitId,
        DamageFormula damage,
        DamageKind damageKind,
        boolean instantGuardEligible,
        int maxHitsPerTarget
) {
    public PromisedConsortHitSpec {
        Objects.requireNonNull(hitId, "hitId");
        Objects.requireNonNull(damage, "damage");
        Objects.requireNonNull(damageKind, "damageKind");
        if (hitId.isBlank() || maxHitsPerTarget <= 0) {
            throw new IllegalArgumentException("invalid hit specification");
        }
    }

    public enum DamageKind {
        PHYSICAL,
        MAGIC,
        HOLY,
        FIRE
    }
}
