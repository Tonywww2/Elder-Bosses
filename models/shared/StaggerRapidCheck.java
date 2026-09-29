import com.tonywww.elder_bosses.combat.state.StaggerTracker;

public final class StaggerRapidCheck {
    static int checks;
    static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
    static void near(double actual, double expected) { check(Math.abs(actual - expected) < 1e-6, actual + " instead of " + expected); }
    static StaggerTracker<String> tracker(int window, double fraction) {
        return new StaggerTracker<>(1000, 1, .5, window, fraction,
                StaggerTracker.DEFAULT_DISTANCE_BANDS, 0, 120, 0, 70, 80);
    }
    public static void main(String[] args) {
        var rapid = tracker(240, .25);
        near(rapid.snapshot(0).capacity(), 500);
        near(rapid.applyHealthLoss("a", 1000, 0, 0).appliedIncrease(), 125);
        near(rapid.snapshot(0).stagger(), 125);
        var sameTick = tracker(240, .25);
        sameTick.applyHealthLoss("a", 1000, 0, 0);
        near(sameTick.applyHealthLoss("b", 1000, 0, 0).appliedIncrease(), 0);
        double[] stages = new double[2];
        boolean stunned = false;
        for (int tick = 1; tick <= 960; tick++) {
            if (tick % 120 == 0) rapid.applyHealthLoss("a", 1000, 0, tick);
            var snap=rapid.snapshot(tick);
            stunned |= snap.state() == StaggerTracker.StaggerState.STUNNED;
            if (tick == 240) stages[0] = snap.stagger();
            if (tick == 480) stages[1] = snap.stagger();
            check(snap.stagger() <= 125 + tick * 125.0 / 240.0 + 1e-6,
                    "Rapid stagger exceeded the 12-second budget at " + tick);
        }
        near(stages[0], 250);
        check(stages[1] >= 350 && stages[1] <= 375,
                "Dynamic reduction must pace near one quarter per window");
        check(stunned, "Continued pressure must eventually stun");

        var persisted=tracker(240,.25);
        persisted.applyHealthLoss("a",1000,0,0);
        var saved=persisted.persistentState(80);
        var restored=StaggerTracker.<String>restore(saved,80);
        near(restored.applyHealthLoss("a",1000,0,120).appliedIncrease(),
                persisted.applyHealthLoss("a",1000,0,120).appliedIncrease());
        near(restored.snapshot(120).stagger(),persisted.snapshot(120).stagger());
        var tuned=tracker(120,.2);
        near(tuned.applyHealthLoss("a",1000,0,0).appliedIncrease(),100);
        tuned.applyHealthLoss("b",1000,0,60);
        near(tuned.snapshot(60).stagger(),150);
        System.out.println("StaggerRapidCheck passed: "+checks);
    }
}
