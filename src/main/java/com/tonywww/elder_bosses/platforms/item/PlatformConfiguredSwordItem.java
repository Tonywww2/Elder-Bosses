package com.tonywww.elder_bosses.platforms.item;

import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig.MaleniaConsecratedProstheticBladeValues;
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

    protected PlatformConfiguredSwordItem(MaleniaConsecratedProstheticBladeValues values) {
        this(new ConfiguredTier(values.durability(), values.enchantability()), values);
    }

    private PlatformConfiguredSwordItem(
            Tier tier,
            MaleniaConsecratedProstheticBladeValues values
    ) {
        //? if forge {
        super(
                tier,
                Math.toIntExact(Math.round(values.attackDamage() - PLAYER_BASE_ATTACK_DAMAGE)),
                (float) (values.attackSpeed() - PLAYER_BASE_ATTACK_SPEED),
                new Item.Properties()
        );
        //?} else {
        /*super(
                tier,
                new Item.Properties().attributes(SwordItem.createAttributes(
                        tier,
                        (float) (values.attackDamage() - PLAYER_BASE_ATTACK_DAMAGE),
                        (float) (values.attackSpeed() - PLAYER_BASE_ATTACK_SPEED)
                ))
        );
        *///?}
    }

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