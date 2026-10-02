package com.tonywww.elder_bosses.platforms.client;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortCloneEntity;
import software.bernie.geckolib.model.GeoModel;
//? if forge {
import software.bernie.geckolib.core.animation.AnimationState;
//?} else {
/*import software.bernie.geckolib.animation.AnimationState;
*///?}

/** Run source compensation after Gecko has evaluated the clone's own curves. */
public abstract class PlatformPromisedConsortCloneGeoModel extends GeoModel<PromisedConsortCloneEntity> {
    //? if forge {
    @Override
    public void handleAnimations(PromisedConsortCloneEntity entity,long instanceId,AnimationState<PromisedConsortCloneEntity> state) {
        entity.prepareSourceFrame(state.getPartialTick());
        super.handleAnimations(entity,instanceId,state);
        afterAnimations(entity);
    }
    //?} else {
    /*@Override
    public void handleAnimations(PromisedConsortCloneEntity entity,long instanceId,AnimationState<PromisedConsortCloneEntity> state,float partialTick) {
        entity.prepareSourceFrame(partialTick);
        super.handleAnimations(entity,instanceId,state,partialTick);
        afterAnimations(entity);
    }
    *///?}
    protected abstract void afterAnimations(PromisedConsortCloneEntity entity);
}
