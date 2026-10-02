package com.tonywww.elder_bosses.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tonywww.elder_bosses.network.BossDefeatedPacket;
import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import com.tonywww.elder_bosses.platforms.client.PlatformVictoryFont;
import com.tonywww.elder_bosses.platforms.registry.ModSoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public final class ClientBossVictoryBanner {
    private static final BossVictoryBannerState STATE = new BossVictoryBannerState();
    private static final Style LETTER_STYLE = Style.EMPTY.withFont(
            PlatformResourceLocation.id("victory"));
    private static final float LETTER_SPACING = 2.0F / PlatformVictoryFont.EM_SCALE;

    private ClientBossVictoryBanner() { }

    public static void receive(BossDefeatedPacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && client.player != null
                && packet.dimension().equals(client.level.dimension().location())) {
            STATE.offer(packet.bossUuid(), packet.victory());
        }
    }

    public static void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) clear();
        else if (!client.isPaused()) {
            STATE.tick();
            if (STATE.consumeStart()) client.getSoundManager().play(SimpleSoundInstance.forUI(
                    ModSoundEvents.BOSS_VICTORY_BANNER.get(), 0.78F, 1.0F));
        }
    }

    public static void clear() {
        STATE.clear();
    }

    public static void render(GuiGraphics graphics, int width, int height, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.options.hideGui
                || width < 80 || height < 60) return;
        BossVictoryBannerState.Frame frame = STATE.frame(client.isPaused() ? 0.0F : partialTick);
        if (!frame.visible()) return;

        Font font = client.font;
        String title = Component.translatable(frame.victory().languageKey()).getString();
        Component[] letters = title.codePoints().mapToObj(codePoint ->
                Component.literal(new String(Character.toChars(codePoint))).setStyle(LETTER_STYLE)
        ).toArray(Component[]::new);
        float textWidth = -LETTER_SPACING;
        for (Component letter : letters) textWidth += font.width(letter) + LETTER_SPACING;
        if (textWidth <= 0) return;
        float scale = Math.min(height / 150.0F * PlatformVictoryFont.EM_SCALE, width * 0.82F / textWidth);
        int center = height / 2;
        int halfBand = Math.max(20, Math.round(height * 0.115F));
        float opacity = frame.opacity();

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Soft edges let the arena remain visible above and below the lettering.
        graphics.fillGradient(0, center - halfBand, width, center,
                color(0x100E0A, 0), color(0x100E0A, opacity * 0.72F));
        graphics.fillGradient(0, center, width, center + halfBand,
                color(0x100E0A, opacity * 0.72F), color(0x100E0A, 0));

        graphics.pose().translate(width / 2.0F, center, 1);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(-textWidth / 2.0F, 0, 0);
        for (Component letter : letters) {
            // Avoid alpha 0..3: Minecraft's text renderer interprets it as opaque.
            if (opacity * 0.42F >= 4.0F / 255.0F) {
                graphics.drawString(font, letter, 0, PlatformVictoryFont.CENTERED_Y + 1,
                        color(0x291B09, opacity * 0.42F), false);
            }
            if (opacity >= 4.0F / 255.0F) {
                graphics.drawString(font, letter, 0, PlatformVictoryFont.CENTERED_Y,
                        color(0xE1BC6C, opacity), false);
            }
            graphics.pose().translate(font.width(letter) + LETTER_SPACING, 0, 0);
        }
        graphics.flush();
        RenderSystem.disableBlend();
        graphics.pose().popPose();
    }

    private static int color(int rgb, float alpha) {
        return Math.round(255.0F * alpha) << 24 | rgb;
    }
}
