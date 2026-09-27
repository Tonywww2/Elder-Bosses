package com.tonywww.elder_bosses.client.vfx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;

public final class MaleniaEffectShader {
    private static ShaderInstance shader;
    public static final RenderType EFFECT = EffectRenderType.createEffect();
    private MaleniaEffectShader() {}
    public static void install(ShaderInstance instance) { shader = instance; }
    public static boolean ready() { return shader != null; }
    public static void mode(int mode) { shader.safeGetUniform("EffectMode").set(mode); }

    private static final class EffectRenderType extends RenderType {
        private EffectRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                                 boolean crumbling, boolean sorted, Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sorted, setup, clear);
        }
        private static RenderType createEffect() {
            return create("elder_bosses_malenia_effect", DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> shader))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
        }
    }
}
