package com.tonywww.elder_bosses.boss.promisedconsort.source;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTimeline.*;

/** One atomic synced actor identity and clock, shared by body and independent clones. */
public record PromisedConsortSourcePlayback(Actor actor,long startWorldMicros,double speed,long sourceOffsetMicros,PromisedConsortSourceTimeWarp warp) {
    public PromisedConsortSourcePlayback(Actor actor,long startWorldMicros,double speed) {this(actor,startWorldMicros,speed,0,PromisedConsortSourceTimeWarp.IDENTITY);}
    public PromisedConsortSourcePlayback(Actor actor,long startWorldMicros,double speed,long offset) {this(actor,startWorldMicros,speed,offset,PromisedConsortSourceTimeWarp.IDENTITY);}
    public PromisedConsortSourcePlayback {
        if (actor==null || warp==null || startWorldMicros<0 || sourceOffsetMicros<0 || !Double.isFinite(speed) || speed<=0)
            throw new IllegalArgumentException("Invalid source playback");
        Clip clip=PromisedConsortSourceAssets.bank().requireClip(actor.taeId());
        if (clip.hkxId()!=actor.hkxId()) throw new IllegalArgumentException("Actor TAE/HKX mismatch");
    }
    public static PromisedConsortSourcePlayback of(PromisedConsortSourceSession.Started start) {
        return new PromisedConsortSourcePlayback(start.actor(),start.startWorldMicros(),start.speed(),0,start.warp());
    }
    public long sourceMicros(long worldMicros) {
        return warp.sourceAt(warp.gameAt(sourceOffsetMicros)+(long)Math.floor(Math.max(0,worldMicros-startWorldMicros)*speed));
    }
    public long worldAtSource(long source) {return startWorldMicros+(long)Math.ceil((warp.gameAt(source)-warp.gameAt(sourceOffsetMicros))/speed);}
    public double animationTicks(long gameTick,double partialTick) {
        if (!Double.isFinite(partialTick) || partialTick<0 || partialTick>1) throw new IllegalArgumentException("Invalid partial tick");
        long time=poseMicros(Math.addExact(Math.multiplyExact(gameTick,GAME_TICK_MICROS),(long)(partialTick*GAME_TICK_MICROS)));
        long duration=PromisedConsortSourceTransition.cinematic(this)?(long)(PromisedConsortSourceTransition.END*1_000_000):PromisedConsortSourceAssets.bank().requireClip(actor.taeId()).durationMicros();
        return Math.min(time,duration)/50_000.0;
    }
    public int poseId() {
        if(PromisedConsortSourceTransition.cinematic(this)) return PromisedConsortSourceTransition.POSE_ID;
        // Source preview actors keep the extracted animation. Production repairs
        // use one baked skeleton for rendering, attachments and hit samples.
        if(actor.actionSequence()>=(1L<<60)) return actor.hkxId();
        return productionPose(actor.hkxId());
    }
    public static int productionPose(int sourceHkx) {
        return switch(sourceHkx) {case 3013->930013;case 3017->930017;case 4100->934100;case 4101->934101;default->sourceHkx;};
    }
    public static boolean matchesSourcePose(int sourceHkx,int sampledPose) {
        return sampledPose==sourceHkx || sampledPose==productionPose(sourceHkx);
    }
    public long poseMicros(long world) {return PromisedConsortSourceTransition.cinematic(this)?(long)(PromisedConsortSourceTransition.seconds(this,world)*1_000_000):sourceMicros(world);}
    public String animationClip() {return "animation.promised_consort.source_"+String.format(java.util.Locale.ROOT,"%06d",poseId());}

    /** Transfer only Master translation to the actor; rendering removes that same channel. */
    public Vec3 displacement(long worldMicros,double yawDegrees) {
        long time=sourceMicros(worldMicros);
        var motion=PromisedConsortSourceAssets.bank().motions().get(actor.taeId()).displacement(time,yawDegrees);
        var master=PromisedConsortSourceAssets.pose(actor.hkxId()).masterTranslationDelta(time);
        double yaw=Math.toRadians(yawAt(worldMicros,yawDegrees)),sin=Math.sin(yaw),cos=Math.cos(yaw);
        return new Vec3(motion.x()+master.x()*cos+master.z()*sin,
                motion.y()+master.y(),motion.z()+master.x()*sin-master.z()*cos);
    }
    public float yawAt(long worldMicros,double initialYaw) {
        return (float)(initialYaw+PromisedConsortSourceAssets.bank().motions().get(actor.taeId()).yawDeltaDegrees(sourceMicros(worldMicros)));
    }
    public CompoundTag encode() {
        CompoundTag tag=new CompoundTag();
        tag.putInt("Version",4); tag.putLong("Sequence",actor.actionSequence());
        tag.putInt("Segment",actor.segmentIndex()); tag.putInt("Slot",actor.slot());
        tag.putInt("TAE",actor.taeId()); tag.putInt("HKX",actor.hkxId()); tag.putInt("StateInfo",actor.stateInfo());
        tag.putLong("StartMicros",startWorldMicros); tag.putDouble("Speed",speed);
        tag.putLong("SourceOffsetMicros",sourceOffsetMicros);
        tag.putLongArray("Warp",warp.values());
        return tag;
    }
    public static PromisedConsortSourcePlayback decode(CompoundTag tag) {
        if (tag.isEmpty()) return null;
        if (tag.getInt("Version")!=4) throw new IllegalArgumentException("Unsupported source playback state");
        long[] w=tag.getLongArray("Warp");
        return new PromisedConsortSourcePlayback(new Actor(tag.getLong("Sequence"),tag.getInt("Segment"),tag.getInt("Slot"),
                tag.getInt("TAE"),tag.getInt("HKX"),tag.getInt("StateInfo")),tag.getLong("StartMicros"),tag.getDouble("Speed"),tag.getLong("SourceOffsetMicros"),PromisedConsortSourceTimeWarp.from(w));
    }
}
