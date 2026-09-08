package com.tonywww.elder_bosses.platforms.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.mojang.logging.LogUtils;
import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortCombatConfigSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSkillConfigSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.combat.damage.DamageFormula;
import com.tonywww.elder_bosses.combat.state.StaggerTracker.DistanceBand;
import com.tonywww.elder_bosses.network.NetworkLimits;
//? if forge {
import net.minecraftforge.common.ForgeConfigSpec;
//?} else {
/*import net.neoforged.neoforge.common.ModConfigSpec;
*///?}

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;

public final class PromisedConsortConfigValues {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<String, Supplier<Boolean>> booleans = new HashMap<>();
    private final Map<String, Supplier<Integer>> integers = new HashMap<>();
    private final Map<String, Supplier<Double>> numbers = new HashMap<>();
    private final Map<String, Supplier<String>> strings = new HashMap<>();
    private final Map<String, Supplier<? extends List<?>>> lists = new HashMap<>();
    private final Map<String, Supplier<? extends UnmodifiableConfig>> formulas = new HashMap<>();
    private final Map<PromisedConsortActionId, SkillValues> skills =
            new EnumMap<>(PromisedConsortActionId.class);

    public PromisedConsortConfigValues(
            //? if forge {
            ForgeConfigSpec.Builder rawBuilder
            //?} else {
            /*ModConfigSpec.Builder rawBuilder
            *///?}
    ) {
        Builder builder = new Builder(rawBuilder);
        builder.push("promised_consort");
        defineGeneral(builder);
        defineEncounter(builder);
        defineDamage(builder);
        defineArenaAndTargeting(builder);
        defineCombatSystems(builder);
        definePresentation(builder);
        defineSkills(builder);
        builder.pop();
    }

    public PromisedConsortCombatConfigSnapshot combatSnapshot() {
        return new PromisedConsortCombatConfigSnapshot(
                new PromisedConsortCombatConfigSnapshot.General(
                        number("general.base_health"),
                        number("general.attack_damage"),
                        number("general.movement_speed"),
                        number("general.follow_range"),
                        number("general.knockback_resistance"),
                        number("general.phase_two_health_ratio"),
                        number("general.meteor_health_ratio"),
                        integer("general.max_active_players"),
                        number("general.health_per_extra_player")
                ),
                new PromisedConsortCombatConfigSnapshot.Encounter(
                        string("encounter.wake_source_policy"),
                        string("encounter.dormant_damage_policy"),
                        bool("encounter.join_on_player_hit"),
                        bool("encounter.join_on_boss_hit"),
                        string("encounter.player_first_hit_mode"),
                        string("encounter.boss_first_hit_mode"),
                        string("encounter.boss_hit_at_full_roster"),
                        string("encounter.overflow_player_policy"),
                        string("encounter.roster_slot_policy"),
                        string("encounter.scaling_count_mode"),
                        bool("encounter.allow_join_during_disengage"),
                        bool("encounter.rejoin_after_death"),
                        bool("encounter.rejoin_after_disconnect"),
                        bool("encounter.rejoin_after_boundary_exit"),
                        bool("encounter.rejoin_after_dimension_change"),
                        integer("encounter.disengage_grace_ticks"),
                        string("encounter.disengage_behavior"),
                        string("encounter.cooldown_resume_policy"),
                        string("encounter.restart_policy"),
                        string("encounter.unload_policy"),
                        string("encounter.peaceful_policy"),
                        string("encounter.overlap_policy"),
                        bool("encounter.persist_dormant")
                ),
                new PromisedConsortCombatConfigSnapshot.DamageRouting(
                        string("damage_routing.ordinary_physical"),
                        string("damage_routing.pierce"),
                        string("damage_routing.bleed_trigger"),
                        string("damage_routing.magic"),
                        string("damage_routing.holy"),
                        string("damage_routing.frost_trigger"),
                        bool("damage_routing.physical_uses_armor"),
                        bool("damage_routing.magic_bypasses_armor")
                ),
                new PromisedConsortCombatConfigSnapshot.IncomingDamage(
                        string("incoming_damage.source_policy"),
                    bool("incoming_damage.forced_death_bypasses_policy")
                ),
                new PromisedConsortCombatConfigSnapshot.Multiplayer(
                        number("multiplayer.damage_multiplier_per_extra_player"),
                        integer("multiplayer.retarget_interval_ticks"),
                        integer("multiplayer.same_target_penalty_after_ticks"),
                        number("multiplayer.same_target_score_multiplier")
                ),
                new PromisedConsortCombatConfigSnapshot.Arena(
                        number("arena.logical_radius"),
                        offset("arena.intro_offset", List.of(0.0, 14.0, 18.0)),
                        offset("arena.phase_return_offset", List.of(0.0, 0.0, -30.0)),
                        bool("arena.allow_block_breaking"),
                        string("arena.breakable_block_tag"),
                        string("arena.protected_block_tag"),
                        number("arena.block_break_radius"),
                        integer("arena.max_blocks_broken_per_tick"),
                        bool("arena.allow_wall_phasing"),
                        integer("arena.wall_phase_max_ticks"),
                        string("arena.wall_phase_failure")
                ),
                new PromisedConsortCombatConfigSnapshot.Targeting(
                        number("targeting.distance_weight"),
                        number("targeting.recent_damage_weight"),
                        number("targeting.item_use_weight"),
                        integer("targeting.recent_damage_window_ticks"),
                        bool("targeting.target_unregistered_players"),
                        bool("targeting.target_creative_players"),
                        bool("targeting.creative_players_can_join"),
                        string("targeting.primary_target_policy"),
                        string("targeting.attack_target_policy")
                ),
                new PromisedConsortCombatConfigSnapshot.Presentation(
                        string("presentation.boss_bar_audience"),
                        string("presentation.stagger_hud_audience")
                ),
                new PromisedConsortCombatConfigSnapshot.Stagger(
                        number("stagger.damage_conversion_ratio"),
                        number("stagger.capacity_health_ratio"),
                        distanceBands(),
                        integer("stagger.source_dedupe_ticks"),
                        integer("stagger.decay_delay_ticks"),
                        number("stagger.decay_per_tick"),
                        integer("stagger.stun_ticks"),
                        integer("stagger.post_stun_immunity_ticks"),
                        bool("stagger.reset_on_phase_change"),
                        string("stagger.generated_hazards_on_stun"),
                        string("stagger.pending_hazards_on_stun")
                ),
                new PromisedConsortCombatConfigSnapshot.InstantGuard(
                        bool("instant_guard.enabled"),
                        integer("instant_guard.start_tick"),
                        integer("instant_guard.end_tick"),
                        integer("instant_guard.rearm_ticks"),
                        number("instant_guard.blocked_damage_multiplier"),
                        number("instant_guard.shield_durability_multiplier"),
                        integer("instant_guard.default_cue_lead_ticks"),
                        integer("instant_guard.cue_pulse_count"),
                        string("instant_guard.red_cue_color"),
                        string("instant_guard.cue_sound"),
                        string("instant_guard.eligible_item_tag"),
                        number("instant_guard.cue_volume"),
                        number("instant_guard.cue_pitch"),
                        integer("instant_guard.cue_cooldown_ticks")
                ),
                new PromisedConsortCombatConfigSnapshot.PhaseResistances(
                        resistance("resistance.phase_one"),
                        resistance("resistance.phase_two")
                ),
                new PromisedConsortCombatConfigSnapshot.PhaseSourceMultipliers(
                        sourceMultipliers("source_multiplier.phase_one"),
                        sourceMultipliers("source_multiplier.phase_two")
                ),
                new PromisedConsortCombatConfigSnapshot.Status(
                        number("status.poison_damage_multiplier"),
                        number("status.wither_damage_multiplier"),
                    number("status.sleep_damage_multiplier")
                ),
                new PromisedConsortCombatConfigSnapshot.Selector(
                        integer("selector.avoid_last_action_count"),
                        number("selector.item_use_punish_min_range"),
                        number("selector.item_use_punish_max_range"),
                        number("selector.item_use_weight_multiplier"),
                        number("selector.ranged_weight_multiplier"),
                        number("selector.crowd_weight_multiplier"),
                        number("selector.miss_recovery_weight_multiplier"),
                        number("selector.blocked_branch_chance"),
                        string("selector.blocked_branch_mode"),
                        integer("selector.guard_chain_threshold"),
                        string("selector.guard_chain_scope"),
                        integer("selector.guard_chain_recovery_ticks")
                ),
                new PromisedConsortCombatConfigSnapshot.PhaseTransition(
                        integer("phase_transition.duration_ticks"),
                        integer("phase_transition.forced_recovery_ticks"),
                        bool("phase_transition.damage_gate"),
                        string("phase_transition.damage_gate_mode"),
                        bool("phase_transition.clear_owned_hazards"),
                        integer("phase_transition.return_impact_tick"),
                        formula("phase_transition.return_physical_damage"),
                        formula("phase_transition.return_holy_damage")
                ),
                new PromisedConsortCombatConfigSnapshot.Meteor(
                        bool("meteor.damage_gate"),
                        string("meteor.damage_gate_mode"),
                        string("meteor.pending_damage_policy"),
                        integer("meteor.invulnerable_start_tick"),
                        integer("meteor.invulnerable_end_tick"),
                        bool("meteor.accepts_stagger"),
                        bool("meteor.clear_owned_hazards"),
                        string("meteor.repeat_mode")
                ),
                new PromisedConsortCombatConfigSnapshot.LightEcho(
                        bool("light_echo.enabled"),
                        integer("light_echo.delay_ticks"),
                        integer("light_echo.telegraph_ticks"),
                        integer("light_echo.active_ticks"),
                        number("light_echo.width"),
                        number("light_echo.height"),
                        integer("light_echo.max_logical_columns"),
                        formula("light_echo.damage")
                ),
                new PromisedConsortCombatConfigSnapshot.Visuals(
                        bool("visuals.placeholder_particles_enabled"),
                        string("visuals.placeholder_particle_quality"),
                        string("visuals.gravity_projectile_block"),
                        bool("visuals.visual_clones_enabled"),
                        string("visuals.clone_render_mode")
                ),
                new PromisedConsortCombatConfigSnapshot.Performance(
                        integer("performance.max_logical_projectiles"),
                        integer("performance.max_logical_light_columns"),
                        integer("performance.max_visual_clones"),
                        integer("performance.normal_particles_per_tick")
                ),
                new PromisedConsortCombatConfigSnapshot.Dialogue(
                        bool("dialogue.enabled"),
                        integer("dialogue.max_queued_lines"),
                        integer("dialogue.player_defeat_delay_ticks"),
                        integer("dialogue.subtitle_duration_ticks"),
                        integer("dialogue.transition_call_tick"),
                        integer("dialogue.phase_two_vow_tick"),
                        integer("dialogue.defeated_tick"),
                        string("dialogue.audience")
                ),
                new PromisedConsortCombatConfigSnapshot.NonverbalAudio(
                        bool("nonverbal_audio.enabled"),
                        number("nonverbal_audio.volume"),
                        number("nonverbal_audio.pitch"),
                        integer("nonverbal_audio.intro_roar_tick"),
                        integer("nonverbal_audio.victory_roar_delay_ticks"),
                        integer("nonverbal_audio.hurt_cooldown_ticks")
                ),
                new PromisedConsortCombatConfigSnapshot.Rewards(
                        integer("rewards.remembrance_count"),
                        integer("rewards.gate_fragment_min"),
                        integer("rewards.gate_fragment_max"),
                        bool("rewards.affected_by_looting"),
                        integer("rewards.experience")
                )
        );
    }

    public PromisedConsortSkillConfigSnapshot skillSnapshot() {
        Map<PromisedConsortActionId, PromisedConsortSkillConfigSnapshot.Skill> snapshot =
                new EnumMap<>(PromisedConsortActionId.class);
        skills.forEach((id, values) -> snapshot.put(id, values.snapshot()));
        return new PromisedConsortSkillConfigSnapshot(snapshot);
    }

    private void defineGeneral(Builder builder) {
        builder.push("general");
        putNumber("general.base_health", builder.number("base_health", 1600.0, 1.0, 1_000_000.0));
        putNumber("general.attack_damage", builder.number("attack_damage", 24.0, 0.0, 2048.0));
        putNumber("general.movement_speed", builder.number("movement_speed", 0.30, 0.0, 4.0));
        putNumber("general.follow_range", builder.number("follow_range", 96.0, 1.0, 2048.0));
        putNumber("general.knockback_resistance", builder.number("knockback_resistance", 1.0, 0.0, 1.0));
        putNumber("general.phase_two_health_ratio", builder.number("phase_two_health_ratio", 0.65, 0.01, 1.0));
        putNumber("general.meteor_health_ratio", builder.number("meteor_health_ratio", 0.25, 0.01, 1.0));
        putInteger("general.max_active_players", builder.integer("max_active_players", 4, 1, 16));
        putNumber("general.health_per_extra_player", builder.number("health_per_extra_player", 0.55, 0.0, 10.0));
        builder.pop();
    }

    private void defineEncounter(Builder builder) {
        builder.push("encounter");
        putString("encounter.wake_source_policy", builder.choice("wake_source_policy", "arena_player_and_owned", "arena_player_and_owned", "player_only"));
        putString("encounter.dormant_damage_policy", builder.choice("dormant_damage_policy", "immune", "immune", "normal"));
        putBoolean("encounter.join_on_player_hit", builder.bool("join_on_player_hit", true));
        putBoolean("encounter.join_on_boss_hit", builder.bool("join_on_boss_hit", true));
        putString("encounter.player_first_hit_mode", builder.choice("player_first_hit_mode", "register_only", "register_only", "register_then_damage", "damage_then_register"));
        putString("encounter.boss_first_hit_mode", builder.choice("boss_first_hit_mode", "register_then_damage", "register_only", "register_then_damage", "damage_then_register"));
        putString("encounter.boss_hit_at_full_roster", builder.choice("boss_hit_at_full_roster", "damage_without_registration", "damage_without_registration", "ignore"));
        putString("encounter.overflow_player_policy", builder.choice("overflow_player_policy", "reject_damage", "allow_without_scaling", "reject_damage", "ignore"));
        putString("encounter.roster_slot_policy", builder.choice("roster_slot_policy", "never_reopen", "never_reopen", "reopen_on_exit"));
        putString("encounter.scaling_count_mode", builder.choice("scaling_count_mode", "cumulative_unique", "cumulative_unique", "current_active", "high_water_mark"));
        putBoolean("encounter.allow_join_during_disengage", builder.bool("allow_join_during_disengage", false));
        putBoolean("encounter.rejoin_after_death", builder.bool("rejoin_after_death", false));
        putBoolean("encounter.rejoin_after_disconnect", builder.bool("rejoin_after_disconnect", false));
        putBoolean("encounter.rejoin_after_boundary_exit", builder.bool("rejoin_after_boundary_exit", false));
        putBoolean("encounter.rejoin_after_dimension_change", builder.bool("rejoin_after_dimension_change", true));
        putInteger("encounter.disengage_grace_ticks", builder.integer("disengage_grace_ticks", 100, 0, NetworkLimits.MAX_TICKS));
        putString("encounter.disengage_behavior", builder.choice("disengage_behavior", "finish_action_then_freeze", "cancel_and_freeze", "finish_action_then_freeze", "continue"));
        putString("encounter.cooldown_resume_policy", builder.choice("cooldown_resume_policy", "clear", "freeze", "elapse", "clear"));
        putString("encounter.restart_policy", builder.choice("restart_policy", "resume", "resume", "reset_dormant", "remove"));
        putString("encounter.unload_policy", builder.choice("unload_policy", "reset_dormant", "resume", "reset_dormant", "force_load"));
        putString("encounter.peaceful_policy", builder.choice("peaceful_policy", "allow_combat", "allow_combat", "dormant", "remove"));
        putString("encounter.overlap_policy", builder.choice("overlap_policy", "allow", "allow", "reject_new", "replace_old"));
        putBoolean("encounter.persist_dormant", builder.bool("persist_dormant", true));
        builder.pop();
    }

    private void defineDamage(Builder builder) {
        builder.push("damage_routing");
        for (String key : List.of("ordinary_physical", "pierce", "bleed_trigger")) {
            putString("damage_routing." + key, builder.choice(key, "physical", "physical", "magic"));
        }
        for (String key : List.of("magic", "holy", "frost_trigger")) {
            putString("damage_routing." + key, builder.choice(key, "magic", "physical", "magic"));
        }
        putBoolean("damage_routing.physical_uses_armor", builder.bool("physical_uses_armor", true));
        putBoolean("damage_routing.magic_bypasses_armor", builder.bool("magic_bypasses_armor", true));
        builder.pop();

        builder.push("incoming_damage");
        putString("incoming_damage.source_policy", builder.choice("source_policy", "arena_player_and_owned", "arena_player_and_owned", "participants_only", "all_non_immune"));
        putBoolean("incoming_damage.forced_death_bypasses_policy", builder.bool("forced_death_bypasses_policy", true));
        builder.pop();

        builder.push("resistance");
        defineResistance(builder, "phase_one", 0.80, 0.80, 0.80, 0.80);
        defineResistance(builder, "phase_two", 0.80, 0.80, 0.80, 0.80);
        builder.pop();

        builder.push("source_multiplier");
        defineSourceMultipliers(builder, "phase_one", 1.00, 1.25, 0.50, 1.00, 1.25, 0.50);
        defineSourceMultipliers(builder, "phase_two", 1.00, 1.25, 0.50, 1.00, 0.75, 0.50);
        builder.pop();

        builder.push("status");
        putNumber("status.poison_damage_multiplier", builder.number("poison_damage_multiplier", 0.35, 0.0, 100.0));
        putNumber("status.wither_damage_multiplier", builder.number("wither_damage_multiplier", 0.35, 0.0, 100.0));
        putNumber("status.sleep_damage_multiplier", builder.number("sleep_damage_multiplier", 0.0, 0.0, 100.0));
        builder.pop();
    }

    private void defineArenaAndTargeting(Builder builder) {
        builder.push("multiplayer");
        putNumber("multiplayer.damage_multiplier_per_extra_player", builder.number("damage_multiplier_per_extra_player", 0.0, 0.0, 10.0));
        putInteger("multiplayer.retarget_interval_ticks", builder.integer("retarget_interval_ticks", 20, 1, NetworkLimits.MAX_TICKS));
        putInteger("multiplayer.same_target_penalty_after_ticks", builder.integer("same_target_penalty_after_ticks", 200, 0, NetworkLimits.MAX_TICKS));
        putNumber("multiplayer.same_target_score_multiplier", builder.number("same_target_score_multiplier", 0.75, 0.0, 1.0));
        builder.pop();

        builder.push("arena");
        putNumber("arena.logical_radius", builder.number("logical_radius", 40.0, 1.0, 2048.0));
        putList("arena.intro_offset", builder.numberList("intro_offset", List.of(0.0, 14.0, 18.0)));
        putList("arena.phase_return_offset", builder.numberList("phase_return_offset", List.of(0.0, 0.0, -30.0)));
        putBoolean("arena.allow_block_breaking", builder.bool("allow_block_breaking", false));
        putString("arena.breakable_block_tag", builder.string("breakable_block_tag", "elder_bosses:boss_breakable"));
        putString("arena.protected_block_tag", builder.string("protected_block_tag", "elder_bosses:arena_protected"));
        putNumber("arena.block_break_radius", builder.number("block_break_radius", 2.5, 0.0, 64.0));
        putInteger("arena.max_blocks_broken_per_tick", builder.integer("max_blocks_broken_per_tick", 40, 0, 4096));
        putBoolean("arena.allow_wall_phasing", builder.bool("allow_wall_phasing", true));
        putInteger("arena.wall_phase_max_ticks", builder.integer("wall_phase_max_ticks", 24, 0, NetworkLimits.MAX_TICKS));
        putString("arena.wall_phase_failure", builder.choice("wall_phase_failure", "nearest_legal_path_then_center", "nearest_legal_path_then_center", "center", "cancel"));
        builder.pop();

        builder.push("targeting");
        putNumber("targeting.distance_weight", builder.number("distance_weight", 0.50, 0.0, 100.0));
        putNumber("targeting.recent_damage_weight", builder.number("recent_damage_weight", 0.35, 0.0, 100.0));
        putNumber("targeting.item_use_weight", builder.number("item_use_weight", 0.15, 0.0, 100.0));
        putInteger("targeting.recent_damage_window_ticks", builder.integer("recent_damage_window_ticks", 160, 1, NetworkLimits.MAX_TICKS));
        putBoolean("targeting.target_unregistered_players", builder.bool("target_unregistered_players", true));
        putBoolean("targeting.target_creative_players", builder.bool("target_creative_players", false));
        putBoolean("targeting.creative_players_can_join", builder.bool("creative_players_can_join", false));
        putString("targeting.primary_target_policy", builder.choice("primary_target_policy", "players_only", "players_only", "living_when_no_players", "all_living"));
        putString("targeting.attack_target_policy", builder.choice("attack_target_policy", "all_living", "players_only", "players_and_owned", "all_living"));
        builder.pop();

        builder.push("presentation");
        putString("presentation.boss_bar_audience", builder.choice("boss_bar_audience", "follow_range", "arena", "participants", "follow_range"));
        putString("presentation.stagger_hud_audience", builder.choice("stagger_hud_audience", "boss_bar", "boss_bar", "participants", "tracking"));
        builder.pop();
    }

    private void defineCombatSystems(Builder builder) {
        builder.push("stagger");
        putNumber("stagger.damage_conversion_ratio", builder.number("damage_conversion_ratio", 0.75, 0.0, 1.0));
        putNumber("stagger.capacity_health_ratio", builder.number("capacity_health_ratio", 0.10, 0.001, 1.0));
        putList("stagger.distance_bands", builder.distanceBandList("distance_bands"));
        putInteger("stagger.source_dedupe_ticks", builder.integer("source_dedupe_ticks", 5, 0, NetworkLimits.MAX_TICKS));
        putInteger("stagger.decay_delay_ticks", builder.integer("decay_delay_ticks", 120, 0, NetworkLimits.MAX_TICKS));
        putNumber("stagger.decay_per_tick", builder.number("decay_per_tick", 2.0, 0.0, 1_000_000.0));
        putInteger("stagger.stun_ticks", builder.integer("stun_ticks", 80, 1, NetworkLimits.MAX_TICKS));
        putInteger("stagger.post_stun_immunity_ticks", builder.integer("post_stun_immunity_ticks", 100, 1, NetworkLimits.MAX_TICKS));
        putBoolean("stagger.reset_on_phase_change", builder.bool("reset_on_phase_change", false));
        putString("stagger.generated_hazards_on_stun", builder.choice("generated_hazards_on_stun", "persist", "persist", "clear"));
        putString("stagger.pending_hazards_on_stun", builder.choice("pending_hazards_on_stun", "cancel", "cancel", "persist"));
        builder.pop();

        builder.push("instant_guard");
        putBoolean("instant_guard.enabled", builder.bool("enabled", true));
        putInteger("instant_guard.start_tick", builder.integer("start_tick", 3, 0, NetworkLimits.MAX_TICKS));
        putInteger("instant_guard.end_tick", builder.integer("end_tick", 6, 0, NetworkLimits.MAX_TICKS));
        putInteger("instant_guard.rearm_ticks", builder.integer("rearm_ticks", 4, 0, NetworkLimits.MAX_TICKS));
        putNumber("instant_guard.blocked_damage_multiplier", builder.number("blocked_damage_multiplier", 0.0, 0.0, 1.0));
        putNumber("instant_guard.shield_durability_multiplier", builder.number("shield_durability_multiplier", 0.50, 0.0, 100.0));
        putInteger("instant_guard.default_cue_lead_ticks", builder.integer("default_cue_lead_ticks", 6, 0, NetworkLimits.MAX_TICKS));
        putInteger("instant_guard.cue_pulse_count", builder.integer("cue_pulse_count", 3, 1, 64));
        putString("instant_guard.red_cue_color", builder.string("red_cue_color", "#FF2020"));
        putString("instant_guard.cue_sound", builder.string("cue_sound", "elder_bosses:promised_consort.instant_guard_cue"));
        putString("instant_guard.eligible_item_tag", builder.string("eligible_item_tag", "elder_bosses:instant_guard_items"));
        putNumber("instant_guard.cue_volume", builder.number("cue_volume", 1.0, 0.0, 16.0));
        putNumber("instant_guard.cue_pitch", builder.number("cue_pitch", 1.0, 0.0, 16.0));
        putInteger("instant_guard.cue_cooldown_ticks", builder.integer("cue_cooldown_ticks", 0, 0, NetworkLimits.MAX_TICKS));
        builder.pop();

        builder.push("selector");
        putInteger("selector.avoid_last_action_count", builder.integer("avoid_last_action_count", 2, 0, 16));
        putNumber("selector.item_use_punish_min_range", builder.number("item_use_punish_min_range", 4.0, 0.0, 2048.0));
        putNumber("selector.item_use_punish_max_range", builder.number("item_use_punish_max_range", 12.0, 0.0, 2048.0));
        putNumber("selector.item_use_weight_multiplier", builder.number("item_use_weight_multiplier", 1.80, 0.0, 100.0));
        putNumber("selector.ranged_weight_multiplier", builder.number("ranged_weight_multiplier", 2.00, 0.0, 100.0));
        putNumber("selector.crowd_weight_multiplier", builder.number("crowd_weight_multiplier", 1.70, 0.0, 100.0));
        putNumber("selector.miss_recovery_weight_multiplier", builder.number("miss_recovery_weight_multiplier", 1.20, 0.0, 100.0));
        putNumber("selector.blocked_branch_chance", builder.number("blocked_branch_chance", 0.70, 0.0, 1.0));
        putString("selector.blocked_branch_mode", builder.choice("blocked_branch_mode", "stomp", "stomp", "continue_combo", "weighted"));
        putInteger("selector.guard_chain_threshold", builder.integer("guard_chain_threshold", 3, 1, 64));
        putString("selector.guard_chain_scope", builder.choice("guard_chain_scope", "current_action", "current_action", "same_target", "encounter"));
        putInteger("selector.guard_chain_recovery_ticks", builder.integer("guard_chain_recovery_ticks", 20, 0, NetworkLimits.MAX_TICKS));
        builder.pop();

        builder.push("phase_transition");
        putInteger("phase_transition.duration_ticks", builder.integer("duration_ticks", 140, 1, NetworkLimits.MAX_TICKS));
        putInteger("phase_transition.forced_recovery_ticks", builder.integer("forced_recovery_ticks", 10, 0, NetworkLimits.MAX_TICKS));
        putBoolean("phase_transition.damage_gate", builder.bool("damage_gate", true));
        putString("phase_transition.damage_gate_mode", builder.choice("damage_gate_mode", "truncate", "truncate", "full_damage"));
        putBoolean("phase_transition.clear_owned_hazards", builder.bool("clear_owned_hazards", true));
        putInteger("phase_transition.return_impact_tick", builder.integer("return_impact_tick", 127, 0, NetworkLimits.MAX_TICKS));
        putFormula("phase_transition.return_physical_damage", builder.formula("return_physical_damage", 4.0, 0.70));
        putFormula("phase_transition.return_holy_damage", builder.formula("return_holy_damage", 2.0, 0.35));
        builder.pop();

        builder.push("meteor");
        putBoolean("meteor.damage_gate", builder.bool("damage_gate", true));
        putString("meteor.damage_gate_mode", builder.choice("damage_gate_mode", "truncate", "truncate", "full_damage", "leave_one_health"));
        putString("meteor.pending_damage_policy", builder.choice("pending_damage_policy", "invulnerable", "invulnerable", "leave_one_health", "normal"));
        putInteger("meteor.invulnerable_start_tick", builder.integer("invulnerable_start_tick", 0, 0, NetworkLimits.MAX_TICKS));
        putInteger("meteor.invulnerable_end_tick", builder.integer("invulnerable_end_tick", 120, 0, NetworkLimits.MAX_TICKS));
        putBoolean("meteor.accepts_stagger", builder.bool("accepts_stagger", false));
        putBoolean("meteor.clear_owned_hazards", builder.bool("clear_owned_hazards", true));
        putString("meteor.repeat_mode", builder.choice("repeat_mode", "cooldown_forced", "cooldown_forced", "weighted", "once"));
        builder.pop();

        builder.push("light_echo");
        putBoolean("light_echo.enabled", builder.bool("enabled", true));
        putInteger("light_echo.delay_ticks", builder.integer("delay_ticks", 8, 0, NetworkLimits.MAX_TICKS));
        putInteger("light_echo.telegraph_ticks", builder.integer("telegraph_ticks", 8, 1, NetworkLimits.MAX_TICKS));
        putInteger("light_echo.active_ticks", builder.integer("active_ticks", 3, 1, NetworkLimits.MAX_TICKS));
        putNumber("light_echo.width", builder.number("width", 0.80, 0.01, 64.0));
        putNumber("light_echo.height", builder.number("height", 8.0, 0.01, 256.0));
        putInteger("light_echo.max_logical_columns", builder.integer("max_logical_columns", 24, 0, 1024));
        putFormula("light_echo.damage", builder.formula("damage", 1.0, 0.25));
        builder.pop();
    }

    private void definePresentation(Builder builder) {
        builder.push("visuals");
        putBoolean("visuals.placeholder_particles_enabled", builder.bool("placeholder_particles_enabled", true));
        putString("visuals.placeholder_particle_quality", builder.choice("placeholder_particle_quality", "full", "minimal", "reduced", "full"));
        putString("visuals.gravity_projectile_block", builder.string("gravity_projectile_block", "minecraft:crying_obsidian"));
        putBoolean("visuals.visual_clones_enabled", builder.bool("visual_clones_enabled", true));
        putString("visuals.clone_render_mode", builder.choice("clone_render_mode", "empty_geo_entity", "empty_geo_entity", "paths_only"));
        builder.pop();

        builder.push("performance");
        putInteger("performance.max_logical_projectiles", builder.integer("max_logical_projectiles", 16, 0, 1024));
        putInteger("performance.max_logical_light_columns", builder.integer("max_logical_light_columns", 24, 0, 1024));
        putInteger("performance.max_visual_clones", builder.integer("max_visual_clones", 4, 0, 64));
        putInteger("performance.normal_particles_per_tick", builder.integer("normal_particles_per_tick", 40, 0, 4096));
        builder.pop();

        builder.push("dialogue");
        putBoolean("dialogue.enabled", builder.bool("enabled", true));
        putInteger("dialogue.max_queued_lines", builder.integer("max_queued_lines", 1, 0, 64));
        putInteger("dialogue.player_defeat_delay_ticks", builder.integer("player_defeat_delay_ticks", 12, 0, NetworkLimits.MAX_TICKS));
        putInteger("dialogue.subtitle_duration_ticks", builder.integer("subtitle_duration_ticks", 70, 1, NetworkLimits.MAX_TICKS));
        putInteger("dialogue.transition_call_tick", builder.integer("transition_call_tick", 58, 0, NetworkLimits.MAX_TICKS));
        putInteger("dialogue.phase_two_vow_tick", builder.integer("phase_two_vow_tick", 92, 0, NetworkLimits.MAX_TICKS));
        putInteger("dialogue.defeated_tick", builder.integer("defeated_tick", 24, 0, NetworkLimits.MAX_TICKS));
        putString("dialogue.audience", builder.choice("audience", "participants", "participants", "arena", "follow_range"));
        builder.pop();

        builder.push("nonverbal_audio");
        putBoolean("nonverbal_audio.enabled", builder.bool("enabled", true));
        putNumber("nonverbal_audio.volume", builder.number("volume", 1.0, 0.0, 16.0));
        putNumber("nonverbal_audio.pitch", builder.number("pitch", 1.0, 0.0, 16.0));
        putInteger("nonverbal_audio.intro_roar_tick", builder.integer("intro_roar_tick", 18, 0, NetworkLimits.MAX_TICKS));
        putInteger("nonverbal_audio.victory_roar_delay_ticks", builder.integer("victory_roar_delay_ticks", 12, 0, NetworkLimits.MAX_TICKS));
        putInteger("nonverbal_audio.hurt_cooldown_ticks", builder.integer("hurt_cooldown_ticks", 20, 0, NetworkLimits.MAX_TICKS));
        builder.pop();

        builder.push("rewards");
        putInteger("rewards.remembrance_count", builder.integer("remembrance_count", 1, 0, 64));
        putInteger("rewards.gate_fragment_min", builder.integer("gate_fragment_min", 4, 0, 64));
        putInteger("rewards.gate_fragment_max", builder.integer("gate_fragment_max", 8, 0, 64));
        putBoolean("rewards.affected_by_looting", builder.bool("affected_by_looting", false));
        putInteger("rewards.experience", builder.integer("experience", 500, 0, 1_000_000));
        builder.pop();
    }

    private void defineSkills(Builder builder) {
        builder.push("skills");
        addSkill(builder, PromisedConsortActionId.GRAVITY_DIVE, 1.0, 160, true)
                .number("range", 4.5).ticks("windup_ticks", 24).ticks("active_ticks", 6)
                .ticks("recovery_ticks", 24).damage("sword_damage", 4.0, 0.80)
                .damage("impact_damage", 3.0, 0.55).finish();
        addSkill(builder, PromisedConsortActionId.L_COMBO_CROSS, 1.0, 50, false)
                .number("range", 3.8).integerList("windup_ticks", 9, 8, 14)
                .integerList("active_ticks", 3, 3, 4).integerList("recovery_ticks", 7, 8, 22)
                .damageList("damage", new double[][]{{2.0, 0.45}, {2.0, 0.45}, {4.0, 0.70}}).finish();
        addSkill(builder, PromisedConsortActionId.L_COMBO_BLOODFLAME, 0.8, 100, false)
                .number("thrust_range", 5.0).number("sweep_range", 4.0)
                .integerList("windup_ticks", 13, 12).integerList("active_ticks", 3, 4)
                .integerList("recovery_ticks", 8, 24).damage("thrust_damage", 3.0, 0.60)
                .damage("sweep_damage", 2.0, 0.50).damage("burst_damage", 2.0, 0.35)
                .ticks("fissure_lifetime_ticks", 24).ticks("burst_tick", 16).finish();
        addSkill(builder, PromisedConsortActionId.R_COMBO_CROSS, 1.0, 55, false)
                .number("range", 3.8).integerList("windup_ticks", 9, 15)
                .integerList("active_ticks", 3, 4).integerList("recovery_ticks", 8, 22)
                .damageList("damage", new double[][]{{2.0, 0.45}, {4.0, 0.75}}).finish();
        addSkill(builder, PromisedConsortActionId.R_COMBO_LEFT_TWIN, 1.0, 50, false)
                .number("range", 3.6).integerList("windup_ticks", 9, 7, 8)
                .integerList("active_ticks", 3, 3, 3).integerList("recovery_ticks", 7, 7, 20)
                .damageList("damage", new double[][]{{2.0, 0.45}, {2.0, 0.40}, {2.0, 0.45}}).finish();
        addSkill(builder, PromisedConsortActionId.R_COMBO_TEMPEST, 0.7, 110, false)
                .number("range", 4.2).integerList("windup_ticks", 10, 10, 10, 18)
                .integerList("active_ticks", 3, 3, 3, 8).integerList("recovery_ticks", 6, 6, 6, 26)
                .damage("opening_damage", 2.0, 0.45).damage("tempest_damage", 3.0, 0.55)
                .integer("tempest_hits", 2).finish();
        addSkill(builder, PromisedConsortActionId.R_COMBO_EARTHHEAVE, 0.7, 140, false)
                .number("range", 7.0).integerList("windup_ticks", 10, 10, 10, 20, 10)
                .integerList("active_ticks", 3, 3, 3, 5, 6).integerList("recovery_ticks", 6, 6, 6, 12, 30)
                .damage("opening_damage", 2.0, 0.45).damage("slam_damage", 5.0, 0.80)
                .damage("fissure_damage", 4.0, 0.65).finish();
        addSkill(builder, PromisedConsortActionId.LION_CLAW, 0.9, 100, true)
                .number("range", 3.5).ticks("windup_ticks", 22).ticks("active_ticks", 5)
                .ticks("recovery_ticks", 28).damage("damage", 5.0, 0.85)
                .number("double_followup_chance", 0.35).ticks("double_windup_ticks", 16)
                .ticks("double_active_ticks", 5).ticks("double_recovery_ticks", 34)
                .damage("double_damage", 5.0, 0.90).finish();
        addSkill(builder, PromisedConsortActionId.STARCALLER_CRY, 0.7, 180, true)
                .number("pull_radius", 12.0).number("impact_radius", 6.0)
                .ticks("windup_ticks", 30).ticks("active_ticks", 10).ticks("recovery_ticks", 34)
                .damage("pull_damage", 0.0, 0.0).damage("impact_damage", 4.0, 0.70)
                .damage("spike_damage", 2.0, 0.40).number("max_pull_per_tick", 0.35)
            .number("jump_avoid_height", 0.60).damage("clone_damage", 1.0, 0.20)
            .finish();
        addSkill(builder, PromisedConsortActionId.GRAVITY_METEOR, 0.7, 220, true)
                .ticks("windup_ticks", 32).ticks("active_ticks", 50).ticks("recovery_ticks", 36)
                .integer("projectile_count", 8).integer("max_hits_per_target", 3)
                .ticks("projectile_lifetime_ticks", 60).number("max_turn_degrees_per_tick", 4.0)
                .number("projectile_health", 6.0).damage("damage", 2.0, 0.35)
                .number("clone_radius", 3.5).damage("clone_damage", 1.0, 0.20).finish();
        addSkill(builder, PromisedConsortActionId.STOMP, 1.0, 70, false)
                .number("forward_range", 5.0).number("width", 4.0)
                .ticks("windup_ticks", 14).ticks("active_ticks", 5).ticks("recovery_ticks", 24)
                .damage("damage", 3.0, 0.55).finish();
        addSkill(builder, PromisedConsortActionId.CROSS_SLASH, 0.9, 90, false)
                .number("sword_range", 4.0).number("debris_range", 7.0)
                .ticks("windup_ticks", 18).ticks("active_ticks", 5).ticks("recovery_ticks", 26)
                .damage("sword_damage", 4.0, 0.75).damage("debris_damage", 2.0, 0.35).finish();
        addSkill(builder, PromisedConsortActionId.SPIRAL_ASSAULT, 0.8, 150, true)
                .number("range", 12.0).number("width", 3.0)
                .ticks("windup_ticks", 26).ticks("active_ticks", 8).ticks("recovery_ticks", 30)
                .damage("spin_damage", 3.0, 0.60).damage("slam_damage", 5.0, 0.85).finish();
        addSkill(builder, PromisedConsortActionId.LIGHT_OF_MIQUELLA, 0.6, 300, true)
                .number("radius", 8.0).ticks("windup_ticks", 44).ticks("active_ticks", 10)
                .ticks("recovery_ticks", 42).damage("main_damage", 5.0, 0.90)
                .damage("afterglow_damage", 1.0, 0.20).integer("afterglow_count", 8).finish();
        addSkill(builder, PromisedConsortActionId.RING_OF_LIGHT, 0.7, 140, false)
                .number("inner_radius", 3.0).number("outer_radius", 11.0)
                .ticks("windup_ticks", 24).ticks("active_ticks", 5).ticks("recovery_ticks", 30)
                .damage("damage", 3.0, 0.55).finish();
        addSkill(builder, PromisedConsortActionId.LIGHTSPEED_SLASH, 0.8, 160, true)
                .ticks("windup_ticks", 28).ticks("active_ticks", 24).ticks("recovery_ticks", 34)
                .integer("clone_count", 3).damage("clone_damage", 1.0, 0.20)
                .damage("body_damage", 5.0, 0.80).finish();
        addSkill(builder, PromisedConsortActionId.LIGHTSPEED_DASH, 0.7, 170, true)
                .number("range", 16.0).number("width", 2.5)
                .ticks("windup_ticks", 26).ticks("active_ticks", 20).ticks("recovery_ticks", 36)
                .damage("clone_damage", 1.0, 0.20).damage("body_damage", 4.0, 0.75)
                .damage("trail_damage", 1.0, 0.20).finish();
        addSkill(builder, PromisedConsortActionId.LIGHTSPEED_SIDE_DASH, 0.8, 150, true)
                .ticks("windup_ticks", 18).ticks("active_ticks", 20).ticks("recovery_ticks", 30)
                .integer("clone_count", 3).damage("clone_damage", 1.0, 0.20)
                .damage("body_damage", 4.0, 0.70).finish();
        addSkill(builder, PromisedConsortActionId.PROMISED_CONSORT, 0.5, 260, true)
                .ticks("windup_ticks", 26).ticks("active_ticks", 54).ticks("recovery_ticks", 48)
                .damage("opening_damage", 3.0, 0.55).damage("spin_damage", 3.0, 0.50)
            .damage("finisher_damage", 6.0, 0.95).damage("holy_ring_damage", 2.0, 0.35)
            .damage("clone_damage", 1.0, 0.20).finish();
        addSkill(builder, PromisedConsortActionId.ENHANCED_EARTHHEAVE, 0.6, 170, true)
                .number("radius", 6.0).ticks("windup_ticks", 20).ticks("active_ticks", 20)
                .ticks("recovery_ticks", 38).damage("slam_damage", 5.0, 0.85)
                .damage("fissure_damage", 4.0, 0.70).damage("light_damage", 1.0, 0.25).finish();
        addSkill(builder, PromisedConsortActionId.CONSORT_METEOR, 0.0, 3600, true)
                .number("core_radius", 9.0).number("outer_radius", 13.0)
                .ticks("script_ticks", 150).ticks("prediction_sample_ticks", 10)
                .ticks("prediction_lead_ticks", 12).damage("core_damage", 8.0, 1.20)
                .damage("outer_damage", 4.0, 0.65).damage("aftershock_damage", 2.0, 0.30).finish();
        builder.pop();
    }

    private SkillValues addSkill(
            Builder builder,
            PromisedConsortActionId actionId,
            double weight,
            int cooldownTicks,
            boolean hyperArmorActive
    ) {
        SkillValues values = new SkillValues(
                builder,
                actionId.serializedName(),
                weight,
                cooldownTicks,
                hyperArmorActive
        );
        skills.put(actionId, values);
        return values;
    }

    private void defineResistance(
            Builder builder,
            String phase,
            double physical,
            double fire,
            double magic,
            double lightning
    ) {
        builder.push(phase);
        putNumber("resistance." + phase + ".physical", builder.number("physical", physical, 0.0, 100.0));
        putNumber("resistance." + phase + ".fire", builder.number("fire", fire, 0.0, 100.0));
        putNumber("resistance." + phase + ".magic", builder.number("magic", magic, 0.0, 100.0));
        putNumber("resistance." + phase + ".lightning", builder.number("lightning", lightning, 0.0, 100.0));
        builder.pop();
    }

    private void defineSourceMultipliers(
            Builder builder,
            String phase,
            double ordinaryPhysical,
            double pierce,
            double bleedTrigger,
            double magic,
            double holy,
            double frostTrigger
    ) {
        builder.push(phase);
        putNumber("source_multiplier." + phase + ".ordinary_physical", builder.number("ordinary_physical", ordinaryPhysical, 0.0, 100.0));
        putNumber("source_multiplier." + phase + ".pierce", builder.number("pierce", pierce, 0.0, 100.0));
        putNumber("source_multiplier." + phase + ".bleed_trigger", builder.number("bleed_trigger", bleedTrigger, 0.0, 100.0));
        putNumber("source_multiplier." + phase + ".magic", builder.number("magic", magic, 0.0, 100.0));
        putNumber("source_multiplier." + phase + ".holy", builder.number("holy", holy, 0.0, 100.0));
        putNumber("source_multiplier." + phase + ".frost_trigger", builder.number("frost_trigger", frostTrigger, 0.0, 100.0));
        builder.pop();
    }

    private PromisedConsortCombatConfigSnapshot.ResistanceProfile resistance(String prefix) {
        return new PromisedConsortCombatConfigSnapshot.ResistanceProfile(
                number(prefix + ".physical"),
                number(prefix + ".fire"),
                number(prefix + ".magic"),
                number(prefix + ".lightning")
        );
    }

    private PromisedConsortCombatConfigSnapshot.SourceMultiplierProfile sourceMultipliers(
            String prefix
    ) {
        return new PromisedConsortCombatConfigSnapshot.SourceMultiplierProfile(
                number(prefix + ".ordinary_physical"),
                number(prefix + ".pierce"),
                number(prefix + ".bleed_trigger"),
                number(prefix + ".magic"),
                number(prefix + ".holy"),
                number(prefix + ".frost_trigger")
        );
    }

    private List<DistanceBand> distanceBands() {
        List<?> configured = list("stagger.distance_bands");
        if (configured.size() != 4) {
            return defaultDistanceBands();
        }
        List<DistanceBand> result = new ArrayList<>(configured.size());
        double previous = -1.0;
        for (int index = 0; index < configured.size(); index++) {
            if (!(configured.get(index) instanceof UnmodifiableConfig config)) {
                return defaultDistanceBands();
            }
            Number maxDistance = config.get("max_distance");
            Number multiplier = config.get("multiplier");
            if (maxDistance == null || multiplier == null) {
                return defaultDistanceBands();
            }
            double maximum = maxDistance.doubleValue();
            double checkedMaximum = maximum == -1.0 ? Double.POSITIVE_INFINITY : maximum;
            if (checkedMaximum <= previous || index < configured.size() - 1 && !Double.isFinite(checkedMaximum)) {
                return defaultDistanceBands();
            }
            result.add(new DistanceBand(checkedMaximum, multiplier.doubleValue()));
            previous = checkedMaximum;
        }
        return List.copyOf(result);
    }

    private PromisedConsortCombatConfigSnapshot.Offset offset(
            String key,
            List<Double> fallback
    ) {
        List<?> configured = list(key);
        List<?> source = configured.size() == 3 ? configured : fallback;
        return new PromisedConsortCombatConfigSnapshot.Offset(
                ((Number) source.get(0)).doubleValue(),
                ((Number) source.get(1)).doubleValue(),
                ((Number) source.get(2)).doubleValue()
        );
    }

    private DamageFormula formula(String key) {
        UnmodifiableConfig config = formulas.get(key).get();
        Number flat = config.get("flat");
        Number attackRatio = config.get("attack_ratio");
        return new DamageFormula(flat.doubleValue(), attackRatio.doubleValue());
    }

    private boolean bool(String key) {
        return booleans.get(key).get();
    }

    private int integer(String key) {
        return integers.get(key).get();
    }

    private double number(String key) {
        return numbers.get(key).get();
    }

    private String string(String key) {
        return strings.get(key).get();
    }

    private List<?> list(String key) {
        return lists.get(key).get();
    }

    private void putBoolean(String key, Supplier<Boolean> value) {
        booleans.put(key, value);
    }

    private void putInteger(String key, Supplier<Integer> value) {
        integers.put(key, value);
    }

    private void putNumber(String key, Supplier<Double> value) {
        numbers.put(key, value);
    }

    private void putString(String key, Supplier<String> value) {
        strings.put(key, value);
    }

    private void putList(String key, Supplier<? extends List<?>> value) {
        lists.put(key, value);
    }

    private void putFormula(String key, Supplier<? extends UnmodifiableConfig> value) {
        formulas.put(key, value);
    }

    private static List<DistanceBand> defaultDistanceBands() {
        return List.of(
                new DistanceBand(4.0, 1.00),
                new DistanceBand(8.0, 0.70),
                new DistanceBand(16.0, 0.40),
                new DistanceBand(Double.POSITIVE_INFINITY, 0.20)
        );
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

    private static Config formulaConfig(double flat, double attackRatio) {
        Config config = Config.inMemory();
        config.set("flat", flat);
        config.set("attack_ratio", attackRatio);
        return config;
    }

    private static boolean isFormula(Object value) {
        if (!(value instanceof UnmodifiableConfig config)) {
            return false;
        }
        Object flat = config.get("flat");
        Object ratio = config.get("attack_ratio");
        return flat instanceof Number flatNumber
                && ratio instanceof Number ratioNumber
                && Double.isFinite(flatNumber.doubleValue())
                && flatNumber.doubleValue() >= 0.0
                && Double.isFinite(ratioNumber.doubleValue())
                && ratioNumber.doubleValue() >= 0.0;
    }

    private static boolean isDistanceBand(Object value) {
        if (!(value instanceof UnmodifiableConfig config)) {
            return false;
        }
        Object maxDistance = config.get("max_distance");
        Object multiplier = config.get("multiplier");
        return maxDistance instanceof Number maxNumber
                && multiplier instanceof Number multiplierNumber
                && Double.isFinite(maxNumber.doubleValue())
                && (maxNumber.doubleValue() == -1.0 || maxNumber.doubleValue() >= 0.0)
                && Double.isFinite(multiplierNumber.doubleValue())
                && multiplierNumber.doubleValue() >= 0.0;
    }

    private final class SkillValues {
        private final Builder builder;
        private final Supplier<Boolean> enabled;
        private final Supplier<Double> weight;
        private final Supplier<Integer> cooldownTicks;
        private final Supplier<Boolean> hyperArmorActive;
        private final Map<String, Supplier<Double>> skillNumbers = new LinkedHashMap<>();
        private final Map<String, Supplier<Integer>> skillIntegers = new LinkedHashMap<>();
        private final Map<String, Supplier<String>> skillStrings = new LinkedHashMap<>();
        private final Map<String, Supplier<? extends List<?>>> skillIntegerLists = new LinkedHashMap<>();
        private final Map<String, Supplier<? extends UnmodifiableConfig>> skillDamage = new LinkedHashMap<>();
        private final Map<String, Supplier<? extends List<?>>> skillDamageLists = new LinkedHashMap<>();
        private final Map<String, Double> defaultNumbers = new LinkedHashMap<>();
        private final Map<String, Integer> defaultIntegers = new LinkedHashMap<>();
        private final Map<String, String> defaultStrings = new LinkedHashMap<>();
        private final Map<String, List<Integer>> defaultIntegerLists = new LinkedHashMap<>();
        private final Map<String, DamageFormula> defaultDamage = new LinkedHashMap<>();
        private final Map<String, List<DamageFormula>> defaultDamageLists = new LinkedHashMap<>();
        private final boolean defaultEnabled = true;
        private final double defaultWeight;
        private final int defaultCooldown;
        private final boolean defaultHyperArmor;
        private final String name;

        private SkillValues(
                Builder builder,
                String name,
                double defaultWeight,
                int defaultCooldown,
                boolean defaultHyperArmor
        ) {
            this.builder = builder;
            this.name = name;
            this.defaultWeight = defaultWeight;
            this.defaultCooldown = defaultCooldown;
            this.defaultHyperArmor = defaultHyperArmor;
            builder.push(name);
            enabled = builder.bool("enabled", true);
            weight = builder.number("weight", defaultWeight, 0.0, 100.0);
            cooldownTicks = builder.integer("cooldown_ticks", defaultCooldown, 0, NetworkLimits.MAX_TICKS);
            hyperArmorActive = builder.bool("hyper_armor_active", defaultHyperArmor);
        }

        private SkillValues number(String name, double value) {
            skillNumbers.put(name, builder.number(name, value, 0.0, 4096.0));
            defaultNumbers.put(name, value);
            return this;
        }

        private SkillValues integer(String name, int value) {
            skillIntegers.put(name, builder.integer(name, value, 0, 4096));
            defaultIntegers.put(name, value);
            return this;
        }

        private SkillValues ticks(String name, int value) {
            skillIntegers.put(name, builder.integer(name, value, 1, NetworkLimits.MAX_TICKS));
            defaultIntegers.put(name, value);
            return this;
        }

        private SkillValues string(String name, String value) {
            skillStrings.put(name, builder.string(name, value));
            defaultStrings.put(name, value);
            return this;
        }

        private SkillValues integerList(String name, Integer... values) {
            List<Integer> defaults = List.of(values);
            skillIntegerLists.put(name, builder.integerList(name, defaults));
            defaultIntegerLists.put(name, defaults);
            return this;
        }

        private SkillValues damage(String name, double flat, double ratio) {
            skillDamage.put(name, builder.formula(name, flat, ratio));
            defaultDamage.put(name, new DamageFormula(flat, ratio));
            return this;
        }

        private SkillValues damageList(String name, double[][] values) {
            List<Config> defaults = new ArrayList<>(values.length);
            for (double[] value : values) {
                defaults.add(formulaConfig(value[0], value[1]));
            }
            skillDamageLists.put(name, builder.formulaList(name, defaults));
                defaultDamageLists.put(name, java.util.Arrays.stream(values)
                    .map(value -> new DamageFormula(value[0], value[1]))
                    .toList());
            return this;
        }

        private void finish() {
            builder.pop();
        }

        private PromisedConsortSkillConfigSnapshot.Skill snapshot() {
            Map<String, Double> resolvedNumbers = new LinkedHashMap<>();
            skillNumbers.forEach((key, value) -> resolvedNumbers.put(key, value.get()));
            Map<String, String> resolvedStrings = new LinkedHashMap<>();
            skillStrings.forEach((key, value) -> resolvedStrings.put(key, value.get()));
            Map<String, Integer> resolvedIntegers = new LinkedHashMap<>();
            skillIntegers.forEach((key, value) -> resolvedIntegers.put(key, value.get()));
            Map<String, List<Integer>> resolvedIntegerLists = new LinkedHashMap<>();
            skillIntegerLists.forEach((key, value) -> resolvedIntegerLists.put(
                    key,
                    value.get().stream().map(entry -> ((Number) entry).intValue()).toList()
            ));
            Map<String, DamageFormula> resolvedDamage = new LinkedHashMap<>();
            skillDamage.forEach((key, value) -> {
                UnmodifiableConfig config = value.get();
                resolvedDamage.put(key, new DamageFormula(
                        ((Number) config.get("flat")).doubleValue(),
                        ((Number) config.get("attack_ratio")).doubleValue()
                ));
            });
            Map<String, List<DamageFormula>> resolvedDamageLists = new LinkedHashMap<>();
            skillDamageLists.forEach((key, value) -> resolvedDamageLists.put(
                    key,
                    value.get().stream().map(entry -> {
                        UnmodifiableConfig config = (UnmodifiableConfig) entry;
                        return new DamageFormula(
                                ((Number) config.get("flat")).doubleValue(),
                                ((Number) config.get("attack_ratio")).doubleValue()
                        );
                    }).toList()
            ));
                boolean changedMultiStageTiming = defaultIntegerLists.entrySet().stream()
                    .anyMatch(entry -> isStageTiming(entry.getKey())
                        && !entry.getValue().equals(resolvedIntegerLists.get(entry.getKey())));
                if (changedMultiStageTiming) {
                LOGGER.warn(
                    "Promised Consort skill '{}' uses fixed multi-stage timing; reverting the whole skill to defaults",
                    name
                );
                return defaultSnapshot();
                }
                return new PromisedConsortSkillConfigSnapshot.Skill(
                    enabled.get(),
                    weight.get(),
                    cooldownTicks.get(),
                    hyperArmorActive.get(),
                    resolvedNumbers,
                    resolvedIntegers,
                    resolvedStrings,
                    resolvedIntegerLists,
                    resolvedDamage,
                    resolvedDamageLists
            );
        }

        private PromisedConsortSkillConfigSnapshot.Skill defaultSnapshot() {
            return new PromisedConsortSkillConfigSnapshot.Skill(
                    defaultEnabled,
                    defaultWeight,
                    defaultCooldown,
                    defaultHyperArmor,
                    defaultNumbers,
                    defaultIntegers,
                    defaultStrings,
                    defaultIntegerLists,
                    defaultDamage,
                    defaultDamageLists
            );
        }

        private static boolean isStageTiming(String key) {
            return key.equals("windup_ticks")
                    || key.equals("active_ticks")
                    || key.equals("recovery_ticks");
        }
    }

    private static final class Builder {
        //? if forge {
        private final ForgeConfigSpec.Builder delegate;

        private Builder(ForgeConfigSpec.Builder delegate) {
        //?} else {
        /*private final ModConfigSpec.Builder delegate;

        private Builder(ModConfigSpec.Builder delegate) {
        *///?}
            this.delegate = delegate;
        }

        private void push(String path) {
            delegate.push(path);
        }

        private void pop() {
            delegate.pop();
        }

        private Supplier<Boolean> bool(String key, boolean defaultValue) {
            return delegate.define(key, defaultValue);
        }

        private Supplier<Integer> integer(String key, int defaultValue, int min, int max) {
            return delegate.defineInRange(key, defaultValue, min, max);
        }

        private Supplier<Double> number(String key, double defaultValue, double min, double max) {
            return delegate.defineInRange(key, defaultValue, min, max);
        }

        private Supplier<String> string(String key, String defaultValue) {
            return delegate.define(key, defaultValue);
        }

        private Supplier<String> choice(String key, String defaultValue, String... allowed) {
            Set<String> choices = Set.of(allowed);
            return delegate.define(key, defaultValue, value -> value instanceof String text
                    && choices.contains(text));
        }

        private Supplier<? extends List<?>> numberList(String key, List<Double> defaults) {
            return delegate.defineList(key, defaults, value -> value instanceof Number number
                    && Double.isFinite(number.doubleValue()));
        }

        private Supplier<? extends List<?>> integerList(String key, List<Integer> defaults) {
            return delegate.defineList(key, defaults, value -> value instanceof Number number
                    && number.doubleValue() >= 1.0
                    && number.doubleValue() == Math.rint(number.doubleValue()));
        }

        private Supplier<? extends List<?>> distanceBandList(String key) {
            return delegate.defineList(key, defaultDistanceBandConfigs(), PromisedConsortConfigValues::isDistanceBand);
        }

        private Supplier<? extends UnmodifiableConfig> formula(
                String key,
                double flat,
                double attackRatio
        ) {
            return delegate.define(key, formulaConfig(flat, attackRatio), PromisedConsortConfigValues::isFormula);
        }

        private Supplier<? extends List<?>> formulaList(String key, List<Config> defaults) {
            return delegate.defineList(key, defaults, PromisedConsortConfigValues::isFormula);
        }
    }
}
