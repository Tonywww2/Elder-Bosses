package com.tonywww.elder_bosses.client.render;

public final class MaleniaAnimationClock {
    private String clip = "";
    private long sequence;
    private float sampledTick = -1.0F;
    private float sampledNextTick;
    private double receivedAt;
    private double displayedTick;
    private String locomotion = "";
    private String pendingLocomotion = "";
    private long pendingSince;
    private double gaitPhase;
    private double gaitFrameTime = -1.0;
    private double gaitX;
    private double gaitZ;
    private boolean gaitWasActive;

    public double sample(String clip, long sequence, float current, float next, double frameTime) {
        boolean restarted = !this.clip.equals(clip) || this.sequence != sequence || current < sampledTick;
        boolean changed = restarted || current != sampledTick || next != sampledNextTick;
        if (changed) {
            this.clip = clip;
            this.sequence = sequence;
            sampledTick = current;
            sampledNextTick = Math.max(current, next);
            receivedAt = frameTime;
        }
        if (restarted) {
            displayedTick = Math.max(0.0, current);
        }
        double progress = Math.max(0.0, Math.min(1.0, frameTime - receivedAt));
        double interpolated = sampledTick + progress * (sampledNextTick - sampledTick);
        displayedTick = Math.min(sampledNextTick, Math.max(displayedTick, interpolated));
        return displayedTick;
    }

    public String locomotion(String candidate, long gameTick) {
        if (locomotion.isEmpty() || candidate.startsWith("idle_phase_") && locomotion.startsWith("idle_phase_")) {
            locomotion = candidate;
        }
        if (!candidate.equals(pendingLocomotion)) {
            pendingLocomotion = candidate;
            pendingSince = gameTick;
        }
        if (!candidate.equals(locomotion) && gameTick - pendingSince >= 4) {
            locomotion = candidate;
        }
        return locomotion;
    }

    public void advanceGait(String clip, double frameTime, double horizontal, double depth) {
        if (frameTime <= gaitFrameTime) {
            return;
        }
        double stride = switch (clip) {
            case "walk" -> 2.0;
            case "walk_back" -> 1.6;
            case "strafe_left", "strafe_right" -> 1.4;
            case "run" -> 2.7;
            default -> 0.0;
        };
        double distance = Math.hypot(horizontal - gaitX, depth - gaitZ);
        if (stride > 0.0 && gaitWasActive && gaitFrameTime >= 0.0
                && frameTime - gaitFrameTime <= 5.0 && distance <= 2.0) {
            gaitPhase = (gaitPhase + distance / stride) % 1.0;
        }
        gaitFrameTime = frameTime;
        gaitX = horizontal;
        gaitZ = depth;
        gaitWasActive = stride > 0.0;
    }

    public double gaitTime(String clip) {
        int duration = switch (clip) {
            case "walk" -> 32;
            case "walk_back", "strafe_left", "strafe_right" -> 36;
            case "run" -> 20;
            default -> -1;
        };
        return duration < 0 ? -1.0 : gaitPhase * duration;
    }
}