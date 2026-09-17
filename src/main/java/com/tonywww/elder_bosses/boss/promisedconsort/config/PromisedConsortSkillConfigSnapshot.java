package com.tonywww.elder_bosses.boss.promisedconsort.config;

import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.combat.action.SkillTuning;
import com.tonywww.elder_bosses.combat.damage.DamageFormula;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record PromisedConsortSkillConfigSnapshot(Map<PromisedConsortActionId, Skill> skills) {
    public PromisedConsortSkillConfigSnapshot {
        Objects.requireNonNull(skills, "skills");
        EnumMap<PromisedConsortActionId, Skill> copy =
                new EnumMap<>(PromisedConsortActionId.class);
        copy.putAll(skills);
        if (copy.keySet().contains(PromisedConsortActionId.LION_CLAW_DOUBLE)) {
            throw new IllegalArgumentException("lion_claw_double must share lion_claw configuration");
        }
        for (PromisedConsortActionId actionId : PromisedConsortActionId.values()) {
            if (actionId != PromisedConsortActionId.LION_CLAW_DOUBLE && !copy.containsKey(actionId)) {
                throw new IllegalArgumentException("missing skill configuration for " + actionId);
            }
            if(actionId!=PromisedConsortActionId.LION_CLAW_DOUBLE) copy.get(actionId).validateRanged(actionId);
        }
        skills = Collections.unmodifiableMap(copy);
    }

    public Skill get(PromisedConsortActionId actionId) {
        Objects.requireNonNull(actionId, "actionId");
        PromisedConsortActionId key = actionId == PromisedConsortActionId.LION_CLAW_DOUBLE
                ? PromisedConsortActionId.LION_CLAW
                : actionId;
        Skill skill = skills.get(key);
        if (skill == null) {
            throw new IllegalArgumentException("missing skill configuration for " + actionId);
        }
        return skill;
    }

    public record Skill(
            boolean enabled,
            double weight,
            int cooldownTicks,
            boolean hyperArmorActive,
            double castSpeedMultiplier,
            double rangeMultiplier,
            Map<String, Double> numbers,
            Map<String, Integer> integers,
            Map<String, String> strings,
            Map<String, List<Integer>> integerLists,
            Map<String, DamageFormula> damage,
            Map<String, List<DamageFormula>> damageLists,
            Map<String, List<String>> idLists
    ) {
        public Skill(boolean enabled, double weight, int cooldownTicks, boolean hyperArmorActive, double castSpeedMultiplier,
                double rangeMultiplier, Map<String, Double> numbers, Map<String, Integer> integers, Map<String, String> strings,
                Map<String, List<Integer>> integerLists, Map<String, DamageFormula> damage, Map<String, List<DamageFormula>> damageLists) {
            this(enabled,weight,cooldownTicks,hyperArmorActive,castSpeedMultiplier,rangeMultiplier,numbers,integers,strings,integerLists,damage,damageLists,Map.of());
        }
        public Skill {
            if (!Double.isFinite(weight) || weight < 0.0) {
                throw new IllegalArgumentException("weight must be finite and non-negative");
            }
            if (cooldownTicks < 0) {
                throw new IllegalArgumentException("cooldownTicks must be non-negative");
            }
            new SkillTuning(rangeMultiplier);
            castSpeedMultiplier = 1.0;
            numbers = immutableMap(numbers);
            integers = immutableMap(integers);
            strings = immutableMap(strings);
            integerLists = immutableListMap(integerLists);
            damage = immutableMap(damage);
            damageLists = immutableListMap(damageLists);
            idLists = idLists == null ? Map.of() : immutableListMap(idLists);
        }

        public SkillTuning tuning() {
            return new SkillTuning(rangeMultiplier);
        }

        public boolean hasRangedCounter() {
            return integers.getOrDefault("ranged_counter.enabled", 0) == 1;
        }

        private void validateRanged(PromisedConsortActionId action) {
            if (action == PromisedConsortActionId.CROSS_LEAP_COMBO) {
                checked("leap_distance", 0, 32);
                checked("leap_height", 0, 4);
                for (String key : List.of("opening_range", "spin_range", "finisher_range")) checked(key, 0, 256);
                for (String key : List.of("windup_ticks", "active_ticks", "recovery_ticks")) {
                    if (integerList(key).size() != 8) throw new IllegalArgumentException("Cross-leap requires eight stages");
                }
                if (integerList("windup_ticks").get(0) < 4 || integerList("windup_ticks").get(4) < 4) {
                    throw new IllegalArgumentException("Cross-leap flight windups require at least four ticks");
                }
            }
            if (action == PromisedConsortActionId.GRAVITY_METEOR) {
                for (String key : List.of("sword_range", "body_range")) {
                    double value = numbers.getOrDefault(key, 4.5);
                    if (!Double.isFinite(value) || value < 0 || value > 256) throw new IllegalArgumentException("Invalid meteor contact range");
                }
            }
            if (action == PromisedConsortActionId.LIGHT_OF_MIQUELLA) {
                double height = numbers.getOrDefault("flight_height", 12.0);
                int rise = integers.getOrDefault("flight_ascent_ticks", 16), fall = integers.getOrDefault("flight_descent_ticks", 20);
                if (!Double.isFinite(height) || height < 0 || height > 32 || rise < 1 || rise > 1200 || fall < 1 || fall > 1200) {
                    throw new IllegalArgumentException("Invalid holy flight settings");
                }
            }
            if(hasRangedCounter()) {
                int count=action==PromisedConsortActionId.LIGHTSPEED_DASH?6:action==PromisedConsortActionId.LIGHTSPEED_SIDE_DASH?4:2;
                long total=0;
                for(String key:List.of("windup_ticks","active_ticks","recovery_ticks","attack_event_offsets")) {
                    if(integerList("ranged_counter."+key).size()!=count) throw new IllegalArgumentException("Invalid ranged component count: "+action);
                }
                for(int index=0;index<count;index++) {
                    int windup=integerList("ranged_counter.windup_ticks").get(index),active=integerList("ranged_counter.active_ticks").get(index),
                        recovery=integerList("ranged_counter.recovery_ticks").get(index),offset=integerList("ranged_counter.attack_event_offsets").get(index);
                    if(windup<0||active<1||recovery<0||offset<0||offset>=active) throw new IllegalArgumentException("Invalid ranged event or stage: "+action);
                    total+=(long)windup+active+recovery;
                }
                if(total>com.tonywww.elder_bosses.network.NetworkLimits.MAX_TICKS) throw new IllegalArgumentException("Ranged action exceeds tick limit");
                checked("ranged_counter.max_forward_per_tick",0.01,8);
                checked("ranged_counter.max_forward_distance",0,256);
                checked("ranged_counter.turn_rate_degrees_per_tick",0,180);
                checked("ranged_counter.min_target_distance",0,256);
                checked("ranged_counter.max_target_distance",number("ranged_counter.min_target_distance"),256);
                if(integer("ranged_counter.minimum_warning_ticks")<1 || integer("ranged_counter.target_lock_lead_ticks")<1
                        || integerList("ranged_counter.windup_ticks").get(0)<integer("ranged_counter.minimum_warning_ticks")) {
                    throw new IllegalArgumentException("Ranged preparation must include its warning");
                }
            }
            if(!action.rangedDefense()) return;
                if(action!=PromisedConsortActionId.GRAVITY_REPRISAL && (integer("windup_ticks")<0 || integer("active_ticks")<1
                    || integer("recovery_ticks")<0 || (long)integer("windup_ticks")+integer("active_ticks")+integer("recovery_ticks")
                    >com.tonywww.elder_bosses.network.NetworkLimits.MAX_TICKS)) throw new IllegalArgumentException("Invalid defense timeline");
            var phases=integerList("phases");
            if(phases.isEmpty() || phases.stream().anyMatch(phase->phase!=1 && phase!=2)) throw new IllegalArgumentException("Defense phases must be 1 or 2");
            checked("defense_arc_degrees",0.01,360);
            checked("min_target_distance",0,256);
            checked("max_target_distance",number("min_target_distance"),256);
            if(action!=PromisedConsortActionId.GRAVITY_REFLECTION) {
                checked("melee_damage_multiplier",0,4096);
                if(integer("trigger.threat_window_ticks")<1) throw new IllegalArgumentException("Threat window must be positive");
            }
            if(action==PromisedConsortActionId.GRAVITY_BULWARK) checked("ranged_damage_reduction",0,1);
            if(action==PromisedConsortActionId.GRAVITY_REFLECTION) {
                checked("intercept_radius",0.01,256); checked("speed_multiplier",0.01,8); checked("max_projectile_speed",0.01,8);
                for(String key:List.of("remaining_lifetime_cap_ticks","max_reflections_per_cast","max_reflections_per_tick",
                        "max_scanned_projectiles_per_tick","trigger.minimum_incoming_projectiles","trigger.incoming_prediction_ticks")) {
                    if(integer(key)<1) throw new IllegalArgumentException("Reflection budget must be positive: "+key);
                }
                for(String id:idLists.getOrDefault("supported_entity_ids",List.of())) {
                    if(!List.of("minecraft:arrow","minecraft:spectral_arrow").contains(id)) throw new IllegalArgumentException("No safe reflection adapter: "+id);
                }
            }
            if(action==PromisedConsortActionId.GRAVITY_REPRISAL) {
                checked("absorption.full_charge_damage",0.01,1000000);
                checked("absorption.minimum_return_multiplier",0,4096);
                checked("absorption.maximum_return_multiplier",number("absorption.minimum_return_multiplier"),4096);
                for(String key:List.of("length","width","height")) checked("counterattack."+key,0.01,256);
                long total=0;
                for(String key:List.of("windup_ticks","active_ticks","recovery_ticks")) {
                    var values=integerList("components."+key);
                    if(values.size()!=3 || values.stream().anyMatch(value->value<(key.equals("active_ticks")?1:0))) throw new IllegalArgumentException("Invalid reprisal components");
                    for(int value:values) total+=value;
                }
                if(total>com.tonywww.elder_bosses.network.NetworkLimits.MAX_TICKS || integer("counterattack.max_hits_per_target")!=1) {
                    throw new IllegalArgumentException("Reprisal must have one hit per target and a bounded timeline");
                }
                int warning=integerList("components.windup_ticks").get(1)+integerList("components.active_ticks").get(1)
                        +integerList("components.recovery_ticks").get(1)+integerList("components.windup_ticks").get(2);
                if(integer("counterattack.minimum_warning_ticks")<1 || warning<integer("counterattack.minimum_warning_ticks")) throw new IllegalArgumentException("Reprisal warning too short");
            }
        }

        private void checked(String key,double minimum,double maximum) {
            double value=number(key);
            if(!Double.isFinite(value)||value<minimum||value>maximum) throw new IllegalArgumentException("Invalid ranged skill value: "+key);
        }

        public Skill rangedVariant() {
            if (!hasRangedCounter()) throw new IllegalStateException("Skill has no enabled ranged variant");
            Map<String, List<Integer>> stages = new java.util.HashMap<>(integerLists);
            for (String field : List.of("windup_ticks", "active_ticks", "recovery_ticks")) {
                stages.put(field, integerList("ranged_counter." + field));
            }
            return new Skill(enabled, weight, cooldownTicks, hyperArmorActive, 1, rangeMultiplier,
                    numbers, integers, strings, stages, damage, damageLists, idLists);
        }

        public double number(String key) {
            Double value = numbers.get(key);
            if (value == null) {
                throw new IllegalArgumentException("missing numeric skill value: " + key);
            }
            return value;
        }

        public int integer(String key) {
            Integer value = integers.get(key);
            if (value == null) {
                throw new IllegalArgumentException("missing integer skill value: " + key);
            }
            return value;
        }

        public List<Integer> integerList(String key) {
            List<Integer> value = integerLists.get(key);
            if (value == null) {
                throw new IllegalArgumentException("missing integer-list skill value: " + key);
            }
            return value;
        }

        public String string(String key) {
            String value = strings.get(key);
            if (value == null) {
                throw new IllegalArgumentException("missing string skill value: " + key);
            }
            return value;
        }

        public DamageFormula damage(String key) {
            DamageFormula value = damage.get(key);
            if (value == null) {
                throw new IllegalArgumentException("missing damage skill value: " + key);
            }
            return value;
        }

        public List<DamageFormula> damageList(String key) {
            List<DamageFormula> value = damageLists.get(key);
            if (value == null) {
                throw new IllegalArgumentException("missing damage-list skill value: " + key);
            }
            return value;
        }

        private static <T> Map<String, T> immutableMap(Map<String, T> values) {
            return Collections.unmodifiableMap(Map.copyOf(Objects.requireNonNull(values, "values")));
        }

        private static <T> Map<String, List<T>> immutableListMap(Map<String, List<T>> values) {
            Objects.requireNonNull(values, "values");
            Map<String, List<T>> copy = new java.util.LinkedHashMap<>();
            values.forEach((key, value) -> copy.put(key, List.copyOf(value)));
            return Collections.unmodifiableMap(copy);
        }
    }
}
