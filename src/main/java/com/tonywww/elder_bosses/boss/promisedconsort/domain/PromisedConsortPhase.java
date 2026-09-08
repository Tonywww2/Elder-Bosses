package com.tonywww.elder_bosses.boss.promisedconsort.domain;

public enum PromisedConsortPhase {
    PHASE_ONE(1, "phase_one"),
    PHASE_TWO(2, "phase_two");

    private final int id;
    private final String serializedName;

    PromisedConsortPhase(int id, String serializedName) {
        this.id = id;
        this.serializedName = serializedName;
    }

    public int id() {
        return id;
    }

    public String serializedName() {
        return serializedName;
    }

    public static PromisedConsortPhase fromId(int id) {
        return id == PHASE_TWO.id ? PHASE_TWO : PHASE_ONE;
    }
}
