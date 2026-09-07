package com.tonywww.elder_bosses.combat.damage;

import java.util.Objects;

public record DamageComponent(DamageChannel channel, DamageFormula formula, double sourceMultiplier) {
    public DamageComponent {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(formula, "formula");
        if (!Double.isFinite(sourceMultiplier) || sourceMultiplier < 0.0) {
            throw new IllegalArgumentException("sourceMultiplier must be finite and non-negative");
        }
    }

    public double evaluate(double attackDamage) {
        return formula.evaluate(attackDamage) * sourceMultiplier;
    }
}