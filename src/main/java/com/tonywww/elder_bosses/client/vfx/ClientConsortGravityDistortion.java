package com.tonywww.elder_bosses.client.vfx;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortGravityRockEntity;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortCombatState;
import com.tonywww.elder_bosses.platforms.client.PlatformVertexConsumer;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.Comparator;

public final class ClientConsortGravityDistortion {
    private static final RenderType DISTORTION = DistortionRenderType.createDistortion();
    private static ShaderInstance shader;
    private static TextureTarget scene;
    private static boolean failed;

    private ClientConsortGravityDistortion() {
    }

    public static void install(ShaderInstance instance) {
        clear();
        shader = instance;
    }

    public static void clear() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(ClientConsortGravityDistortion::clear);
            return;
        }
        if (scene != null) scene.destroyBuffers();
        scene = null;
        failed = false;
    }

    public static void render(PoseStack poses, Camera camera, long gameTick, float partialTick) {
        var minecraft = Minecraft.getInstance();
        var config = ElderBossesCommonConfig.VALUES.skillVfx();
        if (minecraft.level == null || !config.enabled()) {
            if (scene != null) clear();
            return;
        }
        if (shader == null || failed) return;
        Vec3 view = camera.getPosition();
        var lenses = new ArrayList<Lens>();
        double time=gameTick+partialTick;
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (entity.isRemoved() || entity.distanceToSqr(view) > config.renderDistance() * config.renderDistance()) continue;
            double radius;
            double height;
            float intensity=1;
            if (entity instanceof PromisedConsortEntity boss) {
                if(!boss.isAlive()) continue;
                if(boss.usesSourceRig()) {
                    var instances=boss.sourceVisuals().getList("Instances",Tag.TAG_COMPOUND);int count=0;
                    double configuredRadius=boss.sourceConfig().number("visuals.gravity_distortion_radius");
                    float configuredStrength=(float)boss.sourceConfig().number("visuals.gravity_distortion_strength")*ClientConsortSourceEffects.GRAVITY_INTENSITY;
                    float bodyFade=0;
                    for(int i=0;i<instances.size() && count<12 && lenses.size()<24;i++) {
                        var tag=instances.getCompound(i);int id=tag.getInt("FXR");
                        if(!ClientConsortSourceEffects.gravityVisual(id)) continue;
                        float fade=ClientConsortSourceEffects.gravityFade(tag,time*50_000)*ClientConsortSourceEffects.gravitySkillIntensity(boss,tag);if(fade<=0) continue;
                        if(id!=652285) bodyFade=Math.max(bodyFade,fade);
                        Vec3 center=ClientConsortSourceEffects.gravityPoint(tag,time*50_000);
                        if(center.distanceToSqr(view)>config.renderDistance()*config.renderDistance()) continue;
                        radius=id==652285?.85*boss.sourceConfig().number("visuals.meteor_size")*configuredRadius/4.7:
                                ClientConsortSourceEffects.gravityBlade(id)?1.15*boss.sourceConfig().number("visuals.gravity_size_multiplier")*configuredRadius/4.7:configuredRadius;
                        addLens(lenses,view,center,radius,configuredStrength*fade,time*.05+boss.getId()*.618+i*.713);
                        count++;
                    }
                    if(bodyFade>0 && lenses.size()<24) {
                        Vec3 center=new Vec3(Mth.lerp(partialTick,boss.xo,boss.getX()),
                                Mth.lerp(partialTick,boss.yo,boss.getY())+boss.getBbHeight()*.65,Mth.lerp(partialTick,boss.zo,boss.getZ()));
                        addLens(lenses,view,center,configuredRadius,configuredStrength*bodyFade,time*.05+boss.getId()*.618);
                    }
                    if(lenses.size()>=24) break;
                    continue;
                }
                if (ClientConsortBladeTrails.enchantment(boss.actionId().orElse(null), boss.miquellaVisible(),
                        boss.combatState() == PromisedConsortCombatState.INTRO || boss.isOpeningLion())
                        != ClientConsortBladeTrails.Enchantment.GRAVITY) continue;
                radius = 2.6;
                height = boss.getBbHeight() * 0.65;
            } else if (entity instanceof PromisedConsortGravityRockEntity) {
                radius = 0.85 * PromisedConsortGravityRockEntity.SIZE_SCALE;
                height = 0.2 * PromisedConsortGravityRockEntity.SIZE_SCALE;
            } else continue;
            double phase = time * 0.05 + entity.getId() * 0.618;
            Vec3 center = new Vec3(Mth.lerp(partialTick, entity.xo, entity.getX()),
                    Mth.lerp(partialTick, entity.yo, entity.getY()) + height, Mth.lerp(partialTick, entity.zo, entity.getZ()));
            addLens(lenses,view,center,radius,intensity,phase);
            if (lenses.size() >= 24) break;
        }
        if (lenses.isEmpty() || !captureScene(minecraft)) return;
        lenses.sort(Comparator.comparingDouble((Lens lens) -> lens.center().distanceToSqr(view)).reversed());
        shader.setSampler("SceneColor", scene.getColorTextureId());
        shader.safeGetUniform("ScreenSize").set((float) scene.width, (float) scene.height);
        shader.safeGetUniform("EffectTime").set((float) (((gameTick + partialTick) % 24000) / 20));
        var buffers = minecraft.renderBuffers().bufferSource();
        poses.pushPose();
        poses.translate(-view.x, -view.y, -view.z);
        try {
            for (var lens : lenses) {
                shader.safeGetUniform("Intensity").set(lens.intensity());
                shader.safeGetUniform("EffectTime").set((float)(time/20%1200+lens.phase()));
                poses.pushPose();
                poses.translate(lens.center().x, lens.center().y, lens.center().z);
                poses.mulPose(camera.rotation());
                billboard(buffers.getBuffer(DISTORTION), poses.last(), (float) lens.radius());
                poses.popPose();
                buffers.endBatch(DISTORTION);
            }
            buffers.endBatch(DISTORTION);
        } finally {
            poses.popPose();
        }
    }

    private static void addLens(ArrayList<Lens> lenses,Vec3 view,Vec3 center,double radius,float intensity,double phase) {
        double distance=center.distanceTo(view);
        if(intensity<=0 || distance<.2) return;
        radius*=1+.08*Math.sin(phase*5.7)+.04*Math.sin(phase*11.3);
        // Near the camera, keep a local lens rather than dropping distortion entirely.
        radius=Math.min(radius,distance*.9);
        for(var lens:lenses) if(center.distanceToSqr(lens.center())<Math.pow(Math.min(radius,lens.radius())*.3,2)
                && Math.min(radius,lens.radius())/Math.max(radius,lens.radius())>.75) return;
        lenses.add(new Lens(center,radius,intensity,phase));
    }

    private static boolean captureScene(Minecraft minecraft) {
        var target = minecraft.getMainRenderTarget();
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        if (drawFramebuffer != target.frameBufferId || viewport[0] != 0 || viewport[1] != 0
                || viewport[2] != target.width || viewport[3] != target.height || target.width < 1 || target.height < 1
                || (long) target.width * target.height > 16777216 || GL11.glGetInteger(GL30.GL_SAMPLES) > 0) return false;
        try {
            if (scene == null) scene = new TextureTarget(target.width, target.height, false, Minecraft.ON_OSX);
            else if (scene.width != target.width || scene.height != target.height) scene.resize(target.width, target.height, Minecraft.ON_OSX);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, drawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.frameBufferId);
            if (GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) return false;
            GL30.glBlitFramebuffer(0, 0, target.width, target.height, 0, 0, scene.width, scene.height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            return true;
        } catch (RuntimeException exception) {
            failed = true;
            com.mojang.logging.LogUtils.getLogger().warn("Consort gravity scene capture disabled until reload", exception);
            return false;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    public static void billboard(VertexConsumer consumer, PoseStack.Pose pose, float radius) {
        PlatformVertexConsumer.addPositionColorUv(consumer, pose, -radius, -radius, 0, 255, 255, 255, 255, 0, 0);
        PlatformVertexConsumer.addPositionColorUv(consumer, pose, radius, -radius, 0, 255, 255, 255, 255, 1, 0);
        PlatformVertexConsumer.addPositionColorUv(consumer, pose, radius, radius, 0, 255, 255, 255, 255, 1, 1);
        PlatformVertexConsumer.addPositionColorUv(consumer, pose, -radius, radius, 0, 255, 255, 255, 255, 0, 1);
    }

    private record Lens(Vec3 center, double radius,float intensity,double phase) {
    }

    private static final class DistortionRenderType extends RenderType {
        private DistortionRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                                     boolean crumbling, boolean sorted, Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sorted, setup, clear);
        }

        private static RenderType createDistortion() {
            return create("elder_bosses_consort_gravity", DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS, 1536, false, true,
                    CompositeState.builder().setShaderState(new ShaderStateShard(() -> shader))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
        }
    }
}
