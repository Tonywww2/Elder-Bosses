package com.tonywww.elder_bosses.boss.promisedconsort;

import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public final class PromisedConsortEntityTypeTags {
    public static final TagKey<EntityType<?>> ATTACK_IMMUNE = TagKey.create(
            Registries.ENTITY_TYPE,
            PlatformResourceLocation.id("promised_consort_attack_immune")
    );

    private PromisedConsortEntityTypeTags() {
    }
}
