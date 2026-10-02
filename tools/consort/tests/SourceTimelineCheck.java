import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceAi;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceBank;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceMotion;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceSession;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourcePose;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceAttachments;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceController;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceHitVolumes;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceAssets;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourcePlayback;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceCollision;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceExecutionData;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourcePlayerPose;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTimeline.*;

/** Focused regression for the new event cursor: skipped frames, restore, phase and clone identity. */
public class SourceTimelineCheck {
    private static Path asset(String path) { return Path.of("src/main/resources/assets/elder_bosses").resolve(path); }
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        if(args.length>0 && args[0].equals("--meteor-landing")) {
            meteorLandingChecks();
            System.out.println("Gravity meteor landing checks passed: "+checks+"; no world launched.");return;
        }
        if(args.length>0 && args[0].equals("--mechanics")) {
            trajectoryOnceChecks();presentationPolishChecks();projectileBearingChecks();dashContactChecks();
            System.out.println("Meteor / timed contacts / launch bearing checks passed: "+checks+"; no world launched.");return;
        }
        if(args.length>0 && args[0].equals("--recovery-effects")) {
            recoveryEffectsChecks();trajectoryOnceChecks();
            System.out.println("Recovery pose / holy fade / segment contact checks passed: "+checks+"; no world launched.");return;
        }
        if(args.length>0 && args[0].equals("--presentation-polish")) {
            presentationPolishChecks();controllerChecks(PromisedConsortSourceAssets.bank());
            System.out.println("Meteor threshold / bounded landing / flat warnings / animation completion checks passed: "+checks+"; no world launched.");return;
        }
        if(args.length>0 && args[0].equals("--combat-fixes")) {
            combatFixChecks();arenaPolishChecks();System.out.println("Meteor / ranged / clone contact checks passed: "+checks+"; no world launched.");return;
        }
        if(args.length>0 && args[0].equals("--arena-polish")) {
            arenaPolishChecks();System.out.println("Clone targeting / current config checks passed: "+checks+" checks; no Minecraft world launched.");return;
        }
        if(args.length>0 && args[0].equals("--trajectory-once")) {
            trajectoryOnceChecks();System.out.println("Trajectory segment single-contact checks passed: "+checks+" checks; no Minecraft world launched.");return;
        }
        if(args.length>0 && args[0].equals("--ground-effects")) {
            groundEffectsChecks();System.out.println("Ground areas / effects / crash persistence checks passed: "+checks+" checks; no Minecraft world launched.");return;
        }
        if(args.length>0 && (args[0].equals("--audio") || args[0].equals("--audio-export"))) {
            audioChecks(args[0].equals("--audio-export"));
            System.out.println("Source audio checks passed: "+checks+" checks; no Minecraft world launched.");return;
        }
        if(args.length>0 && args[0].equals("--incoming-performance")) {
            incomingPerformanceChecks();
            System.out.println("Incoming/ranged/FXR checks passed: "+checks+" checks; no Minecraft world launched.");return;
        }
        if(args.length>0 && args[0].equals("--priority")) {
            priorityChecks();
            System.out.println("Source priority fixes passed: "+checks+" checks; no Minecraft world launched.");return;
        }
        if(args.length>0 && args[0].equals("--execution")) {
            executionChecks(Path.of(args[1]));
            System.out.println("Source execution checks passed: "+checks+" checks; no Minecraft world launched.");return;
        }
        if (args.length>0 && args[0].equals("--playback")) {
            playbackChecks(Path.of(args[1]));
            System.out.println("Source playback checks passed: "+checks+" checks; no Minecraft world launched.");
            return;
        }
        PromisedConsortSourceBank bank;
        try (Reader input=Files.newBufferedReader(Path.of(args[0]),StandardCharsets.UTF_8)) {
            bank=PromisedConsortSourceBank.load(input);
        }
        check(bank.clips().size()==81,"81 distinct TAE contracts");
        check(bank.requireClip(20012).hkxId()==4101,"TAE import remains distinct from its HKX pose");
        check(!bank.clips().containsKey(3027) && !bank.clips().containsKey(3029),"No invented missing source states");
        sessionChecks(bank);
        controllerChecks(bank);
        attachmentChecks(Path.of(args[0]).getParent().getParent());
        hitVolumeChecks(Path.of(args[0]).getParent().getParent(),bank);
        var bloodflame = new Cursor(bank.requireClip(3014),new Actor(5,0,-1,3014,3014,412)).advance(0);
        check(bloodflame.stream().anyMatch(c->c.event().index()==52 && c.event().startMicros()<0),"Original negative pre-roll is preserved and activated at zero");
        for (var clip:bank.clips().values()) {
            var actor=new Actor(9,0,-1,clip.taeId(),clip.hkxId(),413);
            var cursor=new Cursor(clip,actor);
            var all=cursor.advance(20_000_000L);
            check(new HashSet<>(all.stream().map(Crossing::identity).toList()).size()==all.size(),"Original edges are unique");
            check(cursor.advance(20_000_000L).isEmpty(),"Same time cannot replay edges");
            check(new Cursor(clip,cursor.save()).advance(20_000_000L).isEmpty(),"Restore cannot replay edges");
        }
        Clip meteor=bank.requireClip(3017);
        Cursor meteors=new Cursor(meteor,new Actor(2,1,-1,3017,3017,413));
        meteors.advance(5_000_000);
        var launch=meteors.advance(5_200_000).stream().filter(c->c.identity().edge()==Edge.ENTER
                && c.event().type()==2 && c.event().referenceId()==265).toList();
        check(launch.size()==8,"All eight meteor launches survive a skipped service tick");
        Clip stomp=bank.requireClip(3009);
        for (int phase:List.of(412,413)) {
            var edges=new Cursor(stomp,new Actor(3,0,-1,3009,3009,phase)).advance(2_000_000);
            check(edges.stream().anyMatch(e->e.event().referenceId()==(phase==412?190:191)),"Correct phase-specific stomp");
            check(edges.stream().noneMatch(e->e.event().referenceId()==(phase==412?191:190)),"Wrong phase stomp excluded");
        }
        Clip test=new Clip(1,2,200_000,List.of(new Event(0,1,0,150_000,0,100),
                new Event(1,2,25_000,26_000,0,101),new Event(2,2,25_000,26_000,0,101)));
        Cursor cursor=new Cursor(test,new Actor(0,0,-1,1,2,413));
        check(cursor.advance(0).size()==1,"Frame-zero edge fires once");
        var step=cursor.advance(50_000);
        check(step.size()==4,"Two complete narrow spans in one tick are preserved");
        check(step.get(0).event().index()==1 && step.get(1).event().index()==2,"Equal-time source order is stable");
        cursor=new Cursor(test,cursor.save());
        check(cursor.activeSpans().size()==1,"Restored attack window remains active");
        check(cursor.cancel().size()==1 && cursor.cancel().isEmpty(),"Cancellation cleans the active span once");
        check(new Cursor(test,cursor.save()).advance(100_000).isEmpty(),"Cancelled cursor stays cancelled after restore");
        Clock clock=new Clock(100,0,1);
        check(clock.timeAt(101,.5)==75_000,"One server clock includes partial ticks");
        check(new Clock(100,20_000,2).timeAt(101,0)==120_000,"Configured speed advances the same source clock");
        check(clock.poseSecondsAt(110,0,test)==.2,"Pose sampling clamps without discarding late TAE metadata");
        int actors=0;
        for(var chain:bank.cloneChains()) for(var cue:chain.actors()) {
            actors++;
            Clip clone=bank.requireClip(cue.taeId());
            Actor actor=cue.actor(6,2,413);
            check(actor.taeId()!=chain.parentTaeId(),"Clone owns its source clip, never the parent action pose");
            var edges=new Cursor(clone,actor).advance(1_000_000);
            check(edges.stream().allMatch(c->c.identity().actor().slot()==cue.slot()),"Slot survives independent contact events");
            check(!clone.animationClip().equals(bank.requireClip(chain.parentTaeId()).animationClip()),"Independent clone HKX selection");
        }
        check(actors==23,"Four/three/four-slot source clone schedules");
        checkAi(bank);
        if (args.length>1) poseChecks(Path.of(args[1]));
        System.out.println("SourceTimelineCheck passed: "+checks+" checks; no Minecraft world launched.");
    }

    private static void audioChecks(boolean exportOnly) throws Exception {
        var bank=PromisedConsortSourceAssets.bank();
        var counts=new java.util.EnumMap<com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionSoundPlan.Sound,Integer>(com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionSoundPlan.Sound.class);
        var report=new com.google.gson.JsonArray();long slowest=0;int slowestTae=0;
        for(var clip:bank.clips().values().stream().sorted(java.util.Comparator.comparingInt(Clip::taeId)).toList()) {
            long begin=System.nanoTime();var plan=exportOnly?com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceSoundPlan.authorBindings(clip.taeId()):com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceSoundPlan.bindings(clip.taeId());
            long elapsed=System.nanoTime()-begin;if(elapsed>slowest) {slowest=elapsed;slowestTae=clip.taeId();}
            if(!exportOnly) check(plan==com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceSoundPlan.bindings(clip.taeId()),"Sound plan is cached per clip");
            var authored=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceSoundPlan.authorBindings(clip.taeId());
            check(plan.keySet().equals(authored.keySet()),"Packaged sound indices match original animation export");
            for(var entry:plan.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
                var binding=entry.getValue();var cue=binding.cue();
                var original=authored.get(entry.getKey());
                check(binding.dummyId()==original.dummyId() && cue.sound()==original.cue().sound() && cue.volume()==original.cue().volume()
                        && cue.pitch()==original.cue().pitch(),"Baked sound profile matches current source blade motion");
                var event=clip.events().stream().filter(e->e.index()==entry.getKey()).findFirst().orElseThrow();
                check(event.type()==129 || event.type()==96 || event.type()==118 || event.type()==66,"Audio binding uses original event edge");
                check(Float.isFinite(cue.volume()) && cue.volume()>0 && cue.volume()<=1.05F && Float.isFinite(cue.pitch()) && cue.pitch()>=.5 && cue.pitch()<=2,"Finite authored audio mix: "+clip.taeId()+"/"+event.index());
                if(binding.dummyId()>=0) check(!PromisedConsortSourceAssets.attachments().require(binding.dummyId()).isEmpty(),"Animated sound emitter exists");
                counts.merge(cue.sound(),1,Integer::sum);
                var row=new com.google.gson.JsonObject();row.addProperty("tae_id",clip.taeId());row.addProperty("event_index",event.index());row.addProperty("event_type",event.type());
                row.addProperty("start_micros",event.startMicros());row.addProperty("state_info",event.stateInfo());row.addProperty("sound",cue.sound().eventId());
                row.addProperty("dummy_id",binding.dummyId());row.addProperty("volume",cue.volume());row.addProperty("pitch",cue.pitch());row.addProperty("evidence",binding.evidence());report.add(row);
            }
            for(int phase:new int[]{412,413}) {
                var actor=new Actor(1,0,-1,clip.taeId(),clip.hkxId(),phase);var cursor=new Cursor(clip,actor);
                var notices=cursor.advance(clip.durationMicros()+1_000_000).stream().filter(c->c.identity().edge()==Edge.ENTER && plan.containsKey(c.event().index())).toList();
                check(notices.stream().allMatch(c->c.event().appliesTo(phase)),"Sound state follows original phase filter");
                check(notices.size()==new HashSet<>(notices.stream().map(Crossing::identity).toList()).size(),"Skipped audio frames produce unique cue identities");
                check(new Cursor(clip,cursor.save()).advance(clip.durationMicros()+1_000_000).isEmpty(),"Saved cursor does not replay sounds");
            }
        }
        var soundJson=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/elder_bosses/sounds.json"))).getAsJsonObject();
        for(var sound:com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionSoundPlan.Sound.values()) {
            check(counts.getOrDefault(sound,0)>0,"Every old action timbre is integrated: "+sound);
            var name=soundJson.getAsJsonObject(sound.eventId()).getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString().split(":",2)[1];
            byte[] bytes=Files.readAllBytes(Path.of("src/main/resources/assets/elder_bosses/sounds",name+".ogg"));
            check(bytes.length>1000 && new String(bytes,0,4,StandardCharsets.US_ASCII).equals("OggS"),"Registered legacy OGG is present: "+sound);
        }
        for(int id:new int[]{652200,652205,652222,652236,652263,652285,652286,652252,652253,652255,652256,652291,652292,652293})
            check(com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceSoundPlan.projectile(id)!=null,"Visible projectile effect has audio: "+id);
        check(com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceSoundPlan.projectile(-1)==null,"Invisible carrier does not duplicate its child sound");
        var actor=new Actor(9,0,2,20002,20002,413);var point=new PromisedConsortSourcePose.Point(0,0,0);
        var state=new com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceProjectiles.BulletState(1,0,actor,0,205220945,point,point,point,0,0,java.util.Map.of(),java.util.Map.of(),0,true);
        var gson=new com.google.gson.Gson();var restored=gson.fromJson(gson.toJson(state),state.getClass());
        check(restored.audioStarted() && restored.actor().equals(actor),"Bullet sound birth and clone identity survive saved state");
        var playback=new PromisedConsortSourcePlayback(new Actor(10,0,-1,3014,3014,412),0,1,0,new com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTimeWarp(1_000_000,1_000_000,1_000_000,2_000_000,1_000_000,1_000_000));
        check(playback.worldAtSource(1_000_000)==2_000_000,"Audio notices use the same configured source warp as animations");
        var output=new com.google.gson.JsonObject();output.addProperty("schema_version",1);output.addProperty("adapter","existing project OGG to original TAE/FXR semantics; no original Wwise audio");
        output.addProperty("blade_profile_policy","original FK blade-root-relative direction at 16667us; existing authored mix formula; offline export only");
        output.addProperty("source_contract_sha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of("src/main/resources/assets/elder_bosses/boss/promised_consort/source_contracts.json")))));
        output.add("event_bindings",report);
        Files.writeString(asset("boss/promised_consort/source_audio_bindings.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(output)+"\n");
        System.out.println("Audio events="+report.size()+" counts="+counts+"; slowest initial plan="+(slowest/1_000_000.0)+"ms tae="+slowestTae);
    }
    private static void incomingPerformanceChecks() {
        var builder=new net.neoforged.neoforge.common.ModConfigSpec.Builder();
        new com.tonywww.elder_bosses.platforms.config.PromisedConsortConfigValues(builder);
        var spec=builder.build();
        for(String removed:List.of("damage_routing","resistance","source_multiplier","status"))
            check(spec.getSpec().get("promised_consort."+removed)==null,"Removed incoming adapter is not configurable: "+removed);
        net.neoforged.neoforge.common.ModConfigSpec.ValueSpec distance=spec.getSpec().get("promised_consort.targeting.ranged_damage_distance");
        check(distance!=null && ((Number)distance.getDefault()).doubleValue()==9,"Distant damage uses the established 9-block default");
        check(distance.test(12.0) && !distance.test(0.0) && !distance.test(-1.0),"Distance threshold accepts custom positive values only");
        var ranged=com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortRangedConfig.defaults();
        var beam=damageSource(Set.of("elder_bosses:magic"));
        check(!com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedDamage.ranged(beam,ranged,-1,9),"Unknown owner does not create a distant attack");
        check(!com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedDamage.ranged(beam,ranged,80.99,9),"Nearby beam remains near range");
        check(com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedDamage.ranged(beam,ranged,81,9),"Distant non-projectile damage is ranged at the threshold");
        check(!com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedDamage.ranged(beam,ranged,100,12),"Custom distance threshold is used");
        check(com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedDamage.ranged(damageSource(Set.of("minecraft:is_projectile")),ranged,0,9),"Existing projectile classification remains active at close range");
        for(String excluded:List.of("non_ranged","forced_death","poison","wither","bleed_trigger","frost_trigger"))
            check(!com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedDamage.ranged(damageSource(Set.of("elder_bosses:"+excluded)),ranged,10000,9),"Distant passive/explicitly excluded damage does not trigger a reaction: "+excluded);
        var ffx=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceFfx.get();
        for(int id:List.of(652222,652252,652263,652285,652295)) for(var node:ffx.nodes(id)) {
            for(var action:node.actions()) {
                String name=action.get("name").getAsString();
                var original=node.actions().stream().filter(a->a.get("name").getAsString().equals(name)).findFirst().orElseThrow();
                check(node.action(name)==original,"Indexed action preserves original first-match priority");
            }
            var appearance=node.actions().stream().filter(a->a.get("appearance").getAsBoolean()).findFirst().orElse(null);
            check(node.appearance()==appearance,"Indexed appearance preserves source selection");
            var parents=ffx.nodes(id).stream().filter(p->p.path().endsWith("/Effects/0") && p.action("Appearance")==null
                    && node.path().startsWith(p.path().substring(0,p.path().length()-10)+"/Containers/")).toList();
            check(node.parents().equals(parents),"Cached parent transforms preserve source order");
            var sample=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceFfx.particle(node,.4,.7,12345);
            check(Double.isFinite(sample.x()+sample.y()+sample.z()+sample.gravity()),"Indexed emitter produces a finite trajectory");
        }
    }
    @SuppressWarnings("unchecked")
    private static net.minecraft.world.damagesource.DamageSource damageSource(Set<String> tags) {
        var key=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                com.tonywww.elder_bosses.platforms.PlatformResourceLocation.parse("minecraft:arrow"));
        var holder=(net.minecraft.core.Holder<net.minecraft.world.damagesource.DamageType>)java.lang.reflect.Proxy.newProxyInstance(
                SourceTimelineCheck.class.getClassLoader(),new Class[]{net.minecraft.core.Holder.class},(proxy,method,arguments)->switch(method.getName()) {
                    case "is" -> arguments[0] instanceof net.minecraft.tags.TagKey<?> tag ? tags.contains(tag.location().toString()) : arguments[0].equals(key);
                    case "unwrapKey" -> java.util.Optional.of(key);
                    case "value" -> new net.minecraft.world.damagesource.DamageType("test",0.1F);
                    case "kind" -> net.minecraft.core.Holder.Kind.REFERENCE;
                    case "isBound", "canSerializeIn" -> true;
                    case "tags" -> java.util.stream.Stream.empty();
                    case "toString" -> "incoming_check_source";
                    default -> null;
                });
        return new net.minecraft.world.damagesource.DamageSource(holder);
    }
    private static void recoveryEffectsChecks() throws Exception {
        var rig=com.google.gson.JsonParser.parseString(Files.readString(asset("boss/promised_consort/rig/source_bone_map.json"))).getAsJsonObject();
        var idle=PromisedConsortSourceAssets.pose(20).sample(0);
        for(int id:new int[]{3006,3025}) {
            var pose=PromisedConsortSourceAssets.pose(id);
            var end=pose.sample(PromisedConsortSourceAssets.bank().requireClip(id).durationMicros());
            double max=0;
            for(var value:rig.getAsJsonArray("bones")) {
                int index=value.getAsJsonObject().get("source_index").getAsInt();if(index==0) continue;
                var a=end.joint(index);var b=idle.joint(index);var root=end.joint(0);var rest=idle.joint(0);
                double x=a.x()-root.x()-b.x()+rest.x(),y=a.y()-root.y()-b.y()+rest.y(),z=a.z()-root.z()-b.z()+rest.z();
                max=Math.max(max,Math.sqrt(x*x+y*y+z*z));
            }
            check(max<.0001,"Terminal recovery matches idle joints: "+id+" error="+max);
        }
        double fadeIn=.15,fadeOut=.4,life=.7;
        double previous=0;
        for(int i=0;i<=10;i++) {
            double alpha=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceFfx.opacity(fadeIn*i/10,life,fadeIn,fadeOut);
            check(alpha>=previous && alpha<=1,"Holy fade in is bounded and monotonic");previous=alpha;
        }
        previous=1;
        for(int i=0;i<=10;i++) {
            double alpha=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceFfx.opacity(life-fadeOut+fadeOut*i/10,life,fadeIn,fadeOut);
            check(alpha<=previous && alpha>=0,"Holy fade out is bounded and monotonic");previous=alpha;
        }
        check(previous==0,"Holy effect is transparent at expiry");
        var defaults=com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSourceConfigSnapshot.defaults();
        check(defaults.number("hit_detection.trajectory_radius_multiplier")>1 && defaults.number("hit_detection.trajectory_hilt_extension")>0 && defaults.number("hit_detection.trajectory_arm_extension")>0,"Current defaults broaden sword and arm collision");
        check(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSourceConfigSnapshot.read(binaryNbt(defaults.save())).equals(defaults),"Current skill snapshot survives NBT");
    }
    private static void trajectoryOnceChecks() {
        var hits=new com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceSegmentHits();
        var actor=new Actor(12,0,-1,3002,3002,413);var player=new java.util.UUID(1,2);var otherPlayer=new java.util.UUID(1,3);
        long now=1_000_000,interval=500_000;
        check(hits.available(actor,player,now) && hits.claim(actor,player,now,interval),"First contact is allowed");
        // Separate windows, capsule samples and child bullet serials retain this same actor identity.
        for(int i=0;i<8;i++) check(!hits.available(actor,player,now+interval-1) && !hits.claim(actor,player,now+interval-1,interval),"All contacts share the immunity interval");
        check(hits.claim(actor,otherPlayer,now,interval),"Players have independent contact immunity");
        check(hits.expire(now+interval-1).isEmpty(),"Immunity is retained up to its boundary");
        check(hits.expire(now+interval).size()==2 && hits.claim(actor,player,now+interval,interval),"Same segment can hit again after half a second");
        var next=new Actor(12,1,-1,3003,3003,413);
        check(hits.nextSegment(next).size()==1 && hits.available(actor,player,now+interval),"Next segment removes old body immunity before timeout");
        check(hits.claim(next,player,now+interval,interval),"Next source segment can hit the same player");
        for(int slot=0;slot<4;slot++) {var clone=new Actor(12,0,slot,20002,20002,413);check(hits.claim(clone,player,now+interval,interval) && !hits.claim(clone,player,now+interval,interval),"Clone slots have independent timed immunity");}
        check(hits.claim(actor,player,now+interval,interval),"Old delayed projectile may contact after next-segment reset");
        var gson=new com.google.gson.Gson();var type=new com.google.gson.reflect.TypeToken<List<com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceSegmentHits.Claim>>(){}.getType();
        List<com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceSegmentHits.Claim> saved=gson.fromJson(gson.toJson(hits.save()),type);
        var restored=new com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceSegmentHits();restored.restore(saved,2_000_000);
        check(!restored.claim(actor,player,now+interval+2_000_000,interval) && !restored.claim(next,player,now+interval+2_000_000,interval),"Save/restore shifts immunity to the resumed clock");
        // The body may have ended while its delayed bullets still carry the old actor.
        restored.retain(Set.of(actor,next));check(!restored.claim(actor,player,now+interval+2_000_000,interval),"Live delayed projectile retains timed contact immunity");
        check(restored.save().stream().allMatch(c->c.actor().equals(actor) || c.actor().equals(next)),"Finished actors are pruned");
        restored.retain(Set.of(next));check(restored.available(actor,player,now+interval+2_000_000) && !restored.available(next,player,now+interval+2_000_000),"Pruning one finished segment never resets the next");
        restored.clear();check(restored.save().isEmpty(),"Encounter cleanup releases all claims");
    }
    private static void projectileBearingChecks() {
        for(var direction:List.of(new net.minecraft.world.phys.Vec3(0,0,1),new net.minecraft.world.phys.Vec3(0,0,-1),new net.minecraft.world.phys.Vec3(1,0,0),new net.minecraft.world.phys.Vec3(-1,0,0))) {
            var down=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceProjectiles.launchDirection(direction,-30,-60);
            var up=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceProjectiles.launchDirection(direction,-30,30);
            check(Math.abs(down.y+Math.sin(Math.PI/3))<1e-8 && Math.abs(up.y-.5)<1e-8,"Local pitch stays up/down for each world facing");
            check(Math.abs(down.length()-1)<1e-8,"Launch bearing remains a unit vector");
            var yawed=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceProjectiles.launchDirection(direction,-30,0);
            check(Math.abs(yawed.dot(direction)-Math.cos(Math.PI/6))<1e-7,"Source horizontal angle is applied");
        }
        var defaults=com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSourceConfigSnapshot.defaults();
        check(defaults.flag("entries.act14.invulnerable"),"Light of Miquella defaults to invulnerable");
        check(defaults.number("hit_detection.segment_immunity_ticks")==10,"Default contact protection is half a second");
        check(defaults.flag("animations.a3028.targeted_landing") && defaults.flag("animations.a3032.targeted_landing"),"Dive and side dash retain mandatory target anchoring");
        check(defaults.number("projectiles.a205220400.flight_height")==1.4,"Holy ring remains at player contact height");
        check(defaults.number("projectiles.a205220950.shootAngleXZ")==-30,"Side dash preserves its source horizontal fan");
        check(defaults.number("visuals.meteor_size")==1.35 && !defaults.flag("grab.instant_kill_enabled"),"Source defaults match the current run preset");
    }
    private static void arenaPolishChecks() throws Exception {
        var defaults=com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSourceConfigSnapshot.defaults();
        check(defaults.number("visuals.meteor_size")==1.35,"Meteor display uses the current run preset");
        check(defaults.number("visuals.holy_width_multiplier")==3,"Holy thickness has a current adjustable default");
        check(!defaults.flag("grab.instant_kill_enabled"),"Current preset disables instant grab death");
        var flags=new java.util.HashMap<>(defaults.flags());flags.put("grab.instant_kill_enabled",false);
        var harmless=new com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSourceConfigSnapshot(defaults.numbers(),flags);
        check(!com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSourceConfigSnapshot.read(binaryNbt(harmless.save())).flag("grab.instant_kill_enabled"),"Disabled grab death survives encounter snapshot NBT");
        var builder=new net.neoforged.neoforge.common.ModConfigSpec.Builder();new com.tonywww.elder_bosses.platforms.config.PromisedConsortConfigValues(builder);var spec=builder.build();
        net.neoforged.neoforge.common.ModConfigSpec.ValueSpec death=spec.getSpec().get("promised_consort.encounter.rejoin_after_death");
        check(death.getDefault().equals(false),"Current preset disables encounter rejoin after death");
        net.neoforged.neoforge.common.ModConfigSpec.ValueSpec grab=spec.getSpec().get("promised_consort.skills.grab.instant_kill_enabled");
        check(grab!=null && grab.getDefault().equals(false),"Grab death switch belongs to the main common config");
        var bank=defaults.configure(PromisedConsortSourceAssets.bank());
        var zeroMotion=new PromisedConsortSourceMotion.Displacement(0,0,0);
        for(int id:new int[]{20002,20003,20004,20005,20006}) {
            var clip=bank.requireClip(id);var actor=new Actor(1,0,0,id,clip.hkxId(),413);
            var playback=new PromisedConsortSourcePlayback(actor,0,1,0,defaults.warp(id));
            var strike=clip.events().stream().filter(e->e.type()==1).findFirst().orElseThrow();
            long contact=playback.worldAtSource(strike.startMicros()),end=playback.worldAtSource(strike.endMicros());
            long begin=playback.worldAtSource(clip.events().stream().filter(e->e.type()==760).mapToLong(Event::startMicros).min().orElse(0));
            var origin=new net.minecraft.world.phys.Vec3(0,12,-24);
            var poseAtContact=PromisedConsortSourceAssets.pose(clip.hkxId());
            double sole=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceGrounding.soleY(poseAtContact.sample(strike.startMicros()),poseAtContact.masterTranslationDelta(strike.startMicros()).y());
            var landing=new net.minecraft.world.phys.Vec3(0,-sole,-defaults.number(defaults.segment(id)+"target_standoff"));
            var correction=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceLanding.cloneCorrection(origin,playback.displacement(contact,0),landing);
            check(defaults.flag(defaults.segment(id)+"targeted_landing")== (id==20006),"Clone targeting matches current preset: "+id);
            check(origin.add(playback.displacement(contact,0)).add(correction).distanceToSqr(landing)<1e-15,"Airborne summon lands at corrected endpoint: "+id);
            var footprint=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceGroundAreas.configured(defaults,id,strike.index(),
                    new PromisedConsortSourcePose.Point(0,64,landing.z),playback.yawAt(contact,0));
            check(footprint.contains(0,64,0),"Simple clone footprint hits the floor target: "+id);
            double closest=Double.POSITIVE_INFINITY;boolean hit=false;String nearest="";
            for(long at=contact;at<end;at+=2_500) {
                double amount=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceLanding.progress(at,begin,contact);
                var p=origin.add(playback.displacement(at,0)).add(correction.scale(amount));
                var master=PromisedConsortSourceAssets.pose(clip.hkxId()).masterTranslationDelta(playback.sourceMicros(at));
                double yaw=playback.yawAt(at,0),radians=Math.toRadians(yaw),sin=Math.sin(radians),cos=Math.cos(radians);
                var poseOrigin=new PromisedConsortSourcePose.Point(p.x-master.x()*cos-master.z()*sin,p.y-master.y(),p.z-master.x()*sin+master.z()*cos);
                var capsules=PromisedConsortSourceAssets.hitVolumes().capsules(id,strike.index(),PromisedConsortSourceAssets.attachments(),PromisedConsortSourceAssets.pose(clip.hkxId()).sample(playback.sourceMicros(at)),poseOrigin,zeroMotion,yaw);
                for(var c:capsules) {
                    double dist=PromisedConsortSourceCollision.distanceSquared(c.first(),c.second(),new PromisedConsortSourcePose.Point(-.3,0,-.3),new PromisedConsortSourcePose.Point(.3,1.8,.3));
                    double clearance=Math.sqrt(dist)-c.radius();if(clearance<closest) {closest=clearance;nearest=c.first()+" -> "+c.second();}hit|=dist<=c.radius()*c.radius();
                }
            }
            System.out.println("Clone "+id+": hit="+hit+"; sole="+sole+"; closest clearance="+closest+"; capsule "+nearest);
            check(hit,"Corrected original clone capsules reach the player: "+id+"; closest clearance="+closest);
        }
    }
    private static void presentationPolishChecks() {
        var defaults=com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSourceConfigSnapshot.defaults();
        check(defaults.number("entries.act21.cooldown_ticks")==1200,"Current run preset repeats after a sixty-second cooldown");
        long once=com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.meteorReadyAt("once",100,0);
        check(once==Long.MAX_VALUE,"Once mode closes the release gate");
        check(com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.meteorReadyAt("cooldown_forced",100,0)==Long.MAX_VALUE,"Zero cooldown cannot cause an immediate repetition loop");
        check(!com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.meteorAvailable(true,false,true,10000,once),"Completed meteor cannot repeatedly select itself");
        long repeat=com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.meteorReadyAt("cooldown_forced",100,1200);
        check(repeat==1300,"Repeat cooldown starts after completion");
        check(!com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.meteorAvailable(true,false,true,1299,repeat),"Repeat cannot start before cooldown");
        check(com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.meteorAvailable(true,false,true,1300,repeat),"Explicit repeat becomes available at boundary");
        var contact=new net.minecraft.world.phys.Vec3(12,64,-8);
        var authoredContact=new net.minecraft.world.phys.Vec3(7,18,-20);
        check(com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceLanding.anchoredPosition(contact,authoredContact,authoredContact).equals(contact),"Root motion lands exactly at the fixed arena anchor");
        var approach=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceLanding.anchoredPosition(contact,net.minecraft.world.phys.Vec3.ZERO,authoredContact);
        check(approach.add(authoredContact).distanceToSqr(contact)<1e-15,"Warp compensates authored travel before contact");
        check(com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.meteorThreshold(1600,.85)==1360,
                "First meteor gates at 85% remaining total health");
        check(com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.meteorThresholdReached(0,1600,.85,true,.65),
                "A lethal first-phase hit stops at the higher meteor gate before the phase-two transition");
        check(!com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.meteorThresholdReached(1361,1600,.85,true,.65),"First meteor does not trigger above 85%");
        check(!com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.meteorThresholdReached(0,1600,.5,true,.65),"A configured lower meteor threshold waits for the phase-two health gate");
        check(com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.meteorThresholdReached(800,1600,.5,false,.65),"A configured lower threshold can trigger in phase two");
        check(!com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.meteorAvailable(true,false,false,1000,0),
                "First meteor cannot be selected before its health request");
        var outside=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceLanding.boundedPosition(
                approach.add(100,30,-200),contact,38);
        check(outside.subtract(contact).multiply(1,0,1).length()<=38.000001,"Warp and descent stay inside the arena with the boss footprint reserved");
        var landed=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceLanding.boundedPosition(
                com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceLanding.anchoredPosition(contact,authoredContact,authoredContact),contact,38);
        check(landed.equals(contact),"Absolute descent returns to the centre even when an earlier position was clamped");
        var low=new com.tonywww.elder_bosses.client.indicator.ClientIndicatorGeometry.Vertex(0,60,0);
        var high=new com.tonywww.elder_bosses.client.indicator.ClientIndicatorGeometry.Vertex(5,70,3);
        var mesh=new com.tonywww.elder_bosses.client.indicator.ClientIndicatorGeometry.Mesh(
                List.of(new com.tonywww.elder_bosses.client.indicator.ClientIndicatorGeometry.Quad(low,high,high,low)),
                List.of(new com.tonywww.elder_bosses.client.indicator.ClientIndicatorGeometry.Line(low,high)),List.of());
        var flat=com.tonywww.elder_bosses.client.indicator.ClientIndicatorGeometry.flat(mesh,64);
        check(flat.fills().get(0).first().y()==64 && flat.fills().get(0).second().y()==64
                && flat.borders().get(0).from().y()==64 && flat.borders().get(0).to().y()==64,"Warning fills and borders share one flat plane");
    }
    private static void meteorLandingChecks() {
        var pose=PromisedConsortSourceAssets.pose(3017);
        var motion=PromisedConsortSourceAssets.bank().motions().get(3017);
        var warp=com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSourceConfigSnapshot.defaults().warp(3017);
        long begin=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceLanding.GRAVITY_DESCENT_BEGIN;
        long end=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceLanding.GRAVITY_DESCENT_END;
        double beginY=motion.displacement(begin,0).y()+pose.masterTranslationDelta(begin).y();
        double endY=motion.displacement(end,0).y()+pose.masterTranslationDelta(end).y();
        double beginSole=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceGrounding.soleY(pose.sample(begin),pose.masterTranslationDelta(begin).y());
        double previous=Double.NaN,maxStep=0;
        for(long tick=warp.gameAt(begin);tick<=warp.gameAt(8_000_000);tick+=50_000) {
            long source=warp.sourceAt(tick);
            double authored=motion.displacement(source,0).y()+pose.masterTranslationDelta(source).y();
            double root=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceLanding.gravityDescentY(source,authored,beginY,endY);
            double sole=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceGrounding.soleY(pose.sample(source),pose.masterTranslationDelta(source).y());
            double offset=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceGrounding.gravityMeteorOffset(source);
            check(Math.abs(offset-com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceLanding.gravityBodyOffset(source,beginSole,sole))<1e-9,"Foot-only render sampler agrees with the full authoritative pose");
            double visibleHeight=root-endY+sole+offset;
            check(visibleHeight>=-1e-6,"Meteor feet do not descend below the floor");
            if(Double.isFinite(previous)) {
                check(visibleHeight<=previous+1e-6,"Landing stays monotonic despite changes in the source Root/leg pose");
                maxStep=Math.max(maxStep,Math.abs(previous-visibleHeight));
            }
            if(source>=end) check(Math.abs(visibleHeight)<1e-6,"Meteor feet stay on the floor through the remaining recovery");
            previous=visibleHeight;
        }
        check(maxStep<.6,"No single-tick landing snap in the actual root/body tracks: "+maxStep);
        check(com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceLanding.gravityDescent(5_433_333)==0,"Meteor launches complete before descent adaptation");
        check(Math.abs(warp.gameAt(end)-warp.gameAt(begin)-3_000_000)<=2,"Current half-speed recovery gives a three-second descent");
    }
    private static void dashContactChecks() {
        var defaults=com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSourceConfigSnapshot.defaults();
        check(defaults.flag("animations.a3031.targeted_landing"),"Forward dash target anchoring is mandatory");
        check(defaults.number("animations.a3031.target_standoff")==1.5,"Forward dash stops closer to the locked player");
        var original=PromisedConsortSourceAssets.bank();var configured=defaults.configure(original);
        var dashRaw=original.requireClip(3031).events().stream().filter(e->e.type()==1).findFirst().orElseThrow();
        var dash=configured.requireClip(3031).events().stream().filter(e->e.type()==1).findFirst().orElseThrow();
        check(dashRaw.startMicros()==2_566_667 && dash.startMicros()==2_266_667 && dash.endMicros()==dashRaw.endMicros(),"Forward dash contact starts with travel while preserving the original strike evidence");
        var dashCursor=new Cursor(configured.requireClip(3031),new Actor(1,0,-1,3031,3031,412));
        dashCursor.advance(2_300_000);
        check(dashCursor.activeSpans().contains(dash),"Forward dash can hit during travel, before its final swing");
        var start=new net.minecraft.world.phys.Vec3(0,1.5,0);
        var end=new net.minecraft.world.phys.Vec3(0,0,50);
        var locked=new net.minecraft.world.phys.Vec3(0,0,10);
        double previous=-1;
        for(int z=0;z<=55;z++) {
            var p=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceLanding.dashPosition(start,new net.minecraft.world.phys.Vec3(0,0,z),end,locked);
            check(p.z>=previous && p.z<=10 && p.y>=0 && p.y<=1.5,"Large authored dash remains monotonic and inside the locked route");previous=p.z;
        }
        var meteor=defaults.warp(3017);
        long tail=meteor.sourceWindup()+meteor.sourceActive();
        check(tail==5_433_333 && Math.abs(meteor.gameAt(tail)-tail)<=1,"Meteor launch timing remains unchanged");
        check(Math.abs(meteor.gameAt(tail+1_000_000)-meteor.gameAt(tail)-2_000_000)<=2,"Meteor aerial recovery plays at half speed on the shared clock");
        var raw=original.requireClip(3024).events().stream().filter(e->e.type()==1).findFirst().orElseThrow();
        var charge=configured.requireClip(3024).events().stream().filter(e->e.type()==1).findFirst().orElseThrow();
        check(raw.endMicros()==4_366_667,"Original meteor attack evidence remains unchanged");
        check(charge.index()==raw.index() && charge.startMicros()==raw.startMicros() && charge.endMicros()==4_966_667,"Charge contact continues through its source gate");
        var clip=configured.requireClip(3024);var cursor=new Cursor(clip,new Actor(1,0,-1,3024,clip.hkxId(),412));
        cursor.advance(4_600_000);
        check(cursor.activeSpans().contains(charge),"First-phase meteor still deals contact damage during the charge");
        cursor.advance(4_966_667);check(!cursor.activeSpans().contains(charge),"Charge contact ends before the remainder of recovery");
        var a=new PromisedConsortSourcePose.Point(0,1,0);var b=new PromisedConsortSourcePose.Point(30,1,0);
        var before=new PromisedConsortSourceHitVolumes.Capsule(0,0,a,a,2.5,0,0);
        var after=new PromisedConsortSourceHitVolumes.Capsule(0,0,b,b,2.5,0,0);
        var swept=PromisedConsortSourceHitVolumes.sweptSpheres(List.of(before),List.of(after)).get(0);
        check(PromisedConsortSourceCollision.distanceSquared(swept.first(),swept.second(),new PromisedConsortSourcePose.Point(14.7,0,-.3),new PromisedConsortSourcePose.Point(15.3,1.8,.3))<=swept.radius()*swept.radius(),"Swept charge catches a player between two distant samples");
        check(PromisedConsortSourceCollision.distanceSquared(swept.first(),swept.second(),new PromisedConsortSourcePose.Point(14.7,0,4),new PromisedConsortSourcePose.Point(15.3,1.8,4.6))>swept.radius()*swept.radius(),"Charge does not hit a player outside the configured path radius");
        var bladeBefore=new PromisedConsortSourceHitVolumes.Capsule(0,0,a,new PromisedConsortSourcePose.Point(0,1,3),.7,0,0);
        var bladeAfter=new PromisedConsortSourceHitVolumes.Capsule(0,0,b,new PromisedConsortSourcePose.Point(30,1,3),.7,0,0);
        var blades=PromisedConsortSourceHitVolumes.sweptBlades(List.of(bladeBefore),List.of(bladeAfter));
        check(blades.stream().anyMatch(c->PromisedConsortSourceCollision.distanceSquared(c.first(),c.second(),new PromisedConsortSourcePose.Point(14.7,0,-.3),new PromisedConsortSourcePose.Point(15.3,1.8,.3))<=c.radius()*c.radius()),"Swept blade catches a player between dash samples");
        check(blades.stream().noneMatch(c->PromisedConsortSourceCollision.distanceSquared(c.first(),c.second(),new PromisedConsortSourcePose.Point(14.7,0,5),new PromisedConsortSourcePose.Point(15.3,1.8,5.6))<=c.radius()*c.radius()),"Swept blade respects its contact radius");
        // Real source poses must cover players along the corrected dash, not only its endpoint.
        var playback=new PromisedConsortSourcePlayback(new Actor(1,0,-1,3031,3031,412),0,1);
        var begin=playback.displacement(dash.startMicros(),0);
        var authoredEnd=playback.displacement(dashRaw.startMicros(),0);
        var destination=new net.minecraft.world.phys.Vec3(0,authoredEnd.y,20-defaults.number("animations.a3031.target_standoff"));
        boolean routeHit=true;
        for(int playerZ:new int[]{8,12,16,20}) {
            boolean exactHit=false,simpleHit=false;
            double closest=Double.POSITIVE_INFINITY;String closestPose="";
            var beforeCaps=List.<PromisedConsortSourceHitVolumes.Capsule>of();
            for(long at=dash.startMicros();at<dash.endMicros();at+=8_333) {
                var movement=playback.displacement(at,0);
                var moved=at>=dashRaw.startMicros()?destination.add(0,movement.y-authoredEnd.y,0):
                        com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceLanding.dashPosition(begin,movement,authoredEnd,destination);
                var pose=PromisedConsortSourceAssets.pose(3031);var sample=pose.sample(at);var master=pose.masterTranslationDelta(at);
                double offset=at>=dashRaw.startMicros()?-com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceGrounding.soleY(sample,master.y()):0;
                var anchor=new PromisedConsortSourcePose.Point(moved.x,moved.y-authoredEnd.y,moved.z);
                var poseOrigin=new PromisedConsortSourcePose.Point(anchor.x()-master.x(),anchor.y()+offset-master.y(),anchor.z()+master.z());
                var caps=PromisedConsortSourceAssets.hitVolumes().capsules(3031,dash.index(),PromisedConsortSourceAssets.attachments(),sample,poseOrigin,new PromisedConsortSourceMotion.Displacement(0,0,0),0).stream()
                        .map(c->new PromisedConsortSourceHitVolumes.Capsule(c.primitiveSlot(),c.sourcePointIndex(),c.first(),c.second(),defaults.number("attacks.a5220380.hit"+c.primitiveSlot()+"_Radius")*defaults.number("hit_detection.trajectory_radius_multiplier"),c.hitType(),c.priority())).toList();
                if(at<dashRaw.startMicros()) {
                    var contacts=new ArrayList<>(caps);
                    contacts.add(PromisedConsortSourceHitVolumes.dashBody(anchor,4,caps.stream().mapToDouble(PromisedConsortSourceHitVolumes.Capsule::radius).max().orElseThrow()));
                    caps=contacts;
                }
                for(var c:PromisedConsortSourceHitVolumes.sweptBlades(beforeCaps,caps)) {
                    double clearance=Math.sqrt(PromisedConsortSourceCollision.distanceSquared(c.first(),c.second(),new PromisedConsortSourcePose.Point(-.3,0,playerZ-.3),new PromisedConsortSourcePose.Point(.3,1.8,playerZ+.3)))-c.radius();
                    exactHit|=clearance<=0;
                    if(clearance<closest) {closest=clearance;closestPose=at+" body="+anchor+" blade="+c.first()+".."+c.second();}
                }
                beforeCaps=caps;
                simpleHit|=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceGroundAreas.configured(defaults,3031,dash.index(),new PromisedConsortSourcePose.Point(anchor.x(),0,anchor.z()),0).contains(0,0,playerZ);
            }
            if(!exactHit || !simpleHit) System.out.println("Dash route z="+playerZ+" clearance="+closest+" "+closestPose);
            routeHit&=exactHit && simpleHit;
        }
        check(routeHit,"Real forward dash contacts both modes along the route and at its endpoint");
    }
    private static void combatFixChecks() {
        check(com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.meteorAvailable(true,true,true,100,Long.MAX_VALUE),"Pending threshold request is selectable despite one-shot bookkeeping");
        check(!com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.meteorAvailable(true,false,true,100,Long.MAX_VALUE),"Completed one-shot meteor stays unavailable");
        check(!com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.meteorAvailable(false,true,false,100,0),"Disabled meteor never gates selection");
        check(com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.meteorAvailable(true,false,true,200,200),"Repeating meteor becomes ready exactly at its cooldown boundary");
        for(long now:new long[]{50_000,5_000_000,60_000_000}) check(!PromisedConsortSourceAi.shootReady(now,0,5_000_000,true),"Continuous arrows cannot replace an in-flight reaction");
        check(!PromisedConsortSourceAi.shootReady(9_999_999,5_000_000,5_000_000,false),"Counter completion starts cooldown");
        check(PromisedConsortSourceAi.shootReady(10_000_000,5_000_000,5_000_000,false),"Cooldown expires without requiring a melee hit");
    }
    private static void groundEffectsChecks() throws Exception {
        var defaults=com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSourceConfigSnapshot.defaults();
        check(defaults.flag("hit_detection.simple_ranges"),"Simple area mode is the current default");
        var flags=new java.util.HashMap<>(defaults.flags());flags.put("hit_detection.simple_ranges",false);
        var exact=new com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSourceConfigSnapshot(defaults.numbers(),flags);
        check(!exact.flag("hit_detection.simple_ranges"),"Config switches to source trajectory mode");
        var builder=new net.neoforged.neoforge.common.ModConfigSpec.Builder();new com.tonywww.elder_bosses.platforms.config.PromisedConsortConfigValues(builder);var spec=builder.build();
        for(var value:com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSourceConfigSnapshot.definition().getAsJsonArray("fields")) {
            var row=value.getAsJsonObject();String path=row.get("path").getAsString();
            if(!path.startsWith("ground_areas.") && !path.startsWith("spacing.") && !path.startsWith("visuals.") && !path.contains("simple_") && !path.endsWith("warning_length") && !path.startsWith("hit_detection.")) continue;
            net.neoforged.neoforge.common.ModConfigSpec.ValueSpec registered=spec.getSpec().get("promised_consort.skills."+path);
            check(registered!=null,"New current field registered: "+path);
            check(row.get("type").getAsString().equals("boolean")?registered.getDefault().equals(row.get("default").getAsBoolean()):((Number)registered.getDefault()).doubleValue()==row.get("default").getAsDouble(),"New default matches contract: "+path);
        }
        var snapshot=defaults.save();check(snapshot.getByteArray("Snapshot").length>65_535,"Regression fixture exceeds NBT UTF limit");
        check(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSourceConfigSnapshot.read(binaryNbt(snapshot)).equals(defaults),"Full current config survives actual binary NBT write/read");
        var keys=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceGroundAreas.keys();
        int events=0;for(var clip:PromisedConsortSourceAssets.bank().clips().values()) for(var e:clip.events()) if(e.type()==1) {events++;check(keys.contains("a"+clip.taeId()+"_e"+e.index()),"Every original attack/grab event has an authored ground shape");}
        check(keys.size()==events && events==57,"No unmapped or surplus attack envelopes");
        var point=new PromisedConsortSourcePose.Point(10,64,10);
        var sector=new com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceGroundAreas.Area(com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.ShapeType.SECTOR,point,0,8,1,120,4);
        check(sector.contains(10,64,18) && !sector.contains(10,64,18.01),"Sector radial boundary");
        check(!sector.contains(10,64,9) && !sector.contains(10,69,13),"Sector rejects rear and excessive height");
        var rectangle=new com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceGroundAreas.Area(com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.ShapeType.RECTANGLE,point,90,10,4,360,4);
        check(rectangle.contains(1,64,11.9) && !rectangle.contains(1,64,12.1) && !rectangle.contains(11,64,10),"Rotated forward rectangle boundaries");
        var circle=new com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceGroundAreas.Area(com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.ShapeType.CIRCLE,point,0,6,1,360,6);
        check(circle.contains(4,64,10) && !circle.contains(3.99,64,10),"Circular landing footprint boundary");
        var actor=new Actor(1,0,-1,3000,3000,412);var key=new com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceIndicators.Key(actor,22);
        var danger=new com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceIndicators.Danger(key,0,50_000,100_000,200_000,true,sector,List.of());
        var gson=new com.google.gson.Gson();var saved=new com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceIndicators.SavedState(java.util.Collections.nCopies(1000,danger));
        var state=new net.minecraft.nbt.CompoundTag();byte[] bytes=gson.toJson(saved).getBytes(StandardCharsets.UTF_8);check(bytes.length>65_535,"Large combat warning fixture exceeds string limit");state.putByteArray("State",bytes);
        var restored=gson.fromJson(new String(binaryNbt(state).getByteArray("State"),StandardCharsets.UTF_8),com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceIndicators.SavedState.class);
        check(restored.dangers().equals(saved.dangers()),"Large combat areas retain lock/identity/geometry through binary save");
        check(!PromisedConsortSourceAssets.attachments().has(120) && PromisedConsortSourceAssets.attachments().has(300),"Crash dummy120 is absent; real sword anchor remains strict");
        String combat=Files.readString(Path.of("src/main/java/com/tonywww/elder_bosses/boss/promisedconsort/source/PromisedConsortSourceCombat.java"));
        check(combat.contains("tag.putByteArray(\"State\"") && combat.contains("getByteArray(\"State\")"),"Live combat uses byte arrays on both persistence paths");
        String visuals=Files.readString(Path.of("src/main/java/com/tonywww/elder_bosses/boss/promisedconsort/source/PromisedConsortSourceVisuals.java"));
        check(visuals.contains("frame.visualPoint(") && !visuals.contains("frame.point("),"All decorative FX/tracer anchors use the safe cosmetic resolution");
        var model=com.google.gson.JsonParser.parseString(Files.readString(asset("geo/entity/promised_consort.geo.json"))).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        int crown=0,layers=0;for(var b:model.getAsJsonArray("bones")) {var bone=b.getAsJsonObject();String name=bone.get("name").getAsString();if(name.equals("mesh_hair_volume_crown")) {crown=bone.getAsJsonArray("cubes").size();check(bone.get("parent").getAsString().equals("src_231_Miquella_Head"),"Crown follows animated Miquella head");} else if(name.startsWith("mesh_hair_volume_")) layers++;}
        check(crown==4 && layers==100,"Closed crown and additional moving hair layers packaged");
    }
    private static net.minecraft.nbt.CompoundTag binaryNbt(net.minecraft.nbt.CompoundTag tag) throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();net.minecraft.nbt.NbtIo.write(tag,new java.io.DataOutputStream(bytes));
        return net.minecraft.nbt.NbtIo.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())),net.minecraft.nbt.NbtAccounter.unlimitedHeap());
    }
    private static void priorityChecks() {
        var defaults=com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSourceConfigSnapshot.defaults();
        var definition=com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSourceConfigSnapshot.definition();
        check(definition.getAsJsonObject("entries").size()==22,"Only 22 original attack entries are registered");
        check(defaults.numbers().size()+defaults.flags().size()==definition.getAsJsonArray("fields").size(),"Every field is unique and present in the encounter snapshot");
        var builder=new net.neoforged.neoforge.common.ModConfigSpec.Builder();
        new com.tonywww.elder_bosses.platforms.config.PromisedConsortConfigValues(builder);
        var spec=builder.build();
        for(var field:definition.getAsJsonArray("fields")) {
            var row=field.getAsJsonObject();String path=row.get("path").getAsString();
            net.neoforged.neoforge.common.ModConfigSpec.ValueSpec registered=spec.getSpec().get("promised_consort.skills."+path);
            check(registered!=null,"Current config field is registered: "+path);
            Object actual=registered.getDefault();
            check(row.get("type").getAsString().equals("boolean")?actual.equals(row.get("default").getAsBoolean()):Math.abs(((Number)actual).doubleValue()-row.get("default").getAsDouble())<1e-9,"Registered default matches current contract: "+path);
        }
        check(spec.getSpec().get("promised_consort.skills.left_combo_cross")==null && spec.getSpec().get("promised_consort.ranged_counter")==null,"Removed project skill and ranged variant fields are not registered");
        check(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSourceConfigSnapshot.read(defaults.save()).equals(defaults),"Current config snapshot NBT roundtrip");
        var bank=defaults.configure(PromisedConsortSourceAssets.bank());
        for(var clip:bank.clips().values()) {
            var warp=defaults.warp(clip.taeId());
            for(long t:new long[]{0,clip.durationMicros()/2,clip.durationMicros(),clip.durationMicros()+100_000})
                check(warp.sourceAt(t)==t && warp.gameAt(t)==t,"Default original phase clock remains exact: "+clip.taeId());
        }
        var numbers=new java.util.LinkedHashMap<>(defaults.numbers());
        String prefix=defaults.segment(3010);
        numbers.put(prefix+"windup_ticks",defaults.number(prefix+"windup_ticks")*2);
        numbers.put(prefix+"active_ticks",defaults.number(prefix+"active_ticks")*3);
        numbers.put(prefix+"recovery_ticks",defaults.number(prefix+"recovery_ticks")*.5);
        var configured=new com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSourceConfigSnapshot(numbers,defaults.flags());
        var session=new PromisedConsortSourceSession(configured.configure(PromisedConsortSourceAssets.bank()));
        var update=session.start(PromisedConsortSourceAi.entry(5,context(8,.8,false),()->100),12,412,1_000_000,1);
        var playback=PromisedConsortSourcePlayback.of(update.started().get(0));
        var warp=configured.warp(3010);var clip=bank.requireClip(3010);
        check(playback.warp().equals(warp) && PromisedConsortSourcePlayback.decode(playback.encode()).equals(playback),"Configured clock is carried atomically to client pose playback");
        long source=clip.durationMicros()/2,world=playback.worldAtSource(source);
        check(Math.abs(playback.sourceMicros(world)-source)<=1,"Configured event and pose clocks agree");
        session.advance(world);
        check(Math.abs(session.snapshot(world).actors().get(0).sourceMicros()-source)<=1,"Session cursor follows the same configured clock");
        var resumed=new PromisedConsortSourceSession(configured.configure(PromisedConsortSourceAssets.bank()));resumed.restore(session.save(),2_000_000);
        check(resumed.advance(world+2_000_000).events().isEmpty(),"Retimed restore does not replay current attack events");
        numbers.put(prefix+"active_ticks",Double.NaN);boolean rejected=false;
        try {new com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSourceConfigSnapshot(numbers,defaults.flags());}catch(IllegalArgumentException expected) {rejected=true;}
        check(rejected,"Non-finite config is rejected before execution");
        int grids=0;
        var ffx=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceFfx.get();
        for(int id:List.of(652222,652252,652263,652285,652295)) for(var node:ffx.nodes(id)) for(var action:node.actions()) {
            if(com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceFfx.field(action,"totalFrames",0,1)<=1) continue;
            grids++;var cell=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceFfx.atlasCell(action,.4,.3,.2,12345);
            check(cell.u0()>=0 && cell.v0()>=0 && cell.u1()<=1 && cell.v1()<=1 && (cell.u1()-cell.u0())*(cell.v1()-cell.v0())<.99,"Original multi-frame texture selects a single sprite cell");
            check(cell.equals(com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceFfx.atlasCell(action,.4,.3,.2,12345)),"Particle frame is stable for the same source seed");
        }
        check(grids>0,"Original decoded FXR sprite sheets were exercised");
        var pose=PromisedConsortSourceAssets.pose(3010);
        for(long t:new long[]{0,800_000,1_333_333,1_733_333,clip.durationMicros()}) {
            double sole=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceGrounding.soleY(pose.sample(t),pose.masterTranslationDelta(t).y());
            check(Double.isFinite(sole),"Source heel and toe grounding remains finite across the jump");
        }
        for(int dummy:List.of(300,10,310,20)) check(!PromisedConsortSourceAssets.attachments().require(dummy).isEmpty(),"Full blade base/tip uses an original attachment: "+dummy);
    }

    private static void executionChecks(Path path) throws Exception {
        var bank=PromisedConsortSourceAssets.bank();var data=PromisedConsortSourceExecutionData.get();
        shootChecks(bank);
        var ffx=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceFfx.get();
        check(ffx.has(652295) && ffx.has(4080),"Original charm and bleed status FXR resources are present");
        for(var clip:bank.clips().values()) for(var event:clip.events()) if(event.type()==96 || event.type()==118) {
            int fx=PromisedConsortSourceExecutionData.integer(data.eventFields(clip.taeId(),event.index()),"FFX ID");
            check(fx<0 || ffx.has(fx),"Every positive source TAE special effect resolves: "+fx);
        }
        for(int fx:List.of(652222,652252,652263,652285,652295)) for(var node:ffx.nodes(fx)) {
            var a=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceFfx.particle(node,.4,.7,12345);
            check(Double.isFinite(a.x()+a.y()+a.z()+a.gravity()),"Decoded emitter/motion remains finite");
            check(a.equals(com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceFfx.particle(node,.4,.7,12345)),"Same source emission retains deterministic position");
        }
        for(int tae:List.of(20010,20011,20012,3033,3034,6000,6002,6003,8700)) check(data.behavior(tae).exitAtEnd(),"Resolved original exit "+tae);
        check(data.behavior(30010).looping() && !data.behavior(30010).exitAtEnd(),"Cutscene wait is a loop, not a timed combat opener");
        check(data.behavior(20).looping(),"Non-riding original idle selected");
        check(!data.hasBullet(205220301),"Original missing child bullet is not fabricated");
        check(data.effectDuration(20011573)==150 && data.effectDuration(20011575)==120 && data.effectDuration(20011598)==70,"Original finite cooldown effect lifetime");
        var min=new PromisedConsortSourcePose.Point(0,0,0);var max=new PromisedConsortSourcePose.Point(1,1,1);
        check(PromisedConsortSourceCollision.distanceSquared(new PromisedConsortSourcePose.Point(-1,.5,.5),new PromisedConsortSourcePose.Point(2,.5,.5),min,max)==0,"Capsule crosses box");
        double corner=PromisedConsortSourceCollision.distanceSquared(new PromisedConsortSourcePose.Point(-.9,-.9,.5),new PromisedConsortSourcePose.Point(-.9,-.9,.5),min,max);
        check(Math.abs(corner-1.62)<1e-9,"Rounded capsule corner rejects inflated-AABB false hit");
        check(PromisedConsortSourceCollision.distanceSquared(new PromisedConsortSourcePose.Point(.5,.5,.5),new PromisedConsortSourcePose.Point(.5,.5,.5),min,max)==0,"Zero-length inside capsule");
        var motion=bank.motions().get(3017);
        check(Math.abs(motion.yawDeltaDegrees(bank.requireClip(3017).durationMicros())+180)<.01,"Extracted fourth channel keeps original signed negative pi rotation");
        var entry=PromisedConsortSourceAi.entry(18,new PromisedConsortSourceAi.Context(5,.4,false,Set.of(20011599),Set.of()),()->100);
        var session=new PromisedConsortSourceSession(bank);session.start(entry,9,413,1_000_000,1);session.advance(2_500_000);
        var json=new com.google.gson.Gson();var saved=json.fromJson(json.toJson(session.save()),PromisedConsortSourceSession.SavedState.class);
        var resumed=new PromisedConsortSourceSession(bank);resumed.restore(saved,5_000_000);
        check(resumed.advance(7_500_000).events().isEmpty(),"Saved source cursor does not replay edges at resumed time");
        var expected=session.advance(2_550_000);var actual=resumed.advance(7_550_000);
        check(expected.events().stream().map(x->x.crossing().identity()).toList().equals(actual.events().stream().map(x->x.crossing().identity()).toList()),"Source clone identities survive restore");
        check(expected.events().stream().map(x->x.worldMicros()+5_000_000).toList().equals(actual.events().stream().map(x->x.worldMicros()).toList()),"Source restore rebases clocks once");
        var player=PromisedConsortSourcePlayerPose.get();
        check(player.matrix(player.bone("Head"),1_000_000).m31()>3,"Source player body lift is preserved");
        check(player.matrix(player.bone("Head"),9_333_333).m31()<1,"Source player descent is preserved");
        try(var reader=Files.newBufferedReader(path.getParent().resolve("source_player_grab_pose.json"))) {
            var doc=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            check(doc.getAsJsonObject("charm_death_gate").get("frame").getAsInt()==280,"Charm death uses original EZ state flag frame280");
            check(doc.getAsJsonObject("input_release_gate").get("frame").getAsInt()==290,"Player input unlock uses source frame290");
        }
    }
    private static void shootChecks(PromisedConsortSourceBank bank) {
        var near=PromisedConsortSourceAi.onShoot(context(8,.8,false),()->30,()->.5);
        check(!near.clearSubGoals() && near.sourceSegments().equals(List.of(3009)),"Near Shoot appends stomp without clearing old goals");
        check(PromisedConsortSourceAi.onShoot(context(8,.8,false),()->31,()->.5).sourceSegments().isEmpty(),"Near Shoot 31 does not invent a reaction");
        var walk=PromisedConsortSourceAi.onShoot(context(15,.8,false),()->70,()->.5);
        check(walk.clearSubGoals() && Math.abs(walk.approachSeconds()-1.4)<1e-9,"Medium Shoot 70 selects original .8..2 second walking goal");
        check(PromisedConsortSourceAi.onShoot(context(15.001,.8,false),()->30,()->0).approachSeconds()==.8,"Far Shoot 30 preserves its lower approach threshold");
        for(int roll:new int[]{25,26,50,51,75,76}) {
            int[] values={100,100,roll};int[] cursor={0};
            var shot=PromisedConsortSourceAi.onShoot(context(20,.4,false,20011599),()->values[cursor[0]++],()->.5);
            check(shot.sourceSegments().equals(List.of(roll<=25?3022:roll<=50?3023:roll<=75?3031:3032)),"Phase2 Shoot quartile boundary "+roll);
            check(cursor[0]==3,"Shoot keeps its unused second random roll");
        }
        var session=new PromisedConsortSourceSession(bank);
        session.start(PromisedConsortSourceAi.entry(1,context(5,.8,false),()->100),1,412,0,1);
        check(session.shoot(session.bodyActor().orElseThrow(),near),"Shoot accepts the actual body identity");
        check(session.snapshot(0).pendingSegments().equals(List.of(3001,3009)),"Shoot keeps the previous continuation before appended stomp");
        var far=new PromisedConsortSourceAi.Shoot(true,List.of(3032),Set.of(20011551),0);
        session.shoot(session.bodyActor().orElseThrow(),far);
        check(session.snapshot(0).pendingSegments().equals(List.of(3032)),"Far Shoot atomically replaces queued goals");
    }
    private static void playbackChecks(Path work) throws Exception {
        var bank=PromisedConsortSourceAssets.bank();
        check(bank.clips().size()==81,"Packaged source bank has every TAE identity");
        var rig=com.google.gson.JsonParser.parseString(Files.readString(asset("boss/promised_consort/rig/source_bone_map.json"))).getAsJsonObject();
        var bind=rig.getAsJsonArray("bones").get(0).getAsJsonObject().getAsJsonArray("bind_world_position");
        for (int tae : List.of(3010,3020,3026,20002,20012)) {
            int hkx=bank.requireClip(tae).hkxId();
            var playback=new PromisedConsortSourcePlayback(new Actor(7,2,tae==20002 ? 3 : -1,tae,hkx,413),1_025_000,1.5);
            check(PromisedConsortSourcePlayback.decode(playback.encode()).equals(playback),"Atomic actor/clock NBT roundtrip");
            check(playback.sourceMicros(1_000_000)==0,"Future actor start holds its first pose");
            check(playback.sourceMicros(1_075_000)==75_000,"Sub-tick start and speed survive sync");
            check(playback.animationTicks(21,.5)==1.5,"Client clock uses the clone's own sub-tick origin");
            check(playback.animationClip().endsWith(String.format(java.util.Locale.ROOT,"%06d",hkx)),"Imported TAE plays its actual HKX");
            var pose=PromisedConsortSourceAssets.pose(hkx);
            for (long local : new long[]{0,600_000,1_400_000,2_200_000}) {
                long world=playback.startWorldMicros()+(long)Math.ceil(local/playback.speed());
                long actualTime=playback.sourceMicros(world);
                var master=pose.masterTranslationDelta(actualTime);
                var joint=pose.sample(actualTime).joint(0);
                check(Math.abs(joint.x()-bind.get(0).getAsDouble()-master.x())<1e-9
                        && Math.abs(joint.y()-bind.get(1).getAsDouble()-master.y())<1e-9
                        && Math.abs(joint.z()-bind.get(2).getAsDouble()-master.z())<1e-9,
                        "Transferred Master delta matches full affine pose without removing rotation/scale");
                for (double yaw : new double[]{0,90}) {
                    var extracted=bank.motions().get(tae).displacement(actualTime,yaw);
                    var transferred=playback.displacement(world,yaw);
                    var worldJoint=pose.sample(actualTime).worldPoint(0,new PromisedConsortSourcePose.Point(
                            bind.get(0).getAsDouble(),bind.get(1).getAsDouble(),bind.get(2).getAsDouble()),
                            new PromisedConsortSourcePose.Point(0,0,0),extracted,yaw);
                    double angle=Math.toRadians(yaw);
                    double bx=bind.get(0).getAsDouble()*Math.cos(angle)+bind.get(2).getAsDouble()*Math.sin(angle);
                    double bz=bind.get(0).getAsDouble()*Math.sin(angle)-bind.get(2).getAsDouble()*Math.cos(angle);
                    check(Math.abs(worldJoint.x()-transferred.x-bx)<1e-9
                            && Math.abs(worldJoint.y()-transferred.y-bind.get(1).getAsDouble())<1e-9
                            && Math.abs(worldJoint.z()-transferred.z-bz)<1e-9,
                            "Entity transfer and compensated renderer preserve the same world Master location");
                }
            }
        }
        check(PromisedConsortSourceAssets.pose(3010).masterTranslationDelta(1_400_000).y()>2,
                "3010 body jump remains present when extractedMotion has no vertical path");
        boolean mismatch=false;
        try { new PromisedConsortSourcePlayback(new Actor(1,0,-1,20012,20012,413),0,1); }
        catch (IllegalArgumentException expected) { mismatch=true; }
        check(mismatch,"Imported TAE cannot masquerade as a different pose source");
    }

    private static void hitVolumeChecks(Path work,PromisedConsortSourceBank sourceBank) throws Exception {
        PromisedConsortSourceHitVolumes volumes;
        PromisedConsortSourceAttachments attachments;
        PromisedConsortSourcePose pose;
        try (var input=Files.newBufferedReader(asset("boss/promised_consort/combat_contracts.json"))) {
            volumes=PromisedConsortSourceHitVolumes.load(input);
        }
        try (var rig=Files.newBufferedReader(asset("boss/promised_consort/rig/source_bone_map.json"));
             var input=Files.newBufferedReader(asset("boss/promised_consort/rig/attachments.json"))) {
            attachments=PromisedConsortSourceAttachments.load(rig,input);
        }
        try (var rig=Files.newBufferedReader(asset("boss/promised_consort/rig/source_bone_map.json"));
             var geo=Files.newBufferedReader(asset("geo/entity/promised_consort.geo.json"));
             var animation=Files.newBufferedReader(asset("animations/entity/promised_consort/source_003007.animation.json"))) {
            pose=PromisedConsortSourcePose.load(rig,geo,animation,3007);
        }
        var attack=volumes.require(3007,25);
        check(attack.id()==5220170 && attack.damage().physical()==250 && attack.primitives().size()==2,"Original fast slash keeps its actual attack parameter and two sword capsules");
        var local=volumes.capsules(3007,25,attachments,pose.sample(600_000),new PromisedConsortSourcePose.Point(0,0,0),new PromisedConsortSourceMotion.Displacement(0,0,0),0);
        var moved=volumes.capsules(3007,25,attachments,pose.sample(600_000),new PromisedConsortSourcePose.Point(10,20,30),new PromisedConsortSourceMotion.Displacement(1,2,3),0);
        check(local.size()==2 && Math.abs(local.get(0).radius()-.85)<1e-9,"Original primitive radius is retained in shared world units");
        check(Math.abs(moved.get(0).first().y()-local.get(0).first().y()-22)<1e-9,"Sword collision shares body pose and exactly one entity/root vertical displacement");
        int otherEvent=sourceBank.requireClip(3010).events().stream().filter(e->e.type()==1).findFirst().orElseThrow().index();
        boolean rejected=false;
        try { volumes.capsules(3010,otherEvent,attachments,pose.sample(600_000),new PromisedConsortSourcePose.Point(0,0,0),new PromisedConsortSourceMotion.Displacement(0,0,0),0); }
        catch(IllegalArgumentException expected) { rejected=true; }
        check(rejected,"A hit window cannot silently use another actor/segment's HKX pose");
    }

    private static void attachmentChecks(Path work) throws Exception {
        PromisedConsortSourceAttachments attachments;
        try (var rig=Files.newBufferedReader(asset("boss/promised_consort/rig/source_bone_map.json"));
             var input=Files.newBufferedReader(asset("boss/promised_consort/rig/attachments.json"))) {
            attachments=PromisedConsortSourceAttachments.load(rig,input);
        }
        check(attachments.require(200).size()>1,"Repeated body dummy200 preserves every original point");
        check(attachments.require(231).get(0).sourceBone().isEmpty(),"Grab dummy231 is not Miquella head bone231");
        check(attachments.require(312).get(0).sourceBone().orElseThrow()==81,"Left sword dummy312 binds source L_Sword81");
        check(attachments.require(302).get(0).sourceBone().orElseThrow()==364,"Right sword dummy302 binds source R_Sword364");
    }

    private static void controllerChecks(PromisedConsortSourceBank bank) {
        class SourceHost implements PromisedConsortSourceController.Host {
            long time; int approaches,dispatched; final Set<Integer> active=new HashSet<>(Set.of(20011576));
            final Set<Integer> activated=new HashSet<>(); PromisedConsortSourceSession.Snapshot snapshot;
            PromisedConsortSourceAi.Shoot queuedShoot; int suppressedShots;
            boolean completeAtEnd,selection=true;
            public long worldMicros() { return time; }
            public PromisedConsortSourceAi.Context context() { return new PromisedConsortSourceAi.Context(5,.7,false,active,Set.of()); }
            public PromisedConsortSourceAi.Rolls rolls() { return ()->100; }
            public PromisedConsortSourceAi.CoolTime coolTime() { return (animation,seconds,weight,floor)->animation==3007 ? weight : 0; }
            public double selectionRoll() { return .5; }
            public double sourceSpeed() { return 1; }
            public boolean allowSelection() { return selection; }
            public void beginApproachOrEngineGoal(PromisedConsortSourceAi.Entry entry) { approaches++; }
            public boolean approachComplete(PromisedConsortSourceAi.Entry entry) { return true; }
            public boolean engineGoalComplete(PromisedConsortSourceAi.Entry entry) { return false; }
            public void dispatch(PromisedConsortSourceSession.Update update) {
                dispatched+=update.started().size()+update.events().size()+update.ended().size();
                for (var notice : update.events()) if (notice.crossing().event().referenceId()==20011552) {
                    if (notice.crossing().identity().edge()==Edge.ENTER) { active.add(20011552); activated.add(20011552); }
                    else active.remove(20011552);
                }
            }
            public Set<Integer> activatedBodyEffects() { return activated; }
            public java.util.Optional<PromisedConsortSourceAi.Shoot> pollShootReaction(boolean effectInterrupt) {
                var result=queuedShoot;queuedShoot=null;
                if(result!=null && effectInterrupt) {suppressedShots++;return java.util.Optional.empty();}
                return java.util.Optional.ofNullable(result);
            }
            public void setSourceTimer(int slot,double seconds) {}
            public boolean bodyEngineExited(Actor actor) {
                return completeAtEnd && snapshot.actors().stream().filter(a->a.actor().equals(actor))
                        .anyMatch(a->time-a.startWorldMicros()>=bank.requireClip(actor.taeId()).durationMicros());
            }
            public void synchronize(PromisedConsortSourceSession.Snapshot value) { snapshot=value; }
            void at(long value) { time=value; activated.clear(); }
        }
        var host=new SourceHost(); var controller=new PromisedConsortSourceController(host,bank);
        controller.tick();
        check(host.approaches==1 && controller.entry().orElseThrow().act()==3,"Source AI selection requests its actual approach goal");
        check(host.snapshot.actors().isEmpty(),"Selection cannot start a pose before the approach adapter responds");
        host.at(50_000); controller.tick();
        check(host.snapshot.actors().get(0).actor().taeId()==3007,"Completed approach starts exact source3007");
        controller.shoot(new PromisedConsortSourceAi.Shoot(true,List.of(),Set.of(),1.4));
        check(controller.save().shootApproach()!=null && controller.save().shootCancellation(),"A previous Shoot can queue approach through the new-attack gate");
        host.queuedShoot=new PromisedConsortSourceAi.Shoot(true,List.of(3032),Set.of(20011551),0);
        host.at(1_700_000); controller.tick();
        check(host.snapshot.actors().get(0).actor().taeId()==3008,"TAE observer plus HKS continuation gate drives quick-slash finisher");
        check(host.suppressedShots==1,"Original observed-effect Interrupt branch takes priority over same-frame Shoot");
        check(host.queuedShoot==null && !controller.save().shootCancellation(),"Suppressed Shoot is consumed without leaving a cancellation behind");
        check(controller.save().shootApproach()==null,"Effect ClearSubGoal also clears an earlier queued Shoot approach");
        check(host.snapshot.actors().get(0).sourceMicros()==0 && !host.snapshot.actors().get(0).activeEvents().isEmpty(),"New segment pre-roll enters at activation without a one-tick delay");
        int dispatched=host.dispatched; controller.tick();
        check(host.dispatched==dispatched,"Controller cannot run twice at the same server timestamp");
        host.queuedShoot=new PromisedConsortSourceAi.Shoot(true,List.of(3032),Set.of(20011551),0);
        host.at(1_750_000);host.active.add(20011577);host.activated.add(20011577);controller.tick();
        check(host.queuedShoot==null && host.suppressedShots==1,"Unobserved effect cannot suppress the next frame's Shoot");
        check(host.snapshot.pendingSegments().contains(3032) || host.snapshot.actors().stream().anyMatch(a->a.actor().taeId()==3032),"Shoot still applies through its actual new-attack gate when no observed activation wins");
        controller.cancel();
        check(host.snapshot.actors().isEmpty(),"Controller cancel synchronizes cleared actors");
        host.completeAtEnd=true;host.selection=false;
        for(int tae:new int[]{3000,3001,3002,3007,3008,3014,3015,3016,3024,20011}) {
            controller.startScript(tae);
            long end=host.time+bank.requireClip(tae).durationMicros();
            host.at(end-1);controller.tick();
            check(host.snapshot.actors().stream().anyMatch(a->a.actor().slot()==-1),"Source "+tae+" keeps its body until completion");
            host.at(end);controller.tick();
            check(controller.entry().isEmpty() && host.snapshot.actors().stream().noneMatch(a->a.actor().slot()==-1),
                    "Source "+tae+" releases its final body pose in the completion tick");
            controller.cancel();
        }
    }

    private static void poseChecks(Path work) throws Exception {
        var witness=com.google.gson.JsonParser.parseString(Files.readString(Path.of("build/asset-previews/consort/rig_review_data.json"))).getAsJsonObject();
        var indices=witness.getAsJsonArray("bone_indices");
        double maximum=0;
        for (int id : new int[]{3010,3020,3026}) {
            PromisedConsortSourcePose pose;
            try (var rig=Files.newBufferedReader(asset("boss/promised_consort/rig/source_bone_map.json"));
                 var geo=Files.newBufferedReader(asset("geo/entity/promised_consort.geo.json"));
                 var animation=Files.newBufferedReader(asset("animations/entity/promised_consort/source_"+String.format("%06d",id)+".animation.json"))) {
                pose=PromisedConsortSourcePose.load(rig,geo,animation,id);
            }
            var clip=witness.getAsJsonObject("clips").getAsJsonObject(String.valueOf(id));
            var times=clip.getAsJsonArray("times"); var source=clip.getAsJsonArray("source");
            for (int f=0;f<times.size();f++) {
                var sample=pose.sample(Math.round(times.get(f).getAsDouble()*1_000_000));
                for (int b=0;b<indices.size();b++) {
                    var expected=source.get(f).getAsJsonArray().get(b).getAsJsonArray();
                    var actual=sample.joint(indices.get(b).getAsInt());
                    double error=Math.sqrt(Math.pow(actual.x()-expected.get(0).getAsDouble(),2)
                            +Math.pow(actual.y()-expected.get(1).getAsDouble(),2)+Math.pow(actual.z()-expected.get(2).getAsDouble(),2));
                    maximum=Math.max(maximum,error);
                    check(error<.0025,"Server full body pose differs from untouched HKX: "+id+"/"+f+"/"+indices.get(b)+" error="+error);
                }
            }
            var sample=pose.sample(1_000_000);
            var rig=com.google.gson.JsonParser.parseString(Files.readString(asset("boss/promised_consort/rig/source_bone_map.json"))).getAsJsonObject();
            var bind=rig.getAsJsonArray("bones").get(8).getAsJsonObject().getAsJsonArray("bind_world_position");
            var bindPoint=new PromisedConsortSourcePose.Point(bind.get(0).getAsDouble(),bind.get(1).getAsDouble(),bind.get(2).getAsDouble());
            check(sample.bindPoint(8,bindPoint).equals(sample.joint(8)),"Bone-bound collision point uses the same pelvis transform");
            var world=sample.worldPoint(8,bindPoint,new PromisedConsortSourcePose.Point(10,20,30),new PromisedConsortSourceMotion.Displacement(1,2,3),0);
            var local=sample.joint(8);
            check(Math.abs(world.y()-22-local.y())<1e-10 && Math.abs(world.z()-33+local.z())<1e-10,"World attachment adds body lift and extracted motion exactly once");
        }
        System.out.println("Server pose/HKX witnesses passed; maximum joint error="+maximum+" world units");
    }

    private static void sessionChecks(PromisedConsortSourceBank bank) {
        var motion=new PromisedConsortSourceMotion(100_000,List.of(
                new PromisedConsortSourceMotion.Sample(1,2,3,4),new PromisedConsortSourceMotion.Sample(3,6,9,8)));
        var delta=motion.displacement(50_000,0);
        check(delta.x()==1 && delta.y()==2 && delta.z()==-3,"Motion interpolates XYZ and subtracts its initial offset");
        check(motion.sample(50_000).yawRadians()==6,"Fourth motion scalar is the signed Y rotation");
        var turned=motion.displacement(50_000,90);
        check(Math.abs(turned.x()-3)<1e-10 && Math.abs(turned.z()-1)<1e-10,"Root path uses the shared canonical actor yaw basis");
        check(bank.motions().get(20).displacement(100_000,0).equals(new PromisedConsortSourceMotion.Displacement(0,0,0)),"Missing extractedMotion is a stationary actor path");
        var session=new PromisedConsortSourceSession(bank);
        var entry=PromisedConsortSourceAi.entry(3,context(5,.7,false),()->100);
        var begin=session.start(entry,1,412,0,1);
        Actor first=begin.started().get(0).actor();
        var events=session.advance(1_650_000);
        check(events.events().stream().anyMatch(n->n.crossing().event().referenceId()==20011552),"Quick slash observer effect crosses at original time");
        var follow=PromisedConsortSourceAi.onSpecialEffect(20011552,context(5,.7,false),()->100);
        check(session.interrupt(first,follow),"Observed effect can replace pending goals");
        check(session.bodyActor().orElseThrow().equals(first),"Goal rewrite does not replace the active skeleton before a gate");
        check(session.gateOpen(first,PromisedConsortSourceSession.Gate.CONTINUATION_ATTACK),"Source Jump Table23 opens quick-slash continuation");
        var transition=session.continueAtGate(first,1_650_000);
        Actor second=transition.started().get(0).actor();
        check(second.taeId()==3008 && second.segmentIndex()==1 && second.actionSequence()==1,"Cross finisher gets its own segment identity");
        check(transition.ended().get(0).reason()==PromisedConsortSourceSession.EndReason.SEGMENT_TRANSITION,"Transition emits explicit cleanup");
        check(!session.interrupt(first,follow),"Late previous-segment interrupt is rejected");
        session.advance(100_000_000);
        check(session.bodyActor().orElseThrow().equals(second),"HKX duration alone cannot end a segment or guess its TAE tail");
        check(session.engineExit(second,100_000_000).ended().size()==1,"Explicit engine exit ends the body");
        check(session.engineExit(second,100_000_000).ended().isEmpty(),"Repeated engine exit does not repeat cleanup");
        session=new PromisedConsortSourceSession(bank);
        var phased=PromisedConsortSourceAi.entry(18,context(10,.4,false,20011599),()->100);
        first=session.start(phased,2,413,1_000_000,2).started().get(0).actor();
        var cloned=session.advance(1_360_000);
        check(cloned.started().size()==1 && cloned.started().get(0).actor().taeId()==20002,"Clone owns independent20002 rather than parent3031");
        check(cloned.started().get(0).startWorldMicros()==1_350_000,"21-frame summon preserves sub-tick world time and source speed");
        var snapshots=session.snapshot(1_360_000).actors();
        check(snapshots.stream().filter(a->a.actor().slot()==0).findFirst().orElseThrow().sourceMicros()==20_000,"Clone clock compensates late20Hz delivery without replaying the parent pose");
        check(session.advance(1_360_000).started().isEmpty(),"Same tick cannot spawn a clone twice");
        var before=session.snapshot(1_360_000);
        var bad=new PromisedConsortSourceAi.Interrupt(20011551,true,List.of(3029,3035),Set.of(20011551),Set.of(),java.util.Map.of(),true,"unresolved");
        boolean rejected=false;
        try { session.interrupt(first,bad); } catch(IllegalArgumentException expected) { rejected=true; }
        check(rejected && session.snapshot(1_360_000).equals(before),"Unresolved clip rejects a whole queue change atomically");
        check(session.engineExit(first,1_360_000).ended().size()==1,"Body engine exit leaves its independently running clone alive");
        var restart=session.start(PromisedConsortSourceAi.entry(3,context(5,.7,false),()->100),3,413,1_360_000,1);
        check(restart.ended().isEmpty() && session.snapshot(1_360_000).actors().stream().anyMatch(a->a.actor().slot()==0 && a.actor().actionSequence()==2 && a.speed()==2),"Next Act preserves the previous clone sequence and speed");
        var cancellation=session.cancel(1_360_000);
        check(cancellation.ended().size()==2 && cancellation.events().stream().allMatch(n->n.crossing().identity().edge()==Edge.CANCEL),"Cancel cleans body and independent clone timelines");
        check(session.cancel(1_360_000).ended().isEmpty(),"Cancel cleanup is idempotent");
        session=new PromisedConsortSourceSession(bank);
        session.start(phased,4,413,0,1);
        var completeClones=session.advance(10_000_000);
        check(completeClones.started().size()==4 && completeClones.ended().size()==4,"A skipped update still spawns and ends all four independent3031 clone actors");
        check(completeClones.ended().stream().allMatch(e->e.reason()==PromisedConsortSourceSession.EndReason.SOURCE_CLONE_END_EFFECT),"Clone lifecycle ends by its own original effect, not parent duration");
        check(session.snapshot(10_000_000).actors().size()==1,"No clone attack window survives its source end flag");
    }

    private static PromisedConsortSourceAi.Context context(double distance,double hp,boolean behind,Integer... effects) {
        return new PromisedConsortSourceAi.Context(distance,hp,behind,Set.of(effects),Set.of());
    }
    private static void checkAi(PromisedConsortSourceBank bank) {
        var identity=(PromisedConsortSourceAi.CoolTime)(animation,seconds,weight,floor)->weight;
        var c=context(5,.8,false,20011576);
        var weights=PromisedConsortSourceAi.weights(c,identity);
        check(weights.get(1)==20 && weights.get(2)==40 && weights.get(3)==20,"Original medium-distance sword selection");
        check(weights.get(12)==0 && weights.get(17)==0 && weights.get(18)==0,"Phase-specific entries gated by effect 20011599");
        check(PromisedConsortSourceAi.entry(1,c,()->40).sourceSegments().equals(List.of(3000,3003)),"Act01 roll40 selects short branch");
        check(PromisedConsortSourceAi.entry(1,c,()->41).sourceSegments().equals(List.of(3000,3001)),"Act01 roll41 keeps queued 3001");
        for(int act:PromisedConsortSourceAi.REGISTERED_ACTS) {
            var entry=PromisedConsortSourceAi.entry(act,c,()->100);
            check(entry.sourceSegments().stream().allMatch(bank.clips()::containsKey),"Every transcribed Act resolves its own source TAE");
        }
        check(PromisedConsortSourceAi.entry(8,c,()->100).observeEffects().equals(Set.of(20011550)),"Act08 keeps original observer50, not guessed51");
        check(PromisedConsortSourceAi.entry(20,context(6.99,.4,false),()->100).sourceSegments().equals(List.of(3033,3035,3036)),"Near holy combo uses3033");
        check(PromisedConsortSourceAi.entry(20,context(7,.4,false),()->100).sourceSegments().equals(List.of(3034,3035,3036)),"Far holy combo boundary uses3034");
        check(PromisedConsortSourceAi.entry(21,c,()->100).sourceSegments().equals(List.of(3021,3024)),"Meteor ascent/return remain separate segments");
        var phase2=context(5,.4,false,20011576,20011599);
        weights=PromisedConsortSourceAi.weights(phase2,identity);
        check(weights.get(17)==0,"Medium-range grab requires effect94");
        check(PromisedConsortSourceAi.weights(context(4,.4,false,20011576,20011599),identity).get(17)==70,"Close grab does not require effect94");
        check(PromisedConsortSourceAi.weights(context(5,.4,false,20011576,20011599,20011594),identity).get(17)==30,"Effect94 enables medium-range grab");
        check(PromisedConsortSourceAi.weights(context(18,.65,false,20011576,20011599),identity).get(14)==0,"Light HP gate excludes exactly65percent");
        check(PromisedConsortSourceAi.weights(context(18,.649,false,20011576,20011599),identity).get(14)==1000,"Light becomes weighted below65percent");
        check(PromisedConsortSourceAi.weights(context(18,.5,false,20011576,20011599),identity).get(20)==0,"Holy combo excludes exactly50percent");
        check(PromisedConsortSourceAi.weights(context(18,.499,false,20011576,20011599),identity).get(20)==1000,"Holy combo below50percent remains weighted");
        check(PromisedConsortSourceAi.weights(context(18,.25,false,20011576,20011599),identity).get(21)==1000,"Meteor gate includes25percent");
        check(PromisedConsortSourceAi.weights(context(18,.25,false,20011576,20011599,20011573),identity).get(21)==0,"Meteor cooldown effect gates repeated meteor");
        check(PromisedConsortSourceAi.weights(context(5,.4,true,20011576,20011599),identity).get(43)==100,"Distant rear target selects turn");
        var charmed=new PromisedConsortSourceAi.Context(5,.2,false,Set.of(20011576,20011599),Set.of(19680));
        check(PromisedConsortSourceAi.weights(charmed,identity).get(30)==100,"Original AI checks host-player charm");
        check(PromisedConsortSourceAi.weights(new PromisedConsortSourceAi.Context(5,.2,false,Set.of(20011574,20011576),Set.of(19680)),identity).get(31)==1000,"Wait precedes host charm");
        var helperCalls=new ArrayList<String>();
        PromisedConsortSourceAi.weights(phase2,(animation,seconds,weight,floor)->{helperCalls.add(animation+":"+seconds+":"+floor);return weight;});
        check(helperCalls.size()==21 && helperCalls.contains("3017:50.0:0.0"),"All original cooldown arguments retained, including meteor zero");
        check(helperCalls.contains("6002:10.0:1.0") && helperCalls.contains("6003:10.0:1.0"),"Both step cooldown calls kept");
        var noBranch=PromisedConsortSourceAi.onSpecialEffect(20011559,context(6.01,.4,false,20011599),()->100);
        check(!noBranch.clearSubGoals() && noBranch.sourceSegments().isEmpty(),"Pull beyond6 does not invent a followup");
        check(PromisedConsortSourceAi.onSpecialEffect(20011559,context(6,.4,false,20011599),()->20).sourceSegments().equals(List.of(3013)),"Pull20percent uppercut boundary");
        check(PromisedConsortSourceAi.onSpecialEffect(20011559,context(6,.4,false,20011599),()->21).sourceSegments().equals(List.of(3016,3030)),"Pull phase2 preserves queued3030");
        check(PromisedConsortSourceAi.onSpecialEffect(20011559,context(6,.7,false),()->21).sourceSegments().equals(List.of(3016)),"Phase1 pull never invents3030");
        check(PromisedConsortSourceAi.onSpecialEffect(20011561,phase2,()->100).sourceSegments().equals(List.of(3031)),"Effect61 replaces pull queue with3031");
        check(PromisedConsortSourceAi.onSpecialEffect(20011564,context(8,.4,false,20011599),()->70).sourceSegments().equals(List.of(3032)),"Distance8 follows >=8 branch before <=8");
        check(PromisedConsortSourceAi.onSpecialEffect(20011564,context(7.99,.4,false,20011599),()->70).sourceSegments().equals(List.of(3000,3003)),"Near holy followup keeps sword pair");
        check(PromisedConsortSourceAi.onSpecialEffect(20011565,phase2,()->70).sourceSegments().equals(List.of(3020)),"Near-light70percent grab boundary");
        check(PromisedConsortSourceAi.onSpecialEffect(20011565,phase2,()->85).sourceSegments().equals(List.of(3004,3019)),"Near-light85percent sword boundary");
        check(PromisedConsortSourceAi.onSpecialEffect(20011565,phase2,()->86).sourceSegments().equals(List.of(3007,3008)),"Near-light final15percent quick swords");
        var unresolved=PromisedConsortSourceAi.onSpecialEffect(20011555,phase2,()->100);
        check(unresolved.unresolvedSegments(bank).equals(List.of(3029)),"Unresolved source3029 recorded rather than filled with nearby clip");
        check(PromisedConsortSourceAi.onSpecialEffect(20011557,context(8,.4,false,20011599),()->35).sourceSegments().equals(List.of(3022)),"Unreachable elseif<=40 is not silently repaired");
        check(PromisedConsortSourceAi.onSpecialEffect(20011562,context(6,.4,false),()->100).sourceSegments().isEmpty(),"Effect62 can clear queue without adding attack");
        check(PromisedConsortSourceAi.onSpecialEffect(20011568,charmed,()->100).sourceSegments().equals(List.of(20012)),"Charm end imports4101 through TAE20012");
        check(PromisedConsortSourceAi.choose(java.util.Map.of(1,1.0,2,0.0),.99).orElseThrow()==1,"Zero-weight action is never selected");
    }
}
