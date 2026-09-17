package com.tonywww.elder_bosses.boss.promisedconsort.action;

import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSkillConfigSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase;
import com.tonywww.elder_bosses.combat.action.ActionStage;
import com.tonywww.elder_bosses.combat.action.ActionTimeline;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

public final class PromisedConsortActionCatalog {
    private static final EnumSet<PromisedConsortPhase> BOTH_PHASES =
            EnumSet.allOf(PromisedConsortPhase.class);
    private static final EnumSet<PromisedConsortPhase> PHASE_TWO =
            EnumSet.of(PromisedConsortPhase.PHASE_TWO);

    private final PromisedConsortSkillConfigSnapshot skillConfig;
    private final Map<PromisedConsortActionId, PromisedConsortActionDefinition> definitions;

    public PromisedConsortActionCatalog(PromisedConsortSkillConfigSnapshot skillConfig) {
        this.skillConfig = Objects.requireNonNull(skillConfig, "skillConfig");
        EnumMap<PromisedConsortActionId, PromisedConsortActionDefinition> built =
                new EnumMap<>(PromisedConsortActionId.class);
        addConfigured(built, PromisedConsortActionId.GRAVITY_DIVE, BOTH_PHASES, "gravity_dive");
        addConfigured(built, PromisedConsortActionId.L_COMBO_CROSS, BOTH_PHASES, "basic_combo");
        addConfigured(built, PromisedConsortActionId.L_COMBO_BLOODFLAME, BOTH_PHASES, "bloodflame");
        addConfigured(built, PromisedConsortActionId.R_COMBO_CROSS, BOTH_PHASES, "basic_combo");
        addConfigured(built, PromisedConsortActionId.R_COMBO_LEFT_TWIN, BOTH_PHASES, "basic_combo");
        addConfigured(built, PromisedConsortActionId.R_COMBO_TEMPEST, BOTH_PHASES, "tempest");
        addConfigured(built, PromisedConsortActionId.R_COMBO_EARTHHEAVE, BOTH_PHASES, "earthheave");
        addConfigured(built, PromisedConsortActionId.LION_CLAW, BOTH_PHASES, "lion_claw");
        addLionClawFollowup(built);
        addConfigured(built, PromisedConsortActionId.STARCALLER_CRY, BOTH_PHASES, "gravity");
        addConfigured(built, PromisedConsortActionId.GRAVITY_METEOR, BOTH_PHASES, "gravity");
        addConfigured(built, PromisedConsortActionId.STOMP, BOTH_PHASES, "stomp");
        addConfigured(built, PromisedConsortActionId.CROSS_SLASH, BOTH_PHASES, "cross_slash");
        addConfigured(built, PromisedConsortActionId.SPIRAL_ASSAULT, BOTH_PHASES, "spiral_assault");
        addConfigured(built, PromisedConsortActionId.LIGHT_OF_MIQUELLA, PHASE_TWO, "miquella_light");
        addConfigured(built, PromisedConsortActionId.RING_OF_LIGHT, PHASE_TWO, "miquella_light");
        addConfigured(built, PromisedConsortActionId.LIGHTSPEED_SLASH, PHASE_TWO, "lightspeed");
        addConfigured(built, PromisedConsortActionId.LIGHTSPEED_DASH, PHASE_TWO, "lightspeed");
        addConfigured(built, PromisedConsortActionId.LIGHTSPEED_SIDE_DASH, PHASE_TWO, "lightspeed");
        addConfigured(built, PromisedConsortActionId.PROMISED_CONSORT, PHASE_TWO, "promised_consort");
        addConfigured(built, PromisedConsortActionId.CROSS_LEAP_COMBO, PHASE_TWO, "cross_leap_combo");
        addConfigured(built, PromisedConsortActionId.ENHANCED_EARTHHEAVE, PHASE_TWO, "earthheave");
        addConfigured(built, PromisedConsortActionId.CONSORT_METEOR, PHASE_TWO, "consort_meteor");
        for (var action : new PromisedConsortActionId[]{PromisedConsortActionId.GRAVITY_BULWARK,
                PromisedConsortActionId.GRAVITY_REFLECTION, PromisedConsortActionId.GRAVITY_REPRISAL}) {
            var skill = skillConfig.get(action);
            EnumSet<PromisedConsortPhase> phases = EnumSet.noneOf(PromisedConsortPhase.class);
            for (int phase : skill.integerList("phases")) phases.add(PromisedConsortPhase.fromId(phase));
            addConfigured(built, action, phases, "ranged_defense_" + action.serializedName());
        }
        definitions = Collections.unmodifiableMap(built);
    }

    public PromisedConsortSkillConfigSnapshot skillConfig() {
        return skillConfig;
    }

    public PromisedConsortActionDefinition get(PromisedConsortActionId actionId) {
        PromisedConsortActionDefinition definition = definitions.get(actionId);
        if (definition == null) {
            throw new IllegalArgumentException("unknown Promised Consort action: " + actionId);
        }
        return definition;
    }

    public Map<PromisedConsortActionId, PromisedConsortActionDefinition> definitions() {
        return definitions;
    }

    public PromisedConsortSkillConfigSnapshot.Skill skill(com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortActionSnapshot action) {
        var skill = skillConfig.get(action.actionId());
        return action.rangedCounter() ? skill.rangedVariant() : skill;
    }

    public ActionTimeline timeline(com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortActionSnapshot action) {
        return get(action.actionId(), action.rangedCounter()).timeline();
    }

    public PromisedConsortActionDefinition get(PromisedConsortActionId actionId, boolean rangedCounter) {
        var original = get(actionId);
        if (!rangedCounter) return original;
        return new PromisedConsortActionDefinition(actionId, timeline(actionId, skillConfig.get(actionId).rangedVariant()),
                original.weight(), original.cooldownTicks(), original.availablePhases(), original.hyperArmorActive(), original.cooldownGroup());
    }

    private void addConfigured(
            EnumMap<PromisedConsortActionId, PromisedConsortActionDefinition> target,
            PromisedConsortActionId actionId,
            EnumSet<PromisedConsortPhase> phases,
            String cooldownGroup
    ) {
        PromisedConsortSkillConfigSnapshot.Skill skill = skillConfig.get(actionId);
        target.put(actionId, new PromisedConsortActionDefinition(
                actionId,
            timeline(actionId, skill),
                skill.weight(),
                skill.cooldownTicks(),
                phases,
                skill.hyperArmorActive(),
                cooldownGroup
        ));
    }

    private void addLionClawFollowup(
            EnumMap<PromisedConsortActionId, PromisedConsortActionDefinition> target
    ) {
        PromisedConsortSkillConfigSnapshot.Skill skill =
                skillConfig.get(PromisedConsortActionId.LION_CLAW);
        target.put(PromisedConsortActionId.LION_CLAW_DOUBLE, new PromisedConsortActionDefinition(
                PromisedConsortActionId.LION_CLAW_DOUBLE,
            ActionTimeline.ofStages(new ActionStage(
                        skill.integer("double_windup_ticks"),
                        skill.integer("double_active_ticks"),
                        skill.integer("double_recovery_ticks")
            )),
                0.0,
                skill.cooldownTicks(),
                BOTH_PHASES,
                skill.hyperArmorActive(),
                "lion_claw"
        ));
    }

    private static ActionTimeline timeline(
            PromisedConsortActionId actionId,
            PromisedConsortSkillConfigSnapshot.Skill skill
    ) {
        if (actionId == PromisedConsortActionId.GRAVITY_REPRISAL) {
            var stages = new ActionStage[3];
            for (int index=0;index<3;index++) stages[index] = new ActionStage(skill.integerList("components.windup_ticks").get(index),
                skill.integerList("components.active_ticks").get(index), skill.integerList("components.recovery_ticks").get(index));
            return ActionTimeline.ofStages(stages);
        }
        if (actionId == PromisedConsortActionId.CONSORT_METEOR && !skill.integerLists().containsKey("windup_ticks")) {
            int scriptTicks = skill.integer("script_ticks");
            if (scriptTicks < 122) {
                throw new IllegalArgumentException("consort_meteor script must include tick 121");
            }
            return ActionTimeline.ofStages(new ActionStage(90, 32, scriptTicks - 122));
        }
        if (skill.integerLists().containsKey("windup_ticks")) {
            ActionTimeline configured = multiStage(actionId, skill);
            if (actionId == PromisedConsortActionId.R_COMBO_TEMPEST && configured.stages().size() == 4) {
                ActionStage combined = configured.stages().get(3);
                int firstActive = Math.max(1, combined.activeTicks() / 2);
                return ActionTimeline.ofStages(configured.stages().get(0), configured.stages().get(1), configured.stages().get(2),
                        new ActionStage(combined.windupTicks(), firstActive, 0),
                        new ActionStage(0, Math.max(1, combined.activeTicks() - firstActive), combined.recoveryTicks()));
            }
            return configured;
        }
        return ActionTimeline.ofStages(new ActionStage(
                skill.integer("windup_ticks"),
                skill.integer("active_ticks"),
                skill.integer("recovery_ticks")
        ));
    }

    private static ActionTimeline multiStage(PromisedConsortActionId actionId, PromisedConsortSkillConfigSnapshot.Skill skill) {
        var windup = skill.integerList("windup_ticks");
        var active = skill.integerList("active_ticks");
        var recovery = skill.integerList("recovery_ticks");
        if (windup.size() != active.size() || active.size() != recovery.size()) {
            throw new IllegalArgumentException("multi-stage timing lists must have equal sizes");
        }
        int expected = switch (actionId) {
            case L_COMBO_CROSS, R_COMBO_LEFT_TWIN -> 3;
            case R_COMBO_CROSS, GRAVITY_DIVE, CROSS_SLASH, SPIRAL_ASSAULT, STARCALLER_CRY -> 2;
            case L_COMBO_BLOODFLAME -> windup.size() == 2 ? 2 : 3;
            case R_COMBO_TEMPEST -> windup.size() == 4 ? 4 : 5;
            case R_COMBO_EARTHHEAVE, ENHANCED_EARTHHEAVE, CONSORT_METEOR -> 5;
            case LIGHTSPEED_SLASH, LIGHTSPEED_SIDE_DASH -> 4;
            case LIGHTSPEED_DASH -> 6;
            case PROMISED_CONSORT, CROSS_LEAP_COMBO -> 8;
            case GRAVITY_METEOR -> 12;
            case LIGHT_OF_MIQUELLA -> 9;
            default -> 1;
        };
        if (windup.size() != expected) throw new IllegalArgumentException(actionId + " requires " + expected + " timing components");
        ActionStage[] stages = new ActionStage[windup.size()];
        for (int index = 0; index < stages.length; index++) {
            if (active.get(index) < 1) throw new IllegalArgumentException(actionId + " component release must contain at least one tick");
            stages[index] = new ActionStage(windup.get(index), active.get(index), recovery.get(index));
        }
        ActionTimeline timeline = ActionTimeline.ofStages(stages);
        if (timeline.totalTicks() > com.tonywww.elder_bosses.network.NetworkLimits.MAX_TICKS) throw new IllegalArgumentException(actionId + " timing exceeds the tick limit");
        return timeline;
    }

}
