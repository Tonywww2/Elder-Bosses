package com.tonywww.elder_bosses.client.vfx;

/** Times are in the authored animation clock, after server component retiming. */
public final class MaleniaEffectTimeline {
    private MaleniaEffectTimeline() {}

    public static int bladeWindow(String clip, double tick) {
        if (!Double.isFinite(tick)) return -1;
        int[] windows = switch (clip) {
            case "single_slash" -> new int[]{10, 13};
            case "double_slash" -> new int[]{11, 14, 29, 32};
            case "rapid_slashes" -> new int[]{14, 15, 16, 17, 18, 19, 26, 32};
            case "running_slash" -> new int[]{16, 20};
            case "upward_combo" -> new int[]{18, 22, 44, 49};
            case "thrust" -> new int[]{22, 26};
            case "grab_impale" -> new int[]{44, 45};
            case "retreat_slash" -> new int[]{8, 12};
            case "waterfowl_dance" -> new int[]{32, 50, 62, 74, 82, 102, 110, 116};
            case "scarlet_plunge" -> new int[]{24, 30};
            case "flying_slash" -> new int[]{20, 25, 45, 49};
            case "scarlet_phantoms" -> new int[]{76, 88};
            case "winged_sweep" -> new int[]{16, 24};
            default -> new int[0];
        };
        for (int i = 0; i < windows.length; i += 2) {
            if (tick >= windows[i] && tick < windows[i + 1]) return i / 2;
        }
        return -1;
    }

    public static double trailLifetime(String clip) {
        return clip.equals("waterfowl_dance") || clip.equals("rapid_slashes") ? 2.0 : 3.0;
    }

    public static double smooth(double value) {
        double t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }
}
