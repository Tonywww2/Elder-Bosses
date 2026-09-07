package com.tonywww.elder_bosses.combat.damage;

import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

public final class ModDamageSources {
    public static final ResourceKey<DamageType> MALENIA_MAGIC = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            PlatformResourceLocation.id("malenia_magic")
    );
    public static final ResourceKey<DamageType> SCARLET_ROT = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            PlatformResourceLocation.id("scarlet_rot")
    );

    private ModDamageSources() {
    }

        public static DamageSource maleniaMagic(
            RegistryAccess registryAccess,
            Entity directEntity,
            Entity causingEntity
        ) {
        return new DamageSource(
            registryAccess.registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(MALENIA_MAGIC),
            directEntity,
            causingEntity
        );
        }

    public static DamageSource scarletRot(RegistryAccess registryAccess) {
        return new DamageSource(
                registryAccess.registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(SCARLET_ROT)
        );
    }

    public static DamageSource scarletRot(
            RegistryAccess registryAccess,
            Entity directEntity,
            Entity causingEntity
    ) {
        return new DamageSource(
                registryAccess.registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(SCARLET_ROT),
                directEntity,
                causingEntity
        );
    }
}