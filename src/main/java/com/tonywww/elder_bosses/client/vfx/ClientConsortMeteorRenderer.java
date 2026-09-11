package com.tonywww.elder_bosses.client.vfx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortCombatState;
import com.tonywww.elder_bosses.client.state.ClientIndicatorStateStore;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class ClientConsortMeteorRenderer {
    private ClientConsortMeteorRenderer() {
    }

    public static void render(PoseStack poses, Camera camera, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        var config = ElderBossesCommonConfig.VALUES.skillVfx();
        if (minecraft.level == null || !config.enabled() || !ConsortEnergyShader.ready()) return;
        Vec3 view = camera.getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        poses.pushPose();
        poses.translate(-view.x, -view.y, -view.z);
        try {
            for (Entity candidate : minecraft.level.entitiesForRendering()) {
                if (!(candidate instanceof PromisedConsortEntity boss) || boss.combatState() != PromisedConsortCombatState.METEOR_SCRIPT
                        || boss.meteorLanded() || boss.distanceToSqr(view) > config.renderDistance() * config.renderDistance()) continue;
                boss.prepareAnimationFrame(partialTick);
                double tick = boss.animationTime();
                float charge = (float) Math.max(0, Math.min(1, (tick - 15) / 80));
                float time = (float) ((minecraft.level.getGameTime() % 24000 + partialTick) / 20);
                Vec3 center = new Vec3(Mth.lerp(partialTick, boss.xo, boss.getX()), Mth.lerp(partialTick, boss.yo, boss.getY()),
                        Mth.lerp(partialTick, boss.zo, boss.getZ())).add(0, boss.getBbHeight() * 0.6, 0);
                Vec3 look = view.subtract(center).normalize();
                Vec3 across = look.cross(new Vec3(0, 1, 0)).normalize();
                if (across.lengthSqr() < 0.01) across = new Vec3(1, 0, 0);
                Vec3 up = across.cross(look).normalize();
                double radius = 2.1 + charge * 1.8 + Math.sin(tick * 0.4) * 0.12;
                ConsortEnergyShader.configure(7, time, charge, 0);
                VertexConsumer consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
                billboard(consumer, poses.last(), center, across.scale(radius), up.scale(radius), 0xFFE0A0, 0.5F + charge * 0.38F);
                billboard(consumer, poses.last(), center, across.scale(radius * 0.46), up.scale(radius * 0.46), 0xFFF6DB, 0.55F + charge * 0.4F);
                buffers.endBatch(ConsortEnergyShader.ENERGY);
                ConsortEnergyShader.configure(8, time, charge, 0);
                consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
                for (int streak = -2; streak <= 2; streak++) {
                    Vec3 start = center.add(across.scale(streak * 0.42));
                    Vec3 end = start.add(across.scale(streak * (0.7 + charge))).add(0, 5 + charge * 13 - Math.abs(streak), 0);
                    Vec3 width = across.scale(streak == 0 ? 0.8 : 0.30);
                    ClientConsortEnergyRenderer.quad(consumer, poses.last(), start.subtract(width), start.add(width),
                            end.add(width.scale(0.12)), end.subtract(width.scale(0.12)), 0xFFD480, 0.42F + charge * 0.42F);
                }
                var impact = ClientIndicatorStateStore.snapshots().stream()
                    .filter(snapshot -> snapshot.bossEntityId() == boss.getId() && snapshot.indicatorId().endsWith(":core")
                        && minecraft.level.getGameTime() < snapshot.activeTick())
                        .findFirst();
                if (impact.isPresent() && tick >= 105) {
                    var point = impact.get().anchor();
                    Vec3 ground = new Vec3(point.x(), point.y() + 0.15, point.z());
                    Vec3 width = across.scale(0.65 + charge * 0.35);
                    ClientConsortEnergyRenderer.quad(consumer, poses.last(), ground.subtract(width), ground.add(width),
                            center.add(width), center.subtract(width), 0xFFF0B2, 0.45F + charge * 0.25F);
                }
                buffers.endBatch(ConsortEnergyShader.ENERGY);
            }
        } finally {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            poses.popPose();
        }
    }

    private static void billboard(VertexConsumer consumer, PoseStack.Pose pose, Vec3 center, Vec3 across, Vec3 up, int color, float alpha) {
        ClientConsortEnergyRenderer.quad(consumer, pose, center.subtract(across).subtract(up), center.add(across).subtract(up),
                center.add(across).add(up), center.subtract(across).add(up), color, alpha);
    }
}