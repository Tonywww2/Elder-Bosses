package com.tonywww.elder_bosses.boss.malenia.selection;

import com.tonywww.elder_bosses.boss.malenia.config.MaleniaCombatConfigSnapshot;
import com.tonywww.elder_bosses.boss.malenia.config.MaleniaSkillConfigSnapshot;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaPhase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class MaleniaSkillSelector {
    private static final double BASIC_MELEE_RANGE_TOLERANCE = 0.5;
    private static final double MEDIUM_RANGE_MINIMUM = 6.0;
    private static final List<MaleniaActionId> STABLE_ACTION_ORDER = List.of(
            MaleniaActionId.SINGLE_SLASH,
            MaleniaActionId.DOUBLE_SLASH,
            MaleniaActionId.RAPID_SLASHES,
            MaleniaActionId.RUNNING_SLASH,
            MaleniaActionId.UPWARD_COMBO,
            MaleniaActionId.KICK,
            MaleniaActionId.THRUST,
            MaleniaActionId.GRAB_IMPALE,
            MaleniaActionId.RETREAT_SLASH,
            MaleniaActionId.WATERFOWL_DANCE,
            MaleniaActionId.SCARLET_AEONIA,
            MaleniaActionId.SCARLET_PLUNGE,
            MaleniaActionId.FLYING_SLASH,
            MaleniaActionId.SCARLET_PHANTOMS,
            MaleniaActionId.WINGED_SWEEP
    );

    static {
        if (!EnumSet.copyOf(STABLE_ACTION_ORDER).equals(EnumSet.allOf(MaleniaActionId.class))) {
            throw new IllegalStateException("stable action order must cover every Malenia action");
        }
    }

    private final MaleniaSkillConfigSnapshot config;
    private final MaleniaCombatConfigSnapshot.Selector selectorConfig;

    public MaleniaSkillSelector(
            MaleniaSkillConfigSnapshot config,
            MaleniaCombatConfigSnapshot.Selector selectorConfig
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.selectorConfig = Objects.requireNonNull(selectorConfig, "selectorConfig");
    }

    public Optional<MaleniaActionId> select(Context context, long seed) {
        Objects.requireNonNull(context, "context");

        if (shouldPreferRetreat(context)) {
            return Optional.of(MaleniaActionId.RETREAT_SLASH);
        }

        List<WeightedAction> candidates = new ArrayList<>(STABLE_ACTION_ORDER.size());
        double maximumWeight = 0.0;
        for (MaleniaActionId actionId : STABLE_ACTION_ORDER) {
            if (!isCandidate(actionId, context)) {
                continue;
            }
            double weight = adjustedWeight(actionId, context);
            if (weight == 0.0) {
                continue;
            }
            if (!Double.isFinite(weight)) {
                throw new IllegalArgumentException("adjusted action weight must be finite: " + actionId);
            }
            candidates.add(new WeightedAction(actionId, weight));
            maximumWeight = Math.max(maximumWeight, weight);
        }

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        double normalizedTotal = 0.0;
        for (WeightedAction candidate : candidates) {
            normalizedTotal += candidate.weight() / maximumWeight;
        }

        double selectionPoint = unitInterval(seed) * normalizedTotal;
        for (WeightedAction candidate : candidates) {
            selectionPoint -= candidate.weight() / maximumWeight;
            if (selectionPoint < 0.0) {
                return Optional.of(candidate.id());
            }
        }
        return Optional.of(candidates.get(candidates.size() - 1).id());
    }

    private boolean shouldPreferRetreat(Context context) {
        return context.distance() < selectorConfig.waterfowlRetreatMaxRange()
            && isCandidateExceptDistance(MaleniaActionId.WATERFOWL_DANCE, context)
            && isCandidateExceptDistance(MaleniaActionId.RETREAT_SLASH, context);
    }

    private boolean isCandidate(MaleniaActionId actionId, Context context) {
        return isCandidateExceptDistance(actionId, context)
            && isWithinActionRange(actionId, context);
    }

    private boolean isCandidateExceptDistance(MaleniaActionId actionId, Context context) {
        ConfiguredSkill skill = configuredSkill(actionId);
        return skill.enabled()
                && skill.weight() > 0.0
                && context.cooldownEligibleActions().contains(actionId)
                && isAvailableInPhase(actionId, context.phase())
                && (context.highThreatSlotAvailable() || !isHighThreat(actionId))
                && isWithinPhaseHealthGate(actionId, context);
    }

    private boolean isWithinActionRange(MaleniaActionId actionId, Context context) {
        double distance = context.distance();
        return switch (actionId) {
                case SINGLE_SLASH -> distance <= range(actionId, config.singleSlash().range())
                    + BASIC_MELEE_RANGE_TOLERANCE;
                case DOUBLE_SLASH -> distance <= range(actionId, config.doubleSlash().range())
                    + BASIC_MELEE_RANGE_TOLERANCE;
                case RAPID_SLASHES -> distance <= range(actionId, config.rapidSlashes().range())
                    + BASIC_MELEE_RANGE_TOLERANCE;
                case UPWARD_COMBO -> distance <= range(actionId, config.upwardCombo().range())
                    + BASIC_MELEE_RANGE_TOLERANCE;
                case RETREAT_SLASH -> distance <= range(actionId, config.retreatSlash().range())
                    + BASIC_MELEE_RANGE_TOLERANCE;
                case RUNNING_SLASH -> distance <= range(actionId, config.runningSlash().range());
                case KICK -> distance <= range(actionId, config.kick().range());
                case THRUST -> distance <= range(actionId, config.thrust().range());
                case GRAB_IMPALE -> distance <= range(actionId, config.grabImpale().range());
                case WATERFOWL_DANCE -> distance >= config.waterfowlDance().minimumStartRange()
                    && distance <= context.followRange();
                case SCARLET_AEONIA, SCARLET_PHANTOMS -> distance >= MEDIUM_RANGE_MINIMUM
                    && distance <= context.followRange();
                case SCARLET_PLUNGE -> distance <= range(actionId, config.scarletPlunge().range());
                case FLYING_SLASH -> distance <= range(actionId, config.flyingSlash().range());
                case WINGED_SWEEP -> distance <= range(actionId, config.wingedSweep().range());
            };
            }

    private double range(MaleniaActionId actionId, double value) {
        return config.tuning(actionId).scaleRange(value);
    }

    private boolean isWithinPhaseHealthGate(MaleniaActionId actionId, Context context) {
        return actionId != MaleniaActionId.WATERFOWL_DANCE
                || context.phase() != MaleniaPhase.PHASE_ONE
                || context.phaseHealthRatio()
                <= config.waterfowlDance().phaseOneFirstHealthRatio();
    }

    private double adjustedWeight(MaleniaActionId actionId, Context context) {
        double weight = configuredSkill(actionId).weight();
        if (context.targetUsingConsumable()
                && context.distance() >= selectorConfig.itemUsePunishMinRange()
                && context.distance() <= selectorConfig.itemUsePunishMaxRange()
                && (actionId == MaleniaActionId.RUNNING_SLASH
                || actionId == MaleniaActionId.THRUST)) {
            weight *= selectorConfig.itemUseWeightMultiplier();
        }
        if (context.targetGuardingTicks() > selectorConfig.shieldKickAfterTicks()) {
            if (actionId == MaleniaActionId.KICK) {
                weight *= selectorConfig.shieldKickWeightMultiplier();
            } else if (actionId == MaleniaActionId.GRAB_IMPALE) {
                weight *= selectorConfig.shieldGrabWeightMultiplier();
            }
        }
        if (context.nearbyPlayers() >= selectorConfig.nearbyPlayerCountThreshold()
                && actionId == MaleniaActionId.RETREAT_SLASH) {
            weight *= selectorConfig.nearbyPlayerRetreatWeightMultiplier();
        }
        if (context.recentInterrupts() >= selectorConfig.recentInterruptCountThreshold()
                && (actionId == MaleniaActionId.KICK
                || actionId == MaleniaActionId.RETREAT_SLASH
                || isHyperArmorAction(actionId))) {
            weight *= selectorConfig.recentInterruptWeightMultiplier();
        }
        if (context.distance() > selectorConfig.longRangeThreshold()
                && actionId == MaleniaActionId.RUNNING_SLASH) {
            weight *= selectorConfig.longRangeRunningSlashWeightMultiplier();
        }
        if (context.phase() == MaleniaPhase.PHASE_TWO && isAerial(actionId)) {
            weight *= context.phaseTwoAerialWeightMultiplier();
        }
        return weight;
    }

    private ConfiguredSkill configuredSkill(MaleniaActionId actionId) {
        return switch (actionId) {
            case SINGLE_SLASH -> new ConfiguredSkill(
                    config.singleSlash().enabled(), config.singleSlash().weight());
            case DOUBLE_SLASH -> new ConfiguredSkill(
                    config.doubleSlash().enabled(), config.doubleSlash().weight());
            case RAPID_SLASHES -> new ConfiguredSkill(
                    config.rapidSlashes().enabled(), config.rapidSlashes().weight());
            case RUNNING_SLASH -> new ConfiguredSkill(
                    config.runningSlash().enabled(), config.runningSlash().weight());
            case UPWARD_COMBO -> new ConfiguredSkill(
                    config.upwardCombo().enabled(), config.upwardCombo().weight());
            case KICK -> new ConfiguredSkill(config.kick().enabled(), config.kick().weight());
            case THRUST -> new ConfiguredSkill(config.thrust().enabled(), config.thrust().weight());
            case GRAB_IMPALE -> new ConfiguredSkill(
                    config.grabImpale().enabled(), config.grabImpale().weight());
            case RETREAT_SLASH -> new ConfiguredSkill(
                    config.retreatSlash().enabled(), config.retreatSlash().weight());
            case WATERFOWL_DANCE -> new ConfiguredSkill(
                    config.waterfowlDance().enabled(), config.waterfowlDance().weight());
            case SCARLET_AEONIA -> new ConfiguredSkill(
                    config.scarletAeonia().enabled(), config.scarletAeonia().weight());
            case SCARLET_PLUNGE -> new ConfiguredSkill(
                    config.scarletPlunge().enabled(), config.scarletPlunge().weight());
            case FLYING_SLASH -> new ConfiguredSkill(
                    config.flyingSlash().enabled(), config.flyingSlash().weight());
            case SCARLET_PHANTOMS -> new ConfiguredSkill(
                    config.scarletPhantoms().enabled(), config.scarletPhantoms().weight());
            case WINGED_SWEEP -> new ConfiguredSkill(
                    config.wingedSweep().enabled(), config.wingedSweep().weight());
        };
    }

    private boolean isHyperArmorAction(MaleniaActionId actionId) {
        return switch (actionId) {
            case KICK -> config.kick().hyperArmor();
            case WATERFOWL_DANCE, SCARLET_AEONIA -> true;
            case SCARLET_PHANTOMS -> config.scarletPhantoms().hyperArmor();
            default -> false;
        };
    }

    private static boolean isAvailableInPhase(MaleniaActionId actionId, MaleniaPhase phase) {
        return phase == MaleniaPhase.PHASE_TWO || switch (actionId) {
            case SINGLE_SLASH,
                    DOUBLE_SLASH,
                    RAPID_SLASHES,
                    RUNNING_SLASH,
                    UPWARD_COMBO,
                    KICK,
                    THRUST,
                    GRAB_IMPALE,
                    RETREAT_SLASH,
                    WATERFOWL_DANCE -> true;
            case SCARLET_AEONIA,
                    SCARLET_PLUNGE,
                    FLYING_SLASH,
                    SCARLET_PHANTOMS,
                    WINGED_SWEEP -> false;
        };
    }

    private static boolean isHighThreat(MaleniaActionId actionId) {
        return switch (actionId) {
            case WATERFOWL_DANCE, SCARLET_AEONIA, SCARLET_PHANTOMS -> true;
            default -> false;
        };
    }

    private static boolean isAerial(MaleniaActionId actionId) {
        return switch (actionId) {
            case WATERFOWL_DANCE,
                    SCARLET_AEONIA,
                    SCARLET_PLUNGE,
                    FLYING_SLASH,
                    SCARLET_PHANTOMS -> true;
            default -> false;
        };
    }

    private static double unitInterval(long seed) {
        long mixed = seed;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        mixed ^= mixed >>> 31;
        return (mixed >>> 11) * 0x1.0p-53;
    }

    public record Context(
            MaleniaPhase phase,
            double distance,
            double phaseHealthRatio,
            boolean targetUsingConsumable,
            int targetGuardingTicks,
            int nearbyPlayers,
            int recentInterrupts,
            boolean highThreatSlotAvailable,
            Set<MaleniaActionId> cooldownEligibleActions,
                double phaseTwoAerialWeightMultiplier,
                double followRange
    ) {
        public Context {
            Objects.requireNonNull(phase, "phase");
            if (!Double.isFinite(distance) || distance < 0.0) {
                throw new IllegalArgumentException("distance must be finite and non-negative");
            }
            if (!Double.isFinite(phaseHealthRatio)
                    || phaseHealthRatio < 0.0
                    || phaseHealthRatio > 1.0) {
                throw new IllegalArgumentException("phaseHealthRatio must be between zero and one");
            }
            if (targetGuardingTicks < 0) {
                throw new IllegalArgumentException("targetGuardingTicks must be non-negative");
            }
            if (nearbyPlayers < 0) {
                throw new IllegalArgumentException("nearbyPlayers must be non-negative");
            }
            if (recentInterrupts < 0) {
                throw new IllegalArgumentException("recentInterrupts must be non-negative");
            }
            Objects.requireNonNull(cooldownEligibleActions, "cooldownEligibleActions");
            if (cooldownEligibleActions.isEmpty()) {
                cooldownEligibleActions = Set.of();
            } else {
                cooldownEligibleActions = Collections.unmodifiableSet(
                        EnumSet.copyOf(cooldownEligibleActions)
                );
            }
            if (!Double.isFinite(phaseTwoAerialWeightMultiplier)
                    || phaseTwoAerialWeightMultiplier < 1.0) {
                throw new IllegalArgumentException(
                        "phaseTwoAerialWeightMultiplier must be finite and at least one"
                );
            }
            if (!Double.isFinite(followRange) || followRange <= 0.0) {
                throw new IllegalArgumentException("followRange must be finite and positive");
            }
        }
    }

    private record ConfiguredSkill(boolean enabled, double weight) {
    }

    private record WeightedAction(MaleniaActionId id, double weight) {
    }
}