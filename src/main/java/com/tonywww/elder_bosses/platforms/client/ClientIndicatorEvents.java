package com.tonywww.elder_bosses.platforms.client;

import com.tonywww.elder_bosses.ElderBosses;
import com.tonywww.elder_bosses.client.indicator.ClientIndicatorRenderer;
import com.tonywww.elder_bosses.client.state.ClientIndicatorStateStore;
import com.tonywww.elder_bosses.client.vfx.ClientBossVfxController;
import com.tonywww.elder_bosses.client.vfx.ClientConsortEnergyRenderer;
import com.tonywww.elder_bosses.client.vfx.ClientConsortBladeTrails;
import com.tonywww.elder_bosses.client.vfx.ClientConsortMeteorRenderer;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;
import net.minecraft.client.Minecraft;
//? if forge {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//?} else {
/*import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
*///?}

//? if forge {
@Mod.EventBusSubscriber(modid = ElderBosses.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
//?} else {
/*@EventBusSubscriber(modid = ElderBosses.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
*///?}
public final class ClientIndicatorEvents {
    private ClientIndicatorEvents() {
    }

    @SubscribeEvent
    public static void renderIndicators(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || minecraft.level == null) {
            return;
        }
        ClientBossVfxController.tick(minecraft);
        //? if forge {
        float partialTick = event.getPartialTick();
        //?} else {
        /*float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        *///?}
        com.tonywww.elder_bosses.client.vfx.ClientConsortGravityDistortion.render(event.getPoseStack(), event.getCamera(), minecraft.level.getGameTime(), partialTick);
        ClientConsortEnergyRenderer.render(event.getPoseStack(), event.getCamera(), minecraft.level.getGameTime(), partialTick);
        ClientConsortBladeTrails.render(event.getPoseStack(), event.getCamera(), partialTick);
        ClientConsortMeteorRenderer.render(event.getPoseStack(), event.getCamera(), partialTick);
        ElderBossesCommonConfig.IndicatorValues indicators = ElderBossesCommonConfig.VALUES.indicators();
        if (!indicators.enabled()) {
            ClientIndicatorStateStore.clear();
            return;
        }
        ClientIndicatorStateStore.setMaxActiveIndicators(
            indicators.maxActiveIndicators(),
            minecraft.level.getGameTime()
        );
        ClientIndicatorRenderer.render(
                event.getPoseStack(),
                event.getCamera(),
                minecraft.level.getGameTime(),
            partialTick,
                indicators
        );
    }
}