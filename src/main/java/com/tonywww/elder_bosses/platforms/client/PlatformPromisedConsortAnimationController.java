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
    private Object sourceIdentity;
    public PlatformPromisedConsortAnimationController(PromisedConsortEntity entity) {
        super(entity, "main", 0, state -> {
            RawAnimation animation = RawAnimation.begin();
                var playback=entity.sourcePlayback();
                Object identity=playback==null?Integer.valueOf(entity.sourcePoseId()):playback.actor();
                var controller=(PlatformPromisedConsortAnimationController)state.getController();
                if(!java.util.Objects.equals(identity,controller.sourceIdentity)) {
                    controller.forceAnimationReset();controller.sourceIdentity=identity;
                }
                return state.setAndContinue(playback==null
                    ? animation.thenLoop("animation.promised_consort.source_"+String.format(java.util.Locale.ROOT,"%06d",entity.sourcePoseId()))
                    : animation.thenPlayAndHold(playback.animationClip()));
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
