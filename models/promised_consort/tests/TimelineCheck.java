import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortAnimationTimeline;
import com.tonywww.elder_bosses.combat.action.ActionStage;
import com.tonywww.elder_bosses.combat.action.ActionTimeline;
import com.tonywww.elder_bosses.combat.action.SkillTuning;

public final class TimelineCheck {
    private static int assertions;

    public static void main(String[] arguments) {
        for (double speed : new double[]{1.0, 1.3, 1.35, 1.4, 1.5, 2.0}) {
            SkillTuning tuning = new SkillTuning(speed, 1.0);
            for (PromisedConsortActionId action : PromisedConsortActionId.values()) {
                int[] durations = PromisedConsortAnimationTimeline.durations(action);
                ActionStage[] stages = new ActionStage[durations.length / 3];
                int authoredTotal = 0;
                for (int index = 0; index < stages.length; index++) {
                    stages[index] = new ActionStage(tuning.scaleTicks(durations[index * 3]),
                            tuning.scaleTicks(durations[index * 3 + 1]), tuning.scaleTicks(durations[index * 3 + 2]));
                    authoredTotal += durations[index * 3] + durations[index * 3 + 1] + durations[index * 3 + 2];
                }
                ActionTimeline timeline = ActionTimeline.ofStages(stages);
                double previous = -1;
                for (double tick = 0; tick <= timeline.totalTicks(); tick += 0.25) {
                    double mapped = PromisedConsortAnimationTimeline.sample(tick, action, timeline, tuning);
                    require(Double.isFinite(mapped) && mapped >= previous && mapped >= 0 && mapped <= authoredTotal,
                            action + " is not monotonic at " + speed + " / " + tick);
                    previous = mapped;
                }
                require(PromisedConsortAnimationTimeline.sample(timeline.totalTicks(), action, timeline, tuning) == authoredTotal,
                        action + " does not end on its authored endpoint");
                int runtimeCursor = 0, authoredCursor = 0;
                for (int index = 0; index < stages.length; index++) {
                    int runtimeHit = runtimeCursor + stages[index].windupTicks();
                    int authoredHit = authoredCursor + durations[index * 3];
                        if (action != PromisedConsortActionId.CONSORT_METEOR) {
                        require(PromisedConsortAnimationTimeline.sample(runtimeHit, action, timeline, tuning) == authoredHit,
                            action + " active start mismatch at " + speed);
                        }
                    runtimeCursor += stages[index].windupTicks() + stages[index].activeTicks() + stages[index].recoveryTicks();
                    authoredCursor += durations[index * 3] + durations[index * 3 + 1] + durations[index * 3 + 2];
                }
                if (action == PromisedConsortActionId.PROMISED_CONSORT) {
                    for (int offset : new int[]{8, 18, 28, 42, 44, 47, 50}) {
                        require(PromisedConsortAnimationTimeline.sample(stages[0].windupTicks() + tuning.scaleTicks(offset), action, timeline, tuning) == 26 + offset,
                                "Consort event mismatch at " + speed + " / " + offset);
                    }
                }
                if (action == PromisedConsortActionId.CONSORT_METEOR) {
                    require(PromisedConsortAnimationTimeline.sample(tuning.scaleTicks(91), action, timeline, tuning) == 91,
                            "Meteor lock mismatch at " + speed);
                    require(PromisedConsortAnimationTimeline.sample(tuning.scaleTicks(121), action, timeline, tuning) == 121,
                            "Meteor impact mismatch at " + speed);
                }
            }
        }
        System.out.println("Timeline checks passed: " + assertions + " assertions, 22 actions, 6 cast speeds");
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}