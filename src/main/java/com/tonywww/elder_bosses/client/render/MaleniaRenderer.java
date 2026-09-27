package com.tonywww.elder_bosses.client.render;

import com.tonywww.elder_bosses.boss.malenia.MaleniaEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class MaleniaRenderer extends GeoEntityRenderer<MaleniaEntity> {
    @Override
    public void firePostRenderEvent(com.mojang.blaze3d.vertex.PoseStack poses, software.bernie.geckolib.cache.object.BakedGeoModel model,
                                    net.minecraft.client.renderer.MultiBufferSource buffers, float partialTick, int light) {
        if (getAnimatable() != null) {
            com.tonywww.elder_bosses.client.vfx.ClientMaleniaBladeTrails.capture(getAnimatable(), getGeoModel(), partialTick);
        }
        super.firePostRenderEvent(poses, model, buffers, partialTick, light);
    }

    public MaleniaRenderer(EntityRendererProvider.Context context) {
        super(context, new MaleniaModel());
        shadowRadius = 0.45F;
    }

    @Override
    public void render(MaleniaEntity entity, float yaw, float partialTick,
                       com.mojang.blaze3d.vertex.PoseStack poses,
                       net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        super.render(entity, yaw, partialTick, poses, buffers, light);
        // Explicit owner, after animation evaluation; no reliance on post-event renderer state.
        com.tonywww.elder_bosses.client.vfx.ClientMaleniaWings.capture(entity, getGeoModel(), partialTick);
    }

    @Override
    public boolean shouldRender(MaleniaEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double x, double y, double z) {
        return super.shouldRender(entity, frustum, x, y, z)
                || com.tonywww.elder_bosses.client.vfx.ClientMaleniaWings.visible(entity)
                && entity.shouldRender(x, y, z) && frustum.isVisible(entity.getBoundingBox().inflate(5.0));
    }
}
