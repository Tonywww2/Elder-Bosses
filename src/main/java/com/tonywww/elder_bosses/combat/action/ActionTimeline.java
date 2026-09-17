package com.tonywww.elder_bosses.combat.action;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class ActionTimeline {
    private final List<ActionStage> stages;
    private final List<ActionWindow> windows;
    private final int totalTicks;

    private ActionTimeline(List<ActionStage> stages) {
        this.stages = List.copyOf(stages);
        List<ActionWindow> builtWindows = new ArrayList<>(stages.size() * 3);
        int cursor = 0;
        for (int stageIndex = 0; stageIndex < stages.size(); stageIndex++) {
            ActionStage stage = stages.get(stageIndex);
            cursor = appendWindow(builtWindows, ActionPhase.WINDUP, cursor, stage.windupTicks(), stageIndex);
            cursor = appendWindow(builtWindows, ActionPhase.ACTIVE, cursor, stage.activeTicks(), stageIndex);
            cursor = appendWindow(builtWindows, ActionPhase.RECOVERY, cursor, stage.recoveryTicks(), stageIndex);
        }
        this.windows = List.copyOf(builtWindows);
        this.totalTicks = cursor;
    }

    public static ActionTimeline ofStages(ActionStage... stages) {
        Objects.requireNonNull(stages, "stages");
        if (stages.length == 0) {
            throw new IllegalArgumentException("an action timeline requires at least one stage");
        }
        List<ActionStage> copiedStages = Arrays.stream(stages)
                .map(stage -> Objects.requireNonNull(stage, "stage"))
                .toList();
        return new ActionTimeline(copiedStages);
    }

    public List<ActionStage> stages() {
        return stages;
    }

    public int totalTicks() {
        return totalTicks;
    }

    public int stageStartTick(int stageIndex) {
        if (stageIndex < 0 || stageIndex >= stages.size()) throw new IndexOutOfBoundsException("stageIndex outside timeline");
        int start = 0;
        for (int index = 0; index < stageIndex; index++) start += stages.get(index).totalTicks();
        return start;
    }

    public int activeStartTick(int stageIndex) {
        return stageStartTick(stageIndex) + stages.get(stageIndex).windupTicks();
    }

    public int activeEndTick(int stageIndex) {
        return activeStartTick(stageIndex) + stages.get(stageIndex).activeTicks();
    }

    public int activeTicksBetween(int firstTick, int endTickExclusive) {
        int ticks = 0;
        for (ActionWindow window : windows) {
            if (window.phase() == ActionPhase.ACTIVE) {
                ticks += Math.max(0, Math.min(endTickExclusive, window.endTickExclusive()) - Math.max(firstTick, window.startTickInclusive()));
            }
        }
        return ticks;
    }

    public ActionPhase phaseAt(int actionTick) {
        return windowAt(actionTick).phase();
    }

    public int phaseTickAt(int actionTick) {
        ActionWindow window = windowAt(actionTick);
        return actionTick - window.startTickInclusive();
    }

    public ActionWindow windowAt(int actionTick) {
        if (actionTick < 0 || actionTick >= totalTicks) {
            throw new IndexOutOfBoundsException("actionTick outside timeline: " + actionTick);
        }
        for (ActionWindow window : windows) {
            if (window.contains(actionTick)) {
                return window;
            }
        }
        throw new IllegalStateException("timeline contains an uncovered tick: " + actionTick);
    }

    private static int appendWindow(
            List<ActionWindow> windows,
            ActionPhase phase,
            int startTick,
            int duration,
            int stageIndex
    ) {
        if (duration == 0) {
            return startTick;
        }
        int endTick = Math.addExact(startTick, duration);
        windows.add(new ActionWindow(phase, startTick, endTick, stageIndex));
        return endTick;
    }
}