package com.tonywww.elder_bosses.combat.hit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PerTargetHitCounter {
    private final Map<Key, Integer> hits = new HashMap<>();

    public boolean claim(long actionSequence, int groupIndex, UUID targetId, int maximumHits) {
        if (actionSequence < 0L || groupIndex < 0) {
            throw new IllegalArgumentException("actionSequence and groupIndex must be non-negative");
        }
        if (maximumHits <= 0) {
            throw new IllegalArgumentException("maximumHits must be positive");
        }
        Key key = new Key(actionSequence, groupIndex, Objects.requireNonNull(targetId, "targetId"));
        int currentHits = hits.getOrDefault(key, 0);
        if (currentHits >= maximumHits) {
            return false;
        }
        hits.put(key, currentHits + 1);
        return true;
    }

    public void clearAction(long actionSequence) {
        hits.keySet().removeIf(key -> key.actionSequence == actionSequence);
    }

    public void clear() {
        hits.clear();
    }

    public List<PersistentCount> persistentCounts() {
        return hits.entrySet().stream()
                .map(entry -> new PersistentCount(
                        entry.getKey().actionSequence(),
                        entry.getKey().groupIndex(),
                        entry.getKey().targetId(),
                        entry.getValue()
                ))
                .toList();
    }

    public void restoreCounts(List<PersistentCount> counts) {
        clear();
        for (PersistentCount count : counts) {
            hits.put(
                    new Key(count.actionSequence(), count.groupIndex(), count.targetId()),
                    count.hits()
            );
        }
    }

    public record PersistentCount(
            long actionSequence,
            int groupIndex,
            UUID targetId,
            int hits
    ) {
        public PersistentCount {
            Objects.requireNonNull(targetId, "targetId");
            if (actionSequence < 0L || groupIndex < 0 || hits <= 0) {
                throw new IllegalArgumentException("invalid persisted hit count");
            }
        }
    }

    private record Key(long actionSequence, int groupIndex, UUID targetId) {
    }
}