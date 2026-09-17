package com.tonywww.elder_bosses.boss.promisedconsort.selection;

import com.tonywww.elder_bosses.boss.promisedconsort.action.PromisedConsortActionCatalog;
import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortCombatConfigSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

public final class PromisedConsortSkillSelector {
    private final PromisedConsortActionCatalog catalog;
    private final PromisedConsortCombatConfigSnapshot.Selector config;

    public PromisedConsortSkillSelector(
            PromisedConsortActionCatalog catalog,
            PromisedConsortCombatConfigSnapshot.Selector config
    ) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.config = Objects.requireNonNull(config, "config");
    }

    public Optional<PromisedConsortActionId> select(Context context, long seed) {
        Objects.requireNonNull(context, "context");
        List<WeightedAction> weighted = new ArrayList<>();
        for (PromisedConsortActionId actionId : context.eligibleActions()) {
            double weight = weight(actionId, context);
            if (Double.isFinite(weight) && weight > 0.0) {
                weighted.add(new WeightedAction(actionId, weight));
            }
        }
        weighted.sort(Comparator.comparingInt(value -> value.actionId().ordinal()));
        double total = weighted.stream().mapToDouble(WeightedAction::weight).sum();
        if (!(total > 0.0)) {
            return Optional.empty();
        }
        double cursor = new Random(seed).nextDouble() * total;
        for (WeightedAction value : weighted) {
            cursor -= value.weight();
            if (cursor <= 0.0) {
                return Optional.of(value.actionId());
            }
        }
        return Optional.of(weighted.get(weighted.size() - 1).actionId());
    }

    private double weight(PromisedConsortActionId actionId, Context context) {
        double weight = catalog.get(actionId).weight() * context.rangedWeights().getOrDefault(actionId, 1.0);
        if (context.usingItem()
                && context.distance() >= config.itemUsePunishMinRange()
                && context.distance() <= config.itemUsePunishMaxRange()
                && (actionId == PromisedConsortActionId.LION_CLAW
                || actionId == PromisedConsortActionId.GRAVITY_DIVE)) {
            weight *= config.itemUseWeightMultiplier();
        }
        if (context.distance() > 14.0) {
            if (actionId == PromisedConsortActionId.GRAVITY_METEOR) {
                weight *= config.rangedWeightMultiplier();
            } else if (actionId == PromisedConsortActionId.GRAVITY_DIVE
                    || actionId == PromisedConsortActionId.SPIRAL_ASSAULT
                    || actionId == PromisedConsortActionId.LIGHTSPEED_DASH) {
                weight *= 1.5;
            }
        }
        if (context.nearbyPlayers() >= 2
                && (actionId == PromisedConsortActionId.STOMP
                || actionId == PromisedConsortActionId.STARCALLER_CRY)) {
            weight *= config.crowdWeightMultiplier();
        }
        if (context.rightRearTicks() > 50
                && actionId == PromisedConsortActionId.CROSS_SLASH) {
            weight *= 1.5;
        }
        if (!context.previousActionHit() && isMediumThreat(actionId)) {
            weight *= config.missRecoveryWeightMultiplier();
        }
        return weight;
    }

    private static boolean isMediumThreat(PromisedConsortActionId actionId) {
        return switch (actionId) {
            case L_COMBO_CROSS, L_COMBO_BLOODFLAME, R_COMBO_CROSS,
                    R_COMBO_LEFT_TWIN, STOMP, CROSS_SLASH -> true;
            default -> false;
        };
    }

    public record Context(
            PromisedConsortPhase phase,
            double distance,
            boolean usingItem,
            int nearbyPlayers,
            int rightRearTicks,
            boolean previousActionHit,
            Set<PromisedConsortActionId> eligibleActions,
            java.util.Map<PromisedConsortActionId, Double> rangedWeights
    ) {
        public Context(PromisedConsortPhase phase, double distance, boolean usingItem, int nearbyPlayers,
                int rightRearTicks, boolean previousActionHit, Set<PromisedConsortActionId> eligibleActions) {
            this(phase,distance,usingItem,nearbyPlayers,rightRearTicks,previousActionHit,eligibleActions,java.util.Map.of());
        }
        public Context {
            Objects.requireNonNull(phase, "phase");
            if (!Double.isFinite(distance) || distance < 0.0 || nearbyPlayers < 0
                    || rightRearTicks < 0) {
                throw new IllegalArgumentException("invalid selector context");
            }
            eligibleActions = Set.copyOf(eligibleActions);
            rangedWeights = java.util.Map.copyOf(rangedWeights);
        }
    }

    private record WeightedAction(PromisedConsortActionId actionId, double weight) {
    }
}
