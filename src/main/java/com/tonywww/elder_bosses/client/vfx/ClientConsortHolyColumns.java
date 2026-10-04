package com.tonywww.elder_bosses.client.vfx;

import com.mojang.blaze3d.vertex.*;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortCloneEntity;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortCombatState;
import com.tonywww.elder_bosses.boss.promisedconsort.source.*;
import com.tonywww.elder_bosses.platforms.client.PlatformVertexConsumer;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;
import java.util.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.*;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortHolyColumns.*;

/** All consort holy decoration: two square prisms, then a separate ground halo. */
public final class ClientConsortHolyColumns {
    private static final RenderType MATERIAL=ColumnType.material();
    private static final Map<PromisedConsortEntity,Effects> BOSSES=new WeakHashMap<>();
    private static Object effectLevel;
    private static final int MAX_COLUMNS=128,MAX_SOURCES=512;
    private static final class Emission {
        Vec3 lastPoint;double lastTime,lastSeen;
        Emission(Vec3 point,double time) {lastPoint=point;lastTime=time;lastSeen=time;}
    }
    private record Column(Vec3 floor,double born,Settings settings,double size) {}
    private static final class Effects {
        final Map<String,Emission> seen=new LinkedHashMap<>();
        final ArrayDeque<Column> columns=new ArrayDeque<>();
        final Map<String,Settings> settings=new HashMap<>();
        com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSourceConfigSnapshot config;
        long tick=Long.MIN_VALUE;int spawned;
    }
    private ClientConsortHolyColumns() {}
    public static void render(PoseStack poses,Camera camera,long tick,float partial) {
        var mc=Minecraft.getInstance();var config=ElderBossesCommonConfig.VALUES.skillVfx();
        if(effectLevel!=mc.level || !config.enabled()) {BOSSES.clear();effectLevel=mc.level;}
        if(mc.level==null || !config.enabled()) return;
        BOSSES.keySet().removeIf(b->b.isRemoved() || !b.isAlive() || b.level()!=mc.level || b.combatState()==PromisedConsortCombatState.DORMANT);
        Vec3 view=camera.getPosition();double now=tick+partial;
        var visible=mc.level.getEntitiesOfClass(PromisedConsortEntity.class,new AABB(view,view).inflate(config.renderDistance()));
        for(var boss:visible) {
            if(!boss.isAlive() || boss.combatState()==PromisedConsortCombatState.DORMANT) continue;
            Effects effects=BOSSES.computeIfAbsent(boss,b->new Effects());
            if(effects.config!=boss.sourceConfig()) {
                effects.columns.clear();effects.seen.clear();effects.settings.clear();effects.config=boss.sourceConfig();
            }
            if(effects.tick!=tick) {effects.tick=tick;effects.spawned=0;}
            effects.columns.removeIf(c->now-c.born()>=c.settings().totalTicks());
            effects.seen.values().removeIf(e->now-e.lastSeen>40);
            var instances=boss.sourceVisuals().getList("Instances",Tag.TAG_COMPOUND);
            for(int i=0;i<instances.size();i++) {
                var tag=instances.getCompound(i);int fxr=tag.getInt("FXR");
                if(!PromisedConsortSourceFfx.isHoly(fxr)) continue;
                double born=tag.getLong("Born")/50_000.0;if(now<born) continue;
                var point=ClientConsortSourceEffects.gravityPoint(tag,now*50_000);
                String key="fx:"+tag.getLong("VisualId")+":"+tag.getLong("Born");
                // Long moving projectiles leave individually decaying columns along their path.
                boolean moving=(fxr==652252 || fxr==652293) && tag.getLong("Stop")==Long.MAX_VALUE;
                observe(boss,effects,key,profile(fxr),point,born,now,1,moving,config.particleBudgetPerBossPerTick());
            }
            transition(boss,effects,now,partial,config.particleBudgetPerBossPerTick());
        }
        for(var entity:mc.level.entitiesForRendering()) {
            if(!(entity instanceof PromisedConsortCloneEntity clone) || clone.cinematicMiquella() || clone.isRemoved()
                    || clone.distanceToSqr(view)>config.renderDistance()*config.renderDistance()) continue;
            var owner=clone.cinematicOwner();var effects=BOSSES.get(owner);
            if(owner==null || effects==null || !owner.isAlive()) continue;
            Vec3 point=new Vec3(net.minecraft.util.Mth.lerp(partial,clone.xo,clone.getX()),
                    net.minecraft.util.Mth.lerp(partial,clone.yo,clone.getY()),net.minecraft.util.Mth.lerp(partial,clone.zo,clone.getZ()));
            observe(owner,effects,"clone:"+clone.getUUID(),"clone",point,clone.appearTick(),now,1,false,config.particleBudgetPerBossPerTick());
        }
        var buffers=mc.renderBuffers().bufferSource();VertexConsumer consumer=null;int count=0;
        poses.pushPose();poses.translate(-view.x,-view.y,-view.z);
        try {
            for(var boss:visible) {
                var effects=BOSSES.get(boss);if(effects==null) continue;
                int perBoss=0;
                // Closest encounters are already limited by render distance; cap decoration only.
                for(var iterator=effects.columns.descendingIterator();iterator.hasNext();) {
                    var column=iterator.next();
                    if(column.floor().distanceToSqr(view)>config.renderDistance()*config.renderDistance()) continue;
                    if(perBoss++>=Math.min(MAX_COLUMNS,config.particleBudgetPerBossPerTick()) || count++>=512) break;
                    var phase=envelope(column.settings(),now-column.born());
                    if(phase.alpha()<=0 && phase.haloAlpha()<=0) continue;
                    if(consumer==null) consumer=buffers.getBuffer(MATERIAL);
                    draw(consumer,poses.last(),column,phase);
                }
            }
            if(consumer!=null) buffers.endBatch(MATERIAL);
        } finally {poses.popPose();}
    }
    private static Settings settings(PromisedConsortEntity boss,Effects effects,String profile) {
        return effects.settings.computeIfAbsent(profile,p->PromisedConsortHolyColumns.settings(boss.sourceConfig(),p));
    }
    private static void observe(PromisedConsortEntity boss,Effects effects,String key,String profile,Vec3 point,
                                double born,double now,double size,boolean moving,int budget) {
        observe(boss,effects,key,profile,point,born,now,size,moving,budget,Double.POSITIVE_INFINITY);
    }
    private static void observe(PromisedConsortEntity boss,Effects effects,String key,String profile,Vec3 point,
                                double born,double now,double size,boolean moving,int budget,double columnTicks) {
        var settings=settings(boss,effects,profile).limitedTo(columnTicks);
        var old=effects.seen.get(key);
        if(old!=null) {
            old.lastSeen=now;
            if(!moving || now-old.lastTime<2 || point.distanceToSqr(old.lastPoint)<Math.pow(settings.width()*.75,2)) return;
            born=now;
        } else if(now-born>=settings.totalTicks() && !moving) return;
        if(effects.spawned>=Math.min(64,budget)) return;
        if(old==null) {
            old=new Emission(point,now);effects.seen.put(key,old);
            if(effects.seen.size()>MAX_SOURCES) effects.seen.remove(effects.seen.keySet().iterator().next());
        } else {old.lastPoint=point;old.lastTime=now;}
        // One floor ray per emitted column. No vertex-by-vertex terrain sampling or frame raycasts.
        Vec3 floor=ground(boss,point).add(0,.035,0);
        effects.columns.addLast(new Column(floor,moving?now:born,settings,size));effects.spawned++;
        while(effects.columns.size()>MAX_COLUMNS) effects.columns.removeFirst();
    }
    private static Vec3 ground(PromisedConsortEntity boss,Vec3 point) {
        var top=point.add(0,2,0);
        var bottom=new Vec3(point.x,boss.level().getMinBuildHeight(),point.z);
        var hit=boss.level().clip(new net.minecraft.world.level.ClipContext(top,bottom,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,boss));
        return new Vec3(point.x,hit.getType()==HitResult.Type.BLOCK?hit.getLocation().y:boss.getY(),point.z);
    }
    private static void transition(PromisedConsortEntity boss,Effects effects,double now,float partial,int budget) {
        var playback=boss.sourcePlayback();
        if(boss.combatState()!=PromisedConsortCombatState.TRANSITION || !PromisedConsortSourceTransition.cinematic(playback)) return;
        double seconds=boss.sourceTransitionSeconds(partial),scale=PromisedConsortSourceTransition.scale(playback);
        double start=playback.startWorldMicros()/50_000.0;
        String sequence="transition:"+playback.actor().actionSequence()+":";
        if(seconds>=PromisedConsortSourceTransition.LIGHT_BEGIN)
            observe(boss,effects,sequence+"gate","gate",boss.sourceTransitionGate(),start+PromisedConsortSourceTransition.LIGHT_BEGIN*20*scale,now,1,false,budget,(PromisedConsortSourceTransition.LIGHT_END-PromisedConsortSourceTransition.LIGHT_BEGIN)*20*scale);
        if(seconds>=PromisedConsortSourceTransition.APPEAR)
            observe(boss,effects,sequence+"bright","gate",boss.sourceTransitionGate(),start+PromisedConsortSourceTransition.APPEAR*20*scale,now,1.3,false,budget,(PromisedConsortSourceTransition.LIGHT_END-PromisedConsortSourceTransition.APPEAR)*20*scale);
        if(seconds>=PromisedConsortSourceTransition.BACK_LIGHT_BEGIN)
            observe(boss,effects,sequence+"back","back",boss.sourceTransitionBackPoint(partial),start+PromisedConsortSourceTransition.BACK_LIGHT_BEGIN*20*scale,now,1,false,budget,(PromisedConsortSourceTransition.BACK_LIGHT_END-PromisedConsortSourceTransition.BACK_LIGHT_BEGIN)*20*scale);
        if(seconds>=PromisedConsortSourceTransition.TELEPORT)
            observe(boss,effects,sequence+"teleport","back",boss.sourceTransitionBackPoint(partial),start+PromisedConsortSourceTransition.TELEPORT*20*scale,now,1.35,false,budget,(PromisedConsortSourceTransition.BACK_LIGHT_END-PromisedConsortSourceTransition.TELEPORT)*20*scale);
    }
    private static void draw(VertexConsumer consumer,PoseStack.Pose pose,Column c,Envelope phase) {
        var s=c.settings();
        if(phase.alpha()>0) {
            double width=s.width()*phase.scale()*c.size(),height=s.height()*c.size();
            // Draw outer gold first, then the pale core. Neither writes depth, so both layers stay visible.
            prism(consumer,pose,c.floor(),width,height,0xFFE5A0,phase.alpha());
            prism(consumer,pose,c.floor().add(0,.004,0),width*s.innerRatio(),height*1.03,0xFFFBE9,phase.alpha());
        } else if(phase.haloAlpha()>0) {
            double radius=s.haloRadius()*phase.haloScale()*c.size(),inner=radius*(1-s.haloWidthRatio());
            for(int i=0;i<32;i++) {
                double a=i*Math.PI/16,b=(i+1)*Math.PI/16;
                quad(consumer,pose,c.floor().add(Math.cos(a)*inner,0,Math.sin(a)*inner),c.floor().add(Math.cos(a)*radius,0,Math.sin(a)*radius),
                        c.floor().add(Math.cos(b)*radius,0,Math.sin(b)*radius),c.floor().add(Math.cos(b)*inner,0,Math.sin(b)*inner),0xFFF0BE,phase.haloAlpha());
            }
        }
    }
    private static void prism(VertexConsumer consumer,PoseStack.Pose pose,Vec3 p,double width,double height,int color,double alpha) {
        double half=width*.5;Vec3 a=p.add(-half,0,-half),b=p.add(half,0,-half),c=p.add(half,0,half),d=p.add(-half,0,half);
        Vec3 at=a.add(0,height,0),bt=b.add(0,height,0),ct=c.add(0,height,0),dt=d.add(0,height,0);
        quad(consumer,pose,a,b,bt,at,color,alpha);quad(consumer,pose,b,c,ct,bt,color,alpha);
        quad(consumer,pose,c,d,dt,ct,color,alpha);quad(consumer,pose,d,a,at,dt,color,alpha);
        quad(consumer,pose,at,bt,ct,dt,color,alpha);quad(consumer,pose,d,c,b,a,color,alpha);
    }
    private static void quad(VertexConsumer consumer,PoseStack.Pose pose,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,double alpha) {
        vertex(consumer,pose,a,color,alpha);vertex(consumer,pose,b,color,alpha);vertex(consumer,pose,c,color,alpha);vertex(consumer,pose,d,color,alpha);
    }
    private static void vertex(VertexConsumer consumer,PoseStack.Pose pose,Vec3 p,int color,double alpha) {
        PlatformVertexConsumer.addPositionColor(consumer,pose,(float)p.x,(float)p.y,(float)p.z,(color>>16)&255,(color>>8)&255,color&255,(int)Math.round(alpha*255));
    }
    private static final class ColumnType extends RenderType {
        private ColumnType() {super("unused",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,1536,false,false,()->{},()->{});}
        static RenderType material() {
            return create("elder_bosses_holy_columns",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,1536,false,false,
                    CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
        }
    }
}
