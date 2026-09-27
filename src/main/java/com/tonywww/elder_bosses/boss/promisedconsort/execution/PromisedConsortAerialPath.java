package com.tonywww.elder_bosses.boss.promisedconsort.execution;

import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.combat.action.ActionTimeline;
import net.minecraft.world.phys.Vec3;

/** Entity flight, separate from the tucked legs and landing compression in the pose. */
public record PromisedConsortAerialPath(int takeoffTick, int crestTick, int descentTick, int landingTick, double height) {
    public PromisedConsortAerialPath {
        if (takeoffTick < 1 || crestTick <= takeoffTick || descentTick < crestTick || landingTick <= descentTick
                || !Double.isFinite(height) || height < 0 || height > 2.8) throw new IllegalArgumentException("Invalid aerial path");
    }

    public static PromisedConsortAerialPath from(PromisedConsortActionId action, ActionTimeline timeline, int rangedLockLead) {
        if (timeline.stages().size() < 4 || timeline.activeStartTick(0) < 6) return null;
        int first = timeline.activeStartTick(0);
        if (action == PromisedConsortActionId.LIGHTSPEED_SLASH) {
            int takeoff = Math.max(1, first * 12 / 35), crest = Math.max(takeoff + 1, first * 28 / 35);
            int landing = timeline.activeStartTick(3);
            int descent = Math.max(crest, timeline.activeStartTick(2) + Math.max(1, timeline.stages().get(2).activeTicks() / 3));
            if (descent >= landing) return null;
            return new PromisedConsortAerialPath(takeoff, crest, descent, landing,
                    Math.min(2.8, Math.min(crest - takeoff, landing - descent) * 0.55));
        }
        if (action == PromisedConsortActionId.LIGHTSPEED_SIDE_DASH) {
            // Finish the preparatory sidestep before the ranged path is frozen.
            int landing = Math.min(first * 24 / 36, first - Math.max(0, rangedLockLead));
            if (landing < 4) return null;
            int takeoff = Math.max(1, Math.min(first * 8 / 36, landing - 2));
            int crest = takeoff + (landing - takeoff) / 2;
            return new PromisedConsortAerialPath(takeoff, crest, crest, landing,
                    Math.min(1.6, Math.min(crest - takeoff, landing - crest) * 0.5));
        }
        return null;
    }

    public Vec3 at(Vec3 origin, Vec3 end, double tick) {
        if (tick <= takeoffTick) return origin;
        if (tick >= landingTick) return end;
        double lift = tick < crestTick ? smooth((tick - takeoffTick) / (crestTick - takeoffTick))
                : tick <= descentTick ? 1 : 1 - smooth((tick - descentTick) / (landingTick - descentTick));
        return origin.lerp(end, smooth((tick - takeoffTick) / (landingTick - takeoffTick))).add(0, height * lift, 0);
    }

    private static double smooth(double value) {
        return value * value * (3 - 2 * value);
    }
}
