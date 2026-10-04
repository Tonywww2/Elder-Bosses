package com.tonywww.elder_bosses.boss.promisedconsort.config;

import com.google.gson.*;
import com.tonywww.elder_bosses.boss.promisedconsort.source.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Current original-only skill configuration. No legacy field aliases or migrations. */
public record PromisedConsortSourceConfigSnapshot(Map<String,Double> numbers,Map<String,Boolean> flags) {
    public static final int SAVE_VERSION=8;
    public static final String RESOURCE="/assets/elder_bosses/boss/promised_consort/source_config_defaults.json";
    public PromisedConsortSourceConfigSnapshot {
        numbers=Map.copyOf(numbers);flags=Map.copyOf(flags);
        var numericKeys=new HashSet<String>();var flagKeys=new HashSet<String>();
        for(var field:definition().getAsJsonArray("fields")) {
            var row=field.getAsJsonObject();String key=row.get("path").getAsString(),type=row.get("type").getAsString();
            if(type.equals("boolean")) {flagKeys.add(key);continue;}
            numericKeys.add(key);Double value=numbers.get(key);
            if(value==null || !Double.isFinite(value) || value<row.get("min").getAsDouble() || value>row.get("max").getAsDouble()
                    || type.equals("integer") && value!=Math.rint(value)) throw new IllegalArgumentException("Invalid source config: "+key);
        }
        if(!numericKeys.equals(numbers.keySet()) || !flagKeys.equals(flags.keySet())) throw new IllegalArgumentException("Incomplete original skill config snapshot");
    }
    private static final class Definition {
        static final JsonObject VALUE=loadDefinition();
        static final Map<String,Double> CONSTANTS=constants();
        private static Map<String,Double> constants() {
            var values=new LinkedHashMap<String,Double>();
            VALUE.getAsJsonObject("constants").entrySet().forEach(e->values.put(e.getKey(),e.getValue().getAsDouble()));
            return Map.copyOf(values);
        }
    }
    public static JsonObject definition() {return Definition.VALUE;}
    private static JsonObject loadDefinition() {
        var stream=PromisedConsortSourceConfigSnapshot.class.getResourceAsStream(RESOURCE);
        if(stream==null) throw new IllegalStateException("Missing original skill config definition");
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {return JsonParser.parseReader(reader).getAsJsonObject();}
        catch(java.io.IOException e) {throw new IllegalStateException(e);}
    }
    public double number(String key) {
        var value=numbers.get(key);
        if(value==null) value=Definition.CONSTANTS.get(key);
        if(value==null) throw new IllegalArgumentException("Missing skill parameter: "+key);
        return value;
    }
    public boolean enabled(int act) {return flags.getOrDefault("entries.act"+act+".enabled",false);}
    public boolean flag(String key) {return flags.getOrDefault(key,false);}
    public double optional(String key,double fallback) {return numbers.getOrDefault(key,Definition.CONSTANTS.getOrDefault(key,fallback));}
    public net.minecraft.nbt.CompoundTag save() {
        var tag=new net.minecraft.nbt.CompoundTag();tag.putInt("Version",SAVE_VERSION);
        tag.putByteArray("Snapshot",new Gson().toJson(this).getBytes(StandardCharsets.UTF_8));return tag;
    }
    public static PromisedConsortSourceConfigSnapshot read(net.minecraft.nbt.CompoundTag tag) {
        if(tag.getInt("Version")!=SAVE_VERSION) throw new IllegalArgumentException("Invalid original skill config snapshot");
        var result=new Gson().fromJson(new String(tag.getByteArray("Snapshot"),StandardCharsets.UTF_8),PromisedConsortSourceConfigSnapshot.class);
        var defaults=defaults();
        if(result==null || !result.numbers.keySet().equals(defaults.numbers.keySet()) || !result.flags.keySet().equals(defaults.flags.keySet()))
            throw new IllegalArgumentException("Incomplete original skill config snapshot");
        return result;
    }
    public String segment(int tae) {return "animations.a"+tae+".";}
    public double rangeFactor() {return number("range_percent")/100;}
    public double motionScale(int tae) {return number(segment(tae)+"range_multiplier");}
    public double range(int tae) {return motionScale(tae)*rangeFactor();}
    public PromisedConsortSourceTimeWarp warp(int tae) {
        var phases=definition().getAsJsonObject("source_phases").getAsJsonArray(Integer.toString(tae));
        String prefix=segment(tae);
        return new PromisedConsortSourceTimeWarp(phases.get(0).getAsLong(),phases.get(1).getAsLong(),phases.get(2).getAsLong(),
                Math.round(number(prefix+"windup_ticks")*50_000),Math.round(number(prefix+"active_ticks")*50_000),Math.round(number(prefix+"recovery_ticks")*50_000));
    }
    public PromisedConsortSourceBank configure(PromisedConsortSourceBank bank) {
        var warps=new LinkedHashMap<Integer,PromisedConsortSourceTimeWarp>();bank.clips().keySet().forEach(id->warps.put(id,warp(id)));
        var clips=new LinkedHashMap<>(bank.clips());var meteor=bank.requireClip(3024);
        // MC charge contact lasts through the source charge/invulnerability gate.
        // Keep the original evidence, event identity, damage and animation untouched.
        long chargeEnd=meteor.events().stream().filter(e->e.type()==0 && bank.jumpTables().get(3024).get(e.index()).id()==39)
                .mapToLong(e->e.endMicros()).filter(t->t>=0).max().orElseThrow();
        var events=meteor.events().stream().map(e->e.type()==1
                ?new com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTimeline.Event(
                    e.index(),e.type(),e.startMicros(),Math.max(e.endMicros(),chargeEnd),e.stateInfo(),e.referenceId()):e).toList();
        clips.put(3024,new com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTimeline.Clip(
                meteor.taeId(),meteor.hkxId(),meteor.durationMicros(),events));
        var dash=bank.requireClip(3031);
        long dashBegin=dash.events().stream().filter(e->e.type()==760).mapToLong(e->e.startMicros()).min().orElseThrow();
        var dashEvents=dash.events().stream().map(e->e.type()==1
                ?new PromisedConsortSourceTimeline.Event(e.index(),e.type(),dashBegin,e.endMicros(),e.stateInfo(),e.referenceId()):e).toList();
        clips.put(3031,new PromisedConsortSourceTimeline.Clip(dash.taeId(),dash.hkxId(),dash.durationMicros(),dashEvents));
        return new PromisedConsortSourceBank(clips,bank.cloneChains(),bank.motions(),bank.jumpTables(),warps);
    }
    public JsonObject override(String kind,int id,JsonObject original) {
        var result=original.deepCopy();String prefix=kind+".a"+id+".";
        Definition.CONSTANTS.forEach((key,value)->{if(key.startsWith(prefix) && original.has(key.substring(prefix.length()))) result.addProperty(key.substring(prefix.length()),value);});
        numbers.forEach((key,value)->{if(key.startsWith(prefix) && original.has(key.substring(prefix.length()))) result.addProperty(key.substring(prefix.length()),value);});
        if(kind.equals("projectiles") && result.has("dist")) result.addProperty("dist",result.get("dist").getAsDouble()*rangeFactor());
        return result;
    }
    public static PromisedConsortSourceConfigSnapshot defaults() {
        var n=new LinkedHashMap<String,Double>();var f=new LinkedHashMap<String,Boolean>();
        for(var v:definition().getAsJsonArray("fields")) {var row=v.getAsJsonObject();String path=row.get("path").getAsString();
            if(row.get("type").getAsString().equals("boolean")) f.put(path,row.get("default").getAsBoolean());else n.put(path,row.get("default").getAsDouble());}
        return new PromisedConsortSourceConfigSnapshot(n,f);
    }
}
