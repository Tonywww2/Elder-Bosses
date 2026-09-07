package com.tonywww.elder_bosses.player;

import com.tonywww.elder_bosses.boss.malenia.config.MaleniaCombatConfigSnapshot;
import com.tonywww.elder_bosses.boss.malenia.config.MaleniaConfigProvider;
import com.tonywww.elder_bosses.combat.status.ScarletRotService;
import com.tonywww.elder_bosses.platforms.registry.ModAttributes;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class PlayerRotService {
    private PlayerRotService() {
    }

    public static boolean addBuildup(
            LivingEntity entity,
            double amount,
            double sourceAttackDamage,
            MaleniaCombatConfigSnapshot.ScarletRot rotConfig
    ) {
        return ScarletRotService.addBuildup(
                entity,
                amount,
                sourceAttackDamage,
                capacity(entity),
                rotConfig
        );
    }

    public static double applyHoney(ServerPlayer player) {
        return ScarletRotService.applyHoney(player, capacity(player));
    }

    public static boolean cleanse(ServerPlayer player) {
        return ScarletRotService.cleanseWithGoldenNeedle(player, capacity(player));
    }

    public static float adjustHealing(LivingEntity entity, float amount) {
        return ScarletRotService.adjustHealing(
                entity,
                amount,
                capacity(entity)
        );
    }

    public static PlayerRotSnapshot snapshot(LivingEntity entity) {
        return PlayerRotSnapshot.from(ScarletRotService.snapshot(
                entity,
            capacity(entity)
        ));
    }

    public static void copy(LivingEntity source, LivingEntity target) {
        ScarletRotService.copy(source, target);
    }

    public static int cleanseUseTicks() {
        return config().cleanseUseTicks();
    }

    public static boolean consumeCleanseItem() {
        return config().consumeCleanseItem();
    }

    public static void installSyncSink(SyncSink sink) {
        SyncSink checkedSink = Objects.requireNonNull(sink, "sink");
        ScarletRotService.installSyncSink((player, snapshot, reason) -> checkedSink.sync(
                player,
                PlayerRotSnapshot.from(snapshot),
                SyncReason.valueOf(reason.name())
        ));
    }

    public static void installMovementStateSink(Consumer<LivingEntity> sink) {
        ScarletRotService.installMovementStateSink(Objects.requireNonNull(sink, "sink"));
    }

    public static void requestSync(ServerPlayer player, SyncReason reason) {
        ScarletRotService.requestSync(
                player,
                capacity(player),
                ScarletRotService.SyncReason.valueOf(reason.name())
        );
    }

    private static double capacity(LivingEntity entity) {
        return ModAttributes.scarletRotCapacity(entity);
    }

    private static MaleniaCombatConfigSnapshot.ScarletRot config() {
        return MaleniaConfigProvider.snapshot().scarletRot();
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
        void sync(ServerPlayer player, PlayerRotSnapshot snapshot, SyncReason reason);
    }
}