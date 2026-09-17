package com.tonywww.elder_bosses.boss.promisedconsort.execution;

import com.tonywww.elder_bosses.combat.action.ActionTimeline;
import net.minecraft.world.phys.Vec3;

public record PromisedConsortCrossLeapPath(int takeoffTick, int landingTick, double height) {
    public PromisedConsortCrossLeapPath {
        if (takeoffTick < 1 || landingTick <= takeoffTick || !Double.isFinite(height) || height < 0 || height > 4) {
            throw new IllegalArgumentException("Invalid cross-leap path");
        }
    }

    public static PromisedConsortCrossLeapPath opening(ActionTimeline timeline, double height) {
        int landing = timeline.activeStartTick(0);
        return new PromisedConsortCrossLeapPath(Math.max(1, (int) Math.floor(landing * 12.0 / 27)), landing, height);
    }

    public static PromisedConsortCrossLeapPath finisher(ActionTimeline timeline, double height) {
        int start = timeline.stageStartTick(4), landing = timeline.activeStartTick(4);
        return new PromisedConsortCrossLeapPath(start + Math.max(1, (landing - start) / 7), landing, height);
    }

    public Vec3 destination(Vec3 origin, Vec3 target, double maximum, double separation) {
        Vec3 offset = target.subtract(origin).multiply(1, 0, 1);
        return origin.add(offset.normalize().scale(Math.min(maximum, Math.max(0, offset.length() - separation))));
    }

    public Vec3 at(Vec3 origin, Vec3 destination, double tick) {
        if (tick <= takeoffTick) return origin;
        if (tick >= landingTick) return destination;
        double progress = (tick - takeoffTick) / (landingTick - takeoffTick);
        return origin.lerp(destination, progress * progress * (3 - 2 * progress))
                .add(0, height * Math.sin(Math.PI * progress), 0);
    }

    public boolean supports(Vec3 origin, Vec3 destination) {
        return (1.5 * origin.distanceTo(destination) + Math.PI * height) / (landingTick - takeoffTick) <= 2;
    }
}