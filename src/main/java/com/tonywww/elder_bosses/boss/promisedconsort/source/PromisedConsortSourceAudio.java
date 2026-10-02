package com.tonywww.elder_bosses.boss.promisedconsort.source;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionSoundPlan;
import net.minecraft.world.phys.Vec3;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTimeline.*;

/** One-shot server sound dispatch shares the persisted TAE cursor and bullet lifecycle. */
public final class PromisedConsortSourceAudio {
    private final PromisedConsortSourceCombat combat;
    private final PromisedConsortEntity owner;
    public PromisedConsortSourceAudio(PromisedConsortSourceCombat combat,PromisedConsortEntity owner) {
        this.combat=combat;this.owner=owner;
    }
    public void event(PromisedConsortSourceSession.Notice notice) {
        if(!owner.sourceAudioEnabled()) return;
        var crossing=notice.crossing();
        if(crossing.identity().edge()!=Edge.ENTER) return;
        int type=crossing.event().type();if(type!=129 && type!=96 && type!=118 && type!=66) return;
        var actor=crossing.identity().actor();var binding=PromisedConsortSourceSoundPlan.bindings(actor.taeId()).get(crossing.event().index());
        var frame=combat.frame(actor);if(binding==null || frame==null) return;
        Vec3 point=binding.dummyId()<0?frame.projected(notice.worldMicros()):frame.point(binding.dummyId(),notice.worldMicros());
        play(binding.cue(),point,actor.slot()>=0);
    }
    public void projectile(Actor actor,int fxr,Vec3 point) {
        if(!owner.sourceAudioEnabled()) return;
        var binding=PromisedConsortSourceSoundPlan.projectile(fxr);
        if(binding!=null) play(binding.cue(),point,actor.slot()>=0);
    }
    public void charm(Vec3 point) {
        play(new PromisedConsortActionSoundPlan.Cue("source:charm",PromisedConsortActionSoundPlan.Sound.HOLY,.7F,.85F,0),point,false);
    }
    private void play(PromisedConsortActionSoundPlan.Cue cue,Vec3 point,boolean clone) {
        if(clone) cue=new PromisedConsortActionSoundPlan.Cue(cue.key(),cue.sound(),cue.volume()*.55F,cue.pitch()*1.05F,0);
        owner.playSourceSound(cue,point);
    }
}
