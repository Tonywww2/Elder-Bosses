package com.tonywww.elder_bosses.client.render;

import com.tonywww.elder_bosses.boss.malenia.MaleniaEntity;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaCombatState;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaPhase;
import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import com.tonywww.elder_bosses.platforms.client.PlatformMaleniaGeoModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class MaleniaModel extends PlatformMaleniaGeoModel {
    private static final ResourceLocation MODEL = PlatformResourceLocation.id("geo/malenia/malenia.geo.json");
    private static final ResourceLocation TEXTURE = PlatformResourceLocation.id("textures/entity/malenia/malenia.png");
    private static final ResourceLocation ANIMATION = PlatformResourceLocation.id("animations/malenia/malenia.animation.json");
    private static final List<String> ARMOR = List.of(
            "helm", "armor_torso", "armor_shoulder_l", "armor_shoulder_r", "armor_waist",
            "cape_01", "skirt_front", "skirt_back", "skirt_l", "skirt_r");
    private final Map<MaleniaEntity, PoseHistory> poseHistory = new WeakHashMap<>();

    @Override
    public ResourceLocation getModelResource(MaleniaEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(MaleniaEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(MaleniaEntity entity) {
        return ANIMATION;
    }

    @Override
    protected void afterAnimations(MaleniaEntity entity) {
        blendTransition(entity);
        applySecondaryMotion(entity);
        boolean transitioning = entity.combatState() == MaleniaCombatState.TRANSITION;
        boolean secondPhase = entity.activePhase() == MaleniaPhase.PHASE_TWO;
        boolean scriptedLayers = transitioning || entity.combatState() == MaleniaCombatState.DEFEATED;
        for (String name : ARMOR) {
            visible(name, transitioning || !secondPhase, !transitioning);
        }
        visible("phase_two_body", transitioning || secondPhase, !transitioning);
        visible("phase_two_hair", transitioning || secondPhase, !transitioning);
        visible("wing_root_l", transitioning || secondPhase, !transitioning);
        visible("wing_root_r", transitioning || secondPhase, !transitioning);
        boolean flower = scriptedLayers || entity.animationClip().equals("scarlet_aeonia");
        visible("aeonia_core", flower, false);
        if (!scriptedLayers) {
            getBone("pelvis").ifPresent(bone -> {
                bone.setScaleX(1.0F);
                bone.setScaleY(1.0F);
                bone.setScaleZ(1.0F);
            });
        }
    }

    private void applySecondaryMotion(MaleniaEntity entity) {
        PoseHistory history = poseHistory.get(entity);
        double speed = Math.hypot(entity.getX() - entity.xo, entity.getZ() - entity.zo);
        double vertical = entity.getY() - entity.yo;
        boolean inactive = entity.combatState() == MaleniaCombatState.DORMANT
                || entity.combatState() == MaleniaCombatState.DEFEATED || entity.combatState() == MaleniaCombatState.STUNNED;
        double strength = inactive || speed > 2 || Math.abs(vertical) > 2 ? 0 : entity.hasSynchronizedAnimation() ? 0.4 : 1;
        SecondaryMotionSpring.Offset offset = history.secondary.sample(entity.animationFrameTime(), speed,
                Mth.wrapDegrees(entity.yBodyRot - entity.yBodyRotO), vertical, strength);
        secondaryBone("cape_02", offset, 0.25, 0.18);
        secondaryBone("cape_03", offset, 0.45, 0.28);
        for (int index = 1; index <= 6; index++) {
            secondaryBone("hair_end_0" + index, offset, 0.32 + index * 0.02, 0.25);
        }
    }

    private void secondaryBone(String name, SecondaryMotionSpring.Offset offset, double pitchWeight, double rollWeight) {
        getBone(name).ifPresent(bone -> {
            bone.setRotX(bone.getRotX() + (float) Math.toRadians(offset.pitchDegrees() * pitchWeight));
            bone.setRotZ(bone.getRotZ() + (float) Math.toRadians(offset.rollDegrees() * rollWeight));
        });
    }

    private void blendTransition(MaleniaEntity entity) {
        PoseHistory history = poseHistory.computeIfAbsent(entity, unused -> new PoseHistory());
        double frameTime = entity.animationFrameTime();
        if (history.lastFrameTime == frameTime) {
            getBone("root").ifPresent(root -> restoreFrame(root, history.previous));
            return;
        }
        String clip = entity.animationClip();
        if (!clip.equals(history.clip) || entity.actionSeed() != history.seed) {
            history.source = new HashMap<>(history.previous);
            history.started = frameTime;
            history.duration = history.source.isEmpty() || entity.combatState() == MaleniaCombatState.STUNNED
                    ? 0.0 : entity.hasSynchronizedAnimation() ? Math.max(0.0, 2.0 - entity.animationTime()) : 4.0;
            history.clip = clip;
            history.seed = entity.actionSeed();
        }
        float weight = MaleniaPoseTransition.weight(frameTime - history.started, history.duration);
        getBone("root").ifPresent(root -> blendBone(root, history, weight));
        history.lastFrameTime = frameTime;
        if (weight >= 1.0F) {
            history.source = Map.of();
        }
    }

    private void blendBone(GeoBone bone, PoseHistory history, float weight) {
        BonePose source = history.source.get(bone.getName());
        if (source != null && weight < 1.0F && !bone.getName().equals("root") && !bone.getName().equals("control")) {
            bone.setRotX(MaleniaPoseTransition.rotation(source.rotationX(), bone.getRotX(), weight));
            bone.setRotY(MaleniaPoseTransition.rotation(source.rotationY(), bone.getRotY(), weight));
            bone.setRotZ(MaleniaPoseTransition.rotation(source.rotationZ(), bone.getRotZ(), weight));
            bone.setPosX(MaleniaPoseTransition.position(source.positionX(), bone.getPosX(), weight));
            bone.setPosY(MaleniaPoseTransition.position(source.positionY(), bone.getPosY(), weight));
            bone.setPosZ(MaleniaPoseTransition.position(source.positionZ(), bone.getPosZ(), weight));
        }
        history.previous.put(bone.getName(), new BonePose(bone.getRotX(), bone.getRotY(), bone.getRotZ(),
                bone.getPosX(), bone.getPosY(), bone.getPosZ()));
        for (GeoBone child : bone.getChildBones()) {
            blendBone(child, history, weight);
        }
    }

    private void restoreFrame(GeoBone bone, Map<String, BonePose> poses) {
        BonePose pose = poses.get(bone.getName());
        if (pose != null) {
            bone.setRotX(pose.rotationX());
            bone.setRotY(pose.rotationY());
            bone.setRotZ(pose.rotationZ());
            bone.setPosX(pose.positionX());
            bone.setPosY(pose.positionY());
            bone.setPosZ(pose.positionZ());
        }
        for (GeoBone child : bone.getChildBones()) {
            restoreFrame(child, poses);
        }
    }

    private static final class PoseHistory {
        private final SecondaryMotionSpring secondary = new SecondaryMotionSpring();
        private final Map<String, BonePose> previous = new HashMap<>();
        private Map<String, BonePose> source = Map.of();
        private String clip = "";
        private long seed;
        private double started;
        private double duration;
        private double lastFrameTime = -1.0;
    }

    private record BonePose(float rotationX, float rotationY, float rotationZ,
                            float positionX, float positionY, float positionZ) {
    }

    private void visible(String name, boolean shown, boolean resetScale) {
        getBone(name).ifPresent(bone -> {
            bone.setHidden(!shown);
            bone.setChildrenHidden(!shown);
            if (resetScale && shown) {
                bone.setScaleX(1.0F);
                bone.setScaleY(1.0F);
                bone.setScaleZ(1.0F);
            }
        });
    }
}