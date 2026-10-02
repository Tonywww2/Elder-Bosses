package com.tonywww.elder_bosses.boss.promisedconsort.ranged;

import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortRangedConfig;
import com.tonywww.elder_bosses.combat.damage.ModDamageTypeTags;
import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import java.util.Optional;

public final class PromisedConsortRangedDamage {
    private PromisedConsortRangedDamage() {}

    public static boolean excluded(DamageSource source, PromisedConsortRangedConfig config) {
        return source.is(ModDamageTypeTags.NON_RANGED) || source.is(ModDamageTypeTags.FORCED_DEATH)
                || config.excludedDamageTypeIds().stream().anyMatch(id -> id(source).equals(id))
                || config.excludedDamageTypeTags().stream().anyMatch(tag -> tagged(source, tag));
    }

    public static boolean ranged(DamageSource source, PromisedConsortRangedConfig config) {
        return ranged(source, config, -1, 9.0);
    }

    /** Negative squared distance denotes a source without a known player owner. */
    public static boolean ranged(DamageSource source, PromisedConsortRangedConfig config,
                                 double playerDistanceSquared, double distance) {
        if (excluded(source, config)
                || source.is(ModDamageTypeTags.POISON) || source.is(ModDamageTypeTags.WITHER)
                || source.is(ModDamageTypeTags.BLEED_TRIGGER) || source.is(ModDamageTypeTags.FROST_TRIGGER)) return false;
        return config.acceptOwnedProjectileEntity() && source.getDirectEntity() instanceof Projectile
                || config.damageTypeTags().stream().anyMatch(tag -> tagged(source, tag))
                || config.additionalDamageTypeTags().stream().anyMatch(tag -> tagged(source, tag))
                || config.additionalDamageTypeIds().contains(id(source))
                || playerDistanceSquared >= distance * distance;
    }

    /** A distant player can also deal ranged damage through beams or custom entities. */
    public static boolean ranged(DamageSource source, PromisedConsortRangedConfig config,
                                 LivingEntity boss, Optional<ServerPlayer> owner, double distance) {
        double playerDistanceSquared = owner.map(player -> {
            var box = boss.getBoundingBox();
            double dx = Math.max(box.minX - player.getX(), Math.max(0, player.getX() - box.maxX));
            double dz = Math.max(box.minZ - player.getZ(), Math.max(0, player.getZ() - box.maxZ));
            return dx * dx + dz * dz;
        }).orElse(-1.0);
        return ranged(source, config, playerDistanceSquared, distance);
    }

    private static String id(DamageSource source) {
        return source.typeHolder().unwrapKey().map(key -> key.location().toString()).orElse("");
    }

    private static boolean tagged(DamageSource source, String tag) {
        return source.is(TagKey.create(Registries.DAMAGE_TYPE, PlatformResourceLocation.parse(tag)));
    }
}
