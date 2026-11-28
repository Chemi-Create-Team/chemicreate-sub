package net.createplant.atomics.fusion;

/**
 * Compact gameplay model of the fusion plant. Thermodynamic values use SI units while the machine's
 * geometry and commissioning time are compressed for construction and operation in Minecraft.
 */
public final class FusionPhysics {
    public static final int WIDTH = 11;
    public static final int HEIGHT = 8;
    public static final int HALF_WIDTH = WIDTH / 2;
    public static final int MIDPLANE = HEIGHT / 2;
    public static final int ROOF_OFFSET = HEIGHT - MIDPLANE - 1;
    /** Nominal radius; the exact two-by-two plasma volume is FusionStructure.plasmaChannel(). */
    public static final double PLASMA_MAJOR_RADIUS = 3.5;
    public static final double PLASMA_ELONGATION = 1;

    public static final double RATED_FUSION_WATTS = 500_000_000;
    public static final double PLASMA_KELVIN = 150_000_273.15;
    public static final double D_T_MEV = 17.6;
    public static final double ALPHA_MEV = 3.5;
    public static final double NEUTRON_MEV = 14.1;
    public static final double ALPHA_FRACTION = ALPHA_MEV / D_T_MEV;
    public static final double NEUTRON_FRACTION = NEUTRON_MEV / D_T_MEV;

    public static final double MAGNET_AXIS_TESLA = 5.3;
    public static final double MAGNET_PEAK_TESLA = 11.8;
    public static final double MAGNET_STORED_JOULES = 51_000_000_000d;
    public static final double LIQUID_HELIUM_KELVIN = 4.2;
    /** Conservative operating interlock, below the material's zero-field critical temperature. */
    public static final double QUENCH_KELVIN = 6.5;
    public static final double BASE_VACUUM_PASCALS = 1e-5;
    public static final double OPERATING_PRESSURE_PASCALS = 100;
    public static final double STARTUP_HEATING_JOULES = 1_000_000_000d;

    public static final double AMBIENT_KELVIN = 293.15;
    public static final double COOLANT_INLET_KELVIN = 473.15;
    /** 250 C design ceiling; the reference 80% plant settles below it at roughly 246 C. */
    public static final double COOLANT_OUTLET_KELVIN = 523.15;
    public static final double WATER_JOULES_PER_MB_KELVIN = 4.18;
    /**
     * The flash drum returns saturated water near 212 C in the reference plant.  Five independent pump branches
     * deliver roughly 174,000 mB/t. Together with the 250 C design ceiling this rejects 400 MW fusion plus 40 MW
     * auxiliary heating without accumulating blanket heat.
     */
    public static final int RATED_COOLANT_MB_PER_TICK = 200_000;

    private static final double GAS_KELVIN = AMBIENT_KELVIN;
    private static final double GAS_PASCALS = 101_325;
    private static final double GAS_CONSTANT = 8.314462618;
    private static final double AVOGADRO = 6.02214076e23;
    private static final double JOULES_PER_EV = 1.602176634e-19;
    /** One mB each of D2 and T2 at the registered reference state: both molecules supply two nuclei. */
    public static final double FUSION_JOULES_PER_FUEL_MB = 2 * gasMolesPerMb() * AVOGADRO
        * D_T_MEV * 1e6 * JOULES_PER_EV;

    private FusionPhysics() { }

    private static double gasMolesPerMb() {
        return GAS_PASCALS * 1e-6 / (GAS_CONSTANT * GAS_KELVIN);
    }

    /** Equal standard volumes of D2 and T2 needed during one 20 Hz game tick. */
    public static double fuelMbPerTick(double fusionWatts) {
        return Math.max(0, fusionWatts) / 20 / FUSION_JOULES_PER_FUEL_MB;
    }

    /** Water volume needed each tick to remove the stated power between two measured temperatures. */
    public static double coolantMbPerTick(double thermalWatts, double inletKelvin, double outletKelvin) {
        double rise = outletKelvin - inletKelvin;
        return rise <= 0 ? Double.POSITIVE_INFINITY
            : Math.max(0, thermalWatts) / 20 / (WATER_JOULES_PER_MB_KELVIN * rise);
    }

    /** Heat delivered to a measured water volume without inventing or discarding energy. */
    public static double coolantJoules(int amountMb, double inletKelvin, double outletKelvin) {
        return Math.max(0, amountMb) * WATER_JOULES_PER_MB_KELVIN * Math.max(0, outletKelvin - inletKelvin);
    }

    /**
     * Gameplay-compressed pump-down.  At 64 RPM the ten-second time constant takes about four minutes to move
     * from atmosphere to the ITER pre-operation threshold; a stopped pump admits a very small leak.
     */
    public static double vacuumStep(double pascals, double pumpRpm, boolean pumping) {
        pascals = Math.clamp(pascals, BASE_VACUUM_PASCALS, 101_325);
        if (!pumping || pumpRpm < 1) return Math.min(101_325, pascals + Math.max(.00001, pascals * .00002));
        double rate = Math.min(4, Math.abs(pumpRpm) / 64d);
        return BASE_VACUUM_PASCALS + (pascals - BASE_VACUUM_PASCALS) * Math.exp(-rate / 200d);
    }

    public static boolean vacuumReady(double pascals) {
        return Double.isFinite(pascals) && pascals <= BASE_VACUUM_PASCALS * 1.05;
    }

    public static double magnetCharge(double storedJoules) {
        return Math.clamp(storedJoules / MAGNET_STORED_JOULES, 0, 1);
    }

    /** Magnetic confinement scales approximately with current, and stored energy with current squared. */
    public static double magneticFieldTesla(double storedJoules) {
        return MAGNET_AXIS_TESLA * Math.sqrt(magnetCharge(storedJoules));
    }

    public static double targetWatts(int percent) {
        return RATED_FUSION_WATTS * Math.clamp(percent, 0, 100) / 100d;
    }
}
