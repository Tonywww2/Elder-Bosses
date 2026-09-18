package com.tonywww.elder_bosses.boss.promisedconsort.execution;

import com.tonywww.elder_bosses.combat.action.ActionTimeline;
import net.minecraft.world.phys.Vec3;

public record PromisedConsortMeteorSequence(int groundTick, int riseTick, int crestTick, int landingLockTick, int landingTick) {
    public static final int AUTHORED_RISE_TICK = 28;
    public static final int AUTHORED_CREST_TICK = 45;

    public static PromisedConsortMeteorSequence from(ActionTimeline timeline) {
        int first = timeline.activeStartTick(0);
        int rocks = timeline.stages().size() - 4;
        int last = timeline.stages().size() - 1;
        int land = Math.min(timeline.totalTicks() - 1, timeline.activeEndTick(last)
                + (int) Math.ceil(timeline.stages().get(last).recoveryTicks() * 6.0 / 14));
        if (land <= timeline.activeEndTick(last)) land = 0;
        int ground = Math.max(1, (int) Math.ceil(first * 12.0 / 50));
        int rise = ground + Math.max(1, (int) Math.ceil(first * 3.0 / 50));
        int crest = Math.min(first - 1, rise + Math.min(16, Math.max(1, (int) Math.ceil(first * 16.0 / 50))));
        return new PromisedConsortMeteorSequence(ground, rise, crest,
                timeline.activeStartTick(Math.max(0, rocks)), land);
    }

    public boolean usable() {
        return groundTick > 0 && riseTick > groundTick && crestTick > riseTick && landingLockTick > crestTick && landingTick > landingLockTick;
    }

    public double height() {
        return Math.min(8, Math.min((crestTick - riseTick) * 0.6, (landingTick - landingLockTick) * 0.45));
    }

    public Vec3 landing(Vec3 origin, Vec3 target, double separation) {
        Vec3 offset = target.subtract(origin).multiply(1, 0, 1);
        double length = Math.min(12, Math.min(Math.max(0, offset.length() - separation), (landingTick - landingLockTick) * 0.55));
        return origin.add(offset.normalize().scale(length));
    }

    public Vec3 at(Vec3 origin, Vec3 landing, double tick) {
        return at(origin, origin.add(0, height(), 0), landing, tick);
    }

    public Vec3 crest(Vec3 origin, Vec3 forward) {
        double distance = Math.min(8, Math.min((crestTick - riseTick) * 0.55, (landingTick - landingLockTick) * 0.35));
        Vec3 direction = forward.multiply(1, 0, 1).normalize();
        return origin.add(direction.scale(distance)).add(0, height(), 0);
    }

    public Vec3 at(Vec3 origin, Vec3 crest, Vec3 landing, double tick) {
        if (tick <= riseTick) return origin;
        if (tick < crestTick) return origin.lerp(crest, smooth((tick - riseTick) / (crestTick - riseTick)));
        if (tick <= landingLockTick) return crest;
        if (tick >= landingTick) return landing;
        double progress = smooth((tick - landingLockTick) / (landingTick - landingLockTick));
        return crest.lerp(landing, progress);
    }

    private static double smooth(double value) { return value * value * (3 - 2 * value); }
}