package com.tonywww.elder_bosses.client.vfx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortCloneEntity;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.model.GeoModel;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

/** Cosmetic, bounded by visible clones; never creates attacks or network particles. */
public final class ClientConsortCloneEffects {
    private static final Map<PromisedConsortCloneEntity, ArrayDeque<BladeSample>> TRAILS = new WeakHashMap<>();
    private static Object effectLevel;

    private ClientConsortCloneEffects() {}

    public static float opacity(double time, long appear, long impact, long end) {
        if (time < appear || time >= end || end <= appear) return 0;
        double rise = smooth((time - appear) / Math.max(1.0, Math.min(2.0, impact - appear)));
        double fadeStart = Math.min(end - 1.0, Math.max(impact + 1.0, end - 5.0));
        double fall = 1 - smooth((time - fadeStart) / Math.max(1.0, end - fadeStart));
        return (float) (rise * fall);
    }

    public static double smooth(double value) {
        value = Math.max(0, Math.min(1, value));
        return value * value * (3 - 2 * value);
    }

    private static void resetForLevel(Object level) {
        if (effectLevel != level) { TRAILS.clear(); effectLevel = level; }
    }

    public static void clear() {
        TRAILS.clear();
        effectLevel = null;
    }

    public static void capture(PromisedConsortCloneEntity entity, GeoModel<PromisedConsortCloneEntity> model, float partialTick) {
        resetForLevel(entity.level());
        if (!ElderBossesCommonConfig.VALUES.skillVfx().enabled()) { TRAILS.clear(); return; }
        double time = entity.level().getGameTime() + partialTick;
        if (time < entity.impactTick() - 2 || time > entity.impactTick() + 5) return;
        var samples = TRAILS.computeIfAbsent(entity, unused -> new ArrayDeque<>());
        if (!samples.isEmpty() && time - samples.getLast().time() < 0.125) return;
        Vec3 offset = new Vec3(Mth.lerp(partialTick, entity.xo, entity.getX()) - entity.getX(),
                Mth.lerp(partialTick, entity.yo, entity.getY()) - entity.getY(),
                Mth.lerp(partialTick, entity.zo, entity.getZ()) - entity.getZ());
        Vec3[] points = new Vec3[4];
        String[] names = {"blade_root_l", "blade_tip_l", "blade_root_r", "blade_tip_r"};
        for (int i = 0; i < names.length; i++) {
            var bone = model.getBone(names[i]).orElse(null);
            if (bone == null) return;
            var position = bone.getWorldPosition();
            points[i] = new Vec3(position.x, position.y, position.z).add(offset);
            if (!Double.isFinite(points[i].lengthSqr()) || points[i].distanceToSqr(entity.position()) > 400) return;
        }
        if (points[0].distanceToSqr(points[1]) < 0.01 || points[2].distanceToSqr(points[3]) < 0.01) return;
        samples.addLast(new BladeSample(time, points));
        while (samples.size() > 24 || !samples.isEmpty() && time - samples.getFirst().time() > 3) samples.removeFirst();
    }

    public static void render(PoseStack poses, Vec3 view, double time, float partialTick, double distance) {
        var minecraft = Minecraft.getInstance();
        resetForLevel(minecraft.level);
        if (minecraft.level == null || !ElderBossesCommonConfig.VALUES.skillVfx().enabled()) { TRAILS.clear(); return; }
        TRAILS.keySet().removeIf(entity -> entity.isRemoved() || entity.level() != minecraft.level);
        if (!ConsortEnergyShader.ready()) return;
        var buffers = minecraft.renderBuffers().bufferSource();
        int count = 0;
        poses.pushPose();
        poses.translate(-view.x, -view.y, -view.z);
        try {
            for (var entity : minecraft.level.entitiesForRendering()) {
                if (!(entity instanceof PromisedConsortCloneEntity clone) || clone.isRemoved() || clone.distanceToSqr(view) > distance * distance) continue;
                float alpha = opacity(time, clone.appearTick(), clone.impactTick(), clone.fadeEndTick());
                if (alpha <= 0) continue;
                if (++count > 24) break;
                Vec3 center = new Vec3(Mth.lerp(partialTick, clone.xo, clone.getX()), Mth.lerp(partialTick, clone.yo, clone.getY()),
                        Mth.lerp(partialTick, clone.zo, clone.getZ()));
                double yaw = Math.toRadians(clone.getYRot());
                Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), across = new Vec3(forward.z, 0, -forward.x);
                float clock = (float) ((time % 24000) / 20);
                double impact = Math.max(0, 1 - Math.abs(time - clone.impactTick()) / 2.5);
                ConsortEnergyShader.configure(2, clock, 0, 0);
                var consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
                ClientConsortEnergyRenderer.pillar(consumer, poses.last(), center.add(0, 0.1, 0), view, 1.5, 6.5, 0xFFE2A0, alpha * 0.09F);
                ClientConsortEnergyRenderer.pillar(consumer, poses.last(), center, view, 0.3, 6.2, 0xFFFAE8, alpha * (float) (0.10 + impact * 0.18));
                buffers.endBatch(ConsortEnergyShader.ENERGY);
                ConsortEnergyShader.configure(8, clock, 0, 0);
                consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
                for (int i = 0; i < 3; i++) {
                    Vec3 start = center.add(0, 0.6 + i * 1.3, 0).add(across.scale((i - 1) * 0.4));
                    Vec3 end = start.subtract(forward.scale(2.0 + i * 0.6 + impact));
                    ClientConsortEnergyRenderer.ribbon(consumer, poses.last(), start, end, 0.10 + impact * 0.06, 0xFFE4A8,
                            alpha * (float) (0.12 + impact * 0.34));
                }
                buffers.endBatch(ConsortEnergyShader.ENERGY);
                ConsortEnergyShader.configure(10, clock, 0, 0);
                consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
                ClientConsortEnergyRenderer.holyHalo(consumer, poses.last(), center.add(0, 0.06, 0), 1.5 + impact * 0.55, 0xFFE0A0, alpha * 0.30F);
                buffers.endBatch(ConsortEnergyShader.ENERGY);
                ConsortEnergyShader.configure(17, clock + clone.getId() * 0.13F, 0, 0);
                consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
                ClientConsortEnergyRenderer.pillar(consumer, poses.last(), center, view, 1.4, 6.1, 0xFFE8B5, alpha * 0.55F);
                buffers.endBatch(ConsortEnergyShader.ENERGY);
                var samples = TRAILS.get(clone);
                if (samples == null || samples.size() < 2) continue;
                ConsortEnergyShader.configure(6, clock, 0, 0);
                consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
                BladeSample previous = null;
                for (var sample : samples) {
                    if (time - sample.time() > 3) continue;
                    if (previous != null && sample.time() - previous.time() <= 1.5) {
                        float strength = alpha * (float) Math.pow(Math.max(0, 1 - (time - sample.time()) / 3), 1.5) * 0.65F;
                        for (int side = 0; side < 4; side += 2) {
                            if (previous.points()[side + 1].distanceToSqr(sample.points()[side + 1]) > 100) continue;
                            ClientConsortEnergyRenderer.quad(consumer, poses.last(), previous.points()[side], previous.points()[side + 1],
                                    sample.points()[side + 1], sample.points()[side], 0xFFF2C6, strength);
                        }
                    }
                    previous = sample;
                }
                buffers.endBatch(ConsortEnergyShader.ENERGY);
            }
        } finally {
            RenderSystem.enableDepthTest(); RenderSystem.depthMask(true); RenderSystem.disableBlend(); poses.popPose();
        }
    }

    private record BladeSample(double time, Vec3[] points) {}
}
