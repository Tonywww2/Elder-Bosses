package com.tonywww.elder_bosses.boss.malenia.action;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public record MaleniaActionEvent(
        Type type,
        int sequence,
        OptionalInt actionTick,
        OptionalInt durationTicks,
        OptionalInt minimumDelayAfterPreviousTicks,
        OptionalDouble maxTravel,
        OptionalInt maxHitsPerTarget
) {
    public MaleniaActionEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(actionTick, "actionTick");
        Objects.requireNonNull(durationTicks, "durationTicks");
        Objects.requireNonNull(minimumDelayAfterPreviousTicks, "minimumDelayAfterPreviousTicks");
        Objects.requireNonNull(maxTravel, "maxTravel");
        Objects.requireNonNull(maxHitsPerTarget, "maxHitsPerTarget");
        if (sequence < 0) {
            throw new IllegalArgumentException("event sequence must be non-negative");
        }
        actionTick.ifPresent(value -> requireNonNegative(value, "action tick"));
        durationTicks.ifPresent(value -> requirePositive(value, "event duration"));
        minimumDelayAfterPreviousTicks.ifPresent(value -> requirePositive(value, "minimum event delay"));
        maxTravel.ifPresent(value -> requirePositive(value, "maximum travel"));
        maxHitsPerTarget.ifPresent(value -> requirePositive(value, "maximum hits per target"));
        if (durationTicks.isPresent() && actionTick.isEmpty()) {
            throw new IllegalArgumentException("a lasting event requires an action tick");
        }
        if (type == Type.TRACKING_BURST
                && (actionTick.isEmpty() || maxTravel.isEmpty() || maxHitsPerTarget.isEmpty())) {
            throw new IllegalArgumentException("a tracking burst requires a lock tick, travel limit, and hit limit");
        }
    }

    public static MaleniaActionEvent ordered(Type type, int sequence) {
        return new MaleniaActionEvent(
                type,
                sequence,
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalDouble.empty(),
                OptionalInt.empty()
        );
    }

    public static MaleniaActionEvent delayed(Type type, int sequence, int minimumDelayAfterPreviousTicks) {
        return new MaleniaActionEvent(
                type,
                sequence,
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.of(minimumDelayAfterPreviousTicks),
                OptionalDouble.empty(),
                OptionalInt.empty()
        );
    }

    public static MaleniaActionEvent atTick(Type type, int sequence, int actionTick) {
        return new MaleniaActionEvent(
                type,
                sequence,
                OptionalInt.of(actionTick),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalDouble.empty(),
                OptionalInt.empty()
        );
    }

    public static MaleniaActionEvent lasting(Type type, int sequence, int actionTick, int durationTicks) {
        return new MaleniaActionEvent(
                type,
                sequence,
                OptionalInt.of(actionTick),
                OptionalInt.of(durationTicks),
                OptionalInt.empty(),
                OptionalDouble.empty(),
                OptionalInt.empty()
        );
    }

    public static MaleniaActionEvent trackingBurst(
            int sequence,
            int lockTick,
            double maxTravel,
            int maxHitsPerTarget
    ) {
        return new MaleniaActionEvent(
                Type.TRACKING_BURST,
                sequence,
                OptionalInt.of(lockTick),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalDouble.of(maxTravel),
                OptionalInt.of(maxHitsPerTarget)
        );
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    public enum Type {
        SLASH,
        REVERSE_SLASH,
        RAPID_SLASH,
        DELAYED_FINISHER,
        RUNNING_ENTRY,
        UPWARD_SLASH,
        PLUNGING_SLASH,
        KICK,
        THRUST,
        RETREAT,
        GRAB,
        IMPALE,
        THROW,
        AERIAL_HOVER,
        TRACKING_BURST,
        TARGET_LOCK,
        DIVE,
        IMPACT,
        SCARLET_BLOOM,
        LINGERING_CLOUD,
        ROT_BURST,
        SWEEP,
        PHANTOM_ASSAULT,
        BOSS_DIVE
    }
}