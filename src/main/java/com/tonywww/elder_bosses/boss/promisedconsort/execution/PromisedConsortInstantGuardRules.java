package com.tonywww.elder_bosses.boss.promisedconsort.execution;

import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;

public final class PromisedConsortInstantGuardRules {
    private PromisedConsortInstantGuardRules() {
    }

    public static boolean eligible(PromisedConsortActionId actionId, int stageIndex) {
        return switch (actionId) {
            case L_COMBO_CROSS, L_COMBO_BLOODFLAME, R_COMBO_CROSS,
                    R_COMBO_LEFT_TWIN, R_COMBO_TEMPEST, LION_CLAW, LION_CLAW_DOUBLE,
                    SPIRAL_ASSAULT -> true;
            case R_COMBO_EARTHHEAVE -> stageIndex <= 3;
            case CROSS_SLASH, LIGHTSPEED_SLASH, LIGHTSPEED_DASH,
                    LIGHTSPEED_SIDE_DASH, PROMISED_CONSORT, ENHANCED_EARTHHEAVE -> true;
            default -> false;
        };
    }
}
