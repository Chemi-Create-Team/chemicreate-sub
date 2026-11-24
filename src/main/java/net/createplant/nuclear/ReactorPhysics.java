package net.createplant.nuclear;

/**
 * Lumped RBMK physics with no game state: one-group point kinetics with one delayed group, iodine and xenon with time
 * compressed sixty times, and the reactivity terms of a graphite-moderated, boiling-water core. Reactivity is absolute
 * (dk/k); the neutron level is relative to nominal power.
 */
public final class ReactorPhysics {
    public static final double BETA = 0.0065, GENERATION_SECONDS = 1e-3, PRECURSOR_DECAY = 0.08, SOURCE_LEVEL = 1e-7;
    public static final double TICK_SECONDS = 0.05;
    private static final int SUBSTEPS = 10;
    /** Iodine-135 6.6 h and xenon-135 9.2 h half-lives, run sixty times faster. */
    public static final double IODINE_DECAY = Math.log(2) / (6.6 * 60), XENON_DECAY = Math.log(2) / (9.2 * 60);
    /**
     * Burn-out by nominal flux against xenon decay, and the share of xenon born through iodine. Iodine and xenon are in units
     * of their equilibrium at nominal power, so both are 1 there.
     */
    private static final double XENON_BURNOUT = 9, IODINE_SHARE = 0.953;
    public static final double XENON_WORTH = 0.028, VOID_WORTH = 0.025, DOPPLER_PER_KELVIN = -1.2e-5;
    public static final double FUEL_WORTH = 0.12, STRUCTURE_ABSORPTION = 0.02, MIGRATION_AREA = 0.043;
    /** Boron carbide in a rod channel against the water it replaces, and the graphite displacer against that water. */
    public static final double ABSORBER_WORTH = 0.7, DISPLACER_WORTH = 0.15;
    /**
     * Burn-up of boron carbide: the share of a rod's worth lost per second fully inserted at nominal power, so half of it is
     * gone after two hours there. Worn absorbers are renewed by the gantry crane.
     */
    public static final double ABSORBER_BURNUP = 0.5 / 7200, MAX_ABSORBER_WEAR = 0.9;
    /** Reactivity above prompt critical is held here so the excursion stays finite within a tick, and the level under a million times nominal. */
    public static final double MAX_REACTIVITY = 0.05, MAX_LEVEL = 1e6;
    /** A block has a two-by-two grid: four readable pressure-channel positions in its graphite. */
    public static final int CHANNEL_GRID = 2, RODS_PER_BLOCK = CHANNEL_GRID * CHANNEL_GRID;
    /**
     * One block is one vertical metre. Above the seven-metre core are two metres of upper biological shield and twelve metres
     * of tube-bundle space. Below it are two metres of lower biological shield and five metres of tube-bundle space for the
     * fully inserted graphite displacer. The top cap is the operating floor. The 12.75-metre control rod is continuous geometry:
     * seven metres of absorber, a 1.25-metre connector and a 4.5-metre graphite displacer.
     */
    public static final int CORE_HEIGHT = 7, UPPER_SHIELD_HEIGHT = 2, SUSPENSION_HEIGHT = 12;
    public static final int LOWER_SHIELD_HEIGHT = 2, LOWER_SERVICE_HEIGHT = 5;
    public static final int UPPER_STRUCTURE_HEIGHT = UPPER_SHIELD_HEIGHT + SUSPENSION_HEIGHT, CAP_OFFSET = UPPER_STRUCTURE_HEIGHT + 1;
    public static final int LOWER_STRUCTURE_HEIGHT = LOWER_SHIELD_HEIGHT + LOWER_SERVICE_HEIGHT;
    public static final int FUEL_ASSEMBLY_LENGTH = CORE_HEIGHT + UPPER_STRUCTURE_HEIGHT, ROD_TRAVEL = CORE_HEIGHT;
    public static final double ABSORBER_LENGTH = 7, CONNECTOR_LENGTH = 1.25, DISPLACER_LENGTH = 4.5;
    public static final int COMPACT_CORE_HEIGHT = 3;
    public record Layout(int coreHeight, int upperShieldHeight, int suspensionHeight, int lowerShieldHeight, int lowerServiceHeight) {
        public int upperStructureHeight() { return upperShieldHeight + suspensionHeight; }
        public int lowerStructureHeight() { return lowerShieldHeight + lowerServiceHeight; }
        public int capOffset() { return upperStructureHeight() + 1; }
        public int fuelAssemblyLength() { return coreHeight + upperStructureHeight(); }
        public int totalHeight() { return lowerStructureHeight() + coreHeight + upperStructureHeight() + 1; }
        public double scale() { return coreHeight / (double) CORE_HEIGHT; }
        public double absorberLength() { return ABSORBER_LENGTH * scale(); }
        public double connectorLength() { return CONNECTOR_LENGTH * scale(); }
        public double displacerLength() { return DISPLACER_LENGTH * scale(); }
    }
    public static final Layout STANDARD_LAYOUT = new Layout(CORE_HEIGHT, UPPER_SHIELD_HEIGHT, SUSPENSION_HEIGHT, LOWER_SHIELD_HEIGHT, LOWER_SERVICE_HEIGHT);
    public static final Layout COMPACT_LAYOUT = new Layout(COMPACT_CORE_HEIGHT, 1, 3, 1, 2);
    public static boolean supportedHeight(int height) { return height == CORE_HEIGHT || height == COMPACT_CORE_HEIGHT; }
    public static Layout layout(int height) { return height == COMPACT_CORE_HEIGHT ? COMPACT_LAYOUT : STANDARD_LAYOUT; }
    public static double heightScale(int height) { return height / (double) CORE_HEIGHT; }
    public static double nominalChannelWatts(int height) { return NOMINAL_CHANNEL_WATTS * heightScale(height); }
    /** Rated feedwater and saturated outlet conditions of the pressure channels. */
    public static final double INLET_KELVIN = 543.15, SATURATION_KELVIN = 557.15;
    public static final double COOLANT_ALARM_KELVIN = 575;
    /** Saturation pressure at 284 °C on the simplified water curve: about 67.5 bar, or 66.7 atmospheres. */
    public static final double CHANNEL_PASCALS = 6_753_770.36205875;
    /** The largest recommended 397-fuel-channel core is rated at about 765 MW thermal. */
    public static final double MAX_NOMINAL_WATTS = 765e6;
    public static final double NOMINAL_CHANNEL_WATTS = MAX_NOMINAL_WATTS / ReactorCoreStructure.DEFAULT_FUEL_CHANNELS;
    public static final double NOMINAL_COLUMN_WATTS = RODS_PER_BLOCK * NOMINAL_CHANNEL_WATTS;
    /** Rated full-core volume flows. Steam is the expanded line volume; the internal wet stream remains water-equivalent mass. */
    public static final int NOMINAL_INLET_MB_PER_TICK = 220_000, NOMINAL_LIQUID_OUTLET_MB_PER_TICK = 200_000;
    public static final int NOMINAL_STEAM_OUTLET_MB_PER_TICK = 500_000, NOMINAL_MIXED_OUTLET_MB_PER_TICK = 700_000;
    /** Heat-balanced internal phase flow at that rating; 1,000 mB is one kilogram in either phase. */
    public static final int NOMINAL_STEAM_MASS_MB_PER_TICK = (int) Math.round(1_000 * (MAX_NOMINAL_WATTS * TICK_SECONDS
        - NOMINAL_INLET_MB_PER_TICK / 1_000d * 4_180 * (SATURATION_KELVIN - INLET_KELVIN)) / 2_257_000);
    public static final int NOMINAL_LIQUID_MASS_MB_PER_TICK = NOMINAL_INLET_MB_PER_TICK - NOMINAL_STEAM_MASS_MB_PER_TICK;
    /** Normal fuel-pellet centre band and its calibrated full-power target, all in kelvin. */
    public static final double NORMAL_FUEL_MIN_KELVIN = 1273.15, NOMINAL_FUEL_KELVIN = 1523.15, NORMAL_FUEL_MAX_KELVIN = 1773.15;
    public static final double CELL_HEAT_CAPACITY = RODS_PER_BLOCK * 50_000;
    /** Conductance which puts a wetted, nominal-power channel at the 1,250 °C centre-temperature target. */
    public static final double CELL_CONDUCTANCE = RODS_PER_BLOCK * NOMINAL_CHANNEL_WATTS
        / (CORE_HEIGHT * (NOMINAL_FUEL_KELVIN - SATURATION_KELVIN));
    /**
     * Forced circulation supplies the nominal conductance above. In a hotter radial channel, nucleate boiling raises the film
     * coefficient approximately with heat flux to the 0.7 power. Only the normal radial peak receives this credit; excursions
     * above it do not gain ever-increasing cooling.
     */
    public static final double NOMINAL_RADIAL_POWER_PEAK = 1.8;
    public static double nucleateBoilingHeatTransfer(double nominalPowerFraction) {
        return Math.pow(Math.clamp(nominalPowerFraction, 1, NOMINAL_RADIAL_POWER_PEAK), .7);
    }
    /**
     * Bulk channel void is not the same as a dry fuel surface: nucleate boiling keeps the cladding wetted until the channel is
     * almost all vapour. Past 92% void the film dries out rapidly, reaching the residual steam coefficient at complete voiding.
     */
    public static double coolantHeatTransfer(double voidFraction) {
        double dryout = Math.clamp((voidFraction - .92) / .08, 0, 1);
        return 1 - .98 * dryout * dryout;
    }
    /**
     * Estimated cladding surface temperature. At a wetted surface, the coolant film accounts for eight percent of the total
     * centre-to-water thermal resistance; dryout adds film resistance and moves the surface temperature toward the fuel centre.
     */
    public static double fuelSurfaceKelvin(double fuelKelvin, double coolantKelvin, double voidFraction) {
        double transfer = coolantHeatTransfer(voidFraction);
        double filmShare = 1 - .92 * transfer;
        return coolantKelvin + Math.clamp(filmShare, 0, 1) * (fuelKelvin - coolantKelvin);
    }
    /**
     * Above the alarm the fuel is overheating; at the rupture temperature the zirconium cladding and pressure tubes fail, the
     * water of every channel flashes to steam and blows the top off the core, and the core itself goes a moment later.
     */
    /** Fuel centres run at 1000-1500 °C in normal operation, so the limits lie above that. */
    public static final double CHANNEL_RUPTURE_KELVIN = 2400, ALARM_KELVIN = 2073.15;
    /**
     * The Chernobyl conditions at the moment AZ-5 is pressed, from 0 to 1: almost no absorber left in the core (a small
     * operational reactivity margin), the core poisoned by xenon above its equilibrium, and coolant close to boiling. Each
     * factor alone is harmless; together the graphite tips entering the core push the flux and the reactivity up instead of down.
     */
    public static double scramHazard(double absorberInserted, double xenon, double coolantKelvin) {
        double margin = Math.clamp((0.25 - absorberInserted) / 0.25, 0, 1);
        double poison = Math.clamp((xenon - 1) / 0.5, 0, 1);
        double boiling = Math.clamp((coolantKelvin - (SATURATION_KELVIN - 20)) / 20, 0, 1);
        return margin * poison * boiling;
    }

    /**
     * Under the full hazard, with the flux pushed to the bottom of the core by the xenon above, graphite that AZ-5 drives into
     * water is worth this many times more; with the hazard above {@code JAM_HAZARD}, power rising to {@code JAM_SURGE} times its
     * value at AZ-5 bends the channels and jams the rods there.
     */
    public static final double TIP_SURGE = 100, JAM_HAZARD = 0.3, JAM_SURGE = 10;

    /**
     * How far the core has lost the absorber that holds its flux in shape, from 0 with a sixth of the absorber in to 1 with all of
     * it out. Without it the core is loosely coupled: its power wanders and tilts from side to side, and its void feedback
     * strengthens up to {@code UNSTABLE_VOID} times.
     */
    public static double instability(double absorberInserted) { return Math.clamp((1 / 6d - absorberInserted) * 6, 0, 1); }

    /**
     * Fully unstable, the reactivity and the radial tilt wander as random walks that settle back over {@code WANDER_SECONDS}, with
     * these standard deviations.
     */
    public static final double UNSTABLE_VOID = 3, WANDER_SECONDS = 4, WANDER_REACTIVITY = 0.4 * BETA, WANDER_TILT = 0.35;

    /** One tick of a random walk settling back to zero, with {@code deviation} as its stationary spread; {@code gauss} is a standard normal draw. */
    public static double wander(double value, double deviation, double gauss) {
        double decay = TICK_SECONDS / WANDER_SECONDS;
        return value * (1 - decay) + deviation * Math.sqrt(2 * decay) * gauss;
    }

    /**
     * Dose rate in sieverts a second at {@code metres} from a core giving out {@code watts} through faces with no biological
     * shield: a hundredth of a sievert a second at sixteen metres from a gigawatt, so a few minutes beside an open core kill.
     */

    /** A shut-down core still gives out the radiation of its fission products, as this share of the nominal power of its fuel. */
    public static final double DECAY_SHARE = 0.002;

    /** Ticks between the steam explosion and the explosion of the core, as at Chernobyl a few seconds apart. */
    public static final int CORE_EXPLOSION_DELAY = 40;

    /** Number and spacing of the pressure-wave bursts which follow a Chernobyl-condition core explosion. */
    public static final int CHERNOBYL_SECONDARY_BLASTS = 8, CHERNOBYL_SECONDARY_INTERVAL = 4;

    /** Strength of the steam explosion of {@code kilograms} of channel water flashing at once. */
    public static float steamExplosion(double kilograms) { return (float) Math.clamp(2 + 1.5 * Math.cbrt(kilograms), 2, 10); }

    /** Strength of the core explosion: the more fuel columns and the higher the power at the rupture, the larger. */
    public static float coreExplosion(int fuelColumns, double watts) {
        return (float) Math.clamp(4 + 0.8 * Math.sqrt(fuelColumns) + Math.log10(1 + watts / 1e6), 4, 16);
    }

    /**
     * True only after an emergency shutdown made the graphite-tip excursion large enough to bend the channels and jam the
     * rods. A prompt-critical or loss-of-coolant rupture without that AZ-5 history still uses the ordinary explosion.
     */
    public static boolean chernobylExplosion(boolean scram, boolean jammed, double hazard) {
        return scram && jammed && hazard > JAM_HAZARD;
    }

    /** The first vessel-lifting steam blast in the exceptional AZ-5 accident. */
    public static float chernobylSteamExplosion(double kilograms, double hazard) {
        double water = Math.cbrt(Math.max(0, kilograms)) / 2;
        return (float) Math.clamp(14 + 7 * Math.clamp(hazard, 0, 1) + water, 14, 24);
    }

    /**
     * The destructive core blast after the roof has lifted. Fine pressure-channel count, excursion power and the captured
     * AZ-5 hazard scale it from more than twice the ordinary maximum up to a forty-eight-strength plant-destroying event.
     */
    public static float chernobylCoreExplosion(int fuelChannels, double watts, double hazard) {
        double nominal = Math.max(1, fuelChannels) * NOMINAL_CHANNEL_WATTS;
        double excursion = Math.log10(1 + Math.max(0, watts) / nominal);
        return (float) Math.clamp(34 + 10 * Math.clamp(hazard, 0, 1) + 5 * excursion, 34, 48);
    }

    /** Strength of each delayed pressure-wave burst around the destroyed core. */
    public static float chernobylSecondaryExplosion(float coreStrength) {
        return (float) Math.clamp(coreStrength * 0.34, 12, 16);
    }

    /** Extra local flux beside a withdrawn rod; it reaches zero two bundle-widths away. */
    public static double localRodHeating(double distanceSquared, double withdrawn) {
        if (distanceSquared >= 4 || withdrawn <= 0) return 0;
        return 0.28 * Math.clamp(withdrawn, 0, 1) * (1 - distanceSquared / 4);
    }

    /** One tick of the graphite stack's slow thermal response to coolant and mean channel power. */
    public static double graphiteStep(double graphiteKelvin, double coolantKelvin, double nominalPowerPerChannel) {
        double target = Math.max(293.15, coolantKelvin + 180 * Math.clamp(nominalPowerPerChannel, 0, 4));
        double rate = target > graphiteKelvin ? 0.002 : 0.0005;
        return Math.clamp(graphiteKelvin + (target - graphiteKelvin) * rate, 1, 2000);
    }

    private ReactorPhysics() { }

    /** Mutable kinetic state; precursors are in units that make the delayed source {@code PRECURSOR_DECAY * precursors}. */
    public static final class Kinetics {
        public double level = SOURCE_LEVEL, precursors = equilibriumPrecursors(SOURCE_LEVEL), iodine, xenon;

        /** Advances one tick at a constant reactivity with implicit Euler substeps, which stay stable for any reactivity up to the cap. */
        public void step(double reactivity) {
            double rho = Math.min(reactivity, MAX_REACTIVITY), h = TICK_SECONDS / SUBSTEPS;
            double a = (rho - BETA) / GENERATION_SECONDS, b = BETA / GENERATION_SECONDS, l = PRECURSOR_DECAY;
            double n = level, c = precursors;
            for (int i = 0; i < SUBSTEPS; i++) {
                // (1 - h a) n' - h l c' = n + h S ; -h b n' + (1 + h l) c' = c
                double m11 = 1 - h * a, m12 = -h * l, m21 = -h * b, m22 = 1 + h * l;
                double r1 = n + h * SOURCE_LEVEL / GENERATION_SECONDS * BETA, r2 = c, det = m11 * m22 - m12 * m21;
                n = (r1 * m22 - m12 * r2) / det;
                c = (m11 * r2 - m21 * r1) / det;
            }
            level = Math.clamp(n, SOURCE_LEVEL, MAX_LEVEL);
            precursors = Math.clamp(c, 0, equilibriumPrecursors(MAX_LEVEL));
            double flux = Math.min(100, level);
            iodine += TICK_SECONDS * IODINE_DECAY * (flux - iodine);
            double born = XENON_DECAY * (1 + XENON_BURNOUT) * ((1 - IODINE_SHARE) * flux + IODINE_SHARE * iodine);
            xenon += TICK_SECONDS * (born - XENON_DECAY * (1 + XENON_BURNOUT * flux) * xenon);
            xenon = Math.max(0, xenon);
        }

        public Kinetics copy() {
            var copy = new Kinetics();
            copy.level = level;
            copy.precursors = precursors;
            copy.iodine = iodine;
            copy.xenon = xenon;
            return copy;
        }

        public void shutdown() {
            level = SOURCE_LEVEL;
            precursors = equilibriumPrecursors(SOURCE_LEVEL);
        }
    }

    public static double equilibriumPrecursors(double level) { return BETA * level / (GENERATION_SECONDS * PRECURSOR_DECAY); }

    /** Neutron leakage of a bare box or cylinder, from its geometric buckling. */
    public static double leakage(int width, int depth, int height, boolean circular) {
        double radial = circular ? Math.pow(2.405 / (width / 2d), 2) : Math.PI * Math.PI * (1d / (width * width) + 1d / (depth * depth));
        return MIGRATION_AREA * (radial + Math.PI * Math.PI / (height * height));
    }

    /** Radial importance of a column, from a cosine flux with extrapolated edges one block outside the core. */
    public static double radial(int index, int size) { return Math.cos(Math.PI * (index + 0.5 - size / 2d) / (size + 2)); }

    /** Radial importance of column (i, j) of a circular core {@code size} across. */
    public static double radialDisc(int i, int j, int size) {
        double x = i + 0.5 - size / 2d, z = j + 0.5 - size / 2d;
        return Math.cos(Math.PI / 2 * Math.sqrt(x * x + z * z) / (size / 2d + 1));
    }

    /** Axial flux of each layer, normalised to sum to one; reactivity uses its square, also normalised. */
    public static double[] axialFlux(int height, boolean importance) {
        double[] shape = new double[height];
        double sum = 0;
        for (int k = 0; k < height; k++) {
            double s = Math.sin(Math.PI * (k + 0.5) / height);
            shape[k] = importance ? s * s : s;
            sum += shape[k];
        }
        for (int k = 0; k < height; k++) shape[k] /= sum;
        return shape;
    }

    /** Overlap, in blocks, of a one-block rod segment whose bottom is at {@code bottom} with the layer starting at {@code layer}. */
    public static double overlap(double bottom, double layer) { return Math.max(0, Math.min(bottom + 1, layer + 1) - Math.max(bottom, layer)); }

    /** Overlap, in blocks, of a continuous segment with one core layer. */
    public static double overlap(double bottom, double length, double layer) {
        return Math.max(0, Math.min(bottom + length, layer + 1) - Math.max(bottom, layer));
    }

    /** Void of a fuel channel: the part of its water-equivalent capacity not filled by liquid. */
    public static double voidFraction(double kilograms, double specificJoules, double capacityKilograms) {
        if (capacityKilograms <= 0) return 1;
        double liquid = kilograms * (1 - quality(specificJoules));
        return Math.clamp(1 - liquid / capacityKilograms, 0, 1);
    }

    /** Steam quality of water at the channels' saturation with the given specific energy (internal coolant energy reference). */
    public static double quality(double specificJoules) {
        return Math.clamp((specificJoules - CoolantConstants.CP * CoolantConstants.SATURATION) / CoolantConstants.LATENT, 0, 1);
    }

    /** Coolant temperature for the specific energy: subcooled, saturated, or superheated steam. */
    public static double coolantKelvin(double specificJoules) {
        double saturated = CoolantConstants.CP * CoolantConstants.SATURATION;
        if (specificJoules <= saturated) return specificJoules / CoolantConstants.CP;
        if (specificJoules <= saturated + CoolantConstants.LATENT) return CoolantConstants.SATURATION;
        return (specificJoules - CoolantConstants.LATENT) / CoolantConstants.CP;
    }

    /** Internal coolant constants, kept here so this class needs no game classes. */
    static final class CoolantConstants {
        static final double CP = 4180, LATENT = 2_257_000, SATURATION = SATURATION_KELVIN;
        private CoolantConstants() { }
    }
}
