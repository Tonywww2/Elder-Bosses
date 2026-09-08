package com.tonywww.elder_bosses.boss.promisedconsort;

import com.tonywww.elder_bosses.combat.damage.DamageFormula;
import com.tonywww.elder_bosses.combat.damage.DamageSourceOwnership;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class PromisedConsortGravityRockEntity extends ThrowableItemProjectile {
    private UUID targetId;
    private long actionSequence;
    private int projectileIndex;
    private int remainingTicks = 60;
    private float durability = 6.0F;
    private double maximumTurnRadians = Math.toRadians(4.0);
    private DamageFormula damage = new DamageFormula(2.0, 0.35);
    private int maxHitsPerTarget = 3;
    private Vec3 arenaCenter;
    private double arenaRadius;

    public PromisedConsortGravityRockEntity(
            EntityType<? extends PromisedConsortGravityRockEntity> entityType,
            Level level
    ) {
        super(entityType, level);
        setNoGravity(true);
    }

    public void configure(
            PromisedConsortEntity owner,
            LivingEntity target,
            long actionSequence,
            int projectileIndex,
            double durability,
            int lifetimeTicks,
            double turnDegreesPerTick,
            DamageFormula damage,
                int maxHitsPerTarget,
                Vec3 arenaCenter,
                double arenaRadius
    ) {
        setOwner(owner);
        targetId = target == null ? null : target.getUUID();
        this.actionSequence = actionSequence;
        this.projectileIndex = projectileIndex;
        this.durability = (float) durability;
        remainingTicks = lifetimeTicks;
        maximumTurnRadians = Math.toRadians(turnDegreesPerTick);
        this.damage = damage;
        this.maxHitsPerTarget = maxHitsPerTarget;
        this.arenaCenter = arenaCenter;
        this.arenaRadius = arenaRadius;
    }

    @Override
    public void tick() {
        if (!level().isClientSide) {
            steerTowardTarget();
            if (!mayEnterNextPosition()) {
                discard();
                return;
            }
            if (--remainingTicks <= 0) {
                discard();
                return;
            }
        }
        super.tick();
    }

    @Override
    protected Item getDefaultItem() {
        return Items.CRYING_OBSIDIAN;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!level().isClientSide
                && getOwner() instanceof PromisedConsortEntity owner
                && result.getEntity() instanceof LivingEntity target) {
            owner.resolveGravityRockHit(
                    this,
                    target,
                    actionSequence,
                    projectileIndex,
                    damage,
                    maxHitsPerTarget
            );
        }
        discard();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (result.getType() == HitResult.Type.BLOCK) {
            discard();
        }
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || amount <= 0.0F
                || !(getOwner() instanceof PromisedConsortEntity owner)) {
            return false;
        }
        if (DamageSourceOwnership.playerOwner(source)
                .filter(owner::isEligibleArenaPlayer)
                .isEmpty()) {
            return false;
        }
        durability -= amount;
        if (durability <= 0.0F) {
            level().broadcastEntityEvent(this, (byte) 3);
            discard();
        }
        return true;
    }

    @Override
    public void handleEntityEvent(byte eventId) {
        if (eventId == 3) {
            for (int index = 0; index < 8; index++) {
                level().addParticle(
                        net.minecraft.core.particles.ParticleTypes.PORTAL,
                        getX(), getY(), getZ(),
                        (random.nextDouble() - 0.5) * 0.2,
                        (random.nextDouble() - 0.5) * 0.2,
                        (random.nextDouble() - 0.5) * 0.2
                );
            }
            return;
        }
        super.handleEntityEvent(eventId);
    }

    private void steerTowardTarget() {
        if (!(level() instanceof ServerLevel serverLevel) || targetId == null) {
            return;
        }
        Entity target = serverLevel.getEntity(targetId);
        if (!(target instanceof LivingEntity living) || !living.isAlive()) {
            return;
        }
        Vec3 current = getDeltaMovement();
        double speed = current.length();
        if (speed < 1.0E-4) {
            current = living.getEyePosition().subtract(position()).normalize().scale(0.55);
            setDeltaMovement(current);
            return;
        }
        Vec3 desired = living.getEyePosition().subtract(position()).normalize();
        Vec3 currentDirection = current.normalize();
        double dot = Math.max(-1.0, Math.min(1.0, currentDirection.dot(desired)));
        double angle = Math.acos(dot);
        double blend = angle <= maximumTurnRadians || angle == 0.0
                ? 1.0
                : maximumTurnRadians / angle;
        Vec3 steered = currentDirection.scale(1.0 - blend).add(desired.scale(blend)).normalize();
        setDeltaMovement(steered.scale(speed));
    }

    private boolean mayEnterNextPosition() {
        Vec3 next = position().add(getDeltaMovement());
        if (!level().hasChunkAt(BlockPos.containing(next))) {
            return false;
        }
        if (arenaCenter == null || arenaRadius <= 0.0) {
            return true;
        }
        double x = next.x - arenaCenter.x;
        double z = next.z - arenaCenter.z;
        return x * x + z * z <= arenaRadius * arenaRadius;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (targetId != null) {
            tag.putUUID("Target", targetId);
        }
        tag.putLong("ActionSequence", actionSequence);
        tag.putInt("ProjectileIndex", projectileIndex);
        tag.putInt("RemainingTicks", remainingTicks);
        tag.putFloat("Durability", durability);
        tag.putDouble("MaximumTurnRadians", maximumTurnRadians);
        tag.putDouble("DamageFlat", damage.flat());
        tag.putDouble("DamageRatio", damage.attackRatio());
        tag.putInt("MaxHitsPerTarget", maxHitsPerTarget);
        if (arenaCenter != null) {
            tag.putDouble("ArenaCenterX", arenaCenter.x);
            tag.putDouble("ArenaCenterY", arenaCenter.y);
            tag.putDouble("ArenaCenterZ", arenaCenter.z);
        }
        tag.putDouble("ArenaRadius", arenaRadius);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        targetId = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
        actionSequence = Math.max(0L, tag.getLong("ActionSequence"));
        projectileIndex = Math.max(0, tag.getInt("ProjectileIndex"));
        remainingTicks = Math.max(1, tag.getInt("RemainingTicks"));
        durability = Math.max(0.0F, tag.getFloat("Durability"));
        maximumTurnRadians = Math.max(0.0, tag.getDouble("MaximumTurnRadians"));
        damage = new DamageFormula(
                Math.max(0.0, tag.getDouble("DamageFlat")),
                Math.max(0.0, tag.getDouble("DamageRatio"))
        );
        maxHitsPerTarget = Math.max(1, tag.getInt("MaxHitsPerTarget"));
        if (tag.contains("ArenaCenterX")) {
            arenaCenter = new Vec3(
                    tag.getDouble("ArenaCenterX"),
                    tag.getDouble("ArenaCenterY"),
                    tag.getDouble("ArenaCenterZ")
            );
        }
        arenaRadius = Math.max(0.0, tag.getDouble("ArenaRadius"));
    }
}
