package com.tonywww.elder_bosses.combat.damage;

public record DamageFormula(double flat, double attackRatio) {
    public DamageFormula {
        if (!Double.isFinite(flat) || flat < 0.0) {
            throw new IllegalArgumentException("flat must be finite and non-negative");
        }
        if (!Double.isFinite(attackRatio) || attackRatio < 0.0) {
            throw new IllegalArgumentException("attackRatio must be finite and non-negative");
        }
    }

    public double evaluate(double attackDamage) {
        if (!Double.isFinite(attackDamage) || attackDamage < 0.0) {
            throw new IllegalArgumentException("attackDamage must be finite and non-negative");
        }
        return flat + attackDamage * attackRatio;
    }
}