package com.tonywww.elder_bosses.boss.promisedconsort.source;

import net.minecraft.world.phys.Vec3;

/** One correction curve for clone presentation, warning projection and authoritative contact. */
public final class PromisedConsortSourceLanding {
    public static final long GRAVITY_DESCENT_BEGIN=6_000_000,GRAVITY_DESCENT_END=7_500_000;
    /** The exported meteor recovery leaves the body Root aloft until W_Idle. */
    public static double gravityDescent(long sourceMicros) {
        return progress(sourceMicros,GRAVITY_DESCENT_BEGIN,GRAVITY_DESCENT_END);
    }
    public static double gravityDescentY(long sourceMicros,double authoredY,double beginY,double endY) {
        if(sourceMicros<GRAVITY_DESCENT_BEGIN) return authoredY;
        return beginY+(endY-beginY)*gravityDescent(sourceMicros);
    }
    public static double gravityBodyOffset(long sourceMicros,double beginSoleY,double currentSoleY) {
        if(sourceMicros<GRAVITY_DESCENT_BEGIN) return 0;
        return beginSoleY*(1-gravityDescent(sourceMicros))-currentSoleY;
    }
    public static double progress(long at,long begin,long end) {
        double t=Math.max(0,Math.min(1,(double)(at-begin)/Math.max(1,end-begin)));
        return t*t*(3-2*t);
    }
    public static Vec3 cloneCorrection(Vec3 origin,Vec3 authoredAtContact,Vec3 lockedContact) {
        return lockedContact.subtract(origin.add(authoredAtContact));
    }
    public static Vec3 anchoredPosition(Vec3 contact,Vec3 currentMotion,Vec3 contactMotion) {
        return contact.add(currentMotion.subtract(contactMotion));
    }
    /** Preserve the authored acceleration without the overshoot introduced by blending a large correction. */
    public static Vec3 dashPosition(Vec3 begin,Vec3 current,Vec3 authoredEnd,Vec3 correctedEnd) {
        Vec3 route=authoredEnd.subtract(begin).multiply(1,0,1);
        double progress=route.lengthSqr()<1e-9?1:Math.max(0,Math.min(1,current.subtract(begin).dot(route)/route.lengthSqr()));
        return begin.lerp(correctedEnd,progress);
    }
    public static Vec3 boundedPosition(Vec3 point,Vec3 center,double radius) {
        Vec3 offset=point.subtract(center).multiply(1,0,1);
        if(offset.length()<=radius) return point;
        offset=offset.normalize().scale(Math.max(0,radius));
        return new Vec3(center.x+offset.x,point.y,center.z+offset.z);
    }
    private PromisedConsortSourceLanding() {}
}
