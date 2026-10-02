package com.tonywww.elder_bosses.platforms.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
//? if forge {
import net.minecraftforge.common.ForgeConfigSpec;
//?} else {
/*import net.neoforged.neoforge.common.ModConfigSpec;
*///?}

/** Adds server-safe bilingual comments and native Configured translation metadata. */
public final class LocalizedConfigBuilder {
    private static final JsonObject PRESENTATION = loadPresentation();
    private final List<String> path = new ArrayList<>();
    private final List<Boolean> pushed = new ArrayList<>();
    //? if forge {
    private final ForgeConfigSpec.Builder delegate;
    public LocalizedConfigBuilder(ForgeConfigSpec.Builder delegate) {
    //?} else {
    /*private final ModConfigSpec.Builder delegate;
    public LocalizedConfigBuilder(ModConfigSpec.Builder delegate) {
    *///?}
        this.delegate = delegate;
    }

    private static JsonObject loadPresentation() {
        try (var input = Objects.requireNonNull(LocalizedConfigBuilder.class.getResourceAsStream(
                "/assets/elder_bosses/config/presentation.json"), "Missing config presentation")) {
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot read config presentation", exception);
        }
    }

    private void describe(String kind, String key) {
        String fullPath = fullPath(key);
        JsonObject row = Objects.requireNonNull(PRESENTATION.getAsJsonObject(kind).getAsJsonObject(fullPath),
                "Missing config presentation: " + fullPath);
        delegate.translation("config.elder_bosses." + fullPath);
        delegate.comment("中文：" + row.get("zh_name").getAsString() + "。" + row.get("zh_help").getAsString(),
                "English: " + row.get("en_name").getAsString() + ". " + row.get("en_help").getAsString());
    }
    private String fullPath(String key) {return path.isEmpty()?key:String.join(".",path)+"."+key;}
    private boolean fixed(String key) {return PRESENTATION.getAsJsonObject("fixed_values").has(fullPath(key));}
    @SuppressWarnings("unchecked")
    private <T> T currentDefault(String key,T authored) {
        var row=Objects.requireNonNull(PRESENTATION.getAsJsonObject("values").getAsJsonObject(fullPath(key)),"Missing current default: "+fullPath(key));
        return (T) typed(row.get("default"),authored);
    }
    private static Object typed(com.google.gson.JsonElement value,Object sample) {
        if(sample instanceof Boolean) return value.getAsBoolean();
        if(sample instanceof Integer) return value.getAsInt();
        if(sample instanceof Long) return value.getAsLong();
        if(sample instanceof Float) return value.getAsFloat();
        if(sample instanceof Double) return value.getAsDouble();
        if(sample instanceof String) return value.getAsString();
        if(sample instanceof List<?> list) {
            var result=new ArrayList<>();
            for(var item:value.getAsJsonArray()) {
                Object element=list.isEmpty()?(item.getAsJsonPrimitive().isNumber()?0.0:item.getAsJsonPrimitive().isBoolean()?false:""):list.get(0);
                result.add(typed(item,element));
            }
            return List.copyOf(result);
        }
        throw new IllegalArgumentException("Unsupported current default type: "+sample.getClass());
    }

    public LocalizedConfigBuilder push(String key) {
        boolean visible=PRESENTATION.getAsJsonObject("groups").has(fullPath(key));
        if(visible) {describe("groups",key);delegate.push(key);}
        pushed.add(visible);
        path.add(key);
        return this;
    }

    public LocalizedConfigBuilder pop() { return pop(1); }
    public LocalizedConfigBuilder pop(int count) {
        int actual=0;for(int i=pushed.size()-count;i<pushed.size();i++) if(pushed.get(i)) actual++;
        if(actual>0) delegate.pop(actual);
        pushed.subList(pushed.size()-count,pushed.size()).clear();
        path.subList(path.size() - count, path.size()).clear();
        return this;
    }

    // English source notes are represented by the shared, translated presentation catalog.
    public LocalizedConfigBuilder comment(String... comments) { return this; }
    public LocalizedConfigBuilder worldRestart() { delegate.worldRestart(); return this; }

    public <T> Supplier<T> define(String key, T value) {
        if(fixed(key)) return ()->value;
        describe("values", key);
        return delegate.define(key, currentDefault(key,value));
    }
    public <T> Supplier<T> define(String key, T value, Predicate<Object> validator) {
        if(fixed(key)) return ()->value;
        if (value instanceof UnmodifiableConfig compound) {
            return compound(key, compound, validator);
        }
        describe("values", key);
        return delegate.define(key, currentDefault(key,value), validator);
    }

    /** Configured supports scalar leaves; compose the existing runtime value from those leaves. */
    @SuppressWarnings("unchecked")
    private <T> Supplier<T> compound(String key, UnmodifiableConfig defaults, Predicate<Object> validator) {
        push(key);
        var fields = new LinkedHashMap<String, Supplier<?>>();
        for (var entry : defaults.entrySet()) {
            String leaf = entry.getKey();
            fields.put(leaf, define(leaf, entry.getValue(), candidate -> {
                Config proposed = Config.inMemory();
                for (var original : defaults.entrySet()) proposed.set(original.getKey(), original.getValue());
                proposed.set(leaf, candidate);
                return validator.test(proposed);
            }));
        }
        pop();
        return () -> {
            Config result = Config.inMemory();
            fields.forEach((leaf, supplier) -> result.set(leaf, supplier.get()));
            return (T) result;
        };
    }
    public Supplier<Integer> defineInRange(String key, int value, int min, int max) {
        if(fixed(key)) return ()->value;
        describe("values", key);
        return delegate.defineInRange(key, currentDefault(key,value), min, max);
    }
    public Supplier<Double> defineInRange(String key, double value, double min, double max) {
        if(fixed(key)) return ()->value;
        describe("values", key);
        return delegate.defineInRange(key, currentDefault(key,value), min, max);
    }
    public <T> Supplier<List<? extends T>> defineList(String key, List<? extends T> value, Predicate<Object> validator) {
        if(fixed(key)) return ()->value;
        if (!value.isEmpty() && value.stream().allMatch(UnmodifiableConfig.class::isInstance)) {
            push(key);
            List<Supplier<T>> stages = new ArrayList<>();
            for (int i = 0; i < value.size(); i++) {
                stages.add(compound("stage" + (i + 1), (UnmodifiableConfig) value.get(i), validator));
            }
            pop();
            return () -> stages.stream().map(Supplier::get).toList();
        }
        describe("values", key);
        return delegate.defineList(key, currentDefault(key,value), validator);
    }
    public <T> Supplier<List<? extends T>> defineListAllowEmpty(List<String> keys,
            Supplier<List<? extends T>> value, Predicate<Object> validator) {
        if(fixed(String.join(".",keys))) return value;
        describe("values", String.join(".", keys));
        return delegate.defineListAllowEmpty(keys, ()->currentDefault(String.join(".",keys),value.get()), validator);
    }
}
