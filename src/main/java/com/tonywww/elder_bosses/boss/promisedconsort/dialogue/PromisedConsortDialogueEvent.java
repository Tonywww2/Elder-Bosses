package com.tonywww.elder_bosses.boss.promisedconsort.dialogue;

import java.util.Arrays;
import java.util.Optional;

public enum PromisedConsortDialogueEvent {
    TRANSITION_CALL(0, 4, "dialogue.elder_bosses.promised_consort.transition_call"),
    PHASE_TWO_VOW(1, 4, "dialogue.elder_bosses.promised_consort.phase_two_vow"),
    PLAYER_DEFEATED(2, 2, "dialogue.elder_bosses.promised_consort.player_defeated"),
    DEFEATED(3, 4, "dialogue.elder_bosses.promised_consort.defeated");

    public static final String SPEAKER_LANGUAGE_KEY = "speaker.elder_bosses.miquella";

    private final int id;
    private final int priority;
    private final String languageKey;

    PromisedConsortDialogueEvent(int id, int priority, String languageKey) {
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

    public static Optional<PromisedConsortDialogueEvent> fromId(int id) {
        return Arrays.stream(values()).filter(event -> event.id == id).findFirst();
    }
}
