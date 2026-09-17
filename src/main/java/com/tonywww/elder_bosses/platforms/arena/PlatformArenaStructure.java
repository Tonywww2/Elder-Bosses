package com.tonywww.elder_bosses.platforms.arena;

import com.mojang.serialization.Codec;
//? if neoforge {
/*import com.mojang.serialization.MapCodec;
*///?}
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tonywww.elder_bosses.arena.PromisedConsortArenaTerrain;
import com.tonywww.elder_bosses.arena.PromisedConsortArenaTerrain.SurfaceSample;
import com.tonywww.elder_bosses.platforms.registry.ModStructures;
import java.util.LinkedHashMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;

public final class PlatformArenaStructure extends Structure {
    //? if forge {
    public static final Codec<PlatformArenaStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
    //?} else {
    /*public static final MapCodec<PlatformArenaStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
    *///?}
            settingsCodec(instance),
            PromisedConsortArenaTerrain.CODEC.fieldOf("terrain").forGetter(structure -> structure.terrain)
    ).apply(instance, PlatformArenaStructure::new));

    private final PromisedConsortArenaTerrain terrain;

    public PlatformArenaStructure(StructureSettings settings, PromisedConsortArenaTerrain terrain) {
        super(settings);
        if (settings.step() != GenerationStep.Decoration.SURFACE_STRUCTURES || settings.terrainAdaptation() != TerrainAdjustment.NONE) {
            throw new IllegalArgumentException("Arena requires surface_structures and terrain_adaptation=none");
        }
        this.terrain = terrain;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var current = PlatformArenaWorldgen.definition(context.structureTemplateManager());
        if (current.isEmpty()) return Optional.empty();
        Rotation first = Rotation.getRandom(context.random());
        Rotation[] rotations = Rotation.values();
        for (int attempt = 0; attempt < rotations.length; attempt++) {
            var candidate = findGenerationPoint(context, current.get(), rotations[(first.ordinal() + attempt) % rotations.length]);
            if (candidate.isPresent()) return candidate;
        }
        return Optional.empty();
    }

    private Optional<GenerationStub> findGenerationPoint(GenerationContext context, PlatformArenaWorldgen.Definition definition,
                                                         Rotation rotation) {
        var loaded = definition.loaded();
        var site = definition.site();
        BlockPos horizontalOrigin = new BlockPos(context.chunkPos().getMiddleBlockX(), 0, context.chunkPos().getMiddleBlockZ());
        BlockPos entrance = horizontalOrigin.offset(site.entryProbe().rotate(rotation));
        int entryHeight = surfaceHeight(context, entrance);
        var centerBiome = context.biomeSource().getNoiseBiome(QuartPos.fromBlock(horizontalOrigin.getX()), QuartPos.fromBlock(entryHeight + 7),
                QuartPos.fromBlock(horizontalOrigin.getZ()), context.randomState().sampler());
        if (!biomes().contains(centerBiome)) return Optional.empty();
        var sampledSurfaces = new LinkedHashMap<BlockPos, Integer>();
        Iterable<SurfaceSample> samples = () -> site.samples().stream().map(point -> {
            BlockPos position = horizontalOrigin.offset(point.position().rotate(rotation));
            int surface = surfaceHeight(context, position);
            sampledSurfaces.put(position, surface);
            return new SurfaceSample(surface, false, true, point.entry(), point.foundationBottomY());
        }).iterator();
        var height = terrain.placementHeight(samples, entryHeight, site.minimumY(), site.maximumY(),
                context.heightAccessor().getMinBuildHeight(), context.heightAccessor().getMaxBuildHeight());
        if (height.isEmpty()) return Optional.empty();
        for (var sample : sampledSurfaces.entrySet()) {
            BlockPos position = sample.getKey();
            var column = context.chunkGenerator().getBaseColumn(position.getX(), position.getZ(), context.heightAccessor(), context.randomState());
            if (!column.getBlock(sample.getValue() - 1).getFluidState().isEmpty()) return Optional.empty();
        }
        BlockPos origin = horizontalOrigin.atY(height.getAsInt());
        var metadata = loaded.layout().instanceMetadata(origin, rotation, loaded.hashes().get("root"));
        var hashes = new net.minecraft.nbt.CompoundTag();
        loaded.hashes().forEach(hashes::putString);
        metadata.put("TemplateHashes", hashes);
        return Optional.of(new GenerationStub(origin, builder -> {
            for (var part : loaded.layout().parts()) {
                var partMetadata = metadata.copy();
                partMetadata.putString("Part", part.name());
                builder.addPiece(new PlatformArenaPiece(ModStructures.ARENA_PIECE.get(), loaded.templates().get(part.name()),
                        part.placementOrigin(origin, rotation), rotation, partMetadata));
            }
        }));
    }

    private static int surfaceHeight(GenerationContext context, BlockPos position) {
        return context.chunkGenerator().getFirstFreeHeight(position.getX(), position.getZ(), Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.ARENA.get();
    }
}