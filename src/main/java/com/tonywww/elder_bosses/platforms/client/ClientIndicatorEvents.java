package com.tonywww.elder_bosses.platforms.client;

import com.tonywww.elder_bosses.ElderBosses;
import com.tonywww.elder_bosses.client.indicator.ClientIndicatorRenderer;
import com.tonywww.elder_bosses.client.state.ClientIndicatorStateStore;
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
        ElderBossesCommonConfig.IndicatorValues indicators = ElderBossesCommonConfig.VALUES.indicators();
        if (!indicators.enabled()) {
            ClientIndicatorStateStore.clear();
            return;
        }
        ClientIndicatorStateStore.setMaxActiveIndicators(
            indicators.maxActiveIndicators(),
            minecraft.level.getGameTime()
        );
        //? if forge {
        float partialTick = event.getPartialTick();
        //?} else {
        /*float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        *///?}
        ClientIndicatorRenderer.render(
                event.getPoseStack(),
                event.getCamera(),
                minecraft.level.getGameTime(),
            partialTick,
                indicators
        );
    }
}