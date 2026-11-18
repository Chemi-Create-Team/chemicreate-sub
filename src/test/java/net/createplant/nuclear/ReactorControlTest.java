package net.createplant.nuclear;

/** Fast, game-independent checks for control-room thermal indications and automatic rod control. */
public final class ReactorControlTest {
    public static void main(String[] args) {
        check(ReactorPhysics.localRodHeating(.25, 1) > ReactorPhysics.localRodHeating(3, 1), "withdrawn rod heats nearby fuel most");
        check(ReactorPhysics.localRodHeating(4, 1) == 0, "rod heating has a finite neighbourhood");
        double heated = ReactorPhysics.graphiteStep(400, 550, 1);
        double cooled = ReactorPhysics.graphiteStep(700, 350, 0);
        check(heated > 400, "powered graphite warms");
        check(cooled < 700, "shutdown graphite cools");
        check(ReactorAutomaticControl.nextTarget(40, 1900, 550, 10, 1e9, 765e6, 0, false) > 40, "hot fuel inserts rods");
        check(ReactorAutomaticControl.nextTarget(40, 700, 500, 10, 100e6, 765e6, 0, false) < 40, "cool flowing core may withdraw rods");
        check(ReactorAutomaticControl.nextTarget(0, 300, 300, 10, 0, 765e6, 0, true) == 100, "AZ-5 overrides automatic control");
        check(ReactorAutomaticControl.nextTarget(40, 500, 500, 10, 40e6, 36e6, 0, false) > 40, "a small core limits power against its own rating");
        check(ReactorAutomaticControl.nextTarget(40, 400, 400, 10, 100, 36e6, .01, false) > 40, "high positive reactivity inserts rods before fuel becomes hot");
        double fine = ReactorAutomaticControl.nextTarget(40, 700, 500, 10, .95 * 36e6, 36e6, .0005, false);
        check(fine > 40 && fine < 41, "near criticality automatic control uses fractional percent travel");
        check(ReactorAutomaticControl.nextTarget(40, 700, 500, .1, 2e6, 36e6, 0, false) > 40, "loss of coolant flow takes precedence over power demand");
        check(ReactorAutomaticControl.nextTarget(40, 400, 400, 10, 0, Double.NaN, 0, false) == 100, "invalid telemetry requests full insertion");
        System.out.println("Reactor control tests passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
