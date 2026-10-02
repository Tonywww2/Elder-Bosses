import com.tonywww.elder_bosses.boss.malenia.controller.MaleniaBurstCadence;
import com.tonywww.elder_bosses.boss.malenia.action.MaleniaActionCatalog;
import com.tonywww.elder_bosses.boss.malenia.config.MaleniaSkillConfigSnapshot;
import com.tonywww.elder_bosses.boss.malenia.domain.*;
import com.tonywww.elder_bosses.boss.malenia.execution.*;
import com.tonywww.elder_bosses.boss.malenia.runtime.MaleniaActionRuntime;
import com.tonywww.elder_bosses.combat.action.*;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;
import java.util.*;

public final class MotionCadenceCheck {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args) {
        for(long seed=0;seed<100;seed++) {
            var cadence=new MaleniaBurstCadence();cadence.start(seed);int count=cadence.remaining();
            check(count>=2&&count<=4,"Burst length");
            for(int i=0;i<count;i++) {
                cadence.start(seed+20);check(cadence.remaining()==count-i,"Starting a link reset the burst");
                check(cadence.mayShortenRecovery()==(i<count-1),"Last skill lost its recovery");
                check(cadence.complete()==(i==count-1),"Rest inserted between links");
            }
            cadence.start(seed);cadence.reset();check(cadence.remaining()==0,"Cancelled burst retained links");
        }
        var config=com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        ElderBossesCommonConfig.SPEC.correct(config);ElderBossesCommonConfig.SPEC.setConfig(config);
        var skills=ElderBossesCommonConfig.VALUES.maleniaSkillSnapshot();var catalog=new MaleniaActionCatalog(skills);
        var runtime=new MaleniaActionRuntime(catalog);runtime.start(MaleniaActionId.DOUBLE_SLASH,MaleniaPhase.PHASE_ONE,0,1,null);
        check(runtime.finishRecovery(19,6).isEmpty(),"Intermediate combo recovery was cut");
        check(runtime.finishRecovery(37,6).isEmpty(),"Recovery ended before six ticks");
        check(runtime.finishRecovery(38,6).orElseThrow().completed(),"Final recovery did not link");
        var water=MaleniaSkillEventPlanner.createPlan(skills,MaleniaActionId.WATERFOWL_DANCE);
        var first=water.intentsAt(32).stream().filter(i->i instanceof MaleniaServerIntent.MoveToward).findFirst().orElseThrow();
        check(first==water.intentsAt(33).stream().filter(i->i instanceof MaleniaServerIntent.MoveToward).findFirst().orElseThrow(),
                "Range tuning reset movement progress every tick");
        check(first!=water.intentsAt(62).stream().filter(i->i instanceof MaleniaServerIntent.MoveToward).findFirst().orElseThrow(),
                "Separate rushes shared movement progress");
        for(int t=0;t<water.totalTicks();t++) {
            var intents=water.intentsAt(t);
            boolean moving=intents.stream().anyMatch(i->i instanceof MaleniaServerIntent.MoveToward);
            boolean expected=t>=32&&t<50||t>=62&&t<74||t>=82&&t<102;
            check(moving==expected,"Rush/gap/finisher movement mismatch at "+t);
            check(intents.stream().anyMatch(i->i instanceof MaleniaServerIntent.HoldVertical)==(t<116),"Gravity clock mismatch");
        }
        System.out.println("Burst, final recovery and Waterfowl movement checks passed: "+checks);
    }
}
