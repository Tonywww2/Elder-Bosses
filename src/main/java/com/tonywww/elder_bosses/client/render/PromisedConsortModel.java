package com.tonywww.elder_bosses.client.render;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortCombatState;
import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import com.tonywww.elder_bosses.platforms.client.PlatformPromisedConsortGeoModel;
import net.minecraft.resources.ResourceLocation;
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
    private final Map<PromisedConsortEntity, PoseHistory> histories = new WeakHashMap<>();

    @Override
    public ResourceLocation getModelResource(PromisedConsortEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(PromisedConsortEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(PromisedConsortEntity entity) {
        return ANIMATION;
    }

    @Override
    protected void afterAnimations(PromisedConsortEntity entity) {
        blendPose(entity);
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
