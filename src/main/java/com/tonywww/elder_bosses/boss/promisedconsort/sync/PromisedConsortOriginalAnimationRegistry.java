package com.tonywww.elder_bosses.boss.promisedconsort.sync;

import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Central registry for the Radahn HKX clips shipped with the mod.
 *
 * Named clips are the retimed project action tracks. Numeric clips preserve
 * the original source timing and are available for special and clone actions.
 * Keeping both namespaces here prevents an action from silently falling back
 * to a hand-authored clip when a source clip has been added to the resource.
 */
public final class PromisedConsortOriginalAnimationRegistry {
    private static final Map<PromisedConsortActionId, String> ACTION_CLIPS = createActionClips();
    private static final Map<Integer, String> SOURCE_CLIPS = createSourceClips();

    private PromisedConsortOriginalAnimationRegistry() {
    }

    public static Optional<String> actionClip(String serializedName) {
        return PromisedConsortActionId.fromSerializedName(serializedName).flatMap(
                PromisedConsortOriginalAnimationRegistry::actionClip);
    }

    public static Optional<String> actionClip(PromisedConsortActionId actionId) {
        return Optional.ofNullable(ACTION_CLIPS.get(actionId));
    }

    public static Optional<String> sourceClip(int sourceAnimationId) {
        return Optional.ofNullable(SOURCE_CLIPS.get(sourceAnimationId));
    }

    public static Map<PromisedConsortActionId, String> actionClips() {
        return ACTION_CLIPS;
    }

    public static Map<Integer, String> sourceClips() {
        return SOURCE_CLIPS;
    }

    private static Map<PromisedConsortActionId, String> createActionClips() {
        EnumMap<PromisedConsortActionId, String> clips = new EnumMap<>(PromisedConsortActionId.class);
        for (PromisedConsortActionId actionId : PromisedConsortActionId.values()) {
            if (!actionId.rangedDefense()) {
                clips.put(actionId, actionId.serializedName());
            }
        }
        return Collections.unmodifiableMap(clips);
    }

    private static Map<Integer, String> createSourceClips() {
        Map<Integer, String> clips = new LinkedHashMap<>();
        int[] sourceIds = {
                3000, 3001, 3002, 3003, 3004, 3005, 3006, 3007, 3008,
                3009, 3010, 3011, 3012, 3013, 3014, 3015, 3016, 3017,
                3018, 3019, 3020, 3021, 3022, 3023, 3024, 3025, 3026,
                3028, 3030, 3031, 3032, 3033, 3034, 3035, 3036
        };
        for (int sourceId : sourceIds) {
            clips.put(sourceId, "radahn_" + String.format(java.util.Locale.ROOT, "%06d", sourceId));
        }
        return Collections.unmodifiableMap(clips);
    }
}
