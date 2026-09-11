package com.tonywww.elder_bosses.client.vfx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;

public final class ConsortEnergyShader {
    private static ShaderInstance shader;
    public static final RenderType ENERGY = EnergyRenderType.createEnergy();

    private ConsortEnergyShader() {
    }

    public static void install(ShaderInstance instance) {
        shader = instance;
    }

    public static boolean ready() {
        return shader != null;
    }

    public static void configure(int mode, float time, float progress, float innerRatio) {
        shader.safeGetUniform("EffectMode").set(mode);
        shader.safeGetUniform("EffectTime").set(time);
        shader.safeGetUniform("Progress").set(progress);
        shader.safeGetUniform("InnerRatio").set(innerRatio);
    }

    private static final class EnergyRenderType extends RenderType {
        private EnergyRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                                 boolean crumbling, boolean sorted, Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sorted, setup, clear);
        }

        private static RenderType createEnergy() {
            return create("elder_bosses_consort_energy", DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS, 1536, false, true,
                    CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> shader))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false));
        }
    }
}