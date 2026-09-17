package com.tonywww.elder_bosses.platforms;

import com.tonywww.elder_bosses.ElderBosses;
import com.tonywww.elder_bosses.boss.malenia.config.MaleniaConfigProvider;
import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigProvider;
import com.tonywww.elder_bosses.network.PlayerRotSnapshotPacket;
import com.tonywww.elder_bosses.platforms.arena.PlatformArenaWorldgen;
import com.tonywww.elder_bosses.platforms.arena.PlatformArenaSummoning;
import com.tonywww.elder_bosses.platforms.combat.PlatformCombatEvents;
import com.tonywww.elder_bosses.platforms.command.PlatformSkillTestCommands;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;
import com.tonywww.elder_bosses.platforms.network.PlatformNetwork;
import com.tonywww.elder_bosses.platforms.player.PlatformPlayerRotData;
import com.tonywww.elder_bosses.platforms.player.PlatformPlayerRotEvents;
import com.tonywww.elder_bosses.platforms.registry.ModAttributes;
import com.tonywww.elder_bosses.platforms.registry.ModBlocks;
import com.tonywww.elder_bosses.platforms.registry.ModCreativeTabs;
import com.tonywww.elder_bosses.platforms.registry.ModEntities;
import com.tonywww.elder_bosses.platforms.registry.ModItems;
import com.tonywww.elder_bosses.platforms.registry.ModSoundEvents;
import com.tonywww.elder_bosses.platforms.registry.ModStructures;
import com.tonywww.elder_bosses.player.PlayerRotService;
//? if forge {
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
//?} else {
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;
*///?}

@Mod(ElderBosses.MOD_ID)
public final class ElderBossesMod {
    //? if forge {
    public ElderBossesMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.COMMON,
                ElderBossesCommonConfig.SPEC,
                ElderBosses.COMMON_CONFIG_FILE
        );
    //?} else {
    /*public ElderBossesMod(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerConfig(
                ModConfig.Type.COMMON,
                ElderBossesCommonConfig.SPEC,
                ElderBosses.COMMON_CONFIG_FILE
        );
    *///?}
        MaleniaConfigProvider.install(ElderBossesCommonConfig.VALUES::maleniaCombatSnapshot);
        MaleniaConfigProvider.installSkills(ElderBossesCommonConfig.VALUES::maleniaSkillSnapshot);
        MaleniaConfigProvider.installDebugStateOutput(
            ElderBossesCommonConfig.VALUES::maleniaDebugStateOutput
        );
        MaleniaConfigProvider.installDebugActionBroadcast(
            ElderBossesCommonConfig.VALUES::maleniaDebugActionBroadcast
        );
        PromisedConsortConfigProvider.installCombat(
            ElderBossesCommonConfig.VALUES::promisedConsortCombatSnapshot
        );
        PromisedConsortConfigProvider.installSkills(
            ElderBossesCommonConfig.VALUES::promisedConsortSkillSnapshot
        );
        PromisedConsortConfigProvider.installDebugActionBroadcast(
            ElderBossesCommonConfig.VALUES::promisedConsortDebugActionBroadcast
        );
        PlatformNetwork.register(modBus);
        PlayerRotService.installSyncSink((player, snapshot, reason) -> PlatformNetwork.sendTo(
            player,
            new PlayerRotSnapshotPacket(player.getId(), snapshot)
        ));
        PlatformPlayerRotData.register(modBus);
        PlatformPlayerRotEvents.register();
        ModAttributes.register(modBus);
        ModEntities.register(modBus);
        ModBlocks.register(modBus);
        ModStructures.register(modBus);
        ModItems.register(modBus);
        ModCreativeTabs.register(modBus);
        ModSoundEvents.register(modBus);
        modBus.addListener(ModEntityEvents::registerAttributes);
        modBus.addListener(ModEntityEvents::addLivingEntityAttributes);
        PlatformCombatEvents.register();
        PlatformSkillTestCommands.register();
        PlatformArenaWorldgen.register();
        PlatformArenaSummoning.register();
    }
}