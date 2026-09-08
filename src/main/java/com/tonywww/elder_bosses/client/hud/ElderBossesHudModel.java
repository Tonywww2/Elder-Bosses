package com.tonywww.elder_bosses.client.hud;

import com.tonywww.elder_bosses.client.state.ClientBossStateStore;
import com.tonywww.elder_bosses.client.state.ClientRotStateStore;
import com.tonywww.elder_bosses.player.PlayerRotSnapshot;
import java.util.Objects;

public record ElderBossesHudModel(
        RotMeter rot,
        StaggerMeter stagger,
        Subtitle subtitle
) {
    public ElderBossesHudModel {
        Objects.requireNonNull(rot, "rot");
        Objects.requireNonNull(stagger, "stagger");
        Objects.requireNonNull(subtitle, "subtitle");
    }

    public static ElderBossesHudModel capture(long gameTime) {
        ClientRotStateStore.Snapshot rotSnapshot = ClientRotStateStore.snapshot();
        ClientBossStateStore.Snapshot bossSnapshot = ClientBossStateStore.snapshot();
        PlayerRotSnapshot rotState = rotSnapshot.state();

        long elapsedTicks = gameTime > rotState.gameTick()
                ? gameTime - rotState.gameTick()
                : 0L;
        int remainingActiveTicks = (int) Math.max(
                0L,
                (long) rotState.remainingActiveTicks() - elapsedTicks
        );
        boolean rotActive = rotState.active() && remainingActiveTicks > 0;
        RotMeter rot = RotMeter.hidden();
        if (rotState.buildup() > 0.0 || rotActive) {
            double buildupRatio = rotState.buildup() / rotState.capacity();
            double activeRemainingRatio = rotSnapshot.activeDurationTicks() == 0
                ? 0.0
                : (double) remainingActiveTicks / rotSnapshot.activeDurationTicks();
            rot = RotMeter.visible(buildupRatio, rotActive, activeRemainingRatio);
        }

        StaggerMeter stagger = bossSnapshot.visible() && bossSnapshot.staggerCapacity() > 0.0
            ? StaggerMeter.visible(bossSnapshot.stagger() / bossSnapshot.staggerCapacity())
            : StaggerMeter.hidden();

        Subtitle subtitle = bossSnapshot.dialogueTextKey().isEmpty()
            ? Subtitle.hidden()
            : Subtitle.visible(
                bossSnapshot.dialogueSpeakerKey(),
                bossSnapshot.dialogueTextKey(),
                gameTime - bossSnapshot.dialogueEventStartTick(),
                bossSnapshot.subtitleDurationTicks()
            );

        return new ElderBossesHudModel(rot, stagger, subtitle);
    }

    public record RotMeter(boolean visible, double buildupRatio, boolean active, double activeRemainingRatio) {
        public static RotMeter hidden() {
            return new RotMeter(false, 0.0, false, 0.0);
        }

        public static RotMeter visible(double buildupRatio, boolean active, double activeRemainingRatio) {
            return new RotMeter(
                    true,
                    clampRatio(buildupRatio),
                    active,
                    active ? clampRatio(activeRemainingRatio) : 0.0
            );
        }
    }

    public record StaggerMeter(boolean visible, double ratio) {
        public static StaggerMeter hidden() {
            return new StaggerMeter(false, 0.0);
        }

        public static StaggerMeter visible(double ratio) {
            return new StaggerMeter(true, clampRatio(ratio));
        }
    }

    public record Subtitle(
            boolean visible,
            String speakerLanguageKey,
            String textLanguageKey,
            double opacity
    ) {
        public Subtitle {
            Objects.requireNonNull(speakerLanguageKey, "speakerLanguageKey");
            Objects.requireNonNull(textLanguageKey, "textLanguageKey");
            opacity = clampRatio(opacity);
        }

        public static Subtitle hidden() {
            return new Subtitle(false, "", "", 0.0);
        }

        public static Subtitle visible(
                String speakerLanguageKey,
                String textLanguageKey,
                long ageTicks,
                int durationTicks
        ) {
            if (durationTicks <= 0 || ageTicks < 0L || ageTicks >= durationTicks) {
                return hidden();
            }

            int fadeTicks = Math.min(10, Math.max(1, durationTicks / 4));
            double fadeIn = Math.min(1.0, (ageTicks + 1.0) / fadeTicks);
            double fadeOut = Math.min(1.0, (durationTicks - ageTicks) / fadeTicks);
            return new Subtitle(
                    true,
                    speakerLanguageKey,
                    textLanguageKey,
                    Math.min(fadeIn, fadeOut)
            );
        }
    }

    private static double clampRatio(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}