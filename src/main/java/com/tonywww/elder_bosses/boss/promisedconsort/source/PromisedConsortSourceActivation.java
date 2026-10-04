package com.tonywww.elder_bosses.boss.promisedconsort.source;

/** The arena wake gate is horizontal, including the raised divine-gate platform. */
public final class PromisedConsortSourceActivation {
    private PromisedConsortSourceActivation() {}
    public static boolean inside(double x,double z,double centerX,double centerZ,double radius) {
        double dx=x-centerX,dz=z-centerZ;
        return dx*dx+dz*dz<=radius*radius;
    }
}
