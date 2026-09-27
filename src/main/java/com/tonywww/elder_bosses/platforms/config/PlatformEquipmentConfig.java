package com.tonywww.elder_bosses.platforms.config;

import com.tonywww.elder_bosses.item.EquipmentSettings;
import com.tonywww.elder_bosses.network.EquipmentSettingsPacket;
import com.tonywww.elder_bosses.platforms.network.PlatformNetwork;
import net.minecraft.server.level.ServerPlayer;
//? if forge {
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.util.thread.EffectiveSide;
//?} else {
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
*///?}

public final class PlatformEquipmentConfig {
    // Item registration happens before COMMON config loading: constructors must only use defaults.
    private static volatile EquipmentSettings local = EquipmentSettings.DEFAULT;
    private static volatile EquipmentSettings remote;

    private PlatformEquipmentConfig() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(PlatformEquipmentConfig::commonSetup);
        //? if forge {
        MinecraftForge.EVENT_BUS.addListener(PlatformEquipmentConfig::onLogin);
        //?} else {
        /*NeoForge.EVENT_BUS.addListener(PlatformEquipmentConfig::onLogin);
        *///?}
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> local = ElderBossesCommonConfig.VALUES.equipment().snapshot());
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlatformNetwork.sendTo(player, new EquipmentSettingsPacket(local));
        }
    }

    public static EquipmentSettings current() {
        EquipmentSettings server = remote;
        return server != null && EffectiveSide.get().isClient() ? server : local;
    }

    public static void receive(EquipmentSettings settings) {
        remote = java.util.Objects.requireNonNull(settings);
    }

    public static void disconnect() {
        remote = null;
    }
}
