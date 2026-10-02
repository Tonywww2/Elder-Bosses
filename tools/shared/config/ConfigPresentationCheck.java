import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Inspect the real registered spec without reading a game config or starting a world. */
public final class ConfigPresentationCheck {
    private static Object invoke(Object object,String method) throws Exception {
        return object.getClass().getMethod(method).invoke(object);
    }
    private static Object plain(Object value) {
        if(value instanceof UnmodifiableConfig config) {
            var out=new LinkedHashMap<String,Object>();for(var e:config.entrySet()) out.put(e.getKey(),plain(e.getValue()));return out;
        }
        if(value instanceof List<?> list) return list.stream().map(ConfigPresentationCheck::plain).toList();
        return value;
    }
    private static void collect(UnmodifiableConfig config,String prefix,List<Map<String,Object>> rows) throws Exception {
        for(var entry:config.entrySet()) {
            String key=prefix+entry.getKey();Object value=entry.getValue();
            if(value instanceof UnmodifiableConfig child) {collect(child,key+".",rows);continue;}
            var row=new LinkedHashMap<String,Object>();row.put("path",key);row.put("default",plain(invoke(value,"getDefault")));
            row.put("comment",invoke(value,"getComment"));row.put("translation_key",invoke(value,"getTranslationKey"));
            Object range=invoke(value,"getRange");if(range!=null) row.put("range",range.toString());
            rows.add(row);
        }
    }
    private static void expectValidation(Object spec,String path,Object value,boolean valid) throws Exception {
        Object valueSpec=((UnmodifiableConfig)invoke(spec,"getSpec")).getRaw(path);
        boolean actual=(Boolean)valueSpec.getClass().getMethod("test",Object.class).invoke(valueSpec,value);
        if(actual!=valid) throw new AssertionError("Compound validation changed: "+path+" for "+value);
    }
    private static void loadMemoryConfig(Object spec,com.electronwill.nightconfig.core.CommentedConfig config) throws Exception {
        if(spec.getClass().getName().startsWith("net.minecraftforge.")) {
            spec.getClass().getMethod("setConfig",com.electronwill.nightconfig.core.CommentedConfig.class).invoke(spec,config);
        } else {
            Class<?> loaded=Class.forName("net.neoforged.fml.config.IConfigSpec$ILoadedConfig");
            var constructor=Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructor(
                    com.electronwill.nightconfig.core.CommentedConfig.class,Path.class,Class.forName("net.neoforged.fml.config.ModConfig"));
            constructor.setAccessible(true);
            Object memory=constructor.newInstance(config,null,null);
            spec.getClass().getMethod("acceptConfig",loaded).invoke(spec,memory);
        }
    }
    public static void main(String[] args) throws Exception {
        Object spec=Class.forName("com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig").getField("SPEC").get(null);
        var rows=new ArrayList<Map<String,Object>>();collect((UnmodifiableConfig)invoke(spec,"getSpec"),"",rows);
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(rows)+"\n",StandardCharsets.UTF_8);
        if(args[0].startsWith("--snapshot")) {
            var config=com.electronwill.nightconfig.core.CommentedConfig.inMemory();
            spec.getClass().getMethod("correct",com.electronwill.nightconfig.core.CommentedConfig.class).invoke(spec,config);
            loadMemoryConfig(spec,config);
            Object values=Class.forName("com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig").getField("VALUES").get(null);
            var snapshots=new LinkedHashMap<String,Object>();
            for(String method:List.of("maleniaCombatSnapshot","maleniaSkillSnapshot","promisedConsortCombatSnapshot","promisedConsortSourceSnapshot")) snapshots.put(method,invoke(values,method));
            Files.writeString(Path.of(args[1]),new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create().toJson(snapshots)+"\n",StandardCharsets.UTF_8);
            if(args[0].equals("--snapshot-edit")) {
                config.set("malenia.skills.single_slash.damage.flat",4.5);
                config.set("malenia.skills.single_slash.damage.attack_ratio",0.25);
                config.set("promised_consort.stagger.distance_bands.stage1.max_distance",3.5);
                config.set("promised_consort.stagger.distance_bands.stage1.multiplier",0.6);
                spec.getClass().getMethod("afterReload").invoke(spec);
                Object damage=invoke(invoke(invoke(values,"maleniaSkillSnapshot"),"singleSlash"),"damage");
                if(!invoke(damage,"flat").equals(4.5) || !invoke(damage,"attackRatio").equals(0.25)) throw new AssertionError("Edited scalar formula did not reach skill snapshot");
                Object stagger=invoke(invoke(values,"promisedConsortCombatSnapshot"),"stagger");
                Object band=((List<?>)invoke(stagger,"distanceBands")).get(0);
                if(!invoke(band,"maximumDistance").equals(3.5) || !invoke(band,"multiplier").equals(0.6)) throw new AssertionError("Edited distance band did not reach combat snapshot");
            }
        }
        if(args[0].equals("--check")) {
            var json=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/elder_bosses/config/presentation.json"))).getAsJsonObject();
            var en=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/elder_bosses/lang/en_us.json"))).getAsJsonObject();
            var zh=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/elder_bosses/lang/zh_cn.json"))).getAsJsonObject();
            var baseline=com.google.gson.JsonParser.parseString(Files.readString(Path.of("tools/shared/config/registered_defaults.json"))).getAsJsonArray();
            var expected=new LinkedHashMap<String,com.google.gson.JsonObject>();
            for(var value:baseline) expected.put(value.getAsJsonObject().get("path").getAsString(),value.getAsJsonObject());
            if(rows.size()!=expected.size() || rows.size()!=json.getAsJsonObject("values").size()) throw new AssertionError("Registered config field count changed");
            for(String path:json.getAsJsonObject("fixed_values").keySet()) {
                if(((UnmodifiableConfig)invoke(spec,"getSpec")).getRaw(path)!=null) throw new AssertionError("Fixed implementation field is still editable: "+path);
            }
            for(var row:rows) {
                String path=(String)row.get("path"),key="config.elder_bosses."+path,comment=(String)row.get("comment");
                if(!key.equals(row.get("translation_key")) || !en.has(key) || !zh.has(key) || !en.has(key+".tooltip") || !zh.has(key+".tooltip")) throw new AssertionError("Missing translated value: "+path);
                if(comment==null || !comment.contains("English:") || !comment.contains("中文：")) throw new AssertionError("Missing bilingual TOML comment: "+path);
                if(!json.getAsJsonObject("values").has(path)) throw new AssertionError("Missing presentation metadata: "+path);
                var label=json.getAsJsonObject("values").getAsJsonObject(path);
                for(String language:List.of("zh_name","en_name","zh_help","en_help")) {
                    String text=label.get(language).getAsString();
                    if(text.contains("（") || text.contains("(") || text.contains("TAE") || text.contains("BulletParam") || text.contains("SpEffect")) throw new AssertionError("Verbose/source vocabulary remains: "+path);
                }
                Object defaultValue=row.get("default");
                if(defaultValue instanceof Map<?,?> || defaultValue instanceof List<?> list && list.stream().anyMatch(Map.class::isInstance)) throw new AssertionError("Configured cannot edit compound leaf: "+path);
                var original=expected.get(path);
                if(original==null || !new com.google.gson.Gson().toJsonTree(row.get("default")).equals(original.get("default"))) throw new AssertionError("Default changed: "+path);
                String range=(String)row.get("range");
                if(!java.util.Objects.equals(range,original.has("range")?original.get("range").getAsString():null)) throw new AssertionError("Range changed: "+path);
            }
            for(String group:json.getAsJsonObject("groups").keySet()) {
                String key="config.elder_bosses."+group;
                Object actual=spec.getClass().getMethod("getLevelTranslationKey",List.class).invoke(spec,List.of(group.split("\\.")));
                if(!key.equals(actual) || !en.has(key) || !zh.has(key) || !en.has(key+".tooltip") || !zh.has(key+".tooltip")) throw new AssertionError("Missing translated group: "+group);
            }
            for(Object invalid:List.of(-1.0,2049.0,Double.NaN,"bad")) expectValidation(spec,"malenia.skills.single_slash.damage.flat",invalid,false);
            for(Object valid:List.of(0.0,2048.0)) expectValidation(spec,"malenia.skills.single_slash.damage.flat",valid,true);
            expectValidation(spec,"malenia.skills.upward_combo.damage.stage1.attack_ratio",10.0,true);
            expectValidation(spec,"malenia.skills.upward_combo.damage.stage1.attack_ratio",11.0,false);
            for(String boss:List.of("malenia","promised_consort")) {
                String path=boss+".stagger.distance_bands.stage1.max_distance";
                expectValidation(spec,path,-1.0,true);expectValidation(spec,path,-0.5,false);expectValidation(spec,path,Double.POSITIVE_INFINITY,false);
            }
            expectValidation(spec,"malenia.stagger.distance_bands.stage1.max_distance",2049.0,false);
            expectValidation(spec,"promised_consort.stagger.distance_bands.stage1.max_distance",2049.0,true);
            // Write only to an isolated check directory, exercising the native comment correction.
            var config=com.electronwill.nightconfig.core.CommentedConfig.inMemory();
            spec.getClass().getMethod("correct",com.electronwill.nightconfig.core.CommentedConfig.class).invoke(spec,config);
            var writer=new java.io.StringWriter();new com.electronwill.nightconfig.toml.TomlWriter().write(config,writer);
            String toml=writer.toString();
            if(!toml.contains("中文：") || !toml.contains("English:")) throw new AssertionError("Native TOML writer omitted bilingual comments");
            Files.writeString(Path.of(args[1]+".toml"),toml,StandardCharsets.UTF_8);
            if(rows.stream().anyMatch(row->((String)row.get("path")).startsWith("promised_consort.rewards.")))
                throw new AssertionError("Rewards must be controlled by entity loot tables");
            loadMemoryConfig(spec,config);
            for(String path:List.of("malenia.debug.state_output","malenia.debug.action_broadcast","promised_consort.debug.action_broadcast")) config.set(path,true);
            spec.getClass().getMethod("afterReload").invoke(spec);
            Object values=Class.forName("com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig").getField("VALUES").get(null);
            for(String method:List.of("maleniaDebugStateOutput","maleniaDebugActionBroadcast","promisedConsortDebugActionBroadcast"))
                if(!invoke(values,method).equals(true)) throw new AssertionError("Debug switch is not editable: "+method);
            Object combat=invoke(values,"promisedConsortCombatSnapshot");
            if(!invoke(invoke(combat,"meteor"),"repeatMode").equals("cooldown_forced")) throw new AssertionError("Meteor must default to repeating after its cooldown");
            var original=(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSourceConfigSnapshot)invoke(values,"promisedConsortSourceSnapshot");
            if(original.number("hit_detection.segment_immunity_ticks")!=10 || !original.flag("entries.act14.invulnerable")) throw new AssertionError("Current contact and light defaults are missing");
            config.set("promised_consort.skills.hit_detection.segment_immunity_ticks",24.0);
            config.set("promised_consort.skills.entries.light_of_miquella.invulnerable",false);
            config.set("promised_consort.skills.projectiles.a205220400.flight_height",2.0);
            spec.getClass().getMethod("afterReload").invoke(spec);
            var changed=(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSourceConfigSnapshot)invoke(values,"promisedConsortSourceSnapshot");
            if(changed.number("hit_detection.segment_immunity_ticks")!=24 || changed.flag("entries.act14.invulnerable") || changed.number("projectiles.a205220400.flight_height")!=2) throw new AssertionError("Edited current fields did not reach the encounter snapshot");
            if(original.number("hit_detection.segment_immunity_ticks")!=10 || !original.flag("entries.act14.invulnerable")) throw new AssertionError("Reload changed an existing frozen snapshot");
        }
        System.out.println("Registered config presentation "+args[0]+": "+rows.size()+" values; no game config or world opened.");
    }
}
