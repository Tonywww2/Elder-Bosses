package com.tonywww.elder_bosses.boss.promisedconsort.execution;

import com.tonywww.elder_bosses.combat.action.ActionTimeline;
import net.minecraft.world.phys.Vec3;

public record PromisedConsortCrossLeapPath(int takeoffTick, int landingTick, double height) {
    public static final int ADVANCE_TAKEOFF_POSE = 7;
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

    public static PromisedConsortCrossLeapPath advance(ActionTimeline timeline, int contactOffset) {
        int impact = timeline.activeStartTick(0) + Math.max(0, contactOffset);
        int takeoff = Math.max(1, timeline.activeStartTick(0) * ADVANCE_TAKEOFF_POSE / 35);
        return new PromisedConsortCrossLeapPath(takeoff, impact, Math.min(2, (impact - takeoff) * 0.12));
    }

    public static PromisedConsortCrossLeapPath gravityDive(ActionTimeline timeline, int contactOffset) {
        int impact = timeline.activeStartTick(0) + Math.max(0, contactOffset);
        int takeoff = Math.max(1, timeline.activeStartTick(0) * 12 / 38);
        return new PromisedConsortCrossLeapPath(takeoff, impact, Math.min(3.5, (impact - takeoff) * 0.18));
    }

    public double maximumDistance(double requested) {
        return Math.min(requested, Math.max(0, ((landingTick - takeoffTick) * 2 - Math.PI * height) / 1.5 - 0.1));
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

    public Vec3 advanceDestination(Vec3 origin, Vec3 target, Vec3 forward, double maximum) {
        Vec3 offset = target.subtract(origin).multiply(1, 0, 1);
        Vec3 direction = offset.lengthSqr() > 0.000001 ? offset.normalize() : forward.multiply(1, 0, 1).normalize();
        double distance = Math.min(maximumDistance(maximum), Math.max(6, offset.length() - 1.5));
        return origin.add(direction.scale(distance));
    }

    public Vec3 advanceAt(Vec3 origin, Vec3 destination, double tick) {
        if (tick <= takeoffTick) return origin;
        if (tick >= landingTick) return destination;
        double progress = (tick - takeoffTick) / (landingTick - takeoffTick);
        double lift = Math.min(height, origin.subtract(destination).horizontalDistance() * 0.16);
        return origin.lerp(destination, 1 - Math.pow(1 - progress, 1.5))
                .add(0, lift * Math.sin(Math.PI * progress), 0);
    }

    public boolean supports(Vec3 origin, Vec3 destination) {
        return (1.5 * origin.distanceTo(destination) + Math.PI * height) / (landingTick - takeoffTick) <= 2;
    }
}