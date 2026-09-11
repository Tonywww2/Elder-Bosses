package com.tonywww.elder_bosses.client.vfx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortAnimationTimeline;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;

public final class ClientConsortBladeTrails {
    private static final double TRAIL_TICKS = 3.0;
    private static final Map<PromisedConsortEntity, Trail> TRAILS = new WeakHashMap<>();

    private ClientConsortBladeTrails() {
    }

    public static void capture(PromisedConsortEntity entity, GeoModel<PromisedConsortEntity> model, float partialTick) {
        if (!ElderBossesCommonConfig.VALUES.skillVfx().enabled() || entity.actionId().isEmpty()) {
            TRAILS.remove(entity);
            return;
        }
        Trail trail = TRAILS.computeIfAbsent(entity, unused -> new Trail());
        double frameTime = entity.tickCount + partialTick;
        if (frameTime == trail.lastFrame) return;
        if (trail.seed != entity.actionSeed() || frameTime - trail.lastFrame > 3.0) {
            trail.samples.clear();
            trail.latest = null;
        }
        trail.seed = entity.actionSeed();
        trail.lastFrame = frameTime;
        trail.samples.removeIf(sample -> frameTime - sample.time() > TRAIL_TICKS);
        int sides = swordSides(entity.actionId().orElseThrow(), entity.animationTime());
        if (sides == 0) return;
        Vec3 offset = new Vec3(Mth.lerp(partialTick, entity.xo, entity.getX()) - entity.getX(),
                Mth.lerp(partialTick, entity.yo, entity.getY()) - entity.getY(),
                Mth.lerp(partialTick, entity.zo, entity.getZ()) - entity.getZ());
        Vec3 leftRoot = bone(model, "blade_root_l", offset), leftTip = bone(model, "blade_tip_l", offset);
        Vec3 rightRoot = bone(model, "blade_root_r", offset), rightTip = bone(model, "blade_tip_r", offset);
        if (leftRoot == null || leftTip == null || rightRoot == null || rightTip == null) return;
        if (leftTip.distanceToSqr(entity.position()) > 400 || rightTip.distanceToSqr(entity.position()) > 400) return;
        trail.latest = new Sample(frameTime, leftRoot, leftTip, rightRoot, rightTip, sides);
        if (trail.samples.isEmpty() || frameTime - trail.samples.getLast().time() >= 0.20) trail.samples.addLast(trail.latest);
        while (trail.samples.size() > 20) trail.samples.removeFirst();
    }

    public static void render(PoseStack poses, Camera camera, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !ElderBossesCommonConfig.VALUES.skillVfx().enabled()) {
            TRAILS.clear();
            return;
        }
        if (!ConsortEnergyShader.ready()) return;
        Vec3 view = camera.getPosition();
        double distance = ElderBossesCommonConfig.VALUES.skillVfx().renderDistance();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        poses.pushPose();
        poses.translate(-view.x, -view.y, -view.z);
        try {
            ConsortEnergyShader.configure(6, 0, 0, 0);
            VertexConsumer consumer = null;
            for (var entry : TRAILS.entrySet()) {
                PromisedConsortEntity entity = entry.getKey();
                if (entity.isRemoved() || entity.level() != minecraft.level || entity.distanceToSqr(view) > distance * distance) continue;
                double now = entity.tickCount + partialTick;
                Sample previous = null;
                int color = entity.miquellaVisible() ? 0xFFE6A2 : 0xE7DACA;
                for (Sample sample : entry.getValue().samples) {
                    if (now - sample.time() > TRAIL_TICKS) continue;
                    if (previous != null && sample.time() - previous.time() < 1.0) {
                        if (consumer == null) consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
                        float alpha = (float) Math.max(0, Math.pow(1.0 - (now - sample.time()) / TRAIL_TICKS, 1.3) * 0.85);
                        if ((sample.sides() & previous.sides() & 1) != 0) blade(consumer, poses.last(), previous.leftRoot(), previous.leftTip(), sample.leftRoot(), sample.leftTip(), color, alpha);
                        if ((sample.sides() & previous.sides() & 2) != 0) blade(consumer, poses.last(), previous.rightRoot(), previous.rightTip(), sample.rightRoot(), sample.rightTip(), color, alpha);
                    }
                    previous = sample;
                }
                Sample latest = entry.getValue().latest;
                if (previous != null && latest != null && latest.time() > previous.time() && now - latest.time() <= 0.5) {
                    if (consumer == null) consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
                    if ((latest.sides() & previous.sides() & 1) != 0) blade(consumer, poses.last(), previous.leftRoot(), previous.leftTip(), latest.leftRoot(), latest.leftTip(), color, 0.85F);
                    if ((latest.sides() & previous.sides() & 2) != 0) blade(consumer, poses.last(), previous.rightRoot(), previous.rightTip(), latest.rightRoot(), latest.rightTip(), color, 0.85F);
                }
            }
            if (consumer != null) buffers.endBatch(ConsortEnergyShader.ENERGY);
            ConsortEnergyShader.configure(0, 0, 0, 0);
            consumer = null;
            for (var entry : TRAILS.entrySet()) {
                var entity = entry.getKey();
                if (entity.isRemoved() || entity.level() != minecraft.level || entry.getValue().samples.isEmpty()
                        || entity.distanceToSqr(view) > distance * distance) continue;
                var sample = entry.getValue().latest;
                if (sample == null) continue;
                if (entity.tickCount + partialTick - sample.time() > 0.5) continue;
                if (consumer == null) consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
                if ((sample.sides() & 1) != 0) edge(consumer, poses.last(), sample.leftRoot(), sample.leftTip(), view, entity.miquellaVisible());
                if ((sample.sides() & 2) != 0) edge(consumer, poses.last(), sample.rightRoot(), sample.rightTip(), view, entity.miquellaVisible());
            }
            if (consumer != null) buffers.endBatch(ConsortEnergyShader.ENERGY);
        } finally {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            poses.popPose();
        }
    }

    private static void blade(VertexConsumer consumer, PoseStack.Pose pose, Vec3 oldRoot, Vec3 oldTip,
                              Vec3 root, Vec3 tip, int color, float alpha) {
        if (tip.distanceToSqr(oldTip) < 0.0001 || tip.distanceToSqr(oldTip) > 64) return;
        ClientConsortEnergyRenderer.quad(consumer, pose, oldRoot, oldTip, tip, root, color, alpha);
    }

    private static void edge(VertexConsumer consumer, PoseStack.Pose pose, Vec3 root, Vec3 tip, Vec3 view, boolean holy) {
        Vec3 across = tip.subtract(root).normalize().cross(view.subtract(root).normalize()).normalize();
        Vec3 wide = across.scale(holy ? 0.15 : 0.11);
        ClientConsortEnergyRenderer.quad(consumer, pose, root.subtract(wide), root.add(wide), tip.add(wide), tip.subtract(wide), holy ? 0xFFD57A : 0xDFD4B7, 0.78F);
        Vec3 core = across.scale(0.035);
        ClientConsortEnergyRenderer.quad(consumer, pose, root.subtract(core), root.add(core), tip.add(core), tip.subtract(core), 0xFFF5DF, 0.95F);
    }

    private static int swordSides(PromisedConsortActionId action, double tick) {
        if (!swordActive(action, tick)) return 0;
        return switch (action) {
            case L_COMBO_CROSS -> tick < 18 ? 1 : tick < 40 ? 2 : 3;
            case L_COMBO_BLOODFLAME -> tick < 24 ? 1 : 2;
            case R_COMBO_CROSS -> tick < 20 ? 2 : 3;
            case R_COMBO_LEFT_TWIN -> tick < 19 ? 2 : 1;
            case R_COMBO_TEMPEST, R_COMBO_EARTHHEAVE -> tick < 20 ? 2 : tick < 39 ? 1 : tick < 57 ? 2 : 3;
            case PROMISED_CONSORT -> tick < 30 ? 2 : tick < 40 ? 1 : 3;
            case LIGHTSPEED_SIDE_DASH -> 2;
            default -> 3;
        };
    }

    private static Vec3 bone(GeoModel<PromisedConsortEntity> model, String name, Vec3 offset) {
        GeoBone bone = model.getBone(name).orElse(null);
        if (bone == null) return null;
        var position = bone.getWorldPosition();
        return new Vec3(position.x, position.y, position.z).add(offset);
    }

    private static boolean swordActive(PromisedConsortActionId action, double tick) {
        int[] hits = switch (action) {
            case LIGHT_OF_MIQUELLA, RING_OF_LIGHT, GRAVITY_METEOR, STOMP, CONSORT_METEOR -> new int[0];
            case LIGHTSPEED_SLASH -> new int[]{51};
            case LIGHTSPEED_DASH -> new int[]{45};
            case LIGHTSPEED_SIDE_DASH -> new int[]{37};
            case PROMISED_CONSORT -> new int[]{26, 34, 44, 54, 68};
            case STARCALLER_CRY -> new int[]{39};
            case SPIRAL_ASSAULT -> new int[]{26, 33};
            case R_COMBO_TEMPEST -> new int[]{10, 29, 48, 75, 79};
            case R_COMBO_EARTHHEAVE -> new int[]{10, 29, 48, 77};
            default -> new int[0];
        };
        for (int hit : hits) if (tick >= hit - 2.0 && tick <= hit + 2.5) return true;
        if (hits.length > 0 || action == PromisedConsortActionId.LIGHT_OF_MIQUELLA
                || action == PromisedConsortActionId.RING_OF_LIGHT || action == PromisedConsortActionId.GRAVITY_METEOR
                || action == PromisedConsortActionId.STOMP || action == PromisedConsortActionId.CONSORT_METEOR) return false;
        int[] durations = PromisedConsortAnimationTimeline.durations(action);
        int cursor = 0;
        for (int index = 0; index < durations.length; index += 3) {
            int hit = cursor + durations[index];
            if (tick >= hit - 2.0 && tick <= hit + 2.5) return true;
            cursor += durations[index] + durations[index + 1] + durations[index + 2];
        }
        return false;
    }

    private static final class Trail {
        private long seed;
        private double lastFrame = -1;
        private Sample latest;
        private final Deque<Sample> samples = new ArrayDeque<>();
    }

    private record Sample(double time, Vec3 leftRoot, Vec3 leftTip, Vec3 rightRoot, Vec3 rightTip, int sides) {
    }
}