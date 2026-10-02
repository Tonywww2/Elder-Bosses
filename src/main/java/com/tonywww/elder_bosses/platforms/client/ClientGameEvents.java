package com.tonywww.elder_bosses.platforms.client;

import com.tonywww.elder_bosses.ElderBosses;
import com.tonywww.elder_bosses.client.state.ClientBossStateStore;
import com.tonywww.elder_bosses.client.state.ClientIndicatorStateStore;
import com.tonywww.elder_bosses.client.state.ClientRotStateStore;
import com.tonywww.elder_bosses.client.vfx.ClientBossVfxController;
import com.tonywww.elder_bosses.client.audio.ClientBossMusic;
import com.tonywww.elder_bosses.client.hud.ClientBossVictoryBanner;
//? if forge {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//?} else {
/*import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
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
    //? if forge {
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientBossMusic.tick();
            ClientBossVictoryBanner.tick();
            com.tonywww.elder_bosses.client.render.ConsortGrabPlayerRenderer.lockLocalInput();
        com.tonywww.elder_bosses.client.vfx.ClientMaleniaSkillEffects.tick();
        com.tonywww.elder_bosses.client.vfx.ClientConsortSourceEffects.tick();
        }
    }
    //?} else {
    /*public static void onClientTick(ClientTickEvent.Post event) {
        ClientBossMusic.tick();
        ClientBossVictoryBanner.tick();
        com.tonywww.elder_bosses.client.render.ConsortGrabPlayerRenderer.lockLocalInput();
        com.tonywww.elder_bosses.client.vfx.ClientMaleniaSkillEffects.tick();
        com.tonywww.elder_bosses.client.vfx.ClientConsortSourceEffects.tick();
    }
    *///?}

    @SubscribeEvent
    public static void onGrabPlayerRender(RenderPlayerEvent.Pre event) {
        if(event.getEntity() instanceof net.minecraft.client.player.AbstractClientPlayer player
                && com.tonywww.elder_bosses.client.render.ConsortGrabPlayerRenderer.render(player,event.getPartialTick(),
                event.getPoseStack(),event.getMultiBufferSource(),event.getPackedLight())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onGrabHands(RenderHandEvent event) {
        var player=net.minecraft.client.Minecraft.getInstance().player;
        if(player!=null && com.tonywww.elder_bosses.client.render.ConsortGrabPlayerRenderer.owner(player)!=null) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onGrabInput(InputEvent.InteractionKeyMappingTriggered event) {
        var player=net.minecraft.client.Minecraft.getInstance().player;
        if(player!=null && com.tonywww.elder_bosses.client.render.ConsortGrabPlayerRenderer.owner(player)!=null) {
            event.setCanceled(true);event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (ClientBossMusic.suppress(event.getSound())) event.setSound(null);
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }

        int entityId = event.getEntity().getId();
        ClientBossMusic.onTrackingEnd(entityId);
        ClientBossStateStore.onTrackingEnd(entityId);
        ClientIndicatorStateStore.onTrackingEnd(entityId);
        ClientRotStateStore.onTrackingEnd(entityId);
        ClientBossVfxController.onTrackingEnd(entityId);
        com.tonywww.elder_bosses.client.vfx.ClientMaleniaSkillEffects.remove(entityId);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }

        ClientBossStateStore.onDimensionChanged();
        ClientBossVictoryBanner.clear();
        ClientBossMusic.clear();
        ClientIndicatorStateStore.onDimensionChanged();
        ClientRotStateStore.onDimensionChanged();
        ClientBossVfxController.clear();
        com.tonywww.elder_bosses.client.vfx.ClientMaleniaSkillEffects.clear();
        com.tonywww.elder_bosses.client.vfx.ClientConsortGravityDistortion.clear();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        com.tonywww.elder_bosses.platforms.config.PlatformEquipmentConfig.disconnect();
        ClientBossStateStore.onDisconnect();
        ClientBossVictoryBanner.clear();
        ClientBossMusic.clear();
        ClientIndicatorStateStore.onDisconnect();
        ClientRotStateStore.onDisconnect();
        ClientBossVfxController.clear();
        com.tonywww.elder_bosses.client.vfx.ClientMaleniaSkillEffects.clear();
        com.tonywww.elder_bosses.client.vfx.ClientConsortGravityDistortion.clear();
    }
}
