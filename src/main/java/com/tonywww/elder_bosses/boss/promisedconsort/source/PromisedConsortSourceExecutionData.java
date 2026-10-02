package com.tonywww.elder_bosses.boss.promisedconsort.source;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Original behavior selectors, regulation rows and TAE launch links. */
public final class PromisedConsortSourceExecutionData {
    public record Behavior(boolean exitAtEnd, boolean looping, double speed) {}
    public record Launch(int bulletId, int dummyId, int stateInfo) {}
    public record EventKey(int taeId, int eventIndex) {}
    private final Map<Integer,Behavior> behavior=new LinkedHashMap<>();
    private final Map<Integer,JsonObject> effects=new LinkedHashMap<>(),bullets=new LinkedHashMap<>(),attacks=new LinkedHashMap<>();
    private final Map<EventKey,Launch> launches=new LinkedHashMap<>();
    private final Map<EventKey,JsonObject> eventFields=new LinkedHashMap<>();
    private static final class Holder { static final PromisedConsortSourceExecutionData DATA=load(); }
    public static PromisedConsortSourceExecutionData get() { return Holder.DATA; }
    public Behavior behavior(int tae) { return require(behavior,tae); }
    public JsonObject bullet(int id) { return require(bullets,id); }
    public boolean hasBullet(int id) { return bullets.containsKey(id); }
    public JsonObject attack(int id) { return require(attacks,id); }
    public JsonObject effect(int id) { return require(effects,id); }
    public Launch launch(int tae,int event) { return require(launches,new EventKey(tae,event)); }
    public JsonObject eventFields(int tae,int event) {return require(eventFields,new EventKey(tae,event));}
    public double effectDuration(int id) { var row=effects.get(id); return row==null ? 0 : row.get("effectEndurance").getAsDouble(); }
    public static double number(JsonObject row,String key) { return row.get(key).getAsDouble(); }
    public static int integer(JsonObject row,String key) { return row.get(key).getAsInt(); }
    private static <K,V> V require(Map<K,V> map,K key) {
        V value=map.get(key); if(value==null) throw new IllegalArgumentException("Missing original execution data: "+key); return value;
    }
    private static PromisedConsortSourceExecutionData load() {
        String path="/assets/elder_bosses/boss/promised_consort/source_runtime_contracts.json";
        var stream=PromisedConsortSourceExecutionData.class.getResourceAsStream(path);
        if(stream==null) throw new IllegalStateException("Missing source execution contract");
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var root=JsonParser.parseReader(reader).getAsJsonObject();
            if(root.get("schema_version").getAsInt()!=1) throw new IllegalArgumentException("Invalid execution contract");
            var data=new PromisedConsortSourceExecutionData();
            for(var value:root.getAsJsonArray("behavior")) {
                var row=value.getAsJsonObject();
                data.behavior.put(integer(row,"tae_id"),new Behavior(row.get("exit_at_single_play_end").getAsBoolean(),row.get("looping").getAsBoolean(),number(row.getAsJsonObject("generator"),"playbackSpeed")));
            }
            for(String name:new String[]{"effects","bullets","attacks"}) {
                var target=switch(name) {case "effects"->data.effects;case "bullets"->data.bullets;default->data.attacks;};
                for(var value:root.getAsJsonArray(name)) {var row=value.getAsJsonObject();target.put(integer(row,"id"),row.getAsJsonObject("cells"));}
            }
            for(var value:root.getAsJsonArray("launches")) {
                var row=value.getAsJsonObject();
                data.launches.put(new EventKey(integer(row,"tae_id"),integer(row,"event_index")),new Launch(integer(row,"bullet_id"),integer(row,"dummy_id"),integer(row,"state_info")));
            }
            try(var fieldsReader=new InputStreamReader(PromisedConsortSourceExecutionData.class.getResourceAsStream(PromisedConsortSourceBank.RESOURCE),StandardCharsets.UTF_8)) {
                var source=JsonParser.parseReader(fieldsReader).getAsJsonObject();
                for(var clip:source.getAsJsonArray("animations")) for(var event:clip.getAsJsonObject().getAsJsonArray("events")) {
                    var row=event.getAsJsonObject();
                    data.eventFields.put(new EventKey(integer(clip.getAsJsonObject(),"tae_id"),integer(row,"index")),row.getAsJsonObject("fields"));
                }
            }
            return data;
        } catch(java.io.IOException e) { throw new IllegalStateException("Cannot load original execution data",e); }
    }
}
