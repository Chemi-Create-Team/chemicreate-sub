package net.createplant.factory;

/** Game-unit models of residence time, volume conservation, and winding tension. */
public final class FactoryProcess {
    public static final double REDUCTION = .75;
    public static final double RPM_TOLERANCE = .10;
    public static final double MIN_TENSION = .76, MAX_TENSION = 1.48;

    private FactoryProcess() { }

    public static double kilnTravel(double rpm) { return rpm / 960; }
    public static double bakeRate(int heat) { return heat == 2 ? 1.60 : heat == 1 ? 1 : 0; }
    public static boolean underfired(double dose, int work) { return dose < .78 * work; }
    public static boolean overfired(double dose, int work) { return dose > 1.48 * work; }

    public static double requiredNextSpeed(double rpm) { return rpm / REDUCTION; }
    public static double millRatio(double previous, double current) { return current / requiredNextSpeed(previous); }
    public static float slabThickness(float incoming, double passes) { return (float) (incoming * Math.pow(REDUCTION, passes)); }
    public static float slabLength(float incoming, float thickness) { return .86f * incoming / thickness; }
    public static double coilingRadius(double progress) { return .34 + .27 * Math.sqrt(Math.clamp(progress, 0, 1)); }

    public static double tension(double takeup, double brake, double radius) {
        if (takeup <= 0) return 0;
        double motorTorque = 2.0 * takeup;
        double brakeTorque = .95 * brake;
        return (brakeTorque + .0275 * motorTorque) / ((radius + .23) * motorTorque * .35);
    }
}
