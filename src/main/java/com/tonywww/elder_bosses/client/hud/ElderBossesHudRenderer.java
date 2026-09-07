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
    private static final int ROT_WIDTH = 28;
    private static final int ROT_HEIGHT = 22;

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

        int x = (screenWidth - ROT_WIDTH) / 2;
        int y = Math.max(4, screenHeight - 58);
        int filledPetals = meter.active()
                ? 6
                : (int) Math.ceil(meter.buildupRatio() * 6.0);
        boolean flash = meter.active() && gameTime % 10L < 5L;

        drawPetal(graphics, x + 4, y, filledPetals > 0, flash);
        drawPetal(graphics, x + 16, y, filledPetals > 1, flash);
        drawPetal(graphics, x, y + 7, filledPetals > 2, flash);
        drawPetal(graphics, x + 20, y + 7, filledPetals > 3, flash);
        drawPetal(graphics, x + 4, y + 14, filledPetals > 4, flash);
        drawPetal(graphics, x + 16, y + 14, filledPetals > 5, flash);
        graphics.fill(x + 11, y + 8, x + 17, y + 14, meter.active() ? ROT_CORE_COLOR : ROT_EMPTY_COLOR);

        if (meter.active()) {
            drawCountdownFrame(graphics, x, y, ROT_WIDTH, ROT_HEIGHT, meter.activeRemainingRatio());
        }
    }

    private static void drawPetal(
            GuiGraphics graphics,
            int x,
            int y,
            boolean filled,
            boolean highlighted
    ) {
        int color = filled
                ? (highlighted ? ROT_HIGHLIGHT_COLOR : ROT_FILL_COLOR)
                : ROT_EMPTY_COLOR;
        graphics.fill(x + 2, y, x + 6, y + 1, color);
        graphics.fill(x, y + 1, x + 8, y + 5, color);
        graphics.fill(x + 2, y + 5, x + 6, y + 6, color);
    }

    private static void drawCountdownFrame(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            double ratio
    ) {
        int remaining = (int) Math.round((2 * width + 2 * height - 4) * ratio);
        int top = Math.min(width, remaining);
        graphics.fill(x, y, x + top, y + 1, ROT_TIMER_COLOR);
        remaining -= top;

        int right = Math.min(height - 1, Math.max(0, remaining));
        graphics.fill(x + width - 1, y + 1, x + width, y + 1 + right, ROT_TIMER_COLOR);
        remaining -= right;

        int bottom = Math.min(width - 1, Math.max(0, remaining));
        graphics.fill(x + width - bottom - 1, y + height - 1, x + width - 1, y + height, ROT_TIMER_COLOR);
        remaining -= bottom;

        int left = Math.min(height - 2, Math.max(0, remaining));
        graphics.fill(x, y + height - left - 1, x + 1, y + height - 1, ROT_TIMER_COLOR);
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