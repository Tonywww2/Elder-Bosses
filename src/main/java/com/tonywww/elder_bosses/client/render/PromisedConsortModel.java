package com.tonywww.elder_bosses.client.render;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceAssets;
import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import com.tonywww.elder_bosses.platforms.client.PlatformPromisedConsortGeoModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** One production rig and animation bank for combat, previews and dormant poses. */
public final class PromisedConsortModel extends PlatformPromisedConsortGeoModel {
    private static final ResourceLocation MODEL = PlatformResourceLocation.id("geo/entity/promised_consort.geo.json");
    private static final ResourceLocation TEXTURE = PlatformResourceLocation.id("textures/entity/promised_consort/promised_consort.png");

    @Override public ResourceLocation getModelResource(PromisedConsortEntity entity) { return MODEL; }
    @Override public ResourceLocation getTextureResource(PromisedConsortEntity entity) { return TEXTURE; }
    @Override public ResourceLocation getAnimationResource(PromisedConsortEntity entity) {
        return PlatformResourceLocation.id(PromisedConsortSourceAssets.animationResource(entity.sourcePoseId()));
    }
    @Override public RenderType getRenderType(PromisedConsortEntity entity, ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }
    @Override protected void afterAnimations(PromisedConsortEntity entity) {
        // The baked geometry is shared with cinematic/attack clones.
        for(var bone:getAnimationProcessor().getRegisteredBones()) {bone.setHidden(false);bone.setChildrenHidden(false);}
        // Actor translation belongs to the server; preserve all body Root/Pelvis channels.
        if (entity.sourcePlayback() == null || entity.sourcePlayback().actor().actionSequence() < (1L << 60))
            getBone("frame_000").ifPresent(bone -> { bone.setPosX(0); bone.setPosY(0); bone.setPosZ(0); });
        // Switch both representations on the same partial-frame clock; never interpolate between them.
        boolean miquella=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTransition.cinematic(entity.sourcePlayback())
                && entity.combatState()==com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortCombatState.TRANSITION
                ?entity.animationTime()/20>=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTransition.TELEPORT:entity.miquellaVisible();
        getBone("src_084_Miquella_Root").ifPresent(bone -> {
            bone.setHidden(!miquella);
            bone.setChildrenHidden(!miquella);
        });
    }
}
