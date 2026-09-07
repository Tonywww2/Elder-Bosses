package com.tonywww.elder_bosses.platforms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public final class PlatformVertexConsumer {
    private PlatformVertexConsumer() {
    }

    public static void addPositionColor(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x,
            float y,
            float z,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        //? if forge {
        consumer.vertex(pose.pose(), x, y, z)
                .color(red, green, blue, alpha)
                .endVertex();
        //?} else {
        /*consumer.addVertex(pose, x, y, z)
                .setColor(red, green, blue, alpha);
        *///?}
    }

    public static void addPositionColorNormal(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x,
            float y,
            float z,
            int red,
            int green,
            int blue,
            int alpha,
            float normalX,
            float normalY,
            float normalZ
    ) {
        //? if forge {
        consumer.vertex(pose.pose(), x, y, z)
                .color(red, green, blue, alpha)
                .normal(pose.normal(), normalX, normalY, normalZ)
                .endVertex();
        //?} else {
        /*consumer.addVertex(pose, x, y, z)
                .setColor(red, green, blue, alpha)
                .setNormal(pose, normalX, normalY, normalZ);
        *///?}
    }
}