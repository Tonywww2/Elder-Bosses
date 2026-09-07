package com.tonywww.elder_bosses.boss.malenia.damage;

import com.tonywww.elder_bosses.boss.malenia.config.MaleniaCombatConfigSnapshot;
import com.tonywww.elder_bosses.boss.malenia.config.MaleniaCombatConfigSnapshot.ResistanceCategory;
import com.tonywww.elder_bosses.boss.malenia.config.MaleniaCombatConfigSnapshot.RoutedSource;
import com.tonywww.elder_bosses.combat.damage.ModDamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

import java.util.Objects;
import java.util.Optional;

public final class MaleniaIncomingDamageResolver {
    private MaleniaIncomingDamageResolver() {
    }

    public static Resolution resolve(
            DamageSource source,
            MaleniaCombatConfigSnapshot snapshot,
            boolean phaseTwo
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(snapshot, "snapshot");
        if (isUnscaled(source)) {
            return Resolution.unscaledResult();
        }

        Classification classification = classify(source);
        double resistanceMultiplier = snapshot.resistance()
                .forPhase(phaseTwo)
                .multiplier(classification.resistanceCategory());
        double sourceMultiplier = classification.routedSource()
                .map(snapshot.sourceMultiplier().forPhase(phaseTwo)::multiplier)
                .orElse(1.0);
        return new Resolution(
                classification.resistanceCategory(),
                classification.routedSource(),
                resistanceMultiplier,
                sourceMultiplier,
                resistanceMultiplier * sourceMultiplier,
                false
        );
    }

    public static boolean isUnscaled(DamageSource source) {
        Objects.requireNonNull(source, "source");
        return source.is(ModDamageTypeTags.BYPASSES_BOSS_SCALING);
    }

        private static Classification classify(DamageSource source) {
        if (source.is(ModDamageTypeTags.HOLY)) {
            return routed(ResistanceCategory.MAGIC, RoutedSource.HOLY);
        }
        if (source.is(ModDamageTypeTags.PIERCE)) {
            return routed(ResistanceCategory.PHYSICAL, RoutedSource.PIERCE);
        }
        if (source.is(ModDamageTypeTags.BLEED_TRIGGER)) {
            return routed(ResistanceCategory.PHYSICAL, RoutedSource.BLEED_TRIGGER);
        }
        if (source.is(ModDamageTypeTags.FROST_TRIGGER)) {
            return routed(ResistanceCategory.MAGIC, RoutedSource.FROST_TRIGGER);
        }
        if (source.is(ModDamageTypeTags.FIRE)) {
            return new Classification(ResistanceCategory.FIRE, Optional.empty());
        }
        if (source.is(ModDamageTypeTags.LIGHTNING)) {
            return new Classification(ResistanceCategory.LIGHTNING, Optional.empty());
        }
        if (source.is(ModDamageTypeTags.MAGIC)) {
            return routed(ResistanceCategory.MAGIC, RoutedSource.MAGIC);
        }
        return routed(ResistanceCategory.PHYSICAL, RoutedSource.ORDINARY_PHYSICAL);
    }

    private static Classification routed(
            ResistanceCategory resistanceCategory,
            RoutedSource routedSource
    ) {
        return new Classification(resistanceCategory, Optional.of(routedSource));
    }

    public record Resolution(
            ResistanceCategory resistanceCategory,
            Optional<RoutedSource> sourceCategory,
            double resistanceMultiplier,
            double sourceMultiplier,
            double multiplier,
            boolean unscaled
    ) {
        public Resolution {
            Objects.requireNonNull(resistanceCategory, "resistanceCategory");
            Objects.requireNonNull(sourceCategory, "sourceCategory");
        }

        private static Resolution unscaledResult() {
            return new Resolution(
                    ResistanceCategory.PHYSICAL,
                    Optional.empty(),
                    1.0,
                    1.0,
                    1.0,
                    true
            );
        }
    }

    private record Classification(
            ResistanceCategory resistanceCategory,
            Optional<RoutedSource> routedSource
    ) {
    }

}