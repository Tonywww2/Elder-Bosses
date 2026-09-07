package com.tonywww.elder_bosses.boss.malenia.domain;

public enum MaleniaPhase {
    PHASE_ONE(1, "phase_one"),
    PHASE_TWO(2, "phase_two");

    private final int id;
    private final String serializedName;

    MaleniaPhase(int id, String serializedName) {
        this.id = id;
        this.serializedName = serializedName;
    }

    public int id() {
        return id;
    }

    public String serializedName() {
        return serializedName;
    }

    public static MaleniaPhase fromId(int id) {
        return id == PHASE_TWO.id ? PHASE_TWO : PHASE_ONE;
    }
}