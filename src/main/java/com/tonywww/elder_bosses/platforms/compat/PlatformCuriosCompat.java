package com.tonywww.elder_bosses.platforms.compat;

import com.mojang.logging.LogUtils;
//? if forge {
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
//?} else {
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
*///?}

/** Keep this entry point free of Curios types so the dependency remains optional. */
public final class PlatformCuriosCompat {
    private PlatformCuriosCompat() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(PlatformCuriosCompat::commonSetup);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        if (ModList.get().isLoaded("curios")) {
            event.enqueueWork(() -> {
                PlatformCurioItem.registerItems();
                LogUtils.getLogger().info("Registered Elder Bosses Curios equipment (4 items)");
            });
        }
    }
}
