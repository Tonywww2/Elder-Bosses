package com.tonywww.elder_bosses.client.render;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortCloneEntity;
import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class PromisedConsortCloneModel extends GeoModel<PromisedConsortCloneEntity> {
    private static final ResourceLocation MODEL =
            PlatformResourceLocation.id("geo/entity/promised_consort.geo.json");
    private static final ResourceLocation TEXTURE =
            PlatformResourceLocation.minecraft("textures/entity/armorstand/wood.png");
    private static final ResourceLocation ANIMATION =
            PlatformResourceLocation.id("animations/entity/promised_consort.animation.json");

    @Override
    public ResourceLocation getModelResource(PromisedConsortCloneEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(PromisedConsortCloneEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(PromisedConsortCloneEntity entity) {
        return ANIMATION;
    }
}
