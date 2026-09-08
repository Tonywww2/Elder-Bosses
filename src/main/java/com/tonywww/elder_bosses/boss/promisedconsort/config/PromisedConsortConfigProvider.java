package com.tonywww.elder_bosses.boss.promisedconsort.config;

import java.util.Objects;
import java.util.function.Supplier;

public final class PromisedConsortConfigProvider {
    private static volatile Supplier<PromisedConsortCombatConfigSnapshot> combatSupplier;
    private static volatile Supplier<PromisedConsortSkillConfigSnapshot> skillSupplier;

    private PromisedConsortConfigProvider() {
    }

    public static synchronized void installCombat(
            Supplier<PromisedConsortCombatConfigSnapshot> supplier
    ) {
        Objects.requireNonNull(supplier, "supplier");
        if (combatSupplier != null) {
            throw new IllegalStateException("Promised Consort combat config is already installed");
        }
        combatSupplier = supplier;
    }

    public static synchronized void installSkills(
            Supplier<PromisedConsortSkillConfigSnapshot> supplier
    ) {
        Objects.requireNonNull(supplier, "supplier");
        if (skillSupplier != null) {
            throw new IllegalStateException("Promised Consort skill config is already installed");
        }
        skillSupplier = supplier;
    }

    public static PromisedConsortCombatConfigSnapshot combatSnapshot() {
        Supplier<PromisedConsortCombatConfigSnapshot> supplier = combatSupplier;
        if (supplier == null) {
            throw new IllegalStateException("Promised Consort combat config is not installed");
        }
        return Objects.requireNonNull(supplier.get(), "Promised Consort combat config snapshot");
    }

    public static PromisedConsortSkillConfigSnapshot skillSnapshot() {
        Supplier<PromisedConsortSkillConfigSnapshot> supplier = skillSupplier;
        if (supplier == null) {
            throw new IllegalStateException("Promised Consort skill config is not installed");
        }
        return Objects.requireNonNull(supplier.get(), "Promised Consort skill config snapshot");
    }
}
