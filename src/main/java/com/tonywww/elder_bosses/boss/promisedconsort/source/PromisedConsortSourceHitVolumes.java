package com.tonywww.elder_bosses.boss.promisedconsort.source;

import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourcePose.Point;

/** Original attack windows and pose-bound capsules, before Minecraft damage/defense adaptation. */
public final class PromisedConsortSourceHitVolumes {
    public static final String RESOURCE="/assets/elder_bosses/boss/promised_consort/combat_contracts.json";
    public record Window(int taeId,int poseHkxId,int eventIndex,int judgeId,int stateInfo,int behaviorId,int attackId) {}
    public record Primitive(int slot,double radius,int firstDummy,int secondDummy,int hitType,int priority) {
        public Primitive {
            if (slot<0 || slot>15 || !Double.isFinite(radius) || radius<=0) throw new IllegalArgumentException("Invalid source primitive");
        }
    }
    public record Damage(int physical,int magic,int fire,int thunder,int darkSlot) {}
    public record Attack(int id,int hitSourceType,Damage damage,double knockbackDistance,
                         int throwFlag,int throwTypeId,List<Primitive> primitives) {
        public Attack { primitives=List.copyOf(primitives); }
    }
    public record Capsule(int primitiveSlot,int sourcePointIndex,Point first,Point second,double radius,int hitType,int priority) {}
    /** MC dash contact covers the charging body while the authored swords are still raised. */
    public static Capsule dashBody(Point feet,double height,double radius) {
        return new Capsule(-1,0,new Point(feet.x(),feet.y()+radius,feet.z()),
                new Point(feet.x(),feet.y()+Math.max(radius,height-radius),feet.z()),radius,0,0);
    }
    /** Continuous body contact between sampled charge positions, including skipped space. */
    public static List<Capsule> sweptSpheres(List<Capsule> previous,List<Capsule> current) {
        var result=new ArrayList<Capsule>();
        for(var c:current) {
            var old=previous.stream().filter(p->p.primitiveSlot()==c.primitiveSlot() && p.sourcePointIndex()==c.sourcePointIndex()).findFirst();
            if(c.first().equals(c.second()) && old.isPresent()) result.add(new Capsule(c.primitiveSlot(),c.sourcePointIndex(),
                    old.get().first(),c.first(),Math.max(old.get().radius(),c.radius()),c.hitType(),c.priority()));
            else result.add(c);
        }
        return List.copyOf(result);
    }
    /** Bridge both blade endpoints between close samples so a fast dash cannot skip a target. */
    public static List<Capsule> sweptBlades(List<Capsule> previous,List<Capsule> current) {
        var result=new ArrayList<Capsule>(current);
        for(var c:current) for(var old:previous) {
            if(old.primitiveSlot()!=c.primitiveSlot() || old.sourcePointIndex()!=c.sourcePointIndex()) continue;
            double radius=Math.max(old.radius(),c.radius());
            result.add(new Capsule(c.primitiveSlot(),c.sourcePointIndex(),old.first(),c.first(),radius,c.hitType(),c.priority()));
            result.add(new Capsule(c.primitiveSlot(),c.sourcePointIndex(),old.second(),c.second(),radius,c.hitType(),c.priority()));
            break;
        }
        return List.copyOf(result);
    }
    private record Key(int tae,int event) {}
    private final Map<Integer,Attack> attacks;
    private final Map<Key,Window> windows;
    private PromisedConsortSourceHitVolumes(Map<Integer,Attack> attacks,Map<Key,Window> windows) {
        this.attacks=Map.copyOf(attacks); this.windows=Map.copyOf(windows);
    }

    /** Original event indices keep repeated judge IDs as distinct attack windows. */
    public Attack require(int taeId,int eventIndex) {
        Window window=windows.get(new Key(taeId,eventIndex));
        if (window==null) throw new IllegalArgumentException("Missing source attack window: "+taeId+"/"+eventIndex);
        return attacks.get(window.attackId());
    }

    public List<Capsule> capsules(int taeId,int eventIndex,PromisedConsortSourceAttachments attachments,
                                  PromisedConsortSourcePose.Sample pose,Point entityOrigin,
                                  PromisedConsortSourceMotion.Displacement motion,double yawDegrees) {
        Attack attack=require(taeId,eventIndex);
        if (windows.get(new Key(taeId,eventIndex)).poseHkxId()!=pose.hkxId())
            throw new IllegalArgumentException("Attack actor must sample its own source HKX pose");
        if (attack.hitSourceType()!=0) throw new IllegalStateException("Source attack requires a separate hit-source adapter: "+attack.id());
        var result=new ArrayList<Capsule>();
        for (Primitive primitive : attack.primitives()) {
            List<Point> first=attachments.worldPoints(primitive.firstDummy(),pose,entityOrigin,motion,yawDegrees);
            List<Point> second=primitive.secondDummy()<0 || primitive.firstDummy()==primitive.secondDummy()
                    ? first : attachments.worldPoints(primitive.secondDummy(),pose,entityOrigin,motion,yawDegrees);
            // Single-point spheres and source-ordered duplicate body points are
            // preserved. Different duplicate counts need original pairing evidence.
            if (first.size()!=second.size()) throw new IllegalStateException("Unresolved source dummy pairing: "+attack.id()+"/"+primitive.slot());
            for (int index=0;index<first.size();index++) result.add(new Capsule(primitive.slot(),index,
                    first.get(index),second.get(index),primitive.radius(),primitive.hitType(),primitive.priority()));
        }
        return List.copyOf(result);
    }

    public static PromisedConsortSourceHitVolumes load(Reader input) {
        var json=JsonParser.parseReader(input).getAsJsonObject();
        if (json.get("schema_version").getAsInt()!=1 || !json.getAsJsonArray("unresolved_windows").isEmpty())
            throw new IllegalArgumentException("Incomplete source combat contract");
        var attacks=new LinkedHashMap<Integer,Attack>(); var windows=new LinkedHashMap<Key,Window>();
        for (var value : json.getAsJsonArray("attacks")) {
            var source=value.getAsJsonObject(); var primitives=new ArrayList<Primitive>();
            var slots=new java.util.HashSet<Integer>();
            for (var primitiveValue : source.getAsJsonArray("hit_primitives")) {
                var p=primitiveValue.getAsJsonObject();
                var primitive=new Primitive(p.get("slot").getAsInt(),p.get("radius").getAsDouble(),p.get("first_dummy").getAsInt(),
                        p.get("second_dummy").getAsInt(),p.get("hit_type").getAsInt(),p.get("priority").getAsInt());
                if (!slots.add(primitive.slot())) throw new IllegalArgumentException("Duplicate source primitive slot");
                primitives.add(primitive);
            }
            Attack attack=new Attack(source.get("id").getAsInt(),source.get("hit_source_type").getAsInt(),
                    new Damage(source.get("physical").getAsInt(),source.get("magic").getAsInt(),source.get("fire").getAsInt(),
                            source.get("thunder").getAsInt(),source.get("dark_slot").getAsInt()),
                    source.get("knockback_distance").getAsDouble(),source.get("throw_flag").getAsInt(),source.get("throw_type_id").getAsInt(),primitives);
            if (attacks.put(attack.id(),attack)!=null) throw new IllegalArgumentException("Duplicate source attack parameter");
        }
        for (var value : json.getAsJsonArray("windows")) {
            var source=value.getAsJsonObject();
            Window window=new Window(source.get("tae_id").getAsInt(),source.get("pose_hkx_id").getAsInt(),source.get("event_index").getAsInt(),source.get("judge_id").getAsInt(),
                    source.get("state_info").getAsInt(),source.get("behavior_id").getAsInt(),source.get("attack_id").getAsInt());
            if (!attacks.containsKey(window.attackId()) || windows.put(new Key(window.taeId(),window.eventIndex()),window)!=null)
                throw new IllegalArgumentException("Invalid source attack linkage");
        }
        return new PromisedConsortSourceHitVolumes(attacks,windows);
    }
}
