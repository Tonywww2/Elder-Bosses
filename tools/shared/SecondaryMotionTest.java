import com.tonywww.elder_bosses.client.render.SecondaryMotionSpring;

public final class SecondaryMotionTest {
    public static void main(String[] arguments) {
        SecondaryMotionSpring spring = new SecondaryMotionSpring();
        check(spring.sample(0, 0.4, 15, 0, 1).pitchDegrees() == 0, "First frame starts neutral");
        SecondaryMotionSpring.Offset first = spring.sample(0.5, 0.4, 15, 0, 1);
        check(first.pitchDegrees() > 0 && first.pitchDegrees() < 3.6, "Motion eases into target");
        check(first.equals(spring.sample(0.5, 99, -99, 99, 1)), "Duplicate render does not advance state");
        SecondaryMotionSpring.Offset previous = first;
        for (double time = 0.75; time < 20; time += 0.25) {
            SecondaryMotionSpring.Offset next = spring.sample(time, 0.4, 15, 0, 1);
            check(next.pitchDegrees() >= previous.pitchDegrees() && next.pitchDegrees() <= 3.6, "Critical damping has no step overshoot");
            check(Math.abs(next.rollDegrees()) <= 5, "Turn offset is limited");
            previous = next;
        }
        for (double time = 20; time <= 45; time += 0.25) spring.sample(time, 0, 0, 0, 0);
        SecondaryMotionSpring.Offset settled = spring.sample(45.25, 0, 0, 0, 0);
        check(Math.abs(settled.pitchDegrees()) < 0.001 && Math.abs(settled.rollDegrees()) < 0.001, "Stopped motion settles");
        check(spring.sample(80, 99, 99, 99, 1).equals(new SecondaryMotionSpring.Offset(0, 0)), "Long gap resets stale momentum");
        check(spring.sample(2, 99, 99, 99, 1).equals(new SecondaryMotionSpring.Offset(0, 0)), "Clock rewind resets");
        SecondaryMotionSpring.Offset invalid = spring.sample(2.5, Double.NaN, Double.POSITIVE_INFINITY, 0, 1);
        check(Double.isFinite(invalid.pitchDegrees()) && Double.isFinite(invalid.rollDegrees()), "Invalid samples stay finite");
        SecondaryMotionSpring coarse = new SecondaryMotionSpring(), fine = new SecondaryMotionSpring();
        coarse.sample(0, 0.3, 6, 0, 1);
        fine.sample(0, 0.3, 6, 0, 1);
        SecondaryMotionSpring.Offset large = coarse.sample(1, 0.3, 6, 0, 1);
        SecondaryMotionSpring.Offset small = null;
        for (int index = 1; index <= 10; index++) small = fine.sample(index / 10.0, 0.3, 6, 0, 1);
        check(Math.abs(large.pitchDegrees() - small.pitchDegrees()) < 0.0000001, "Constant input is frame-rate independent");
        check(Math.abs(large.rollDegrees() - small.rollDegrees()) < 0.0000001, "Turn damping is frame-rate independent");
        System.out.println("Secondary motion checks passed: bounded response, frame reuse, settling, clock resets and timestep independence.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}