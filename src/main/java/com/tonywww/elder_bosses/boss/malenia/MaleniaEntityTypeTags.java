package com.tonywww.elder_bosses.boss.malenia;

import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public final class MaleniaEntityTypeTags {
    public static final TagKey<EntityType<?>> HEALING_EXCLUDED = create("malenia_healing_excluded");
    public static final TagKey<EntityType<?>> HEALING_SUMMONS = create("malenia_healing_summons");

    private MaleniaEntityTypeTags() {
    }

    private static TagKey<EntityType<?>> create(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, PlatformResourceLocation.id(path));
    }
}