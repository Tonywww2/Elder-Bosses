package com.tonywww.elder_bosses.client.hud;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class ElderBossesHudRenderer {
    private static final int ROT_EMPTY_COLOR = 0xB0433626;
    private static final int ROT_FILL_COLOR = 0xFF9D2D2C;
    private static final int ROT_HIGHLIGHT_COLOR = 0xFFE16343;
    private static final int ROT_CORE_COLOR = 0xFF1A1517;
    private static final int ROT_TIMER_COLOR = 0xFFD2B978;
    private static final int STAGGER_BACKGROUND_COLOR = 0xC0433626;
    private static final int STAGGER_FILL_COLOR = 0xFFD2B978;
    private static final int STAGGER_TICK_COLOR = 0xFF9A7B49;
    private static final int SUBTITLE_BACKGROUND_COLOR = 0xA01A1517;
    private static final int SUBTITLE_TEXT_COLOR = 0xD9D0B5;
    private static final int BOSS_BAR_WIDTH = 182;
    private static final int SUBTITLE_MAX_WIDTH = 320;
    private static final int ROT_WIDTH = 182;
    private static final int ROT_HEIGHT = 20;

    private ElderBossesHudRenderer() {
    }

    public static void render(
            GuiGraphics graphics,
            int screenWidth,
            int screenHeight,
            long gameTime
    ) {
        if (screenWidth <= 0 || screenHeight <= 0) {
            return;
        }

        ElderBossesHudModel model = ElderBossesHudModel.capture(gameTime);
        renderStagger(graphics, screenWidth, model.stagger());
        renderSubtitle(graphics, screenWidth, screenHeight, model.subtitle());
        renderRot(graphics, screenWidth, screenHeight, gameTime, model.rot());
    }

    private static void renderStagger(
            GuiGraphics graphics,
            int screenWidth,
            ElderBossesHudModel.StaggerMeter meter
    ) {
        int width = Math.min(BOSS_BAR_WIDTH, screenWidth - 24);
        if (!meter.visible() || width < 40) {
            return;
        }

        int x = (screenWidth - width) / 2;
        int y = 19;
        graphics.fill(x, y, x + width, y + 3, STAGGER_BACKGROUND_COLOR);
        int fillWidth = (int) Math.round((width - 2) * meter.ratio());
        if (fillWidth > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + fillWidth, y + 2, STAGGER_FILL_COLOR);
        }
        for (int index = 1; index < 4; index++) {
            int tickX = x + width * index / 4;
            graphics.fill(tickX, y + 1, tickX + 1, y + 2, STAGGER_TICK_COLOR);
        }
    }

    private static void renderRot(
            GuiGraphics graphics,
            int screenWidth,
            int screenHeight,
            long gameTime,
            ElderBossesHudModel.RotMeter meter
    ) {
        if (!meter.visible() || screenWidth < ROT_WIDTH + 8 || screenHeight < ROT_HEIGHT + 8) {
            return;
        }

        int width = Math.min(182, screenWidth - 24);
        int x = (screenWidth - width) / 2, y = Math.max(14, screenHeight - 76);
        double ratio = meter.active() ? meter.activeRemainingRatio() : meter.buildupRatio();
        int fill = (int) Math.round((width - 4) * ratio);
        graphics.fill(x - 1, y - 1, x + width + 1, y + 9, 0xE0100B10);
        graphics.fill(x, y, x + width, y + 8, 0xFFB09272);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 7, 0xF0301820);
        if (fill > 0) {
            int color = meter.active() ? 0xFFE65032 : ROT_FILL_COLOR;
            graphics.fill(x + 2, y + 2, x + 2 + fill, y + 6, color);
            graphics.fill(x + 2, y + 2, x + 2 + fill, y + 3, 0xFFFFAD63);
            graphics.fill(x + Math.max(2, fill), y + 1, x + 2 + fill, y + 7, 0xFFFFD5A6);
        }
        graphics.drawString(Minecraft.getInstance().font,
                Component.translatable(meter.active() ? "hud.elder_bosses.rot_active" : "hud.elder_bosses.rot_buildup"),
                x, y - 11, meter.active() ? 0xFFFF9472 : 0xFFE9C7B0, true);
    }

    private static void renderSubtitle(
            GuiGraphics graphics,
            int screenWidth,
            int screenHeight,
            ElderBossesHudModel.Subtitle subtitle
    ) {
        int maxWidth = Math.min(SUBTITLE_MAX_WIDTH, screenWidth - 32);
        if (!subtitle.visible() || maxWidth < 40 || screenHeight < 80) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        Component text = Component.translatable(subtitle.speakerLanguageKey())
                .append(Component.literal(": "))
                .append(Component.translatable(subtitle.textLanguageKey()));
        List<FormattedCharSequence> lines = font.split(text, maxWidth - 12);
        int lineHeight = font.lineHeight + 2;
        int totalHeight = lines.size() * lineHeight + 8;
        int baseY = Math.min(screenHeight - 86, Math.max(30, screenHeight * 7 / 10));
        int y = baseY - totalHeight / 2;
        int alpha = (int) Math.round(255.0 * subtitle.opacity());
        int background = alpha << 24 | SUBTITLE_BACKGROUND_COLOR & 0x00FFFFFF;
        int color = alpha << 24 | SUBTITLE_TEXT_COLOR;

        int widestLine = 0;
        for (FormattedCharSequence line : lines) {
            widestLine = Math.max(widestLine, font.width(line));
        }
        int left = (screenWidth - widestLine) / 2 - 6;
        graphics.fill(left, y - 4, left + widestLine + 12, y + totalHeight - 4, background);

        for (FormattedCharSequence line : lines) {
            int x = (screenWidth - font.width(line)) / 2;
            graphics.drawString(font, line, x, y, color, true);
            y += lineHeight;
        }
    }
}