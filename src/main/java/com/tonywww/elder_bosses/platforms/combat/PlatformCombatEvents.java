package com.tonywww.elder_bosses.platforms.combat;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.combat.hit.ShieldBlockProbe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
//? if forge {
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
//?} else {
/*import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
*///?}

public final class PlatformCombatEvents {
    private PlatformCombatEvents() {
    }

    public static void register() {
        //? if forge {
        MinecraftForge.EVENT_BUS.register(PlatformCombatEvents.class);
        //?} else {
        /*NeoForge.EVENT_BUS.register(PlatformCombatEvents.class);
        *///?}
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            notifyParticipantExit(player, PromisedConsortEntity.ParticipantExit.DEATH);
        }
    }

    @SubscribeEvent
    public static void onProjectileLoaded(EntityJoinLevelEvent event) {
        if(!event.getLevel().isClientSide()) com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedDefense.trackReflected(event.getEntity());
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if(!event.getProjectile().level().isClientSide()
                && event.getProjectile() instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow
                && event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit
                && hit.getEntity() instanceof PromisedConsortEntity boss && boss.reflectIncomingArrow(arrow)) {
            event.setCanceled(true);
        }
    }

    //? if forge {
    @SubscribeEvent
    public static void onReflectedProjectileTick(TickEvent.LevelTickEvent event) {
        if(event.phase==TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedDefense.expireReflected(level);
        }
    }
    //?} else {
    /*@SubscribeEvent
    public static void onReflectedProjectileTick(LevelTickEvent.Post event) {
        if(event.getLevel() instanceof ServerLevel level) {
            com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedDefense.expireReflected(level);
        }
    }
    *///?}

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            notifyParticipantExit(player, PromisedConsortEntity.ParticipantExit.DISCONNECT);
        }
    }

    private static void notifyParticipantExit(
            ServerPlayer player,
            PromisedConsortEntity.ParticipantExit reason
    ) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        for (PromisedConsortEntity boss : level.getEntitiesOfClass(
                PromisedConsortEntity.class,
                player.getBoundingBox().inflate(256.0)
        )) {
            boss.recordParticipantExit(player.getUUID(), reason);
        }
    }

    //? if forge {
    @SubscribeEvent
    public static void onShieldBlock(ShieldBlockEvent event) {
        ShieldBlockProbe.Policy policy = ShieldBlockProbe.record(
                event.getEntity(),
                event.getDamageSource(),
            event.getOriginalBlockedDamage()
        );
        if (policy == ShieldBlockProbe.Policy.OVERRIDE_BLOCK) {
            event.setBlockedDamage(0.0F);
            event.setShieldTakesDamage(false);
        } else if (policy == ShieldBlockProbe.Policy.SUPPRESS_SHIELD_DAMAGE) {
            event.setShieldTakesDamage(false);
        }
        ShieldBlockProbe.recordActual(
                event.getEntity(),
                event.getDamageSource(),
                event.getBlockedDamage()
        );
    }
    //?} else {
    /*@SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        float originalBlockedDamage = event.getOriginalBlock()
            ? event.getOriginalBlockedDamage()
            : 0.0F;
        ShieldBlockProbe.Policy policy = ShieldBlockProbe.record(
            event.getEntity(),
            event.getDamageSource(),
            originalBlockedDamage
        );
        if (policy == ShieldBlockProbe.Policy.OVERRIDE_BLOCK) {
            event.setBlockedDamage(0.0F);
            event.setShieldDamage(0.0F);
        } else if (policy == ShieldBlockProbe.Policy.SUPPRESS_SHIELD_DAMAGE) {
            event.setShieldDamage(0.0F);
        }
        ShieldBlockProbe.recordActual(
            event.getEntity(),
            event.getDamageSource(),
            event.getBlockedDamage()
        );
    }
    *///?}
}