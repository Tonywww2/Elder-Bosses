package com.tonywww.elder_bosses.boss.promisedconsort.ranged;

import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSkillConfigSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.combat.action.ActionTimeline;
import net.minecraft.world.phys.Vec3;
import java.util.function.Predicate;

public record PromisedConsortRangedPath(Vec3 origin, Vec3 corner, Vec3 end) {
    public static PromisedConsortRangedPath create(PromisedConsortActionId action, PromisedConsortSkillConfigSnapshot.Skill skill,
            ActionTimeline timeline, Vec3 origin, Vec3 target, Vec3 forward, Predicate<Vec3> clear) {
        Vec3 direction=forward.multiply(1,0,1).normalize();
        Vec3 corner=origin;
        if(action==PromisedConsortActionId.LIGHTSPEED_SIDE_DASH) {
            int lateralTicks=timeline.activeTicksBetween(0,timeline.stageStartTick(3));
            double lateral=Math.min(skill.number("ranged_counter.max_lateral_distance"),lateralTicks*skill.number("ranged_counter.max_lateral_per_tick"));
            corner=clip(origin,origin.add(-direction.z*lateral,0,direction.x*lateral),clear);
        }
        Vec3 aim=target.subtract(corner).multiply(1,0,1);
        int movementTicks=action==PromisedConsortActionId.LIGHTSPEED_DASH ? timeline.activeTicksBetween(0,timeline.activeEndTick(4))
                : timeline.stages().get(action==PromisedConsortActionId.LIGHTSPEED_SIDE_DASH?3:0).activeTicks();
        double distance=Math.min(skill.number("ranged_counter.max_forward_distance"),movementTicks*skill.number("ranged_counter.max_forward_per_tick"));
        distance=Math.max(0,Math.min(distance,aim.length()-skill.number("ranged_counter.stop_distance")));
        Vec3 end=clip(corner,corner.add(aim.normalize().scale(distance)),clear);
        return new PromisedConsortRangedPath(origin,corner,end);
    }

    public Vec3 at(PromisedConsortActionId action, ActionTimeline timeline, int tick) {
        if(action==PromisedConsortActionId.LIGHTSPEED_SIDE_DASH) {
            if(tick<timeline.stageStartTick(3)) return origin.lerp(corner,fraction(timeline,0,timeline.activeEndTick(2),tick));
            return corner.lerp(end,fraction(timeline,timeline.stageStartTick(3),timeline.activeEndTick(3),tick));
        }
        int finish=timeline.activeEndTick(action==PromisedConsortActionId.LIGHTSPEED_DASH?4:0);
        return origin.lerp(end,fraction(timeline,0,finish,tick));
    }

    private static double fraction(ActionTimeline timeline,int start,int end,int tick) {
        if(tick<start) return 0;
        return Math.min(1,(double)timeline.activeTicksBetween(start,Math.min(end,tick+1))/Math.max(1,timeline.activeTicksBetween(start,end)));
    }

    public static Vec3 clip(Vec3 from,Vec3 to,Predicate<Vec3> clear) {
        int samples=Math.max(1,(int)Math.ceil(from.distanceTo(to)/0.25));
        Vec3 previous=from;
        for(int sample=1;sample<=samples;sample++) {
            Vec3 next=from.lerp(to,(double)sample/samples);
            if(!clear.test(next)) return previous;
            previous=next;
        }
        return to;
    }
}