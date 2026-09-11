package com.tonywww.elder_bosses.boss.promisedconsort.config;

import com.tonywww.elder_bosses.combat.damage.DamageFormula;
import com.tonywww.elder_bosses.combat.state.StaggerTracker.DistanceBand;

import java.util.List;
import java.util.Objects;

public record PromisedConsortCombatConfigSnapshot(
        General general,
        Encounter encounter,
        DamageRouting damageRouting,
        IncomingDamage incomingDamage,
        Multiplayer multiplayer,
        Arena arena,
        Targeting targeting,
        Presentation presentation,
        Stagger stagger,
        InstantGuard instantGuard,
        PhaseResistances resistance,
        PhaseSourceMultipliers sourceMultiplier,
        Status status,
        Selector selector,
        PhaseTransition phaseTransition,
        Meteor meteor,
        LightEcho lightEcho,
        Visuals visuals,
        Performance performance,
        Dialogue dialogue,
        NonverbalAudio nonverbalAudio,
        Rewards rewards
) {
    public PromisedConsortCombatConfigSnapshot {
        Objects.requireNonNull(general, "general");
        Objects.requireNonNull(encounter, "encounter");
        Objects.requireNonNull(damageRouting, "damageRouting");
        Objects.requireNonNull(incomingDamage, "incomingDamage");
        Objects.requireNonNull(multiplayer, "multiplayer");
        Objects.requireNonNull(arena, "arena");
        Objects.requireNonNull(targeting, "targeting");
        Objects.requireNonNull(presentation, "presentation");
        Objects.requireNonNull(stagger, "stagger");
        Objects.requireNonNull(instantGuard, "instantGuard");
        Objects.requireNonNull(resistance, "resistance");
        Objects.requireNonNull(sourceMultiplier, "sourceMultiplier");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(phaseTransition, "phaseTransition");
        Objects.requireNonNull(meteor, "meteor");
        Objects.requireNonNull(lightEcho, "lightEcho");
        Objects.requireNonNull(visuals, "visuals");
        Objects.requireNonNull(performance, "performance");
        Objects.requireNonNull(dialogue, "dialogue");
        Objects.requireNonNull(nonverbalAudio, "nonverbalAudio");
        Objects.requireNonNull(rewards, "rewards");
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

    public record DamageRouting(
            String ordinaryPhysical,
            String pierce,
            String bleedTrigger,
            String magic,
            String holy,
            String frostTrigger,
            boolean physicalUsesArmor,
            boolean magicBypassesArmor
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
            String attackTargetPolicy
    ) {
    }

    public record Presentation(String bossBarAudience, String staggerHudAudience) {
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

    public record PhaseResistances(ResistanceProfile phaseOne, ResistanceProfile phaseTwo) {
    }

    public record ResistanceProfile(double physical, double fire, double magic, double lightning) {
    }

    public record PhaseSourceMultipliers(
            SourceMultiplierProfile phaseOne,
            SourceMultiplierProfile phaseTwo
    ) {
    }

    public record SourceMultiplierProfile(
            double ordinaryPhysical,
            double pierce,
            double bleedTrigger,
            double magic,
            double holy,
            double frostTrigger
    ) {
    }

    public record Status(
            double poisonDamageMultiplier,
            double witherDamageMultiplier,
            double sleepDamageMultiplier
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
            int guardChainRecoveryTicks
    ) {
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
            String pendingDamagePolicy,
            int invulnerableStartTick,
            int invulnerableEndTick,
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

    public record Rewards(
            int remembranceCount,
            int gateFragmentMin,
            int gateFragmentMax,
            boolean affectedByLooting,
            int experience
    ) {
    }
}
