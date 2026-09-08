package com.tonywww.elder_bosses.boss.promisedconsort;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
//? if forge {
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.RawAnimation;
//?} else {
/*import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.RawAnimation;
*///?}
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public final class PromisedConsortCloneEntity extends ArmorStand implements GeoEntity {
    private static final int DEFAULT_LIFETIME_TICKS = 16;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int remainingTicks = DEFAULT_LIFETIME_TICKS;
    private UUID ownerId;

    public PromisedConsortCloneEntity(
            EntityType<? extends PromisedConsortCloneEntity> entityType,
            Level level
    ) {
        super(entityType, level);
        setNoGravity(true);
        setInvulnerable(true);
    }

    public void configure(PromisedConsortEntity owner, int lifetimeTicks) {
        ownerId = owner.getUUID();
        remainingTicks = Math.max(1, lifetimeTicks);
    }

    public boolean isOwnedBy(PromisedConsortEntity owner) {
        return ownerId != null && ownerId.equals(owner.getUUID());
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(0.0, 0.0, 0.0);
        if (!level().isClientSide && --remainingTicks <= 0) {
            discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
        tag.putInt("RemainingTicks", remainingTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        remainingTicks = Math.max(1, tag.getInt("RemainingTicks"));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
            this,
            "main",
            0,
            state -> state.setAndContinue(
                RawAnimation.begin().thenLoop("animation.promised_consort.idle")
            )
        ));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
