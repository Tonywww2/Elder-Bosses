package com.tonywww.elder_bosses.boss.promisedconsort.source;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** ThrowParam34522000 and player70890 state, including release on every invalidation path. */
public final class PromisedConsortSourceGrab {
    private static final Map<UUID,PromisedConsortSourceGrab> HELD=new HashMap<>();
    private static final Set<PromisedConsortSourceGrab> ACTIVE=Collections.newSetFromMap(new WeakHashMap<>());
    private final PromisedConsortSourceCombat combat;
    private final PromisedConsortEntity owner;
    private final Set<UUID> charmed=new HashSet<>(),heartStolen=new HashSet<>();
    private final Map<UUID,Long> charmStart=new HashMap<>();
    private ServerPlayer victim,pending;
    private Vec3 releaseAnchor;
    private long start;
    private boolean oldGravity,oldPhysics,charmApplied;
    public record SavedState(Set<UUID> charmed,Set<UUID> heartStolen,Map<UUID,Long> charmStart,boolean interruptedThrow) {}
    public PromisedConsortSourceGrab(PromisedConsortSourceCombat combat,PromisedConsortEntity owner) {this.combat=combat;this.owner=owner;ACTIVE.add(this);}
    public static void clearCharm(LivingEntity target) {
        for(var grab:List.copyOf(ACTIVE)) {grab.charmed.remove(target.getUUID());grab.heartStolen.remove(target.getUUID());grab.charmStart.remove(target.getUUID());if(grab.victim==target) grab.release();}
    }
    public void applyCharm(LivingEntity target) {charmed.add(target.getUUID());charmStart.putIfAbsent(target.getUUID(),combat.worldMicros());}
    public net.minecraft.nbt.ListTag charmTags() {
        var list=new net.minecraft.nbt.ListTag();for(var id:charmed) {var tag=new net.minecraft.nbt.CompoundTag();tag.putUUID("Player",id);tag.putLong("Born",charmStart.get(id));list.add(tag);}return list;
    }
    public void close() {release();ACTIVE.remove(this);}
    public static boolean held(LivingEntity entity) {return HELD.containsKey(entity.getUUID());}
    public boolean active() {return victim!=null || pending!=null;}
    public SavedState save() {return new SavedState(Set.copyOf(charmed),Set.copyOf(heartStolen),Map.copyOf(charmStart),active());}
    public void restore(SavedState saved,long shift) {charmed.clear();charmed.addAll(saved.charmed());heartStolen.clear();heartStolen.addAll(saved.heartStolen());saved.charmStart().forEach((id,t)->charmStart.put(id,t+shift));}
    public Set<Integer> targetEffects(LivingEntity target) {
        if(target==null) return Set.of();
        if(owner.sourceConfig().flag("grab.instant_kill_enabled") && heartStolen.contains(target.getUUID())) return Set.of(19680,19681);
        return charmed.contains(target.getUUID())?Set.of(19681):Set.of();
    }
    public boolean tryCapture(LivingEntity target,long sourceWorldTime) {
        if(owner.isDisengaging() || active() || !(target instanceof ServerPlayer player) || player.isSpectator() || player.hasDisconnected()
                || HELD.containsKey(player.getUUID()) || player.level()!=owner.level() || !player.isAlive()
                || owner.distanceTo(player)>owner.sourceConfig().number("grab.max_capture_distance") || Math.abs(owner.getY()-player.getY())>owner.sourceConfig().number("grab.max_capture_height")) return false;
        pending=player;return true;
    }
    public void tick(long now) {
        charmed.removeIf(id->{var player=owner.level().getServer().getPlayerList().getPlayer(id);return player!=null && !player.isAlive();});
        heartStolen.retainAll(charmed);
        charmStart.keySet().retainAll(charmed);
        if(pending!=null) {
            victim=pending;pending=null;start=now;releaseAnchor=victim.position();oldGravity=victim.isNoGravity();oldPhysics=victim.noPhysics;
            charmApplied=false;HELD.put(victim.getUUID(),this);
            victim.stopRiding();victim.stopUsingItem();victim.setNoGravity(true);victim.noPhysics=true;
            combat.startThrow();owner.setTarget(victim);owner.setSourceGrab(victim.getUUID(),start,owner.getYRot()+180);
        }
        if(victim==null) return;
        if(owner.isRemoved() || !owner.isAlive() || owner.isSourceDefeated() || victim.isRemoved() || !victim.isAlive()
                || victim.hasDisconnected() || victim.isSpectator() || victim.level()!=owner.level()) {release();return;}
        var clock=combat.bodyFrame();long elapsed=clock==null?0:clock.playback.sourceMicros(now);
        // Original target TAE applies19682 at frame3. The player HKS combines
        // this with an existing19681 to set19680 on a repeated capture.
        if(owner.sourceConfig().flag("grab.instant_kill_enabled") && elapsed>=100_000 && charmed.contains(victim.getUUID())) heartStolen.add(victim.getUUID());
        // First charm is authored at181/30 seconds, not at the end of the movie.
        if(!charmApplied && elapsed>=6_033_333) {applyCharm(victim);charmApplied=true;combat.audio().charm(victim.position());}
        var frame=combat.bodyFrame();
        if(frame==null || frame.playback.actor().taeId()!=4100 && frame.playback.actor().taeId()!=20012) {release();return;}
        // Source231 is an actor-root anchor, not Miquella's hand bone. The
        // target motion itself lifts the player body; subtracting eye height
        // would lower every source joint and visibly break the paired motion.
        Vec3 anchor=frame.point(231,now);
        victim.connection.teleport(anchor.x,anchor.y,anchor.z,owner.getYRot()+180,0);
        victim.setDeltaMovement(Vec3.ZERO);victim.fallDistance=0;
        // TAE227 sets EZ State Flag0 at280/30s. ThrowDef HKS uses this
        // gate with19680 to enter charm death; this is not clip completion.
        if(owner.sourceConfig().flag("grab.instant_kill_enabled") && elapsed>=9_333_333 && heartStolen.contains(victim.getUUID())) {
            ServerPlayer target=victim;release();
            target.hurt(owner.level().damageSources().genericKill(),Float.MAX_VALUE);
            charmed.remove(target.getUUID());heartStolen.remove(target.getUUID());
        } else if(elapsed>=9_666_667) {
            // JumpTable51 ends and69/87 plus input320 begin at frame290.
            release();
        }
    }
    public void release() {
        pending=null;owner.setSourceGrab(null,0,0);if(victim==null) return;
        ServerPlayer target=victim;victim=null;HELD.remove(target.getUUID(),this);
        target.setNoGravity(oldGravity);target.noPhysics=oldPhysics;target.fallDistance=0;target.setDeltaMovement(Vec3.ZERO);
        if(target.isAlive() && !target.hasDisconnected() && target.level()==owner.level()) {
            Vec3 safe=releaseAnchor;
            if(safe!=null && owner.level().noCollision(target,target.getBoundingBox().move(safe.subtract(target.position()))))
                target.connection.teleport(safe.x,safe.y,safe.z,target.getYRot(),target.getXRot());
        }
    }
}
