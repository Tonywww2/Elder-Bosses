package com.tonywww.elder_bosses.boss.promisedconsort.controller;

import com.tonywww.elder_bosses.boss.promisedconsort.action.PromisedConsortActionCatalog;
import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortCombatConfigSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortCombatState;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase;
import com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortActionRuntime;
import com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortActionSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortCooldowns;
import com.tonywww.elder_bosses.boss.promisedconsort.selection.PromisedConsortSkillSelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PromisedConsortCombatController {
    private static final int MINIMUM_IDLE_TICKS = 6;
    private static final int MAXIMUM_IDLE_TICKS = 14;

    private final Host host;
    private final PromisedConsortCombatConfigSnapshot config;
    private final PromisedConsortActionCatalog catalog;
    private final PromisedConsortActionRuntime runtime;
    private final PromisedConsortCooldowns cooldowns;
    private final PromisedConsortSkillSelector selector;
    private final Deque<PromisedConsortActionId> history = new ArrayDeque<>();

    private UUID targetId;
    private long targetSinceTick = -1L;
    private long nextRetargetTick;
    private long nextSelectionTick;
    private long lastTick = -1L;

    public PromisedConsortCombatController(
            Host host,
            PromisedConsortCombatConfigSnapshot config,
            PromisedConsortActionCatalog catalog,
            PromisedConsortActionRuntime runtime,
            PromisedConsortCooldowns cooldowns,
            PromisedConsortSkillSelector selector
    ) {
        this.host = Objects.requireNonNull(host, "host");
        this.config = Objects.requireNonNull(config, "config");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.selector = Objects.requireNonNull(selector, "selector");
    }

    public TickResult tick() {
        long gameTick = host.gameTime();
        if (gameTick < lastTick) {
            throw new IllegalStateException("game time moved backwards");
        }
        Optional<PromisedConsortActionRuntime.ActionEnd> ended = runtime.advance(gameTick);
        ended.filter(PromisedConsortActionRuntime.ActionEnd::completed).ifPresent(this::recordCompleted);

        if (!isCombatState(host.combatState())) {
            runtime.cancel(gameTick);
            clearTarget();
            lastTick = gameTick;
            return new TickResult(Optional.empty(), Optional.empty(), ended);
        }

        if (gameTick >= nextRetargetTick || currentTarget().isEmpty()) {
            selectTarget(gameTick);
            nextRetargetTick = saturatedAdd(gameTick, config.multiplayer().retargetIntervalTicks());
        }

        if (ended.isPresent()) {
            Optional<PromisedConsortActionSnapshot> branch = branchAfter(ended.get(), gameTick);
            if (branch.isPresent()) {
                lastTick = gameTick;
                return new TickResult(Optional.ofNullable(targetId), branch, ended);
            }
            int recovery = host.consumeForcedRecoveryTicks();
            nextSelectionTick = saturatedAdd(
                    gameTick,
                    recovery > 0 ? recovery : idleTicks(gameTick)
            );
        }

        if (!runtime.isActive() && gameTick >= nextSelectionTick) {
            startSelectedAction(gameTick);
        }

        Optional<PromisedConsortActionSnapshot> snapshot = runtime.snapshot(gameTick);
        lastTick = gameTick;
        return new TickResult(Optional.ofNullable(targetId), snapshot, ended);
    }

    public void cancel() {
        runtime.cancel(host.gameTime());
    }

    public Optional<PromisedConsortActionSnapshot> force(
            PromisedConsortActionId actionId,
            PromisedConsortPhase phase
    ) {
        runtime.cancel(host.gameTime());
        Optional<LivingEntity> target = currentTarget().or(() -> selectTarget(host.gameTime()));
        PromisedConsortActionSnapshot snapshot = runtime.start(
                actionId,
                phase,
                host.gameTime(),
                stableSeed(host.gameTime(), actionId, targetId),
                target.map(LivingEntity::getUUID).orElse(null)
        );
        cooldowns.recordStarted(actionId, host.gameTime());
        return Optional.of(snapshot);
    }

    public void clearCooldowns() {
        cooldowns.clear();
        nextSelectionTick = host.gameTime();
    }

    public PromisedConsortCooldowns cooldowns() {
        return cooldowns;
    }

    private Optional<PromisedConsortActionSnapshot> startSelectedAction(long gameTick) {
        Optional<LivingEntity> target = currentTarget();
        if (target.isEmpty()) {
            return Optional.empty();
        }
        EnumSet<PromisedConsortActionId> eligible = EnumSet.noneOf(PromisedConsortActionId.class);
        for (PromisedConsortActionId actionId : PromisedConsortActionId.values()) {
            if (actionId == PromisedConsortActionId.LION_CLAW_DOUBLE
                    || actionId == PromisedConsortActionId.CONSORT_METEOR
                    && !host.canSelectWeightedMeteor(gameTick)
                    || !cooldowns.isEligible(actionId, host.phase(), gameTick)) {
                continue;
            }
            eligible.add(actionId);
        }
        if (!history.isEmpty() && eligible.size() > history.size()) {
            eligible.removeAll(history);
        }
        LivingEntity selected = target.get();
        PromisedConsortSkillSelector.Context context = new PromisedConsortSkillSelector.Context(
                host.phase(),
                host.distanceTo(selected),
                host.isUsingItem(selected),
                host.nearbyPlayers(4.0),
                host.rightRearTicks(selected),
                host.previousActionHit(),
                eligible
        );
        Optional<PromisedConsortActionId> selectedAction = selector.select(
            context,
            stableSeed(gameTick, null, targetId)
        );
        if (selectedAction.isEmpty()) {
            return Optional.empty();
        }
        PromisedConsortActionId actionId = selectedAction.get();
        PromisedConsortActionSnapshot snapshot = runtime.start(
            actionId,
            host.phase(),
            gameTick,
            stableSeed(gameTick, actionId, targetId),
            targetId
        );
        cooldowns.recordStarted(actionId, gameTick);
        return Optional.of(snapshot);
    }

    private Optional<PromisedConsortActionSnapshot> branchAfter(
            PromisedConsortActionRuntime.ActionEnd end,
            long gameTick
    ) {
        if (end.actionId() == PromisedConsortActionId.LION_CLAW
                && (!host.actionHit(end.sequence()) || host.actionBlocked(end.sequence()))
                && chance(gameTick, end.sequence())
                < catalog.skillConfig().get(PromisedConsortActionId.LION_CLAW)
                .number("double_followup_chance")) {
            return force(PromisedConsortActionId.LION_CLAW_DOUBLE, host.phase());
        }
        if (!host.actionBlocked(end.sequence())
                || chance(gameTick ^ 0x9E3779B97F4A7C15L, end.sequence())
                >= config.selector().blockedBranchChance()) {
            return Optional.empty();
        }
        return switch (config.selector().blockedBranchMode()) {
            case "stomp" -> cooldowns.isEligible(
                    PromisedConsortActionId.STOMP,
                    host.phase(),
                    gameTick
            ) ? force(PromisedConsortActionId.STOMP, host.phase()) : Optional.empty();
            case "weighted" -> startSelectedAction(gameTick);
            case "continue_combo" -> Optional.empty();
            default -> Optional.empty();
        };
    }

    private Optional<LivingEntity> selectTarget(long gameTick) {
        Collection<? extends LivingEntity> supplied = host.visibleEligibleTargets();
        List<Candidate> candidates = new ArrayList<>();
        double maximumDamage = 0.0;
        for (LivingEntity target : supplied) {
            if (target == null || !target.isAlive() || target.isRemoved()) {
                continue;
            }
            if (target instanceof Player player && player.isSpectator()) {
                continue;
            }
            double distance = host.distanceTo(target);
            if (distance > config.arena().logicalRadius()) {
                continue;
            }
            double recentDamage = host.recentDamage(target, config.targeting().recentDamageWindowTicks());
            maximumDamage = Math.max(maximumDamage, recentDamage);
            candidates.add(new Candidate(target, distance, recentDamage));
        }
        double damageScale = maximumDamage;
        Optional<LivingEntity> selected = candidates.stream()
                .max(Comparator.comparingDouble(candidate -> score(candidate, damageScale, gameTick)))
                .map(Candidate::target);
        UUID selectedId = selected.map(LivingEntity::getUUID).orElse(null);
        if (!Objects.equals(selectedId, targetId)) {
            targetId = selectedId;
            targetSinceTick = selectedId == null ? -1L : gameTick;
            runtime.retarget(targetId);
        }
        return selected;
    }

    private Optional<LivingEntity> currentTarget() {
        if (targetId == null) {
            return Optional.empty();
        }
        return host.visibleEligibleTargets().stream()
                .filter(target -> target != null && target.isAlive() && !target.isRemoved())
                .filter(target -> target.getUUID().equals(targetId))
                .map(target -> (LivingEntity) target)
                .findFirst();
    }

    private double score(Candidate candidate, double maximumDamage, long gameTick) {
        double distanceScore = 1.0 - Math.min(
                candidate.distance() / config.arena().logicalRadius(),
                1.0
        );
        double damageScore = maximumDamage <= 0.0 ? 0.0 : candidate.recentDamage() / maximumDamage;
        double score = distanceScore * config.targeting().distanceWeight()
                + damageScore * config.targeting().recentDamageWeight()
                + (host.isUsingItem(candidate.target()) ? config.targeting().itemUseWeight() : 0.0);
        if (candidate.target().getUUID().equals(targetId)
                && targetSinceTick >= 0L
                && gameTick - targetSinceTick >= config.multiplayer().sameTargetPenaltyAfterTicks()) {
            score *= config.multiplayer().sameTargetScoreMultiplier();
        }
        return score;
    }

    private void recordCompleted(PromisedConsortActionRuntime.ActionEnd end) {
        int limit = config.selector().avoidLastActionCount();
        if (limit == 0) {
            history.clear();
            return;
        }
        while (history.size() >= limit) {
            history.removeFirst();
        }
        history.addLast(end.actionId());
    }

    private void clearTarget() {
        targetId = null;
        targetSinceTick = -1L;
    }

    private static boolean isCombatState(PromisedConsortCombatState state) {
        return state == PromisedConsortCombatState.PHASE_1
                || state == PromisedConsortCombatState.PHASE_2;
    }

    private static int idleTicks(long gameTick) {
        return MINIMUM_IDLE_TICKS
                + (int) Math.floorMod(gameTick, MAXIMUM_IDLE_TICKS - MINIMUM_IDLE_TICKS + 1L);
    }

    private static double chance(long gameTick, long sequence) {
        return new java.util.Random(gameTick ^ Long.rotateLeft(sequence, 17)).nextDouble();
    }

    private static long stableSeed(
            long gameTick,
            PromisedConsortActionId actionId,
            UUID targetId
    ) {
        long seed = gameTick ^ (actionId == null ? 0L : Long.rotateLeft(actionId.ordinal(), 23));
        if (targetId != null) {
            seed ^= targetId.getMostSignificantBits();
            seed ^= Long.rotateLeft(targetId.getLeastSignificantBits(), 31);
        }
        seed = (seed ^ seed >>> 30) * 0xBF58476D1CE4E5B9L;
        seed = (seed ^ seed >>> 27) * 0x94D049BB133111EBL;
        return seed ^ seed >>> 31;
    }

    private static long saturatedAdd(long value, int increment) {
        return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }

    public interface Host {
        long gameTime();

        PromisedConsortCombatState combatState();

        PromisedConsortPhase phase();

        Collection<? extends LivingEntity> visibleEligibleTargets();

        double distanceTo(LivingEntity target);

        double recentDamage(LivingEntity target, int windowTicks);

        boolean isUsingItem(LivingEntity target);

        int nearbyPlayers(double range);

        int rightRearTicks(LivingEntity target);

        boolean previousActionHit();

        boolean actionHit(long sequence);

        boolean actionBlocked(long sequence);

        int consumeForcedRecoveryTicks();

        boolean canSelectWeightedMeteor(long gameTick);
    }

    private record Candidate(LivingEntity target, double distance, double recentDamage) {
    }

    public record TickResult(
            Optional<UUID> targetId,
            Optional<PromisedConsortActionSnapshot> action,
            Optional<PromisedConsortActionRuntime.ActionEnd> actionEnded
    ) {
        public TickResult {
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(actionEnded, "actionEnded");
        }
    }
}
