package com.tonywww.elder_bosses.platforms.combat;

import com.tonywww.elder_bosses.combat.hit.ShieldBlockProbe;
//? if forge {
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
//?} else {
/*import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
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