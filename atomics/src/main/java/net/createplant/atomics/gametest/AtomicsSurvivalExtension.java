package net.createplant.atomics.gametest;

import net.createplant.gametest.SurvivalReachabilityGameTests;
import static net.createplant.gametest.SurvivalReachabilityGameTests.has;

import java.util.Arrays;
import java.util.Set;
import net.createplant.atomics.recipe.ConverterRecipe;
import net.createplant.atomics.recipe.ContinuousFactoryRecipe;
import net.createplant.atomics.recipe.CubicPressRecipe;
import net.createplant.atomics.recipe.FuelCycleRecipe;
import net.createplant.registry.*;
import net.createplant.atomics.registry.*;
import net.createplant.thermal.FluidForm;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** The Atomics & Steel part of the survival reachability audit; loaded by name, so the audit runs without the addon too. */
final class AtomicsSurvivalExtension implements SurvivalReachabilityGameTests.Extension {
    static { SurvivalReachabilityGameTests.EXTENSIONS.add(new AtomicsSurvivalExtension()); }

    private static boolean gasInput(ConverterRecipe recipe, Set<Object> reached) {
        for (FluidStack choice : recipe.gasIngredient().getFluids()) if (SurvivalReachabilityGameTests.has(reached, choice)) return true;
        return false;
    }
    @Override public Boolean equipment(Recipe<?> recipe, Set<Item> reached) {
        if (recipe instanceof CubicPressRecipe) return has(reached, "cubic_press");
        if (recipe instanceof ConverterRecipe converter)
            return !converter.legacy() && has(reached, "converter_shell", "converter_trunnion_bearing", "silica_refractory_bricks");
        if (recipe instanceof ContinuousFactoryRecipe continuous)
            return has(reached, continuous.machine().id, "continuous_factory_outlet", "continuous_factory_frame")
                && switch (continuous.machine()) {
                    case KILN -> has(reached, "rotary_kiln_exhaust");
                    case MILL -> has(reached, "rolling_stand");
                    case WINDER -> has(reached, "winder_brake");
                };
        if (recipe instanceof FuelCycleRecipe)
            return has(reached, "pulse_extraction_column", "extraction_column_shell", "extraction_pulse_plate", "extraction_column_outlet",
                "vitrification_melter", "vitrification_refractory_shell", "vitrification_electrode");
        return null;
    }
    @Override public boolean blocked(Recipe<?> recipe, Set<Item> items, Set<Object> fluids) {
        return recipe instanceof ConverterRecipe converter && (!gasInput(converter, fluids) || converter.metalIngredient()
            .filter(metal -> Arrays.stream(metal.getFluids()).noneMatch(stack -> fluids.contains(stack.getFluid()))).isPresent());
    }
    @Override public boolean produces(Recipe<?> recipe, Set<Item> items, Set<Object> fluids) {
        boolean changed = false;
        if (recipe instanceof ContinuousFactoryRecipe continuous)
            changed |= switch (continuous.machine()) {
                case KILN, MILL -> items.add(CEContinuousFactory.SCRAP.get());
                case WINDER -> items.add(CEContinuousFactory.LOOSE_WINDING.get());
            };
        if (recipe instanceof ConverterRecipe converter) {
            if (!converter.byproduct().isEmpty()) changed |= fluids.add(converter.byproduct().getFluid());
            if (!converter.metalResult().isEmpty()) changed |= fluids.add(converter.metalResult().getFluid());
        }
        return changed;
    }
    @Override public boolean fixed(Set<Item> items, Set<Object> fluids) {
        boolean changed = false;
        if (has(items, "fuel_rod", "reactor_fuel_channel", "reactor_graphite", "biological_shield") && fluids.contains(Fluids.WATER))
            changed |= items.add(CENuclear.SPENT_FUEL_ROD.get());
        if(has(items,"control_rod","reactor_fuel_channel"))changed|=items.add(CENuclear.SPENT_CONTROL_ROD.get());
        if(has(items,"fusion_reactor_controller","lithium_lead_blanket_cartridge","tungsten_divertor_cassette") && fluids.contains(FluidForm.HOT_REACTOR_COOLANT))changed|=items.add(CEFusion.SPENT_BLANKET.get()) | items.add(CEFusion.SPENT_DIVERTOR.get());
        if(has(items,"fusion_reactor_controller","irradiation_cartridge","tungsten_divertor_cassette") && fluids.contains(FluidForm.HOT_REACTOR_COOLANT))changed|=items.add(CEFusion.IRRADIATED_CARTRIDGE.get());
        if (has(items, "lithium_target_rod", "fuel_rod", "control_rod", "reactor_fuel_channel", "reactor_top_cap", "reactor_graphite", "biological_shield")
            && fluids.contains(Fluids.WATER)) changed |= items.add(CEFusionFuel.IRRADIATED_TARGET.get());
        return changed;
    }
    @Override public void roots(Set<Item> items) {
        items.add(CEUranium.URANIUM_ORE.get().asItem());items.add(CEUranium.DEEPSLATE_URANIUM_ORE.get().asItem());
        items.add(CENuclear.SERPENTINITE.get().asItem());
    }
    @Override public void audit(GameTestHelper helper, Set<Item> items, Set<Object> fluids) {
        helper.assertTrue(items.contains(Items.DIAMOND) && has(items, "cubic_press", "gas_centrifuge") && fluids.contains(CEUranium.ENRICHED_URANIUM_HEXAFLUORIDE.get()), "Artificial diamonds and enriched uranium are reachable from survival resources");
    }
}
