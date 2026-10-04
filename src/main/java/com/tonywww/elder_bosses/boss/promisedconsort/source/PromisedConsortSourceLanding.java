package com.tonywww.elder_bosses.boss.promisedconsort.source;

import net.minecraft.world.phys.Vec3;

/** One correction curve for clone presentation, warning projection and authoritative contact. */
public final class PromisedConsortSourceLanding {
    public static final long STARFALL_ASCENT=2_333_333,STARFALL_WARNING_DELAY=1_000_000;
    public static long warningAt(PromisedConsortSourcePlayback playback) {
        return Math.addExact(playback.worldAtSource(STARFALL_ASCENT),STARFALL_WARNING_DELAY);
    }
    public static final long STARFALL_IMPACT=4_300_000,STARFALL_FOLLOW_DELAY=150_000;
    public static final long STARFALL_CONTACT=4_266_667,STARFALL_APPROACH=400_000;
    public static final double STARFALL_APPROACH_RADIUS=12;
    /** Keep every clone's summon/contact clock; accelerate the body after the final summon. */
    public static PromisedConsortSourceTimeWarp starfallClock(PromisedConsortSourceBank bank,
            PromisedConsortSourceTimeWarp body,double speed,long cloneWait) {
        long lastSummon=0;double lastContact=0;
        for(var chain:bank.cloneChains()) if(chain.parentTaeId()==3024) for(var cue:chain.actors()) {
            long contact=bank.requireClip(cue.taeId()).events().stream().filter(e->e.type()==1)
                    .mapToLong(PromisedConsortSourceTimeline.Event::startMicros).min().orElseThrow();
            var clone=bank.timeWarps().getOrDefault(cue.taeId(),PromisedConsortSourceTimeWarp.IDENTITY);
            lastSummon=Math.max(lastSummon,cue.spawnSourceMicros());
            lastContact=Math.max(lastContact,body.gameAt(cue.spawnSourceMicros())+clone.gameAt(contact)+cloneWait*speed);
        }
        if(lastSummon==0 || lastSummon>=STARFALL_IMPACT) throw new IllegalArgumentException("Missing starfall clone contacts");
        long landing=(long)Math.ceil(lastContact+STARFALL_FOLLOW_DELAY*speed);
        return body.withSpan(lastSummon,STARFALL_IMPACT,Math.max(1,landing-body.gameAt(lastSummon)));
    }
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
    /** The source Master includes an off-arena reset after contact, not recovery locomotion. */
    public static Vec3 starfallPosition(Vec3 contact,Vec3 currentMotion,Vec3 contactMotion,Vec3 approachMotion,double approachRadius,boolean landed) {
        if(landed) return contact;
        double distance=approachMotion.subtract(contactMotion).horizontalDistance();
        double scale=distance>approachRadius?Math.max(0,approachRadius)/distance:1;
        Vec3 offset=currentMotion.subtract(contactMotion);
        return contact.add(offset.x*scale,Math.max(0,offset.y),offset.z*scale);
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
