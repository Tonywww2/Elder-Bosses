package com.tonywww.elder_bosses.boss.promisedconsort.execution;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSkillConfigSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortActionSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortAnimationTimeline;
import com.tonywww.elder_bosses.combat.action.ActionTimeline;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class PromisedConsortActionSoundPlan {
    private PromisedConsortActionSoundPlan() {
    }

    public enum Sound {
        SLASH("heavy_slash"), STOMP("stomp"), DASH("gravity_dash"),
        HOLY("holy_echo"), REFLECTION("reflection"), METEOR("meteor_impact");

        private final String asset;

        Sound(String asset) {
            this.asset = asset;
        }

        public String asset() {
            return asset;
        }

        public String eventId() {
            return "entity.promised_consort." + asset;
        }

        public int distance() {
            return this == METEOR ? 64 : this == REFLECTION ? 24 : 48;
        }
    }

    public record Swing(int side, double arcDegrees, double elevationDegrees) {
        public Swing {
            if (side != -1 && side != 1 || !Double.isFinite(arcDegrees) || arcDegrees < 0
                    || !Double.isFinite(elevationDegrees) || Math.abs(elevationDegrees) > 90) {
                throw new IllegalArgumentException("Invalid sword swing geometry");
            }
        }
    }

    public record Cue(String key, Sound sound, float volume, float pitch, int side) {
        public Cue {
            if (key == null || key.isBlank() || sound == null || !Float.isFinite(volume) || volume < 0
                    || !Float.isFinite(pitch) || pitch <= 0 || side < -1 || side > 1) {
                throw new IllegalArgumentException("Invalid action sound cue");
            }
        }
    }

    public record TimedCue(int tick, Cue cue) {
    }

    public record BladeEvent(int index, double contactTick, List<Swing> swings) {
        public BladeEvent {
            swings = List.copyOf(swings);
        }
    }

    private record Profiles(Map<PromisedConsortActionId, List<BladeEvent>> events, Map<String, List<Swing>> clones) {
    }

    private static final class ProfileHolder {
        private static final Profiles VALUE = loadProfiles();
    }

    private static Profiles loadProfiles() {
        String resource = "/assets/elder_bosses/sounds/entity/promised_consort/sword_profiles.json";
        try (var stream = Objects.requireNonNull(PromisedConsortActionSoundPlan.class.getResourceAsStream(resource), resource);
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            var gson = new Gson();
            Map<PromisedConsortActionId, List<BladeEvent>> events = new EnumMap<>(PromisedConsortActionId.class);
            for (var action : PromisedConsortActionId.values()) {
                var values = List.of(gson.fromJson(json.getAsJsonObject("events").get(action.serializedName()), BladeEvent[].class));
                var windows = PromisedConsortAnimationTimeline.swordWindows(action);
                if (values.size() != windows.size()) throw new IllegalArgumentException("Incomplete sword audio: " + action);
                for (int index = 0; index < values.size(); index++) {
                    var event = values.get(index);
                    int sides = event.swings().stream().mapToInt(swing -> swing.side() == -1 ? 1 : 2).reduce(0, (first, second) -> first | second);
                    if (event.index() != index || event.contactTick() != windows.get(index).contactTick() || sides != windows.get(index).sides()) {
                        throw new IllegalArgumentException("Sword audio differs from blade window: " + action);
                    }
                    blades("validate", event.swings());
                }
                events.put(action, values);
            }
            Map<String, List<Swing>> clones = new java.util.HashMap<>();
            json.getAsJsonObject("clones").entrySet().forEach(entry -> clones.put(entry.getKey(), List.of(gson.fromJson(entry.getValue(), Swing[].class))));
            for (var action : PromisedConsortActionId.values()) Objects.requireNonNull(clones.get(cloneClip(action)), "Missing clone audio");
            return new Profiles(Map.copyOf(events), Map.copyOf(clones));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot load sword sound profiles", exception);
        }
    }

    public static List<TimedCue> schedule(PromisedConsortActionSnapshot action, ActionTimeline timeline,
                                          PromisedConsortSkillConfigSnapshot.Skill skill) {
        List<TimedCue> result = new ArrayList<>();
        for (BladeEvent event : ProfileHolder.VALUE.events().get(action.actionId())) {
            int tick = runtimeTick(event.contactTick(), action, timeline, skill);
            for (Cue cue : blades(Integer.toString(event.index()), event.swings())) result.add(new TimedCue(tick, cue));
        }
        switch (action.actionId()) {
                case GRAVITY_DIVE, LIGHTSPEED_SLASH, LIGHTSPEED_DASH, LIGHTSPEED_SIDE_DASH ->
                    result.add(new TimedCue(timeline.activeStartTick(0), cue("dash", Sound.DASH, 0.7F, 1)));
                case SPIRAL_ASSAULT -> result.add(new TimedCue(PromisedConsortCrossLeapPath.advance(timeline,
                    action.rangedCounter() ? skill.integerList("ranged_counter.attack_event_offsets").get(0) : 0).takeoffTick(),
                    cue("leap", Sound.DASH, 0.7F, 0.95F)));
            case LION_CLAW, LION_CLAW_DOUBLE -> result.add(new TimedCue(0, cue("leap", Sound.DASH, 0.45F, 0.9F)));
            case STARCALLER_CRY -> result.add(new TimedCue(timeline.activeStartTick(0), cue("pull", Sound.DASH, 0.65F, 0.75F)));
            case GRAVITY_METEOR -> result.add(new TimedCue(0, cue("gather", Sound.DASH, 0.45F, 0.7F)));
            case LIGHT_OF_MIQUELLA, RING_OF_LIGHT, PROMISED_CONSORT, CROSS_LEAP_COMBO, ENHANCED_EARTHHEAVE ->
                    result.add(new TimedCue(0, cue("holy_prepare", Sound.HOLY, 0.4F, 0.8F)));
            case CONSORT_METEOR -> {
                result.add(new TimedCue(0, cue("ascent", Sound.DASH, 0.8F, 0.65F)));
                int descent = timeline.stages().size() > 1 ? timeline.activeStartTick(2) : Math.max(0, timeline.activeStartTick(0) - 10);
                result.add(new TimedCue(descent, cue("descent", Sound.DASH, 0.85F, 0.85F)));
            }
            case GRAVITY_BULWARK, GRAVITY_REFLECTION, GRAVITY_REPRISAL -> {
                boolean mirror = action.actionId() == PromisedConsortActionId.GRAVITY_REFLECTION;
                result.add(new TimedCue(0, cue("defense_open", mirror ? Sound.REFLECTION : Sound.DASH, 0.65F, mirror ? 0.9F : 0.65F)));
                if (!mirror) for (int tick = timeline.activeStartTick(0); tick < timeline.activeEndTick(0); tick += 12) {
                    result.add(new TimedCue(tick, cue("defense_hold:" + tick, Sound.DASH, 0.2F, 0.65F)));
                }
            }
            default -> { }
        }
        return List.copyOf(result);
    }

    private static int runtimeTick(double contact, PromisedConsortActionSnapshot action, ActionTimeline timeline,
                                   PromisedConsortSkillConfigSnapshot.Skill skill) {
        double lower = 0, upper = timeline.totalTicks();
        for (int iteration = 0; iteration < 36; iteration++) {
            double middle = (lower + upper) * 0.5;
            if (PromisedConsortAnimationTimeline.sample(middle, action, timeline, skill) < contact) lower = middle;
            else upper = middle;
        }
        return (int) Math.ceil(upper - 0.000001);
    }

    public static Cue cue(String key, Sound sound, float volume, float pitch) {
        return new Cue(key, sound, volume, pitch, 0);
    }

    public static List<Cue> meteorLanding() {
        return List.of(cue("meteor_ground_fracture", Sound.STOMP, 0.9F, 0.7F),
                cue("meteor_landing_explosion", Sound.METEOR, 1, 0.9F));
    }

    public static boolean claim(Set<String> played, String key, long now, long start, long end) {
        return now >= start && now < end && played.add(key);
    }

    public static void dispatch(List<Cue> cues, Set<String> played, String prefix, long now, long start, long end,
                                java.util.function.Consumer<Cue> sink) {
        for (Cue cue : cues) if (claim(played, prefix + cue.key(), now, start, end)) sink.accept(cue);
    }

    public record Playback(float volume, float pitch) {
    }

    public static Playback playback(Cue cue, boolean enabled, double volume, double pitch) {
        if (!enabled || !Double.isFinite(volume) || volume <= 0 || !Double.isFinite(pitch) || pitch < 0) return new Playback(0, 1);
        return new Playback((float) Math.min(16, cue.volume() * volume), (float) Math.max(0.5, Math.min(2, cue.pitch() * pitch)));
    }

    public static final class AuxiliaryBudget {
        private long tick = Long.MIN_VALUE;
        private final Map<Sound, Integer> counts = new EnumMap<>(Sound.class);

        public boolean claim(Cue cue, long gameTick) {
            if (cue.sound() == Sound.SLASH) return true;
            if (tick != gameTick) {
                tick = gameTick;
                counts.clear();
            }
            int count = counts.getOrDefault(cue.sound(), 0);
            if (count >= 2) return false;
            counts.put(cue.sound(), count + 1);
            return true;
        }
    }

    public static String cloneClip(PromisedConsortActionId action) {
        return switch (action) {
            case LIGHTSPEED_SIDE_DASH -> "clone_side_fan_3";
            case GRAVITY_METEOR -> "clone_meteor_4";
            case STARCALLER_CRY -> "clone_starcaller_2";
            case LIGHTSPEED_DASH -> "clone_dash_4";
            case PROMISED_CONSORT, CROSS_LEAP_COMBO -> "clone_cross_return_2";
            default -> "clone_overhead_3";
        };
    }

    public static List<Cue> impact(PromisedConsortActionId action, String occurrence) {
        if (occurrence.startsWith("clone_")) {
            return blades(occurrence, ProfileHolder.VALUE.clones().get(cloneClip(action))).stream()
                    .map(cue -> new Cue(cue.key(), cue.sound(), cue.volume() * 0.55F, cue.pitch() * 1.05F, cue.side())).toList();
        }
        Cue effect = effect(occurrence);
        return effect == null ? List.of() : List.of(effect);
    }

    public static Cue effect(String occurrence) {
        if (occurrence.startsWith("echo") || occurrence.startsWith("afterglow") || occurrence.startsWith("light_")
                || occurrence.equals("holy_ring") || occurrence.equals("trail") || occurrence.equals("ring") || occurrence.equals("aftershock")) {
            return cue("effect:" + occurrence, Sound.HOLY, occurrence.equals("aftershock") ? 0.9F : 0.5F, 1.05F);
        }
        return switch (occurrence) {
            case "main" -> cue("effect:main", Sound.HOLY, 1, 0.7F);
            case "stomp" -> cue("effect:stomp", Sound.STOMP, 1, 0.9F);
            case "slam", "double", "impact", "finisher", "meteor_ground", "meteor_body" -> cue("effect:" + occurrence, Sound.STOMP, 0.85F, 0.9F);
            case "fissure", "debris" -> cue("effect:" + occurrence, Sound.STOMP, 0.45F, 1.2F);
            case "spikes" -> cue("effect:spikes", Sound.DASH, 0.4F, 0.75F);
            case "bloodflame" -> cue("effect:bloodflame", Sound.DASH, 0.5F, 1.3F);
            case "reprisal" -> cue("effect:reprisal", Sound.DASH, 1, 0.75F);
            default -> null;
        };
    }

    public static List<Cue> blades(String occurrence, List<Swing> swings) {
        if (swings.size() > 2) throw new IllegalArgumentException("At most two swords per occurrence");
        List<Cue> cues = new ArrayList<>();
        var sides = new HashSet<Integer>();
        for (Swing swing : swings) {
            if (!sides.add(swing.side())) throw new IllegalArgumentException("Duplicate sword side");
            double amplitude = Math.min(1.5, swing.arcDegrees() / 180.0);
            double elevation = Math.sin(Math.toRadians(swing.elevationDegrees()));
            double volume = Math.max(0.45, Math.min(1.05, 0.58 + 0.26 * amplitude - 0.10 * elevation));
            double pitch = Math.max(0.8, Math.min(1.2, 1.04 + 0.12 * elevation - 0.08 * amplitude));
            cues.add(new Cue("blade:" + occurrence + ":" + swing.side(), Sound.SLASH,
                    (float) (volume / Math.max(1, swings.size())), (float) pitch, swing.side()));
        }
        return List.copyOf(cues);
    }
}