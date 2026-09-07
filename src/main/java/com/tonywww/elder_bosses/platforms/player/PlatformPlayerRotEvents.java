package com.tonywww.elder_bosses.platforms.player;

import com.tonywww.elder_bosses.boss.malenia.config.MaleniaConfigProvider;
import com.tonywww.elder_bosses.combat.status.ScarletRotService;
import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import com.tonywww.elder_bosses.player.PlayerRotService;
import com.tonywww.elder_bosses.player.PlayerRotService.SyncReason;
import com.tonywww.elder_bosses.platforms.registry.ModAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
//? if forge {
import java.util.UUID;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
*///?}

public final class PlatformPlayerRotEvents {
    //? if forge {
    private static final UUID SCARLET_ROT_MOVEMENT_SPEED_ID =
        UUID.fromString("3ddf8f9f-a305-4e8a-9f34-b965be493d19");
    //?} else {
    /*private static final ResourceLocation SCARLET_ROT_MOVEMENT_SPEED_ID =
        PlatformResourceLocation.id("scarlet_rot_movement_speed");
    *///?}

    private PlatformPlayerRotEvents() {
    }

    public static void register() {
        PlayerRotService.installMovementStateSink(PlatformPlayerRotEvents::syncMovementSpeed);
        //? if forge {
        MinecraftForge.EVENT_BUS.register(PlatformPlayerRotEvents.class);
        //?} else {
        /*NeoForge.EVENT_BUS.register(PlatformPlayerRotEvents.class);
        *///?}
    }

    //? if forge {
    @SubscribeEvent
    public static void attachLivingEntityData(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof LivingEntity) {
            ForgePlayerRotCapability.Provider provider = new ForgePlayerRotCapability.Provider();
            event.addCapability(PlatformResourceLocation.id("scarlet_rot"), provider);
            event.addListener(provider::invalidate);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.level().isClientSide()) {
            tick(entity);
        }
    }
    //?} else {
    /*@SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity entity && !entity.level().isClientSide()) {
            tick(entity);
        }
    }
    *///?}

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        event.setAmount(ScarletRotService.adjustHealing(
                entity,
                event.getAmount(),
                capacity(entity)
        ));
    }

    @SubscribeEvent
    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (event.getItem().is(Items.HONEY_BOTTLE)) {
            PlayerRotService.applyHoney(player);
        } else if (event.getItem().is(Items.MILK_BUCKET)
                && MaleniaConfigProvider.snapshot().scarletRot().milkClearsRot()) {
            PlayerRotService.cleanse(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        //? if forge {
        Player original = event.getOriginal();
        if (original instanceof ServerPlayer source && event.getEntity() instanceof ServerPlayer target) {
            original.reviveCaps();
            try {
                PlayerRotService.copy(source, target);
            } finally {
                original.invalidateCaps();
            }
        }
        //?} else {
        /*if (event.getOriginal() instanceof ServerPlayer source
                && event.getEntity() instanceof ServerPlayer target) {
            PlatformPlayerRotData.get(target).copyFrom(PlatformPlayerRotData.get(source));
        }
        *///?}
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerRotService.requestSync(player, SyncReason.LOGIN);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerRotService.requestSync(player, SyncReason.RESPAWN);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerRotService.requestSync(player, SyncReason.CHANGED_DIMENSION);
        }
    }

    public static void removeMovementSpeedModifier(LivingEntity entity) {
        AttributeInstance movementSpeed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.removeModifier(SCARLET_ROT_MOVEMENT_SPEED_ID);
        }
    }

    private static void syncMovementSpeed(LivingEntity entity) {
        AttributeInstance movementSpeed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) {
            return;
        }

        com.tonywww.elder_bosses.combat.status.ScarletRotSnapshot snapshot =
            ScarletRotService.snapshot(entity, capacity(entity));
        if (!snapshot.active()) {
            movementSpeed.removeModifier(SCARLET_ROT_MOVEMENT_SPEED_ID);
            return;
        }

        double amount = snapshot.movementSpeedMultiplier() - 1.0;
        if (amount == 0.0) {
            movementSpeed.removeModifier(SCARLET_ROT_MOVEMENT_SPEED_ID);
            return;
        }

        AttributeModifier current = movementSpeed.getModifier(SCARLET_ROT_MOVEMENT_SPEED_ID);
        if (current != null && movementSpeedModifierMatches(current, amount)) {
            return;
        }
        movementSpeed.removeModifier(SCARLET_ROT_MOVEMENT_SPEED_ID);

        //? if forge {
        movementSpeed.addTransientModifier(new AttributeModifier(
                SCARLET_ROT_MOVEMENT_SPEED_ID,
                "elder_bosses.scarlet_rot_movement_speed",
                amount,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        ));
        //?} else {
        /*movementSpeed.addTransientModifier(new AttributeModifier(
                SCARLET_ROT_MOVEMENT_SPEED_ID,
                amount,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        ));
        *///?}
    }

    private static boolean movementSpeedModifierMatches(AttributeModifier modifier, double amount) {
        //? if forge {
        return Double.compare(modifier.getAmount(), amount) == 0
                && modifier.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL;
        //?} else {
        /*return Double.compare(modifier.amount(), amount) == 0
                && modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
        *///?}
    }

    private static void tick(LivingEntity entity) {
        ScarletRotService.tick(entity, capacity(entity));
    }

    private static double capacity(LivingEntity entity) {
        return ModAttributes.scarletRotCapacity(entity);
    }
}