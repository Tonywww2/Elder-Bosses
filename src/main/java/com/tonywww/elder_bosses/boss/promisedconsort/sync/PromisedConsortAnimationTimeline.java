package com.tonywww.elder_bosses.boss.promisedconsort.sync;

import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.combat.action.ActionStage;
import com.tonywww.elder_bosses.combat.action.ActionTimeline;
import com.tonywww.elder_bosses.combat.action.SkillTuning;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class PromisedConsortAnimationTimeline {
    private PromisedConsortAnimationTimeline() {
    }

    public static double sample(double tick, PromisedConsortActionId action, ActionTimeline timeline, SkillTuning tuning) {
        int[] authored = durations(action);
        NavigableMap<Integer, Integer> points = new TreeMap<>();
        points.put(0, 0);
        int runtimeCursor = 0;
        int authoredCursor = 0;
        int index = 0;
        for (ActionStage stage : timeline.stages()) {
            int windup = authored[index++];
            int active = authored[index++];
            int recovery = authored[index++];
            runtimeCursor += stage.windupTicks();
            authoredCursor += windup;
            points.put(runtimeCursor, authoredCursor);
            if (action == PromisedConsortActionId.PROMISED_CONSORT) {
                for (int landmark : new int[]{8, 18, 28, 42, 44, 47, 50}) {
                    int offset = tuning.scaleTicks(landmark);
                    if (offset < stage.activeTicks()) points.put(runtimeCursor + offset, authoredCursor + landmark);
                }
            } else if (action == PromisedConsortActionId.STARCALLER_CRY
                    || action == PromisedConsortActionId.SPIRAL_ASSAULT
                    || action == PromisedConsortActionId.LIGHTSPEED_SLASH
                    || action == PromisedConsortActionId.LIGHTSPEED_DASH
                    || action == PromisedConsortActionId.LIGHTSPEED_SIDE_DASH) {
                if (stage.activeTicks() > 1) points.put(runtimeCursor + stage.activeTicks() - 1, authoredCursor + active - 1);
            }
            runtimeCursor += stage.activeTicks();
            authoredCursor += active;
            points.put(runtimeCursor, authoredCursor);
            runtimeCursor += stage.recoveryTicks();
            authoredCursor += recovery;
            points.put(runtimeCursor, authoredCursor);
        }
        if (action == PromisedConsortActionId.CONSORT_METEOR) {
            for (int landmark : new int[]{50, 90, 91, 110, 120, 121, 123}) {
                int scaled = tuning.scaleTicks(landmark);
                if (scaled > 0 && scaled < timeline.totalTicks()) points.put(scaled, landmark);
            }
        }
        double bounded = Math.max(0.0, Math.min(timeline.totalTicks(), tick));
        Map.Entry<Integer, Integer> lower = points.floorEntry((int) Math.floor(bounded));
        Map.Entry<Integer, Integer> upper = points.higherEntry(lower.getKey());
        if (upper == null) return lower.getValue();
        double fraction = (bounded - lower.getKey()) / (upper.getKey() - lower.getKey());
        return lower.getValue() + fraction * (upper.getValue() - lower.getValue());
    }

    public static int[] durations(PromisedConsortActionId action) {
        return switch (action) {
            case GRAVITY_DIVE -> new int[]{24, 6, 24};
            case L_COMBO_CROSS -> new int[]{9, 3, 7, 8, 3, 8, 14, 4, 22};
            case L_COMBO_BLOODFLAME -> new int[]{13, 3, 8, 12, 4, 24};
            case R_COMBO_CROSS -> new int[]{9, 3, 8, 15, 4, 22};
            case R_COMBO_LEFT_TWIN -> new int[]{9, 3, 7, 7, 3, 7, 8, 3, 20};
            case R_COMBO_TEMPEST -> new int[]{10, 3, 6, 10, 3, 6, 10, 3, 6, 18, 8, 26};
            case R_COMBO_EARTHHEAVE -> new int[]{10, 3, 6, 10, 3, 6, 10, 3, 6, 20, 5, 12, 10, 6, 30};
            case LION_CLAW -> new int[]{22, 5, 28};
            case LION_CLAW_DOUBLE -> new int[]{16, 5, 34};
            case STARCALLER_CRY -> new int[]{30, 10, 34};
            case GRAVITY_METEOR -> new int[]{32, 50, 36};
            case STOMP -> new int[]{14, 5, 24};
            case CROSS_SLASH -> new int[]{18, 5, 26};
            case SPIRAL_ASSAULT -> new int[]{26, 8, 30};
            case LIGHT_OF_MIQUELLA -> new int[]{44, 10, 42};
            case RING_OF_LIGHT -> new int[]{24, 5, 30};
            case LIGHTSPEED_SLASH -> new int[]{28, 24, 34};
            case LIGHTSPEED_DASH -> new int[]{26, 20, 36};
            case LIGHTSPEED_SIDE_DASH -> new int[]{18, 20, 30};
            case PROMISED_CONSORT -> new int[]{26, 54, 48};
            case ENHANCED_EARTHHEAVE -> new int[]{20, 20, 38};
            case CONSORT_METEOR -> new int[]{90, 32, 28};
        };
    }
}