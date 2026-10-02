package com.tonywww.elder_bosses.platforms.client;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortCloneEntity;
import com.tonywww.elder_bosses.client.render.PromisedConsortCloneModel;
import com.tonywww.elder_bosses.client.vfx.ClientConsortCloneEffects;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
//? if forge {
import software.bernie.geckolib.core.object.Color;
//?} else {
/*import software.bernie.geckolib.util.Color;
*///?}

public abstract class PlatformConsortCloneRenderer extends GeoEntityRenderer<PromisedConsortCloneEntity> {
    protected PlatformConsortCloneRenderer(EntityRendererProvider.Context context) {
        super(context, new PromisedConsortCloneModel());
    }

    @Override
    public Color getRenderColor(PromisedConsortCloneEntity entity, float partialTick, int light) {
        if (entity.usesSourceRig() && !entity.projectPresentation()) return Color.ofRGBA(1.0F,1.0F,1.0F,
                com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourcePresentation.opacity(
                        entity.sourcePlayback(),entity.level().getGameTime()*50_000L+(long)(partialTick*50_000)));
        float opacity = ClientConsortCloneEffects.opacity(entity.level().getGameTime() + partialTick,
                entity.appearTick(), entity.impactTick(), entity.fadeEndTick());
        return Color.ofRGBA(1.0F, 1.0F, 1.0F, opacity);
    }
}
