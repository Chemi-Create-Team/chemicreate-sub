package net.createplant.atomics.nuclear;

import net.createplant.block.Finite;
public final class RadiationPhysics {
    public static final double TIME_MULTIPLIER = 1;
    public static final double SHORT_HALF_LIFE = 8.02 * 86400,
            CESIUM_HALF_LIFE = 30.05 * 365.25 * 86400,
            LONG_HALF_LIFE = 24110 * 365.25 * 86400;
    public static final int REACH = 64, MAX_SOURCES = 256, MAX_CACHE = 4096;
    public static final double CUTOFF = 1e-12,
            INHALED_SV_PER_BQ = 2e-8,
            ALPHA_INHALED_SV_PER_BQ = 3.2e-5;
    public static final double CORE_GAMMA_PER_WATT = 2.56e-9, CORE_NEUTRON_PER_WATT = 6.4e-10;
    public static final double GAMMA_PER_SURFACE_BQ = .08e-3 / (3600 * 1e9),
            BETA_PER_SURFACE_BQ = 2e-16;

    private RadiationPhysics() {}

    public static double decay(double seconds, double halfLife) {
        return Math.exp(-Math.log(2) * Math.max(0, seconds) / halfLife);
    }

    public static double transmission(double metres, double halfValueLayer) {
        return decay(metres, halfValueLayer);
    }

    public static double inverseSquare(double atOneMetre, double metres) {
        return atOneMetre / Math.max(.25, metres * metres);
    }

    public static double betaAir(double metres) {
        return Math.max(0, 1 - metres / 4);
    }

    public record Spectrum(double gamma, double beta, double neutron, double activity) {
        public Spectrum {
            gamma = Finite.of(gamma, 0, 0, 1e30);
            beta = Finite.of(beta, 0, 0, 1e30);
            neutron = Finite.of(neutron, 0, 0, 1e30);
            activity = Finite.of(activity, 0, 0, 1e30);
        }

        public static final Spectrum ZERO = new Spectrum(0, 0, 0, 0);

        public Spectrum scale(double f) {
            return new Spectrum(gamma * f, beta * f, neutron * f, activity * f);
        }

        public Spectrum plus(Spectrum s) {
            return new Spectrum(
                    gamma + s.gamma, beta + s.beta, neutron + s.neutron, activity + s.activity);
        }

        public double external() {
            return gamma + beta + neutron;
        }
    }
}
