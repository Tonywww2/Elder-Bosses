package com.tonywww.elder_bosses.boss.promisedconsort.config;

import com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedState;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import java.util.List;

public record PromisedConsortRangedConfig(boolean enabled, double enterDistance, double exitDistance,
        int farDwellTicks, int farDamageWindowTicks, double farDamageThreshold, int threatWindowTicks,
        int globalCooldownTicks, int defenseSharedCooldownTicks, int maxConsecutiveDefenses,
        boolean acceptOwnedProjectileEntity, List<String> damageTypeTags, List<String> additionalDamageTypeIds,
        List<String> excludedDamageTypeIds, List<String> excludedDamageTypeTags, List<String> additionalDamageTypeTags,
        double segmentAdjustmentBudgetMultiplier, double pursuitSelectionWeightMultiplier,
        double meleeSuppressionDistance, double meleeZeroWeightDistance, double distantMeleeWeightMultiplier,
        double pursuitIdleMultiplier) {
    public PromisedConsortRangedConfig(boolean enabled, double enterDistance, double exitDistance, int farDwellTicks,
            int farDamageWindowTicks, double farDamageThreshold, int threatWindowTicks, int globalCooldownTicks,
            int defenseSharedCooldownTicks, int maxConsecutiveDefenses, boolean acceptOwnedProjectileEntity,
            List<String> damageTypeTags, List<String> additionalDamageTypeIds, List<String> excludedDamageTypeIds,
            List<String> excludedDamageTypeTags, List<String> additionalDamageTypeTags,
            double segmentAdjustmentBudgetMultiplier, double pursuitSelectionWeightMultiplier) {
        this(enabled, enterDistance, exitDistance, farDwellTicks, farDamageWindowTicks, farDamageThreshold, threatWindowTicks,
                globalCooldownTicks, defenseSharedCooldownTicks, maxConsecutiveDefenses, acceptOwnedProjectileEntity, damageTypeTags,
                additionalDamageTypeIds, excludedDamageTypeIds, excludedDamageTypeTags, additionalDamageTypeTags,
                segmentAdjustmentBudgetMultiplier, pursuitSelectionWeightMultiplier, 9, 16, 0.1, 0.5);
    }

    public PromisedConsortRangedConfig(boolean enabled, double enterDistance, double exitDistance, int farDwellTicks,
            int farDamageWindowTicks, double farDamageThreshold, int threatWindowTicks, int globalCooldownTicks,
            int defenseSharedCooldownTicks, int maxConsecutiveDefenses, boolean acceptOwnedProjectileEntity,
            List<String> damageTypeTags, List<String> additionalDamageTypeIds, List<String> excludedDamageTypeIds,
            List<String> excludedDamageTypeTags, List<String> additionalDamageTypeTags) {
        this(enabled,enterDistance,exitDistance,farDwellTicks,farDamageWindowTicks,farDamageThreshold,threatWindowTicks,
                globalCooldownTicks,defenseSharedCooldownTicks,maxConsecutiveDefenses,acceptOwnedProjectileEntity,damageTypeTags,
                additionalDamageTypeIds,excludedDamageTypeIds,excludedDamageTypeTags,additionalDamageTypeTags,2,2);
    }

    public PromisedConsortRangedConfig(boolean enabled, double enterDistance, double exitDistance, int farDwellTicks,
            int farDamageWindowTicks, double farDamageThreshold, int threatWindowTicks, int globalCooldownTicks,
            int defenseSharedCooldownTicks, int maxConsecutiveDefenses, boolean acceptOwnedProjectileEntity,
            List<String> damageTypeTags, List<String> additionalDamageTypeIds, List<String> excludedDamageTypeIds, List<String> excludedDamageTypeTags) {
        this(enabled,enterDistance,exitDistance,farDwellTicks,farDamageWindowTicks,farDamageThreshold,threatWindowTicks,
                globalCooldownTicks,defenseSharedCooldownTicks,maxConsecutiveDefenses,acceptOwnedProjectileEntity,damageTypeTags,
                additionalDamageTypeIds,excludedDamageTypeIds,excludedDamageTypeTags,List.of());
    }
    public PromisedConsortRangedConfig {
        new PromisedConsortRangedState.Rules(enterDistance, exitDistance, farDwellTicks, farDamageWindowTicks, farDamageThreshold, threatWindowTicks);
        if (globalCooldownTicks < 0 || defenseSharedCooldownTicks < 0 || maxConsecutiveDefenses < 1) {
            throw new IllegalArgumentException("Invalid ranged cooldowns");
        }
        if (!Double.isFinite(segmentAdjustmentBudgetMultiplier) || segmentAdjustmentBudgetMultiplier < 0 || segmentAdjustmentBudgetMultiplier > 16
                || !Double.isFinite(pursuitSelectionWeightMultiplier) || pursuitSelectionWeightMultiplier < 0 || pursuitSelectionWeightMultiplier > 16) {
            throw new IllegalArgumentException("Invalid ranged multipliers");
        }
        if (!Double.isFinite(meleeSuppressionDistance) || meleeSuppressionDistance < 0 || meleeSuppressionDistance > 256
                || !Double.isFinite(meleeZeroWeightDistance) || meleeZeroWeightDistance < meleeSuppressionDistance || meleeZeroWeightDistance > 256
                || !Double.isFinite(distantMeleeWeightMultiplier) || distantMeleeWeightMultiplier < 0 || distantMeleeWeightMultiplier > 1
                || !Double.isFinite(pursuitIdleMultiplier) || pursuitIdleMultiplier < 0 || pursuitIdleMultiplier > 16) {
            throw new IllegalArgumentException("Invalid ranged selection settings");
        }
        damageTypeTags = List.copyOf(damageTypeTags);
        additionalDamageTypeIds = List.copyOf(additionalDamageTypeIds);
        excludedDamageTypeIds = List.copyOf(excludedDamageTypeIds);
        excludedDamageTypeTags = List.copyOf(excludedDamageTypeTags);
        additionalDamageTypeTags = additionalDamageTypeTags == null ? List.of() : List.copyOf(additionalDamageTypeTags);
    }

    public PromisedConsortRangedState.Rules rules() {
        return new PromisedConsortRangedState.Rules(enterDistance, exitDistance, farDwellTicks, farDamageWindowTicks, farDamageThreshold, threatWindowTicks);
    }

    public double segmentAdjustmentBudget(double baseBudget, boolean rangedTarget) {
        return baseBudget * (enabled && rangedTarget ? segmentAdjustmentBudgetMultiplier : 1);
    }

    public double meleeWeightMultiplier(PromisedConsortActionId action, double distance) {
        boolean closeCombo = switch (action) {
            case L_COMBO_CROSS, L_COMBO_BLOODFLAME, R_COMBO_CROSS, R_COMBO_LEFT_TWIN, R_COMBO_TEMPEST, R_COMBO_EARTHHEAVE -> true;
            default -> false;
        };
        if (!enabled || !closeCombo || !Double.isFinite(distance) || distance < meleeSuppressionDistance) return 1;
        return distance >= meleeZeroWeightDistance ? 0 : distantMeleeWeightMultiplier;
    }

    public int idleTicks(int baseTicks, boolean rangedTarget) {
        if (baseTicks < 0) throw new IllegalArgumentException("Negative idle interval");
        return enabled && rangedTarget ? (int) Math.min(Integer.MAX_VALUE, Math.ceil(baseTicks * pursuitIdleMultiplier)) : baseTicks;
    }

    public static PromisedConsortRangedConfig defaults() {
        return new PromisedConsortRangedConfig(true, 9, 5, 100, 80, 1, 80, 60, 100, 1, true,
                List.of("minecraft:is_projectile"), List.of(), List.of(), List.of());
    }
}