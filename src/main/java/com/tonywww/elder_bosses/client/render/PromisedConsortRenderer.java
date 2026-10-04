package com.tonywww.elder_bosses.client.render;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.client.vfx.ClientConsortBladeTrails;
import com.tonywww.elder_bosses.client.vfx.ClientConsortMeteorRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
//? if forge {
import software.bernie.geckolib.core.object.Color;
//?} else {
/*import software.bernie.geckolib.util.Color;
*///?}

public final class PromisedConsortRenderer extends GeoEntityRenderer<PromisedConsortEntity> {
    public PromisedConsortRenderer(EntityRendererProvider.Context context) {
        super(context, new PromisedConsortModel());
        shadowRadius = 0.9F;
    }

    @Override
    public void render(PromisedConsortEntity entity, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        entity.prepareAnimationFrame(partialTick);
        if (!entity.usesSourceRig() && !ClientConsortMeteorRenderer.bodyVisible(entity)) return;
        var offset = entity.usesSourceRig()?net.minecraft.world.phys.Vec3.ZERO:ClientConsortMeteorRenderer.visualOffset(entity, partialTick);
        double bodyOffset=entity.usesSourceRig()?entity.sourceBodyOffsetY():0;
        var playback=entity.sourcePlayback();
        if(playback!=null && playback.actor().taeId()==3024)
            bodyOffset=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceGrounding.starfallOffset(
                    playback.sourceMicros(entity.level().getGameTime()*50_000L+(long)(partialTick*50_000)));
        if(com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTransition.cinematic(playback))
            bodyOffset=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceGrounding.transitionOffset(playback.poseMicros(entity.level().getGameTime()*50_000L+(long)(partialTick*50_000)));
        if(playback!=null && playback.actor().taeId()==3017) {
            long source=playback.sourceMicros(entity.level().getGameTime()*50_000L+(long)(partialTick*50_000));
            if(source>=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceLanding.GRAVITY_DESCENT_BEGIN)
                bodyOffset=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceGrounding.gravityMeteorOffset(source,playback.poseId());
        }
        poses.pushPose();
        try {
            poses.translate(offset.x, offset.y+bodyOffset, offset.z);
            super.render(entity, yaw, partialTick, poses, buffers, light);
        } finally {
            poses.popPose();
        }
    }
    @Override public Color getRenderColor(PromisedConsortEntity entity,float partialTick,int light) {
        if(!entity.usesSourceRig()) return super.getRenderColor(entity,partialTick,light);
        float alpha=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourcePresentation.opacity(
                entity.sourcePlayback(),entity.level().getGameTime()*50_000L+(long)(partialTick*50_000));
        return Color.ofRGBA(1F,1F,1F,alpha);
    }

    @Override
    public void firePostRenderEvent(PoseStack poses, BakedGeoModel model, MultiBufferSource buffers, float partialTick, int light) {
        if (getAnimatable() != null && !getAnimatable().usesSourceRig()) ClientConsortBladeTrails.capture(getAnimatable(), getGeoModel(), partialTick);
        super.firePostRenderEvent(poses, model, buffers, partialTick, light);
    }
}
