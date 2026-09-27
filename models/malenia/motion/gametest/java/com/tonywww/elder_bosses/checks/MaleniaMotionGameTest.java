package com.tonywww.elder_bosses.checks;

import com.tonywww.elder_bosses.boss.malenia.action.MaleniaActionCatalog;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.boss.malenia.execution.*;
import com.tonywww.elder_bosses.boss.malenia.runtime.MaleniaActionSnapshot;
import com.tonywww.elder_bosses.boss.malenia.server.MaleniaIntentExecutor;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;
import com.tonywww.elder_bosses.platforms.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import java.util.Optional;

@GameTestHolder("elder_bosses")
@PrefixGameTestTemplate(false)
public final class MaleniaMotionGameTest {
    @GameTest(template="malenia_arena_empty",timeoutTicks=270)
    public static void continuousAeoniaAndWaterfowl(GameTestHelper helper) {
        var level=helper.getLevel(); var floor=new BlockPos(128,100,128);
        for(int x=-14;x<=14;x++)for(int z=-14;z<=14;z++) {
            level.setBlock(floor.offset(x,-1,z),Blocks.STONE.defaultBlockState(),18);
            for(int y=0;y<12;y++)level.setBlock(floor.offset(x,y,z),Blocks.AIR.defaultBlockState(),18);
        }
        var start=new Vec3(128.5,100,128.5);
        var boss=ModEntities.MALENIA.get().create(level);
        boss.setPos(start);boss.setOnGround(true); // Driven exclusively by the real executor below.
        var target=EntityType.COW.create(level);target.setNoAi(true);target.setInvulnerable(true);
        target.setPos(start.add(7,0,0));level.addFreshEntity(target);
        var skills=ElderBossesCommonConfig.VALUES.maleniaSkillSnapshot();
        var catalog=new MaleniaActionCatalog(skills);
        var executor=new MaleniaIntentExecutor(new MaleniaIntentExecutor.GeometryDefaults(90,1));
        var aeonia=MaleniaSkillEventPlanner.createPlan(skills,MaleniaActionId.SCARLET_AEONIA);
        var water=MaleniaSkillEventPlanner.createPlan(skills,MaleniaActionId.WATERFOWL_DANCE);
        double ascent=aeonia.intents().stream().map(MaleniaActionPlan.ScheduledIntent::intent)
                .filter(i->i instanceof MaleniaServerIntent.MoveVertical)
                .mapToDouble(i->((MaleniaServerIntent.MoveVertical)i).maxTravel()).findFirst().orElseThrow();
        double[] firstDiveStep={0},midDiveStep={0},waterTravel={0};
        for(int t=0;t<=86;t++) {
            final int tick=t;
            helper.runAtTickTime(t+1,()->{
                Vec3 before=boss.position();
                var timeline=catalog.get(MaleniaActionId.SCARLET_AEONIA).timeline();
                var action=new MaleniaActionSnapshot(MaleniaActionId.SCARLET_AEONIA,1,level.getGameTime()-tick,1,
                        Optional.of(target.getUUID()),tick,timeline.windowAt(tick));
                executor.tick(boss,action,aeonia.intentsAt(tick));
                double step=before.distanceTo(boss.position());
                check(step<=.951,"Aeonia jumped more than one controlled step: "+step);
                if(tick==25)check(Math.abs(boss.getY()-100-ascent)<.01,"Takeoff height mismatch: "+boss.getY());
                if(tick==43)firstDiveStep[0]=step;
                if(tick==51)midDiveStep[0]=step;
                if(tick==61) {
                    check(midDiveStep[0]>firstDiveStep[0]*2,"Dive has no acceleration");
                    check(Math.abs(boss.getY()-100)<.05,"Dive did not reach floor");
                    check(executor.lockedPoints().get("aeonia_impact").distanceTo(boss.position())<.001,"Bloom separated from impact");
                }
                if(tick==86)check(executor.persistentZones().get(0).center().distanceTo(boss.position())<.01,"Rot zone shifted after landing");
            });
        }
        helper.runAtTickTime(95,()->{executor.clear();boss.setPos(start);boss.setDeltaMovement(Vec3.ZERO);target.setPos(start.add(4,0,0));});
        for(int t=0;t<water.totalTicks();t++) {
            final int tick=t;
            helper.runAtTickTime(100+t,()->{
                Vec3 before=boss.position();var timeline=catalog.get(MaleniaActionId.WATERFOWL_DANCE).timeline();
                var action=new MaleniaActionSnapshot(MaleniaActionId.WATERFOWL_DANCE,2,level.getGameTime()-tick,2,
                        Optional.of(target.getUUID()),tick,timeline.windowAt(tick));
                executor.tick(boss,action,water.intentsAt(tick));
                double step=before.distanceTo(boss.position());check(step<=1.801,"Waterfowl teleported");
                boolean rush=tick>=32&&tick<50||tick>=62&&tick<74||tick>=82&&tick<102;
                if(rush)waterTravel[0]+=step;
                if(tick>=32&&!rush)check(step<.001,"Waterfowl moved through a pause/terminal flurry");
                if(tick==water.totalTicks()-1){check(waterTravel[0]>10,"Later rushes stalled at target");
                    executor.clear();target.discard();boss.discard();helper.succeed();}
            });
        }
    }
    private static void check(boolean value,String message){if(!value)throw new GameTestAssertException(message);}
}
