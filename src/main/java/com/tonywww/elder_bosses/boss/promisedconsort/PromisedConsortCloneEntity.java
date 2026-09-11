package com.tonywww.elder_bosses.boss.promisedconsort;

import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.platforms.entity.PlatformArmorStand;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
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

public final class PromisedConsortCloneEntity extends PlatformArmorStand implements GeoEntity {
    private static final int DEFAULT_LIFETIME_TICKS = 16;
    private static final EntityDataAccessor<Integer> PARENT_ACTION =
            SynchedEntityData.defineId(PromisedConsortCloneEntity.class, EntityDataSerializers.INT);

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
        entityData.set(PARENT_ACTION, owner.actionId().map(Enum::ordinal).orElse(-1));
    }

    @Override
    protected void definePlatformSynchedData(SynchedDataRegistrar registrar) {
        registrar.define(PARENT_ACTION, -1);
    }

    public String animationClip() {
        int ordinal = entityData.get(PARENT_ACTION);
        PromisedConsortActionId[] actions = PromisedConsortActionId.values();
        if (ordinal < 0 || ordinal >= actions.length) return "clone_overhead_3";
        return switch (actions[ordinal]) {
            case LIGHTSPEED_SIDE_DASH -> "clone_side_fan_3";
            case GRAVITY_METEOR -> "clone_meteor_4";
            case STARCALLER_CRY -> "clone_starcaller_2";
            case LIGHTSPEED_DASH -> "clone_dash_4";
            case PROMISED_CONSORT -> "clone_cross_return_2";
            default -> "clone_overhead_3";
        };
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
        tag.putInt("ParentAction", entityData.get(PARENT_ACTION));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        remainingTicks = Math.max(1, tag.getInt("RemainingTicks"));
        entityData.set(PARENT_ACTION, tag.contains("ParentAction") ? tag.getInt("ParentAction") : -1);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
            this,
            "main",
            0,
            state -> state.setAndContinue(
                RawAnimation.begin().thenPlayAndHold("animation.promised_consort." + animationClip())
            )
        ));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
