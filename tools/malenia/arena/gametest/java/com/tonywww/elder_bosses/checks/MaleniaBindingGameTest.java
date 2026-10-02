package com.tonywww.elder_bosses.checks;

import com.mojang.authlib.GameProfile;
import com.tonywww.elder_bosses.arena.MaleniaArenaBinding;
import com.tonywww.elder_bosses.boss.malenia.MaleniaEntity;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaCombatState;
import com.tonywww.elder_bosses.boss.malenia.execution.MaleniaServerIntent;
import com.tonywww.elder_bosses.boss.malenia.runtime.MaleniaActionSnapshot;
import com.tonywww.elder_bosses.boss.malenia.server.MaleniaIntentExecutor;
import com.tonywww.elder_bosses.combat.action.ActionPhase;
import com.tonywww.elder_bosses.combat.action.ActionWindow;
import com.tonywww.elder_bosses.platforms.arena.*;
import com.tonywww.elder_bosses.platforms.registry.ModBlocks;
import com.tonywww.elder_bosses.platforms.registry.ModEntities;
import com.tonywww.elder_bosses.platforms.registry.ModItems;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("elder_bosses")
@PrefixGameTestTemplate(false)
public final class MaleniaBindingGameTest {
    @GameTest(template="malenia_arena_empty",timeoutTicks=1200)
    public static void summonAndBinding(GameTestHelper helper) throws Exception {
        var level=helper.getLevel();
        BlockPos origin=new BlockPos(512,80,512);
        for(int x=29;x<=35;x++)for(int z=29;z<=35;z++)level.getChunk(x,z);
        try { PlatformMaleniaArenaPlacement.undo(level,origin); }
        catch(java.nio.file.NoSuchFileException firstRun) { }
        var metadata=PlatformMaleniaArenaPlacement.build(level,origin,Rotation.COUNTERCLOCKWISE_90);
        var binding=new MaleniaArenaBinding(metadata);
        check(level.getBlockState(binding.altar()).is(ModBlocks.HALIGTREE_ALTAR.get()),"Prayer tile absent from manual NBT");
        try { verifySummoning(level,binding,true); }
        finally { PlatformMaleniaArenaPlacement.undo(level,origin); }
        System.out.println("Malenia binding: offerings, rejected/cancelled spawns, ownership, bounds, underground landing, reset and reload passed");
        helper.succeed();
    }

    static void verifySummoning(ServerLevel level,MaleniaArenaBinding binding,boolean full) throws Exception {
        var altar=binding.altar();
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"MaleniaTest"));
        player.moveTo(altar.getX()+.5,altar.getY()+1,altar.getZ()+.5,0,0);
        player.getAbilities().instabuild=false;
        player.setShiftKeyDown(true);
        var offering=new ItemStack(ModItems.RUNE_FRAGMENT.get(),4);
        player.setItemInHand(InteractionHand.MAIN_HAND,offering);
        check(PlatformMaleniaArenaSummoning.findBinding(level,altar).save().equals(binding.save()),"Designated altar did not resolve binding");
        check(PlatformMaleniaArenaSummoning.summon(player,altar.offset(1,0,0))==PlatformMaleniaArenaSummoning.Result.INVALID,"Copied altar position accepted");
        player.setShiftKeyDown(false);
        check(PlatformMaleniaArenaSummoning.summon(player,altar)==PlatformMaleniaArenaSummoning.Result.INVALID,"Sneak contract ignored");
        player.setShiftKeyDown(true);
        var spawn=binding.anchor("boss_spawn");
        var previous=level.getBlockState(spawn);
        level.setBlock(spawn,Blocks.STONE.defaultBlockState(),18);
        check(PlatformMaleniaArenaSummoning.summon(player,altar)==PlatformMaleniaArenaSummoning.Result.BLOCKED,"Obstructed spawn accepted");
        level.setBlock(spawn,previous,18);
        check(offering.getCount()==4,"Failed summon consumed offering");
        java.util.function.Consumer<EntityJoinLevelEvent> cancel=event -> {
            if(event.getEntity() instanceof MaleniaEntity && event.getLevel()==level)event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(cancel);
        try {
            check(PlatformMaleniaArenaSummoning.summon(player,altar)==PlatformMaleniaArenaSummoning.Result.FAILED,"Cancelled entity insertion reported success");
        } finally { MinecraftForge.EVENT_BUS.unregister(cancel); }
        var instances=PlatformMaleniaArenaSavedData.get(level);
        check(!instances.occupied(binding.origin()) && offering.getCount()==4,"Failed insertion leaked ownership or offering");
        check(PlatformMaleniaArenaSummoning.summon(player,altar)==PlatformMaleniaArenaSummoning.Result.SUMMONED,"Summon failed");
        check(offering.getCount()==3,"Success did not consume exactly one offering");
        var boss=level.getEntitiesOfClass(MaleniaEntity.class,new net.minecraft.world.phys.AABB(spawn).inflate(3)).stream().findFirst().orElseThrow();
        check(boss.arenaBinding().orElseThrow().save().equals(binding.save()),"Wrong entity binding");
        check(boss.position().equals(binding.standingAnchor("boss_spawn")),"Spawn ignored rotation or authored anchor");
        check(PlatformMaleniaArenaSummoning.summon(player,altar)==PlatformMaleniaArenaSummoning.Result.OCCUPIED && offering.getCount()==3,"Duplicate summon consumed an offering");
        try {
            if(full) {
                try { PlatformMaleniaArenaPlacement.undo(level,binding.origin()); throw new AssertionError("Occupied arena allowed undo"); }
                catch(java.io.IOException expected) { check(expected.getMessage().contains("bound Malenia"),"Wrong undo rejection"); }
                verifyCombat(level,binding,boss);
            }
            var saved=instances.write(new CompoundTag());
            var restoredInstances=PlatformMaleniaArenaSavedData.load(saved);
            check(restoredInstances.occupied(binding.origin()),"Saved ownership missing");
            check(!restoredInstances.claim(binding,UUID.randomUUID()),"Saved arena accepts another UUID");
            check(restoredInstances.claim(binding,boss.getUUID()),"Saved owner cannot reclaim");
            var entityData=boss.saveWithoutId(new CompoundTag());
            var duplicate=ModEntities.MALENIA.get().create(level);
            duplicate.load(entityData);
            duplicate.setUUID(UUID.randomUUID());
            duplicate.tick();
            check(duplicate.isRemoved() && instances.occupied(binding.origin()),"Duplicate load displaced real owner");
            boss.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            check(instances.occupied(binding.origin()),"Chunk unload freed ownership");
            var restored=ModEntities.MALENIA.get().create(level);
            restored.load(entityData);
            restored.tick();
            check(!restored.isRemoved() && restored.arenaBinding().orElseThrow().save().equals(binding.save()),"Owner did not survive reload");
            check(restored.combatState()==MaleniaCombatState.DORMANT,"Default unload policy did not reset fight");
            restored.discard();
            check(!instances.occupied(binding.origin()),"Destruction did not free ownership");
            if(full) {
                var hit=new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(altar),net.minecraft.core.Direction.UP,altar,false);
                var offhand=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,InteractionHand.OFF_HAND,altar,hit);
                MinecraftForge.EVENT_BUS.post(offhand);
                check(!instances.occupied(binding.origin()),"Offhand event summoned another boss");
                player.getAbilities().instabuild=true;
                var interaction=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,altar,hit);
                MinecraftForge.EVENT_BUS.post(interaction);
                check(interaction.isCanceled() && instances.occupied(binding.origin()) && offering.getCount()==3,"Creative right-click ritual failed or consumed offering");
                var rematch=level.getEntitiesOfClass(MaleniaEntity.class,new net.minecraft.world.phys.AABB(spawn).inflate(3)).stream().findFirst().orElseThrow();
                var lootRule=level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT);
                boolean oldLoot=lootRule.get();
                lootRule.set(false,level.getServer());
                try {
                    rematch.beginEncounter(1);
                    rematch.transitionTo(MaleniaCombatState.PHASE_1);
                    rematch.kill();
                    for(int i=0;i<71;i++)rematch.tick();
                    rematch.transitionTo(MaleniaCombatState.PHASE_2);
                    rematch.kill();
                    check(rematch.combatState()==MaleniaCombatState.DEFEATED && instances.occupied(binding.origin()),"Death sequence freed the arena too early");
                    for(int i=0;i<200 && !rematch.isRemoved();i++)rematch.tick();
                    check(rematch.isRemoved() && !instances.occupied(binding.origin()),"Completed defeat retained ownership");
                } finally { lootRule.set(oldLoot,level.getServer());rematch.discard(); }
            }
        } finally { boss.discard(); }
    }

    private static void verifyCombat(ServerLevel level,MaleniaArenaBinding binding,MaleniaEntity boss) {
        var center=binding.standingAnchor("arena_center");
        check(!boss.insideBoundArena(center.add(0,58,0)),"Surface player included in chamber");
        check(!boss.insideBoundArena(center.add(30,0,0)),"Outside player included in chamber");
        var cow=EntityType.COW.create(level);
        cow.moveTo(center.x+3,center.y,center.z,0,0);
        level.addFreshEntity(cow);
        try {
            check(boss.visibleEligibleTargets().contains(cow),"Inside target excluded");
            // This roof distinguishes chamber floor projection from a global heightmap.
            var roof=BlockPos.containing(cow.position()).above(25);
            var before=level.getBlockState(roof);
            level.setBlock(roof,Blocks.STONE.defaultBlockState(),18);
            var executor=new MaleniaIntentExecutor(new MaleniaIntentExecutor.GeometryDefaults(90,1));
            var snapshot=new MaleniaActionSnapshot(MaleniaActionId.SCARLET_AEONIA,1,level.getGameTime(),1,Optional.of(cow.getUUID()),0,new ActionWindow(ActionPhase.WINDUP,0,10,0));
            executor.tick(boss,snapshot,List.of(new MaleniaServerIntent.LockPoint("landing",true)));
            check(executor.lockedPoints().get("landing").y==center.y-.5,"Aeonia missed the half-height pool floor");
            executor.clear();
            level.setBlock(roof,before,18);
            cow.setPos(center.add(30,0,0));
            check(!boss.visibleEligibleTargets().contains(cow),"Outside target eligible");
        } finally { cow.discard(); }
        boss.setPos(center.add(24,0,0));
        boss.move(MoverType.SELF,new Vec3(8,0,0));
        check(boss.insideBoundArena(boss.position()),"Controlled movement escaped arena");
        check(boss.beginEncounter(1),"Bound encounter did not start");
        boss.transitionTo(MaleniaCombatState.PHASE_1);
        boss.kill(); // Deplete the first health pool before the phase transition.
        for(int i=0;i<71;i++)boss.tick();
        var takeoff=boss.position();
        check(boss.transitionTo(MaleniaCombatState.AEONIA_OPENING),"Opening transition failed");
        check(boss.position().equals(takeoff),"Phase opening teleported instead of using flight intents");
        for(int i=0;i<101;i++)boss.tick();
        check(boss.combatState()==MaleniaCombatState.DORMANT,"Empty arena did not reset after grace period");
        check(boss.position().equals(binding.standingAnchor("boss_spawn")),"Reset did not return to root seat");
    }

    private static void check(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}
