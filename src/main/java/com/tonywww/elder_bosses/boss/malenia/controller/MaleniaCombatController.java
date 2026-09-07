package com.tonywww.elder_bosses.boss.malenia.controller;

import com.tonywww.elder_bosses.boss.malenia.action.MaleniaActionCatalog;
import com.tonywww.elder_bosses.boss.malenia.action.MaleniaActionDefinition;
import com.tonywww.elder_bosses.boss.malenia.config.MaleniaCombatConfigSnapshot;
import com.tonywww.elder_bosses.boss.malenia.config.MaleniaSkillConfigSnapshot;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaCombatState;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaPhase;
import com.tonywww.elder_bosses.boss.malenia.execution.MaleniaActionPlan;
import com.tonywww.elder_bosses.boss.malenia.execution.MaleniaServerIntent;
import com.tonywww.elder_bosses.boss.malenia.runtime.MaleniaActionRuntime;
import com.tonywww.elder_bosses.boss.malenia.runtime.MaleniaActionSnapshot;
import com.tonywww.elder_bosses.boss.malenia.runtime.MaleniaCooldownSnapshot;
import com.tonywww.elder_bosses.boss.malenia.runtime.MaleniaCooldowns;
import com.tonywww.elder_bosses.boss.malenia.selection.MaleniaSkillSelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MaleniaCombatController {
    public static final double DEFAULT_PHASE_TWO_AERIAL_WEIGHT_MULTIPLIER = 1.50;
    public static final int AEONIA_POST_ACTION_WINDOW_TICKS = 30;

    private final Host host;
    private final MaleniaCombatConfigSnapshot combatConfig;
    private final MaleniaActionRuntime actionRuntime;
    private final MaleniaCooldowns cooldowns;
    private final MaleniaSkillSelector skillSelector;
    private final Map<MaleniaActionId, MaleniaActionPlan> eventPlans;
    private final Deque<MaleniaActionId> completedActionHistory = new ArrayDeque<>();

    private MaleniaCombatState observedState;
    private UUID targetId;
    private long targetSinceGameTime = -1L;
    private long nextRetargetGameTime;
    private long nextSelectionGameTime;
    private long aeoniaRecoveryUntilGameTime = -1L;
    private long lastGameTime = -1L;

    public MaleniaCombatController(
            Host host,
            MaleniaCombatConfigSnapshot combatConfig,
            MaleniaSkillConfigSnapshot skillConfig,
            MaleniaActionCatalog actionCatalog,
            MaleniaActionRuntime actionRuntime,
            MaleniaCooldowns cooldowns,
            MaleniaSkillSelector skillSelector,
            Map<MaleniaActionId, MaleniaActionPlan> eventPlans
    ) {
        this.host = Objects.requireNonNull(host, "host");
        this.combatConfig = Objects.requireNonNull(combatConfig, "combatConfig");
        Objects.requireNonNull(skillConfig, "skillConfig");
        Objects.requireNonNull(actionCatalog, "actionCatalog");
        this.actionRuntime = Objects.requireNonNull(actionRuntime, "actionRuntime");
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.skillSelector = Objects.requireNonNull(skillSelector, "skillSelector");
        this.eventPlans = validatePlans(eventPlans, actionCatalog);
        if (!skillConfig.equals(actionCatalog.skillConfig())) {
            throw new IllegalArgumentException("skillConfig must match the action catalog snapshot");
        }
    }

    public TickResult tick() {
        long gameTime = host.gameTime();
        validateGameTime(gameTime);
        MaleniaCombatState state = Objects.requireNonNull(host.combatState(), "host combatState");
        MaleniaPhase phase = Objects.requireNonNull(host.phase(), "host phase");
        validatePhaseForState(state, phase);

        boolean stateChanged = state != observedState;
        Optional<MaleniaActionRuntime.ActionEnd> actionEnded = Optional.empty();
        boolean carryOpeningAeonia = stateChanged
            && observedState == MaleniaCombatState.AEONIA_OPENING
            && state == MaleniaCombatState.PHASE_2
            && actionRuntime.definition(gameTime)
            .map(definition -> definition.id() == MaleniaActionId.SCARLET_AEONIA)
            .orElse(false);
        if ((stateChanged && observedState != null && !carryOpeningAeonia)
            || !allowsActiveAction(state)) {
            actionEnded = finishAction(actionRuntime.cancel(gameTime));
        } else {
            actionEnded = finishAction(actionRuntime.advance(gameTime));
        }
        if (actionEnded.filter(end -> end.actionId() == MaleniaActionId.SCARLET_AEONIA)
                .isPresent()) {
            MaleniaActionRuntime.ActionEnd aeoniaEnd = actionEnded.get();
            if (!aeoniaEnd.completed() && observedState == MaleniaCombatState.AEONIA_OPENING) {
                cooldowns.recordCompleted(aeoniaEnd.actionId(), aeoniaEnd.endGameTick());
            }
            settleOpeningAeonia(aeoniaEnd.endGameTick());
        }

        Optional<MaleniaActionSnapshot> actionStarted = Optional.empty();
        if (stateChanged) {
            actionStarted = enterState(state, gameTime);
            observedState = state;
        }

        if (allowsActiveAction(state) && gameTime >= nextRetargetGameTime) {
            selectTarget(gameTime);
            nextRetargetGameTime = nextRetargetTime(gameTime);
        }

        if (actionEnded.isPresent() && isRandomSelectionState(state)) {
            nextSelectionGameTime = nextSelectionTime(gameTime, phase);
        }

        if (isRandomSelectionState(state)
                && actionRuntime.snapshot(gameTime).isEmpty()
            && gameTime >= aeoniaRecoveryUntilGameTime
                && gameTime >= nextSelectionGameTime) {
            actionStarted = observeAndStart(phase, gameTime);
            nextSelectionGameTime = nextSelectionTime(gameTime, phase);
        }

        Optional<MaleniaActionSnapshot> action = actionRuntime.snapshot(gameTime);
        if (action.filter(this::isWaterfowlLockPoint).isPresent()) {
            selectTarget(gameTime);
            action = actionRuntime.snapshot(gameTime);
        }
        List<MaleniaServerIntent> intents = action
                .map(snapshot -> planFor(snapshot.actionId()).intentsAt(snapshot.actionTick()))
                .orElseGet(List::of);
        lastGameTime = gameTime;
        return new TickResult(
                Optional.ofNullable(targetId),
                action,
                actionStarted,
                actionEnded,
                intents
        );
    }

    public TickResult cancel() {
        long gameTime = host.gameTime();
        validateGameTime(gameTime);
        Optional<MaleniaActionRuntime.ActionEnd> actionEnded = finishAction(
                actionRuntime.cancel(gameTime)
        );
        if (actionEnded.filter(end -> end.actionId() == MaleniaActionId.SCARLET_AEONIA)
                .isPresent()
                && host.combatState() == MaleniaCombatState.AEONIA_OPENING) {
            MaleniaActionRuntime.ActionEnd aeoniaEnd = actionEnded.get();
            if (!aeoniaEnd.completed()) {
                cooldowns.recordCompleted(aeoniaEnd.actionId(), aeoniaEnd.endGameTick());
            }
            settleOpeningAeonia(aeoniaEnd.endGameTick());
        }
        clearTarget();
        nextRetargetGameTime = nextRetargetTime(gameTime);
        nextSelectionGameTime = nextSelectionTime(gameTime, host.phase());
        lastGameTime = gameTime;
        return new TickResult(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                actionEnded,
                List.of()
        );
    }

    private boolean isWaterfowlLockPoint(MaleniaActionSnapshot snapshot) {
        if (snapshot.actionId() != MaleniaActionId.WATERFOWL_DANCE) {
            return false;
        }
        return planFor(snapshot.actionId()).intents().stream()
                .anyMatch(scheduled -> scheduled.actionTick() == snapshot.actionTick()
                        && scheduled.intent() instanceof MaleniaServerIntent.LockPoint);
    }

    public void settleRestoredAction(MaleniaActionId actionId) {
        Objects.requireNonNull(actionId, "actionId");
        long gameTime = host.gameTime();
        validateGameTime(gameTime);
        cooldowns.recordCompleted(actionId, gameTime);
        if (actionId == MaleniaActionId.SCARLET_AEONIA) {
            settleOpeningAeonia(gameTime);
        }
        lastGameTime = gameTime;
    }

    private Optional<MaleniaActionSnapshot> enterState(
            MaleniaCombatState state,
            long gameTime
    ) {
        return switch (state) {
            case PHASE_1 -> {
                ensurePhaseStarted(MaleniaPhase.PHASE_ONE, gameTime);
                nextRetargetGameTime = gameTime;
                nextSelectionGameTime = nextSelectionTime(
                        gameTime,
                        MaleniaPhase.PHASE_ONE
                );
                yield Optional.empty();
            }
            case AEONIA_OPENING -> {
                ensurePhaseStarted(MaleniaPhase.PHASE_TWO, gameTime);
                Optional<LivingEntity> target = selectTarget(gameTime);
                nextRetargetGameTime = nextRetargetTime(gameTime);
                yield Optional.of(startAction(
                        MaleniaActionId.SCARLET_AEONIA,
                        MaleniaPhase.PHASE_TWO,
                        target,
                        gameTime
                ));
            }
            case PHASE_2 -> {
                ensurePhaseStarted(MaleniaPhase.PHASE_TWO, gameTime);
                if (!combatConfig.phaseTransition().openingAeonia()) {
                    ensurePhaseTwoOpeningEnded(gameTime);
                }
                nextRetargetGameTime = gameTime;
                nextSelectionGameTime = nextSelectionTime(
                        gameTime,
                        MaleniaPhase.PHASE_TWO
                );
                yield Optional.empty();
            }
            default -> {
                clearTarget();
                nextRetargetGameTime = Long.MAX_VALUE;
                nextSelectionGameTime = Long.MAX_VALUE;
                yield Optional.empty();
            }
        };
    }

    private Optional<MaleniaActionSnapshot> observeAndStart(
            MaleniaPhase phase,
            long gameTime
    ) {
        Optional<LivingEntity> target = currentTarget();
        if (target.isEmpty()) {
            target = selectTarget(gameTime);
            nextRetargetGameTime = nextRetargetTime(gameTime);
        }
        if (target.isEmpty()) {
            return Optional.empty();
        }

        double healthRatio = host.phaseHealthRatio();
        Set<MaleniaActionId> eligibleActions = eligibleActions(phase, gameTime, healthRatio);
        LivingEntity selectedTarget = target.get();
        MaleniaSkillSelector.Context context = new MaleniaSkillSelector.Context(
                phase,
                checkedDistance(selectedTarget),
                healthRatio,
                host.isUsingItem(selectedTarget),
                host.guardingTicks(selectedTarget),
                host.nearbyPlayersWithin(combatConfig.selector().nearbyPlayerRange()),
                host.recentInterrupts(combatConfig.selector().recentInterruptWindowTicks()),
                cooldowns.highThreatRemainingTicks(gameTime) == 0,
                eligibleActions,
                host.phaseTwoAerialWeightMultiplier(),
                combatConfig.general().followRange()
        );
        long seed = stableSeed(gameTime, phase, selectedTarget.getUUID());
        Optional<LivingEntity> actionTarget = target;
        return skillSelector.select(context, seed)
            .map(actionId -> startAction(actionId, phase, actionTarget, gameTime));
    }

    private Optional<LivingEntity> selectTarget(long gameTime) {
        Collection<? extends LivingEntity> suppliedTargets = Objects.requireNonNull(
                host.visibleEligibleTargets(),
                "host visibleEligibleTargets"
        );
        int windowTicks = combatConfig.targeting().recentDamageWindowTicks();
        List<TargetCandidate> candidates = new ArrayList<>(suppliedTargets.size());
        double maximumRecentDamage = 0.0;
        for (LivingEntity target : suppliedTargets) {
            if (target == null || !target.isAlive() || target.isRemoved()) {
                continue;
            }
            if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
                continue;
            }
            double distance = checkedDistance(target);
            if (distance > combatConfig.general().followRange()) {
                continue;
            }
            double recentDamage = host.recentDamage(target, windowTicks);
            if (!Double.isFinite(recentDamage) || recentDamage < 0.0) {
                throw new IllegalArgumentException(
                        "host recent damage must be finite and non-negative"
                );
            }
            candidates.add(new TargetCandidate(target, distance, recentDamage));
            maximumRecentDamage = Math.max(maximumRecentDamage, recentDamage);
        }

        double damageScale = maximumRecentDamage;
        Optional<LivingEntity> selected = candidates.stream()
                .min(Comparator
                .comparingDouble((TargetCandidate candidate) -> targetScore(
                    candidate,
                    damageScale,
                    windowTicks,
                    gameTime
                ))
                        .reversed()
                .thenComparing(candidate -> candidate.target().getUUID()))
            .map(TargetCandidate::target);
        UUID selectedTargetId = selected.map(LivingEntity::getUUID).orElse(null);
        updateTarget(selectedTargetId, gameTime);
        actionRuntime.definition(gameTime)
            .filter(definition -> switch (definition.id()) {
                case WATERFOWL_DANCE, SCARLET_PHANTOMS, SCARLET_AEONIA -> true;
                default -> false;
            })
            .ifPresent(definition -> actionRuntime.retarget(gameTime, selectedTargetId));
        return selected;
    }

        private Optional<LivingEntity> currentTarget() {
        if (targetId == null) {
            return Optional.empty();
        }
        return Objects.requireNonNull(
            host.visibleEligibleTargets(),
            "host visibleEligibleTargets"
        ).stream()
                .filter(Objects::nonNull)
                .filter(target -> target.getUUID().equals(targetId))
                .filter(LivingEntity::isAlive)
                .filter(target -> !target.isRemoved())
                .filter(target -> !(target instanceof Player player)
                        || (!player.isCreative() && !player.isSpectator()))
                .filter(target -> checkedDistance(target)
                        <= combatConfig.general().followRange())
                .map(target -> (LivingEntity) target)
                .findFirst();
        }

        private double targetScore(
            TargetCandidate candidate,
            double maximumRecentDamage,
            int windowTicks,
            long gameTime
        ) {
        LivingEntity target = candidate.target();
        MaleniaCombatConfigSnapshot.Targeting targeting = combatConfig.targeting();
        double distanceScore = 1.0 - Math.min(
            candidate.distance() / combatConfig.general().followRange(),
            1.0
        );
        double recentDamageScore = maximumRecentDamage > 0.0
            ? candidate.recentDamage() / maximumRecentDamage
            : 0.0;
        double score = distanceScore * targeting.distanceWeight()
            + recentDamageScore * targeting.recentDamageWeight()
            + (host.isUsingItem(target) ? targeting.itemUseWeight() : 0.0)
            + (host.recentlyInterruptedBy(target, windowTicks)
            ? targeting.interruptWeight()
            : 0.0);
        if (target.getUUID().equals(targetId)
                && targetSinceGameTime >= 0L
                && gameTime - targetSinceGameTime
                >= combatConfig.multiplayer().sameTargetPenaltyAfterTicks()) {
            score *= combatConfig.multiplayer().sameTargetScoreMultiplier();
        }
        return score;
    }

    private Set<MaleniaActionId> eligibleActions(
            MaleniaPhase phase,
            long gameTime,
            double healthRatio
    ) {
        EnumSet<MaleniaActionId> eligible = EnumSet.noneOf(MaleniaActionId.class);
        for (MaleniaActionId actionId : MaleniaActionId.values()) {
            if (cooldowns.isEligible(actionId, phase, gameTime, healthRatio)) {
                eligible.add(actionId);
            }
        }
        if (eligible.isEmpty()) {
            return Set.of();
        }
        Set<MaleniaActionId> allEligible = Collections.unmodifiableSet(eligible);
        if (completedActionHistory.isEmpty()) {
            return allEligible;
        }
        EnumSet<MaleniaActionId> withoutRecent = EnumSet.copyOf(eligible);
        withoutRecent.removeAll(completedActionHistory);
        return withoutRecent.isEmpty()
                ? allEligible
                : Collections.unmodifiableSet(withoutRecent);
    }

    private MaleniaActionSnapshot startAction(
            MaleniaActionId actionId,
            MaleniaPhase phase,
            Optional<LivingEntity> target,
            long gameTime
    ) {
        UUID selectedTargetId = target.map(LivingEntity::getUUID).orElse(null);
        long seed = stableSeed(gameTime, phase, selectedTargetId);
        MaleniaActionSnapshot started = actionRuntime.start(
                actionId,
                phase,
                gameTime,
                seed,
                selectedTargetId
        );
        cooldowns.recordActionStarted(actionId, phase, gameTime);
        return started;
    }

    private Optional<MaleniaActionRuntime.ActionEnd> finishAction(
            Optional<MaleniaActionRuntime.ActionEnd> actionEnd
    ) {
        actionEnd.filter(MaleniaActionRuntime.ActionEnd::completed).ifPresent(completed -> {
            cooldowns.recordCompleted(completed);
            recordCompletedAction(completed.actionId());
        });
        return actionEnd;
    }

    private void recordCompletedAction(MaleniaActionId actionId) {
        int historyLimit = combatConfig.selector().avoidLastActionCount();
        if (historyLimit == 0) {
            completedActionHistory.clear();
            return;
        }
        while (completedActionHistory.size() >= historyLimit) {
            completedActionHistory.removeFirst();
        }
        completedActionHistory.addLast(actionId);
    }

    private void ensurePhaseStarted(MaleniaPhase phase, long gameTime) {
        MaleniaCooldownSnapshot snapshot = cooldowns.snapshot(gameTime);
        if (!snapshot.waterfowlPhaseGateRemainingTicks().containsKey(phase)) {
            cooldowns.beginPhase(phase, gameTime);
        }
    }

    private void ensurePhaseTwoOpeningEnded(long gameTime) {
        if (cooldowns.snapshot(gameTime).phaseTwoOpeningRemainingTicks().isEmpty()) {
            cooldowns.recordPhaseTwoOpeningEnded(gameTime);
        }
    }

    private void settleOpeningAeonia(long gameTime) {
        ensurePhaseTwoOpeningEnded(gameTime);
        aeoniaRecoveryUntilGameTime = addSaturated(
                gameTime,
                AEONIA_POST_ACTION_WINDOW_TICKS
        );
    }

    private void updateTarget(UUID selectedTargetId, long gameTime) {
        if (!Objects.equals(targetId, selectedTargetId)) {
            targetId = selectedTargetId;
            targetSinceGameTime = selectedTargetId == null ? -1L : gameTime;
        }
    }

    private void clearTarget() {
        targetId = null;
        targetSinceGameTime = -1L;
    }

    private double checkedDistance(LivingEntity target) {
        double distance = host.distanceTo(Objects.requireNonNull(target, "target"));
        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException("host distance must be finite and non-negative");
        }
        return distance;
    }

    private long nextRetargetTime(long gameTime) {
        int interval = combatConfig.multiplayer().retargetIntervalTicks();
        return addSaturated(gameTime, interval);
    }

    private long nextSelectionTime(long gameTime, MaleniaPhase phase) {
        MaleniaCombatConfigSnapshot.Selector selector = combatConfig.selector();
        int minimum = phase == MaleniaPhase.PHASE_ONE
                ? selector.phaseOneIdleMinTicks()
                : selector.phaseTwoIdleMinTicks();
        int maximum = phase == MaleniaPhase.PHASE_ONE
                ? selector.phaseOneIdleMaxTicks()
                : selector.phaseTwoIdleMaxTicks();
        long range = (long) maximum - minimum + 1L;
        long seed = stableSeed(gameTime, phase, targetId) ^ 0x6a09e667f3bcc909L;
        int idleTicks = Math.toIntExact(minimum + Long.remainderUnsigned(seed, range));
        return addSaturated(gameTime, idleTicks);
    }

    private static long addSaturated(long gameTime, int ticks) {
        return gameTime > Long.MAX_VALUE - ticks ? Long.MAX_VALUE : gameTime + ticks;
    }

    private MaleniaActionPlan planFor(MaleniaActionId actionId) {
        MaleniaActionPlan plan = eventPlans.get(actionId);
        if (plan == null) {
            throw new IllegalStateException("missing event plan for " + actionId);
        }
        return plan;
    }

    private void validateGameTime(long gameTime) {
        if (gameTime < 0L) {
            throw new IllegalArgumentException("host gameTime must be non-negative");
        }
        if (gameTime < lastGameTime) {
            throw new IllegalArgumentException("host gameTime must not move backwards");
        }
    }

    private static void validatePhaseForState(MaleniaCombatState state, MaleniaPhase phase) {
        if (state == MaleniaCombatState.PHASE_1 && phase != MaleniaPhase.PHASE_ONE) {
            throw new IllegalStateException("PHASE_1 requires PHASE_ONE");
        }
        if ((state == MaleniaCombatState.AEONIA_OPENING
                || state == MaleniaCombatState.PHASE_2)
                && phase != MaleniaPhase.PHASE_TWO) {
            throw new IllegalStateException(state + " requires PHASE_TWO");
        }
    }

    private static boolean allowsActiveAction(MaleniaCombatState state) {
        return state == MaleniaCombatState.PHASE_1
                || state == MaleniaCombatState.AEONIA_OPENING
                || state == MaleniaCombatState.PHASE_2;
    }

    private static boolean isRandomSelectionState(MaleniaCombatState state) {
        return state == MaleniaCombatState.PHASE_1 || state == MaleniaCombatState.PHASE_2;
    }

    private static long stableSeed(long gameTime, MaleniaPhase phase, UUID targetId) {
        long value = gameTime ^ Long.rotateLeft((long) phase.id(), 21);
        if (targetId != null) {
            value ^= targetId.getMostSignificantBits();
            value ^= Long.rotateLeft(targetId.getLeastSignificantBits(), 32);
        }
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private static Map<MaleniaActionId, MaleniaActionPlan> validatePlans(
            Map<MaleniaActionId, MaleniaActionPlan> plans,
            MaleniaActionCatalog actionCatalog
    ) {
        Objects.requireNonNull(plans, "eventPlans");
        EnumMap<MaleniaActionId, MaleniaActionPlan> copy = new EnumMap<>(MaleniaActionId.class);
        for (MaleniaActionId actionId : MaleniaActionId.values()) {
            MaleniaActionPlan plan = Objects.requireNonNull(
                    plans.get(actionId),
                    "event plan for " + actionId
            );
            MaleniaActionDefinition definition = Objects.requireNonNull(
                    actionCatalog.get(actionId),
                    "action definition for " + actionId
            );
            if (plan.actionId() != actionId) {
                throw new IllegalArgumentException("event plan key does not match " + plan.actionId());
            }
            if (plan.totalTicks() != definition.timeline().totalTicks()) {
                throw new IllegalArgumentException("event plan timeline does not match " + actionId);
            }
            copy.put(actionId, plan);
        }
        if (plans.size() != copy.size()) {
            throw new IllegalArgumentException("event plans contain unsupported keys");
        }
        return Collections.unmodifiableMap(copy);
    }

    public interface Host {
        long gameTime();

        MaleniaCombatState combatState();

        MaleniaPhase phase();

        double phaseHealthRatio();

        Collection<? extends LivingEntity> visibleEligibleTargets();

        double distanceTo(LivingEntity target);

        boolean isUsingItem(LivingEntity target);

        int guardingTicks(LivingEntity target);

        int nearbyPlayersWithin(double range);

        int recentInterrupts(int windowTicks);

        double recentDamage(LivingEntity target, int windowTicks);

        boolean recentlyInterruptedBy(LivingEntity target, int windowTicks);

        double phaseTwoAerialWeightMultiplier();
    }

    private record TargetCandidate(LivingEntity target, double distance, double recentDamage) {
    }

    public record TickResult(
            Optional<UUID> target,
            Optional<MaleniaActionSnapshot> action,
            Optional<MaleniaActionSnapshot> actionStarted,
            Optional<MaleniaActionRuntime.ActionEnd> actionEnded,
            List<MaleniaServerIntent> intents
    ) {
        public TickResult {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(actionStarted, "actionStarted");
            Objects.requireNonNull(actionEnded, "actionEnded");
            intents = List.copyOf(Objects.requireNonNull(intents, "intents"));
        }
    }
}