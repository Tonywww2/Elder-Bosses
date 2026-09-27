package com.tonywww.elder_bosses.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tonywww.elder_bosses.platforms.client.PlatformVertexConsumer;
//? if forge {
import static software.bernie.geckolib.util.RenderUtils.*;
//?} else {
/*import static software.bernie.geckolib.util.RenderUtil.*;
*///?}

/** Untextured support grids only. The GPU defines the silhouette, holes, flame flow and motes. */
public final class MaleniaWingGeometry {
    private static final int COLUMNS = 16, ROWS = 20;
    private MaleniaWingGeometry() {}

    /** Same pivot traversal as GeckoLib's entity renderer, for independent phantom poses. */
    public static org.joml.Matrix4f anchor(software.bernie.geckolib.cache.object.GeoBone bone) {
        var chain = new java.util.ArrayList<software.bernie.geckolib.cache.object.GeoBone>();
        for (var current = bone; current != null; current = current.getParent()) chain.add(0, current);
        PoseStack poses = new PoseStack();
        for (var current : chain) {
            translateMatrixToBone(poses, current);
            translateToPivotPoint(poses, current);
            rotateMatrixAroundBone(poses, current);
            scaleMatrixForBone(poses, current);
            if (current != bone) translateAwayFromPivotPoint(poses, current);
        }
        return new org.joml.Matrix4f(poses.last().pose());
    }

    public static void emit(VertexConsumer out, float opacity, float seed, boolean rearView) {
        PoseStack.Pose identity = new PoseStack().last();
        for (int order = 0; order < 3; order++) {
            int layer = rearView ? order : 2 - order;
            for (int side : new int[]{-1, 1}) for (int x = 0; x < COLUMNS; x++) for (int y = 0; y < ROWS; y++) {
                vertex(out, identity, x, y, side, layer, seed, opacity);
                vertex(out, identity, x + 1, y, side, layer, seed, opacity);
                vertex(out, identity, x + 1, y + 1, side, layer, seed, opacity);
                vertex(out, identity, x, y + 1, side, layer, seed, opacity);
            }
        }
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, int x, int y,
                               int side, int layer, float seed, float opacity) {
        float u = x * 1.12F / COLUMNS, v = y / (float) ROWS;
        // Position stores the domain; vertex color stores stable per-wing/layer parameters.
        PlatformVertexConsumer.addPositionColorUv(out, pose, u, v, layer,
                side < 0 ? 0 : 255, Math.round(layer * 127.5F), Math.round(seed * 255),
                Math.round(Math.max(0, Math.min(1, opacity)) * 255), u, v);
    }
}
