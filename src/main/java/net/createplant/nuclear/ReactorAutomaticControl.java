package net.createplant.nuclear;

/** Game-independent control law used by the integrated console and its fast regression test. */
public final class ReactorAutomaticControl {
    private ReactorAutomaticControl() { }

    /** Returns the next absorber insertion target (0 out, 100 in). */
    public static double nextTarget(double current, double hottestKelvin, double outletKelvin, double flowMbPerTick,
                                    double powerWatts, double nominalWatts, double reactivity, boolean scram) {
        if (!Double.isFinite(current) || !Double.isFinite(hottestKelvin) || !Double.isFinite(outletKelvin)
            || !Double.isFinite(flowMbPerTick) || !Double.isFinite(nominalWatts) || nominalWatts <= 0
            || !Double.isFinite(powerWatts) || !Double.isFinite(reactivity)) return 100;
        current = Math.clamp(current, 0, 100);
        if (scram || hottestKelvin >= ReactorPhysics.ALARM_KELVIN) return 100;
        if (hottestKelvin >= 1850 || outletKelvin >= 610 || powerWatts > 1e6 && flowMbPerTick < .25)
            return Math.min(100, current + 8);
        if (hottestKelvin >= 1700 || outletKelvin >= 575) return Math.min(100, current + 3);
        double load = powerWatts / nominalWatts;
        double wanted = .003 * Math.clamp(1 - load, -1, 1);
        double step = Math.max(.1, Math.round(Math.min(1, Math.abs(reactivity - wanted) * 200) * 10) / 10d);
        if (load > 1.02 || reactivity > wanted + .0002) return Math.min(100, current + step);
        if (load < .98 && reactivity < wanted - .0002 && hottestKelvin < 1650 && outletKelvin < 575 && flowMbPerTick >= .5)
            return Math.max(0, current - step);
        return current;
    }
}
