package com.tonywww.elder_bosses.boss.malenia.sync;

import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.combat.action.ActionStage;
import com.tonywww.elder_bosses.combat.action.ActionTimeline;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class MaleniaAnimationTimeline {
    private MaleniaAnimationTimeline() {
    }

    public static double sample(
            double tick,
            MaleniaActionId action,
            ActionTimeline timeline,
            Map<Integer, Integer> landmarks
    ) {
        int[] authored = durations(action);
        NavigableMap<Integer, Integer> points = new TreeMap<>();
        int runtimeCursor = 0;
        int authoredCursor = 0;
        int index = 0;
        points.put(0, 0);
        for (ActionStage stage : timeline.stages()) {
            for (int duration : new int[]{stage.windupTicks(), stage.activeTicks(), stage.recoveryTicks()}) {
                runtimeCursor += duration;
                authoredCursor += authored[index++];
                points.put(runtimeCursor, authoredCursor);
            }
        }
        landmarks.forEach((runtimeTick, authoredTick) -> {
            if (runtimeTick > 0 && runtimeTick < timeline.totalTicks()) {
                points.put(runtimeTick, authoredTick);
            }
        });
        return interpolate(tick, points);
    }

    public static double interpolate(double tick, NavigableMap<Integer, Integer> points) {
        double bounded = Math.max(points.firstKey(), Math.min(points.lastKey(), tick));
        Map.Entry<Integer, Integer> lower = points.floorEntry((int) Math.floor(bounded));
        Map.Entry<Integer, Integer> upper = points.higherEntry(lower.getKey());
        if (upper == null) {
            return lower.getValue();
        }
        double fraction = (bounded - lower.getKey()) / (upper.getKey() - lower.getKey());
        return lower.getValue() + fraction * (upper.getValue() - lower.getValue());
    }

    public static int[] durations(MaleniaActionId action) {
        return switch (action) {
            case SINGLE_SLASH -> new int[]{10, 3, 14};
            case DOUBLE_SLASH -> new int[]{11, 3, 6, 9, 3, 18};
            case RAPID_SLASHES -> new int[]{14, 18, 22};
            case RUNNING_SLASH -> new int[]{16, 4, 18};
            case UPWARD_COMBO -> new int[]{18, 4, 8, 14, 5, 24};
            case KICK -> new int[]{9, 4, 18};
            case THRUST -> new int[]{22, 4, 24};
            case GRAB_IMPALE -> new int[]{24, 5, 38};
            case RETREAT_SLASH -> new int[]{8, 4, 20};
            case WATERFOWL_DANCE -> new int[]{32, 68, 42};
            case SCARLET_AEONIA -> new int[]{42, 58, 54};
            case SCARLET_PLUNGE -> new int[]{24, 12, 30};
            case FLYING_SLASH -> new int[]{20, 5, 8, 12, 4, 24};
            case SCARLET_PHANTOMS -> new int[]{36, 72, 38};
            case WINGED_SWEEP -> new int[]{16, 8, 22};
        };
    }
}