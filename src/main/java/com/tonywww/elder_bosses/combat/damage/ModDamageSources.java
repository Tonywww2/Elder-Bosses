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
        public static final ResourceKey<DamageType> PROMISED_CONSORT_MAGIC = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            PlatformResourceLocation.id("promised_consort_magic")
        );
        public static final ResourceKey<DamageType> PROMISED_CONSORT_HOLY = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            PlatformResourceLocation.id("promised_consort_holy")
        );
        public static final ResourceKey<DamageType> PROMISED_CONSORT_FIRE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            PlatformResourceLocation.id("promised_consort_fire")
        );
            public static final ResourceKey<DamageType> PROMISED_CONSORT_INCOMING_PHYSICAL_ARMOR =
                promisedConsortIncomingKey("promised_consort_incoming_physical_armor");
            public static final ResourceKey<DamageType> PROMISED_CONSORT_INCOMING_PHYSICAL_BYPASS_ARMOR =
                promisedConsortIncomingKey("promised_consort_incoming_physical_bypass_armor");
            public static final ResourceKey<DamageType> PROMISED_CONSORT_INCOMING_MAGIC_ARMOR =
                promisedConsortIncomingKey("promised_consort_incoming_magic_armor");
            public static final ResourceKey<DamageType> PROMISED_CONSORT_INCOMING_MAGIC_BYPASS_ARMOR =
                promisedConsortIncomingKey("promised_consort_incoming_magic_bypass_armor");

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

    public static DamageSource promisedConsortMagic(
            RegistryAccess registryAccess,
            Entity directEntity,
            Entity causingEntity
    ) {
        return source(registryAccess, PROMISED_CONSORT_MAGIC, directEntity, causingEntity);
    }

    public static DamageSource promisedConsortHoly(
            RegistryAccess registryAccess,
            Entity directEntity,
            Entity causingEntity
    ) {
        return source(registryAccess, PROMISED_CONSORT_HOLY, directEntity, causingEntity);
    }

    public static DamageSource promisedConsortFire(
            RegistryAccess registryAccess,
            Entity directEntity,
            Entity causingEntity
    ) {
        return source(registryAccess, PROMISED_CONSORT_FIRE, directEntity, causingEntity);
    }

    public static DamageSource promisedConsortIncoming(
            RegistryAccess registryAccess,
            Entity directEntity,
            Entity causingEntity,
            boolean magic,
            boolean bypassesArmor
    ) {
        ResourceKey<DamageType> type = magic
                ? bypassesArmor
                ? PROMISED_CONSORT_INCOMING_MAGIC_BYPASS_ARMOR
                : PROMISED_CONSORT_INCOMING_MAGIC_ARMOR
                : bypassesArmor
                ? PROMISED_CONSORT_INCOMING_PHYSICAL_BYPASS_ARMOR
                : PROMISED_CONSORT_INCOMING_PHYSICAL_ARMOR;
        return source(registryAccess, type, directEntity, causingEntity);
    }

    private static ResourceKey<DamageType> promisedConsortIncomingKey(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, PlatformResourceLocation.id(path));
    }

    private static DamageSource source(
            RegistryAccess registryAccess,
            ResourceKey<DamageType> type,
            Entity directEntity,
            Entity causingEntity
    ) {
        return new DamageSource(
                registryAccess.registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type),
                directEntity,
                causingEntity
        );
    }
}