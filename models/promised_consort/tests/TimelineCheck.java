import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortAnimationTimeline;
import com.tonywww.elder_bosses.combat.action.ActionStage;
import com.tonywww.elder_bosses.combat.action.ActionTimeline;
import com.tonywww.elder_bosses.combat.action.SkillTuning;

public final class TimelineCheck {
    private static int assertions;

    public static void main(String[] arguments) throws java.io.IOException {
        if (java.util.Arrays.asList(arguments).contains("--sync-rhythm-configs")) syncRhythmConfigs();
        if (java.util.Arrays.asList(arguments).contains("--check-batch-configs")
                || java.util.Arrays.asList(arguments).contains("--sync-batch-configs")) {
            syncBatchConfigs(java.util.Arrays.asList(arguments).contains("--sync-batch-configs"));
        }
        if (java.util.Arrays.asList(arguments).contains("--migrate-configs")) migrateAbsoluteTickConfigs();
        checkAbsoluteStageTicks();
        checkActualConfigSpec();
        checkIndependentImpactStages();
        checkCloneComponentStages();
        checkSwordConfigurations();
        require(java.util.Arrays.equals(PromisedConsortAnimationTimeline.durations(PromisedConsortActionId.L_COMBO_CROSS),
            new int[]{11, 3, 1, 6, 3, 9, 11, 4, 36}),
            "Approved left cross motion must use contacts 11/21/44 and duration 84");
        require(PromisedConsortAnimationTimeline.swordSides(PromisedConsortActionId.L_COMBO_BLOODFLAME, 13) == 1,
            "Bloodflame thrust must use the left blade");
        require(PromisedConsortAnimationTimeline.swordSides(PromisedConsortActionId.L_COMBO_BLOODFLAME, 36) == 1,
            "Bloodflame tear must continue with the same left blade");
        require(PromisedConsortAnimationTimeline.swordSides(PromisedConsortActionId.RING_OF_LIGHT, 24) == 2,
            "Ring release must use only the right blade");
        require(PromisedConsortAnimationTimeline.swordSides(PromisedConsortActionId.R_COMBO_EARTHHEAVE, 105) == 3,
            "Earthheave extraction needs the actual sword trace");
        require(PromisedConsortAnimationTimeline.swordSides(PromisedConsortActionId.L_COMBO_CROSS, 47) == 3,
            "Combo trace must cover the final configured active tick");
        require(PromisedConsortAnimationTimeline.swordSides(PromisedConsortActionId.L_COMBO_CROSS, 21) == 2,
            "Retimed second contact must use only the right blade");
        require(PromisedConsortAnimationTimeline.swordSides(PromisedConsortActionId.L_COMBO_CROSS, 55) == 0,
            "Retimed recovery must not keep the old final-contact trace");
        require(PromisedConsortAnimationTimeline.swordSides(PromisedConsortActionId.STOMP, 14) == 0,
            "A foot impact must not emit a sword trace");
        require(!PromisedConsortAnimationTimeline.cancelledCloneContact(18, 20, 7, 7),
            "A pending clone must remain while its parent action is active");
        require(PromisedConsortAnimationTimeline.cancelledCloneContact(18, 20, 7, -1),
            "A cancelled action must remove its pending clone contact");
        require(PromisedConsortAnimationTimeline.cancelledCloneContact(20, 20, 7, 8),
            "Starting another action must not retain the old clone contact on the impact tick");
        require(!PromisedConsortAnimationTimeline.cancelledCloneContact(21, 20, 7, -1),
            "Completed clone contacts must retain their normal fade after cancellation");
        require(!PromisedConsortAnimationTimeline.cancelledCloneContact(18, 20, -1, -1),
            "Legacy clones without a stored sequence must retain their bounded lifetime");
        for (double speed : new double[]{1.0, 1.3, 1.35, 1.4, 1.5, 2.0}) {
            SkillTuning tuning = new SkillTuning(speed, 1.0);
            for (PromisedConsortActionId action : PromisedConsortActionId.values()) {
                int[] durations = PromisedConsortAnimationTimeline.durations(action);
                ActionStage[] stages = new ActionStage[durations.length / 3];
                int authoredTotal = 0;
                for (int index = 0; index < stages.length; index++) {
                    stages[index] = new ActionStage(tuning.scaleTicks(durations[index * 3]),
                            tuning.scaleTicks(durations[index * 3 + 1]), tuning.scaleTicks(durations[index * 3 + 2]));
                    authoredTotal += durations[index * 3] + durations[index * 3 + 1] + durations[index * 3 + 2];
                }
                ActionTimeline timeline = ActionTimeline.ofStages(stages);
                double previous = -1;
                for (double tick = 0; tick <= timeline.totalTicks(); tick += 0.25) {
                    double mapped = PromisedConsortAnimationTimeline.sample(tick, action, timeline, tuning);
                    require(Double.isFinite(mapped) && mapped >= previous && mapped >= 0 && mapped <= authoredTotal,
                            action + " is not monotonic at " + speed + " / " + tick);
                    previous = mapped;
                }
                require(PromisedConsortAnimationTimeline.sample(timeline.totalTicks(), action, timeline, tuning) == authoredTotal,
                        action + " does not end on its authored endpoint");
                int runtimeCursor = 0, authoredCursor = 0;
                for (int index = 0; index < stages.length; index++) {
                    int runtimeHit = runtimeCursor + stages[index].windupTicks();
                    int authoredHit = authoredCursor + durations[index * 3];
                        if (action != PromisedConsortActionId.CONSORT_METEOR) {
                        require(PromisedConsortAnimationTimeline.sample(runtimeHit, action, timeline, tuning) == authoredHit,
                            action + " active start mismatch at " + speed);
                        }
                    runtimeCursor += stages[index].windupTicks() + stages[index].activeTicks() + stages[index].recoveryTicks();
                    authoredCursor += durations[index * 3] + durations[index * 3 + 1] + durations[index * 3 + 2];
                }
                if (action == PromisedConsortActionId.PROMISED_CONSORT) {
                    int[] offsets = {8, 18, 28, 42, 44, 47, 50};
                    int[] expected = {41, 54, 70, 111, 115, 120, 127};
                    for (int index = 0; index < offsets.length; index++) {
                        int offset = Math.max(1, (int) Math.round(offsets[index] * stages[0].activeTicks() / 54.0));
                        require(PromisedConsortAnimationTimeline.sample(stages[0].windupTicks() + offset, action, timeline, tuning) == expected[index],
                                "Consort event mismatch at " + speed + " / " + offset);
                    }
                }
                if (action == PromisedConsortActionId.CONSORT_METEOR) {
                    require(PromisedConsortAnimationTimeline.sample(stages[0].windupTicks(), action, timeline, tuning) == 183,
                            "Meteor lock mismatch at " + speed);
                    require(PromisedConsortAnimationTimeline.sample(stages[0].windupTicks() + stages[0].activeTicks(), action, timeline, tuning) == 212,
                            "Meteor impact mismatch at " + speed);
                }
            }
        }
        System.out.println("Timeline checks passed: " + assertions + " assertions, 22 actions, 6 cast speeds");
    }

    private static void checkAbsoluteStageTicks() {
        for (double legacySpeed : new double[]{0.5, 1.0, 1.3, 2.0, 5.0}) {
            SkillTuning tuning = new SkillTuning(legacySpeed, 1.25);
            for (int ticks : new int[]{0, 1, 7, 26, 121}) require(tuning.scaleTicks(ticks) == ticks,
                "Legacy speed must not change explicit tick values");
            require(tuning.scaleRange(4.0) == 5.0, "Range scaling must remain independent from explicit timing");
        }
        var skills = new java.util.EnumMap<PromisedConsortActionId,
            com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSkillConfigSnapshot.Skill>(PromisedConsortActionId.class);
        var defaultBuilder = new net.minecraftforge.common.ForgeConfigSpec.Builder();
        var defaultValues = new com.tonywww.elder_bosses.platforms.config.PromisedConsortConfigValues(defaultBuilder);
        var defaultSpec = defaultBuilder.build();
        var defaultDocument = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        defaultSpec.correct(defaultDocument);
        defaultSpec.setConfig(defaultDocument);
        var defaultSkills = defaultValues.skillSnapshot();
        var integers = java.util.Map.of("windup_ticks", 9, "active_ticks", 3, "recovery_ticks", 20,
            "double_windup_ticks", 9, "double_active_ticks", 3, "double_recovery_ticks", 20, "script_ticks", 150, "tempest_hits", 2);
        for (PromisedConsortActionId action : PromisedConsortActionId.values()) {
            if (action == PromisedConsortActionId.LION_CLAW_DOUBLE) continue;
            if(action.rangedDefense() || action == PromisedConsortActionId.CROSS_LEAP_COMBO) { skills.put(action,defaultSkills.get(action)); continue; }
            java.util.Map<String, java.util.List<Integer>> stages = action == PromisedConsortActionId.L_COMBO_CROSS
                ? java.util.Map.of("windup_ticks", java.util.List.of(25, 16, 19), "active_ticks", java.util.List.of(7, 5, 6),
                    "recovery_ticks", java.util.List.of(13, 8, 17)) : action == PromisedConsortActionId.R_COMBO_TEMPEST
                ? java.util.Map.of("windup_ticks", java.util.List.of(12, 7, 8, 20, 29), "active_ticks", java.util.List.of(3, 4, 3, 5, 6),
                    "recovery_ticks", java.util.List.of(5, 6, 7, 8, 9)) : java.util.Map.of();
            skills.put(action, new com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSkillConfigSnapshot.Skill(
                true, 1, 50, false, 2.0, 1.25, java.util.Map.of("range", 4.2), integers, java.util.Map.of(), stages, java.util.Map.of(), java.util.Map.of()));
        }
        var catalog = new com.tonywww.elder_bosses.boss.promisedconsort.action.PromisedConsortActionCatalog(
            new com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSkillConfigSnapshot(skills));
        var timeline = catalog.get(PromisedConsortActionId.L_COMBO_CROSS).timeline();
        require(timeline.stages().equals(java.util.List.of(new ActionStage(25, 7, 13), new ActionStage(16, 5, 8), new ActionStage(19, 6, 17))),
            "Configured phase ticks must be used verbatim, including when a legacy cast multiplier is present");
        require(timeline.totalTicks() == 116, "Independent component durations must sum without percentage scaling");
        require(timeline.activeStartTick(1) == 61 && timeline.activeEndTick(1) == 66 && timeline.stageStartTick(2) == 74,
            "Component event offsets must include previous windup, release and recovery");
        require(timeline.activeTicksBetween(28, 98) == 14,
            "Movement must count only release ticks, excluding configurable pauses between components");
        var tempest = new com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortActionSnapshot(
            PromisedConsortActionId.R_COMBO_TEMPEST, com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase.PHASE_ONE,
            1, 0, 0, com.tonywww.elder_bosses.combat.action.ActionPhase.WINDUP, 0, 0, 42, null);
        var tempestPlan = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortAttackPlan.create(tempest,
            skills.get(tempest.actionId()), catalog.get(tempest.actionId()).timeline(), net.minecraft.world.phys.Vec3.ZERO,
            new com.tonywww.elder_bosses.combat.geometry.Vec2(0, 1), java.util.Map.of(), 6);
        require(tempestPlan.size() == 5 && tempestPlan.stream().map(strike -> strike.id()).distinct().count() == 5,
            "Each independently configured tempest component must have exactly one attack occurrence");
        require(tempestPlan.get(3).activeTick() == 75 && tempestPlan.get(4).activeTick() == 117,
            "Both whirlwind contacts must use their own windup/active/recovery segments");
    }

    private static void checkIndependentImpactStages() {
        for (PromisedConsortActionId action : new PromisedConsortActionId[]{PromisedConsortActionId.GRAVITY_DIVE,
                PromisedConsortActionId.CROSS_SLASH, PromisedConsortActionId.SPIRAL_ASSAULT}) {
            ActionTimeline timeline = ActionTimeline.ofStages(new ActionStage(25, 7, 4), new ActionStage(19, 6, 12));
            var snapshot = new com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortActionSnapshot(action,
                com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase.PHASE_ONE,
                1, 100, 0, com.tonywww.elder_bosses.combat.action.ActionPhase.WINDUP, 0, 0, 42, null);
            var skill = new com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSkillConfigSnapshot.Skill(
                true, 1, 50, false, 1, 1.25, java.util.Map.of("range", 8.0, "width", 2.0, "sword_range", 4.0, "debris_range", 7.0),
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of());
            var plan = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortAttackPlan.create(snapshot, skill, timeline,
                net.minecraft.world.phys.Vec3.ZERO, new com.tonywww.elder_bosses.combat.geometry.Vec2(0, 1), java.util.Map.of(), 6);
            require(plan.size() == 2 && plan.get(0).activeTick() == 125 && plan.get(1).activeTick() == 155,
                "Independent impact must use its own segment, not a hard-coded offset: " + action);
            int[] authoredContacts = switch (action) {
                case GRAVITY_DIVE -> new int[]{38, 43};
                case CROSS_SLASH -> new int[]{17, 19};
                default -> new int[]{35, 43};
            };
            for (int index = 0; index < 2; index++) require(PromisedConsortAnimationTimeline.sample(timeline.activeStartTick(index), action,
                    timeline, SkillTuning.NEUTRAL) == authoredContacts[index], "Independent impact pose differs from its server event: " + action);
            double previous = -1;
            for (double tick = 0; tick <= timeline.totalTicks(); tick += 0.25) {
                double sample = PromisedConsortAnimationTimeline.sample(tick, action, timeline, SkillTuning.NEUTRAL);
                require(sample >= previous, "Independent-stage animation moved backwards: " + action);
                previous = sample;
            }
        }
    }

    private static void checkCloneComponentStages() {
        var events = java.util.Map.of(
            PromisedConsortActionId.LIGHTSPEED_SLASH, new int[]{35, 48, 61, 69},
            PromisedConsortActionId.LIGHTSPEED_SIDE_DASH, new int[]{36, 44, 52, 65},
            PromisedConsortActionId.LIGHTSPEED_DASH, new int[]{46, 52, 58, 64, 73, 80},
            PromisedConsortActionId.PROMISED_CONSORT, new int[]{27, 41, 54, 70, 111, 115, 120, 127},
            PromisedConsortActionId.CROSS_LEAP_COMBO, new int[]{27, 41, 54, 70, 111, 115, 120, 127},
            PromisedConsortActionId.L_COMBO_BLOODFLAME, new int[]{15, 35, 55},
            PromisedConsortActionId.STARCALLER_CRY, new int[]{18, 58},
            PromisedConsortActionId.GRAVITY_METEOR, new int[]{90, 94, 98, 102, 106, 110, 114, 118, 122, 127, 132, 137},
            PromisedConsortActionId.LIGHT_OF_MIQUELLA, new int[]{110, 124, 125, 126, 127, 128, 129, 130, 131},
            PromisedConsortActionId.ENHANCED_EARTHHEAVE, new int[]{28, 47, 51, 55, 59});
        for (var entry : events.entrySet()) {
            PromisedConsortActionId action = entry.getKey();
            ActionStage[] stages = new ActionStage[entry.getValue().length];
            for (int index = 0; index < stages.length; index++) stages[index] = new ActionStage(12 + index * 2, 3 + index, 5 + index);
            ActionTimeline timeline = ActionTimeline.ofStages(stages);
            var snapshot = new com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortActionSnapshot(action,
                com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase.PHASE_TWO,
                1, 100, 0, com.tonywww.elder_bosses.combat.action.ActionPhase.WINDUP, 0, 0, 42, null);
            var skill = new com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSkillConfigSnapshot.Skill(
                true, 1, 50, false, 1, 1.25, java.util.Map.of("range", 16.0, "width", 2.5, "radius", 8.0,
                    "sweep_range", 4.0, "thrust_range", 5.0, "clone_radius", 3.5, "pull_radius", 12.0, "impact_radius", 6.0,
                    "leap_distance", 16.0, "leap_height", 1.2),
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of());
            var plan = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortAttackPlan.create(snapshot, skill, timeline,
                net.minecraft.world.phys.Vec3.ZERO, new com.tonywww.elder_bosses.combat.geometry.Vec2(0, 1),
                java.util.Map.of("clone_meteor_0", net.minecraft.world.phys.Vec3.ZERO, "clone_meteor_1", net.minecraft.world.phys.Vec3.ZERO,
                    "clone_meteor_2", net.minecraft.world.phys.Vec3.ZERO, "clone_meteor_3", net.minecraft.world.phys.Vec3.ZERO), 6);
            int expected = stages.length + (action == PromisedConsortActionId.ENHANCED_EARTHHEAVE ? 1 : action == PromisedConsortActionId.STARCALLER_CRY ? 3 : 0);
                if (action == PromisedConsortActionId.GRAVITY_METEOR) {
                expected += 2;
                var sequence = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortMeteorSequence.from(timeline);
                require(plan.stream().filter(strike -> strike.id().startsWith("rock_flight_")).count() == 8
                    && plan.stream().filter(strike -> strike.id().startsWith("clone_meteor_")).count() == 4,
                    "Meteor revision changed the original eight rocks/four clones");
                for (var contact : java.util.Map.of("meteor_ground", sequence.groundTick(), "meteor_body", sequence.landingTick()).entrySet()) {
                    var strike = plan.stream().filter(candidate -> candidate.id().equals(contact.getKey())).findFirst().orElseThrow();
                    require(strike.activeTick() == 100 + contact.getValue() && strike.endTick() == strike.activeTick() + 1,
                        "New meteor contact must occur once at the flight sequence event");
                    double pose = PromisedConsortAnimationTimeline.sample(contact.getValue(), action, timeline, SkillTuning.NEUTRAL);
                    require(Math.abs(pose - (contact.getKey().equals("meteor_ground") ? 21 : 144)) < 0.00001,
                        "New meteor contact pose disagrees with server event: " + contact.getKey() + "/" + pose);
                }
                }
            require(plan.size() == expected && plan.stream().map(strike -> strike.id()).distinct().count() == expected,
                "Components must not disappear or duplicate when their durations change: " + action);
            for (int index = 0; index < stages.length; index++) {
                long expectedTick = 100 + timeline.activeStartTick(index);
                require(plan.stream().anyMatch(strike -> strike.activeTick() == expectedTick), "Component event uses a fixed offset: " + action);
                require(PromisedConsortAnimationTimeline.sample(timeline.activeStartTick(index), action, timeline, SkillTuning.NEUTRAL)
                    == entry.getValue()[index], "Component pose and event disagree: " + action + "/" + index);
            }
            require(plan.stream().allMatch(strike -> strike.startTick() < strike.activeTick()), "Missing component warning: " + action);
            double previous = -1;
            for (double tick = 0; tick <= timeline.totalTicks(); tick += 0.25) {
                double sampled = PromisedConsortAnimationTimeline.sample(tick, action, timeline, SkillTuning.NEUTRAL);
                require(sampled >= previous, "Component interpolation moved backwards: " + action);
                previous = sampled;
            }
        }
    }

    private static void checkActualConfigSpec() throws java.io.IOException {
        var builder = new net.minecraftforge.common.ForgeConfigSpec.Builder();
        var values = new com.tonywww.elder_bosses.platforms.config.PromisedConsortConfigValues(builder);
        var spec = builder.build();
        var config = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        spec.correct(config);
        require(spec.isCorrect(config), "Actual configuration defaults must accept zero windup/recovery components");
        spec.setConfig(config);
        var snapshot = values.skillSnapshot();
        var catalog = new com.tonywww.elder_bosses.boss.promisedconsort.action.PromisedConsortActionCatalog(snapshot);
        ActionTimeline crossSlash = catalog.get(PromisedConsortActionId.CROSS_SLASH).timeline();
        require(crossSlash.stages().equals(java.util.List.of(new ActionStage(17, 1, 0), new ActionStage(1, 1, 17)))
            && crossSlash.totalTicks() == 37, "Rhythm cross slash must keep independent contacts and shorten only recovery");
        require(crossSlash.activeStartTick(0) == 17 && crossSlash.activeStartTick(1) == 19,
            "Cross slash sword and ground events must align with the approved prototype");
        String crossPrefix = "promised_consort.skills.cross_slash.";
        require(((Number) config.get(crossPrefix + "sword_range")).doubleValue() == 4.0
            && ((Number) config.get(crossPrefix + "debris_range")).doubleValue() == 7.0
            && ((Number) config.get(crossPrefix + "range_multiplier")).doubleValue() == 1.35,
            "Cross slash retiming changed range");
        require(((Number) config.get(crossPrefix + "sword_damage.flat")).doubleValue() == 4.0
            && ((Number) config.get(crossPrefix + "sword_damage.attack_ratio")).doubleValue() == 0.75
            && ((Number) config.get(crossPrefix + "debris_damage.flat")).doubleValue() == 2.0
            && ((Number) config.get(crossPrefix + "debris_damage.attack_ratio")).doubleValue() == 0.35,
            "Cross slash retiming changed damage");
        require(PromisedConsortAnimationTimeline.swordSides(PromisedConsortActionId.CROSS_SLASH, 17) == 3,
            "Cross slash must show both blades at contact");
        ActionTimeline earthheave = catalog.get(PromisedConsortActionId.R_COMBO_EARTHHEAVE).timeline();
        int[] earthContacts = {16, 42, 54, 87, 105}, earthSides = {2, 1, 2, 3, 3};
        int[] runtimeEarthContacts = {16, 39, 49, 78, 95};
        require(earthheave.totalTicks() == 134, "Rhythm earthheave must last 134 absolute ticks");
        for (int index = 0; index < earthContacts.length; index++) {
            require(earthheave.activeStartTick(index) == runtimeEarthContacts[index], "Rhythm earthheave event differs: " + index);
            for (int tick = runtimeEarthContacts[index]; tick < earthheave.activeEndTick(index); tick++) {
                double poseTick = PromisedConsortAnimationTimeline.sample(tick, PromisedConsortActionId.R_COMBO_EARTHHEAVE,
                    earthheave, snapshot.get(PromisedConsortActionId.R_COMBO_EARTHHEAVE).tuning());
                require(PromisedConsortAnimationTimeline.swordSides(PromisedConsortActionId.R_COMBO_EARTHHEAVE, poseTick) == earthSides[index],
                    "Earthheave sword side or release window differs: " + index + " @ " + tick);
            }
        }
        ActionTimeline extendedEarthheave = ActionTimeline.ofStages(new ActionStage(20, 3, 7), new ActionStage(25, 4, 6),
            new ActionStage(9, 3, 11), new ActionStage(28, 6, 17), new ActionStage(15, 8, 30));
        var earthSnapshot = new com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortActionSnapshot(
            PromisedConsortActionId.R_COMBO_EARTHHEAVE, com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase.PHASE_ONE,
            1, 100, 0, com.tonywww.elder_bosses.combat.action.ActionPhase.WINDUP, 0, 0, 42, null);
        var earthPlan = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortAttackPlan.create(earthSnapshot,
            snapshot.get(earthSnapshot.actionId()), extendedEarthheave, net.minecraft.world.phys.Vec3.ZERO,
            new com.tonywww.elder_bosses.combat.geometry.Vec2(0, 1), java.util.Map.of(), 6);
        require(earthPlan.size() == 5, "Changing earthheave component durations must not change the five hits");
        for (int index = 0; index < earthContacts.length; index++) {
            require(earthPlan.get(index).activeTick() == 100 + extendedEarthheave.activeStartTick(index), "Earthheave event ignores its configured segment");
            require(PromisedConsortAnimationTimeline.sample(extendedEarthheave.activeStartTick(index), earthSnapshot.actionId(),
                extendedEarthheave, SkillTuning.NEUTRAL) == earthContacts[index], "Earthheave pose does not follow its independently timed contact");
        }
        for (PromisedConsortActionId action : PromisedConsortActionId.values()) {
            require(catalog.get(action).timeline().totalTicks() > 0, "Missing actual configured action: " + action);
            require(!snapshot.get(action).numbers().containsKey("cast_speed_multiplier"), "Spec still exposes percentage speed: " + action);
            ActionTimeline configured = catalog.get(action).timeline();
            if(action.rangedDefense()) continue;
            var profile = rhythmProfile(action.serializedName());
            var stages = profile.getAsJsonArray("stage_ticks");
            require(stages.size() == configured.stages().size(), "Rhythm component count differs: " + action);
            for (int index = 0; index < stages.size(); index++) {
                var expected = stages.get(index).getAsJsonArray();
                require(configured.stages().get(index).equals(new ActionStage(expected.get(0).getAsInt(), expected.get(1).getAsInt(),
                    expected.get(2).getAsInt())), "Default stages differ from checked rhythm candidate: " + action);
            }
            require(configured.totalTicks() == profile.get("duration_ticks").getAsInt(), "Rhythm duration differs: " + action);
            var knots = profile.getAsJsonArray("knots");
            if (action == PromisedConsortActionId.GRAVITY_METEOR) {
                var revised = new java.util.TreeMap<Integer, Integer>();
                for (var knot : knots) {
                    var pair = knot.getAsJsonArray();
                    revised.put(pair.get(1).getAsInt(), pair.get(0).getAsInt());
                }
                revised.putAll(java.util.Map.of(21, 21, 34, 35, 61, 63, 131, 144));
                knots = new com.google.gson.JsonArray();
                for (var knot : revised.entrySet()) {
                    var pair = new com.google.gson.JsonArray();
                    pair.add(knot.getValue());
                    pair.add(knot.getKey());
                    knots.add(pair);
                }
            }
            for (double tick = 0; tick <= configured.totalTicks(); tick += 0.25) {
                require(Math.abs(PromisedConsortAnimationTimeline.sample(tick, action, configured, snapshot.get(action).tuning())
                    - sourceTick(tick, knots)) < 0.00001, "Runtime clock differs from candidate: " + action + " @ " + tick);
            }
        }
        var meteor = catalog.get(PromisedConsortActionId.CONSORT_METEOR).timeline();
        require(meteor.stages().size() == 5 && meteor.activeStartTick(2) == 163 && meteor.activeStartTick(3) == 188
            && meteor.activeStartTick(4) == 190 && meteor.totalTicks() == 243, "Meteor rhythm component boundaries differ");
        var modified = ActionTimeline.ofStages(new ActionStage(7, 45, 3), new ActionStage(9, 31, 2), new ActionStage(6, 20, 4),
            new ActionStage(3, 2, 5), new ActionStage(7, 4, 30));
        require(PromisedConsortAnimationTimeline.sample(modified.activeStartTick(2), PromisedConsortActionId.CONSORT_METEOR, modified,
            SkillTuning.NEUTRAL) == 183, "Configured meteor lock must match the authored lock");
        require(PromisedConsortAnimationTimeline.sample(modified.activeStartTick(3), PromisedConsortActionId.CONSORT_METEOR, modified,
            SkillTuning.NEUTRAL) == 212, "Configured meteor impact must match the authored impact");
        config.set("promised_consort.skills.lightspeed_slash.active_ticks", java.util.List.of(6, 0, 11, 1));
        require(!spec.isCorrect(config), "Actual config spec must reject a zero-duration attack component");
    }

    private static void checkSwordConfigurations() throws java.io.IOException {
        record ExpectedCombo(PromisedConsortActionId action, int[] timing, int[] sides, double range, double[][] damage) {}
        ExpectedCombo[] combos = {
            new ExpectedCombo(PromisedConsortActionId.L_COMBO_CROSS, new int[]{11, 3, 1, 6, 3, 9, 11, 4, 36},
                new int[]{1, 2, 3}, 3.8, new double[][]{{2, 0.45}, {2, 0.45}, {4, 0.70}}),
            new ExpectedCombo(PromisedConsortActionId.R_COMBO_CROSS, new int[]{11, 3, 6, 21, 4, 30},
                new int[]{2, 3}, 3.8, new double[][]{{2, 0.45}, {4, 0.75}}),
            new ExpectedCombo(PromisedConsortActionId.R_COMBO_LEFT_TWIN, new int[]{18, 3, 5, 14, 3, 3, 9, 3, 17},
                new int[]{2, 1, 1}, 3.6, new double[][]{{2, 0.45}, {2, 0.40}, {2, 0.45}})
        };
        for (ExpectedCombo combo : combos) {
            require(java.util.Arrays.equals(PromisedConsortAnimationTimeline.durations(combo.action()), combo.timing()),
                "Authored sword timing differs from the reviewed prototype: " + combo.action());
            int cursor = 0;
            for (int stage = 0; stage < combo.sides().length; stage++) {
                int contact = cursor + combo.timing()[stage * 3];
                int active = combo.timing()[stage * 3 + 1];
                for (int tick = contact; tick < contact + active; tick++) {
                    require(PromisedConsortAnimationTimeline.swordSides(combo.action(), tick) == combo.sides()[stage],
                        "Sword side changes inside an active contact: " + combo.action() + " @ " + tick);
                }
                cursor = contact + active + combo.timing()[stage * 3 + 2];
            }
        }
        for (String file : new String[]{"docs/config/elder-bosses-common.example.toml", "run/config/elder_bosses-common.toml",
                "versions/1.21.1-neoforge/run/config/elder_bosses-common.toml"}) {
            java.nio.file.Path path = java.nio.file.Path.of(file);
            if (!java.nio.file.Files.isRegularFile(path)) continue;
            var config = new com.electronwill.nightconfig.toml.TomlParser().parse(java.nio.file.Files.readString(path));
            String crossPrefix = "promised_consort.skills.cross_slash.";
            require(config.get(crossPrefix + "windup_ticks").equals(java.util.List.of(17, 1))
                && config.get(crossPrefix + "active_ticks").equals(java.util.List.of(1, 1))
                && config.get(crossPrefix + "recovery_ticks").equals(java.util.List.of(0, 17)), "Stale cross-slash stage ticks: " + file);
            require(!config.contains(crossPrefix + "cast_speed_multiplier"), "Obsolete cross-slash speed multiplier: " + file);
            require(((Number) config.get(crossPrefix + "sword_range")).doubleValue() == 4.0
                && ((Number) config.get(crossPrefix + "debris_range")).doubleValue() == 7.0
                && ((Number) config.get(crossPrefix + "range_multiplier")).doubleValue() == 1.35, "Cross-slash range changed: " + file);
            require(((Number) config.get(crossPrefix + "sword_damage.flat")).doubleValue() == 4.0
                && ((Number) config.get(crossPrefix + "sword_damage.attack_ratio")).doubleValue() == 0.75
                && ((Number) config.get(crossPrefix + "debris_damage.flat")).doubleValue() == 2.0
                && ((Number) config.get(crossPrefix + "debris_damage.attack_ratio")).doubleValue() == 0.35, "Cross-slash damage changed: " + file);
            String earthPrefix = "promised_consort.skills.right_combo_earthheave.";
            require(config.get(earthPrefix + "windup_ticks").equals(java.util.List.of(16, 17, 6, 20, 6)), "Stale earthheave windup: " + file);
            require(config.get(earthPrefix + "active_ticks").equals(java.util.List.of(2, 2, 2, 5, 5)), "Earthheave release windows differ from rhythm candidate: " + file);
            require(config.get(earthPrefix + "recovery_ticks").equals(java.util.List.of(4, 2, 7, 6, 34)), "Stale earthheave recovery: " + file);
            require(!config.contains(earthPrefix + "cast_speed_multiplier"), "Obsolete earthheave speed multiplier: " + file);
            require(((Number) config.get(earthPrefix + "range")).doubleValue() == 7.0
                && ((Number) config.get(earthPrefix + "range_multiplier")).doubleValue() == 1.45, "Earthheave range changed: " + file);
            String[] earthDamage = {"opening_damage", "slam_damage", "fissure_damage"};
            double[][] expectedEarthDamage = {{2, 0.45}, {5, 0.80}, {4, 0.65}};
            for (int index = 0; index < earthDamage.length; index++) {
                require(((Number) config.get(earthPrefix + earthDamage[index] + ".flat")).doubleValue() == expectedEarthDamage[index][0]
                    && ((Number) config.get(earthPrefix + earthDamage[index] + ".attack_ratio")).doubleValue() == expectedEarthDamage[index][1],
                    "Earthheave damage changed: " + file);
            }
            for (ExpectedCombo combo : combos) {
                String prefix = "promised_consort.skills." + combo.action().serializedName() + ".";
                String context = combo.action() + " in " + file;
                String[] fields = {"windup_ticks", "active_ticks", "recovery_ticks"};
                for (int field = 0; field < fields.length; field++) {
                    java.util.List<Integer> expected = new java.util.ArrayList<>();
                    for (var stage : rhythmProfile(combo.action().serializedName()).getAsJsonArray("stage_ticks")) {
                        expected.add(stage.getAsJsonArray().get(field).getAsInt());
                    }
                    require(config.get(prefix + fields[field]).equals(expected), "Stale " + fields[field] + ": " + context);
                }
                require(!config.contains(prefix + "cast_speed_multiplier"), "Obsolete cast speed configuration: " + context);
                require(((Number) config.get(prefix + "range_multiplier")).doubleValue() == 1.25, "Range scale changed: " + context);
                require(((Number) config.get(prefix + "range")).doubleValue() == combo.range(), "Range changed: " + context);
                java.util.List<? extends com.electronwill.nightconfig.core.UnmodifiableConfig> damage = config.get(prefix + "damage");
                require(damage.size() == combo.damage().length, "Hit count changed: " + context);
                for (int index = 0; index < damage.size(); index++) {
                    require(((Number) damage.get(index).get("flat")).doubleValue() == combo.damage()[index][0]
                        && ((Number) damage.get(index).get("attack_ratio")).doubleValue() == combo.damage()[index][1], "Damage changed: " + context);
                }
            }
        }
    }

    private static com.google.gson.JsonObject rhythmProfile(String name) throws java.io.IOException {
        if (name.equals("cross_leap_combo")) return com.google.gson.JsonParser.parseString("""
            {"duration_ticks":151,"stage_ticks":[[27,3,4],[7,3,4],[6,4,4],[8,4,9],[28,1,1],[2,1,2],[2,1,2],[4,1,23]],
             "source_stages":[[27,3,4],[7,3,4],[6,4,4],[8,4,9],[28,1,1],[2,1,2],[2,1,2],[4,1,23]],"knots":[[0,0],[151,151]]}
            """).getAsJsonObject();
        return com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(
            "models/promised_consort/tests/fixtures/action_timings.json"))).getAsJsonObject().getAsJsonObject(name);
    }

    private static double sourceTick(double tick, com.google.gson.JsonArray knots) {
        for (int index = 1; index < knots.size(); index++) {
            var before = knots.get(index - 1).getAsJsonArray();
            var after = knots.get(index).getAsJsonArray();
            if (tick <= after.get(1).getAsDouble()) {
                double weight = (tick - before.get(1).getAsDouble()) / (after.get(1).getAsDouble() - before.get(1).getAsDouble());
                return before.get(0).getAsDouble() + (after.get(0).getAsDouble() - before.get(0).getAsDouble()) * weight;
            }
        }
        return knots.get(knots.size() - 1).getAsJsonArray().get(0).getAsDouble();
    }

    private static Object timingValue(com.google.gson.JsonArray stages, int field) {
        var values = new java.util.ArrayList<Integer>();
        for (var stage : stages) values.add(stage.getAsJsonArray().get(field).getAsInt());
        return values.size() == 1 ? values.get(0) : values;
    }

    private static void syncRhythmConfigs() throws java.io.IOException {
        var parser = new com.electronwill.nightconfig.toml.TomlParser();
        for (String file : new String[]{"docs/config/elder-bosses-common.example.toml", "run/config/elder_bosses-common.toml",
                "versions/1.21.1-neoforge/run/config/elder_bosses-common.toml"}) {
            var path = java.nio.file.Path.of(file);
            String source = java.nio.file.Files.readString(path);
            var original = parser.parse(source);
            var updated = parser.parse(source);
            var changed = new java.util.LinkedHashMap<String, Object>();
            for (var action : PromisedConsortActionId.values()) {
                if(action.rangedDefense()) continue;
                var profile = rhythmProfile(action.serializedName());
                String prefix = "promised_consort.skills." + (action == PromisedConsortActionId.LION_CLAW_DOUBLE
                    ? "lion_claw.double_" : action.serializedName() + ".");
                String[] fields = {"windup_ticks", "active_ticks", "recovery_ticks"};
                boolean old = true, current = true;
                for (int field = 0; field < fields.length; field++) {
                    Object actual = original.get(prefix + fields[field]);
                    old &= java.util.Objects.equals(actual, timingValue(profile.getAsJsonArray("source_stages"), field));
                    current &= java.util.Objects.equals(actual, timingValue(profile.getAsJsonArray("stage_ticks"), field));
                }
                if (!old) {
                    if (!current) System.out.println("PRESERVED custom rhythm: " + file + " / " + action);
                    continue;
                }
                for (int field = 0; field < fields.length; field++) {
                    String key = prefix + fields[field];
                    Object replacement = timingValue(profile.getAsJsonArray("stage_ticks"), field);
                    if (java.util.Objects.equals(original.get(key), replacement)) continue;
                    changed.put(key, original.get(key));
                    updated.set(key, replacement);
                }
            }
            if (changed.isEmpty()) continue;
            String output = new com.electronwill.nightconfig.toml.TomlWriter().writeToString(updated);
            var roundtrip = parser.parse(output);
            require(roundtrip.equals(updated), "Rhythm serialization changed values: " + file);
            changed.forEach(roundtrip::set);
            require(roundtrip.equals(original), "Rhythm update changed protected fields: " + file);
            var backup = java.nio.file.Path.of(file + ".pre-rhythm-v1");
            require(!java.nio.file.Files.exists(backup), "Rhythm backup already exists; inspect before retry: " + file);
            require(java.nio.file.Files.readString(path).equals(source), "Config changed during rhythm sync: " + file);
            java.nio.file.Files.copy(path, backup);
            java.nio.file.Files.writeString(path, output);
            System.out.println("Synced rhythm fields: " + changed.size() + " in " + file + "; all other values preserved");
        }
    }

    private static void migrateAbsoluteTickConfigs() throws java.io.IOException {
        var builder = new net.minecraftforge.common.ForgeConfigSpec.Builder();
        new com.tonywww.elder_bosses.platforms.config.PromisedConsortConfigValues(builder);
        var spec = builder.build();
        var defaults = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        spec.correct(defaults);
        for (String file : new String[]{"docs/config/elder-bosses-common.example.toml", "run/config/elder_bosses-common.toml",
                "versions/1.21.1-neoforge/run/config/elder_bosses-common.toml"}) {
            java.nio.file.Path path = java.nio.file.Path.of(file);
            if (!java.nio.file.Files.isRegularFile(path)) continue;
            var config = new com.electronwill.nightconfig.toml.TomlParser().parse(java.nio.file.Files.readString(path));
            for (String boss : new String[]{"malenia", "promised_consort"}) {
                com.electronwill.nightconfig.core.Config skills = config.get(boss + ".skills");
                for (var entry : skills.entrySet()) {
                    if (entry.getValue() instanceof com.electronwill.nightconfig.core.Config skill) skill.remove("cast_speed_multiplier");
                }
            }
            String prefix = "promised_consort.skills.right_combo_tempest.";
            java.util.List<Integer> windup = config.get(prefix + "windup_ticks");
            java.util.List<Integer> active = config.get(prefix + "active_ticks");
            java.util.List<Integer> recovery = config.get(prefix + "recovery_ticks");
            if (windup.equals(java.util.List.of(10, 10, 10, 18)) && active.equals(java.util.List.of(3, 3, 3, 8))
                    && recovery.equals(java.util.List.of(6, 6, 6, 26))) {
                config.set(prefix + "windup_ticks", java.util.List.of(12, 13, 14, 15, 18));
                config.set(prefix + "active_ticks", java.util.List.of(3, 3, 3, 4, 4));
                config.set(prefix + "recovery_ticks", java.util.List.of(5, 6, 8, 8, 33));
            }
            String[] fields = {"windup_ticks", "active_ticks", "recovery_ticks"};
            for (PromisedConsortActionId action : PromisedConsortActionId.values()) {
                if (action == PromisedConsortActionId.LION_CLAW_DOUBLE) continue;
                String skillPath = "promised_consort.skills." + action.serializedName() + ".";
                Object defaultWindup = defaults.get(skillPath + fields[0]);
                if (!(defaultWindup instanceof java.util.List<?> expectedStages)) continue;
                Object configuredWindup = config.get(skillPath + fields[0]);
                if (configuredWindup instanceof java.util.List<?> configured && configured.size() == expectedStages.size()) continue;
                int[] legacy = PromisedConsortAnimationTimeline.durations(action);
                for (int field = 0; field < fields.length; field++) {
                    if (action == PromisedConsortActionId.CONSORT_METEOR) {
                        require(((Number) config.get(skillPath + "script_ticks")).intValue() == 150, "Custom meteor timing needs explicit migration: " + file);
                        break;
                    }
                    java.util.List<Integer> expected = new java.util.ArrayList<>();
                    for (int index = field; index < legacy.length; index += 3) expected.add(legacy[index]);
                    Object actual = config.get(skillPath + fields[field]);
                    java.util.List<?> actualList = actual instanceof Number number ? java.util.List.of(number.intValue()) : (java.util.List<?>) actual;
                    require(actualList.equals(expected), "Custom timing must not be overwritten during component migration: " + skillPath + fields[field]);
                }
                for (String field : fields) config.set(skillPath + field, defaults.get(skillPath + field));
                if (action == PromisedConsortActionId.CONSORT_METEOR) config.remove(skillPath + "script_ticks");
                if (action == PromisedConsortActionId.L_COMBO_BLOODFLAME) {
                    config.remove(skillPath + "burst_tick");
                    config.remove(skillPath + "fissure_lifetime_ticks");
                }
            }
            for (var action : com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId.values()) {
                var stages = com.tonywww.elder_bosses.boss.malenia.config.MaleniaSkillConfigSnapshot.defaultComponentStages(action);
                if (stages.isEmpty()) continue;
                String skillPath = "malenia.skills." + action.serializedName() + ".";
                if (config.contains(skillPath + "components.windup_ticks")) continue;
                int[] legacy = com.tonywww.elder_bosses.boss.malenia.sync.MaleniaAnimationTimeline.durations(action);
                for (int field = 0; field < fields.length; field++) require(((Number) config.get(skillPath + fields[field])).intValue() == legacy[field],
                    "Custom Malenia timing needs explicit migration: " + skillPath + fields[field]);
                config.set(skillPath + "components.windup_ticks", stages.stream().map(stage -> stage.windupTicks()).toList());
                config.set(skillPath + "components.active_ticks", stages.stream().map(stage -> stage.activeTicks()).toList());
                config.set(skillPath + "components.recovery_ticks", stages.stream().map(stage -> stage.recoveryTicks()).toList());
                for (String field : fields) config.remove(skillPath + field);
                for (String obsolete : new String[]{"finisher_delay_ticks", "burst_lock_ticks", "target_lock_tick", "telegraph_start_tick", "phantom_interval_ticks"})
                    config.remove(skillPath + obsolete);
            }
            java.nio.file.Path backup = java.nio.file.Path.of(file + ".pre-malenia-component-ticks");
            if (!java.nio.file.Files.exists(backup)) java.nio.file.Files.copy(path, backup);
            java.nio.file.Files.writeString(path, new com.electronwill.nightconfig.toml.TomlWriter().writeToString(config));
        }
    }

    private static void syncBatchConfigs(boolean write) throws java.io.IOException {
        var previous = new java.util.LinkedHashMap<String, int[][]>();
        previous.put("gravity_dive", new int[][]{{24,1},{1,1},{0,27}});
        previous.put("left_combo_bloodflame", new int[][]{{13,12,12},{3,4,8},{8,0,4}});
        previous.put("lion_claw", new int[][]{{22},{5},{28}});
        previous.put("lion_claw_double", new int[][]{{16},{5},{34}});
        previous.put("stomp", new int[][]{{14},{5},{24}});
        previous.put("starcaller_cry", new int[][]{{30,0},{9,1},{0,34}});
        previous.put("spiral_assault", new int[][]{{26,0},{7,1},{0,30}});
        previous.put("gravity_meteor", new int[][]{{32,0,0,0,0,0,0,0,0,0,0,0},{6,6,6,6,6,6,6,8,5,5,5,1},{0,0,0,0,0,0,0,0,0,0,0,20}});
        previous.put("light_of_miquella", new int[][]{{44,4,0,0,0,0,0,0,0},{10,1,1,1,1,1,1,1,1},{0,0,0,0,0,0,0,0,30}});
        previous.put("ring_of_light", new int[][]{{24},{5},{30}});
        previous.put("lightspeed_slash", new int[][]{{28,0,0,0},{6,6,11,1},{0,0,0,34}});
        previous.put("lightspeed_dash", new int[][]{{26,0,0,0,0,1},{4,4,4,7,1,1},{0,0,0,0,0,34}});
        previous.put("lightspeed_side_dash", new int[][]{{18,0,0,0},{5,5,9,1},{0,0,0,30}});
        previous.put("promised_consort", new int[][]{{26,0,0,0,0,0,0,0},{8,10,10,14,2,3,3,4},{0,0,0,0,0,0,0,48}});
        previous.put("enhanced_earthheave", new int[][]{{20,2,1,1,1},{1,1,1,1,1},{0,0,0,0,48}});
        previous.put("consort_meteor", new int[][]{{0,0,0,0,1},{50,41,30,1,1},{0,0,0,0,26}});
        var builder = new net.minecraftforge.common.ForgeConfigSpec.Builder();
        new com.tonywww.elder_bosses.platforms.config.PromisedConsortConfigValues(builder);
        var defaults = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        builder.build().correct(defaults);
        var parser = new com.electronwill.nightconfig.toml.TomlParser();
        for (String file : new String[]{"docs/config/elder-bosses-common.example.toml", "run/config/elder_bosses-common.toml",
                "versions/1.21.1-neoforge/run/config/elder_bosses-common.toml"}) {
            var path = java.nio.file.Path.of(file);
            String source = java.nio.file.Files.readString(path);
            var original = parser.parse(source);
            var updated = parser.parse(source);
            var changed = new java.util.LinkedHashMap<String, Object>();
            for (var entry : previous.entrySet()) {
                boolean followup = entry.getKey().equals("lion_claw_double");
                String prefix = "promised_consort.skills." + (followup ? "lion_claw.double_" : entry.getKey() + ".");
                String[] fields = {"windup_ticks", "active_ticks", "recovery_ticks"};
                boolean oldDefaults = true, currentDefaults = true;
                for (int field = 0; field < fields.length; field++) {
                    Object actual = original.get(prefix + fields[field]);
                    Object expected = entry.getValue()[field].length == 1 ? entry.getValue()[field][0]
                            : java.util.Arrays.stream(entry.getValue()[field]).boxed().toList();
                    oldDefaults &= java.util.Objects.equals(actual, expected);
                    currentDefaults &= java.util.Objects.equals(actual, defaults.get(prefix + fields[field]));
                }
                if (!oldDefaults) {
                    if (!currentDefaults) System.out.println("PRESERVED custom timing: " + file + " / " + entry.getKey());
                    continue;
                }
                for (String field : fields) {
                    String key = prefix + field;
                    if (java.util.Objects.equals(original.get(key), defaults.get(key))) continue;
                    changed.put(key, original.get(key));
                    updated.set(key, defaults.get(key));
                }
            }
            String output = new com.electronwill.nightconfig.toml.TomlWriter().writeToString(updated);
            var roundtrip = parser.parse(output);
            require(roundtrip.equals(updated), "Batch TOML serialization changed configuration values: " + file);
            for (var entry : changed.entrySet()) roundtrip.set(entry.getKey(), entry.getValue());
            require(roundtrip.equals(original), "Batch timing update changed protected configuration values: " + file);
            if (write && !changed.isEmpty()) {
                var backup = java.nio.file.Path.of(file + ".pre-motion-batch-v13");
                require(!java.nio.file.Files.exists(backup), "Existing batch backup requires explicit review: " + file);
                require(java.nio.file.Files.readString(path).equals(source), "Config changed during batch update: " + file);
                java.nio.file.Files.copy(path, backup);
                java.nio.file.Files.writeString(path, output);
            }
            System.out.println((write ? "Synced " : "Would sync ") + changed.size() + " timing fields: " + file
                    + "; custom timings and every non-time value preserved");
        }
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}