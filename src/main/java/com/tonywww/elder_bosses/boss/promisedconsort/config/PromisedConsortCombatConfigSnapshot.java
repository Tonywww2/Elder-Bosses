package com.tonywww.elder_bosses.boss.promisedconsort.config;

import com.tonywww.elder_bosses.combat.damage.DamageFormula;
import com.tonywww.elder_bosses.combat.state.StaggerTracker.DistanceBand;

import java.util.List;
import java.util.Objects;

public record PromisedConsortCombatConfigSnapshot(
        General general,
        Encounter encounter,
        IncomingDamage incomingDamage,
        Multiplayer multiplayer,
        Arena arena,
        Targeting targeting,
        Presentation presentation,
        Stagger stagger,
        InstantGuard instantGuard,
        Selector selector,
        PhaseTransition phaseTransition,
        Meteor meteor,
        LightEcho lightEcho,
        Visuals visuals,
        Performance performance,
        Dialogue dialogue,
        NonverbalAudio nonverbalAudio
) {
    public PromisedConsortCombatConfigSnapshot {
        Objects.requireNonNull(general, "general");
        Objects.requireNonNull(encounter, "encounter");
        Objects.requireNonNull(incomingDamage, "incomingDamage");
        Objects.requireNonNull(multiplayer, "multiplayer");
        Objects.requireNonNull(arena, "arena");
        Objects.requireNonNull(targeting, "targeting");
        Objects.requireNonNull(presentation, "presentation");
        Objects.requireNonNull(stagger, "stagger");
        Objects.requireNonNull(instantGuard, "instantGuard");
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(phaseTransition, "phaseTransition");
        Objects.requireNonNull(meteor, "meteor");
        Objects.requireNonNull(lightEcho, "lightEcho");
        Objects.requireNonNull(visuals, "visuals");
        Objects.requireNonNull(performance, "performance");
        Objects.requireNonNull(dialogue, "dialogue");
        Objects.requireNonNull(nonverbalAudio, "nonverbalAudio");
    }

    public record General(
            double baseHealth,
            double attackDamage,
            double movementSpeed,
            double followRange,
            double knockbackResistance,
            double phaseTwoHealthRatio,
            double meteorHealthRatio,
            int maxActivePlayers,
            double healthPerExtraPlayer
    ) {
        public static General defaults() {
            return new General(1600.0, 24.0, 0.30, 96.0, 1.0, 0.65, 0.85, 4, 0.55);
        }
    }

    public record Encounter(
            String wakeSourcePolicy,
            String dormantDamagePolicy,
            boolean joinOnPlayerHit,
            boolean joinOnBossHit,
            String playerFirstHitMode,
            String bossFirstHitMode,
            String bossHitAtFullRoster,
            String overflowPlayerPolicy,
            String rosterSlotPolicy,
            String scalingCountMode,
            boolean allowJoinDuringDisengage,
            boolean rejoinAfterDeath,
            boolean rejoinAfterDisconnect,
            boolean rejoinAfterBoundaryExit,
            boolean rejoinAfterDimensionChange,
            double disengageRadius,
            int disengageGraceTicks,
            String disengageBehavior,
            String cooldownResumePolicy,
            String restartPolicy,
            String unloadPolicy,
            String peacefulPolicy,
            String overlapPolicy,
            boolean persistDormant
    ) {
    }

    public record IncomingDamage(
            String sourcePolicy,
            boolean forcedDeathBypassesPolicy
    ) {
    }

    public record Multiplayer(
            double damageMultiplierPerExtraPlayer,
            int retargetIntervalTicks,
            int sameTargetPenaltyAfterTicks,
            double sameTargetScoreMultiplier
    ) {
    }

    public record Arena(
            double logicalRadius,
            Offset introOffset,
            Offset phaseReturnOffset,
            boolean allowBlockBreaking,
            String breakableBlockTag,
            String protectedBlockTag,
            double blockBreakRadius,
            int maxBlocksBrokenPerTick,
            boolean allowWallPhasing,
            int wallPhaseMaxTicks,
            String wallPhaseFailure
    ) {
    }

    public record Offset(double x, double y, double z) {
    }

    public record Targeting(
            double distanceWeight,
            double recentDamageWeight,
            double itemUseWeight,
            int recentDamageWindowTicks,
            boolean targetUnregisteredPlayers,
            boolean targetCreativePlayers,
            boolean creativePlayersCanJoin,
            String primaryTargetPolicy,
                        String attackTargetPolicy,
                        double maxSegmentPursuitDistance,
                        double rangedDamageDistance,
                        PromisedConsortRangedConfig rangedCounter
    ) {
                public Targeting {
                        if (!Double.isFinite(rangedDamageDistance) || rangedDamageDistance <= 0 || rangedDamageDistance > 2048)
                                throw new IllegalArgumentException("rangedDamageDistance must be between 0 (exclusive) and 2048 blocks");
                        if (rangedCounter == null) rangedCounter = PromisedConsortRangedConfig.defaults();
                        if (!Double.isFinite(maxSegmentPursuitDistance) || maxSegmentPursuitDistance < 0 || maxSegmentPursuitDistance > 16) {
                                throw new IllegalArgumentException("maxSegmentPursuitDistance must be between 0 and 16 blocks");
                        }
                }

                public Targeting(double distanceWeight, double recentDamageWeight, double itemUseWeight, int recentDamageWindowTicks,
                                 boolean targetUnregisteredPlayers, boolean targetCreativePlayers, boolean creativePlayersCanJoin,
                                 String primaryTargetPolicy, String attackTargetPolicy, double maxSegmentPursuitDistance) {
                        this(distanceWeight, recentDamageWeight, itemUseWeight, recentDamageWindowTicks, targetUnregisteredPlayers,
                                targetCreativePlayers, creativePlayersCanJoin, primaryTargetPolicy, attackTargetPolicy, maxSegmentPursuitDistance, 9.0,
                                PromisedConsortRangedConfig.defaults());
                }

                public Targeting(double distanceWeight, double recentDamageWeight, double itemUseWeight, int recentDamageWindowTicks,
                                                 boolean targetUnregisteredPlayers, boolean targetCreativePlayers, boolean creativePlayersCanJoin,
                                                 String primaryTargetPolicy, String attackTargetPolicy) {
                        this(distanceWeight, recentDamageWeight, itemUseWeight, recentDamageWindowTicks, targetUnregisteredPlayers,
                                        targetCreativePlayers, creativePlayersCanJoin, primaryTargetPolicy, attackTargetPolicy, 3.0);
                }
    }

    public record Presentation(String bossBarAudience, String staggerHudAudience) {
    }

    public record Stagger(
            double damageConversionRatio,
            double capacityHealthRatio,
            int rapidWindowTicks,
            double rapidFraction,
            List<DistanceBand> distanceBands,
            int sourceDedupeTicks,
            int decayDelayTicks,
            double decayPerTick,
            int stunTicks,
            int postStunImmunityTicks,
            boolean resetOnPhaseChange,
            String generatedHazardsOnStun,
            String pendingHazardsOnStun
    ) {
        public Stagger {
            distanceBands = List.copyOf(distanceBands);
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
            String cueSound,
            String eligibleItemTag,
            double cueVolume,
            double cuePitch,
            int cueCooldownTicks
    ) {
    }

    public record Selector(
            int avoidLastActionCount,
            double itemUsePunishMinRange,
            double itemUsePunishMaxRange,
            double itemUseWeightMultiplier,
            double rangedWeightMultiplier,
            double crowdWeightMultiplier,
            double missRecoveryWeightMultiplier,
            double blockedBranchChance,
            String blockedBranchMode,
            int guardChainThreshold,
            String guardChainScope,
                        int guardChainRecoveryTicks,
                        com.tonywww.elder_bosses.boss.promisedconsort.controller.PromisedConsortBurstCadence.Settings burst
    ) {
                public Selector {
                        if (burst == null) burst = com.tonywww.elder_bosses.boss.promisedconsort.controller.PromisedConsortBurstCadence.Settings.defaults();
                }

                public Selector(int avoidLastActionCount, double itemUsePunishMinRange, double itemUsePunishMaxRange,
                                                double itemUseWeightMultiplier, double rangedWeightMultiplier, double crowdWeightMultiplier,
                                                double missRecoveryWeightMultiplier, double blockedBranchChance, String blockedBranchMode,
                                                int guardChainThreshold, String guardChainScope, int guardChainRecoveryTicks) {
                        this(avoidLastActionCount, itemUsePunishMinRange, itemUsePunishMaxRange, itemUseWeightMultiplier,
                                        rangedWeightMultiplier, crowdWeightMultiplier, missRecoveryWeightMultiplier, blockedBranchChance,
                                        blockedBranchMode, guardChainThreshold, guardChainScope, guardChainRecoveryTicks, null);
                }
    }

    public record PhaseTransition(
            int durationTicks,
            int forcedRecoveryTicks,
            boolean damageGate,
            String damageGateMode,
            boolean clearOwnedHazards,
            int returnImpactTick,
            DamageFormula returnPhysicalDamage,
            DamageFormula returnHolyDamage
    ) {
    }

    public record Meteor(
            boolean damageGate,
            String damageGateMode,
            boolean acceptsStagger,
            boolean clearOwnedHazards,
            String repeatMode
    ) {
    }

    public record LightEcho(
            boolean enabled,
            int delayTicks,
            int telegraphTicks,
            int activeTicks,
            double width,
            double height,
            int maxLogicalColumns,
            DamageFormula damage
    ) {
    }

    public record Visuals(
            String gravityProjectileBlock,
            boolean visualClonesEnabled,
            String cloneRenderMode
    ) {
    }

    public record Performance(
            int maxLogicalProjectiles,
            int maxLogicalLightColumns,
            int maxVisualClones
    ) {
    }

    public record Dialogue(
            boolean enabled,
            int maxQueuedLines,
            int playerDefeatDelayTicks,
            int subtitleDurationTicks,
            int transitionCallTick,
            int phaseTwoVowTick,
            int defeatedTick,
            String audience
    ) {
    }

    public record NonverbalAudio(
            boolean enabled,
            double volume,
            double pitch,
            int introRoarTick,
            int victoryRoarDelayTicks,
            int hurtCooldownTicks
    ) {
    }

}
