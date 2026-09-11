package com.tonywww.elder_bosses.platforms.client;

import com.tonywww.elder_bosses.boss.malenia.MaleniaEntity;
import software.bernie.geckolib.model.GeoModel;
//? if forge {
import software.bernie.geckolib.core.animation.AnimationState;
//?} else {
/*import software.bernie.geckolib.animation.AnimationState;
*///?}

public abstract class PlatformMaleniaGeoModel extends GeoModel<MaleniaEntity> {
    //? if forge {
    @Override
    public void handleAnimations(MaleniaEntity entity, long instanceId, AnimationState<MaleniaEntity> state) {
        entity.prepareAnimationFrame(state.getPartialTick());
        super.handleAnimations(entity, instanceId, state);
        afterAnimations(entity);
    }
    //?} else {
    /*@Override
    public void handleAnimations(MaleniaEntity entity, long instanceId,
                                 AnimationState<MaleniaEntity> state, float partialTick) {
        entity.prepareAnimationFrame(partialTick);
        super.handleAnimations(entity, instanceId, state, partialTick);
        afterAnimations(entity);
    }
    *///?}

    protected abstract void afterAnimations(MaleniaEntity entity);
}