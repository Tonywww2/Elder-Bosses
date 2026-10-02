package com.tonywww.elder_bosses.boss.promisedconsort.source;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTimeline.*;

/**
 * Executable segment queue and independent clone timelines. The entity adapter
 * supplies approach completion, engine exits, actual effect state and event
 * consumers. A clip's pose duration alone never advances or ends the queue.
 */
public final class PromisedConsortSourceSession {
    public enum EndReason { SEGMENT_TRANSITION, SOURCE_CLONE_END_EFFECT, CANCELLED, ENGINE_EXIT }
    public enum Gate { CONTINUATION_ATTACK, NEW_ATTACK }
    public record Notice(Crossing crossing, long worldMicros) {}
    public record Started(Actor actor, long startWorldMicros, double speed,PromisedConsortSourceTimeWarp warp) {
        public Started(Actor actor,long start,double speed) {this(actor,start,speed,PromisedConsortSourceTimeWarp.IDENTITY);}
    }
    public record Ended(Actor actor, EndReason reason, long worldMicros) {}
    public record Update(List<Started> started, List<Notice> events, List<Ended> ended) {
        public Update { started=List.copyOf(started); events=List.copyOf(events); ended=List.copyOf(ended); }
    }
    /** Values suitable for server synchronization; clone time is never the parent's pose time. */
    public record ActorSnapshot(Actor actor, long startWorldMicros, double speed, long sourceMicros,
                                double poseSeconds, List<Event> activeEvents) {
        public ActorSnapshot { activeEvents=List.copyOf(activeEvents); }
    }
    public record Snapshot(long actionSequence, int sourceAct, List<Integer> pendingSegments,
                           Set<Integer> observedEffects, List<ActorSnapshot> actors) {
        public Snapshot { pendingSegments=List.copyOf(pendingSegments); observedEffects=Set.copyOf(observedEffects); actors=List.copyOf(actors); }
    }
    public record RunningState(Actor actor,long startWorldMicros,double speed,int cloneEndEffect,SavedCursor cursor) {}
    public record SavedState(long sequence,long lastWorldMicros,int sourceAct,int segmentIndex,double speed,
                             List<Integer> pending,Set<Integer> observers,List<RunningState> actors) {}

    private final class Running {
        final Clip clip;
        final Actor actor;
        final Cursor cursor;
        final long startWorldMicros;
        final double speed;
        final int cloneEndEffect;
        final PromisedConsortSourceTimeWarp warp;
        Running(Clip clip, Actor actor, long start, double speed, int endEffect) {
            this(clip,actor,start,speed,endEffect,new SavedCursor(actor,-1,false));
        }
        Running(Clip clip, Actor actor, long start, double speed, int endEffect,SavedCursor saved) {
            this.clip=clip; this.actor=actor; this.cursor=new Cursor(clip,saved);
            this.startWorldMicros=start; this.speed=speed; this.cloneEndEffect=endEffect;
            this.warp=bank.timeWarps().getOrDefault(actor.taeId(),PromisedConsortSourceTimeWarp.IDENTITY);
        }
        long sourceAt(long worldMicros) {
            return warp.sourceAt((long)Math.floor(Math.max(0,worldMicros-startWorldMicros)*speed));
        }
        long worldAt(long sourceMicros) {
            return Math.addExact(startWorldMicros,(long)Math.ceil(warp.gameAt(sourceMicros)/speed));
        }
        ActorSnapshot snapshot(long worldMicros) {
            long time=sourceAt(worldMicros);
            return new ActorSnapshot(actor,startWorldMicros,speed,time,
                    Math.min(time,clip.durationMicros())/1_000_000.0,cursor.activeSpans());
        }
    }

    private final PromisedConsortSourceBank bank;
    private final long cloneSpawnWaitMicros;
    private final Deque<Integer> pending = new ArrayDeque<>();
    private final Set<Integer> observers = new LinkedHashSet<>();
    private final Map<Actor, Running> actors = new LinkedHashMap<>();
    private Running body;
    private long sequence = -1, lastWorldMicros = -1;
    private int sourceAct, segmentIndex;
    private double speed;

    public PromisedConsortSourceSession(PromisedConsortSourceBank bank) {this(bank,0);}
    public PromisedConsortSourceSession(PromisedConsortSourceBank bank,long cloneSpawnWaitMicros) {
        this.bank=Objects.requireNonNull(bank);if(cloneSpawnWaitMicros<0) throw new IllegalArgumentException("Invalid clone wait");this.cloneSpawnWaitMicros=cloneSpawnWaitMicros;
    }

    /** The movement adapter calls this only after the Entry approach is satisfied. */
    public Update start(PromisedConsortSourceAi.Entry entry, long actionSequence, int stateInfo,
                        long worldMicros, double sourceSpeed) {
        Objects.requireNonNull(entry);
        if (actionSequence <= sequence || worldMicros < 0 || !Double.isFinite(sourceSpeed) || sourceSpeed <= 0
                || stateInfo != 412 && stateInfo != 413 || entry.sourceSegments().isEmpty())
            throw new IllegalArgumentException("Invalid source session entry");
        if (body!=null) throw new IllegalStateException("End or cancel the current body before starting another source Act");
        validateSegments(entry.sourceSegments());
        requireMonotonic(worldMicros);
        // Independent clones keep their original sequence, speed and end effect
        // when the body begins another Act. Explicit cancel handles all actors.
        sequence=actionSequence; sourceAct=entry.act(); segmentIndex=0; speed=sourceSpeed;
        pending.clear(); pending.addAll(entry.sourceSegments());
        observers.clear(); observers.addAll(entry.observeEffects());
        body=createBody(pending.removeFirst(),stateInfo,worldMicros);
        return new Update(List.of(started(body)),List.of(),List.of());
    }

    /** Crossed events are delivered in world time, even when multiple 30fps edges fall in one 20Hz tick. */
    public Update advance(long worldMicros) {
        requireMonotonic(worldMicros);
        var started=new ArrayList<Started>(); var events=new ArrayList<Notice>(); var ended=new ArrayList<Ended>();
        var queue=new java.util.PriorityQueue<Notice>(Comparator.comparingLong(Notice::worldMicros)
                .thenComparingInt(n->n.crossing().identity().actor().slot())
                .thenComparingInt(n->n.crossing().event().index()).thenComparing(n->n.crossing().identity().edge()));
        for (Running actor : actors.values()) collect(actor,worldMicros,queue);
        while (!queue.isEmpty()) {
            Notice notice=queue.remove(); Crossing crossing=notice.crossing();
            Running running=actors.get(crossing.identity().actor());
            if (running == null) continue;
            events.add(notice);
            if (crossing.identity().edge()!=Edge.ENTER) continue;
            if (running.actor.slot()>=0 && (crossing.event().type()==66 || crossing.event().type()==67)
                    && crossing.event().referenceId()==running.cloneEndEffect) {
                stop(running,EndReason.SOURCE_CLONE_END_EFFECT,notice.worldMicros(),events,ended);
                continue;
            }
            if (running.actor.slot()!=-1) continue;
            for (var chain : bank.cloneChains()) if (chain.parentTaeId()==running.clip.taeId()) {
                for (var cue : chain.actors()) if (cue.triggerEventIndex()==crossing.event().index()) {
                    Actor identity=cue.actor(running.actor.actionSequence(),running.actor.segmentIndex(),running.actor.stateInfo());
                    if (actors.containsKey(identity)) throw new IllegalStateException("Clone event replayed");
                    Running clone=new Running(bank.requireClip(cue.taeId()),identity,Math.addExact(notice.worldMicros(),cloneSpawnWaitMicros),running.speed,20011581+cue.slot()*2);
                    actors.put(identity,clone); started.add(started(clone));
                    collect(clone,worldMicros,queue);
                }
            }
        }
        return new Update(started,events,ended);
    }

    /**
     * Commit the Lua decision to future goals. ClearSubGoal never zeroes the
     * current skeleton or replaces its clip before the behavior transition gate.
     * Missing 3027/3029 rejects the complete decision before any queue mutation.
     */
    public boolean interrupt(Actor expectedBody, PromisedConsortSourceAi.Interrupt decision) {
        Objects.requireNonNull(decision);
        if (!isCurrentBody(expectedBody) || !observers.contains(decision.effect())) return false;
        validateSegments(decision.sourceSegments());
        if (decision.clearSubGoals()) { pending.clear(); pending.addAll(decision.sourceSegments()); }
        observers.removeAll(decision.removeObservers()); observers.addAll(decision.addObservers());
        return true;
    }

    public boolean shoot(Actor expectedBody,PromisedConsortSourceAi.Shoot decision) {
        if(!isCurrentBody(expectedBody)) return false;
        validateSegments(decision.sourceSegments());
        if(decision.clearSubGoals()) pending.clear();
        pending.addAll(decision.sourceSegments());observers.addAll(decision.observers());return true;
    }

    /** Template Jump Table 23 = AI continuation, 86 = new AI attack cancellation. */
    public boolean gateOpen(Actor expectedBody, Gate gate) {
        return isCurrentBody(expectedBody) && bank.activeJumpTable(body.clip.taeId(),
                gate==Gate.CONTINUATION_ATTACK ? 23 : 86,body.cursor.activeSpans());
    }

    /** Called after advance and AI interrupt dispatch, preserving the HKS engine's gate ordering. */
    public Update continueAtGate(Actor expectedBody, long worldMicros) {
        return continueAtGate(expectedBody,worldMicros,Gate.CONTINUATION_ATTACK);
    }
    public Update continueAtGate(Actor expectedBody,long worldMicros,Gate gate) {
        requireMonotonic(worldMicros);
        if (!gateOpen(expectedBody,gate) || pending.isEmpty()) return empty();
        if (body.cursor.save().lastProcessedMicros()!=body.sourceAt(worldMicros))
            throw new IllegalStateException("Advance the source clock before accepting a transition");
        int state=body.actor.stateInfo(); var events=new ArrayList<Notice>(); var ended=new ArrayList<Ended>();
        stop(body,EndReason.SEGMENT_TRANSITION,worldMicros,events,ended);
        segmentIndex=Math.incrementExact(segmentIndex);
        body=createBody(pending.removeFirst(),state,worldMicros);
        return new Update(List.of(started(body)),events,ended);
    }

    /** Engine exit is explicit: reaching HKX duration is not sufficient evidence. */
    public Update engineExit(Actor expectedBody, long worldMicros) {
        requireMonotonic(worldMicros);
        if (!isCurrentBody(expectedBody)) return empty();
        var events=new ArrayList<Notice>(); var ended=new ArrayList<Ended>();
        int state=body.actor.stateInfo();
        stop(body,EndReason.ENGINE_EXIT,worldMicros,events,ended);
        if (!pending.isEmpty()) {
            segmentIndex=Math.incrementExact(segmentIndex);
            body=createBody(pending.removeFirst(),state,worldMicros);
            return new Update(List.of(started(body)),events,ended);
        }
        body=null; observers.clear();
        return new Update(List.of(),events,ended);
    }

    public Update cancel(long worldMicros) {
        requireMonotonic(worldMicros);
        var events=new ArrayList<Notice>(); var ended=new ArrayList<Ended>();
        for (Running actor : List.copyOf(actors.values())) stop(actor,EndReason.CANCELLED,worldMicros,events,ended);
        body=null; pending.clear(); observers.clear();
        return new Update(List.of(),events,ended);
    }

    public Optional<Actor> bodyActor() { return Optional.ofNullable(body).map(r->r.actor); }
    public Set<Integer> observedEffects() { return Set.copyOf(observers); }
    public Snapshot snapshot(long worldMicros) {
        if (worldMicros<lastWorldMicros) throw new IllegalArgumentException("Snapshot precedes processed events");
        return new Snapshot(sequence,sourceAct,List.copyOf(pending),observers,
                actors.values().stream().map(r->r.snapshot(worldMicros)).toList());
    }
    public SavedState save() {
        return new SavedState(sequence,lastWorldMicros,sourceAct,segmentIndex,speed,List.copyOf(pending),Set.copyOf(observers),
                actors.values().stream().map(r->new RunningState(r.actor,r.startWorldMicros,r.speed,r.cloneEndEffect,r.cursor.save())).toList());
    }
    public void restore(SavedState saved,long timeShift) {
        if(!actors.isEmpty() || body!=null || timeShift<0) throw new IllegalStateException("Invalid source restore");
        validateSegments(saved.pending());sequence=saved.sequence();sourceAct=saved.sourceAct();segmentIndex=saved.segmentIndex();speed=saved.speed();
        lastWorldMicros=saved.lastWorldMicros()<0?-1:Math.addExact(saved.lastWorldMicros(),timeShift);
        pending.addAll(saved.pending());observers.addAll(saved.observers());
        for(var state:saved.actors()) {
            if(state.actor().hkxId()!=bank.requireClip(state.actor().taeId()).hkxId() || !state.actor().equals(state.cursor().actor()))
                throw new IllegalArgumentException("Inconsistent saved source actor");
            Running running=new Running(bank.requireClip(state.actor().taeId()),state.actor(),Math.addExact(state.startWorldMicros(),timeShift),state.speed(),state.cloneEndEffect(),state.cursor());
            if(actors.put(state.actor(),running)!=null) throw new IllegalArgumentException("Duplicate restored actor");
            if(state.actor().slot()==-1) {
                if(body!=null) throw new IllegalArgumentException("Duplicate restored source body");body=running;
            }
        }
    }

    private Running createBody(int taeId, int stateInfo, long start) {
        Clip clip=bank.requireClip(taeId);
        Actor actor=new Actor(sequence,segmentIndex,-1,taeId,clip.hkxId(),stateInfo);
        Running running=new Running(clip,actor,start,speed,-1); actors.put(actor,running); return running;
    }
    private boolean isCurrentBody(Actor expected) { return body!=null && body.actor.equals(expected); }
    private void validateSegments(List<Integer> segments) { segments.forEach(bank::requireClip); }
    private void requireMonotonic(long worldMicros) {
        if (worldMicros<0 || worldMicros<lastWorldMicros) throw new IllegalArgumentException("Source world time moved backwards");
        lastWorldMicros=worldMicros;
    }
    private static Started started(Running actor) { return new Started(actor.actor,actor.startWorldMicros,actor.speed,actor.warp); }
    private static Update empty() { return new Update(List.of(),List.of(),List.of()); }
    private static void collect(Running actor,long worldMicros,java.util.Queue<Notice> queue) {
        if(worldMicros<actor.startWorldMicros) return;
        for (Crossing crossing : actor.cursor.advance(actor.sourceAt(worldMicros)))
            queue.add(new Notice(crossing,actor.worldAt(crossing.sourceMicros())));
    }
    private void stop(Running actor,EndReason reason,long worldMicros,List<Notice> events,List<Ended> ended) {
        // A batched cursor may have sampled past an early clone-end flag. Cancel
        // using the event's exact time, so attack windows do not linger to tick end.
        Cursor atEnd=new Cursor(actor.clip,new SavedCursor(actor.actor,actor.sourceAt(worldMicros),false));
        for (Crossing crossing : atEnd.cancel()) events.add(new Notice(crossing,worldMicros));
        actor.cursor.cancel(); actors.remove(actor.actor); ended.add(new Ended(actor.actor,reason,worldMicros));
    }
}
