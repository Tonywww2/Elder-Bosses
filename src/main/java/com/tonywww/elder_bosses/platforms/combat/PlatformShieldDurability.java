package com.tonywww.elder_bosses.platforms.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
//? if neoforge {
/*import net.minecraft.world.entity.EquipmentSlot;
*///?}
import net.minecraft.world.item.ItemStack;

public final class PlatformShieldDurability {
    private PlatformShieldDurability() {
    }

    public static UsedItemSnapshot captureUsedItem(ServerPlayer player) {
        ItemStack stack = player.getUseItem();
        if (stack.isEmpty()) {
            return UsedItemSnapshot.EMPTY;
        }
        return new UsedItemSnapshot(player.getUsedItemHand(), stack);
    }

    public static void applyConfiguredDamage(
            ServerPlayer player,
            UsedItemSnapshot snapshot,
            float attemptedDamage,
            double multiplier
    ) {
        if (!snapshot.tracked()) {
            return;
        }
        ItemStack stack = snapshot.stack();
        if (player.getItemInHand(snapshot.hand()) != stack || stack.isEmpty()) {
            return;
        }
        if (stack.isDamageableItem() && attemptedDamage > 0.0F && multiplier > 0.0) {
            int durabilityDamage = Math.max(1, Mth.ceil(attemptedDamage * multiplier));
            //? if forge {
            stack.hurtAndBreak(
                    durabilityDamage,
                    player,
                    entity -> entity.broadcastBreakEvent(snapshot.hand())
            );
            //?} else {
            /*EquipmentSlot slot = snapshot.hand() == InteractionHand.MAIN_HAND
                    ? EquipmentSlot.MAINHAND
                    : EquipmentSlot.OFFHAND;
            stack.hurtAndBreak(durabilityDamage, player, slot);
            *///?}
        }
    }

    public record UsedItemSnapshot(
            InteractionHand hand,
            ItemStack stack
    ) {
        private static final UsedItemSnapshot EMPTY = new UsedItemSnapshot(
                InteractionHand.MAIN_HAND,
                ItemStack.EMPTY
        );

        public boolean tracked() {
            return !stack.isEmpty();
        }
    }
}