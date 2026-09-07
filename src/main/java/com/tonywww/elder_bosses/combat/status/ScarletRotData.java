package com.tonywww.elder_bosses.combat.status;

import com.tonywww.elder_bosses.boss.malenia.config.MaleniaCombatConfigSnapshot;
import net.minecraft.nbt.CompoundTag;

public interface ScarletRotData {
    boolean addBuildup(
            double amount,
            double sourceAttackDamage,
            double capacity,
            long gameTick,
            MaleniaCombatConfigSnapshot.ScarletRot rotConfig
    );

    double applyHoney(long gameTick);

    boolean cleanse(long gameTick);

    ScarletRotTickResult tick(long gameTick, double capacity);

    ScarletRotSnapshot snapshot(long gameTick, double capacity);

    CompoundTag save();

    void load(CompoundTag tag);

    default void copyFrom(ScarletRotData source) {
        load(source.save());
    }
}