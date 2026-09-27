package com.tonywww.elder_bosses.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tonywww.elder_bosses.boss.malenia.MaleniaEntity;
import com.tonywww.elder_bosses.network.IndicatorSnapshotPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** A gameplay cue, independent of ground-range and cosmetic skill-VFX switches. */
public final class ClientMaleniaParryCue {
    private static final Map<String, IndicatorSnapshotPacket> WINDOWS = new LinkedHashMap<>();
    private ClientMaleniaParryCue() {}
    public static void observe(IndicatorSnapshotPacket packet) {
        String key = packet.bossEntityId() + "/" + packet.indicatorId();
        if (!packet.instantGuardCue() || packet.state() == IndicatorSnapshotPacket.IndicatorState.EXPIRED) WINDOWS.remove(key);
        else WINDOWS.put(key, packet);
        while (WINDOWS.size() > 256) WINDOWS.remove(WINDOWS.keySet().iterator().next());
    }
    public static void render(PoseStack poses, Camera camera, float partialTick) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) { clear(); return; }
        double now = mc.level.getGameTime() + partialTick;
        WINDOWS.values().removeIf(packet -> now >= packet.endTick());
        if (!MaleniaParryCueShader.ready()) return;
        var drawn = new HashSet<Integer>();
        var buffers = mc.renderBuffers().bufferSource();
        Vec3 view = camera.getPosition();
        poses.pushPose(); poses.translate(-view.x, -view.y, -view.z);
        try {
            for (var packet : WINDOWS.values()) {
                if (now < packet.lockTick() || !(mc.level.getEntity(packet.bossEntityId()) instanceof MaleniaEntity boss)
                        || boss.distanceToSqr(view) > 64 * 64 || !drawn.add(boss.getId())) continue;
                Vec3 center = new Vec3(Mth.lerp(partialTick, boss.xo, boss.getX()),
                        Mth.lerp(partialTick, boss.yo, boss.getY()) + 2.55,
                        Mth.lerp(partialTick, boss.zo, boss.getZ()));
                Vec3 facing = view.subtract(center).normalize();
                Vec3 right = new Vec3(camera.getLeftVector()).scale(-1.25);
                Vec3 up = new Vec3(camera.getUpVector()).scale(1.25);
                center = center.add(facing.scale(0.15));
                MaleniaParryCueShader.configure((float) (now / 20),
                        (float) ((now - packet.lockTick()) / Math.max(1, packet.endTick() - packet.lockTick())),
                        packet.cuePulseCount(), packet.cueRgb());
                MaleniaEffectGeometry.quad(buffers.getBuffer(MaleniaParryCueShader.CUE), poses.last(),
                        center.subtract(right).subtract(up), center.add(right).subtract(up),
                        center.add(right).add(up), center.subtract(right).add(up), 0xFFFFFF, 1);
                buffers.endBatch(MaleniaParryCueShader.CUE);
            }
        } finally { poses.popPose(); }
    }
    public static void remove(int id) { WINDOWS.values().removeIf(packet -> packet.bossEntityId() == id); }
    public static void clear() { WINDOWS.clear(); }
}
