package com.tonywww.elder_bosses.client.render;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortCloneEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.tonywww.elder_bosses.platforms.client.PlatformConsortCloneRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;

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
        if(entity.cinematicMiquella()) {
            var owner=entity.cinematicOwner();
            if(owner==null || owner.combatState()!=com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortCombatState.TRANSITION
                    || com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTransition.miquellaOpacity(owner.sourceTransitionSeconds(partialTick))<=0) return;
        }
        entity.prepareSourceFrame(partialTick);
        poses.pushPose();
        if(entity.cinematicMiquella()) {
            var target=entity.miquellaRenderOrigin(partialTick);
            var actual=new net.minecraft.world.phys.Vec3(net.minecraft.util.Mth.lerp(partialTick,entity.xo,entity.getX()),net.minecraft.util.Mth.lerp(partialTick,entity.yo,entity.getY()),net.minecraft.util.Mth.lerp(partialTick,entity.zo,entity.getZ()));
            var delta=target.subtract(actual);poses.translate(delta.x,delta.y,delta.z);
        }
        try {super.render(entity, yaw, partialTick, poses, buffers, LightTexture.FULL_BRIGHT);}
        finally {poses.popPose();}
    }


}
