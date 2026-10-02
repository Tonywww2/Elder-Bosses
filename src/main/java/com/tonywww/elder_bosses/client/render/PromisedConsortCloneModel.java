package com.tonywww.elder_bosses.client.render;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortCloneEntity;
import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import com.tonywww.elder_bosses.platforms.client.PlatformPromisedConsortCloneGeoModel;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceAssets;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public final class PromisedConsortCloneModel extends PlatformPromisedConsortCloneGeoModel {
    private static final ResourceLocation MODEL =
            PlatformResourceLocation.id("geo/entity/promised_consort.geo.json");
    private static final ResourceLocation TEXTURE =
            PlatformResourceLocation.id("textures/entity/promised_consort/promised_consort_clone.png");

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
        return PlatformResourceLocation.id(PromisedConsortSourceAssets.animationResource(
                entity.sourcePlayback() == null ? 2000 : entity.sourcePlayback().actor().hkxId()));
    }

    @Override
    protected void afterAnimations(PromisedConsortCloneEntity entity) {
        if(!entity.projectPresentation()) getBone("frame_000").ifPresent(bone -> { bone.setPosX(0); bone.setPosY(0); bone.setPosZ(0); });
        boolean visible=entity.sourcePlayback()!=null && entity.sourcePlayback().actor().stateInfo()==413;
        getBone("src_084_Miquella_Root").ifPresent(bone -> {
            bone.setHidden(!visible); bone.setChildrenHidden(!visible);
        });
    }

    @Override
    public RenderType getRenderType(PromisedConsortCloneEntity entity, ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }
}
