package com.tonywww.elder_bosses.combat.status;

import java.util.Objects;
import java.util.OptionalDouble;

public record ScarletRotTickResult(
        int damagePulses,
        OptionalDouble damagePerPulse,
        boolean stateChanged
) {
    public ScarletRotTickResult {
        if (damagePulses < 0) {
            throw new IllegalArgumentException("damagePulses must be non-negative");
        }
        Objects.requireNonNull(damagePerPulse, "damagePerPulse");
        if ((damagePulses == 0) != damagePerPulse.isEmpty()) {
            throw new IllegalArgumentException(
                    "damagePerPulse must be present exactly when damagePulses is positive"
            );
        }
        if (damagePerPulse.isPresent()) {
            double damage = damagePerPulse.getAsDouble();
            if (!Double.isFinite(damage) || damage < 0.0) {
                throw new IllegalArgumentException(
                        "damagePerPulse must be finite and non-negative"
                );
            }
        }
    }

    public static ScarletRotTickResult unchanged() {
        return new ScarletRotTickResult(0, OptionalDouble.empty(), false);
    }
}