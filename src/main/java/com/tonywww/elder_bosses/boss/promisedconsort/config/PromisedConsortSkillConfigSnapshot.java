package com.tonywww.elder_bosses.boss.promisedconsort.config;

import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
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
            Map<String, Double> numbers,
            Map<String, Integer> integers,
            Map<String, String> strings,
            Map<String, List<Integer>> integerLists,
            Map<String, DamageFormula> damage,
            Map<String, List<DamageFormula>> damageLists
    ) {
        public Skill {
            if (!Double.isFinite(weight) || weight < 0.0) {
                throw new IllegalArgumentException("weight must be finite and non-negative");
            }
            if (cooldownTicks < 0) {
                throw new IllegalArgumentException("cooldownTicks must be non-negative");
            }
            numbers = immutableMap(numbers);
            integers = immutableMap(integers);
            strings = immutableMap(strings);
            integerLists = immutableListMap(integerLists);
            damage = immutableMap(damage);
            damageLists = immutableListMap(damageLists);
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
