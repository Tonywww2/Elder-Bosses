package com.tonywww.elder_bosses.boss.promisedconsort.execution;

import net.minecraft.world.phys.Vec3;

public record PromisedConsortLionClawPath(int takeoffTick, int lockTick, int impactTick, double height, double maximumStep) {
    public static final double TARGET_OVERSHOOT = 1.5;
    public static final double NORMAL_MAXIMUM_STEP = 2.0;
    public PromisedConsortLionClawPath {
        if (takeoffTick < 0 || lockTick <= takeoffTick || impactTick <= lockTick || !Double.isFinite(height) || height < 0
                || !Double.isFinite(maximumStep) || maximumStep <= 0 || maximumStep > 8) {
            throw new IllegalArgumentException("Invalid lion claw trajectory");
        }
    }

    public static PromisedConsortLionClawPath create(int impactTick, int cueLeadTicks, double requestedHeight, double maximumStep) {
        int takeoff = Math.max(1, impactTick * 3 / 10);
        int lock = Math.max(takeoff + 1, impactTick - Math.max(cueLeadTicks, (impactTick - takeoff) * 3 / 5));
        lock = Math.min(impactTick - 1, lock);
        double height = Math.min(requestedHeight, Math.min((lock - takeoff) * maximumStep / 1.6,
            (impactTick - lock) * maximumStep / 2.4));
        return new PromisedConsortLionClawPath(takeoff, lock, impactTick, height, maximumStep);
    }

    public Vec3 destination(Vec3 origin, Vec3 target, double maximumDistance, double overshoot) {
        Vec3 offset = target.subtract(origin).multiply(1, 0, 1);
        double velocityBudget = (impactTick - lockTick) * maximumStep / 1.5;
        double verticalBudget = height * Math.PI / 2;
        double horizontalBudget = Math.sqrt(Math.max(0, velocityBudget * velocityBudget - verticalBudget * verticalBudget));
        double distance = Math.min(Math.max(0, offset.length() + overshoot), Math.min(maximumDistance,
            Math.min((impactTick - lockTick) * maximumStep * 0.55, horizontalBudget)));
        return origin.add(offset.normalize().scale(distance));
    }

    public static PromisedConsortLionClawPath opening(int impactTick, int cueLeadTicks) {
        int takeoff = Math.max(1, impactTick * 3 / 10);
        int lock = Math.max(takeoff + 1, Math.min(takeoff + 3, impactTick - Math.max(1, cueLeadTicks)));
        lock = Math.min(impactTick - 1, lock);
        return new PromisedConsortLionClawPath(takeoff, lock, impactTick, Math.min(6, (lock - takeoff) * 1.8), 7);
    }

    public Vec3 at(Vec3 origin, Vec3 destination, double tick) {
        if (tick <= takeoffTick) return origin;
        if (tick >= impactTick) return destination;
        if (tick <= lockTick) return origin.add(0, height * smooth((tick - takeoffTick) / (lockTick - takeoffTick)), 0);
        double progress = smooth((tick - lockTick) / (impactTick - lockTick));
        return origin.lerp(destination, progress).add(0, height * Math.cos(progress * Math.PI / 2), 0);
    }

    private static double smooth(double value) {
        return value * value * (3 - 2 * value);
    }
}