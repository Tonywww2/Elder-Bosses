package com.tonywww.elder_bosses.client.render;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PursuitGait {
    private double lastFrame = Double.NaN;
    private double lastX;
    private double lastY;
    private double lastZ;
    private double phase;
    private Sample sample = new Sample(0, 0);

    public Sample sample(double frame, double horizontal, double height, double depth, boolean enabled) {
        if (frame == lastFrame) return sample;
        double elapsed = frame - lastFrame;
        double distance = Math.hypot(horizontal - lastX, depth - lastZ);
        boolean continuous = Double.isFinite(elapsed) && elapsed > 0 && elapsed <= 5
                && distance <= 1.5 && Math.abs(height - lastY) <= 0.5;
        lastFrame = frame;
        lastX = horizontal;
        lastY = height;
        lastZ = depth;
        if (!enabled || !continuous) {
            sample = new Sample(phase * 40, 0);
            return sample;
        }
        phase = (phase + distance / 1.38) % 1;
        double target = Math.min(1, distance / elapsed / 0.12);
        double weight = sample.weight() + Math.max(-elapsed / 2, Math.min(elapsed / 2, target - sample.weight()));
        sample = new Sample(phase * 40, weight);
        return sample;
    }

    public static double authoredPoseWeight(double requested, double firstFootHeight, double secondFootHeight, double legLength) {
        if (!Double.isFinite(requested) || !Double.isFinite(firstFootHeight) || !Double.isFinite(secondFootHeight)
                || !Double.isFinite(legLength) || legLength <= 0) return 0;
        double difference = Math.abs(firstFootHeight - secondFootHeight) / legLength;
        double blend = Math.max(0, Math.min(1, (difference - 0.15) / 0.20));
        return Math.max(0, Math.min(1, requested)) * (1 - blend * blend * (3 - 2 * blend));
    }

    public static Quaternionf supportCorrection(Vector3f previous, Vector3f current, Vector3f up) {
        float length = current.length();
        if (length < 0.0001F || current.dot(up) >= previous.dot(up)) return new Quaternionf();
        float height = Math.max(-length, Math.min(length, previous.dot(up)));
        Vector3f horizontal = new Vector3f(current).sub(new Vector3f(up).mul(current.dot(up)));
        if (horizontal.lengthSquared() < 0.0001F) return new Quaternionf();
        Vector3f desired = horizontal.normalize().mul((float) Math.sqrt(Math.max(0, length * length - height * height)))
                .add(new Vector3f(up).mul(height));
        return new Quaternionf().rotationTo(new Vector3f(current).normalize(), desired.normalize());
    }

    public record Sample(double tick, double weight) {
    }

    public record RotationTrack(double[] ticks, float[][] angles) {
        public float[] at(double tick) {
            for (int index = 1; index < ticks.length; index++) {
                if (tick > ticks[index]) continue;
                double weight = Math.max(0, Math.min(1, (tick - ticks[index - 1]) / (ticks[index] - ticks[index - 1])));
                float[] result = new float[3];
                for (int axis = 0; axis < 3; axis++) result[axis] = (float) (angles[index - 1][axis]
                        + (angles[index][axis] - angles[index - 1][axis]) * weight);
                return result;
            }
            return angles[angles.length - 1].clone();
        }
    }
}