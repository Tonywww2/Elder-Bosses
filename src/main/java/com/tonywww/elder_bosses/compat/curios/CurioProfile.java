package com.tonywww.elder_bosses.compat.curios;

import com.tonywww.elder_bosses.item.EquipmentSettings;
import java.util.List;
import java.util.Locale;

/** Gameplay values shared by both Curios adapters; amounts never depend on stack count. */
public enum CurioProfile {
    GOLDEN_NEEDLE("charm"),
    CONSECRATED_PROSTHETIC_BLADE("charm"),
    YOUNG_LION_GREATSWORD("charm"),
    CIRCLET_OF_FADING_LIGHT("head");

    public enum Stat {
        ROT_CAPACITY, ATTACK_SPEED, ATTACK_DAMAGE, ARMOR, KNOCKBACK_RESISTANCE, LUCK
    }

    /** Percent bonuses use Minecraft's additive base multiplier, so they do not compound. */
    public record Bonus(Stat stat, double amount, boolean percent) {
    }

    private final String slot;

    CurioProfile(String slot) {
        this.slot = slot;
    }

    public String itemId() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String slot() {
        return slot;
    }

    public List<Bonus> bonuses(EquipmentSettings settings) {
        return switch (this) {
            case GOLDEN_NEEDLE -> List.of(flat(Stat.ROT_CAPACITY, settings.needleRotCapacity()));
            case CONSECRATED_PROSTHETIC_BLADE -> List.of(percent(Stat.ATTACK_SPEED, settings.bladeAttackSpeed()));
            case YOUNG_LION_GREATSWORD -> List.of(percent(Stat.ATTACK_DAMAGE, settings.greatswordAttackDamage()),
                flat(Stat.KNOCKBACK_RESISTANCE, settings.greatswordKnockbackResistance()));
            case CIRCLET_OF_FADING_LIGHT -> List.of(flat(Stat.ARMOR, settings.circletArmor()), flat(Stat.LUCK, settings.circletLuck()));
        };
    }

    private static Bonus flat(Stat stat, double amount) {
        return new Bonus(stat, amount, false);
    }

    private static Bonus percent(Stat stat, double amount) {
        return new Bonus(stat, amount, true);
    }
}
