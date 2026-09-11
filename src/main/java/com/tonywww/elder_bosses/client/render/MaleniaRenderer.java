package com.tonywww.elder_bosses.client.render;

import com.tonywww.elder_bosses.boss.malenia.MaleniaEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class MaleniaRenderer extends GeoEntityRenderer<MaleniaEntity> {
    public MaleniaRenderer(EntityRendererProvider.Context context) {
        super(context, new MaleniaModel());
        shadowRadius = 0.45F;
    }
}