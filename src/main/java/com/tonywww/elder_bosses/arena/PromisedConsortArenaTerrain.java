package com.tonywww.elder_bosses.arena;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

public record PromisedConsortArenaTerrain(int maxHeightDifference, int maxEntryHeightDifference) {
    public static final PromisedConsortArenaTerrain DEFAULT = new PromisedConsortArenaTerrain(12, 1);
    public static final Codec<PromisedConsortArenaTerrain> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT).comapFlatMap(values -> {
        if (!Set.of("max_height_difference", "max_entry_height_difference").containsAll(values.keySet())) {
            return DataResult.error(() -> "Unknown terrain rule");
        }
        int span = values.getOrDefault("max_height_difference", 12);
        int entrySpan = values.getOrDefault("max_entry_height_difference", 1);
        if (span < 0 || span > 64 || entrySpan < 0 || entrySpan > 64) {
            return DataResult.error(() -> "Terrain tolerances must be in 0..64");
        }
        return DataResult.success(new PromisedConsortArenaTerrain(span, entrySpan));
    }, rule -> Map.of("max_height_difference", rule.maxHeightDifference(),
            "max_entry_height_difference", rule.maxEntryHeightDifference()));

    public PromisedConsortArenaTerrain {
        if (maxHeightDifference < 0 || maxHeightDifference > 64 || maxEntryHeightDifference < 0 || maxEntryHeightDifference > 64) {
            throw new IllegalArgumentException("Terrain tolerances must be in 0..64");
        }
    }

    public OptionalInt placementHeight(List<SurfaceSample> samples, int entrySurfaceY, int templateMinY,
                                       int templateMaxY, int minBuildHeight, int maxBuildHeight) {
        return placementHeight((Iterable<SurfaceSample>) samples, entrySurfaceY, templateMinY, templateMaxY, minBuildHeight, maxBuildHeight);
    }

    public OptionalInt placementHeight(Iterable<SurfaceSample> samples, int entrySurfaceY, int templateMinY,
                                       int templateMaxY, int minBuildHeight, int maxBuildHeight) {
        if (templateMinY > templateMaxY || minBuildHeight >= maxBuildHeight) return OptionalInt.empty();
        long originY = (long) entrySurfaceY + 7;
        if (originY + templateMinY < minBuildHeight || originY + templateMaxY >= maxBuildHeight) return OptionalInt.empty();
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        int entryMinimum = Integer.MAX_VALUE;
        int entryMaximum = Integer.MIN_VALUE;
        boolean entryFound = false;
        for (SurfaceSample sample : samples) {
            if (sample.surfaceFluid() || !sample.allowedBiome()
                    || sample.surfaceY() <= minBuildHeight || sample.surfaceY() >= maxBuildHeight
                    || sample.foundationBottomY() < templateMinY || sample.foundationBottomY() > templateMaxY
                    || originY + sample.foundationBottomY() >= sample.surfaceY()) return OptionalInt.empty();
            minimum = Math.min(minimum, sample.surfaceY());
            maximum = Math.max(maximum, sample.surfaceY());
            if ((long) maximum - minimum > maxHeightDifference) return OptionalInt.empty();
            if (sample.entry()) {
                entryFound = true;
                entryMinimum = Math.min(entryMinimum, sample.surfaceY());
                entryMaximum = Math.max(entryMaximum, sample.surfaceY());
                if ((long) entryMaximum - entryMinimum > maxEntryHeightDifference) return OptionalInt.empty();
            }
        }
        if (!entryFound || entrySurfaceY < entryMinimum || entrySurfaceY > entryMaximum) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) originY);
    }

    public record SurfaceSample(int surfaceY, boolean surfaceFluid, boolean allowedBiome,
                                boolean entry, int foundationBottomY) {
    }
}