package com.tonywww.elder_bosses.client.audio;

import com.tonywww.elder_bosses.network.BossCombatSnapshotPacket;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;
import com.tonywww.elder_bosses.platforms.registry.ModSoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** One encounter at a time; a second stream exists only during a bounded crossfade. */
public final class ClientBossMusic {
    private static final BossMusicState STATE = new BossMusicState();
    private static Loop playing;
    private static Loop fading;
    private static long clock;
    private static long retryAt;
    private static long startedAt;
    private static int owner = -1;
    private static int phase;
    private static int parryDuckTicks;

    private ClientBossMusic() {}

    public static void observe(BossCombatSnapshotPacket packet) {
        STATE.observe(packet.entityId(), packet.bossId(), packet.phase(), packet.combatState(),
                packet.hudVisible(), packet.health(), clock);
    }

    public static void onTrackingEnd(int entityId) { STATE.remove(entityId); }

    public static void observe(com.tonywww.elder_bosses.network.MaleniaCombatSnapshotPacket packet) {
        STATE.observe(packet.entityId(), "elder_bosses:malenia", packet.phase().name().toLowerCase(java.util.Locale.ROOT),
                packet.combatState().name().toLowerCase(java.util.Locale.ROOT), true, packet.phaseHealth(), clock);
    }

    public static void emphasizeParry() { parryDuckTicks = 12; }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { clear(); return; }
        if (mc.isPaused()) return;
        clock++;
        if (parryDuckTicks > 0) parryDuckTicks--;
        var settings = ElderBossesCommonConfig.VALUES.bossMusic();
        if (!settings.enabled() || settings.volume() <= 0
                || mc.options.getSoundSourceVolume(SoundSource.MUSIC) <= 0
                || mc.options.getSoundSourceVolume(SoundSource.MASTER) <= 0) {
            stopStreams(mc);
            return;
        }
        var encounter = !mc.player.isAlive() ? null : STATE.select(clock, id -> {
            var entity = mc.level.getEntity(id);
            return entity == null || !entity.isAlive() ? Double.POSITIVE_INFINITY : mc.player.distanceToSqr(entity);
        }, settings.distance());
        if (fading != null && (fading.isStopped() || !mc.getSoundManager().isActive(fading))) {
            fading.finish(); fading = null;
        }
        // Sound/resource reload and missing or replaced assets must not suppress vanilla music forever.
        if (playing != null && clock - startedAt > 20 && !mc.getSoundManager().isActive(playing)) {
            playing.finish(); playing = null; owner = -1; retryAt = clock + 100;
        }
        if (encounter == null) {
            retire(mc, settings.fadeTicks());
            return;
        }
        if (playing == null || owner != encounter.entityId() || phase != encounter.phase()) {
            if (clock < retryAt) return;
            retire(mc, settings.fadeTicks());
            boolean malenia = encounter.boss().equals("elder_bosses:malenia");
            SoundEvent event = malenia
                    ? encounter.phase() == 2 ? ModSoundEvents.MALENIA_MUSIC_PHASE_TWO.get() : ModSoundEvents.MALENIA_MUSIC_PHASE_ONE.get()
                    : encounter.phase() == 2 ? ModSoundEvents.CONSORT_MUSIC_PHASE_TWO.get() : ModSoundEvents.CONSORT_MUSIC_PHASE_ONE.get();
            if (mc.getSoundManager().getSoundEvent(event.getLocation()) == null) { retryAt = clock + 100; return; }
            playing = new Loop(event, (float) settings.volume(), settings.fadeTicks());
            owner = encounter.entityId(); phase = encounter.phase(); startedAt = clock;
            // Stop the current vanilla track once. The platform play event suppresses new music during ownership.
            mc.getMusicManager().stopPlaying();
            mc.getSoundManager().play(playing);
        }
        if (playing != null) playing.target = (float) (settings.volume() * (1 - .65 * Math.min(1, parryDuckTicks / 6.0)));
    }

    private static void retire(Minecraft mc, int ticks) {
        if (playing == null) return;
        if (fading != null) { fading.finish(); mc.getSoundManager().stop(fading); }
        fading = playing; fading.fadeOut(ticks); playing = null; owner = -1;
    }

    public static boolean suppress(SoundInstance sound) {
        return sound != null && sound.getSource() == SoundSource.MUSIC && !(sound instanceof Loop)
                && ((playing != null && !playing.isStopped()) || (fading != null && !fading.isStopped()));
    }

    private static void stopStreams(Minecraft mc) {
        for (Loop stream : new Loop[]{playing, fading}) {
            if (stream != null) { stream.finish(); mc.getSoundManager().stop(stream); }
        }
        playing = null; fading = null; owner = -1; phase = 0; retryAt = 0;
    }

    public static void clear() {
        stopStreams(Minecraft.getInstance()); STATE.clear(); clock = 0; startedAt = 0; parryDuckTicks = 0;
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private float target;
        private final BossMusicFade envelope;

        Loop(SoundEvent event, float target, int ticks) {
            super(event, SoundSource.MUSIC, RandomSource.create());
            this.target = target; envelope = new BossMusicFade(ticks);
            volume = 0.001F; looping = true; delay = 0;
            relative = true; attenuation = Attenuation.NONE;
        }

        void fadeOut(int ticks) { envelope.retire(ticks); }
        void finish() { stop(); }
        @Override public boolean canStartSilent() { return true; }
        @Override public void tick() {
            volume = envelope.tick(target);
            if (envelope.finished()) stop();
        }
    }
}
