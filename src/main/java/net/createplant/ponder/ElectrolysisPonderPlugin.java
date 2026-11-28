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
        for (String name : java.util.List.of("cassiterite", "cassiterite_ore", "deepslate_cassiterite_ore", "crushed_raw_tin", "tin_ingot", "galena", "lead_ingot", "tailings_mud", "dilute_sulfuric_acid_bucket")) preparation.add(CreatePlant.location(name));
        helper.forComponents(preparation.toArray(ResourceLocation[]::new)).addStoryBoard("plant/ore_recovery", MetalRecoveryPonderScenes::preparation);
        helper.forComponents(CreatePlant.location("electrolysis_cell"), CreatePlant.location("electrolysis_cell_controller"), CreatePlant.location("electrolysis_electrode_head"),
            CreatePlant.location("iron_sulfate_solution_bucket"), CreatePlant.location("copper_sulfate_solution_bucket"), CreatePlant.location("zinc_sulfate_solution_bucket"), CreatePlant.location("tin_sulfate_solution_bucket"), CreatePlant.location("lead_chloride_solution_bucket"))
            .addStoryBoard("plant/electrowinning", MetalRecoveryPonderScenes::electrowinning);
        for (var pump : net.createplant.registry.CETransport.MATERIAL_PUMPS) {
            String id = pump.getId().getPath();
            String material = id.substring(0, id.length() - "_pump".length());
            helper.forComponents(pump.getId()).addStoryBoard("plant/material_pump",
                (scene, util) -> MaterialPumpPonderScenes.pump(scene, util, material));
        }
        helper.forComponents(CreatePlant.location("high_pressure_pump"), CreatePlant.location("hose_connector"),
                CreatePlant.location("flexible_rubber_hose"), CreatePlant.location("hazard_helmet"), CreatePlant.location("hazard_boots"),
                CreatePlant.location("hazard_chestplate"), CreatePlant.location("hazard_leggings"))
            .addStoryBoard("plant/pressure_infrastructure", PressureInfrastructurePonderScenes::pump);
        helper.forComponents(CreatePlant.location("fire_extinguisher"),CreatePlant.location("flamethrower"),CreatePlant.location("chemical_sprayer"))
            .addStoryBoard("plant/portable_sprayers",PortableSprayerPonderScenes::tools);
        helper.forComponents(CreatePlant.location("chemical_sprayer"), CreatePlant.location("liquid_fertilizer_bucket"))
            .addStoryBoard("plant/liquid_fertilizer", CropSprayerPonderScenes::fertilize);
        helper.forComponents(CreatePlant.location("agricultural_sprayer"), CreatePlant.location("diluted_pesticide_bucket"))
            .addStoryBoard("plant/agricultural_sprayer_bearing", AgriculturalSprayerPonderScenes::bearing)
            .addStoryBoard("plant/agricultural_sprayer_gantry", AgriculturalSprayerPonderScenes::gantry)
            .addStoryBoard("plant/agricultural_sprayer_stationary", AgriculturalSprayerPonderScenes::stationary);
        helper.forComponents(CreatePlant.location("steam_gun")).addStoryBoard("plant/steam_gun", SteamGunPonderScenes::controls);
        helper.forComponents(CreatePlant.location("steam_hammer")).addStoryBoard("plant/steam_hammer", SteamHammerPonderScenes::controls);
        helper.forComponents(CreatePlant.location("ammonium_nitrate"), CreatePlant.location("ammonium_sulfate"),
                CreatePlant.location("npk_fertilizer"), CreatePlant.location("phosphate_fertilizer"), CreatePlant.location("urea"))
            .addStoryBoard("plant/fertilizer_cycle", FertilizerPonderScenes::cycle);
        helper.forComponents(CreatePlant.location("quicklime"), CreatePlant.location("sodium_bicarbonate"),
                CreatePlant.location("sodium_carbonate"), CreatePlant.location("methanol_bucket"),
                CreatePlant.location("ammonium_chloride_liquor_bucket"))
            .addStoryBoard("plant/fertilizer_cycle", FertilizerPonderScenes::industry);
        helper.forComponents(CreatePlant.location("pipe_configurator"), com.simibubi.create.AllBlocks.FLUID_PIPE.getId())
            .addStoryBoard("plant/pipe_configurator", PipeConfiguratorPonderScenes::tool);
        helper.forComponents(CreatePlant.location("vault_hatch"), com.simibubi.create.AllBlocks.ITEM_VAULT.getId())
            .addStoryBoard("plant/vault_hatch", VaultHatchPonderScenes::hatch);
        helper.forComponents(CreatePlant.location("transmission_tower"), CreatePlant.location("high_voltage_cable"))
            .addStoryBoard("plant/transmission_tower", TransmissionTowerPonderScenes::connection);
        helper.forComponents(CreatePlant.location("electric_pump")).addStoryBoard("plant/electric_pump", ElectricUtilityPonderScenes::pump);
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
        liquidContainers.add(CreatePlant.location("stainless_steel_bucket"));
        for (var liquid : net.createplant.registry.CEWorldFluids.entries())
            liquidContainers.add(CreatePlant.location(liquid.name() + "_bucket"));
        helper.forComponents(liquidContainers.toArray(ResourceLocation[]::new))
            .addStoryBoard("plant/world_liquids", WorldLiquidsPonderScenes::liquids);
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
        for (var tutorial : PlantTutorialCatalog.all()) if (tutorial.family() == null) {
            helper.forComponents(tutorial.components().stream().map(name -> name.contains(":") ? ResourceLocation.parse(name) : CreatePlant.location(name)).toArray(ResourceLocation[]::new))
                .addStoryBoard(tutorial.storyboard(), (scene, util) -> PlantPonderScenes.show(scene, util, tutorial));
        }
        var wetComponents = new java.util.ArrayList<ResourceLocation>();
        helper.forComponents(CreatePlant.location("steam_gasifier")).addStoryBoard("plant/entrained_gasifier", GasifierPonderScenes::processing);
        net.createplant.registry.CEWetProcessing.PARTS.values().forEach(block -> wetComponents.add(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block.get())));
        wetComponents.add(CreatePlant.location("stirred_tank"));wetComponents.add(CreatePlant.location("reactor_impeller"));wetComponents.add(CreatePlant.location("clarifier_center_drive"));
        helper.forComponents(wetComponents)
            .addStoryBoard("plant/wet_processing", WetProcessPonderScenes::processing)
            .addStoryBoard("plant/electrowinning", MetalRecoveryPonderScenes::electrowinning);
        helper.forComponents(CreatePlant.location("diorite_casing"))
            .addStoryBoard("plant/diorite_casing", DioriteCasingPonderScenes::building);

        helper.forComponents(CreatePlant.location("large_absorption_tower"))
            .addStoryBoard("plant/large_absorption_tower", AbsorptionTowerPonderScenes::large);
        helper.forComponents(CreatePlant.location("fractionating_column"), CreatePlant.location("naphtha_bucket"),
                CreatePlant.location("low_sulfur_diesel_bucket"))
            .addStoryBoard("plant/fractionating_column", FractionationPonderScenes::column);
        helper.forComponents(CreatePlant.location("fractionating_column"))
            .addStoryBoard("plant/fractionating_column_binary", FractionationPonderScenes::binary);
        helper.forComponents(CreatePlant.location("fractionating_column"), CreatePlant.location("synthetic_crude_oil_bucket"),
                CreatePlant.location("synthetic_naphtha_bucket"), CreatePlant.location("ft_offgas_bucket"),
                CreatePlant.location("molten_paraffin_bucket"))
            .addStoryBoard("plant/fractionating_column", FractionationPonderScenes::synthetic);
        helper.forComponents(CreatePlant.location("fractionating_column"))
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
        helper.forComponents(CreatePlant.location("reaction_tower"), CreatePlant.location("reactor_separator_core"), CreatePlant.location("iron_catalyst"),
                CreatePlant.location("oxidation_catalyst"), CreatePlant.location("cracking_catalyst"), CreatePlant.location("polymerization_catalyst"),
                CreatePlant.location("hydrotreating_catalyst"), CreatePlant.location("hydrocracking_catalyst"))
            .addStoryBoard("plant/reaction_tower", ReactionTowerPonderScenes::tower)
            .addStoryBoard("plant/reaction_tower_separator", ReactionTowerPonderScenes::separator);
        helper.forComponents(CreatePlant.location("reaction_tower"), CreatePlant.location("reactor_separator_core"), CreatePlant.location("yellowcake"))
            .addStoryBoard("plant/reaction_tower", ReactionTowerPonderScenes::trays);
        helper.forComponents(CreatePlant.location("reaction_tower"), CreatePlant.location("shift_catalyst_core"),
                CreatePlant.location("iron_ft_catalyst_core"), CreatePlant.location("cobalt_ft_catalyst_core"),
                CreatePlant.location("zsm5_catalyst_core"), CreatePlant.location("purified_syngas_bucket"),
                CreatePlant.location("btx_bucket"))
            .addStoryBoard("plant/reaction_tower", ReactionTowerPonderScenes::synthesis);
        helper.forComponents(CreatePlant.location("reaction_tower"), CreatePlant.location("platinum_zeolite_core"),
                CreatePlant.location("cumene_catalyst_core"), CreatePlant.location("phenolic_board"),
                CreatePlant.location("molded_polymer"), CreatePlant.location("synthetic_rubber"))
            .addStoryBoard("plant/reaction_tower", ReactionTowerPonderScenes::materials);
        helper.forComponents(CreatePlant.location("ammonia_separator"))
            .addStoryBoard("chemistry/ammonia_separator", AmmoniaSeparatorPonderScenes::separator);
        helper.forComponents(CreatePlant.location("pipe_thermometer"), CreatePlant.location("pipe_pressure_gauge"), CreatePlant.location("pipe_flow_meter"))
            .addStoryBoard("plant/pipe_gauges", PipeGaugePonderScenes::gauges);
        helper.forComponents(CreatePlant.location("flare_tip"),CreatePlant.location("pipe_pressure_gauge"),CreatePlant.location("stainless_steel_pipe"))
            .addStoryBoard("plant/flare_stack",FlarePonderScenes::flare);
        helper.forComponents(CreatePlant.location("horizontal_fluid_tank"))
            .addStoryBoard("plant/horizontal_tank", HorizontalTankPonderScenes::tank);
        helper.forComponents(CreatePlant.location("stainless_boiler"))
            .addStoryBoard("plant/horizontal_tank", HorizontalTankPonderScenes::stainless);
        helper.forComponents(ResourceLocation.fromNamespaceAndPath("create", "nozzle"))
            .addStoryBoard("plant/material_pump", (scene,util)->MaterialPumpPonderScenes.pump(scene,util,"stainless_steel",true));
        helper.forComponents(CreatePlant.location("lidded_vessel"))
            .addStoryBoard("plant/lidded_vessel", LiddedVesselPonderScenes::vessel);
        helper.forComponents(CreatePlant.location("lidded_vessel"), CreatePlant.location("sulfur_dioxide_bucket"))
            .addStoryBoard("plant/material_pump", LiddedVesselPonderScenes::sulfur);
        helper.forComponents(CreatePlant.location("lidded_vessel"))
            .addStoryBoard("plant/lidded_vessel_2x2", LiddedVesselPonderScenes::compactVessel);
        helper.forComponents(CreatePlant.location("vessel_stirrer"), CreatePlant.location("reactor_impeller"), CreatePlant.location("stirred_tank"))
            .addStoryBoard("plant/vessel_internals", LiddedVesselPonderScenes::internals);
        helper.forComponents(CreatePlant.location("stirred_tank"),CreatePlant.location("vessel_stirrer"))
            .addStoryBoard("plant/vessel_basin",VesselBasinPonderScenes::basin);
        helper.forComponents(CreatePlant.location("lidded_vessel"),CreatePlant.location("pipe_pressure_gauge"))
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
                CreatePlant.location("stainless_steel_pipe"))
            .addStoryBoard("turbine/multiblock", ElectrolysisPonderScenes::turbineMultiblock);


        helper.forComponents(CreatePlant.location("natural_draft_cooling_tower"), CreatePlant.location("cooler_radiator"), CreatePlant.location("lidded_vessel"))
            .addStoryBoard("plant/natural_draft_tower", SeasonalThermalPonderScenes::seasons);

        helper.forComponents(CreatePlant.location("lidded_vessel"), com.simibubi.create.AllBlocks.FLUID_TANK.getId(), CreatePlant.location("antifreeze_bucket"))
            .addStoryBoard("plant/chemical_mixtures", MixturePonderScenes::mixing);
    }
}
