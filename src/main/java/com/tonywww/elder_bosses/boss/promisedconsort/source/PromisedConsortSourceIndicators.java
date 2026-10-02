package com.tonywww.elder_bosses.boss.promisedconsort.source;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.network.IndicatorSnapshotPacket;
import java.util.*;
import static com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.*;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTimeline.*;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourcePose.Point;

/** Ground warnings use authored skill footprints. Exact mode separately retains locked sword sweeps. */
public final class PromisedConsortSourceIndicators {
    public record Key(Actor actor,int event) {}
    public record Slice(long at,PromisedConsortSourceHitVolumes.Capsule capsule) {}
    public record Sweep(int slot,int point,double radius,List<Slice> slices) {public Sweep {slices=List.copyOf(slices);}}
    public record Danger(Key key,long start,long lock,long active,long end,boolean locked,PromisedConsortSourceGroundAreas.Area area,List<Sweep> sweeps) {
        public Danger {sweeps=List.copyOf(sweeps);}
    }
    public record SavedState(List<Danger> dangers) {}
    private final PromisedConsortSourceCombat combat;
    private final PromisedConsortEntity owner;
    private final Map<Key,Danger> dangers=new LinkedHashMap<>();
    private final Map<Key,Long> preparedAt=new HashMap<>();
    public PromisedConsortSourceIndicators(PromisedConsortSourceCombat combat,PromisedConsortEntity owner) {this.combat=combat;this.owner=owner;}
    public void prepare(long now) {
        dangers.entrySet().removeIf(e->e.getValue().end()+GAME_TICK_MICROS<=now || combat.frame(e.getKey().actor())==null);
        preparedAt.keySet().retainAll(dangers.keySet());
        for(var frame:combat.frames()) for(var event:combat.clip(frame.playback.actor().taeId()).events()) {
            if(event.type()!=1 || !event.appliesTo(frame.playback.actor().stateInfo())) continue;
            long active=frame.playback.worldAtSource(event.startMicros());
            long end=frame.playback.worldAtSource(event.endMicros());
            if(end<=now) continue;
            String prefix=owner.sourceConfig().segment(frame.playback.actor().taeId());
            long lead=(long)(owner.sourceConfig().number(prefix+"warning_lead_ticks")*GAME_TICK_MICROS);
            long start=Math.max(frame.playback.startWorldMicros(),active-lead);
            if(now<start) continue;
            Key key=new Key(frame.playback.actor(),event.index());Danger old=dangers.get(key);
            if(old!=null && (old.locked() || preparedAt.getOrDefault(key,-1L)==now)) continue;
            long lock=Math.max(start,active-(long)(owner.sourceConfig().number(prefix+"target_lock_lead_ticks")*GAME_TICK_MICROS));
            var grouped=new LinkedHashMap<String,List<Slice>>();
            int samples=owner.sourceConfig().flag("hit_detection.simple_ranges") || PromisedConsortSourceCombat.liveContact(frame.playback.actor().taeId())?0:Math.max(2,Math.min(47,(int)Math.ceil((end-active)/16_667.0)+1));
            for(int i=0;i<samples;i++) {
                long at=active+(Math.max(0,end-active-1))*i/(samples-1);
                for(var capsule:combat.capsules(frame,event.index(),at)) grouped.computeIfAbsent(capsule.primitiveSlot()+":"+capsule.sourcePointIndex(),k->new ArrayList<>()).add(new Slice(at,capsule));
            }
            var sweeps=grouped.values().stream().map(slices->{var c=slices.get(0).capsule();return new Sweep(c.primitiveSlot(),c.sourcePointIndex(),c.radius(),slices);}).toList();
            dangers.put(key,new Danger(key,start,lock,active,end,now>=lock,PromisedConsortSourceGroundAreas.forEvent(owner,frame,event.index(),active),sweeps));
            preparedAt.put(key,now);
        }
    }
    /** Collision interpolates the locked trajectory, never re-aiming a locked warning. */
    public List<PromisedConsortSourceHitVolumes.Capsule> capsules(Actor actor,int event,long time) {
        Danger danger=dangers.get(new Key(actor,event));
        if(danger==null || !danger.locked()) return List.of();
        var result=new ArrayList<PromisedConsortSourceHitVolumes.Capsule>();
        for(var sweep:danger.sweeps()) {
            var slices=sweep.slices();Slice a=slices.get(0),b=a;
            for(var slice:slices) {if(slice.at()>time) {b=slice;break;}a=slice;b=slice;}
            double t=a.at()==b.at()?0:Math.max(0,Math.min(1,(double)(time-a.at())/(b.at()-a.at())));
            var first=lerp(a.capsule().first(),b.capsule().first(),t);var second=lerp(a.capsule().second(),b.capsule().second(),t);
            var c=a.capsule();result.add(new PromisedConsortSourceHitVolumes.Capsule(c.primitiveSlot(),c.sourcePointIndex(),first,second,c.radius(),c.hitType(),c.priority()));
        }
        return result;
    }
    public PromisedConsortSourceGroundAreas.Area area(Actor actor,int event) {
        var danger=dangers.get(new Key(actor,event));return danger==null?null:danger.area();
    }
    private static Point lerp(Point a,Point b,double t) {return new Point(a.x()+(b.x()-a.x())*t,a.y()+(b.y()-a.y())*t,a.z()+(b.z()-a.z())*t);}
    public List<IndicatorSnapshotPacket> packets(long gameTick) {
        long now=gameTick*GAME_TICK_MICROS;var packets=new ArrayList<IndicatorSnapshotPacket>();
        for(var danger:dangers.values()) {
            if(now>=danger.end()) continue;
            Actor actor=danger.key().actor();
            var attack=PromisedConsortSourceAssets.hitVolumes().require(actor.taeId(),danger.key().event());
            var row=combat.attackRow(attack.id());
            boolean holy=PromisedConsortSourceExecutionData.number(row,"atkDark")>0,fire=PromisedConsortSourceExecutionData.number(row,"atkFire")>0,magic=PromisedConsortSourceExecutionData.number(row,"atkMag")>0;
            StyleRole style=actor.slot()>=0?StyleRole.CLONE_GOLD:holy?StyleRole.HOLY_IVORY:fire?StyleRole.BLOODFLAME_RED:magic?StyleRole.GRAVITY_PURPLE:StyleRole.PHYSICAL_GOLD;
            Semantic semantic=holy?Semantic.HOLY:fire?Semantic.BLOODFLAME:magic?Semantic.GRAVITY:Semantic.PHYSICAL;
            long active=tick(danger.active()),start=Math.min(active,tick(danger.start())),lock=Math.max(start,Math.min(active,tick(danger.lock()))),end=Math.max(active+1,tick(danger.end()));
            IndicatorState state=gameTick>=active?IndicatorState.ACTIVE:gameTick>=lock?IndicatorState.IMMINENT:IndicatorState.TRACKING;
                var frame=combat.frame(actor);
                var area=frame!=null && PromisedConsortSourceCombat.liveContact(actor.taeId()) && now>=danger.active()
                        ?PromisedConsortSourceGroundAreas.forEvent(owner,frame,danger.key().event(),now):danger.area();
                String id="source:"+actor.actionSequence()+":"+actor.segmentIndex()+":"+actor.slot()+":"+actor.taeId()+":"+danger.key().event();
                boolean cue=!fire && owner.sourceGuardEnabled();
                packets.add(new IndicatorSnapshotPacket(owner.getId(),id,gameTick<active?SegmentSlot.NEXT:SegmentSlot.CURRENT,style,semantic,state,area.shape(),point(area.anchor()),(float)area.yaw(),area.ranges(),List.of(),start,lock,active,end,cue,cue?owner.sourceGuardCuePulseCount():0,cue?owner.sourceGuardCueRgb():0));
        }
        return packets;
    }
    private static long tick(long micros) {return Math.max(0,(micros+GAME_TICK_MICROS-1)/GAME_TICK_MICROS);}
    private static IndicatorSnapshotPacket.Point point(Point p) {return new IndicatorSnapshotPacket.Point(p.x(),p.y(),p.z());}
    public void cancel(Actor actor) {
        dangers.keySet().removeIf(k->k.actor().equals(actor));
        preparedAt.keySet().removeIf(k->k.actor().equals(actor));
    }
    public SavedState save() {return new SavedState(List.copyOf(dangers.values()));}
    public void restore(SavedState state,long shift) {
        dangers.clear();preparedAt.clear();for(var d:state.dangers()) {
            var sweeps=d.sweeps().stream().map(s->new Sweep(s.slot(),s.point(),s.radius(),s.slices().stream().map(p->new Slice(p.at()+shift,p.capsule())).toList())).toList();
            dangers.put(d.key(),new Danger(d.key(),d.start()+shift,d.lock()+shift,d.active()+shift,d.end()+shift,d.locked(),d.area(),sweeps));
        }
    }
}
