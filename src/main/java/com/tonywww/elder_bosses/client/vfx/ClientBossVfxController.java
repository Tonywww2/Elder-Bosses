package com.tonywww.elder_bosses.client.vfx;

import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaCombatState;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.client.state.ClientIndicatorStateStore;
import com.tonywww.elder_bosses.network.BossCombatSnapshotPacket;
import com.tonywww.elder_bosses.network.MaleniaCombatSnapshotPacket;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

public final class ClientBossVfxController {
    private static final Map<MaleniaActionId, Profile> MALENIA_PROFILES = maleniaProfiles();
    private static final Map<PromisedConsortActionId, Profile> CONSORT_PROFILES = consortProfiles();
    private static final Map<Integer, ActiveVfx> ACTIVE = new HashMap<>();
    private static long lastEmissionTick = Long.MIN_VALUE;

    private ClientBossVfxController() {
    }

    public static void observe(MaleniaCombatSnapshotPacket packet) {
        if (packet.actionId() == null
                || packet.combatState() == MaleniaCombatState.DORMANT
                || packet.combatState() == MaleniaCombatState.DEFEATED) {
            onTrackingEnd(packet.entityId());
            return;
        }
        observe(
                packet.entityId(),
                packet.actionId().serializedName(),
                packet.actionSequence(),
                packet.actionTick(),
                packet.actionStartGameTime(),
                packet.seed(),
                packet.actionRangeMultiplier(),
                packet.targetEntityId(),
                MALENIA_PROFILES.get(packet.actionId())
        );
    }

    public static void observe(BossCombatSnapshotPacket packet) {
        if (!"elder_bosses:promised_consort".equals(packet.bossId())
            || packet.actionId().isEmpty()
            || "dormant".equals(packet.combatState())
            || "defeated".equals(packet.combatState())) {
            onTrackingEnd(packet.entityId());
            return;
        }
        PromisedConsortActionId actionId = PromisedConsortActionId
                .fromSerializedName(packet.actionId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown Promised Consort VFX action " + packet.actionId()
                ));
        observe(
                packet.entityId(),
                packet.actionId(),
                packet.actionSequence(),
                packet.actionTick(),
                packet.actionStartGameTime(),
                packet.actionSeed(),
                packet.actionRangeMultiplier(),
                packet.targetEntityId(),
                CONSORT_PROFILES.get(actionId)
        );
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            clear();
            return;
        }
        long gameTick = level.getGameTime();
        if (lastEmissionTick == gameTick) {
            return;
        }
        lastEmissionTick = gameTick;
        ElderBossesCommonConfig.SkillVfxValues config = ElderBossesCommonConfig.VALUES.skillVfx();
        if (!config.enabled()) {
            return;
        }
        double maximumDistanceSquared = config.renderDistance() * config.renderDistance();
        Iterator<ActiveVfx> iterator = ACTIVE.values().iterator();
        while (iterator.hasNext()) {
            ActiveVfx active = iterator.next();
            Entity boss = level.getEntity(active.entityId());
            if (boss == null || !boss.isAlive() || gameTick - active.observedGameTick() > 100L) {
                iterator.remove();
                continue;
            }
            if (minecraft.player.distanceToSqr(boss) > maximumDistanceSquared) {
                continue;
            }
            int actionTick = Math.max(
                    active.actionTick(),
                    Math.toIntExact(Math.min(Integer.MAX_VALUE,
                            active.actionTick() + gameTick - active.observedGameTick()))
            );
            Entity target = active.targetEntityId() < 0
                    ? null
                    : level.getEntity(active.targetEntityId());
            Random random = new Random(mixSeed(active.seed(), active.sequence(), actionTick));
            Emitter emitter = new Emitter(
                    level,
                    random,
                    config.particleBudgetPerBossPerTick()
            );
            Profile profile = active.profile().scaled(active.rangeMultiplier());
            if (boss instanceof PromisedConsortEntity) {
                for (var snapshot : ClientIndicatorStateStore.snapshots()) {
                    if (snapshot.bossEntityId() != boss.getId() || snapshot.activeTick() != gameTick) continue;
                    String style = snapshot.styleRole().name();
                    if (!style.contains("PHYSICAL") && !style.contains("BLOOD")) continue;
                    Vec3 point = new Vec3(snapshot.anchor().x(), snapshot.anchor().y() + 0.1, snapshot.anchor().z());
                    for (int index = 0; index < 2; index++) {
                        emitter.add(style.contains("BLOOD") ? ParticleTypes.SMALL_FLAME : ParticleTypes.ASH,
                                point.add((random.nextDouble() - 0.5) * 1.4, 0.05, (random.nextDouble() - 0.5) * 1.4), new Vec3(0, 0.02, 0));
                    }
                }
                continue;
            }
            emit(profile, boss, target, actionTick, emitter);
        }
    }

    public static void onTrackingEnd(int entityId) {
        ACTIVE.remove(entityId);
    }

    public static void clear() {
        ACTIVE.clear();
        lastEmissionTick = Long.MIN_VALUE;
    }

    private static void observe(
            int entityId,
            String actionId,
            long sequence,
            int actionTick,
            long actionStartGameTime,
            long seed,
            double rangeMultiplier,
            int targetEntityId,
            Profile profile
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        long observedGameTick = minecraft.level == null
                ? actionStartGameTime + actionTick
                : minecraft.level.getGameTime();
        ACTIVE.put(entityId, new ActiveVfx(
                entityId,
                actionId,
                sequence,
                actionTick,
                observedGameTick,
                seed,
                rangeMultiplier,
                targetEntityId,
                profile
        ));
    }

    private static void emit(
            Profile profile,
            Entity boss,
            Entity target,
            int actionTick,
            Emitter emitter
    ) {
        Vec3 center = boss.position().add(0.0, boss.getBbHeight() * 0.55, 0.0);
        Vec3 forward = horizontalDirection(boss.getYRot());
        Vec3 targetCenter = target == null
                ? center.add(forward.scale(profile.radius()))
                : target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        switch (profile.style()) {
            case BLADE -> bladeArc(center, forward, profile.radius(), actionTick, emitter, false);
            case RAPID_BLADE -> bladeArc(center, forward, profile.radius(), actionTick, emitter, true);
            case THRUST -> thrustTrail(center, targetCenter, profile.radius(), emitter);
            case AERIAL -> aerialTrail(center, profile.radius(), profile.height(), actionTick, emitter);
            case SCARLET -> scarletBloom(center, profile.radius(), actionTick, emitter);
            case PHANTOM -> phantomTrails(center, targetCenter, profile.radius(), actionTick, emitter);
            case GRAVITY -> gravityVortex(center, profile.radius(), actionTick, emitter);
            case BLOODFLAME -> bloodflameTrail(center, forward, profile.radius(), actionTick, emitter);
            case HOLY -> holyLight(targetCenter, profile.radius(), profile.height(), actionTick, emitter);
            case LIGHTSPEED -> lightspeedTrail(center, targetCenter, profile.radius(), actionTick, emitter);
            case EARTH -> earthImpact(center, forward, profile.radius(), actionTick, emitter);
            case METEOR -> meteorDescent(targetCenter, profile.radius(), profile.height(), actionTick, emitter);
        }
    }

    private static void bladeArc(
            Vec3 center,
            Vec3 forward,
            double radius,
            int tick,
            Emitter emitter,
            boolean rapid
    ) {
        int points = rapid ? 10 : 7;
        double phase = rapid ? (tick % 4) * 0.32 : 0.0;
        for (int index = 0; index < points; index++) {
            double angle = -1.15 + 2.30 * index / Math.max(1, points - 1) + phase;
            Vec3 direction = rotateY(forward, angle);
            double distance = radius * (0.45 + 0.55 * index / Math.max(1, points - 1));
            emitter.add(ParticleTypes.CRIT, center.add(direction.scale(distance)), direction.scale(0.04));
        }
        if (tick % (rapid ? 2 : 4) == 0) {
            emitter.add(ParticleTypes.SWEEP_ATTACK, center.add(forward.scale(radius * 0.6)), Vec3.ZERO);
        }
    }

    private static void thrustTrail(
            Vec3 center,
            Vec3 target,
            double radius,
            Emitter emitter
    ) {
        Vec3 direction = target.subtract(center).normalize();
        if (direction.lengthSqr() == 0.0) {
            direction = new Vec3(0.0, 0.0, 1.0);
        }
        for (int index = 1; index <= 10; index++) {
            Vec3 point = center.add(direction.scale(radius * index / 10.0));
            emitter.add(index % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.CRIT,
                    point, direction.scale(0.03));
        }
    }

    private static void aerialTrail(
            Vec3 center,
            double radius,
            double height,
            int tick,
            Emitter emitter
    ) {
        for (int index = 0; index < 10; index++) {
            double angle = tick * 0.32 + index * Math.PI * 0.4;
            double scale = radius * (0.25 + index / 20.0);
            Vec3 point = center.add(
                    Math.cos(angle) * scale,
                    -height * 0.5 + height * index / 9.0,
                    Math.sin(angle) * scale
            );
            emitter.add(index % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.CLOUD,
                    point, new Vec3(0.0, 0.035, 0.0));
        }
    }

    private static void scarletBloom(Vec3 center, double radius, int tick, Emitter emitter) {
        for (int index = 0; index < 12; index++) {
            double angle = index * Math.PI / 6.0 + tick * 0.10;
            double distance = radius * (0.35 + 0.65 * ((index % 3) + 1) / 3.0);
            Vec3 point = center.add(
                    Math.cos(angle) * distance,
                    Math.sin(tick * 0.18 + index) * 0.45,
                    Math.sin(angle) * distance
            );
            emitter.add(index % 3 == 0 ? ParticleTypes.CRIMSON_SPORE : ParticleTypes.SPORE_BLOSSOM_AIR,
                    point, new Vec3(0.0, 0.015, 0.0));
        }
    }

    private static void phantomTrails(
            Vec3 center,
            Vec3 target,
            double radius,
            int tick,
            Emitter emitter
    ) {
        Vec3 baseDirection = target.subtract(center).normalize();
        if (baseDirection.lengthSqr() == 0.0) {
            baseDirection = new Vec3(0.0, 0.0, 1.0);
        }
        for (int lane = -2; lane <= 2; lane++) {
            Vec3 direction = rotateY(baseDirection, lane * 0.22 + tick * 0.015);
            for (int step = 1; step <= 3; step++) {
                Vec3 point = center.add(direction.scale(radius * step / 3.0))
                        .add(0.0, lane * 0.22, 0.0);
                emitter.add(lane == 0 ? ParticleTypes.END_ROD : ParticleTypes.REVERSE_PORTAL,
                        point, direction.scale(0.06));
            }
        }
    }

    private static void gravityVortex(Vec3 center, double radius, int tick, Emitter emitter) {
        for (int index = 0; index < 14; index++) {
            double angle = tick * 0.25 + index * Math.PI * 2.0 / 14.0;
            double distance = radius * (0.3 + 0.7 * ((index % 5) + 1) / 5.0);
            Vec3 point = center.add(
                    Math.cos(angle) * distance,
                    (index % 4 - 1.5) * 0.35,
                    Math.sin(angle) * distance
            );
            Vec3 velocity = center.subtract(point).normalize().scale(0.045);
            emitter.add(index % 4 == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.PORTAL,
                    point, velocity);
        }
    }

    private static void bloodflameTrail(
            Vec3 center,
            Vec3 forward,
            double radius,
            int tick,
            Emitter emitter
    ) {
        for (int index = 0; index < 10; index++) {
            double side = Math.sin(tick * 0.35 + index) * radius * 0.2;
            Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
            Vec3 point = center.add(forward.scale(radius * index / 9.0)).add(right.scale(side));
            emitter.add(index % 4 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME,
                    point, new Vec3(0.0, 0.025, 0.0));
        }
    }

    private static void holyLight(
            Vec3 center,
            double radius,
            double height,
            int tick,
            Emitter emitter
    ) {
        for (int index = 0; index < 12; index++) {
            double angle = tick * 0.12 + index * Math.PI / 6.0;
            Vec3 ring = center.add(Math.cos(angle) * radius, 0.05, Math.sin(angle) * radius);
            emitter.add(index % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD,
                    ring, new Vec3(0.0, 0.04, 0.0));
        }
        for (int index = 0; index < 5; index++) {
            emitter.add(ParticleTypes.END_ROD,
                    center.add(0.0, height * index / 4.0, 0.0),
                    new Vec3(0.0, 0.055, 0.0));
        }
    }

    private static void lightspeedTrail(
            Vec3 center,
            Vec3 target,
            double radius,
            int tick,
            Emitter emitter
    ) {
        Vec3 direction = target.subtract(center).normalize();
        if (direction.lengthSqr() == 0.0) {
            direction = new Vec3(0.0, 0.0, 1.0);
        }
        Vec3 right = new Vec3(-direction.z, 0.0, direction.x);
        for (int index = 0; index < 14; index++) {
            double distance = radius * index / 13.0;
            double offset = Math.sin(tick * 0.55 + index) * 0.35;
            Vec3 point = center.add(direction.scale(distance)).add(right.scale(offset));
            emitter.add(index % 3 == 0 ? ParticleTypes.GLOW : ParticleTypes.END_ROD,
                    point, direction.scale(0.09));
        }
    }

    private static void earthImpact(
            Vec3 center,
            Vec3 forward,
            double radius,
            int tick,
            Emitter emitter
    ) {
        Vec3 ground = new Vec3(center.x, center.y - 1.0, center.z);
        for (int index = 0; index < 12; index++) {
            double angle = index * Math.PI / 6.0 + tick * 0.04;
            Vec3 direction = rotateY(forward, angle);
            Vec3 point = ground.add(direction.scale(radius * (0.3 + (index % 4) * 0.2)));
            emitter.add(index % 3 == 0 ? ParticleTypes.POOF : ParticleTypes.ASH,
                    point, new Vec3(direction.x * 0.04, 0.06, direction.z * 0.04));
        }
    }

    private static void meteorDescent(
            Vec3 center,
            double radius,
            double height,
            int tick,
            Emitter emitter
    ) {
        for (int index = 0; index < 12; index++) {
            double angle = index * Math.PI / 6.0 + tick * 0.18;
            Vec3 point = center.add(
                    Math.cos(angle) * radius * 0.45,
                    height - (tick % 20) * height / 20.0 + (index % 3) * 0.4,
                    Math.sin(angle) * radius * 0.45
            );
            emitter.add(index % 3 == 0 ? ParticleTypes.FLAME : ParticleTypes.LARGE_SMOKE,
                    point, new Vec3(0.0, -0.08, 0.0));
        }
        if (tick % 12 == 0) {
            emitter.add(ParticleTypes.FLASH, center, Vec3.ZERO);
        }
    }

    private static Vec3 horizontalDirection(float yawDegrees) {
        double radians = Math.toRadians(yawDegrees);
        return new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
    }

    private static Vec3 rotateY(Vec3 direction, double radians) {
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return new Vec3(
                direction.x * cosine - direction.z * sine,
                direction.y,
                direction.x * sine + direction.z * cosine
        );
    }

    private static long mixSeed(long seed, long sequence, int actionTick) {
        long value = seed ^ Long.rotateLeft(sequence, 21) ^ actionTick * 0x9E3779B97F4A7C15L;
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private static Map<MaleniaActionId, Profile> maleniaProfiles() {
        EnumMap<MaleniaActionId, Profile> profiles = new EnumMap<>(MaleniaActionId.class);
        profiles.put(MaleniaActionId.SINGLE_SLASH, profile(Style.BLADE, 3.4, 1.5));
        profiles.put(MaleniaActionId.DOUBLE_SLASH, profile(Style.BLADE, 3.5, 1.5));
        profiles.put(MaleniaActionId.RAPID_SLASHES, profile(Style.RAPID_BLADE, 3.8, 1.8));
        profiles.put(MaleniaActionId.RUNNING_SLASH, profile(Style.LIGHTSPEED, 6.0, 1.5));
        profiles.put(MaleniaActionId.UPWARD_COMBO, profile(Style.AERIAL, 3.8, 4.0));
        profiles.put(MaleniaActionId.KICK, profile(Style.EARTH, 2.5, 1.0));
        profiles.put(MaleniaActionId.THRUST, profile(Style.THRUST, 7.0, 1.4));
        profiles.put(MaleniaActionId.GRAB_IMPALE, profile(Style.THRUST, 5.0, 2.0));
        profiles.put(MaleniaActionId.RETREAT_SLASH, profile(Style.BLADE, 3.5, 1.5));
        profiles.put(MaleniaActionId.WATERFOWL_DANCE, profile(Style.RAPID_BLADE, 5.0, 3.0));
        profiles.put(MaleniaActionId.SCARLET_AEONIA, profile(Style.SCARLET, 5.5, 7.0));
        profiles.put(MaleniaActionId.SCARLET_PLUNGE, profile(Style.SCARLET, 4.0, 4.0));
        profiles.put(MaleniaActionId.FLYING_SLASH, profile(Style.AERIAL, 7.0, 4.0));
        profiles.put(MaleniaActionId.SCARLET_PHANTOMS, profile(Style.PHANTOM, 8.0, 5.0));
        profiles.put(MaleniaActionId.WINGED_SWEEP, profile(Style.SCARLET, 4.5, 2.5));
        requireComplete(profiles, MaleniaActionId.values().length, "Malenia");
        return Map.copyOf(profiles);
    }

    private static Map<PromisedConsortActionId, Profile> consortProfiles() {
        EnumMap<PromisedConsortActionId, Profile> profiles =
                new EnumMap<>(PromisedConsortActionId.class);
        profiles.put(PromisedConsortActionId.GRAVITY_DIVE, profile(Style.GRAVITY, 5.0, 4.0));
        profiles.put(PromisedConsortActionId.L_COMBO_CROSS, profile(Style.BLADE, 4.0, 2.0));
        profiles.put(PromisedConsortActionId.L_COMBO_BLOODFLAME, profile(Style.BLOODFLAME, 5.0, 2.0));
        profiles.put(PromisedConsortActionId.R_COMBO_CROSS, profile(Style.BLADE, 4.0, 2.0));
        profiles.put(PromisedConsortActionId.R_COMBO_LEFT_TWIN, profile(Style.RAPID_BLADE, 4.0, 2.0));
        profiles.put(PromisedConsortActionId.R_COMBO_TEMPEST, profile(Style.GRAVITY, 4.5, 3.0));
        profiles.put(PromisedConsortActionId.R_COMBO_EARTHHEAVE, profile(Style.EARTH, 7.0, 3.0));
        profiles.put(PromisedConsortActionId.LION_CLAW, profile(Style.AERIAL, 4.0, 4.0));
        profiles.put(PromisedConsortActionId.LION_CLAW_DOUBLE, profile(Style.AERIAL, 4.5, 4.0));
        profiles.put(PromisedConsortActionId.STARCALLER_CRY, profile(Style.GRAVITY, 12.0, 5.0));
        profiles.put(PromisedConsortActionId.GRAVITY_METEOR, profile(Style.METEOR, 5.0, 8.0));
        profiles.put(PromisedConsortActionId.STOMP, profile(Style.EARTH, 5.0, 1.0));
        profiles.put(PromisedConsortActionId.CROSS_SLASH, profile(Style.BLADE, 7.0, 2.0));
        profiles.put(PromisedConsortActionId.SPIRAL_ASSAULT, profile(Style.RAPID_BLADE, 8.0, 3.0));
        profiles.put(PromisedConsortActionId.LIGHT_OF_MIQUELLA, profile(Style.HOLY, 8.0, 10.0));
        profiles.put(PromisedConsortActionId.RING_OF_LIGHT, profile(Style.HOLY, 11.0, 4.0));
        profiles.put(PromisedConsortActionId.LIGHTSPEED_SLASH, profile(Style.LIGHTSPEED, 10.0, 3.0));
        profiles.put(PromisedConsortActionId.LIGHTSPEED_DASH, profile(Style.LIGHTSPEED, 16.0, 3.0));
        profiles.put(PromisedConsortActionId.LIGHTSPEED_SIDE_DASH, profile(Style.LIGHTSPEED, 9.0, 3.0));
        profiles.put(PromisedConsortActionId.PROMISED_CONSORT, profile(Style.HOLY, 10.0, 7.0));
        profiles.put(PromisedConsortActionId.ENHANCED_EARTHHEAVE, profile(Style.HOLY, 8.0, 5.0));
        profiles.put(PromisedConsortActionId.CONSORT_METEOR, profile(Style.METEOR, 13.0, 14.0));
        requireComplete(profiles, PromisedConsortActionId.values().length, "Promised Consort");
        return Map.copyOf(profiles);
    }

    private static Profile profile(Style style, double radius, double height) {
        return new Profile(style, radius, height);
    }

    private static void requireComplete(Map<?, ?> profiles, int expected, String boss) {
        if (profiles.size() != expected) {
            throw new IllegalStateException(boss + " VFX profiles must cover every action");
        }
    }

    private enum Style {
        BLADE,
        RAPID_BLADE,
        THRUST,
        AERIAL,
        SCARLET,
        PHANTOM,
        GRAVITY,
        BLOODFLAME,
        HOLY,
        LIGHTSPEED,
        EARTH,
        METEOR
    }

    private record Profile(Style style, double radius, double height) {
        private Profile scaled(double rangeMultiplier) {
            return new Profile(style, radius * rangeMultiplier, height * rangeMultiplier);
        }
    }

    private record ActiveVfx(
            int entityId,
            String actionId,
            long sequence,
            int actionTick,
            long observedGameTick,
            long seed,
            double rangeMultiplier,
            int targetEntityId,
            Profile profile
    ) {
    }

    private static final class Emitter {
        private final ClientLevel level;
        private final Random random;
        private int remaining;

        private Emitter(ClientLevel level, Random random, int budget) {
            this.level = level;
            this.random = random;
            remaining = budget;
        }

        private void add(ParticleOptions particle, Vec3 position, Vec3 velocity) {
            if (remaining-- <= 0) {
                return;
            }
            double jitter = 0.035;
            level.addParticle(
                    particle,
                    position.x + (random.nextDouble() - 0.5) * jitter,
                    position.y + (random.nextDouble() - 0.5) * jitter,
                    position.z + (random.nextDouble() - 0.5) * jitter,
                    velocity.x,
                    velocity.y,
                    velocity.z
            );
        }
    }
}
