package com.tonywww.elder_bosses.boss.promisedconsort.source;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Server-side sampling of the same new numeric curves used by GeckoLib.
 * Full affine ancestry preserves nonuniform scale, zero-scale parts and body
 * lift. Collision/attachment consumers can sample this instead of a flat entity
 * origin. Source units are shared with extractedMotion, never scaled per clip.
 */
public final class PromisedConsortSourcePose {
    public record Point(double x, double y, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Nonfinite pose point");
        }
        Vector3d vector() { return new Vector3d(x,y,z); }
        static Point of(Vector3d vector) { return new Point(vector.x,vector.y,vector.z); }
    }
    private record Key(double seconds, Point value) {}
    private record Track(List<Key> keys, Point neutral) {
        Track { keys=List.copyOf(keys); }
        Point at(double seconds) {
            if (keys.isEmpty()) return neutral;
            if (seconds<=keys.get(0).seconds()) return keys.get(0).value();
            int lo=0, hi=keys.size()-1;
            if (seconds>=keys.get(hi).seconds()) return keys.get(hi).value();
            while (hi-lo>1) { int mid=(lo+hi)>>>1; if (keys.get(mid).seconds()<=seconds) lo=mid; else hi=mid; }
            Key first=keys.get(lo), next=keys.get(hi);
            return Point.of(first.value().vector().lerp(next.value().vector(),(seconds-first.seconds())/(next.seconds()-first.seconds())));
        }
    }
    private record Bone(int index, int parent, Point bind, Point frameEuler, Matrix4d inverseBindRotation,
                        Track rotation, Track position, Track scale) {}
    private final int hkxId;
    private final long durationMicros;
    private final double modelUnits;
    private final List<Bone> bones;

    private PromisedConsortSourcePose(int hkxId,long durationMicros,double modelUnits,List<Bone> bones) {
        this.hkxId=hkxId; this.durationMicros=durationMicros; this.modelUnits=modelUnits; this.bones=List.copyOf(bones);
    }
    public int hkxId() { return hkxId; }
    public long durationMicros() { return durationMicros; }

    /** Master has no parent. Transfer its translation delta, retaining rotation and scale. */
    public Point masterTranslationDelta(long sourceMicros) {
        if (sourceMicros<0) throw new IllegalArgumentException("Negative pose time");
        Bone master=bones.get(0);
        if (master.parent()!=-1) throw new IllegalStateException("Master must be the source root");
        Point delta=master.position().at(Math.min(sourceMicros,durationMicros)/1_000_000.0);
        return new Point(delta.x()/modelUnits,delta.y()/modelUnits,delta.z()/modelUnits);
    }

    public final class Sample {
        private final Matrix4d[] transforms;
        private Sample(Matrix4d[] transforms) { this.transforms=transforms; }
        public int hkxId() { return PromisedConsortSourcePose.this.hkxId; }
        /** The input is an authored FLVER bind-world point attached to this source bone. */
        public Point bindPoint(int sourceBone, Point bindWorldPoint) {
            Bone bone=bones.get(sourceBone);
            Vector3d offset=bindWorldPoint.vector().sub(bone.bind().vector());
            return Point.of(transforms[sourceBone].transformPosition(offset));
        }
        public Point joint(int sourceBone) { return Point.of(transforms[sourceBone].getTranslation(new Vector3d())); }
        /** Entity origin + extractedMotion + authored local pose, with one shared yaw and axis basis. */
        public Point worldPoint(int sourceBone,Point bindWorldPoint,Point entityOrigin,
                                PromisedConsortSourceMotion.Displacement motion,double yawDegrees) {
            return world(bindPoint(sourceBone,bindWorldPoint),entityOrigin,motion,yawDegrees);
        }
        public Point worldJoint(int sourceBone,Point entityOrigin,double yawDegrees) {
            return world(joint(sourceBone),entityOrigin,new PromisedConsortSourceMotion.Displacement(0,0,0),yawDegrees);
        }
        private Point world(Point local,Point entityOrigin,PromisedConsortSourceMotion.Displacement motion,double yawDegrees) {
            if (!Double.isFinite(yawDegrees)) throw new IllegalArgumentException("Invalid actor yaw");
            double yaw=Math.toRadians(yawDegrees), sin=Math.sin(yaw), cos=Math.cos(yaw);
            return new Point(entityOrigin.x()+motion.x()+local.x()*cos+local.z()*sin,
                    entityOrigin.y()+motion.y()+local.y(),entityOrigin.z()+motion.z()+local.x()*sin-local.z()*cos);
        }
    }

    public Sample sample(long sourceMicros) {
        return sampleThrough(sourceMicros,bones.size()-1);
    }
    /** Source indices are parent-first; foot grounding needs only bones 0..47. */
    Sample sampleThrough(long sourceMicros,int lastSourceBone) {
        if (sourceMicros<0) throw new IllegalArgumentException("Negative pose time");
        double seconds=Math.min(sourceMicros,durationMicros)/1_000_000.0;
        Matrix4d[] transforms=new Matrix4d[lastSourceBone+1];
        for (int i=0;i<=lastSourceBone;i++) {
            Bone bone=bones.get(i);
            Point euler=bone.rotation().at(seconds), translation=bone.position().at(seconds), scale=bone.scale().at(seconds);
            double x=Math.toRadians(-(euler.x()+bone.frameEuler().x()));
            double y=Math.toRadians(-(euler.y()+bone.frameEuler().y()));
            double z=Math.toRadians(euler.z()+bone.frameEuler().z());
            Vector3d offset=bone.bind().vector();
            if (bone.parent()>=0) offset.sub(bones.get(bone.parent()).bind().vector());
            offset.add(translation.x()/modelUnits,translation.y()/modelUnits,translation.z()/modelUnits);
            Matrix4d local=new Matrix4d().translation(offset).scale(-1,1,1)
                    .rotate(new Quaterniond().rotationZYX(z,y,x)).scale(-1,1,1)
                    .scale(scale.x(),scale.y(),scale.z()).mul(bone.inverseBindRotation());
            transforms[bone.index()]=bone.parent()<0 ? local : new Matrix4d(transforms[bone.parent()]).mul(local);
        }
        return new Sample(transforms);
    }

    public static PromisedConsortSourcePose load(Reader rigInput,Reader geometryInput,Reader animationInput,int hkxId) {
        if (hkxId<0) throw new IllegalArgumentException("Invalid source HKX");
        JsonObject rig=JsonParser.parseReader(rigInput).getAsJsonObject();
        if (!rig.get("method").getAsString().equals("complete_source_TRS_with_explicit_bind_basis_helpers")
                || rig.getAsJsonArray("frozen_root_indices").size()!=0
                || rig.get("source_units_to_world_units").getAsDouble()!=1)
            throw new IllegalArgumentException("Unsupported source body/units contract");
        double modelUnits=rig.get("model_units_per_world_unit").getAsDouble();
        if (!Double.isFinite(modelUnits) || modelUnits<=0) throw new IllegalArgumentException("Invalid model unit scale");
        var geometry=JsonParser.parseReader(geometryInput).getAsJsonObject().getAsJsonArray("minecraft:geometry");
        if (geometry.size()!=1) throw new IllegalArgumentException("Expected one source geometry");
        Map<String,Point> frameRotations=new HashMap<>();
        for (var value : geometry.get(0).getAsJsonObject().getAsJsonArray("bones")) {
            var bone=value.getAsJsonObject();
            frameRotations.put(bone.get("name").getAsString(),bone.has("rotation")
                    ? point(bone.getAsJsonArray("rotation")) : new Point(0,0,0));
        }
        var animations=JsonParser.parseReader(animationInput).getAsJsonObject().getAsJsonObject("animations");
        String clip="animation.promised_consort.source_"+String.format(java.util.Locale.ROOT,"%06d",hkxId);
        var animation=animations.getAsJsonObject(clip);
        if (animation==null) throw new IllegalArgumentException("Missing fresh source clip: "+clip);
        long duration=Math.round(animation.get("animation_length").getAsDouble()*1_000_000);
        if (duration<0) throw new IllegalArgumentException("Invalid pose duration");
        var tracks=animation.getAsJsonObject("bones");
        var bones=new ArrayList<Bone>();
        for (var value : rig.getAsJsonArray("bones")) {
            var bone=value.getAsJsonObject();
            int index=bone.get("source_index").getAsInt(), parent=bone.get("source_parent_index").getAsInt();
            if (index!=bones.size() || parent>=index || parent< -1) throw new IllegalArgumentException("Source skeleton must be topological");
            String frame=bone.get("transform_bone_name").getAsString();
            if (!frameRotations.containsKey(frame)) throw new IllegalArgumentException("Missing bind basis helper");
            var q=bone.getAsJsonArray("bind_world_quaternion_xyzw");
            Quaterniond quaternion=new Quaterniond(q.get(0).getAsDouble(),q.get(1).getAsDouble(),q.get(2).getAsDouble(),q.get(3).getAsDouble()).normalize();
            JsonObject channels=tracks.getAsJsonObject(frame);
            bones.add(new Bone(index,parent,point(bone.getAsJsonArray("bind_world_position")),frameRotations.get(frame),
                    new Matrix4d().rotate(quaternion.conjugate()),track(channels,"rotation",new Point(0,0,0)),
                    track(channels,"position",new Point(0,0,0)),track(channels,"scale",new Point(1,1,1))));
        }
        if (bones.size()!=rig.get("source_bone_count").getAsInt()) throw new IllegalArgumentException("Incomplete source rig");
        return new PromisedConsortSourcePose(hkxId,duration,modelUnits,bones);
    }

    private static Track track(JsonObject channels,String channel,Point neutral) {
        if (channels==null || !channels.has(channel)) return new Track(List.of(),neutral);
        var keys=new ArrayList<Key>();
        for (var entry : channels.getAsJsonObject(channel).entrySet()) {
            double seconds=Double.parseDouble(entry.getKey());
            if (!Double.isFinite(seconds) || seconds<0) throw new IllegalArgumentException("Invalid numeric source key");
            keys.add(new Key(seconds,point(entry.getValue().getAsJsonArray())));
        }
        keys.sort(Comparator.comparingDouble(Key::seconds));
        for (int i=1;i<keys.size();i++) if (keys.get(i).seconds()==keys.get(i-1).seconds()) throw new IllegalArgumentException("Duplicate source key time");
        return new Track(keys,neutral);
    }
    private static Point point(com.google.gson.JsonArray array) {
        if (array.size()!=3) throw new IllegalArgumentException("Expected XYZ source point");
        return new Point(array.get(0).getAsDouble(),array.get(1).getAsDouble(),array.get(2).getAsDouble());
    }
}
