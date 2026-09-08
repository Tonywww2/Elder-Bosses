package com.tonywww.elder_bosses.platforms.registry;

import com.tonywww.elder_bosses.ElderBosses;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
//? if forge {
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
//?} else {
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
*///?}

public final class ModCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElderBosses.MOD_ID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elder_bosses.main"))
                    .icon(() -> new ItemStack(ModItems.GOD_AND_LORD_REMEMBRANCE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.GOLDEN_NEEDLE.get());
                        output.accept(ModItems.ROT_GODDESS_REMEMBRANCE.get());
                        output.accept(ModItems.CONSECRATED_PROSTHETIC_BLADE.get());
                        output.accept(ModItems.UNALLOYED_WINGED_HELM.get());
                        output.accept(ModItems.SCARLET_AEONIA_CORE.get());
                        output.accept(ModItems.HALIGTREE_ROOT_FRAGMENT.get());
                        output.accept(ModItems.GOD_AND_LORD_REMEMBRANCE.get());
                        output.accept(ModItems.YOUNG_LION_GREATSWORD.get());
                        output.accept(ModItems.CIRCLET_OF_FADING_LIGHT.get());
                        output.accept(ModItems.GATE_FRAGMENT.get());
                    })
                    .build()
    );

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}