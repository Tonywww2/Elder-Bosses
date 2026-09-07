package com.tonywww.elder_bosses.boss.malenia.action;

import com.tonywww.elder_bosses.boss.malenia.config.MaleniaSkillConfigSnapshot;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaPhase;
import com.tonywww.elder_bosses.combat.action.ActionStage;
import com.tonywww.elder_bosses.combat.action.ActionTimeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.tonywww.elder_bosses.boss.malenia.action.MaleniaActionEvent.Type;
import static com.tonywww.elder_bosses.boss.malenia.action.MaleniaActionTag.*;

public final class MaleniaActionCatalog {
    private static final Set<MaleniaPhase> BOTH_PHASES = Set.of(
            MaleniaPhase.PHASE_ONE,
            MaleniaPhase.PHASE_TWO
    );
    private static final Set<MaleniaPhase> SECOND_PHASE = Set.of(MaleniaPhase.PHASE_TWO);
    private static final int RAPID_OPENING_HIT_COUNT = 3;
    private static final int WATERFOWL_BURST_COUNT = 4;

    private final MaleniaSkillConfigSnapshot skillConfig;
    private final Map<MaleniaActionId, MaleniaActionDefinition> definitions;

    public MaleniaActionCatalog(MaleniaSkillConfigSnapshot skillConfig) {
        this.skillConfig = Objects.requireNonNull(skillConfig, "skillConfig");
        definitions = buildDefinitions(skillConfig);
    }

    public MaleniaSkillConfigSnapshot skillConfig() {
        return skillConfig;
    }

    public MaleniaActionDefinition get(MaleniaActionId id) {
        return definitions.get(Objects.requireNonNull(id, "id"));
    }

    public Map<MaleniaActionId, MaleniaActionDefinition> all() {
        return definitions;
    }

    public List<MaleniaActionDefinition> availableIn(MaleniaPhase phase) {
        Objects.requireNonNull(phase, "phase");
        return definitions.values().stream()
                .filter(definition -> definition.isAvailableIn(phase))
                .toList();
    }

    private static Map<MaleniaActionId, MaleniaActionDefinition> buildDefinitions(
            MaleniaSkillConfigSnapshot skillConfig
    ) {
        EnumMap<MaleniaActionId, MaleniaActionDefinition> definitions = new EnumMap<>(MaleniaActionId.class);
        MaleniaSkillConfigSnapshot.SingleSlash singleSlash = skillConfig.singleSlash();

        add(definitions, definition(
                MaleniaActionId.SINGLE_SLASH,
                timeline(singleSlash.windupTicks(), singleSlash.activeTicks(), singleSlash.recoveryTicks()),
                singleSlash.cooldownTicks(),
                BOTH_PHASES,
                Set.of(FLOWING_SWORDPLAY),
                MaleniaActionEvent.ordered(Type.SLASH, 0)
        ));
        MaleniaSkillConfigSnapshot.DoubleSlash doubleSlash = skillConfig.doubleSlash();
        add(definitions, definition(
                MaleniaActionId.DOUBLE_SLASH,
                timeline(doubleSlash.windupTicks(), doubleSlash.activeTicks(), doubleSlash.recoveryTicks()),
                doubleSlash.cooldownTicks(),
                BOTH_PHASES,
                Set.of(FLOWING_SWORDPLAY, REVERSING_COMBO),
                MaleniaActionEvent.ordered(Type.SLASH, 0),
                MaleniaActionEvent.ordered(Type.REVERSE_SLASH, 1)
        ));
        MaleniaSkillConfigSnapshot.RapidSlashes rapidSlashes = skillConfig.rapidSlashes();
        requireCount(rapidSlashes.openingHits(), RAPID_OPENING_HIT_COUNT, "rapidSlashes.openingHits");
        add(definitions, definition(
                MaleniaActionId.RAPID_SLASHES,
                timeline(
                        rapidSlashes.windupTicks(),
                        rapidSlashes.activeTicks(),
                        rapidSlashes.recoveryTicks()
                ),
                rapidSlashes.cooldownTicks(),
                BOTH_PHASES,
                Set.of(FLOWING_SWORDPLAY, RAPID_CHAIN, DELAYED_FINISHER),
                MaleniaActionEvent.ordered(Type.RAPID_SLASH, 0),
                MaleniaActionEvent.ordered(Type.RAPID_SLASH, 1),
                MaleniaActionEvent.ordered(Type.RAPID_SLASH, 2),
                MaleniaActionEvent.delayed(
                        Type.DELAYED_FINISHER,
                        3,
                        rapidSlashes.finisherDelayTicks()
                )
        ));
        MaleniaSkillConfigSnapshot.RunningSlash runningSlash = skillConfig.runningSlash();
        add(definitions, definition(
                MaleniaActionId.RUNNING_SLASH,
                timeline(runningSlash.windupTicks(), runningSlash.activeTicks(), runningSlash.recoveryTicks()),
                runningSlash.cooldownTicks(),
                BOTH_PHASES,
                Set.of(FLOWING_SWORDPLAY, RUNNING_ENTRY),
                MaleniaActionEvent.ordered(Type.RUNNING_ENTRY, 0),
                MaleniaActionEvent.ordered(Type.SLASH, 1)
        ));
        MaleniaSkillConfigSnapshot.UpwardCombo upwardCombo = skillConfig.upwardCombo();
        add(definitions, definition(
                MaleniaActionId.UPWARD_COMBO,
                timeline(upwardCombo.windupTicks(), upwardCombo.activeTicks(), upwardCombo.recoveryTicks()),
                upwardCombo.cooldownTicks(),
                BOTH_PHASES,
                Set.of(FLOWING_SWORDPLAY, LAUNCH_AND_PLUNGE),
                MaleniaActionEvent.ordered(Type.UPWARD_SLASH, 0),
                MaleniaActionEvent.ordered(Type.PLUNGING_SLASH, 1)
        ));
        MaleniaSkillConfigSnapshot.Kick kick = skillConfig.kick();
        add(definitions, definition(
                MaleniaActionId.KICK,
                timeline(kick.windupTicks(), kick.activeTicks(), kick.recoveryTicks()),
                kick.cooldownTicks(),
                BOTH_PHASES,
                Set.of(MaleniaActionTag.KICK),
                MaleniaActionEvent.ordered(Type.KICK, 0)
        ));
        MaleniaSkillConfigSnapshot.Thrust thrust = skillConfig.thrust();
        add(definitions, definition(
                MaleniaActionId.THRUST,
                timeline(thrust.windupTicks(), thrust.activeTicks(), thrust.recoveryTicks()),
                thrust.cooldownTicks(),
                BOTH_PHASES,
                Set.of(DELAYED_THRUST),
                MaleniaActionEvent.ordered(Type.THRUST, 0)
        ));
        MaleniaSkillConfigSnapshot.GrabImpale grabImpale = skillConfig.grabImpale();
        add(definitions, definition(
                MaleniaActionId.GRAB_IMPALE,
                timeline(grabImpale.windupTicks(), grabImpale.activeTicks(), grabImpale.recoveryTicks()),
                grabImpale.cooldownTicks(),
                BOTH_PHASES,
                Set.of(GRAB_AND_IMPALE),
                MaleniaActionEvent.ordered(Type.GRAB, 0),
                MaleniaActionEvent.ordered(Type.IMPALE, 1),
                MaleniaActionEvent.ordered(Type.THROW, 2)
        ));
        MaleniaSkillConfigSnapshot.RetreatSlash retreatSlash = skillConfig.retreatSlash();
        add(definitions, definition(
                MaleniaActionId.RETREAT_SLASH,
                timeline(retreatSlash.windupTicks(), retreatSlash.activeTicks(), retreatSlash.recoveryTicks()),
                retreatSlash.cooldownTicks(),
                BOTH_PHASES,
                Set.of(FLOWING_SWORDPLAY, RETREAT_COUNTER),
                MaleniaActionEvent.ordered(Type.RETREAT, 0),
                MaleniaActionEvent.ordered(Type.SLASH, 1)
        ));
        MaleniaSkillConfigSnapshot.WaterfowlDance waterfowlDance = skillConfig.waterfowlDance();
        requireCount(waterfowlDance.burstCount(), WATERFOWL_BURST_COUNT, "waterfowlDance.burstCount");
        add(definitions, highThreatDefinition(
                MaleniaActionId.WATERFOWL_DANCE,
                timeline(
                        waterfowlDance.windupTicks(),
                        waterfowlDance.activeTicks(),
                        waterfowlDance.recoveryTicks()
                ),
                waterfowlDance.cooldownTicks(),
                BOTH_PHASES,
                Set.of(AERIAL, TRACKING, MULTI_BURST),
                waterfowlEvents(waterfowlDance)
        ));
        MaleniaSkillConfigSnapshot.ScarletAeonia scarletAeonia = skillConfig.scarletAeonia();
        add(definitions, highThreatDefinition(
                MaleniaActionId.SCARLET_AEONIA,
                timeline(
                        scarletAeonia.windupTicks(),
                        scarletAeonia.activeTicks(),
                        scarletAeonia.recoveryTicks()
                ),
                scarletAeonia.cooldownTicks(),
                SECOND_PHASE,
                Set.of(AERIAL, SCARLET_ROT, SCARLET_BLOOM, LINGERING_AREA),
                MaleniaActionEvent.ordered(Type.AERIAL_HOVER, 0),
                MaleniaActionEvent.atTick(Type.TARGET_LOCK, 1, scarletAeonia.targetLockTick()),
                MaleniaActionEvent.ordered(Type.DIVE, 2),
                MaleniaActionEvent.ordered(Type.IMPACT, 3),
                MaleniaActionEvent.ordered(Type.SCARLET_BLOOM, 4),
                MaleniaActionEvent.ordered(Type.LINGERING_CLOUD, 5)
        ));
        MaleniaSkillConfigSnapshot.ScarletPlunge scarletPlunge = skillConfig.scarletPlunge();
        add(definitions, definition(
                MaleniaActionId.SCARLET_PLUNGE,
                timeline(
                        scarletPlunge.windupTicks(),
                        scarletPlunge.activeTicks(),
                        scarletPlunge.recoveryTicks()
                ),
                scarletPlunge.cooldownTicks(),
                SECOND_PHASE,
                Set.of(AERIAL, LAUNCH_AND_PLUNGE, SCARLET_ROT),
                MaleniaActionEvent.ordered(Type.PLUNGING_SLASH, 0),
                MaleniaActionEvent.ordered(Type.ROT_BURST, 1)
        ));
        MaleniaSkillConfigSnapshot.FlyingSlash flyingSlash = skillConfig.flyingSlash();
        add(definitions, definition(
                MaleniaActionId.FLYING_SLASH,
                timeline(flyingSlash.windupTicks(), flyingSlash.activeTicks(), flyingSlash.recoveryTicks()),
                flyingSlash.cooldownTicks(),
                SECOND_PHASE,
                Set.of(AERIAL, WINGED_ATTACK, FLOWING_SWORDPLAY),
                MaleniaActionEvent.ordered(Type.SWEEP, 0),
                MaleniaActionEvent.ordered(Type.THRUST, 1)
        ));
        MaleniaSkillConfigSnapshot.ScarletPhantoms scarletPhantoms = skillConfig.scarletPhantoms();
        add(definitions, highThreatDefinition(
                MaleniaActionId.SCARLET_PHANTOMS,
                timeline(
                        scarletPhantoms.windupTicks(),
                        scarletPhantoms.activeTicks(),
                        scarletPhantoms.recoveryTicks()
                ),
                scarletPhantoms.cooldownTicks(),
                SECOND_PHASE,
                Set.of(AERIAL, SCARLET_ROT, PHANTOM_ASSAULT),
                phantomEvents(
                        scarletPhantoms.phantomCount(),
                        scarletPhantoms.phantomIntervalTicks()
                )
        ));
        MaleniaSkillConfigSnapshot.WingedSweep wingedSweep = skillConfig.wingedSweep();
        add(definitions, definition(
                MaleniaActionId.WINGED_SWEEP,
                timeline(wingedSweep.windupTicks(), wingedSweep.activeTicks(), wingedSweep.recoveryTicks()),
                wingedSweep.cooldownTicks(),
                SECOND_PHASE,
                Set.of(WINGED_ATTACK, FLOWING_SWORDPLAY),
                MaleniaActionEvent.ordered(Type.SWEEP, 0)
        ));

        if (!definitions.keySet().equals(EnumSet.allOf(MaleniaActionId.class))) {
            throw new IllegalStateException("Malenia action catalog must cover every action id");
        }
        return Collections.unmodifiableMap(definitions);
    }

    private static MaleniaActionDefinition definition(
            MaleniaActionId id,
            ActionTimeline timeline,
            int cooldownTicks,
            Set<MaleniaPhase> phases,
            Set<MaleniaActionTag> tags,
            MaleniaActionEvent... events
    ) {
        return new MaleniaActionDefinition(
                id,
                timeline,
                cooldownTicks,
                phases,
                tags,
                List.of(events),
                false
        );
    }

    private static MaleniaActionDefinition highThreatDefinition(
            MaleniaActionId id,
            ActionTimeline timeline,
            int cooldownTicks,
            Set<MaleniaPhase> phases,
            Set<MaleniaActionTag> tags,
            MaleniaActionEvent... events
    ) {
        return new MaleniaActionDefinition(
                id,
                timeline,
                cooldownTicks,
                phases,
                tags,
                List.of(events),
                true
        );
    }

    private static ActionTimeline timeline(int windupTicks, int activeTicks, int recoveryTicks) {
        return timeline(new ActionStage(windupTicks, activeTicks, recoveryTicks));
    }

    private static ActionTimeline timeline(
            List<Integer> windupTicks,
            List<Integer> activeTicks,
            List<Integer> recoveryTicks
    ) {
        ActionStage[] stages = new ActionStage[windupTicks.size()];
        for (int index = 0; index < stages.length; index++) {
            stages[index] = new ActionStage(
                    windupTicks.get(index),
                    activeTicks.get(index),
                    recoveryTicks.get(index)
            );
        }
        return timeline(stages);
    }

    private static ActionTimeline timeline(ActionStage... stages) {
        return ActionTimeline.ofStages(stages);
    }

    private static MaleniaActionEvent[] waterfowlEvents(
            MaleniaSkillConfigSnapshot.WaterfowlDance waterfowlDance
    ) {
        MaleniaActionEvent[] events = new MaleniaActionEvent[WATERFOWL_BURST_COUNT + 1];
        events[0] = MaleniaActionEvent.ordered(Type.AERIAL_HOVER, 0);
        for (int burstIndex = 0; burstIndex < WATERFOWL_BURST_COUNT; burstIndex++) {
            events[burstIndex + 1] = MaleniaActionEvent.trackingBurst(
                    burstIndex + 1,
                    waterfowlDance.burstLockTicks().get(burstIndex),
                    waterfowlDance.burstMaxTravel().get(burstIndex),
                    waterfowlDance.burstMaxHitsPerTarget().get(burstIndex)
            );
        }
        return events;
    }

    private static MaleniaActionEvent[] phantomEvents(int phantomCount, int phantomIntervalTicks) {
        List<MaleniaActionEvent> events = new ArrayList<>(phantomCount + 1);
        events.add(MaleniaActionEvent.ordered(Type.PHANTOM_ASSAULT, 0));
        for (int sequence = 1; sequence < phantomCount; sequence++) {
            events.add(MaleniaActionEvent.delayed(
                    Type.PHANTOM_ASSAULT,
                    sequence,
                    phantomIntervalTicks
            ));
        }
        events.add(MaleniaActionEvent.ordered(Type.BOSS_DIVE, phantomCount));
        return events.toArray(MaleniaActionEvent[]::new);
    }

    private static void requireCount(int actual, int expected, String name) {
        if (actual != expected) {
            throw new IllegalArgumentException(name + " must be " + expected + " to preserve action syntax");
        }
    }

    private static void add(
            EnumMap<MaleniaActionId, MaleniaActionDefinition> definitions,
            MaleniaActionDefinition definition
    ) {
        if (definitions.put(definition.id(), definition) != null) {
            throw new IllegalStateException("duplicate Malenia action definition: " + definition.id());
        }
    }
}