package com.tonywww.elder_bosses.client.vfx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortCombatState;
import com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortAnimationTimeline;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.WeakHashMap;
import java.util.function.BiFunction;

public final class ClientConsortBladeTrails {
    private static final double TRAIL_TICKS = 4.0;
    private static final Map<PromisedConsortEntity, Trail> TRAILS = new WeakHashMap<>();

    private ClientConsortBladeTrails() {
    }

    public static void capture(PromisedConsortEntity entity, GeoModel<PromisedConsortEntity> model, float partialTick) {
        boolean intro = entity.combatState() == PromisedConsortCombatState.INTRO;
        if (!ElderBossesCommonConfig.VALUES.skillVfx().enabled() || entity.actionId().isEmpty() && !intro) {
            TRAILS.remove(entity);
            return;
        }
        Trail trail = TRAILS.computeIfAbsent(entity, unused -> new Trail());
        double frameTime = entity.tickCount + partialTick;
        if (frameTime == trail.lastFrame) return;
        if (trail.seed != entity.actionSeed() || frameTime - trail.lastFrame > 3.0) {
            trail.samples.clear();
            trail.latest = null;
        }
        if (trail.seed != entity.actionSeed()) trail.groundContacts.clear();
        trail.seed = entity.actionSeed();
        trail.lastFrame = frameTime;
        trail.samples.removeIf(sample -> frameTime - sample.time() > TRAIL_TICKS);
        var action = entity.actionId().orElse(null);
        int sides = action == null ? 0 : PromisedConsortAnimationTimeline.swordSides(action, entity.animationTime());
        Enchantment enchantment = enchantment(action, entity.miquellaVisible(), intro || entity.isOpeningLion());
        if (sides == 0 && enchantment == Enchantment.NONE) return;
        Vec3 offset = new Vec3(Mth.lerp(partialTick, entity.xo, entity.getX()) - entity.getX(),
                Mth.lerp(partialTick, entity.yo, entity.getY()) - entity.getY(),
                Mth.lerp(partialTick, entity.zo, entity.getZ()) - entity.getZ());
        Vec3 leftRoot = bone(model, "blade_root_l", offset), leftTip = bone(model, "blade_tip_l", offset);
        Vec3 rightRoot = bone(model, "blade_root_r", offset), rightTip = bone(model, "blade_tip_r", offset);
        if (leftRoot == null || leftTip == null || rightRoot == null || rightTip == null) return;
        if (leftTip.distanceToSqr(entity.position()) > 400 || rightTip.distanceToSqr(entity.position()) > 400) return;
        if (sides != 0) groundDebris(entity, trail, action, sides, leftRoot, leftTip, rightRoot, rightTip);
        trail.latest = new Sample(frameTime, leftRoot, leftTip, rightRoot, rightTip, sides, enchantment, action, entity.animationTime());
        if (trail.samples.isEmpty() || frameTime - trail.samples.getLast().time() >= 0.20) trail.samples.addLast(trail.latest);
        while (trail.samples.size() > 24) trail.samples.removeFirst();
    }

    private static void groundDebris(PromisedConsortEntity entity, Trail trail, PromisedConsortActionId action, int sides,
                                     Vec3 leftRoot, Vec3 leftTip, Vec3 rightRoot, Vec3 rightTip) {
        double time = entity.animationTime();
        PromisedConsortAnimationTimeline.SwordWindow window = null;
        for (var candidate : PromisedConsortAnimationTimeline.swordWindows(action)) {
            if (time >= candidate.startTick() && time <= candidate.endTick()) { window = candidate; break; }
        }
        if (window == null) return;
        for (int side : new int[]{1, 2}) {
            String key = action.serializedName() + ":" + window.contactTick() + ":" + side;
            if ((sides & side) == 0 || trail.groundContacts.contains(key)) continue;
            Vec3 root = side == 1 ? leftRoot : rightRoot, tip = side == 1 ? leftTip : rightTip;
            Sample previous = trail.latest;
            double gap = previous == null ? 0 : trail.lastFrame - previous.time();
            boolean continuous = previous != null && previous.action() == action && (previous.sides() & side) != 0
                    && previous.animationTick() >= window.startTick() && previous.animationTick() <= time
                    && gap > 0 && gap <= 1.0;
            Vec3 oldRoot = !continuous ? root : side == 1 ? previous.leftRoot() : previous.rightRoot();
            Vec3 oldTip = !continuous ? tip : side == 1 ? previous.leftTip() : previous.rightTip();
            var contact = groundContact(root, tip, oldRoot, oldTip, gap, continuous,
                    (start, end) -> entity.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)));
            if (contact == null) continue;
            var state = entity.level().getBlockState(contact.getBlockPos());
            if (!claimGroundContact(trail.groundContacts, key, true, state.isAir(), !state.getFluidState().isEmpty())) continue;
            Vec3 point = contact.getLocation().add(0, 0.035, 0);
            Vec3 sweep = oldTip.distanceToSqr(tip) > 16 ? Vec3.ZERO : tip.subtract(oldTip).multiply(1, 0, 1).normalize();
            var random = new java.util.Random(entity.actionSeed() ^ window.contactTick() * 31L ^ side);
            var particle = new BlockParticleOption(ParticleTypes.BLOCK, state);
            int count = Math.min(16, ElderBossesCommonConfig.VALUES.skillVfx().particleBudgetPerBossPerTick() / 2);
            for (int index = 0; index < count; index++) {
                var fragment = Minecraft.getInstance().particleEngine.createParticle(particle,
                    point.x + (random.nextDouble() - 0.5) * 0.55, point.y, point.z + (random.nextDouble() - 0.5) * 0.55,
                    sweep.x * 0.23 + (random.nextDouble() - 0.5) * 0.34, 0.18 + random.nextDouble() * 0.26,
                    sweep.z * 0.23 + (random.nextDouble() - 0.5) * 0.34);
                if (fragment != null) fragment.scale(1.6F);
            }
        }
    }

    public static BlockHitResult groundContact(Vec3 root, Vec3 tip, Vec3 oldRoot, Vec3 oldTip, double sampleGap,
                                                boolean continuousSwing, BiFunction<Vec3, Vec3, BlockHitResult> clip) {
        if (!finitePoint(root) || !finitePoint(tip)) return null;
        BlockHitResult contact = clip.apply(root, tip);
        if (topContact(contact)) return contact;
        if (!continuousSwing || !Double.isFinite(sampleGap) || sampleGap <= 0 || sampleGap > 1.0
                || !finitePoint(oldRoot) || !finitePoint(oldTip)
                || oldRoot.distanceToSqr(root) > 16 || oldTip.distanceToSqr(tip) > 16) return null;
        double length = Math.max(root.distanceTo(tip), oldRoot.distanceTo(oldTip));
        int segments = Math.min(32, Math.max(1, (int) Math.ceil(length / 0.25)));
        for (int index = 0; index <= segments; index++) {
            double fraction = 1.0 - index / (double) segments;
            Vec3 start = oldRoot.lerp(oldTip, fraction), end = root.lerp(tip, fraction);
            if (start.distanceToSqr(end) < 1.0e-8) continue;
            contact = clip.apply(start, end);
            if (topContact(contact)) return contact;
        }
        return null;
    }

    private static boolean finitePoint(Vec3 point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    private static boolean topContact(BlockHitResult contact) {
        return contact != null && contact.getType() == HitResult.Type.BLOCK && contact.getDirection() == Direction.UP && !contact.isInside();
    }

    public static boolean claimGroundContact(Set<String> contacts, String key, boolean topFace, boolean air, boolean fluid) {
        return topFace && !air && !fluid && contacts.add(key);
    }

    public static void render(PoseStack poses, Camera camera, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !ElderBossesCommonConfig.VALUES.skillVfx().enabled()) {
            TRAILS.clear();
            return;
        }
        if (!ConsortEnergyShader.ready()) return;
        Vec3 view = camera.getPosition();
        double distance = ElderBossesCommonConfig.VALUES.skillVfx().renderDistance();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        poses.pushPose();
        poses.translate(-view.x, -view.y, -view.z);
        try {
            ConsortEnergyShader.configure(6, 0, 0, 0);
            VertexConsumer consumer = null;
            for (var entry : TRAILS.entrySet()) {
                PromisedConsortEntity entity = entry.getKey();
                if (entity.isRemoved() || entity.level() != minecraft.level || entity.distanceToSqr(view) > distance * distance) continue;
                double now = entity.tickCount + partialTick;
                Sample previous = null;
                int color = enchantment(entity.actionId().orElse(null), entity.miquellaVisible(), entity.isOpeningLion()) == Enchantment.GRAVITY ? 0xAA66F2
                    : entity.actionId().orElse(null) == PromisedConsortActionId.L_COMBO_BLOODFLAME ? 0xF45A48 : entity.miquellaVisible() ? 0xFFE6A2 : 0xE7DACA;
                for (Sample sample : entry.getValue().samples) {
                    if (now - sample.time() > TRAIL_TICKS) continue;
                    if (previous != null && sample.time() - previous.time() < 1.0) {
                        if (consumer == null) consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
                        float alpha = (float) Math.max(0, Math.pow(1.0 - (now - sample.time()) / TRAIL_TICKS, 1.3) * 0.85);
                        if ((sample.sides() & previous.sides() & 1) != 0) blade(consumer, poses.last(), previous.leftRoot(), previous.leftTip(), sample.leftRoot(), sample.leftTip(), color, alpha);
                        if ((sample.sides() & previous.sides() & 2) != 0) blade(consumer, poses.last(), previous.rightRoot(), previous.rightTip(), sample.rightRoot(), sample.rightTip(), color, alpha);
                    }
                    previous = sample;
                }
                Sample latest = entry.getValue().latest;
                if (previous != null && latest != null && latest.time() > previous.time() && now - latest.time() <= 0.5) {
                    if (consumer == null) consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
                    if ((latest.sides() & previous.sides() & 1) != 0) blade(consumer, poses.last(), previous.leftRoot(), previous.leftTip(), latest.leftRoot(), latest.leftTip(), color, 0.85F);
                    if ((latest.sides() & previous.sides() & 2) != 0) blade(consumer, poses.last(), previous.rightRoot(), previous.rightTip(), latest.rightRoot(), latest.rightTip(), color, 0.85F);
                }
            }
            if (consumer != null) buffers.endBatch(ConsortEnergyShader.ENERGY);
            ConsortEnergyShader.configure(0, 0, 0, 0);
            consumer = null;
            for (var entry : TRAILS.entrySet()) {
                var entity = entry.getKey();
                if (entity.isRemoved() || entity.level() != minecraft.level || entry.getValue().samples.isEmpty()
                        || entity.distanceToSqr(view) > distance * distance) continue;
                var sample = entry.getValue().latest;
                if (sample == null) continue;
                if (entity.tickCount + partialTick - sample.time() > 0.5) continue;
                if (consumer == null) consumer = buffers.getBuffer(ConsortEnergyShader.ENERGY);
                boolean blood = entity.actionId().orElse(null) == PromisedConsortActionId.L_COMBO_BLOODFLAME;
                if ((sample.sides() & 1) != 0) edge(consumer, poses.last(), sample.leftRoot(), sample.leftTip(), view, entity.miquellaVisible(), blood);
                if ((sample.sides() & 2) != 0) edge(consumer, poses.last(), sample.rightRoot(), sample.rightTip(), view, entity.miquellaVisible(), blood);
                if (sample.enchantment() != Enchantment.NONE) {
                    enchantedBlade(consumer, poses.last(), sample.leftRoot(), sample.leftTip(), view, sample.enchantment(), entity.tickCount + partialTick, -1);
                    enchantedBlade(consumer, poses.last(), sample.rightRoot(), sample.rightTip(), view, sample.enchantment(), entity.tickCount + partialTick, 1);
                }
            }
            if (consumer != null) buffers.endBatch(ConsortEnergyShader.ENERGY);
        } finally {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            poses.popPose();
        }
    }

    public enum Enchantment { NONE, GRAVITY, HOLY }

    public static Enchantment enchantment(PromisedConsortActionId action, boolean phaseTwo, boolean intro) {
        if (intro) return Enchantment.GRAVITY;
        if (action == null) return Enchantment.NONE;
        if (switch (action) { case GRAVITY_DIVE, GRAVITY_METEOR, STARCALLER_CRY, SPIRAL_ASSAULT -> true; default -> false; }) {
            return Enchantment.GRAVITY;
        }
        if (phaseTwo && !action.rangedDefense() && action != PromisedConsortActionId.CONSORT_METEOR
                && action != PromisedConsortActionId.STOMP && action != PromisedConsortActionId.L_COMBO_BLOODFLAME) return Enchantment.HOLY;
        return Enchantment.NONE;
    }

    public static List<Vec3> lightningPoints(Vec3 root, Vec3 tip, double time, int strand) {
        Vec3 direction = tip.subtract(root).normalize();
        Vec3 across = direction.cross(Math.abs(direction.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 normal = direction.cross(across).normalize();
        double phase = Math.floor(time * 0.75) + strand * 13;
        List<Vec3> points = new ArrayList<>();
        for (int index = 0; index <= 12; index++) {
            double fraction = index / 12.0;
            double envelope = Math.sin(fraction * Math.PI) * 0.14;
            points.add(root.lerp(tip, fraction).add(across.scale(Math.sin(index * 9.13 + phase * 4.1) * envelope))
                    .add(normal.scale(Math.cos(index * 5.71 - phase * 3.7) * envelope)));
        }
        return List.copyOf(points);
    }

    public static void enchantedBlade(VertexConsumer consumer, PoseStack.Pose pose, Vec3 root, Vec3 tip, Vec3 view,
                                      Enchantment enchantment, double time, int side) {
        if (enchantment == Enchantment.NONE || root.distanceToSqr(tip) < 0.0001) return;
        int tint = enchantment == Enchantment.GRAVITY ? 0x954CEA : 0xFFD36B;
        bladeBand(consumer, pose, root, tip, view, enchantment == Enchantment.GRAVITY ? 0.16 : 0.25, tint, 0.42F);
        bladeBand(consumer, pose, root, tip, view, 0.065, enchantment == Enchantment.GRAVITY ? 0xD9AAFF : 0xFFF5C0, 0.9F);
        if (enchantment == Enchantment.GRAVITY) {
            for (int strand = 0; strand < 2; strand++) {
                var points = lightningPoints(root, tip, time, side * (strand + 1));
                for (int index = 1; index < points.size(); index++) {
                    bladeBand(consumer, pose, points.get(index - 1), points.get(index), view, 0.027, 0xDDACFF, 0.95F);
                }
            }
        } else {
            for (int index = 0; index < 4; index++) {
                double fraction = (time * 0.045 + index * 0.25) % 1;
                ClientConsortEnergyRenderer.sparkle(consumer, pose, root.lerp(tip, fraction), view, 0.11, 0xFFF1A0, 0.55F);
            }
        }
    }

    private static void bladeBand(VertexConsumer consumer, PoseStack.Pose pose, Vec3 start, Vec3 end, Vec3 view,
                                  double width, int color, float alpha) {
        Vec3 direction = end.subtract(start).normalize();
        Vec3 across = direction.cross(view.subtract(start).normalize());
        if (across.lengthSqr() < 0.0001) across = direction.cross(Math.abs(direction.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0));
        across = across.normalize().scale(width);
        ClientConsortEnergyRenderer.quad(consumer, pose, start.subtract(across), start.add(across), end.add(across), end.subtract(across), color, alpha);
    }

    private static void blade(VertexConsumer consumer, PoseStack.Pose pose, Vec3 oldRoot, Vec3 oldTip,
                              Vec3 root, Vec3 tip, int color, float alpha) {
        if (tip.distanceToSqr(oldTip) < 0.0001 || tip.distanceToSqr(oldTip) > 64) return;
        Vec3 extendedOld = trailTip(oldRoot, oldTip), extended = trailTip(root, tip);
        ClientConsortEnergyRenderer.quad(consumer, pose, oldRoot.lerp(oldTip, 0.12), extendedOld, extended,
            root.lerp(tip, 0.12), color, alpha * 0.48F);
        ClientConsortEnergyRenderer.quad(consumer, pose, oldRoot, oldTip, tip, root, color, alpha);
        ClientConsortEnergyRenderer.quad(consumer, pose, oldRoot.lerp(extendedOld, 0.88), extendedOld,
            extended, root.lerp(extended, 0.88), 0xFFF1C7, alpha * 0.82F);
    }

        public static Vec3 trailTip(Vec3 root, Vec3 tip) {
        return root.add(tip.subtract(root).scale(1.38));
        }

        private static void edge(VertexConsumer consumer, PoseStack.Pose pose, Vec3 root, Vec3 tip, Vec3 view, boolean holy, boolean blood) {
        Vec3 across = tip.subtract(root).normalize().cross(view.subtract(root).normalize()).normalize();
        Vec3 aura = across.scale(holy ? 0.55 : 0.42), extended = trailTip(root, tip);
        ClientConsortEnergyRenderer.quad(consumer, pose, root.subtract(aura), root.add(aura), extended.add(aura), extended.subtract(aura),
            blood ? 0xC52232 : holy ? 0xECAF43 : 0xB8C8DA, 0.38F);
        Vec3 wide = across.scale(holy ? 0.27 : 0.22);
        ClientConsortEnergyRenderer.quad(consumer, pose, root.subtract(wide), root.add(wide), tip.add(wide), tip.subtract(wide),
            blood ? 0xFF6D40 : holy ? 0xFFD57A : 0xDFD4B7, 0.78F);
        Vec3 core = across.scale(0.065);
        ClientConsortEnergyRenderer.quad(consumer, pose, root.subtract(core), root.add(core), tip.add(core), tip.subtract(core), 0xFFF5DF, 0.95F);
    }

    private static Vec3 bone(GeoModel<PromisedConsortEntity> model, String name, Vec3 offset) {
        GeoBone bone = model.getBone(name).orElse(null);
        if (bone == null) return null;
        var position = bone.getWorldPosition();
        return new Vec3(position.x, position.y, position.z).add(offset);
    }

    private static final class Trail {
        private long seed;
        private double lastFrame = -1;
        private Sample latest;
        private final Deque<Sample> samples = new ArrayDeque<>();
        private final Set<String> groundContacts = new HashSet<>();
    }

    private record Sample(double time, Vec3 leftRoot, Vec3 leftTip, Vec3 rightRoot, Vec3 rightTip, int sides, Enchantment enchantment,
                          PromisedConsortActionId action, double animationTick) {
    }
}