package net.createplant.gametest;

import java.util.*;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import net.createplant.CreatePlant;
import net.createplant.block.entity.ElectrolysisFurnaceBlockEntity;
import net.createplant.chemistry.ProcessModule;
import net.createplant.chemistry.ProcessRecipe;
import net.createplant.recipe.ConverterRecipe;
import net.createplant.recipe.PressureReactionRecipe;
import net.createplant.registry.*;
import net.createplant.thermal.ThermalStreams;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.material.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.gametest.*;

/** Proves the survival bootstrap, including chemical products and the initial fusion-fuel supply. */
@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public final class SurvivalReachabilityGameTests {
    /** The overhaul specification requires these systems to be audited but not changed or made part of this gate. */
    private static final Set<String> REPORT_ONLY_ITEMS = Set.of(
        "uranium_ore", "deepslate_uranium_ore", "uraninite", "fluorite", "yellowcake", "uranium_trioxide",
        "uranium_dioxide", "uranium_tetrafluoride", "enriched_uranium_dioxide",
        "green_uranium_pellet", "uranium_pellet", "reactor_graphite", "reactor_fuel_channel",
        "reactor_fuel_channel_tube", "serpentinite", "biological_shield", "biological_shield_drive",
        "biological_shield_coolant_outlet", "reactor_top_cap", "reactor_feed_bundle", "control_rod_link",
        "control_rod_controller", "reactor_control_console", "reactor_console_display", "reactor_console_desk", "reactor_core_map",
        "reactor_instrument_panel", "reactor_chart_recorder", "reactor_automatic_control", "fuel_pool", "fuel_rod",
        "spent_fuel_rod", "control_rod", "spent_control_rod", "protective_cap", "protective_coat",
        "protective_trousers", "protective_shoe_covers", "crane_drive", "quarry_drive",
        "fusion_reinforced_casing", "fusion_toroidal_field_coil", "fusion_central_solenoid", "fusion_vacuum_vessel",
        "fusion_breeding_blanket", "fusion_divertor", "fusion_reactor_controller", "fusion_vacuum_pump",
        "fusion_deuterium_port", "fusion_tritium_port", "fusion_ash_port", "fusion_helium_inlet",
        "fusion_helium_outlet", "fusion_coolant_inlet", "fusion_coolant_outlet", "fusion_control_console",
        "fusion_console_panel", "lithium_lead_blanket_cartridge", "spent_blanket_cartridge", "spent_divertor_cassette");
    /** Recipe-internal stock and wood created directly in the world are not inventory goals. */
    private static final Set<String> NON_GOALS = Set.of("charred_wood");
    /** The contaminated stream and the feedstock for rearrangement are supplied externally. */
    private static final Set<String> CREATIVE_ONLY = Set.of("contaminated_gas_bucket", "iodine", "trimethyl_phosphite_bucket",
        "crude_methylphosphonate_bucket", "dimethyl_methylphosphonate_bucket");
    // Keep items recovered from old machines out of the new-world production audit.
    private static final Set<String> LEGACY_ONLY = Set.of("glassy_slag", "raw_process_gas_bucket", "superheated_steam_bucket");

    private SurvivalReachabilityGameTests() { }

    private static boolean ours(ResourceLocation id) { return id.getNamespace().equals(CreatePlant.MOD_ID); }
    private static Item item(String id) { return BuiltInRegistries.ITEM.get(CreatePlant.location(id)); }
    private static boolean has(Set<Item> items, String... ids) {
        for (String id : ids) if (!BuiltInRegistries.ITEM.containsKey(CreatePlant.location(id)) || !items.contains(item(id))) return false;
        return true;
    }
    private static boolean itemInputs(Recipe<?> recipe, Set<Item> reached) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            boolean found = false;
            for (ItemStack choice : ingredient.getItems()) if (reached.contains(choice.getItem())) { found = true;break; }
            if (!found) return false;
        }
        return true;
    }
    private static boolean fluidInputs(ProcessingRecipe<?, ?> recipe, Set<Fluid> reached) {
        for (var ingredient : recipe.getFluidIngredients()) {
            boolean found = false;
            for (FluidStack choice : ingredient.getFluids()) if (reached.contains(choice.getFluid())) { found = true;break; }
            if (!found) return false;
        }
        return true;
    }
    private static boolean gasInput(ConverterRecipe recipe, Set<Fluid> reached) {
        for (FluidStack choice : recipe.gasIngredient().getFluids()) if (reached.contains(choice.getFluid())) return true;
        return false;
    }
    private static boolean equipmentFor(Recipe<?> recipe, Set<Item> reached) {
        if (recipe instanceof PressureReactionRecipe reaction)
            return reaction.module()==net.createplant.chemistry.ReactorModule.ABSORBER ? has(reached, "reaction_tower", "reactor_separator_core") : has(reached, "lidded_vessel", "vessel_stirrer");
        if (recipe instanceof net.createplant.recipe.ClarifyingRecipe) return has(reached, "clarifier_center_drive", "tank_casing", "clarifier_feed_port");
        if (recipe instanceof net.createplant.recipe.CubicPressRecipe) return has(reached, "cubic_press");
        if (recipe instanceof net.createplant.recipe.EntrainedGasificationRecipe)
            return reached.contains(com.simibubi.create.AllBlocks.BLAZE_BURNER.asItem())
                && has(reached, "steam_gasifier");
        if (recipe instanceof ConverterRecipe)
            return has(reached, "converter_shell", "converter_trunnion_bearing", "zirconia_refractory_bricks");
        if (recipe instanceof net.createplant.recipe.FuelCycleRecipe)
            return has(reached, "pulse_extraction_column", "extraction_column_shell", "extraction_pulse_plate", "extraction_column_outlet",
                "vitrification_melter", "vitrification_refractory_shell", "vitrification_electrode");
        return true;
    }
    private static boolean addRecipe(RecipeHolder<?> holder, Set<Item> items, Set<Fluid> fluids,
        net.minecraft.core.HolderLookup.Provider registries) {
        Recipe<?> recipe = holder.value();
        if (!equipmentFor(recipe, items) || !itemInputs(recipe, items)) return false;
        if (recipe instanceof ConverterRecipe converter && !gasInput(converter, fluids)) return false;
        if (recipe instanceof net.createplant.recipe.EntrainedGasificationRecipe gasifier
            && Arrays.stream(gasifier.steam().getFluids()).noneMatch(stack -> fluids.contains(stack.getFluid()))) return false;
        if (recipe instanceof ProcessingRecipe<?, ?> processing && !fluidInputs(processing, fluids)) return false;
        if (recipe instanceof com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe assembly) {
            if (Arrays.stream(assembly.getIngredient().getItems()).noneMatch(stack -> items.contains(stack.getItem()))) return false;
            for (var step : assembly.getSequence()) {
                var needed = new ArrayList<Ingredient>();
                step.getAsAssemblyRecipe().addAssemblyIngredients(needed);
                if (needed.stream().anyMatch(ingredient -> Arrays.stream(ingredient.getItems()).noneMatch(stack -> items.contains(stack.getItem())))
                    || !fluidInputs(step.getRecipe(), fluids)) return false;
            }
        }
        boolean changed = false;
        ItemStack result = recipe.getResultItem(registries);
        if (!result.isEmpty()) changed |= items.add(result.getItem());
        if (recipe instanceof com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe assembly) {
            changed |= items.add(assembly.getTransitionalItem().getItem());
            for (var step : assembly.getSequence()) for (var output : step.getRecipe().getRollableResults())
                if (!output.getStack().isEmpty() && output.getChance() > 0) changed |= items.add(output.getStack().getItem());
        }
        if (recipe instanceof ProcessingRecipe<?, ?> processing) {
            for (var output : processing.getRollableResults()) if (!output.getStack().isEmpty()) changed |= items.add(output.getStack().getItem());
            for (FluidStack output : processing.getFluidResults()) if (!output.isEmpty()) changed |= fluids.addAll(stateEntries(output));
        }
        if (recipe instanceof net.createplant.recipe.EntrainedGasificationRecipe gasifier) changed |= fluids.add(gasifier.gasResult().getFluid());
        if (recipe instanceof ConverterRecipe converter && !converter.byproduct().isEmpty()) changed |= fluids.add(converter.byproduct().getFluid());
        return changed;
    }
    private static boolean processEquipment(ProcessModule module, Set<Item> reached) {
        if (module == ProcessModule.GAS_LIQUID_SEPARATOR) return has(reached, "ammonia_separator");
        if (module == ProcessModule.FRACTION_SEPARATOR) return has(reached, "fractionating_column");
        if (module == ProcessModule.ABSORPTION_TRAYS) return has(reached, "large_absorption_tower");
        if (module == ProcessModule.GAS_CENTRIFUGE) return has(reached, "gas_centrifuge");
        if (module == ProcessModule.REACTION_VESSEL || module == ProcessModule.CATALYST_UNIT) return has(reached, "reaction_tower", "reactor_separator_core");
        return module == ProcessModule.STIRRED_REACTOR && has(reached, "lidded_vessel", "vessel_stirrer");
    }
    private static boolean addFixedProcesses(Set<Item> items, Set<Fluid> fluids) {
        boolean changed = false;
        for (ProcessRecipe process : ProcessRecipe.all()) {
            if (!processEquipment(process.module(), items) || process.catalyst() != Items.AIR && !items.contains(process.catalyst())
                || process.inputs().stream().anyMatch(stack -> !fluids.contains(stack.getFluid()))) continue;
            for (FluidStack output : process.outputs()) changed |= fluids.addAll(stateEntries(output));
        }
        if (has(items, "electrolysis_cell", "electrolysis_cell_controller", "electrolysis_electrode_head", "asbestos_wrapped_electrode")) {
            if (fluids.contains(Fluids.WATER)) changed |= fluids.add(CEFluids.HYDROGEN_GAS.get()) | fluids.add(CEFluids.OXYGEN_GAS.get());
            if (fluids.contains(CEFusionFuel.HEAVY_WATER.get())) changed |= fluids.add(CEFusion.DEUTERIUM_GAS.get()) | fluids.add(CEFluids.OXYGEN_GAS.get());
            if (fluids.contains(CEChlorAlkali.BRINE.get())) changed |= fluids.add(CEFluids.HYDROGEN_GAS.get())
                | fluids.add(CEChlorAlkali.CHLORINE.get()) | fluids.add(CEChlorAlkali.CAUSTIC_SODA.get());
            if (fluids.contains(CEUranium.HYDROGEN_FLUORIDE.get())) changed |= fluids.add(CEUranium.FLUORINE.get());
        }
        if (has(items, "electrolysis_cell", "electrolysis_cell_controller", "electrolysis_electrode_head", "asbestos_wrapped_electrode")) {
            for (var recipe : net.createplant.chemistry.ElectrolysisProcess.all()) if (recipe.electrowinning() && fluids.contains(recipe.feed())) {
                changed |= items.add(recipe.metal());changed |= fluids.add(recipe.recovered());
            }
        }
        if (has(items, "electrolysis_furnace_shell", "electrolysis_furnace_anode", "electrolysis_furnace_busbar"))
            for (var reduction : ElectrolysisFurnaceBlockEntity.reductions().entrySet())
                if (items.contains(reduction.getKey()) && items.contains(Items.COAL)) changed |= items.add(reduction.getValue().metal());
        if (has(items, "stainless_steel_pipe") && items.contains(com.simibubi.create.AllBlocks.NOZZLE.asItem())
            && items.contains(com.simibubi.create.AllBlocks.MECHANICAL_PUMP.asItem())) changed |= fluids.add(CEFluids.AIR_GAS.get());
        if (has(items, "fractionating_column") && fluids.contains(CEProcessFluids.COMPRESSED_AIR.get())) {
            changed |= fluids.add(CEFluids.LIQUID_OXYGEN.get());
            changed |= fluids.add(CEFluids.ARGON_GAS.get());
            changed |= fluids.add(CEFluids.NITROGEN_GAS.get());
        }
        if (has(items, "cooler_radiator", "cooling_fan")) for (var stream : ThermalStreams.cooledStreams().entrySet())
            if (fluids.contains(stream.getKey())) changed |= fluids.add(stream.getValue());
        if (has(items, "tube_piped_tank") && fluids.contains(CEFluids.STEAM.get()) && fluids.contains(CEProcessFluids.SLIME_COOLANT.get()))
            changed |= fluids.add(CEProcessFluids.WARM_SLIME_COOLANT.get());
        if(has(items,"reciprocating_compressor","reciprocating_cylinder"))for(var fluid:new ArrayList<>(fluids)){
            changed|=fluids.addAll(stateEntries(net.createplant.thermal.Compression.convert(new FluidStack(fluid,1),true)));
        }
        if(has(items,"stainless_boiler") && fluids.contains(Fluids.WATER))changed|=fluids.add(CEProcessFluids.HIGH_PRESSURE_STEAM.get());
        if(has(items,"fusion_reactor_controller","lithium_lead_blanket_cartridge","tungsten_divertor_cassette") && fluids.contains(CEProcessFluids.PRESSURISED_WATER.get()) && fluids.contains(CEFusion.DEUTERIUM_GAS.get()) && fluids.contains(CEFusion.TRITIUM_GAS.get()) && fluids.contains(CEFusion.LIQUID_HELIUM.get()))changed|=fluids.add(CEProcessFluids.HOT_REACTOR_COOLANT.get());
        if (has(items, "fuel_rod", "reactor_fuel_channel", "reactor_graphite", "biological_shield") && fluids.contains(Fluids.WATER))
            changed |= items.add(CENuclear.SPENT_FUEL_ROD.get());
        if(has(items,"control_rod","reactor_fuel_channel"))changed|=items.add(CENuclear.SPENT_CONTROL_ROD.get());
        if(has(items,"fusion_reactor_controller","lithium_lead_blanket_cartridge","tungsten_divertor_cassette") && fluids.contains(CEProcessFluids.HOT_REACTOR_COOLANT.get()))changed|=items.add(CEFusion.SPENT_BLANKET.get()) | items.add(CEFusion.SPENT_DIVERTOR.get());
        if(has(items,"fusion_reactor_controller","irradiation_cartridge","tungsten_divertor_cassette") && fluids.contains(CEProcessFluids.HOT_REACTOR_COOLANT.get()))changed|=items.add(CEFusion.IRRADIATED_CARTRIDGE.get());
        if (has(items, "lithium_target_rod", "fuel_rod", "control_rod", "reactor_fuel_channel", "reactor_top_cap", "reactor_graphite", "biological_shield")
            && fluids.contains(Fluids.WATER)) changed |= items.add(CEFusionFuel.IRRADIATED_TARGET.get());
        if (has(items, "ammonia_separator") && fluids.contains(CEProcessFluids.COLD_SYNTHESIS_MIXTURE.get())) {
            changed |= fluids.add(CEProcessFluids.LIQUID_AMMONIA.get());
            changed |= fluids.add(CEProcessFluids.COMPRESSED_SYNTHESIS_FEED.get());
        }
        // Create boilers and the thermal plant can change physical phase without changing the chemical feedstock.
        if (fluids.contains(Fluids.WATER)) changed |= fluids.add(CEFluids.STEAM.get());
        if (has(items, "cooler_radiator", "cooling_fan") && fluids.contains(CEFluids.AMMONIA_GAS.get()))
            changed |= fluids.add(CEProcessFluids.LIQUID_AMMONIA.get());
        if (has(items, "tube_piped_tank", "pressure_reducing_valve", "reciprocating_compressor", "reciprocating_cylinder")
            && fluids.contains(CEFluids.LIQUID_OXYGEN.get())) {
            for (Fluid vapour : new ArrayList<>(fluids)) {
                var pair = net.createplant.thermal.PhaseChange.ofVapour(new FluidStack(vapour, 1));
                if (pair != null && pair.liquid().get() != CEFusion.LIQUID_HELIUM.get()) changed |= fluids.add(pair.liquid().get());
            }
        }
        if (has(items, "cryogenic_cold_box_drive", "cryogenic_regenerator")
            && items.contains(CEMetals.STEEL_BLOCK.get())
            && items.contains(com.simibubi.create.AllBlocks.SHAFT.asItem())
            && fluids.contains(Fluids.WATER) && fluids.contains(CEFusion.HELIUM_GAS.get()))
            changed |= fluids.add(CEFusion.LIQUID_HELIUM.get());
        if (has(items, "lidded_vessel", "vessel_stirrer") && items.contains(com.simibubi.create.AllBlocks.BLAZE_BURNER.asItem())
            && fluids.contains(Fluids.WATER) && fluids.contains(CEUranium.SULFURIC_ACID.get()))
            changed |= fluids.add(CEProcessFluids.WASTEWATER.get());
        return changed;
    }
    /** The registered entries that name a stack's state, such as compressed air for air at a raised pressure, so this audit can follow them by id. */
    private static List<Fluid> stateEntries(FluidStack stack) {
        var entries = new ArrayList<Fluid>(List.of(stack.getFluid()));
        for (var variant : net.createplant.thermal.FluidIdentity.variants(stack.getFluid()))
            if (net.createplant.thermal.FluidIdentity.matches(stack, variant)) entries.add(variant);
        return entries;
    }
    private static boolean addFilledBuckets(Set<Item> items, Set<Fluid> fluids) {
        boolean changed = false;
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof BucketItem) || items.contains(item)) continue;
            if (item instanceof net.createplant.item.ProcessBucketItem bucket && bucket.alloy()
                && !items.contains(CEWorldFluids.EMPTY_ALLOY_BUCKET.get())) continue;
            var contained = FluidUtil.getFluidContained(new ItemStack(item));
            if (contained.isPresent() && fluids.contains(contained.orElseThrow().getFluid())) changed |= items.add(item);
        }
        return changed;
    }

    @GameTest(template = "test_empty")
    public static void everyOrdinaryMachineAndMaterialHasASurvivalRoute(GameTestHelper helper) {
        helper.assertTrue(!BuiltInRegistries.FLUID.containsKey(CreatePlant.location("liquid_air"))
                && !BuiltInRegistries.ITEM.containsKey(CreatePlant.location("liquid_air_bucket")),
            "Liquid air, which no survival process made, is no longer registered");
        Set<Item> items = new HashSet<>();
        Set<Fluid> fluids = new HashSet<>();
        for (Item item : BuiltInRegistries.ITEM) if (!ours(BuiltInRegistries.ITEM.getKey(item))) items.add(item);
        for (Fluid fluid : BuiltInRegistries.FLUID) if (!ours(BuiltInRegistries.FLUID.getKey(fluid))) fluids.add(fluid);
        items.remove(Items.DIAMOND);
        // The ore blocks themselves are survival roots because Silk Touch is an ordinary external resource.
        CEMetals.ORES.values().forEach(ore -> items.add(ore.get()));
        items.add(CEChlorAlkali.HALITE_ORE.get());
        items.add(CEChlorAlkali.DEEPSLATE_HALITE_ORE.get());
        items.add(CEUranium.URANIUM_ORE.get().asItem());items.add(CEUranium.DEEPSLATE_URANIUM_ORE.get().asItem());
        items.add(CENuclear.SERPENTINITE.get().asItem());

        var manager = helper.getLevel().getRecipeManager();
        var registries = helper.getLevel().registryAccess();
        boolean changed;
        do {
            changed = false;
            for (RecipeHolder<?> holder : manager.getRecipes()) changed |= addRecipe(holder, items, fluids, registries);
            changed |= addFixedProcesses(items, fluids);
            changed |= addFilledBuckets(items, fluids);
        } while (changed);

        helper.assertTrue(items.contains(Items.DIAMOND) && has(items, "cubic_press", "gas_centrifuge") && fluids.contains(CEUranium.ENRICHED_URANIUM_HEXAFLUORIDE.get()), "Artificial diamonds and enriched uranium are reachable from survival resources");
        helper.assertTrue(fluids.contains(CEProcessFluids.WATER_GAS.get()) && has(items, "water_gas_bucket"),
            "Carbon powder, steam and an external burner make the water gas bucket reachable");
        List<String> missing = BuiltInRegistries.ITEM.stream()
            .filter(item -> ours(BuiltInRegistries.ITEM.getKey(item)))
            .map(BuiltInRegistries.ITEM::getKey)
            .filter(id -> !REPORT_ONLY_ITEMS.contains(id.getPath()) && !NON_GOALS.contains(id.getPath()) && !CREATIVE_ONLY.contains(id.getPath())
                && !LEGACY_ONLY.contains(id.getPath()))
            .filter(id -> !items.contains(BuiltInRegistries.ITEM.get(id)))
            .map(ResourceLocation::toString).sorted().toList();
        var upstream = REPORT_ONLY_ITEMS.stream().filter(id -> !items.contains(item(id))).sorted().toList();
        try { java.nio.file.Files.writeString(java.nio.file.Path.of("survival-production-audit.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(java.util.Map.of("ordinary", missing, "nuclear", upstream, "creativeOnly", CREATIVE_ONLY, "legacyOnly", LEGACY_ONLY))); } catch (java.io.IOException error) { throw new RuntimeException(error); }
        helper.assertTrue(missing.isEmpty(), "Survival-unreachable ordinary items: " + missing + "; unavailable nuclear prerequisites: " + upstream);
        helper.succeed();
    }
}
