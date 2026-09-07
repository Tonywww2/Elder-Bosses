package com.tonywww.elder_bosses.boss.malenia.domain;

public enum MaleniaCombatState {
    DORMANT(0, "dormant"),
    INTRO(1, "intro"),
    PHASE_1(2, "phase_1"),
    TRANSITION(3, "transition"),
    AEONIA_OPENING(4, "aeonia_opening"),
    PHASE_2(5, "phase_2"),
    STUNNED(6, "stunned"),
    DEFEATED(7, "defeated");

    private final int id;
    private final String serializedName;

    MaleniaCombatState(int id, String serializedName) {
        this.id = id;
        this.serializedName = serializedName;
    }

    public int id() {
        return id;
    }

    public String serializedName() {
        return serializedName;
    }

    public static MaleniaCombatState fromId(int id) {
        for (MaleniaCombatState state : values()) {
            if (state.id == id) {
                return state;
            }
        }
        return DORMANT;
    }
}