package com.tonywww.elder_bosses.boss.promisedconsort;

import com.tonywww.elder_bosses.combat.damage.DamageFormula;
import com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionSoundPlan;
import com.tonywww.elder_bosses.combat.geometry.Vec2;
import com.tonywww.elder_bosses.combat.damage.DamageSourceOwnership;
import com.tonywww.elder_bosses.platforms.entity.PlatformGravityRockProjectile;
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

public final class PromisedConsortGravityRockEntity extends PlatformGravityRockProjectile {
    public static final float SIZE_SCALE = 1.35F;
    public static final float COLLISION_SIZE = 0.75F * SIZE_SCALE;
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
    private boolean impactSoundPlayed;
    private long heldStartTick;
    private long launchTick;
    private Vec3 heldOrigin;
    private int heldCount;
    private int gatherTicks = 16;
    private Item chargedItem;

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
        if (isHeld()) {
            baseTick();
            setDeltaMovement(Vec3.ZERO);
            if (level().isClientSide) return;
            PromisedConsortEntity owner = getOwner() instanceof PromisedConsortEntity boss ? boss : null;
            long now = level().getGameTime();
            HeldPhase phase = heldPhase(now, launchTick, owner != null && owner.isAlive(),
                    owner == null ? -1 : owner.activeActionSequence(), actionSequence);
            if (phase == HeldPhase.CANCEL) {
                discard();
                return;
            }
            double angle = Math.PI * 2 * projectileIndex / Math.max(1, heldCount);
                Vec3 overhead = owner.position().add(Math.cos(angle) * 2.4 * SIZE_SCALE,
                    owner.getBbHeight() + (1.0 + (projectileIndex % 2) * 0.5) * SIZE_SCALE, Math.sin(angle) * 2.4 * SIZE_SCALE);
            double progress = Math.max(0, Math.min(1, (now - heldStartTick) / (double) gatherTicks));
            Vec3 destination = heldOrigin.lerp(overhead, progress * progress * (3 - 2 * progress));
            if (!level().hasChunkAt(BlockPos.containing(destination)) || arenaCenter != null
                    && destination.subtract(arenaCenter).horizontalDistance() > arenaRadius
                    || !level().noCollision(this, getBoundingBox().move(destination.subtract(position())))) {
                discard();
                return;
            }
            setPos(destination.x, destination.y, destination.z);
            if (progress >= 0.8 && chargedItem != null) setItem(new net.minecraft.world.item.ItemStack(chargedItem));
            if (phase == HeldPhase.WAIT) return;
            setHeld(false);
            owner.playActionSound(PromisedConsortActionSoundPlan.cue("rock_launch", PromisedConsortActionSoundPlan.Sound.DASH, 0.25F, 1.15F),
                    position(), new Vec2(0, 1));
                steerTowardTarget();
                return;
        }
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

    public void prepareHeld(Vec3 origin, long start, long launch, int count, Item charged) {
        prepareHeld(origin, start, launch, count, charged, 16);
    }

    public void prepareHeld(Vec3 origin, long start, long launch, int count, Item charged, int gatherTicks) {
        if (launch <= start || count < 1 || origin == null || charged == null || !Double.isFinite(origin.x + origin.y + origin.z)) {
            throw new IllegalArgumentException("Invalid held rock timing or origin");
        }
        heldOrigin = origin;
        heldStartTick = start;
        launchTick = launch;
        heldCount = count;
        this.gatherTicks = Math.max(1, gatherTicks);
        chargedItem = charged;
        setHeld(true);
        setDeltaMovement(Vec3.ZERO);
    }

    public boolean matchesCast(PromisedConsortEntity owner, long sequence, int index) {
        return getOwner() == owner && actionSequence == sequence && projectileIndex == index;
    }

    public enum HeldPhase { WAIT, LAUNCH, CANCEL }

    public static HeldPhase heldPhase(long now, long launchTick, boolean ownerAlive, long activeSequence, long expectedSequence) {
        if (!ownerAlive || activeSequence != expectedSequence) return HeldPhase.CANCEL;
        return now < launchTick ? HeldPhase.WAIT : HeldPhase.LAUNCH;
    }

    @Override
    protected Item getDefaultItem() {
        return Items.CRYING_OBSIDIAN;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (isHeld()) return;
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
        if (isHeld()) return;
        if (result.getType() != HitResult.Type.MISS) playImpactSound(result.getLocation(), 0.55F, 0.95F);
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
            playImpactSound(position(), 0.3F, 1.25F);
            level().broadcastEntityEvent(this, (byte) 3);
            discard();
        }
        return true;
    }

    private void playImpactSound(Vec3 position, float volume, float pitch) {
        if (level().isClientSide || impactSoundPlayed || isRemoved() || !(getOwner() instanceof PromisedConsortEntity owner)) return;
        impactSoundPlayed = true;
        owner.playActionSound(PromisedConsortActionSoundPlan.cue("rock_impact", PromisedConsortActionSoundPlan.Sound.STOMP, volume, pitch),
                position, new Vec2(0, 1));
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
        tag.putBoolean("Held", isHeld());
        if (isHeld()) {
            tag.putLong("HeldAge", Math.max(0, level().getGameTime() - heldStartTick));
            tag.putLong("LaunchRemaining", Math.max(0, launchTick - level().getGameTime()));
            tag.putInt("HeldCount", heldCount);
            tag.putInt("GatherTicks", gatherTicks);
            tag.putDouble("HeldX", heldOrigin.x); tag.putDouble("HeldY", heldOrigin.y); tag.putDouble("HeldZ", heldOrigin.z);
            tag.putString("ChargedItem", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(chargedItem == null ? Items.CRYING_OBSIDIAN : chargedItem).toString());
        }
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
        if (tag.getBoolean("Held")) {
            heldStartTick = level().getGameTime() - Math.max(0, tag.getLong("HeldAge"));
            launchTick = level().getGameTime() + Math.max(0, tag.getLong("LaunchRemaining"));
            heldOrigin = new Vec3(tag.getDouble("HeldX"), tag.getDouble("HeldY"), tag.getDouble("HeldZ"));
            heldCount = Math.max(1, tag.getInt("HeldCount"));
            gatherTicks = tag.contains("GatherTicks") ? Math.max(1, tag.getInt("GatherTicks")) : 16;
            chargedItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                com.tonywww.elder_bosses.platforms.PlatformResourceLocation.parse(tag.getString("ChargedItem")));
            setHeld(true);
        }
    }
}
