package com.tonywww.elder_bosses.platforms;

import com.tonywww.elder_bosses.ElderBosses;
import net.minecraft.resources.ResourceLocation;

public final class PlatformResourceLocation {
    private PlatformResourceLocation() {
    }

    public static ResourceLocation id(String path) {
        //? if forge {
        return new ResourceLocation(ElderBosses.MOD_ID, path);
        //?} else {
        /*return ResourceLocation.fromNamespaceAndPath(ElderBosses.MOD_ID, path);
        *///?}
    }

    public static ResourceLocation parse(String location) {
        //? if forge {
        return new ResourceLocation(location);
        //?} else {
        /*return ResourceLocation.parse(location);
        *///?}
    }

    public static ResourceLocation minecraft(String path) {
        //? if forge {
        return new ResourceLocation(path);
        //?} else {
        /*return ResourceLocation.withDefaultNamespace(path);
        *///?}
    }
}