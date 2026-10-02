package com.tonywww.elder_bosses.boss.promisedconsort.source;

/** Source foot sole anchors; one body translation keeps pelvis, hands and hit volumes together. */
public final class PromisedConsortSourceGrounding {
    private PromisedConsortSourceGrounding() {}
    private static final class GravityRecovery {
        static final PromisedConsortSourcePose POSE=PromisedConsortSourceAssets.pose(3017);
        static final double BEGIN_SOLE=soleY(POSE,PromisedConsortSourceLanding.GRAVITY_DESCENT_BEGIN);
    }
    public static double soleY(PromisedConsortSourcePose pose,long sourceMicros) {
        return soleY(pose.sampleThrough(sourceMicros,47),pose.masterTranslationDelta(sourceMicros).y());
    }
    /** Sample at render partial-tick time too, so the body does not step between network updates. */
    public static double gravityMeteorOffset(long sourceMicros) {
        if(sourceMicros<PromisedConsortSourceLanding.GRAVITY_DESCENT_BEGIN) return 0;
        return PromisedConsortSourceLanding.gravityBodyOffset(sourceMicros,GravityRecovery.BEGIN_SOLE,soleY(GravityRecovery.POSE,sourceMicros));
    }
    public static double soleY(PromisedConsortSourcePose.Sample pose,double masterY) {
        var leftHeel=pose.bindPoint(30,new PromisedConsortSourcePose.Point(.3496719334843716,.008258596718994593,-.0008363886436926204));
        var rightHeel=pose.bindPoint(45,new PromisedConsortSourcePose.Point(-.3496724099124668,.008258586652160615,-.0008355544295516881));
        return Math.min(Math.min(leftHeel.y(),rightHeel.y()),Math.min(pose.joint(32).y(),pose.joint(47).y()))-masterY;
    }
}
