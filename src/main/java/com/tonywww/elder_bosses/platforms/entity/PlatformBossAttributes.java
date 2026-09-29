package com.tonywww.elder_bosses.platforms.entity;

import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public final class PlatformBossAttributes {
    //? if forge {
    private static final UUID HEALTH_SCALING_ID = id("boss_health_scaling");
    private static final UUID HYPER_ARMOR_ID = id("malenia_hyper_armor");
    //?} else {
    /*private static final net.minecraft.resources.ResourceLocation HEALTH_SCALING_ID =
            PlatformResourceLocation.id("boss_health_scaling");
    private static final net.minecraft.resources.ResourceLocation HYPER_ARMOR_ID =
            PlatformResourceLocation.id("malenia_hyper_armor");
    *///?}

    private PlatformBossAttributes() {
    }

    public static void setHealthScaling(LivingEntity boss, double extraHealthPerBase) {
        AttributeInstance health = Objects.requireNonNull(boss.getAttribute(Attributes.MAX_HEALTH));
        AttributeModifier existing = health.getModifier(HEALTH_SCALING_ID);
        if (existing != null && amount(existing) == extraHealthPerBase) return;
        if (existing != null) health.removeModifier(HEALTH_SCALING_ID);
        if (extraHealthPerBase == 0.0) return;
        //? if forge {
        health.addPermanentModifier(new AttributeModifier(HEALTH_SCALING_ID,
                "Elder Bosses participant health", extraHealthPerBase,
                AttributeModifier.Operation.MULTIPLY_BASE));
        //?} else {
        /*health.addPermanentModifier(new AttributeModifier(HEALTH_SCALING_ID,
                extraHealthPerBase, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        *///?}
    }

    public static void setHyperArmor(LivingEntity boss, boolean active) {
        AttributeInstance resistance = Objects.requireNonNull(boss.getAttribute(Attributes.KNOCKBACK_RESISTANCE));
        double bonus = active ? Math.max(0.0, 1.0 - resistance.getBaseValue()) : 0.0;
        AttributeModifier existing = resistance.getModifier(HYPER_ARMOR_ID);
        if (existing != null && amount(existing) == bonus) return;
        if (existing != null) resistance.removeModifier(HYPER_ARMOR_ID);
        if (bonus == 0.0) return;
        //? if forge {
        resistance.addTransientModifier(new AttributeModifier(HYPER_ARMOR_ID,
                "Elder Bosses hyper armor", bonus, AttributeModifier.Operation.ADDITION));
        //?} else {
        /*resistance.addTransientModifier(new AttributeModifier(HYPER_ARMOR_ID,
                bonus, AttributeModifier.Operation.ADD_VALUE));
        *///?}
    }

    private static double amount(AttributeModifier modifier) {
        //? if forge {
        return modifier.getAmount();
        //?} else {
        /*return modifier.amount();
        *///?}
    }

    //? if forge {
    private static UUID id(String path) {
        return UUID.nameUUIDFromBytes(PlatformResourceLocation.id(path).toString()
                .getBytes(StandardCharsets.UTF_8));
    }
    //?}
}
