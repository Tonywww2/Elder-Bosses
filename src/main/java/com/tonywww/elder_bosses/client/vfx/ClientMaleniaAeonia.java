package com.tonywww.elder_bosses.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import net.minecraft.world.phys.Vec3;

/** World anchored petals. The damage zone remains owned exclusively by the server. */
public final class ClientMaleniaAeonia {
    private ClientMaleniaAeonia() {}

    public static boolean isFlower(MaleniaEffectState.Segment segment) {
        return segment.persistent() && segment.identity().action() == MaleniaActionId.SCARLET_AEONIA;
    }

    public static void render(VertexConsumer out, PoseStack.Pose pose, MaleniaEffectState.Segment segment, double now) {
        var packet = segment.packet();
        if (!isFlower(segment) || packet.ranges().isEmpty() || now < packet.activeTick() || now >= packet.endTick()) return;
        Vec3 center = new Vec3(packet.anchor().x(), packet.anchor().y() + 0.04, packet.anchor().z());
        double radius = packet.ranges().get(0) * 1.32;
        double age = now - packet.activeTick();
        double opening = MaleniaEffectTimeline.smooth(age / 14.0);
        float fade = (float) Math.min(1, (packet.endTick() - now) / 16.0);
        for (int layer = 0; layer < 5; layer++) {
            int count = Math.max(6, 12 - layer * 2);
            double extent = radius * (0.98 - layer * 0.155);
            double spread = MaleniaEffectTimeline.smooth((age - layer * 1.5) / 12.0);
            for (int petal = 0; petal < count; petal++) {
                double angle = petal * Math.PI * 2 / count + layer * 0.43;
                Vec3 forward = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Vec3 side = new Vec3(-forward.z, 0, forward.x);
                Vec3 previous = center.add(forward.scale(radius * 0.055)).add(0, 0.20 + layer * 0.16, 0);
                double oldWidth = extent * 0.045;
                for (int joint = 1; joint <= 4; joint++) {
                    double t = joint / 4.0;
                    double horizontal = extent * (0.12 + 0.88 * spread) * t;
                    double height = extent * ((1 - spread) * t * 0.95
                            + spread * (0.18 * t + Math.sin(t * Math.PI * 0.70) * 0.58));
                    Vec3 next = center.add(forward.scale(horizontal)).add(0, height + layer * 0.20 + 0.16, 0);
                    double width = extent * (joint == 4 ? 0.06 : 0.30) * (0.65 + opening * 0.35);
                    double thickness = 0.12 + extent * 0.022 * Math.sin(t * Math.PI);
                    Vec3 a = previous.subtract(side.scale(oldWidth)), b = previous.add(side.scale(oldWidth));
                    Vec3 c = next.add(side.scale(width)), d = next.subtract(side.scale(width));
                    Vec3 down = new Vec3(0, -thickness, 0);
                    int color = layer < 2 ? (joint == 4 ? 0xFFB458 : 0xF87331) : layer < 4 ? 0xFF913C : 0xFFD079;
                    float from = (joint - 1) / 4F, to = joint / 4F;
                    MaleniaEffectGeometry.petal(out, pose, a, b, c, d, color, fade * 0.90F, from, to);
                    MaleniaEffectGeometry.petal(out, pose, b.add(down), a.add(down), d.add(down), c.add(down),
                            0xC74C36, fade * 0.82F, from, to);
                    MaleniaEffectGeometry.petal(out, pose, a.add(down), a, d, d.add(down), 0xFF9F50, fade * 0.88F, from, to);
                    MaleniaEffectGeometry.petal(out, pose, b, b.add(down), c.add(down), c, 0xFF9F50, fade * 0.88F, from, to);
                    previous = next; oldWidth = width;
                }
            }
        }
    }

    public static void ground(VertexConsumer out, PoseStack.Pose pose, MaleniaEffectState.Segment segment, double now) {
        var packet = segment.packet();
        if (segment.identity().action() != MaleniaActionId.SCARLET_AEONIA || packet.ranges().isEmpty()) return;
        boolean flower = isFlower(segment);
        if (!flower && !segment.identity().segment().equals("dive")) return;
        double age = now - packet.activeTick();
        if (age < 0 || now >= (flower ? packet.endTick() : packet.activeTick() + 6)) return;
        Vec3 center = new Vec3(packet.anchor().x(), packet.anchor().y() + 0.06, packet.anchor().z());
        double radius = packet.ranges().get(0);
        float fade = flower ? (float) Math.min(1, (packet.endTick() - now) / 12.0) : 1;
        if (flower) for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4 + Math.sin(i * 3.1) * 0.12;
            double extent = radius * (0.45 + (i % 3) * 0.07) * MaleniaEffectTimeline.smooth(age / 8);
            Vec3 previous = center;
            for (int joint = 1; joint <= 3; joint++) {
                double bend = angle + Math.sin(i * 2.7 + joint * 1.9) * 0.18;
                Vec3 next = center.add(Math.cos(bend) * extent * joint / 3, 0, Math.sin(bend) * extent * joint / 3);
                root(out, pose, previous, next, (4 - joint) * 0.025, fade * 0.45F);
                if (joint == 2) {
                    double branch = bend + (i % 2 == 0 ? 0.55 : -0.55);
                    root(out, pose, next, next.add(Math.cos(branch) * extent * 0.2, 0, Math.sin(branch) * extent * 0.2), 0.02, fade * 0.3F);
                }
                previous = next;
            }
        }
        double core = flower ? radius * 0.11 : 0;
        double wave = radius * Math.min(1, age / 7.0) * (flower ? 1 : 0.55);
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12, b = (i + 1) * Math.PI / 12;
            Vec3 da = new Vec3(Math.cos(a), 0, Math.sin(a)), db = new Vec3(Math.cos(b), 0, Math.sin(b));
            if (flower) MaleniaEffectGeometry.quad(out, pose, center, center.add(da.scale(core)), center.add(db.scale(core)), center,
                    age < 5 ? 0xFFC385 : 0x893B33, fade * (age < 5 ? 0.7F : 0.45F));
            if (age < 7) MaleniaEffectGeometry.quad(out, pose, center.add(da.scale(wave * 0.94)), center.add(da.scale(wave)),
                    center.add(db.scale(wave)), center.add(db.scale(wave * 0.94)), flower ? 0xF3B277 : 0xD4C7AE, (float) (0.65 * (1 - age / 7)));
        }
    }

    private static void root(VertexConsumer out, PoseStack.Pose pose, Vec3 start, Vec3 end, double width, float alpha) {
        Vec3 side = end.subtract(start).cross(new Vec3(0, 1, 0)).normalize().scale(width);
        MaleniaEffectGeometry.quad(out, pose, start.subtract(side), start.add(side), end.add(side.scale(0.5)), end.subtract(side.scale(0.5)), 0x602C32, alpha);
    }
}
