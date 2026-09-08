package com.tonywww.elder_bosses.combat.hit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class HitRegistry {
    private final Map<HitId, Set<UUID>> targetsByHit = new HashMap<>();

    public boolean claim(HitId hitId, UUID targetId) {
        Objects.requireNonNull(hitId, "hitId");
        Objects.requireNonNull(targetId, "targetId");
        return targetsByHit.computeIfAbsent(hitId, ignored -> new HashSet<>()).add(targetId);
    }

    public void close(HitId hitId) {
        targetsByHit.remove(Objects.requireNonNull(hitId, "hitId"));
    }

    public void clear() {
        targetsByHit.clear();
    }

    public List<PersistentClaim> persistentClaims() {
        return targetsByHit.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(targetId -> new PersistentClaim(entry.getKey(), targetId)))
                .toList();
    }

    public void restoreClaims(List<PersistentClaim> claims) {
        clear();
        for (PersistentClaim claim : claims) {
            targetsByHit.computeIfAbsent(claim.hitId(), ignored -> new HashSet<>())
                    .add(claim.targetId());
        }
    }

    public record PersistentClaim(HitId hitId, UUID targetId) {
        public PersistentClaim {
            Objects.requireNonNull(hitId, "hitId");
            Objects.requireNonNull(targetId, "targetId");
        }
    }
}