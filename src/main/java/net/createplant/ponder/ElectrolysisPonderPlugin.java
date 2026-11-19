package net.createplant.ponder;

import net.createplant.CreatePlant;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class ElectrolysisPonderPlugin implements PonderPlugin {
    @Override
    public void registerSharedText(net.createmod.ponder.api.registration.SharedTextRegistrationHelper helper) {
        PonderText.register(helper);
    }

    @Override
    public String getModId() {
        return CreatePlant.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        var preparation = new java.util.ArrayList<ResourceLocation>();
        for (String name : java.util.List.of("cassiterite", "cassiterite_ore", "deepslate_cassiterite_ore", "crushed_raw_tin", "tin_ingot", "tailings_mud", "dilute_sulfuric_acid_bucket")) preparation.add(CreatePlant.location(name));
        helper.forComponents(preparation.toArray(ResourceLocation[]::new)).addStoryBoard("plant/ore_recovery", MetalRecoveryPonderScenes::preparation);
        helper.forComponents(CreatePlant.location("electrolysis_cell"), CreatePlant.location("electrolysis_cell_controller"), CreatePlant.location("electrolysis_electrode_head"),
            CreatePlant.location("iron_sulfate_solution_bucket"), CreatePlant.location("copper_sulfate_solution_bucket"), CreatePlant.location("zinc_sulfate_solution_bucket"), CreatePlant.location("tin_sulfate_solution_bucket"))
            .addStoryBoard("plant/electrowinning", MetalRecoveryPonderScenes::electrowinning);
        for (var pump : net.createplant.registry.CETransport.MATERIAL_PUMPS) {
            String id = pump.getId().getPath();
            String material = id.substring(0, id.length() - "_pump".length());
            helper.forComponents(pump.getId()).addStoryBoard("plant/material_pump",
                (scene, util) -> MaterialPumpPonderScenes.pump(scene, util, material));
        }
        helper.forComponents(CreatePlant.location("fire_extinguisher"),CreatePlant.location("flamethrower"),CreatePlant.location("chemical_sprayer"))
            .addStoryBoard("plant/portable_sprayers",PortableSprayerPonderScenes::tools);
        helper.forComponents(CreatePlant.location("steam_gun")).addStoryBoard("plant/steam_gun", SteamGunPonderScenes::controls);
        helper.forComponents(CreatePlant.location("steam_hammer")).addStoryBoard("plant/steam_hammer", SteamHammerPonderScenes::controls);
        helper.forComponents(CreatePlant.location("vault_hatch"), com.simibubi.create.AllBlocks.ITEM_VAULT.getId())
            .addStoryBoard("plant/vault_hatch", VaultHatchPonderScenes::hatch);
        helper.forComponents(CreatePlant.location("transmission_tower"), CreatePlant.location("high_voltage_cable"))
            .addStoryBoard("plant/transmission_tower", TransmissionTowerPonderScenes::connection);
        helper.forComponents(CreatePlant.location("electric_pump")).addStoryBoard("plant/electric_pump", ElectricUtilityPonderScenes::pump);
        helper.forComponents(CreatePlant.location("cubic_press")).addStoryBoard("plant/cubic_press", HighStressPonderScenes::press);
        for (var tutorial : PlantTutorialCatalog.all()) if (tutorial.family() != null) {
            helper.forComponents(tutorial.components().stream().map(name -> name.contains(":") ? ResourceLocation.parse(name) : CreatePlant.location(name)).toArray(ResourceLocation[]::new))
                .addStoryBoard(tutorial.storyboard(), (scene, util) -> PlantPonderScenes.show(scene, util, tutorial));
        }
        helper.forComponents(CreatePlant.location("pig_iron"), CreatePlant.location("heated_unfinished_steel"), CreatePlant.location("steel_ingot"), CreatePlant.location("steel_slag"))
            .addStoryBoard("plant/steel_forging", ForgingPonderScenes::steel);
        helper.forComponents(CreatePlant.location("fluorite"))
            .addStoryBoard("plant/fluorite_sources", ForgingPonderScenes::fluorite);
        helper.forComponents(CreatePlant.location("gas_receiver")).addStoryBoard("plant/pulsation_tube",PulsationTubePonderScenes::tube);
        helper.forComponents(CreatePlant.location("gas_holder"))
            .addStoryBoard("plant/gas_bell",GasBellPonderScenes::bell);
        var liquidContainers = new java.util.ArrayList<ResourceLocation>();
        liquidContainers.add(CreatePlant.location("corrosion_resistant_bucket"));
        for (var liquid : net.createplant.registry.CEWorldFluids.entries())
            liquidContainers.add(CreatePlant.location(liquid.name() + "_bucket"));
        helper.forComponents(liquidContainers.toArray(ResourceLocation[]::new))
            .addStoryBoard("plant/world_liquids", WorldLiquidsPonderScenes::liquids);
        helper.forComponents(CreatePlant.location("facility_constructor"))
            .addStoryBoard("plant/facility_constructor", AssemblyPonderScenes::constructor);
        helper.forComponents(CreatePlant.location("natural_draft_cooling_tower"), CreatePlant.location("cooling_tower_shell"),
            CreatePlant.location("cooling_tower_basin"), CreatePlant.location("cooling_tower_fill"), CreatePlant.location("cooling_tower_distributor"),
            CreatePlant.location("cooling_tower_hot_inlet"), CreatePlant.location("cooling_tower_cold_outlet"), CreatePlant.location("cooling_tower_makeup_inlet"))
            .addStoryBoard("plant/natural_draft_tower", PlantSystemsPonderScenes::tower);
        helper.forComponents(CreatePlant.location("condenser_waterbox"), CreatePlant.location("condenser_shell"), CreatePlant.location("condenser_tube_bundle"),
            CreatePlant.location("condenser_cooling_inlet"), CreatePlant.location("condenser_cooling_outlet"))
            .addStoryBoard("plant/condenser_loop", PlantSystemsPonderScenes::condenser);
        helper.forComponents(CreatePlant.location("pid_controller"), CreatePlant.location("pid_instrument_rack"), CreatePlant.location("process_transmitter"),
            CreatePlant.location("control_valve_actuator"), CreatePlant.location("flow_regulator"))
            .addStoryBoard("plant/process_pid", PlantSystemsPonderScenes::pid);
        helper.forComponents(CreatePlant.location("pulse_extraction_column"), CreatePlant.location("extraction_column_shell"), CreatePlant.location("extraction_pulse_plate"),
            CreatePlant.location("extraction_column_outlet"), CreatePlant.location("vitrification_melter"), CreatePlant.location("vitrification_refractory_shell"),
            CreatePlant.location("vitrification_electrode"), CreatePlant.location("empty_waste_canister"), CreatePlant.location("vitrified_waste_canister"),
            CreatePlant.location("mox_fuel_rod"), CreatePlant.location("borosilicate_glass_frit"), CreatePlant.location("chopped_spent_fuel"),
            CreatePlant.location("spent_cladding_hulls"), CreatePlant.location("plutonium_dioxide"), CreatePlant.location("depleted_uranium_dioxide"),
            CreatePlant.location("mox_powder"), CreatePlant.location("green_mox_pellet"), CreatePlant.location("mox_pellet"))
            .addStoryBoard("plant/fuel_cycle", PlantSystemsPonderScenes::fuelCycle);
        helper.forComponents(CreatePlant.location("fusion_crane_drive"), CreatePlant.location("lithium_lead_blanket_cartridge"), CreatePlant.location("tungsten_divertor_cassette"),
            CreatePlant.location("spent_blanket_cartridge"), CreatePlant.location("spent_divertor_cassette"),
            CreatePlant.location("irradiation_cartridge"), CreatePlant.location("irradiated_cartridge"))
            .addStoryBoard("plant/fusion_maintenance", PlantSystemsPonderScenes::maintenance);
        helper.forComponents(CreatePlant.location("gas_centrifuge"),CreatePlant.location("lidded_vessel"),
                CreatePlant.location("gas_receiver"))
            .addStoryBoard("plant/gas_centrifuge",HighStressPonderScenes::centrifuge);
        for (var tutorial : PlantTutorialCatalog.all()) if (tutorial.family() == null) {
            helper.forComponents(tutorial.components().stream().map(name -> name.contains(":") ? ResourceLocation.parse(name) : CreatePlant.location(name)).toArray(ResourceLocation[]::new))
                .addStoryBoard(tutorial.storyboard(), (scene, util) -> PlantPonderScenes.show(scene, util, tutorial));
        }
        var wetComponents = new java.util.ArrayList<ResourceLocation>();
        helper.forComponents(CreatePlant.location("steam_gasifier")).addStoryBoard("plant/entrained_gasifier", GasifierPonderScenes::processing);
        net.createplant.registry.CEWetProcessing.PARTS.values().forEach(block -> wetComponents.add(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block.get())));
        wetComponents.add(CreatePlant.location("lidded_vessel"));wetComponents.add(CreatePlant.location("reactor_impeller"));wetComponents.add(CreatePlant.location("clarifier_center_drive"));
        helper.forComponents(wetComponents)
            .addStoryBoard("plant/wet_processing", WetProcessPonderScenes::processing)
            .addStoryBoard("plant/electrowinning", MetalRecoveryPonderScenes::electrowinning);
        helper.forComponents(CreatePlant.location("diorite_casing"))
            .addStoryBoard("plant/diorite_casing", DioriteCasingPonderScenes::building);

        helper.forComponents(CreatePlant.location("large_absorption_tower"))
            .addStoryBoard("plant/large_absorption_tower", AbsorptionTowerPonderScenes::large);
        helper.forComponents(CreatePlant.location("fractionating_column"), CreatePlant.location("gas_pipe"), CreatePlant.location("naphtha_bucket"),
                CreatePlant.location("low_sulfur_diesel_bucket"))
            .addStoryBoard("plant/fractionating_column", FractionationPonderScenes::column);
        helper.forComponents(CreatePlant.location("fractionating_column"))
            .addStoryBoard("plant/fractionating_column_binary", FractionationPonderScenes::binary);
        helper.forComponents(CreatePlant.location("fractionating_column"),CreatePlant.location("gas_pipe"))
            .addStoryBoard("plant/fractionating_column_air", FractionationPonderScenes::air);
        helper.forComponents(
                CreatePlant.location("electrolysis_cell"),
                CreatePlant.location("electrolysis_cell_controller"),
                CreatePlant.location("electrolysis_electrode_head"),
                CreatePlant.location("asbestos_wrapped_electrode"),
                CreatePlant.location("nickel_electrode_plate"))
            .addStoryBoard("plant/electrolysis_cell", ElectrolysisCellPonderScenes::cells);
        helper.forComponents(
                CreatePlant.location("reciprocating_cylinder"),
                CreatePlant.location("reciprocating_compressor"))
            .addStoryBoard("chemistry/reciprocating_compressor", ReciprocatingPonderScenes::compressor);
        helper.forComponents(CreatePlant.location("reaction_tower"), CreatePlant.location("reactor_catalyst_basket"), CreatePlant.location("iron_catalyst"),
                CreatePlant.location("oxidation_catalyst"), CreatePlant.location("cracking_catalyst"), CreatePlant.location("polymerization_catalyst"),
                CreatePlant.location("hydrotreating_catalyst"), CreatePlant.location("hydrocracking_catalyst"))
            .addStoryBoard("plant/reaction_tower", ReactionTowerPonderScenes::tower)
            .addStoryBoard("plant/reaction_tower_separator", ReactionTowerPonderScenes::separator);
        helper.forComponents(CreatePlant.location("ammonia_separator"))
            .addStoryBoard("chemistry/ammonia_separator", AmmoniaSeparatorPonderScenes::separator);
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
        helper.forComponents(CreatePlant.location("quarry_drive"))
            .addStoryBoard("crane/quarry_frame", CranePonderScenes::quarryFrame);
        helper.forComponents(CreatePlant.location("personal_dosimeter"), CreatePlant.location("survey_meter"), CreatePlant.location("fuel_pool"))
            .addStoryBoard("nuclear/radiation_measurement", RadiationPonderScenes::measurement);
        helper.forComponents(CreatePlant.location("personal_dosimeter"), CreatePlant.location("survey_meter"), CreatePlant.location("soap"), CreatePlant.location("gas_mask"))
            .addStoryBoard("nuclear/radiation_decontamination", RadiationPonderScenes::decontamination);
        helper.forComponents(CreatePlant.location("fuel_pool"))
            .addStoryBoard("nuclear/fuel_pool", FuelPoolPonderScenes::pool);
        helper.forComponents(CreatePlant.location("pipe_thermometer"), CreatePlant.location("pipe_pressure_gauge"), CreatePlant.location("pipe_flow_meter"))
            .addStoryBoard("plant/pipe_gauges", PipeGaugePonderScenes::gauges);
        helper.forComponents(CreatePlant.location("flare_tip"),CreatePlant.location("pipe_pressure_gauge"),CreatePlant.location("gas_pipe"))
            .addStoryBoard("plant/flare_stack",FlarePonderScenes::flare);
        helper.forComponents(CreatePlant.location("horizontal_fluid_tank"))
            .addStoryBoard("plant/horizontal_tank", HorizontalTankPonderScenes::tank);
        helper.forComponents(CreatePlant.location("lidded_vessel"))
            .addStoryBoard("plant/lidded_vessel", LiddedVesselPonderScenes::vessel);
        helper.forComponents(CreatePlant.location("lidded_vessel"))
            .addStoryBoard("plant/lidded_vessel_2x2", LiddedVesselPonderScenes::compactVessel);
        helper.forComponents(CreatePlant.location("reactor_impeller"), CreatePlant.location("gas_centrifuge"), CreatePlant.location("lidded_vessel"))
            .addStoryBoard("plant/vessel_internals", LiddedVesselPonderScenes::internals);
        helper.forComponents(CreatePlant.location("lidded_vessel"),CreatePlant.location("reactor_impeller"))
            .addStoryBoard("plant/vessel_basin",VesselBasinPonderScenes::basin);
        helper.forComponents(CreatePlant.location("lidded_vessel"),CreatePlant.location("reactor_impeller"),CreatePlant.location("pipe_pressure_gauge"))
            .addStoryBoard("plant/vessel_maintenance",VesselBasinPonderScenes::maintenance);

        helper.forComponents(
                CreatePlant.location("high_pressure_turbine_unit"),
                CreatePlant.location("low_pressure_turbine_unit"),
                CreatePlant.location("low_pressure_turbine_blade"),
                CreatePlant.location("high_pressure_turbine_blade"),
                CreatePlant.location("low_pressure_turbine_casing"),
                CreatePlant.location("high_pressure_turbine_casing"),
                CreatePlant.location("low_pressure_turbine_end"),
                CreatePlant.location("tarbin_end"),
                CreatePlant.location("steam_bucket"),
                CreatePlant.location("stainless_steel_pipe"))
            .addStoryBoard("turbine/multiblock", ElectrolysisPonderScenes::turbineMultiblock);


        helper.forComponents(CreatePlant.location("natural_draft_cooling_tower"), CreatePlant.location("cooler_radiator"), CreatePlant.location("lidded_vessel"))
            .addStoryBoard("plant/natural_draft_tower", SeasonalThermalPonderScenes::seasons);

        helper.forComponents(CreatePlant.location("lidded_vessel"), com.simibubi.create.AllBlocks.FLUID_TANK.getId(), CreatePlant.location("antifreeze_bucket"))
            .addStoryBoard("plant/chemical_mixtures", MixturePonderScenes::mixing);

    }
}
