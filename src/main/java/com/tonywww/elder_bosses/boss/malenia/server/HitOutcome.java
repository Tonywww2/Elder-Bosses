package com.tonywww.elder_bosses.boss.malenia.server;

import com.tonywww.elder_bosses.boss.malenia.config.MaleniaSkillConfigSnapshot.HealProfile;
import com.tonywww.elder_bosses.combat.damage.DamageChannel;

import java.util.Objects;
import java.util.UUID;

public record HitOutcome(
        UUID targetId,
        long actionSequence,
        int actionTick,
        String hitIdSuffix,
        DamageChannel channel,
        ContactType contactType,
        float attemptedDamage,
        float healthDamage,
        float absorptionDamage,
        double rotBuildup,
        HealProfile healProfile,
        boolean killedTarget,
        boolean instantGuardEligible
) {
    public HitOutcome {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(hitIdSuffix, "hitIdSuffix");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(contactType, "contactType");
        Objects.requireNonNull(healProfile, "healProfile");
        if (actionSequence < 0L) {
            throw new IllegalArgumentException("actionSequence must be non-negative");
        }
        if (actionTick < 0) {
            throw new IllegalArgumentException("actionTick must be non-negative");
        }
        requireNonNegativeFinite(attemptedDamage, "attemptedDamage");
        requireNonNegativeFinite(healthDamage, "healthDamage");
        requireNonNegativeFinite(absorptionDamage, "absorptionDamage");
        requireNonNegativeFinite(rotBuildup, "rotBuildup");
    }

    public float resolvedDamage() {
        return healthDamage + absorptionDamage;
    }

    public boolean blocked() {
        return contactType == ContactType.BLOCKED;
    }

    private static void requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    public enum ContactType {
        CONTACT,
        BLOCKED,
        DAMAGED
    }
}