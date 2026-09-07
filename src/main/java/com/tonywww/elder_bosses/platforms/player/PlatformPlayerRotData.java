package com.tonywww.elder_bosses.platforms.player;

import com.tonywww.elder_bosses.combat.status.ScarletRotData;
import net.minecraft.world.entity.LivingEntity;
//? if forge {
import net.minecraftforge.eventbus.api.IEventBus;
//?} else {
/*import net.neoforged.bus.api.IEventBus;
*///?}

public final class PlatformPlayerRotData {
    private PlatformPlayerRotData() {
    }

    public static ScarletRotData get(LivingEntity entity) {
        //? if forge {
        return entity.getCapability(ForgePlayerRotCapability.CAPABILITY)
                .orElseThrow(() -> new IllegalStateException("Living entity scarlet rot capability is missing"));
        //?} else {
        /*return entity.getData(NeoForgePlayerRotAttachments.SCARLET_ROT.get());
        *///?}
    }

    public static void register(IEventBus modBus) {
        //? if forge {
        modBus.addListener(ForgePlayerRotCapability::registerCapabilities);
        //?} else {
        /*NeoForgePlayerRotAttachments.register(modBus);
        *///?}
    }
}