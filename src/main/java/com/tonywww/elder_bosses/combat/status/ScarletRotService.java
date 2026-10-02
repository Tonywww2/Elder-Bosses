package com.tonywww.elder_bosses.combat.status;

import com.tonywww.elder_bosses.boss.malenia.config.MaleniaCombatConfigSnapshot;
import com.tonywww.elder_bosses.combat.damage.ModDamageSources;
import com.tonywww.elder_bosses.item.RotBuildupMultiplierItem;
import com.tonywww.elder_bosses.platforms.player.PlatformPlayerRotData;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class ScarletRotService {
    private static volatile Consumer<LivingEntity> movementStateSink = entity -> {
    };
    private static volatile SyncSink syncSink = (player, snapshot, reason) -> {
    };

    private ScarletRotService() {
    }

    public static boolean addBuildup(
            LivingEntity entity,
            double amount,
            double sourceAttackDamage,
            double capacity,
            MaleniaCombatConfigSnapshot.ScarletRot rotConfig
    ) {
        Objects.requireNonNull(entity, "entity");
        double checkedCapacity = requireCapacity(capacity);
        Optional<ScarletRotData> availableData = data(entity);
        if (availableData.isEmpty()) {
            return false;
        }
        long gameTick = entity.level().getGameTime();
        double adjustedAmount = applyPlayerEquipmentMultiplier(entity, amount);
        ScarletRotData entityData = availableData.get();
        ScarletRotSnapshot previousSnapshot = entityData.snapshot(gameTick, checkedCapacity);
        boolean triggered = entityData.addBuildup(
                adjustedAmount,
                sourceAttackDamage,
                checkedCapacity,
                gameTick,
                rotConfig
        );
        if (!entityData.snapshot(gameTick, checkedCapacity).equals(previousSnapshot)) {
            requestSync(
                    entity,
                    checkedCapacity,
                    triggered ? SyncReason.ROT_TRIGGERED : SyncReason.STATE_CHANGED
            );
        }
        return triggered;
    }

    public static double applyHoney(ServerPlayer player, double capacity) {
        Objects.requireNonNull(player, "player");
        double checkedCapacity = requireCapacity(capacity);
        Optional<ScarletRotData> availableData = data(player);
        if (availableData.isEmpty()) {
            return 0.0;
        }
        double reduction = availableData.get().applyHoney(player.level().getGameTime());
        if (reduction > 0.0) {
            requestSync(player, checkedCapacity, SyncReason.HONEY_APPLIED);
        }
        return reduction;
    }

    public static boolean cleanseWithGoldenNeedle(ServerPlayer player, double capacity) {
        Objects.requireNonNull(player, "player");
        double checkedCapacity = requireCapacity(capacity);
        Optional<ScarletRotData> availableData = data(player);
        if (availableData.isEmpty()) {
            return false;
        }
        boolean changed = availableData.get().cleanse(player.level().getGameTime());
        if (changed) {
            requestSync(player, checkedCapacity, SyncReason.CLEANSED);
        }
        return changed;
    }

    public static ScarletRotTickResult tick(LivingEntity entity, double capacity) {
        Objects.requireNonNull(entity, "entity");
        double checkedCapacity = requireCapacity(capacity);
        Optional<ScarletRotData> availableData = data(entity);
        if (availableData.isEmpty()) {
            return ScarletRotTickResult.unchanged();
        }
        ScarletRotTickResult result = availableData.get().tick(
                entity.level().getGameTime(),
                checkedCapacity
        );
        applyDamagePulses(entity, result);
        if (result.stateChanged()) {
            requestSync(entity, checkedCapacity, SyncReason.STATE_CHANGED);
        }
        return result;
    }

    public static ScarletRotSnapshot snapshot(LivingEntity entity, double capacity) {
        Objects.requireNonNull(entity, "entity");
        double checkedCapacity = requireCapacity(capacity);
        if (entity.isRemoved() || !entity.isAlive()) {
            // A discarded entity has no live rot state. Do not access its world or capabilities.
            return ScarletRotSnapshot.inactive(0L, checkedCapacity);
        }
        long gameTick = entity.level().getGameTime();
        return data(entity)
                .map(value -> value.snapshot(gameTick, checkedCapacity))
                .orElseGet(() -> ScarletRotSnapshot.inactive(gameTick, checkedCapacity));
    }

    public static double healingMultiplier(LivingEntity entity, double capacity) {
        return snapshot(entity, capacity).healingMultiplier();
    }

    public static double movementSpeedMultiplier(LivingEntity entity, double capacity) {
        return snapshot(entity, capacity).movementSpeedMultiplier();
    }

    public static float adjustHealing(LivingEntity entity, float amount, double capacity) {
        if (!Float.isFinite(amount) || amount < 0.0F) {
            throw new IllegalArgumentException("amount must be finite and non-negative");
        }
        return (float) (amount * healingMultiplier(entity, capacity));
    }

    public static void copy(LivingEntity source, LivingEntity target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        // Clone explicitly revives the old player's capabilities, even though it is dead.
        PlatformPlayerRotData.find(source).ifPresent(sourceData ->
                PlatformPlayerRotData.find(target).ifPresent(targetData -> targetData.copyFrom(sourceData))
        );
    }

    public static void installSyncSink(SyncSink sink) {
        syncSink = Objects.requireNonNull(sink, "sink");
    }

    public static void installMovementStateSink(Consumer<LivingEntity> sink) {
        movementStateSink = Objects.requireNonNull(sink, "sink");
    }

    public static void requestSync(
            LivingEntity entity,
            double capacity,
            SyncReason reason
    ) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(reason, "reason");
        double checkedCapacity = requireCapacity(capacity);
        Optional<ScarletRotData> availableData = data(entity);
        if (availableData.isEmpty()) {
            return;
        }
        movementStateSink.accept(entity);
        if (!entity.isRemoved() && entity.isAlive() && entity instanceof ServerPlayer player) {
            syncSink.sync(player, availableData.get().snapshot(player.level().getGameTime(), checkedCapacity), reason);
        }
    }

    private static Optional<ScarletRotData> data(LivingEntity entity) {
        // Death, dimension removal and other mods can invalidate capabilities before late callbacks.
        // Skip unavailable data rather than reviving a corpse or replacing its persisted rot state.
        if (entity.isRemoved() || !entity.isAlive() || entity.level().isClientSide()) {
            return Optional.empty();
        }
        return PlatformPlayerRotData.find(entity);
    }

    private static double applyPlayerEquipmentMultiplier(LivingEntity entity, double amount) {
        double adjustedAmount = requireNonNegativeFinite(amount, "amount");
        if (!(entity instanceof Player player)) {
            return adjustedAmount;
        }
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(helmet.getItem() instanceof RotBuildupMultiplierItem multiplierItem)) {
            return adjustedAmount;
        }
        double multiplier = requireNonNegativeFinite(
                multiplierItem.rotBuildupMultiplier(helmet),
                "rotBuildupMultiplier"
        );
        return requireNonNegativeFinite(adjustedAmount * multiplier, "adjustedAmount");
    }

    private static void applyDamagePulses(
            LivingEntity entity,
            ScarletRotTickResult result
    ) {
        if (result.damagePulses() == 0) {
            return;
        }

        float damage = (float) Math.min(result.damagePerPulse().orElseThrow(), Float.MAX_VALUE);
        if (damage <= 0.0F) {
            return;
        }
        for (int pulse = 0; pulse < result.damagePulses() && !entity.isRemoved() && entity.isAlive(); pulse++) {
            entity.hurt(ModDamageSources.scarletRot(entity.level().registryAccess()), damage);
        }
    }

    private static double requireCapacity(double capacity) {
        if (!Double.isFinite(capacity) || capacity <= 0.0) {
            throw new IllegalArgumentException("capacity must be finite and positive");
        }
        return capacity;
    }

    private static double requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }

    public enum SyncReason {
        STATE_CHANGED,
        ROT_TRIGGERED,
        HONEY_APPLIED,
        CLEANSED,
        LOGIN,
        RESPAWN,
        CHANGED_DIMENSION
    }

    @FunctionalInterface
    public interface SyncSink {
        void sync(ServerPlayer player, ScarletRotSnapshot snapshot, SyncReason reason);
    }
}
