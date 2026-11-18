package net.createplant.chemistry;

import java.util.List;
import net.createplant.registry.CEChlorAlkali;
import net.createplant.registry.CEMetals;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEFusion;
import net.createplant.registry.CEFusionFuel;
import net.createplant.registry.CEUranium;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** The existing electrolysis batches, driven by electrical work in the filter press. */
public record ElectrolysisProcess(Fluid feed, int input, Fluid cathode, int cathodeAmount, Fluid anode, int soda,
    Item metal, int metalAmount, Fluid recovered, int recoveredAmount, double cycleWork) {
    public ElectrolysisProcess(Fluid feed, int input, Fluid cathode, int cathodeAmount, Fluid anode, int soda) {
        this(feed, input, cathode, cathodeAmount, anode, soda, Items.AIR, 0, Fluids.EMPTY, 0, JOULES_PER_PLATE);
    }
    public boolean electrowinning() { return metal != Items.AIR; }
    /** Whether the anode gives off a gas; it gives none where the anode reaction stays in the solution, as in the sulfurous acid cell. */
    public boolean anodeGas() { return anode != Fluids.EMPTY; }
    private static ElectrolysisProcess metal(Fluid feed, Item product) {
        return new ElectrolysisProcess(feed, 1000, Fluids.EMPTY, 0, Fluids.EMPTY, 0, product, 3, CEMetals.DILUTE_SULFURIC_ACID.get(), 250, 20000);
    }
    public static final int PLATES_PER_HEAD = 16, WATER_PER_PLATE = 3, FLUORIDE_PER_PLATE = 2,
        BRINE_PER_PLATE = 2, SODA_PER_PLATE = 2, SULFUROUS_PER_PLATE = 2;
    /** The hybrid sulfur cell oxidises sulfur dioxide at the anode instead of water, at about 0.6 V instead of 1.8 V. */
    public static final double SULFUROUS_JOULES_PER_PLATE = 20;
    public static final double JOULES_PER_PLATE = 50, NICKEL_JOULES_PER_PLATE = 35, OHMS_PER_PLATE = 160,
        BUFFER_JOULES = 40000;

    private static final class Recipes {
        private static final List<ElectrolysisProcess> VALUES = List.of(new ElectrolysisProcess(Fluids.WATER, 3, CEFluids.HYDROGEN_GAS.get(), 2, CEFluids.OXYGEN_GAS.get(), 0),
            new ElectrolysisProcess(CEFusionFuel.HEAVY_WATER.get(), 3, CEFusion.DEUTERIUM_GAS.get(), 2, CEFluids.OXYGEN_GAS.get(), 0),
            new ElectrolysisProcess(CEChlorAlkali.BRINE.get(), 2, CEFluids.HYDROGEN_GAS.get(), 1, CEChlorAlkali.CHLORINE.get(), 2),
            new ElectrolysisProcess(CEUranium.HYDROGEN_FLUORIDE.get(), 2, CEFluids.HYDROGEN_GAS.get(), 1, CEUranium.FLUORINE.get(), 0),
            // SO2 + 2 H2O -> H2SO4 + H2: hydrogen at the cathode, the acid stays in the anolyte and leaves with the spent liquor.
            new ElectrolysisProcess(CEUranium.SULFUROUS_ACID.get(), SULFUROUS_PER_PLATE, CEFluids.HYDROGEN_GAS.get(), 1, Fluids.EMPTY, 0,
                Items.AIR, 0, CEUranium.SULFURIC_ACID.get(), 1, SULFUROUS_JOULES_PER_PLATE),
            metal(CEMetals.IRON_SULFATE_SOLUTION.get(), Items.IRON_INGOT),
            metal(CEMetals.COPPER_SULFATE_SOLUTION.get(), Items.COPPER_INGOT),
            metal(CEMetals.ZINC_SULFATE_SOLUTION.get(), com.simibubi.create.AllItems.ZINC_INGOT.get()),
            metal(CEMetals.TIN_SULFATE_SOLUTION.get(), CEMetals.TIN_INGOT.get()));
    }

    public static List<ElectrolysisProcess> all() { return Recipes.VALUES; }

    public static ElectrolysisProcess of(FluidStack fluid) {
        if (!fluid.isEmpty()) for (var recipe : all()) if (fluid.is(recipe.feed())) return recipe;
        return null;
    }

    public static double work(ElectrolysisProcess process, double cycles, int nickel) {
        return process == null ? 0 : work(cycles, nickel) * process.cycleWork() / JOULES_PER_PLATE;
    }
    public static double work(double cycles, int nickel) {
        double efficient = Math.min(cycles, nickel);
        return efficient * NICKEL_JOULES_PER_PLATE + (cycles - efficient) * JOULES_PER_PLATE;
    }
}
