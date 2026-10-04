package com.tonywww.elder_bosses.boss.promisedconsort.source;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** One cinematic clock for the server, animation authoring and client presentation. */
public final class PromisedConsortSourceTransition {
    private static final JsonObject DATA=load();
    public static final int POSE_ID=DATA.get("pose_id").getAsInt();
    public static final double RISE_BEGIN=stage("rise","begin"), WALK_BEGIN=stage("walk","begin"), WALK_END=stage("walk_stop","end");
    public static final double TURN_BEGIN=WALK_END, TURN_END=stage("turn","end");
    public static final double LIGHT_BEGIN=value("gate_light_begin"), APPEAR=value("miquella_appear");
    public static final double APPEAR_END=value("miquella_appear_end"), BRIGHT_END=value("gate_bright_end");
    public static final double BACK_LIGHT_BEGIN=value("back_light_begin"), TELEPORT=value("miquella_teleport");
    public static final double BACK_LIGHT_END=value("back_light_end"), FLASH_END=value("teleport_flash_end");
    public static final double LIGHT_END=value("gate_light_end"), END=stage("ready","end");
    private PromisedConsortSourceTransition() {}
    private static JsonObject load() {
        var input=PromisedConsortSourceTransition.class.getResourceAsStream("/assets/elder_bosses/boss/promised_consort/phase_transition.json");
        if(input==null) throw new IllegalStateException("Missing phase transition timeline");
        try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {return JsonParser.parseReader(reader).getAsJsonObject();}
        catch(java.io.IOException e) {throw new IllegalStateException("Cannot read phase transition timeline",e);}
    }
    private static double value(String name) {return DATA.get(name).getAsDouble();}
    private static double stage(String name,String key) {
        for(var row:DATA.getAsJsonArray("stages")) if(row.getAsJsonObject().get("name").getAsString().equals(name)) return row.getAsJsonObject().get(key).getAsDouble();
        throw new IllegalArgumentException("Unknown cinematic stage: "+name);
    }
    public static boolean cinematic(PromisedConsortSourcePlayback playback) {
        return playback!=null && playback.actor().taeId()==20011 && playback.actor().slot()==-1 && playback.actor().actionSequence()<(1L<<60);
    }
    public static double scale(PromisedConsortSourcePlayback playback) {
        long original=PromisedConsortSourceAssets.bank().requireClip(20011).durationMicros();
        return playback.warp().gameAt(original)/(double)original/playback.speed();
    }
    public static double seconds(PromisedConsortSourcePlayback playback,long worldMicros) {
        return Math.max(0,worldMicros-playback.startWorldMicros())/1_000_000.0/scale(playback);
    }
    public static double progress(double seconds,double begin,double end) {
        double t=Math.max(0,Math.min(1,(seconds-begin)/(end-begin)));
        return t*t*(3-2*t);
    }



    public static float miquellaOpacity(double seconds) {
        return seconds>=TELEPORT?0:(float)progress(seconds,APPEAR,APPEAR_END);
    }
    public static boolean phaseTwoName(boolean phaseTwo,boolean transitioning,double seconds,boolean miquellaVisible,boolean sourceRig) {
        return phaseTwo && (!transitioning || (sourceRig?seconds>=APPEAR_END:miquellaVisible));
    }
}
