package com.tonywww.elder_bosses.client.render;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortCloneEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class PromisedConsortCloneRenderer extends GeoEntityRenderer<PromisedConsortCloneEntity> {
    public PromisedConsortCloneRenderer(EntityRendererProvider.Context context) {
        super(context, new PromisedConsortCloneModel());
        shadowRadius = 0.0F;
    }
}
