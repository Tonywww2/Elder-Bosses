package com.tonywww.elder_bosses.dialogue;

import java.util.Optional;

public enum DialogueEvent {
    INTRO_WARNING(0, 3, "dialogue.elder_bosses.malenia.intro_warning"),
    PLAYER_DEFEATED_P1(1, 2, "dialogue.elder_bosses.malenia.player_defeated_p1"),
    TRANSITION_RELEASE(2, 4, "dialogue.elder_bosses.malenia.transition_release"),
    PLAYER_DEFEATED_P2(3, 2, "dialogue.elder_bosses.malenia.player_defeated_p2"),
    DEFEATED(4, 4, "dialogue.elder_bosses.malenia.defeated");

    private final int id;
    private final int priority;
    private final String languageKey;

    DialogueEvent(int id, int priority, String languageKey) {
        this.id = id;
        this.priority = priority;
        this.languageKey = languageKey;
    }

    public int id() {
        return id;
    }

    public int priority() {
        return priority;
    }

    public String languageKey() {
        return languageKey;
    }

    public static Optional<DialogueEvent> fromId(int id) {
        for (DialogueEvent event : values()) {
            if (event.id == id) {
                return Optional.of(event);
            }
        }
        return Optional.empty();
    }
}