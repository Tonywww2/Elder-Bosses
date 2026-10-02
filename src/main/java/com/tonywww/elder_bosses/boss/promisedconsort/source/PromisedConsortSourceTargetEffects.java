package com.tonywww.elder_bosses.boss.promisedconsort.source;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import java.util.*;
import net.minecraft.world.entity.LivingEntity;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceExecutionData.*;

/** Original10690 ->10691 bloodflame cadence; the player bleed meter is an MC adapter. */
public final class PromisedConsortSourceTargetEffects {
    private final PromisedConsortEntity owner;
    private final PromisedConsortSourceCombat combat;
    private final Map<UUID,Bleed> meters=new HashMap<>();
    private static final class Bleed {double amount;long end,next,through;}
    public record Meter(UUID target,double amount,long end,long next,long through) {}
    public record SavedState(List<Meter> meters) {}
    public PromisedConsortSourceTargetEffects(PromisedConsortSourceCombat combat,PromisedConsortEntity owner) {this.combat=combat;this.owner=owner;}
    public void apply(LivingEntity target,int id,long now) {
        if(id!=10690) return;
        var row=PromisedConsortSourceExecutionData.get().effect(id);var meter=meters.computeIfAbsent(target.getUUID(),uuid->new Bleed());
        meter.end=now+Math.round(owner.sourceConfig().number("bleed.duration_ticks")*50_000);
        meter.next=now+Math.round(owner.sourceConfig().number("bleed.pulse_interval_ticks")*50_000);meter.through=now;
    }
    public void tick(long now) {
        var data=PromisedConsortSourceExecutionData.get();var root=data.effect(10690);var pulse=data.effect(integer(root,"cycleOccurrenceSpEffectId"));
        long interval=Math.round(owner.sourceConfig().number("bleed.pulse_interval_ticks")*50_000);
        for(var entry:List.copyOf(meters.entrySet())) {
            var target=owner.level() instanceof net.minecraft.server.level.ServerLevel server?server.getEntity(entry.getKey()):null;
            if(!(target instanceof LivingEntity living) || !living.isAlive()) {meters.remove(entry.getKey());continue;}
            Bleed meter=entry.getValue();
            while(meter.next<=now && meter.next<=meter.end) {
                meter.amount+=owner.sourceConfig().number("bleed.buildup_per_pulse");meter.next+=interval;
                if(meter.amount>=owner.sourceConfig().number("bleed.threshold")) {
                    meter.amount-=owner.sourceConfig().number("bleed.threshold");
                    // ER points are mapped to health points at1/100 here;
                    // its max-health percentage remains the original15%.
                    float damage=(float)(living.getMaxHealth()*owner.sourceConfig().number("bleed.health_ratio")+owner.sourceConfig().number("bleed.flat"));
                    living.hurt(owner.level().damageSources().mobAttack(owner),damage);
                    combat.visuals().pulse(4080,living.getBoundingBox().getCenter(),living.getLookAngle(),now);
                }
            }
            if(now>meter.end) meter.amount=Math.max(0,meter.amount-owner.sourceConfig().number("bleed.decay_per_second")*(now-Math.max(meter.through,meter.end))/1_000_000.0);
            meter.through=now;if(meter.amount==0 && now>meter.end) meters.remove(entry.getKey());
        }
    }
    public SavedState save() {return new SavedState(meters.entrySet().stream().map(e->new Meter(e.getKey(),e.getValue().amount,e.getValue().end,e.getValue().next,e.getValue().through)).toList());}
    public void restore(SavedState saved,long shift) {
        meters.clear();for(var s:saved.meters()) {var m=new Bleed();m.amount=s.amount();m.end=Math.addExact(s.end(),shift);m.next=Math.addExact(s.next(),shift);m.through=Math.addExact(s.through(),shift);meters.put(s.target(),m);}
    }
    public void clear() {meters.clear();}
}
