package com.tonywww.elder_bosses.platforms.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

public final class PlatformEnchantmentLevels {
    private PlatformEnchantmentLevels() {
    }

    public static int looting(LivingEntity entity) {
        if (entity == null) {
            return 0;
        }
        //? if forge {
        return EnchantmentHelper.getItemEnchantmentLevel(
            Enchantments.MOB_LOOTING,
            entity.getMainHandItem()
        );
        //?} else {
        /*return EnchantmentHelper.getItemEnchantmentLevel(
            entity.level().registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.LOOTING),
            entity.getMainHandItem()
        );
        *///?}
    }
}
