package com.tonywww.elder_bosses.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tonywww.elder_bosses.client.audio.ClientBossMusic;
import com.tonywww.elder_bosses.network.MaleniaHitFeedbackPacket;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;
import com.tonywww.elder_bosses.platforms.registry.ModSoundEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.Deque;

/** Confirmed server results only; called after the contact packet's age and duplicate checks. */
public final class ClientMaleniaParryFeedback {
    private static final Deque<Flash> FLASHES = new ArrayDeque<>();
    private ClientMaleniaParryFeedback() {}

    public static void observe(MaleniaHitFeedbackPacket packet) {
        if (packet.result() != MaleniaHitFeedbackPacket.Result.INSTANT_GUARD) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var p = packet.point();
        Vec3 center = new Vec3(p.x(), p.y(), p.z());
        if (center.distanceToSqr(mc.player.position()) > 64 * 64) return;
        // Receipts are stamped locally so normal packet delay cannot erase the initial flash.
        FLASHES.addLast(new Flash(packet.bossId(), center, mc.level.getGameTime()));
        while (FLASHES.size() > 64) FLASHES.removeFirst();
        mc.level.playLocalSound(center.x, center.y, center.z, ModSoundEvents.MALENIA_PARRY_SUCCESS.get(),
                SoundSource.PLAYERS, 1.25F, 1F, false);
        if (packet.targetId() == mc.player.getId()) ClientBossMusic.emphasizeParry();
        if (!ElderBossesCommonConfig.VALUES.skillVfx().enabled()) return;
        for (int i = 0; i < 20; i++) {
            double angle = i * Math.PI * 2 / 20;
            mc.level.addParticle(i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK,
                    center.x, center.y, center.z, Math.cos(angle) * 0.24,
                    0.08 + (i % 4) * 0.035, Math.sin(angle) * 0.24);
        }
    }

    public static void render(PoseStack poses, Camera camera, float partialTick) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) { clear(); return; }
        double now = mc.level.getGameTime() + partialTick;
        FLASHES.removeIf(flash -> now - flash.tick() >= 10 || now < flash.tick());
        if (!MaleniaParryCueShader.ready() || !ElderBossesCommonConfig.VALUES.skillVfx().enabled()) return;
        var buffers = mc.renderBuffers().bufferSource();
        Vec3 view = camera.getPosition();
        Vec3 right = new Vec3(camera.getLeftVector()).scale(-1.35);
        Vec3 up = new Vec3(camera.getUpVector()).scale(1.35);
        poses.pushPose(); poses.translate(-view.x, -view.y, -view.z);
        try {
            for (Flash flash : FLASHES) {
                if (flash.center().distanceToSqr(view) > 64 * 64) continue;
                MaleniaParryCueShader.configureSuccess((float) ((now - flash.tick()) / 10));
                Vec3 center = flash.center();
                MaleniaEffectGeometry.quad(buffers.getBuffer(MaleniaParryCueShader.CUE), poses.last(),
                        center.subtract(right).subtract(up), center.add(right).subtract(up),
                        center.add(right).add(up), center.subtract(right).add(up), 0xFFFFFF, 1);
                buffers.endBatch(MaleniaParryCueShader.CUE);
            }
        } finally { poses.popPose(); }
    }

    public static void remove(int id) { FLASHES.removeIf(flash -> flash.bossId() == id); }
    public static void clear() { FLASHES.clear(); }
    private record Flash(int bossId, Vec3 center, long tick) {}
}
