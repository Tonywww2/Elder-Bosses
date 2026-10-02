package com.tonywww.elder_bosses.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
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
    private static final Set<Integer> GRAVITY=Set.of(652210,652211,652212,652213,652225,652229,652230,652231,652232,652233,652234,652235,652236,652280,652285,652286);
    private static final Set<Integer> BLOOD=Set.of(4080,652220,652222,652223);
    private static final Map<String,Long> BURSTS=new HashMap<>();
    private static Object levelIdentity;
    private static long lastTick=Long.MIN_VALUE;
    private static final ItemStack OLD_ROCK=new ItemStack(Items.CRYING_OBSIDIAN);
    public static boolean replaced(int fxr) {return BLOOD.contains(fxr) || GRAVITY.contains(fxr);}
    public static boolean hasGravity(PromisedConsortEntity boss) {
        var list=boss.sourceVisuals().getList("Instances",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++) {var tag=list.getCompound(i);int id=tag.getInt("FXR");
            if(GRAVITY.contains(id) && id!=652285 && id!=652286 && (tag.getLong("Stop")==Long.MAX_VALUE || tag.getLong("At")-tag.getLong("Stop")<500_000)) return true;}
        return false;
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
                long stop=tag.getLong("Stop");if(stop!=Long.MAX_VALUE && tick*50_000-stop>600_000) continue;
                Vec3 p=point(tag);if(p.distanceToSqr(mc.player.position())>config.renderDistance()*config.renderDistance()) continue;
                boolean burst=id==4080 || id==652222 || id==652286;
                if(burst) {
                    String key=boss.getId()+":"+id+":"+tag.getLong("Born")+":"+p;
                    if(age>.8 || BURSTS.putIfAbsent(key,tick+40)!=null) continue;
                }
                int count=Math.min(budget,id==4080?(int)boss.sourceConfig().number("visuals.bleed_burst_particles"):burst?20:BLOOD.contains(id)?(int)boss.sourceConfig().number("visuals.bloodflame_particles_per_tick"):4);budget-=count;
                var random=new Random(boss.getId()*31L+tick*17+index);
                for(int n=0;n<count;n++) {
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
                    var tag=list.getCompound(i);int id=tag.getInt("FXR");if(!GRAVITY.contains(id) || id==652286) continue;
                    if(time*50_000<tag.getLong("Born")) continue;
                    double afterStop=tag.getLong("Stop")==Long.MAX_VALUE?0:Math.max(0,(time*50_000-tag.getLong("Stop"))/1_000_000.0);
                    if(afterStop>.5) continue;
                    Vec3 p=point(tag).add(tag.getFloat("VX")*(time*50_000-tag.getLong("At"))/1_000_000.0,tag.getFloat("VY")*(time*50_000-tag.getLong("At"))/1_000_000.0,tag.getFloat("VZ")*(time*50_000-tag.getLong("At"))/1_000_000.0);
                    if(p.distanceToSqr(view)>config.renderDistance()*config.renderDistance()) continue;count++;
                    boolean rock=id==652285;
                    if(rock) {
                        // The old renderer was ThrownItemRenderer(CRYING_OBSIDIAN, SIZE_SCALE=1.35).
                        float size=(float)boss.sourceConfig().number("visuals.meteor_size");poses.pushPose();poses.translate(p.x,p.y,p.z);poses.mulPose(camera.rotation());poses.mulPose(new org.joml.Quaternionf().rotationY((float)Math.PI));poses.scale(size,size,size);
                        mc.getItemRenderer().renderStatic(OLD_ROCK,ItemDisplayContext.GROUND,15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,poses,buffers,mc.level,boss.getId());poses.popPose();
                    }
                    if(!ConsortEnergyShader.ready()) continue;
                    float strength=(float)(1-afterStop*2);float clock=(float)(time/20%1200);
                    ConsortEnergyShader.configure(4,clock,0,0);var consumer=buffers.getBuffer(ConsortEnergyShader.ENERGY);
                    if(rock) ClientConsortEnergyRenderer.rockAura(consumer,poses.last(),p,view,time,false,boss.sourceConfig().number("visuals.meteor_size"),strength*(float)(boss.sourceConfig().number("visuals.gravity_strength_multiplier")/1.5));
                    else {
                        boolean blade=id==652210 || id==652225;
                        double radius=(blade?.9:id==652211?3.2:2.6)*boss.sourceConfig().number("visuals.gravity_size_multiplier");
                        float brightness=(float)(boss.sourceConfig().number("visuals.gravity_strength_multiplier")/1.5);
                        // Reuse the old purple gravity surface, enlarged and brighter.
                        ClientConsortEnergyRenderer.defenseSurface(consumer,poses.last(),p,radius,blade?radius*.45:radius*.7,0,360,0x9B22EF,Math.min(1,.42F*strength*brightness));
                        ClientConsortEnergyRenderer.ringWall(consumer,poses.last(),p,radius,blade?.5:1.4,0xAF2CFF,Math.min(1,.55F*strength*brightness),24);
                        ClientConsortEnergyRenderer.sparkle(consumer,poses.last(),p,view,blade?.8:1.7,0xC66CFF,Math.min(1,.85F*strength*brightness));
                    }
                    buffers.endBatch(ConsortEnergyShader.ENERGY);
                    if(rock && new Vec3(tag.getFloat("VX"),tag.getFloat("VY"),tag.getFloat("VZ")).lengthSqr()>.01) {
                        Vec3 v=new Vec3(tag.getFloat("VX"),tag.getFloat("VY"),tag.getFloat("VZ")).normalize();
                        double size=boss.sourceConfig().number("visuals.meteor_size")/1.35;
                        ConsortEnergyShader.configure(8,clock,0,0);ClientConsortEnergyRenderer.ribbon(buffers.getBuffer(ConsortEnergyShader.ENERGY),poses.last(),p.subtract(v.scale(2.4*size)),p,.35*size,0x9523EE,.8F*strength);buffers.endBatch(ConsortEnergyShader.ENERGY);
                    }
                }
            }
        } finally {poses.popPose();}
    }
    private static Vec3 point(CompoundTag tag) {return new Vec3(tag.getDouble("X"),tag.getDouble("Y"),tag.getDouble("Z"));}
    private ClientConsortSourceEffects() {}
}
