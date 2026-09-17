package com.tonywww.elder_bosses.platforms.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.tonywww.elder_bosses.boss.malenia.config.MaleniaCombatConfigSnapshot;
import com.tonywww.elder_bosses.boss.malenia.config.MaleniaSkillConfigSnapshot;
import com.tonywww.elder_bosses.boss.malenia.config.MaleniaSkillConfigSnapshot.HealProfile;
import com.tonywww.elder_bosses.combat.damage.DamageFormula;
import com.tonywww.elder_bosses.combat.state.StaggerTracker.DistanceBand;
import com.tonywww.elder_bosses.network.NetworkLimits;

//? if forge {
import net.minecraftforge.common.ForgeConfigSpec;
//?} else {
/*import net.neoforged.neoforge.common.ModConfigSpec;
*///?}

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class ElderBossesCommonConfig {
    //? if forge {
    public static final ForgeConfigSpec SPEC;
    //?} else {
    /*public static final ModConfigSpec SPEC;
    *///?}
    public static final ElderBossesCommonConfig VALUES;

    static {
        //? if forge {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        //?} else {
        /*ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        *///?}
        VALUES = new ElderBossesCommonConfig(builder);
        SPEC = builder.build();
    }

    private final Supplier<Boolean> indicatorsEnabled;
    private final Supplier<Boolean> maleniaIndicatorsEnabled;
    private final Supplier<Boolean> promisedConsortIndicatorsEnabled;
    private final Supplier<Double> indicatorOpacity;
    private final Supplier<Double> occludedOutlineOpacityMultiplier;
    private final Supplier<Double> indicatorRenderDistance;
    private final Supplier<Double> indicatorSurfaceOffset;
    private final Supplier<Integer> maxActiveIndicators;
    private final Supplier<Integer> maxSegmentsPerShape;
    private final Supplier<Boolean> skillVfxEnabled;
    private final Supplier<Integer> skillVfxParticleBudgetPerBossPerTick;
    private final Supplier<Double> skillVfxRenderDistance;
    private final Supplier<Double> phaseOneHealth;
    private final Supplier<Double> phaseTwoHealth;
    private final Supplier<Double> phaseTwoStartRatio;
    private final Supplier<Double> attackDamage;
    private final Supplier<Double> movementSpeed;
    private final Supplier<Double> followRange;
    private final Supplier<Double> knockbackResistance;
    private final Supplier<Integer> maxActivePlayers;
    private final Supplier<Boolean> debugStateOutput;
    private final Supplier<Boolean> debugActionBroadcast;

    private final Supplier<Double> phaseOnePhysicalResistance;
    private final Supplier<Double> phaseOneFireResistance;
    private final Supplier<Double> phaseOneMagicResistance;
    private final Supplier<Double> phaseOneLightningResistance;
    private final Supplier<Double> phaseTwoPhysicalResistance;
    private final Supplier<Double> phaseTwoFireResistance;
    private final Supplier<Double> phaseTwoMagicResistance;
    private final Supplier<Double> phaseTwoLightningResistance;

    private final Supplier<Double> phaseOneOrdinaryPhysicalSourceMultiplier;
    private final Supplier<Double> phaseOnePierceSourceMultiplier;
    private final Supplier<Double> phaseOneBleedTriggerSourceMultiplier;
    private final Supplier<Double> phaseOneMagicSourceMultiplier;
    private final Supplier<Double> phaseOneHolySourceMultiplier;
    private final Supplier<Double> phaseOneFrostTriggerSourceMultiplier;
    private final Supplier<Double> phaseTwoOrdinaryPhysicalSourceMultiplier;
    private final Supplier<Double> phaseTwoPierceSourceMultiplier;
    private final Supplier<Double> phaseTwoBleedTriggerSourceMultiplier;
    private final Supplier<Double> phaseTwoMagicSourceMultiplier;
    private final Supplier<Double> phaseTwoHolySourceMultiplier;
    private final Supplier<Double> phaseTwoFrostTriggerSourceMultiplier;

    private final Supplier<Double> healthPerExtraPlayer;
    private final Supplier<Double> healingWindowCapPerExtraPlayer;
    private final Supplier<Integer> retargetIntervalTicks;
    private final Supplier<Integer> sameTargetPenaltyAfterTicks;
    private final Supplier<Double> sameTargetScoreMultiplier;

    private final Supplier<Double> targetingDistanceWeight;
    private final Supplier<Double> targetingRecentDamageWeight;
    private final Supplier<Double> targetingItemUseWeight;
    private final Supplier<Double> targetingInterruptWeight;
    private final Supplier<Integer> targetingRecentDamageWindowTicks;

    private final Supplier<Double> arenaLogicalRadius;
    private final Supplier<Integer> arenaLeashGraceTicks;
    private final Supplier<Boolean> arenaLogicalShallowWater;
    private final Supplier<Boolean> arenaUnloadResetsFight;
    private final Supplier<Boolean> arenaBossIgnoresHeightVariation;
    private final Supplier<Boolean> arenaAllowBlockBreaking;
    private final Supplier<String> arenaBreakableBlockTag;
    private final Supplier<String> arenaProtectedBlockTag;
    private final Supplier<Double> arenaBlockBreakRadius;
    private final Supplier<Integer> arenaMaxBlocksBrokenPerTick;
    private final Supplier<Boolean> arenaRestoreBrokenBlocksOnReset;
    private final Supplier<Boolean> arenaAllowWallPhasing;
    private final Supplier<Integer> arenaWallPhaseMaxTicks;

    private final Supplier<Double> staggerDamageConversionRatio;
    private final Supplier<Double> staggerCapacityHealthRatio;
    private final Supplier<? extends List<?>> staggerDistanceBands;
    private final Supplier<Integer> staggerSourceDedupeTicks;
    private final Supplier<Integer> staggerDecayDelayTicks;
    private final Supplier<Double> staggerDecayPerTick;
    private final Supplier<Integer> staggerStunTicks;
    private final Supplier<Integer> staggerPostStunImmunityTicks;
    private final Supplier<Boolean> staggerResetOnPhaseChange;

    private final Supplier<Boolean> instantGuardEnabled;
    private final Supplier<Integer> instantGuardStartTick;
    private final Supplier<Integer> instantGuardEndTick;
    private final Supplier<Integer> instantGuardRearmTicks;
    private final Supplier<Double> instantGuardBlockedDamageMultiplier;
    private final Supplier<Double> instantGuardShieldDurabilityMultiplier;
    private final Supplier<Integer> instantGuardDefaultCueLeadTicks;
    private final Supplier<Integer> instantGuardCuePulseCount;
    private final Supplier<String> instantGuardRedCueColor;
    private final Supplier<String> instantGuardEligibleItemTag;

    private final Supplier<Boolean> healingEnabled;
    private final Supplier<Double> healingBlockedHitMultiplier;
    private final Supplier<Boolean> healingFromPlayers;
    private final Supplier<Boolean> healingFromNonHostileEntities;
    private final Supplier<Boolean> healingFromTamedEntities;
    private final Supplier<Boolean> healingFromSummons;
    private final Supplier<Boolean> healingFromArmorStands;
    private final Supplier<Double> healingActionCap;
    private final Supplier<Integer> healingWindowTicks;
    private final Supplier<Double> healingWindowCap;
    private final Supplier<? extends UnmodifiableConfig> standardHeal;
    private final Supplier<? extends UnmodifiableConfig> heavyHeal;
    private final Supplier<? extends UnmodifiableConfig> waterfowlHeal;
    private final Supplier<? extends UnmodifiableConfig> grabHeal;

    private final Supplier<Integer> scarletRotDecayDelayTicks;
    private final Supplier<Double> scarletRotDecayPerTwentyTicks;
    private final Supplier<Integer> scarletRotDurationTicks;
    private final Supplier<Integer> scarletRotDamageIntervalTicks;
    private final Supplier<? extends UnmodifiableConfig> scarletRotDamage;
    private final Supplier<Double> scarletRotHealingReduction;
    private final Supplier<Double> scarletRotMovementSpeedReduction;
    private final Supplier<Double> scarletRotHoneyBuildupReduction;
    private final Supplier<Boolean> scarletRotConsumeCleanseItem;
    private final Supplier<Integer> scarletRotCleanseUseTicks;
    private final Supplier<Boolean> scarletRotMilkClearsRot;

    private final Supplier<Double> phaseTwoRotOrdinarySwordBuildup;
    private final Supplier<Double> phaseTwoRotHeavyThrustBuildup;
    private final Supplier<Double> phaseTwoRotWaterfowlBuildup;
    private final Supplier<Double> phaseTwoRotKickBuildup;

    private final Supplier<Integer> selectorPhaseOneIdleMinTicks;
    private final Supplier<Integer> selectorPhaseOneIdleMaxTicks;
    private final Supplier<Integer> selectorPhaseTwoIdleMinTicks;
    private final Supplier<Integer> selectorPhaseTwoIdleMaxTicks;
    private final Supplier<Integer> selectorAvoidLastActionCount;
    private final Supplier<Double> selectorWaterfowlRetreatMaxRange;
    private final Supplier<Double> selectorItemUsePunishMinRange;
    private final Supplier<Double> selectorItemUsePunishMaxRange;
    private final Supplier<Double> selectorItemUseWeightMultiplier;
    private final Supplier<Integer> selectorShieldKickAfterTicks;
    private final Supplier<Double> selectorShieldKickWeightMultiplier;
    private final Supplier<Double> selectorShieldGrabWeightMultiplier;
    private final Supplier<Double> selectorNearbyPlayerRange;
    private final Supplier<Integer> selectorNearbyPlayerCountThreshold;
    private final Supplier<Double> selectorNearbyPlayerRetreatWeightMultiplier;
    private final Supplier<Integer> selectorRecentInterruptWindowTicks;
    private final Supplier<Integer> selectorRecentInterruptCountThreshold;
    private final Supplier<Double> selectorRecentInterruptWeightMultiplier;
    private final Supplier<Double> selectorLongRangeThreshold;
    private final Supplier<Double> selectorLongRangeRunningSlashWeightMultiplier;
    private final Supplier<Integer> selectorHighThreatGroupCooldownTicks;

    private final Supplier<Integer> phaseTransitionDurationTicks;
    private final Supplier<Boolean> phaseTransitionClearOwnedSlashHazards;
    private final Supplier<Boolean> phaseTransitionPreservePlayerRotBuildup;
    private final Supplier<Boolean> phaseTransitionResetStagger;
    private final Supplier<Boolean> phaseTransitionOpeningAeonia;

    private final Supplier<Integer> performanceMaxRotZones;
    private final Supplier<Boolean> dialogueEnabled;
    private final Supplier<Integer> dialogueMaxQueuedLines;
    private final Supplier<Integer> dialogueSubtitleDurationTicks;
    private final Supplier<Integer> dialoguePlayerDefeatDelayTicks;
    private final Supplier<Integer> dialogueIntroWarningTick;
    private final Supplier<Integer> dialogueTransitionReleaseTick;
    private final Supplier<Integer> dialogueDefeatedTick;
    private final Supplier<Boolean> nonverbalAudioEnabled;
    private final Supplier<Double> nonverbalAudioVolume;
    private final Supplier<Double> nonverbalAudioPitch;
    private final Supplier<Integer> nonverbalAudioHurtCooldownTicks;
    private final Supplier<Integer> nonverbalAudioGruntCooldownTicks;
    private final MaleniaSkillsValues maleniaSkills;
    private final PromisedConsortConfigValues promisedConsort;

    private ElderBossesCommonConfig(
            //? if forge {
            ForgeConfigSpec.Builder builder
            //?} else {
            /*ModConfigSpec.Builder builder
            *///?}
    ) {
        builder.push("indicators");
        indicatorsEnabled = builder.define("enabled", true);
        maleniaIndicatorsEnabled = builder.define("malenia_enabled", true);
        promisedConsortIndicatorsEnabled = builder.define("promised_consort_enabled", true);
        indicatorOpacity = builder.defineInRange("opacity", 0.60, 0.10, 1.00);
        occludedOutlineOpacityMultiplier = builder.defineInRange(
                "occluded_outline_opacity_multiplier", 0.35, 0.0, 1.0);
        indicatorRenderDistance = builder.defineInRange("render_distance", 96.0, 1.0, 256.0);
        indicatorSurfaceOffset = builder.defineInRange("surface_offset", 0.02, 0.0, 1.0);
        maxActiveIndicators = builder.defineInRange("max_active_indicators", 64, 1, 512);
        maxSegmentsPerShape = builder.defineInRange("max_segments_per_shape", 96, 3, 512);
        builder.pop();

        builder.push("skill_vfx");
        skillVfxEnabled = builder.define("enabled", true);
        skillVfxParticleBudgetPerBossPerTick = builder.defineInRange(
            "particle_budget_per_boss_per_tick", 18, 1, 128);
        skillVfxRenderDistance = builder.defineInRange(
            "render_distance", 96.0, 8.0, 256.0);
        builder.pop();

        builder.push("malenia");
        builder.push("general");
        phaseOneHealth = builder.defineInRange("phase_one_health", 900.0, 1.0, 1_000_000.0);
        phaseTwoHealth = builder.defineInRange("phase_two_health", 900.0, 1.0, 1_000_000.0);
        phaseTwoStartRatio = builder.defineInRange("phase_two_start_ratio", 0.80, 0.01, 1.0);
        attackDamage = builder.defineInRange("attack_damage", 20.0, 0.0, 2048.0);
        movementSpeed = builder.defineInRange("movement_speed", 0.34, 0.0, 4.0);
        followRange = builder.defineInRange("follow_range", 56.0, 1.0, 2048.0);
        knockbackResistance = builder.defineInRange("knockback_resistance", 0.75, 0.0, 1.0);
        maxActivePlayers = builder.defineInRange("max_active_players", 4, 1, 4);
        builder.pop();

        builder.push("debug");
        debugStateOutput = builder.define("state_output", false);
        debugActionBroadcast = builder.define("action_broadcast", false);
        builder.pop();

        builder.push("multiplayer");
        healthPerExtraPlayer = builder.defineInRange("health_per_extra_player", 0.50, 0.0, 10.0);
        healingWindowCapPerExtraPlayer = builder.defineInRange(
            "healing_window_cap_per_extra_player", 0.15, 0.0, 10.0);
        retargetIntervalTicks = builder.defineInRange("retarget_interval_ticks", 15, 1, Integer.MAX_VALUE);
        sameTargetPenaltyAfterTicks = builder.defineInRange(
            "same_target_penalty_after_ticks", 180, 0, Integer.MAX_VALUE);
        sameTargetScoreMultiplier = builder.defineInRange("same_target_score_multiplier", 0.78, 0.0, 1.0);
        builder.pop();

        builder.push("targeting");
        targetingDistanceWeight = builder.defineInRange("distance_weight", 0.45, 0.0, 100.0);
        targetingRecentDamageWeight = builder.defineInRange("recent_damage_weight", 0.35, 0.0, 100.0);
        targetingItemUseWeight = builder.defineInRange("item_use_weight", 0.10, 0.0, 100.0);
        targetingInterruptWeight = builder.defineInRange("interrupt_weight", 0.10, 0.0, 100.0);
        targetingRecentDamageWindowTicks = builder.defineInRange(
            "recent_damage_window_ticks", 160, 1, Integer.MAX_VALUE);
        builder.pop();

        builder.push("arena");
        arenaLogicalRadius = builder.defineInRange("logical_radius", 26.0, 1.0, 2048.0);
        arenaLeashGraceTicks = builder.defineInRange("leash_grace_ticks", 100, 0, Integer.MAX_VALUE);
        arenaLogicalShallowWater = builder.define("logical_shallow_water", false);
        arenaUnloadResetsFight = builder.define("unload_resets_fight", true);
        arenaBossIgnoresHeightVariation = builder.define("boss_ignores_arena_height_variation", true);
        arenaAllowBlockBreaking = builder.define("allow_block_breaking", true);
        arenaBreakableBlockTag = builder.define("breakable_block_tag", "elder_bosses:boss_breakable");
        arenaProtectedBlockTag = builder.define("protected_block_tag", "elder_bosses:arena_protected");
        arenaBlockBreakRadius = builder.defineInRange("block_break_radius", 1.5, 0.0, 64.0);
        arenaMaxBlocksBrokenPerTick = builder.defineInRange("max_blocks_broken_per_tick", 24, 0, 4096);
        arenaRestoreBrokenBlocksOnReset = builder.define("restore_broken_blocks_on_reset", true);
        arenaAllowWallPhasing = builder.define("allow_wall_phasing", true);
        arenaWallPhaseMaxTicks = builder.defineInRange("wall_phase_max_ticks", 20, 0, Integer.MAX_VALUE);
        builder.pop();

        builder.push("stagger");
        staggerDamageConversionRatio = builder.defineInRange("damage_conversion_ratio", 0.75, 0.0, 1.0);
        staggerCapacityHealthRatio = builder.defineInRange("capacity_health_ratio", 0.10, 0.001, 1.0);
        staggerDistanceBands = builder.defineList(
            "distance_bands", defaultDistanceBandConfigs(), ElderBossesCommonConfig::isDistanceBand);
        staggerSourceDedupeTicks = builder.defineInRange("source_dedupe_ticks", 5, 0, Integer.MAX_VALUE);
        staggerDecayDelayTicks = builder.defineInRange("decay_delay_ticks", 120, 0, Integer.MAX_VALUE);
        staggerDecayPerTick = builder.defineInRange("decay_per_tick", 1.5, 0.0, 1_000_000.0);
        staggerStunTicks = builder.defineInRange("stun_ticks", 70, 1, Integer.MAX_VALUE);
        staggerPostStunImmunityTicks = builder.defineInRange(
            "post_stun_immunity_ticks", 80, 1, Integer.MAX_VALUE);
        staggerResetOnPhaseChange = builder.define("reset_on_phase_change", true);
        builder.pop();

        builder.push("instant_guard");
        instantGuardEnabled = builder.define("enabled", true);
        instantGuardStartTick = builder.defineInRange("start_tick", 3, 0, Integer.MAX_VALUE);
        instantGuardEndTick = builder.defineInRange("end_tick", 6, 0, Integer.MAX_VALUE);
        instantGuardRearmTicks = builder.defineInRange("rearm_ticks", 4, 4, Integer.MAX_VALUE);
        instantGuardBlockedDamageMultiplier = builder.defineInRange("blocked_damage_multiplier", 0.0, 0.0, 1.0);
        instantGuardShieldDurabilityMultiplier = builder.defineInRange(
            "shield_durability_multiplier", 0.50, 0.0, 100.0);
        instantGuardDefaultCueLeadTicks = builder.defineInRange(
            "default_cue_lead_ticks", 6, 0, Integer.MAX_VALUE);
        instantGuardCuePulseCount = builder.defineInRange("cue_pulse_count", 3, 1, 64);
        instantGuardRedCueColor = builder.define("red_cue_color", "#FF2020");
        instantGuardEligibleItemTag = builder.define("eligible_item_tag", "elder_bosses:instant_guard_items");
        builder.pop();

        builder.push("healing");
        healingEnabled = builder.define("enabled", true);
        healingBlockedHitMultiplier = builder.defineInRange("blocked_hit_multiplier", 1.0, 0.0, 10.0);
        healingFromPlayers = builder.define("heal_from_players", true);
        healingFromNonHostileEntities = builder.define("heal_from_non_hostile_entities", true);
        healingFromTamedEntities = builder.define("heal_from_tamed_entities", false);
        healingFromSummons = builder.define("heal_from_summons", false);
        healingFromArmorStands = builder.define("heal_from_armor_stands", false);
        healingActionCap = builder.defineInRange("action_cap", 16.0, 0.0, 1_000_000.0);
        healingWindowTicks = builder.defineInRange("window_ticks", 100, 1, Integer.MAX_VALUE);
        healingWindowCap = builder.defineInRange("window_cap", 36.0, 0.0, 1_000_000.0);
        standardHeal = builder.define(
            "standard_heal", damageFormulaConfig(1.0, 0.06), ElderBossesCommonConfig::isDamageFormula);
        heavyHeal = builder.define(
            "heavy_heal", damageFormulaConfig(2.0, 0.08), ElderBossesCommonConfig::isDamageFormula);
        waterfowlHeal = builder.define(
            "waterfowl_heal", damageFormulaConfig(0.0, 0.04), ElderBossesCommonConfig::isDamageFormula);
        grabHeal = builder.define(
            "grab_heal", damageFormulaConfig(3.0, 0.10), ElderBossesCommonConfig::isDamageFormula);
        builder.pop();

        builder.push("resistance");
        builder.push("phase_one");
        phaseOnePhysicalResistance = builder.defineInRange("physical", 0.90, 0.0, 100.0);
        phaseOneFireResistance = builder.defineInRange("fire", 1.00, 0.0, 100.0);
        phaseOneMagicResistance = builder.defineInRange("magic", 0.80, 0.0, 100.0);
        phaseOneLightningResistance = builder.defineInRange("lightning", 0.80, 0.0, 100.0);
        builder.pop();
        builder.push("phase_two");
        phaseTwoPhysicalResistance = builder.defineInRange("physical", 0.90, 0.0, 100.0);
        phaseTwoFireResistance = builder.defineInRange("fire", 1.00, 0.0, 100.0);
        phaseTwoMagicResistance = builder.defineInRange("magic", 0.80, 0.0, 100.0);
        phaseTwoLightningResistance = builder.defineInRange("lightning", 0.80, 0.0, 100.0);
        builder.pop();
        builder.pop();

        builder.push("source_multiplier");
        builder.push("phase_one");
        phaseOneOrdinaryPhysicalSourceMultiplier = builder.defineInRange(
            "ordinary_physical", 1.00, 0.0, 100.0);
        phaseOnePierceSourceMultiplier = builder.defineInRange("pierce", 1.00, 0.0, 100.0);
        phaseOneBleedTriggerSourceMultiplier = builder.defineInRange(
            "bleed_trigger", 1.00, 0.0, 100.0);
        phaseOneMagicSourceMultiplier = builder.defineInRange("magic", 1.00, 0.0, 100.0);
        phaseOneHolySourceMultiplier = builder.defineInRange("holy", 0.75, 0.0, 100.0);
        phaseOneFrostTriggerSourceMultiplier = builder.defineInRange(
            "frost_trigger", 1.00, 0.0, 100.0);
        builder.pop();
        builder.push("phase_two");
        phaseTwoOrdinaryPhysicalSourceMultiplier = builder.defineInRange(
            "ordinary_physical", 1.00, 0.0, 100.0);
        phaseTwoPierceSourceMultiplier = builder.defineInRange("pierce", 1.00, 0.0, 100.0);
        phaseTwoBleedTriggerSourceMultiplier = builder.defineInRange(
            "bleed_trigger", 1.00, 0.0, 100.0);
        phaseTwoMagicSourceMultiplier = builder.defineInRange("magic", 1.00, 0.0, 100.0);
        phaseTwoHolySourceMultiplier = builder.defineInRange("holy", 0.75, 0.0, 100.0);
        phaseTwoFrostTriggerSourceMultiplier = builder.defineInRange(
            "frost_trigger", 1.00, 0.0, 100.0);
        builder.pop();
        builder.pop();

        builder.push("scarlet_rot");
        scarletRotDecayDelayTicks = builder.defineInRange("decay_delay_ticks", 60, 0, Integer.MAX_VALUE);
        scarletRotDecayPerTwentyTicks = builder.defineInRange("decay_per_20_ticks", 8.0, 0.0, 1_000_000.0);
        scarletRotDurationTicks = builder.defineInRange(
            "duration_ticks", 120, 1, NetworkLimits.MAX_TICKS);
        scarletRotDamageIntervalTicks = builder.defineInRange(
            "damage_interval_ticks", 20, 1, NetworkLimits.MAX_TICKS);
        scarletRotDamage = builder.define(
            "damage", damageFormulaConfig(0.0, 0.10), ElderBossesCommonConfig::isDamageFormula);
        scarletRotHealingReduction = builder.defineInRange("healing_reduction", 0.30, 0.0, 1.0);
        scarletRotMovementSpeedReduction = builder.defineInRange("movement_speed_reduction", 0.10, 0.0, 1.0);
        scarletRotHoneyBuildupReduction = builder.defineInRange(
            "honey_buildup_reduction", 25.0, 0.0, 1_000_000.0);
        scarletRotConsumeCleanseItem = builder.define("consume_cleanse_item", true);
        scarletRotCleanseUseTicks = builder.defineInRange("cleanse_use_ticks", 32, 1, Integer.MAX_VALUE);
        scarletRotMilkClearsRot = builder.define("milk_clears_rot", false);
        builder.pop();

        builder.push("phase_two_rot");
        phaseTwoRotOrdinarySwordBuildup = builder.defineInRange(
            "ordinary_sword_buildup", 4.0, 0.0, 1_000_000.0);
        phaseTwoRotHeavyThrustBuildup = builder.defineInRange(
            "heavy_thrust_buildup", 8.0, 0.0, 1_000_000.0);
        phaseTwoRotWaterfowlBuildup = builder.defineInRange(
            "waterfowl_buildup", 5.0, 0.0, 1_000_000.0);
        phaseTwoRotKickBuildup = builder.defineInRange(
            "kick_buildup", 8.0, 0.0, 1_000_000.0);
        builder.pop();

        builder.push("selector");
        selectorPhaseOneIdleMinTicks = builder.defineInRange(
            "phase_one_idle_min_ticks", 15, 0, Integer.MAX_VALUE);
        selectorPhaseOneIdleMaxTicks = builder.defineInRange(
            "phase_one_idle_max_ticks", 35, 0, Integer.MAX_VALUE);
        selectorPhaseTwoIdleMinTicks = builder.defineInRange(
            "phase_two_idle_min_ticks", 8, 0, Integer.MAX_VALUE);
        selectorPhaseTwoIdleMaxTicks = builder.defineInRange(
            "phase_two_idle_max_ticks", 20, 0, Integer.MAX_VALUE);
        selectorAvoidLastActionCount = builder.defineInRange(
            "avoid_last_action_count", 2, 0, Integer.MAX_VALUE);
        selectorWaterfowlRetreatMaxRange = builder.defineInRange(
            "waterfowl_retreat_max_range", 4.0, 0.0, 2048.0);
        selectorItemUsePunishMinRange = builder.defineInRange(
            "item_use_punish_min_range", 4.0, 0.0, 2048.0);
        selectorItemUsePunishMaxRange = builder.defineInRange(
            "item_use_punish_max_range", 10.0, 0.0, 2048.0);
        selectorItemUseWeightMultiplier = builder.defineInRange(
            "item_use_weight_multiplier", 1.70, 0.0, 100.0);
        selectorShieldKickAfterTicks = builder.defineInRange(
            "shield_kick_after_ticks", 40, 0, Integer.MAX_VALUE);
        selectorShieldKickWeightMultiplier = builder.defineInRange(
            "shield_kick_weight_multiplier", 1.80, 0.0, 100.0);
        selectorShieldGrabWeightMultiplier = builder.defineInRange(
            "shield_grab_weight_multiplier", 1.40, 0.0, 100.0);
        selectorNearbyPlayerRange = builder.defineInRange(
            "nearby_player_range", 4.0, 0.0, 2048.0);
        selectorNearbyPlayerCountThreshold = builder.defineInRange(
            "nearby_player_count_threshold", 2, 1, Integer.MAX_VALUE);
        selectorNearbyPlayerRetreatWeightMultiplier = builder.defineInRange(
            "nearby_player_retreat_weight_multiplier", 1.50, 0.0, 100.0);
        selectorRecentInterruptWindowTicks = builder.defineInRange(
            "recent_interrupt_window_ticks", 100, 1, Integer.MAX_VALUE);
        selectorRecentInterruptCountThreshold = builder.defineInRange(
            "recent_interrupt_count_threshold", 2, 1, Integer.MAX_VALUE);
        selectorRecentInterruptWeightMultiplier = builder.defineInRange(
            "recent_interrupt_weight_multiplier", 1.40, 0.0, 100.0);
        selectorLongRangeThreshold = builder.defineInRange(
            "long_range_threshold", 12.0, 0.0, 2048.0);
        selectorLongRangeRunningSlashWeightMultiplier = builder.defineInRange(
            "long_range_running_slash_weight_multiplier", 1.60, 0.0, 100.0);
        selectorHighThreatGroupCooldownTicks = builder.defineInRange(
            "high_threat_group_cooldown_ticks", 140, 0, Integer.MAX_VALUE);
        builder.pop();

        builder.push("phase_transition");
        phaseTransitionDurationTicks = builder.defineInRange(
            "duration_ticks", 150, 1, NetworkLimits.MAX_TICKS);
        phaseTransitionClearOwnedSlashHazards = builder.define("clear_owned_slash_hazards", true);
        phaseTransitionPreservePlayerRotBuildup = builder.define("preserve_player_rot_buildup", true);
        phaseTransitionResetStagger = builder.define("reset_stagger", true);
        phaseTransitionOpeningAeonia = builder.define("opening_aeonia", true);
        builder.pop();

        builder.push("performance");
        performanceMaxRotZones = builder.defineInRange("max_rot_zones", 3, 0, Integer.MAX_VALUE);
        builder.pop();

        builder.push("dialogue");
        dialogueEnabled = builder.define("enabled", true);
        dialogueMaxQueuedLines = builder.defineInRange("max_queued_lines", 1, 0, 64);
        dialogueSubtitleDurationTicks = builder.defineInRange(
            "subtitle_duration_ticks", 70, 1, NetworkLimits.MAX_TICKS);
        dialoguePlayerDefeatDelayTicks = builder.defineInRange(
            "player_defeat_delay_ticks", 12, 0, NetworkLimits.MAX_TICKS);
        dialogueIntroWarningTick = builder.defineInRange(
            "intro_warning_tick", 20, 0, NetworkLimits.MAX_TICKS);
        dialogueTransitionReleaseTick = builder.defineInRange(
            "transition_release_tick", 42, 0, NetworkLimits.MAX_TICKS);
        dialogueDefeatedTick = builder.defineInRange(
            "defeated_tick", 28, 0, NetworkLimits.MAX_TICKS);
        builder.pop();

        builder.push("nonverbal_audio");
        nonverbalAudioEnabled = builder.define("enabled", true);
        nonverbalAudioVolume = builder.defineInRange("volume", 1.0, 0.0, 16.0);
        nonverbalAudioPitch = builder.defineInRange("pitch", 1.0, 0.0, 16.0);
        nonverbalAudioHurtCooldownTicks = builder.defineInRange(
            "hurt_cooldown_ticks", 20, 0, Integer.MAX_VALUE);
        nonverbalAudioGruntCooldownTicks = builder.defineInRange(
            "grunt_cooldown_ticks", 80, 0, Integer.MAX_VALUE);
        builder.pop();

        maleniaSkills = new MaleniaSkillsValues(builder);

        builder.pop();
        promisedConsort = new PromisedConsortConfigValues(builder);
    }

    public IndicatorValues indicators() {
        return new IndicatorValues(
                indicatorsEnabled.get(),
                indicatorOpacity.get(),
                occludedOutlineOpacityMultiplier.get(),
                indicatorRenderDistance.get(),
                indicatorSurfaceOffset.get(),
                maxActiveIndicators.get(),
                maxSegmentsPerShape.get(),
                maleniaIndicatorsEnabled.get(),
                promisedConsortIndicatorsEnabled.get()
        );
    }

    public SkillVfxValues skillVfx() {
        return new SkillVfxValues(
                skillVfxEnabled.get(),
                skillVfxParticleBudgetPerBossPerTick.get(),
                skillVfxRenderDistance.get()
        );
    }

    public MaleniaGeneralValues maleniaGeneral() {
        return new MaleniaGeneralValues(
                phaseOneHealth.get(),
                phaseTwoHealth.get(),
                phaseTwoStartRatio.get(),
                attackDamage.get(),
                movementSpeed.get(),
                followRange.get(),
                knockbackResistance.get(),
                maxActivePlayers.get()
        );
    }

    public boolean maleniaDebugStateOutput() {
        return debugStateOutput.get();
    }

    public boolean maleniaDebugActionBroadcast() {
        return debugActionBroadcast.get();
    }

    public boolean promisedConsortDebugActionBroadcast() {
        return promisedConsort.debugActionBroadcast();
    }

        public MaleniaPhaseResistanceValues maleniaResistance() {
        return new MaleniaPhaseResistanceValues(
            new MaleniaResistanceValues(
                phaseOnePhysicalResistance.get(),
                phaseOneFireResistance.get(),
                phaseOneMagicResistance.get(),
                phaseOneLightningResistance.get()
            ),
            new MaleniaResistanceValues(
                phaseTwoPhysicalResistance.get(),
                phaseTwoFireResistance.get(),
                phaseTwoMagicResistance.get(),
                phaseTwoLightningResistance.get()
            )
        );
        }

        public MaleniaPhaseSourceMultiplierValues maleniaSourceMultiplier() {
        return new MaleniaPhaseSourceMultiplierValues(
            new MaleniaSourceMultiplierValues(
                phaseOneOrdinaryPhysicalSourceMultiplier.get(),
                phaseOnePierceSourceMultiplier.get(),
                phaseOneBleedTriggerSourceMultiplier.get(),
                phaseOneMagicSourceMultiplier.get(),
                phaseOneHolySourceMultiplier.get(),
                phaseOneFrostTriggerSourceMultiplier.get()
            ),
            new MaleniaSourceMultiplierValues(
                phaseTwoOrdinaryPhysicalSourceMultiplier.get(),
                phaseTwoPierceSourceMultiplier.get(),
                phaseTwoBleedTriggerSourceMultiplier.get(),
                phaseTwoMagicSourceMultiplier.get(),
                phaseTwoHolySourceMultiplier.get(),
                phaseTwoFrostTriggerSourceMultiplier.get()
            )
        );
        }

    public MaleniaMultiplayerValues maleniaMultiplayer() {
        return new MaleniaMultiplayerValues(
                healthPerExtraPlayer.get(),
                healingWindowCapPerExtraPlayer.get(),
                retargetIntervalTicks.get(),
                sameTargetPenaltyAfterTicks.get(),
                sameTargetScoreMultiplier.get()
        );
    }

    public MaleniaArenaValues maleniaArena() {
        return new MaleniaArenaValues(
                arenaLogicalRadius.get(),
                arenaLeashGraceTicks.get(),
                arenaLogicalShallowWater.get(),
                arenaUnloadResetsFight.get(),
                arenaBossIgnoresHeightVariation.get(),
                arenaAllowBlockBreaking.get(),
                arenaBreakableBlockTag.get(),
                arenaProtectedBlockTag.get(),
                arenaBlockBreakRadius.get(),
                arenaMaxBlocksBrokenPerTick.get(),
                arenaRestoreBrokenBlocksOnReset.get(),
                arenaAllowWallPhasing.get(),
                arenaWallPhaseMaxTicks.get()
        );
    }

    public MaleniaStaggerValues maleniaStagger() {
        return new MaleniaStaggerValues(
                staggerDamageConversionRatio.get(),
                staggerCapacityHealthRatio.get(),
                readDistanceBands(staggerDistanceBands.get()),
                staggerSourceDedupeTicks.get(),
                staggerDecayDelayTicks.get(),
                staggerDecayPerTick.get(),
                staggerStunTicks.get(),
                staggerPostStunImmunityTicks.get(),
                staggerResetOnPhaseChange.get()
        );
    }

    public MaleniaTargetingValues maleniaTargeting() {
        return new MaleniaTargetingValues(
                targetingDistanceWeight.get(),
                targetingRecentDamageWeight.get(),
                targetingItemUseWeight.get(),
                targetingInterruptWeight.get(),
                targetingRecentDamageWindowTicks.get()
        );
    }

    public MaleniaSelectorValues maleniaSelector() {
        return new MaleniaSelectorValues(
                selectorPhaseOneIdleMinTicks.get(),
                selectorPhaseOneIdleMaxTicks.get(),
                selectorPhaseTwoIdleMinTicks.get(),
                selectorPhaseTwoIdleMaxTicks.get(),
                selectorAvoidLastActionCount.get(),
                selectorWaterfowlRetreatMaxRange.get(),
                selectorItemUsePunishMinRange.get(),
                selectorItemUsePunishMaxRange.get(),
                selectorItemUseWeightMultiplier.get(),
                selectorShieldKickAfterTicks.get(),
                selectorShieldKickWeightMultiplier.get(),
                selectorShieldGrabWeightMultiplier.get(),
                selectorNearbyPlayerRange.get(),
                selectorNearbyPlayerCountThreshold.get(),
                selectorNearbyPlayerRetreatWeightMultiplier.get(),
                selectorRecentInterruptWindowTicks.get(),
                selectorRecentInterruptCountThreshold.get(),
                selectorRecentInterruptWeightMultiplier.get(),
                selectorLongRangeThreshold.get(),
                selectorLongRangeRunningSlashWeightMultiplier.get(),
                selectorHighThreatGroupCooldownTicks.get()
        );
    }

    public MaleniaInstantGuardValues maleniaInstantGuard() {
        return new MaleniaInstantGuardValues(
                instantGuardEnabled.get(),
                instantGuardStartTick.get(),
                instantGuardEndTick.get(),
                instantGuardRearmTicks.get(),
                instantGuardBlockedDamageMultiplier.get(),
                instantGuardShieldDurabilityMultiplier.get(),
                instantGuardDefaultCueLeadTicks.get(),
                instantGuardCuePulseCount.get(),
                instantGuardRedCueColor.get(),
                instantGuardEligibleItemTag.get()
        );
    }

    public MaleniaHealingValues maleniaHealing() {
        return new MaleniaHealingValues(
                healingEnabled.get(),
                healingBlockedHitMultiplier.get(),
                healingFromPlayers.get(),
                healingFromNonHostileEntities.get(),
                healingFromTamedEntities.get(),
                healingFromSummons.get(),
                healingFromArmorStands.get(),
                healingActionCap.get(),
                healingWindowTicks.get(),
                healingWindowCap.get(),
                readDamageFormula(standardHeal.get()),
                readDamageFormula(heavyHeal.get()),
                readDamageFormula(waterfowlHeal.get()),
                readDamageFormula(grabHeal.get())
        );
    }

    public MaleniaScarletRotValues maleniaScarletRot() {
        return new MaleniaScarletRotValues(
                scarletRotDecayDelayTicks.get(),
                scarletRotDecayPerTwentyTicks.get(),
                scarletRotDurationTicks.get(),
                scarletRotDamageIntervalTicks.get(),
                readDamageFormula(scarletRotDamage.get()),
                scarletRotHealingReduction.get(),
                scarletRotMovementSpeedReduction.get(),
                scarletRotHoneyBuildupReduction.get(),
                scarletRotConsumeCleanseItem.get(),
                scarletRotCleanseUseTicks.get(),
                scarletRotMilkClearsRot.get()
        );
    }

    public MaleniaPhaseTransitionValues maleniaPhaseTransition() {
        return new MaleniaPhaseTransitionValues(
                phaseTransitionDurationTicks.get(),
                phaseTransitionClearOwnedSlashHazards.get(),
                phaseTransitionPreservePlayerRotBuildup.get(),
                phaseTransitionResetStagger.get(),
                phaseTransitionOpeningAeonia.get()
        );
    }

    public MaleniaCombatConfigSnapshot maleniaCombatSnapshot() {
        MaleniaGeneralValues general = maleniaGeneral();
        MaleniaPhaseResistanceValues resistance = maleniaResistance();
        MaleniaPhaseSourceMultiplierValues sourceMultiplier = maleniaSourceMultiplier();
        MaleniaMultiplayerValues multiplayer = maleniaMultiplayer();
        MaleniaTargetingValues targeting = maleniaTargeting();
        MaleniaSelectorValues selector = maleniaSelector();
        MaleniaHealingValues healing = maleniaHealing();
        MaleniaStaggerValues stagger = maleniaStagger();
        MaleniaInstantGuardValues instantGuard = maleniaInstantGuard();
        MaleniaScarletRotValues scarletRot = maleniaScarletRot();
        MaleniaPhaseTransitionValues phaseTransition = maleniaPhaseTransition();
        MaleniaPerformanceValues performance = maleniaPerformance();
        MaleniaDialogueValues dialogue = maleniaDialogue();
        MaleniaNonverbalAudioValues nonverbalAudio = maleniaNonverbalAudio();

        return new MaleniaCombatConfigSnapshot(
                new MaleniaCombatConfigSnapshot.General(
                    general.phaseOneHealth(),
                    general.phaseTwoHealth(),
                    general.phaseTwoStartRatio(),
                    general.attackDamage(),
                    general.movementSpeed(),
                    general.followRange(),
                    general.knockbackResistance(),
                    general.maxActivePlayers()
                ),
                    new MaleniaCombatConfigSnapshot.PhaseResistances(
                        toCombatResistanceProfile(resistance.phaseOne()),
                        toCombatResistanceProfile(resistance.phaseTwo())
                    ),
                    new MaleniaCombatConfigSnapshot.PhaseSourceMultipliers(
                        toCombatSourceMultiplierProfile(sourceMultiplier.phaseOne()),
                        toCombatSourceMultiplierProfile(sourceMultiplier.phaseTwo())
                    ),
                new MaleniaCombatConfigSnapshot.Multiplayer(
                    multiplayer.healthPerExtraPlayer(),
                    multiplayer.healingWindowCapPerExtraPlayer(),
                    multiplayer.retargetIntervalTicks(),
                    multiplayer.sameTargetPenaltyAfterTicks(),
                    multiplayer.sameTargetScoreMultiplier()
                ),
                new MaleniaCombatConfigSnapshot.Targeting(
                    targeting.distanceWeight(),
                    targeting.recentDamageWeight(),
                    targeting.itemUseWeight(),
                    targeting.interruptWeight(),
                    targeting.recentDamageWindowTicks()
                ),
                new MaleniaCombatConfigSnapshot.Selector(
                    selector.phaseOneIdleMinTicks(),
                    selector.phaseOneIdleMaxTicks(),
                    selector.phaseTwoIdleMinTicks(),
                    selector.phaseTwoIdleMaxTicks(),
                    selector.avoidLastActionCount(),
                    selector.waterfowlRetreatMaxRange(),
                    selector.itemUsePunishMinRange(),
                    selector.itemUsePunishMaxRange(),
                    selector.itemUseWeightMultiplier(),
                    selector.shieldKickAfterTicks(),
                    selector.shieldKickWeightMultiplier(),
                    selector.shieldGrabWeightMultiplier(),
                    selector.nearbyPlayerRange(),
                    selector.nearbyPlayerCountThreshold(),
                    selector.nearbyPlayerRetreatWeightMultiplier(),
                    selector.recentInterruptWindowTicks(),
                    selector.recentInterruptCountThreshold(),
                    selector.recentInterruptWeightMultiplier(),
                    selector.longRangeThreshold(),
                    selector.longRangeRunningSlashWeightMultiplier(),
                    selector.highThreatGroupCooldownTicks()
                ),
                new MaleniaCombatConfigSnapshot.Healing(
                    healing.enabled(),
                    healing.blockedHitMultiplier(),
                    healing.healFromPlayers(),
                    healing.healFromNonHostileEntities(),
                    healing.healFromTamedEntities(),
                    healing.healFromSummons(),
                    healing.healFromArmorStands(),
                    healing.actionCap(),
                    healing.windowTicks(),
                    healing.windowCap(),
                    toCombatDamageFormula(healing.standardHeal()),
                    toCombatDamageFormula(healing.heavyHeal()),
                    toCombatDamageFormula(healing.waterfowlHeal()),
                    toCombatDamageFormula(healing.grabHeal())
                ),
                new MaleniaCombatConfigSnapshot.Stagger(
                    stagger.damageConversionRatio(),
                    stagger.capacityHealthRatio(),
                    toCombatDistanceBands(stagger.distanceBands()),
                    stagger.sourceDedupeTicks(),
                    stagger.decayDelayTicks(),
                    stagger.decayPerTick(),
                    stagger.stunTicks(),
                    stagger.postStunImmunityTicks(),
                    stagger.resetOnPhaseChange()
                ),
                new MaleniaCombatConfigSnapshot.InstantGuard(
                    instantGuard.enabled(),
                    instantGuard.startTick(),
                    instantGuard.endTick(),
                    instantGuard.rearmTicks(),
                    instantGuard.blockedDamageMultiplier(),
                    instantGuard.shieldDurabilityMultiplier(),
                    instantGuard.defaultCueLeadTicks(),
                    instantGuard.cuePulseCount(),
                    instantGuard.redCueColor(),
                    instantGuard.eligibleItemTag()
                ),
                new MaleniaCombatConfigSnapshot.ScarletRot(
                    scarletRot.decayDelayTicks(),
                    scarletRot.decayPerTwentyTicks(),
                    scarletRot.durationTicks(),
                    scarletRot.damageIntervalTicks(),
                    toCombatDamageFormula(scarletRot.damage()),
                    scarletRot.healingReduction(),
                    scarletRot.movementSpeedReduction(),
                    scarletRot.honeyBuildupReduction(),
                    scarletRot.consumeCleanseItem(),
                    scarletRot.cleanseUseTicks(),
                    scarletRot.milkClearsRot()
                ),
                new MaleniaCombatConfigSnapshot.PhaseTransition(
                    phaseTransition.durationTicks(),
                    phaseTransition.clearOwnedSlashHazards(),
                    phaseTransition.preservePlayerRotBuildup(),
                    phaseTransition.resetStagger(),
                    phaseTransition.openingAeonia()
                ),
                new MaleniaCombatConfigSnapshot.Performance(
                    performance.maxRotZones()
                ),
                new MaleniaCombatConfigSnapshot.Dialogue(
                    dialogue.enabled(),
                    dialogue.maxQueuedLines(),
                    dialogue.subtitleDurationTicks(),
                    dialogue.playerDefeatDelayTicks(),
                    dialogue.introWarningTick(),
                    dialogue.transitionReleaseTick(),
                    dialogue.defeatedTick()
                ),
                new MaleniaCombatConfigSnapshot.NonverbalAudio(
                    nonverbalAudio.enabled(),
                    nonverbalAudio.volume(),
                    nonverbalAudio.pitch(),
                    nonverbalAudio.hurtCooldownTicks(),
                    nonverbalAudio.gruntCooldownTicks()
                )
            );
    }

    public MaleniaSkillConfigSnapshot maleniaSkillSnapshot() {
        return maleniaSkills.snapshot(new MaleniaSkillConfigSnapshot.PhaseTwoRot(
                phaseTwoRotOrdinarySwordBuildup.get(),
                phaseTwoRotHeavyThrustBuildup.get(),
            phaseTwoRotWaterfowlBuildup.get(),
            phaseTwoRotKickBuildup.get()
        ));
    }

    public com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortCombatConfigSnapshot
            promisedConsortCombatSnapshot() {
        return promisedConsort.combatSnapshot();
    }

    public com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSkillConfigSnapshot
            promisedConsortSkillSnapshot() {
        return promisedConsort.skillSnapshot();
    }

    public MaleniaPerformanceValues maleniaPerformance() {
        return new MaleniaPerformanceValues(performanceMaxRotZones.get());
    }

    public MaleniaDialogueValues maleniaDialogue() {
        return new MaleniaDialogueValues(
                dialogueEnabled.get(),
                dialogueMaxQueuedLines.get(),
                dialogueSubtitleDurationTicks.get(),
                dialoguePlayerDefeatDelayTicks.get(),
                dialogueIntroWarningTick.get(),
                dialogueTransitionReleaseTick.get(),
                dialogueDefeatedTick.get()
        );
    }

    public MaleniaNonverbalAudioValues maleniaNonverbalAudio() {
        return new MaleniaNonverbalAudioValues(
                nonverbalAudioEnabled.get(),
                nonverbalAudioVolume.get(),
                nonverbalAudioPitch.get(),
                nonverbalAudioHurtCooldownTicks.get(),
                nonverbalAudioGruntCooldownTicks.get()
        );
    }

            private static final class MaleniaSkillsValues {
            private final SkillHeader singleSlash;
            private final Supplier<Double> singleSlashRange;
            private final Supplier<Double> singleSlashArcDegrees;
            private final Supplier<Integer> singleSlashWindupTicks;
            private final Supplier<Integer> singleSlashActiveTicks;
            private final Supplier<Integer> singleSlashRecoveryTicks;
            private final Supplier<? extends UnmodifiableConfig> singleSlashDamage;

            private final SkillHeader doubleSlash;
            private final Supplier<Double> doubleSlashRange;
            private final Supplier<? extends List<?>> doubleSlashWindupTicks;
            private final Supplier<? extends List<?>> doubleSlashActiveTicks;
            private final Supplier<? extends List<?>> doubleSlashRecoveryTicks;
            private final Supplier<? extends List<?>> doubleSlashDamage;

            private final SkillHeader rapidSlashes;
            private final Supplier<Double> rapidSlashesRange;
            private final Supplier<Integer> rapidSlashesWindupTicks;
            private final Supplier<Integer> rapidSlashesActiveTicks;
            private final Supplier<Integer> rapidSlashesRecoveryTicks;
            private final Supplier<? extends UnmodifiableConfig> rapidSlashesOpeningDamage;
            private final Supplier<? extends UnmodifiableConfig> rapidSlashesFinisherDamage;
            private final Supplier<Integer> rapidSlashesOpeningHits;
            private final Supplier<Integer> rapidSlashesFinisherDelayTicks;

            private final SkillHeader runningSlash;
            private final Supplier<Double> runningSlashRange;
            private final Supplier<Integer> runningSlashWindupTicks;
            private final Supplier<Integer> runningSlashActiveTicks;
            private final Supplier<Integer> runningSlashRecoveryTicks;
            private final Supplier<? extends UnmodifiableConfig> runningSlashDamage;

            private final SkillHeader upwardCombo;
            private final Supplier<Double> upwardComboRange;
            private final Supplier<? extends List<?>> upwardComboWindupTicks;
            private final Supplier<? extends List<?>> upwardComboActiveTicks;
            private final Supplier<? extends List<?>> upwardComboRecoveryTicks;
            private final Supplier<? extends List<?>> upwardComboDamage;

            private final SkillHeader kick;
            private final Supplier<Double> kickRange;
            private final Supplier<Double> kickArcDegrees;
            private final Supplier<Integer> kickWindupTicks;
            private final Supplier<Integer> kickActiveTicks;
            private final Supplier<Integer> kickRecoveryTicks;
            private final Supplier<? extends UnmodifiableConfig> kickDamage;
            private final Supplier<Double> kickShieldStaminaMultiplier;
            private final Supplier<Boolean> kickHyperArmor;

            private final SkillHeader thrust;
            private final Supplier<Double> thrustRange;
            private final Supplier<Double> thrustWidth;
            private final Supplier<Integer> thrustWindupTicks;
            private final Supplier<Integer> thrustActiveTicks;
            private final Supplier<Integer> thrustRecoveryTicks;
            private final Supplier<? extends UnmodifiableConfig> thrustDamage;

            private final SkillHeader grabImpale;
            private final Supplier<Double> grabImpaleRange;
            private final Supplier<Double> grabImpaleWidth;
            private final Supplier<Integer> grabImpaleWindupTicks;
            private final Supplier<Integer> grabImpaleActiveTicks;
            private final Supplier<Integer> grabImpaleRecoveryTicks;
            private final Supplier<? extends UnmodifiableConfig> grabImpaleGrabDamage;
            private final Supplier<? extends UnmodifiableConfig> grabImpaleImpaleDamage;
            private final Supplier<? extends UnmodifiableConfig> grabImpaleThrowDamage;

            private final SkillHeader retreatSlash;
            private final Supplier<Double> retreatSlashRange;
            private final Supplier<Double> retreatSlashRetreatDistance;
            private final Supplier<Integer> retreatSlashWindupTicks;
            private final Supplier<Integer> retreatSlashActiveTicks;
            private final Supplier<Integer> retreatSlashRecoveryTicks;
            private final Supplier<? extends UnmodifiableConfig> retreatSlashDamage;

            private final SkillHeader waterfowlDance;
            private final Supplier<Integer> waterfowlFirstEligibleTicks;
            private final Supplier<Double> waterfowlPhaseOneFirstHealthRatio;
            private final Supplier<Integer> waterfowlPhaseTwoOpeningDelayTicks;
            private final Supplier<Double> waterfowlMinimumStartRange;
            private final Supplier<Integer> waterfowlWindupTicks;
            private final Supplier<Integer> waterfowlActiveTicks;
            private final Supplier<Integer> waterfowlRecoveryTicks;
            private final Supplier<Integer> waterfowlBurstCount;
            private final Supplier<Double> waterfowlBurstWidth;
            private final Supplier<? extends List<?>> waterfowlBurstLockTicks;
            private final Supplier<? extends List<?>> waterfowlBurstMaxHitsPerTarget;
            private final Supplier<? extends List<?>> waterfowlBurstMaxTravel;
            private final Supplier<? extends UnmodifiableConfig> waterfowlSlashDamage;
            private final Supplier<Double> waterfowlActionHealCap;

            private final SkillHeader scarletAeonia;
            private final Supplier<Double> scarletAeoniaRadius;
            private final Supplier<Integer> scarletAeoniaWindupTicks;
            private final Supplier<Integer> scarletAeoniaActiveTicks;
            private final Supplier<Integer> scarletAeoniaRecoveryTicks;
            private final Supplier<Integer> scarletAeoniaTargetLockTick;
            private final Supplier<Integer> scarletAeoniaTelegraphStartTick;
            private final Supplier<? extends UnmodifiableConfig> scarletAeoniaDiveDamage;
            private final Supplier<? extends UnmodifiableConfig> scarletAeoniaExplosionDamage;
            private final Supplier<? extends UnmodifiableConfig> scarletAeoniaZoneDamage;
            private final Supplier<Double> scarletAeoniaDiveRotBuildup;
            private final Supplier<Double> scarletAeoniaExplosionRotBuildup;
            private final Supplier<Double> scarletAeoniaZoneRotBuildup;
            private final Supplier<Integer> scarletAeoniaZoneDurationTicks;
            private final Supplier<Integer> scarletAeoniaZoneIntervalTicks;

            private final SkillHeader scarletPlunge;
            private final Supplier<Double> scarletPlungeRange;
            private final Supplier<Integer> scarletPlungeWindupTicks;
            private final Supplier<Integer> scarletPlungeActiveTicks;
            private final Supplier<Integer> scarletPlungeRecoveryTicks;
            private final Supplier<? extends UnmodifiableConfig> scarletPlungeBladeDamage;
            private final Supplier<? extends UnmodifiableConfig> scarletPlungeBurstDamage;
            private final Supplier<Double> scarletPlungeBladeRotBuildup;
            private final Supplier<Double> scarletPlungeBurstRotBuildup;

            private final SkillHeader flyingSlash;
            private final Supplier<Double> flyingSlashRange;
            private final Supplier<? extends List<?>> flyingSlashWindupTicks;
            private final Supplier<? extends List<?>> flyingSlashActiveTicks;
            private final Supplier<? extends List<?>> flyingSlashRecoveryTicks;
            private final Supplier<? extends List<?>> flyingSlashDamage;
            private final Supplier<? extends List<?>> flyingSlashRotBuildup;

            private final SkillHeader scarletPhantoms;
            private final Supplier<Integer> scarletPhantomsWindupTicks;
            private final Supplier<Integer> scarletPhantomsActiveTicks;
            private final Supplier<Integer> scarletPhantomsRecoveryTicks;
            private final Supplier<Integer> scarletPhantomsCount;
            private final Supplier<Double> scarletPhantomsWidth;
            private final Supplier<Integer> scarletPhantomsIntervalTicks;
            private final Supplier<Integer> scarletPhantomsMaxEarlyHitsPerTarget;
            private final Supplier<Integer> scarletPhantomsMaxLateHitsPerTarget;
            private final Supplier<? extends UnmodifiableConfig> scarletPhantomsDamage;
            private final Supplier<? extends UnmodifiableConfig> scarletPhantomsDiveDamage;
            private final Supplier<Double> scarletPhantomsRotBuildup;
            private final Supplier<Double> scarletPhantomsDiveRotBuildup;
            private final Supplier<Boolean> scarletPhantomsHyperArmor;

            private final SkillHeader wingedSweep;
            private final Supplier<Double> wingedSweepRange;
            private final Supplier<Integer> wingedSweepWindupTicks;
            private final Supplier<Integer> wingedSweepActiveTicks;
            private final Supplier<Integer> wingedSweepRecoveryTicks;
            private final Supplier<? extends UnmodifiableConfig> wingedSweepDamage;
            private final Supplier<Double> wingedSweepRotBuildup;

            private MaleniaSkillsValues(
                //? if forge {
                ForgeConfigSpec.Builder builder
                //?} else {
                /*ModConfigSpec.Builder builder
                *///?}
            ) {
                builder.push("skills");

                builder.push("single_slash");
                singleSlash = new SkillHeader(builder, 1.0, 28, "standard", 1.00, 1.25);
                singleSlashRange = builder.defineInRange("range", 3.4, 0.01, 2048.0);
                singleSlashArcDegrees = builder.defineInRange("arc_degrees", 120.0, 0.01, 360.0);
                singleSlashWindupTicks = builder.defineInRange(
                    "windup_ticks", 10, 1, NetworkLimits.MAX_TICKS);
                singleSlashActiveTicks = builder.defineInRange(
                    "active_ticks", 3, 1, NetworkLimits.MAX_TICKS);
                singleSlashRecoveryTicks = builder.defineInRange(
                    "recovery_ticks", 14, 1, NetworkLimits.MAX_TICKS);
                singleSlashDamage = builder.define(
                    "damage", damageFormulaConfig(1.0, 0.45), ElderBossesCommonConfig::isDamageFormula);
                builder.pop();

                builder.push("double_slash");
                doubleSlash = new SkillHeader(builder, 1.0, 42, "standard", 1.00, 1.25);
                doubleSlashRange = builder.defineInRange("range", 3.5, 0.01, 2048.0);
                doubleSlashWindupTicks = builder.defineList(
                    "windup_ticks", List.of(11, 9), ElderBossesCommonConfig::isPositiveTick);
                doubleSlashActiveTicks = builder.defineList(
                    "active_ticks", List.of(3, 3), ElderBossesCommonConfig::isPositiveTick);
                doubleSlashRecoveryTicks = builder.defineList(
                    "recovery_ticks", List.of(6, 18), ElderBossesCommonConfig::isPositiveTick);
                doubleSlashDamage = builder.defineList(
                    "damage",
                    List.of(damageFormulaConfig(1.0, 0.42), damageFormulaConfig(1.0, 0.42)),
                    ElderBossesCommonConfig::isDamageFormula
                );
                builder.pop();

                builder.push("rapid_slashes");
                rapidSlashes = new SkillHeader(builder, 0.9, 70, "standard", 1.00, 1.25);
                rapidSlashes.components(builder, com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId.RAPID_SLASHES);
                rapidSlashesRange = builder.defineInRange("range", 3.6, 0.01, 2048.0);
                rapidSlashesWindupTicks = () -> 14;
                rapidSlashesActiveTicks = () -> 18;
                rapidSlashesRecoveryTicks = () -> 22;
                rapidSlashesOpeningDamage = builder.define(
                    "opening_damage", damageFormulaConfig(0.0, 0.34), ElderBossesCommonConfig::isDamageFormula);
                rapidSlashesFinisherDamage = builder.define(
                    "finisher_damage", damageFormulaConfig(2.0, 0.50), ElderBossesCommonConfig::isDamageFormula);
                rapidSlashesOpeningHits = builder.defineInRange("opening_hits", 3, 3, 3);
                rapidSlashesFinisherDelayTicks = () -> 8;
                builder.pop();

                builder.push("running_slash");
                runningSlash = new SkillHeader(builder, 0.9, 55, "standard", 1.30, 1.30);
                runningSlashRange = builder.defineInRange("range", 6.0, 0.01, 2048.0);
                runningSlashWindupTicks = builder.defineInRange(
                    "windup_ticks", 16, 1, NetworkLimits.MAX_TICKS);
                runningSlashActiveTicks = builder.defineInRange(
                    "active_ticks", 4, 1, NetworkLimits.MAX_TICKS);
                runningSlashRecoveryTicks = builder.defineInRange(
                    "recovery_ticks", 18, 1, NetworkLimits.MAX_TICKS);
                runningSlashDamage = builder.define(
                    "damage", damageFormulaConfig(2.0, 0.55), ElderBossesCommonConfig::isDamageFormula);
                builder.pop();

                builder.push("upward_combo");
                upwardCombo = new SkillHeader(builder, 0.75, 90, "heavy", 1.35, 1.30);
                upwardComboRange = builder.defineInRange("range", 3.5, 0.01, 2048.0);
                upwardComboWindupTicks = builder.defineList(
                    "windup_ticks", List.of(18, 14), ElderBossesCommonConfig::isPositiveTick);
                upwardComboActiveTicks = builder.defineList(
                    "active_ticks", List.of(4, 5), ElderBossesCommonConfig::isPositiveTick);
                upwardComboRecoveryTicks = builder.defineList(
                    "recovery_ticks", List.of(8, 24), ElderBossesCommonConfig::isPositiveTick);
                upwardComboDamage = builder.defineList(
                    "damage",
                    List.of(damageFormulaConfig(2.0, 0.50), damageFormulaConfig(3.0, 0.65)),
                    ElderBossesCommonConfig::isDamageFormula
                );
                builder.pop();

                builder.push("kick");
                kick = new SkillHeader(builder, 0.8, 50, "standard", 1.00, 1.20);
                kickRange = builder.defineInRange("range", 2.3, 0.01, 2048.0);
                kickArcDegrees = builder.defineInRange("arc_degrees", 100.0, 0.01, 360.0);
                kickWindupTicks = builder.defineInRange(
                    "windup_ticks", 9, 1, NetworkLimits.MAX_TICKS);
                kickActiveTicks = builder.defineInRange(
                    "active_ticks", 4, 1, NetworkLimits.MAX_TICKS);
                kickRecoveryTicks = builder.defineInRange(
                    "recovery_ticks", 18, 1, NetworkLimits.MAX_TICKS);
                kickDamage = builder.define(
                    "damage", damageFormulaConfig(2.0, 0.42), ElderBossesCommonConfig::isDamageFormula);
                kickShieldStaminaMultiplier = builder.defineInRange(
                    "shield_stamina_multiplier", 1.8, 0.0, 100.0);
                kickHyperArmor = builder.define("hyper_armor", true);
                builder.pop();

                builder.push("thrust");
                thrust = new SkillHeader(builder, 0.75, 85, "heavy", 1.40, 1.30);
                thrustRange = builder.defineInRange("range", 7.0, 0.01, 2048.0);
                thrustWidth = builder.defineInRange("width", 1.2, 0.01, 2048.0);
                thrustWindupTicks = builder.defineInRange(
                    "windup_ticks", 22, 1, NetworkLimits.MAX_TICKS);
                thrustActiveTicks = builder.defineInRange(
                    "active_ticks", 4, 1, NetworkLimits.MAX_TICKS);
                thrustRecoveryTicks = builder.defineInRange(
                    "recovery_ticks", 24, 1, NetworkLimits.MAX_TICKS);
                thrustDamage = builder.define(
                    "damage", damageFormulaConfig(3.0, 0.70), ElderBossesCommonConfig::isDamageFormula);
                builder.pop();

                builder.push("grab_impale");
                grabImpale = new SkillHeader(builder, 0.45, 180, "grab", 1.50, 1.35);
                grabImpale.components(builder, com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId.GRAB_IMPALE);
                grabImpaleRange = builder.defineInRange("range", 5.0, 0.01, 2048.0);
                grabImpaleWidth = builder.defineInRange("width", 1.2, 0.01, 2048.0);
                grabImpaleWindupTicks = () -> 24;
                grabImpaleActiveTicks = () -> 5;
                grabImpaleRecoveryTicks = () -> 38;
                grabImpaleGrabDamage = builder.define(
                    "grab_damage", damageFormulaConfig(0.0, 0.0), ElderBossesCommonConfig::isDamageFormula);
                grabImpaleImpaleDamage = builder.define(
                    "impale_damage", damageFormulaConfig(6.0, 0.95), ElderBossesCommonConfig::isDamageFormula);
                grabImpaleThrowDamage = builder.define(
                    "throw_damage", damageFormulaConfig(2.0, 0.35), ElderBossesCommonConfig::isDamageFormula);
                builder.pop();

                builder.push("retreat_slash");
                retreatSlash = new SkillHeader(builder, 0.8, 65, "standard", 1.00, 1.25);
                retreatSlashRange = builder.defineInRange("range", 3.4, 0.01, 2048.0);
                retreatSlashRetreatDistance = builder.defineInRange("retreat_distance", 3.0, 0.01, 2048.0);
                retreatSlashWindupTicks = builder.defineInRange(
                    "windup_ticks", 8, 1, NetworkLimits.MAX_TICKS);
                retreatSlashActiveTicks = builder.defineInRange(
                    "active_ticks", 4, 1, NetworkLimits.MAX_TICKS);
                retreatSlashRecoveryTicks = builder.defineInRange(
                    "recovery_ticks", 20, 1, NetworkLimits.MAX_TICKS);
                retreatSlashDamage = builder.define(
                    "damage", damageFormulaConfig(1.0, 0.45), ElderBossesCommonConfig::isDamageFormula);
                builder.pop();

                builder.push("waterfowl_dance");
                waterfowlDance = new SkillHeader(builder, 0.40, 320, "waterfowl", 1.40, 1.35);
                waterfowlDance.components(builder, com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId.WATERFOWL_DANCE);
                waterfowlFirstEligibleTicks = builder.defineInRange(
                    "first_eligible_ticks", 260, 0, Integer.MAX_VALUE);
                waterfowlPhaseOneFirstHealthRatio = builder.defineInRange(
                    "phase_one_first_health_ratio", 0.70, 0.01, 1.0);
                waterfowlPhaseTwoOpeningDelayTicks = builder.defineInRange(
                    "phase_two_opening_delay_ticks", 180, 0, Integer.MAX_VALUE);
                waterfowlMinimumStartRange = builder.defineInRange(
                    "minimum_start_range", 6.0, 0.01, 2048.0);
                waterfowlWindupTicks = () -> 32;
                waterfowlActiveTicks = () -> 68;
                waterfowlRecoveryTicks = () -> 42;
                waterfowlBurstCount = builder.defineInRange("burst_count", 4, 4, 4);
                waterfowlBurstWidth = builder.defineInRange("burst_width", 3.5, 0.01, 2048.0);
                waterfowlBurstLockTicks = () -> List.of(22, 46, 62, 78);
                waterfowlBurstMaxHitsPerTarget = builder.defineList(
                    "burst_max_hits_per_target", List.of(2, 2, 2, 1), ElderBossesCommonConfig::isPositiveInteger);
                waterfowlBurstMaxTravel = builder.defineList(
                    "burst_max_travel", List.of(7.0, 6.0, 5.0, 4.0), ElderBossesCommonConfig::isPositiveNumber);
                waterfowlSlashDamage = builder.define(
                    "slash_damage", damageFormulaConfig(0.0, 0.22), ElderBossesCommonConfig::isDamageFormula);
                waterfowlActionHealCap = builder.defineInRange("action_heal_cap", 16.0, 0.0, 1_000_000.0);
                builder.pop();

                builder.push("scarlet_aeonia");
                scarletAeonia = new SkillHeader(builder, 0.35, 360, "none", 1.50, 1.50);
                scarletAeonia.components(builder, com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId.SCARLET_AEONIA);
                scarletAeoniaRadius = builder.defineInRange("radius", 5.5, 0.01, 2048.0);
                scarletAeoniaWindupTicks = () -> 42;
                scarletAeoniaActiveTicks = () -> 58;
                scarletAeoniaRecoveryTicks = () -> 54;
                scarletAeoniaTargetLockTick = () -> 26;
                scarletAeoniaTelegraphStartTick = () -> 27;
                scarletAeoniaDiveDamage = builder.define(
                    "dive_damage", damageFormulaConfig(3.0, 0.60), ElderBossesCommonConfig::isDamageFormula);
                scarletAeoniaExplosionDamage = builder.define(
                    "explosion_damage", damageFormulaConfig(5.0, 0.90), ElderBossesCommonConfig::isDamageFormula);
                scarletAeoniaZoneDamage = builder.define(
                    "zone_damage", damageFormulaConfig(0.0, 0.15), ElderBossesCommonConfig::isDamageFormula);
                scarletAeoniaDiveRotBuildup = builder.defineInRange(
                    "dive_rot_buildup", 15.0, 0.0, 1_000_000.0);
                scarletAeoniaExplosionRotBuildup = builder.defineInRange(
                    "explosion_rot_buildup", 45.0, 0.0, 1_000_000.0);
                scarletAeoniaZoneRotBuildup = builder.defineInRange(
                    "zone_rot_buildup", 12.0, 0.0, 1_000_000.0);
                scarletAeoniaZoneDurationTicks = builder.defineInRange(
                    "zone_duration_ticks", 84, 1, NetworkLimits.MAX_TICKS);
                scarletAeoniaZoneIntervalTicks = builder.defineInRange(
                    "zone_interval_ticks", 20, 1, NetworkLimits.MAX_TICKS);
                builder.pop();

                builder.push("scarlet_plunge");
                scarletPlunge = new SkillHeader(builder, 0.65, 130, "heavy", 1.35, 1.35);
                scarletPlunge.components(builder, com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId.SCARLET_PLUNGE);
                scarletPlungeRange = builder.defineInRange("range", 4.0, 0.01, 2048.0);
                scarletPlungeWindupTicks = () -> 24;
                scarletPlungeActiveTicks = () -> 12;
                scarletPlungeRecoveryTicks = () -> 30;
                scarletPlungeBladeDamage = builder.define(
                    "blade_damage", damageFormulaConfig(3.0, 0.65), ElderBossesCommonConfig::isDamageFormula);
                scarletPlungeBurstDamage = builder.define(
                    "burst_damage", damageFormulaConfig(2.0, 0.45), ElderBossesCommonConfig::isDamageFormula);
                scarletPlungeBladeRotBuildup = builder.defineInRange(
                    "blade_rot_buildup", 8.0, 0.0, 1_000_000.0);
                scarletPlungeBurstRotBuildup = builder.defineInRange(
                    "burst_rot_buildup", 24.0, 0.0, 1_000_000.0);
                builder.pop();

                builder.push("flying_slash");
                flyingSlash = new SkillHeader(builder, 0.75, 120, "heavy", 1.35, 1.30);
                flyingSlashRange = builder.defineInRange("range", 7.0, 0.01, 2048.0);
                flyingSlashWindupTicks = builder.defineList(
                    "windup_ticks", List.of(20, 12), ElderBossesCommonConfig::isPositiveTick);
                flyingSlashActiveTicks = builder.defineList(
                    "active_ticks", List.of(5, 4), ElderBossesCommonConfig::isPositiveTick);
                flyingSlashRecoveryTicks = builder.defineList(
                    "recovery_ticks", List.of(8, 24), ElderBossesCommonConfig::isPositiveTick);
                flyingSlashDamage = builder.defineList(
                    "damage",
                    List.of(damageFormulaConfig(2.0, 0.50), damageFormulaConfig(3.0, 0.65)),
                    ElderBossesCommonConfig::isDamageFormula
                );
                flyingSlashRotBuildup = builder.defineList(
                    "rot_buildup", List.of(6.0, 9.0), ElderBossesCommonConfig::isPositiveNumber);
                builder.pop();

                builder.push("scarlet_phantoms");
                scarletPhantoms = new SkillHeader(builder, 0.35, 280, "none", 1.50, 1.45);
                scarletPhantoms.components(builder, com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId.SCARLET_PHANTOMS);
                scarletPhantomsWindupTicks = () -> 36;
                scarletPhantomsActiveTicks = () -> 72;
                scarletPhantomsRecoveryTicks = () -> 38;
                scarletPhantomsCount = builder.defineInRange("phantom_count", 5, 1, Integer.MAX_VALUE);
                scarletPhantomsWidth = builder.defineInRange("phantom_width", 1.6, 0.01, 2048.0);
                scarletPhantomsIntervalTicks = () -> 8;
                scarletPhantomsMaxEarlyHitsPerTarget = builder.defineInRange(
                    "max_early_hits_per_target", 2, 1, Integer.MAX_VALUE);
                scarletPhantomsMaxLateHitsPerTarget = builder.defineInRange(
                    "max_late_hits_per_target", 2, 1, Integer.MAX_VALUE);
                scarletPhantomsDamage = builder.define(
                    "phantom_damage", damageFormulaConfig(1.0, 0.28), ElderBossesCommonConfig::isDamageFormula);
                scarletPhantomsDiveDamage = builder.define(
                    "dive_damage", damageFormulaConfig(4.0, 0.75), ElderBossesCommonConfig::isDamageFormula);
                scarletPhantomsRotBuildup = builder.defineInRange(
                    "phantom_rot_buildup", 8.0, 0.0, 1_000_000.0);
                scarletPhantomsDiveRotBuildup = builder.defineInRange(
                    "dive_rot_buildup", 12.0, 0.0, 1_000_000.0);
                scarletPhantomsHyperArmor = builder.define("hyper_armor", true);
                builder.pop();

                builder.push("winged_sweep");
                wingedSweep = new SkillHeader(builder, 0.70, 85, "standard", 1.30, 1.30);
                wingedSweepRange = builder.defineInRange("range", 4.0, 0.01, 2048.0);
                wingedSweepWindupTicks = builder.defineInRange(
                    "windup_ticks", 16, 1, NetworkLimits.MAX_TICKS);
                wingedSweepActiveTicks = builder.defineInRange(
                    "active_ticks", 8, 1, NetworkLimits.MAX_TICKS);
                wingedSweepRecoveryTicks = builder.defineInRange(
                    "recovery_ticks", 22, 1, NetworkLimits.MAX_TICKS);
                wingedSweepDamage = builder.define(
                    "damage", damageFormulaConfig(2.0, 0.55), ElderBossesCommonConfig::isDamageFormula);
                wingedSweepRotBuildup = builder.defineInRange("rot_buildup", 10.0, 0.0, 1_000_000.0);
                builder.pop();

                builder.pop();
            }

                private MaleniaSkillConfigSnapshot snapshot(
                    MaleniaSkillConfigSnapshot.PhaseTwoRot phaseTwoRot
                ) {
                return new MaleniaSkillConfigSnapshot(
                    phaseTwoRot,
                    new MaleniaSkillConfigSnapshot.SingleSlash(
                        singleSlash.enabled(),
                        singleSlash.weight(),
                        singleSlash.cooldownTicks(),
                        singleSlashRange.get(),
                        singleSlashArcDegrees.get(),
                        singleSlashWindupTicks.get(),
                        singleSlashActiveTicks.get(),
                        singleSlashRecoveryTicks.get(),
                        readDamageFormula(singleSlashDamage.get()).toDamageFormula(),
                        singleSlash.healProfile()
                    ),
                    new MaleniaSkillConfigSnapshot.DoubleSlash(
                        doubleSlash.enabled(),
                        doubleSlash.weight(),
                        doubleSlash.cooldownTicks(),
                        doubleSlashRange.get(),
                        readIntegerList(doubleSlashWindupTicks.get(), List.of(11, 9)),
                        readIntegerList(doubleSlashActiveTicks.get(), List.of(3, 3)),
                        readIntegerList(doubleSlashRecoveryTicks.get(), List.of(6, 18)),
                        readDamageFormulaList(
                            doubleSlashDamage.get(),
                            List.of(damageFormulaConfig(1.0, 0.42), damageFormulaConfig(1.0, 0.42))
                        ),
                        doubleSlash.healProfile()
                    ),
                    new MaleniaSkillConfigSnapshot.RapidSlashes(
                        rapidSlashes.enabled(),
                        rapidSlashes.weight(),
                        rapidSlashes.cooldownTicks(),
                        rapidSlashesRange.get(),
                        rapidSlashesWindupTicks.get(),
                        rapidSlashesActiveTicks.get(),
                        rapidSlashesRecoveryTicks.get(),
                        readDamageFormula(rapidSlashesOpeningDamage.get()).toDamageFormula(),
                        readDamageFormula(rapidSlashesFinisherDamage.get()).toDamageFormula(),
                        rapidSlashesOpeningHits.get(),
                        rapidSlashesFinisherDelayTicks.get(),
                        rapidSlashes.healProfile()
                    ),
                    new MaleniaSkillConfigSnapshot.RunningSlash(
                        runningSlash.enabled(),
                        runningSlash.weight(),
                        runningSlash.cooldownTicks(),
                        runningSlashRange.get(),
                        runningSlashWindupTicks.get(),
                        runningSlashActiveTicks.get(),
                        runningSlashRecoveryTicks.get(),
                        readDamageFormula(runningSlashDamage.get()).toDamageFormula(),
                        runningSlash.healProfile()
                    ),
                    new MaleniaSkillConfigSnapshot.UpwardCombo(
                        upwardCombo.enabled(),
                        upwardCombo.weight(),
                        upwardCombo.cooldownTicks(),
                        upwardComboRange.get(),
                        readIntegerList(upwardComboWindupTicks.get(), List.of(18, 14)),
                        readIntegerList(upwardComboActiveTicks.get(), List.of(4, 5)),
                        readIntegerList(upwardComboRecoveryTicks.get(), List.of(8, 24)),
                        readDamageFormulaList(
                            upwardComboDamage.get(),
                            List.of(damageFormulaConfig(2.0, 0.50), damageFormulaConfig(3.0, 0.65))
                        ),
                        upwardCombo.healProfile()
                    ),
                    new MaleniaSkillConfigSnapshot.Kick(
                        kick.enabled(),
                        kick.weight(),
                        kick.cooldownTicks(),
                        kickRange.get(),
                        kickArcDegrees.get(),
                        kickWindupTicks.get(),
                        kickActiveTicks.get(),
                        kickRecoveryTicks.get(),
                        readDamageFormula(kickDamage.get()).toDamageFormula(),
                        kickShieldStaminaMultiplier.get(),
                        kick.healProfile(),
                        kickHyperArmor.get()
                    ),
                    new MaleniaSkillConfigSnapshot.Thrust(
                        thrust.enabled(),
                        thrust.weight(),
                        thrust.cooldownTicks(),
                        thrustRange.get(),
                        thrustWidth.get(),
                        thrustWindupTicks.get(),
                        thrustActiveTicks.get(),
                        thrustRecoveryTicks.get(),
                        readDamageFormula(thrustDamage.get()).toDamageFormula(),
                        thrust.healProfile()
                    ),
                    new MaleniaSkillConfigSnapshot.GrabImpale(
                        grabImpale.enabled(),
                        grabImpale.weight(),
                        grabImpale.cooldownTicks(),
                        grabImpaleRange.get(),
                        grabImpaleWidth.get(),
                        grabImpaleWindupTicks.get(),
                        grabImpaleActiveTicks.get(),
                        grabImpaleRecoveryTicks.get(),
                        readDamageFormula(grabImpaleGrabDamage.get()).toDamageFormula(),
                        readDamageFormula(grabImpaleImpaleDamage.get()).toDamageFormula(),
                        readDamageFormula(grabImpaleThrowDamage.get()).toDamageFormula(),
                        grabImpale.healProfile()
                    ),
                    new MaleniaSkillConfigSnapshot.RetreatSlash(
                        retreatSlash.enabled(),
                        retreatSlash.weight(),
                        retreatSlash.cooldownTicks(),
                        retreatSlashRange.get(),
                        retreatSlashRetreatDistance.get(),
                        retreatSlashWindupTicks.get(),
                        retreatSlashActiveTicks.get(),
                        retreatSlashRecoveryTicks.get(),
                        readDamageFormula(retreatSlashDamage.get()).toDamageFormula(),
                        retreatSlash.healProfile()
                    ),
                    new MaleniaSkillConfigSnapshot.WaterfowlDance(
                        waterfowlDance.enabled(),
                        waterfowlDance.weight(),
                        waterfowlDance.cooldownTicks(),
                        waterfowlFirstEligibleTicks.get(),
                        waterfowlPhaseOneFirstHealthRatio.get(),
                        waterfowlPhaseTwoOpeningDelayTicks.get(),
                        waterfowlMinimumStartRange.get(),
                        waterfowlWindupTicks.get(),
                        waterfowlActiveTicks.get(),
                        waterfowlRecoveryTicks.get(),
                        waterfowlBurstCount.get(),
                        waterfowlBurstWidth.get(),
                        readIntegerList(waterfowlBurstLockTicks.get(), List.of(22, 46, 62, 78)),
                        readIntegerList(waterfowlBurstMaxHitsPerTarget.get(), List.of(2, 2, 2, 1)),
                        readDoubleList(waterfowlBurstMaxTravel.get(), List.of(7.0, 6.0, 5.0, 4.0)),
                        readDamageFormula(waterfowlSlashDamage.get()).toDamageFormula(),
                        waterfowlActionHealCap.get(),
                        waterfowlDance.healProfile()
                    ),
                    new MaleniaSkillConfigSnapshot.ScarletAeonia(
                        scarletAeonia.enabled(),
                        scarletAeonia.weight(),
                        scarletAeonia.cooldownTicks(),
                        scarletAeoniaRadius.get(),
                        scarletAeoniaWindupTicks.get(),
                        scarletAeoniaActiveTicks.get(),
                        scarletAeoniaRecoveryTicks.get(),
                        scarletAeoniaTargetLockTick.get(),
                        scarletAeoniaTelegraphStartTick.get(),
                        readDamageFormula(scarletAeoniaDiveDamage.get()).toDamageFormula(),
                        readDamageFormula(scarletAeoniaExplosionDamage.get()).toDamageFormula(),
                        readDamageFormula(scarletAeoniaZoneDamage.get()).toDamageFormula(),
                        scarletAeoniaDiveRotBuildup.get(),
                        scarletAeoniaExplosionRotBuildup.get(),
                        scarletAeoniaZoneRotBuildup.get(),
                        scarletAeoniaZoneDurationTicks.get(),
                        scarletAeoniaZoneIntervalTicks.get(),
                        scarletAeonia.healProfile()
                    ),
                    new MaleniaSkillConfigSnapshot.ScarletPlunge(
                        scarletPlunge.enabled(),
                        scarletPlunge.weight(),
                        scarletPlunge.cooldownTicks(),
                        scarletPlungeRange.get(),
                        scarletPlungeWindupTicks.get(),
                        scarletPlungeActiveTicks.get(),
                        scarletPlungeRecoveryTicks.get(),
                        readDamageFormula(scarletPlungeBladeDamage.get()).toDamageFormula(),
                        readDamageFormula(scarletPlungeBurstDamage.get()).toDamageFormula(),
                        scarletPlungeBladeRotBuildup.get(),
                        scarletPlungeBurstRotBuildup.get(),
                        scarletPlunge.healProfile()
                    ),
                    new MaleniaSkillConfigSnapshot.FlyingSlash(
                        flyingSlash.enabled(),
                        flyingSlash.weight(),
                        flyingSlash.cooldownTicks(),
                        flyingSlashRange.get(),
                        readIntegerList(flyingSlashWindupTicks.get(), List.of(20, 12)),
                        readIntegerList(flyingSlashActiveTicks.get(), List.of(5, 4)),
                        readIntegerList(flyingSlashRecoveryTicks.get(), List.of(8, 24)),
                        readDamageFormulaList(
                            flyingSlashDamage.get(),
                            List.of(damageFormulaConfig(2.0, 0.50), damageFormulaConfig(3.0, 0.65))
                        ),
                        readDoubleList(flyingSlashRotBuildup.get(), List.of(6.0, 9.0)),
                        flyingSlash.healProfile()
                    ),
                    new MaleniaSkillConfigSnapshot.ScarletPhantoms(
                        scarletPhantoms.enabled(),
                        scarletPhantoms.weight(),
                        scarletPhantoms.cooldownTicks(),
                        scarletPhantomsWindupTicks.get(),
                        scarletPhantomsActiveTicks.get(),
                        scarletPhantomsRecoveryTicks.get(),
                        scarletPhantomsCount.get(),
                        scarletPhantomsWidth.get(),
                        scarletPhantomsIntervalTicks.get(),
                        scarletPhantomsMaxEarlyHitsPerTarget.get(),
                        scarletPhantomsMaxLateHitsPerTarget.get(),
                        readDamageFormula(scarletPhantomsDamage.get()).toDamageFormula(),
                        readDamageFormula(scarletPhantomsDiveDamage.get()).toDamageFormula(),
                        scarletPhantomsRotBuildup.get(),
                        scarletPhantomsDiveRotBuildup.get(),
                        scarletPhantoms.healProfile(),
                        scarletPhantomsHyperArmor.get()
                    ),
                    new MaleniaSkillConfigSnapshot.WingedSweep(
                        wingedSweep.enabled(),
                        wingedSweep.weight(),
                        wingedSweep.cooldownTicks(),
                        wingedSweepRange.get(),
                        wingedSweepWindupTicks.get(),
                        wingedSweepActiveTicks.get(),
                        wingedSweepRecoveryTicks.get(),
                        readDamageFormula(wingedSweepDamage.get()).toDamageFormula(),
                        wingedSweepRotBuildup.get(),
                        wingedSweep.healProfile()
                        ),
                        tunings()
                );
            }

                    private Map<com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId,
                        com.tonywww.elder_bosses.combat.action.SkillTuning> tunings() {
                    Map<com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId,
                        com.tonywww.elder_bosses.combat.action.SkillTuning> values =
                        new java.util.EnumMap<>(
                            com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId.class
                        );
                    values.put(com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId.SINGLE_SLASH, singleSlash.tuning());
                    values.put(com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId.DOUBLE_SLASH, doubleSlash.tuning());
                    values.put(com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId.RAPID_SLASHES, rapidSlashes.tuning());
                    values.put(com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId.RUNNING_SLASH, runningSlash.tuning());
                    values.put(com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId.UPWARD_COMBO, upwardCombo.tuning());
                    values.put(com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId.KICK, kick.tuning());
                    values.put(com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId.THRUST, thrust.tuning());
                    values.put(com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId.GRAB_IMPALE, grabImpale.tuning());
                    values.put(com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId.RETREAT_SLASH, retreatSlash.tuning());
                    values.put(com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId.WATERFOWL_DANCE, waterfowlDance.tuning());
                    values.put(com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId.SCARLET_AEONIA, scarletAeonia.tuning());
                    values.put(com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId.SCARLET_PLUNGE, scarletPlunge.tuning());
                    values.put(com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId.FLYING_SLASH, flyingSlash.tuning());
                    values.put(com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId.SCARLET_PHANTOMS, scarletPhantoms.tuning());
                    values.put(com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId.WINGED_SWEEP, wingedSweep.tuning());
                    return values;
                    }
            }

            private static final class SkillHeader {
            private final Supplier<Boolean> enabled;
            private final Supplier<Double> weight;
            private final Supplier<Integer> cooldownTicks;
            private final Supplier<String> healProfile;
            private final Supplier<Double> rangeMultiplier;
            private Supplier<List<? extends Integer>> componentWindup;
            private Supplier<List<? extends Integer>> componentActive;
            private Supplier<List<? extends Integer>> componentRecovery;
            private int componentCount;

            private SkillHeader(
                //? if forge {
                ForgeConfigSpec.Builder builder,
                //?} else {
                /*ModConfigSpec.Builder builder,
                *///?}
                double defaultWeight,
                int defaultCooldownTicks,
                String defaultHealProfile,
                double defaultCastSpeedMultiplier,
                double defaultRangeMultiplier
            ) {
                enabled = builder.define("enabled", true);
                weight = builder.defineInRange("weight", defaultWeight, 0.0, 100.0);
                cooldownTicks = builder.defineInRange(
                    "cooldown_ticks", defaultCooldownTicks, 1, Integer.MAX_VALUE);
                healProfile = builder.define(
                    "heal_profile", defaultHealProfile, ElderBossesCommonConfig::isHealProfile);
                rangeMultiplier = builder.defineInRange(
                    "range_multiplier", defaultRangeMultiplier, 0.1, 5.0);
            }

            private boolean enabled() {
                return enabled.get();
            }

            private void components(
                //? if forge {
                ForgeConfigSpec.Builder builder,
                //?} else {
                /*ModConfigSpec.Builder builder,
                *///?}
                com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId action
            ) {
                var stages = MaleniaSkillConfigSnapshot.defaultComponentStages(action);
                componentCount = stages.size();
                builder.push("components");
                componentWindup = builder.defineList("windup_ticks", stages.stream().map(stage -> stage.windupTicks()).toList(),
                    value -> value instanceof Number number && number.doubleValue() >= 0 && number.doubleValue() <= NetworkLimits.MAX_TICKS && number.doubleValue() == Math.rint(number.doubleValue()));
                componentActive = builder.defineList("active_ticks", stages.stream().map(stage -> stage.activeTicks()).toList(), ElderBossesCommonConfig::isPositiveTick);
                componentRecovery = builder.defineList("recovery_ticks", stages.stream().map(stage -> stage.recoveryTicks()).toList(),
                    value -> value instanceof Number number && number.doubleValue() >= 0 && number.doubleValue() <= NetworkLimits.MAX_TICKS && number.doubleValue() == Math.rint(number.doubleValue()));
                builder.pop();
            }

            private double weight() {
                return weight.get();
            }

            private int cooldownTicks() {
                return cooldownTicks.get();
            }

            private HealProfile healProfile() {
                return readHealProfile(healProfile.get());
            }

            private com.tonywww.elder_bosses.combat.action.SkillTuning tuning() {
                if (componentCount > 0) {
                    var windup = componentWindup.get();
                    var active = componentActive.get();
                    var recovery = componentRecovery.get();
                    if (windup.size() != componentCount || active.size() != componentCount || recovery.size() != componentCount)
                        throw new IllegalArgumentException("All component tick lists must match the fixed action component count");
                    var stages = new java.util.ArrayList<com.tonywww.elder_bosses.combat.action.ActionStage>();
                    for (int index = 0; index < componentCount; index++) stages.add(new com.tonywww.elder_bosses.combat.action.ActionStage(
                        windup.get(index), active.get(index), recovery.get(index)));
                    return new com.tonywww.elder_bosses.combat.action.SkillTuning(1.0, rangeMultiplier.get(), stages);
                }
                return new com.tonywww.elder_bosses.combat.action.SkillTuning(
                        rangeMultiplier.get()
                );
            }
            }

    private static List<Config> defaultDistanceBandConfigs() {
        return List.of(
                distanceBandConfig(4.0, 1.00),
                distanceBandConfig(8.0, 0.70),
                distanceBandConfig(16.0, 0.40),
                distanceBandConfig(-1.0, 0.20)
        );
    }

    private static Config distanceBandConfig(double maxDistance, double multiplier) {
        Config config = Config.inMemory();
        config.set("max_distance", maxDistance);
        config.set("multiplier", multiplier);
        return config;
    }

    private static Config damageFormulaConfig(double flat, double attackRatio) {
        Config config = Config.inMemory();
        config.set("flat", flat);
        config.set("attack_ratio", attackRatio);
        return config;
    }

    private static boolean isDamageFormula(Object value) {
        if (!(value instanceof UnmodifiableConfig config)) {
            return false;
        }
        Object flat = config.get("flat");
        Object attackRatio = config.get("attack_ratio");
        return flat instanceof Number flatNumber
                && attackRatio instanceof Number attackRatioNumber
                && Double.isFinite(flatNumber.doubleValue())
                && flatNumber.doubleValue() >= 0.0
                && flatNumber.doubleValue() <= 2048.0
                && Double.isFinite(attackRatioNumber.doubleValue())
                && attackRatioNumber.doubleValue() >= 0.0
                && attackRatioNumber.doubleValue() <= 10.0;
    }

    private static DamageFormulaValues readDamageFormula(UnmodifiableConfig config) {
        Number flat = config.get("flat");
        Number attackRatio = config.get("attack_ratio");
        return new DamageFormulaValues(flat.doubleValue(), attackRatio.doubleValue());
    }

    private static DamageFormula toCombatDamageFormula(DamageFormulaValues values) {
        return new DamageFormula(values.flat(), values.attackRatio());
    }

    private static List<DamageFormula> readDamageFormulaList(List<?> configuredValues) {
        List<DamageFormula> values = new ArrayList<>(configuredValues.size());
        for (Object configuredValue : configuredValues) {
            values.add(readDamageFormula((UnmodifiableConfig) configuredValue).toDamageFormula());
        }
        return List.copyOf(values);
    }

    private static List<DamageFormula> readDamageFormulaList(
            List<?> configuredValues,
            List<? extends UnmodifiableConfig> defaultValues
    ) {
        return readDamageFormulaList(
                configuredValues.size() == defaultValues.size() ? configuredValues : defaultValues
        );
    }

    private static List<Integer> readIntegerList(List<?> configuredValues) {
        List<Integer> values = new ArrayList<>(configuredValues.size());
        for (Object configuredValue : configuredValues) {
            values.add(((Number) configuredValue).intValue());
        }
        return List.copyOf(values);
    }

    private static List<Integer> readIntegerList(
            List<?> configuredValues,
            List<Integer> defaultValues
    ) {
        return readIntegerList(
                configuredValues.size() == defaultValues.size() ? configuredValues : defaultValues
        );
    }

    private static List<Double> readDoubleList(List<?> configuredValues) {
        List<Double> values = new ArrayList<>(configuredValues.size());
        for (Object configuredValue : configuredValues) {
            values.add(((Number) configuredValue).doubleValue());
        }
        return List.copyOf(values);
    }

    private static List<Double> readDoubleList(
            List<?> configuredValues,
            List<Double> defaultValues
    ) {
        return readDoubleList(
                configuredValues.size() == defaultValues.size() ? configuredValues : defaultValues
        );
    }

    private static boolean isPositiveInteger(Object value) {
        if (!(value instanceof Number number)) {
            return false;
        }
        double numericValue = number.doubleValue();
        return Double.isFinite(numericValue)
                && numericValue >= 1.0
                && numericValue <= Integer.MAX_VALUE
                && numericValue == Math.rint(numericValue);
    }

    private static boolean isPositiveTick(Object value) {
        if (!(value instanceof Number number)) {
            return false;
        }
        double numericValue = number.doubleValue();
        return Double.isFinite(numericValue)
                && numericValue >= 1.0
                && numericValue <= NetworkLimits.MAX_TICKS
                && numericValue == Math.rint(numericValue);
    }

    private static boolean isPositiveNumber(Object value) {
        return value instanceof Number number
                && Double.isFinite(number.doubleValue())
                && number.doubleValue() > 0.0;
    }

    private static boolean isHealProfile(Object value) {
        return value instanceof String profile && switch (profile) {
            case "standard", "heavy", "waterfowl", "grab", "none" -> true;
            default -> false;
        };
    }

        private static MaleniaCombatConfigSnapshot.ResistanceProfile toCombatResistanceProfile(
            MaleniaResistanceValues values
        ) {
        return new MaleniaCombatConfigSnapshot.ResistanceProfile(
            values.physical(),
            values.fire(),
            values.magic(),
            values.lightning()
        );
        }

        private static MaleniaCombatConfigSnapshot.SourceMultiplierProfile
            toCombatSourceMultiplierProfile(MaleniaSourceMultiplierValues values) {
        return new MaleniaCombatConfigSnapshot.SourceMultiplierProfile(
            values.ordinaryPhysical(),
            values.pierce(),
            values.bleedTrigger(),
            values.magic(),
            values.holy(),
            values.frostTrigger()
        );
        }

    private static HealProfile readHealProfile(String value) {
        return switch (value) {
            case "standard" -> HealProfile.STANDARD;
            case "heavy" -> HealProfile.HEAVY;
            case "waterfowl" -> HealProfile.WATERFOWL;
            case "grab" -> HealProfile.GRAB;
            case "none" -> HealProfile.NONE;
            default -> throw new IllegalArgumentException("Unsupported Malenia heal profile: " + value);
        };
    }

    private static boolean isDistanceBand(Object value) {
        if (!(value instanceof UnmodifiableConfig config)) {
            return false;
        }
        Object maxDistance = config.get("max_distance");
        Object multiplier = config.get("multiplier");
        if (!(maxDistance instanceof Number maxDistanceNumber)
                || !(multiplier instanceof Number multiplierNumber)) {
            return false;
        }
        double maxDistanceValue = maxDistanceNumber.doubleValue();
        double multiplierValue = multiplierNumber.doubleValue();
        return Double.isFinite(maxDistanceValue)
                && (maxDistanceValue == -1.0 || maxDistanceValue >= 0.0 && maxDistanceValue <= 2048.0)
                && Double.isFinite(multiplierValue)
                && multiplierValue >= 0.0
                && multiplierValue <= 100.0;
    }

    private static List<DistanceBandValues> readDistanceBands(List<?> configuredBands) {
        if (configuredBands.size() != 4) {
            return defaultDistanceBandValues();
        }
        List<DistanceBandValues> values = new ArrayList<>(configuredBands.size());
        for (Object configuredBand : configuredBands) {
            if (!isDistanceBand(configuredBand)) {
                return defaultDistanceBandValues();
            }
            UnmodifiableConfig config = (UnmodifiableConfig) configuredBand;
            Number maxDistance = config.get("max_distance");
            Number multiplier = config.get("multiplier");
            values.add(new DistanceBandValues(maxDistance.doubleValue(), multiplier.doubleValue()));
        }
        return List.copyOf(values);
    }

    private static List<DistanceBand> toCombatDistanceBands(List<DistanceBandValues> values) {
        List<DistanceBand> distanceBands = new ArrayList<>(values.size());
        for (DistanceBandValues value : values) {
            double maximumDistance = value.maxDistance() == -1.0
                    ? Double.POSITIVE_INFINITY
                    : value.maxDistance();
            distanceBands.add(new DistanceBand(maximumDistance, value.multiplier()));
        }
        return List.copyOf(distanceBands);
    }

    private static List<DistanceBandValues> defaultDistanceBandValues() {
        return List.of(
                new DistanceBandValues(4.0, 1.00),
                new DistanceBandValues(8.0, 0.70),
                new DistanceBandValues(16.0, 0.40),
                new DistanceBandValues(-1.0, 0.20)
        );
    }

    public record IndicatorValues(
            boolean enabled,
            double opacity,
            double occludedOutlineOpacityMultiplier,
            double renderDistance,
            double surfaceOffset,
            int maxActiveIndicators,
            int maxSegmentsPerShape,
            boolean maleniaEnabled,
            boolean promisedConsortEnabled
    ) {
        public IndicatorValues(boolean enabled, double opacity, double occludedOutlineOpacityMultiplier,
                               double renderDistance, double surfaceOffset, int maxActiveIndicators, int maxSegmentsPerShape) {
            this(enabled, opacity, occludedOutlineOpacityMultiplier, renderDistance, surfaceOffset,
                    maxActiveIndicators, maxSegmentsPerShape, true, true);
        }

        public boolean rangeEnabled(boolean promisedConsort) {
            return enabled && (promisedConsort ? promisedConsortEnabled : maleniaEnabled);
        }
    }

        public record SkillVfxValues(
            boolean enabled,
            int particleBudgetPerBossPerTick,
            double renderDistance
        ) {
        }

    public record MaleniaGeneralValues(
            double phaseOneHealth,
            double phaseTwoHealth,
            double phaseTwoStartRatio,
            double attackDamage,
            double movementSpeed,
            double followRange,
            double knockbackResistance,
            int maxActivePlayers
    ) {
    }

        public record MaleniaPhaseResistanceValues(
            MaleniaResistanceValues phaseOne,
            MaleniaResistanceValues phaseTwo
        ) {
        }

        public record MaleniaResistanceValues(
            double physical,
            double fire,
            double magic,
            double lightning
        ) {
        }

        public record MaleniaPhaseSourceMultiplierValues(
            MaleniaSourceMultiplierValues phaseOne,
            MaleniaSourceMultiplierValues phaseTwo
        ) {
        }

        public record MaleniaSourceMultiplierValues(
            double ordinaryPhysical,
            double pierce,
            double bleedTrigger,
            double magic,
            double holy,
            double frostTrigger
        ) {
        }

    public record MaleniaMultiplayerValues(
            double healthPerExtraPlayer,
            double healingWindowCapPerExtraPlayer,
            int retargetIntervalTicks,
            int sameTargetPenaltyAfterTicks,
            double sameTargetScoreMultiplier
    ) {
    }

        public record MaleniaTargetingValues(
            double distanceWeight,
            double recentDamageWeight,
            double itemUseWeight,
            double interruptWeight,
            int recentDamageWindowTicks
        ) {
        }

        public record MaleniaSelectorValues(
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
        }

    public record MaleniaArenaValues(
            double logicalRadius,
            int leashGraceTicks,
            boolean logicalShallowWater,
            boolean unloadResetsFight,
            boolean bossIgnoresArenaHeightVariation,
            boolean allowBlockBreaking,
            String breakableBlockTag,
            String protectedBlockTag,
            double blockBreakRadius,
            int maxBlocksBrokenPerTick,
            boolean restoreBrokenBlocksOnReset,
            boolean allowWallPhasing,
            int wallPhaseMaxTicks
    ) {
    }

    public record MaleniaStaggerValues(
            double damageConversionRatio,
            double capacityHealthRatio,
            List<DistanceBandValues> distanceBands,
            int sourceDedupeTicks,
            int decayDelayTicks,
            double decayPerTick,
            int stunTicks,
            int postStunImmunityTicks,
            boolean resetOnPhaseChange
    ) {
        public MaleniaStaggerValues {
            distanceBands = List.copyOf(distanceBands);
        }
    }

    public record DistanceBandValues(double maxDistance, double multiplier) {
    }

    public record MaleniaInstantGuardValues(
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
    }

    public record MaleniaHealingValues(
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
            DamageFormulaValues standardHeal,
            DamageFormulaValues heavyHeal,
            DamageFormulaValues waterfowlHeal,
            DamageFormulaValues grabHeal
    ) {
    }

    public record DamageFormulaValues(double flat, double attackRatio) {
        private DamageFormula toDamageFormula() {
            return new DamageFormula(flat, attackRatio);
        }
    }

    public record MaleniaScarletRotValues(
            int decayDelayTicks,
            double decayPerTwentyTicks,
            int durationTicks,
            int damageIntervalTicks,
            DamageFormulaValues damage,
            double healingReduction,
            double movementSpeedReduction,
            double honeyBuildupReduction,
            boolean consumeCleanseItem,
            int cleanseUseTicks,
            boolean milkClearsRot
    ) {
    }

    public record MaleniaPhaseTransitionValues(
            int durationTicks,
            boolean clearOwnedSlashHazards,
            boolean preservePlayerRotBuildup,
            boolean resetStagger,
            boolean openingAeonia
    ) {
    }

        public record MaleniaPerformanceValues(int maxRotZones) {
    }

        public record MaleniaDialogueValues(
            boolean enabled,
            int maxQueuedLines,
            int subtitleDurationTicks,
            int playerDefeatDelayTicks,
            int introWarningTick,
            int transitionReleaseTick,
            int defeatedTick
        ) {
        }

        public record MaleniaNonverbalAudioValues(
            boolean enabled,
            double volume,
            double pitch,
            int hurtCooldownTicks,
            int gruntCooldownTicks
        ) {
        }

        public static final class MaleniaConsecratedProstheticBladeValues {
            private final double attackDamage;
            private final double attackSpeed;
            private final int durability;
            private final int enchantability;

            public MaleniaConsecratedProstheticBladeValues(
                    double attackDamage,
                    double attackSpeed,
                    int durability,
                    int enchantability
            ) {
                this.attackDamage = attackDamage;
                this.attackSpeed = attackSpeed;
                this.durability = durability;
                this.enchantability = enchantability;
            }

            public double attackDamage() {
                return attackDamage;
            }

            public double attackSpeed() {
                return attackSpeed;
            }

            public int durability() {
                return durability;
            }

            public int enchantability() {
                return enchantability;
            }
        }

        public static final class MaleniaUnalloyedWingedHelmValues {
            private final double armor;
            private final double armorToughness;
            private final double knockbackResistance;
            private final int durability;
            private final double rotBuildupMultiplier;

            public MaleniaUnalloyedWingedHelmValues(
                    double armor,
                    double armorToughness,
                    double knockbackResistance,
                    int durability,
                    double rotBuildupMultiplier
            ) {
                this.armor = armor;
                this.armorToughness = armorToughness;
                this.knockbackResistance = knockbackResistance;
                this.durability = durability;
                this.rotBuildupMultiplier = rotBuildupMultiplier;
            }

            public double armor() {
                return armor;
            }

            public double armorToughness() {
                return armorToughness;
            }

            public double knockbackResistance() {
                return knockbackResistance;
            }

            public int durability() {
                return durability;
            }

            public double rotBuildupMultiplier() {
                return rotBuildupMultiplier;
            }
        }
}