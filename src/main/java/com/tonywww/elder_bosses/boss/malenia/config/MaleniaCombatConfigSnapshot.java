package com.tonywww.elder_bosses.boss.malenia.config;

import com.tonywww.elder_bosses.combat.damage.DamageFormula;
import com.tonywww.elder_bosses.combat.state.StaggerTracker.DistanceBand;
import com.tonywww.elder_bosses.network.NetworkLimits;

import java.util.List;
import java.util.Objects;

public record MaleniaCombatConfigSnapshot(
        General general,
    PhaseResistances resistance,
    PhaseSourceMultipliers sourceMultiplier,
        Multiplayer multiplayer,
    Targeting targeting,
    Selector selector,
        Healing healing,
        Stagger stagger,
        InstantGuard instantGuard,
        ScarletRot scarletRot,
        PhaseTransition phaseTransition,
        Performance performance,
        Dialogue dialogue,
        NonverbalAudio nonverbalAudio
) {
    public MaleniaCombatConfigSnapshot {
        Objects.requireNonNull(general, "general");
        Objects.requireNonNull(resistance, "resistance");
        Objects.requireNonNull(sourceMultiplier, "sourceMultiplier");
        Objects.requireNonNull(multiplayer, "multiplayer");
        Objects.requireNonNull(targeting, "targeting");
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(healing, "healing");
        Objects.requireNonNull(stagger, "stagger");
        Objects.requireNonNull(instantGuard, "instantGuard");
        Objects.requireNonNull(scarletRot, "scarletRot");
        Objects.requireNonNull(phaseTransition, "phaseTransition");
        Objects.requireNonNull(performance, "performance");
        Objects.requireNonNull(dialogue, "dialogue");
        Objects.requireNonNull(nonverbalAudio, "nonverbalAudio");
    }

    public MaleniaCombatConfigSnapshot(
            General general,
            Multiplayer multiplayer,
            Targeting targeting,
            Selector selector,
            Healing healing,
            Stagger stagger,
            InstantGuard instantGuard,
            ScarletRot scarletRot,
            PhaseTransition phaseTransition,
            Performance performance
    ) {
        this(
                general,
                defaultResistance(),
                defaultSourceMultiplier(),
                multiplayer,
                targeting,
                selector,
                healing,
                stagger,
                instantGuard,
                scarletRot,
                phaseTransition,
                performance,
                defaultDialogue(),
                defaultNonverbalAudio()
        );
    }

    public static Dialogue defaultDialogue() {
        return new Dialogue(true, 1, 70, 12, 20, 42, 28);
    }

    public static NonverbalAudio defaultNonverbalAudio() {
        return new NonverbalAudio(true, 1.0, 1.0, 20, 80);
    }

    public MaleniaCombatConfigSnapshot(
            General general,
            Multiplayer multiplayer,
            Healing healing,
            Stagger stagger,
            InstantGuard instantGuard,
            ScarletRot scarletRot,
            PhaseTransition phaseTransition,
            Performance performance
    ) {
        this(
                general,
                multiplayer,
                defaultTargeting(),
                defaultSelector(),
                healing,
                stagger,
                instantGuard,
                scarletRot,
                phaseTransition,
                performance
        );
    }

    public static PhaseResistances defaultResistance() {
        ResistanceProfile profile = new ResistanceProfile(0.90, 1.00, 0.80, 0.80);
        return new PhaseResistances(profile, profile);
    }

    public static PhaseSourceMultipliers defaultSourceMultiplier() {
        SourceMultiplierProfile profile = new SourceMultiplierProfile(
                1.00,
                1.00,
                1.00,
                1.00,
                0.75,
                1.00
        );
        return new PhaseSourceMultipliers(profile, profile);
    }

    private static Targeting defaultTargeting() {
        return new Targeting(0.45, 0.35, 0.10, 0.10, 160);
    }

    static Selector defaultSelector() {
        return new Selector(
                15,
                35,
                8,
                20,
                2,
                4.0,
                4.0,
                10.0,
                1.70,
                40,
                1.80,
                1.40,
                4.0,
                2,
                1.50,
                100,
                2,
                1.40,
                12.0,
                1.60,
                140
        );
    }

    public record General(
            double phaseOneHealth,
            double phaseTwoHealth,
            double phaseTwoStartRatio,
            double attackDamage,
            double movementSpeed,
            double followRange,
            double knockbackResistance,
            int maxActivePlayers
    ) {
        public General {
            requirePositiveFinite(phaseOneHealth, "general.phaseOneHealth");
            requirePositiveFinite(phaseTwoHealth, "general.phaseTwoHealth");
            requirePositiveFraction(phaseTwoStartRatio, "general.phaseTwoStartRatio");
            requireNonNegativeFinite(attackDamage, "general.attackDamage");
            requireNonNegativeFinite(movementSpeed, "general.movementSpeed");
            requirePositiveFinite(followRange, "general.followRange");
            requireFraction(knockbackResistance, "general.knockbackResistance");
            requirePositive(maxActivePlayers, "general.maxActivePlayers");
        }
    }

    public enum RoutedSource {
        ORDINARY_PHYSICAL,
        PIERCE,
        BLEED_TRIGGER,
        MAGIC,
        HOLY,
        FROST_TRIGGER
    }

    public record PhaseResistances(
            ResistanceProfile phaseOne,
            ResistanceProfile phaseTwo
    ) {
        public PhaseResistances {
            Objects.requireNonNull(phaseOne, "resistance.phaseOne");
            Objects.requireNonNull(phaseTwo, "resistance.phaseTwo");
        }

        public ResistanceProfile forPhase(boolean phaseTwoActive) {
            return phaseTwoActive ? phaseTwo : phaseOne;
        }
    }

    public record ResistanceProfile(
            double physical,
            double fire,
            double magic,
            double lightning
    ) {
        public ResistanceProfile {
            requireNonNegativeFinite(physical, "resistance.physical");
            requireNonNegativeFinite(fire, "resistance.fire");
            requireNonNegativeFinite(magic, "resistance.magic");
            requireNonNegativeFinite(lightning, "resistance.lightning");
        }

        public double multiplier(ResistanceCategory category) {
            return switch (Objects.requireNonNull(category, "category")) {
                case PHYSICAL -> physical;
                case FIRE -> fire;
                case MAGIC -> magic;
                case LIGHTNING -> lightning;
            };
        }
    }

    public enum ResistanceCategory {
        PHYSICAL,
        FIRE,
        MAGIC,
        LIGHTNING
    }

    public record PhaseSourceMultipliers(
            SourceMultiplierProfile phaseOne,
            SourceMultiplierProfile phaseTwo
    ) {
        public PhaseSourceMultipliers {
            Objects.requireNonNull(phaseOne, "sourceMultiplier.phaseOne");
            Objects.requireNonNull(phaseTwo, "sourceMultiplier.phaseTwo");
        }

        public SourceMultiplierProfile forPhase(boolean phaseTwoActive) {
            return phaseTwoActive ? phaseTwo : phaseOne;
        }
    }

    public record SourceMultiplierProfile(
            double ordinaryPhysical,
            double pierce,
            double bleedTrigger,
            double magic,
            double holy,
            double frostTrigger
    ) {
        public SourceMultiplierProfile {
            requireNonNegativeFinite(ordinaryPhysical, "sourceMultiplier.ordinaryPhysical");
            requireNonNegativeFinite(pierce, "sourceMultiplier.pierce");
            requireNonNegativeFinite(bleedTrigger, "sourceMultiplier.bleedTrigger");
            requireNonNegativeFinite(magic, "sourceMultiplier.magic");
            requireNonNegativeFinite(holy, "sourceMultiplier.holy");
            requireNonNegativeFinite(frostTrigger, "sourceMultiplier.frostTrigger");
        }

        public double multiplier(RoutedSource source) {
            return switch (Objects.requireNonNull(source, "source")) {
                case ORDINARY_PHYSICAL -> ordinaryPhysical;
                case PIERCE -> pierce;
                case BLEED_TRIGGER -> bleedTrigger;
                case MAGIC -> magic;
                case HOLY -> holy;
                case FROST_TRIGGER -> frostTrigger;
            };
        }
    }

    public record Multiplayer(
            double healthPerExtraPlayer,
            double healingWindowCapPerExtraPlayer,
            int retargetIntervalTicks,
            int sameTargetPenaltyAfterTicks,
            double sameTargetScoreMultiplier
    ) {
        public Multiplayer {
            requireNonNegativeFinite(healthPerExtraPlayer, "multiplayer.healthPerExtraPlayer");
            requireNonNegativeFinite(
                    healingWindowCapPerExtraPlayer,
                    "multiplayer.healingWindowCapPerExtraPlayer"
            );
            requirePositive(retargetIntervalTicks, "multiplayer.retargetIntervalTicks");
            requireNonNegative(
                    sameTargetPenaltyAfterTicks,
                    "multiplayer.sameTargetPenaltyAfterTicks"
            );
            requireFraction(sameTargetScoreMultiplier, "multiplayer.sameTargetScoreMultiplier");
        }
    }

        public record Targeting(
            double distanceWeight,
            double recentDamageWeight,
            double itemUseWeight,
            double interruptWeight,
            int recentDamageWindowTicks
        ) {
        public Targeting {
            requireNonNegativeFinite(distanceWeight, "targeting.distanceWeight");
            requireNonNegativeFinite(recentDamageWeight, "targeting.recentDamageWeight");
            requireNonNegativeFinite(itemUseWeight, "targeting.itemUseWeight");
            requireNonNegativeFinite(interruptWeight, "targeting.interruptWeight");
            if (distanceWeight + recentDamageWeight + itemUseWeight + interruptWeight <= 0.0) {
            throw new IllegalArgumentException("targeting weights must include a positive value");
            }
            requirePositive(recentDamageWindowTicks, "targeting.recentDamageWindowTicks");
        }
        }

        public record Selector(
            int phaseOneIdleMinTicks,
            int phaseOneIdleMaxTicks,
            int phaseTwoIdleMinTicks,
            int phaseTwoIdleMaxTicks,
            int avoidLastActionCount,
                double waterfowlRetreatMaxRange,
            double itemUsePunishMinRange,
            double itemUsePunishMaxRange,
            double itemUseWeightMultiplier,
            int shieldKickAfterTicks,
            double shieldKickWeightMultiplier,
            double shieldGrabWeightMultiplier,
                double nearbyPlayerRange,
                int nearbyPlayerCountThreshold,
                double nearbyPlayerRetreatWeightMultiplier,
                int recentInterruptWindowTicks,
                int recentInterruptCountThreshold,
                double recentInterruptWeightMultiplier,
                double longRangeThreshold,
                double longRangeRunningSlashWeightMultiplier,
            int highThreatGroupCooldownTicks
        ) {
        public Selector {
            requireRange(
                phaseOneIdleMinTicks,
                phaseOneIdleMaxTicks,
                "selector.phaseOneIdle"
            );
            requireRange(
                phaseTwoIdleMinTicks,
                phaseTwoIdleMaxTicks,
                "selector.phaseTwoIdle"
            );
            requireNonNegative(avoidLastActionCount, "selector.avoidLastActionCount");
                requireNonNegativeFinite(
                    waterfowlRetreatMaxRange,
                    "selector.waterfowlRetreatMaxRange"
                );
            requireNonNegativeFinite(
                itemUsePunishMinRange,
                "selector.itemUsePunishMinRange"
            );
            requireNonNegativeFinite(
                itemUsePunishMaxRange,
                "selector.itemUsePunishMaxRange"
            );
            if (itemUsePunishMinRange > itemUsePunishMaxRange) {
            throw new IllegalArgumentException(
                "selector.itemUsePunishMinRange must not exceed itemUsePunishMaxRange"
            );
            }
            requireNonNegativeFinite(
                itemUseWeightMultiplier,
                "selector.itemUseWeightMultiplier"
            );
            requireNonNegative(shieldKickAfterTicks, "selector.shieldKickAfterTicks");
            requireNonNegativeFinite(
                shieldKickWeightMultiplier,
                "selector.shieldKickWeightMultiplier"
            );
            requireNonNegativeFinite(
                shieldGrabWeightMultiplier,
                "selector.shieldGrabWeightMultiplier"
            );
                requireNonNegativeFinite(nearbyPlayerRange, "selector.nearbyPlayerRange");
                requirePositive(
                    nearbyPlayerCountThreshold,
                    "selector.nearbyPlayerCountThreshold"
                );
                requireNonNegativeFinite(
                    nearbyPlayerRetreatWeightMultiplier,
                    "selector.nearbyPlayerRetreatWeightMultiplier"
                );
                requirePositive(
                    recentInterruptWindowTicks,
                    "selector.recentInterruptWindowTicks"
                );
                requirePositive(
                    recentInterruptCountThreshold,
                    "selector.recentInterruptCountThreshold"
                );
                requireNonNegativeFinite(
                    recentInterruptWeightMultiplier,
                    "selector.recentInterruptWeightMultiplier"
                );
                requireNonNegativeFinite(longRangeThreshold, "selector.longRangeThreshold");
                requireNonNegativeFinite(
                    longRangeRunningSlashWeightMultiplier,
                    "selector.longRangeRunningSlashWeightMultiplier"
                );
            requireNonNegative(
                highThreatGroupCooldownTicks,
                "selector.highThreatGroupCooldownTicks"
            );
        }
        }

    public record Healing(
            boolean enabled,
            double blockedHitMultiplier,
            boolean healFromPlayers,
            boolean healFromNonHostileEntities,
            boolean healFromTamedEntities,
            boolean healFromSummons,
            boolean healFromArmorStands,
            double actionCap,
            int windowTicks,
            double windowCap,
            DamageFormula standardHeal,
            DamageFormula heavyHeal,
            DamageFormula waterfowlHeal,
            DamageFormula grabHeal
    ) {
        public Healing {
            requireNonNegativeFinite(blockedHitMultiplier, "healing.blockedHitMultiplier");
            requireNonNegativeFinite(actionCap, "healing.actionCap");
            requirePositive(windowTicks, "healing.windowTicks");
            requireNonNegativeFinite(windowCap, "healing.windowCap");
            Objects.requireNonNull(standardHeal, "healing.standardHeal");
            Objects.requireNonNull(heavyHeal, "healing.heavyHeal");
            Objects.requireNonNull(waterfowlHeal, "healing.waterfowlHeal");
            Objects.requireNonNull(grabHeal, "healing.grabHeal");
        }
    }

    public record Stagger(
            double damageConversionRatio,
            double capacityHealthRatio,
            List<DistanceBand> distanceBands,
            int sourceDedupeTicks,
            int decayDelayTicks,
            double decayPerTick,
            int stunTicks,
            int postStunImmunityTicks,
            boolean resetOnPhaseChange
    ) {
        public Stagger {
            requireFraction(damageConversionRatio, "stagger.damageConversionRatio");
            requirePositiveFraction(capacityHealthRatio, "stagger.capacityHealthRatio");
            distanceBands = validateDistanceBands(distanceBands);
            requireNonNegative(sourceDedupeTicks, "stagger.sourceDedupeTicks");
            requireNonNegative(decayDelayTicks, "stagger.decayDelayTicks");
            requireNonNegativeFinite(decayPerTick, "stagger.decayPerTick");
            requirePositive(stunTicks, "stagger.stunTicks");
            requirePositive(postStunImmunityTicks, "stagger.postStunImmunityTicks");
        }
    }

    public record InstantGuard(
            boolean enabled,
            int startTick,
            int endTick,
            int rearmTicks,
            double blockedDamageMultiplier,
            double shieldDurabilityMultiplier,
            int defaultCueLeadTicks,
            int cuePulseCount,
            String redCueColor,
            String eligibleItemTag
    ) {
        public InstantGuard {
            requireNonNegative(startTick, "instantGuard.startTick");
            requireNonNegative(endTick, "instantGuard.endTick");
            if (startTick > endTick) {
                throw new IllegalArgumentException(
                        "instantGuard.startTick must be less than or equal to instantGuard.endTick"
                );
            }
            if (rearmTicks < 4) {
                throw new IllegalArgumentException("instantGuard.rearmTicks must be at least 4");
            }
            requireFraction(blockedDamageMultiplier, "instantGuard.blockedDamageMultiplier");
            requireNonNegativeFinite(
                    shieldDurabilityMultiplier,
                    "instantGuard.shieldDurabilityMultiplier"
            );
            requireNonNegative(defaultCueLeadTicks, "instantGuard.defaultCueLeadTicks");
            requirePositive(cuePulseCount, "instantGuard.cuePulseCount");
            requireNonBlank(redCueColor, "instantGuard.redCueColor");
            requireNonBlank(eligibleItemTag, "instantGuard.eligibleItemTag");
        }

        public String cueSound() {
            return "elder_bosses:malenia.instant_guard_cue";
        }
    }

    public record ScarletRot(
            int decayDelayTicks,
            double decayPerTwentyTicks,
            int durationTicks,
            int damageIntervalTicks,
            DamageFormula damage,
            double healingReduction,
            double movementSpeedReduction,
            double honeyBuildupReduction,
            boolean consumeCleanseItem,
            int cleanseUseTicks,
            boolean milkClearsRot
    ) {
        public ScarletRot {
            requireNonNegative(decayDelayTicks, "scarletRot.decayDelayTicks");
            requireNonNegativeFinite(decayPerTwentyTicks, "scarletRot.decayPerTwentyTicks");
            requirePositiveTick(durationTicks, "scarletRot.durationTicks");
            requirePositiveTick(damageIntervalTicks, "scarletRot.damageIntervalTicks");
            if (damageIntervalTicks > durationTicks) {
                throw new IllegalArgumentException(
                        "scarletRot.damageIntervalTicks must not exceed scarletRot.durationTicks"
                );
            }
            Objects.requireNonNull(damage, "scarletRot.damage");
            requireFraction(healingReduction, "scarletRot.healingReduction");
            requireFraction(movementSpeedReduction, "scarletRot.movementSpeedReduction");
            requireNonNegativeFinite(
                    honeyBuildupReduction,
                    "scarletRot.honeyBuildupReduction"
            );
            requirePositive(cleanseUseTicks, "scarletRot.cleanseUseTicks");
        }
    }

    public record PhaseTransition(
            int durationTicks,
            boolean clearOwnedSlashHazards,
            boolean preservePlayerRotBuildup,
            boolean resetStagger,
            boolean openingAeonia
    ) {
        public PhaseTransition {
            requirePositiveTick(durationTicks, "phaseTransition.durationTicks");
        }
    }

        public record Performance(int maxRotZones) {
        public Performance {
            requireNonNegative(maxRotZones, "performance.maxRotZones");
        }
    }

    public record Dialogue(
            boolean enabled,
            int maxQueuedLines,
            int subtitleDurationTicks,
            int playerDefeatDelayTicks,
            int introWarningTick,
            int transitionReleaseTick,
            int defeatedTick
    ) {
        public Dialogue {
            requireNonNegative(maxQueuedLines, "dialogue.maxQueuedLines");
            requirePositiveTick(subtitleDurationTicks, "dialogue.subtitleDurationTicks");
            requireNonNegativeTick(playerDefeatDelayTicks, "dialogue.playerDefeatDelayTicks");
            requireNonNegativeTick(introWarningTick, "dialogue.introWarningTick");
            requireNonNegativeTick(transitionReleaseTick, "dialogue.transitionReleaseTick");
            requireNonNegativeTick(defeatedTick, "dialogue.defeatedTick");
        }
    }

    public record NonverbalAudio(
            boolean enabled,
            double volume,
            double pitch,
            int hurtCooldownTicks,
            int gruntCooldownTicks
    ) {
        public NonverbalAudio {
            requireNonNegativeFinite(volume, "nonverbalAudio.volume");
            requireNonNegativeFinite(pitch, "nonverbalAudio.pitch");
            requireNonNegative(hurtCooldownTicks, "nonverbalAudio.hurtCooldownTicks");
            requireNonNegative(gruntCooldownTicks, "nonverbalAudio.gruntCooldownTicks");
        }
    }

    private static List<DistanceBand> validateDistanceBands(List<DistanceBand> distanceBands) {
        Objects.requireNonNull(distanceBands, "stagger.distanceBands");
        if (distanceBands.isEmpty()) {
            throw new IllegalArgumentException("stagger.distanceBands must not be empty");
        }

        double previousMaximum = 0.0;
        for (DistanceBand distanceBand : distanceBands) {
            Objects.requireNonNull(distanceBand, "stagger.distanceBands element");
            if (!(distanceBand.maximumDistance() > previousMaximum)) {
                throw new IllegalArgumentException(
                        "stagger.distanceBands maximumDistance values must be strictly increasing"
                );
            }
            previousMaximum = distanceBand.maximumDistance();
        }
        return List.copyOf(distanceBands);
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

    private static void requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0.0 and 1.0");
        }
    }

    private static void requirePositiveFraction(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be greater than 0.0 and at most 1.0");
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

    private static void requireNonNegativeTick(int value, String name) {
        requireNonNegative(value, name);
        if (value > NetworkLimits.MAX_TICKS) {
            throw new IllegalArgumentException(
                    name + " must not exceed " + NetworkLimits.MAX_TICKS
            );
        }
    }

    private static void requireRange(int minimum, int maximum, String name) {
        requireNonNegative(minimum, name + " minimum");
        if (maximum < minimum) {
            throw new IllegalArgumentException(name + " maximum must not be less than minimum");
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}