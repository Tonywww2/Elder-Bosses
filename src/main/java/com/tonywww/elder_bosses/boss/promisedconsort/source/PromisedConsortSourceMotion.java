package com.tonywww.elder_bosses.boss.promisedconsort.source;

import java.util.List;

/** HKX extractedMotion only. Authored Master/Root motion stays in the local pose. */
public record PromisedConsortSourceMotion(long durationMicros, List<Sample> samples) {
    public record Sample(double x, double y, double z, double yawRadians) {
        public Sample {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || !Double.isFinite(yawRadians)) throw new IllegalArgumentException("Invalid source motion");
        }
        private Sample lerp(Sample other, double amount) {
            return new Sample(x+(other.x-x)*amount, y+(other.y-y)*amount,
                    z+(other.z-z)*amount, yawRadians+(other.yawRadians-yawRadians)*amount);
        }
    }
    public record Displacement(double x, double y, double z) {}

    public PromisedConsortSourceMotion {
        samples = List.copyOf(samples);
        if (durationMicros < 0 || samples.size() == 1 || durationMicros == 0 && !samples.isEmpty())
            throw new IllegalArgumentException("Invalid extracted motion duration/samples");
    }

    public Sample sample(long sourceMicros) {
        if (sourceMicros < 0) throw new IllegalArgumentException("Negative source motion time");
        if (samples.isEmpty()) return new Sample(0,0,0,0);
        double position = Math.min(sourceMicros, durationMicros)/(double)durationMicros*(samples.size()-1);
        int first = (int)Math.floor(position), next = Math.min(first+1,samples.size()-1);
        return samples.get(first).lerp(samples.get(next),position-first);
    }

    /** Canonical yaw 0 follows +Z; same X/Z basis as GeoEntityRenderer's reflected model. */
    public Displacement displacement(long sourceMicros, double actorYawDegrees) {
        if (!Double.isFinite(actorYawDegrees)) throw new IllegalArgumentException("Invalid actor yaw");
        Sample origin=sample(0), point=sample(sourceMicros);
        double x=point.x-origin.x, y=point.y-origin.y, z=-(point.z-origin.z);
        double yaw=Math.toRadians(actorYawDegrees), sine=Math.sin(yaw), cosine=Math.cos(yaw);
        return new Displacement(x*cosine-z*sine,y,x*sine+z*cosine);
    }

    /** Havok hkaDefaultAnimatedReferenceFrame samples store XYZ + signed Y rotation. */
    public double yawDeltaDegrees(long sourceMicros) {
        return Math.toDegrees(sample(sourceMicros).yawRadians()-sample(0).yawRadians());
    }
}
