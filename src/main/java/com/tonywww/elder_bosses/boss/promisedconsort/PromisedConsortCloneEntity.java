package com.tonywww.elder_bosses.boss.promisedconsort;

import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortAnimationTimeline;
import com.tonywww.elder_bosses.platforms.entity.PlatformArmorStand;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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
        private static final EntityDataAccessor<Long> APPEAR_TICK =
            SynchedEntityData.defineId(PromisedConsortCloneEntity.class, EntityDataSerializers.LONG);
        private static final EntityDataAccessor<Long> IMPACT_TICK =
            SynchedEntityData.defineId(PromisedConsortCloneEntity.class, EntityDataSerializers.LONG);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int remainingTicks = DEFAULT_LIFETIME_TICKS;
    private UUID ownerId;
    private long actionSequence = -1L;

    public PromisedConsortCloneEntity(
            EntityType<? extends PromisedConsortCloneEntity> entityType,
            Level level
    ) {
        super(entityType, level);
        setNoGravity(true);
        setInvulnerable(true);
    }

    public void configure(PromisedConsortEntity owner, int lifetimeTicks) {
        configure(owner, lifetimeTicks, level().getGameTime() + 4);
    }

    public void configure(PromisedConsortEntity owner, int lifetimeTicks, long impactTick) {
        ownerId = owner.getUUID();
        actionSequence = owner.activeActionSequence();
        remainingTicks = Math.max(1, lifetimeTicks);
        entityData.set(PARENT_ACTION, owner.actionId().map(Enum::ordinal).orElse(-1));
        entityData.set(APPEAR_TICK, level().getGameTime());
        entityData.set(IMPACT_TICK, Math.max(level().getGameTime() + 1, impactTick));
    }

    @Override
    protected void definePlatformSynchedData(SynchedDataRegistrar registrar) {
        registrar.define(PARENT_ACTION, -1);
        registrar.define(APPEAR_TICK, 0L);
        registrar.define(IMPACT_TICK, 4L);
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
            case PROMISED_CONSORT, CROSS_LEAP_COMBO -> "clone_cross_return_2";
            default -> "clone_overhead_3";
        };
    }

    public boolean isOwnedBy(PromisedConsortEntity owner) {
        return ownerId != null && ownerId.equals(owner.getUUID());
    }

    public long impactTick() {
        return entityData.get(IMPACT_TICK);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(0.0, 0.0, 0.0);
        if (level() instanceof ServerLevel serverLevel) {
            long now = serverLevel.getGameTime();
            if (now <= impactTick() && actionSequence >= 0) {
                long activeSequence = ownerId != null && serverLevel.getEntity(ownerId) instanceof PromisedConsortEntity owner
                        ? owner.activeActionSequence() : -1L;
                if (PromisedConsortAnimationTimeline.cancelledCloneContact(now, impactTick(), actionSequence, activeSequence)) {
                    discard();
                    return;
                }
            }
            if (--remainingTicks <= 0) discard();
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
        tag.putLong("ActionSequence", actionSequence);
        tag.putLong("AppearTick", entityData.get(APPEAR_TICK));
        tag.putLong("ImpactTick", entityData.get(IMPACT_TICK));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        remainingTicks = Math.max(1, tag.getInt("RemainingTicks"));
        entityData.set(PARENT_ACTION, tag.contains("ParentAction") ? tag.getInt("ParentAction") : -1);
        actionSequence = tag.contains("ActionSequence") ? tag.getLong("ActionSequence") : -1L;
        entityData.set(APPEAR_TICK, tag.contains("AppearTick") ? tag.getLong("AppearTick") : level().getGameTime());
        entityData.set(IMPACT_TICK, tag.contains("ImpactTick") ? tag.getLong("ImpactTick") : level().getGameTime() + 4);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<PromisedConsortCloneEntity>(
            this,
            "main",
            0,
            state -> state.setAndContinue(
                RawAnimation.begin().thenPlayAndHold("animation.promised_consort." + animationClip())
            )
        ) {
            @Override
            protected double adjustTick(double tick) {
                super.adjustTick(tick);
                double gameTime = level().getGameTime() + tick - Math.floor(tick);
                return PromisedConsortAnimationTimeline.cloneTick(gameTime, entityData.get(APPEAR_TICK), entityData.get(IMPACT_TICK));
            }
        });
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
