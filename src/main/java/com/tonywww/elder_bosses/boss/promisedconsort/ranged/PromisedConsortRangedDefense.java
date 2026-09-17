package com.tonywww.elder_bosses.boss.promisedconsort.ranged;

import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionSoundPlan;
import com.tonywww.elder_bosses.combat.geometry.Vec2;
import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSkillConfigSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortRangedConfig;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortActionSnapshot;
import com.tonywww.elder_bosses.combat.action.ActionLifecycleEvent;
import com.tonywww.elder_bosses.combat.action.ActionPhase;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.List;

public final class PromisedConsortRangedDefense {
    private static final java.util.Set<AbstractArrow> REFLECTED_ARROWS=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private final PromisedConsortEntity boss;
    private final PromisedConsortRangedConfig config;
    private final PromisedConsortSkillConfigSnapshot skills;
    private final PromisedConsortRangedState history;
    private long globalReady;
    private long defenseReady;
    private int consecutive;
    private double absorbed;
    private int reflected;
    private boolean currentCounter;
    private long lastReflectionTick = -1;
    private int reflectedThisTick;
    private long lastFeedbackTick = Long.MIN_VALUE;

    public PromisedConsortRangedDefense(PromisedConsortEntity boss, PromisedConsortRangedConfig config,
            PromisedConsortSkillConfigSnapshot skills, PromisedConsortRangedState history) {
        this.boss=boss; this.config=config; this.skills=skills; this.history=history;
    }

    public boolean variant(PromisedConsortActionId action, LivingEntity target) {
        var skill=skills.get(action);
        double distance=horizontal(target.position());
        return config.enabled() && skill.hasRangedCounter() && boss.isRangedTarget(target) && boss.level().getGameTime()>=globalReady
                && distance>=skill.number("ranged_counter.min_target_distance") && distance<=skill.number("ranged_counter.max_target_distance");
    }

    public double weight(PromisedConsortActionId action, LivingEntity target) {
        var skill=skills.get(action);
        if (!action.rangedDefense()) {
            if(!variant(action,target)) return config.meleeWeightMultiplier(action, horizontal(target.position()));
            var timeline=boss.rangedTimeline(action);
            Vec3 origin=boss.position(),forward=target.position().subtract(origin).multiply(1,0,1).normalize();
            var desired=PromisedConsortRangedPath.create(action,skill.rangedVariant(),timeline,origin,target.position(),forward,point->true);
            var reachable=PromisedConsortRangedPath.create(action,skill.rangedVariant(),timeline,origin,target.position(),forward,point->
                point.subtract(boss.combatCenter()).horizontalDistance()<=boss.rangedArenaRadius()
                && boss.level().noCollision(boss,boss.getBoundingBox().move(point.subtract(origin))));
            double intended=desired.origin().distanceTo(desired.corner())+desired.corner().distanceTo(desired.end());
            double actual=reachable.origin().distanceTo(reachable.corner())+reachable.corner().distanceTo(reachable.end());
            return pursuitWeight(config,skill,true,intended,actual);
        }
        long now=boss.level().getGameTime();
        if (!config.enabled() || !boss.isRangedTarget(target) || now<globalReady || now<defenseReady
                || consecutive>=config.maxConsecutiveDefenses() || horizontal(target.position())<skill.number("min_target_distance")
                || horizontal(target.position())>skill.number("max_target_distance")) return 0;
        boolean eligible;
        if (action==PromisedConsortActionId.GRAVITY_REFLECTION) eligible=incoming(skill,skill.integer("trigger.incoming_prediction_ticks"))
                .stream().filter(arrow->!crosses(arrow,skill,skill.integer("windup_ticks")-1))
                .limit(skill.integer("trigger.minimum_incoming_projectiles")).count()>=skill.integer("trigger.minimum_incoming_projectiles");
        else {
            int window=skill.integer("trigger.threat_window_ticks");
            eligible=history.threatDamage(target.getUUID(),now,window)>=skill.number("trigger.minimum_threat_damage");
            if (action==PromisedConsortActionId.GRAVITY_BULWARK) eligible|=history.threatAttempts(target.getUUID(),now,window)>=skill.integer("trigger.minimum_attack_attempts");
        }
        return eligible ? skill.number("trigger.condition_weight_multiplier") : 0;
    }

    public static double pursuitWeight(PromisedConsortRangedConfig config, PromisedConsortSkillConfigSnapshot.Skill skill,
            boolean rangedVariant, double intendedDistance, double reachableDistance) {
        if (!config.enabled() || !rangedVariant || !skill.hasRangedCounter()) return 1;
        return intendedDistance <= 0.001 ? 0 : skill.number("ranged_counter.selection_weight_multiplier")
                * config.pursuitSelectionWeightMultiplier() * Math.min(1, reachableDistance / intendedDistance);
    }

    public void prepare(boolean counter) { currentCounter=counter; }
    public boolean currentCounter() { return currentCounter; }

    public void lifecycle(ActionLifecycleEvent event) {
        var action=PromisedConsortActionId.fromSerializedName(event.actionId()).orElseThrow();
        if (event.outcome()==ActionLifecycleEvent.Outcome.STARTED) {
            absorbed=0; reflected=0; lastReflectionTick=-1; reflectedThisTick=0;
            return;
        }
        long now=boss.level().getGameTime();
        if (action.rangedDefense() || currentCounter) globalReady=now+config.globalCooldownTicks();
        if (action.rangedDefense()) {
            defenseReady=now+config.defenseSharedCooldownTicks();
            consecutive++;
        } else if(event.outcome()==ActionLifecycleEvent.Outcome.COMPLETED) consecutive=0;
        if (action.rangedDefense()) feedback(PromisedConsortActionSoundPlan.cue("defense_close", PromisedConsortActionSoundPlan.Sound.DASH, 0.2F, 1.3F), boss.position());
        absorbed=0; reflected=0; currentCounter=false;
    }

    public double defend(PromisedConsortActionSnapshot action, DamageSource source, double amount, boolean ranged) {
        if (!config.enabled() || action==null || !action.actionId().rangedDefense() || action.actionPhase()!=ActionPhase.ACTIVE) return amount;
        if (action.actionId()==PromisedConsortActionId.GRAVITY_REFLECTION) return amount;
        var skill=skills.get(action.actionId());
        if (!ranged) {
            boolean melee=source.getDirectEntity() instanceof net.minecraft.world.entity.player.Player
                && !source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
                && !PromisedConsortRangedDamage.excluded(source,config);
            return melee ? amount*skill.number("melee_damage_multiplier") : amount;
        }
        Vec3 sourcePoint=source.getSourcePosition();
        if (!inArc(sourcePoint,skill.number("defense_arc_degrees"))) return amount;
        var result=resolveDamage(action.actionId(),action.stageIndex(),true,ranged,amount,absorbed,skill);
        if (result.damage() < amount) feedback(PromisedConsortActionSoundPlan.cue("defense_absorb", PromisedConsortActionSoundPlan.Sound.REFLECTION,
            0.35F, action.actionId()==PromisedConsortActionId.GRAVITY_REPRISAL ? 0.8F : 0.65F), boss.position());
        absorbed=result.absorbed();
        return result.damage();
    }

    public static DefenseResult resolveDamage(PromisedConsortActionId action,int stage,boolean active,boolean ranged,
            double amount,double absorbed,PromisedConsortSkillConfigSnapshot.Skill skill) {
        if(!Double.isFinite(amount)||amount<=0) return new DefenseResult(0,absorbed);
        if(!active||!ranged) return new DefenseResult(amount,absorbed);
        if(action==PromisedConsortActionId.GRAVITY_BULWARK) return new DefenseResult(amount*(1-skill.number("ranged_damage_reduction")),absorbed);
        if(action==PromisedConsortActionId.GRAVITY_REPRISAL && stage==0) return new DefenseResult(0,Math.min(skill.number("absorption.full_charge_damage"),absorbed+amount));
        return new DefenseResult(amount,absorbed);
    }

    public record DefenseResult(double damage,double absorbed) {}

    public double returnMultiplier() {
        var skill=skills.get(PromisedConsortActionId.GRAVITY_REPRISAL);
        return PromisedConsortRangedState.returnMultiplier(absorbed,skill.number("absorption.full_charge_damage"),
                skill.number("absorption.minimum_return_multiplier"),skill.number("absorption.maximum_return_multiplier"));
    }

    public float charge() { return (float)(absorbed/skills.get(PromisedConsortActionId.GRAVITY_REPRISAL).number("absorption.full_charge_damage")); }

    public void tick(PromisedConsortActionSnapshot action) {
        if (!config.enabled() || action==null || action.actionId()!=PromisedConsortActionId.GRAVITY_REFLECTION
                || action.actionPhase()!=ActionPhase.ACTIVE) return;
        var skill=skills.get(action.actionId());
        for (AbstractArrow arrow:incoming(skill,1)) {
            reflect(action,arrow);
        }
    }

    public boolean reflect(PromisedConsortActionSnapshot action, AbstractArrow arrow) {
        if(!config.enabled() || action==null || action.actionId()!=PromisedConsortActionId.GRAVITY_REFLECTION
                || action.actionPhase()!=ActionPhase.ACTIVE) return false;
        var skill=skills.get(action.actionId());
        long now=boss.level().getGameTime();
        if(lastReflectionTick!=now) { lastReflectionTick=now; reflectedThisTick=0; }
        if(reflectedThisTick>=skill.integer("max_reflections_per_tick") || reflected>=skill.integer("max_reflections_per_cast")
                || !supported(arrow,skill) || !crosses(arrow,skill,1)) return false;
            Vec3 velocity=arrow.getDeltaMovement();
            var owner=arrow.getOwner();
            Vec3 aim=owner!=null && owner.isAlive() ? owner.getBoundingBox().getCenter().subtract(arrow.position()) : velocity.scale(-1);
            if (aim.lengthSqr()<0.000001) aim=velocity.scale(-1);
            double speed=Math.min(skill.number("max_projectile_speed"),velocity.length()*skill.number("speed_multiplier"));
            arrow.setOwner(boss);
            arrow.setDeltaMovement(aim.normalize().scale(speed));
            arrow.hurtMarked=true;
            arrow.setYRot((float)Math.toDegrees(Math.atan2(aim.x,aim.z)));
            arrow.setXRot((float)Math.toDegrees(Math.atan2(aim.y,aim.horizontalDistance())));
            arrow.yRotO=arrow.getYRot(); arrow.xRotO=arrow.getXRot();
            arrow.getPersistentData().putBoolean("elder_bosses_reflected",true);
            arrow.getPersistentData().putLong("elder_bosses_reflection_expires",boss.level().getGameTime()+skill.integer("remaining_lifetime_cap_ticks"));
            trackReflected(arrow);
            if(owner!=null) arrow.getPersistentData().putUUID("elder_bosses_reflected_from",owner.getUUID());
            reflected++;
            reflectedThisTick++;
            feedback(PromisedConsortActionSoundPlan.cue("reflect", PromisedConsortActionSoundPlan.Sound.REFLECTION, 0.75F, 1.05F), arrow.position());
        return true;
    }

    private void feedback(PromisedConsortActionSoundPlan.Cue cue, Vec3 position) {
        long now = boss.level().getGameTime();
        if (lastFeedbackTick == now) return;
        lastFeedbackTick = now;
        boss.playActionSound(cue, position, new Vec2(0, 1));
    }

    private List<AbstractArrow> incoming(PromisedConsortSkillConfigSnapshot.Skill skill, int ticks) {
        double radius=skill.number("trigger.scan_radius");
        return boss.level().getEntitiesOfClass(AbstractArrow.class,boss.getBoundingBox().inflate(radius),arrow->supported(arrow,skill))
                .stream().sorted(Comparator.comparingDouble(arrow->arrow.distanceToSqr(boss)))
                .limit(skill.integer("max_scanned_projectiles_per_tick")).filter(arrow->crosses(arrow,skill,ticks)).toList();
    }

    public static void trackReflected(net.minecraft.world.entity.Entity entity) {
        if(entity instanceof AbstractArrow arrow && arrow.getPersistentData().getBoolean("elder_bosses_reflected")) REFLECTED_ARROWS.add(arrow);
    }

    public static void expireReflected(net.minecraft.server.level.ServerLevel level) {
        REFLECTED_ARROWS.removeIf(arrow -> {
            if(arrow.isRemoved()) return true;
            if(arrow.level()!=level) return false;
            if(level.getGameTime()>=arrow.getPersistentData().getLong("elder_bosses_reflection_expires")) { arrow.discard(); return true; }
            return false;
        });
    }

    private boolean supported(AbstractArrow arrow, PromisedConsortSkillConfigSnapshot.Skill skill) {
        if (!(arrow instanceof Arrow || arrow instanceof SpectralArrow) || arrow.getClass()!=Arrow.class && arrow.getClass()!=SpectralArrow.class
                || arrow.isRemoved() || arrow.getPersistentData().getBoolean("elder_bosses_reflected") || !boss.rangedParticipant(arrow.getOwner())) return false;
        String id=BuiltInRegistries.ENTITY_TYPE.getKey(arrow.getType()).toString();
        if (!skill.idLists().getOrDefault("supported_entity_ids",List.of()).contains(id)
                || skill.idLists().getOrDefault("excluded_entity_ids",List.of()).contains(id)) return false;
        return PromisedConsortRangedDamage.ranged(boss.damageSources().arrow(arrow,arrow.getOwner()),config);
    }

    private boolean crosses(AbstractArrow arrow, PromisedConsortSkillConfigSnapshot.Skill skill, int ticks) {
        Vec3 velocity=arrow.getDeltaMovement();
        if(ticks<0 || velocity.lengthSqr()<0.000001 || velocity.dot(boss.getBoundingBox().getCenter().subtract(arrow.position()))<=0) return false;
        if(!inArc(arrow.position(),skill.number("defense_arc_degrees"))) return false;
        return intersectsSphere(arrow.position(),arrow.position().add(velocity.scale(ticks)),boss.getBoundingBox().getCenter(),skill.number("intercept_radius"));
    }

    public static boolean intersectsSphere(Vec3 from,Vec3 to,Vec3 center,double radius) {
        Vec3 travel=to.subtract(from);
        double fraction=travel.lengthSqr()<0.000001?0:Math.max(0,Math.min(1,center.subtract(from).dot(travel)/travel.lengthSqr()));
        return from.add(travel.scale(fraction)).distanceToSqr(center)<=radius*radius;
    }

    private boolean inArc(Vec3 point,double degrees) {
        if(degrees>=360) return true;
        if(point==null) return false;
        Vec3 direction=point.subtract(boss.position()).multiply(1,0,1).normalize();
        double yaw=Math.toRadians(boss.getYRot());
        return direction.dot(new Vec3(-Math.sin(yaw),0,Math.cos(yaw)))>=Math.cos(Math.toRadians(degrees/2));
    }

    private double horizontal(Vec3 point) { return point.subtract(boss.position()).horizontalDistance(); }

    public CompoundTag save() {
        CompoundTag tag=new CompoundTag();
        long now=boss.level().getGameTime();
        tag.putLong("GlobalRemaining",Math.max(0,globalReady-now)); tag.putLong("DefenseRemaining",Math.max(0,defenseReady-now));
        tag.putInt("Consecutive",consecutive); tag.putDouble("Absorbed",absorbed); tag.putInt("Reflected",reflected); tag.putBoolean("Counter",currentCounter);
        return tag;
    }

    public void restore(CompoundTag tag) {
        long now=boss.level().getGameTime();
        globalReady=now+Math.max(0,tag.getLong("GlobalRemaining")); defenseReady=now+Math.max(0,tag.getLong("DefenseRemaining"));
        consecutive=Math.max(0,tag.getInt("Consecutive")); reflected=Math.max(0,tag.getInt("Reflected")); currentCounter=tag.getBoolean("Counter");
        double saved=tag.getDouble("Absorbed");
        absorbed=Double.isFinite(saved)?Math.max(0,Math.min(saved,skills.get(PromisedConsortActionId.GRAVITY_REPRISAL).number("absorption.full_charge_damage"))):0;
    }
}