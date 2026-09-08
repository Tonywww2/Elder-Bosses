package com.tonywww.elder_bosses.boss.promisedconsort.damage;

import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortCombatConfigSnapshot;
import com.tonywww.elder_bosses.combat.damage.ModDamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

import java.util.Objects;

public final class PromisedConsortIncomingDamageResolver {
    private PromisedConsortIncomingDamageResolver() {
    }

    public static Resolution resolve(
            DamageSource source,
            PromisedConsortCombatConfigSnapshot config,
            boolean phaseTwo
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(config, "config");
        if (source.is(ModDamageTypeTags.FORCED_DEATH)) {
            return new Resolution(1.0, true, false, DamageChannel.NONE);
        }
        if (source.is(ModDamageTypeTags.PROMISED_CONSORT_IMMUNE)) {
            return new Resolution(0.0, false, true, DamageChannel.NONE);
        }

        PromisedConsortCombatConfigSnapshot.ResistanceProfile resistance = phaseTwo
                ? config.resistance().phaseTwo()
                : config.resistance().phaseOne();
        PromisedConsortCombatConfigSnapshot.SourceMultiplierProfile sourceMultiplier = phaseTwo
                ? config.sourceMultiplier().phaseTwo()
                : config.sourceMultiplier().phaseOne();

        double multiplier;
        DamageChannel channel = DamageChannel.NONE;
        if (source.is(ModDamageTypeTags.SLEEP)) {
            multiplier = config.status().sleepDamageMultiplier();
        } else if (source.is(ModDamageTypeTags.HOLY)) {
            channel = DamageChannel.from(config.damageRouting().holy());
            multiplier = resistance(channel, resistance) * sourceMultiplier.holy();
        } else if (source.is(ModDamageTypeTags.PIERCE)) {
            channel = DamageChannel.from(config.damageRouting().pierce());
            multiplier = resistance(channel, resistance) * sourceMultiplier.pierce();
        } else if (source.is(ModDamageTypeTags.BLEED_TRIGGER)) {
            channel = DamageChannel.from(config.damageRouting().bleedTrigger());
            multiplier = resistance(channel, resistance) * sourceMultiplier.bleedTrigger();
        } else if (source.is(ModDamageTypeTags.FROST_TRIGGER)) {
            channel = DamageChannel.from(config.damageRouting().frostTrigger());
            multiplier = resistance(channel, resistance) * sourceMultiplier.frostTrigger();
        } else if (source.is(ModDamageTypeTags.POISON)) {
            multiplier = config.status().poisonDamageMultiplier();
        } else if (source.is(ModDamageTypeTags.WITHER)) {
            multiplier = config.status().witherDamageMultiplier();
        } else if (source.is(ModDamageTypeTags.FIRE)) {
            multiplier = resistance.fire();
        } else if (source.is(ModDamageTypeTags.LIGHTNING)) {
            multiplier = resistance.lightning();
        } else if (source.is(ModDamageTypeTags.MAGIC)) {
            channel = DamageChannel.from(config.damageRouting().magic());
            multiplier = resistance(channel, resistance) * sourceMultiplier.magic();
        } else {
            channel = DamageChannel.from(config.damageRouting().ordinaryPhysical());
            multiplier = resistance(channel, resistance) * sourceMultiplier.ordinaryPhysical();
        }
        return new Resolution(multiplier, false, multiplier == 0.0, channel);
    }

    private static double resistance(
            DamageChannel channel,
            PromisedConsortCombatConfigSnapshot.ResistanceProfile resistance
    ) {
        return channel == DamageChannel.MAGIC ? resistance.magic() : resistance.physical();
    }

    public record Resolution(
            double multiplier,
            boolean forcedDeath,
            boolean immune,
            DamageChannel channel
    ) {
        public Resolution {
            if (!Double.isFinite(multiplier) || multiplier < 0.0) {
                throw new IllegalArgumentException("multiplier must be finite and non-negative");
            }
            Objects.requireNonNull(channel, "channel");
        }
    }

    public enum DamageChannel {
        NONE,
        PHYSICAL,
        MAGIC;

        private static DamageChannel from(String value) {
            return "magic".equals(value) ? MAGIC : PHYSICAL;
        }
    }
}
