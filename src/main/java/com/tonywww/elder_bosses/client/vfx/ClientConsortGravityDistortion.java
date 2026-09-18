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
        for (var entity : minecraft.level.entitiesForRendering()) {
            if (entity.isRemoved() || entity.distanceToSqr(view) > config.renderDistance() * config.renderDistance()) continue;
            double radius;
            double height;
            if (entity instanceof PromisedConsortEntity boss) {
                if (!boss.isAlive() || ClientConsortBladeTrails.enchantment(boss.actionId().orElse(null), boss.miquellaVisible(),
                        boss.combatState() == PromisedConsortCombatState.INTRO || boss.isOpeningLion())
                        != ClientConsortBladeTrails.Enchantment.GRAVITY) continue;
                radius = 2.6;
                height = boss.getBbHeight() * 0.65;
            } else if (entity instanceof PromisedConsortGravityRockEntity) {
                radius = 0.85 * PromisedConsortGravityRockEntity.SIZE_SCALE;
                height = 0.2 * PromisedConsortGravityRockEntity.SIZE_SCALE;
            } else continue;
            double phase = (gameTick + partialTick) * 0.05 + entity.getId() * 0.618;
            radius *= 1 + 0.08 * Math.sin(phase * 5.7) + 0.04 * Math.sin(phase * 11.3);
            Vec3 center = new Vec3(Mth.lerp(partialTick, entity.xo, entity.getX()),
                    Mth.lerp(partialTick, entity.yo, entity.getY()) + height, Mth.lerp(partialTick, entity.zo, entity.getZ()));
            if (center.distanceToSqr(view) < radius * radius) continue;
            lenses.add(new Lens(center, radius));
            if (lenses.size() >= 24) break;
        }
        if (lenses.isEmpty() || !captureScene(minecraft)) return;
        lenses.sort(Comparator.comparingDouble((Lens lens) -> lens.center().distanceToSqr(view)).reversed());
        shader.setSampler("SceneColor", scene.getColorTextureId());
        shader.safeGetUniform("ScreenSize").set((float) scene.width, (float) scene.height);
        shader.safeGetUniform("EffectTime").set((float) (((gameTick + partialTick) % 24000) / 20));
        shader.safeGetUniform("Intensity").set(1.0F);
        var buffers = minecraft.renderBuffers().bufferSource();
        poses.pushPose();
        poses.translate(-view.x, -view.y, -view.z);
        try {
            for (var lens : lenses) {
                poses.pushPose();
                poses.translate(lens.center().x, lens.center().y, lens.center().z);
                poses.mulPose(camera.rotation());
                billboard(buffers.getBuffer(DISTORTION), poses.last(), (float) lens.radius());
                poses.popPose();
            }
            buffers.endBatch(DISTORTION);
        } finally {
            poses.popPose();
        }
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

    private record Lens(Vec3 center, double radius) {
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