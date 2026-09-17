package com.tonywww.elder_bosses.client.render;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.client.vfx.ClientConsortBladeTrails;
import com.tonywww.elder_bosses.client.vfx.ClientConsortMeteorRenderer;
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
    public void render(PromisedConsortEntity entity, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        entity.prepareAnimationFrame(partialTick);
        if (!ClientConsortMeteorRenderer.bodyVisible(entity)) return;
        var offset = ClientConsortMeteorRenderer.visualOffset(entity, partialTick);
        poses.pushPose();
        try {
            poses.translate(offset.x, offset.y, offset.z);
            super.render(entity, yaw, partialTick, poses, buffers, light);
        } finally {
            poses.popPose();
        }
    }

    @Override
    public void firePostRenderEvent(PoseStack poses, BakedGeoModel model, MultiBufferSource buffers, float partialTick, int light) {
        if (getAnimatable() != null) ClientConsortBladeTrails.capture(getAnimatable(), getGeoModel(), partialTick);
        super.firePostRenderEvent(poses, model, buffers, partialTick, light);
    }
}
