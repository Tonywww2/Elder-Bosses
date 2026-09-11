package com.tonywww.elder_bosses.boss.promisedconsort.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortActionRuntime;
import com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionExecutor;
import com.tonywww.elder_bosses.boss.promisedconsort.dialogue.PromisedConsortDialogueController;
import com.tonywww.elder_bosses.combat.hit.PerTargetHitCounter;
import com.tonywww.elder_bosses.combat.state.StaggerTracker;
import net.minecraft.nbt.CompoundTag;

import java.util.Objects;
import java.util.Optional;
import java.util.Arrays;
import java.util.List;

public final class PromisedConsortConfigNbt {
    private static final String COMBAT_KEY = "Combat";
    private static final String SKILLS_KEY = "Skills";
    private static final Gson GSON = new GsonBuilder()
            .serializeSpecialFloatingPointValues()
            .create();

    private PromisedConsortConfigNbt() {
    }

    public static CompoundTag write(
            PromisedConsortCombatConfigSnapshot combat,
            PromisedConsortSkillConfigSnapshot skills
    ) {
        CompoundTag tag = new CompoundTag();
        tag.putString(COMBAT_KEY, GSON.toJson(Objects.requireNonNull(combat, "combat")));
        tag.putString(SKILLS_KEY, GSON.toJson(Objects.requireNonNull(skills, "skills")));
        return tag;
    }

    public static Optional<EncounterConfig> read(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        if (!tag.contains(COMBAT_KEY) || !tag.contains(SKILLS_KEY)) {
            return Optional.empty();
        }
        try {
            PromisedConsortCombatConfigSnapshot combat = GSON.fromJson(
                    tag.getString(COMBAT_KEY),
                    PromisedConsortCombatConfigSnapshot.class
            );
            JsonElement serializedSkills = JsonParser.parseString(tag.getString(SKILLS_KEY));
            addMissingSkillTunings(serializedSkills);
            PromisedConsortSkillConfigSnapshot skills = GSON.fromJson(
                    serializedSkills,
                    PromisedConsortSkillConfigSnapshot.class
            );
            return Optional.of(new EncounterConfig(combat, skills));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static void addMissingSkillTunings(JsonElement serializedSkills) {
        if (!serializedSkills.isJsonObject()) {
            return;
        }
        JsonElement skills = serializedSkills.getAsJsonObject().get("skills");
        if (skills == null || !skills.isJsonObject()) {
            return;
        }
        for (JsonElement serializedSkill : skills.getAsJsonObject().asMap().values()) {
            if (!serializedSkill.isJsonObject()) {
                continue;
            }
            JsonObject skill = serializedSkill.getAsJsonObject();
            if (!skill.has("castSpeedMultiplier")) {
                skill.addProperty("castSpeedMultiplier", 1.0);
            }
            if (!skill.has("rangeMultiplier")) {
                skill.addProperty("rangeMultiplier", 1.0);
            }
        }
    }

    public record EncounterConfig(
            PromisedConsortCombatConfigSnapshot combat,
            PromisedConsortSkillConfigSnapshot skills
    ) {
        public EncounterConfig {
            Objects.requireNonNull(combat, "combat");
            Objects.requireNonNull(skills, "skills");
        }
    }

    public static String writeAction(PromisedConsortActionRuntime.PersistentState state) {
        return GSON.toJson(Objects.requireNonNull(state, "state"));
    }

    public static Optional<PromisedConsortActionRuntime.PersistentState> readAction(String value) {
        return read(value, PromisedConsortActionRuntime.PersistentState.class);
    }

    public static String writeDialogue(PromisedConsortDialogueController.PersistentState state) {
        return GSON.toJson(Objects.requireNonNull(state, "state"));
    }

    public static Optional<PromisedConsortDialogueController.PersistentState> readDialogue(
            String value
    ) {
        return read(value, PromisedConsortDialogueController.PersistentState.class);
    }

    public static String writeActionExecutor(PromisedConsortActionExecutor.PersistentState state) {
        return GSON.toJson(Objects.requireNonNull(state, "state"));
    }

    public static Optional<PromisedConsortActionExecutor.PersistentState> readActionExecutor(
            String value
    ) {
        return read(value, PromisedConsortActionExecutor.PersistentState.class);
    }

    public static String writeHitCounts(List<PerTargetHitCounter.PersistentCount> counts) {
        return GSON.toJson(counts.toArray(PerTargetHitCounter.PersistentCount[]::new));
    }

    public static List<PerTargetHitCounter.PersistentCount> readHitCounts(String value) {
        return read(value, PerTargetHitCounter.PersistentCount[].class)
                .map(Arrays::asList)
                .map(List::copyOf)
                .orElseGet(List::of);
    }

    public static String writeStagger(StaggerTracker.PersistentState state) {
        return GSON.toJson(Objects.requireNonNull(state, "state"));
    }

    public static Optional<StaggerTracker.PersistentState> readStagger(String value) {
        return read(value, StaggerTracker.PersistentState.class);
    }

    public static String writeHazards(
            List<PromisedConsortActionExecutor.PersistentHazard> hazards
    ) {
        return GSON.toJson(hazards.toArray(
                PromisedConsortActionExecutor.PersistentHazard[]::new
        ));
    }

    public static List<PromisedConsortActionExecutor.PersistentHazard> readHazards(String value) {
        return read(value, PromisedConsortActionExecutor.PersistentHazard[].class)
                .map(Arrays::asList)
                .map(List::copyOf)
                .orElseGet(List::of);
    }

    private static <T> Optional<T> read(String value, Class<T> type) {
        try {
            return Optional.ofNullable(GSON.fromJson(value, type));
        } catch (JsonParseException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
