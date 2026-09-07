package com.tonywww.elder_bosses.platforms.client;

import com.tonywww.elder_bosses.ElderBosses;
import com.tonywww.elder_bosses.client.state.ClientBossStateStore;
import com.tonywww.elder_bosses.client.state.ClientIndicatorStateStore;
import com.tonywww.elder_bosses.client.state.ClientRotStateStore;
//? if forge {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//?} else {
/*import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
*///?}

//? if forge {
@Mod.EventBusSubscriber(modid = ElderBosses.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
//?} else {
/*@EventBusSubscriber(modid = ElderBosses.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
*///?}
public final class ClientGameEvents {
    private ClientGameEvents() {
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }

        int entityId = event.getEntity().getId();
        ClientBossStateStore.onTrackingEnd(entityId);
        ClientIndicatorStateStore.onTrackingEnd(entityId);
        ClientRotStateStore.onTrackingEnd(entityId);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }

        ClientBossStateStore.onDimensionChanged();
        ClientIndicatorStateStore.onDimensionChanged();
        ClientRotStateStore.onDimensionChanged();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientBossStateStore.onDisconnect();
        ClientIndicatorStateStore.onDisconnect();
        ClientRotStateStore.onDisconnect();
    }
}