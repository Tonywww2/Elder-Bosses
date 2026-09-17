package com.tonywww.elder_bosses.boss.promisedconsort.controller;

import com.tonywww.elder_bosses.boss.promisedconsort.action.PromisedConsortActionCatalog;
import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortCombatConfigSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortRangedConfig;
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
    private final PromisedConsortBurstCadence burst;

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
        this.burst = new PromisedConsortBurstCadence(config.selector().burst());
    }

    public TickResult tick() {
        long gameTick = host.gameTime();
        if (gameTick < lastTick) {
            throw new IllegalStateException("game time moved backwards");
        }
        if (gameTick == lastTick) return new TickResult(Optional.ofNullable(targetId), runtime.snapshot(gameTick), Optional.empty());
        if (runtime.isActive() && burst.remaining() == 0) burst.start(stableSeed(gameTick, null, targetId));
        Optional<PromisedConsortActionRuntime.ActionEnd> ended = runtime.advance(gameTick);
        if (ended.isEmpty() && isCombatState(host.combatState()) && burst.mayShortenRecovery()
            && !host.hasForcedRecovery() && currentTarget().isPresent() && runtime.snapshot(gameTick).map(action -> chainable(action.actionId())).orElse(false)) {
            ended = runtime.finishRecovery(gameTick, config.selector().burst().chainRecoveryTicks());
        }
        ended.filter(PromisedConsortActionRuntime.ActionEnd::completed).ifPresent(this::recordCompleted);

        if (!isCombatState(host.combatState())) {
            runtime.cancel(gameTick);
            burst.reset();
            clearTarget();
            lastTick = gameTick;
            return new TickResult(Optional.empty(), Optional.empty(), ended);
        }

        boolean lionActive = runtime.snapshot(gameTick).map(action -> action.actionId() == PromisedConsortActionId.LION_CLAW
            || action.actionId() == PromisedConsortActionId.LION_CLAW_DOUBLE).orElse(false);
        if (!lionActive && (gameTick >= nextRetargetTick || currentTarget().isEmpty())) {
            selectTarget(gameTick);
            nextRetargetTick = saturatedAdd(gameTick, config.multiplayer().retargetIntervalTicks());
        }

        if (ended.isPresent()) {
            int recovery = host.consumeForcedRecoveryTicks();
            if (recovery <= 0) {
                Optional<PromisedConsortActionSnapshot> branch = branchAfter(ended.get(), gameTick, true);
                if (branch.isPresent()) {
                    lastTick = gameTick;
                    return new TickResult(Optional.ofNullable(targetId), branch, ended);
                }
            }
            var delay = burst.complete(stableSeed(gameTick, ended.get().actionId(), targetId));
            if (recovery > 0) burst.reset();
            if (recovery <= 0 && !delay.breathing()) {
                Optional<PromisedConsortActionSnapshot> branch = branchAfter(ended.get(), gameTick, false);
                if (branch.isPresent()) {
                    lastTick = gameTick;
                    return new TickResult(Optional.ofNullable(targetId), branch, ended);
                }
            }
            var ranged = config.targeting().rangedCounter();
            boolean pursuing = ranged.enabled() && currentTarget().map(host::isRangedTarget).orElse(false);
            nextSelectionTick = saturatedAdd(gameTick, recovery > 0 ? recovery : delay.scaled(ranged.pursuitIdleMultiplier(), pursuing));
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
        burst.reset();
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
        burst.reset();
        nextSelectionTick = host.gameTime();
    }

    public PromisedConsortActionSnapshot forceOpeningLion(UUID participant) {
        runtime.cancel(host.gameTime());
        targetId = Objects.requireNonNull(participant);
        targetSinceTick = host.gameTime();
        var action = PromisedConsortActionId.LION_CLAW;
        var snapshot = runtime.start(action, host.phase(), host.gameTime(), stableSeed(host.gameTime(), action, targetId), targetId);
        cooldowns.recordStarted(action, host.gameTime());
        return snapshot;
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
            if (host.rangedActionWeight(actionId, target.orElseThrow()) <= 0) continue;
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
                eligible,
                eligible.stream().collect(java.util.stream.Collectors.toMap(action -> action,
                    action -> host.rangedActionWeight(action, selected)))
        );
        Optional<PromisedConsortActionId> selectedAction = selector.select(
            context,
            stableSeed(gameTick, null, targetId)
        );
        if (selectedAction.isEmpty()) {
            return Optional.empty();
        }
        PromisedConsortActionId actionId = selectedAction.get();
        burst.start(stableSeed(gameTick, actionId, targetId));
        PromisedConsortActionSnapshot snapshot = runtime.start(
            actionId,
            host.phase(),
            gameTick,
            stableSeed(gameTick, actionId, targetId),
            targetId,
            host.useRangedVariant(actionId, selected)
        );
        cooldowns.recordStarted(actionId, gameTick);
        return Optional.of(snapshot);
    }

    private Optional<PromisedConsortActionSnapshot> branchAfter(
            PromisedConsortActionRuntime.ActionEnd end,
            long gameTick,
            boolean lionFollowupOnly
    ) {
        if (lionFollowupOnly && end.actionId() == PromisedConsortActionId.LION_CLAW
                && (!host.actionHit(end.sequence()) || host.actionBlocked(end.sequence()))
                && chance(gameTick, end.sequence())
                < catalog.skillConfig().get(PromisedConsortActionId.LION_CLAW)
                .number("double_followup_chance")) {
            return force(PromisedConsortActionId.LION_CLAW_DOUBLE, host.phase());
        }
        if (lionFollowupOnly) return Optional.empty();
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
        if (runtime.snapshot(host.gameTime()).map(action -> action.actionId() == PromisedConsortActionId.LION_CLAW
                || action.actionId() == PromisedConsortActionId.LION_CLAW_DOUBLE).orElse(false)) return host.lockedActionTarget(targetId);
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

    public static boolean chainable(PromisedConsortActionId action) {
        return switch (action) {
            case L_COMBO_CROSS, L_COMBO_BLOODFLAME, R_COMBO_CROSS, R_COMBO_LEFT_TWIN, R_COMBO_TEMPEST,
                    R_COMBO_EARTHHEAVE, CROSS_SLASH, STOMP -> true;
            default -> false;
        };
    }

    public net.minecraft.nbt.CompoundTag saveCadence() {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("RemainingSkills", burst.remaining());
        tag.putLong("WaitTicks", Math.max(0, nextSelectionTick - host.gameTime()));
        return tag;
    }

    public void restoreCadence(net.minecraft.nbt.CompoundTag tag) {
        burst.restore(Math.max(0, Math.min(config.selector().burst().maximumSkills(), tag.getInt("RemainingSkills"))));
        nextSelectionTick = saturatedAdd(host.gameTime(), (int) Math.max(0, Math.min(Integer.MAX_VALUE, tag.getLong("WaitTicks"))));
    }

    public static int selectionDelay(long gameTick, int forcedRecoveryTicks, boolean rangedTarget, PromisedConsortRangedConfig rangedConfig) {
        if (forcedRecoveryTicks > 0) return forcedRecoveryTicks;
        int baseTicks = MINIMUM_IDLE_TICKS
                + (int) Math.floorMod(gameTick, MAXIMUM_IDLE_TICKS - MINIMUM_IDLE_TICKS + 1L);
        return rangedConfig.idleTicks(baseTicks, rangedTarget);
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
        default Optional<LivingEntity> lockedActionTarget(UUID targetId) {
            return visibleEligibleTargets().stream().filter(target -> target != null && target.isAlive() && !target.isRemoved()
                    && target.getUUID().equals(targetId)).map(target -> (LivingEntity) target).findFirst();
        }

        default boolean hasForcedRecovery() { return false; }

        default boolean isRangedTarget(LivingEntity target) { return false; }

        default double rangedActionWeight(PromisedConsortActionId action, LivingEntity target) {
            return action.rangedDefense() ? 0 : 1;
        }

        default boolean useRangedVariant(PromisedConsortActionId action, LivingEntity target) { return false; }

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
