package net.createplant.nuclear;

/** Dependency-free regression checks of the RBMK physics; executable with java -ea. */
public final class ReactorPhysicsTest {
    private static int checks;

    public static void main(String[] args) {
        var critical = new ReactorPhysics.Kinetics();
        critical.level = 1;
        critical.precursors = ReactorPhysics.equilibriumPrecursors(1);
        for (int i = 0; i < 200; i++) critical.step(0);
        near(critical.level, 1, 1e-6, "an exactly critical core holds its power");

        var sub = start();
        for (int i = 0; i < 200; i++) sub.step(-0.01);
        check(sub.level < 0.6 && sub.level > ReactorPhysics.SOURCE_LEVEL, "negative reactivity drops power promptly, then on the delayed neutrons: " + sub.level);

        var delayed = start();
        for (int i = 0; i < 20; i++) delayed.step(0.001);
        double period = 1 / Math.log(delayed.level / 1) * 20 * ReactorPhysics.TICK_SECONDS;
        check(delayed.level > 1.1 && delayed.level < 2, "below prompt critical power rises on the delayed neutrons: " + delayed.level + " after one second");

        var prompt = start();
        for (int i = 0; i < 20; i++) prompt.step(0.012);
        check(prompt.level > 100, "above prompt critical power runs away within a second: " + prompt.level);
        var capped = start();
        for (int i = 0; i < 20; i++) capped.step(10);
        check(Double.isFinite(capped.level), "the reactivity cap keeps the excursion finite");

        var xenon = start();
        for (int i = 0; i < 20 * 60 * 120; i++) xenon.step(0);
        near(xenon.xenon, 1, 0.02, "xenon settles at its nominal equilibrium");
        double equilibrium = xenon.xenon;
        xenon.shutdown();
        double peak = 0;
        for (int i = 0; i < 20 * 60 * 30; i++) { xenon.step(-0.05); peak = Math.max(peak, xenon.xenon); }
        check(peak > equilibrium * 1.3, "after a shutdown iodine decays into a xenon pit: " + peak);

        check(ReactorPhysics.leakage(3, 3, 2, false) > ReactorPhysics.leakage(9, 9, 6, false), "small cores leak more neutrons");
        // A full-power 12-wide, 4-high cylinder with 15 % rod columns: excess reactivity to hold with rods, not prompt critical by itself.
        double excess = ReactorPhysics.FUEL_WORTH * 0.85 - ReactorPhysics.leakage(12, 12, 4, true) - ReactorPhysics.STRUCTURE_ABSORPTION;
        check(excess > ReactorPhysics.BETA && excess < 10 * ReactorPhysics.BETA && excess - ReactorPhysics.ABSORBER_WORTH * 0.15 < 0,
            "a large cylinder needs its rods and they can shut it down: " + excess);
        check(ReactorPhysics.radialDisc(5, 5, 12) > ReactorPhysics.radialDisc(0, 5, 12), "the centre of a cylinder matters most");
        double[] flux = ReactorPhysics.axialFlux(4, false), importance = ReactorPhysics.axialFlux(4, true);
        near(flux[0] + flux[1] + flux[2] + flux[3], 1, 1e-12, "axial flux is normalised");
        check(importance[1] > importance[0] && importance[0] < flux[0], "importance weights the middle of the core more than the flux does");

        near(ReactorPhysics.overlap(3.25, 3), 0.75, 1e-12, "a rod segment a quarter block low covers three quarters of the layer");
        check(ReactorPhysics.overlap(5, 3) == 0, "a rod above a layer does not touch it");
        check(ReactorPhysics.CHANNEL_GRID == 2 && ReactorPhysics.RODS_PER_BLOCK == 4, "one block carries a 2 x 2 fine-channel grid");
        check(ReactorCoreVariants.MIN_WIDTH == 5 && ReactorCoreVariants.MAX_WIDTH == 13
            && ReactorCoreVariants.RECOMMENDED_DIAMETERS.equals(java.util.List.of(5, 7, 9, 11, 13)),
            "reactors start at five by five and expose the five recommended circular variants");
        int[][] variants = {{5, 9, 21, 44, 8}, {7, 11, 37, 99, 13}, {9, 13, 69, 182, 26},
            {11, 15, 97, 279, 37}, {13, 17, 137, 397, 51}};
        for (int[] expected : variants) {
            var actual = ReactorCoreVariants.circular(expected[0]);
            check(actual.outerDiameter() == expected[1] && actual.bundleColumns() == expected[2]
                && actual.fuelChannels() == expected[3] && actual.controlChannels() == expected[4], expected[0] + " m variant is "
                    + expected[1] + " m outside with stable bundles/fuel/control: " + actual.bundleColumns() + "/"
                    + actual.fuelChannels() + "/" + actual.controlChannels());
        }
        var maximum = ReactorCoreVariants.circular(ReactorCoreVariants.MAX_WIDTH);
        check(maximum.fineChannels() == ReactorCoreStructure.MAX_CHANNELS
            && maximum.fuelChannels() == ReactorCoreStructure.DEFAULT_FUEL_CHANNELS
            && maximum.controlChannels() == ReactorCoreStructure.DEFAULT_CONTROL_CHANNELS,
            "maximum-core constants match the generated circular layout");
        check(ReactorPhysics.CORE_HEIGHT == 7 && ReactorPhysics.UPPER_SHIELD_HEIGHT == 2 && ReactorPhysics.SUSPENSION_HEIGHT == 12
            && ReactorPhysics.LOWER_SHIELD_HEIGHT == 2 && ReactorPhysics.LOWER_SERVICE_HEIGHT == 5
            && ReactorPhysics.FUEL_ASSEMBLY_LENGTH == 21 && ReactorPhysics.CAP_OFFSET == 15,
            "vertical dimensions are twelve upper tubes, two upper shield, seven core, two lower shield and five lower tubes");
        double insertedDisplacerBottom = (ReactorPhysics.CORE_HEIGHT - ReactorPhysics.DISPLACER_LENGTH) / 2 - ReactorPhysics.ROD_TRAVEL;
        check(insertedDisplacerBottom >= -ReactorPhysics.LOWER_STRUCTURE_HEIGHT && insertedDisplacerBottom < -ReactorPhysics.LOWER_SHIELD_HEIGHT,
            "the fully inserted graphite displacer stops in the five-metre lower tube bundle: " + insertedDisplacerBottom);
        near(ReactorPhysics.ABSORBER_LENGTH + ReactorPhysics.CONNECTOR_LENGTH + ReactorPhysics.DISPLACER_LENGTH, 12.75, 1e-12,
            "continuous control-rod geometry totals 12.75 metres");
        near(ReactorPhysics.overlap(1.25, ReactorPhysics.DISPLACER_LENGTH, 1)
            + ReactorPhysics.overlap(1.25, ReactorPhysics.DISPLACER_LENGTH, 2)
            + ReactorPhysics.overlap(1.25, ReactorPhysics.DISPLACER_LENGTH, 3)
            + ReactorPhysics.overlap(1.25, ReactorPhysics.DISPLACER_LENGTH, 4)
            + ReactorPhysics.overlap(1.25, ReactorPhysics.DISPLACER_LENGTH, 5), 4.5, 1e-12,
            "fractional graphite displacer conserves its 4.5-metre length across core layers");

        double cold = 4180 * 293.15, saturated = 4180 * ReactorPhysics.SATURATION_KELVIN;
        near(ReactorPhysics.INLET_KELVIN - 273.15, 270, 1e-12, "rated feedwater enters at 270 Celsius");
        near(ReactorPhysics.SATURATION_KELVIN - 273.15, 284, 1e-12, "pressure-channel water boils at 284 Celsius");
        near(ReactorPhysics.CHANNEL_PASCALS / 1e5, 67.5377036205875, 1e-9, "the channel boundary is the saturation pressure at 284 Celsius");
        near(ReactorPhysics.CHANNEL_PASCALS / 101_325, 66.6545310837281, 1e-9, "the channel boundary is approximately 70 atmospheres");
        near(ReactorPhysics.NOMINAL_CHANNEL_WATTS * ReactorCoreStructure.DEFAULT_FUEL_CHANNELS, 765e6, 1e-6,
            "the maximum 397-fuel-channel core is rated at 765 MW thermal");
        check(ReactorPhysics.NOMINAL_INLET_MB_PER_TICK == 220_000 && ReactorPhysics.NOMINAL_LIQUID_OUTLET_MB_PER_TICK == 200_000
            && ReactorPhysics.NOMINAL_STEAM_OUTLET_MB_PER_TICK == 500_000 && ReactorPhysics.NOMINAL_MIXED_OUTLET_MB_PER_TICK == 700_000,
            "rated Create volume flows match the 4,400 L/s feed and 4,000/10,000 L/s separated outlets");
        check(ReactorPhysics.NOMINAL_STEAM_MASS_MB_PER_TICK == 11_243 && ReactorPhysics.NOMINAL_LIQUID_MASS_MB_PER_TICK == 208_757,
            "the internal water-equivalent split preserves mass and the 765 MW sensible-plus-latent heat balance");
        check(ReactorPhysics.voidFraction(4, cold, 4) == 0, "a channel full of cold water has no void");
        near(ReactorPhysics.voidFraction(4, saturated + 0.25 * 2_257_000, 4), 0.25, 1e-12, "boiling a quarter of the water leaves a quarter void");
        near(ReactorPhysics.coolantKelvin(saturated + 2_257_000), ReactorPhysics.SATURATION_KELVIN, 1e-9,
            "one kilogram absorbs the full latent heat while boiling without increasing channel temperature");
        check(ReactorPhysics.voidFraction(0, cold, 4) == 1, "an empty channel is all void");
        near(ReactorPhysics.coolantKelvin(saturated + 2_257_000 + 4180 * 50), ReactorPhysics.SATURATION_KELVIN + 50, 1e-9, "dry steam above boiling is superheated");
        near(ReactorPhysics.SATURATION_KELVIN + ReactorPhysics.NOMINAL_CHANNEL_WATTS
            / (ReactorPhysics.CELL_CONDUCTANCE * ReactorPhysics.CORE_HEIGHT / ReactorPhysics.RODS_PER_BLOCK),
            ReactorPhysics.NOMINAL_FUEL_KELVIN, 1e-9, "a wetted nominal-power channel settles at the calibrated fuel-centre target");
        near(ReactorPhysics.nucleateBoilingHeatTransfer(1), 1, 1e-12,
            "the nominal channel retains its calibrated forced-circulation conductance");
        check(ReactorPhysics.SATURATION_KELVIN + 1.75 * ReactorPhysics.NOMINAL_CHANNEL_WATTS
                / (ReactorPhysics.CELL_CONDUCTANCE * ReactorPhysics.CORE_HEIGHT / ReactorPhysics.RODS_PER_BLOCK
                    * ReactorPhysics.nucleateBoilingHeatTransfer(1.75)) < ReactorPhysics.NORMAL_FUEL_MAX_KELVIN,
            "nucleate boiling keeps the normal radial power peak inside the fuel-centre band");
        near(ReactorPhysics.nucleateBoilingHeatTransfer(10),
            ReactorPhysics.nucleateBoilingHeatTransfer(ReactorPhysics.NOMINAL_RADIAL_POWER_PEAK), 1e-12,
            "accident power receives no cooling credit beyond the normal radial peak");
        near(ReactorPhysics.NORMAL_FUEL_MIN_KELVIN - 273.15, 1000, 1e-12, "normal fuel-centre band starts at 1000 Celsius");
        near(ReactorPhysics.NORMAL_FUEL_MAX_KELVIN - 273.15, 1500, 1e-12, "normal fuel-centre band ends at 1500 Celsius");
        check(ReactorPhysics.ALARM_KELVIN > ReactorPhysics.NORMAL_FUEL_MAX_KELVIN,
            "the fuel alarm stays above the normal centre-temperature band");
        near(ReactorPhysics.coolantHeatTransfer(.85), 1, 1e-12, "nucleate boiling keeps the fuel surface wetted");
        check(ReactorPhysics.coolantHeatTransfer(.96) < 1 && ReactorPhysics.coolantHeatTransfer(.96) > .02,
            "heat transfer falls through the dryout transition");
        near(ReactorPhysics.coolantHeatTransfer(1), .02, 1e-12, "fully voided steam retains only residual heat transfer");
        double wetSurface = ReactorPhysics.fuelSurfaceKelvin(ReactorPhysics.NOMINAL_FUEL_KELVIN, ReactorPhysics.SATURATION_KELVIN, .5);
        near(wetSurface, ReactorPhysics.SATURATION_KELVIN + .08 * (ReactorPhysics.NOMINAL_FUEL_KELVIN - ReactorPhysics.SATURATION_KELVIN), 1e-9,
            "a wetted fuel surface includes only the coolant-film share of the centre-to-water temperature drop");
        check(wetSurface > ReactorPhysics.SATURATION_KELVIN && wetSurface < ReactorPhysics.NOMINAL_FUEL_KELVIN
            && ReactorPhysics.fuelSurfaceKelvin(ReactorPhysics.NOMINAL_FUEL_KELVIN, ReactorPhysics.SATURATION_KELVIN, 1) > wetSurface,
            "reported fuel surface temperature lies between coolant and centre and rises during dryout");

        check(PlugPhysicsSimulator.PLUG_MASS_KG == 350 && PlugPhysicsSimulator.RELIEF_OPEN_METRES == .05f,
            "the pressure-channel relief animation is calibrated for the 350 kg plug and five-centimetre opening");
        check(PlugPhysicsSimulator.localPressure(false, 2000, 0, 0) == 0
            && PlugPhysicsSimulator.localPressure(true, ReactorPhysics.SATURATION_KELVIN - 10, 0, 0) < 1,
            "inactive channels and water well below boiling keep their plugs exactly seated");
        check(PlugPhysicsSimulator.localPressure(true, ReactorPhysics.SATURATION_KELVIN, 0, 0) == 0
            && PlugPhysicsSimulator.localPressure(true, ReactorPhysics.COOLANT_ALARM_KELVIN - .01, 0, 0) < 1,
            "plugs stay seated during normal boiling and below the coolant alarm, even without measured flow");
        check(PlugPhysicsSimulator.localPressure(true, ReactorPhysics.COOLANT_ALARM_KELVIN, 0, 0) == 1
            && PlugPhysicsSimulator.localPressure(true, ReactorPhysics.COOLANT_ALARM_KELVIN + 5, 0, 0) > 1,
            "a stagnant channel can lift its plug from the coolant alarm temperature");
        check(PlugPhysicsSimulator.localPressure(true, ReactorPhysics.CHANNEL_RUPTURE_KELVIN, 128, 0) < 1
            && PlugPhysicsSimulator.localPressure(true, ReactorPhysics.CHANNEL_RUPTURE_KELVIN, 0, 1) < 1,
            "local flow or full rod insertion suppresses cap lift in the pseudo-pressure binding");
        boolean staggered = false;
        for (int tick = 0; tick < 200; tick++) {
            int moving = 0;
            for (int slot = 0; slot < PlugPhysicsSimulator.PLUGS; slot++) {
                check(PlugPhysicsSimulator.lift(7, -3, slot, tick, .999) == 0,
                    "pressure below one produces no visual movement");
                float lift = PlugPhysicsSimulator.lift(7, -3, slot, tick, 1.5);
                check(lift >= 0 && lift <= PlugPhysicsSimulator.MAX_LIFT_METRES
                    && lift == PlugPhysicsSimulator.lift(7, -3, slot, tick, 1.5),
                    "plug lift is bounded and deterministic");
                if (lift > 1f / 512) moving |= 1 << slot;
            }
            staggered |= moving != 0 && moving != (1 << PlugPhysicsSimulator.PLUGS) - 1;
        }
        check(staggered, "independent phases and noise make the four caps lift sparsely rather than together");
        var plugs = new PlugPhysicsSimulator();
        double[] hot = {1800, 0, 0, 0}, noFlow = new double[4], withdrawn = new double[4];
        boolean vented = false, pressureSuppressed = false, landed = false;
        double maximumRelief = 0;
        for (int tick = 0; tick < 500; tick++) {
            var step = plugs.tick(7, -3, tick, 1, hot, noFlow, withdrawn);
            vented |= (step.ventingMask() & 1) != 0;
            landed |= (step.landedMask() & 1) != 0;
            maximumRelief = Math.max(maximumRelief, plugs.relief(0));
            pressureSuppressed |= vented && plugs.pressure(0) < 1;
        }
        check(vented && maximumRelief > 0 && pressureSuppressed && landed,
            "a five-centimetre opening accumulates temporary relief, closes the plug and produces a landing transition");

        // The graphite tip entering the top of a three-high core first adds reactivity: the positive scram effect.
        double[] weight = ReactorPhysics.axialFlux(3, true);
        double tipIn = weight[2] * ReactorPhysics.DISPLACER_WORTH * 0.5;
        double absorberIn = weight[2] * (-ReactorPhysics.ABSORBER_WORTH) + weight[1] * ReactorPhysics.DISPLACER_WORTH;
        check(tipIn > 0 && absorberIn < 0, "the displacer adds reactivity before the absorber takes it away");

        // AZ-5 is dangerous only under all the Chernobyl conditions at once, and then the tip outweighs the absorber.
        check(ReactorPhysics.scramHazard(0.03, 1.8, ReactorPhysics.SATURATION_KELVIN) > 0.8, "few rods, high xenon and near-boiling water are the Chernobyl state");
        check(ReactorPhysics.scramHazard(0.5, 1.8, ReactorPhysics.SATURATION_KELVIN) == 0 && ReactorPhysics.scramHazard(0.03, 0.9, ReactorPhysics.SATURATION_KELVIN) == 0
            && ReactorPhysics.scramHazard(0.03, 1.8, 300) == 0, "any one condition missing makes AZ-5 safe");
        check(ReactorPhysics.DISPLACER_WORTH * (1 + ReactorPhysics.TIP_SURGE) > ReactorPhysics.ABSORBER_WORTH, "under the full hazard a tip outweighs an absorber");

        // Only a jammed AZ-5 excursion gets the plant-destroying profile; ordinary ruptures keep their original cap.
        double hazard = ReactorPhysics.scramHazard(0.03, 1.8, ReactorPhysics.SATURATION_KELVIN);
        check(ReactorPhysics.chernobylExplosion(true, true, hazard), "a jammed hazardous AZ-5 excursion selects the Chernobyl explosion");
        check(!ReactorPhysics.chernobylExplosion(false, true, hazard) && !ReactorPhysics.chernobylExplosion(true, false, hazard)
            && !ReactorPhysics.chernobylExplosion(true, true, ReactorPhysics.JAM_HAZARD), "scram, jam and high captured hazard are all required");
        float ordinarySteam = ReactorPhysics.steamExplosion(10_000), ordinaryCore = ReactorPhysics.coreExplosion(137, 1e12);
        float catastrophicSteam = ReactorPhysics.chernobylSteamExplosion(10_000, hazard);
        float catastrophicCore = ReactorPhysics.chernobylCoreExplosion(ReactorCoreStructure.DEFAULT_FUEL_CHANNELS, 1e12, hazard);
        check(ordinarySteam <= 10 && ordinaryCore <= 16, "ordinary rupture strengths remain capped at 10 and 16");
        check(catastrophicSteam >= 20 && catastrophicCore >= 40 && catastrophicCore <= 48,
            "the AZ-5 accident has a much larger steam and core blast: " + catastrophicSteam + "/" + catastrophicCore);
        check(ReactorPhysics.chernobylSecondaryExplosion(catastrophicCore) >= 12 && ReactorPhysics.CHERNOBYL_SECONDARY_BLASTS == 8,
            "the catastrophic core blast is followed by eight substantial pressure-wave bursts");

        // With the rods out the core wanders: a random walk settling back with the set spread, and none with a sixth of the absorber in.
        check(ReactorPhysics.instability(0) == 1 && ReactorPhysics.instability(1 / 6d) == 0 && ReactorPhysics.instability(0.5) == 0, "only a core with its rods out is unstable");
        var random = new java.util.Random(7);
        double walk = 0, squares = 0;
        for (int i = 0; i < 200_000; i++) {
            walk = ReactorPhysics.wander(walk, 1, random.nextGaussian());
            squares += walk * walk;
        }
        near(Math.sqrt(squares / 200_000), 1, 0.08, "the wandering keeps its spread");
        near(ReactorPhysics.wander(1, 0, 0), 1 - ReactorPhysics.TICK_SECONDS / ReactorPhysics.WANDER_SECONDS, 1e-12, "and settles back without noise");

        // A gigawatt core with no shield gives a hundredth of a sievert a second at sixteen metres; a closed vault gives none.
        System.out.println("ReactorPhysicsTest: " + checks + " checks passed (delayed rise period " + String.format("%.1f", period) + " s)");
    }

    private static ReactorPhysics.Kinetics start() {
        var kinetics = new ReactorPhysics.Kinetics();
        kinetics.level = 1;
        kinetics.precursors = ReactorPhysics.equilibriumPrecursors(1);
        return kinetics;
    }

    private static void near(double actual, double expected, double tolerance, String message) {
        check(Math.abs(actual - expected) <= tolerance, message + ": " + actual + " vs " + expected);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
