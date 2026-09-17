package com.tonywww.elder_bosses.platforms.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.level.Level;

public abstract class PlatformGravityRockProjectile extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Boolean> HELD = SynchedEntityData.defineId(PlatformGravityRockProjectile.class, EntityDataSerializers.BOOLEAN);

    protected PlatformGravityRockProjectile(EntityType<? extends ThrowableItemProjectile> type, Level level) {
        super(type, level);
    }

    public final boolean isHeld() { return entityData.get(HELD); }
    protected final void setHeld(boolean held) { entityData.set(HELD, held); }

    //? if forge {
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(HELD, false);
    }
    //?} else {
    /*@Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(HELD, false);
    }
    *///?}
}