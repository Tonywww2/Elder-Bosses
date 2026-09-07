package com.tonywww.elder_bosses.boss.malenia.execution;

import com.tonywww.elder_bosses.boss.malenia.config.MaleniaSkillConfigSnapshot.HealProfile;
import com.tonywww.elder_bosses.combat.damage.DamageChannel;
import com.tonywww.elder_bosses.combat.damage.DamageFormula;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

public sealed interface MaleniaServerIntent permits
        MaleniaServerIntent.LockFacing,
        MaleniaServerIntent.LockPoint,
    MaleniaServerIntent.LockPhantom,
        MaleniaServerIntent.MoveToward,
        MaleniaServerIntent.MoveVertical,
        MaleniaServerIntent.HoldVertical,
        MaleniaServerIntent.MoveAway,
        MaleniaServerIntent.HitSector,
        MaleniaServerIntent.HitCapsule,
        MaleniaServerIntent.HitCircle,
        MaleniaServerIntent.HitAnnulus,
        MaleniaServerIntent.RotZone,
        MaleniaServerIntent.IndicatorOnlyZone,
        MaleniaServerIntent.Grab,
        MaleniaServerIntent.HitGrabbed,
        MaleniaServerIntent.Release,
        MaleniaServerIntent.WaterfowlBurst,
        MaleniaServerIntent.PhantomStrike {

    record LockFacing() implements MaleniaServerIntent {
    }

    record LockPoint(String pointId, boolean projectToSurface) implements MaleniaServerIntent {
        public LockPoint(String pointId) {
            this(pointId, false);
        }

        public LockPoint {
            requireIdentifier(pointId, "pointId");
        }
    }

    record LockPhantom(int phantomIndex, int phantomCount) implements MaleniaServerIntent {
        public LockPhantom {
            requireNonNegative(phantomIndex, "phantomIndex");
            requirePositive(phantomCount, "phantomCount");
            if (phantomIndex >= phantomCount) {
                throw new IllegalArgumentException("phantomIndex must be less than phantomCount");
            }
        }
    }

    record MoveToward(
            String pointId,
            double maxTravel,
            int travelTicks,
            boolean includeVertical
    ) implements MaleniaServerIntent {
        public MoveToward(String pointId, double maxTravel) {
            this(pointId, maxTravel, 2, false);
        }

        public MoveToward(String pointId, double maxTravel, int travelTicks) {
            this(pointId, maxTravel, travelTicks, false);
        }

        public MoveToward {
            requireIdentifier(pointId, "pointId");
            requirePositiveFinite(maxTravel, "maxTravel");
            requireAtLeast(travelTicks, 2, "travelTicks");
        }

        public double perTickBudget() {
            return maxTravel / travelTicks;
        }
    }

    record MoveVertical(double maxTravel, int travelTicks) implements MaleniaServerIntent {
        public MoveVertical {
            requirePositiveFinite(maxTravel, "maxTravel");
            requireAtLeast(travelTicks, 2, "travelTicks");
        }

        public double perTickBudget() {
            return maxTravel / travelTicks;
        }
    }

    record HoldVertical() implements MaleniaServerIntent {
    }

    record MoveAway(double maxTravel, int travelTicks) implements MaleniaServerIntent {
        public MoveAway(double maxTravel) {
            this(maxTravel, 2);
        }

        public MoveAway {
            requirePositiveFinite(maxTravel, "maxTravel");
            requireAtLeast(travelTicks, 2, "travelTicks");
        }

        public double perTickBudget() {
            return maxTravel / travelTicks;
        }
    }

    record HitSector(
            double range,
            OptionalDouble arcDegrees,
            HitSpec hit
    ) implements MaleniaServerIntent {
        public HitSector {
            requirePositiveFinite(range, "range");
            arcDegrees = requireOptionalPositive(arcDegrees, "arcDegrees");
            if (arcDegrees.isPresent() && arcDegrees.getAsDouble() > 360.0) {
                throw new IllegalArgumentException("arcDegrees must not exceed 360");
            }
            Objects.requireNonNull(hit, "hit");
        }
    }

    record HitCapsule(
            double length,
            OptionalDouble width,
            Optional<String> endPointId,
            HitSpec hit
    ) implements MaleniaServerIntent {
        public HitCapsule(double length, OptionalDouble width, HitSpec hit) {
            this(length, width, Optional.empty(), hit);
        }

        public HitCapsule {
            requirePositiveFinite(length, "length");
            width = requireOptionalPositive(width, "width");
            endPointId = requireOptionalIdentifier(endPointId, "endPointId");
            Objects.requireNonNull(hit, "hit");
        }
    }

    record HitCircle(
            double radius,
            Optional<String> centerPointId,
            HitSpec hit
    ) implements MaleniaServerIntent {
        public HitCircle(double radius, HitSpec hit) {
            this(radius, Optional.empty(), hit);
        }

        public HitCircle {
            requirePositiveFinite(radius, "radius");
            centerPointId = requireOptionalIdentifier(centerPointId, "centerPointId");
            Objects.requireNonNull(hit, "hit");
        }
    }

    record HitAnnulus(
            double innerRadius,
            double outerRadius,
            HitSpec hit
    ) implements MaleniaServerIntent {
        public HitAnnulus {
            requireNonNegativeFinite(innerRadius, "innerRadius");
            requirePositiveFinite(outerRadius, "outerRadius");
            if (innerRadius > outerRadius) {
                throw new IllegalArgumentException("innerRadius must not exceed outerRadius");
            }
            Objects.requireNonNull(hit, "hit");
        }
    }

    record RotZone(
            double radius,
            Optional<String> centerPointId,
            int durationTicks,
            int intervalTicks,
            HitSpec hit
    ) implements MaleniaServerIntent {
        public RotZone(double radius, int durationTicks, int intervalTicks, HitSpec hit) {
            this(radius, Optional.empty(), durationTicks, intervalTicks, hit);
        }

        public RotZone {
            requirePositiveFinite(radius, "radius");
            centerPointId = requireOptionalIdentifier(centerPointId, "centerPointId");
            requirePositive(durationTicks, "durationTicks");
            requirePositive(intervalTicks, "intervalTicks");
            if (intervalTicks > durationTicks) {
                throw new IllegalArgumentException("intervalTicks must not exceed durationTicks");
            }
            Objects.requireNonNull(hit, "hit");
        }
    }

    record IndicatorOnlyZone(
            String zoneId,
            double radius,
            int durationTicks
    ) implements MaleniaServerIntent {
        public IndicatorOnlyZone {
            requireIdentifier(zoneId, "zoneId");
            requirePositiveFinite(radius, "radius");
            requirePositive(durationTicks, "durationTicks");
        }
    }

    record Grab(
            double length,
            double width,
            HitSpec grabHit,
            HitSpec impaleHit,
            HitSpec throwHit,
            int impaleDelayTicks,
            int throwDelayTicks
    ) implements MaleniaServerIntent {
        public Grab {
            requirePositiveFinite(length, "length");
            requirePositiveFinite(width, "width");
            Objects.requireNonNull(grabHit, "grabHit");
            Objects.requireNonNull(impaleHit, "impaleHit");
            Objects.requireNonNull(throwHit, "throwHit");
            requirePositive(impaleDelayTicks, "impaleDelayTicks");
            requirePositive(throwDelayTicks, "throwDelayTicks");
            if (throwDelayTicks <= impaleDelayTicks) {
                throw new IllegalArgumentException("throwDelayTicks must be after impaleDelayTicks");
            }
        }

        public HitSpec hit() {
            return grabHit;
        }
    }

    record HitGrabbed(HitSpec hit) implements MaleniaServerIntent {
        public HitGrabbed {
            Objects.requireNonNull(hit, "hit");
        }
    }

    record Release() implements MaleniaServerIntent {
    }

    record WaterfowlBurst(int burstIndex, double width, HitSpec hit) implements MaleniaServerIntent {
        public WaterfowlBurst {
            requireNonNegative(burstIndex, "burstIndex");
            requirePositiveFinite(width, "width");
            Objects.requireNonNull(hit, "hit");
        }
    }

    record PhantomStrike(
            int strikeIndex,
            int phantomCount,
            PhantomAttackKind attackKind,
                double width,
            HitSpec hit
    ) implements MaleniaServerIntent {
        public PhantomStrike {
            requireNonNegative(strikeIndex, "strikeIndex");
            requirePositive(phantomCount, "phantomCount");
            if (strikeIndex > phantomCount) {
                throw new IllegalArgumentException("strikeIndex must not exceed phantomCount");
            }
            Objects.requireNonNull(attackKind, "attackKind");
            requirePositiveFinite(width, "width");
            Objects.requireNonNull(hit, "hit");
            if ((strikeIndex == phantomCount) != (attackKind == PhantomAttackKind.BOSS_DIVE)) {
                throw new IllegalArgumentException("only the final adapted strike may be BOSS_DIVE");
            }
        }
    }

    record HitSpec(
            String hitIdSuffix,
            DamageFormula damage,
            DamageChannel channel,
            double rotBuildup,
            HealProfile healProfile,
            boolean instantGuardEligible,
            int maxHitsPerTarget
    ) {
        public HitSpec {
            requireIdentifier(hitIdSuffix, "hitIdSuffix");
            Objects.requireNonNull(damage, "damage");
            Objects.requireNonNull(channel, "channel");
            requireNonNegativeFinite(rotBuildup, "rotBuildup");
            Objects.requireNonNull(healProfile, "healProfile");
            requirePositive(maxHitsPerTarget, "maxHitsPerTarget");
        }
    }

    enum PhantomAttackKind {
        SWEEP,
        THRUST,
        BOSS_DIVE
    }

    private static OptionalDouble requireOptionalPositive(OptionalDouble value, String name) {
        Objects.requireNonNull(value, name);
        value.ifPresent(number -> requirePositiveFinite(number, name));
        return value;
    }

    private static Optional<String> requireOptionalIdentifier(Optional<String> value, String name) {
        Objects.requireNonNull(value, name);
        value.ifPresent(identifier -> requireIdentifier(identifier, name));
        return value;
    }

    private static void requireIdentifier(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException(name + " must be a lowercase identifier");
        }
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

    private static void requireAtLeast(int value, int minimum, String name) {
        if (value < minimum) {
            throw new IllegalArgumentException(name + " must be at least " + minimum);
        }
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}