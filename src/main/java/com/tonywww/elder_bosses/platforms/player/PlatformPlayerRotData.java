package com.tonywww.elder_bosses.platforms.player;

import com.tonywww.elder_bosses.combat.status.ScarletRotData;
import net.minecraft.world.entity.LivingEntity;
import java.util.Optional;
//? if forge {
import net.minecraftforge.eventbus.api.IEventBus;
//?} else {
/*import net.neoforged.bus.api.IEventBus;
*///?}

public final class PlatformPlayerRotData {
    private PlatformPlayerRotData() {
    }

    public static ScarletRotData get(LivingEntity entity) {
        return find(entity).orElseThrow(
                () -> new IllegalStateException("Living entity scarlet rot capability is missing")
        );
    }

    public static Optional<ScarletRotData> find(LivingEntity entity) {
        //? if forge {
        return entity.getCapability(ForgePlayerRotCapability.CAPABILITY).resolve();
        //?} else {
        /*return Optional.of(entity.getData(NeoForgePlayerRotAttachments.SCARLET_ROT.get()));
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