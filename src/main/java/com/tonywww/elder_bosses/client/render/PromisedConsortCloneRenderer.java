package com.tonywww.elder_bosses.client.render;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortCloneEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.tonywww.elder_bosses.platforms.client.PlatformConsortCloneRenderer;
import com.tonywww.elder_bosses.client.vfx.ClientConsortCloneEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import software.bernie.geckolib.cache.object.BakedGeoModel;

public final class PromisedConsortCloneRenderer extends PlatformConsortCloneRenderer {
    public PromisedConsortCloneRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public boolean shouldShowName(PromisedConsortCloneEntity entity) {
        return false;
    }

    @Override
    public void render(PromisedConsortCloneEntity entity, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        for (String marker : java.util.List.of("blade_root_l", "blade_tip_l", "blade_root_r", "blade_tip_r"))
            getGeoModel().getBone(marker).ifPresent(bone -> bone.setTrackingMatrices(true));
        super.render(entity, yaw, partialTick, poses, buffers, LightTexture.FULL_BRIGHT);
    }

    @Override
    public void firePostRenderEvent(PoseStack poses, BakedGeoModel model, MultiBufferSource buffers, float partialTick, int light) {
        if (getAnimatable() != null) ClientConsortCloneEffects.capture(getAnimatable(), getGeoModel(), partialTick);
        super.firePostRenderEvent(poses, model, buffers, partialTick, light);
    }
}
