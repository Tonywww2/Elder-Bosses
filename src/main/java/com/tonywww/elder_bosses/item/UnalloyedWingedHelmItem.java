package com.tonywww.elder_bosses.item;

import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig.MaleniaUnalloyedWingedHelmValues;
import com.tonywww.elder_bosses.platforms.registry.ModItems;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class UnalloyedWingedHelmItem extends ArmorItem implements RotBuildupMultiplierItem {
    private final double rotBuildupMultiplier;

    public UnalloyedWingedHelmItem(MaleniaUnalloyedWingedHelmValues values) {
        super(
                ModItems.unalloyedGoldMaterial(),
                ArmorItem.Type.HELMET,
                new Item.Properties().durability(values.durability())
        );
        rotBuildupMultiplier = values.rotBuildupMultiplier();
    }

    @Override
    public double rotBuildupMultiplier(ItemStack stack) {
        return rotBuildupMultiplier;
    }

}