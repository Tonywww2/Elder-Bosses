package com.tonywww.elder_bosses.client.render;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class PromisedConsortRenderer extends GeoEntityRenderer<PromisedConsortEntity> {
    public PromisedConsortRenderer(EntityRendererProvider.Context context) {
        super(context, new PromisedConsortModel());
        shadowRadius = 0.0F;
    }
}
