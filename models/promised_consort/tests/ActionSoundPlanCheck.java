import com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionSoundPlan;
import com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionSoundPlan.Swing;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortAnimationTimeline;
import java.util.List;

public class ActionSoundPlanCheck {
    public static void main(String[] arguments) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var bootstrap = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrap.setAccessible(true);
        bootstrap.setBoolean(null, true);
        net.minecraft.core.registries.BuiltInRegistries.bootStrap();
        var horizontal = PromisedConsortActionSoundPlan.blades("cut", List.of(new Swing(-1, 120, 0))).get(0);
        var descending = PromisedConsortActionSoundPlan.blades("cut", List.of(new Swing(-1, 120, -70))).get(0);
        var rising = PromisedConsortActionSoundPlan.blades("cut", List.of(new Swing(-1, 120, 70))).get(0);
        require(descending.volume() > horizontal.volume() && horizontal.volume() > rising.volume(), "Direction does not change volume");
        require(descending.pitch() < horizontal.pitch() && horizontal.pitch() < rising.pitch(), "Direction does not change playback rate");
        var narrow = PromisedConsortActionSoundPlan.blades("cut", List.of(new Swing(-1, 40, 0))).get(0);
        require(narrow.volume() < horizontal.volume() && narrow.pitch() > horizontal.pitch(), "Arc does not change sound profile");
        var dual = PromisedConsortActionSoundPlan.blades("cross", List.of(new Swing(-1, 120, -45), new Swing(1, 120, -45)));
        require(dual.size() == 2 && !dual.get(0).key().equals(dual.get(1).key()), "Dual swords must emit two independent cues");
        var next = PromisedConsortActionSoundPlan.blades("cross_2", List.of(new Swing(-1, 120, -45), new Swing(1, 120, -45)));
        require(!dual.get(0).key().equals(next.get(0).key()), "Consecutive attacks share a sound identity");
        require(dual.stream().mapToDouble(cue -> cue.volume()).sum() <= 1.05, "Dual swords double the mix ceiling");
        for (double arc : new double[]{0, 45, 180, 360, 720}) for (double angle : new double[]{-90, -30, 0, 30, 90}) {
            var cue = PromisedConsortActionSoundPlan.blades("cut", List.of(new Swing(1, arc, angle))).get(0);
            require(cue.volume() >= 0.45 && cue.volume() <= 1.05 && cue.pitch() >= 0.8 && cue.pitch() <= 1.2, "Unsafe sound profile");
        }
        var exported = new com.google.gson.JsonObject();
        int contacts = 0, blades = 0;
        for (var action : PromisedConsortActionId.values()) {
            var windows = PromisedConsortAnimationTimeline.swordWindows(action);
            var values = new com.google.gson.JsonArray();
            int previous = -1;
            for (var window : windows) {
                require(window.contactTick() > previous, "Sword contacts are not unique and ordered: " + action);
                require(window.sides() == PromisedConsortAnimationTimeline.swordSides(action, window.contactTick()), "Visual and audio sword sides differ");
                require(window.startTick() < window.contactTick() && window.endTick() > window.contactTick(), "Invalid sword window");
                previous = window.contactTick();
                contacts++;
                blades += Integer.bitCount(window.sides());
                var value = new com.google.gson.JsonObject();
                value.addProperty("contact_tick", window.contactTick());
                value.addProperty("start_tick", window.startTick());
                value.addProperty("end_tick", window.endTick());
                value.addProperty("sides", window.sides());
                values.add(value);
            }
            exported.add(action.serializedName(), values);
        }
        require(PromisedConsortAnimationTimeline.swordWindows(PromisedConsortActionId.L_COMBO_CROSS).size() == 3, "Three-part combo lost a cut");
        require(PromisedConsortAnimationTimeline.swordWindows(PromisedConsortActionId.R_COMBO_TEMPEST).size() == 5, "Tempest lost a cut");
        var newWindows = PromisedConsortAnimationTimeline.swordWindows(PromisedConsortActionId.CROSS_LEAP_COMBO);
        require(newWindows.size() == 5 && newWindows.stream().mapToInt(window -> Integer.bitCount(window.sides())).sum() == 8,
            "Cross-leap must have five slash contacts and eight per-blade cues");
        if (List.of(arguments).contains("--export-windows")) java.nio.file.Files.writeString(
                java.nio.file.Path.of("models/promised_consort/audio/sword_windows.json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(exported) + "\n");
        System.out.println("Authoritative sword windows: " + contacts + " contacts / " + blades + " individual blades");
        if (List.of(arguments).contains("--export-windows")) return;
        checkConfiguredSchedules();
        checkPersistenceAndResources();
        System.out.println("Per-blade identity, dual-sword counts and angle-dependent volume/pitch checks passed");
    }

    private static void checkConfiguredSchedules() {
        var builder = new net.minecraftforge.common.ForgeConfigSpec.Builder();
        var values = new com.tonywww.elder_bosses.platforms.config.PromisedConsortConfigValues(builder);
        var spec = builder.build();
        var document = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        spec.correct(document);
        spec.setConfig(document);
        var catalog = new com.tonywww.elder_bosses.boss.promisedconsort.action.PromisedConsortActionCatalog(values.skillSnapshot());
        int tested = 0;
        int phaseTwoCases = 0;
        for (var phase : com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase.values())
        for (var action : PromisedConsortActionId.values()) for (boolean variant : new boolean[]{false, true}) {
            if (variant && !values.skillSnapshot().get(action).hasRangedCounter()) continue;
            if (!catalog.get(action, variant).isAvailableIn(phase)) continue;
            var runtime = new com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortActionRuntime(catalog);
            var snapshot = runtime.start(action, phase,
                    100, 42, new java.util.UUID(1, 2), variant);
            var timeline = catalog.timeline(snapshot);
            var schedule = PromisedConsortActionSoundPlan.schedule(snapshot, timeline, catalog.skill(snapshot));
            long expected = PromisedConsortAnimationTimeline.swordWindows(action).stream().mapToInt(window -> Integer.bitCount(window.sides())).sum();
            var swordCues = schedule.stream().filter(event -> event.cue().sound() == PromisedConsortActionSoundPlan.Sound.SLASH).toList();
            require(swordCues.size() == expected, "Missing individual sword release: " + action + " / " + variant);
            if (action == PromisedConsortActionId.L_COMBO_CROSS) require(swordCues.stream().map(event -> event.tick()).toList().equals(List.of(11, 20, 40, 40)),
                    "Three-stage combo must emit four blade sounds at its actual contacts");
                if (action == PromisedConsortActionId.LION_CLAW || action == PromisedConsortActionId.LION_CLAW_DOUBLE) {
                require(swordCues.size() == 4, "Lion preparation and landing both need two blade sounds");
                require(swordCues.get(0).tick() == swordCues.get(1).tick() && swordCues.get(2).tick() == swordCues.get(3).tick()
                    && swordCues.get(0).tick() < timeline.activeStartTick(0) && swordCues.get(2).tick() == timeline.activeStartTick(0),
                    "Lion ground-cut/landing sound pairs are not aligned to their separate motions");
                }
            if (variant) {
                int stage = switch (action) { case LIGHTSPEED_DASH -> 4; case LIGHTSPEED_SIDE_DASH -> 3; default -> 0; };
                int contact = timeline.activeStartTick(stage) + catalog.skill(snapshot).integerList("ranged_counter.attack_event_offsets").get(stage);
                require(swordCues.stream().anyMatch(event -> event.tick() == contact), "Ranged blade sound ignored attack offset: " + action);
            }
            var played = new java.util.HashSet<String>();
            var emitted = new java.util.ArrayList<PromisedConsortActionSoundPlan.Cue>();
            for (int tick = 0; tick < timeline.totalTicks(); tick++) for (int repeat = 0; repeat < 3; repeat++) for (var event : schedule) {
                PromisedConsortActionSoundPlan.dispatch(List.of(event.cue()), played, "", tick, event.tick(), event.tick() + 1, emitted::add);
            }
            require(emitted.size() == schedule.size(), "Repeated tick duplicated or lost a cue: " + action);
            for (var event : schedule) require(!PromisedConsortActionSoundPlan.claim(new java.util.HashSet<>(played), event.cue().key(),
                    event.tick(), event.tick(), event.tick() + 1), "Restored sound key replayed");
            require(!PromisedConsortActionSoundPlan.claim(new java.util.HashSet<>(), "expired", 20, 5, 10), "Expired sound backlog played");
            var extended = com.tonywww.elder_bosses.combat.action.ActionTimeline.ofStages(timeline.stages().stream()
                    .map(stage -> new com.tonywww.elder_bosses.combat.action.ActionStage(stage.windupTicks() + 7, stage.activeTicks(), stage.recoveryTicks() + 3))
                    .toArray(com.tonywww.elder_bosses.combat.action.ActionStage[]::new));
            var custom = PromisedConsortActionSoundPlan.schedule(snapshot, extended, catalog.skill(snapshot));
            require(custom.stream().filter(event -> event.cue().sound() == PromisedConsortActionSoundPlan.Sound.SLASH).count() == expected,
                    "Custom timing lost a blade");
            require(custom.stream().allMatch(event -> event.tick() >= 0 && event.tick() < extended.totalTicks()), "Custom sound outside action");
            if (!swordCues.isEmpty()) require(custom.stream().filter(event -> event.cue().sound() == PromisedConsortActionSoundPlan.Sound.SLASH)
                    .findFirst().orElseThrow().tick() > swordCues.get(0).tick(), "Audio ignored changed windup");
            for (String occurrence : List.of("clone_0", "clone_1")) {
                var clone = PromisedConsortActionSoundPlan.impact(action, occurrence);
                require(clone.size() == 2 && clone.get(0).side() != clone.get(1).side(), "Clone lost dual blade sound");
            }
            tested++;
            if (phase == com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase.PHASE_TWO) phaseTwoCases++;
        }
        require(phaseTwoCases == 30 && tested == 49, "Missing action, legal phase or ranged variant schedule");
        var cue = PromisedConsortActionSoundPlan.cue("test", PromisedConsortActionSoundPlan.Sound.SLASH, 0.7F, 1.1F);
        require(PromisedConsortActionSoundPlan.playback(cue, false, 1, 1).volume() == 0
                && PromisedConsortActionSoundPlan.playback(cue, true, 0, 1).volume() == 0, "Audio disable ignored");
        require(Math.abs(PromisedConsortActionSoundPlan.playback(cue, true, 0.5, 1.2).volume() - 0.35) < 0.00001
                && Math.abs(PromisedConsortActionSoundPlan.playback(cue, true, 0.5, 1.2).pitch() - 1.32) < 0.00001, "Configured volume/pitch ignored");
        System.out.println("26 actions + 4 variants / " + tested + " legal phase combinations: dispatched per-blade counts, custom timings, offsets, restored keys and config passed");
    }

    private static void checkPersistenceAndResources() throws Exception {
        var budget = new PromisedConsortActionSoundPlan.AuxiliaryBudget();
        var auxiliary = PromisedConsortActionSoundPlan.cue("holy", PromisedConsortActionSoundPlan.Sound.HOLY, 0.5F, 1);
        require(budget.claim(auxiliary, 100) && budget.claim(auxiliary, 100) && !budget.claim(auxiliary, 100), "Auxiliary layers are unbounded");
        require(budget.claim(auxiliary, 101), "Auxiliary budget did not reset");
        for (int blade = 0; blade < 16; blade++) require(budget.claim(PromisedConsortActionSoundPlan.cue("blade" + blade,
            PromisedConsortActionSoundPlan.Sound.SLASH, 0.5F, 1), 101), "Auxiliary cap swallowed a blade");
        for (Boolean saved : new Boolean[]{null, false, true}) for (long remaining : new long[]{0, 5}) {
            var events = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionExecutor.restoredSoundEvents(saved, remaining);
            boolean expectedPlayed = saved == null ? remaining == 0 : saved;
            require(events.contains("activation") == expectedPlayed, "Legacy hazard audio restore differs");
            require(!PromisedConsortActionSoundPlan.claim(events, "activation", 99, 100, 104), "Pending hazard sound played early");
            require(PromisedConsortActionSoundPlan.claim(events, "activation", 100, 100, 104) == !expectedPlayed, "Hazard activation replay or omission");
            require(!PromisedConsortActionSoundPlan.claim(events, "activation", 101, 100, 104), "Sustained hazard repeats sound");
        }
        var events = new java.util.HashSet<String>();
        var sink = new java.util.ArrayList<PromisedConsortActionSoundPlan.Cue>();
        var blades = PromisedConsortActionSoundPlan.blades("both", List.of(new Swing(-1, 120, 0), new Swing(1, 120, 0)));
        for (int targetCount : new int[]{0, 1, 8}) {
            events.clear(); sink.clear();
            for (int repeat = 0; repeat < Math.max(1, targetCount); repeat++) PromisedConsortActionSoundPlan.dispatch(blades, events, "sequence:1:", 10, 10, 11, sink::add);
            require(sink.size() == 2, "Blade count depends on target count");
        }
        var gson = new com.google.gson.Gson();
        var hazard = new com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionExecutor.PersistentHazard(
                "1:echo_cross", 1, com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionExecutor.ShapeKind.CIRCLE,
                0, 0, 0, 1, List.of(2.0), 0, 2, 0, 10,
                new com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortHitSpec("test",
                        new com.tonywww.elder_bosses.combat.damage.DamageFormula(1, 0),
                        com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortHitSpec.DamageKind.HOLY, false, 1), java.util.Set.of(), true);
        var json = gson.toJsonTree(hazard).getAsJsonObject();
        require(Boolean.TRUE.equals(gson.fromJson(json, hazard.getClass()).activationSoundPlayed()), "Saved hazard sound state lost");
        json.remove("activationSoundPlayed");
        require(gson.fromJson(json, hazard.getClass()).activationSoundPlayed() == null, "Old hazard sound state not nullable");
        var sounds = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/assets/elder_bosses/sounds.json"))).getAsJsonObject();
        for (var sound : PromisedConsortActionSoundPlan.Sound.values()) {
            var entry = sounds.getAsJsonObject(sound.eventId());
            require(entry != null, "Missing sound resource event: " + sound);
            var file = entry.getAsJsonArray("sounds").get(0).getAsJsonObject();
            require(file.get("attenuation_distance").getAsInt() == sound.distance(), "Server/client sound distance differs");
            require(java.nio.file.Files.size(java.nio.file.Path.of("src/main/resources/assets/elder_bosses/sounds/entity/promised_consort/" + sound.asset() + ".ogg")) > 0, "Missing OGG");
            for (String language : List.of("en_us", "zh_cn")) {
                var translations = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/assets/elder_bosses/lang/" + language + ".json"))).getAsJsonObject();
                require(translations.has(entry.get("subtitle").getAsString()), "Missing sound subtitle");
            }
        }
        require(sounds.getAsJsonObject("promised_consort.instant_guard_cue").getAsJsonArray("sounds").get(0).getAsJsonObject()
                .get("name").getAsString().equals("minecraft:item.shield.block"), "Original instant guard cue changed");
        var registry = com.tonywww.elder_bosses.platforms.registry.ModSoundEvents.class.getDeclaredField("CONSORT_ACTIONS");
        registry.setAccessible(true);
        require(((java.util.Map<?, ?>) registry.get(null)).keySet().equals(java.util.EnumSet.allOf(PromisedConsortActionSoundPlan.Sound.class)),
            "Sound registration does not cover every action sound type");
        System.out.println("Hazard persistence, no-target/repeated dispatch, six OGG definitions and bilingual subtitle checks passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}