package com.tonywww.elder_bosses.client.render;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class PromisedConsortModel extends GeoModel<PromisedConsortEntity> {
    private static final ResourceLocation MODEL =
            PlatformResourceLocation.id("geo/entity/promised_consort.geo.json");
    private static final ResourceLocation TEXTURE =
            PlatformResourceLocation.minecraft("textures/entity/armorstand/wood.png");
    private static final ResourceLocation ANIMATION =
            PlatformResourceLocation.id("animations/entity/promised_consort.animation.json");

    @Override
    public ResourceLocation getModelResource(PromisedConsortEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(PromisedConsortEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(PromisedConsortEntity entity) {
        return ANIMATION;
    }
}
