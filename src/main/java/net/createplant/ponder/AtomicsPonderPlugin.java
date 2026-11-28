package net.createplant.ponder;

import net.createplant.CreatePlant;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class AtomicsPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return CreatePlant.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(CreatePlant.location("cubic_press")).addStoryBoard("plant/cubic_press", HighStressPonderScenes::press);
        helper.forComponents(CreatePlant.location("large_mechanical_press"), CreatePlant.location("large_depot"), CreatePlant.location("large_iron_plate"))
            .addStoryBoard("plant/large_press", LargePressPonderScenes::press);
        helper.forComponents(CreatePlant.location("rotary_kiln"), CreatePlant.location("rotary_kiln_exhaust"), CreatePlant.location("continuous_factory_frame"), CreatePlant.location("continuous_factory_outlet"))
            .addStoryBoard("plant/rotary_kiln", ContinuousFactoryPonderScenes::kiln);
        helper.forComponents(CreatePlant.location("rolling_mill"), CreatePlant.location("rolling_stand"), CreatePlant.location("thick_steel_plate"),
            CreatePlant.location("cast_stainless_steel_slab"))
            .addStoryBoard("plant/rolling_mill", ContinuousFactoryPonderScenes::mill);
        helper.forComponents(CreatePlant.location("torpedo_tank"), CreatePlant.location("silica_refractory_bricks"),
            CreatePlant.location("cast_steel_slab"), CreatePlant.location("molten_pig_iron_bucket"))
            .addStoryBoard("plant/torpedo_car", SteelworksPonderScenes::torpedo);
        helper.forComponents(CreatePlant.location("stainless_steel_ingot"), CreatePlant.location("molten_stainless_steel_bucket"),
            CreatePlant.location("ferrochrome"), CreatePlant.location("cast_stainless_steel_slab"),
            CreatePlant.location("stainless_steel_sheet"), CreatePlant.location("stainless_steel_block"))
            .addStoryBoard("plant/stainless_casting", SteelworksPonderScenes::stainless);
        for (var product : ContinuousFactoryPonderScenes.ROLLED) helper.forComponents(CreatePlant.location(product.id()))
            .addStoryBoard("plant/rolled_" + product.id(), (scene, util) -> ContinuousFactoryPonderScenes.rolled(scene, util, product));
        // Every stainless item leads to the rolling step that finally makes the ingots.
        helper.forComponents(CreatePlant.location("cast_stainless_steel_slab"), CreatePlant.location("molten_stainless_steel_bucket"),
                CreatePlant.location("stainless_steel_sheet"), CreatePlant.location("stainless_steel_block"))
            .addStoryBoard("plant/rolled_stainless_steel_ingot", (scene, util) -> ContinuousFactoryPonderScenes.rolled(scene, util,
                ContinuousFactoryPonderScenes.ROLLED.getLast()));
        helper.forComponents(CreatePlant.location("coil_winder"), CreatePlant.location("winder_brake"), CreatePlant.location("heavy_wire_rope"))
            .addStoryBoard("plant/coil_winder", ContinuousFactoryPonderScenes::winder);
        helper.forComponents(CreatePlant.location("facility_constructor"))
            .addStoryBoard("plant/facility_constructor", AssemblyPonderScenes::constructor);
        helper.forComponents(CreatePlant.location("pulse_extraction_column"), CreatePlant.location("extraction_column_shell"), CreatePlant.location("extraction_pulse_plate"),
            CreatePlant.location("extraction_column_outlet"), CreatePlant.location("vitrification_melter"), CreatePlant.location("vitrification_refractory_shell"),
            CreatePlant.location("vitrification_electrode"), CreatePlant.location("empty_waste_canister"), CreatePlant.location("vitrified_waste_canister"),
            CreatePlant.location("mox_fuel_rod"), CreatePlant.location("borosilicate_glass_frit"), CreatePlant.location("chopped_spent_fuel"),
            CreatePlant.location("spent_cladding_hulls"), CreatePlant.location("plutonium_dioxide"), CreatePlant.location("depleted_uranium_dioxide"),
            CreatePlant.location("mox_powder"), CreatePlant.location("green_mox_pellet"), CreatePlant.location("mox_pellet"))
            .addStoryBoard("plant/fuel_cycle", AtomicsSystemsPonderScenes::fuelCycle);
        helper.forComponents(CreatePlant.location("fusion_crane_drive"), CreatePlant.location("lithium_lead_blanket_cartridge"), CreatePlant.location("tungsten_divertor_cassette"),
            CreatePlant.location("spent_blanket_cartridge"), CreatePlant.location("spent_divertor_cassette"),
            CreatePlant.location("irradiation_cartridge"), CreatePlant.location("irradiated_cartridge"))
            .addStoryBoard("plant/fusion_maintenance", AtomicsSystemsPonderScenes::maintenance);
        helper.forComponents(CreatePlant.location("gas_centrifuge"),CreatePlant.location("lidded_vessel"),
                CreatePlant.location("gas_receiver"))
            .addStoryBoard("plant/gas_centrifuge",HighStressPonderScenes::centrifuge);
        helper.forComponents(
                CreatePlant.location("reactor_graphite"),
                CreatePlant.location("reactor_fuel_channel"),
                CreatePlant.location("reactor_top_cap"),
                CreatePlant.location("biological_shield"),
                CreatePlant.location("reactor_feed_bundle"),
                CreatePlant.location("biological_shield_coolant_outlet"),
                CreatePlant.location("serpentinite"))
            .addStoryBoard("nuclear/rbmk_core", RbmkPonderScenes::core);
        helper.forComponents(CreatePlant.location("reactor_fuel_channel"), CreatePlant.location("reactor_top_cap"),
                CreatePlant.location("reactor_feed_bundle"), CreatePlant.location("biological_shield"),
                CreatePlant.location("biological_shield_drive"), CreatePlant.location("reactor_fuel_channel_tube"),
                CreatePlant.location("compact_enriched_fuel_rod"),
                CreatePlant.location("compact_uranium_dioxide"), CreatePlant.location("green_compact_uranium_pellet"), CreatePlant.location("compact_uranium_pellet"))
            .addStoryBoard("nuclear/rbmk_compact_core", RbmkPonderScenes::compactCore);
        helper.forComponents(
                CreatePlant.location("fusion_reinforced_casing"),
                CreatePlant.location("fusion_toroidal_field_coil"),
                CreatePlant.location("fusion_central_solenoid"),
                CreatePlant.location("fusion_vacuum_vessel"),
                CreatePlant.location("fusion_breeding_blanket"),
                CreatePlant.location("fusion_divertor"),
                CreatePlant.location("fusion_reactor_controller"),
                CreatePlant.location("fusion_vacuum_pump"),
                CreatePlant.location("fusion_deuterium_port"),
                CreatePlant.location("fusion_tritium_port"),
                CreatePlant.location("fusion_ash_port"),
                CreatePlant.location("fusion_helium_inlet"),
                CreatePlant.location("fusion_helium_outlet"),
                CreatePlant.location("fusion_coolant_inlet"),
                CreatePlant.location("fusion_coolant_outlet"),
                CreatePlant.location("fusion_viewport"))
            .addStoryBoard("nuclear/fusion_tokamak", FusionPonderScenes::tokamak);
        helper.forComponents(
                CreatePlant.location("fusion_control_console"),
                CreatePlant.location("fusion_console_panel"),
                com.simibubi.create.AllBlocks.DISPLAY_LINK.getId())
            .addStoryBoard("nuclear/fusion_console", FusionPonderScenes::console);
        helper.forComponents(CreatePlant.location("cryogenic_cold_box_drive"), CreatePlant.location("cryogenic_regenerator"))
            .addStoryBoard("plant/helium_cold_box", FusionPonderScenes::coldBox);
        helper.forComponents(CreatePlant.location("fusion_motor_generator"))
            .addStoryBoard("nuclear/fusion_motor_generator", FusionPonderScenes::motorGenerator);
        helper.forComponents(
                CreatePlant.location("reactor_top_cap"),
                CreatePlant.location("control_rod"),
                CreatePlant.location("control_rod_controller"),
                CreatePlant.location("control_rod_link"))
            .addStoryBoard("nuclear/rbmk_control_rods", RbmkPonderScenes::controlRods);
        helper.forComponents(
                CreatePlant.location("reactor_control_console"),
                com.simibubi.create.AllBlocks.DISPLAY_LINK.getId(),
                CreatePlant.location("control_rod_controller"))
            .addStoryBoard("nuclear/rbmk_control_room", RbmkPonderScenes::controlRoom);
        helper.forComponents(
                CreatePlant.location("crane_drive"),
                com.simibubi.create.AllBlocks.METAL_GIRDER.getId())
            .addStoryBoard("crane/gantry_crane", CranePonderScenes::crane);
        helper.forComponents(CreatePlant.location("crane_drive"), CreatePlant.location("fuel_pool"), CreatePlant.location("reactor_top_cap"))
            .addStoryBoard("crane/crane_refuelling", CranePonderScenes::refuel);
        helper.forComponents(CreatePlant.location("personal_dosimeter"), CreatePlant.location("survey_meter"), CreatePlant.location("fuel_pool"))
            .addStoryBoard("nuclear/radiation_measurement", RadiationPonderScenes::measurement);
        helper.forComponents(CreatePlant.location("personal_dosimeter"), CreatePlant.location("survey_meter"), CreatePlant.location("soap"), CreatePlant.location("gas_mask"))
            .addStoryBoard("nuclear/radiation_decontamination", RadiationPonderScenes::decontamination);
        helper.forComponents(CreatePlant.location("fuel_pool"))
            .addStoryBoard("nuclear/fuel_pool", FuelPoolPonderScenes::pool);
    }
}
