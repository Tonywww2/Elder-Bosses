package com.tonywww.elder_bosses.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tonywww.elder_bosses.boss.malenia.MaleniaEntity;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.model.GeoModel;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;

public final class ClientMaleniaBladeTrails {
    private static final Map<MaleniaEntity, Trail> TRAILS = new WeakHashMap<>();
    private ClientMaleniaBladeTrails() {}

    public static void capture(MaleniaEntity entity, GeoModel<MaleniaEntity> model, float partialTick) {
        if (!ElderBossesCommonConfig.VALUES.skillVfx().enabled() || entity.actionId().isEmpty()) {
            TRAILS.remove(entity);
            return;
        }
        double now = entity.tickCount + partialTick;
        String clip = entity.animationClip();
        int window = MaleniaEffectTimeline.bladeWindow(clip, entity.animationTime(partialTick));
        Trail trail = TRAILS.computeIfAbsent(entity, unused -> new Trail());
        if (now == trail.lastFrame) return;
        if (trail.seed != entity.actionSeed() || !trail.clip.equals(clip) || now - trail.lastFrame > 1.5
                || now < trail.lastFrame || entity.position().distanceToSqr(trail.position) > 64) trail.samples.clear();
        trail.seed = entity.actionSeed(); trail.clip = clip; trail.lastFrame = now; trail.position = entity.position();
        trail.samples.removeIf(sample -> now - sample.time() > MaleniaEffectTimeline.trailLifetime(clip));
        if (window < 0) return;
        Vec3 offset = new Vec3(Mth.lerp(partialTick, entity.xo, entity.getX()) - entity.getX(),
                Mth.lerp(partialTick, entity.yo, entity.getY()) - entity.getY(),
                Mth.lerp(partialTick, entity.zo, entity.getZ()) - entity.getZ());
        Vec3 root = bone(model, "blade_root", offset), tip = bone(model, "blade_tip", offset);
        if (root == null || tip == null || !Double.isFinite(tip.lengthSqr()) || tip.distanceToSqr(entity.position()) > 100) return;
        Sample previous = trail.samples.peekLast();
        if (previous != null && (previous.window() != window || previous.tip().distanceToSqr(tip) > 36)) trail.samples.clear();
        if (previous == null || now - previous.time() >= 0.125) trail.samples.addLast(new Sample(now, root, tip, window));
        while (trail.samples.size() > 24) trail.samples.removeFirst();
    }

    public static void render(PoseStack poses, Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !ElderBossesCommonConfig.VALUES.skillVfx().enabled()) { clear(); return; }
        if (!MaleniaEffectShader.ready()) return;
        Vec3 view = camera.getPosition();
        double distance = ElderBossesCommonConfig.VALUES.skillVfx().renderDistance();
        var buffers = mc.renderBuffers().bufferSource();
        TRAILS.keySet().removeIf(entity -> entity.isRemoved() || entity.level() != mc.level || entity.actionId().isEmpty());
        poses.pushPose();
        poses.translate(-view.x, -view.y, -view.z);
        try {
            MaleniaEffectShader.mode(0);
            var out = buffers.getBuffer(MaleniaEffectShader.EFFECT);
            for (var entry : TRAILS.entrySet()) {
                MaleniaEntity entity = entry.getKey(); Trail trail = entry.getValue();
                if (entity.distanceToSqr(view) > distance * distance || entity.actionSeed() != trail.seed) continue;
                double now = entity.tickCount + partialTick, lifetime = MaleniaEffectTimeline.trailLifetime(trail.clip);
                Sample previous = null;
                for (Sample sample : trail.samples) {
                    double age = now - sample.time();
                    if (age < 0 || age > lifetime) continue;
                    float alpha = (float) (0.78 * Math.pow(1 - age / lifetime, 1.5));
                    if (previous != null && sample.time() - previous.time() <= 1 && previous.window() == sample.window()) {
                        MaleniaEffectGeometry.quad(out, poses.last(), previous.root(), previous.tip(), sample.tip(), sample.root(), 0xDCEBF2, alpha * 0.55F);
                        MaleniaEffectGeometry.quad(out, poses.last(), previous.root().lerp(previous.tip(), 0.83), previous.tip(),
                                sample.tip(), sample.root().lerp(sample.tip(), 0.83), 0xFFF3D7, alpha);
                    }
                    previous = sample;
                }
                if (previous != null && now - previous.time() < 0.5) {
                    MaleniaEffectGeometry.band(out, poses.last(), previous.root(), previous.tip(), view, 0.025, 0xFFF7E3, 0.8F);
                }
            }
            buffers.endBatch(MaleniaEffectShader.EFFECT);
        } finally { poses.popPose(); }
    }

    private static Vec3 bone(GeoModel<MaleniaEntity> model, String name, Vec3 offset) {
        var bone = model.getBone(name).orElse(null);
        if (bone == null) return null;
        var p = bone.getWorldPosition();
        return new Vec3(p.x, p.y, p.z).add(offset);
    }
    public static void remove(int id) { TRAILS.keySet().removeIf(entity -> entity.getId() == id); }
    public static void clear() { TRAILS.clear(); }
    private static final class Trail {
        private long seed;
        private String clip = "";
        private double lastFrame = -1;
        private Vec3 position = Vec3.ZERO;
        private final Deque<Sample> samples = new ArrayDeque<>();
    }
    private record Sample(double time, Vec3 root, Vec3 tip, int window) {}
}
