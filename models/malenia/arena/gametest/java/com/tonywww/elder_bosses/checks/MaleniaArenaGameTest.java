package com.tonywww.elder_bosses.checks;

import com.tonywww.elder_bosses.platforms.arena.PlatformMaleniaArenaPlacement;
import com.tonywww.elder_bosses.arena.MaleniaArenaFloor;
import com.tonywww.elder_bosses.platforms.arena.PlatformMaleniaArenaTemplates;
import com.tonywww.elder_bosses.platforms.registry.ModBlocks;
import com.tonywww.elder_bosses.platforms.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("elder_bosses")
@PrefixGameTestTemplate(false)
public final class MaleniaArenaGameTest {
    @GameTest(template="malenia_arena_empty",timeoutTicks=400)
    public static void placementAndRecovery(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var loaded = PlatformMaleniaArenaTemplates.load(level.getServer().getResourceManager(),
                level.registryAccess().registryOrThrow(Registries.BLOCK));
        check(loaded.templates().size()==18,"Authored arena resources missing");
        BlockPos origin = new BlockPos(256,80,256);
        // Reserve a distant empty volume in this temporary flat GameTest world.
        for (int x=12;x<=20;x++) for(int z=12;z<=20;z++) level.getChunk(x,z);
        try { PlatformMaleniaArenaPlacement.undo(level,origin); }
        catch (java.nio.file.NoSuchFileException firstRun) { /* No prior failed run to recover. */ }
        for (Rotation rotation : Rotation.values()) {
            BlockPos sentinel=origin.offset(0,-2,0);
            level.setBlock(sentinel,Blocks.DIAMOND_BLOCK.defaultBlockState(),18);
            var metadata=PlatformMaleniaArenaPlacement.build(level,origin,rotation);
            check(metadata.getString("Rotation").equals(rotation.name()),"Rotation metadata changed");
            BlockPos center=PlatformMaleniaArenaPlacement.anchor(level,origin,"arena_center");
            check(center.equals(origin.above()),"Center marker drifted");
            check(level.getBlockState(center).isAir(),"Legacy surface remains over the pool");
            check(MaleniaArenaFloor.isPoolSlab(level.getBlockState(center.below())),"Missing waterlogged bottom slab");
            check(level.getFluidState(center.below()).isSource(),"Pool is not real source water");
            check(level.getBlockState(center.below()).getCollisionShape(level,center.below()).max(net.minecraft.core.Direction.Axis.Y)==.5,"Pool floor is not half-height");
            for(int x=-18;x<=18;x++) for(int z=-18;z<=18;z++) {
                if(x*x+z*z>18*18)continue;
                BlockPos foot=origin.offset(x,1,z);
                check(MaleniaArenaFloor.top(level.getBlockState(foot.below()),level,foot.below())>0,"Hole in core floor");
                for(int y=0;y<18;y++) check(level.getBlockState(foot.above(y)).getCollisionShape(level,foot.above(y)).isEmpty(),"Blocked flight clearance");
            }
            var boss=ModEntities.MALENIA.get().create(level);
            boss.bindArena(new com.tonywww.elder_bosses.arena.MaleniaArenaBinding(metadata));
            BlockPos spawn=PlatformMaleniaArenaPlacement.anchor(level,origin,"boss_spawn");
            boss.moveTo(spawn.getX()+.5,spawn.getY(),spawn.getZ()+.5,0,0);
            boss.setOnGround(true);
            var path=boss.getNavigation().createPath(center,0);
            if(path==null || !path.canReach()) {
                System.out.println("Pool navigation diagnostic: "+path+" end="+(path==null?null:path.getEndNode())+" target="+center);
                for(int z=-12;z<=2;z++) {
                    var pos=origin.offset(new BlockPos(0,1,z).rotate(rotation));
                    var type=net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getBlockPathTypeStatic(level,pos.mutable());
                    System.out.println("Pool node "+pos+" type="+type+" cost="+boss.getPathfindingMalus(type)+" below="+level.getBlockState(pos.below())+" floor="+net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getFloorLevel(level,pos));
                }
            }
            check(path!=null && path.canReach(),"Malenia cannot navigate through flowers to water");
            boss.moveTo(center.getX()+.5,center.getY()-.5,center.getZ()+.5,0,0);
            boss.setOnGround(true);
            var out=boss.getNavigation().createPath(spawn,0);
            check(out!=null && out.canReach(),"Malenia cannot navigate out of the half-depth pool");
            boss.discard();
            // Exercise the real fluid engine, including irregular shores and piece seams.
            for(int tick=0;tick<3;tick++) for(var entry:loaded.composition().entrySet()) {
                if(!MaleniaArenaFloor.isPoolSlab(entry.getValue()))continue;
                BlockPos wet=origin.offset(entry.getKey().rotate(rotation));
                level.getFluidState(wet).tick(level,wet);
            }
            for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)for(int y=-1;y<=1;y++) {
                BlockPos local=new BlockPos(x,y,z), actual=origin.offset(local.rotate(rotation));
                check(level.getBlockState(actual).equals(loaded.composition().get(local)),"Pool flow changed the authored shore or floor");
            }
            // Overlap and post-build edits must reject without consuming the recovery journal.
            rejected(() -> PlatformMaleniaArenaPlacement.build(level,origin,rotation),"overlap");
            BlockPos edited=origin.offset(2,1,2);
            var saved=level.getBlockState(edited);
            level.setBlock(edited,Blocks.GOLD_BLOCK.defaultBlockState(),18);
            rejected(() -> PlatformMaleniaArenaPlacement.undo(level,origin),"later edit");
            level.setBlock(edited,saved,18);
            PlatformMaleniaArenaPlacement.undo(level,origin);
            check(level.getBlockState(sentinel).is(Blocks.DIAMOND_BLOCK),"Undo lost original block");
            check(level.getBlockState(center).isAir(),"Undo retained the floor decoration");
            level.setBlock(sentinel,Blocks.AIR.defaultBlockState(),18);
        }
        BlockPos container=origin;
        level.setBlock(container,Blocks.CHEST.defaultBlockState(),18);
        rejected(() -> PlatformMaleniaArenaPlacement.build(level,origin,Rotation.NONE),"container");
        check(level.getBlockState(container).is(Blocks.CHEST),"Rejected placement overwrote container");
        level.setBlock(container,Blocks.WATER.defaultBlockState(),18);
        rejected(() -> PlatformMaleniaArenaPlacement.build(level,origin,Rotation.NONE),"fluid");
        level.setBlock(container,Blocks.AIR.defaultBlockState(),18);
        System.out.println("Malenia arena runtime: four rotations, waterlogged half-height pool, real fluid ticks/containment, clearance, navigation, overlap, edit conflicts and durable undo passed");
        helper.succeed();
    }
    private interface Operation { void run() throws Exception; }
    private static void rejected(Operation action,String message) throws Exception {
        try { action.run(); } catch (java.io.IOException expected) { return; }
        throw new AssertionError("Expected rejection: "+message);
    }
    private static void check(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}
