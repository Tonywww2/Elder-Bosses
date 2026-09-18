package com.tonywww.elder_bosses.arena;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

public record PromisedConsortArenaTerrain(int maxHeightDifference, int maxEntryHeightDifference,
                                         int maxWaterDepth, int maxWaterPercentage) {
    public static final PromisedConsortArenaTerrain DEFAULT = new PromisedConsortArenaTerrain(16, 4, 4, 25);
    public static final Codec<PromisedConsortArenaTerrain> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT).comapFlatMap(values -> {
        if (!Set.of("max_height_difference", "max_entry_height_difference", "max_water_depth", "max_water_percentage").containsAll(values.keySet())) {
            return DataResult.error(() -> "Unknown terrain rule");
        }
        int span = values.getOrDefault("max_height_difference", 16);
        int entrySpan = values.getOrDefault("max_entry_height_difference", 4);
        int waterDepth = values.getOrDefault("max_water_depth", 4);
        int waterPercentage = values.getOrDefault("max_water_percentage", 25);
        if (span < 0 || span > 64 || entrySpan < 0 || entrySpan > 64 || waterDepth < 0 || waterDepth > 64
                || waterPercentage < 0 || waterPercentage > 100) {
            return DataResult.error(() -> "Terrain heights must be in 0..64 and water percentage in 0..100");
        }
        return DataResult.success(new PromisedConsortArenaTerrain(span, entrySpan, waterDepth, waterPercentage));
    }, rule -> Map.of("max_height_difference", rule.maxHeightDifference(),
            "max_entry_height_difference", rule.maxEntryHeightDifference(),
            "max_water_depth", rule.maxWaterDepth(), "max_water_percentage", rule.maxWaterPercentage()));

    public PromisedConsortArenaTerrain(int maxHeightDifference, int maxEntryHeightDifference) {
        this(maxHeightDifference, maxEntryHeightDifference, 0, 0);
    }

    public PromisedConsortArenaTerrain {
        if (maxHeightDifference < 0 || maxHeightDifference > 64 || maxEntryHeightDifference < 0 || maxEntryHeightDifference > 64
                || maxWaterDepth < 0 || maxWaterDepth > 64 || maxWaterPercentage < 0 || maxWaterPercentage > 100) {
            throw new IllegalArgumentException("Terrain heights must be in 0..64 and water percentage in 0..100");
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

    public ColumnSample sampleColumn(int surfaceY, int foundationBottomY, int minBuildHeight, IntFunction<BlockState> column) {
        int depth = 0;
        int bottom = surfaceY - 1;
        while (bottom >= minBuildHeight && Fluids.WATER.isSame(column.apply(bottom).getFluidState().getType())) {
            depth++;
            if (depth > maxWaterDepth) return new ColumnSample(surfaceY, depth, false, foundationBottomY);
            bottom--;
        }
        boolean supported = bottom >= minBuildHeight && column.apply(bottom).getFluidState().isEmpty()
                && column.apply(bottom).isFaceSturdy(EmptyBlockGetter.INSTANCE, new BlockPos(0, bottom, 0), Direction.UP);
        return new ColumnSample(surfaceY, depth, supported, foundationBottomY);
    }

    public boolean supportsColumns(Iterable<ColumnSample> columns, int originY, int totalSamples) {
        if (totalSamples <= 0) return false;
        int checked = 0;
        int wet = 0;
        for (ColumnSample column : columns) {
            if (++checked > totalSamples || !column.groundSupported() || column.waterDepth() < 0
                    || column.waterDepth() > maxWaterDepth
                    || (long) originY + column.foundationBottomY() >= (long) column.surfaceY() - column.waterDepth()) return false;
            if (column.waterDepth() > 0 && (long) ++wet * 100 > (long) totalSamples * maxWaterPercentage) return false;
        }
        return checked == totalSamples;
    }

    public record ColumnSample(int surfaceY, int waterDepth, boolean groundSupported, int foundationBottomY) {
    }

    public record SurfaceSample(int surfaceY, boolean surfaceFluid, boolean allowedBiome,
                                boolean entry, int foundationBottomY) {
    }
}