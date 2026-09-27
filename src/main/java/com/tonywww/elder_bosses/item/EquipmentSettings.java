package com.tonywww.elder_bosses.item;

/** Immutable equipment values, loaded once at startup and synchronized to joining clients. */
public record EquipmentSettings(
        Weapon blade, Weapon greatsword, double needleRotCapacity, double bladeAttackSpeed,
        double greatswordAttackDamage, double greatswordKnockbackResistance,
        double circletArmor, double circletLuck) {
    public static final EquipmentSettings DEFAULT = new EquipmentSettings(
        new Weapon(9.0, 1.6, 2300, 15), new Weapon(12.0, 1.0, 2400, 15),
        40.0, 0.10, 0.10, 0.10, 2.0, 1.0);

    public EquipmentSettings {
        java.util.Objects.requireNonNull(blade);
        java.util.Objects.requireNonNull(greatsword);
        range(needleRotCapacity, 0, 1_000_000);
        range(bladeAttackSpeed, 0, 10);
        range(greatswordAttackDamage, 0, 10);
        range(greatswordKnockbackResistance, 0, 1);
        range(circletArmor, 0, 1000);
        range(circletLuck, -1024, 1024);
    }

    public record Weapon(double attackDamage, double attackSpeed, int durability, int enchantability) {
        public Weapon {
            range(attackDamage, 1, 1_000_000);
            range(attackSpeed, 0.01, 20);
            range(durability, 1, 1_000_000);
            range(enchantability, 0, 1000);
        }
    }

    private static void range(double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException("Equipment value outside [" + min + ", " + max + "]: " + value);
        }
    }
}
