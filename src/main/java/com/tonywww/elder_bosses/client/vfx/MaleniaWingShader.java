package com.tonywww.elder_bosses.client.vfx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

/** A dedicated material: rot wings are part of the character, independent of skill VFX toggles. */
public final class MaleniaWingShader {
    private static ShaderInstance shader;
    public static final RenderType WINGS = WingRenderType.createWings();
    private MaleniaWingShader() {}
    public static void install(ShaderInstance instance) { shader = instance; }
    public static boolean ready() { return shader != null; }

    public static void configure(Matrix4f transform, float time, float spread, float activity) {
        shader.safeGetUniform("WingMatrix").set(transform);
        shader.safeGetUniform("EffectTime").set(time);
        shader.safeGetUniform("Spread").set(spread);
        shader.safeGetUniform("Activity").set(activity);
    }

    private static final class WingRenderType extends RenderType {
        private WingRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                               boolean crumbling, boolean sorted, Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sorted, setup, clear);
        }
        private static RenderType createWings() {
            return create("elder_bosses_malenia_rot_wings", DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS, 262144, false, false, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> shader))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE).createCompositeState(false));
        }
    }
}
