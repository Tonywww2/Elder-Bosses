package com.tonywww.elder_bosses.mixin;

import com.tonywww.elder_bosses.client.render.ConsortGrabPlayerRenderer;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourcePlayerPose;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First-person eye follows70890's head, while the entity stays at the throw anchor. */
@Mixin(Camera.class)
public abstract class ConsortGrabCameraMixin {
    @Shadow protected abstract void setPosition(double x,double y,double z);
    @Inject(method="setup",at=@At("RETURN"))
    private void elderBosses$sourceGrabEye(BlockGetter level,Entity entity,boolean thirdPerson,boolean mirrored,float partial,CallbackInfo ci) {
        if(thirdPerson || !(entity instanceof Player player)) return;
        var boss=ConsortGrabPlayerRenderer.owner(player);if(boss==null) return;
        var state=boss.sourceGrab();long time=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTimeWarp.from(state.getLongArray("Warp")).sourceAt(Math.max(0,(long)((boss.level().getGameTime()+partial)*50_000)-state.getLong("StartMicros")));
        var pose=PromisedConsortSourcePlayerPose.get();
        Vector3f eye=pose.matrix(pose.bone("Head"),Math.max(0,time)).transformPosition(new Vector3f(0,.3f,0));
        eye.z=-eye.z;eye.rotateY((float)Math.toRadians(-state.getFloat("Yaw")));
        setPosition(net.minecraft.util.Mth.lerp(partial,player.xo,player.getX())+eye.x,
                net.minecraft.util.Mth.lerp(partial,player.yo,player.getY())+eye.y,
                net.minecraft.util.Mth.lerp(partial,player.zo,player.getZ())+eye.z);
    }
}
