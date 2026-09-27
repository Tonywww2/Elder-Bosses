package com.tonywww.elder_bosses.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tonywww.elder_bosses.boss.malenia.MaleniaEntity;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaCombatState;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaPhase;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.model.GeoModel;
import java.util.Map;
import java.util.WeakHashMap;

public final class ClientMaleniaWings {
    private static final Map<MaleniaEntity, Frame> FRAMES = new WeakHashMap<>();
    private ClientMaleniaWings() {}

    public static boolean visible(MaleniaEntity boss) {
        return boss.activePhase() == MaleniaPhase.PHASE_TWO || boss.combatState() == MaleniaCombatState.TRANSITION;
    }

    /** Capture after bones have rendered, so the wings follow torso lean, twist and translation exactly. */
    public static void capture(MaleniaEntity boss, GeoModel<MaleniaEntity> model, float partialTick) {
        if (!visible(boss) || boss.isInvisible()) { FRAMES.remove(boss); return; }
        model.getBone("chest").ifPresent(chest -> {
            Matrix4f anchor = new Matrix4f().translation(
                    (float) Mth.lerp(partialTick, boss.xo, boss.getX()),
                    (float) Mth.lerp(partialTick, boss.yo, boss.getY()) + 0.01F,
                    (float) Mth.lerp(partialTick, boss.zo, boss.getZ()))
                    .rotateY((180 - Mth.rotLerp(partialTick, boss.yBodyRotO, boss.yBodyRot)) * Mth.DEG_TO_RAD)
                    .mul(MaleniaWingGeometry.anchor(chest));
            FRAMES.put(boss, new Frame(anchor));
        });
    }

    public static void render(PoseStack poses, Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { clear(); return; }
        FRAMES.keySet().removeIf(boss -> boss.isRemoved() || boss.level() != mc.level || !visible(boss));
        if (!MaleniaWingShader.ready()) return;
        var view = camera.getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        for (var entry : FRAMES.entrySet()) {
            var boss = entry.getKey(); var frame = entry.getValue();
            // Never draw last frame's detached wings when the owner wasn't rendered this frame.
            if (boss.isInvisible()
                    || boss.distanceToSqr(view) > 128 * 128) continue;
            double tick = boss.animationTime(partialTick);
            float opacity = 1, spread = 1;
            if (boss.combatState() == MaleniaCombatState.TRANSITION) {
                opacity = (float) MaleniaEffectTimeline.smooth((tick - 38) / 32);
                spread = opacity;
            } else if (boss.combatState() == MaleniaCombatState.DEFEATED) {
                opacity = 1 - (float) MaleniaEffectTimeline.smooth(tick / 36);
            }
            if (boss.animationClip().equals("scarlet_aeonia")) {
                // Fold during the dive and flower, then reopen continuously during recovery.
                spread = 1 - 0.78F * (float) (MaleniaEffectTimeline.smooth((tick - 26) / 17)
                        * (1 - MaleniaEffectTimeline.smooth((tick - 106) / 28)));
                opacity *= 1 - 0.8F * (float) (MaleniaEffectTimeline.smooth((tick - 55) / 15)
                        * (1 - MaleniaEffectTimeline.smooth((tick - 106) / 28)));
            }
            if (opacity <= 0.001F) continue;
            float activity = boss.hasSynchronizedAnimation() ? 1 : 0.25F;
            Matrix4f transform = new Matrix4f(poses.last().pose())
                    .translate((float) -view.x, (float) -view.y, (float) -view.z).mul(frame.anchor());
            float seconds = (boss.tickCount + partialTick) / 20F;
            MaleniaWingShader.configure(transform, seconds, spread, activity);
            Vector3f localView = new Matrix4f(frame.anchor()).invert().transformPosition(view.toVector3f());
            MaleniaWingGeometry.emit(buffers.getBuffer(MaleniaWingShader.WINGS), opacity,
                    (boss.getId() * 73 & 255) / 255F, localView.z > 0);
            // Uniforms are per owner; flush before configuring the next Malenia.
            buffers.endBatch(MaleniaWingShader.WINGS);
        }
        // Each world pass consumes captures. Entity and world-stage partial ticks need not match.
        FRAMES.clear();
    }

    public static void remove(int id) { FRAMES.keySet().removeIf(boss -> boss.getId() == id); }
    public static void clear() { FRAMES.clear(); }
    private record Frame(Matrix4f anchor) {}
}
