package com.tonywww.elder_bosses.boss.promisedconsort.source;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Source70890 local TRS interpolation and FK, independent of the vanilla player's gait. */
public final class PromisedConsortSourcePlayerPose {
    private final long duration;
    private record Transform(Vector3f position,Quaternionf rotation,Vector3f scale) {}
    private final Transform[][] frames;
    private final int[] parents;
    private final Map<String,Integer> names=new HashMap<>();
    private final Vector3f[] bind;
    private final Matrix4f[] inverseBind;
    private static final class Holder {static final PromisedConsortSourcePlayerPose INSTANCE=load();}
    public static PromisedConsortSourcePlayerPose get() {return Holder.INSTANCE;}
    private PromisedConsortSourcePlayerPose(long duration,int frameCount,int bones) {
        this.duration=duration;frames=new Transform[frameCount][bones];parents=new int[bones];bind=new Vector3f[bones];inverseBind=new Matrix4f[bones];
    }
    public int bone(String name) {Integer i=names.get(name);if(i==null) throw new IllegalArgumentException("Missing player source bone "+name);return i;}
    public Vector3f bind(int bone) {return new Vector3f(bind[bone]);}
    public final class Sample {
        private final Matrix4f[] world;
        private Sample(Matrix4f[] world) {this.world=world;}
        public Matrix4f matrix(int bone) {return new Matrix4f(world[bone]);}
        public Matrix4f skin(int bone) {return matrix(bone).mul(inverseBind[bone]);}
        /** Fit a rigid cube between actual joints; keep its cross-section orthogonal. */
        public Matrix4f segment(int bone,int end,Matrix4f bindBasis,int pixels) {
            Matrix4f transform=skin(bone).mul(bindBasis);
            if(end<0) return transform;
            Vector3f y=world[end].getTranslation(new Vector3f()).sub(world[bone].getTranslation(new Vector3f()));
            float length=y.length();if(length<1e-6f) return transform;
            y.div(length);
            Vector3f x=new Vector3f(transform.m00(),transform.m01(),transform.m02());float width=x.length();
            float depth=new Vector3f(transform.m20(),transform.m21(),transform.m22()).length();
            x.sub(new Vector3f(y).mul(x.dot(y)));
            if(x.lengthSquared()<1e-8f) x.set(y).cross(Math.abs(y.z)<.9f?new Vector3f(0,0,1):new Vector3f(0,1,0));
            x.normalize();Vector3f z=new Vector3f(x).cross(y).normalize();
            return transform.setColumn(0,new Vector4f(x.mul(width),0)).setColumn(1,new Vector4f(y.mul(length*16/pixels),0))
                    .setColumn(2,new Vector4f(z.mul(depth),0));
        }
    }
    public Sample sample(long micros) {
        double time=Math.max(0,Math.min(micros,duration))/(double)duration*(frames.length-1);
        int i=(int)Math.floor(time),next=Math.min(i+1,frames.length-1);float fraction=(float)(time-i);
        Matrix4f[] world=new Matrix4f[parents.length];
        for(int bone=0;bone<world.length;bone++) {
            var a=frames[i][bone];var b=frames[next][bone];
            Matrix4f local=new Matrix4f().translationRotateScale(new Vector3f(a.position()).lerp(b.position(),fraction),
                    new Quaternionf(a.rotation()).slerp(b.rotation(),fraction),new Vector3f(a.scale()).lerp(b.scale(),fraction));
            world[bone]=parents[bone]<0?local:new Matrix4f(world[parents[bone]]).mul(local);
        }
        return new Sample(world);
    }
    public Matrix4f matrix(int bone,long micros) {return sample(micros).matrix(bone);}
    public Matrix4f skin(int bone,long micros) {return sample(micros).skin(bone);}
    private static PromisedConsortSourcePlayerPose load() {
        var stream=PromisedConsortSourcePlayerPose.class.getResourceAsStream("/assets/elder_bosses/boss/promised_consort/source_player_grab_pose.json");
        if(stream==null) throw new IllegalStateException("Missing original player grab pose");
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var root=JsonParser.parseReader(reader).getAsJsonObject();
            if(root.get("schema_version").getAsInt()!=1 || root.get("source_animation").getAsInt()!=70890) throw new IllegalArgumentException("Invalid source player pose");
            var bones=root.getAsJsonArray("bones");var data=new PromisedConsortSourcePlayerPose(root.get("duration_micros").getAsLong(),root.get("frame_count").getAsInt(),bones.size());
            for(var value:bones) {
                var row=value.getAsJsonObject();int index=row.get("index").getAsInt();data.names.put(row.get("name").getAsString(),index);
                int parent=row.get("parent").getAsInt();if(parent>=index || parent< -1) throw new IllegalArgumentException("Player bones must be parent-first");data.parents[index]=parent;
                var p=row.getAsJsonArray("bind_point");data.bind[index]=new Vector3f(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat());
                var a=row.getAsJsonArray("inverse_bind_basis");float[] m=new float[16];m[15]=1;
                for(int r=0;r<3;r++) for(int c=0;c<3;c++) m[c*4+r]=a.get(r*3+c).getAsFloat();data.inverseBind[index]=new Matrix4f().set(m);
            }
            var frames=root.getAsJsonArray("frames_affine_row_major");
            for(int f=0;f<frames.size();f++) {
                Matrix4f[] world=new Matrix4f[bones.size()];
                for(int b=0;b<bones.size();b++) {
                    var a=frames.get(f).getAsJsonArray().get(b).getAsJsonArray();float[] m=new float[16];
                    for(int r=0;r<4;r++) for(int c=0;c<4;c++) m[c*4+r]=a.get(r*4+c).getAsFloat();world[b]=new Matrix4f().set(m);
                    Matrix4f local=data.parents[b]<0?new Matrix4f(world[b]):new Matrix4f(world[data.parents[b]]).invert().mul(world[b]);
                    data.frames[f][b]=new Transform(local.getTranslation(new Vector3f()),local.getUnnormalizedRotation(new Quaternionf()).normalize(),local.getScale(new Vector3f()));
                }
            }
            return data;
        } catch(java.io.IOException e) {throw new IllegalStateException("Cannot read original player pose",e);}
    }
}
