package com.tonywww.elder_bosses.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tonywww.elder_bosses.boss.malenia.MaleniaEntity;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaCombatState;
import com.tonywww.elder_bosses.network.IndicatorSnapshotPacket;
import com.tonywww.elder_bosses.network.MaleniaCombatSnapshotPacket;
import com.tonywww.elder_bosses.network.MaleniaHitFeedbackPacket;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public final class ClientMaleniaSkillEffects {
    private static final MaleniaEffectState STATE = new MaleniaEffectState();
    private static final Map<String, Long> PLAYED = new LinkedHashMap<>();
    private static final Map<Integer, Integer> PARTICLES = new HashMap<>();
    private static final Map<String, MaleniaHitFeedbackPacket> CONTACTS = new LinkedHashMap<>();
    private static final Map<Integer, Integer> SOUNDS = new HashMap<>();
    private static int totalSounds;
    private static long lastTick = Long.MIN_VALUE;
    private ClientMaleniaSkillEffects() {}

    public static void observe(IndicatorSnapshotPacket packet, long now) {
        STATE.observe(packet, now); ClientMaleniaParryCue.observe(packet);
    }
    public static void observe(MaleniaCombatSnapshotPacket packet) {
        if (packet.combatState() == MaleniaCombatState.DORMANT || packet.combatState() == MaleniaCombatState.DEFEATED
                || packet.combatState() == MaleniaCombatState.TRANSITION) remove(packet.entityId());
        else STATE.action(packet.entityId(), packet.actionSequence());
    }

    public static void observe(MaleniaHitFeedbackPacket packet) {
        var level = Minecraft.getInstance().level;
        if (level == null || level.getGameTime() - packet.gameTick() > 6 || packet.gameTick() - level.getGameTime() > 2) return;
        String key = packet.bossId() + "/contact/" + packet.sequence() + "/" + packet.actionTick() + "/" + packet.segment() + "/" + packet.targetId();
        if (PLAYED.putIfAbsent(key, packet.gameTick() + 12) != null) return;
        if (packet.result() == MaleniaHitFeedbackPacket.Result.INSTANT_GUARD) {
            ClientMaleniaParryFeedback.observe(packet);
            return;
        }
        CONTACTS.put(key, packet);
        while (CONTACTS.size() > 64) CONTACTS.remove(CONTACTS.keySet().iterator().next());
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { clear(); return; }
        long now = mc.level.getGameTime();
        if (now == lastTick) return;
        lastTick = now; PARTICLES.clear(); SOUNDS.clear(); totalSounds = 0; STATE.prune(now);
        PLAYED.values().removeIf(expiry -> expiry <= now);
        CONTACTS.values().removeIf(contact -> now >= contact.gameTick() + 7);
        if (!ElderBossesCommonConfig.VALUES.skillVfx().enabled()) return;
        double distance = ElderBossesCommonConfig.VALUES.skillVfx().renderDistance();
        for (var entry : CONTACTS.entrySet()) {
            var contact = entry.getValue();
            Vec3 origin = point(contact.point());
            if (origin.distanceToSqr(mc.player.position()) > distance * distance || now - contact.gameTick() > 6) continue;
            if (PLAYED.putIfAbsent(entry.getKey() + "/sound", now + 12) == null) {
                playSound(contact.bossId(), origin, contact.result() == MaleniaHitFeedbackPacket.Result.DAMAGED
                        ? "entity.player.attack.crit" : "item.shield.block", 0.55F, 1.1F);
                Random random = new Random(entry.getKey().hashCode());
                for (int i = 0; i < 6; i++) particle(contact.bossId(),
                        contact.result() == MaleniaHitFeedbackPacket.Result.DAMAGED ? ParticleTypes.CRIT : ParticleTypes.ELECTRIC_SPARK,
                        origin, new Vec3((random.nextDouble() - 0.5) * 0.25, random.nextDouble() * 0.18, (random.nextDouble() - 0.5) * 0.25));
            }
        }
        for (var segment : STATE.segments()) {
            var packet = segment.packet();
            if (!(mc.level.getEntity(packet.bossEntityId()) instanceof MaleniaEntity boss)) continue;
            Vec3 origin = point(packet.anchor());
            if (origin.distanceToSqr(mc.player.position()) > distance * distance) continue;
            double age = now - packet.activeTick();
            Random random = new Random(packet.indicatorId().hashCode() * 31L + now);
            if (age >= 0 && age <= 2 && !segment.persistent()
                    && packet.semantic() != IndicatorSnapshotPacket.Semantic.MOVEMENT
                    && !segment.identity().segment().equals("phantom_spawn_pool")
                    && !segment.identity().segment().equals("zone")) {
                String key = packet.bossEntityId() + "/" + packet.indicatorId();
                if (PLAYED.putIfAbsent(key, segment.expires()) == null) {
                    boolean flower = segment.identity().action() == MaleniaActionId.SCARLET_AEONIA;
                    boolean ground = flower || segment.identity().action() == MaleniaActionId.KICK;
                    playSound(packet.bossEntityId(), origin, flower ? "entity.generic.explode" : "entity.player.attack.sweep",
                            flower ? 0.75F : 0.45F, flower ? 0.75F : segment.identity().action() == MaleniaActionId.WATERFOWL_DANCE ? 1.35F : 1.0F);
                    if (ground) for (int i = 0; i < 10; i++) {
                        double angle = random.nextDouble() * Math.PI * 2;
                        particle(packet.bossEntityId(), ParticleTypes.POOF, origin.add(Math.cos(angle) * 0.5, 0.12, Math.sin(angle) * 0.5),
                                new Vec3(Math.cos(angle) * 0.13, 0.05, Math.sin(angle) * 0.13));
                    }
                }
            }
            if (ClientMaleniaAeonia.isFlower(segment) && age >= 0 && now < packet.endTick()) {
                int count = age < 10 ? 8 : 2;
                double radius = packet.ranges().get(0);
                for (int i = 0; i < count; i++) {
                    double angle = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * radius;
                    particle(packet.bossEntityId(), ParticleTypes.CRIMSON_SPORE, origin.add(Math.cos(angle) * r, 0.2 + random.nextDouble(), Math.sin(angle) * r),
                            new Vec3(0, 0.035, 0));
                }
            } else if (ClientMaleniaPhantoms.isPhantom(segment) && now >= packet.lockTick() && age < 1 && packet.pathPoints().size() == 2) {
                double progress = MaleniaEffectTimeline.smooth((now - packet.lockTick()) / (double) Math.max(1, packet.activeTick() - packet.lockTick()));
                Vec3 position = point(packet.pathPoints().get(0)).lerp(point(packet.pathPoints().get(1)), progress);
                for (int i = 0; i < 3; i++) particle(packet.bossEntityId(), ParticleTypes.CRIMSON_SPORE,
                        position.add(random.nextDouble() - 0.5, 1 + random.nextDouble(), random.nextDouble() - 0.5), Vec3.ZERO);
            }
        }
        while (PLAYED.size() > 512) PLAYED.remove(PLAYED.keySet().iterator().next());
    }

    private static void particle(int bossId, ParticleOptions type, Vec3 position, Vec3 velocity) {
        int used = PARTICLES.getOrDefault(bossId, 0);
        if (used >= ElderBossesCommonConfig.VALUES.skillVfx().particleBudgetPerBossPerTick()) return;
        PARTICLES.put(bossId, used + 1);
        Minecraft.getInstance().level.addParticle(type, position.x, position.y, position.z, velocity.x, velocity.y, velocity.z);
    }

    private static void playSound(int bossId, Vec3 origin, String name, float volume, float pitch) {
        int count = SOUNDS.getOrDefault(bossId, 0);
        if (count >= 2 || totalSounds >= 8) return;
        SOUNDS.put(bossId, count + 1); totalSounds++;
        var sound = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(
                com.tonywww.elder_bosses.platforms.PlatformResourceLocation.parse("minecraft:" + name));
        if (sound != null) Minecraft.getInstance().level.playLocalSound(origin.x, origin.y, origin.z, sound,
                SoundSource.HOSTILE, volume, pitch, false);
    }

    public static void render(PoseStack poses, Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !ElderBossesCommonConfig.VALUES.skillVfx().enabled()) return;
        Vec3 view = camera.getPosition();
        double now = mc.level.getGameTime() + partialTick, distance = ElderBossesCommonConfig.VALUES.skillVfx().renderDistance();
        var segments = STATE.segments();
        if (segments.isEmpty() && CONTACTS.isEmpty()) {
            ClientMaleniaBladeTrails.render(poses, camera, partialTick);
            return;
        }
        var buffers = mc.renderBuffers().bufferSource();
        poses.pushPose(); poses.translate(-view.x, -view.y, -view.z);
        try {
            ClientMaleniaPhantoms.render(poses, buffers, segments, view, now, distance);
            // Flush the translucent model pass before changing the effect shader's mode.
            buffers.endBatch();
            if (MaleniaEffectShader.ready()) {
                MaleniaEffectShader.mode(1);
                var out = buffers.getBuffer(MaleniaEffectShader.EFFECT);
                for (var segment : segments) {
                    if (point(segment.packet().anchor()).distanceToSqr(view) <= distance * distance)
                        ClientMaleniaAeonia.render(out, poses.last(), segment, now);
                }
                buffers.endBatch(MaleniaEffectShader.EFFECT);
                MaleniaEffectShader.mode(0);
                out = buffers.getBuffer(MaleniaEffectShader.EFFECT);
                for (var segment : segments) {
                    if (point(segment.packet().anchor()).distanceToSqr(view) <= distance * distance)
                        ClientMaleniaAeonia.ground(out, poses.last(), segment, now);
                }
                for (var contact : CONTACTS.values()) {
                    Vec3 center = point(contact.point());
                    double age = now - contact.gameTick();
                    if (age < 0 || age > 4 || center.distanceToSqr(view) > distance * distance) continue;
                    int rgb = contact.result() == MaleniaHitFeedbackPacket.Result.BLOCKED ? 0xFFE3A6 : 0xFFF3DD;
                    float alpha = (float) (1 - age / 4);
                    Vec3 side = view.subtract(center).cross(new Vec3(0, 1, 0)).normalize();
                    for (int i = 0; i < (contact.result() == MaleniaHitFeedbackPacket.Result.DAMAGED ? 2 : 4); i++) {
                        double angle = i * Math.PI / 4 + 0.4;
                        Vec3 ray = side.scale(Math.cos(angle)).add(0, Math.sin(angle), 0).scale(0.25 + age * 0.10);
                        MaleniaEffectGeometry.band(out, poses.last(), center.subtract(ray), center.add(ray), view, 0.025 * alpha, rgb, alpha);
                    }
                }
                buffers.endBatch(MaleniaEffectShader.EFFECT);
            }
        } finally { poses.popPose(); }
        ClientMaleniaBladeTrails.render(poses, camera, partialTick);
    }

    private static Vec3 point(IndicatorSnapshotPacket.Point p) { return new Vec3(p.x(), p.y(), p.z()); }
    public static boolean hasWorldBloom(int id) {
        var level = Minecraft.getInstance().level;
        if (level == null || !ElderBossesCommonConfig.VALUES.skillVfx().enabled() || !MaleniaEffectShader.ready()) return false;
        return STATE.segments().stream().anyMatch(segment -> segment.packet().bossEntityId() == id
                && ClientMaleniaAeonia.isFlower(segment) && level.getGameTime() >= segment.packet().activeTick()
                && level.getGameTime() < segment.packet().endTick());
    }

    public static void remove(int id) {
        ClientMaleniaParryFeedback.remove(id);
        ClientMaleniaParryCue.remove(id);
        ClientMaleniaWings.remove(id);
        STATE.remove(id); ClientMaleniaBladeTrails.remove(id);
        PLAYED.keySet().removeIf(key -> key.startsWith(id + "/")); PARTICLES.remove(id);
        CONTACTS.values().removeIf(contact -> contact.bossId() == id);
    }
    public static void clear() {
        ClientMaleniaParryFeedback.clear();
        ClientMaleniaParryCue.clear();
        ClientMaleniaWings.clear();
        STATE.clear(); PLAYED.clear(); PARTICLES.clear(); CONTACTS.clear(); SOUNDS.clear(); totalSounds = 0; lastTick = Long.MIN_VALUE;
        ClientMaleniaBladeTrails.clear(); ClientMaleniaPhantoms.clear();
    }
}
