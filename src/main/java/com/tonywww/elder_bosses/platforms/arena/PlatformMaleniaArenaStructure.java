package com.tonywww.elder_bosses.platforms.arena;

import com.mojang.serialization.Codec;
//? if neoforge {
/*import com.mojang.serialization.MapCodec;
*///?}
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tonywww.elder_bosses.arena.MaleniaArenaTerrain;
import com.tonywww.elder_bosses.platforms.registry.ModStructures;
import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;

public final class PlatformMaleniaArenaStructure extends Structure {
    //? if forge {
    public static final Codec<PlatformMaleniaArenaStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
    //?} else {
    /*public static final MapCodec<PlatformMaleniaArenaStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
    *///?}
            settingsCodec(instance), MaleniaArenaTerrain.CODEC.fieldOf("terrain").forGetter(s -> s.terrain)
    ).apply(instance, PlatformMaleniaArenaStructure::new));

    private final MaleniaArenaTerrain terrain;

    public PlatformMaleniaArenaStructure(StructureSettings settings, MaleniaArenaTerrain terrain) {
        super(settings);
        if(settings.step()!=GenerationStep.Decoration.UNDERGROUND_STRUCTURES || settings.terrainAdaptation()!=TerrainAdjustment.NONE) {
            throw new IllegalArgumentException("Malenia requires underground_structures and terrain_adaptation=none");
        }
        this.terrain=terrain;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var definition=PlatformMaleniaArenaWorldgen.definition(context.structureTemplateManager());
        if(definition.isEmpty())return Optional.empty();
        var center=new BlockPos(context.chunkPos().getMiddleBlockX(),0,context.chunkPos().getMiddleBlockZ());
        if(!allowedSurfaceBiome(context,center,surfaceHeight(context,center)))return Optional.empty();
        int first=Rotation.getRandom(context.random()).ordinal();
        for(int attempt=0;attempt<4;attempt++) {
            var result=candidate(context,definition.get(),center,Rotation.values()[(first+attempt)%4]);
            if(result.isPresent())return result;
        }
        return Optional.empty();
    }

    private Optional<GenerationStub> candidate(GenerationContext context, PlatformMaleniaArenaWorldgen.Definition definition,
                                               BlockPos horizontalOrigin, Rotation rotation) {
        var loaded=definition.loaded();var site=definition.site();
        BlockPos entry=horizontalOrigin.offset(site.entry().rotate(rotation));
        int entryHeight=surfaceHeight(context,entry);
        if(!allowedSurfaceBiome(context,entry,entryHeight))return Optional.empty();
        var samples=new ArrayList<MaleniaArenaTerrain.Sample>();
        var columns=new ArrayList<Column>();
        int minimum=entryHeight,maximum=entryHeight,entryMin=entryHeight,entryMax=entryHeight;
        int originY=entryHeight-site.entry().getY();
        for(var sample:site.samples()) {
            BlockPos position=horizontalOrigin.offset(sample.position().rotate(rotation));
            int surface=surfaceHeight(context,position);
            minimum=Math.min(minimum,surface);maximum=Math.max(maximum,surface);
            if((long)maximum-minimum>terrain.maxSurfaceHeightDifference())return Optional.empty();
            if(sample.entry()) {
                entryMin=Math.min(entryMin,surface);entryMax=Math.max(entryMax,surface);
                if((long)entryMax-entryMin>terrain.maxEntryHeightDifference())return Optional.empty();
            }
            if(sample.approach() && Math.abs((long)surface-entryHeight)>1)return Optional.empty();
            if(sample.roofY()<site.entry().getY() && surface<(long)originY+sample.roofY()+1+terrain.minimumRoofCover())return Optional.empty();
            columns.add(new Column(position,surface));
            samples.add(new MaleniaArenaTerrain.Sample(surface,false,true,sample.roofY(),sample.entry(),sample.approach()));
        }
        var height=terrain.placementHeight(entryHeight,site.entry().getY(),site.minY(),site.maxY(),
                context.heightAccessor().getMinBuildHeight(),context.heightAccessor().getMaxBuildHeight(),samples);
        if(height.isEmpty())return Optional.empty();
        // Full noise columns are considerably more expensive than surface heights.
        // Only inspect fluids/support after the complete height contract has passed.
        for(var sample:columns) {
            var position=sample.position();
            var column=context.chunkGenerator().getBaseColumn(position.getX(),position.getZ(),context.heightAccessor(),context.randomState());
            var ground=column.getBlock(sample.surfaceY()-1);
            if(!ground.getFluidState().isEmpty() || !ground.isFaceSturdy(EmptyBlockGetter.INSTANCE,position.atY(sample.surfaceY()-1),Direction.UP))return Optional.empty();
        }
        BlockPos origin=horizontalOrigin.atY(height.getAsInt());
        var metadata=loaded.layout().instanceMetadata(origin,rotation,loaded.hashes().get("worldgen_root"));
        var hashes=new net.minecraft.nbt.CompoundTag();loaded.hashes().forEach(hashes::putString);
        metadata.put("TemplateHashes",hashes);
        metadata.putBoolean("Natural",true);
        // Biome validation uses the surface doorway, not the cave biome at the boss floor.
        return Optional.of(new GenerationStub(entry.atY(entryHeight),builder -> {
            for(var part:loaded.layout().parts()) {
                var data=metadata.copy();data.putString("Part",part.name());
                builder.addPiece(new PlatformArenaPiece(ModStructures.MALENIA_ARENA_PIECE.get(),loaded.templates().get(part.name()),
                        part.placementOrigin(origin,rotation),rotation,data));
            }
        }));
    }

    private boolean allowedSurfaceBiome(GenerationContext context,BlockPos position,int surfaceY) {
        return biomes().contains(context.biomeSource().getNoiseBiome(QuartPos.fromBlock(position.getX()),QuartPos.fromBlock(surfaceY),
                QuartPos.fromBlock(position.getZ()),context.randomState().sampler()));
    }

    private static int surfaceHeight(GenerationContext context,BlockPos position) {
        return context.chunkGenerator().getFirstFreeHeight(position.getX(),position.getZ(),Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(),context.randomState());
    }

    @Override public StructureType<?> type() { return ModStructures.MALENIA_ARENA.get(); }

    private record Column(BlockPos position,int surfaceY) {}
}
