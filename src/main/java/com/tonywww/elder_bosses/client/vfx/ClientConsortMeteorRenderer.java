package com.tonywww.elder_bosses.client.vfx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortCombatState;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.client.state.ClientIndicatorStateStore;
import com.tonywww.elder_bosses.network.IndicatorSnapshotPacket;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class ClientConsortMeteorRenderer {
    private static final java.util.Map<PromisedConsortEntity, ImpactVisual> IMPACTS = new java.util.WeakHashMap<>();
    private static Object impactLevel;
    private ClientConsortMeteorRenderer() {
    }

    public static void render(PoseStack poses, Camera camera, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        var config = ElderBossesCommonConfig.VALUES.skillVfx();
        if (impactLevel != minecraft.level) {
            IMPACTS.clear();
            impactLevel = minecraft.level;
        }
        if (minecraft.level == null || !config.enabled() || !ConsortEnergyShader.ready()) return;
        Vec3 view = camera.getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        poses.pushPose();
        poses.translate(-view.x, -view.y, -view.z);
        try {
            for (Entity candidate : minecraft.level.entitiesForRendering()) {
                if (!(candidate instanceof PromisedConsortEntity boss)) continue;
                boolean meteor = isMeteor(boss);
                double now = minecraft.level.getGameTime() + partialTick;
                ImpactVisual cached = IMPACTS.get(boss);
                if (cached != null && (now >= cached.tick() + 40 || !boss.isAlive()
                        || meteor && cached.seed() != boss.actionSeed()
                        || !meteor && now < cached.tick())) {
                    IMPACTS.remove(boss);
                    cached = null;
                }
                if (meteor) {
                    var marker = impact(boss);
                    if (marker != null && (cached == null || !cached.landed())) {
                        double outer = ClientIndicatorStateStore.snapshots().stream()
                                .filter(value -> value.bossEntityId() == boss.getId() && value.indicatorId().endsWith(":outer")
                                        && value.activeTick() == marker.activeTick())
                                .mapToDouble(value -> value.ranges().get(1)).max().orElse(marker.ranges().get(0));
                        cached = new ImpactVisual(boss.actionSeed(), new Vec3(marker.anchor().x(), marker.anchor().y(), marker.anchor().z()),
                                marker.ranges().get(0), outer, marker.activeTick(), false);
                        IMPACTS.put(boss, cached);
                    }
                    if (cached != null && boss.meteorLanded() && !cached.landed()) {
                        cached = new ImpactVisual(cached.seed(), cached.center(), cached.core(), cached.outer(), (long) now, true);
                        IMPACTS.put(boss, cached);
                    }
                }
                if (cached != null && cached.center().distanceToSqr(view) <= config.renderDistance() * config.renderDistance()) {
                    renderImpact(buffers, poses.last(), view, cached, now);
                }
                if (!meteor || boss.meteorLanded() || boss.distanceToSqr(view) > config.renderDistance() * config.renderDistance()) continue;
                boss.prepareAnimationFrame(partialTick);
                double tick = presentationTick(boss.animationTime());
                float charge = (float) charge(tick);
                float launch = (float) (smooth(18, 44, tick) * (1.0 - smooth(48, 57, tick)));
                float emergence = (float) smooth(73, 93, tick);
                float strength = Math.max(launch * 0.75F, emergence * (0.42F + charge * 0.58F));
                if (strength < 0.015F) continue;
                float time = (float) ((minecraft.level.getGameTime() % 24000 + partialTick) / 20);
                Vec3 center = visualPosition(boss, partialTick).add(0, boss.getBbHeight() * 0.6, 0);
                Vec3 look = view.subtract(center).normalize();
                Vec3 across = look.cross(new Vec3(0, 1, 0)).normalize();
                if (across.lengthSqr() < 0.01) across = new Vec3(1, 0, 0);
                Vec3 up = across.cross(look).normalize();
                double radius = Math.max(launch * 5.8, 1.2 + charge * charge * 16.0);
                ConsortEnergyShader.configure(7, time, charge, 0);
                VertexConsumer consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
                billboard(consumer, poses.last(), center, across.scale(radius), up.scale(radius), 0xFFCA60, strength);
                billboard(consumer, poses.last(), center, across.scale(radius * 0.40), up.scale(radius * 0.40), 0xFFF8E4, strength);
                if (charge > 0.55) {
                    Vec3 diagonal = across.add(up).normalize();
                    Vec3 other = up.subtract(across).normalize();
                    billboard(consumer, poses.last(), center, diagonal.scale(radius * 1.25), other.scale(radius * 1.25), 0xFFF0B2, strength * 0.62F);
                }
                buffers.endBatch(ConsortEnergyShader.ENERGY);
                ConsortEnergyShader.configure(8, time, charge, 0);
                consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
                var impact = impact(boss);
                Vec3 tail = impact == null || tick < 110 ? new Vec3(0, 1, 0)
                        : interpolatedPosition(boss, partialTick).add(0, 24, 0)
                        .subtract(new Vec3(impact.anchor().x(), impact.anchor().y(), impact.anchor().z())).normalize();
                for (int streak = -2; streak <= 2; streak++) {
                    Vec3 start = center.add(across.scale(streak * (0.3 + charge * 0.6)));
                    Vec3 end = start.add(across.scale(streak * (0.6 + charge))).add(tail.scale(6 + charge * 34 - Math.abs(streak) * 2));
                    Vec3 width = across.scale((streak == 0 ? 1.6 : 0.65) * (0.35 + charge));
                    ClientConsortEnergyRenderer.quad(consumer, poses.last(), start.subtract(width), start.add(width),
                            end.add(width.scale(0.05)), end.subtract(width.scale(0.05)), streak == 0 ? 0xFFF8E4 : 0xFFD078,
                            strength * (tick < 91 ? launch : 0.4F + charge * 0.6F));
                }
                if (impact != null && tick >= 110) {
                    var point = impact.anchor();
                    Vec3 ground = new Vec3(point.x(), point.y() + 0.15, point.z());
                    Vec3 width = across.scale(0.25 + charge * 0.65);
                    ClientConsortEnergyRenderer.quad(consumer, poses.last(), ground.subtract(width), ground.add(width),
                            center.add(width), center.subtract(width), 0xFFF0B2, strength * 0.34F);
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

    public static Vec3 visualOffset(PromisedConsortEntity boss, float partialTick) {
        if (!isMeteor(boss) || boss.meteorLanded()) return Vec3.ZERO;
        return visualPosition(boss, partialTick).subtract(interpolatedPosition(boss, partialTick));
    }

    public static boolean bodyVisible(PromisedConsortEntity boss) {
        return !isMeteor(boss) || boss.meteorLanded()
                || presentationTick(boss.animationTime()) < 52 || presentationTick(boss.animationTime()) >= 117;
    }

    private static boolean isMeteor(PromisedConsortEntity boss) {
        return boss.combatState() == PromisedConsortCombatState.METEOR_SCRIPT
                || boss.actionId().orElse(null) == PromisedConsortActionId.CONSORT_METEOR;
    }

    public static double impactEnvelope(double elapsed) {
        if (elapsed < 0 || elapsed >= 40) return 0;
        return (1 - smooth(4, 40, elapsed)) * (0.45 + 0.55 * (1 - smooth(0, 8, elapsed)));
    }

    private static void renderImpact(MultiBufferSource.BufferSource buffers, PoseStack.Pose pose, Vec3 view,
                                     ImpactVisual impact, double now) {
        double elapsed = now - impact.tick();
        if (elapsed < -29 || elapsed >= 40) return;
        if (!impact.landed() && elapsed >= 0) return;
        float time = (float) ((now % 24000) / 20);
        Vec3 center = impact.center().add(0, 0.18, 0);
        Vec3 across = view.subtract(center).multiply(1, 0, 1).normalize().cross(new Vec3(0, 1, 0));
        if (across.lengthSqr() < 0.01) across = new Vec3(1, 0, 0);
        if (elapsed < 0) {
            float charge = (float) smooth(-29, 0, elapsed);
            ConsortEnergyShader.configure(10, time, charge, 0);
            VertexConsumer consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
            ground(consumer, pose, center, impact.core() * (1.0 - charge * 0.55), 0xFFE3A3, 0.35F + charge * 0.5F);
            buffers.endBatch(ConsortEnergyShader.ENERGY);
            ConsortEnergyShader.configure(2, time, 0, 0);
            consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
            column(consumer, pose, center, across, 0.7 + charge * 2.1, 36, 0.3F + charge * 0.6F);
            buffers.endBatch(ConsortEnergyShader.ENERGY);
            ConsortEnergyShader.configure(13, time, charge, 0);
            consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
            for (int layer = 0; layer < 3; layer++) {
                double height = (1 - charge) * (8 + layer * 7) + layer * 1.8;
                ClientConsortEnergyRenderer.ringWall(consumer, pose, center.add(0, height, 0),
                        impact.core() * (0.22 + layer * 0.12) * (1 - charge * 0.3), 0.9,
                        layer == 1 ? 0xFFF6D5 : 0xFFC669, 0.35F + charge * 0.4F, 48);
            }
            buffers.endBatch(ConsortEnergyShader.ENERGY);
            return;
        }
        float strength = (float) impactEnvelope(elapsed);
        ConsortEnergyShader.configure(15, time, (float) (elapsed / 40), 0);
        VertexConsumer fractures = buffers.getBuffer(ConsortEnergyShader.ENERGY);
        ground(fractures, pose, center.add(0, 0.025, 0), impact.outer(), 0xE6A84C, strength);
        buffers.endBatch(ConsortEnergyShader.ENERGY);
        ConsortEnergyShader.configure(5, time, (float) smooth(0, 24, elapsed), 0);
        VertexConsumer consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
        ground(consumer, pose, center, impact.outer(), 0xFFD27A, strength);
        buffers.endBatch(ConsortEnergyShader.ENERGY);
        ConsortEnergyShader.configure(13, time, (float) (elapsed / 40), 0);
        consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
        double spread = smooth(0, 24, elapsed);
        shockDome(consumer, pose, center, Math.max(0.3, impact.core() * smooth(0, 10, elapsed)),
            8 * (1 - smooth(3, 19, elapsed)), 0xFFE7A8, strength * 0.65F);
        for (int ring = 0; ring < 2; ring++) {
            double expansion = smooth(ring * 5, 22 + ring * 5, elapsed);
            if (elapsed < ring * 5) continue;
            ClientConsortEnergyRenderer.ringWall(consumer, pose, center, Math.max(0.25, impact.outer() * expansion),
                (1.6 + ring * 0.7) * strength, ring == 0 ? 0xFFE4A0 : 0xFFF4D1, strength * 0.85F, 64);
        }
        buffers.endBatch(ConsortEnergyShader.ENERGY);
        ConsortEnergyShader.configure(8, time, (float) spread, 0);
        consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
        for (int fragment = 0; fragment < 20; fragment++) {
            double angle = fragment * Math.PI / 10;
            Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            double distance = impact.outer() * spread * (0.65 + fragment % 3 * 0.12);
            double altitude = Math.sin(Math.PI * Math.min(1, elapsed / 30)) * (3 + fragment % 4 * 1.2);
            Vec3 point = center.add(radial.scale(distance)).add(0, altitude, 0);
            Vec3 tail = point.subtract(radial.scale(1.5 + strength * 3)).add(0, -0.5, 0);
            ClientConsortEnergyRenderer.ribbon(consumer, pose, tail, point, 0.18 + strength * 0.15,
                fragment % 2 == 0 ? 0xFFE8AD : 0xD8A657, strength);
        }
        buffers.endBatch(ConsortEnergyShader.ENERGY);
        if (elapsed < 9) {
            ConsortEnergyShader.configure(7, time, 1, 0);
            consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
            double radius = Math.min(12, impact.core() * 0.6) * (0.6 + smooth(0, 3, elapsed) * 0.4);
            billboard(consumer, pose, center.add(0, 3, 0), across.scale(radius), new Vec3(0, radius, 0),
                    0xFFF4CF, (float) (1 - smooth(1, 9, elapsed)));
            buffers.endBatch(ConsortEnergyShader.ENERGY);
        }
        ConsortEnergyShader.configure(2, time, (float) Math.min(1, elapsed / 40), 0);
        consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
        column(consumer, pose, center, across, 2.8 * strength + 0.25, 30 * strength + 3, strength);
        for (int index = 0; index < 10; index++) {
            double angle = index * Math.PI / 5;
            double radius = impact.core() * (0.6 + smooth(0, 14, elapsed) * 0.4);
            Vec3 point = center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            column(consumer, pose, point, across, 0.35 + strength * 0.4, 3 + strength * 9, strength * 0.7F);
        }
        buffers.endBatch(ConsortEnergyShader.ENERGY);
    }

    public static void shockDome(VertexConsumer consumer, PoseStack.Pose pose, Vec3 center, double radius, double height,
                                 int color, float alpha) {
        if (height <= 0.01 || alpha <= 0.001) return;
        int bands = 5, segments = 48;
        for (int band = 0; band < bands; band++) {
            double lower = band * Math.PI / (2 * bands), upper = (band + 1) * Math.PI / (2 * bands);
            for (int segment = 0; segment < segments; segment++) {
                double first = segment * Math.PI * 2 / segments, second = (segment + 1) * Math.PI * 2 / segments;
                ClientConsortEnergyRenderer.quad(consumer, pose,
                        domePoint(center, radius, height, lower, first), domePoint(center, radius, height, lower, second),
                        domePoint(center, radius, height, upper, second), domePoint(center, radius, height, upper, first), color, alpha);
            }
        }
    }

    private static Vec3 domePoint(Vec3 center, double radius, double height, double elevation, double angle) {
        return center.add(Math.cos(angle) * Math.cos(elevation) * radius, Math.sin(elevation) * height,
                Math.sin(angle) * Math.cos(elevation) * radius);
    }

    private static void ground(VertexConsumer consumer, PoseStack.Pose pose, Vec3 center, double radius, int color, float alpha) {
        ClientConsortEnergyRenderer.quad(consumer, pose, center.add(-radius, 0, -radius), center.add(radius, 0, -radius),
                center.add(radius, 0, radius), center.add(-radius, 0, radius), color, alpha);
    }

    private static void column(VertexConsumer consumer, PoseStack.Pose pose, Vec3 center, Vec3 across,
                               double width, double height, float alpha) {
        Vec3 half = across.scale(width);
        ClientConsortEnergyRenderer.quad(consumer, pose, center.subtract(half), center.add(half),
                center.add(half).add(0, height, 0), center.subtract(half).add(0, height, 0), 0xFFF0BE, alpha);
    }

    private record ImpactVisual(long seed, Vec3 center, double core, double outer, long tick, boolean landed) {
    }

    private static Vec3 visualPosition(PromisedConsortEntity boss, float partialTick) {
        Vec3 position = interpolatedPosition(boss, partialTick);
        double tick = presentationTick(boss.animationTime());
        Vec3 high = position.add(0, smooth(48, 64, tick) * 24, 0);
        var impact = impact(boss);
        if (impact == null || tick < 110) return high;
        var point = impact.anchor();
        return high.lerp(new Vec3(point.x(), point.y(), point.z()), returnProgress(tick));
    }

    private static IndicatorSnapshotPacket impact(PromisedConsortEntity boss) {
        return ClientIndicatorStateStore.snapshots().stream()
                .filter(snapshot -> snapshot.bossEntityId() == boss.getId() && snapshot.indicatorId().endsWith(":core")
                        && snapshot.state() != IndicatorSnapshotPacket.IndicatorState.EXPIRED
                        && boss.level().getGameTime() <= snapshot.activeTick())
                .findFirst().orElse(null);
    }

    private static Vec3 interpolatedPosition(PromisedConsortEntity boss, float partialTick) {
        return new Vec3(Mth.lerp(partialTick, boss.xo, boss.getX()), Mth.lerp(partialTick, boss.yo, boss.getY()),
                Mth.lerp(partialTick, boss.zo, boss.getZ()));
    }

    private static double charge(double tick) {
        return smooth(76, 116, tick);
    }

    private static double presentationTick(double tick) {
        double[] authored = {0, 34, 43, 60, 150, 183, 202, 208, 212, 280};
        double[] presentation = {0, 18, 48, 64, 76, 91, 110, 117, 121, 150};
        for (int index = 1; index < authored.length; index++) {
            if (tick <= authored[index]) {
                double weight = Math.max(0, (tick - authored[index - 1]) / (authored[index] - authored[index - 1]));
                return presentation[index - 1] + weight * (presentation[index] - presentation[index - 1]);
            }
        }
        return 150;
    }

    private static double returnProgress(double tick) {
        double progress = smooth(110, 121, tick);
        return progress * progress;
    }

    private static double smooth(double start, double end, double tick) {
        double progress = Math.max(0.0, Math.min(1.0, (tick - start) / (end - start)));
        return progress * progress * (3.0 - 2.0 * progress);
    }

    private static void billboard(VertexConsumer consumer, PoseStack.Pose pose, Vec3 center, Vec3 across, Vec3 up, int color, float alpha) {
        ClientConsortEnergyRenderer.quad(consumer, pose, center.subtract(across).subtract(up), center.add(across).subtract(up),
                center.add(across).add(up), center.subtract(across).add(up), color, alpha);
    }
}