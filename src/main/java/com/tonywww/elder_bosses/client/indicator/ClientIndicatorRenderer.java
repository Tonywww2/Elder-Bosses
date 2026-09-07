package com.tonywww.elder_bosses.client.indicator;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tonywww.elder_bosses.client.state.ClientIndicatorStateStore;
import com.tonywww.elder_bosses.network.IndicatorSnapshotPacket;
import com.tonywww.elder_bosses.platforms.client.PlatformVertexConsumer;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class ClientIndicatorRenderer {
    private static final double FILL_OPACITY_MULTIPLIER = 0.45;
    private static final RenderType TRANSLUCENT_FILLS = IndicatorRenderType.createTranslucentFills();
    private static final RenderType VISIBLE_LINES = IndicatorRenderType.createVisibleLines();
    private static final RenderType OCCLUDED_LINES = IndicatorRenderType.createOccludedLines();

    private ClientIndicatorRenderer() {
    }

    public static void render(
            PoseStack poseStack,
            Camera camera,
            long gameTick,
            float partialTick,
            ElderBossesCommonConfig.IndicatorValues config
    ) {
        Vec3 cameraPosition = camera.getPosition();
        double renderDistanceSquared = config.renderDistance() * config.renderDistance();
        List<IndicatorSnapshotPacket> snapshots = ClientIndicatorStateStore
            .activeSnapshots(gameTick, partialTick).stream()
                .filter(snapshot -> distanceSquared(cameraPosition, snapshot.anchor()) <= renderDistanceSquared)
                .toList();
        if (snapshots.isEmpty()) {
            return;
        }

        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        try {
            drawFills(poseStack.last(), buffers, snapshots, gameTick, config);
            buffers.endBatch(TRANSLUCENT_FILLS);
            drawBorders(poseStack.last(), buffers, snapshots, VISIBLE_LINES, false, gameTick, config);
            buffers.endBatch(VISIBLE_LINES);

            drawBorders(poseStack.last(), buffers, snapshots, OCCLUDED_LINES, true, gameTick, config);
            buffers.endBatch(OCCLUDED_LINES);
        } finally {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            poseStack.popPose();
        }
    }

    private static void drawFills(
            PoseStack.Pose pose,
            MultiBufferSource.BufferSource buffers,
            List<IndicatorSnapshotPacket> snapshots,
            long gameTick,
            ElderBossesCommonConfig.IndicatorValues config
    ) {
        VertexConsumer consumer = buffers.getBuffer(TRANSLUCENT_FILLS);
        for (IndicatorSnapshotPacket snapshot : snapshots) {
            ClientIndicatorGeometry.Mesh mesh = ClientIndicatorGeometry.create(
                    snapshot,
                    config.maxSegmentsPerShape()
            );
                int alpha = alpha(
                    visibility(snapshot, gameTick),
                    config.opacity() * FILL_OPACITY_MULTIPLIER
                );
            int rgb = snapshot.styleRole().rgb();
            for (ClientIndicatorGeometry.Quad quad : mesh.fills()) {
                fillVertex(consumer, pose, quad.first(), rgb, alpha, config.surfaceOffset());
                fillVertex(consumer, pose, quad.second(), rgb, alpha, config.surfaceOffset());
                fillVertex(consumer, pose, quad.third(), rgb, alpha, config.surfaceOffset());
                fillVertex(consumer, pose, quad.fourth(), rgb, alpha, config.surfaceOffset());
            }
        }
    }

    private static void drawBorders(
            PoseStack.Pose pose,
            MultiBufferSource.BufferSource buffers,
            List<IndicatorSnapshotPacket> snapshots,
            RenderType renderType,
            boolean occluded,
            long gameTick,
            ElderBossesCommonConfig.IndicatorValues config
    ) {
        VertexConsumer consumer = buffers.getBuffer(renderType);
        for (IndicatorSnapshotPacket snapshot : snapshots) {
            ClientIndicatorGeometry.Mesh mesh = ClientIndicatorGeometry.create(
                    snapshot,
                    config.maxSegmentsPerShape()
            );
            float visibility = visibility(snapshot, gameTick);
                double layerMultiplier = occluded
                    ? config.occludedOutlineOpacityMultiplier()
                    : 1.0 - config.occludedOutlineOpacityMultiplier();
                int alpha = alpha(visibility, config.opacity() * layerMultiplier);
            int rgb = snapshot.styleRole().rgb();
            drawLines(
                    consumer,
                    pose,
                    mesh.borders(),
                    rgb,
                    alpha,
                    snapshot.styleRole().dashed(),
                    config.surfaceOffset()
            );
            drawLines(consumer, pose, mesh.accents(), rgb, alpha, true, config.surfaceOffset());
            if (occluded && snapshot.instantGuardCue()) {
                drawInstantGuardCue(consumer, pose, snapshot, gameTick, alpha, config.surfaceOffset());
            }
        }
    }

    private static void drawLines(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            List<ClientIndicatorGeometry.Line> lines,
            int rgb,
            int alpha,
                boolean dashed,
                double surfaceOffset
    ) {
        for (int index = 0; index < lines.size(); index++) {
            if (dashed && index % 2 != 0) {
                continue;
            }
            ClientIndicatorGeometry.Line line = lines.get(index);
            drawLine(consumer, pose, line.from(), line.to(), rgb, alpha, surfaceOffset);
        }
    }

    private static void drawInstantGuardCue(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            IndicatorSnapshotPacket snapshot,
            long gameTick,
            int alpha,
            double surfaceOffset
    ) {
        long cueStartTick = snapshot.lockTick();
        if (gameTick < cueStartTick || gameTick >= snapshot.activeTick()) {
            return;
        }
        long cueDuration = snapshot.activeTick() - cueStartTick;
        if (cueDuration <= 0L) {
            return;
        }
        long elapsedTicks = gameTick - cueStartTick;
        double pulseProgress = (double) elapsedTicks * snapshot.cuePulseCount() / cueDuration;
        double pulsePhase = pulseProgress - Math.floor(pulseProgress);
        double radius = 0.55 + 0.25 * (1.0 - pulsePhase);
        int pulseAlpha = Math.max(
                1,
                (int) Math.round(alpha * (0.45 + 0.55 * (1.0 - pulsePhase)))
        );
        ClientIndicatorGeometry.Vertex center = new ClientIndicatorGeometry.Vertex(
                snapshot.anchor().x(),
                snapshot.anchor().y(),
                snapshot.anchor().z()
        );
        ClientIndicatorGeometry.Vertex west = new ClientIndicatorGeometry.Vertex(
                center.x() - radius,
                center.y(),
                center.z()
        );
        ClientIndicatorGeometry.Vertex east = new ClientIndicatorGeometry.Vertex(
                center.x() + radius,
                center.y(),
                center.z()
        );
        ClientIndicatorGeometry.Vertex north = new ClientIndicatorGeometry.Vertex(
                center.x(),
                center.y(),
                center.z() - radius
        );
        ClientIndicatorGeometry.Vertex south = new ClientIndicatorGeometry.Vertex(
                center.x(),
                center.y(),
                center.z() + radius
        );
        double yaw = Math.toRadians(snapshot.directionYawDegrees());
        double directionX = -Math.sin(yaw);
        double directionZ = Math.cos(yaw);
        ClientIndicatorGeometry.Vertex weaponBase = new ClientIndicatorGeometry.Vertex(
            center.x() - directionX * 0.35,
            center.y(),
            center.z() - directionZ * 0.35
        );
        ClientIndicatorGeometry.Vertex weaponTip = new ClientIndicatorGeometry.Vertex(
            center.x() + directionX * (1.6 + radius * 0.5),
            center.y(),
            center.z() + directionZ * (1.6 + radius * 0.5)
        );
        drawLine(consumer, pose, west, east, snapshot.cueRgb(), pulseAlpha, surfaceOffset);
        drawLine(consumer, pose, north, south, snapshot.cueRgb(), pulseAlpha, surfaceOffset);
        drawLine(
            consumer,
            pose,
            weaponBase,
            weaponTip,
            snapshot.cueRgb(),
            pulseAlpha,
            surfaceOffset
        );
    }

    private static double distanceSquared(Vec3 position, IndicatorSnapshotPacket.Point anchor) {
        double deltaX = position.x - anchor.x();
        double deltaY = position.y - anchor.y();
        double deltaZ = position.z - anchor.z();
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }

    private static int alpha(float visibility, double opacity) {
        return Math.max(0, Math.min(255, (int) Math.round(255.0 * visibility * opacity)));
    }

    private static float visibility(IndicatorSnapshotPacket snapshot, long gameTick) {
        boolean expired = snapshot.state() == IndicatorSnapshotPacket.IndicatorState.EXPIRED
                || gameTick >= snapshot.endTick();
        float stateVisibility = expired ? 0.32F : switch (snapshot.state()) {
            case TRACKING -> 0.48F;
            case LOCKED -> 0.68F;
            case IMMINENT -> 0.88F;
            case ACTIVE -> 1.0F;
            case PERSISTENT -> 0.72F;
            case EXPIRED -> throw new IllegalStateException("expired state was handled above");
        };
        if (expired) {
            long elapsedFadeTicks = Math.max(0L, gameTick - snapshot.endTick());
            float fade = Math.max(
                    0.0F,
                    (IndicatorSnapshotPacket.EXPIRED_FADE_TICKS - elapsedFadeTicks)
                            / (float) IndicatorSnapshotPacket.EXPIRED_FADE_TICKS
            );
            stateVisibility *= fade;
        }
        return snapshot.slot() == IndicatorSnapshotPacket.SegmentSlot.NEXT
                ? stateVisibility * 0.55F
                : stateVisibility;
    }

    private static void fillVertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            ClientIndicatorGeometry.Vertex vertex,
            int rgb,
            int alpha,
            double surfaceOffset
    ) {
        int red = rgb >> 16 & 0xFF;
        int green = rgb >> 8 & 0xFF;
        int blue = rgb & 0xFF;
        PlatformVertexConsumer.addPositionColor(
            consumer,
            pose,
            (float) vertex.x(),
            (float) (vertex.y() + surfaceOffset),
            (float) vertex.z(),
            red,
            green,
            blue,
            alpha
        );
    }

        private static void drawLine(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            ClientIndicatorGeometry.Vertex from,
            ClientIndicatorGeometry.Vertex to,
            int rgb,
            int alpha,
            double surfaceOffset
        ) {
        double deltaX = to.x() - from.x();
        double deltaY = to.y() - from.y();
        double deltaZ = to.z() - from.z();
        double length = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        if (length == 0.0) {
            return;
        }
        float normalX = (float) (deltaX / length);
        float normalY = (float) (deltaY / length);
        float normalZ = (float) (deltaZ / length);
        lineVertex(consumer, pose, from, rgb, alpha, normalX, normalY, normalZ, surfaceOffset);
        lineVertex(consumer, pose, to, rgb, alpha, normalX, normalY, normalZ, surfaceOffset);
        }

        private static void lineVertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            ClientIndicatorGeometry.Vertex vertex,
            int rgb,
            int alpha,
            float normalX,
            float normalY,
            float normalZ,
            double surfaceOffset
    ) {
        int red = rgb >> 16 & 0xFF;
        int green = rgb >> 8 & 0xFF;
        int blue = rgb & 0xFF;
        PlatformVertexConsumer.addPositionColorNormal(
            consumer,
            pose,
            (float) vertex.x(),
            (float) (vertex.y() + surfaceOffset),
            (float) vertex.z(),
            red,
            green,
            blue,
            alpha,
            normalX,
            normalY,
            normalZ
        );
    }

    private static final class IndicatorRenderType extends RenderType {
        private IndicatorRenderType(
                String name,
                VertexFormat format,
                VertexFormat.Mode mode,
                int bufferSize,
                boolean affectsCrumbling,
                boolean sortOnUpload,
                Runnable setupState,
                Runnable clearState
        ) {
            super(
                    name,
                    format,
                    mode,
                    bufferSize,
                    affectsCrumbling,
                    sortOnUpload,
                    setupState,
                    clearState
            );
        }

        private static RenderType createTranslucentFills() {
            return RenderType.create(
                "elder_bosses_indicator_translucent_fills",
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS,
                256,
                false,
                true,
                CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(true)
            );
        }

        private static RenderType createVisibleLines() {
            return createLines("elder_bosses_indicator_visible_lines", LEQUAL_DEPTH_TEST);
        }

        private static RenderType createOccludedLines() {
            return createLines("elder_bosses_indicator_occluded_lines", NO_DEPTH_TEST);
        }

        private static RenderType createLines(String name, DepthTestStateShard depthTestState) {
            return RenderType.create(
                    name,
                    DefaultVertexFormat.POSITION_COLOR_NORMAL,
                    VertexFormat.Mode.LINES,
                    256,
                    false,
                    false,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_LINES_SHADER)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(depthTestState)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .setLineState(DEFAULT_LINE)
                            .createCompositeState(false)
            );
        }
    }
}