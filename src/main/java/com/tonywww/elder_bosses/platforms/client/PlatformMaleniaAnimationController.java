package com.tonywww.elder_bosses.platforms.client;

import com.tonywww.elder_bosses.boss.malenia.MaleniaEntity;
//? if forge {
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
//?} else {
/*import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
*///?}

public final class PlatformMaleniaAnimationController extends AnimationController<MaleniaEntity> {
    public PlatformMaleniaAnimationController(MaleniaEntity entity) {
        super(entity, "main", 0, state -> state.setAndContinue(
                RawAnimation.begin().thenLoop("animation.malenia." + entity.animationClip())));
    }

    @Override
    protected double adjustTick(double tick) {
        double localTick = super.adjustTick(tick);
        if (getCurrentAnimation() == null || getAnimationState() == State.TRANSITIONING) {
            return localTick;
        }
        if (!animatable.hasSynchronizedAnimation()) {
            double gaitTick = animatable.locomotionTime();
            return gaitTick >= 0.0 ? gaitTick : localTick;
        }
        return Math.min(animatable.animationTime(),
                Math.max(0.0, getCurrentAnimation().animation().length() - 0.001));
    }
}