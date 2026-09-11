package com.tonywww.elder_bosses.platforms.client;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
//? if forge {
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
//?} else {
/*import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
*///?}

public final class PlatformPromisedConsortAnimationController extends AnimationController<PromisedConsortEntity> {
    public PlatformPromisedConsortAnimationController(PromisedConsortEntity entity) {
        super(entity, "main", 0, state -> {
            RawAnimation animation = RawAnimation.begin();
            String clip = "animation.promised_consort." + entity.animationClip();
            return state.setAndContinue(entity.hasSynchronizedAnimation()
                    ? animation.thenPlayAndHold(clip) : animation.thenLoop(clip));
        });
    }

    @Override
    protected double adjustTick(double tick) {
        double localTick = super.adjustTick(tick);
        if (getCurrentAnimation() == null || getAnimationState() == State.TRANSITIONING) {
            return localTick;
        }
        if (!animatable.hasSynchronizedAnimation()) {
            double gaitTick = animatable.locomotionAnimationTime();
            return gaitTick >= 0.0 ? gaitTick : localTick;
        }
        return Math.min(animatable.animationTime(),
                Math.max(0.0, getCurrentAnimation().animation().length() - 0.001));
    }
}