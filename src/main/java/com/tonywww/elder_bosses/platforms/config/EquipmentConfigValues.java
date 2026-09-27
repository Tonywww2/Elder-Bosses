package com.tonywww.elder_bosses.platforms.config;

import com.tonywww.elder_bosses.item.EquipmentSettings;
import java.util.function.Supplier;
//? if forge {
import net.minecraftforge.common.ForgeConfigSpec;
//?} else {
/*import net.neoforged.neoforge.common.ModConfigSpec;
*///?}

/** The equipment subtree of the existing common config; no additional config file. */
public final class EquipmentConfigValues {
    private final WeaponValues blade;
    private final WeaponValues greatsword;
    private final Supplier<Double> needleRotCapacity;
    private final Supplier<Double> bladeAttackSpeed;
    private final Supplier<Double> greatswordAttackDamage;
    private final Supplier<Double> greatswordKnockbackResistance;
    private final Supplier<Double> circletArmor;
    private final Supplier<Double> circletLuck;

    EquipmentConfigValues(
            //? if forge {
            ForgeConfigSpec.Builder builder
            //?} else {
            /*ModConfigSpec.Builder builder
            *///?}
    ) {
        EquipmentSettings defaults = EquipmentSettings.DEFAULT;
        builder.comment("Equipment balance. Restart the game/server after editing; server values are sent to clients.")
            .push("equipment");
        builder.push("weapons");
        blade = new WeaponValues(builder, "consecrated_prosthetic_blade", defaults.blade());
        greatsword = new WeaponValues(builder, "young_lion_greatsword", defaults.greatsword());
        builder.pop();
        builder.comment("Bonuses apply only in functional Curios slots. Set a bonus to 0 to disable it.")
            .push("curios");
        builder.push("golden_needle");
        needleRotCapacity = builder.comment("Flat addition to scarlet rot capacity (base player capacity: 100).")
            .worldRestart().defineInRange("rot_capacity_bonus", defaults.needleRotCapacity(), 0.0, 1_000_000.0);
        builder.pop();
        builder.push("consecrated_prosthetic_blade");
        bladeAttackSpeed = builder.comment("Attack speed ratio: 0.10 means +10%.")
            .worldRestart().defineInRange("attack_speed_bonus", defaults.bladeAttackSpeed(), 0.0, 10.0);
        builder.pop();
        builder.push("young_lion_greatsword");
        greatswordAttackDamage = builder.comment("Attack damage ratio: 0.10 means +10%.")
            .worldRestart().defineInRange("attack_damage_bonus", defaults.greatswordAttackDamage(), 0.0, 10.0);
        greatswordKnockbackResistance = builder.comment("Flat knockback resistance: 0.10 means 10 percentage points.")
            .worldRestart().defineInRange("knockback_resistance_bonus", defaults.greatswordKnockbackResistance(), 0.0, 1.0);
        builder.pop();
        builder.push("circlet_of_fading_light");
        circletArmor = builder.comment("Flat armor addition.")
            .worldRestart().defineInRange("armor_bonus", defaults.circletArmor(), 0.0, 1000.0);
        circletLuck = builder.comment("Flat luck addition. Only affects loot tables that use luck.")
            .worldRestart().defineInRange("luck_bonus", defaults.circletLuck(), -1024.0, 1024.0);
        builder.pop(3);
    }

    public EquipmentSettings snapshot() {
        return new EquipmentSettings(blade.snapshot(), greatsword.snapshot(), needleRotCapacity.get(),
            bladeAttackSpeed.get(), greatswordAttackDamage.get(), greatswordKnockbackResistance.get(),
            circletArmor.get(), circletLuck.get());
    }

    private static final class WeaponValues {
        private final Supplier<Double> attackDamage;
        private final Supplier<Double> attackSpeed;
        private final Supplier<Integer> durability;
        private final Supplier<Integer> enchantability;

        private WeaponValues(
                //? if forge {
                ForgeConfigSpec.Builder builder,
                //?} else {
                /*ModConfigSpec.Builder builder,
                *///?}
                String name, EquipmentSettings.Weapon defaults) {
            builder.push(name);
            attackDamage = builder.comment("Total main-hand attack damage, including the player's base 1 damage. Decimals supported.")
                .worldRestart().defineInRange("attack_damage", defaults.attackDamage(), 1.0, 1_000_000.0);
            attackSpeed = builder.comment("Total main-hand attack speed, including the player's base 4 speed.")
                .worldRestart().defineInRange("attack_speed", defaults.attackSpeed(), 0.01, 20.0);
            durability = builder.comment("Maximum durability; applies to existing and newly crafted weapons.")
                .worldRestart().defineInRange("durability", defaults.durability(), 1, 1_000_000);
            enchantability = builder.comment("Enchanting-table quality. 0 disables enchanting-table offers.")
                .worldRestart().defineInRange("enchantability", defaults.enchantability(), 0, 1000);
            builder.pop();
        }

        private EquipmentSettings.Weapon snapshot() {
            return new EquipmentSettings.Weapon(attackDamage.get(), attackSpeed.get(), durability.get(), enchantability.get());
        }
    }
}
