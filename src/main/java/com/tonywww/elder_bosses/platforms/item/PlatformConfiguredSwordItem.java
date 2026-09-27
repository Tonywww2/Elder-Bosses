package com.tonywww.elder_bosses.platforms.item;

import com.tonywww.elder_bosses.item.EquipmentSettings.Weapon;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
//? if forge {
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
//?} else {
/*import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.component.ItemAttributeModifiers;
*///?}
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

public abstract class PlatformConfiguredSwordItem extends SwordItem {
    private static final double PLAYER_BASE_ATTACK_DAMAGE = 1.0;
    private static final double PLAYER_BASE_ATTACK_SPEED = 4.0;
    private final Supplier<Weapon> settings;

    protected PlatformConfiguredSwordItem(Weapon defaults, Supplier<Weapon> settings) {
        this(new ConfiguredTier(defaults.durability(), defaults.enchantability()), defaults, settings);
    }

    private PlatformConfiguredSwordItem(
            Tier tier,
            Weapon defaults,
            Supplier<Weapon> settings
    ) {
        //? if forge {
        super(
                tier,
                Math.toIntExact(Math.round(defaults.attackDamage() - PLAYER_BASE_ATTACK_DAMAGE)),
                (float) (defaults.attackSpeed() - PLAYER_BASE_ATTACK_SPEED),
                new Item.Properties()
        );
        //?} else {
        /*super(tier, new Item.Properties());
        *///?}
        this.settings = settings;
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return settings.get().durability();
    }

    @Override
    public int getEnchantmentValue() {
        return settings.get().enchantability();
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return settings.get().enchantability();
    }

    //? if forge {
    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot != EquipmentSlot.MAINHAND) return ImmutableMultimap.of();
        Weapon values = settings.get();
        return ImmutableMultimap.of(
            Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier",
                values.attackDamage() - PLAYER_BASE_ATTACK_DAMAGE, AttributeModifier.Operation.ADDITION),
            Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier",
                values.attackSpeed() - PLAYER_BASE_ATTACK_SPEED, AttributeModifier.Operation.ADDITION));
    }

    @Override
    public float getDamage() {
        return (float) (settings.get().attackDamage() - PLAYER_BASE_ATTACK_DAMAGE);
    }
    //?} else {
    /*@Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        return getDefaultAttributeModifiers();
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        Weapon values = settings.get();
        return ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID,
                values.attackDamage() - PLAYER_BASE_ATTACK_DAMAGE, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID,
                values.attackSpeed() - PLAYER_BASE_ATTACK_SPEED, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .build();
    }
    *///?}

    private static final class ConfiguredTier implements Tier {
        private final int durability;
        private final int enchantability;

        private ConfiguredTier(int durability, int enchantability) {
            this.durability = durability;
            this.enchantability = enchantability;
        }

        @Override
        public int getUses() {
            return durability;
        }

        @Override
        public float getSpeed() {
            return Tiers.NETHERITE.getSpeed();
        }

        @Override
        public float getAttackDamageBonus() {
            return 0.0F;
        }

        //? if forge {
        @Override
        public int getLevel() {
            return Tiers.NETHERITE.getLevel();
        }
        //?} else {
        /*@Override
        public TagKey<Block> getIncorrectBlocksForDrops() {
            return Tiers.NETHERITE.getIncorrectBlocksForDrops();
        }
        *///?}

        @Override
        public int getEnchantmentValue() {
            return enchantability;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }
    }
}
