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
        addConfigured(built, PromisedConsortActionId.ENHANCED_EARTHHEAVE, PHASE_TWO, "earthheave");
        addConfigured(built, PromisedConsortActionId.CONSORT_METEOR, PHASE_TWO, "consort_meteor");
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
        if (actionId == PromisedConsortActionId.CONSORT_METEOR) {
            int scriptTicks = skill.integer("script_ticks");
            if (scriptTicks < 122) {
                throw new IllegalArgumentException("consort_meteor script must include tick 121");
            }
            return ActionTimeline.ofStages(new ActionStage(90, 32, scriptTicks - 122));
        }
        if (skill.integerLists().containsKey("windup_ticks")) {
            return multiStage(skill);
        }
        return ActionTimeline.ofStages(new ActionStage(
                skill.integer("windup_ticks"),
                skill.integer("active_ticks"),
                skill.integer("recovery_ticks")
        ));
    }

    private static ActionTimeline multiStage(PromisedConsortSkillConfigSnapshot.Skill skill) {
        var windup = skill.integerList("windup_ticks");
        var active = skill.integerList("active_ticks");
        var recovery = skill.integerList("recovery_ticks");
        if (windup.size() != active.size() || active.size() != recovery.size()) {
            throw new IllegalArgumentException("multi-stage timing lists must have equal sizes");
        }
        ActionStage[] stages = new ActionStage[windup.size()];
        for (int index = 0; index < stages.length; index++) {
            stages[index] = new ActionStage(windup.get(index), active.get(index), recovery.get(index));
        }
        return ActionTimeline.ofStages(stages);
    }
}
