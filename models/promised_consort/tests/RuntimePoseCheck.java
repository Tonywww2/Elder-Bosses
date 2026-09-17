import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.eliotlash.mclib.math.IValue;
import software.bernie.geckolib.core.keyframe.Keyframe;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.util.JsonUtil;
import com.tonywww.elder_bosses.client.render.PursuitGait;
import com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortAttackPlan;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.combat.action.ActionStage;
import com.tonywww.elder_bosses.combat.action.ActionTimeline;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class RuntimePoseCheck {
    public static void main(String[] arguments) throws Exception {
    Path animationFile = Path.of(arguments.length == 0
        ? "src/main/resources/assets/elder_bosses/animations/entity/promised_consort.animation.json"
        : arguments[0]);
    JsonObject library = JsonParser.parseString(Files.readString(animationFile))
                .getAsJsonObject().getAsJsonObject("animations");
        BakedAnimations baked = JsonUtil.GEO_GSON.fromJson(library, BakedAnimations.class);
        int checks = 0;
        int mismatches = 0;
        for (String name : library.keySet()) {
            JsonObject rawBones = library.getAsJsonObject(name).getAsJsonObject("bones");
            for (var bone : baked.getAnimation(name).boneAnimations()) {
                for (String channel : List.of("rotation", "position", "scale")) {
                JsonObject values = rawBones.getAsJsonObject(bone.boneName()).getAsJsonObject(channel);
                if (values == null) continue;
                Map<Double, double[]> expected = new TreeMap<>();
                values.entrySet().forEach(entry -> {
                    var vector = entry.getValue().getAsJsonArray();
                    double[] components = new double[3];
                    for (int axis = 0; axis < 3; axis++) {
                        components[axis] = vector.get(axis).getAsDouble();
                        if (channel.equals("rotation")) components[axis] = Math.toRadians(components[axis]) * (axis < 2 ? -1 : 1);
                    }
                    expected.put(Double.parseDouble(entry.getKey()) * 20.0, components);
                });
                var stack = switch (channel) {
                    case "position" -> bone.positionKeyFrames();
                    case "scale" -> bone.scaleKeyFrames();
                    default -> bone.rotationKeyFrames();
                };
                var axes = List.of(stack.xKeyframes(), stack.yKeyframes(), stack.zKeyframes());
                for (var pose : expected.entrySet()) {
                    for (int axis = 0; axis < 3; axis++) {
                        double actual = sample(axes.get(axis), pose.getKey());
                        checks++;
                        if (Math.abs(actual - pose.getValue()[axis]) > 0.00001) {
                            if (mismatches++ < 12) System.out.println(name + "/" + bone.boneName() + "/" + channel + " @ " + pose.getKey()
                                    + " axis " + axis + ": expected " + pose.getValue()[axis] + ", baked " + actual);
                        }
                    }
                }
                }
            }
        }
        if (mismatches > 0) throw new AssertionError("GeckoLib keyframe pose differs: " + mismatches + "/" + checks);
        System.out.println("GeckoLib keyframe poses passed: " + library.size() + " clips, " + checks
            + " rotation/position/scale values; actual runtime deserializer");
        if (arguments.length == 0) checkPursuitLegs(library);
        if (List.of(arguments).contains("--candidate-pursuit")) checkCandidatePursuit(library, animationFile);
        if (List.of(arguments).contains("--pose-weight-model")) checkPoseWeightModel(library);
    }

    private static void checkPoseWeightModel(JsonObject candidate) throws Exception {
        JsonObject rig = JsonParser.parseString(Files.readString(Path.of("models/promised_consort/rig.json"))).getAsJsonObject();
        var model = new com.tonywww.elder_bosses.client.render.PromisedConsortModel();
        var bones = new java.util.LinkedHashMap<String, software.bernie.geckolib.cache.object.GeoBone>();
        for (var entry : rig.entrySet()) {
            var definition = entry.getValue().getAsJsonObject();
            var parent = definition.get("parent").isJsonNull() ? null : bones.get(definition.get("parent").getAsString());
            var bone = new software.bernie.geckolib.cache.object.GeoBone(parent, entry.getKey(), false, 0.0, false, false);
            Vector3f pivot = origin(rig, entry.getKey());
            bone.setPivotX(pivot.x); bone.setPivotY(pivot.y); bone.setPivotZ(pivot.z);
            if (parent != null) parent.getChildBones().add(bone);
            bones.put(entry.getKey(), bone);
        }
        model.getAnimationProcessor().registerGeoBone(bones.get("root"));
        var weightMethod = model.getClass().getDeclaredMethod("authoredPursuitWeight", double.class);
        weightMethod.setAccessible(true);
        var clips = new java.util.LinkedHashMap<String, JsonObject>();
        for (String filename : List.of("promised_consort.animation.json", "promised_consort_ranged.animation.json")) {
            var library = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/elder_bosses/animations/entity/" + filename))).getAsJsonObject().getAsJsonObject("animations");
            for (var entry : library.entrySet()) clips.put(entry.getKey(),entry.getValue().getAsJsonObject());
        }
        candidate.entrySet().forEach(entry -> clips.put("candidate:" + entry.getKey(),entry.getValue().getAsJsonObject()));
        var chain = List.of("root","control","pelvis","thigh_r","shin_r","foot_r","thigh_l","shin_l","foot_l");
        int samples = 0, lowPoseSamples = 0, highPoseSamples = 0;
        for (var clip : clips.entrySet()) {
            JsonObject tracks = clip.getValue().getAsJsonObject("bones");
            double duration = clip.getValue().get("animation_length").getAsDouble() * 20;
            for (double tick = 0; tick <= duration; tick += 0.5) {
                var transforms = new java.util.HashMap<String, org.joml.Matrix4f>();
                for (String name : chain) {
                    var bone = bones.get(name);
                    Vector3f angles = rotation(tracks,name,tick).getEulerAnglesZYX(new Vector3f());
                    bone.setRotX(angles.x); bone.setRotY(angles.y); bone.setRotZ(angles.z);
                    Vector3f pivot = origin(rig,name);
                    Vector3f relative = bone.getParent() == null ? pivot : pivot.sub(origin(rig,bone.getParent().getName()));
                    var local = new org.joml.Matrix4f().translation(relative).rotate(new Quaternionf().rotationZYX(angles.z,angles.y,angles.x));
                    if (bone.getParent() != null) local = new org.joml.Matrix4f(transforms.get(bone.getParent().getName())).mul(local);
                    transforms.put(name,local);
                }
                double first = transforms.get("foot_r").getTranslation(new Vector3f()).y;
                double second = transforms.get("foot_l").getTranslation(new Vector3f()).y;
                double length = Math.min(origin(rig,"thigh_r").distance(origin(rig,"shin_r")) + origin(rig,"shin_r").distance(origin(rig,"foot_r")),
                        origin(rig,"thigh_l").distance(origin(rig,"shin_l")) + origin(rig,"shin_l").distance(origin(rig,"foot_l")));
                double ratio = Math.abs(first-second)/length;
                for (double requested : new double[]{0,0.5,1}) {
                    double actual = (double) weightMethod.invoke(model,requested);
                    double expected = PursuitGait.authoredPoseWeight(requested,first,second,length);
                    if (!Double.isFinite(actual) || Math.abs(actual-expected) > 0.00001) throw new AssertionError("Model pose weight differs: " + clip.getKey() + "/" + tick);
                    if (ratio < 0.149 && actual != requested) throw new AssertionError("Ordinary posture gait weight changed");
                    if (ratio > 0.351 && actual != 0) throw new AssertionError("High pose gait weight not zero");
                    samples++;
                }
                if (ratio < 0.149) lowPoseSamples++;
                if (ratio > 0.351) highPoseSamples++;
            }
        }
        if (lowPoseSamples == 0 || highPoseSamples == 0) throw new AssertionError("Missing low/high posture coverage");
        System.out.println("Actual model weight entry passed: " + clips.size() + " clips, " + samples + " weights, low=" + lowPoseSamples + ", high=" + highPoseSamples
                + "; GeoBone rotational chain and unchanged rig, no entity render or visual acceptance");
    }

    private static void checkCandidatePursuit(JsonObject library, Path animationFile) throws Exception {
        var action = PromisedConsortActionId.L_COMBO_CROSS;
        String name = "animation.promised_consort." + action.serializedName();
        JsonObject bones = library.getAsJsonObject(name).getAsJsonObject("bones");
        JsonObject rigs = JsonParser.parseString(Files.readString(Path.of("models/promised_consort/rig.json"))).getAsJsonObject();
        JsonObject formal = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/elder_bosses/animations/entity/promised_consort.animation.json")))
                .getAsJsonObject().getAsJsonObject("animations");
        JsonObject walking = formal.getAsJsonObject("animation.promised_consort.walk").getAsJsonObject("bones");
        int samples = 0, raisedPoseSamples = 0;
        float maximumAdditionalAnkleTravel = 0, maximumAdditionalHeight = 0, minimumHeightDelta = Float.POSITIVE_INFINITY;
        float maximumRaisedKneeTravel = 0, maximumRaisedKneeHeight = 0;
        float maximumHeldKneeTravel = 0;
        int protectedHoldSamples = 0;
        for (double weight : new double[]{0, 0.5, 1}) {
            if (PursuitGait.authoredPoseWeight(weight, 2, 2, 40) != weight
                || PursuitGait.authoredPoseWeight(weight, 6, 0, 40) != weight
                || PursuitGait.authoredPoseWeight(weight, 15, 0, 40) != 0
                || PursuitGait.authoredPoseWeight(weight, 0, 15, 40) != 0) throw new AssertionError("Authored-pose weight bounds/symmetry");
            if (Math.abs(PursuitGait.authoredPoseWeight(weight, 10, 0, 40) - weight * 0.5) > 0.00001) throw new AssertionError("Pose weight does not blend smoothly");
        }
        if (PursuitGait.authoredPoseWeight(1, Double.NaN, 0, 40) != 0 || PursuitGait.authoredPoseWeight(1, 0, 0, 0) != 0) throw new AssertionError("Invalid pose weight data");
        JsonObject worstTravel = new JsonObject(), worstRaisedKnee = new JsonObject();
        var timelineKeys = new java.util.LinkedHashSet<String>();
        for (String configFile : List.of("docs/config/elder-bosses-common.example.toml", "run/config/elder_bosses-common.toml",
                "versions/1.21.1-neoforge/run/config/elder_bosses-common.toml")) {
            var config = new com.electronwill.nightconfig.toml.TomlParser().parse(Files.readString(Path.of(configFile)));
            String configPrefix = "promised_consort.skills.left_combo_cross.";
            List<? extends Number> windup = config.get(configPrefix + "windup_ticks");
            List<? extends Number> active = config.get(configPrefix + "active_ticks");
            List<? extends Number> recovery = config.get(configPrefix + "recovery_ticks");
            ActionStage[] stages = new ActionStage[windup.size()];
            for (int index = 0; index < stages.length; index++) stages[index] = new ActionStage(windup.get(index).intValue(), active.get(index).intValue(), recovery.get(index).intValue());
            ActionTimeline timeline = ActionTimeline.ofStages(stages);
            timelineKeys.add(timeline.stages().toString());
            for (double tick = 0; tick < timeline.totalTicks(); tick += 0.25) {
                if (PromisedConsortAttackPlan.repositionStage(action, timeline, (int) Math.floor(tick), 6) < 0) continue;
                double authored = com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortAnimationTimeline.sample(tick, action, timeline,
                        com.tonywww.elder_bosses.combat.action.SkillTuning.NEUTRAL);
                double[] footHeights = new double[2];
                double legLength = Double.POSITIVE_INFINITY;
                for (int sideIndex = 0; sideIndex < 2; sideIndex++) {
                    String side = sideIndex == 0 ? "r" : "l";
                    Vector3f upper = origin(rigs,"shin_" + side).sub(origin(rigs,"thigh_" + side));
                    Vector3f lower = origin(rigs,"foot_" + side).sub(origin(rigs,"shin_" + side));
                    legLength = Math.min(legLength, upper.length() + lower.length());
                    Vector3f ankle = rotation(bones,"thigh_" + side,authored).transform(rotation(bones,"shin_" + side,authored).transform(new Vector3f(lower)).add(upper))
                            .add(origin(rigs,"thigh_" + side));
                    footHeights[sideIndex] = rotation(bones,"root",authored).mul(rotation(bones,"control",authored)).mul(rotation(bones,"pelvis",authored)).transform(ankle).y;
                }
                for (double gaitTick = 0; gaitTick < 40; gaitTick += 2.5) for (float weight : new float[]{0, 0.5F, 1}) for (String side : List.of("r", "l")) {
                    float effectiveWeight = (float) PursuitGait.authoredPoseWeight(weight,footHeights[0],footHeights[1],legLength);
                    String thighName = "thigh_" + side, shinName = "shin_" + side, footName = "foot_" + side;
                    Vector3f upper = origin(rigs, shinName).sub(origin(rigs, thighName));
                    Vector3f lower = origin(rigs, footName).sub(origin(rigs, shinName));
                    Quaternionf thigh = rotation(bones, thighName, authored), shin = rotation(bones, shinName, authored), foot = rotation(bones, footName, authored);
                    Vector3f previous = new Quaternionf(thigh).transform(new Quaternionf(shin).transform(new Vector3f(lower)).add(upper));
                    Quaternionf sole = new Quaternionf(thigh).mul(shin).mul(foot);
                    Quaternionf thighDelta = rotation(walking, thighName, 0).invert().mul(rotation(walking, thighName, gaitTick));
                    Quaternionf shinDelta = rotation(walking, shinName, 0).invert().mul(rotation(walking, shinName, gaitTick));
                    thigh.mul(new Quaternionf().slerp(thighDelta, effectiveWeight));
                    shin.mul(new Quaternionf().slerp(shinDelta, effectiveWeight));
                    Quaternionf parent = rotation(bones, "root", authored).mul(rotation(bones, "control", authored)).mul(rotation(bones, "pelvis", authored));
                    Vector3f up = parent.invert().transform(new Vector3f(0, 1, 0));
                    Vector3f ankle = new Quaternionf(thigh).transform(new Quaternionf(shin).transform(new Vector3f(lower)).add(upper));
                        if (effectiveWeight > 0) thigh.premul(PursuitGait.supportCorrection(previous, ankle, up));
                        Vector3f corrected = effectiveWeight == 0 ? new Vector3f(previous)
                            : new Quaternionf(thigh).transform(new Quaternionf(shin).transform(new Vector3f(lower)).add(upper));
                    float heightDelta = corrected.dot(up) - previous.dot(up);
                    if (!Float.isFinite(heightDelta) || heightDelta < -0.0001F) throw new AssertionError("Candidate ankle lowered by pursuit: " + side + "/" + authored);
                    foot = new Quaternionf(thigh).mul(shin).invert().mul(sole);
                    if (1 - Math.abs(new Quaternionf(thigh).mul(shin).mul(foot).dot(sole)) > 0.00001) throw new AssertionError("Candidate sole rotated by pursuit");
                    float travel = corrected.distance(previous);
                    if (authored >= 35 && authored <= 40) {
                        if (effectiveWeight != 0 || travel > 0.0001F) throw new AssertionError("Authored high-knee hold changed by pursuit");
                        protectedHoldSamples++;
                        maximumHeldKneeTravel = Math.max(maximumHeldKneeTravel, travel);
                    }
                    if (weight == 0 && travel > 0.0001F) throw new AssertionError("Disabled pursuit alters candidate pose");
                    if (travel > maximumAdditionalAnkleTravel) {
                        maximumAdditionalAnkleTravel = travel;
                        worstTravel = pursuitCase(configFile, tick, authored, gaitTick, weight, side, previous, corrected);
                    }
                    maximumAdditionalHeight = Math.max(maximumAdditionalHeight, heightDelta);
                    minimumHeightDelta = Math.min(minimumHeightDelta, heightDelta);
                    if (side.equals("r") && authored >= 34 && authored <= 40) {
                        raisedPoseSamples++;
                        maximumRaisedKneeHeight = Math.max(maximumRaisedKneeHeight, heightDelta);
                        if (travel > maximumRaisedKneeTravel) {
                            maximumRaisedKneeTravel = travel;
                            worstRaisedKnee = pursuitCase(configFile, tick, authored, gaitTick, weight, side, previous, corrected);
                        }
                    }
                    samples++;
                }
            }
        }
        if (samples == 0 || raisedPoseSamples == 0) throw new AssertionError("Candidate raised-knee pursuit overlap was not checked");
        var gait = new PursuitGait();
        gait.sample(0, 0, 0, 0, true);
        gait.sample(1, 0.25, 0, 0, true);
        if (gait.sample(2, 0.5, 0, 0, false).weight() != 0) throw new AssertionError("Pursuit weight persists after its window closes");
        var report = new JsonObject();
        report.addProperty("candidate_sha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(animationFile))));
        report.addProperty("samples", samples);
        report.addProperty("raised_knee_samples", raisedPoseSamples);
        report.addProperty("configurations", 3);
        report.addProperty("distinct_timelines", timelineKeys.size());
        report.addProperty("maximum_additional_ankle_travel_model_units", maximumAdditionalAnkleTravel);
        report.addProperty("maximum_additional_height_model_units", maximumAdditionalHeight);
        report.addProperty("minimum_height_delta_model_units", minimumHeightDelta);
        report.addProperty("maximum_raised_knee_travel_model_units", maximumRaisedKneeTravel);
        report.addProperty("maximum_raised_knee_height_delta_model_units", maximumRaisedKneeHeight);
        if (protectedHoldSamples == 0) throw new AssertionError("No protected high-knee hold checked");
        report.addProperty("protected_hold_samples", protectedHoldSamples);
        report.addProperty("maximum_held_knee_travel_model_units", maximumHeldKneeTravel);
        report.add("worst_ankle_travel_case", worstTravel);
        report.add("worst_raised_knee_case", worstRaisedKnee);
        report.addProperty("case_scope", "Sweeps possible gait phase/weight combinations, not observed in-game trajectories. Production render also converts composed rotations through Euler angles; this helper-level check does not prove final renderer output.");
        report.addProperty("scope", "Production timing/support helper with current walk tracks and candidate leg matrices; mirrors renderer composition, not a full entity render test. Horizontal motion is measured, not certified natural.");
        Files.writeString(Path.of("models/promised_consort/previews/force_chain_pursuit_check.json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        System.out.println("Candidate pursuit: " + samples + " poses; raised-knee=" + raisedPoseSamples + "; ankle not lowered, sole preserved; max added travel="
                + maximumAdditionalAnkleTravel + ", height=" + maximumAdditionalHeight + " model units; visual review still required");
        System.out.println("Raised-knee stress case: travel=" + maximumRaisedKneeTravel + ", extra height=" + maximumRaisedKneeHeight + "; " + worstRaisedKnee);
    }

    private static JsonObject pursuitCase(String config, double tick, double authored, double gaitTick, float weight, String side,
                                         Vector3f previous, Vector3f corrected) {
        var record = new JsonObject();
        record.addProperty("configuration", config);
        record.addProperty("runtime_tick", tick);
        record.addProperty("authored_tick", authored);
        record.addProperty("gait_tick", gaitTick);
        record.addProperty("weight", weight);
        record.addProperty("side", side);
        var gson = new com.google.gson.Gson();
        record.add("ankle_before_hip_local", gson.toJsonTree(new float[]{previous.x, previous.y, previous.z}));
        record.add("ankle_after_hip_local", gson.toJsonTree(new float[]{corrected.x, corrected.y, corrected.z}));
        return record;
    }

    private static void checkPursuitLegs(JsonObject library) throws Exception {
        JsonObject rigs = JsonParser.parseString(Files.readString(Path.of("models/promised_consort/rig.json"))).getAsJsonObject();
        var configuration = new com.electronwill.nightconfig.toml.TomlParser().parse(Files.readString(Path.of("docs/config/elder-bosses-common.example.toml")));
        net.minecraft.SharedConstants.tryDetectVersion();
        var bootstrap = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrap.setAccessible(true);
        bootstrap.setBoolean(null, true);
        net.minecraft.core.registries.BuiltInRegistries.bootStrap();
        com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig.SPEC.correct(configuration);
        com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig.SPEC.setConfig(configuration);
        var catalog = new com.tonywww.elder_bosses.boss.promisedconsort.action.PromisedConsortActionCatalog(
            com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig.VALUES.promisedConsortSkillSnapshot());
        JsonObject walking = library.getAsJsonObject("animation.promised_consort.walk").getAsJsonObject("bones");
        int samples = 0;
        for (PromisedConsortActionId action : PromisedConsortActionId.values()) {
            if(action.rangedDefense()) continue;
            var timeline = catalog.get(action).timeline();
            JsonObject bones = library.getAsJsonObject("animation.promised_consort." + action.serializedName()).getAsJsonObject("bones");
            for (int tick = 0; tick < timeline.totalTicks(); tick++) {
                if (PromisedConsortAttackPlan.repositionStage(action, timeline, tick, 6) < 0) continue;
                double authored = com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortAnimationTimeline.sample(tick, action, timeline,
                    com.tonywww.elder_bosses.combat.action.SkillTuning.NEUTRAL);
                for (double gaitTick = 0; gaitTick < 40; gaitTick += 2.5) for (String side : List.of("r", "l")) {
                    String thighName = "thigh_" + side, shinName = "shin_" + side, footName = "foot_" + side;
                    Vector3f upper = origin(rigs, shinName).sub(origin(rigs, thighName));
                    Vector3f lower = origin(rigs, footName).sub(origin(rigs, shinName));
                    Quaternionf thigh = rotation(bones, thighName, authored), shin = rotation(bones, shinName, authored);
                    Quaternionf foot = rotation(bones, footName, authored);
                    Vector3f previous = new Quaternionf(thigh).transform(new Quaternionf(shin).transform(new Vector3f(lower)).add(upper));
                    Quaternionf sole = new Quaternionf(thigh).mul(shin).mul(foot);
                    thigh.mul(rotation(walking, thighName, 0).invert().mul(rotation(walking, thighName, gaitTick)));
                    shin.mul(rotation(walking, shinName, 0).invert().mul(rotation(walking, shinName, gaitTick)));
                    Quaternionf parent = rotation(bones, "root", authored).mul(rotation(bones, "control", authored)).mul(rotation(bones, "pelvis", authored));
                    Vector3f up = parent.invert().transform(new Vector3f(0, 1, 0));
                    Vector3f ankle = new Quaternionf(thigh).transform(new Quaternionf(shin).transform(new Vector3f(lower)).add(upper));
                    thigh.premul(PursuitGait.supportCorrection(previous, ankle, up));
                    Vector3f corrected = new Quaternionf(thigh).transform(new Quaternionf(shin).transform(new Vector3f(lower)).add(upper));
                    if (corrected.dot(up) < previous.dot(up) - 0.0001) throw new AssertionError("Pursuit foot sinks: " + action + " @ " + tick);
                    foot = new Quaternionf(thigh).mul(shin).invert().mul(sole);
                    Quaternionf restored = new Quaternionf(thigh).mul(shin).mul(foot);
                    if (1 - Math.abs(restored.dot(sole)) > 0.00001) throw new AssertionError("Pursuit sole changes direction: " + action);
                    samples++;
                }
            }
        }
        if (samples == 0) throw new AssertionError("No actual pursuit poses checked");
        System.out.println("Pursuit support helper passed: " + samples + " actual asset poses on current example-config clocks; both feet, full gait stress, no lower support or sole rotation drift; model weight tested separately");
    }

    private static Vector3f origin(JsonObject rigs, String name) {
        var values = rigs.getAsJsonObject(name).getAsJsonArray("origin");
        return new Vector3f(values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat());
    }

    private static Quaternionf rotation(JsonObject bones, String name, double tick) {
        if (!bones.has(name) || !bones.getAsJsonObject(name).has("rotation")) return new Quaternionf();
        var values = bones.getAsJsonObject(name).getAsJsonObject("rotation");
        var keys = values.entrySet().stream().sorted(java.util.Comparator.comparingDouble(entry -> Double.parseDouble(entry.getKey()))).toList();
        double[] times = new double[keys.size()];
        float[][] angles = new float[keys.size()][3];
        for (int index = 0; index < keys.size(); index++) {
            times[index] = Double.parseDouble(keys.get(index).getKey()) * 20;
            for (int axis = 0; axis < 3; axis++) angles[index][axis] = (float) Math.toRadians(
                    keys.get(index).getValue().getAsJsonArray().get(axis).getAsDouble() * (axis < 2 ? -1 : 1));
        }
        float[] pose = new PursuitGait.RotationTrack(times, angles).at(tick);
        return new Quaternionf().rotationZYX(pose[2], pose[1], pose[0]);
    }

    private static double sample(List<Keyframe<IValue>> keys, double tick) {
        double cursor = 0;
        for (Keyframe<IValue> key : keys) {
            if (key.length() < 0) throw new AssertionError("Negative baked keyframe length: " + key.length());
            double end = cursor + key.length();
            if (tick <= end + 0.000001) {
                double fraction = key.length() == 0 ? 1 : Math.max(0, Math.min(1, (tick - cursor) / key.length()));
                return key.startValue().get() + fraction * (key.endValue().get() - key.startValue().get());
            }
            cursor = end;
        }
        return keys.get(keys.size() - 1).endValue().get();
    }
}