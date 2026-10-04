package com.tonywww.elder_bosses.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceFfx;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;
import java.util.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** MC sprite particles and the previous gravity material; original clocks/positions stay authoritative. */
public final class ClientConsortSourceEffects {
    static final float GRAVITY_INTENSITY=.85F;
    private static final Set<Integer> BLOOD=Set.of(4080,652220,652222,652223);
    private static final Map<String,Long> BURSTS=new HashMap<>();
    private static Object levelIdentity;
    private static long lastTick=Long.MIN_VALUE;
    private static final ItemStack OLD_ROCK=new ItemStack(Items.CRYING_OBSIDIAN);
    public static boolean replaced(int fxr) {return BLOOD.contains(fxr) || PromisedConsortSourceFfx.isGravity(fxr);}
    static boolean gravityVisual(int id) {return PromisedConsortSourceFfx.isGravity(id) && id!=652286;}
    static boolean gravityBlade(int id) {return id==652210 || id==652225;}
    static float gravityFade(CompoundTag tag,double worldMicros) {
        return (float)PromisedConsortSourceFfx.gravityOpacity(tag.getLong("Born"),tag.getLong("Stop"),worldMicros);
    }
    static float gravitySkillIntensity(PromisedConsortEntity boss,CompoundTag tag) {
        var playback=boss.sourcePlayback();
        int tae=playback==null?0:playback.actor().taeId();
        return PromisedConsortSourceFfx.gravitySkillIntensity(tae,tag.getInt("FXR"));
    }
    static Vec3 gravityPoint(CompoundTag tag,double worldMicros) {
        double elapsed=Math.max(0,worldMicros-tag.getLong("At"))/1_000_000;
        return point(tag).add(tag.getFloat("VX")*elapsed,tag.getFloat("VY")*elapsed,tag.getFloat("VZ")*elapsed);
    }
    public static void tick() {
        var mc=Minecraft.getInstance();
        if(mc.level!=levelIdentity) {BURSTS.clear();lastTick=Long.MIN_VALUE;levelIdentity=mc.level;}
        if(mc.level==null || mc.player==null || mc.isPaused()) return;
        long tick=mc.level.getGameTime();if(tick==lastTick) return;lastTick=tick;BURSTS.values().removeIf(end->end<tick);
        var config=ElderBossesCommonConfig.VALUES.skillVfx();if(!config.enabled()) return;
        for(var boss:mc.level.getEntitiesOfClass(PromisedConsortEntity.class,mc.player.getBoundingBox().inflate(config.renderDistance()))) {
            int budget=Math.min(64,config.particleBudgetPerBossPerTick());var list=boss.sourceVisuals().getList("Instances",Tag.TAG_COMPOUND);
            for(int index=0;index<list.size() && budget>0;index++) {
                var tag=list.getCompound(index);int id=tag.getInt("FXR");if(!replaced(id)) continue;
                double age=(tick*50_000-tag.getLong("Born"))/1_000_000.0;if(age<0) continue;
                long stop=tag.getLong("Stop");if(stop!=Long.MAX_VALUE && tick*50_000-stop>900_000) continue;
                Vec3 p=gravityPoint(tag,tick*50_000);if(p.distanceToSqr(mc.player.position())>config.renderDistance()*config.renderDistance()) continue;
                boolean burst=id==4080 || id==652222 || id==652286;
                if(burst) {
                    String key=boss.getId()+":"+id+":"+tag.getLong("Born")+":"+p;
                    if(age>.8 || BURSTS.putIfAbsent(key,tick+40)!=null) continue;
                }
                int count=Math.min(budget,id==4080?(int)boss.sourceConfig().number("visuals.bleed_burst_particles"):burst?20:BLOOD.contains(id)?(int)boss.sourceConfig().number("visuals.bloodflame_particles_per_tick"):4);budget-=count;
                var random=new Random(boss.getId()*31L+tick*17+index);
                float fade=gravityVisual(id)?gravityFade(tag,tick*50_000)*gravitySkillIntensity(boss,tag):1;
                for(int n=0;n<count;n++) {
                    if(fade<1 && random.nextFloat()>=fade) continue;
                    double angle=random.nextDouble()*Math.PI*2,radius=burst?random.nextDouble()*.8:random.nextDouble()*.35;
                    Vec3 location=p.add(Math.cos(angle)*radius,random.nextDouble()*.5,Math.sin(angle)*radius);
                    ParticleOptions type=id==4080?new BlockParticleOption(ParticleTypes.BLOCK,Blocks.REDSTONE_BLOCK.defaultBlockState()):
                            id==652286?new BlockParticleOption(ParticleTypes.BLOCK,Blocks.CRYING_OBSIDIAN.defaultBlockState()):
                            BLOOD.contains(id)?(n%3==0?ParticleTypes.SMOKE:ParticleTypes.FLAME):ParticleTypes.PORTAL;
                    mc.level.addParticle(type,location.x,location.y,location.z,Math.cos(angle)*.06,.03+random.nextDouble()*.08,Math.sin(angle)*.06);
                }
            }
        }
    }
    public static void render(PoseStack poses,Camera camera,long tick,float partial) {
        var mc=Minecraft.getInstance();var config=ElderBossesCommonConfig.VALUES.skillVfx();
        if(mc.level==null || !config.enabled()) return;
        Vec3 view=camera.getPosition();double time=tick+partial;var buffers=mc.renderBuffers().bufferSource();
        poses.pushPose();poses.translate(-view.x,-view.y,-view.z);
        try {
            for(var boss:mc.level.getEntitiesOfClass(PromisedConsortEntity.class,new AABB(view,view).inflate(config.renderDistance()))) {
                var list=boss.sourceVisuals().getList("Instances",Tag.TAG_COMPOUND);int count=0;
                for(int i=0;i<list.size() && count<12;i++) {
                    var tag=list.getCompound(i);int id=tag.getInt("FXR");if(!gravityVisual(id)) continue;
                    float strength=gravityFade(tag,time*50_000)*GRAVITY_INTENSITY*gravitySkillIntensity(boss,tag);if(strength<=0) continue;
                    Vec3 p=gravityPoint(tag,time*50_000);
                    if(p.distanceToSqr(view)>config.renderDistance()*config.renderDistance()) continue;count++;
                    boolean rock=id==652285;
                    if(rock && (tag.getLong("Stop")==Long.MAX_VALUE || time*50_000<tag.getLong("Stop"))) {
                        // The old renderer was ThrownItemRenderer(CRYING_OBSIDIAN, SIZE_SCALE=1.35).
                        float size=(float)boss.sourceConfig().number("visuals.meteor_size");poses.pushPose();poses.translate(p.x,p.y,p.z);poses.mulPose(camera.rotation());poses.mulPose(new org.joml.Quaternionf().rotationY((float)Math.PI));poses.scale(size,size,size);
                        mc.getItemRenderer().renderStatic(OLD_ROCK,ItemDisplayContext.GROUND,15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,poses,buffers,mc.level,boss.getId());poses.popPose();
                    }
                    if(!ConsortEnergyShader.ready()) continue;
                    float clock=(float)(time/20%1200)+boss.getId()*.137F+i*.713F;
                    ConsortEnergyShader.configure(4,clock,0,0);var consumer=buffers.getBuffer(ConsortEnergyShader.ENERGY);
                    if(rock) ClientConsortEnergyRenderer.rockAura(consumer,poses.last(),p,view,time,false,boss.sourceConfig().number("visuals.meteor_size"),strength*(float)(boss.sourceConfig().number("visuals.gravity_strength_multiplier")/1.5));
                    else {
                        boolean blade=gravityBlade(id);
                        double radius=(blade?.9:id==652211?3.2:2.6)*boss.sourceConfig().number("visuals.gravity_size_multiplier");
                        float brightness=(float)(boss.sourceConfig().number("visuals.gravity_strength_multiplier")/1.5);
                        ClientConsortEnergyRenderer.gravityAura(consumer,poses.last(),p,view,time+i*13.7,radius,blade?radius*.65:radius,
                                Math.min(1,.20F*strength*brightness));
                    }
                    buffers.endBatch(ConsortEnergyShader.ENERGY);
                    if(rock && new Vec3(tag.getFloat("VX"),tag.getFloat("VY"),tag.getFloat("VZ")).lengthSqr()>.01) {
                        Vec3 v=new Vec3(tag.getFloat("VX"),tag.getFloat("VY"),tag.getFloat("VZ")).normalize();
                        double size=boss.sourceConfig().number("visuals.meteor_size")/1.35;
                        ConsortEnergyShader.configure(4,clock,0,0);ClientConsortEnergyRenderer.ribbon(buffers.getBuffer(ConsortEnergyShader.ENERGY),poses.last(),p.subtract(v.scale(2.4*size)),p,.35*size,0x9523EE,.22F*strength);buffers.endBatch(ConsortEnergyShader.ENERGY);
                    }
                }
            }
        } finally {poses.popPose();}
    }
    private static Vec3 point(CompoundTag tag) {return new Vec3(tag.getDouble("X"),tag.getDouble("Y"),tag.getDouble("Z"));}
    private ClientConsortSourceEffects() {}
}
