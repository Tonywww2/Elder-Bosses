package com.tonywww.elder_bosses.boss.malenia.config;

import java.util.Objects;
import java.util.function.Supplier;

public final class MaleniaConfigProvider {
    private static volatile Supplier<MaleniaCombatConfigSnapshot> snapshotSupplier;
    private static volatile Supplier<MaleniaSkillConfigSnapshot> skillSnapshotSupplier;
    private static volatile Supplier<Boolean> debugStateOutputSupplier;
    private static volatile Supplier<Boolean> debugActionBroadcastSupplier;

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

    public static synchronized void installDebugStateOutput(Supplier<Boolean> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        if (debugStateOutputSupplier != null) {
            throw new IllegalStateException("Malenia debug config provider is already installed");
        }
        debugStateOutputSupplier = supplier;
    }

    public static synchronized void installDebugActionBroadcast(Supplier<Boolean> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        if (debugActionBroadcastSupplier != null) {
            throw new IllegalStateException("Malenia action debug config is already installed");
        }
        debugActionBroadcastSupplier = supplier;
    }

    public static boolean debugActionBroadcastEnabled() {
        Supplier<Boolean> supplier = debugActionBroadcastSupplier;
        return supplier != null && supplier.get();
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

    public static boolean debugStateOutputEnabled() {
        Supplier<Boolean> supplier = debugStateOutputSupplier;
        if (supplier == null) {
            throw new IllegalStateException("Malenia debug config provider has not been installed");
        }
        return supplier.get();
    }
}