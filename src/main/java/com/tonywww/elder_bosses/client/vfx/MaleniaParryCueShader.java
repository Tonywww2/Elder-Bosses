package com.tonywww.elder_bosses.client.vfx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;

public final class MaleniaParryCueShader {
    private static ShaderInstance shader;
    public static final RenderType CUE = CueRenderType.createCue();
    private MaleniaParryCueShader() {}
    public static void install(ShaderInstance instance) { shader = instance; }
    public static boolean ready() { return shader != null; }
    public static void configure(float time, float progress, int pulses, int color) {
        shader.safeGetUniform("Success").set(0F);
        shader.safeGetUniform("EffectTime").set(time);
        shader.safeGetUniform("Progress").set(progress);
        shader.safeGetUniform("PulseCount").set((float) pulses);
        shader.safeGetUniform("CueColor").set((color >> 16 & 255) / 255F,
                (color >> 8 & 255) / 255F, (color & 255) / 255F);
    }

    public static void configureSuccess(float progress) {
        shader.safeGetUniform("Success").set(1F);
        shader.safeGetUniform("Progress").set(progress);
    }

    private static final class CueRenderType extends RenderType {
        private CueRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                                 boolean crumbling, boolean sorted, Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sorted, setup, clear);
        }
        private static RenderType createCue() {
            return create("elder_bosses_malenia_parry_cue", DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> shader))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(NO_DEPTH_TEST)
                            .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
        }
    }
}
