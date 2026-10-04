package com.tonywww.elder_bosses.boss.promisedconsort.source;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTimeline.Actor;

/** Source AI, approach, segment, event and clone orchestration for an entity adapter. */
public final class PromisedConsortSourceController {
    public interface Host {
        long worldMicros();
        PromisedConsortSourceAi.Context context();
        PromisedConsortSourceAi.Rolls rolls();
        PromisedConsortSourceAi.CoolTime coolTime();
        double selectionRoll();
        double sourceSpeed();
        default java.util.Map<Integer,Double> selectionWeights(java.util.Map<Integer,Double> weights) {return weights;}
        default boolean allowSelection() { return true; }
        default long cloneSpawnWaitMicros() {return 0;}
        void beginApproachOrEngineGoal(PromisedConsortSourceAi.Entry entry);
        boolean approachComplete(PromisedConsortSourceAi.Entry entry);
        boolean engineGoalComplete(PromisedConsortSourceAi.Entry entry);
        default PromisedConsortSourceAi.Entry resolveEngineEntry(PromisedConsortSourceAi.Entry entry) { return entry; }
        /** Process start/event/end identities with their original timestamps, including clone actors. */
        void dispatch(PromisedConsortSourceSession.Update update);
        /** Boss effects activated since the last source update; clone effects stay on their own actors. */
        Set<Integer> activatedBodyEffects();
        /** Lua Interrupt checks observed effect activations before Shoot, consuming that frame's input. */
        default Optional<PromisedConsortSourceAi.Shoot> pollShootReaction(boolean effectInterrupt) { return Optional.empty(); }
        void setSourceTimer(int slot,double seconds);
        /** Actual behavior/goal exit; the host must not derive this from a guessed TAE tail. */
        boolean bodyEngineExited(Actor actor);
        default void bodyEntryCompleted(PromisedConsortSourceAi.Entry completed) {}
        void synchronize(PromisedConsortSourceSession.Snapshot snapshot);
    }
    private final Host host;
    private final PromisedConsortSourceSession session;
    private PromisedConsortSourceAi.Entry entry;
    private long sequence=-1,lastTickMicros=-1;
    private boolean started;
    private PromisedConsortSourceAi.Entry shootApproach;
    private boolean shootCancellation;
    public record SavedState(PromisedConsortSourceSession.SavedState session,PromisedConsortSourceAi.Entry entry,
                             long sequence,long lastTickMicros,boolean started,
                             PromisedConsortSourceAi.Entry shootApproach,boolean shootCancellation) {}

    public PromisedConsortSourceController(Host host,PromisedConsortSourceBank bank) {
        this.host=Objects.requireNonNull(host); this.session=new PromisedConsortSourceSession(bank,host.cloneSpawnWaitMicros());
    }

    /** Server simulation only. Rendering reads snapshots and never drives this method. */
    public void tick() {
        long now=host.worldMicros();
        if (now<0 || now<lastTickMicros) throw new IllegalStateException("Source server time moved backwards");
        if (now==lastTickMicros) return;
        lastTickMicros=now;
        host.dispatch(session.advance(now));
        var actor=session.bodyActor();
        boolean effectInterrupt=actor.map(this::dispatchEffectInterrupt).orElse(false);
        host.pollShootReaction(effectInterrupt).ifPresent(this::shoot);
        if (actor.isPresent()) {
            if(shootApproach!=null && (session.gateOpen(actor.get(),PromisedConsortSourceSession.Gate.NEW_ATTACK)
                    || host.bodyEngineExited(actor.get()))) {
                host.dispatch(session.engineExit(actor.get(),now));
                entry=shootApproach;shootApproach=null;started=false;shootCancellation=false;
                host.beginApproachOrEngineGoal(entry);
            }
            var continuation=session.continueAtGate(actor.get(),now,shootCancellation
                    ? PromisedConsortSourceSession.Gate.NEW_ATTACK:PromisedConsortSourceSession.Gate.CONTINUATION_ATTACK);
            host.dispatch(continuation);
            if (!continuation.started().isEmpty()) {shootCancellation=false;host.dispatch(session.advance(now));}
            // A continuation has a new identity; an exit response for the old
            // segment cannot cancel the newly started segment.
            if (session.bodyActor().filter(actor.get()::equals).isPresent() && host.bodyEngineExited(actor.get())) {
                var exit=session.engineExit(actor.get(),now); host.dispatch(exit);
                if (session.bodyActor().isEmpty()) {
                    var completed=entry;
                    entry=shootApproach;shootApproach=null;started=false;shootCancellation=false;
                    if(entry!=null) host.beginApproachOrEngineGoal(entry);
                    else host.bodyEntryCompleted(completed);
                }
                else host.dispatch(session.advance(now));
            }
        }
        // Resolve ready goals immediately, with a bounded budget for zero-time
        // movement/facing goals. A pending approach is advanced only once per tick.
        for(int decisions=0;decisions<8;decisions++) {
            if (entry!=null && !started) {
                if (entry.sourceSegments().isEmpty()) {
                    if (host.engineGoalComplete(entry)) entry=null;
                } else if (host.approachComplete(entry)) {
                    sequence=Math.incrementExact(sequence);
                    host.dispatch(session.start(entry,sequence,host.context().phaseTwo() ? 413 : 412,now,host.sourceSpeed()));
                    started=true;
                    host.dispatch(session.advance(now));
                }
            }
            if(entry!=null || session.bodyActor().isPresent() || !host.allowSelection()) break;
            var selected=PromisedConsortSourceAi.choose(host.selectionWeights(PromisedConsortSourceAi.weights(host.context(),host.coolTime())),host.selectionRoll());
            if (selected.isPresent()) {
                entry=host.resolveEngineEntry(PromisedConsortSourceAi.entry(selected.getAsInt(),host.context(),host.rolls()));
                host.beginApproachOrEngineGoal(entry);
            } else break;
        }
        host.synchronize(session.snapshot(now));
    }

    /** Preserve the original if-branch priority; one matching activation branch per dispatch. */
    private boolean dispatchEffectInterrupt(Actor actor) {
        Set<Integer> activated=Set.copyOf(host.activatedBodyEffects());
        var context=host.context();
        for (int effect : PromisedConsortSourceAi.SPECIAL_EFFECT_BRANCH_ORDER) {
            if (!activated.contains(effect) || !context.has(effect) || !session.observedEffects().contains(effect)) continue;
            var decision=PromisedConsortSourceAi.onSpecialEffect(effect,context,host.rolls());
            if (session.interrupt(actor,decision)) {
                decision.timerSeconds().forEach(host::setSourceTimer);
                if(decision.clearSubGoals()) {shootApproach=null;shootCancellation=false;}
            }
            return true;
        }
        return activated.stream().anyMatch(effect->context.has(effect) && session.observedEffects().contains(effect));
    }

    public void cancel() {
        host.dispatch(session.cancel(host.worldMicros())); entry=null; started=false;
        shootApproach=null;shootCancellation=false;
        host.synchronize(session.snapshot(host.worldMicros()));
    }
    /** Explicit phase, entry or throw transition; keeps source identities and clocks. */
    public void startScript(int taeId) {
        startEntry(new PromisedConsortSourceAi.Entry(-1,java.util.List.of(taeId),Set.of(),null,"source_script"));
    }
    public void startEntry(PromisedConsortSourceAi.Entry next) {
        cancel();
        entry=Objects.requireNonNull(next);
        sequence=Math.incrementExact(sequence);
        long now=host.worldMicros();
        host.dispatch(session.start(entry,sequence,host.context().phaseTwo()?413:412,now,host.sourceSpeed()));
        started=true;
        host.dispatch(session.advance(now));
        host.synchronize(session.snapshot(now));
    }
    public void prepareEntry(PromisedConsortSourceAi.Entry next) {
        cancel();entry=host.resolveEngineEntry(Objects.requireNonNull(next));started=false;host.beginApproachOrEngineGoal(entry);
    }
    public Optional<PromisedConsortSourceAi.Entry> entry() { return Optional.ofNullable(entry); }
    public void shoot(PromisedConsortSourceAi.Shoot decision) {
        var body=session.bodyActor();
        if(body.isPresent()) {
            if(!session.shoot(body.get(),decision)) return;
            if(decision.clearSubGoals()) {
                shootCancellation=true;
                shootApproach=decision.approachSeconds()>0?new PromisedConsortSourceAi.Entry(-2,java.util.List.of(),Set.of(),
                        new PromisedConsortSourceAi.Approach(java.util.List.of(10.0,decision.approachSeconds())),"ShootApproach"):null;
            }
        } else if(decision.clearSubGoals() || !decision.sourceSegments().isEmpty()) {
            entry=new PromisedConsortSourceAi.Entry(-2,decision.sourceSegments(),decision.observers(),
                    decision.approachSeconds()>0?new PromisedConsortSourceAi.Approach(java.util.List.of(10.0,decision.approachSeconds())):null,"ShootApproach");
            started=false;host.beginApproachOrEngineGoal(entry);
        }
    }
    public SavedState save() {return new SavedState(session.save(),entry,sequence,lastTickMicros,started,shootApproach,shootCancellation);}
    public void restore(SavedState saved,long timeShift) {
        session.restore(saved.session(),timeShift);entry=saved.entry();sequence=saved.sequence();started=saved.started();
        shootApproach=saved.shootApproach();shootCancellation=saved.shootCancellation();
        lastTickMicros=saved.lastTickMicros()<0?-1:Math.addExact(saved.lastTickMicros(),timeShift);
    }
}
