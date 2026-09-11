package com.tonywww.elder_bosses.client.render;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.client.vfx.ClientConsortBladeTrails;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class PromisedConsortRenderer extends GeoEntityRenderer<PromisedConsortEntity> {
    public PromisedConsortRenderer(EntityRendererProvider.Context context) {
        super(context, new PromisedConsortModel());
        shadowRadius = 0.9F;
    }

    @Override
    public void firePostRenderEvent(PoseStack poses, BakedGeoModel model, MultiBufferSource buffers, float partialTick, int light) {
        if (getAnimatable() != null) ClientConsortBladeTrails.capture(getAnimatable(), getGeoModel(), partialTick);
        super.firePostRenderEvent(poses, model, buffers, partialTick, light);
    }
}
