package com.tonywww.elder_bosses.client.render;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortCombatState;
import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import com.tonywww.elder_bosses.platforms.client.PlatformPromisedConsortGeoModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class PromisedConsortModel extends PlatformPromisedConsortGeoModel {
    private static final ResourceLocation MODEL =
            PlatformResourceLocation.id("geo/entity/promised_consort.geo.json");
    private static final ResourceLocation TEXTURE =
            PlatformResourceLocation.id("textures/entity/promised_consort/promised_consort.png");
    private static final ResourceLocation ANIMATION =
            PlatformResourceLocation.id("animations/entity/promised_consort.animation.json");
        private static final ResourceLocation RANGED_ANIMATION =
            PlatformResourceLocation.id("animations/entity/promised_consort_ranged.animation.json");
    private final Map<PromisedConsortEntity, PoseHistory> histories = new WeakHashMap<>();
    private Object gaitAnimation;
    private Map<String, PursuitGait.RotationTrack> gaitTracks = Map.of();

    @Override
    public ResourceLocation getModelResource(PromisedConsortEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(PromisedConsortEntity entity) {
        return TEXTURE;
    }

    @Override
    public RenderType getRenderType(PromisedConsortEntity entity, ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public ResourceLocation getAnimationResource(PromisedConsortEntity entity) {
        return entity.actionId().filter(action -> action.rangedDefense() && entity.animationClip().equals(action.serializedName())).isPresent()
            ? RANGED_ANIMATION : ANIMATION;
    }

    @Override
    protected void afterAnimations(PromisedConsortEntity entity) {
        blendPose(entity);
        applyPursuitGait(entity);
        applySecondaryMotion(entity);
        for (String name : new String[]{"blade_root_l", "blade_tip_l", "blade_root_r", "blade_tip_r"}) {
            getBone(name).ifPresent(bone -> bone.setTrackingMatrices(true));
        }
        boolean transitioning = entity.combatState() == PromisedConsortCombatState.TRANSITION;
        boolean visible = entity.miquellaVisible();
        getBone("miquella_root").ifPresent(bone -> {
            bone.setHidden(!visible);
            bone.setChildrenHidden(!visible);
            if (!transitioning) {
                bone.setScaleX(1.0F);
                bone.setScaleY(1.0F);
                bone.setScaleZ(1.0F);
            }
        });
    }

    private void applyPursuitGait(PromisedConsortEntity entity) {
        double partial = entity.animationFrameTime() - entity.tickCount;
        PursuitGait.Sample step = histories.get(entity).gait.sample(entity.animationFrameTime(),
                Mth.lerp(partial, entity.xo, entity.getX()), Mth.lerp(partial, entity.yo, entity.getY()),
                Mth.lerp(partial, entity.zo, entity.getZ()), entity.isPursuing());
        if (step.weight() <= 0) return;
        double weight = authoredPursuitWeight(step.weight());
        if (weight <= 0) return;
        var walk = getAnimation(entity, "animation.promised_consort.walk");
        if (walk == null) return;
        if (walk != gaitAnimation) {
            gaitTracks = loadGaitTracks();
            gaitAnimation = walk;
        }
        Map<String, Quaternionf> deltas = new HashMap<>();
        for (var track : gaitTracks.entrySet()) {
            String name = track.getKey();
            float[][] angles = {track.getValue().at(0), track.getValue().at(step.tick())};
            Quaternionf neutral = new Quaternionf().rotationZYX(angles[0][2], angles[0][1], angles[0][0]);
            Quaternionf gait = new Quaternionf().rotationZYX(angles[1][2], angles[1][1], angles[1][0]);
            deltas.put(name, new Quaternionf().slerp(neutral.invert().mul(gait), (float) weight));
        }
        for (String side : new String[]{"r", "l"}) {
            GeoBone thigh = getBone("thigh_" + side).orElse(null), shin = getBone("shin_" + side).orElse(null);
            GeoBone foot = getBone("foot_" + side).orElse(null);
            if (thigh == null || shin == null || foot == null) continue;
            Vector3f upper = new Vector3f(shin.getPivotX() - thigh.getPivotX(), shin.getPivotY() - thigh.getPivotY(), shin.getPivotZ() - thigh.getPivotZ());
            Vector3f lower = new Vector3f(foot.getPivotX() - shin.getPivotX(), foot.getPivotY() - shin.getPivotY(), foot.getPivotZ() - shin.getPivotZ());
            Vector3f previousAnkle = rotation(thigh).transform(rotation(shin).transform(new Vector3f(lower)).add(upper));
            Quaternionf sole = rotation(thigh).mul(rotation(shin)).mul(rotation(foot));
            setRotation(thigh, rotation(thigh).mul(deltas.getOrDefault(thigh.getName(), new Quaternionf())));
            setRotation(shin, rotation(shin).mul(deltas.getOrDefault(shin.getName(), new Quaternionf())));
            Quaternionf parent = new Quaternionf();
            for (GeoBone ancestor = thigh.getParent(); ancestor != null; ancestor = ancestor.getParent()) parent.premul(rotation(ancestor));
            Vector3f up = parent.invert().transform(new Vector3f(0, 1, 0));
            Vector3f ankle = rotation(thigh).transform(rotation(shin).transform(new Vector3f(lower)).add(upper));
            setRotation(thigh, PursuitGait.supportCorrection(previousAnkle, ankle, up).mul(rotation(thigh)));
            setRotation(foot, rotation(thigh).mul(rotation(shin)).invert().mul(sole));
        }
        deltas.forEach((name, delta) -> {
            if (name.startsWith("tasset_")) getBone(name).ifPresent(bone -> setRotation(bone, rotation(bone).mul(delta)));
        });
    }

    private double authoredPursuitWeight(double requested) {
        double[] heights = new double[2];
        double length = Double.POSITIVE_INFINITY;
        for (int index = 0; index < 2; index++) {
            String side = index == 0 ? "r" : "l";
            GeoBone thigh = getBone("thigh_" + side).orElse(null), shin = getBone("shin_" + side).orElse(null);
            GeoBone foot = getBone("foot_" + side).orElse(null);
            if (thigh == null || shin == null || foot == null) return 0;
            Vector3f upper = new Vector3f(shin.getPivotX() - thigh.getPivotX(), shin.getPivotY() - thigh.getPivotY(), shin.getPivotZ() - thigh.getPivotZ());
            Vector3f lower = new Vector3f(foot.getPivotX() - shin.getPivotX(), foot.getPivotY() - shin.getPivotY(), foot.getPivotZ() - shin.getPivotZ());
            length = Math.min(length, upper.length() + lower.length());
            Vector3f ankle = rotation(thigh).transform(rotation(shin).transform(new Vector3f(lower)).add(upper))
                    .add(thigh.getPivotX(), thigh.getPivotY(), thigh.getPivotZ());
            Quaternionf parent = new Quaternionf();
            for (GeoBone ancestor = thigh.getParent(); ancestor != null; ancestor = ancestor.getParent()) parent.premul(rotation(ancestor));
            heights[index] = parent.transform(ankle).y;
        }
        return PursuitGait.authoredPoseWeight(requested, heights[0], heights[1], length);
    }

    private static Map<String, PursuitGait.RotationTrack> loadGaitTracks() {
        Map<String, PursuitGait.RotationTrack> tracks = new HashMap<>();
        try (var reader = new java.io.InputStreamReader(Minecraft.getInstance().getResourceManager()
                .getResource(ANIMATION).orElseThrow().open(), java.nio.charset.StandardCharsets.UTF_8)) {
            var bones = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations")
                    .getAsJsonObject("animation.promised_consort.walk").getAsJsonObject("bones");
            for (String name : bones.keySet()) {
                if (!name.startsWith("thigh_") && !name.startsWith("shin_") && !name.startsWith("tasset_")) continue;
                var keys = bones.getAsJsonObject(name).getAsJsonObject("rotation").entrySet().stream()
                        .sorted(java.util.Comparator.comparingDouble(entry -> Double.parseDouble(entry.getKey()))).toList();
                double[] ticks = new double[keys.size()];
                float[][] angles = new float[keys.size()][3];
                for (int index = 0; index < keys.size(); index++) {
                    ticks[index] = Double.parseDouble(keys.get(index).getKey()) * 20;
                    for (int axis = 0; axis < 3; axis++) angles[index][axis] = (float) Math.toRadians(
                            keys.get(index).getValue().getAsJsonArray().get(axis).getAsDouble() * (axis < 2 ? -1 : 1));
                }
                tracks.put(name, new PursuitGait.RotationTrack(ticks, angles));
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot load pursuit gait from the model animation", exception);
        }
        return Map.copyOf(tracks);
    }

    private static Quaternionf rotation(GeoBone bone) {
        return new Quaternionf().rotationZYX(bone.getRotZ(), bone.getRotY(), bone.getRotX());
    }

    private static void setRotation(GeoBone bone, Quaternionf rotation) {
        Vector3f angles = rotation.getEulerAnglesZYX(new Vector3f());
        bone.setRotX(angles.x);
        bone.setRotY(angles.y);
        bone.setRotZ(angles.z);
    }

    private void applySecondaryMotion(PromisedConsortEntity entity) {
        PoseHistory history = histories.get(entity);
        double speed = Math.hypot(entity.getX() - entity.xo, entity.getZ() - entity.zo);
        double vertical = entity.getY() - entity.yo;
        boolean inactive = entity.combatState() == PromisedConsortCombatState.STUNNED || !entity.isAlive();
        double strength = inactive || speed > 2 || Math.abs(vertical) > 2 ? 0 : entity.hasSynchronizedAnimation() ? 0.35 : 1;
        SecondaryMotionSpring.Offset offset = history.secondary.sample(entity.animationFrameTime(), speed,
                Mth.wrapDegrees(entity.yBodyRot - entity.yBodyRotO), vertical, strength);
        secondaryBone("cape_middle", offset, 0.2, 0.12);
        secondaryBone("cape_end", offset, 0.35, 0.2);
        for (int index = 1; index <= 10; index++) {
            String number = index < 10 ? "0" + index : Integer.toString(index);
            secondaryBone("miquella_lock_" + number + "_middle", offset, 0.1, 0.08);
            secondaryBone("miquella_lock_" + number + "_end", offset, 0.22, 0.18);
        }
        for (int index = 1; index <= 12; index++) {
            String number = index < 10 ? "0" + index : Integer.toString(index);
            secondaryBone("mane_lock_" + number, offset, 0.1, 0.1);
        }
    }

    private void secondaryBone(String name, SecondaryMotionSpring.Offset offset, double pitchWeight, double rollWeight) {
        getBone(name).ifPresent(bone -> {
            bone.setRotX(bone.getRotX() + (float) Math.toRadians(offset.pitchDegrees() * pitchWeight));
            bone.setRotZ(bone.getRotZ() + (float) Math.toRadians(offset.rollDegrees() * rollWeight));
        });
    }

    private void blendPose(PromisedConsortEntity entity) {
        PoseHistory history = histories.computeIfAbsent(entity, unused -> new PoseHistory());
        double now = entity.animationFrameTime();
        boolean sameFrame = history.lastFrame == now;
        if (!sameFrame && (!history.clip.equals(entity.animationClip()) || history.seed != entity.actionSeed())) {
            history.source = new HashMap<>(history.previous);
            history.started = now;
            history.clip = entity.animationClip();
            history.seed = entity.actionSeed();
            history.duration = entity.combatState() == PromisedConsortCombatState.STUNNED ? 0
                    : entity.hasSynchronizedAnimation() ? Math.max(0, 1.5 - entity.animationTime()) : 3;
        }
        float progress = history.duration <= 0 ? 1 : (float) Math.max(0, Math.min(1, (now - history.started) / history.duration));
        if (entity.hasSynchronizedAnimation() && entity.animationTime() >= 1.5) progress = 1;
        float weight = progress * progress * (3 - 2 * progress);
        getBone("root").ifPresent(root -> blendBone(root, history, weight, sameFrame));
        history.lastFrame = now;
        if (progress >= 1) history.source = Map.of();
    }

    private static void blendBone(GeoBone bone, PoseHistory history, float weight, boolean sameFrame) {
        BonePose source = sameFrame ? history.previous.get(bone.getName()) : history.source.get(bone.getName());
        if (source != null && !bone.getName().equals("root") && !bone.getName().equals("control") && (sameFrame || weight < 1)) {
            float amount = sameFrame ? 0 : weight;
            Quaternionf rotation = new Quaternionf().rotationZYX(source.rotationZ, source.rotationY, source.rotationX)
                    .slerp(new Quaternionf().rotationZYX(bone.getRotZ(), bone.getRotY(), bone.getRotX()), amount);
            Vector3f angles = rotation.getEulerAnglesZYX(new Vector3f());
            bone.setRotX(angles.x);
            bone.setRotY(angles.y);
            bone.setRotZ(angles.z);
            bone.setPosX(source.positionX + (bone.getPosX() - source.positionX) * amount);
            bone.setPosY(source.positionY + (bone.getPosY() - source.positionY) * amount);
            bone.setPosZ(source.positionZ + (bone.getPosZ() - source.positionZ) * amount);
        }
        if (!sameFrame) history.previous.put(bone.getName(), new BonePose(bone.getRotX(), bone.getRotY(), bone.getRotZ(),
                bone.getPosX(), bone.getPosY(), bone.getPosZ()));
        for (GeoBone child : bone.getChildBones()) blendBone(child, history, weight, sameFrame);
    }

    private static final class PoseHistory {
        private final PursuitGait gait = new PursuitGait();
        private final SecondaryMotionSpring secondary = new SecondaryMotionSpring();
        private final Map<String, BonePose> previous = new HashMap<>();
        private Map<String, BonePose> source = Map.of();
        private String clip = "";
        private long seed;
        private double started;
        private double duration;
        private double lastFrame = -1;
    }

    private record BonePose(float rotationX, float rotationY, float rotationZ,
                            float positionX, float positionY, float positionZ) {
    }
}
