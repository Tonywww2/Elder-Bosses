package com.tonywww.elder_bosses.client.vfx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.client.state.ClientIndicatorStateStore;
import com.tonywww.elder_bosses.network.IndicatorSnapshotPacket;
import com.tonywww.elder_bosses.platforms.client.PlatformVertexConsumer;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public final class ClientConsortEnergyRenderer {
    private ClientConsortEnergyRenderer() {
    }

    public static void render(PoseStack poses, Camera camera, long gameTick, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        var config = ElderBossesCommonConfig.VALUES.skillVfx();
        if (!config.enabled() || !ConsortEnergyShader.ready() || minecraft.level == null) return;
        Vec3 cameraPosition = camera.getPosition();
        double time = gameTick + partialTick;
        var snapshots = ClientIndicatorStateStore.activeSnapshots(gameTick, partialTick).stream()
                .filter(snapshot -> minecraft.level.getEntity(snapshot.bossEntityId()) instanceof PromisedConsortEntity)
                .filter(snapshot -> snapshot.slot() != IndicatorSnapshotPacket.SegmentSlot.NEXT)
                .filter(snapshot -> snapshot.state() != IndicatorSnapshotPacket.IndicatorState.EXPIRED)
                .filter(snapshot -> time < snapshot.endTick() && time >= snapshot.lockTick())
                .filter(snapshot -> anchor(snapshot).distanceToSqr(cameraPosition) <= config.renderDistance() * config.renderDistance())
                .sorted(Comparator.comparingDouble((IndicatorSnapshotPacket snapshot) -> anchor(snapshot).distanceToSqr(cameraPosition)).reversed())
                .toList();
        if (snapshots.isEmpty()) return;
        Map<Integer, Integer> budgets = new HashMap<>();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        poses.pushPose();
        poses.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        try {
            for (var snapshot : snapshots) {
                int count = budgets.merge(snapshot.bossEntityId(), 1, Integer::sum);
                if (count > 32) continue;
                String style = snapshot.styleRole().name();
                boolean active = time >= snapshot.activeTick();
                if (!active && !style.contains("HOLY") && !style.contains("GRAVITY") && !style.contains("BLOOD")) continue;
                float progress = (float) Math.max(0.0, Math.min(1.0, (time - snapshot.activeTick()) / Math.max(1L, snapshot.endTick() - snapshot.activeTick())));
                float strength = active ? (float) (0.9 * (1.0 - Math.pow(progress, 3))) : 0.18F;
                Vec3 center = anchor(snapshot).add(0, 0.11, 0);
                double first = snapshot.ranges().get(0);
                double outer = snapshot.shapeType() == IndicatorSnapshotPacket.ShapeType.ANNULUS ? snapshot.ranges().get(1) : first;
                float inner = snapshot.shapeType() == IndicatorSnapshotPacket.ShapeType.ANNULUS ? (float) (first / Math.max(0.01, outer)) : 0.0F;
                int rgb = snapshot.styleRole().rgb();
                int mode = style.contains("GRAVITY") ? 4 : style.contains("BLOOD") ? 3 : active ? 5 : 1;
                ConsortEnergyShader.configure(mode, (float) ((time % 24000) / 20), progress, inner);
                VertexConsumer consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
                switch (snapshot.shapeType()) {
                    case CIRCLE, ZONE, ANNULUS -> ground(consumer, poses.last(), center, outer, outer, 0, rgb, strength);
                    case RECTANGLE, CAPSULE -> {
                        double width = snapshot.ranges().get(1);
                        Vec3 forward = forward(snapshot.directionYawDegrees());
                        ConsortEnergyShader.configure(style.contains("BLOOD") ? 3 : 0, (float) ((time % 24000) / 20), progress, 0);
                        ground(consumer, poses.last(), center.add(forward.scale(first / 2)), width / 2, first / 2,
                                snapshot.directionYawDegrees(), rgb, strength * 0.65F);
                    }
                    case PATH -> {
                        ConsortEnergyShader.configure(0, (float) ((time % 24000) / 20), progress, 0);
                        double width = Math.min(0.4, first * 0.5);
                        for (int index = 1; index < snapshot.pathPoints().size(); index++) {
                            var start = snapshot.pathPoints().get(index - 1);
                            var end = snapshot.pathPoints().get(index);
                            ribbon(consumer, poses.last(), new Vec3(start.x(), start.y() + 0.18, start.z()),
                                    new Vec3(end.x(), end.y() + 0.18, end.z()), width, rgb, strength);
                        }
                    }
                    case SECTOR -> {
                        if (!active || snapshot.semantic() == IndicatorSnapshotPacket.Semantic.PHYSICAL) continue;
                        ConsortEnergyShader.configure(0, (float) ((time % 24000) / 20), progress, 0);
                        double radians = Math.toRadians(snapshot.ranges().get(1));
                        double yaw = Math.toRadians(snapshot.directionYawDegrees());
                        for (int index = 0; index < 20; index++) {
                            double start = yaw - radians / 2 + radians * index / 20;
                            double end = yaw - radians / 2 + radians * (index + 1) / 20;
                            ribbon(consumer, poses.last(), center.add(-Math.sin(start) * first, 0.16, Math.cos(start) * first),
                                    center.add(-Math.sin(end) * first, 0.16, Math.cos(end) * first), 0.13, rgb, strength * 0.35F);
                        }
                    }
                }
                buffers.endBatch(ConsortEnergyShader.ENERGY);
                if (style.contains("HOLY") && (snapshot.shapeType() == IndicatorSnapshotPacket.ShapeType.CIRCLE
                        || snapshot.shapeType() == IndicatorSnapshotPacket.ShapeType.ZONE)) {
                    ConsortEnergyShader.configure(2, (float) ((time % 24000) / 20), progress, 0);
                    consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
                    double width = Math.min(2.4, Math.max(0.35, outer * 0.45));
                    double height = Math.min(14.0, Math.max(4.0, outer * 1.7));
                    Vec3 across = cameraPosition.subtract(center).multiply(1, 0, 1).normalize().cross(new Vec3(0, 1, 0)).scale(width);
                    quad(consumer, poses.last(), center.subtract(across), center.add(across),
                            center.add(across).add(0, height, 0), center.subtract(across).add(0, height, 0), rgb, strength);
                    buffers.endBatch(ConsortEnergyShader.ENERGY);
                }
            }
        } finally {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            poses.popPose();
        }
    }

    private static Vec3 anchor(IndicatorSnapshotPacket snapshot) {
        return new Vec3(snapshot.anchor().x(), snapshot.anchor().y(), snapshot.anchor().z());
    }

    private static Vec3 forward(double yaw) {
        double angle = Math.toRadians(yaw);
        return new Vec3(-Math.sin(angle), 0, Math.cos(angle));
    }

    private static void ground(VertexConsumer consumer, PoseStack.Pose pose, Vec3 center,
                               double width, double length, double yaw, int rgb, float alpha) {
        Vec3 depth = forward(yaw).scale(length);
        Vec3 across = new Vec3(depth.z, 0, -depth.x).normalize().scale(width);
        quad(consumer, pose, center.subtract(across).subtract(depth), center.add(across).subtract(depth),
                center.add(across).add(depth), center.subtract(across).add(depth), rgb, alpha);
    }

    public static void ribbon(VertexConsumer consumer, PoseStack.Pose pose, Vec3 start, Vec3 end, double width, int rgb, float alpha) {
        Vec3 across = end.subtract(start).normalize().cross(new Vec3(0, 1, 0)).scale(width);
        quad(consumer, pose, start.subtract(across), start.add(across), end.add(across), end.subtract(across), rgb, alpha);
    }

    public static void quad(VertexConsumer consumer, PoseStack.Pose pose, Vec3 first, Vec3 second, Vec3 third, Vec3 fourth, int rgb, float alpha) {
        vertex(consumer, pose, first, 0, 0, rgb, alpha);
        vertex(consumer, pose, second, 1, 0, rgb, alpha);
        vertex(consumer, pose, third, 1, 1, rgb, alpha);
        vertex(consumer, pose, fourth, 0, 1, rgb, alpha);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 point, float horizontal, float vertical, int rgb, float alpha) {
        PlatformVertexConsumer.addPositionColorUv(consumer, pose, (float) point.x, (float) point.y, (float) point.z,
                rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, Math.max(0, Math.min(255, Math.round(alpha * 255))), horizontal, vertical);
    }
}