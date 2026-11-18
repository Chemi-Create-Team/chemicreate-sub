package net.createplant.thermal;

/** Percentage-domain controller; derivative is on measurement to avoid setpoint kicks. */
public final class PidLoop {
    private double integral, previous, derivative;
    private boolean initialized;
    public double step(double target, double measurement, double kp, double ti, double td, boolean reverse, double seconds) {
        if (!Double.isFinite(measurement) || !Double.isFinite(seconds) || seconds <= 0) return 0;
        double sign = reverse ? -1 : 1;
        double error = (target - measurement) * sign;
        double slope = initialized ? (measurement - previous) / seconds : 0;
        derivative += (slope - derivative) * seconds / (.5 + seconds);
        previous = measurement;initialized = true;
        double p = kp * error, d = -kp * td * derivative * sign;
        double next = integral + (ti > 0 ? kp * error * seconds / ti : 0);
        double unconstrained = p + next + d;
        if (unconstrained >= 0 && unconstrained <= 100 || unconstrained > 100 && error < 0 || unconstrained < 0 && error > 0)
            integral = Math.clamp(next, -100, 100);
        return Math.clamp(p + integral + d, 0, 100);
    }
    public void track(double output, double target, double measurement, double kp, boolean reverse) {
        integral = Math.clamp(output - kp * (target - measurement) * (reverse ? -1 : 1), -100, 100);
        previous = measurement;derivative = 0;initialized = true;
    }
    public double integral() { return integral; }
    public void restore(double saved) { integral = Double.isFinite(saved) ? Math.clamp(saved, -100, 100) : 0;initialized = false; }
}
