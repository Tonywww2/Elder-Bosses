package com.tonywww.elder_bosses.platforms.client;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import software.bernie.geckolib.model.GeoModel;
//? if forge {
import software.bernie.geckolib.core.animation.AnimationState;
//?} else {
/*import software.bernie.geckolib.animation.AnimationState;
*///?}

public abstract class PlatformPromisedConsortGeoModel extends GeoModel<PromisedConsortEntity> {
    //? if forge {
    @Override
    public void handleAnimations(PromisedConsortEntity entity, long instanceId, AnimationState<PromisedConsortEntity> state) {
        entity.prepareAnimationFrame(state.getPartialTick());
        super.handleAnimations(entity, instanceId, state);
        afterAnimations(entity);
    }
    //?} else {
    /*@Override
    public void handleAnimations(PromisedConsortEntity entity, long instanceId,
                                 AnimationState<PromisedConsortEntity> state, float partialTick) {
        entity.prepareAnimationFrame(partialTick);
        super.handleAnimations(entity, instanceId, state, partialTick);
        afterAnimations(entity);
    }
    *///?}

    protected abstract void afterAnimations(PromisedConsortEntity entity);
}