package com.tonywww.elder_bosses.combat.damage;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.Optional;

public final class DamageSourceOwnership {
    private DamageSourceOwnership() {
    }

    public static Optional<ServerPlayer> playerOwner(DamageSource source) {
        Entity causing = source.getEntity();
        if (causing instanceof ServerPlayer player) {
            return Optional.of(player);
        }
        Optional<ServerPlayer> causingOwner = playerOwner(causing);
        if (causingOwner.isPresent()) {
            return causingOwner;
        }
        return playerOwner(source.getDirectEntity());
    }

    public static Optional<ServerPlayer> playerOwner(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            return Optional.of(player);
        }
        if (entity instanceof Projectile projectile
                && projectile.getOwner() instanceof ServerPlayer player) {
            return Optional.of(player);
        }
        if (entity instanceof TamableAnimal tamable
                && tamable.getOwner() instanceof ServerPlayer player) {
            return Optional.of(player);
        }
        return Optional.empty();
    }
}
