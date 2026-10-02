package com.tonywww.elder_bosses.boss.promisedconsort.source;

import java.util.*;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTimeline.Actor;

/** A timed contact shield shared by a segment's windows and descendant bullets. */
public final class PromisedConsortSourceSegmentHits {
    public record Claim(Actor actor,UUID target,long expiresAt) {
        public Claim {Objects.requireNonNull(actor);Objects.requireNonNull(target);}
    }
    private record Key(Actor actor,UUID target) {}
    private final Map<Key,Claim> claimed=new LinkedHashMap<>();
    public boolean available(Actor actor,UUID target,long now) {
        var old=claimed.get(new Key(actor,target));return old==null || now>=old.expiresAt();
    }
    /** Consume a contact before damage/guard resolution, as with the previous HitRegistry. */
    public boolean claim(Actor actor,UUID target,long now,long immunityMicros) {
        if(!available(actor,target,now)) return false;
        claimed.put(new Key(actor,target),new Claim(actor,target,Math.addExact(now,immunityMicros)));return true;
    }
    public List<Claim> expire(long now) {
        var removed=new ArrayList<Claim>();var iterator=claimed.values().iterator();
        while(iterator.hasNext()) {var claim=iterator.next();if(now>=claim.expiresAt()) {removed.add(claim);iterator.remove();}}
        return List.copyOf(removed);
    }
    public List<Claim> nextSegment(Actor actor) {
        var removed=new ArrayList<Claim>();var iterator=claimed.values().iterator();
        while(iterator.hasNext()) {var claim=iterator.next();if(claim.actor().slot()==actor.slot() && !claim.actor().equals(actor)) {removed.add(claim);iterator.remove();}}
        return List.copyOf(removed);
    }
    public static int maximumHits(boolean simple,int configured) {return simple?configured:1;}
    public void retain(Set<Actor> live) {claimed.values().removeIf(c->!live.contains(c.actor()));}
    public List<Claim> save() {return List.copyOf(claimed.values());}
    public void restore(List<Claim> saved,long shift) {
        claimed.clear();for(var c:saved) claimed.put(new Key(c.actor(),c.target()),new Claim(c.actor(),c.target(),Math.addExact(c.expiresAt(),shift)));
    }
    public void clear() {claimed.clear();}
}
