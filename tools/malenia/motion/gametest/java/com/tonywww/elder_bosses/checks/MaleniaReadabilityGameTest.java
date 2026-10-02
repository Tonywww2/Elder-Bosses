package com.tonywww.elder_bosses.checks;

import com.mojang.authlib.GameProfile;
import com.tonywww.elder_bosses.boss.malenia.MaleniaEntity;
import com.tonywww.elder_bosses.boss.malenia.action.MaleniaActionCatalog;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.boss.malenia.execution.*;
import com.tonywww.elder_bosses.boss.malenia.runtime.MaleniaActionSnapshot;
import com.tonywww.elder_bosses.boss.malenia.server.MaleniaIntentExecutor;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;
import com.tonywww.elder_bosses.platforms.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("elder_bosses")
@PrefixGameTestTemplate(false)
public final class MaleniaReadabilityGameTest {
    @GameTest(template="malenia_arena_empty", timeoutTicks=70)
    public static void shieldGrabLiftRelease(GameTestHelper helper) {
        var level=helper.getLevel(); Vec3 start=new Vec3(80.5,100,128.5); floor(level,start);
        var boss=ModEntities.MALENIA.get().create(level); boss.setPos(start); boss.setYRot(0);
        var player=new ShieldPlayer(level); player.setPos(start.add(0,0,1.5)); player.setYRot(180);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); player.setHealth(200);
        level.addNewPlayer(player); player.raiseShield(20);
        var skills=ElderBossesCommonConfig.VALUES.maleniaSkillSnapshot();
        var plan=MaleniaSkillEventPlanner.createPlan(skills,MaleniaActionId.GRAB_IMPALE);
        var timeline=new MaleniaActionCatalog(skills).get(MaleniaActionId.GRAB_IMPALE).timeline();
        var scheduled=plan.intents().stream().filter(i->i.intent() instanceof MaleniaServerIntent.Grab).findFirst().orElseThrow();
        var grab=(MaleniaServerIntent.Grab)scheduled.intent();
        check(grab.grabHit().instantGuardEligible(),"Grab cannot be parried");
        var executor=new MaleniaIntentExecutor(new MaleniaIntentExecutor.GeometryDefaults(90,1));
        double[] healthAfterGrab={0};
        for(int age=0;age<=grab.throwDelayTicks()+1;age++) {
            final int t=age;
            helper.runAtTickTime(age+31,()->{
                int actionTick=scheduled.actionTick()+t;
                var action=new MaleniaActionSnapshot(MaleniaActionId.GRAB_IMPALE,1,level.getGameTime()-actionTick,1,
                        Optional.of(player.getUUID()),actionTick,timeline.windowAt(actionTick));
                if(t==0)check(player.isBlocking(),"Fixture did not hold a normal shield");
                executor.tick(boss,action,t==0?List.of(grab):List.of());
                if(t==0) { check(!player.isUsingItem(),"Grab did not break sustained guard");
                    check(player.isNoGravity(),"Normal shield prevented capture"); healthAfterGrab[0]=player.getHealth(); }
                if(t==10) { check(player.getY()>start.y+1.8,"Victim was not lifted into the air: "+player.getY());
                    check(player.isNoGravity(),"Grab lost gravity control"); }
                if(t==grab.throwDelayTicks()+1) {
                    check(!player.isNoGravity(),"Throw left gravity disabled");
                    check(player.getHealth()<healthAfterGrab[0],"Impale and throw did not damage victim");
                    executor.clear(); player.setPos(start.add(0,0,1.5)); player.raiseShield(0); float before=player.getHealth();
                    var parry=new MaleniaIntentExecutor(new MaleniaIntentExecutor.GeometryDefaults(90,1),
                            (p,hit,damage)->Optional.of(new MaleniaIntentExecutor.InstantGuardResult(0,ignored->{})));
                    var guarded=new MaleniaActionSnapshot(MaleniaActionId.GRAB_IMPALE,2,level.getGameTime()-scheduled.actionTick(),2,
                            Optional.of(player.getUUID()),scheduled.actionTick(),timeline.windowAt(scheduled.actionTick()));
                    parry.tick(boss,guarded,List.of(grab));
                    check(player.getHealth()==before&&!player.isNoGravity()&&player.isUsingItem(),"Parried grab captured the victim");
                    parry.clear(); player.discard(); boss.discard(); helper.succeed();
                }
            });
        }
    }

    @GameTest(template="malenia_arena_empty", timeoutTicks=245)
    public static void dashDistancePhantomSpeedAndIdleDrift(GameTestHelper helper) {
        var level=helper.getLevel(); Vec3 start=new Vec3(80.5,100,96.5); floor(level,start);
        var boss=ModEntities.MALENIA.get().create(level); boss.setPos(start); boss.setOnGround(true);
        for(int i=0;i<6;i++) { boss.setDeltaMovement(.8,0,.4); boss.travel(Vec3.ZERO); }
        check(boss.position().subtract(start).horizontalDistance()<.001,"Idle physics drifted horizontally");
        var target=EntityType.COW.create(level); target.setNoAi(true);target.setInvulnerable(true);target.setPos(start.add(18,0,0));level.addFreshEntity(target);
        var skills=ElderBossesCommonConfig.VALUES.maleniaSkillSnapshot();var catalog=new MaleniaActionCatalog(skills);
        var executor=new MaleniaIntentExecutor(new MaleniaIntentExecutor.GeometryDefaults(90,1));
        int base=1;
        for(var id:List.of(MaleniaActionId.RUNNING_SLASH,MaleniaActionId.THRUST,MaleniaActionId.SCARLET_PHANTOMS)) {
            var plan=MaleniaSkillEventPlanner.createPlan(skills,id);var timeline=catalog.get(id).timeline();
            long sequence=base; double[] diveTravel={0}; int[] diveTicks={0};
            helper.runAtTickTime(base,()->{executor.clear();boss.setPos(start);boss.setDeltaMovement(Vec3.ZERO);
                boss.setYRot(-90);target.setPos(start.add(18,0,0));});
            for(int t=0;t<plan.totalTicks();t++) {
                final int tick=t;
                helper.runAtTickTime(base+t+1,()->{
                    var action=new MaleniaActionSnapshot(id,sequence,level.getGameTime()-tick,1,Optional.of(target.getUUID()),tick,timeline.windowAt(tick));
                    Vec3 before=boss.position();executor.tick(boss,action,plan.intentsAt(tick));
                    double step=before.distanceTo(boss.position());check(step<=3.601,"Unbounded dash step: "+id+" "+step);
                    if(id==MaleniaActionId.SCARLET_PHANTOMS&&plan.intentsAt(tick).stream().anyMatch(i->i instanceof MaleniaServerIntent.MoveToward)) {
                        diveTravel[0]+=step; diveTicks[0]++;
                    }
                    if(tick==plan.totalTicks()-1) {
                        check(boss.position().subtract(start).horizontalDistance()>10,"Dash range was not extended: "+id+" distance="+boss.position().subtract(start).horizontalDistance()+" locks="+executor.lockedPoints());
                        if(id==MaleniaActionId.SCARLET_PHANTOMS) {
                            check(diveTicks[0]==12,"Body dive is not 12 ticks");
                            check(diveTravel[0]/diveTicks[0]>1.3,"Body dive still moves too slowly");
                        }
                    }
                });
            }
            base+=plan.totalTicks()+3;
        }
        helper.runAtTickTime(base,()->{executor.clear();target.discard();boss.discard();helper.succeed();});
    }

    private static void floor(ServerLevel level,Vec3 start) {
        var pos=BlockPos.containing(start);
        for(int x=-3;x<=24;x++)for(int z=-4;z<=4;z++) {
            level.setBlock(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState(),18);
            for(int y=0;y<=12;y++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),18);
        }
    }
    private static void check(boolean value,String message){if(!value)throw new GameTestAssertException(message);}
    private static final class ShieldPlayer extends FakePlayer {
        ShieldPlayer(ServerLevel level){
            super(level,new GameProfile(UUID.randomUUID(),"MaleniaGrabTest"));
            // FakePlayer never ticks ServerPlayer's 60-tick spawn protection down.
            try {
                var protection=net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
                protection.setAccessible(true);protection.setInt(this,0);
            }catch(ReflectiveOperationException error){throw new AssertionError(error);}
        }
        @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source){return false;}
        void raiseShield(int age){stopUsingItem();setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SHIELD));
            startUsingItem(InteractionHand.MAIN_HAND);useItemRemaining-=age;}
    }
}
