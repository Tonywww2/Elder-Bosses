package com.tonywww.elder_bosses.boss.promisedconsort.source;

import com.google.gson.JsonObject;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import java.util.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTimeline.Actor;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceExecutionData.*;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourcePose.Point;

/** BulletParam lifecycle, collision, homing and child chains on the same source clock. */
public final class PromisedConsortSourceProjectiles {
    private final PromisedConsortSourceCombat combat;
    private final PromisedConsortEntity owner;
    private final PromisedConsortSourceExecutionData data=PromisedConsortSourceExecutionData.get();
    private final List<Bullet> running=new ArrayList<>(),pending=new ArrayList<>();
    private long serial;
    public record BulletState(long serial,long born,Actor actor,int event,int id,Point direction,Point point,Point velocity,
                              long through,long nextChild,Map<UUID,Integer> hits,Map<UUID,Long> lastHit,double travelled,boolean audioStarted) {}
    public record LaunchState(Actor actor,int event,Point point) {}
    public record SavedState(long serial,List<BulletState> bullets,List<LaunchState> launches) {}
    private static final class Bullet {
        final long serial,born;final Actor actor;final int event,id;final JsonObject row;final Vec3 direction;
        final Map<UUID,Integer> hits=new HashMap<>();final Map<UUID,Long> lastHit=new HashMap<>();Vec3 point,velocity;long through,nextChild;boolean dead;
        double travelled;
        boolean audioStarted;
        Bullet(long serial,Actor actor,int event,int id,JsonObject row,Vec3 point,Vec3 direction,long born) {
            this.serial=serial;this.actor=actor;this.event=event;this.id=id;this.row=row;this.point=point;this.direction=direction;this.born=born;
            velocity=direction.scale(number(row,"initVellocity"));through=born;
            nextChild=born+Math.round(number(row,"intervalCreateWaitTime")*1_000_000);
        }
    }
    public PromisedConsortSourceProjectiles(PromisedConsortSourceCombat combat,PromisedConsortEntity owner) {this.combat=combat;this.owner=owner;}
    public void launch(Actor actor,int event,PromisedConsortSourceExecutionData.Launch launch,PromisedConsortSourceCombat.Frame frame,long time) {
        // Warning positions are estimates; release from the actual moving actor.
        Vec3 p=frame.point(launch.dummyId(),time);LivingEntity target=combat.target();
        Vec3 direction=target==null?owner.getLookAngle():target.getBoundingBox().getCenter().subtract(p).normalize();
        if(launch.bulletId()==205220410) {
            p=PromisedConsortSourceGroundAreas.ground(owner,frame.projected(time)).add(0,.08,0);
            direction=owner.getLookAngle().multiply(1,0,1).normalize();
        } else if(launch.bulletId()==205220400) {
            p=PromisedConsortSourceGroundAreas.ground(owner,p).add(0,owner.sourceConfig().number("projectiles.a205220400.flight_height"),0);
            direction=direction.multiply(1,0,1).normalize();
        }
        emit(actor,event,launch.bulletId(),p,direction,time);
    }
    /** Pitch about the launch bearing, so down/up is independent of world facing. */
    public static Vec3 launchDirection(Vec3 direction,double yawDegrees,double pitchDegrees) {
        double horizontal=Math.hypot(direction.x,direction.z);
        Vec3 bearing=horizontal<1e-8?new Vec3(0,0,1):new Vec3(direction.x/horizontal,0,direction.z/horizontal);
        double yaw=Math.toRadians(yawDegrees),sin=Math.sin(yaw),cos=Math.cos(yaw);
        bearing=new Vec3(bearing.x*cos+bearing.z*sin,0,bearing.z*cos-bearing.x*sin);
        double pitch=Math.atan2(direction.y,horizontal)+Math.toRadians(pitchDegrees);
        return bearing.scale(Math.cos(pitch)).add(0,Math.sin(pitch),0).normalize();
    }
    private void emit(Actor actor,int event,int id,Vec3 p,Vec3 direction,long time) {
        // The original full Bullet.param confirms 205220301 is absent. Its dangling
        // reference has no executable child; do not synthesize a nearby ID.
        if(id<0 || !data.hasBullet(id)) return;
        JsonObject row=combat.bulletRow(id);int count=Math.max(0,integer(row,"numShoot"));
        for(int i=0;i<count;i++) {
            double spread=number(row,"shootAngleXZ")+(i-(count-1)*.5)*number(row,"shootAngleInterval");
            double elevation=number(row,"shootAngle")+(i-(count-1)*.5)*number(row,"shootAngleXInterval");
            Vec3 vector=launchDirection(direction,spread,elevation);
            long born=Math.max(time+Math.round(i*number(row,"shootInterval")*1_000_000),combat.worldMicros()+50_000);
            // Child impacts get one server tick of warning before becoming damaging.
            pending.add(new Bullet(serial++,actor,event,id,row,p,vector,born));
        }
    }
    public void tick(long now) {
        var wave=new ArrayList<>(running);
        wave.addAll(pending);running.addAll(pending);pending.clear();
        do {
        for(Bullet bullet:wave) {
            if(now<bullet.born) continue;
            if(!bullet.audioStarted) {
                bullet.audioStarted=true;
                combat.audio().projectile(bullet.actor,integer(bullet.row,"sfxId_Bullet"),bullet.point);
            }
            long end=number(bullet.row,"life")<0?Long.MAX_VALUE:bullet.born+Math.round(number(bullet.row,"life")*1_000_000);
            long until=Math.min(now,end);
            while(bullet.through<until && !bullet.dead) {
                long next=Math.min(until,bullet.through+16_667);advance(bullet,next);
            }
            if(!bullet.dead && now>=end) end(bullet,end);
        }
        wave=new ArrayList<>(pending);running.addAll(pending);pending.clear();
        } while(!wave.isEmpty());
        running.removeIf(b->b.dead);
    }
    private void advance(Bullet b,long next) {
        JsonObject row=b.row;double dt=(next-b.through)/1_000_000.0;
        LivingEntity target=combat.target();
        // Paramdex original definition: metres travelled before homing starts,
        // not the current distance to the enemy.
        if(target!=null && b.travelled>=number(row,"homingBeginDist") && integer(row,"homingAngle")>0) {
            Vec3 desired=target.getBoundingBox().getCenter().subtract(b.point);
            if(b.id==205220400) desired=desired.multiply(1,0,1);
            desired=desired.normalize();
            double angle=Math.acos(Math.max(-1,Math.min(1,b.velocity.normalize().dot(desired))));
            double turn=Math.toRadians(integer(row,"homingAngle"))*dt;
            if(angle>0) b.velocity=b.velocity.normalize().lerp(desired,Math.min(1,turn/angle)).normalize().scale(b.velocity.length());
        }
        boolean inRange=b.travelled<number(row,"dist");
        double accelerating=Math.max(0,(next-Math.max(b.through,b.born+Math.round(number(row,"accelTime")*1_000_000)))/1_000_000.0);
        double speed=b.velocity.length()+number(row,inRange?"accelInRange":"accelOutRange")*accelerating;
        double max=number(row,"maxVellocity"),min=number(row,"minVellocity");
        if(max>0) speed=Math.min(max,speed);if(min>0) speed=Math.max(min,speed);
        if(b.velocity.lengthSqr()>0) b.velocity=b.velocity.normalize().scale(Math.max(0,speed));
        if(b.id==205220400) b.velocity=b.velocity.multiply(1,0,1);
        else b.velocity=b.velocity.add(0,-number(row,inRange?"gravityInRange":"gravityOutRange")*dt,0);
        Vec3 from=b.point,to=from.add(b.velocity.scale(dt));
        if(b.id==205220400) to=PromisedConsortSourceGroundAreas.ground(owner,to).add(0,owner.sourceConfig().number("projectiles.a205220400.flight_height"),0);
        if(integer(row,"isPenetrateMap")==0 && from.distanceToSqr(to)>0) {
            HitResult hit=owner.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
            if(hit.getType()!=HitResult.Type.MISS) {b.point=hit.getLocation();end(b,next);return;}
        }
        double age=(next-b.born)/1_000_000.0,radius=Math.max(0,number(row,"hitRadius")),radiusMax=number(row,"hitRadiusMax"),spread=number(row,"spreadTime");
        if(radiusMax>=0) radius+=(radiusMax-radius)*Math.min(1,spread<=0?1:age/spread);
        // The original 150m flash is a radial floor attack in this arena, in both modes.
        if(b.id==205220410) radius=Math.min(radius,owner.sourceConfig().number("projectiles.a205220410.simple_radius"));
        radius*=owner.sourceConfig().range(b.actor.taeId());
        boolean simple=owner.sourceConfig().flag("hit_detection.simple_ranges");
        int maximumHits=PromisedConsortSourceSegmentHits.maximumHits(simple,(int)owner.sourceConfig().number("projectiles.a"+b.id+".max_hits_per_target"));
        double simpleHeight=owner.sourceConfig().number("projectiles.a"+b.id+".simple_height");
        if(simple) radius=owner.sourceConfig().number("projectiles.a"+b.id+".simple_radius")*owner.sourceConfig().range(b.actor.taeId());
        AABB area=new AABB(Math.min(from.x,to.x)-radius,Math.min(from.y,to.y)-radius,Math.min(from.z,to.z)-radius,Math.max(from.x,to.x)+radius,Math.max(from.y,to.y)+radius,Math.max(from.z,to.z)+radius);
        boolean floorZone=number(row,"initVellocity")<=.1 && number(row,"gravityInRange")==0;
        Vec3 simpleAnchor=simple && floorZone?PromisedConsortSourceGroundAreas.ground(owner,to):to.add(0,-simpleHeight*.5,0);
        if(simple) area=new AABB(to.x-radius,simpleAnchor.y-1,to.z-radius,to.x+radius,simpleAnchor.y+simpleHeight,to.z+radius);
        for(var targetEntity:owner.level().getEntitiesOfClass(LivingEntity.class,area,e->e!=owner && e.isAlive() && !owner.isAttackImmune(e) && combat.canDamageSegment(b.actor,e.getUUID()) && b.hits.getOrDefault(e.getUUID(),0)<maximumHits && b.lastHit.getOrDefault(e.getUUID(),-1L)!=combat.worldMicros()/50_000)) {
            AABB box=targetEntity.getBoundingBox();
            if(simple) {
                if(!(targetEntity instanceof net.minecraft.world.entity.player.Player) || targetEntity.getY()<simpleAnchor.y-1 || targetEntity.getY()>simpleAnchor.y+simpleHeight
                        || Math.pow(targetEntity.getX()-to.x,2)+Math.pow(targetEntity.getZ()-to.z,2)>radius*radius) continue;
            } else if(PromisedConsortSourceCollision.distanceSquared(new Point(from.x,from.y,from.z),new Point(to.x,to.y,to.z),new Point(box.minX,box.minY,box.minZ),new Point(box.maxX,box.maxY,box.maxZ))>radius*radius) continue;
            int attack=integer(row,"atkId_Bullet");
            boolean damage=attack>=0 && combat.damage(targetEntity,b.actor,(int)(b.serial&0x7fffffff),attack);
            if(damage) {
                double distance=combat.knockback(attack);
                Vec3 radial=targetEntity.position().subtract(b.point).multiply(1,0,1).normalize();
                targetEntity.setDeltaMovement(targetEntity.getDeltaMovement().add(radial.scale(distance)));targetEntity.hurtMarked=true;
            }
            boolean status=false;for(int index=0;index<5;index++) {
                int effect=integer(row,"spEffectId"+index);
                if(effect>0) {if(!damage && !status && !combat.claimContact(b.actor,targetEntity.getUUID())) break;combat.applyTargetEffect(targetEntity,effect,next);status=true;}
            }
            if(damage || status) {b.hits.merge(targetEntity.getUUID(),1,Integer::sum);b.lastHit.put(targetEntity.getUUID(),combat.worldMicros()/50_000);}
            if((damage || status) && integer(row,"isPenetrateChr")==0) {b.point=to;end(b,next);return;}
        }
        b.travelled+=from.distanceTo(to);b.point=to;b.through=next;
        int child=integer(row,"intervalCreateBulletId");
        double delayMin=number(row,"intervalCreateTimeMin"),delayMax=number(row,"intervalCreateTimeMax");
        if(child>=0 && data.hasBullet(child) && delayMax>0) while(b.nextChild<=next) {
            emit(b.actor,b.event,child,b.point,b.direction,b.nextChild);
            double delay=delayMin+(delayMax-delayMin)*owner.getRandom().nextDouble();b.nextChild+=Math.max(1,Math.round(delay*1_000_000));
        }
    }
    private void end(Bullet b,long time) {
        if(b.dead) return;b.dead=true;
        combat.visuals().finishProjectile(integer(b.row,"sfxId_Bullet"),b.point,b.direction,b.born,time);
        emit(b.actor,b.event,integer(b.row,"HitBulletID"),b.point,b.direction,time);
        combat.visuals().pulse(integer(b.row,"sfxId_Hit"),b.point,b.direction,time);
        combat.audio().projectile(b.actor,integer(b.row,"sfxId_Hit"),b.point);
    }
    public void clear() {running.clear();pending.clear();lockedLaunches.clear();}
    public void resetContact(Actor actor,UUID target) {
        for(var b:running) if(b.actor.equals(actor)) {b.hits.remove(target);b.lastHit.remove(target);}
        for(var b:pending) if(b.actor.equals(actor)) {b.hits.remove(target);b.lastHit.remove(target);}
    }
    public Set<Actor> activeActors() {
        var actors=new HashSet<Actor>();for(var b:running) if(!b.dead) actors.add(b.actor);for(var b:pending) if(!b.dead) actors.add(b.actor);return actors;
    }
    private record LaunchKey(Actor actor,int event) {}
    private final Map<LaunchKey,Vec3> lockedLaunches=new HashMap<>();
    public List<com.tonywww.elder_bosses.network.IndicatorSnapshotPacket> indicators(long gameTick) {
        var packets=new ArrayList<com.tonywww.elder_bosses.network.IndicatorSnapshotPacket>();long now=gameTick*50_000;
        lockedLaunches.keySet().removeIf(k->combat.frame(k.actor())==null);
        for(var frame:combat.frames()) for(var event:PromisedConsortSourceAssets.bank().requireClip(frame.playback.actor().taeId()).events()) {
            if(event.type()!=2 || !event.appliesTo(frame.playback.actor().stateInfo())) continue;
            long at=frame.playback.worldAtSource(event.startMicros()),lead=(long)(owner.sourceConfig().number(owner.sourceConfig().segment(frame.playback.actor().taeId())+"warning_lead_ticks")*50_000);
            if(at<=now || at-now>lead) continue;
            var launch=data.launch(frame.playback.actor().taeId(),event.index());var row=combat.bulletRow(launch.bulletId());
            int previewAttack=integer(row,"atkId_Bullet");
            int previewTae=frame.playback.actor().taeId();
            boolean composite=previewTae==3009 || previewTae==3012 || previewTae==3014 || previewTae==3026;
            if(previewAttack>=0 && previewAttack<=4 && !composite) continue;
            LaunchKey key=new LaunchKey(frame.playback.actor(),event.index());Vec3 p=frame.point(launch.dummyId(),at);
            if(now>=at-50_000) p=lockedLaunches.computeIfAbsent(key,k->frame.point(launch.dummyId(),at));
            if(launch.bulletId()==205220410) p=frame.projected(at);
            Vec3 direction=combat.target()==null?owner.getLookAngle():combat.target().getBoundingBox().getCenter().subtract(p).normalize();
            boolean stationary=number(row,"initVellocity")==0 && number(row,"gravityInRange")==0;
            double radius=warningRadius(launch.bulletId(),row,frame.playback.actor().taeId());
            int tae=frame.playback.actor().taeId();
            // Authored launch previews include the downstream impact of helper bullets.
            if(tae==3009) {stationary=true;radius=Math.max(radius,10*owner.sourceConfig().range(tae));p=frame.projected(at);}
            if(tae==3012) {stationary=true;p=frame.projected(at);}
            if(tae==3026) {stationary=true;radius=owner.sourceConfig().number("projectiles.a205220312.simple_radius")*owner.sourceConfig().range(tae);}
            if(tae==3014) radius=Math.max(radius,owner.sourceConfig().number("projectiles.a205220241.simple_radius")*owner.sourceConfig().range(tae));
            packets.add(warning(integer(row,"atkId_Bullet"),"source_launch:"+frame.playback.actor().actionSequence()+":"+frame.playback.actor().segmentIndex()+":"+frame.playback.actor().slot()+":"+event.index(),p,direction,stationary,radius,
                    owner.sourceConfig().number("projectiles.a"+launch.bulletId()+".warning_length"),tae,Math.max(frame.playback.startWorldMicros(),at-lead),Math.max(frame.playback.startWorldMicros(),at-50_000),at,at+50_000,gameTick));
        }
        var all=new ArrayList<>(running);all.addAll(pending);
        for(var b:all) if(!b.dead) {
            boolean stationary=number(b.row,"initVellocity")<=.1 && number(b.row,"gravityInRange")==0 && number(b.row,"accelInRange")==0 || b.actor.taeId()==3012;
            double radius=warningRadius(b.id,b.row,b.actor.taeId());
            int attack=integer(b.row,"atkId_Bullet");
            if(attack>=0 && attack<=4) continue; // travel helpers have no damaging floor footprint
            double life=number(b.row,"life");long end=life<0?now+1_000_000:b.born+Math.round(life*1_000_000);
            if(end<now || b.born-now>250_000) continue;
            packets.add(warning(attack,"source_bullet:"+b.serial,b.point,b.velocity.lengthSqr()>0?b.velocity.normalize():b.direction,stationary,radius,owner.sourceConfig().number("projectiles.a"+b.id+".warning_length"),b.actor.taeId(),Math.max(0,b.born-50_000),b.born,b.born,Math.max(b.born+50_000,end),gameTick));
        }
        return packets;
    }
    private double warningRadius(int id,JsonObject row,int tae) {
        return (owner.sourceConfig().flag("hit_detection.simple_ranges")?owner.sourceConfig().number("projectiles.a"+id+".simple_radius"):Math.max(.1,Math.max(number(row,"hitRadius"),number(row,"hitRadiusMax"))))*owner.sourceConfig().range(tae);
    }
    private com.tonywww.elder_bosses.network.IndicatorSnapshotPacket warning(int attackId,String id,Vec3 p,Vec3 direction,boolean stationary,double radius,double length,int tae,long start,long lock,long active,long end,long tick) {
        p=PromisedConsortSourceGroundAreas.ground(owner,p);radius=Math.max(.001,radius);
        var anchor=new com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.Point(p.x,p.y,p.z);
        var points=List.<com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.Point>of();
        boolean area=stationary && radius>0;
        var attack=attackId>=0?combat.attackRow(attackId):null;
        boolean holy=tae==3026 || attack!=null && number(attack,"atkDark")>0,fire=tae==3014 || attack!=null && number(attack,"atkFire")>0,magic=tae==3012 || attack!=null && number(attack,"atkMag")>0;
        var state=tick*50_000>=active?com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.IndicatorState.PERSISTENT:com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.IndicatorState.IMMINENT;
        return new com.tonywww.elder_bosses.network.IndicatorSnapshotPacket(owner.getId(),id,com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.SegmentSlot.NEXT,
                holy?com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.StyleRole.HOLY_IVORY:fire?com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.StyleRole.BLOODFLAME_RED:magic?com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.StyleRole.GRAVITY_PURPLE:com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.StyleRole.PHYSICAL_GOLD,
                holy?com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.Semantic.HOLY:fire?com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.Semantic.BLOODFLAME:magic?com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.Semantic.GRAVITY:com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.Semantic.PHYSICAL,state,
                area?com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.ShapeType.CIRCLE:com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.ShapeType.CAPSULE,
                anchor,(float)Math.toDegrees(Math.atan2(-direction.x,direction.z)),area?List.of((float)radius):List.of((float)length,(float)(radius*2)),points,(start+49_999)/50_000,(lock+49_999)/50_000,(active+49_999)/50_000,(end+49_999)/50_000,false);
    }
    public void appendVisuals(net.minecraft.nbt.ListTag list,long now) {
        for(var b:running) if(!b.dead && now>=b.born) {
            int id=integer(b.row,"sfxId_Bullet");
            if(PromisedConsortSourceFfx.get().has(id)) list.add(PromisedConsortSourceVisuals.tag(id,b.born,Long.MAX_VALUE,b.point,b.velocity.lengthSqr()>0?b.velocity.normalize():b.direction,b.velocity,now));
        }
    }
    private static Point point(Vec3 p) {return new Point(p.x,p.y,p.z);}
    private static Vec3 vector(Point p) {return new Vec3(p.x(),p.y(),p.z());}
    public SavedState save() {
        var all=new ArrayList<>(running);all.addAll(pending);
        return new SavedState(serial,all.stream().filter(b->!b.dead).map(b->new BulletState(b.serial,b.born,b.actor,b.event,b.id,
                point(b.direction),point(b.point),point(b.velocity),b.through,b.nextChild,Map.copyOf(b.hits),Map.copyOf(b.lastHit),b.travelled,b.audioStarted)).toList(),lockedLaunches.entrySet().stream().map(e->new LaunchState(e.getKey().actor(),e.getKey().event(),point(e.getValue()))).toList());
    }
    public void restore(SavedState state,long shift) {
        clear();serial=state.serial();state.launches().forEach(s->lockedLaunches.put(new LaunchKey(s.actor(),s.event()),vector(s.point())));
        for(var s:state.bullets()) {
            Bullet b=new Bullet(s.serial(),s.actor(),s.event(),s.id(),combat.bulletRow(s.id()),vector(s.point()),vector(s.direction()),Math.addExact(s.born(),shift));
            b.velocity=vector(s.velocity());b.through=Math.addExact(s.through(),shift);b.nextChild=Math.addExact(s.nextChild(),shift);
            b.travelled=s.travelled();
            b.audioStarted=s.audioStarted();
            b.hits.putAll(s.hits());s.lastHit().forEach((id,t)->b.lastHit.put(id,t+shift/50_000));running.add(b);
        }
    }
}
