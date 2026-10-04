package com.tonywww.elder_bosses.client.vfx;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceFfx;
import com.tonywww.elder_bosses.platforms.client.PlatformVertexConsumer;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.RenderType;
import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceFfx.*;

/** Original FXR primitive dimensions, color curves and FLVER meshes on Minecraft materials. */
public final class ClientConsortSourceFfxRenderer {
    private record Vertex(float x,float y,float z,float u,float v,float shade) {}
    private record Mesh(Vertex[] vertices,int[] indices,ResourceLocation texture,float ringRadius) {}
    private static final Set<Integer> GRAVITY_DEBRIS=Set.of(652201,652205,652206);
    private static final class Geometry {static final Map<Integer,List<Mesh>> MODELS=loadGeometry();}
    private static final class Textures {static final Map<Integer,ResourceLocation> DIFFUSE=loadTextures();}
    private ClientConsortSourceFfxRenderer() {}
    public static void render(PoseStack stack,Camera camera,long tick,float partial) {
        var mc=Minecraft.getInstance();var config=ElderBossesCommonConfig.VALUES.skillVfx();
        if(mc.level==null || !config.enabled() || !ConsortSourceShader.ready()) return;
        double now=(tick+partial)/20;Vec3 cameraPoint=camera.getPosition();
        var buffers=mc.renderBuffers().bufferSource();
        var usedTypes=new LinkedHashSet<RenderType>();
        for(var boss:mc.level.getEntitiesOfClass(PromisedConsortEntity.class,new AABB(cameraPoint,cameraPoint).inflate(config.renderDistance()))) {
            int budget=config.particleBudgetPerBossPerTick();
            var instances=boss.sourceVisuals().getList("Instances",Tag.TAG_COMPOUND);
            var list=new ArrayList<CompoundTag>(instances.size());
            for(int index=0;index<instances.size();index++) list.add(instances.getCompound(index));
            var marks=boss.sourceVisuals().getList("Charmed",Tag.TAG_COMPOUND);
            for(int m=0;m<marks.size();m++) {
                var mark=marks.getCompound(m);var player=mc.level.getPlayerByUUID(mark.getUUID("Player"));
                if(player==null || !player.isAlive()) continue;
                var point=com.tonywww.elder_bosses.client.render.ConsortGrabPlayerRenderer.charmPoint(player,(long)(now*1_000_000),partial);
                list.add(com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceVisuals.tag(652295,mark.getLong("Born"),Long.MAX_VALUE,point,player.getLookAngle(),Vec3.ZERO,(long)(now*1_000_000)));
            }
            list.sort(Comparator.comparingInt(t->t.contains("Trace")?0:1));
            for(int index=0;index<list.size() && budget>0;index++) {
                var instance=list.get(index);double born=instance.getLong("Born")/1_000_000.0,active=now-born;
                if(ClientConsortSourceEffects.replaced(instance.getInt("FXR")) || PromisedConsortSourceFfx.isHoly(instance.getInt("FXR"))) continue;
                if(active<0) continue;
                double thickness=1,fadeIn=0,fadeOut=0;
                Vec3 position=new Vec3(instance.getDouble("X"),instance.getDouble("Y"),instance.getDouble("Z"));
                Vec3 velocity=new Vec3(instance.getFloat("VX"),instance.getFloat("VY"),instance.getFloat("VZ"));
                position=position.add(velocity.scale(Math.max(0,now-instance.getLong("At")/1_000_000.0)));
                if(position.distanceToSqr(cameraPoint)>config.renderDistance()*config.renderDistance()) continue;
                Vec3 direction=new Vec3(instance.getFloat("DX"),instance.getFloat("DY"),instance.getFloat("DZ"));
                var nodes=PromisedConsortSourceFfx.get().nodes(instance.getInt("FXR"));
                if(instance.getInt("FXR")==652299) {
                    // One full-blade grey ribbon. The other source nodes add thin white outlines.
                    var appearance=nodes.stream().map(Node::appearance).filter(Objects::nonNull)
                            .filter(a->a.get("name").getAsString().equals("Tracer")).findFirst();
                    if(instance.contains("Trace") && appearance.isPresent()
                            && tracer(stack,cameraPoint,instance,appearance.get(),active,now,buffers,usedTypes,1,0,0)) budget--;
                    continue;
                }
                for(var node:nodes) {
                    JsonObject appearance=node.appearance();
                    if(appearance==null) continue;
                    String type=appearance.get("name").getAsString();
                    if(List.of("PointLight","LensFlare","RadialBlur","Distortion").contains(type)) continue;
                    if((type.equals("Tracer") || type.equals("LegacyTracer")) && instance.contains("Trace")) {
                        if(tracer(stack,cameraPoint,instance,appearance,active,now,buffers,usedTypes,thickness,fadeIn,fadeOut)) budget--;
                        continue;
                    }
                    JsonObject attributes=node.action("NodeAttributes"),particle=node.action("ParticleAttributes"),emitter=node.action("PeriodicEmitter");
                    double delay=field(attributes,"delay",0,0),age=active-delay;if(age<0) continue;
                    double duration=scalar(attributes,"duration",age,0,0,-1),sourceLife=scalar(particle,"duration",age,0,0,0);
                    double life=sourceLife;
                    if(life==0) continue;
                    double stop=instance.getLong("Stop")==Long.MAX_VALUE?Double.POSITIVE_INFINITY:Math.max(delay,instance.getLong("Stop")/1_000_000.0-born);
                    double emitEnd=Math.min(duration<0?Double.POSITIVE_INFINITY:duration,stop-delay);
                    boolean once=node.action("OneTimeEmitter")!=null;
                    double interval=once?Double.POSITIVE_INFINITY:Math.max(1.0/60,scalar(emitter,"interval",age,0,0,1.0/30));
                    double last=Math.min(age,emitEnd);
                    if(last<0) continue;
                    long newest=once?0:(long)Math.floor(last/interval),oldest=once||life<0?0:(long)Math.max(0,Math.ceil((age-life)/interval));
                    int total=(int)scalar(emitter,"totalEmissions",age,0,0,-1);
                    if(total>=0) newest=Math.min(newest,total-1);
                    // Rendering budget affects decoration only; attack instances,
                    // source schedules, collision and damage remain uncapped.
                    oldest=Math.max(oldest,newest-31);
                    int perEmission=Math.max(1,(int)scalar(emitter,"perEmission",age,0,0,1));
                    for(long particleIndex=oldest*perEmission;particleIndex<(newest+1)*perEmission;particleIndex++) {
                        if(budget<=0) break;
                        long emission=particleIndex/perEmission;
                        double emitted=once?0:emission*interval,particleAge=age-emitted;
                        if(life>0 && particleAge>=life) continue;
                        double curveAge=life>0?particleAge*sourceLife/life:particleAge;
                        var motion=particle(node,emitted,curveAge,instance.getLong("Born")^((long)node.path().hashCode()<<32)^particleIndex);
                        Matrix4f matrix=new Matrix4f().translation((float)(position.x-cameraPoint.x),(float)(position.y-cameraPoint.y),(float)(position.z-cameraPoint.z));
                        matrix.translate(0,(float)motion.gravity(),0);
                        int orientation=(int)field(appearance,"orientation",0,0);
                        if(orientation==1) matrix.rotate(camera.rotation());
                        else if(orientation==2 && direction.lengthSqr()>0) matrix.rotate(new Quaternionf().rotationTo(new Vector3f(0,0,-1),new Vector3f((float)direction.x,(float)direction.y,(float)direction.z)));
                        else if(orientation==4) matrix.rotateY((float)Math.atan2(cameraPoint.x-position.x,cameraPoint.z-position.z));
                        matrix.scale(1,1,-1);
                        for(var parent:node.parents()) {transform(matrix,parent.action("StaticNodeTransform"));dynamic(matrix,parent,age);}
                        transform(matrix,node.action("StaticNodeTransform"));
                        dynamic(matrix,node,age);
                        matrix.translate((float)motion.x(),(float)motion.y(),(float)motion.z());
                        var modifier=node.action("ParticleModifier");
                        double sx=scalar(modifier,"scaleX",age,emitted,curveAge,1),sy=field(modifier,"uniformScale",0,0)==1?sx:scalar(modifier,"scaleY",age,emitted,curveAge,1),sz=field(modifier,"uniformScale",0,0)==1?sx:scalar(modifier,"scaleZ",age,emitted,curveAge,1);
                        matrix.scale((float)sx,(float)sy,(float)sz);
                        double[] color=tint(appearance,age,emitted,curveAge),modifierTint=value(modifier,"color",age,emitted,curveAge,1,1,1,1);
                        for(int c=0;c<4;c++) color[c]*=modifierTint[c];
                        if(GRAVITY_DEBRIS.contains(instance.getInt("FXR")) && !type.equals("Model") && !type.equals("RichModel")) {
                            color[0]=.61;color[1]=.13;color[2]=.94;
                        }
                        if(color[3]<.004) continue;
                        int blend=(int)scalar(appearance,"blendMode",age,emitted,curveAge,2);
                        if(type.equals("Model") || type.equals("RichModel")) {
                            int model=(int)scalar(appearance,"model",age,emitted,curveAge,0);
                            double x=scalar(appearance,"sizeX",age,emitted,curveAge,1),y=field(appearance,"uniformScale",0,0)==1?x:scalar(appearance,"sizeY",age,emitted,curveAge,1),z=field(appearance,"uniformScale",0,0)==1?x:scalar(appearance,"sizeZ",age,emitted,curveAge,1);
                            rotate(matrix,scalar(appearance,"rotationX",age,emitted,curveAge,0),scalar(appearance,"rotationY",age,emitted,curveAge,0),scalar(appearance,"rotationZ",age,emitted,curveAge,0));
                            matrix.scale((float)x,(float)y,(float)z);
                            stack.pushPose();stack.last().pose().mul(matrix);
                            for(var mesh:Geometry.MODELS.getOrDefault(model,List.of())) {
                                var typeBuffer=ConsortSourceShader.type(mesh.texture(),blend);usedTypes.add(typeBuffer);
                                var consumer=buffers.getBuffer(typeBuffer);
                                for(int t=0;t+2<mesh.indices().length;t+=3) {
                                    for(int corner=0;corner<4;corner++) {
                                        var v=mesh.vertices()[mesh.indices()[t+Math.min(2,corner)]];
                                        // Widen the holy ring's radial band, retaining its centre radius.
                                        if(model==83022 && thickness!=1) {
                                            double radius=Math.hypot(v.x(),v.z()),wide=Math.max(0,mesh.ringRadius()+(radius-mesh.ringRadius())*thickness);
                                            double scale=radius<1e-6?1:wide/radius;v=new Vertex((float)(v.x()*scale),v.y(),(float)(v.z()*scale),v.u(),v.v(),v.shade());
                                        }
                                        vertex(consumer,stack.last(),v,color,model>=84000);
                                    }
                                }
                            }
                            stack.popPose();budget--;
                        } else if(List.of("BillboardEx","MultiTextureBillboardEx","PointSprite","QuadLine","Tracer","LegacyTracer","GPUStandardParticle").contains(type)) {
                            float width=(float)scalar(appearance,"width",age,emitted,curveAge,scalar(appearance,"size",age,emitted,curveAge,1));
                            float height=(float)(field(appearance,"uniformScale",0,0)==1?width:scalar(appearance,"height",age,emitted,curveAge,width));
                            if(type.equals("QuadLine") && width>height) height*=thickness;
                            else {width*=thickness;if(field(appearance,"uniformScale",0,0)==1) height*=thickness;}
                            if(width<=0 || height<=0) continue;
                            boolean charmMask=instance.getInt("FXR")==652295 && type.equals("MultiTextureBillboardEx");
                            int textureId=(int)scalar(appearance,"texture",age,emitted,curveAge,field(appearance,type.equals("MultiTextureBillboardEx")?"layer1":"texture",0,0));
                            // Charm's layer1 may be solid white; layer2 then holds
                            // the actual luminance glyph, not an RGBA background.
                            if(charmMask && textureId==1) textureId=(int)field(appearance,"layer2",0,0);
                            var texture=Textures.DIFFUSE.get(textureId);
                            if(texture==null) continue;
                            var renderType=charmMask?ConsortSourceShader.masked(texture):ConsortSourceShader.type(texture,blend);usedTypes.add(renderType);
                            stack.pushPose();stack.last().pose().mul(matrix);var consumer=buffers.getBuffer(renderType);
                            var uv=atlasCell(appearance,age,emitted,curveAge,instance.getLong("Born")^particleIndex);
                            for(var v:new Vertex[]{new Vertex(-width/2,-height/2,0,uv.u0(),uv.v0(),1),new Vertex(width/2,-height/2,0,uv.u1(),uv.v0(),1),new Vertex(width/2,height/2,0,uv.u1(),uv.v1(),1),new Vertex(-width/2,height/2,0,uv.u0(),uv.v1(),1)}) vertex(consumer,stack.last(),v,color,false);
                            stack.popPose();budget--;
                        }
                    }
                }
            }
        }
        for(var type:usedTypes) buffers.endBatch(type);
    }
    private static void transform(Matrix4f matrix,JsonObject action) {
        if(action==null) return;matrix.translate((float)field(action,"offset",0,0),(float)field(action,"offset",1,0),(float)field(action,"offset",2,0));
        rotate(matrix,field(action,"rotation",0,0),field(action,"rotation",1,0),field(action,"rotation",2,0));
    }
    private static void dynamic(Matrix4f matrix,PromisedConsortSourceFfx.Node node,double age) {
        var acceleration=node.action("NodeAcceleration");if(acceleration==null) acceleration=node.action("NodeAccelerationPartialFollow");
        var speed=node.action("NodeSpeedSpin");
        matrix.translate(0,(float)(integrate(acceleration,"accelerationY","",age,true)+integrate(speed,"accelerationY","",age,true)),
                (float)(scalar(acceleration,"speedZ",age,0,0,0)*age+integrate(acceleration,"accelerationZ","accelerationMultiplierZ",age,true)
                        +integrate(speed,"speedZ","speedMultiplierZ",age,false)));
        rotate(matrix,integrate(speed,"angularSpeedX","angularSpeedMultiplierX",age,false),
                integrate(speed,"angularSpeedY","angularSpeedMultiplierY",age,false),integrate(speed,"angularSpeedZ","angularSpeedMultiplierZ",age,false));
    }
    private static boolean tracer(PoseStack stack,Vec3 camera,CompoundTag instance,JsonObject action,double active,double now,
                                  net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers,Set<RenderType> used,double thickness,double fadeIn,double fadeOut) {
        var rows=instance.getList("Trace",Tag.TAG_COMPOUND);if(rows.size()<2) return false;
        boolean ordinary=instance.getInt("FXR")==652299;
        var texture=ordinary?null:Textures.DIFFUSE.get((int)scalar(action,"texture",active,0,0,0));if(!ordinary && texture==null) return false;
        var type=ordinary?ConsortSourceShader.SWORD_RIBBON:ConsortSourceShader.type(texture,(int)scalar(action,"blendMode",active,0,0,2));used.add(type);
        var consumer=buffers.getBuffer(type);double duration=Math.max(fadeIn+fadeOut,Math.max(.15,field(action,"segmentDuration",0,1)));
        var baseColor=tint(action,active,0,0);var uv=atlasCell(action,active,0,0,instance.getLong("Born"));
        if(ordinary) baseColor=new double[]{.78,.80,.82,.9};
        int concurrent=(int)field(action,"concurrentSegments",0,60);
        for(int i=Math.max(1,rows.size()-concurrent);i<rows.size();i++) {
            var a=rows.getCompound(i-1);var b=rows.getCompound(i);double age=now-b.getLong("At")/1_000_000.0;
            if(age>duration) continue;
            double width=scalar(action,"width",active,active-age,age,1)*scalar(action,"widthMultiplier",active,active-age,age,1)*thickness;
            if(ordinary) width=Math.max(width,2.5);
            Vec3 p=new Vec3(a.getDouble("X"),a.getDouble("Y"),a.getDouble("Z")),q=new Vec3(b.getDouble("X"),b.getDouble("Y"),b.getDouble("Z"));
            Vec3 side=q.subtract(p).cross(camera.subtract(q)).normalize().scale(width/2);
            Vec3[] points=a.contains("TX") && b.contains("TX")?new Vec3[]{p,new Vec3(a.getDouble("TX"),a.getDouble("TY"),a.getDouble("TZ")),new Vec3(b.getDouble("TX"),b.getDouble("TY"),b.getDouble("TZ")),q}
                    :new Vec3[]{p.subtract(side),p.add(side),q.add(side),q.subtract(side)};
            if(thickness!=1 && a.contains("TX") && b.contains("TX")) {
                Vec3 midA=points[0].add(points[1]).scale(.5),midB=points[2].add(points[3]).scale(.5);
                points[0]=midA.add(points[0].subtract(midA).scale(thickness));points[1]=midA.add(points[1].subtract(midA).scale(thickness));
                points[2]=midB.add(points[2].subtract(midB).scale(thickness));points[3]=midB.add(points[3].subtract(midB).scale(thickness));
            }
            double[] color=baseColor.clone();color[3]*=fadeIn+fadeOut>0?PromisedConsortSourceFfx.opacity(age,duration,0,fadeOut)*PromisedConsortSourceFfx.opacity(active,-1,fadeIn,0):Math.max(0,1-age/duration);
            // A stopped light ribbon must also fade as a whole, including its
            // youngest segment, before the server removes the visual tail.
            double stopped=instance.getLong("Stop")/1_000_000.0;
            if(!ordinary && fadeOut>0 && instance.getLong("Stop")!=Long.MAX_VALUE && now>stopped)
                color[3]*=PromisedConsortSourceFfx.opacity(now-stopped,fadeOut,0,fadeOut);
            float va=(float)(uv.v0()+(uv.v1()-uv.v0())*Math.max(0,1-(now-a.getLong("At")/1_000_000.0)/duration));
            float vb=(float)(uv.v0()+(uv.v1()-uv.v0())*Math.max(0,1-age/duration));
            for(int c=0;c<4;c++) {Vec3 pnt=points[c].subtract(camera);
                if(ordinary) PlatformVertexConsumer.addPositionColor(consumer,stack.last(),(float)pnt.x,(float)pnt.y,(float)pnt.z,channel(color[0]),channel(color[1]),channel(color[2]),channel(color[3]));
                else vertex(consumer,stack.last(),new Vertex((float)pnt.x,(float)pnt.y,(float)pnt.z,c==0||c==3?uv.u0():uv.u1(),c<2?va:vb,1),color,false);}
        }
        return true;
    }
    private static void rotate(Matrix4f matrix,double x,double y,double z) {matrix.rotateY((float)Math.toRadians(y)).rotateX((float)Math.toRadians(x)).rotateZ((float)Math.toRadians(z));}
    private static void vertex(VertexConsumer consumer,PoseStack.Pose pose,Vertex v,double[] color,boolean rock) {
        double shade=rock?v.shade()*.42:1;
        PlatformVertexConsumer.addPositionColorUv(consumer,pose,v.x(),v.y(),v.z(),channel(color[0]*shade),channel(color[1]*shade),channel(color[2]*shade),channel(color[3]),v.u(),v.v());
    }
    private static int channel(double v) {return (int)Math.max(0,Math.min(255,Math.round(v*255)));}
    private static Map<Integer,List<Mesh>> loadGeometry() {
        var stream=ClientConsortSourceFfxRenderer.class.getResourceAsStream("/assets/elder_bosses/boss/promised_consort/source_sfx_geometry.json");
        if(stream==null) throw new IllegalStateException("Missing original special-effect geometry");
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var result=new HashMap<Integer,List<Mesh>>();
            for(var model:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("models")) {
                var row=model.getAsJsonObject();var meshes=new ArrayList<Mesh>();
                for(var m:row.getAsJsonArray("meshes")) {
                    var mesh=m.getAsJsonObject();Vertex[] vertices=new Vertex[mesh.getAsJsonArray("vertices").size()];int i=0;
                    for(var v:mesh.getAsJsonArray("vertices")) {
                        var value=v.getAsJsonObject();JsonArray p=value.getAsJsonArray("position"),uv=value.getAsJsonArray("uv"),normal=value.getAsJsonArray("normal");
                        vertices[i++]=new Vertex(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat(),uv.get(0).getAsFloat(),uv.get(1).getAsFloat(),.55f+.45f*Math.max(0,normal.get(1).getAsFloat()));
                    }
                    int[] indices=new int[mesh.getAsJsonArray("indices").size()];i=0;for(var index:mesh.getAsJsonArray("indices")) indices[i++]=index.getAsInt();
                    ResourceLocation texture=null;
                    for(var t:mesh.getAsJsonArray("material_textures")) {
                        var tex=t.getAsJsonObject();String kind=tex.get("type").getAsString();
                        if(kind.contains("Albedo") || kind.contains("Diffuse")) {
                            String path=tex.get("path").getAsString().replace('\\','/');path=path.substring(path.lastIndexOf('/')+1).replace(".tif",".png");
                            texture=PlatformResourceLocation.id("textures/particle/promised_consort/"+path);break;
                        }
                    }
                    if(texture==null) throw new IllegalArgumentException("Missing original FXR diffuse texture");
                    double min=Double.POSITIVE_INFINITY,max=0;
                    for(var v:vertices) {double radius=Math.hypot(v.x(),v.z());min=Math.min(min,radius);max=Math.max(max,radius);}
                    meshes.add(new Mesh(vertices,indices,texture,(float)((min+max)*.5)));
                }
                result.put(row.get("id").getAsInt(),List.copyOf(meshes));
            }
            return Map.copyOf(result);
        } catch(java.io.IOException e) {throw new IllegalStateException("Cannot read original FXR geometry",e);}
    }
    private static Map<Integer,ResourceLocation> loadTextures() {
        var stream=ClientConsortSourceFfxRenderer.class.getResourceAsStream("/assets/elder_bosses/boss/promised_consort/source_sfx_textures.json");
        try(var reader=new InputStreamReader(Objects.requireNonNull(stream),StandardCharsets.UTF_8)) {
            var result=new HashMap<Integer,ResourceLocation>();
            for(var t:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("textures")) {
                var row=t.getAsJsonObject();String name=row.get("name").getAsString();
                if(name.endsWith("_a")) result.put(Integer.parseInt(name.substring(1,name.length()-2)),PlatformResourceLocation.parse(row.get("resource").getAsString()));
            }
            return Map.copyOf(result);
        } catch(java.io.IOException e) {throw new IllegalStateException("Cannot read original FXR textures",e);}
    }
}
