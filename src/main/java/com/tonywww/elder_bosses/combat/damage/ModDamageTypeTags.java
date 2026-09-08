package com.tonywww.elder_bosses.combat.damage;

import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;

public final class ModDamageTypeTags {
    public static final TagKey<DamageType> FORCED_DEATH = create("forced_death");
    public static final TagKey<DamageType> BYPASSES_BOSS_SCALING = create("bypasses_boss_scaling");
    public static final TagKey<DamageType> MAGIC = create("magic");
    public static final TagKey<DamageType> FIRE = create("fire");
    public static final TagKey<DamageType> LIGHTNING = create("lightning");
    public static final TagKey<DamageType> HOLY = create("holy");
    public static final TagKey<DamageType> PIERCE = create("pierce");
    public static final TagKey<DamageType> BLEED_TRIGGER = create("bleed_trigger");
    public static final TagKey<DamageType> FROST_TRIGGER = create("frost_trigger");
    public static final TagKey<DamageType> POISON = create("poison");
    public static final TagKey<DamageType> WITHER = create("wither");
    public static final TagKey<DamageType> SLEEP = create("sleep");
        public static final TagKey<DamageType> PROMISED_CONSORT_IMMUNE =
            create("promised_consort_immune");

    private ModDamageTypeTags() {
    }

    private static TagKey<DamageType> create(String path) {
        return TagKey.create(Registries.DAMAGE_TYPE, PlatformResourceLocation.id(path));
    }
}