package com.tonywww.elder_bosses.boss.malenia.config;

import com.tonywww.elder_bosses.combat.damage.DamageFormula;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.combat.action.SkillTuning;
import com.tonywww.elder_bosses.network.NetworkLimits;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record MaleniaSkillConfigSnapshot(
    PhaseTwoRot phaseTwoRot,
        SingleSlash singleSlash,
        DoubleSlash doubleSlash,
        RapidSlashes rapidSlashes,
        RunningSlash runningSlash,
        UpwardCombo upwardCombo,
        Kick kick,
        Thrust thrust,
        GrabImpale grabImpale,
        RetreatSlash retreatSlash,
        WaterfowlDance waterfowlDance,
        ScarletAeonia scarletAeonia,
        ScarletPlunge scarletPlunge,
        FlyingSlash flyingSlash,
        ScarletPhantoms scarletPhantoms,
        WingedSweep wingedSweep,
        Map<MaleniaActionId, SkillTuning> tunings
) {
    public MaleniaSkillConfigSnapshot {
        Objects.requireNonNull(phaseTwoRot, "phaseTwoRot");
        Objects.requireNonNull(singleSlash, "singleSlash");
        Objects.requireNonNull(doubleSlash, "doubleSlash");
        Objects.requireNonNull(rapidSlashes, "rapidSlashes");
        Objects.requireNonNull(runningSlash, "runningSlash");
        Objects.requireNonNull(upwardCombo, "upwardCombo");
        Objects.requireNonNull(kick, "kick");
        Objects.requireNonNull(thrust, "thrust");
        Objects.requireNonNull(grabImpale, "grabImpale");
        Objects.requireNonNull(retreatSlash, "retreatSlash");
        Objects.requireNonNull(waterfowlDance, "waterfowlDance");
        Objects.requireNonNull(scarletAeonia, "scarletAeonia");
        Objects.requireNonNull(scarletPlunge, "scarletPlunge");
        Objects.requireNonNull(flyingSlash, "flyingSlash");
        Objects.requireNonNull(scarletPhantoms, "scarletPhantoms");
        Objects.requireNonNull(wingedSweep, "wingedSweep");
        Objects.requireNonNull(tunings, "tunings");
        EnumMap<MaleniaActionId, SkillTuning> tuningCopy =
                new EnumMap<>(MaleniaActionId.class);
        tuningCopy.putAll(tunings);
        for (MaleniaActionId actionId : MaleniaActionId.values()) {
            Objects.requireNonNull(tuningCopy.get(actionId), "missing tuning for " + actionId);
        }
        tunings = Collections.unmodifiableMap(tuningCopy);
    }

    public SkillTuning tuning(MaleniaActionId actionId) {
        return tunings.get(Objects.requireNonNull(actionId, "actionId"));
    }

    public static Map<MaleniaActionId, SkillTuning> neutralTunings() {
        EnumMap<MaleniaActionId, SkillTuning> result = new EnumMap<>(MaleniaActionId.class);
        for (MaleniaActionId actionId : MaleniaActionId.values()) {
            result.put(actionId, SkillTuning.NEUTRAL);
        }
        return result;
    }

    public enum HealProfile {
        STANDARD,
        HEAVY,
        WATERFOWL,
        GRAB,
        NONE
    }

    public record PhaseTwoRot(
            double ordinarySwordBuildup,
            double heavyThrustBuildup,
            double waterfowlBuildup,
            double kickBuildup
    ) {
        public PhaseTwoRot {
            requireNonNegativeFinite(ordinarySwordBuildup, "phaseTwoRot.ordinarySwordBuildup");
            requireNonNegativeFinite(heavyThrustBuildup, "phaseTwoRot.heavyThrustBuildup");
            requireNonNegativeFinite(waterfowlBuildup, "phaseTwoRot.waterfowlBuildup");
            requireNonNegativeFinite(kickBuildup, "phaseTwoRot.kickBuildup");
        }
    }

    public record SingleSlash(
            boolean enabled,
            double weight,
            int cooldownTicks,
            double range,
            double arcDegrees,
            int windupTicks,
            int activeTicks,
            int recoveryTicks,
            DamageFormula damage,
            HealProfile healProfile
    ) {
        public SingleSlash {
            validateCommon(weight, cooldownTicks, healProfile, "singleSlash");
            requirePositiveFinite(range, "singleSlash.range");
            requireDegrees(arcDegrees, "singleSlash.arcDegrees");
            validateActionTicks(windupTicks, activeTicks, recoveryTicks, "singleSlash");
            Objects.requireNonNull(damage, "singleSlash.damage");
        }
    }

    public record DoubleSlash(
            boolean enabled,
            double weight,
            int cooldownTicks,
            double range,
            List<Integer> windupTicks,
            List<Integer> activeTicks,
            List<Integer> recoveryTicks,
            List<DamageFormula> damage,
            HealProfile healProfile
    ) {
        public DoubleSlash {
            validateCommon(weight, cooldownTicks, healProfile, "doubleSlash");
            requirePositiveFinite(range, "doubleSlash.range");
            windupTicks = copyPositiveIntegers(windupTicks, 2, "doubleSlash.windupTicks");
            activeTicks = copyPositiveIntegers(activeTicks, 2, "doubleSlash.activeTicks");
            recoveryTicks = copyPositiveIntegers(recoveryTicks, 2, "doubleSlash.recoveryTicks");
            validateActionTicks(windupTicks, activeTicks, recoveryTicks, "doubleSlash");
            damage = copyDamageFormulas(damage, 2, "doubleSlash.damage");
        }
    }

    public record RapidSlashes(
            boolean enabled,
            double weight,
            int cooldownTicks,
            double range,
            int windupTicks,
            int activeTicks,
            int recoveryTicks,
            DamageFormula openingDamage,
            DamageFormula finisherDamage,
            int openingHits,
            int finisherDelayTicks,
            HealProfile healProfile
    ) {
        public RapidSlashes {
            validateCommon(weight, cooldownTicks, healProfile, "rapidSlashes");
            requirePositiveFinite(range, "rapidSlashes.range");
            validateActionTicks(windupTicks, activeTicks, recoveryTicks, "rapidSlashes");
            Objects.requireNonNull(openingDamage, "rapidSlashes.openingDamage");
            Objects.requireNonNull(finisherDamage, "rapidSlashes.finisherDamage");
            if (openingHits != 3) {
                throw new IllegalArgumentException("rapidSlashes.openingHits must be 3");
            }
            requireTickWithinAction(
                    finisherDelayTicks,
                    windupTicks,
                    activeTicks,
                    recoveryTicks,
                    "rapidSlashes.finisherDelayTicks"
            );
        }
    }

    public record RunningSlash(
            boolean enabled,
            double weight,
            int cooldownTicks,
            double range,
            int windupTicks,
            int activeTicks,
            int recoveryTicks,
            DamageFormula damage,
            HealProfile healProfile
    ) {
        public RunningSlash {
            validateCommon(weight, cooldownTicks, healProfile, "runningSlash");
            requirePositiveFinite(range, "runningSlash.range");
            validateActionTicks(windupTicks, activeTicks, recoveryTicks, "runningSlash");
            Objects.requireNonNull(damage, "runningSlash.damage");
        }
    }

    public record UpwardCombo(
            boolean enabled,
            double weight,
            int cooldownTicks,
            double range,
            List<Integer> windupTicks,
            List<Integer> activeTicks,
            List<Integer> recoveryTicks,
            List<DamageFormula> damage,
            HealProfile healProfile
    ) {
        public UpwardCombo {
            validateCommon(weight, cooldownTicks, healProfile, "upwardCombo");
            requirePositiveFinite(range, "upwardCombo.range");
            windupTicks = copyPositiveIntegers(windupTicks, 2, "upwardCombo.windupTicks");
            activeTicks = copyPositiveIntegers(activeTicks, 2, "upwardCombo.activeTicks");
            recoveryTicks = copyPositiveIntegers(recoveryTicks, 2, "upwardCombo.recoveryTicks");
            validateActionTicks(windupTicks, activeTicks, recoveryTicks, "upwardCombo");
            damage = copyDamageFormulas(damage, 2, "upwardCombo.damage");
        }
    }

    public record Kick(
            boolean enabled,
            double weight,
            int cooldownTicks,
            double range,
            double arcDegrees,
            int windupTicks,
            int activeTicks,
            int recoveryTicks,
            DamageFormula damage,
            double shieldStaminaMultiplier,
            HealProfile healProfile,
            boolean hyperArmor
    ) {
        public Kick {
            validateCommon(weight, cooldownTicks, healProfile, "kick");
            requirePositiveFinite(range, "kick.range");
            requireDegrees(arcDegrees, "kick.arcDegrees");
            validateActionTicks(windupTicks, activeTicks, recoveryTicks, "kick");
            Objects.requireNonNull(damage, "kick.damage");
            requireNonNegativeFinite(shieldStaminaMultiplier, "kick.shieldStaminaMultiplier");
        }
    }

    public record Thrust(
            boolean enabled,
            double weight,
            int cooldownTicks,
            double range,
            double width,
            int windupTicks,
            int activeTicks,
            int recoveryTicks,
            DamageFormula damage,
            HealProfile healProfile
    ) {
        public Thrust {
            validateCommon(weight, cooldownTicks, healProfile, "thrust");
            requirePositiveFinite(range, "thrust.range");
            requirePositiveFinite(width, "thrust.width");
            validateActionTicks(windupTicks, activeTicks, recoveryTicks, "thrust");
            Objects.requireNonNull(damage, "thrust.damage");
        }
    }

    public record GrabImpale(
            boolean enabled,
            double weight,
            int cooldownTicks,
            double range,
            double width,
            int windupTicks,
            int activeTicks,
            int recoveryTicks,
            DamageFormula grabDamage,
            DamageFormula impaleDamage,
            DamageFormula throwDamage,
            HealProfile healProfile
    ) {
        public GrabImpale {
            validateCommon(weight, cooldownTicks, healProfile, "grabImpale");
            requirePositiveFinite(range, "grabImpale.range");
            requirePositiveFinite(width, "grabImpale.width");
            validateActionTicks(windupTicks, activeTicks, recoveryTicks, "grabImpale");
            Objects.requireNonNull(grabDamage, "grabImpale.grabDamage");
            Objects.requireNonNull(impaleDamage, "grabImpale.impaleDamage");
            Objects.requireNonNull(throwDamage, "grabImpale.throwDamage");
        }
    }

    public record RetreatSlash(
            boolean enabled,
            double weight,
            int cooldownTicks,
            double range,
            double retreatDistance,
            int windupTicks,
            int activeTicks,
            int recoveryTicks,
            DamageFormula damage,
            HealProfile healProfile
    ) {
        public RetreatSlash {
            validateCommon(weight, cooldownTicks, healProfile, "retreatSlash");
            requirePositiveFinite(range, "retreatSlash.range");
            requirePositiveFinite(retreatDistance, "retreatSlash.retreatDistance");
            validateActionTicks(windupTicks, activeTicks, recoveryTicks, "retreatSlash");
            Objects.requireNonNull(damage, "retreatSlash.damage");
        }
    }

    public record WaterfowlDance(
            boolean enabled,
            double weight,
            int cooldownTicks,
            int firstEligibleTicks,
            double phaseOneFirstHealthRatio,
            int phaseTwoOpeningDelayTicks,
            double minimumStartRange,
            int windupTicks,
            int activeTicks,
            int recoveryTicks,
            int burstCount,
            double burstWidth,
            List<Integer> burstLockTicks,
            List<Integer> burstMaxHitsPerTarget,
            List<Double> burstMaxTravel,
            DamageFormula slashDamage,
            double actionHealCap,
            HealProfile healProfile
    ) {
        public WaterfowlDance {
            validateCommon(weight, cooldownTicks, healProfile, "waterfowlDance");
            requireNonNegative(firstEligibleTicks, "waterfowlDance.firstEligibleTicks");
            requirePositiveFraction(
                    phaseOneFirstHealthRatio,
                    "waterfowlDance.phaseOneFirstHealthRatio"
            );
            requireNonNegative(
                    phaseTwoOpeningDelayTicks,
                    "waterfowlDance.phaseTwoOpeningDelayTicks"
            );
            requirePositiveFinite(minimumStartRange, "waterfowlDance.minimumStartRange");
            validateActionTicks(windupTicks, activeTicks, recoveryTicks, "waterfowlDance");
            if (burstCount != 4) {
                throw new IllegalArgumentException("waterfowlDance.burstCount must be 4");
            }
            requirePositiveFinite(burstWidth, "waterfowlDance.burstWidth");
            burstLockTicks = copyPositiveIntegers(
                    burstLockTicks,
                    burstCount,
                    "waterfowlDance.burstLockTicks"
            );
            burstMaxHitsPerTarget = copyPositiveIntegers(
                    burstMaxHitsPerTarget,
                    burstCount,
                    "waterfowlDance.burstMaxHitsPerTarget"
            );
            burstMaxTravel = copyPositiveDoubles(
                    burstMaxTravel,
                    burstCount,
                    "waterfowlDance.burstMaxTravel"
            );
            validateStrictlyIncreasing(burstLockTicks, "waterfowlDance.burstLockTicks");
            for (int burstLockTick : burstLockTicks) {
                requireTickWithinAction(
                        burstLockTick,
                        windupTicks,
                        activeTicks,
                        recoveryTicks,
                        "waterfowlDance.burstLockTicks"
                );
            }
            Objects.requireNonNull(slashDamage, "waterfowlDance.slashDamage");
            requireNonNegativeFinite(actionHealCap, "waterfowlDance.actionHealCap");
        }
    }

    public record ScarletAeonia(
            boolean enabled,
            double weight,
            int cooldownTicks,
            double radius,
            int windupTicks,
            int activeTicks,
            int recoveryTicks,
            int targetLockTick,
            int telegraphStartTick,
            DamageFormula diveDamage,
            DamageFormula explosionDamage,
            DamageFormula zoneDamage,
            double diveRotBuildup,
            double explosionRotBuildup,
            double zoneRotBuildup,
            int zoneDurationTicks,
            int zoneIntervalTicks,
            HealProfile healProfile
    ) {
        public ScarletAeonia {
            validateCommon(weight, cooldownTicks, healProfile, "scarletAeonia");
            requirePositiveFinite(radius, "scarletAeonia.radius");
            validateActionTicks(windupTicks, activeTicks, recoveryTicks, "scarletAeonia");
            requireTickWithinAction(
                    targetLockTick,
                    windupTicks,
                    activeTicks,
                    recoveryTicks,
                    "scarletAeonia.targetLockTick"
            );
            requireTickWithinAction(
                    telegraphStartTick,
                    windupTicks,
                    activeTicks,
                    recoveryTicks,
                    "scarletAeonia.telegraphStartTick"
            );
            if (targetLockTick > telegraphStartTick) {
                throw new IllegalArgumentException(
                        "scarletAeonia.targetLockTick must not exceed telegraphStartTick"
                );
            }
            Objects.requireNonNull(diveDamage, "scarletAeonia.diveDamage");
            Objects.requireNonNull(explosionDamage, "scarletAeonia.explosionDamage");
            Objects.requireNonNull(zoneDamage, "scarletAeonia.zoneDamage");
            requireNonNegativeFinite(diveRotBuildup, "scarletAeonia.diveRotBuildup");
            requireNonNegativeFinite(explosionRotBuildup, "scarletAeonia.explosionRotBuildup");
            requireNonNegativeFinite(zoneRotBuildup, "scarletAeonia.zoneRotBuildup");
            requirePositiveTick(zoneDurationTicks, "scarletAeonia.zoneDurationTicks");
            requirePositiveTick(zoneIntervalTicks, "scarletAeonia.zoneIntervalTicks");
            if (zoneIntervalTicks > zoneDurationTicks) {
                throw new IllegalArgumentException(
                        "scarletAeonia.zoneIntervalTicks must not exceed zoneDurationTicks"
                );
            }
        }
    }

    public record ScarletPlunge(
            boolean enabled,
            double weight,
            int cooldownTicks,
            double range,
            int windupTicks,
            int activeTicks,
            int recoveryTicks,
            DamageFormula bladeDamage,
            DamageFormula burstDamage,
            double bladeRotBuildup,
            double burstRotBuildup,
            HealProfile healProfile
    ) {
        public ScarletPlunge {
            validateCommon(weight, cooldownTicks, healProfile, "scarletPlunge");
            requirePositiveFinite(range, "scarletPlunge.range");
            validateActionTicks(windupTicks, activeTicks, recoveryTicks, "scarletPlunge");
            Objects.requireNonNull(bladeDamage, "scarletPlunge.bladeDamage");
            Objects.requireNonNull(burstDamage, "scarletPlunge.burstDamage");
            requireNonNegativeFinite(bladeRotBuildup, "scarletPlunge.bladeRotBuildup");
            requireNonNegativeFinite(burstRotBuildup, "scarletPlunge.burstRotBuildup");
        }
    }

    public record FlyingSlash(
            boolean enabled,
            double weight,
            int cooldownTicks,
            double range,
            List<Integer> windupTicks,
            List<Integer> activeTicks,
            List<Integer> recoveryTicks,
            List<DamageFormula> damage,
            List<Double> rotBuildup,
            HealProfile healProfile
    ) {
        public FlyingSlash {
            validateCommon(weight, cooldownTicks, healProfile, "flyingSlash");
            requirePositiveFinite(range, "flyingSlash.range");
            windupTicks = copyPositiveIntegers(windupTicks, 2, "flyingSlash.windupTicks");
            activeTicks = copyPositiveIntegers(activeTicks, 2, "flyingSlash.activeTicks");
            recoveryTicks = copyPositiveIntegers(recoveryTicks, 2, "flyingSlash.recoveryTicks");
            validateActionTicks(windupTicks, activeTicks, recoveryTicks, "flyingSlash");
            damage = copyDamageFormulas(damage, 2, "flyingSlash.damage");
            rotBuildup = copyPositiveDoubles(rotBuildup, 2, "flyingSlash.rotBuildup");
        }
    }

    public record ScarletPhantoms(
            boolean enabled,
            double weight,
            int cooldownTicks,
            int windupTicks,
            int activeTicks,
            int recoveryTicks,
            int phantomCount,
            double phantomWidth,
            int phantomIntervalTicks,
            int maxEarlyHitsPerTarget,
            int maxLateHitsPerTarget,
            DamageFormula phantomDamage,
            DamageFormula diveDamage,
            double phantomRotBuildup,
            double diveRotBuildup,
            HealProfile healProfile,
            boolean hyperArmor
    ) {
        public ScarletPhantoms {
            validateCommon(weight, cooldownTicks, healProfile, "scarletPhantoms");
            validateActionTicks(windupTicks, activeTicks, recoveryTicks, "scarletPhantoms");
            requirePositive(phantomCount, "scarletPhantoms.phantomCount");
            requirePositiveFinite(phantomWidth, "scarletPhantoms.phantomWidth");
            requirePositiveTick(phantomIntervalTicks, "scarletPhantoms.phantomIntervalTicks");
            long finalPhantomTick = (long) (phantomCount - 1) * phantomIntervalTicks;
            if (finalPhantomTick >= actionDuration(windupTicks, activeTicks, recoveryTicks)) {
                throw new IllegalArgumentException(
                        "scarletPhantoms phantom sequence must fit inside the action timeline"
                );
            }
            requirePositive(maxEarlyHitsPerTarget, "scarletPhantoms.maxEarlyHitsPerTarget");
            requirePositive(maxLateHitsPerTarget, "scarletPhantoms.maxLateHitsPerTarget");
            Objects.requireNonNull(phantomDamage, "scarletPhantoms.phantomDamage");
            Objects.requireNonNull(diveDamage, "scarletPhantoms.diveDamage");
            requireNonNegativeFinite(phantomRotBuildup, "scarletPhantoms.phantomRotBuildup");
            requireNonNegativeFinite(diveRotBuildup, "scarletPhantoms.diveRotBuildup");
        }
    }

    public record WingedSweep(
            boolean enabled,
            double weight,
            int cooldownTicks,
            double range,
            int windupTicks,
            int activeTicks,
            int recoveryTicks,
            DamageFormula damage,
            double rotBuildup,
            HealProfile healProfile
    ) {
        public WingedSweep {
            validateCommon(weight, cooldownTicks, healProfile, "wingedSweep");
            requirePositiveFinite(range, "wingedSweep.range");
            validateActionTicks(windupTicks, activeTicks, recoveryTicks, "wingedSweep");
            Objects.requireNonNull(damage, "wingedSweep.damage");
            requireNonNegativeFinite(rotBuildup, "wingedSweep.rotBuildup");
        }
    }

    private static void validateCommon(
            double weight,
            int cooldownTicks,
            HealProfile healProfile,
            String name
    ) {
        requireNonNegativeFinite(weight, name + ".weight");
        requirePositive(cooldownTicks, name + ".cooldownTicks");
        Objects.requireNonNull(healProfile, name + ".healProfile");
    }

    private static void validateActionTicks(
            int windupTicks,
            int activeTicks,
            int recoveryTicks,
            String name
    ) {
        requirePositiveTick(windupTicks, name + ".windupTicks");
        requirePositiveTick(activeTicks, name + ".activeTicks");
        requirePositiveTick(recoveryTicks, name + ".recoveryTicks");
        requireActionDuration(
                actionDuration(windupTicks, activeTicks, recoveryTicks),
                name
        );
    }

    private static void validateActionTicks(
            List<Integer> windupTicks,
            List<Integer> activeTicks,
            List<Integer> recoveryTicks,
            String name
    ) {
        long totalTicks = 0L;
        for (int stageIndex = 0; stageIndex < windupTicks.size(); stageIndex++) {
            int windup = windupTicks.get(stageIndex);
            int active = activeTicks.get(stageIndex);
            int recovery = recoveryTicks.get(stageIndex);
            requirePositiveTick(windup, name + ".windupTicks element");
            requirePositiveTick(active, name + ".activeTicks element");
            requirePositiveTick(recovery, name + ".recoveryTicks element");
            totalTicks += actionDuration(windup, active, recovery);
        }
        requireActionDuration(totalTicks, name);
    }

    private static long actionDuration(int windupTicks, int activeTicks, int recoveryTicks) {
        return (long) windupTicks + activeTicks + recoveryTicks;
    }

    private static void requireActionDuration(long totalTicks, String name) {
        if (totalTicks > NetworkLimits.MAX_TICKS) {
            throw new IllegalArgumentException(
                    name + " action duration exceeds " + NetworkLimits.MAX_TICKS + " ticks"
            );
        }
    }

    private static void requireTickWithinAction(
            int tick,
            int windupTicks,
            int activeTicks,
            int recoveryTicks,
            String name
    ) {
        requireNonNegative(tick, name);
        if (tick >= actionDuration(windupTicks, activeTicks, recoveryTicks)) {
            throw new IllegalArgumentException(name + " must be inside the action timeline");
        }
    }

    private static List<Integer> copyPositiveIntegers(
            List<Integer> values,
            int expectedSize,
            String name
    ) {
        Objects.requireNonNull(values, name);
        if (values.size() != expectedSize) {
            throw new IllegalArgumentException(name + " must contain " + expectedSize + " values");
        }
        for (Integer value : values) {
            Objects.requireNonNull(value, name + " element");
            requirePositive(value, name + " element");
        }
        return List.copyOf(values);
    }

    private static List<Double> copyPositiveDoubles(
            List<Double> values,
            int expectedSize,
            String name
    ) {
        Objects.requireNonNull(values, name);
        if (values.size() != expectedSize) {
            throw new IllegalArgumentException(name + " must contain " + expectedSize + " values");
        }
        for (Double value : values) {
            Objects.requireNonNull(value, name + " element");
            requirePositiveFinite(value, name + " element");
        }
        return List.copyOf(values);
    }

    private static List<DamageFormula> copyDamageFormulas(
            List<DamageFormula> values,
            int expectedSize,
            String name
    ) {
        Objects.requireNonNull(values, name);
        if (values.size() != expectedSize) {
            throw new IllegalArgumentException(name + " must contain " + expectedSize + " values");
        }
        for (DamageFormula value : values) {
            Objects.requireNonNull(value, name + " element");
        }
        return List.copyOf(values);
    }

    private static void validateStrictlyIncreasing(List<Integer> values, String name) {
        int previous = -1;
        for (int value : values) {
            if (value <= previous) {
                throw new IllegalArgumentException(name + " must be strictly increasing");
            }
            previous = value;
        }
    }

    private static void requireDegrees(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0 || value > 360.0) {
            throw new IllegalArgumentException(name + " must be greater than 0.0 and at most 360.0");
        }
    }

    private static void requirePositiveFraction(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be greater than 0.0 and at most 1.0");
        }
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requirePositiveTick(int value, String name) {
        requirePositive(value, name);
        if (value > NetworkLimits.MAX_TICKS) {
            throw new IllegalArgumentException(
                    name + " must not exceed " + NetworkLimits.MAX_TICKS
            );
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }
}