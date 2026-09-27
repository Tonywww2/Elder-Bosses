import com.tonywww.elder_bosses.boss.promisedconsort.action.PromisedConsortActionCatalog;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase;
import com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortAerialPath;
import com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortAttackPlan;
import com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortCrossLeapPath;
import com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortActionSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortAnimationTimeline;
import com.tonywww.elder_bosses.combat.action.ActionPhase;
import com.tonywww.elder_bosses.combat.action.ActionStage;
import com.tonywww.elder_bosses.combat.action.ActionTimeline;
import com.tonywww.elder_bosses.combat.action.SkillTuning;
import com.tonywww.elder_bosses.combat.geometry.Vec2;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;

public class AerialMovementCheck {
    private static int checks;
    private static void require(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static PromisedConsortActionSnapshot action(PromisedConsortActionId id, int tick, boolean ranged) {
        return new PromisedConsortActionSnapshot(id, PromisedConsortPhase.PHASE_TWO, 1, 100, tick, ActionPhase.WINDUP, 0, 0, 42, null, ranged);
    }
    public static void main(String[] args) throws Exception {
        var builder = new net.minecraftforge.common.ForgeConfigSpec.Builder();
        var values = new com.tonywww.elder_bosses.platforms.config.PromisedConsortConfigValues(builder);
        var spec = builder.build();
        var config = com.electronwill.nightconfig.core.CommentedConfig.inMemory(); spec.correct(config); spec.setConfig(config);
        var catalog = new PromisedConsortActionCatalog(values.skillSnapshot());
        for (var id : List.of(PromisedConsortActionId.LIGHTSPEED_SLASH, PromisedConsortActionId.LIGHTSPEED_SIDE_DASH)) {
            for (boolean ranged : new boolean[]{false, true}) {
                if (ranged && id == PromisedConsortActionId.LIGHTSPEED_SLASH) continue;
                var snapshot = action(id, 0, ranged); var timeline = catalog.timeline(snapshot); var skill = catalog.skill(snapshot);
                int lead = ranged ? skill.integer("ranged_counter.target_lock_lead_ticks") : 0;
                var flight = PromisedConsortAerialPath.from(id, timeline, lead);
                require(flight != null, "Default flight must be usable: " + id);
                boolean side = id == PromisedConsortActionId.LIGHTSPEED_SIDE_DASH;
                require(PromisedConsortAnimationTimeline.sample(flight.takeoffTick(), snapshot, timeline, skill) == (side ? 8 : 12), "Takeoff pose differs");
                require(PromisedConsortAnimationTimeline.sample(flight.crestTick(), snapshot, timeline, skill) == (side ? 16 : 28), "Crest pose differs");
                require(PromisedConsortAnimationTimeline.sample(flight.landingTick(), snapshot, timeline, skill) == (side ? 24 : 69), "Landing pose differs");
                if (ranged) require(flight.landingTick() <= timeline.activeStartTick(0) - lead, "Side hop crosses ranged lock");
                Vec3 origin = new Vec3(4, 17, 9), end = side ? origin.add(-1.7, 0, 1.7) : origin;
                checkPath(flight, origin, end);
                if (!side) {
                    for (int stage = 0; stage < 3; stage++) require(flight.at(origin, end, timeline.activeStartTick(stage)).y > origin.y + 2,
                            "The body must hover while clones attack");
                    var plan = PromisedConsortAttackPlan.create(action(id, flight.crestTick(), false), skill, timeline,
                            origin.add(0, flight.height(), 0), new Vec2(0,1), Map.of("aerial_end", end), 6);
                    require(plan.size() == 4, "Aerial motion changed strike count");
                    for (var strike : plan) require(strike.baseY() == end.y, "Clone/impact warning floated with body");
                }
            }
        }
        for (var id : List.of(PromisedConsortActionId.PROMISED_CONSORT, PromisedConsortActionId.CROSS_LEAP_COMBO)) {
            var snapshot = action(id, 0, false); var timeline = catalog.timeline(snapshot);
            var leap = PromisedConsortCrossLeapPath.finisher(timeline, 3);
            require(PromisedConsortAnimationTimeline.sample(leap.takeoffTick(), snapshot, timeline, catalog.skill(snapshot)) == 87, "Finisher takeoff drifted");
            require(PromisedConsortAnimationTimeline.sample(leap.landingTick(), snapshot, timeline, catalog.skill(snapshot)) == 111, "Finisher impact drifted");
            Vec3 end = new Vec3(5, 13, 8);
            var plan = PromisedConsortAttackPlan.create(action(id, leap.takeoffTick()+5, false), catalog.skill(snapshot), timeline,
                    end.add(0, 3, 0), new Vec2(0,1), Map.of("cross_finisher_end",end), 6);
            var finisher = plan.stream().filter(s -> s.id().equals("finisher")).findFirst().orElseThrow();
            require(finisher.baseY() == end.y && finisher.lockTick() == 100 + leap.takeoffTick(), "Finisher ground lock differs");
            require(PromisedConsortAttackPlan.repositionStage(id, timeline, leap.takeoffTick()+1, 6) != 4, "Ground pursuit is active during finisher flight");
        }
        // Retiming, very short components, long holds and ranged lock leads must stay bounded.
        var random = new java.util.Random(926);
        for (int run = 0; run < 300; run++) {
            ActionStage[] stages = new ActionStage[4];
            for (int i = 0; i < 4; i++) stages[i] = new ActionStage(1+random.nextInt(i==0?90:12), 1+random.nextInt(5), random.nextInt(7));
            var timeline = ActionTimeline.ofStages(stages);
            for (var id : List.of(PromisedConsortActionId.LIGHTSPEED_SLASH, PromisedConsortActionId.LIGHTSPEED_SIDE_DASH)) {
                var flight = PromisedConsortAerialPath.from(id, timeline, 0);
                if (flight != null) checkPath(flight, Vec3.ZERO, Vec3.ZERO);
                for (int i = 0; i < 4; i++) {
                    int[] contacts = id == PromisedConsortActionId.LIGHTSPEED_SLASH ? new int[]{35,48,61,69} : new int[]{36,44,52,65};
                    require(PromisedConsortAnimationTimeline.sample(timeline.activeStartTick(i), id, timeline, SkillTuning.NEUTRAL) == contacts[i],
                            "Flight landmark overwrote a clone/body contact: " + id + " stage " + i);
                }
                double previous = -1;
                for (double tick = 0; tick <= timeline.totalTicks(); tick += 0.25) {
                    double pose = PromisedConsortAnimationTimeline.sample(tick, id, timeline, SkillTuning.NEUTRAL);
                    require(pose >= previous && Double.isFinite(pose), "Retimed aerial clock went backwards: " + id + " " + tick);
                    previous = pose;
                }
            }
        }
        var legacy = ActionTimeline.ofStages(new ActionStage(20, 30, 20));
        require(PromisedConsortAerialPath.from(PromisedConsortActionId.LIGHTSPEED_SLASH,legacy,0) == null, "Legacy timeline must remain valid");
        var effect = com.tonywww.elder_bosses.client.vfx.ClientBossVfxController.class.getDeclaredMethod("aerialLanding",String.class,String.class);
        effect.setAccessible(true);
        require((boolean)effect.invoke(null,"promised_consort","hazard:1:finisher"),"Finisher lacks landing burst");
        require((boolean)effect.invoke(null,"lightspeed_slash","hazard:1:body"),"Slash lacks landing burst");
        require(!(boolean)effect.invoke(null,"lightspeed_slash","hazard:1:clone_0"),"Clone contact emitted body landing burst");
        require(!(boolean)effect.invoke(null,"lightspeed_side_dash","hazard:1:body"),"Grounded side slash is not a landing");
        System.out.println("Aerial movement checks passed: " + checks + "; paths, ground anchors, retiming, ranged lock and landing effects");
    }
    private static void checkPath(PromisedConsortAerialPath flight, Vec3 origin, Vec3 end) {
        require(flight.at(origin,end,flight.takeoffTick()-1).equals(origin),"Flight moves before takeoff");
        require(flight.at(origin,end,flight.landingTick()+1).equals(end),"Landing drifts");
        Vec3 previous=origin;
        for (int tick=flight.takeoffTick(); tick<=flight.landingTick(); tick++) {
            Vec3 point=flight.at(origin,end,tick);
            require(point.distanceTo(previous)<=1.25,"Flight exceeds collision step budget");
            require(point.y>=Math.min(origin.y,end.y)-0.00001,"Flight entered ground");
            previous=point;
        }
    }
}
