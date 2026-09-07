package com.tonywww.elder_bosses.platforms.item;

import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig.MaleniaUnalloyedWingedHelmValues;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

public final class PlatformArmorMaterials {
    private PlatformArmorMaterials() {
    }

    public static ArmorMaterial createUnalloyedGold(MaleniaUnalloyedWingedHelmValues values) {
        //? if forge {
        return new ConfiguredArmorMaterial(values);
        //?} else {
        /*Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            defense.put(type, type == ArmorItem.Type.HELMET ? roundedArmor(values.armor()) : 0);
        }
        return new ArmorMaterial(
                defense,
                0,
                SoundEvents.ARMOR_EQUIP_GOLD,
                () -> Ingredient.EMPTY,
                List.of(new ArmorMaterial.Layer(PlatformResourceLocation.minecraft("gold"))),
                (float) values.armorToughness(),
                (float) values.knockbackResistance()
        );
        *///?}
    }

    private static int roundedArmor(double armor) {
        return Math.toIntExact(Math.round(armor));
    }

    //? if forge {
    private static final class ConfiguredArmorMaterial implements ArmorMaterial {
        private final MaleniaUnalloyedWingedHelmValues values;

        private ConfiguredArmorMaterial(MaleniaUnalloyedWingedHelmValues values) {
            this.values = values;
        }

        @Override
        public int getDurabilityForType(ArmorItem.Type type) {
            return values.durability();
        }

        @Override
        public int getDefenseForType(ArmorItem.Type type) {
            return type == ArmorItem.Type.HELMET ? roundedArmor(values.armor()) : 0;
        }

        @Override
        public int getEnchantmentValue() {
            return 0;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_GOLD;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }

        @Override
        public String getName() {
            return PlatformResourceLocation.minecraft("gold").toString();
        }

        @Override
        public float getToughness() {
            return (float) values.armorToughness();
        }

        @Override
        public float getKnockbackResistance() {
            return (float) values.knockbackResistance();
        }
    }
    //?}
}