package com.tonywww.elder_bosses.boss.malenia.execution;

import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;

import java.util.List;
import java.util.Objects;

public record MaleniaActionPlan(
        MaleniaActionId actionId,
        boolean enabled,
        int totalTicks,
        List<ScheduledIntent> intents
) {
    public MaleniaActionPlan {
        Objects.requireNonNull(actionId, "actionId");
        if (totalTicks <= 0) {
            throw new IllegalArgumentException("totalTicks must be positive");
        }
        intents = List.copyOf(Objects.requireNonNull(intents, "intents"));
        int previousTick = -1;
        for (ScheduledIntent scheduled : intents) {
            Objects.requireNonNull(scheduled, "scheduled intent");
            if (scheduled.actionTick() >= totalTicks) {
                throw new IllegalArgumentException("intent tick outside action timeline");
            }
            if (scheduled.actionTick() < previousTick) {
                throw new IllegalArgumentException("intents must be ordered by action tick");
            }
            previousTick = scheduled.actionTick();
        }
    }

    public List<MaleniaServerIntent> intentsAt(int actionTick) {
        if (actionTick < 0 || actionTick >= totalTicks) {
            throw new IndexOutOfBoundsException("actionTick outside timeline: " + actionTick);
        }
        return intents.stream()
                .filter(scheduled -> scheduled.actionTick() == actionTick)
                .map(ScheduledIntent::intent)
                .toList();
    }

    public record ScheduledIntent(int actionTick, MaleniaServerIntent intent) {
        public ScheduledIntent {
            if (actionTick < 0) {
                throw new IllegalArgumentException("actionTick must be non-negative");
            }
            Objects.requireNonNull(intent, "intent");
        }
    }
}