package com.tonywww.elder_bosses.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tonywww.elder_bosses.platforms.client.PlatformVertexConsumer;
import net.minecraft.world.phys.Vec3;

public final class MaleniaEffectGeometry {
    private MaleniaEffectGeometry() {}
    public static void quad(VertexConsumer out, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, float alpha) {
        vertex(out, pose, a, 0, 0, rgb, alpha); vertex(out, pose, b, 1, 0, rgb, alpha);
        vertex(out, pose, c, 1, 1, rgb, alpha); vertex(out, pose, d, 0, 1, rgb, alpha);
    }
    public static void petal(VertexConsumer out, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, float alpha, float from, float to) {
        vertex(out, pose, a, 0, from, rgb, alpha); vertex(out, pose, b, 1, from, rgb, alpha);
        vertex(out, pose, c, 1, to, rgb, alpha); vertex(out, pose, d, 0, to, rgb, alpha);
    }
    public static void band(VertexConsumer out, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 view, double width, int rgb, float alpha) {
        Vec3 side = b.subtract(a).cross(view.subtract(a)).normalize().scale(width);
        quad(out, pose, a.subtract(side), a.add(side), b.add(side), b.subtract(side), rgb, alpha);
    }
    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vec3 point, float u, float v, int rgb, float alpha) {
        PlatformVertexConsumer.addPositionColorUv(out, pose, (float) point.x, (float) point.y, (float) point.z,
                rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, Math.max(0, Math.min(255, Math.round(alpha * 255))), u, v);
    }
}
