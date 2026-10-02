package com.tonywww.elder_bosses.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourcePlayerPose;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.resources.ResourceLocation;
import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Segmented Minecraft skin follows the original player's70890 skeleton. */
public final class ConsortGrabPlayerRenderer {
    private record Piece(int bone,Matrix4f bindBasis,ModelPart skin,ModelPart overlay) {}
    private static final Map<Boolean,List<Piece>> PIECES=new HashMap<>();
    private static final Map<String,ModelPart> ARMOR=new HashMap<>();
    private ConsortGrabPlayerRenderer() {}
    public static PromisedConsortEntity owner(Player player) {
        if(player.level()==null) return null;
        for(var boss:player.level().getEntitiesOfClass(PromisedConsortEntity.class,player.getBoundingBox().inflate(192))) {
            var state=boss.sourceGrab();
            if(state.hasUUID("Victim") && state.getUUID("Victim").equals(player.getUUID())) return boss;
        }
        return null;
    }
    public static void lockLocalInput() {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || owner(mc.player)==null) return;
        for(var key:List.of(mc.options.keyUp,mc.options.keyDown,mc.options.keyLeft,mc.options.keyRight,mc.options.keyJump,
                mc.options.keyShift,mc.options.keySprint,mc.options.keyAttack,mc.options.keyUse)) key.setDown(false);
        mc.player.input.forwardImpulse=0;mc.player.input.leftImpulse=0;mc.player.input.jumping=false;
        mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }
    /** SpEffect19681 -> Vfx20050560 -> FXR652295, original c0000 dummy92/Head. */
    public static net.minecraft.world.phys.Vec3 charmPoint(Player player,long now,float partial) {
        var boss=owner(player);
        net.minecraft.world.phys.Vec3 base=new net.minecraft.world.phys.Vec3(
                net.minecraft.util.Mth.lerp(partial,player.xo,player.getX()),net.minecraft.util.Mth.lerp(partial,player.yo,player.getY()),net.minecraft.util.Mth.lerp(partial,player.zo,player.getZ()));
        if(boss==null) return base.add(0,player.getEyeHeight(),0).add(player.getLookAngle().scale(.1848220676));
        var pose=PromisedConsortSourcePlayerPose.get();int head=pose.bone("Head");
        Vector3f point=new Vector3f(.0000216663f,1.634678f,-.18482207f).sub(pose.bind(head));
        pose.skin(head,com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTimeWarp.from(boss.sourceGrab().getLongArray("Warp")).sourceAt(Math.max(0,now-boss.sourceGrab().getLong("StartMicros")))).transformPosition(point);
        point.z=-point.z;point.rotateY((float)Math.toRadians(-boss.sourceGrab().getFloat("Yaw")));
        return base.add(point.x,point.y,point.z);
    }
    public static boolean render(AbstractClientPlayer player,float partial,PoseStack stack,MultiBufferSource buffers,int light) {
        var boss=owner(player);if(boss==null) return false;
        var state=boss.sourceGrab();long now=boss.level().getGameTime()*50_000L+(long)(partial*50_000),elapsed=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTimeWarp.from(state.getLongArray("Warp")).sourceAt(Math.max(0,now-state.getLong("StartMicros")));
        //? if forge {
        var texture=player.getSkinTextureLocation();boolean slim="slim".equals(player.getModelName());
        //?} else {
        /*var texture=player.getSkin().texture();boolean slim=player.getSkin().model()==net.minecraft.client.resources.PlayerSkin.Model.SLIM;
        *///?}
        var pose=PromisedConsortSourcePlayerPose.get();
        stack.pushPose();
        stack.mulPose(new org.joml.Quaternionf().rotationY((float)Math.toRadians(-state.getFloat("Yaw"))));
        stack.scale(1,1,-1);
        for(var piece:PIECES.computeIfAbsent(slim,ConsortGrabPlayerRenderer::build)) {
            stack.pushPose();var transform=pose.skin(piece.bone(),elapsed).mul(piece.bindBasis());
            stack.last().pose().mul(transform);
            stack.last().normal().mul(new org.joml.Matrix3f(transform).invert().transpose());
            var consumer=buffers.getBuffer(RenderType.entityTranslucent(texture));
            //? if forge {
            piece.skin().render(stack,consumer,light,OverlayTexture.NO_OVERLAY,1,1,1,1);
            piece.overlay().render(stack,consumer,light,OverlayTexture.NO_OVERLAY,1,1,1,1);
            //?} else {
            /*piece.skin().render(stack,consumer,light,OverlayTexture.NO_OVERLAY,-1);
            piece.overlay().render(stack,consumer,light,OverlayTexture.NO_OVERLAY,-1);
            *///?}
            stack.popPose();
        }
        renderArmor(player,elapsed,slim,stack,buffers,light);
        renderHeld(player,elapsed,stack,buffers,light);
        renderCape(player,elapsed,stack,buffers,light);
        stack.popPose();return true;
    }
    private static void renderArmor(AbstractClientPlayer player,long elapsed,boolean slim,PoseStack stack,MultiBufferSource buffers,int light) {
        var pieces=PIECES.get(slim);var pose=PromisedConsortSourcePlayerPose.get();
        for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}) {
            var item=player.getItemBySlot(slot);if(!(item.getItem() instanceof ArmorItem armor)) continue;
            var textures=new ArrayList<ResourceLocation>();int color=0xffffff;
            //? if forge {
            String material=armor.getMaterial().getName(),namespace="minecraft",name=material;
            if(material.contains(":")) {namespace=material.substring(0,material.indexOf(':'));name=material.substring(material.indexOf(':')+1);}
            String base=namespace+":textures/models/armor/"+name+"_layer_"+(slot==EquipmentSlot.LEGS?2:1);
            textures.add(PlatformResourceLocation.parse(net.minecraftforge.client.ForgeHooksClient.getArmorTexture(player,item,base+".png",slot,null)));
            if(item.getItem() instanceof net.minecraft.world.item.DyeableLeatherItem dye) {
                color=dye.getColor(item);textures.add(PlatformResourceLocation.parse(net.minecraftforge.client.ForgeHooksClient.getArmorTexture(player,item,base+"_overlay.png",slot,"overlay")));
            }
            //?} else {
            /*for(var layer:armor.getMaterial().value().layers()) textures.add(layer.texture(slot==EquipmentSlot.LEGS));
            if(item.is(net.minecraft.tags.ItemTags.DYEABLE)) color=net.minecraft.world.item.component.DyedItemColor.getOrDefault(item,0xa06540);
            *///?}
            for(int index=0;index<pieces.size();index++) {
                boolean torso=index<3,head=index==3,arm=index>=4 && (index-4)%4<2,leg=index>=4 && !arm;
                if(!(slot==EquipmentSlot.HEAD && head || slot==EquipmentSlot.CHEST && (torso||arm)
                        || slot==EquipmentSlot.LEGS && (torso||leg) || slot==EquipmentSlot.FEET && leg)) continue;
                int width=head?8:torso?8:4,height=head?8:torso?4:6,depth=head?8:4;
                int u=head?0:torso?16:arm?40:0,v=head?0:torso?24-index*4:16+(index-4)%2*6;
                boolean mirror=index>=4 && index<8;
                String key=slot+":"+index+":"+slim;
                var part=ARMOR.computeIfAbsent(key,k->armorBox(u,v,width,height,depth,mirror,slot==EquipmentSlot.LEGS?.3f:.6f,head));
                var piece=pieces.get(index);stack.pushPose();Matrix4f transform=pose.skin(piece.bone(),elapsed).mul(piece.bindBasis());
                stack.last().pose().mul(transform);stack.last().normal().mul(new org.joml.Matrix3f(transform).invert().transpose());
                for(int layer=0;layer<textures.size();layer++) {
                    //? if forge {
                    var consumer=net.minecraft.client.renderer.entity.ItemRenderer.getArmorFoilBuffer(buffers,RenderType.armorCutoutNoCull(textures.get(layer)),false,item.hasFoil());
                    //?} else {
                    /*var consumer=net.minecraft.client.renderer.entity.ItemRenderer.getArmorFoilBuffer(buffers,RenderType.armorCutoutNoCull(textures.get(layer)),item.hasFoil());
                    *///?}
                    int tint=layer==0?color:0xffffff;
                    //? if forge {
                    part.render(stack,consumer,light,OverlayTexture.NO_OVERLAY,(tint>>16&255)/255f,(tint>>8&255)/255f,(tint&255)/255f,1);
                    //?} else {
                    /*part.render(stack,consumer,light,OverlayTexture.NO_OVERLAY,0xff000000|tint);
                    *///?}
                }
                stack.popPose();
            }
        }
    }
    private static ModelPart armorBox(int u,int v,int w,int h,int d,boolean mirror,float inflate,boolean head) {
        MeshDefinition mesh=new MeshDefinition();mesh.getRoot().addOrReplaceChild("cube",CubeListBuilder.create().texOffs(u,v).mirror(mirror)
                .addBox(-w/2f,head?-h:0,-d/2f,w,h,d,new CubeDeformation(inflate)),PartPose.ZERO);
        return LayerDefinition.create(mesh,64,32).bakeRoot().getChild("cube");
    }
    private static void renderHeld(AbstractClientPlayer player,long elapsed,PoseStack stack,MultiBufferSource buffers,int light) {
        var pose=PromisedConsortSourcePlayerPose.get();
        for(boolean left:new boolean[]{false,true}) {
            var item=left?player.getOffhandItem():player.getMainHandItem();if(item.isEmpty()) continue;
            boolean actualLeft=left==(player.getMainArm()==net.minecraft.world.entity.HumanoidArm.RIGHT);
            stack.pushPose();stack.last().pose().mul(pose.skin(pose.bone(actualLeft?"L_Hand":"R_Hand"),elapsed));
            stack.scale(.8f,-.8f,.8f);stack.mulPose(new org.joml.Quaternionf().rotationX((float)-Math.PI/2).rotateY((float)Math.PI));
            stack.translate(0,.125,-.625);
            Minecraft.getInstance().getItemRenderer().renderStatic(player,item,actualLeft?ItemDisplayContext.THIRD_PERSON_LEFT_HAND:ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    actualLeft,stack,buffers,player.level(),light,OverlayTexture.NO_OVERLAY,player.getId());stack.popPose();
        }
    }
    private static void renderCape(AbstractClientPlayer player,long elapsed,PoseStack stack,MultiBufferSource buffers,int light) {
        //? if forge {
        var texture=player.getCloakTextureLocation();
        //?} else {
        /*var texture=player.getSkin().capeTexture();
        *///?}
        if(texture==null || !player.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.CAPE)) return;
        var part=ARMOR.computeIfAbsent("cape",key->{MeshDefinition mesh=new MeshDefinition();mesh.getRoot().addOrReplaceChild("cape",
                CubeListBuilder.create().texOffs(0,0).addBox(-5,0,2,10,16,1),PartPose.ZERO);return LayerDefinition.create(mesh,64,32).bakeRoot().getChild("cape");});
        stack.pushPose();var pose=PromisedConsortSourcePlayerPose.get();stack.last().pose().mul(pose.skin(pose.bone("Spine1"),elapsed));stack.scale(.8f,-.8f,.8f);
        var consumer=buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        //? if forge {
        part.render(stack,consumer,light,OverlayTexture.NO_OVERLAY,1,1,1,1);
        //?} else {
        /*part.render(stack,consumer,light,OverlayTexture.NO_OVERLAY,-1);
        *///?}
        stack.popPose();
    }
    private static List<Piece> build(boolean slim) {
        var list=new ArrayList<Piece>();var pose=PromisedConsortSourcePlayerPose.get();
        // Rest fitting is shared by every70890 frame, with no per-frame limb scaling.
        int pelvis=pose.bone("Pelvis"),spine=pose.bone("Spine"),spine1=pose.bone("Spine1"),head=pose.bone("Head");
        add(list,pelvis,spine,16,24,16,40,8,4,4,false,false);
        add(list,spine,spine1,16,20,16,36,8,4,4,false,false);
        add(list,spine1,head,16,16,16,32,8,4,4,false,false);
        Matrix4f headBasis=new Matrix4f().scale(.8f,-.8f,.8f);
        list.add(new Piece(head,headBasis,box(0,0,8,8,8,false,0,true),box(32,0,8,8,8,false,.35f,true)));
        for(String side:new String[]{"L_","R_"}) {
            boolean left=side.equals("L_");int armWidth=slim?3:4;
            add(list,pose.bone(side+"UpperArm"),pose.bone(side+"Elbow"),left?32:40,left?48:16,left?48:40,left?48:32,armWidth,6,4,false,false);
            add(list,pose.bone(side+"Forearm"),pose.bone(side+"Hand"),left?32:40,left?54:22,left?48:40,left?54:38,armWidth,6,4,false,false);
            add(list,pose.bone(side+"Thigh"),pose.bone(side+"Knee"),left?16:0,left?48:16,left?0:0,left?48:32,4,6,4,false,false);
            add(list,pose.bone(side+"Calf"),pose.bone(side+"Foot"),left?16:0,left?54:22,left?0:0,left?54:38,4,6,4,false,false);
        }
        return List.copyOf(list);
    }
    private static void add(List<Piece> list,int bone,int end,int u,int v,int ou,int ov,int width,int height,int depth,boolean mirror,boolean head) {
        var pose=PromisedConsortSourcePlayerPose.get();Vector3f y=pose.bind(end).sub(pose.bind(bone));float length=y.length();y.normalize();
        Vector3f z=new Vector3f(0,0,1),x=new Vector3f(y).cross(z).normalize();z=new Vector3f(x).cross(y).normalize();
        Matrix4f basis=new Matrix4f().setColumn(0,new org.joml.Vector4f(x.mul(.8f),0))
                .setColumn(1,new org.joml.Vector4f(y.mul(length*16/height),0)).setColumn(2,new org.joml.Vector4f(z.mul(.8f),0));
        list.add(new Piece(bone,basis,box(u,v,width,height,depth,mirror,0,head),box(ou,ov,width,height,depth,mirror,.2f,head)));
    }
    private static ModelPart box(int u,int v,int width,int height,int depth,boolean mirror,float inflate,boolean head) {
        MeshDefinition mesh=new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("cube",CubeListBuilder.create().texOffs(u,v).mirror(mirror)
                .addBox(-width/2f,head?-height:0,-depth/2f,width,height,depth,new CubeDeformation(inflate)),PartPose.ZERO);
        return LayerDefinition.create(mesh,64,64).bakeRoot().getChild("cube");
    }
}
