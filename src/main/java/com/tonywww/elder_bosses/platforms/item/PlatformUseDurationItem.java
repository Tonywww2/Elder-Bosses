package com.tonywww.elder_bosses.platforms.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public abstract class PlatformUseDurationItem extends Item {
    protected PlatformUseDurationItem(Properties properties) {
        super(properties);
    }

    protected abstract int configuredUseDuration();

    //? if forge {
    @Override
    public int getUseDuration(ItemStack stack) {
        return configuredUseDuration();
    }
    //?} else {
    /*@Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return configuredUseDuration();
    }
    *///?}
}