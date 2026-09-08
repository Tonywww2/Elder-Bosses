package com.tonywww.elder_bosses.platforms.client;

import com.tonywww.elder_bosses.ElderBosses;
import com.tonywww.elder_bosses.client.hud.ElderBossesHudRenderer;
import com.tonywww.elder_bosses.client.render.MaleniaRenderer;
import com.tonywww.elder_bosses.client.render.PromisedConsortCloneRenderer;
import com.tonywww.elder_bosses.client.render.PromisedConsortRenderer;
import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import com.tonywww.elder_bosses.platforms.network.ClientNetworkHandlers;
import com.tonywww.elder_bosses.platforms.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
//? if forge {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
//?} else {
/*import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
*///?}

//? if forge {
@Mod.EventBusSubscriber(modid = ElderBosses.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
//?} else {
/*@EventBusSubscriber(modid = ElderBosses.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
*///?}
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        ClientNetworkHandlers.install();
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.MALENIA.get(), MaleniaRenderer::new);
        event.registerEntityRenderer(
                ModEntities.PROMISED_CONSORT.get(),
                PromisedConsortRenderer::new
        );
        event.registerEntityRenderer(
            ModEntities.PROMISED_CONSORT_GRAVITY_ROCK.get(),
            context -> new ThrownItemRenderer<>(context)
        );
        event.registerEntityRenderer(
            ModEntities.PROMISED_CONSORT_CLONE.get(),
            PromisedConsortCloneRenderer::new
        );
    }

    //? if forge {
    @SubscribeEvent
    public static void registerHud(RegisterGuiOverlaysEvent event) {
        event.registerAbove(
                VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id(),
                "elder_bosses_hud",
                (gui, graphics, partialTick, screenWidth, screenHeight) -> ElderBossesHudRenderer.render(
                        graphics,
                        screenWidth,
                        screenHeight,
                        gameTime()
                )
        );
    }
    //?} else {
    /*@SubscribeEvent
    public static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.BOSS_OVERLAY,
                PlatformResourceLocation.id("elder_bosses_hud"),
                (graphics, deltaTracker) -> ElderBossesHudRenderer.render(
                        graphics,
                        graphics.guiWidth(),
                        graphics.guiHeight(),
                        gameTime()
                )
        );
    }
    *///?}

    private static long gameTime() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }
}