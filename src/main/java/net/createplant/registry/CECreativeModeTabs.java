package net.createplant.registry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.createplant.CreatePlant;
import net.createplant.item.GasBucketItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CECreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreatePlant.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BASE = TABS.register(
        "base",
        () -> CreativeModeTab.builder()
            .withTabFactory(SectionedCreativeTab::new)
            .hideTitle()
            .title(Component.translatable("itemGroup.create_refinery_n_reactors.base"))
            .icon(() -> CETransport.LIDDED_VESSEL.asItem().getDefaultInstance())
            .displayItems((parameters, output) -> display(output))
            .build()
    );

    /** Material groups follow the equipment, after the liquid and gas buckets. */
    private static final List<List<String>> MATERIAL_GROUPS = List.of(
        List.of("uranium_ore", "deepslate_uranium_ore", "bauxite_ore", "deepslate_bauxite_ore", "pentlandite_ore", "deepslate_pentlandite_ore",
            "ilmenite_ore", "deepslate_ilmenite_ore", "zircon_ore", "deepslate_zircon_ore", "chromite_ore", "deepslate_chromite_ore",
            "molybdenite_ore", "deepslate_molybdenite_ore", "halite_ore", "deepslate_halite_ore", "serpentinite"),
        List.of("uraninite", "bauxite", "pentlandite", "ilmenite", "zircon", "chromite", "molybdenite", "fluorite", "borax", "gypsum", "asbestos"),
        List.of("salt", "sulfur_dust", "alumina", "titanium_dioxide", "zirconia", "nickel_oxide", "molybdenum_trioxide", "chromium_oxide",
            "iron_oxide_red", "nickel_titanium_yellow", "molybdenum_blue", "triiron_tetroxide_pile", "ammonium_nitrate", "ammonium_sulfate",
            "quicklime", "sodium_bicarbonate", "sodium_carbonate", "urea", "potash", "phosphate_fertilizer", "npk_fertilizer",
            "pvc_resin", "cement_clinker", "ground_clinker", "cement_powder", "borosilicate_glass_frit", "water_treatment_flocculant",
            "treatment_sludge", "charred_wood"),
        List.of("carbon_powder", "unprocessed_steel", "pig_iron", "heated_unfinished_steel", "steel_slag", "steel_ingot", "steel_block", "ferrochrome", "aluminum_ingot", "nickel_ingot", "titanium_ingot", "zirconium_ingot", "chromium_ingot",
            "molybdenum_ingot", "stainless_steel_ingot", "aluminum_sheet", "nickel_sheet", "titanium_sheet", "stainless_steel_sheet",
            "iron_pipe", "unfired_ceramic_pipe", "zircaloy_cladding_tube"),
        List.of("polyethylene", "polypropylene", "synthetic_rubber", "industrial_resin", "plastic_sheet", "rubber_gasket",
            "flame_retardant_pellet", "heat_resistant_gasket", "flexible_rubber_hose", "paraffin_wax", "phenolic_resin", "phenolic_board",
            "phosphate_compound", "heat_resistant_coating", "fire_resistant_conductor", "fire_resistant_cable"),
        List.of("iron_catalyst", "oxidation_catalyst", "cracking_catalyst", "polymerization_catalyst", "hydrotreating_catalyst",
            "hydrocracking_catalyst", "shift_catalyst_core", "iron_ft_catalyst_core", "cobalt_ft_catalyst_core",
            "zsm5_catalyst_core", "cumene_catalyst_core", "platinum_zeolite_core", "water_filter_medium"),
        List.of("nickel_electrode_plate", "asbestos_wrapped_electrode", "high_pressure_turbine_blade", "low_pressure_turbine_blade", "tarbin_end",
            "low_pressure_turbine_end"),
        List.of("yellowcake", "uranium_trioxide", "uranium_dioxide", "uranium_tetrafluoride", "enriched_uranium_dioxide", "green_uranium_pellet",
            "uranium_pellet", "fuel_rod", "compact_uranium_dioxide", "green_compact_uranium_pellet", "compact_uranium_pellet",
            "compact_enriched_fuel_rod", "control_rod", "plutonium_dioxide", "depleted_uranium_dioxide", "mox_powder", "green_mox_pellet",
            "mox_pellet", "mox_fuel_rod", "lithium_target_rod", "irradiated_lithium_target_rod", "lithium_lead_blanket_cartridge",
            "irradiation_cartridge", "tungsten_divertor_cassette"),
        List.of("spent_fuel_rod", "spent_control_rod", "chopped_spent_fuel", "spent_cladding_hulls", "spent_blanket_cartridge",
            "irradiated_cartridge", "spent_divertor_cassette", "empty_waste_canister", "vitrified_waste_canister"),
        List.of("anfo", "blasting_charge"),
        List.of("fire_extinguisher", "flamethrower", "chemical_sprayer", "steam_gun", "steam_hammer", "pipe_configurator", "gas_mask", "gas_mask_filter", "inert_gas_cylinder", "protective_cap", "protective_coat", "protective_trousers", "protective_shoe_covers", "hazard_helmet", "hazard_chestplate", "hazard_leggings", "hazard_boots", "soap")
    );

    /** Equipment and construction groups in display order. */
    private static final List<List<String>> EQUIPMENT_GROUPS = List.of(
        List.of("industrial_concrete", "industrial_concrete_slab", "industrial_concrete_stairs", "corrosion_resistant_floor",
            "corrosion_resistant_floor_slab", "molded_polymer", "industrial_glass", "zirconia_refractory_bricks", "stainless_steel_block", "titanium_block",
            "diorite_casing", "tank_casing"),
        List.of("nickel_alloy_pipe", "zirconium_pipe", "stainless_steel_pipe", "ceramic_pipe"),
        List.of("nickel_alloy_pump", "zirconium_pump", "stainless_steel_pump"),
        List.of("pressure_reducing_valve", "relief_scrubber", "gas_detector", "gas_recovery_duct", "flow_regulator",
            "pipe_thermometer", "pipe_pressure_gauge", "pipe_flow_meter", "process_transmitter", "control_valve_actuator", "pid_controller",
            "pid_instrument_rack"),
        List.of("horizontal_fluid_tank", "stainless_boiler", "pressure_tank", "stainless_steel_tank", "cryogenic_tank", "gas_receiver", "gas_tank_container",
            "gas_holder", "lidded_vessel", "ammonia_separator"),
        List.of("high_pressure_turbine_unit", "low_pressure_turbine_unit", "large_flywheel", "high_pressure_turbine_casing", "low_pressure_turbine_casing", "reciprocating_cylinder",
            "reciprocating_compressor"),
        List.of("electric_pump", "high_pressure_pump", "hose_connector", "agricultural_sprayer",
            "tube_piped_tank", "jacketed_pipe",
            "cooler_radiator", "cooling_fan", "cooling_fan_shroud", "natural_draft_cooling_tower", "cooling_tower_shell", "cooling_tower_basin",
            "cooling_tower_fill", "cooling_tower_distributor", "cooling_tower_hot_inlet", "cooling_tower_cold_outlet",
            "cooling_tower_makeup_inlet", "condenser_waterbox", "condenser_shell", "condenser_tube_bundle", "condenser_cooling_inlet",
            "condenser_cooling_outlet", "cryogenic_cold_box_drive", "cryogenic_regenerator"),
        List.of("fractionating_column", "vessel_stirrer", "reactor_impeller", "gas_centrifuge", "cubic_press", "reactor_separator_core", "large_absorption_tower", "reaction_tower",
            "electrolysis_cell", "electrolysis_electrode_head", "electrolysis_cell_controller", "electrolysis_furnace_shell", "electrolysis_furnace_anode",
            "electrolysis_furnace_busbar", "converter_shell", "converter_trunnion_bearing", "fuel_cell", "flare_tip"),
        List.of("reactor_graphite", "reactor_fuel_channel", "reactor_fuel_channel_tube", "reactor_top_cap", "reactor_feed_bundle",
            "biological_shield", "biological_shield_drive", "biological_shield_coolant_outlet", "control_rod_link", "control_rod_controller",
            "reactor_control_console", "reactor_console_display", "fuel_pool"),
        List.of("pulse_extraction_column", "extraction_column_shell", "extraction_pulse_plate", "extraction_column_outlet", "vitrification_melter",
            "vitrification_refractory_shell", "vitrification_electrode"),
        List.of("fusion_reactor_controller", "fusion_reinforced_casing", "fusion_toroidal_field_coil", "fusion_central_solenoid",
            "fusion_vacuum_vessel", "fusion_breeding_blanket", "fusion_divertor", "fusion_viewport", "fusion_motor_generator",
            "fusion_vacuum_pump", "fusion_deuterium_port", "fusion_tritium_port", "fusion_ash_port", "fusion_helium_inlet",
            "fusion_helium_outlet", "fusion_coolant_inlet", "fusion_coolant_outlet", "fusion_control_console", "fusion_console_panel"),
        List.of("crane_drive", "fusion_crane_drive", "quarry_drive", "industrial_dispatch_board", "facility_constructor", "vault_hatch")
    );

    private static void display(CreativeModeTab.Output output) {
        display(output, false);
        display(output, true);
        EQUIPMENT_GROUPS.getFirst().stream().map(CECreativeModeTabs::item).forEach(output::accept);
        output.accept(CERadiation.DOSIMETER.get());
        output.accept(CERadiation.SURVEY_METER.get());
    }

    private static void display(CreativeModeTab.Output output, boolean materials) {
        var groups = (materials ? MATERIAL_GROUPS : EQUIPMENT_GROUPS.subList(1, EQUIPMENT_GROUPS.size())).stream()
            .map(paths -> paths.stream().map(CECreativeModeTabs::item).toList()).toList();
        var listed = new HashSet<Item>();
        for (var paths : MATERIAL_GROUPS) for (var path : paths) listed.add(item(path));
        for (var paths : EQUIPMENT_GROUPS) for (var path : paths) listed.add(item(path));
        listed.add(CEWorldFluids.EMPTY_ALLOY_BUCKET.get());
        listed.add(CESafety.RAW_PROCESS_GAS.get().getBucket());
        // Dyed flywheels are made by dyeing the placed yellow one.
        CEItems.LARGE_FLYWHEELS.forEach(item -> listed.add(item.get()));
        var liquids = new ArrayList<Item>();
        var gases = new ArrayList<Item>();
        var rest = new ArrayList<Item>();
        BuiltInRegistries.ITEM.stream()
            .filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(CreatePlant.MOD_ID) && !listed.contains(item))
            .forEach(item -> {
                if (item == CERadiation.DOSIMETER.get() || item == CERadiation.SURVEY_METER.get()) return;
                if (item instanceof GasBucketItem) gases.add(item);
                else if (item instanceof BucketItem bucket && bucket.content != Fluids.EMPTY) liquids.add(item);
                else if (!(item instanceof BlockItem) == materials) rest.add(item);
            });
        if (materials) {
            output.accept(CEWorldFluids.EMPTY_ALLOY_BUCKET.get());
            liquids.forEach(output::accept);
            output.accept(com.jesz.createdieselgenerators.CDGFluids.CRUDE_OIL.getBucket().get());
            gases.forEach(output::accept);
        }
        for (var group : groups) group.forEach(output::accept);
        rest.forEach(output::accept);
    }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.get(CreatePlant.location(path));
    }

    static String section(Item item) {
        if (item == CERadiation.DOSIMETER.get() || item == CERadiation.SURVEY_METER.get()) return "equipment";
        if (item == CEWorldFluids.EMPTY_ALLOY_BUCKET.get() || item instanceof GasBucketItem
            || item instanceof BucketItem bucket && bucket.content != Fluids.EMPTY) return "materials";
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (EQUIPMENT_GROUPS.getFirst().contains(path) || path.equals("steel_block")) return "construction";
        for (int i = 1; i < EQUIPMENT_GROUPS.size(); i++) if (EQUIPMENT_GROUPS.get(i).contains(path)) return "equipment";
        if (MATERIAL_GROUPS.getLast().contains(path)) return "equipment";
        for (var group : MATERIAL_GROUPS) if (group.contains(path)) return "materials";
        return item instanceof BlockItem ? "equipment" : "materials";
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
