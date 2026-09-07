package com.tonywww.elder_bosses.boss.malenia.action;

import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaPhase;
import com.tonywww.elder_bosses.combat.action.ActionTimeline;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record MaleniaActionDefinition(
        MaleniaActionId id,
        ActionTimeline timeline,
        int cooldownTicks,
        Set<MaleniaPhase> availablePhases,
        Set<MaleniaActionTag> tags,
        List<MaleniaActionEvent> events,
        boolean highThreat
) {
    public MaleniaActionDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(availablePhases, "availablePhases");
        Objects.requireNonNull(tags, "tags");
        Objects.requireNonNull(events, "events");
        if (cooldownTicks <= 0) {
            throw new IllegalArgumentException("cooldown must be positive");
        }
        if (availablePhases.isEmpty()) {
            throw new IllegalArgumentException("an action must be available in at least one phase");
        }
        if (tags.isEmpty()) {
            throw new IllegalArgumentException("an action requires at least one syntax tag");
        }
        if (events.isEmpty()) {
            throw new IllegalArgumentException("an action requires at least one event");
        }
        availablePhases = Collections.unmodifiableSet(EnumSet.copyOf(availablePhases));
        tags = Collections.unmodifiableSet(EnumSet.copyOf(tags));
        events = List.copyOf(events);
        validateEvents(timeline, events);
    }

    public boolean isAvailableIn(MaleniaPhase phase) {
        return availablePhases.contains(Objects.requireNonNull(phase, "phase"));
    }

    private static void validateEvents(ActionTimeline timeline, List<MaleniaActionEvent> events) {
        int previousSequence = -1;
        for (MaleniaActionEvent event : events) {
            Objects.requireNonNull(event, "event");
            if (event.sequence() <= previousSequence) {
                throw new IllegalArgumentException("event sequences must be strictly increasing");
            }
            previousSequence = event.sequence();
            if (event.actionTick().isPresent()) {
                int actionTick = event.actionTick().getAsInt();
                if (actionTick >= timeline.totalTicks()) {
                    throw new IllegalArgumentException("event tick outside action timeline: " + actionTick);
                }
                if (event.durationTicks().isPresent()
                        && (long) actionTick + event.durationTicks().getAsInt() > timeline.totalTicks()) {
                    throw new IllegalArgumentException("event duration extends beyond action timeline");
                }
            }
        }
    }
}