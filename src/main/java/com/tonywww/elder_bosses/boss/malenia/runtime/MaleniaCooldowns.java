package com.tonywww.elder_bosses.boss.malenia.runtime;

import com.tonywww.elder_bosses.boss.malenia.action.MaleniaActionCatalog;
import com.tonywww.elder_bosses.boss.malenia.action.MaleniaActionDefinition;
import com.tonywww.elder_bosses.boss.malenia.config.MaleniaCombatConfigSnapshot;
import com.tonywww.elder_bosses.boss.malenia.config.MaleniaSkillConfigSnapshot;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaPhase;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

public final class MaleniaCooldowns {
    private final MaleniaActionCatalog catalog;
    private final MaleniaSkillConfigSnapshot.WaterfowlDance waterfowlDance;
    private final int highThreatGroupCooldownTicks;
    private final EnumMap<MaleniaActionId, Long> completionTicks = new EnumMap<>(MaleniaActionId.class);
    private final EnumMap<MaleniaPhase, Long> phaseStartTicks = new EnumMap<>(MaleniaPhase.class);
    private Long highThreatCompletionTick;
    private Long phaseTwoOpeningEndTick;
    private boolean phaseOneWaterfowlStarted;
    private long lastObservedGameTick = -1L;

        public MaleniaCooldowns(
            MaleniaActionCatalog catalog,
            MaleniaCombatConfigSnapshot.Selector selectorConfig
        ) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        waterfowlDance = catalog.skillConfig().waterfowlDance();
        highThreatGroupCooldownTicks = Objects.requireNonNull(
            selectorConfig,
            "selectorConfig"
        ).highThreatGroupCooldownTicks();
    }

    public static MaleniaCooldowns restore(
            MaleniaActionCatalog catalog,
            MaleniaCombatConfigSnapshot.Selector selectorConfig,
            MaleniaCooldownSnapshot snapshot,
            long gameTick
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        validateGameTick(gameTick);
        MaleniaCooldowns cooldowns = new MaleniaCooldowns(catalog, selectorConfig);

        for (Map.Entry<MaleniaActionId, Integer> entry : snapshot.actionRemainingTicks().entrySet()) {
            MaleniaActionDefinition definition = cooldowns.requireDefinition(entry.getKey());
            int remainingTicks = requireAtMost(
                    entry.getValue(),
                    definition.cooldownTicks(),
                    "action cooldown"
            );
            if (remainingTicks > 0) {
                cooldowns.completionTicks.put(
                        entry.getKey(),
                        originTickForRemaining(gameTick, definition.cooldownTicks(), remainingTicks)
                );
            }
        }

        int highThreatRemaining = requireAtMost(
                snapshot.highThreatRemainingTicks(),
            cooldowns.highThreatGroupCooldownTicks,
                "high-threat cooldown"
        );
        if (highThreatRemaining > 0) {
            cooldowns.highThreatCompletionTick = originTickForRemaining(
                    gameTick,
                cooldowns.highThreatGroupCooldownTicks,
                    highThreatRemaining
            );
        }

        for (Map.Entry<MaleniaPhase, Integer> entry
                : snapshot.waterfowlPhaseGateRemainingTicks().entrySet()) {
            int remainingTicks = requireAtMost(
                    entry.getValue(),
                cooldowns.waterfowlDance.firstEligibleTicks(),
                    "waterfowl phase gate"
            );
            cooldowns.phaseStartTicks.put(
                    entry.getKey(),
                originTickForRemaining(
                    gameTick,
                    cooldowns.waterfowlDance.firstEligibleTicks(),
                    remainingTicks
                )
            );
        }

        if (snapshot.phaseTwoOpeningRemainingTicks().isPresent()) {
            int remainingTicks = requireAtMost(
                    snapshot.phaseTwoOpeningRemainingTicks().getAsInt(),
                cooldowns.waterfowlDance.phaseTwoOpeningDelayTicks(),
                    "phase-two opening gate"
            );
            cooldowns.phaseTwoOpeningEndTick = originTickForRemaining(
                    gameTick,
                cooldowns.waterfowlDance.phaseTwoOpeningDelayTicks(),
                    remainingTicks
            );
        }
        cooldowns.phaseOneWaterfowlStarted = snapshot.phaseOneWaterfowlStarted();
        cooldowns.lastObservedGameTick = gameTick;
        return cooldowns;
    }

    public void beginPhase(MaleniaPhase phase, long gameTick) {
        Objects.requireNonNull(phase, "phase");
        recordGameTick(gameTick);
        if (phaseStartTicks.putIfAbsent(phase, gameTick) != null) {
            throw new IllegalStateException(phase + " has already started");
        }
    }

    public void recordPhaseTwoOpeningEnded(long gameTick) {
        recordGameTick(gameTick);
        if (phaseTwoOpeningEndTick != null) {
            throw new IllegalStateException("phase-two opening end has already been recorded");
        }
        phaseTwoOpeningEndTick = gameTick;
    }

    public void recordActionStarted(MaleniaActionId actionId, MaleniaPhase phase, long gameTick) {
        Objects.requireNonNull(phase, "phase");
        recordGameTick(gameTick);
        MaleniaActionDefinition definition = requireDefinition(actionId);
        if (!definition.isAvailableIn(phase)) {
            throw new IllegalArgumentException(actionId + " is not available in " + phase);
        }
        if (actionId == MaleniaActionId.WATERFOWL_DANCE && phase == MaleniaPhase.PHASE_ONE) {
            phaseOneWaterfowlStarted = true;
        }
    }

    public void recordCompleted(MaleniaActionId actionId, long completionTick) {
        MaleniaActionDefinition definition = requireDefinition(actionId);
        recordGameTick(completionTick);
        Long previousCompletionTick = completionTicks.get(actionId);
        if (previousCompletionTick != null && completionTick < previousCompletionTick) {
            throw new IllegalArgumentException("completionTick must not move backwards for " + actionId);
        }
        completionTicks.put(actionId, completionTick);
        if (definition.highThreat()) {
            if (highThreatCompletionTick != null && completionTick < highThreatCompletionTick) {
                throw new IllegalArgumentException("high-threat completionTick must not move backwards");
            }
            highThreatCompletionTick = completionTick;
        }
    }

    public void recordCompleted(MaleniaActionRuntime.ActionEnd actionEnd) {
        Objects.requireNonNull(actionEnd, "actionEnd");
        if (!actionEnd.completed()) {
            throw new IllegalArgumentException("only completed actions start cooldowns");
        }
        recordCompleted(actionEnd.actionId(), actionEnd.endGameTick());
    }

    public int actionRemainingTicks(MaleniaActionId actionId, long gameTick) {
        MaleniaActionDefinition definition = requireDefinition(actionId);
        observeGameTick(gameTick);
        return remainingTicks(completionTicks.get(actionId), definition.cooldownTicks(), gameTick);
    }

    public int highThreatRemainingTicks(long gameTick) {
        observeGameTick(gameTick);
        return remainingTicks(highThreatCompletionTick, highThreatGroupCooldownTicks, gameTick);
    }

    public int cooldownRemainingTicks(MaleniaActionId actionId, long gameTick) {
        MaleniaActionDefinition definition = requireDefinition(actionId);
        int actionRemaining = remainingTicks(
                completionTicks.get(actionId),
                definition.cooldownTicks(),
                gameTick
        );
        int groupRemaining = definition.highThreat() ? highThreatRemainingTicks(gameTick) : 0;
        return Math.max(actionRemaining, groupRemaining);
    }

    public Eligibility eligibility(
            MaleniaActionId actionId,
            MaleniaPhase phase,
            long gameTick,
            double phaseHealthRatio
    ) {
        Objects.requireNonNull(phase, "phase");
        validateHealthRatio(phaseHealthRatio);
        MaleniaActionDefinition definition = requireDefinition(actionId);
        int actionRemaining = actionRemainingTicks(actionId, gameTick);
        int highThreatRemaining = definition.highThreat() ? highThreatRemainingTicks(gameTick) : 0;

        boolean phaseGateRecorded = true;
        int phaseGateRemaining = 0;
        boolean openingGateRecorded = true;
        int openingGateRemaining = 0;
        boolean healthGateSatisfied = true;
        if (actionId == MaleniaActionId.WATERFOWL_DANCE) {
            Long phaseStartTick = phaseStartTicks.get(phase);
            phaseGateRecorded = phaseStartTick != null;
            phaseGateRemaining = remainingTicks(
                    phaseStartTick,
                    waterfowlDance.firstEligibleTicks(),
                    gameTick
            );
            if (phase == MaleniaPhase.PHASE_ONE) {
                healthGateSatisfied = phaseOneWaterfowlStarted
                    || phaseHealthRatio < waterfowlDance.phaseOneFirstHealthRatio();
            } else {
                openingGateRecorded = phaseTwoOpeningEndTick != null;
                openingGateRemaining = remainingTicks(
                        phaseTwoOpeningEndTick,
                    waterfowlDance.phaseTwoOpeningDelayTicks(),
                        gameTick
                );
            }
        }

        return new Eligibility(
                actionId,
                phase,
                definition.isAvailableIn(phase),
                actionRemaining,
                highThreatRemaining,
                phaseGateRecorded,
                phaseGateRemaining,
                openingGateRecorded,
                openingGateRemaining,
                healthGateSatisfied
        );
    }

    public boolean isEligible(
            MaleniaActionId actionId,
            MaleniaPhase phase,
            long gameTick,
            double phaseHealthRatio
    ) {
        return eligibility(actionId, phase, gameTick, phaseHealthRatio).eligible();
    }

    public MaleniaCooldownSnapshot snapshot(long gameTick) {
        observeGameTick(gameTick);
        EnumMap<MaleniaActionId, Integer> actionRemaining = new EnumMap<>(MaleniaActionId.class);
        for (MaleniaActionId actionId : completionTicks.keySet()) {
            int remainingTicks = actionRemainingTicks(actionId, gameTick);
            if (remainingTicks > 0) {
                actionRemaining.put(actionId, remainingTicks);
            }
        }

        EnumMap<MaleniaPhase, Integer> phaseGateRemaining = new EnumMap<>(MaleniaPhase.class);
        for (Map.Entry<MaleniaPhase, Long> entry : phaseStartTicks.entrySet()) {
            phaseGateRemaining.put(
                    entry.getKey(),
                    remainingTicks(
                        entry.getValue(),
                        waterfowlDance.firstEligibleTicks(),
                        gameTick
                    )
            );
        }

        OptionalInt openingRemaining = phaseTwoOpeningEndTick == null
                ? OptionalInt.empty()
                : OptionalInt.of(remainingTicks(
                        phaseTwoOpeningEndTick,
                    waterfowlDance.phaseTwoOpeningDelayTicks(),
                        gameTick
                ));
        return new MaleniaCooldownSnapshot(
                actionRemaining,
                highThreatRemainingTicks(gameTick),
                phaseGateRemaining,
                openingRemaining,
                phaseOneWaterfowlStarted
        );
    }

    public void clear() {
        completionTicks.clear();
        phaseStartTicks.clear();
        highThreatCompletionTick = null;
        phaseTwoOpeningEndTick = null;
        phaseOneWaterfowlStarted = false;
        lastObservedGameTick = -1L;
    }

    private void observeGameTick(long gameTick) {
        validateGameTick(gameTick);
        if (gameTick < lastObservedGameTick) {
            throw new IllegalArgumentException("gameTick must not move backwards");
        }
        lastObservedGameTick = gameTick;
    }

    private void recordGameTick(long gameTick) {
        validateGameTick(gameTick);
        lastObservedGameTick = Math.max(lastObservedGameTick, gameTick);
    }

    private static int remainingTicks(Long originTick, int durationTicks, long gameTick) {
        validateGameTick(gameTick);
        if (originTick == null) {
            return 0;
        }
        long elapsedTicks = gameTick - originTick;
        if (elapsedTicks < 0L) {
            throw new IllegalArgumentException("gameTick precedes a recorded cooldown boundary");
        }
        if (elapsedTicks >= durationTicks) {
            return 0;
        }
        return durationTicks - Math.toIntExact(elapsedTicks);
    }

    private static long originTickForRemaining(long gameTick, int durationTicks, int remainingTicks) {
        return gameTick - (durationTicks - remainingTicks);
    }

    private static int requireAtMost(int value, int maximum, String name) {
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException(name + " must be between 0 and " + maximum);
        }
        return value;
    }

    private static void validateGameTick(long gameTick) {
        if (gameTick < 0L) {
            throw new IllegalArgumentException("gameTick must be non-negative");
        }
    }

    private static void validateHealthRatio(double healthRatio) {
        if (!Double.isFinite(healthRatio) || healthRatio < 0.0 || healthRatio > 1.0) {
            throw new IllegalArgumentException("phaseHealthRatio must be finite and between 0 and 1");
        }
    }

    private MaleniaActionDefinition requireDefinition(MaleniaActionId actionId) {
        MaleniaActionDefinition definition = catalog.get(
                Objects.requireNonNull(actionId, "actionId")
        );
        if (definition == null) {
            throw new IllegalArgumentException("unknown Malenia action: " + actionId);
        }
        return definition;
    }

    public record Eligibility(
            MaleniaActionId actionId,
            MaleniaPhase phase,
            boolean availableInPhase,
            int actionRemainingTicks,
            int highThreatRemainingTicks,
            boolean phaseGateRecorded,
            int phaseGateRemainingTicks,
            boolean phaseTwoOpeningGateRecorded,
            int phaseTwoOpeningRemainingTicks,
            boolean phaseOneHealthGateSatisfied
    ) {
        public Eligibility {
            Objects.requireNonNull(actionId, "actionId");
            Objects.requireNonNull(phase, "phase");
            if (actionRemainingTicks < 0
                    || highThreatRemainingTicks < 0
                    || phaseGateRemainingTicks < 0
                    || phaseTwoOpeningRemainingTicks < 0) {
                throw new IllegalArgumentException("remaining ticks must be non-negative");
            }
        }

        public boolean eligible() {
            return availableInPhase
                    && actionRemainingTicks == 0
                    && highThreatRemainingTicks == 0
                    && phaseGateRecorded
                    && phaseGateRemainingTicks == 0
                    && phaseTwoOpeningGateRecorded
                    && phaseTwoOpeningRemainingTicks == 0
                    && phaseOneHealthGateSatisfied;
        }

        public int timeGateRemainingTicks() {
            return Math.max(
                    Math.max(actionRemainingTicks, highThreatRemainingTicks),
                    Math.max(phaseGateRemainingTicks, phaseTwoOpeningRemainingTicks)
            );
        }
    }
}