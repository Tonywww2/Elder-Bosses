package com.tonywww.elder_bosses.boss.promisedconsort.source;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** Source clock primitives. Event edges are observations, not automatic damage or effect removal. */
public final class PromisedConsortSourceTimeline {
    public static final long GAME_TICK_MICROS = 50_000L;

    private PromisedConsortSourceTimeline() {}

    public record Event(int index, int type, long startMicros, long endMicros, int stateInfo, int referenceId) {
        public Event {
            if (index < 0 || endMicros != -1 && endMicros < startMicros)
                throw new IllegalArgumentException("Invalid source event interval");
        }
        public boolean appliesTo(int actorStateInfo) { return stateInfo == 0 || stateInfo == actorStateInfo; }
    }

    public record Clip(int taeId, int hkxId, long durationMicros, List<Event> events) {
        public Clip {
            if (taeId < 0 || hkxId < 0 || durationMicros < 0) throw new IllegalArgumentException("Invalid source clip");
            events = List.copyOf(events);
            var indices = new HashSet<Integer>();
            for (Event event : events)
                if (!indices.add(event.index())) throw new IllegalArgumentException("Duplicate source event index");
        }
        public String animationClip() { return "animation.promised_consort.source_" + String.format(java.util.Locale.ROOT, "%06d", hkxId); }
    }

    /** A clone has its own clip and start tick; slot -1 identifies the boss body. */
    public record Actor(long actionSequence, int segmentIndex, int slot, int taeId, int hkxId, int stateInfo) {
        public Actor {
            if (actionSequence < 0 || segmentIndex < 0 || slot < -1 || slot > 3 || taeId < 0 || hkxId < 0)
                throw new IllegalArgumentException("Invalid source actor");
        }
    }

    public record Clock(long startGameTick, long sourceOffsetMicros, double speed) {
        public Clock {
            if (startGameTick < 0 || sourceOffsetMicros < 0 || !Double.isFinite(speed) || speed <= 0)
                throw new IllegalArgumentException("Invalid source clock");
        }
        public long timeAt(long gameTick, double partialTick) {
            if (gameTick < startGameTick || !Double.isFinite(partialTick) || partialTick < 0 || partialTick > 1)
                throw new IllegalArgumentException("Time precedes source segment or invalid partial tick");
            long elapsed = Math.multiplyExact(gameTick - startGameTick, GAME_TICK_MICROS);
            double time = sourceOffsetMicros + (elapsed + partialTick * GAME_TICK_MICROS) * speed;
            if (time >= Long.MAX_VALUE) throw new ArithmeticException("Source clock overflow");
            return (long)Math.floor(time);
        }
        public double poseSecondsAt(long gameTick, double partialTick, Clip clip) {
            return Math.min(timeAt(gameTick, partialTick), clip.durationMicros()) / 1_000_000.0;
        }
    }

    public enum Edge { ENTER, LEAVE, CANCEL }
    public record Identity(Actor actor, int eventIndex, Edge edge) {}
    public record Crossing(Identity identity, Event event, long sourceMicros) {}
    public record SavedCursor(Actor actor, long lastProcessedMicros, boolean cancelled) {
        public SavedCursor {
            if (lastProcessedMicros < -1) throw new IllegalArgumentException("Invalid event cursor");
        }
    }

    /** Walk every crossed edge; identical judge IDs remain distinct original TAE events. */
    public static final class Cursor {
        private final Clip clip;
        private final Actor actor;
        private final List<Crossing> crossings;
        private long lastProcessedMicros;
        private boolean cancelled;

        public Cursor(Clip clip, Actor actor) { this(clip, new SavedCursor(actor, -1L, false)); }

        public Cursor(Clip clip, SavedCursor saved) {
            if (clip.taeId() != saved.actor().taeId() || clip.hkxId() != saved.actor().hkxId())
                throw new IllegalArgumentException("Actor must use its own TAE/HKX contract");
            this.clip = clip;
            this.actor = saved.actor();
            this.lastProcessedMicros = saved.lastProcessedMicros();
            this.cancelled = saved.cancelled();
            var edges = new ArrayList<Crossing>();
            for (Event event : clip.events()) {
                if (!event.appliesTo(actor.stateInfo())) continue;
                // Original 3014 includes a -1..0 frame jump-table initialization.
                // Preserve its raw interval, but deliver pre-roll edges at segment activation.
                edges.add(crossing(event, Edge.ENTER, Math.max(0, event.startMicros())));
                if (event.endMicros() != -1) edges.add(crossing(event, Edge.LEAVE, Math.max(0, event.endMicros())));
            }
            edges.sort(Comparator.comparingLong(Crossing::sourceMicros)
                    .thenComparingInt(c -> c.event().index()).thenComparing(c -> c.identity().edge()));
            this.crossings = List.copyOf(edges);
        }

        public List<Crossing> advance(long sourceMicros) {
            if (sourceMicros < 0 || sourceMicros < lastProcessedMicros)
                throw new IllegalArgumentException("Source event time must be monotonic");
            if (cancelled || sourceMicros == lastProcessedMicros) return List.of();
            var result = new ArrayList<Crossing>();
            for (Crossing crossing : crossings)
                if (crossing.sourceMicros() > lastProcessedMicros && crossing.sourceMicros() <= sourceMicros)
                    result.add(crossing);
            lastProcessedMicros = sourceMicros;
            return List.copyOf(result);
        }

        /** Active spans survive restoration without replaying their ENTER edge. */
        public List<Event> activeSpans() {
            if (cancelled || lastProcessedMicros < 0) return List.of();
            return clip.events().stream().filter(e -> e.appliesTo(actor.stateInfo())
                    && e.startMicros() <= lastProcessedMicros
                    && (e.endMicros() == -1 || e.endMicros() > lastProcessedMicros)).toList();
        }

        /** Adapters clear attacks, looping visuals and attachments using CANCEL, once. */
        public List<Crossing> cancel() {
            if (cancelled) return List.of();
            var result = activeSpans().stream().sorted(Comparator.comparingInt(Event::index))
                    .map(e -> crossing(e, Edge.CANCEL, lastProcessedMicros)).toList();
            cancelled = true;
            return result;
        }

        public SavedCursor save() { return new SavedCursor(actor, lastProcessedMicros, cancelled); }

        private Crossing crossing(Event event, Edge edge, long time) {
            return new Crossing(new Identity(actor, event.index(), edge), event, time);
        }
    }
}
