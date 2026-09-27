package com.tonywww.elder_bosses.compat.jei;

import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import com.tonywww.elder_bosses.platforms.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Discovered by JEI only; common mod initialization must not load this class. */
@JeiPlugin
public final class ElderBossesJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return PlatformResourceLocation.id("jei_plugin");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addItemStackInfo(new ItemStack(ModItems.CONSORT_ALTAR.get()),
                Component.translatable("jei.elder_bosses.info.consort_altar"));
        registration.addItemStackInfo(new ItemStack(ModItems.RUNE_FRAGMENT.get()),
                Component.translatable("jei.elder_bosses.info.rune_fragment"));
    }
}
