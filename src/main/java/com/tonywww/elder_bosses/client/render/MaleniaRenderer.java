package com.tonywww.elder_bosses.client.render;

import com.tonywww.elder_bosses.boss.malenia.MaleniaEntity;
import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class MaleniaRenderer extends EntityRenderer<MaleniaEntity> {
    private static final ResourceLocation PLACEHOLDER_TEXTURE =
            PlatformResourceLocation.minecraft("textures/entity/armorstand/wood.png");

    public MaleniaRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(MaleniaEntity entity) {
        return PLACEHOLDER_TEXTURE;
    }
}