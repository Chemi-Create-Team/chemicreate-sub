package net.createplant.atomics.registry;

import net.createplant.registry.CECreativeModeTabs;

/** Puts the atomics addon's items into the base creative tab, where they stood when the tab listed them itself. */
public final class AtomicsCreativeTabs {
    private AtomicsCreativeTabs() { }

    public static void register() {
        CECreativeModeTabs.insertBefore("bauxite_ore", "uranium_ore", "deepslate_uranium_ore");
        CECreativeModeTabs.insertAfter("deepslate_halite_ore", "serpentinite");
        CECreativeModeTabs.insertBefore("bauxite", "uraninite");
        CECreativeModeTabs.insertAfter("cement_powder", "borosilicate_glass_frit");
        CECreativeModeTabs.insertAfter("stainless_steel_sheet",
            "large_iron_plate", "cast_steel_slab", "cast_stainless_steel_slab", "thick_steel_plate", "steel_h_beam", "steel_rail_stock",
            "copper_foil", "aluminum_foil", "electrical_steel_sheet", "stainless_clad_plate", "lead_sheet", "gold_foil", "steel_core_aluminum_cable",
            "heavy_wire_rope", "deformed_workpiece", "loose_winding");
        CECreativeModeTabs.addGroupAfter("low_pressure_turbine_end",
            "yellowcake", "uranium_trioxide", "uranium_dioxide", "uranium_tetrafluoride", "enriched_uranium_dioxide", "green_uranium_pellet",
            "uranium_pellet", "fuel_rod", "compact_uranium_dioxide", "green_compact_uranium_pellet", "compact_uranium_pellet",
            "compact_enriched_fuel_rod", "control_rod", "plutonium_dioxide", "depleted_uranium_dioxide", "mox_powder", "green_mox_pellet",
            "mox_pellet", "mox_fuel_rod", "lithium_target_rod", "irradiated_lithium_target_rod", "lithium_lead_blanket_cartridge",
            "irradiation_cartridge", "tungsten_divertor_cassette");
        CECreativeModeTabs.addGroupAfter("low_pressure_turbine_end",
            "spent_fuel_rod", "spent_control_rod", "chopped_spent_fuel", "spent_cladding_hulls", "spent_blanket_cartridge", "irradiated_cartridge",
            "spent_divertor_cassette", "empty_waste_canister", "vitrified_waste_canister");
        CECreativeModeTabs.insertAfter("industrial_glass", "silica_refractory_bricks", "zirconia_refractory_bricks");
        CECreativeModeTabs.insertAfter("horizontal_fluid_tank", "torpedo_tank");
        CECreativeModeTabs.insertAfter("reactor_impeller", "gas_centrifuge", "cubic_press", "large_mechanical_press", "large_depot");
        CECreativeModeTabs.insertAfter("electrolysis_furnace_busbar", "converter_shell", "converter_trunnion_bearing");
        CECreativeModeTabs.insertBefore("facility_constructor", "crane_drive", "fusion_crane_drive");
        CECreativeModeTabs.addGroupAfter("flare_tip",
            "reactor_graphite", "reactor_fuel_channel", "reactor_fuel_channel_tube", "reactor_top_cap", "reactor_feed_bundle", "biological_shield",
            "biological_shield_drive", "biological_shield_coolant_outlet", "control_rod_link", "control_rod_controller", "reactor_control_console",
            "reactor_console_display", "fuel_pool");
        CECreativeModeTabs.addGroupAfter("flare_tip",
            "pulse_extraction_column", "extraction_column_shell", "extraction_pulse_plate", "extraction_column_outlet", "vitrification_melter",
            "vitrification_refractory_shell", "vitrification_electrode");
        CECreativeModeTabs.addGroupAfter("flare_tip",
            "fusion_reactor_controller", "fusion_reinforced_casing", "fusion_toroidal_field_coil", "fusion_central_solenoid", "fusion_vacuum_vessel",
            "fusion_breeding_blanket", "fusion_divertor", "fusion_viewport", "fusion_motor_generator", "fusion_vacuum_pump", "fusion_deuterium_port",
            "fusion_tritium_port", "fusion_ash_port", "fusion_helium_inlet", "fusion_helium_outlet", "fusion_coolant_inlet", "fusion_coolant_outlet",
            "fusion_control_console", "fusion_console_panel");
        CECreativeModeTabs.addGroupAfter("vault_hatch",
            "rotary_kiln", "rotary_kiln_exhaust", "rolling_mill", "rolling_stand", "coil_winder", "winder_brake", "continuous_factory_frame",
            "continuous_factory_outlet");
    }
}
