package com.tonywww.elder_bosses.boss.promisedconsort.execution;

import com.tonywww.elder_bosses.combat.damage.DamageFormula;

import java.util.Objects;

public record PromisedConsortHitSpec(
        String hitId,
        DamageFormula damage,
        DamageKind damageKind,
        boolean instantGuardEligible,
        int maxHitsPerTarget,
        java.util.Map<DamageKind,Double> channelWeights
) {
    public PromisedConsortHitSpec(String hitId,DamageFormula damage,DamageKind kind,boolean guard,int maxHits) {
        this(hitId,damage,kind,guard,maxHits,java.util.Map.of());
    }
    public PromisedConsortHitSpec {
        Objects.requireNonNull(hitId, "hitId");
        Objects.requireNonNull(damage, "damage");
        Objects.requireNonNull(damageKind, "damageKind");
        channelWeights=java.util.Map.copyOf(channelWeights);
        if(channelWeights.values().stream().anyMatch(v->!Double.isFinite(v)||v<=0)) throw new IllegalArgumentException("Invalid damage channels");
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
