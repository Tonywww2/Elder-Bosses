package com.tonywww.elder_bosses.checks;

import com.tonywww.elder_bosses.arena.MaleniaArenaTerrain;
import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import com.tonywww.elder_bosses.platforms.arena.PlatformArenaPiece;
import com.tonywww.elder_bosses.platforms.arena.PlatformMaleniaArenaWorldgen;
import com.tonywww.elder_bosses.platforms.registry.ModBlocks;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("elder_bosses")
@PrefixGameTestTemplate(false)
public final class MaleniaWorldgenGameTest {
    @GameTest(template="malenia_arena_empty",timeoutTicks=1200)
    public static void forestGeneration(GameTestHelper helper) throws Exception {
        terrainRules();
        var server=helper.getLevel().getServer();
        ServerLevel forest=server.getLevel(ResourceKey.create(Registries.DIMENSION,PlatformResourceLocation.id("malenia_forest_check")));
        ServerLevel plains=server.getLevel(ResourceKey.create(Registries.DIMENSION,PlatformResourceLocation.id("malenia_plains_check")));
        check(forest!=null && plains!=null,"Isolated worldgen dimensions missing");
        var registry=forest.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var holder=registry.getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE,PlatformResourceLocation.id("malenia_arena")));
        Structure arena=holder.value();
        check(arena.biomes().size()==1,"Biome tag must contain dark forest only by default");
        check(arena.biomes().stream().allMatch(b -> b.is(net.minecraft.world.level.biome.Biomes.DARK_FOREST)),"Unexpected natural biome");
        var state=forest.getChunkSource().getGeneratorState();
        var placements=state.getPlacementsForStructure(holder);
        check(placements.size()==1 && placements.get(0) instanceof RandomSpreadStructurePlacement,"Random spread placement missing");
        var placement=(RandomSpreadStructurePlacement)placements.get(0);
        check(placement.spacing()==48 && placement.separation()==16,"Distribution configuration not loaded");
        int accepted=0,rejected=0;
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++) {
            var p=placement.getPotentialStructureChunk(forest.getSeed(),x*48,z*48);
            if(placement.isStructureChunk(state,p.x,p.z))accepted++;else rejected++;
        }
        check(accepted>80 && rejected>80,"Frequency is not reducing candidate count");
        System.out.println("Malenia candidate frequency: accepted="+accepted+", rejected="+rejected+" / 289 cells");
        var definition=PlatformMaleniaArenaWorldgen.definition(server.getStructureManager()).orElseThrow();
        check(definition.loaded().templates().size()==19,"Underground entrance parts missing");
        StructureStart found=null;
        for(int ring=0;ring<6 && found==null;ring++)for(int x=-ring;x<=ring && found==null;x++)for(int z=-ring;z<=ring && found==null;z++) {
            if(Math.max(Math.abs(x),Math.abs(z))!=ring)continue;
            var chunk=placement.getPotentialStructureChunk(forest.getSeed(),x*48,z*48);
            if(!placement.isStructureChunk(state,chunk.x,chunk.z))continue;
            var context=context(forest,arena,chunk);
            var point=arena.findValidGenerationPoint(context);
            if(point.isEmpty())continue;
            System.out.println("Malenia terrain approved candidate "+chunk+"; generating real chunks");
            check(arena.findValidGenerationPoint(context(plains,arena,chunk)).isEmpty(),"Same terrain incorrectly allowed in plains");
            check(arena.findValidGenerationPoint(context(forest,arena,chunk)).map(Structure.GenerationStub::position).equals(point.map(Structure.GenerationStub::position)),"Seeded location is not deterministic");
            // Let vanilla chunk generation create the start. Do not call /place or build().
            var actual=forest.getChunk(chunk.x,chunk.z).getStartForStructure(arena);
            check(actual!=null && actual.isValid(),"Approved random-spread candidate did not naturally create a start");
            found=actual;
        }
        check(found!=null,"No accepted underground arena in bounded noise-terrain search");
        var start=found;
        check(start.getPieces().size()==19,"Incomplete natural structure start");
        var bounds=start.getBoundingBox();
        for(int x=bounds.minX()>>4;x<=bounds.maxX()>>4;x++)for(int z=bounds.minZ()>>4;z<=bounds.maxZ()>>4;z++)forest.getChunk(x,z);
        var metadata=((PlatformArenaPiece)start.getPieces().get(0)).arenaMetadata();
        BlockPos origin=BlockPos.of(metadata.getLong("Origin"));Rotation rotation=Rotation.valueOf(metadata.getString("Rotation"));
        BlockPos entry=anchor(metadata,"surface_entry",origin,rotation),inside=anchor(metadata,"player_entry",origin,rotation);
        check(com.tonywww.elder_bosses.arena.MaleniaArenaFloor.isPoolSlab(forest.getBlockState(origin)),"Natural waterlogged pool did not place");
        check(forest.getBlockState(origin.above()).isAir(),"Natural legacy film remains");
        check(entry.getY()-origin.getY()==58,"Chamber buried at the wrong depth");
        for(int y=0;y<3;y++)check(forest.getBlockState(entry.above(y)).getCollisionShape(forest,entry.above(y)).isEmpty(),"Surface doorway buried or blocked");
        // Use a player-sized mob with a wider search budget for all eight return flights.
        var walker=EntityType.ZOMBIE.create(forest);
        walker.moveTo(entry.getX()+.5,entry.getY(),entry.getZ()+.5,0,0);walker.setOnGround(true);
        walker.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).setBaseValue(256);
        walker.getNavigation().setMaxVisitedNodesMultiplier(16);
        var down=walker.getNavigation().createPath(inside,0);
        System.out.println("Malenia stairs down: "+down+" target="+inside+" entry="+entry+" end="+(down==null?null:down.getEndNode()));
        check(down!=null && down.canReach(),"Surface stairs do not reach the chamber");
        walker.moveTo(inside.getX()+.5,inside.getY(),inside.getZ()+.5,0,0);walker.setOnGround(true);
        var up=walker.getNavigation().createPath(entry,0);
        check(up!=null && up.canReach(),"Chamber stairs do not reach the surface");
        var serialization=StructurePieceSerializationContext.fromLevel(forest);
        var encoded=start.createTag(serialization,start.getChunkPos());
        var restored=StructureStart.loadStaticStart(serialization,encoded,forest.getSeed());
        check(restored!=null && restored.getPieces().size()==19,"Natural pieces failed save/reload");
        for(var piece:restored.getPieces())check(((PlatformArenaPiece)piece).arenaMetadata().getBoolean("Natural"),"Natural metadata lost on reload");
        MaleniaBindingGameTest.verifySummoning(forest,new com.tonywww.elder_bosses.arena.MaleniaArenaBinding(metadata),false);
        System.out.println("Malenia natural worldgen passed: origin="+origin.toShortString()+" entry="+entry.toShortString()+" rotation="+rotation+" parts=19; real noise chunks, biome rejection, stairs both ways and saved pieces");
        helper.succeed();
    }

    private static Structure.GenerationContext context(ServerLevel level,Structure arena,net.minecraft.world.level.ChunkPos chunk) {
        var generator=level.getChunkSource().getGenerator();
        return new Structure.GenerationContext(level.registryAccess(),generator,generator.getBiomeSource(),level.getChunkSource().randomState(),
                level.getServer().getStructureManager(),level.getSeed(),chunk,level,arena.biomes()::contains);
    }
    private static BlockPos anchor(net.minecraft.nbt.CompoundTag tag,String name,BlockPos origin,Rotation rotation) {
        return origin.offset(BlockPos.of(tag.getCompound("Anchors").getLong(name)).rotate(rotation));
    }
    private static void terrainRules() {
        var rules=new MaleniaArenaTerrain(3,16,2);
        var good=new MaleniaArenaTerrain.Sample(64,false,true,37,false,false);
        var door=new MaleniaArenaTerrain.Sample(64,false,true,50,true,false);
        var approach=new MaleniaArenaTerrain.Sample(64,false,true,44,true,true);
        check(rules.placementHeight(64,44,-4,50,-64,320,List.of(good,door,approach)).equals(java.util.OptionalInt.of(20)),"Wrong burial depth");
        for(var bad:List.of(new MaleniaArenaTerrain.Sample(59,false,true,37,false,false),new MaleniaArenaTerrain.Sample(64,true,true,50,true,false),
                new MaleniaArenaTerrain.Sample(64,false,false,37,false,false),new MaleniaArenaTerrain.Sample(82,false,true,37,false,false),
                new MaleniaArenaTerrain.Sample(66,false,true,44,true,true))) {
            check(rules.placementHeight(64,44,-4,50,-64,320,List.of(good,door,approach,bad)).isEmpty(),"Unsafe terrain accepted");
        }
        check(rules.placementHeight(-30,44,-4,50,-64,320,List.of(good,door,approach)).isEmpty(),"World-bottom overflow accepted");
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
