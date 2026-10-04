package com.tonywww.elder_bosses.boss.promisedconsort.source;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortCloneEntity;
import com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortHitSpec;
import com.tonywww.elder_bosses.combat.damage.DamageFormula;
import com.tonywww.elder_bosses.platforms.registry.ModEntities;
import java.util.*;
import com.google.gson.Gson;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTimeline.*;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourcePose.Point;

/** Server adapter for the original AI/TAE execution, independent of project extensions. */
public final class PromisedConsortSourceCombat implements PromisedConsortSourceController.Host {
    private final PromisedConsortEntity owner;
    private final PromisedConsortSourceBank bank;
    private final Map<Integer,com.google.gson.JsonObject> configuredAttacks=new HashMap<>(),configuredBullets=new HashMap<>();
    private final Map<Integer,Long> lastEntry=new HashMap<>();
    private final PromisedConsortSourceExecutionData data=PromisedConsortSourceExecutionData.get();
    private final PromisedConsortSourceController controller;
    private final Map<Actor,Frame> frames=new LinkedHashMap<>();
    private final Map<Integer,Long> effectExpiry=new HashMap<>(),lastAttack=new HashMap<>(),timers=new HashMap<>();
    private final Map<Key,Integer> effectSpans=new LinkedHashMap<>();
    private final Set<Integer> activated=new LinkedHashSet<>();
    private final Map<Key,Window> windows=new LinkedHashMap<>();
    private final PromisedConsortSourceProjectiles projectiles;
    private final PromisedConsortSourceIndicators warnings;
    private final PromisedConsortSourceGrab grab;
    private final PromisedConsortSourceVisuals visuals;
    private final PromisedConsortSourceAudio audio;
    private final PromisedConsortSourceTargetEffects targetEffects;
    private final PromisedConsortSourceSegmentHits segmentHits=new PromisedConsortSourceSegmentHits();
    private PromisedConsortSourceSession.Snapshot snapshot;
    private PromisedConsortSourceAi.Entry approaching;
    private long approachEnd;
    private boolean approachWalk;
    private Vec3 navigationGoal;
    private long navigationAttempt=Long.MIN_VALUE/2;
    private int script;
    private boolean initialized,closed;
    private boolean selectionEnabled=true;
    private boolean meteorMapPending;
    private PromisedConsortSourceMap.Landing meteorLanding;
    private long meteorWarpAt=Long.MAX_VALUE;
    private long meteorWarningAt=Long.MAX_VALUE,meteorWarningSequence;
    private long recoveryEnd;
    private boolean shootPending;
    private boolean acceptanceOnly;
    private long spacingEnd,spacingCooldown;
    private static final Gson SAVE_JSON=new Gson();
    private record FrameState(Actor actor,long start,double speed,Point origin,float yaw,Point displacement,long through,PromisedConsortSourceTimeWarp warp,Point landing,Point correction,long lockAt,long landingAt,long warpBeginAt,boolean landingLocked,double baseY) {}
    private record WindowState(Actor actor,int event,long through,Map<UUID,Integer> hit,Map<UUID,Long> lastHit) {}
    private record SavedState(long at,PromisedConsortSourceController.SavedState controller,int script,boolean initialized,
                              Map<Integer,Long> effects,Map<Integer,Long> lastAttack,Map<Integer,Long> timers,Map<Integer,Long> lastEntry,
                              List<FrameState> frames,List<WindowState> windows,
                              PromisedConsortSourceProjectiles.SavedState projectiles,PromisedConsortSourceGrab.SavedState grab,
                              PromisedConsortSourceAi.Entry approaching,long approachEnd,boolean approachWalk,PromisedConsortSourceIndicators.SavedState warnings,
                              PromisedConsortSourceVisuals.SavedState visuals,PromisedConsortSourceTargetEffects.SavedState targetEffects,
                              boolean meteorMapPending,Point meteorPoint,float meteorYaw,long meteorWarpAt,long meteorWarningAt,long meteorWarningSequence,long recoveryEnd,
                              PromisedConsortSourcePlayback heldPresentation,boolean acceptanceOnly,boolean shootPending,List<PromisedConsortSourceSegmentHits.Claim> segmentHits) {}
    private SavedState restoring;

    private record Key(Actor actor,int event) {}
    private static final class Window {
        final Event event; final Map<UUID,Integer> hit=new HashMap<>();final Map<UUID,Long> lastHit=new HashMap<>(); long through;
        Window(Event e,long begin) {event=e;through=begin-1;}
    }
    public final class Frame {
        public final PromisedConsortSourcePlayback playback;
        public final Entity entity;
        public final Vec3 origin;
        public float initialYaw;
        private Vec3 displacement=Vec3.ZERO;
        private long through;
        private Vec3 landing,correction=Vec3.ZERO;
        private long lockAt=Long.MAX_VALUE,landingAt=Long.MAX_VALUE;
        private double baseY;
        private double landingSoleOffset;
        private final double gravityDescentStartY,gravityDescentEndY;
        private long warpBeginAt=Long.MAX_VALUE;
        private boolean landingLocked;
        private final Map<Long,Vec3> projectedCache=new HashMap<>();
        private final Map<Long,PromisedConsortSourcePose.Sample> poseCache=new HashMap<>();
        private final Map<Long,Double> bodyOffsetCache=new HashMap<>();
        Frame(PromisedConsortSourcePlayback playback,Entity entity,Vec3 origin,float yaw) {
            this.playback=playback;this.entity=entity;this.origin=origin;initialYaw=yaw;through=playback.startWorldMicros();
            int tae=playback.actor().taeId();
            gravityDescentStartY=tae==3017?playback.displacement(playback.worldAtSource(PromisedConsortSourceLanding.GRAVITY_DESCENT_BEGIN),yaw).y:0;
            gravityDescentEndY=tae==3017?playback.displacement(playback.worldAtSource(PromisedConsortSourceLanding.GRAVITY_DESCENT_END),yaw).y:0;
            baseY=origin.y-movement(through).y;
            if(owner.sourceConfig().flag(owner.sourceConfig().segment(tae)+"targeted_landing")) {
                // Landing uses the authored strike, even when the MC contact window starts earlier.
                var clip=PromisedConsortSourceAssets.bank().requireClip(tae);
                boolean dash=entity!=owner || tae==3031 || tae==3032 || tae==3025;
                long sourceLock=clip.events().stream().filter(e->e.type()==760).mapToLong(Event::startMicros).min().orElse(0);
                if(!dash) sourceLock=clip.events().stream().filter(e->e.type()==760).mapToLong(Event::startMicros).max().orElse(-1);
                final long strikeAfter=sourceLock;
                var strike=clip.events().stream().filter(e->e.type()==1 && e.startMicros()>strikeAfter).min(Comparator.comparingLong(Event::startMicros));
                if(strike.isPresent()) {
                    landingAt=playback.worldAtSource(strike.get().startMicros());
                    warpBeginAt=playback.worldAtSource(clip.events().stream().filter(e->e.type()==760).mapToLong(Event::startMicros).min().orElse(sourceLock));
                    lockAt=Math.max(playback.startWorldMicros(),landingAt-(long)(owner.sourceConfig().number(owner.sourceConfig().segment(tae)+"target_lock_lead_ticks")*GAME_TICK_MICROS));
                    warpBeginAt=Math.min(warpBeginAt,lockAt);
                    if(tae==3031) lockAt=warpBeginAt;
                    // Anchor the authored descent to its landing frame, rather
                    // than treating a downward Master path as below-ground travel.
                    if(entity==owner && tae!=20010) baseY=origin.y-movement(landingAt).y;
                    else if(entity!=owner) {
                        var pose=PromisedConsortSourceAssets.pose(playback.actor().hkxId());long contact=playback.sourceMicros(landingAt);
                        landingSoleOffset=-PromisedConsortSourceGrounding.soleY(pose.sample(contact),pose.masterTranslationDelta(contact).y());
                    }
                }
            }
        }
        private Vec3 movement(long world) {
            String prefix=owner.sourceConfig().segment(playback.actor().taeId());
            double horizontal=owner.sourceConfig().number(prefix+"movement_multiplier")*owner.sourceConfig().motionScale(playback.actor().taeId());
            Vec3 authored=playback.displacement(world,initialYaw);
            if(playback.actor().taeId()==3017) {
                // Replace the exported 1.5-block root step with the same descent
                // clock used below for the much larger animated body elevation.
                authored=new Vec3(authored.x,PromisedConsortSourceLanding.gravityDescentY(playback.sourceMicros(world),authored.y,gravityDescentStartY,gravityDescentEndY),authored.z);
            }
            return authored.multiply(horizontal,owner.sourceConfig().number(prefix+"vertical_movement_multiplier"),horizontal);
        }
        private Vec3 adjusted(long world) {
            Vec3 m=movement(world);double t=landing==null?0:PromisedConsortSourceLanding.progress(world,warpBeginAt,landingAt);
            if(playback.actor().taeId()==3031 && landing!=null && world>=warpBeginAt) {
                Vec3 end=movement(landingAt),corrected=end.add(correction);
                if(world>=landingAt) return corrected.add(0,m.y-end.y,0);
                return PromisedConsortSourceLanding.dashPosition(movement(warpBeginAt),m,end,corrected);
            }
            return m.add(correction.scale(t));
        }
        private void lockLanding(long world) {
            if(landingAt==Long.MAX_VALUE || landingLocked || target()==null) return;
            // Clones need the upcoming contact point before their first warning is projected.
            if(entity==owner && world<warpBeginAt) return;
            double previous=Math.max(0,Math.min(1,(double)(through-warpBeginAt)/Math.max(1,landingAt-warpBeginAt)));previous=previous*previous*(3-2*previous);
            Vec3 toward=target().position().subtract(entity==owner && playback.actor().taeId()!=3031?owner.position():origin).multiply(1,0,1).normalize();
            double gap=owner.sourceConfig().number(owner.sourceConfig().segment(playback.actor().taeId())+"target_standoff");
            Vec3 aim=target().position().subtract(toward.scale(gap));
            landing=entity==owner?owner.sourceStandingPosition(aim,target().getY()):
                    PromisedConsortSourceGroundAreas.ground(owner,aim).add(0,owner.sourceConfig().number("terrain.foot_clearance"),0);
            if(entity!=owner) {
                // Clone HKX Root/Pelvis can be over a metre below its actor
                // anchor at contact. Match the source sole to the actual floor,
                // preserving the complete body pose and paired blade volumes.
                landing=landing.add(0,landingSoleOffset,0);
                correction=PromisedConsortSourceLanding.cloneCorrection(origin,movement(landingAt),landing);
                landingLocked=world>=lockAt;projectedCache.clear();return;
            }
            Vec3 expected=owner.position().add(movement(landingAt).subtract(movement(through))).subtract(correction.scale(previous));
            correction=new Vec3(landing.x-expected.x,landing.y-(baseY+movement(landingAt).y),landing.z-expected.z);landingLocked=world>=lockAt;
        }
        public Vec3 projected(long world) {return projectedCache.computeIfAbsent(world,this::project);}
        private Vec3 project(long world) {
            if(PromisedConsortSourceTransition.cinematic(playback)) return owner.position();
            if(entity!=owner) return origin.add(adjusted(world));
            if(meteorAnchored()) return owner.sourcePredictDestination(owner.position(),meteorPosition(world),airborne(world));
            Vec3 now=owner.position(),delta=adjusted(world).subtract(adjusted(through));
            Vec3 p=now.add(delta);
            if(landingAt!=Long.MAX_VALUE) p=new Vec3(p.x,baseY+adjusted(world).y,p.z);
            return owner.sourcePredictDestination(now,p,airborne(world));
        }
        private boolean meteorAnchored() {
            return entity==owner && playback.actor().taeId()==3024 && landingLocked && meteorWarpAt==Long.MAX_VALUE;
        }
        private Vec3 meteorPosition(long world) {
            return owner.sourceArenaPoint(PromisedConsortSourceLanding.starfallPosition(landing,movement(world),movement(landingAt),
                    movement(playback.worldAtSource(PromisedConsortSourceLanding.STARFALL_APPROACH)),meteorApproachRadius(),world>=landingAt));
        }
        private double meteorApproachRadius() {
            return PromisedConsortSourceLanding.STARFALL_APPROACH_RADIUS*owner.sourceConfig().number(owner.sourceConfig().segment(3024)+"movement_multiplier")
                    *owner.sourceConfig().motionScale(3024);
        }
        private boolean airborne(long world) {
            if(playback.actor().taeId()==20011) return false;
            long at=playback.sourceMicros(world);
            if(meteorAnchored()) return world<landingAt;
            if(playback.actor().taeId()==3017 && at>=PromisedConsortSourceLanding.GRAVITY_DESCENT_END) return false;
            return landingAt!=Long.MAX_VALUE && world<landingAt || activeJump(playback,27,at) || activeJump(playback,129,at) || activeJump(playback,113,at);
        }
        public PromisedConsortSourcePose.Sample pose(long world) {return poseCache.computeIfAbsent(world,t->PromisedConsortSourceAssets.pose(playback.poseId()).sample(playback.poseMicros(t)));}
        private double bodyOffset(long world) {
            return bodyOffsetCache.computeIfAbsent(world,t->{
                if(entity!=owner) return 0.0;
                long source=playback.sourceMicros(t);
                if(playback.actor().taeId()==3024) return PromisedConsortSourceGrounding.starfallOffset(source);
                if(playback.actor().taeId()==3017 && source>=PromisedConsortSourceLanding.GRAVITY_DESCENT_BEGIN) {
                    // Keep the whole rig, its attachment points and hit volumes
                    // together; touching down happens before the idle switch.
                    return PromisedConsortSourceGrounding.gravityMeteorOffset(source,playback.poseId());
                }
                if(airborne(t)) return 0.0;
                Vec3 p=projected(t);
                var support=owner.sourceGroundPosition(p,p.y,0);
                if(support.isEmpty() || Math.abs(support.get().y-p.y)>.1) return 0.0;
                double master=PromisedConsortSourceAssets.pose(playback.poseId()).masterTranslationDelta(playback.poseMicros(t)).y();
                return -PromisedConsortSourceGrounding.soleY(pose(t),master);
            });
        }
        public Point poseOrigin(long world) {
            Vec3 p=projected(world);var m=PromisedConsortSourceAssets.pose(playback.poseId()).masterTranslationDelta(playback.poseMicros(world));
            double yaw=Math.toRadians(playback.yawAt(world,initialYaw)),sin=Math.sin(yaw),cos=Math.cos(yaw);
            return new Point(p.x-m.x()*cos-m.z()*sin,p.y+bodyOffset(world)-m.y(),p.z-m.x()*sin+m.z()*cos);
        }
        public Vec3 point(int reference,long world) {
            var p=PromisedConsortSourceAssets.attachments().worldPoints(reference,
                    pose(world),
                    poseOrigin(world),new PromisedConsortSourceMotion.Displacement(0,0,0),playback.yawAt(world,initialYaw)).get(0);
            return new Vec3(p.x(),p.y(),p.z());
        }
        /** Some decorative clone FX refer to equipment absent from c5220.
         * Only decoration may use the actor centre; attack/launch anchors stay strict. */
        public Vec3 visualPoint(int reference,long world) {
            return PromisedConsortSourceAssets.attachments().has(reference)?point(reference,world):projected(world).add(0,entity.getBbHeight()*.5,0);
        }
        void move(long world) {
            projectedCache.clear();poseCache.clear();bodyOffsetCache.clear();
            if(world<through) throw new IllegalArgumentException("Source movement clock reversed");
            if(playback.actor().taeId()==20011) {
                owner.tickSourceTransitionMotion((world-through)/(double)GAME_TICK_MICROS);
                through=world;initialYaw=owner.getYRot();
                projectedCache.clear();bodyOffsetCache.clear();owner.setSourceBodyOffsetY(bodyOffset(world));return;
            }
            double speed=0;int priority=-1;
            long at=playback.sourceMicros(through+(world-through)/2);
            for(var e:bank.requireClip(playback.actor().taeId()).events()) if(active(e,playback.actor(),at) && e.type()==224) {
                var fields=data.eventFields(playback.actor().taeId(),e.index());int p=PromisedConsortSourceExecutionData.integer(fields,"Priority");
                if(p>=priority) {priority=p;speed=PromisedConsortSourceExecutionData.number(fields,"Turn Speed")*owner.sourceConfig().number(owner.sourceConfig().segment(playback.actor().taeId())+"turn_speed_multiplier");}
            }
            boolean fixed=activeJump(playback,7,at) || bank.requireClip(playback.actor().taeId()).events().stream().anyMatch(e->e.type()==703 && active(e,playback.actor(),at));
            lockLanding(world);
            if(target()!=null && speed>0 && !fixed && !landingLocked) {
                Vec3 d=target().position().subtract(owner.position());float desired=(float)Math.toDegrees(Math.atan2(-d.x,d.z));
                float current=playback.yawAt(through,initialYaw),change=net.minecraft.util.Mth.wrapDegrees(desired-current);
                double limit=speed*(world-through)/1_000_000.0;
                initialYaw+=(float)Math.max(-limit,Math.min(limit,change));
            }
            Vec3 requested=adjusted(world);
            Vec3 delta;
            if(meteorAnchored()) delta=meteorPosition(world).subtract(owner.position());
            else {
                delta=requested.subtract(adjusted(through));
                if(landingAt!=Long.MAX_VALUE) delta=new Vec3(delta.x,baseY+requested.y-owner.getY(),delta.z);
            }
            owner.moveSourceControlled(delta,airborne(world));displacement=requested;through=world;
            projectedCache.clear();bodyOffsetCache.clear();owner.setSourceBodyOffsetY(bodyOffset(world));
            float yaw=playback.yawAt(world,initialYaw);owner.setYRot(yaw);owner.setYBodyRot(yaw);owner.setYHeadRot(yaw);
        }
        void moveClone(long world) {
            projectedCache.clear();poseCache.clear();bodyOffsetCache.clear();lockLanding(world);
            Vec3 p=projected(world);entity.setPos(p.x,p.y,p.z);
            float yaw=playback.yawAt(world,initialYaw);entity.setYRot(yaw);
            if(entity instanceof PromisedConsortCloneEntity clone) {clone.setYBodyRot(yaw);clone.setYHeadRot(yaw);}
            displacement=adjusted(world);through=world;
        }
    }

    public PromisedConsortSourceCombat(PromisedConsortEntity owner) {
        this.owner=owner;bank=owner.sourceConfig().configure(PromisedConsortSourceAssets.bank());controller=new PromisedConsortSourceController(this,bank);
        projectiles=new PromisedConsortSourceProjectiles(this,owner);grab=new PromisedConsortSourceGrab(this,owner);
        visuals=new PromisedConsortSourceVisuals(this,owner);warnings=new PromisedConsortSourceIndicators(this,owner);
        targetEffects=new PromisedConsortSourceTargetEffects(this,owner);
        audio=new PromisedConsortSourceAudio(this,owner);
    }
    public void tick() {
        if(closed) return;
        if(restoring!=null) {restoreNow(restoring);restoring=null;}
        long now=worldMicros();activated.clear();effectExpiry.entrySet().removeIf(e->e.getValue()!=Long.MAX_VALUE && e.getValue()<=now);
        resetContacts(segmentHits.expire(now));
        LivingEntity target=owner.getTarget();
        if(target==null || !target.isAlive() || owner.isAttackImmune(target) || target.level()!=owner.level())
            owner.setTarget(owner.visibleEligibleTargets().stream().filter(e->!owner.isAttackImmune(e))
                    .min(Comparator.comparingDouble(owner::distanceToSqr)).orElse(null));
        if(!initialized) {
            // Map20012810 replaces the looping cutscene wait30010 with20010
            // when the arena is entered. Minecraft encounter wake is that gate.
            initialized=true;effectExpiry.put(20011576,Long.MAX_VALUE);script=20010;
            controller.startScript(script);
        }
        if(script==0 && !owner.sourceMeteorWaiting() && !owner.sourceRetainedActionActive() && owner.hasForcedRecovery()) {
            recoveryEnd=now+owner.consumeForcedRecoveryTicks()*GAME_TICK_MICROS;
            grab.release();controller.cancel();owner.getNavigation().stop();
        }
        if(owner.isSourceDefeated()) {if(script!=10000) {grab.release();projectiles.clear();startScript(10000);}}
        else if(!context().phaseTwo() && owner.sourcePhaseTwoPending()) {
            grab.release();projectiles.clear();
            owner.enterSourcePhaseTwo();effectExpiry.put(20011599,Long.MAX_VALUE);effectExpiry.put(20011594,Long.MAX_VALUE);
            startScript(20011);
        }
        else if(!owner.sourceMeteorWaiting() && owner.isSourceStunned() && script!=8700) {
            grab.release();startScript(8700);
        }
        else if(selectionEnabled && !owner.isDisengaging() && script==0 && controller.entry().isEmpty() && bodyFrame()==null && owner.sourceMeteorForced()) {
            shootPending=false;lastEntry.remove(-3);grab.release();owner.getNavigation().stop();
            script=3021;controller.startEntry(PromisedConsortSourceAi.entry(21,context(),rolls()));
        }
        Frame body=bodyFrame();
        if(body!=null) body.move(now);
        if(script==20011) owner.tickSourceTransitionPresentation();
        for(Frame frame:frames.values()) if(frame.entity!=owner) frame.moveClone(now);
        warnings.prepare(now);controller.tick();warnings.prepare(now);
        if(lastEntry.containsKey(-3) && controller.entry().isEmpty() && frames.isEmpty()) {
            lastEntry.remove(-3);lastEntry.put(-2,now);
        }
        if(meteorMapPending) {
            meteorMapPending=false;meteorLanding=PromisedConsortSourceMap.get().landing(owner);
            startScript(3024);meteorWarpAt=bodyFrame().playback.worldAtSource(PromisedConsortSourceLanding.STARFALL_APPROACH);
        }
        body=bodyFrame();
        if(body!=null && body.playback.actor().taeId()==3024) {
            if(now>=meteorWarpAt) {
                body.initialYaw=meteorLanding.yaw();
                long impact=body.playback.worldAtSource(PromisedConsortSourceLanding.STARFALL_CONTACT);
                Vec3 atImpact=body.movement(impact),atWarp=body.movement(now);
                Vec3 position=PromisedConsortSourceLanding.starfallPosition(meteorLanding.point(),atWarp,atImpact,
                        body.movement(body.playback.worldAtSource(PromisedConsortSourceLanding.STARFALL_APPROACH)),body.meteorApproachRadius(),now>=impact);
                owner.placeSourceMeteor(position);
                body.landingAt=impact;body.landing=meteorLanding.point();body.landingLocked=true;
                body.correction=Vec3.ZERO;body.baseY=meteorLanding.point().y-atImpact.y;
                body.displacement=atWarp;body.through=now;
                body.projectedCache.clear();body.bodyOffsetCache.clear();
                owner.setYRot(body.initialYaw);owner.setYBodyRot(body.initialYaw);owner.setYHeadRot(body.initialYaw);
                meteorWarpAt=Long.MAX_VALUE;
            }
            if(!owner.meteorLanded() && body.playback.sourceMicros(now)>=PromisedConsortSourceLanding.STARFALL_IMPACT) {
                owner.landSourceMeteor();
            }
        }
        for(var entry:List.copyOf(windows.entrySet())) sample(entry.getKey(),entry.getValue(),now);
        projectiles.tick(now);grab.tick(now);targetEffects.tick(now);
        var liveActors=new HashSet<>(frames.keySet());liveActors.addAll(projectiles.activeActors());segmentHits.retain(liveActors);
        var visible=new net.minecraft.nbt.ListTag();visuals.append(visible,now);projectiles.appendVisuals(visible,now);
        var visualState=new CompoundTag();visualState.put("Instances",visible);visualState.put("Charmed",grab.charmTags());owner.setSourceVisuals(visualState);
        finishCompletedScript();
    }
    private void finishCompletedScript() {
        if(script==0 || controller.entry().isPresent() || script==8700 && !owner.sourceStunDone()
                || script==20011 && !owner.sourceTransitionDone()) return;
        if(script==10000) owner.finishSourceDeath();
        else {if(script==8700) owner.setSourcePlayback(null);script=0;meteorWarningAt=Long.MAX_VALUE;owner.enterSourceBattle();}
    }
    @Override public void bodyEntryCompleted(PromisedConsortSourceAi.Entry completed) {finishCompletedScript();}
    public void startScript(int tae) {lastEntry.remove(-3);shootPending=false;owner.cancelSourceRetainedAction();script=tae;if(tae!=3024) meteorWarningAt=Long.MAX_VALUE;controller.startScript(tae);}
    public void startThrow() {
        script=4100;owner.getNavigation().stop();
        controller.startEntry(new PromisedConsortSourceAi.Entry(17,List.of(4100),Set.of(20011568),null,"ThrowParam34522000"));
    }
    public void close() {
        if(closed) return;controller.cancel();owner.finishSourceTransitionPresentation();projectiles.clear();visuals.clear();targetEffects.clear();grab.close();segmentHits.clear();closed=true;
    }
    public boolean idle() {return bodyFrame()==null && controller.entry().isEmpty();}
    public boolean gateOpening() {return script==20010 || script==20011;}
    public boolean scripted() {return script!=0;}
    public void beginAcceptance(int act) {
        if(initialized || !PromisedConsortSourceAi.REGISTERED_ACTS.contains(act) || owner.sourceConfig().flags().containsKey("entries.act"+act+".enabled") && !owner.sourceConfig().enabled(act)) throw new IllegalArgumentException("Unavailable source Act");
        initialized=true;acceptanceOnly=true;effectExpiry.put(20011576,Long.MAX_VALUE);
        if(owner.phase()==com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase.PHASE_TWO) {
            effectExpiry.put(20011599,Long.MAX_VALUE);effectExpiry.put(20011594,Long.MAX_VALUE);
        }
        controller.prepareEntry(PromisedConsortSourceAi.entry(act,context(),rolls()));
    }
    public void notifyShoot() {if(!closed && initialized && script==0 && owner.sourceRangedConfig().enabled()) shootPending=true;}
    private static Point point(Vec3 p) {return new Point(p.x,p.y,p.z);}
    private static Vec3 vector(Point p) {return new Vec3(p.x(),p.y(),p.z());}
    public CompoundTag save() {
        SavedState state=new SavedState(worldMicros(),controller.save(),script,initialized,Map.copyOf(effectExpiry),Map.copyOf(lastAttack),Map.copyOf(timers),Map.copyOf(lastEntry),
                frames.values().stream().map(f->new FrameState(f.playback.actor(),f.playback.startWorldMicros(),f.playback.speed(),point(f.origin),f.initialYaw,point(f.displacement),f.through,f.playback.warp(),f.landing==null?null:point(f.landing),point(f.correction),f.lockAt,f.landingAt,f.warpBeginAt,f.landingLocked,f.baseY)).toList(),
                windows.entrySet().stream().map(e->new WindowState(e.getKey().actor(),e.getKey().event(),e.getValue().through,Map.copyOf(e.getValue().hit),Map.copyOf(e.getValue().lastHit))).toList(),
                projectiles.save(),grab.save(),approaching,approachEnd,approachWalk,warnings.save(),visuals.save(),targetEffects.save(),meteorMapPending,
                meteorLanding==null?null:point(meteorLanding.point()),meteorLanding==null?0:meteorLanding.yaw(),meteorWarpAt,meteorWarningAt,meteorWarningSequence,recoveryEnd,
                bodyFrame()==null?owner.sourcePlayback():null,acceptanceOnly,shootPending,segmentHits.save());
        CompoundTag tag=new CompoundTag();tag.putInt("Version",12);tag.putByteArray("State",SAVE_JSON.toJson(state).getBytes(java.nio.charset.StandardCharsets.UTF_8));return tag;
    }
    public void restore(CompoundTag tag) {
        if(tag.getInt("Version")!=12 || initialized || restoring!=null) throw new IllegalArgumentException("Invalid source combat save");
        restoring=SAVE_JSON.fromJson(new String(tag.getByteArray("State"),java.nio.charset.StandardCharsets.UTF_8),SavedState.class);
        if(restoring==null) throw new IllegalArgumentException("Missing source combat state");
    }
    private void restoreNow(SavedState state) {
        long shift=Math.max(0,worldMicros()-state.at());controller.restore(state.controller(),shift);
        script=state.script();initialized=state.initialized();grab.restore(state.grab(),shift);
        acceptanceOnly=state.acceptanceOnly();shootPending=state.shootPending();
        segmentHits.restore(state.segmentHits(),shift);
        meteorMapPending=state.meteorMapPending();meteorLanding=state.meteorPoint()==null?null:new PromisedConsortSourceMap.Landing(vector(state.meteorPoint()),state.meteorYaw());
        meteorWarpAt=state.meteorWarpAt()==Long.MAX_VALUE?Long.MAX_VALUE:Math.addExact(state.meteorWarpAt(),shift);
        meteorWarningAt=state.meteorWarningAt()==Long.MAX_VALUE?Long.MAX_VALUE:Math.addExact(state.meteorWarningAt(),shift);
        meteorWarningSequence=state.meteorWarningSequence();
        recoveryEnd=Math.addExact(state.recoveryEnd(),shift);
        if(state.heldPresentation()!=null) owner.setSourcePlayback(new PromisedConsortSourcePlayback(state.heldPresentation().actor(),
                Math.addExact(state.heldPresentation().startWorldMicros(),shift),state.heldPresentation().speed(),state.heldPresentation().sourceOffsetMicros(),state.heldPresentation().warp()));
        state.effects().forEach((id,end)->effectExpiry.put(id,end==Long.MAX_VALUE?end:Math.addExact(end,shift)));
        state.lastAttack().forEach((id,at)->lastAttack.put(id,Math.addExact(at,shift)));
        state.timers().forEach((id,at)->timers.put(id,Math.addExact(at,shift)));
        state.lastEntry().forEach((id,at)->lastEntry.put(id,Math.addExact(at,shift)));
        approaching=state.approaching();approachEnd=Math.addExact(state.approachEnd(),shift);approachWalk=state.approachWalk();
        for(var saved:state.frames()) {
            var playback=new PromisedConsortSourcePlayback(saved.actor(),Math.addExact(saved.start(),shift),saved.speed(),0,saved.warp());
            Entity entity=owner;
            if(saved.actor().slot()>=0) {
                var clone=ModEntities.PROMISED_CONSORT_CLONE.get().create(owner.level());
                if(clone==null) throw new IllegalStateException("Cannot restore source clone");
                clone.configureSource(owner,playback,vector(saved.origin()),saved.yaw());clone.removeTag("elder_bosses_source_preview");
                if(!owner.level().addFreshEntity(clone)) {clone.discard();continue;}
                clone.bindSourceCombatMotion();entity=clone;
            } else owner.setSourcePlayback(playback);
            Frame frame=new Frame(playback,entity,vector(saved.origin()),saved.yaw());frame.displacement=vector(saved.displacement());frame.through=Math.addExact(saved.through(),shift);frame.landing=saved.landing()==null?null:vector(saved.landing());frame.correction=vector(saved.correction());frame.lockAt=saved.lockAt()==Long.MAX_VALUE?Long.MAX_VALUE:saved.lockAt()+shift;frame.landingAt=saved.landingAt()==Long.MAX_VALUE?Long.MAX_VALUE:saved.landingAt()+shift;frame.warpBeginAt=saved.warpBeginAt()==Long.MAX_VALUE?Long.MAX_VALUE:saved.warpBeginAt()+shift;frame.landingLocked=saved.landingLocked();frame.baseY=saved.baseY();frames.put(saved.actor(),frame);
            if(entity!=owner) frame.moveClone(worldMicros());
        }
        for(var saved:state.windows()) {
            var frame=frames.get(saved.actor());if(frame==null) continue;
            Event event=bank.requireClip(saved.actor().taeId()).events().stream().filter(e->e.index()==saved.event()).findFirst().orElseThrow();
            Window window=new Window(event,0);window.through=Math.addExact(saved.through(),shift);window.hit.putAll(saved.hit());saved.lastHit().forEach((id,t)->window.lastHit.put(id,t+shift/GAME_TICK_MICROS));windows.put(new Key(saved.actor(),saved.event()),window);
        }
        for(var actor:state.controller().session().actors()) if(actor.actor().slot()==-1) for(var event:actor.cursor().cancelled()?List.<Event>of():new Cursor(bank.requireClip(actor.actor().taeId()),actor.cursor()).activeSpans())
            if(event.type()==67) effectSpans.put(new Key(actor.actor(),event.index()),event.referenceId());
        warnings.restore(state.warnings(),shift);projectiles.restore(state.projectiles(),shift);visuals.restore(state.visuals(),shift);targetEffects.restore(state.targetEffects(),shift);owner.setSourceRigEnabled(true);
        // A disconnected/unloaded victim is released by close(). Resume the
        // encounter safely, retaining the charm mark but not an orphaned lock.
        if(state.grab().interruptedThrow()) {controller.cancel();script=0;meteorWarningAt=Long.MAX_VALUE;owner.enterSourceBattle();}
    }
    public Frame bodyFrame() {return frames.values().stream().filter(f->f.playback.actor().slot()==-1).findFirst().orElse(null);}
    public Collection<Frame> frames() {return List.copyOf(frames.values());}
    public java.util.List<com.tonywww.elder_bosses.network.IndicatorSnapshotPacket> indicators(long gameTick) {
        var result=new ArrayList<>(warnings.packets(gameTick));result.addAll(projectiles.indicators(gameTick));
        var warning=meteorWarning(gameTick);if(warning!=null) {
            // Announce the outer final impact as well as the body footprint during ascent.
            result.removeIf(p->p.indicatorId().startsWith("source:") && p.indicatorId().contains(":-1:3024:0"));
            result.add(warning);
        }
        return result;
    }
    public Frame frame(Actor actor) {return frames.get(actor);}
    private com.tonywww.elder_bosses.network.IndicatorSnapshotPacket meteorWarning(long tick) {
        long now=tick*GAME_TICK_MICROS;
        if(meteorWarningAt==Long.MAX_VALUE || now<meteorWarningAt || meteorLanding==null || owner.meteorLanded()
                || script!=3021 && script!=3024) return null;
        var body=bodyFrame();
        long active=body!=null && body.playback.actor().taeId()==3024
                ?body.playback.worldAtSource(PromisedConsortSourceLanding.STARFALL_CONTACT)
                :body!=null?body.playback.worldAtSource(bank.requireClip(3021).durationMicros())
                    +(long)Math.ceil(owner.sourceConfig().warp(3024).gameAt(PromisedConsortSourceLanding.STARFALL_CONTACT)/body.playback.speed())
                :now+1_000_000;
        active=Math.max(now+GAME_TICK_MICROS,active);
        var p=meteorLanding.point();
        var area=PromisedConsortSourceGroundAreas.configured(owner.sourceConfig(),3024,0,point(p),meteorLanding.yaw());
        return new com.tonywww.elder_bosses.network.IndicatorSnapshotPacket(owner.getId(),"source_meteor:"+meteorWarningSequence,
                com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.SegmentSlot.CURRENT,
                com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.StyleRole.HOLY_IVORY,
                com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.Semantic.HOLY,
                com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.IndicatorState.IMMINENT,
                com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.ShapeType.CIRCLE,
                new com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.Point(p.x,p.y,p.z),0,List.of((float)Math.max(area.length(),projectiles.starfallWarningRadius())),List.of(),
                (meteorWarningAt+GAME_TICK_MICROS-1)/GAME_TICK_MICROS,(meteorWarningAt+GAME_TICK_MICROS-1)/GAME_TICK_MICROS,
                (active+GAME_TICK_MICROS-1)/GAME_TICK_MICROS,(active+2*GAME_TICK_MICROS-1)/GAME_TICK_MICROS,false);
    }
    public PromisedConsortSourceVisuals visuals() {return visuals;}
    public PromisedConsortSourceAudio audio() {return audio;}
    public void applyTargetEffect(LivingEntity target,int id,long time) {
        if(id==19681) grab.applyCharm(target);else targetEffects.apply(target,id,time);
    }
    public LivingEntity target() {return owner.getTarget();}
    public PromisedConsortSourceSession.Snapshot snapshot() {return snapshot;}
    @Override public long worldMicros() {return Math.multiplyExact(owner.level().getGameTime(),GAME_TICK_MICROS);}
    @Override public PromisedConsortSourceAi.Context context() {
        var effects=new HashSet<>(effectExpiry.keySet());effects.addAll(effectSpans.values());
        if(owner.sourceMeteorAvailable()) effects.remove(20011573);
        else effects.add(20011573);
        LivingEntity target=target();Vec3 delta=target==null?Vec3.ZERO:target.position().subtract(owner.position());
        boolean behind=target!=null && owner.getLookAngle().multiply(1,0,1).dot(delta.multiply(1,0,1))<0;
        return new PromisedConsortSourceAi.Context(target==null?0:owner.distanceTo(target),
                Math.max(0,Math.min(1,owner.getHealth()/owner.getMaxHealth())),behind,effects,grab.targetEffects(target));
    }
    @Override public PromisedConsortSourceAi.Rolls rolls() {return ()->owner.getRandom().nextInt(100)+1;}
    @Override public double selectionRoll() {return owner.getRandom().nextDouble();}
    @Override public double sourceSpeed() {return 1;}
    // EMEVD WaitFixedTimeFrames(1), sampled on a60Hz original engine clock.
    @Override public long cloneSpawnWaitMicros() {return 16_667;}
    @Override public boolean allowSelection() {
        finishCompletedScript();
        if(owner.sourceMeteorForced()) return false;
        if(acceptanceOnly || lastEntry.containsKey(-3) || !selectionEnabled || worldMicros()<recoveryEnd || owner.isDisengaging() || script!=0 || target()==null || grab.active() || owner.sourceRetainedActionActive()) return false;
        if(idle() && owner.sourceConfig().flag("spacing.retreat_when_crowded")) {
            long now=worldMicros();double minimum=owner.sourceConfig().number("spacing.minimum_melee_distance");
            if(now>=spacingCooldown && context().distance()<minimum) {spacingEnd=now+(long)(owner.sourceConfig().number("spacing.retreat_ticks")*GAME_TICK_MICROS);spacingCooldown=now+(long)(owner.sourceConfig().number("spacing.retreat_cooldown_ticks")*GAME_TICK_MICROS);}
            if(now<spacingEnd && context().distance()<owner.sourceConfig().number("spacing.retreat_distance")) {
                Vec3 away=owner.position().subtract(target().position()).multiply(1,0,1).normalize();
                if(away.lengthSqr()<.01) away=owner.getLookAngle().multiply(-1,0,-1).normalize();
                Vec3 p=owner.sourceStandingPosition(owner.position().add(away.scale(owner.sourceConfig().number("spacing.retreat_distance")-context().distance())),owner.getY());
                owner.getNavigation().moveTo(p.x,p.y,p.z,owner.sourceConfig().number("spacing.retreat_speed"));owner.setPursuing(true);faceTarget();return false;
            }
        }
        return true;
    }
    @Override public Map<Integer,Double> selectionWeights(Map<Integer,Double> original) {
        var result=new LinkedHashMap<Integer,Double>();
        original.forEach((act,weight)-> {
            if(owner.sourceConfig().flags().containsKey("entries.act"+act+".enabled")) {
                double scale=owner.sourceConfig().number("entries.act"+act+".selection_distance_multiplier");
                if(scale!=1) {
                    var c=context();weight=PromisedConsortSourceAi.weights(new PromisedConsortSourceAi.Context(c.distance()/scale,c.hpRate(),c.targetBehind90(),c.selfEffects(),c.hostPlayerEffects()),coolTime()).get(act);
                }
                if(!owner.sourceConfig().enabled(act)) weight=0.0;
                else weight*=owner.sourceConfig().number("entries.act"+act+".weight_multiplier");
                if(PromisedConsortSourceAi.COOLDOWNS.stream().noneMatch(c->c.act()==act)
                        && worldMicros()-lastEntry.getOrDefault(act,Long.MIN_VALUE/2)<owner.sourceConfig().number("entries.act"+act+".cooldown_ticks")*GAME_TICK_MICROS && owner.sourceConfig().enabled(act) && weight>0) weight=owner.sourceConfig().number("entries.act"+act+".weight_during_cooldown")*owner.sourceConfig().number("entries.act"+act+".weight_multiplier");
            }
            result.put(act,weight);
        });
        if(!owner.sourceMeteorAvailable()) result.put(21,0.0);
        else result.put(21,1000.0*owner.sourceConfig().number("entries.act21.weight_multiplier"));
        return result;
    }
    @Override public PromisedConsortSourceAi.CoolTime coolTime() {
        return (id,seconds,weight,during)-> {
            var cooldown=PromisedConsortSourceAi.COOLDOWNS.stream().filter(c->c.sourceAnimationId()==id).findFirst().orElseThrow();
            String prefix="entries.act"+cooldown.act()+".";
            double duration=owner.sourceConfig().optional(prefix+"cooldown_ticks",seconds*20)*GAME_TICK_MICROS;
            return weight<=0?0:worldMicros()-lastAttack.getOrDefault(id,Long.MIN_VALUE/2)<duration?owner.sourceConfig().optional(prefix+"weight_during_cooldown",during):weight;
        };
    }
    public com.google.gson.JsonObject attackRow(int id) {return configuredAttacks.computeIfAbsent(id,k->owner.sourceConfig().override("attacks",k,data.attack(k)));}
    public com.google.gson.JsonObject bulletRow(int id) {return configuredBullets.computeIfAbsent(id,k->owner.sourceConfig().override("projectiles",k,data.bullet(k)));}
    public double knockback(int id) {return PromisedConsortSourceExecutionData.number(attackRow(id),"knockbackDist")*.1;}
    private List<Double> approachArguments(PromisedConsortSourceAi.Entry entry) {
        var names=List.of("approach_stop_distance","approach_walk_distance","approach_run_distance","approach_run_chance","approach_guard_chance","approach_walk_ticks","approach_run_ticks");
        var original=entry.approach().sourceArguments();var result=new ArrayList<Double>();
        for(int i=0;i<original.size();i++) {
            double value=owner.sourceConfig().optional("entries.act"+entry.act()+"."+names.get(i),original.get(i)*(i>=5?20:1));
            result.add(i>=5?value/20:value);
        }return result;
    }
    @Override public void beginApproachOrEngineGoal(PromisedConsortSourceAi.Entry entry) {
        navigationGoal=null;navigationAttempt=Long.MIN_VALUE/2;
        approaching=entry;
        if(entry.act()>0) lastEntry.put(entry.act(),worldMicros());
        if(entry.act()==-2) {
            approachWalk=true;approachEnd=worldMicros()+Math.round((entry.approach()==null?0:entry.approach().sourceArguments().get(1))*1_000_000);
            owner.getNavigation().stop();return;
        }
        if(entry.approach()!=null) {
            var a=approachArguments(entry);double d=context().distance();
            approachWalk=!(d>=a.get(2) || d>=a.get(1) && rolls().percent()<=a.get(3));
            rolls().percent(); // Original guard roll (all these entries have0%).
            approachEnd=worldMicros()+Math.round(a.get(approachWalk?5:6)*1_000_000);
        } else {
            double seconds=switch(entry.act()) {
                case 31->10;case 39->3.5;case 40->owner.getRandom().nextInt(2)+1;case 41->owner.getRandom().nextInt(3)+1;
                case 42->.8+owner.getRandom().nextDouble()*.7;case 43->2;default->0;
            };
            approachEnd=worldMicros()+Math.round(seconds*1_000_000);
        }
        owner.getNavigation().stop();
    }
    @Override public PromisedConsortSourceAi.Entry resolveEngineEntry(PromisedConsortSourceAi.Entry entry) {
        if(entry.act()!=44 && entry.act()!=45) return entry;
        Vec3 d=target()==null?Vec3.ZERO:target().position().subtract(owner.position());
        Vec3 look=owner.getLookAngle();boolean right=look.z*d.x-look.x*d.z>0;
        int step=entry.act()==45?6000:right?6003:6002;
        return new PromisedConsortSourceAi.Entry(entry.act(),List.of(step),entry.observeEffects(),null,entry.engineGoal());
    }
    @Override public boolean approachComplete(PromisedConsortSourceAi.Entry entry) {
        if(target()==null) return false;
        var approach=entry.approach();
        double stop=approach==null?0:Math.max(approachArguments(entry).get(0),owner.sourceConfig().number("spacing.minimum_melee_distance"));
        if(approach==null || context().distance()<=stop || worldMicros()>=approachEnd) {
            owner.getNavigation().stop();faceTarget();return true;
        }
        Vec3 toward=target().position().subtract(owner.position()).multiply(1,0,1).normalize();
        Vec3 p=owner.sourceStandingPosition(target().position().subtract(toward.scale(stop)),target().getY());
        navigate(p,owner.sourceConfig().optional("entries.act"+entry.act()+"."+(approachWalk?"approach_walk_speed":"approach_run_speed"),approachWalk?1:1.5));owner.setPursuing(true);faceTarget();return false;
    }
    private void navigate(Vec3 point,double speed) {
        long now=worldMicros();
        if(now-navigationAttempt<400_000 && navigationGoal!=null && navigationGoal.distanceToSqr(point)<1) return;
        navigationGoal=point;navigationAttempt=now;
        if(!owner.getNavigation().moveTo(point.x,point.y,point.z,speed))
            approachEnd=Math.min(approachEnd,now+250_000);
    }
    @Override public boolean engineGoalComplete(PromisedConsortSourceAi.Entry entry) {
        if(target()==null || worldMicros()>=approachEnd || entry.act()==31 && (!context().has(20011574) || context().distance()<=owner.sourceRangedConfig().exitDistance())) {
            if(entry.act()==31) effectExpiry.remove(20011574);
            owner.getNavigation().stop();return true;
        }
        switch(entry.act()) {
            case -2 -> {if(context().distance()<=10) {owner.getNavigation().stop();return true;}navigate(target().position(),1);owner.setPursuing(true);}
            case 31 -> {owner.getNavigation().stop();return false;}
            case 39,40 -> {Vec3 toward=target().position().subtract(owner.position()).multiply(1,0,1).normalize();Vec3 p=target().position().subtract(toward.scale(owner.sourceConfig().number("spacing.minimum_melee_distance")));navigate(p,1);owner.setPursuing(true);}
            case 41 -> {Vec3 away=owner.position().subtract(target().position()).multiply(1,0,1).normalize();Vec3 p=target().position().add(away.scale(10));navigate(p,1);owner.setPursuing(true);}
            case 42 -> {Vec3 toward=target().position().subtract(owner.position()).multiply(1,0,1).normalize();Vec3 p=owner.position().add(-toward.z*3,0,toward.x*3);navigate(p,1);owner.setPursuing(true);}
            case 43 -> {faceTarget();return true;}
            default -> owner.getNavigation().stop();
        }
        faceTarget();return entry.act()==39 && context().distance()<=10 || entry.act()==40 && context().distance()<=owner.sourceConfig().number("spacing.minimum_melee_distance") || entry.act()==41 && context().distance()>=10;
    }
    private void faceTarget() {
        if(target()==null) return;Vec3 d=target().position().subtract(owner.position());
        float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z));owner.setYRot(yaw);owner.setYBodyRot(yaw);owner.setYHeadRot(yaw);
    }
    /** Finish the running source chain or cancel it according to encounter policy. */
    public void tickDisengaged(boolean cancel) {
        grab.release();selectionEnabled=false;
        try {
            if(cancel) {controller.cancel();script=0;meteorMapPending=false;meteorWarpAt=Long.MAX_VALUE;meteorWarningAt=Long.MAX_VALUE;}
            else if(!frames.isEmpty() || controller.entry().isPresent()) tick();
            if(frames.isEmpty() && controller.entry().isEmpty()) {
                projectiles.clear();visuals.clear();segmentHits.clear();owner.setSourcePlayback(null);owner.setSourceLocomotion(20);owner.setNoGravity(false);
            }
        } finally {selectionEnabled=true;}
    }
    public void resumeCooldowns(long elapsed,String policy) {
        if("clear".equals(policy)) {lastAttack.clear();timers.clear();}
        else if("freeze".equals(policy)) {
            lastAttack.replaceAll((id,time)->Math.addExact(time,elapsed));timers.replaceAll((id,time)->Math.addExact(time,elapsed));
        }
    }
    public void playGuardCue() {
        var body=bodyFrame();if(body==null) return;long now=worldMicros(),previous=now-GAME_TICK_MICROS,lead=owner.sourceGuardLeadMicros();
        for(var event:bank.requireClip(body.playback.actor().taeId()).events()) if(event.type()==1 && event.startMicros()>=lead) {
            long at=body.playback.worldAtSource(event.startMicros())-lead;
            if(previous<at && at<=now) owner.playSourceGuardCue();
        }
    }
    private static boolean active(Event e,Actor actor,long at) {
        return (e.stateInfo()==0 || e.stateInfo()==actor.stateInfo()) && e.startMicros()<=at && (e.endMicros()<0 || at<e.endMicros());
    }
    private boolean activeJump(PromisedConsortSourcePlayback playback,int jump,long at) {
        return bank.requireClip(playback.actor().taeId()).events().stream().anyMatch(e->e.type()==0 && active(e,playback.actor(),at)
                && PromisedConsortSourceExecutionData.integer(data.eventFields(playback.actor().taeId(),e.index()),"Jump Table ID")==jump);
    }
    public boolean activeJump(int jump) {var frame=bodyFrame();return frame!=null && (jump!=24 || owner.sourceConfig().flag(owner.sourceConfig().segment(frame.playback.actor().taeId())+"hyper_armor_active")) && activeJump(frame.playback,jump,frame.playback.sourceMicros(worldMicros()));}
    public boolean invulnerable() {
        var body=bodyFrame();
        if(body!=null && body.playback.actor().taeId()==3026) return owner.sourceConfig().flag("entries.act14.invulnerable");
        return activeJump(8)||activeJump(39)||activeJump(67)||activeJump(68)||activeJump(94);
    }
    @Override public void dispatch(PromisedConsortSourceSession.Update update) {
        for(var start:update.started()) {
            resetContacts(segmentHits.nextSegment(start.actor()));
            var playback=PromisedConsortSourcePlayback.of(start);
            if(start.actor().slot()==-1) {
                Frame frame=new Frame(playback,owner,owner.position(),owner.getYRot());frames.put(start.actor(),frame);
                owner.getNavigation().stop();owner.setPursuing(false);
                owner.setSourcePlayback(playback);frame.move(start.startWorldMicros());lastAttack.put(start.actor().taeId(),start.startWorldMicros());
                owner.broadcastSourceActionDebug(controller.entry().map(PromisedConsortSourceAi.Entry::act).orElse(-1),start.actor(),true);
                if(start.actor().taeId()==3021) {
                    owner.beginSourceMeteor();meteorLanding=PromisedConsortSourceMap.get().landing(owner);
                    meteorWarningAt=PromisedConsortSourceLanding.warningAt(playback);
                    meteorWarningSequence=start.actor().actionSequence();
                }
            } else {
                Frame parent=bodyFrame();if(parent==null) throw new IllegalStateException("Source clone lost spawn parent");
                long summon=start.startWorldMicros()-cloneSpawnWaitMicros();
                Vec3 p=parent.playback.actor().taeId()==3024 && meteorLanding!=null?meteorLanding.point():parent.point(228,summon);
                var clone=ModEntities.PROMISED_CONSORT_CLONE.get().create(owner.level());
                if(clone==null) throw new IllegalStateException("Cannot create source clone");
                float yaw=parent.playback.actor().taeId()==3024 && meteorLanding!=null?meteorLanding.yaw():parent.playback.yawAt(summon,parent.initialYaw);
                if(target()!=null && owner.sourceConfig().flag(owner.sourceConfig().segment(start.actor().taeId())+"targeted_landing")) {
                    Vec3 toward=target().position().subtract(p);yaw=(float)Math.toDegrees(Math.atan2(-toward.x,toward.z));
                }
                clone.configureSource(owner,playback,p,yaw);clone.bindSourceCombatMotion();clone.removeTag("elder_bosses_source_preview");
                Frame frame=new Frame(playback,clone,p,yaw);frame.moveClone(start.startWorldMicros());
                if(owner.level().addFreshEntity(clone)) frames.put(start.actor(),frame);
                else clone.discard();
            }
        }
        warnings.prepare(worldMicros());
        for(var notice:update.events()) {
            // The cinematic owns its light/dialogue/flight beats. Original
            // combat events must not fire while its source poses are rearranged.
            if(script==20011 && notice.crossing().identity().actor().taeId()==20011) continue;
            visuals.event(notice);
            audio.event(notice);
            Crossing crossing=notice.crossing();Actor actor=crossing.identity().actor();Event event=crossing.event();
            Key key=new Key(actor,event.index());boolean enter=crossing.identity().edge()==Edge.ENTER;
            if(event.type()==1) {
                if(enter) {var window=new Window(event,notice.worldMicros());windows.put(key,window);sample(key,window,notice.worldMicros());}
                else {var window=windows.remove(key);if(window!=null) sample(key,window,notice.worldMicros()-1);}
            } else if(event.type()==2 && enter) {
                Frame frame=frames.get(actor);if(frame!=null) {
                    var launch=data.launch(actor.taeId(),event.index());projectiles.launch(actor,event.index(),launch,frame,notice.worldMicros());
                }
            } else if((event.type()==66 || event.type()==67) && actor.slot()==-1) {
                if(enter) {
                    if(event.type()==67) effectSpans.put(key,event.referenceId());
                    else {double duration=owner.sourceConfig().number("effects.a"+event.referenceId()+".duration_ticks")/20;if(duration!=0) effectExpiry.put(event.referenceId(),duration<0?Long.MAX_VALUE:notice.worldMicros()+Math.round(duration*1_000_000));}
                    activated.add(event.referenceId());
                    if(event.referenceId()==20011572 && actor.taeId()==3021) meteorMapPending=true;
                } else if(event.type()==67) effectSpans.remove(key);
            }
        }
        for(var end:update.ended()) {
            if(end.actor().slot()==-1) owner.broadcastSourceActionDebug(controller.entry().map(PromisedConsortSourceAi.Entry::act).orElse(-1),end.actor(),false);
            Frame frame=frames.remove(end.actor());warnings.cancel(end.actor());
            if(isRangedCounter(end.actor().taeId())) lastEntry.put(-2,worldMicros());
            if(frame!=null && frame.entity!=owner) frame.entity.discard();
            windows.keySet().removeIf(k->k.actor().equals(end.actor()));effectSpans.keySet().removeIf(k->k.actor().equals(end.actor()));
            if(end.actor().slot()==-1 && bodyFrame()==null && (end.actor().taeId()!=8700 || owner.sourceStunDone())) {
                owner.setSourcePlayback(null);
                int tae=end.actor().taeId();
                if(tae==3006 || tae==3025 || tae==3013 || tae==4100 || tae==20012) {
                    owner.setSourceLocomotion(20);
                    var idle=PromisedConsortSourceAssets.pose(20);
                    owner.setSourceBodyOffsetY(-PromisedConsortSourceGrounding.soleY(idle.sample(0),idle.masterTranslationDelta(0).y()));
                }
            }
        }
    }
    public List<PromisedConsortSourceHitVolumes.Capsule> capsules(Frame frame,int event,long world) {
        var actor=frame.playback.actor();var hit=PromisedConsortSourceAssets.hitVolumes();var attack=hit.require(actor.taeId(),event);
        var pose=frame.pose(world);
        var raw=hit.capsules(actor.taeId(),event,PromisedConsortSourceAssets.attachments(),pose,frame.poseOrigin(world),new PromisedConsortSourceMotion.Displacement(0,0,0),frame.playback.yawAt(world,frame.initialYaw));
        double range=owner.sourceConfig().range(actor.taeId());Vec3 center=frame.projected(world);
        var result=new ArrayList<PromisedConsortSourceHitVolumes.Capsule>();var arms=new HashSet<Integer>();
        for(var c:raw) {
            var primitive=attack.primitives().stream().filter(p->p.slot()==c.primitiveSlot()).findFirst().orElseThrow();
            int side=attack.throwFlag()!=0?0:swordSide(primitive.firstDummy());
            if(side==0 && attack.throwFlag()==0) side=swordSide(primitive.secondDummy());
            double radius=owner.sourceConfig().number("attacks.a"+attack.id()+".hit"+c.primitiveSlot()+"_Radius");
            Vec3 a=vector(c.first()),b=vector(c.second());
            if(side!=0) {
                radius*=owner.sourceConfig().number("hit_detection.trajectory_radius_multiplier");
                Vec3 hilt=frame.point(side==1?300:310,world),tip=frame.point(side==1?10:20,world);
                Vec3 extension=hilt.subtract(tip).normalize().scale(owner.sourceConfig().number("hit_detection.trajectory_hilt_extension"));
                if(a.distanceToSqr(hilt)<=b.distanceToSqr(hilt)) a=a.add(extension);else b=b.add(extension);
                double length=owner.sourceConfig().number("hit_detection.trajectory_arm_extension");
                if(length>0 && radius*range>0 && arms.add(side)) {
                    Vec3 forearm=vector(pose.worldJoint(side==1?345:62,frame.poseOrigin(world),frame.playback.yawAt(world,frame.initialYaw)));
                    Vec3 delta=forearm.subtract(hilt);Vec3 arm=hilt.add(delta.normalize().scale(Math.min(length,delta.length())));
                    result.add(new PromisedConsortSourceHitVolumes.Capsule(c.primitiveSlot(),-side,
                            scaled(hilt,center,range),scaled(arm,center,range),radius*range,c.hitType(),c.priority()));
                }
            }
            if(radius*range>0) result.add(new PromisedConsortSourceHitVolumes.Capsule(c.primitiveSlot(),c.sourcePointIndex(),
                    scaled(a,center,range),scaled(b,center,range),radius*range,c.hitType(),c.priority()));
        }
        if(actor.taeId()==3031 && world>=frame.warpBeginAt && world<frame.landingAt) {
            double radius=result.stream().mapToDouble(PromisedConsortSourceHitVolumes.Capsule::radius).max().orElse(0);
            if(radius>0) result.add(PromisedConsortSourceHitVolumes.dashBody(point(center),frame.entity.getBbHeight(),radius));
        }
        return List.copyOf(result);
    }
    private static int swordSide(int dummy) {return dummy==300 || dummy>=10 && dummy<=12?1:dummy==310 || dummy>=20 && dummy<=22?2:0;}
    public Clip clip(int tae) {return bank.requireClip(tae);}
    public static boolean liveContact(int tae) {return tae==3031 || tae==3032 || tae==3028 || tae==3024;}
    private static Point scaled(Vec3 p,Vec3 center,double range) {return point(center.add(p.subtract(center).scale(range)));}
    private void sample(Key key,Window window,long throughWorld) {
        Frame frame=frames.get(key.actor());if(frame==null || throughWorld<=window.through) return;
        long last=window.event.endMicros()<0?throughWorld:Math.min(throughWorld,frame.playback.worldAtSource(window.event.endMicros())-1);
        if(last<=window.through) return;
        var attack=PromisedConsortSourceAssets.hitVolumes().require(key.actor().taeId(),key.event());
        boolean simple=owner.sourceConfig().flag("hit_detection.simple_ranges");
        int maximumHits=PromisedConsortSourceSegmentHits.maximumHits(simple,(int)owner.sourceConfig().number("attacks.a"+attack.id()+".max_hits_per_target"));
        long first=Math.max(window.through+1,frame.playback.worldAtSource(Math.max(0,window.event.startMicros())));
        int count=Math.max(1,(int)Math.ceil((last-first)/8_333.0));
        if(simple) {
            int samples=liveContact(key.actor().taeId())?count:0;
            for(int step=0;step<=samples;step++) {
            long world=samples==0?first:first+(last-first)*step/samples;
            var area=liveContact(key.actor().taeId())?PromisedConsortSourceGroundAreas.forEvent(owner,frame,key.event(),world):warnings.area(key.actor(),key.event());
            if(area!=null) for(var target:owner.level().getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,area.bounds(),e->e.isAlive() && !owner.isAttackImmune(e) && canDamageSegment(key.actor(),e.getUUID())
                    && window.hit.getOrDefault(e.getUUID(),0)<maximumHits && window.lastHit.getOrDefault(e.getUUID(),-1L)!=throughWorld/GAME_TICK_MICROS)) {
                if(!area.contains(target.getX(),target.getY(),target.getZ())) continue;
                boolean hit=attack.throwFlag()!=0?grab.tryCapture(target,first):damage(target,key.actor(),key.event(),attack.id());
                if(hit && attack.throwFlag()!=0) claimContact(key.actor(),target.getUUID());
                if(hit) {window.hit.merge(target.getUUID(),1,Integer::sum);window.lastHit.put(target.getUUID(),throughWorld/GAME_TICK_MICROS);
                    if(attack.throwFlag()==0) {Vec3 direction=target.position().subtract(frame.entity.position()).multiply(1,0,1).normalize();target.setDeltaMovement(target.getDeltaMovement().add(direction.scale(knockback(attack.id()))));target.hurtMarked=true;}}
            }
            }
            window.through=last;return;
        }
        var previous=key.actor().taeId()==3024 || key.actor().taeId()==3031?capsules(frame,key.event(),Math.max(first,window.through)):List.<PromisedConsortSourceHitVolumes.Capsule>of();
        for(int step=0;step<=count;step++) {
            long world=first+(last-first)*step/count;
            var capsules=liveContact(key.actor().taeId())?capsules(frame,key.event(),world):warnings.capsules(key.actor(),key.event(),world);
            if(key.actor().taeId()==3024) {var current=capsules;capsules=PromisedConsortSourceHitVolumes.sweptSpheres(previous,current);previous=current;}
            if(key.actor().taeId()==3031) {var current=capsules;capsules=PromisedConsortSourceHitVolumes.sweptBlades(previous,current);previous=current;}
            for(var capsule:capsules) {
                var a=capsule.first();var b=capsule.second();double r=capsule.radius();
                AABB area=new AABB(Math.min(a.x(),b.x())-r,Math.min(a.y(),b.y())-r,Math.min(a.z(),b.z())-r,Math.max(a.x(),b.x())+r,Math.max(a.y(),b.y())+r,Math.max(a.z(),b.z())+r);
                for(var target:owner.level().getEntitiesOfClass(LivingEntity.class,area,e->e!=owner && e.isAlive() && !owner.isAttackImmune(e) && canDamageSegment(key.actor(),e.getUUID()) && window.hit.getOrDefault(e.getUUID(),0)<maximumHits && window.lastHit.getOrDefault(e.getUUID(),-1L)!=throughWorld/GAME_TICK_MICROS)) {
                    AABB box=target.getBoundingBox();
                    if(PromisedConsortSourceCollision.distanceSquared(a,b,new Point(box.minX,box.minY,box.minZ),new Point(box.maxX,box.maxY,box.maxZ))>r*r) continue;
                    if(attack.throwFlag()!=0) {if(grab.tryCapture(target,world)) {claimContact(key.actor(),target.getUUID());window.hit.merge(target.getUUID(),1,Integer::sum);}}
                    else if(damage(target,key.actor(),key.event(),attack.id())) {
                        window.hit.merge(target.getUUID(),1,Integer::sum);window.lastHit.put(target.getUUID(),throughWorld/GAME_TICK_MICROS);
                        Vec3 direction=target.position().subtract(frame.entity.position()).multiply(1,0,1).normalize();
                        target.setDeltaMovement(target.getDeltaMovement().add(direction.scale(knockback(attack.id()))));target.hurtMarked=true;
                    }
                }
            }
        }
        window.through=last;
    }
    public boolean damage(LivingEntity target,Actor actor,int event,int attackId) {
        var row=attackRow(attackId);double physical=PromisedConsortSourceExecutionData.number(row,"atkPhys"),magic=PromisedConsortSourceExecutionData.number(row,"atkMag")+PromisedConsortSourceExecutionData.number(row,"atkThun"),fire=PromisedConsortSourceExecutionData.number(row,"atkFire"),holy=PromisedConsortSourceExecutionData.number(row,"atkDark");
        double total=physical+magic+fire+holy;if(total<=0 && owner.sourceConfig().number("attacks.a"+attackId+".flat")<=0) return false;
        boolean simple=owner.sourceConfig().flag("hit_detection.simple_ranges");
        if(!claimContact(actor,target.getUUID())) return false;
        var kind=holy>=Math.max(physical,Math.max(magic,fire))?PromisedConsortHitSpec.DamageKind.HOLY:fire>=Math.max(physical,magic)?PromisedConsortHitSpec.DamageKind.FIRE:magic>physical?PromisedConsortHitSpec.DamageKind.MAGIC:PromisedConsortHitSpec.DamageKind.PHYSICAL;
        String id="source:"+actor.segmentIndex()+":"+actor.slot()+":"+actor.taeId()+":"+event;
        var channels=new EnumMap<PromisedConsortHitSpec.DamageKind,Double>(PromisedConsortHitSpec.DamageKind.class);
        if(physical>0) channels.put(PromisedConsortHitSpec.DamageKind.PHYSICAL,(double)physical);
        if(magic>0) channels.put(PromisedConsortHitSpec.DamageKind.MAGIC,(double)magic);
        if(fire>0) channels.put(PromisedConsortHitSpec.DamageKind.FIRE,(double)fire);
        if(holy>0) channels.put(PromisedConsortHitSpec.DamageKind.HOLY,(double)holy);
        var outcome=owner.damageTarget(target,actor.actionSequence(),new PromisedConsortHitSpec(id,new DamageFormula(owner.sourceConfig().number("attacks.a"+attackId+".flat"),total/100.0*owner.sourceConfig().number("attacks.a"+attackId+".attack_ratio")),kind,true,PromisedConsortSourceSegmentHits.maximumHits(simple,(int)owner.sourceConfig().number("attacks.a"+attackId+".max_hits_per_target")),channels));
        outcome.ifPresent(hit->owner.recordSourceOutcomes(List.of(hit)));return outcome.isPresent();
    }
    public boolean canDamageSegment(Actor actor,UUID target) {return segmentHits.available(actor,target,worldMicros());}
    public boolean claimContact(Actor actor,UUID target) {
        return segmentHits.claim(actor,target,worldMicros(),Math.round(owner.sourceConfig().number("hit_detection.segment_immunity_ticks")*GAME_TICK_MICROS));
    }
    private void resetContacts(List<PromisedConsortSourceSegmentHits.Claim> claims) {
        for(var claim:claims) {
            for(var entry:windows.entrySet()) if(entry.getKey().actor().equals(claim.actor())) {
                entry.getValue().hit.remove(claim.target());entry.getValue().lastHit.remove(claim.target());
            }
            projectiles.resetContact(claim.actor(),claim.target());
        }
    }
    @Override public Set<Integer> activatedBodyEffects() {return Set.copyOf(activated);}
    private static boolean isRangedCounter(int tae) {
        return tae==3031 || tae==3032 || tae==3022 || tae==3023 || tae>=20002 && tae<=20006;
    }
    @Override public Optional<PromisedConsortSourceAi.Shoot> pollShootReaction(boolean effectInterrupt) {
        if(!shootPending) return Optional.empty();
        shootPending=false;
        if(effectInterrupt || script!=0 || grab.active() || owner.sourceRetainedActionActive()
                || !selectionEnabled || owner.isDisengaging() || owner.sourceMeteorForced()) return Optional.empty();
        // Repeated arrows must not keep cancelling/replacing a counter before contact.
        // End of its body/clones starts a finite cooldown, independently of melee damage.
        long cooldown=Math.max(owner.sourceRangedConfig().globalCooldownTicks(),owner.sourceRangedConfig().defenseSharedCooldownTicks())*GAME_TICK_MICROS;
        if(!PromisedConsortSourceAi.shootReady(worldMicros(),lastEntry.getOrDefault(-2,Long.MIN_VALUE/2),cooldown,lastEntry.containsKey(-3))
                || frames.keySet().stream().anyMatch(a->isRangedCounter(a.taeId()))
                || controller.entry().map(e->e.act()==-2).orElse(false)) return Optional.empty();
        var reaction=PromisedConsortSourceAi.onShoot(context(),rolls(),()->owner.getRandom().nextDouble());
        if(!reaction.sourceSegments().isEmpty()) {
            int act=switch(reaction.sourceSegments().get(0)) {case 3009->4;case 3010->5;case 3012->6;case 3015->10;
                case 3022->15;case 3023->16;case 3031->18;case 3032->19;default->0;};
            if(act!=0 && !owner.sourceConfig().enabled(act)) return Optional.empty();
        }
        lastEntry.put(-2,worldMicros());
        lastEntry.put(-3,worldMicros());
        return Optional.of(reaction);
    }
    @Override public void setSourceTimer(int slot,double seconds) {timers.put(slot,worldMicros()+Math.round(seconds*1_000_000));}
    @Override public boolean bodyEngineExited(Actor actor) {
        Frame frame=frames.get(actor);
        if(frame==null) return false;
        if(PromisedConsortSourceTransition.cinematic(frame.playback)) return owner.sourceTransitionDone();
        // Death has no W_Idle transition. Its completed single play is the
        // terminal entity state, after which Minecraft delivers the rewards.
        return (actor.taeId()==10000 || data.behavior(actor.taeId()).exitAtEnd())
                && frame.playback.sourceMicros(worldMicros())>=bank.requireClip(actor.taeId()).durationMicros();
    }
    @Override public void synchronize(PromisedConsortSourceSession.Snapshot state) {
        snapshot=state;var body=state.actors().stream().filter(a->a.actor().slot()==-1).findFirst();
        boolean fly=body.map(a->bank.activeJumpTable(a.actor().taeId(),27,a.activeEvents())).orElse(false);
        if(!owner.sourceRetainedActionActive()) owner.setNoGravity(script!=20011 && (fly || bodyFrame()!=null && bodyFrame().airborne(worldMicros()) || script==10000));owner.setSourceRigEnabled(true);
        if(body.isEmpty()) {
            // An ended attack must relinquish the held client clip in this same tick.
            // Stagger alone retains its final pose until the configured stun expires.
            if(script!=8700 && owner.sourcePlayback()!=null) owner.setSourcePlayback(null);
            Vec3 movement=owner.getDeltaMovement();double speed=movement.horizontalDistance();
            int pose=speed>.17?2100:speed>.005?2000:20;
            if(speed>.005 && movement.multiply(1,0,1).dot(owner.getLookAngle().multiply(1,0,1))<0) pose=2002;
            if(pose==2000 && approaching!=null && approaching.act()==42) pose=2003;
            owner.setSourceLocomotion(pose);
        }
    }
}
