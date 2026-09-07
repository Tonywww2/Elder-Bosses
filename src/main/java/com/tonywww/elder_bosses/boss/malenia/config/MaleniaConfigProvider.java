package com.tonywww.elder_bosses.boss.malenia.config;

import java.util.Objects;
import java.util.function.Supplier;

public final class MaleniaConfigProvider {
    private static volatile Supplier<MaleniaCombatConfigSnapshot> snapshotSupplier;
    private static volatile Supplier<MaleniaSkillConfigSnapshot> skillSnapshotSupplier;

    private MaleniaConfigProvider() {
    }

    public static synchronized void install(Supplier<MaleniaCombatConfigSnapshot> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        if (snapshotSupplier != null) {
            throw new IllegalStateException("Malenia config provider is already installed");
        }
        snapshotSupplier = supplier;
    }

    public static synchronized void installSkills(Supplier<MaleniaSkillConfigSnapshot> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        if (skillSnapshotSupplier != null) {
            throw new IllegalStateException("Malenia skill config provider is already installed");
        }
        skillSnapshotSupplier = supplier;
    }

    public static MaleniaCombatConfigSnapshot snapshot() {
        Supplier<MaleniaCombatConfigSnapshot> supplier = snapshotSupplier;
        if (supplier == null) {
            throw new IllegalStateException("Malenia config provider has not been installed");
        }
        return Objects.requireNonNull(supplier.get(), "Malenia config snapshot");
    }

    public static MaleniaSkillConfigSnapshot skillSnapshot() {
        Supplier<MaleniaSkillConfigSnapshot> supplier = skillSnapshotSupplier;
        if (supplier == null) {
            throw new IllegalStateException("Malenia skill config provider has not been installed");
        }
        return Objects.requireNonNull(supplier.get(), "Malenia skill config snapshot");
    }
}