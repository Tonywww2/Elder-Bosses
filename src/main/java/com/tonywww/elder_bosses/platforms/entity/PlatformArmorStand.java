package com.tonywww.elder_bosses.platforms.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;

public abstract class PlatformArmorStand extends ArmorStand {
    protected PlatformArmorStand(EntityType<? extends ArmorStand> entityType, Level level) {
        super(entityType, level);
    }

    protected abstract void definePlatformSynchedData(SynchedDataRegistrar registrar);

    //? if forge {
    @Override
    protected final void defineSynchedData() {
        super.defineSynchedData();
        definePlatformSynchedData(new SynchedDataRegistrar() {
            @Override
            public <T> void define(EntityDataAccessor<T> accessor, T initialValue) {
                entityData.define(accessor, initialValue);
            }
        });
    }
    //?} else {
    /*@Override
    protected final void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        definePlatformSynchedData(new SynchedDataRegistrar() {
            @Override
            public <T> void define(EntityDataAccessor<T> accessor, T initialValue) {
                builder.define(accessor, initialValue);
            }
        });
    }
    *///?}

    @FunctionalInterface
    protected interface SynchedDataRegistrar {
        <T> void define(EntityDataAccessor<T> accessor, T initialValue);
    }
}