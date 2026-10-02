package com.tonywww.elder_bosses.boss.promisedconsort.source;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortCloneEntity;
import com.tonywww.elder_bosses.platforms.registry.ModEntities;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTimeline.*;

/**
 * Visual rehearsal on the real entity, with source clocks, motion and clones.
 * This explicit command path deals no damage and does not claim original AI or
 * behavior completion. The final one-second hold is a review policy only.
 */
public final class PromisedConsortSourceRehearsal {
    private final PromisedConsortEntity owner;
    private final PromisedConsortSourceSession session;
    private final Map<Actor,PromisedConsortCloneEntity> clones=new LinkedHashMap<>();
    private final PromisedConsortSourcePlayback body;
    private final long startMicros,endMicros;
    private final float yaw;
    private Vec3 previousDisplacement=Vec3.ZERO;
    private boolean closed;

    public PromisedConsortSourceRehearsal(PromisedConsortEntity owner,int taeId,int stateInfo,long startMicros) {
        this.owner=owner; this.startMicros=startMicros; this.yaw=owner.getYRot();
        var bank=PromisedConsortSourceAssets.bank();
        var clip=bank.requireClip(taeId);
        long duration=clip.durationMicros()+1_000_000L;
        for (var chain : bank.cloneChains()) if (chain.parentTaeId()==taeId)
            for (var cue : chain.actors()) duration=Math.max(duration,cue.spawnSourceMicros()+bank.requireClip(cue.taeId()).durationMicros()+1_000_000L);
        this.endMicros=Math.addExact(startMicros,duration);
        this.session=new PromisedConsortSourceSession(bank);
        var entry=new PromisedConsortSourceAi.Entry(0,List.of(taeId),Set.of(),null,"source_visual_rehearsal");
        var initial=session.start(entry,0,stateInfo,startMicros,1);
        this.body=PromisedConsortSourcePlayback.of(initial.started().get(0));
        owner.setSourcePlayback(body);
    }
    public int durationTicks() { return (int)Math.ceil((endMicros-startMicros)/50_000.0)+20; }

    /** Returns true after the explicit rehearsal window, not an inferred engine exit. */
    public boolean tick(long now) {
        if (closed) return true;
        owner.getNavigation().stop(); owner.setNoGravity(true); owner.setDeltaMovement(Vec3.ZERO);
        float heading=body.yawAt(now,yaw);
        owner.setYRot(heading); owner.setYBodyRot(heading); owner.setYHeadRot(heading);
        if (now<startMicros) return false;
        Vec3 requested=body.displacement(now,yaw);
        owner.move(MoverType.SELF,requested.subtract(previousDisplacement));
        previousDisplacement=requested;
        dispatch(session.advance(now));
        if (now>=endMicros) { close(now); return true; }
        return false;
    }
    private void dispatch(PromisedConsortSourceSession.Update update) {
        for (var end : update.ended()) {
            var clone=clones.remove(end.actor());
            if (clone!=null) clone.discard();
        }
        for (var start : update.started()) if (start.actor().slot()>=0) {
            if (update.ended().stream().anyMatch(end -> end.actor().equals(start.actor()))) continue;
            var clone=ModEntities.PROMISED_CONSORT_CLONE.get().create(owner.level());
            if (clone==null) throw new IllegalStateException("Cannot create source clone rehearsal actor");
            // Review origin only. Production spawn transforms still require map/behavior integration.
            clone.configureSource(owner,PromisedConsortSourcePlayback.of(start),owner.position().subtract(previousDisplacement),yaw);
            if (owner.level().addFreshEntity(clone)) clones.put(start.actor(),clone);
            else clone.discard();
        }
    }
    public void close(long now) {
        if (closed) return;
        dispatch(session.cancel(Math.max(now,startMicros)));
        clones.values().forEach(PromisedConsortCloneEntity::discard); clones.clear();
        closed=true;
    }
}
