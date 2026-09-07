package com.tonywww.elder_bosses.boss.malenia.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class MaleniaStateTransitions {
    private static final Map<MaleniaCombatState, Set<MaleniaCombatState>> ALLOWED = Map.of(
            MaleniaCombatState.DORMANT, EnumSet.of(MaleniaCombatState.INTRO),
            MaleniaCombatState.INTRO, EnumSet.of(MaleniaCombatState.PHASE_1),
            MaleniaCombatState.PHASE_1, EnumSet.of(MaleniaCombatState.STUNNED, MaleniaCombatState.TRANSITION),
            MaleniaCombatState.TRANSITION, EnumSet.of(
                    MaleniaCombatState.AEONIA_OPENING,
                    MaleniaCombatState.PHASE_2
            ),
            MaleniaCombatState.AEONIA_OPENING, EnumSet.of(
                    MaleniaCombatState.PHASE_2,
                    MaleniaCombatState.DEFEATED
            ),
            MaleniaCombatState.PHASE_2, EnumSet.of(MaleniaCombatState.STUNNED, MaleniaCombatState.DEFEATED),
            MaleniaCombatState.STUNNED, EnumSet.of(
                    MaleniaCombatState.PHASE_1,
                    MaleniaCombatState.PHASE_2,
                    MaleniaCombatState.TRANSITION,
                    MaleniaCombatState.DEFEATED
            ),
            MaleniaCombatState.DEFEATED, EnumSet.noneOf(MaleniaCombatState.class)
    );

    private MaleniaStateTransitions() {
    }

    public static boolean allows(MaleniaCombatState current, MaleniaCombatState next) {
        Set<MaleniaCombatState> allowed = ALLOWED.get(current);
        return allowed != null && allowed.contains(next);
    }
}