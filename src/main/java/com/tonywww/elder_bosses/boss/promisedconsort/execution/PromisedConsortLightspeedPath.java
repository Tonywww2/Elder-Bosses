package com.tonywww.elder_bosses.boss.promisedconsort.execution;

import com.tonywww.elder_bosses.combat.action.ActionTimeline;
import net.minecraft.world.phys.Vec3;

public record PromisedConsortLightspeedPath(int lockTick, int departureTick, int impactTick) {
    public static PromisedConsortLightspeedPath from(ActionTimeline timeline, int lead) {
        int body = timeline.stages().size() - 2;
        if (body < 1) return new PromisedConsortLightspeedPath(0, 0, 0);
        int impact = timeline.activeStartTick(body);
        int departure = Math.max(timeline.activeStartTick(body - 1), impact - 7);
        return new PromisedConsortLightspeedPath(Math.max(0, timeline.activeStartTick(0) - Math.max(1, lead)), departure, impact);
    }

    public boolean usable() {
        return lockTick < departureTick && departureTick < impactTick;
    }

    public boolean supports(double distance) {
        return usable() && Double.isFinite(distance) && distance >= 0 && distance <= 256
                && distance * 1.5 / (impactTick - departureTick) <= 8;
    }

    public Vec3 at(Vec3 origin, Vec3 end, double tick) {
        if (tick <= departureTick) return origin;
        if (tick >= impactTick) return end;
        double progress = (tick - departureTick) / (impactTick - departureTick);
        return origin.lerp(end, progress * progress * (3 - 2 * progress));
    }
}