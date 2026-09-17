package com.tonywww.elder_bosses.boss.promisedconsort.domain;

import java.util.Arrays;
import java.util.Optional;

public enum PromisedConsortActionId {
    GRAVITY_DIVE("gravity_dive"),
    L_COMBO_CROSS("left_combo_cross"),
    L_COMBO_BLOODFLAME("left_combo_bloodflame"),
    R_COMBO_CROSS("right_combo_cross"),
    R_COMBO_LEFT_TWIN("right_combo_left_twin"),
    R_COMBO_TEMPEST("right_combo_tempest"),
    R_COMBO_EARTHHEAVE("right_combo_earthheave"),
    LION_CLAW("lion_claw"),
    LION_CLAW_DOUBLE("lion_claw_double"),
    STARCALLER_CRY("starcaller_cry"),
    GRAVITY_METEOR("gravity_meteor"),
    STOMP("stomp"),
    CROSS_SLASH("cross_slash"),
    SPIRAL_ASSAULT("spiral_assault"),
    LIGHT_OF_MIQUELLA("light_of_miquella"),
    RING_OF_LIGHT("ring_of_light"),
    LIGHTSPEED_SLASH("lightspeed_slash"),
    LIGHTSPEED_DASH("lightspeed_dash"),
    LIGHTSPEED_SIDE_DASH("lightspeed_side_dash"),
    PROMISED_CONSORT("promised_consort"),
    ENHANCED_EARTHHEAVE("enhanced_earthheave"),
    CONSORT_METEOR("consort_meteor"),
    GRAVITY_BULWARK("gravity_bulwark"),
    GRAVITY_REFLECTION("gravity_reflection"),
    GRAVITY_REPRISAL("gravity_reprisal"),
    CROSS_LEAP_COMBO("cross_leap_combo");

    private final String serializedName;

    PromisedConsortActionId(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean rangedDefense() {
        return this == GRAVITY_BULWARK || this == GRAVITY_REFLECTION || this == GRAVITY_REPRISAL;
    }

    public static Optional<PromisedConsortActionId> fromSerializedName(String name) {
        return Arrays.stream(values())
                .filter(action -> action.serializedName.equals(name))
                .findFirst();
    }
}
