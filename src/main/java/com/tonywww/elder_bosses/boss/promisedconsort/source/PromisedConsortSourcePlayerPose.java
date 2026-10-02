package com.tonywww.elder_bosses.boss.promisedconsort.source;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Untouched70890 source affine FK, independent of the vanilla player's gait. */
public final class PromisedConsortSourcePlayerPose {
    private final long duration;
    private final Matrix4f[][] frames;
    private final Map<String,Integer> names=new HashMap<>();
    private final Vector3f[] bind;
    private final Matrix4f[] inverseBind;
    private static final class Holder {static final PromisedConsortSourcePlayerPose INSTANCE=load();}
    public static PromisedConsortSourcePlayerPose get() {return Holder.INSTANCE;}
    private PromisedConsortSourcePlayerPose(long duration,int frameCount,int bones) {
        this.duration=duration;frames=new Matrix4f[frameCount][bones];bind=new Vector3f[bones];inverseBind=new Matrix4f[bones];
    }
    public int bone(String name) {Integer i=names.get(name);if(i==null) throw new IllegalArgumentException("Missing player source bone "+name);return i;}
    public Vector3f bind(int bone) {return new Vector3f(bind[bone]);}
    public Matrix4f matrix(int bone,long micros) {
        double time=Math.max(0,Math.min(micros,duration))/(double)duration*(frames.length-1);
        int i=(int)Math.floor(time),next=Math.min(i+1,frames.length-1);float fraction=(float)(time-i);
        // Source60fps FK sampled at render time. Complete affine interpolation
        // preserves authored scale; no limb is normalized or frozen per action.
        return new Matrix4f(frames[i][bone]).lerp(frames[next][bone],fraction);
    }
    public Matrix4f skin(int bone,long micros) {return matrix(bone,micros).mul(inverseBind[bone]);}
    private static PromisedConsortSourcePlayerPose load() {
        var stream=PromisedConsortSourcePlayerPose.class.getResourceAsStream("/assets/elder_bosses/boss/promised_consort/source_player_grab_pose.json");
        if(stream==null) throw new IllegalStateException("Missing original player grab pose");
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var root=JsonParser.parseReader(reader).getAsJsonObject();
            if(root.get("schema_version").getAsInt()!=1 || root.get("source_animation").getAsInt()!=70890) throw new IllegalArgumentException("Invalid source player pose");
            var bones=root.getAsJsonArray("bones");var data=new PromisedConsortSourcePlayerPose(root.get("duration_micros").getAsLong(),root.get("frame_count").getAsInt(),bones.size());
            for(var value:bones) {
                var row=value.getAsJsonObject();int index=row.get("index").getAsInt();data.names.put(row.get("name").getAsString(),index);
                var p=row.getAsJsonArray("bind_point");data.bind[index]=new Vector3f(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat());
                var a=row.getAsJsonArray("inverse_bind_basis");float[] m=new float[16];m[15]=1;
                for(int r=0;r<3;r++) for(int c=0;c<3;c++) m[c*4+r]=a.get(r*3+c).getAsFloat();data.inverseBind[index]=new Matrix4f().set(m);
            }
            var frames=root.getAsJsonArray("frames_affine_row_major");
            for(int f=0;f<frames.size();f++) for(int b=0;b<bones.size();b++) {
                var a=frames.get(f).getAsJsonArray().get(b).getAsJsonArray();float[] m=new float[16];
                for(int r=0;r<4;r++) for(int c=0;c<4;c++) m[c*4+r]=a.get(r*4+c).getAsFloat();data.frames[f][b]=new Matrix4f().set(m);
            }
            return data;
        } catch(java.io.IOException e) {throw new IllegalStateException("Cannot read original player pose",e);}
    }
}
