package com.tonywww.elder_bosses.client.render;

public final class SecondaryMotionSpring {
    private final Axis pitch = new Axis();
    private final Axis roll = new Axis();
    private double lastFrame = Double.NaN;
    private Offset offset = new Offset(0, 0);

    public Offset sample(double frame, double speed, double turn, double verticalSpeed, double strength) {
        if (!Double.isFinite(frame)) return reset();
        if (frame == lastFrame) return offset;
        double elapsed = frame - lastFrame;
        if (!Double.isFinite(lastFrame) || elapsed < 0 || elapsed > 5) {
            reset();
            lastFrame = frame;
            return offset;
        }
        lastFrame = frame;
        double amount = bounded(strength, 0, 1);
        double pitchTarget = bounded(bounded(speed, 0, 0.7) * 9 - bounded(verticalSpeed, -1, 1) * 3.5, -5, 6) * amount;
        double rollTarget = bounded(turn, -20, 20) * -0.25 * amount;
        pitch.advance(pitchTarget, elapsed, 0.7);
        roll.advance(rollTarget, elapsed, 0.55);
        offset = new Offset(bounded(pitch.value, -6, 6), bounded(roll.value, -5, 5));
        return offset;
    }

    private Offset reset() {
        pitch.value = pitch.velocity = 0;
        roll.value = roll.velocity = 0;
        lastFrame = Double.NaN;
        offset = new Offset(0, 0);
        return offset;
    }

    private static double bounded(double value, double minimum, double maximum) {
        return Double.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : 0;
    }

    private static final class Axis {
        private double value;
        private double velocity;

        private void advance(double target, double elapsed, double frequency) {
            double displacement = value - target;
            double impulse = velocity + frequency * displacement;
            double decay = Math.exp(-frequency * elapsed);
            value = target + (displacement + impulse * elapsed) * decay;
            velocity = (velocity - frequency * impulse * elapsed) * decay;
        }
    }

    public record Offset(double pitchDegrees, double rollDegrees) {}
}