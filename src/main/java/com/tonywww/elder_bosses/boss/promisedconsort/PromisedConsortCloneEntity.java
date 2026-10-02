package com.tonywww.elder_bosses.boss.promisedconsort;

import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortAnimationTimeline;
import com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortOriginalAnimationRegistry;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourcePlayback;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceAssets;
import com.tonywww.elder_bosses.platforms.entity.PlatformArmorStand;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
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
    private static final EntityDataAccessor<CompoundTag> SOURCE_PLAYBACK =
            SynchedEntityData.defineId(PromisedConsortCloneEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private Vec3 sourceOrigin;
    private float sourceYaw;
    private double sourceAnimationTicks;
    private static final EntityDataAccessor<Integer> PARENT_ACTION =
            SynchedEntityData.defineId(PromisedConsortCloneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> APPEAR_TICK =
            SynchedEntityData.defineId(PromisedConsortCloneEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> IMPACT_TICK =
            SynchedEntityData.defineId(PromisedConsortCloneEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> FADE_END_TICK =
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
        entityData.set(FADE_END_TICK, level().getGameTime() + remainingTicks);
        if(owner.usesSourceRig()) {
            int tae=switch(owner.actionId().orElse(PromisedConsortActionId.LIGHTSPEED_SLASH)) {
                case LIGHTSPEED_DASH -> 20002;case LIGHTSPEED_SIDE_DASH -> 20003;
                case GRAVITY_METEOR -> 20004;case STARCALLER_CRY -> 20005;
                case PROMISED_CONSORT,CROSS_LEAP_COMBO -> 20003;default -> 20006;
            };
            var clip=PromisedConsortSourceAssets.bank().requireClip(tae);
            long contact=clip.events().stream().filter(e->e.type()==1).mapToLong(e->Math.max(0,e.startMicros())).min().orElse(clip.durationMicros()/2);
            double speed=Math.max(1,contact)/(Math.max(1,impactTick()-appearTick())*50_000.0);
            var actor=new com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTimeline.Actor((1L<<60)+Math.max(0,actionSequence),0,0,tae,clip.hkxId(),owner.phase()==com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase.PHASE_TWO?413:412);
            entityData.set(SOURCE_PLAYBACK,new PromisedConsortSourcePlayback(actor,appearTick()*50_000L,speed).encode());
        }
    }

    @Override
    protected void definePlatformSynchedData(SynchedDataRegistrar registrar) {
        registrar.define(SOURCE_PLAYBACK, new CompoundTag());
        registrar.define(PARENT_ACTION, -1);
        registrar.define(APPEAR_TICK, 0L);
        registrar.define(IMPACT_TICK, 4L);
        registrar.define(FADE_END_TICK, 16L);
    }

    public String animationClip() {
        PromisedConsortActionId action = parentAction();
        if (action == null) return "clone_overhead_3";
        return switch (action) {
            case LIGHTSPEED_SIDE_DASH -> "clone_side_fan_3";
            case GRAVITY_METEOR -> "clone_meteor_4";
            case STARCALLER_CRY -> "clone_starcaller_2";
            case LIGHTSPEED_DASH -> "clone_dash_4";
            case PROMISED_CONSORT, CROSS_LEAP_COMBO -> "clone_cross_return_2";
            default -> "clone_overhead_3";
        };
    }

    public boolean usesOriginalAnimation() {
        return originalAnimationClip() != null;
    }

    public String originalAnimationClip() {
        if (usesSourceRig()) return null;
        PromisedConsortActionId action = parentAction();
        return action == null ? null : PromisedConsortOriginalAnimationRegistry.actionClip(action).orElse(null);
    }

    private PromisedConsortActionId parentAction() {
        int ordinal = entityData.get(PARENT_ACTION);
        PromisedConsortActionId[] actions = PromisedConsortActionId.values();
        return ordinal < 0 || ordinal >= actions.length ? null : actions[ordinal];
    }

    public boolean isOwnedBy(PromisedConsortEntity owner) {
        return ownerId != null && ownerId.equals(owner.getUUID());
    }

    public long impactTick() {
        return entityData.get(IMPACT_TICK);
    }

    public long appearTick() {
        return entityData.get(APPEAR_TICK);
    }

    public long fadeEndTick() {
        return entityData.get(FADE_END_TICK);
    }

    public void configureSource(PromisedConsortEntity owner,PromisedConsortSourcePlayback playback,Vec3 origin,float yaw) {
        if (level().isClientSide || playback.actor().slot()<0) throw new IllegalArgumentException("Invalid source clone");
        ownerId=owner.getUUID(); sourceOrigin=origin; sourceYaw=yaw;
        entityData.set(SOURCE_PLAYBACK,playback.encode());
        entityData.set(APPEAR_TICK,playback.startWorldMicros()/50_000L);
        entityData.set(IMPACT_TICK,playback.startWorldMicros()/50_000L+1);
        long sourceEnd=PromisedConsortSourceAssets.bank().requireClip(playback.actor().taeId()).events().stream()
                .filter(e -> (e.type()==66 || e.type()==67) && e.referenceId()==20011581+2*playback.actor().slot())
                .mapToLong(e -> Math.max(0,e.startMicros())).min().orElseThrow();
        entityData.set(FADE_END_TICK,(long)Math.ceil((playback.startWorldMicros()+sourceEnd/playback.speed())/50_000.0));
        addTag("elder_bosses_source_preview");
        updateSourcePosition();
    }
    private boolean sourceCombatDriven;
    public void bindSourceCombatMotion() {sourceCombatDriven=true;}
    public boolean usesSourceRig() { return !entityData.get(SOURCE_PLAYBACK).isEmpty(); }
    public boolean projectPresentation() {return usesSourceRig() && sourcePlayback().actor().actionSequence()>=(1L<<60);}
    public PromisedConsortSourcePlayback sourcePlayback() { return PromisedConsortSourcePlayback.decode(entityData.get(SOURCE_PLAYBACK)); }
    public void prepareSourceFrame(float partialTick) {
        if (usesSourceRig()) sourceAnimationTicks=sourcePlayback().animationTicks(level().getGameTime(),partialTick);
    }
    private void updateSourcePosition() {
        Vec3 position=sourceOrigin.add(sourcePlayback().displacement(level().getGameTime()*50_000L,sourceYaw));
        float heading=sourcePlayback().yawAt(level().getGameTime()*50_000L,sourceYaw);
        setPos(position.x,position.y,position.z); setYRot(heading); setYBodyRot(heading); setYHeadRot(heading);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(0.0, 0.0, 0.0);
        if (level() instanceof ServerLevel serverLevel) {
            if (usesSourceRig() && !projectPresentation()) {
                if (ownerId==null || !(serverLevel.getEntity(ownerId) instanceof PromisedConsortEntity owner)
                        || owner.isRemoved()) discard();
                else if(!sourceCombatDriven) updateSourcePosition();
                return;
            }
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
    public boolean shouldBeSaved() { return (!usesSourceRig() || projectPresentation()) && super.shouldBeSaved(); }

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
        tag.putLong("FadeEndTick", entityData.get(FADE_END_TICK));
        if(projectPresentation()) tag.put("SourcePresentation",entityData.get(SOURCE_PLAYBACK));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if(tag.contains("SourcePresentation")) entityData.set(SOURCE_PLAYBACK,tag.getCompound("SourcePresentation"));
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        remainingTicks = Math.max(1, tag.getInt("RemainingTicks"));
        entityData.set(PARENT_ACTION, tag.contains("ParentAction") ? tag.getInt("ParentAction") : -1);
        actionSequence = tag.contains("ActionSequence") ? tag.getLong("ActionSequence") : -1L;
        entityData.set(APPEAR_TICK, tag.contains("AppearTick") ? tag.getLong("AppearTick") : level().getGameTime());
        entityData.set(IMPACT_TICK, tag.contains("ImpactTick") ? tag.getLong("ImpactTick") : level().getGameTime() + 4);
        entityData.set(FADE_END_TICK, tag.contains("FadeEndTick") ? tag.getLong("FadeEndTick") : level().getGameTime() + remainingTicks);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<PromisedConsortCloneEntity>(
            this,
            "main",
            0,
            state -> {
                if (usesSourceRig()) return state.setAndContinue(RawAnimation.begin().thenPlayAndHold(sourcePlayback().animationClip()));
                return state.setAndContinue(RawAnimation.begin().thenLoop("animation.promised_consort.source_002000"));
            }
        ) {
            @Override
            protected double adjustTick(double tick) {
                super.adjustTick(tick);
                if (usesSourceRig()) return sourceAnimationTicks;
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
