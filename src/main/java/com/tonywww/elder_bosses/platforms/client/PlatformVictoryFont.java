package com.tonywww.elder_bosses.platforms.client;

/** Metrics for the bundled 24px Cinzel font, centered on its uppercase ink bounds. */
public final class PlatformVictoryFont {
    // 1.20's STB uses ascent - descent (1348 units); 1.21's FreeType uses the 1000-unit em.
    // Their glyph origins also differ. Keep the visible cap height and center consistent.
    //? if forge {
    public static final float EM_SCALE = 1.348F;
    public static final int CENTERED_Y = -8;
    //?} else {
    /*public static final float EM_SCALE = 1.0F;
    public static final int CENTERED_Y = 2;
    *///?}

    private PlatformVictoryFont() { }
}
