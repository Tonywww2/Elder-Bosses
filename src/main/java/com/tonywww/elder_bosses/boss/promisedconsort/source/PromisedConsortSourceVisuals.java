package com.tonywww.elder_bosses.boss.promisedconsort.source;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTimeline.*;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceExecutionData.*;

/** Server FXR instances use the same exact event identity and clock as attacks. */
public final class PromisedConsortSourceVisuals {
    private record Key(Actor actor,int event) {}
    public record TracePoint(long at,PromisedConsortSourcePose.Point base,PromisedConsortSourcePose.Point tip) {}
    private record Instance(long visualId,int id,int dummy,int tip,long born,long stop,boolean follow,Vec3 point,Vec3 direction,List<TracePoint> trace) {}
    private final PromisedConsortSourceCombat combat;
    private final PromisedConsortEntity owner;
    private final Map<Key,Instance> attached=new LinkedHashMap<>();
    private final List<Instance> tails=new ArrayList<>();
    private final Map<Integer,Long> tailDurations=new HashMap<>();
    private long serial;
    public record InstanceState(Actor actor,int event,long visualId,int id,int dummy,long born,long stop,boolean follow,
                                PromisedConsortSourcePose.Point point,PromisedConsortSourcePose.Point direction,int tip,List<TracePoint> trace) {}
    public record SavedState(long serial,List<InstanceState> instances) {}
    public PromisedConsortSourceVisuals(PromisedConsortSourceCombat combat,PromisedConsortEntity owner) {this.combat=combat;this.owner=owner;}
    public void event(PromisedConsortSourceSession.Notice notice) {
        var crossing=notice.crossing();var actor=crossing.identity().actor();var e=crossing.event();
        if(e.type()!=96 && e.type()!=118) return;
        Key key=new Key(actor,e.index());
        if(crossing.identity().edge()!=Edge.ENTER) {
            Instance old=attached.remove(key);
            if(old!=null) {trace(key,old,notice.worldMicros());tails.add(new Instance(old.visualId(),old.id(),old.dummy(),old.tip(),old.born(),notice.worldMicros(),false,position(key,old,notice.worldMicros()),old.direction(),old.trace()));}
            return;
        }
        var fields=PromisedConsortSourceExecutionData.get().eventFields(actor.taeId(),e.index());
        int id=integer(fields,"FFX ID");if(!PromisedConsortSourceFfx.get().has(id)) return;
        int dummy=integer(fields,e.type()==118?"Dummy Poly - Blade Base ID":"Dummy Poly ID");
        var frame=combat.frame(actor);if(frame==null) return;
        Vec3 p=frame.visualPoint(dummy,notice.worldMicros());
        boolean follow=e.type()==118 || integer(fields,"Follow Dummy Poly")==1;
        long stop=e.endMicros()<0?Long.MAX_VALUE:frame.playback.worldAtSource(e.endMicros());
        int tip=e.type()==118?integer(fields,"Dummy Poly - Blade Tip ID"):-1;
        // Full-blade MC trail adapter: original FLVER confirms 300/10 share
        // R_Sword and 310/20 share L_Sword. This is not an invented TAE tip ID.
        if(e.type()==118 && tip<0) tip=dummy==300?10:dummy==310?20:-1;
        var path=new ArrayList<TracePoint>();
        if(!PromisedConsortSourceFfx.isHoly(id) && PromisedConsortSourceFfx.get().nodes(id).stream().anyMatch(n->n.action("Tracer")!=null || n.action("LegacyTracer")!=null))
            path.add(new TracePoint(notice.worldMicros(),point(p),tip<0?null:point(frame.visualPoint(tip,notice.worldMicros()))));
        attached.put(key,new Instance(++serial,id,dummy,tip,notice.worldMicros(),stop,follow,p,frame.entity.getLookAngle(),path));
    }
    public void pulse(int id,Vec3 point,Vec3 direction,long time) {
        if(PromisedConsortSourceFfx.get().has(id)) tails.add(new Instance(++serial,id,-1,-1,time,time,false,point,direction,new ArrayList<>()));
    }
    public void finishProjectile(long serial,int id,Vec3 point,Vec3 direction,long born,long stopped) {
        if((PromisedConsortSourceFfx.isHoly(id) || PromisedConsortSourceFfx.isGravity(id)) && PromisedConsortSourceFfx.get().has(id))
            tails.add(new Instance(-serial,id,-1,-1,born,stopped,false,point,direction,new ArrayList<>()));
    }
    public void append(ListTag list,long now) {
        tails.removeIf(i->now-i.stop()>tailMicros(i.id()));
        for(var entry:attached.entrySet()) {
            var i=entry.getValue();trace(entry.getKey(),i,now);var tag=tag(i.id(),i.born(),i.stop(),position(entry.getKey(),i,now),i.direction(),Vec3.ZERO,now);tag.putLong("VisualId",i.visualId());appendTrace(tag,i,now);list.add(tag);
        }
        for(var i:tails) {var tag=tag(i.id(),i.born(),i.stop(),i.point(),i.direction(),Vec3.ZERO,now);tag.putLong("VisualId",i.visualId());appendTrace(tag,i,now);list.add(tag);}
    }
    private static PromisedConsortSourcePose.Point point(Vec3 p) {return new PromisedConsortSourcePose.Point(p.x,p.y,p.z);}
    private void trace(Key key,Instance i,long now) {
        if(i.trace().isEmpty()) return;var frame=combat.frame(key.actor());if(frame==null) return;
        long first=i.trace().get(i.trace().size()-1).at();
        for(long at=first+50_000;at<=now;at+=50_000) i.trace().add(new TracePoint(at,point(frame.visualPoint(i.dummy(),at)),i.tip()<0?null:point(frame.visualPoint(i.tip(),at))));
        if(now>i.trace().get(i.trace().size()-1).at()) i.trace().add(new TracePoint(now,point(frame.visualPoint(i.dummy(),now)),i.tip()<0?null:point(frame.visualPoint(i.tip(),now))));
        while(i.trace().size()>96) i.trace().remove(0);
    }
    private static void appendTrace(CompoundTag tag,Instance i,long now) {
        if(i.trace().isEmpty()) return;var rows=new ListTag();
        for(var p:i.trace()) {var row=new CompoundTag();row.putLong("At",p.at());row.putDouble("X",p.base().x());row.putDouble("Y",p.base().y());row.putDouble("Z",p.base().z());
            if(p.tip()!=null) {row.putDouble("TX",p.tip().x());row.putDouble("TY",p.tip().y());row.putDouble("TZ",p.tip().z());}rows.add(row);}
        tag.put("Trace",rows);
    }
    private Vec3 position(Key key,Instance instance,long world) {
        var frame=combat.frame(key.actor());return instance.follow() && frame!=null?frame.visualPoint(instance.dummy(),world):instance.point();
    }
    public static CompoundTag tag(int id,long born,long stop,Vec3 point,Vec3 direction,Vec3 velocity,long now) {
        CompoundTag tag=new CompoundTag();tag.putInt("FXR",id);tag.putLong("Born",born);tag.putLong("Stop",stop);tag.putLong("At",now);
        tag.putDouble("X",point.x);tag.putDouble("Y",point.y);tag.putDouble("Z",point.z);
        tag.putFloat("DX",(float)direction.x);tag.putFloat("DY",(float)direction.y);tag.putFloat("DZ",(float)direction.z);
        tag.putFloat("VX",(float)velocity.x);tag.putFloat("VY",(float)velocity.y);tag.putFloat("VZ",(float)velocity.z);return tag;
    }
    private long tailMicros(int id) {
        return tailDurations.computeIfAbsent(id,this::calculateTailMicros);
    }
    private long calculateTailMicros(int id) {
        double longest=0;
        if(PromisedConsortSourceFfx.isHoly(id))
            return Math.round(PromisedConsortHolyColumns.settings(owner.sourceConfig(),PromisedConsortHolyColumns.profile(id)).totalTicks()*50_000);
        if(PromisedConsortSourceFfx.isGravity(id))
            return PromisedConsortSourceFfx.GRAVITY_FADE_IN+PromisedConsortSourceFfx.GRAVITY_FADE_OUT;
        for(var node:PromisedConsortSourceFfx.get().nodes(id)) {
            double delay=PromisedConsortSourceFfx.field(node.action("NodeAttributes"),"delay",0,0);
            double duration=PromisedConsortSourceFfx.scalar(node.action("NodeAttributes"),"duration",0,0,0,0);
            double particle=PromisedConsortSourceFfx.scalar(node.action("ParticleAttributes"),"duration",0,0,0,0);
            longest=Math.max(longest,delay+Math.max(0,duration)+Math.max(0,particle));
        }
        return Math.round(longest*1_000_000);
    }
    public void clear() {attached.clear();tails.clear();owner.setSourceVisuals(new CompoundTag());}
    public SavedState save() {
        var list=new ArrayList<InstanceState>();
        attached.forEach((key,i)->list.add(state(key,i)));
        tails.forEach(i->list.add(state(null,i)));return new SavedState(serial,List.copyOf(list));
    }
    private static InstanceState state(Key key,Instance i) {
        return new InstanceState(key==null?null:key.actor(),key==null?-1:key.event(),i.visualId(),i.id(),i.dummy(),i.born(),i.stop(),i.follow(),
                new PromisedConsortSourcePose.Point(i.point().x,i.point().y,i.point().z),
                new PromisedConsortSourcePose.Point(i.direction().x,i.direction().y,i.direction().z),i.tip(),List.copyOf(i.trace()));
    }
    public void restore(SavedState saved,long shift) {
        clear();serial=saved.serial();for(var s:saved.instances()) {
            if(!PromisedConsortSourceFfx.get().has(s.id())) continue;
            var i=new Instance(s.visualId(),s.id(),s.dummy(),s.tip(),Math.addExact(s.born(),shift),s.stop()==Long.MAX_VALUE?Long.MAX_VALUE:Math.addExact(s.stop(),shift),s.follow(),
                    new Vec3(s.point().x(),s.point().y(),s.point().z()),new Vec3(s.direction().x(),s.direction().y(),s.direction().z()),new ArrayList<>(s.trace().stream().map(t->new TracePoint(Math.addExact(t.at(),shift),t.base(),t.tip())).toList()));
            if(s.actor()==null) tails.add(i);else if(combat.frame(s.actor())!=null) attached.put(new Key(s.actor(),s.event()),i);
        }
    }
}
