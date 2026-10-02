package com.tonywww.elder_bosses.boss.promisedconsort.source;

import com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionSoundPlan;
import java.util.*;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTimeline.*;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceExecutionData.integer;
import static com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionSoundPlan.Sound.*;

/** Project-authored OGG timbres adapted to original TAE/FXR semantics, not Wwise bank replacements. */
public final class PromisedConsortSourceSoundPlan {
    public record Binding(PromisedConsortActionSoundPlan.Cue cue, int dummyId, String evidence) {}
    private record Candidate(Event event, Binding binding, int priority) {}
    private static final class Holder { static final Map<Integer,Map<Integer,Binding>> PLANS = load(); }
    private PromisedConsortSourceSoundPlan() {}

    public static Map<Integer,Binding> bindings(int tae) {
        return Holder.PLANS.getOrDefault(tae,Map.of());
    }
    /** Offline export only: pose sampling must never run in combat sound dispatch. */
    public static Map<Integer,Binding> authorBindings(int tae) {return compile(tae);}
    private static Map<Integer,Map<Integer,Binding>> load() {
        String path="/assets/elder_bosses/boss/promised_consort/source_audio_bindings.json";
        var stream=PromisedConsortSourceSoundPlan.class.getResourceAsStream(path);
        if(stream==null) throw new IllegalStateException("Missing authored source audio bindings");
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var root=JsonParser.parseReader(reader).getAsJsonObject();
            if(integer(root,"schema_version")!=1) throw new IllegalArgumentException("Invalid source audio bindings");
            var result=new HashMap<Integer,Map<Integer,Binding>>();
            var events=new HashMap<Integer,Map<Integer,Event>>();
            for(var clip:PromisedConsortSourceAssets.bank().clips().values()) {
                var index=new HashMap<Integer,Event>();clip.events().forEach(e->index.put(e.index(),e));events.put(clip.taeId(),index);
            }
            var sounds=new HashMap<String,PromisedConsortActionSoundPlan.Sound>();
            for(var sound:PromisedConsortActionSoundPlan.Sound.values()) sounds.put(sound.eventId(),sound);
            for(var value:root.getAsJsonArray("event_bindings")) {
                var row=value.getAsJsonObject();int tae=integer(row,"tae_id"),index=integer(row,"event_index");
                var event=events.getOrDefault(tae,Map.of()).get(index);
                var sound=sounds.get(row.get("sound").getAsString());
                if(event==null || event.type()!=integer(row,"event_type") || event.startMicros()!=row.get("start_micros").getAsLong()
                        || event.stateInfo()!=integer(row,"state_info") || sound==null)
                    throw new IllegalArgumentException("Stale source audio binding: "+tae+"/"+index);
                String evidence=row.get("evidence").getAsString();
                var binding=cue(sound,row.get("volume").getAsFloat(),row.get("pitch").getAsFloat(),integer(row,"dummy_id"),evidence);
                if(result.computeIfAbsent(tae,k->new LinkedHashMap<>()).put(index,binding)!=null)
                    throw new IllegalArgumentException("Duplicate source audio binding");
            }
            var frozen=new HashMap<Integer,Map<Integer,Binding>>();result.forEach((tae,plan)->frozen.put(tae,Map.copyOf(plan)));return Map.copyOf(frozen);
        } catch(java.io.IOException e) {throw new IllegalStateException("Cannot read source audio bindings",e);}
    }
    private static Binding cue(PromisedConsortActionSoundPlan.Sound sound, float volume, float pitch, int dummy, String evidence) {
        return new Binding(new PromisedConsortActionSoundPlan.Cue(evidence, sound, volume, pitch, 0), dummy, evidence);
    }
    private static Binding sound(int id, int dummy) {
        return switch (id) {
            case 522006500 -> cue(STOMP,.6F,1,dummy,"TAE129:weapon_contact");
            case 522006505 -> cue(STOMP,.9F,.9F,dummy,"TAE129:ground_contact");
            case 522006501, 652281 -> cue(DASH,.8F,.65F,dummy,"TAE129:meteor_ascent");
            case 522006007 -> cue(DASH,.8F,.75F,dummy,"TAE129:gravity_release");
            case 522006016 -> cue(DASH,.45F,.8F,dummy,"TAE129:gravity_charge");
            case 652221 -> cue(DASH,.6F,1.3F,dummy,"TAE129:bloodflame");
            case 652231 -> cue(DASH,.8F,.75F,dummy,"TAE129:gravity_wave");
            case 652234 -> cue(STOMP,.75F,.85F,dummy,"TAE129:gravity_debris");
            case 652264 -> cue(DASH,.85F,.85F,dummy,"TAE129:meteor_descent");
            case 652265, 522007010 -> cue(DASH,.7F,1.2F,dummy,"TAE129:light_dash");
            case 652273, 652274 -> cue(HOLY,.45F,.8F,dummy,"TAE129:light_charge");
            case 473000007 -> cue(REFLECTION,.45F,1,dummy,"TAE129:meteor_warp");
            case 522007000 -> cue(DASH,.65F,.75F,dummy,"TAE129:meteor_return");
            case 522007001 -> cue(METEOR,1,.9F,dummy,"TAE129:meteor_landing");
            case 522007020 -> cue(REFLECTION,.35F,1.15F,dummy,"TAE129:clone_appear");
            default -> null;
        };
    }
    private static Binding attachedEffect(int id, int dummy, int tae) {
        return switch (id) {
            case 650000 -> cue(STOMP,.6F,1,dummy,"FXR:weapon_ground_dust");
            case 652210, 652229 -> cue(DASH,.35F,.8F,dummy,"FXR:gravity_charge");
            case 652230, 652231 -> cue(DASH,.65F,.75F,dummy,"FXR:gravity_release");
            case 652234, 652235 -> cue(STOMP,.85F,.85F,dummy,"FXR:gravity_ground_impact");
            case 652220, 652223 -> cue(DASH,.5F,1.3F,dummy,"FXR:bloodflame");
            case 652213, 652240, 652254, 652260, 652272, 652273 -> cue(HOLY,.4F,.8F,dummy,"FXR:light_charge_or_grab");
            case 652214 -> cue(DASH,.8F,.65F,dummy,"FXR:meteor_departure");
            case 652219 -> cue(METEOR,1,.9F,dummy,"FXR:meteor_landing");
            case 652266, 652275 -> cue(REFLECTION,.4F,1.1F,dummy,"FXR:clone_or_grab_appear");
            case 652274 -> cue(DASH,.7F,1.15F,dummy,"FXR:light_dash_departure");
            case 652290 -> tae == 3022 ? cue(HOLY,.35F,.8F,dummy,"FXR:holy_wave_prepare") : null;
            case 214 -> cue(HOLY,.45F,.75F,dummy,"FXR:defeat_dissolve");
            default -> null;
        };
    }
    /** Invisible carrier bullets have no sound; the visible child owns its warning/burst. */
    public static Binding projectile(int fxr) {
        return switch (fxr) {
            case 652200,652201,652205,652206,652236 -> cue(STOMP,.55F,1.1F,-1,"BulletFXR:ground_wave");
            case 652222 -> cue(DASH,.5F,1.3F,-1,"BulletFXR:bloodflame_burst");
            case 652280 -> cue(DASH,.65F,1,-1,"BulletFXR:gravity_projectile");
            case 652285 -> cue(DASH,.35F,1.05F,-1,"BulletFXR:rock_launch");
            case 652286 -> cue(METEOR,.85F,1.05F,-1,"BulletFXR:rock_impact");
            case 652262 -> cue(HOLY,.45F,.8F,-1,"BulletFXR:miquella_charge");
            case 652263 -> cue(HOLY,1,.7F,-1,"BulletFXR:miquella_burst");
            case 652291 -> cue(HOLY,.35F,.95F,-1,"BulletFXR:pillar_charge");
            case 652292,652293 -> cue(HOLY,.55F,1.05F,-1,"BulletFXR:pillar_or_trail_burst");
            case 652252 -> cue(HOLY,.55F,1,-1,"BulletFXR:holy_wave");
            case 652253 -> cue(HOLY,.75F,.95F,-1,"BulletFXR:holy_wave_impact");
            case 652255,652256 -> cue(HOLY,.6F,1.1F,-1,"BulletFXR:holy_ring");
            default -> null;
        };
    }
    private static Map<Integer,Binding> compile(int tae) {
        Clip clip = PromisedConsortSourceAssets.bank().requireClip(tae);
        var data = PromisedConsortSourceExecutionData.get();
        var candidates = new ArrayList<Candidate>();
        for (Event e : clip.events()) {
            var fields = data.eventFields(tae,e.index());
            Binding binding = null;
            if (e.type()==129) {
                int id=integer(fields,"Sound ID"),dummy=integer(fields,"Dummy Poly ID");
                if (id==522006000 && (dummy==10 || dummy==20)) binding=blade(clip,e,dummy,"TAE129:blade_swing");
                else binding=sound(id,dummy);
            } else if (e.type()==96) binding=attachedEffect(integer(fields,"FFX ID"),integer(fields,"Dummy Poly ID"),tae);
            else if (tae==20011 && e.type()==66 && e.referenceId()==20011599)
                binding=cue(HOLY,.65F,.7F,255,"TAE66:phase_two_entry");
            if (binding!=null) candidates.add(new Candidate(e,binding,e.type()==129?0:1));
        }
        for (Event e : clip.events()) if (e.type()==118) {
            int base=integer(data.eventFields(tae,e.index()),"Dummy Poly - Blade Base ID");
            int dummy=base==300?10:base==310?20:-1;
            if (dummy<0) continue;
            // Some source clones/holy chains omit a separate Wwise swing. Use
            // that blade's original trail edge, never an old project contact tick.
            boolean covered=candidates.stream().anyMatch(c->c.binding().cue().sound()==SLASH && c.binding().dummyId()==dummy
                    && Math.abs(c.event().startMicros()-e.startMicros())<=250_000);
            if (!covered) candidates.add(new Candidate(e,blade(clip,e,dummy,"TAE118:blade_trail_fallback"),2));
        }
        candidates.sort(Comparator.comparingInt(Candidate::priority).thenComparingLong(c->c.event().startMicros())
                .thenComparing((Candidate c)->-c.binding().cue().volume()).thenComparingInt(c->c.event().index()));
        var accepted=new ArrayList<Candidate>();
        for (var candidate:candidates) {
            boolean duplicate=candidate.binding().cue().sound()!=SLASH && accepted.stream().anyMatch(c ->
                    c.binding().cue().sound()==candidate.binding().cue().sound()
                    && (c.event().stateInfo()==0 || candidate.event().stateInfo()==0 || c.event().stateInfo()==candidate.event().stateInfo())
                    && Math.abs(c.event().startMicros()-candidate.event().startMicros())<=100_000);
            if (!duplicate) accepted.add(candidate);
        }
        var result=new LinkedHashMap<Integer,Binding>();
        accepted.sort(Comparator.comparingLong(c->c.event().startMicros()));
        for (var c:accepted) {
            Binding b=c.binding();var cue=b.cue();
            // Simultaneous two-blade swings share volume while keeping both emitters.
            if (cue.sound()==SLASH && accepted.stream().anyMatch(other->other!=c && other.binding().cue().sound()==SLASH
                    && other.binding().dummyId()!=b.dummyId() && Math.abs(other.event().startMicros()-c.event().startMicros())<=33_334))
                cue=new PromisedConsortActionSoundPlan.Cue(cue.key(),SLASH,cue.volume()*.5F,cue.pitch(),0);
            result.put(c.event().index(),new Binding(cue,b.dummyId(),b.evidence()));
        }
        return Collections.unmodifiableMap(result);
    }
    private static Binding blade(Clip clip, Event event, int dummy, String evidence) {
        int base=dummy==10?300:310;
        var data=PromisedConsortSourceExecutionData.get();
        Event trail=clip.events().stream().filter(e->e.type()==118
                && integer(data.eventFields(clip.taeId(),e.index()),"Dummy Poly - Blade Base ID")==base
                && Math.abs(e.startMicros()-event.startMicros())<=250_000)
                .min(Comparator.comparingLong(e->Math.abs(e.startMicros()-event.startMicros()))).orElse(null);
        long begin=Math.max(0,trail==null?event.startMicros():trail.startMicros());
        long end=Math.min(clip.durationMicros(),trail==null?begin+300_000:trail.endMicros());
        end=Math.max(begin,end);
        var pose=PromisedConsortSourceAssets.pose(clip.hkxId());var anchors=PromisedConsortSourceAssets.attachments();
        var origin=new PromisedConsortSourcePose.Point(0,0,0);
        var motion=new PromisedConsortSourceMotion.Displacement(0,0,0);
        net.minecraft.world.phys.Vec3 previous=null,first=null,last=null;double arc=0;
        int count=Math.max(1,(int)Math.ceil((end-begin)/16_667.0));
        for (int i=0;i<=count;i++) {
            var sample=pose.sample(begin+(end-begin)*i/count);
            var root=anchors.worldPoints(base,sample,origin,motion,0).get(0);
            var tip=anchors.worldPoints(dummy,sample,origin,motion,0).get(0);
            var vector=new net.minecraft.world.phys.Vec3(tip.x()-root.x(),tip.y()-root.y(),tip.z()-root.z()).normalize();
            if(previous!=null) arc+=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,previous.dot(vector)))));
            if(first==null) first=vector;last=vector;previous=vector;
        }
        // Root-relative direction removes authored body lift from the pitch estimate.
        var delta=last.subtract(first);double elevation=Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance()));
        var cue=PromisedConsortActionSoundPlan.blades(evidence,List.of(new PromisedConsortActionSoundPlan.Swing(dummy==10?1:-1,arc,elevation))).get(0);
        return new Binding(cue,dummy,evidence);
    }
}
