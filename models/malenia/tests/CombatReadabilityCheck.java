import com.tonywww.elder_bosses.boss.malenia.action.MaleniaActionCatalog;
import com.tonywww.elder_bosses.boss.malenia.config.MaleniaConfigNbt;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.boss.malenia.execution.*;
import com.tonywww.elder_bosses.boss.malenia.indicator.*;
import com.tonywww.elder_bosses.boss.malenia.runtime.*;
import com.tonywww.elder_bosses.boss.malenia.sync.MaleniaIndicatorPacketMapper;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;
import java.util.*;

public final class CombatReadabilityCheck {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args) {
        var config=com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        ElderBossesCommonConfig.SPEC.correct(config);ElderBossesCommonConfig.SPEC.setConfig(config);
        var combat=ElderBossesCommonConfig.VALUES.maleniaCombatSnapshot();
        var skills=ElderBossesCommonConfig.VALUES.maleniaSkillSnapshot();
        var loaded=MaleniaConfigNbt.read(MaleniaConfigNbt.write(combat,skills)).orElseThrow();
        check(loaded.combat().equals(combat)&&loaded.skills().equals(skills),"Current config NBT roundtrip failed");
        var catalog=new MaleniaActionCatalog(skills);
        for(var id:List.of(MaleniaActionId.SINGLE_SLASH,MaleniaActionId.DOUBLE_SLASH,MaleniaActionId.RUNNING_SLASH,
                MaleniaActionId.THRUST,MaleniaActionId.GRAB_IMPALE)) {
            var plan=MaleniaSkillEventPlanner.createPlan(skills,id);var timeline=catalog.get(id).timeline();
            var first=plan.intents().stream().filter(i->hit(i.intent())!=null&&hit(i.intent()).instantGuardEligible()).findFirst().orElseThrow();
            var suffix=hit(first.intent()).hitIdSuffix();int start=Math.max(0,first.actionTick()-combat.instantGuard().windowTicks());
            int end=plan.intents().stream().filter(i->hit(i.intent())!=null&&hit(i.intent()).hitIdSuffix().equals(suffix))
                    .mapToInt(MaleniaActionPlan.ScheduledIntent::actionTick).max().orElseThrow()+1;
            var window=new MaleniaParryWindow(start,end);
            check(!window.accepts(start-1,first.actionTick()),"Early held shield counted as a parry");
            for(int raise=start;raise<end;raise++) {
                check(window.accepts(raise,Math.max(raise,first.actionTick())),"Valid raise rejected: "+id+" "+raise);
                check(!window.accepts(raise,end),"Expired window accepted");
            }
            check(window.accepts(first.actionTick(),first.actionTick()),"Shield startup delay leaked into parry");
            var action=new MaleniaActionSnapshot(id,3,1000,4,Optional.empty(),start,timeline.windowAt(start));
            var context=new MaleniaIndicatorGenerator.Context(7,new IndicatorPoint(0,0,0),
                    new MaleniaIndicatorGenerator.Direction(1,0),Optional.empty(),Map.of(),
                    Optional.of(new IndicatorPoint(8,0,0)),Map.of(),Map.of());
            var frame=MaleniaIndicatorGenerator.generate(plan,action,skills,context);
            var packets=MaleniaIndicatorPacketMapper.toPackets(frame,3,0xFF2020,combat.instantGuard().windowTicks());
            var cue=packets.stream().filter(p->p.instantGuardCue()&&p.activeTick()==1000+first.actionTick()).findFirst().orElseThrow();
            check(cue.lockTick()==1000+start&&cue.endTick()==1000+end,"Cue and server window disagree: "+id);
            var hidden=new MaleniaIndicatorFrame(frame.serverGameTick(),frame.current(),frame.next(),List.of());
            check(MaleniaIndicatorPacketMapper.toPackets(hidden,3,0xFF2020,8).stream().noneMatch(p->p.instantGuardCue()),"Cue-off flag leaked");
            check(window.accepts(start,first.actionTick()),"Disabling cue changed parry logic");
        }
        System.out.println("Parry window boundaries, cue agreement, cue toggle and current config roundtrip passed: "+checks);
    }
    private static MaleniaServerIntent.HitSpec hit(MaleniaServerIntent intent) {
        if(intent instanceof MaleniaServerIntent.HitSector i)return i.hit();
        if(intent instanceof MaleniaServerIntent.HitCapsule i)return i.hit();
        if(intent instanceof MaleniaServerIntent.Grab i)return i.grabHit();
        return null;
    }
}
