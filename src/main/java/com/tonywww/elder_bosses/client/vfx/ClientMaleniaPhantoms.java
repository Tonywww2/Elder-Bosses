package com.tonywww.elder_bosses.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tonywww.elder_bosses.boss.malenia.MaleniaEntity;
import com.tonywww.elder_bosses.platforms.client.PlatformMaleniaPhantomRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.List;

public final class ClientMaleniaPhantoms {
    private static PlatformMaleniaPhantomRenderer renderer;
    private ClientMaleniaPhantoms() {}

    public static void render(PoseStack poses, MultiBufferSource.BufferSource buffers, List<MaleniaEffectState.Segment> segments,
                              Vec3 view, double now, double distance) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        int detailedCount = 0;
        for (var segment : segments.stream().filter(ClientMaleniaPhantoms::isPhantom)
                .sorted(Comparator.comparingDouble(s -> Math.abs(now - s.packet().activeTick()))).toList()) {
            var packet = segment.packet();
            if (packet.pathPoints().size() != 2 || now < packet.lockTick() || now >= packet.activeTick() + 6) continue;
            if (!(level.getEntity(packet.bossEntityId()) instanceof MaleniaEntity boss)) continue;
            Vec3 start = point(packet.pathPoints().get(0)), end = point(packet.pathPoints().get(1));
            double progress = Math.max(0, Math.min(1, (now - packet.lockTick()) / Math.max(1, packet.activeTick() - packet.lockTick())));
            Vec3 location = start.lerp(end, MaleniaEffectTimeline.smooth(progress));
            if (location.distanceToSqr(view) > distance * distance) continue;
            float alpha = (float) (0.65 * Math.min(1, (now - packet.lockTick()) / 1.5)
                    * (1 - MaleniaEffectTimeline.smooth((now - packet.activeTick()) / 6.0)));
            int index = Integer.parseInt(segment.identity().segment().substring("phantom_".length())) - 1;
            if (renderer == null) renderer = new PlatformMaleniaPhantomRenderer();
            poses.pushPose();
            try {
                poses.translate(location.x, location.y, location.z);
                double yaw = Math.atan2(end.z - start.z, end.x - start.x) * 180 / Math.PI - 90;
                poses.mulPose(Axis.YP.rotationDegrees((float) (180 - yaw)));
                renderer.draw(poses, boss, buffers, progress, index, alpha, detailedCount++ < 2);
                if (MaleniaWingShader.ready() && alpha > 0.01F) {
                    buffers.endBatch();
                    var transform = new org.joml.Matrix4f(poses.last().pose()).mul(renderer.wingAnchor());
                    MaleniaWingShader.configure(transform, (float) (now / 20), 0.7F, 1);
                    // Projections keep the wing silhouette, with a lighter density than their owner.
                    MaleniaWingGeometry.emit(buffers.getBuffer(MaleniaWingShader.WINGS), alpha * 0.7F,
                            (index * 43 & 255) / 255F, false);
                    buffers.endBatch(MaleniaWingShader.WINGS);
                }
            } finally { poses.popPose(); }
        }
    }

    public static boolean isPhantom(MaleniaEffectState.Segment segment) {
        return segment.identity().action() == com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId.SCARLET_PHANTOMS
                && segment.identity().segment().matches("phantom_[1-9][0-9]?");
    }
    private static Vec3 point(com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.Point point) {
        return new Vec3(point.x(), point.y(), point.z());
    }
    public static void clear() { renderer = null; }
}
