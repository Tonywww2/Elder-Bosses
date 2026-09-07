package com.tonywww.elder_bosses.boss.malenia.domain;

public enum MaleniaActionId {
    SINGLE_SLASH("single_slash"),
    DOUBLE_SLASH("double_slash"),
    RAPID_SLASHES("rapid_slashes"),
    RUNNING_SLASH("running_slash"),
    UPWARD_COMBO("upward_combo"),
    KICK("kick"),
    THRUST("thrust"),
    GRAB_IMPALE("grab_impale"),
    RETREAT_SLASH("retreat_slash"),
    WATERFOWL_DANCE("waterfowl_dance"),
    SCARLET_AEONIA("scarlet_aeonia"),
    SCARLET_PLUNGE("scarlet_plunge"),
    FLYING_SLASH("flying_slash"),
    SCARLET_PHANTOMS("scarlet_phantoms"),
    WINGED_SWEEP("winged_sweep");

    private final String serializedName;

    MaleniaActionId(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }
}