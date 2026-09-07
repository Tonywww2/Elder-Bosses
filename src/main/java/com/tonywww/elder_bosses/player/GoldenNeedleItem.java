package com.tonywww.elder_bosses.player;

import com.tonywww.elder_bosses.boss.malenia.config.MaleniaConfigProvider;
import com.tonywww.elder_bosses.platforms.item.PlatformUseDurationItem;
import com.tonywww.elder_bosses.platforms.player.PlatformPlayerRotEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public final class GoldenNeedleItem extends PlatformUseDurationItem {
    public GoldenNeedleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    protected int configuredUseDuration() {
        return MaleniaConfigProvider.snapshot().scarletRot().cleanseUseTicks();
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        if (!level.isClientSide && livingEntity instanceof ServerPlayer player) {
            PlayerRotService.cleanse(player);
            PlatformPlayerRotEvents.removeMovementSpeedModifier(player);
            if (MaleniaConfigProvider.snapshot().scarletRot().consumeCleanseItem()
                    && !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return stack;
    }
}