package com.tonywww.elder_bosses.boss.promisedconsort.domain;

public enum PromisedConsortCombatState {
    DORMANT(0, "dormant"),
    INTRO(1, "intro"),
    PHASE_1(2, "phase_1"),
    TRANSITION(3, "transition"),
    PHASE_2(4, "phase_2"),
    METEOR_SCRIPT(5, "meteor_script"),
    STUNNED(6, "stunned"),
    DEFEATED(7, "defeated");

    private final int id;
    private final String serializedName;

    PromisedConsortCombatState(int id, String serializedName) {
        this.id = id;
        this.serializedName = serializedName;
    }

    public int id() {
        return id;
    }

    public String serializedName() {
        return serializedName;
    }

    public static PromisedConsortCombatState fromId(int id) {
        for (PromisedConsortCombatState state : values()) {
            if (state.id == id) {
                return state;
            }
        }
        return DORMANT;
    }
}
