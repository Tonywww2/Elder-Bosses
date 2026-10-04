package com.tonywww.elder_bosses.platforms.client;

import com.tonywww.elder_bosses.ElderBosses;
import com.tonywww.elder_bosses.client.hud.ElderBossesHudRenderer;
import com.tonywww.elder_bosses.client.hud.ClientBossVictoryBanner;
import com.tonywww.elder_bosses.client.render.MaleniaRenderer;
import com.tonywww.elder_bosses.client.render.PromisedConsortCloneRenderer;
import com.tonywww.elder_bosses.client.render.PromisedConsortRenderer;
import com.tonywww.elder_bosses.client.vfx.ConsortEnergyShader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import com.tonywww.elder_bosses.platforms.network.ClientNetworkHandlers;
import com.tonywww.elder_bosses.platforms.registry.ModEntities;
import com.tonywww.elder_bosses.platforms.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
//? if forge {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
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
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
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
    public static void registerArenaColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> 0x718D8F, ModBlocks.HALIGTREE_SHALLOW_WATER.get());
    }

    @SubscribeEvent
    public static void registerArenaItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> 0x718D8F,
                com.tonywww.elder_bosses.platforms.registry.ModItems.HALIGTREE_SHALLOW_WATER.get());
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws java.io.IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                PlatformResourceLocation.id("malenia_parry_cue"), DefaultVertexFormat.POSITION_TEX_COLOR),
                com.tonywww.elder_bosses.client.vfx.MaleniaParryCueShader::install);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                PlatformResourceLocation.id("malenia_wings"), DefaultVertexFormat.POSITION_TEX_COLOR),
                com.tonywww.elder_bosses.client.vfx.MaleniaWingShader::install);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                PlatformResourceLocation.id("malenia_effect"), DefaultVertexFormat.POSITION_TEX_COLOR),
                com.tonywww.elder_bosses.client.vfx.MaleniaEffectShader::install);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                PlatformResourceLocation.id("consort_energy"), DefaultVertexFormat.POSITION_TEX_COLOR),
                ConsortEnergyShader::install);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),PlatformResourceLocation.id("consort_source"),DefaultVertexFormat.POSITION_TEX_COLOR),
                com.tonywww.elder_bosses.client.vfx.ConsortSourceShader::installNormal);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),PlatformResourceLocation.id("consort_source_add"),DefaultVertexFormat.POSITION_TEX_COLOR),
                com.tonywww.elder_bosses.client.vfx.ConsortSourceShader::installAdd);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),PlatformResourceLocation.id("consort_source_mask"),DefaultVertexFormat.POSITION_TEX_COLOR),
                com.tonywww.elder_bosses.client.vfx.ConsortSourceShader::installMask);
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        PlatformResourceLocation.id("consort_gravity"), DefaultVertexFormat.POSITION_TEX_COLOR),
                        com.tonywww.elder_bosses.client.vfx.ClientConsortGravityDistortion::install);
            } catch (java.io.IOException exception) {
                com.tonywww.elder_bosses.client.vfx.ClientConsortGravityDistortion.install(null);
                com.mojang.logging.LogUtils.getLogger().warn("Consort gravity distortion unavailable; retaining energy effects", exception);
            }
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
            context -> new ThrownItemRenderer<>(context,
                com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortGravityRockEntity.SIZE_SCALE, false)
        );
        event.registerEntityRenderer(
            ModEntities.PROMISED_CONSORT_CLONE.get(),
            PromisedConsortCloneRenderer::new
        );
    }

    //? if forge {
    @SubscribeEvent
    public static void registerHud(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("boss_victory", (gui, graphics, partialTick, width, height) ->
                ClientBossVictoryBanner.render(graphics, width, height, partialTick));
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
        event.registerAboveAll(PlatformResourceLocation.id("boss_victory"), (graphics, deltaTracker) ->
                ClientBossVictoryBanner.render(graphics, graphics.guiWidth(), graphics.guiHeight(),
                        deltaTracker.getGameTimeDeltaPartialTick(false)));
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
