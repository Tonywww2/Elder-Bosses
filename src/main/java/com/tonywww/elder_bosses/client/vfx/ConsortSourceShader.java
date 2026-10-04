package com.tonywww.elder_bosses.client.vfx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

/** Original diffuse textures and source normal/add blending; no procedural repaint. */
public final class ConsortSourceShader {
    private static ShaderInstance normal,add,mask;
    private static final Map<Key,RenderType> TYPES=new HashMap<>();
    public static final RenderType SWORD_RIBBON=SourceType.ribbon();
    private record Key(ResourceLocation texture,boolean additive,boolean mask) {}
    private ConsortSourceShader() {}
    public static void installNormal(ShaderInstance shader) {normal=shader;TYPES.clear();}
    public static void installAdd(ShaderInstance shader) {add=shader;TYPES.clear();}
    public static void installMask(ShaderInstance shader) {mask=shader;TYPES.clear();}
    public static boolean ready() {return normal!=null && add!=null;}
    public static RenderType type(ResourceLocation texture,int blend) {
        return TYPES.computeIfAbsent(new Key(texture,blend==0 || blend==4 || blend==7,false),SourceType::create);
    }
    public static RenderType masked(ResourceLocation texture) {return TYPES.computeIfAbsent(new Key(texture,false,true),SourceType::create);}
    private static final class SourceType extends RenderType {
        private SourceType() {super("unused",DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,1536,false,true,()->{},()->{});}
        private static RenderType create(Key key) {
            return create("elder_bosses_original_fxr",DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,1536,false,true,
                    CompositeState.builder().setShaderState(new ShaderStateShard(()->key.mask()?mask:key.additive()?add:normal))
                            .setTextureState(new TextureStateShard(key.texture(),false,false))
                            .setTransparencyState(key.additive()?ADDITIVE_TRANSPARENCY:TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
        }
        private static RenderType ribbon() {
            return create("elder_bosses_sword_ribbon",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,1536,false,true,
                    CompositeState.builder().setShaderState(POSITION_COLOR_SHADER)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
        }
    }
}
