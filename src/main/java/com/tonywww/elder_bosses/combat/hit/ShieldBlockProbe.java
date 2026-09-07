package com.tonywww.elder_bosses.combat.hit;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public final class ShieldBlockProbe {
    private static final ThreadLocal<Deque<Attempt>> ACTIVE_ATTEMPTS =
            ThreadLocal.withInitial(ArrayDeque::new);

    public enum Policy {
        OBSERVE,
        OVERRIDE_BLOCK,
        SUPPRESS_SHIELD_DAMAGE
    }

    private ShieldBlockProbe() {
    }

    public static Scope begin(LivingEntity target, DamageSource source) {
        return begin(target, source, Policy.OBSERVE);
    }

    public static Scope begin(
            LivingEntity target,
            DamageSource source,
            Policy policy
    ) {
        Attempt attempt = new Attempt(
                Objects.requireNonNull(target, "target"),
                Objects.requireNonNull(source, "source"),
                Objects.requireNonNull(policy, "policy")
        );
        ACTIVE_ATTEMPTS.get().push(attempt);
        return new Scope(attempt);
    }

    public static Policy record(
            LivingEntity target,
            DamageSource source,
            float originalBlockedDamage
    ) {
        for (Attempt attempt : ACTIVE_ATTEMPTS.get()) {
            if (attempt.target == target && attempt.source == source) {
                if (originalBlockedDamage > 0.0F) {
                    attempt.originalBlockedDamage = Math.max(
                            attempt.originalBlockedDamage,
                            originalBlockedDamage
                    );
                }
                return attempt.policy;
            }
        }
        return Policy.OBSERVE;
    }

    public static void recordActual(
            LivingEntity target,
            DamageSource source,
            float blockedDamage
    ) {
        for (Attempt attempt : ACTIVE_ATTEMPTS.get()) {
            if (attempt.target == target && attempt.source == source) {
                if (blockedDamage > 0.0F) {
                    attempt.blockedDamage = Math.max(attempt.blockedDamage, blockedDamage);
                }
                return;
            }
        }
    }

    private static final class Attempt {
        private final LivingEntity target;
        private final DamageSource source;
        private final Policy policy;
        private float originalBlockedDamage;
        private float blockedDamage;

        private Attempt(
                LivingEntity target,
                DamageSource source,
                Policy policy
        ) {
            this.target = target;
            this.source = source;
            this.policy = policy;
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Attempt attempt;
        private boolean closed;

        private Scope(Attempt attempt) {
            this.attempt = attempt;
        }

        public boolean blocked() {
            return attempt.originalBlockedDamage > 0.0F;
        }

        public float actualBlockedDamage() {
            return attempt.blockedDamage;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            Deque<Attempt> attempts = ACTIVE_ATTEMPTS.get();
            if (attempts.isEmpty() || attempts.pop() != attempt) {
                attempts.clear();
                ACTIVE_ATTEMPTS.remove();
                throw new IllegalStateException("shield block probe scopes closed out of order");
            }
            if (attempts.isEmpty()) {
                ACTIVE_ATTEMPTS.remove();
            }
        }
    }
}