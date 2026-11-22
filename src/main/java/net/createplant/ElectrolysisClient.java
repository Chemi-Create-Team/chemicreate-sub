package net.createplant;

import net.createplant.client.HighPressureTurbineEndRenderer;
import net.createplant.client.HighPressureTurbinePartRenderer;
import net.createplant.client.LargeFlywheelRenderer;
import net.createplant.client.SteamTurbineRenderer;
import net.createplant.client.TankCasingRenderer;
import net.createplant.ponder.ElectrolysisPonderPlugin;
import net.createplant.registry.CEBlockEntityTypes;
import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = CreatePlant.MOD_ID, dist = Dist.CLIENT)
public class ElectrolysisClient {
    public ElectrolysisClient(IEventBus modEventBus) {
        modEventBus.addListener(ElectrolysisClient::clientSetup);
        modEventBus.addListener(ElectrolysisClient::registerRenderers);
        modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) -> event.registerSpriteSet(net.createplant.registry.CEParticles.TOWER_STEAM.get(), net.createplant.client.TowerSteamParticle.Provider::new));
        modEventBus.addListener(ElectrolysisClient::tintPowders);
        modEventBus.addListener(net.createplant.client.ProtectiveCapModel::register);
        net.createplant.client.CoolingFanRenderer.init();
        net.createplant.client.WetProcessRenderer.init();
        net.createplant.client.ElectricPumpRenderer.init();
        net.createplant.client.PressurePumpRenderer.init();
        net.createplant.client.CubicPressRenderer.init();
        net.createplant.client.ReciprocatingCompressorRenderer.init();
        net.createplant.client.ReactorMachineRenderer.init();
        net.createplant.client.LiddedVesselRenderer.init();
        net.createplant.client.ReactionTowerRenderer.init();
        net.createplant.client.ElectrolysisCellRenderer.init();
        net.createplant.client.ElectrodeHeadRenderer.init();
        net.createplant.client.ElectrolysisControllerRenderer.init();
        net.createplant.client.PipeGaugeRenderer.init();net.createplant.client.PulsationTubeRenderer.init();
        net.createplant.client.GasHolderRenderer.init();
        net.createplant.client.FuelChannelRenderer.init();
        net.createplant.client.RBMKPlugRenderer.init();
        net.createplant.client.CraneRenderer.init();
        net.createplant.client.QuarryRenderer.init();
        net.createplant.client.SprayerItemRenderer.init();
        net.createplant.client.FuelCycleMachineRenderer.init();
        net.createplant.client.ReactorControlConsoleRenderer.init();
        net.createplant.client.PlantTankModels.init();
        modEventBus.addListener(net.createplant.client.PlantTankModels::wrap);
        net.createplant.client.CasingModels.init();
        modEventBus.addListener(net.createplant.client.CasingModels::wrap);
        net.createplant.client.FinFanModels.init();
        modEventBus.addListener(net.createplant.client.FinFanModels::wrap);
        modEventBus.addListener(net.createplant.client.StainlessPipeModels::wrap);
        if (Boolean.getBoolean("createplant.clientQualityCheck")) NeoForge.EVENT_BUS.addListener(net.createplant.client.ClientQualityCheck::tick);
        if (Boolean.getBoolean("createplant.allBlocksClientCheck")) NeoForge.EVENT_BUS.addListener(net.createplant.client.AllBlocksClientCheck::tick);
        if (Boolean.getBoolean("createplant.powerPlantClientCheck")) NeoForge.EVENT_BUS.addListener(net.createplant.client.PowerPlantClientCheck::tick);
        if (System.getProperty("createplant.rbmkVariantsPlaytest")!=null) NeoForge.EVENT_BUS.addListener(net.createplant.client.RbmkVariantsPlaytestWorld::tick);
        if (System.getProperty("createplant.fusionPlaytest")!=null) NeoForge.EVENT_BUS.addListener(net.createplant.client.FusionPlaytestWorld::tick);
        if (Boolean.getBoolean("createplant.sulfurCyclePlaytest")) NeoForge.EVENT_BUS.addListener(net.createplant.client.SulfurCyclePlaytestWorld::tick);
        if (Boolean.getBoolean("createplant.syntheticFuelPlaytest")) NeoForge.EVENT_BUS.addListener(net.createplant.client.SyntheticFuelPlaytestWorld::tick);
        if (Boolean.getBoolean("createplant.fertilizerPlaytest")) NeoForge.EVENT_BUS.addListener(net.createplant.client.FertilizerPlaytestWorld::tick);
        modEventBus.addListener(net.createplant.client.GasCloudRenderer::registerShaders);
        NeoForge.EVENT_BUS.addListener(net.createplant.client.GasCloudRenderer::renderGroundMist);
        NeoForge.EVENT_BUS.addListener(net.createplant.client.GasCloudRenderer::fogColor);
        NeoForge.EVENT_BUS.addListener(net.createplant.client.GasCloudRenderer::fogDistance);
        NeoForge.EVENT_BUS.addListener(net.createplant.client.StructureGuideRenderer::tick);
        NeoForge.EVENT_BUS.addListener(net.createplant.client.MachineSounds::tick);
        NeoForge.EVENT_BUS.addListener(net.createplant.client.StructureGuideRenderer::render);
        NeoForge.EVENT_BUS.addListener(net.createplant.client.ProcessPortOverlay::tick);
        NeoForge.EVENT_BUS.addListener(net.createplant.client.ProcessPortOverlay::drawPorts);
        NeoForge.EVENT_BUS.addListener(net.createplant.client.ProcessPortOverlay::drawPrompt);
        NeoForge.EVENT_BUS.addListener(net.createplant.client.PipeQualityOverlay::goggles);
        NeoForge.EVENT_BUS.addListener(net.createplant.client.PipeQualityOverlay::tooltip);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> PonderIndex.addPlugin(new ElectrolysisPonderPlugin()));
        event.enqueueWork(net.createplant.client.StainlessPipeModels::setup);
        event.enqueueWork(() -> dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer.builder(net.createplant.registry.CEHighStressMachines.CENTRIFUGE_ENTITY.get())
            .factory(com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual::shaft).skipVanillaRender(be -> true).apply());
        event.enqueueWork(() -> dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer.builder(net.createplant.registry.CEReactor.STIRRER_ENTITY.get())
            .factory(com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual.of(com.simibubi.create.AllPartialModels.SHAFTLESS_COGWHEEL)).skipVanillaRender(be -> true).apply());
        event.enqueueWork(() -> dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer.builder(net.createplant.registry.CETransport.MATERIAL_PUMP_ENTITY.get())
            .factory(com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual.ofZ(com.simibubi.create.AllPartialModels.MECHANICAL_PUMP_COG))
            .skipVanillaRender(be -> true).apply());
        event.enqueueWork(() -> dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer.builder(net.createplant.registry.CEAssembly.CONSTRUCTOR_ENTITY.get())
            .factory(com.simibubi.create.content.schematics.cannon.SchematicannonVisual::new).skipVanillaRender(be -> false).apply());
        event.enqueueWork(net.createplant.client.CasingModels::setup);
        event.enqueueWork(() -> dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer.builder(net.createplant.registry.CEReciprocating.SHAFT_ENTITY.get())
            .factory(com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual::shaft).skipVanillaRender(be -> true).apply());
        event.enqueueWork(() -> dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer.builder(net.createplant.registry.CENuclear.SHIELD_DRIVE_ENTITY.get())
            .factory(com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual::shaft).skipVanillaRender(be -> true).apply());
        // The crane drive keeps its shaft in Flywheel while its bridge, trolley and mast use the block-entity renderer.
        event.enqueueWork(() -> dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer.builder(net.createplant.registry.CECrane.DRIVE_ENTITY.get())
            .factory(com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual::shaft).skipVanillaRender(be -> false).apply());
        // The quarry drive's two shaft halves may turn opposite ways, the outer one driving the side belt.
        event.enqueueWork(() -> dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer.builder(net.createplant.registry.CECrane.QUARRY_DRIVE_ENTITY.get())
            .factory(net.createplant.client.QuarryVisual::new).skipVanillaRender(be -> false).apply());
        event.enqueueWork(() -> dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer.builder(net.createplant.registry.CEFusion.VACUUM_PUMP_ENTITY.get())
            .factory(com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual::shaft).skipVanillaRender(be -> true).apply());
        event.enqueueWork(() -> dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer.builder(net.createplant.registry.CEConverter.TRUNNION_ENTITY.get())
            .factory(com.simibubi.create.content.contraptions.bearing.BearingVisual::new).skipVanillaRender(be -> true).apply());
    }

    /** The uranium powders are vanilla gunpowder in their own colours. */
    private static void tintPowders(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> 0xFFFFB850, net.createplant.registry.CEUranium.URANIUM_TRIOXIDE.get());
        event.register((stack, layer) -> 0xFF8A7464, net.createplant.registry.CEUranium.URANIUM_DIOXIDE.get());
        event.register((stack, layer) -> 0xFF96C882, net.createplant.registry.CEUranium.URANIUM_TETRAFLUORIDE.get());
        event.register((stack, layer) -> 0xFF7A8296, net.createplant.registry.CEUranium.ENRICHED_URANIUM_DIOXIDE.get());
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(net.createplant.registry.CESafety.GAS_CLOUD.get(), net.createplant.client.GasCloudRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEHighStressMachines.CENTRIFUGE_ENTITY.get(), com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEHighStressMachines.PRESS_ENTITY.get(), net.createplant.client.CubicPressRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CETransport.MATERIAL_PUMP_ENTITY.get(), com.simibubi.create.content.fluids.pump.PumpRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEAssembly.CONSTRUCTOR_ENTITY.get(), net.createplant.client.FacilityConstructorRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEElectricUtilities.PUMP_ENTITY.get(), net.createplant.client.ElectricPumpRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEPressureInfrastructure.PUMP_ENTITY.get(), net.createplant.client.PressurePumpRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEPressureInfrastructure.CONNECTOR_ENTITY.get(), net.createplant.client.HoseConnectorRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEElectrolysis.CELL_ENTITY.get(), net.createplant.client.ElectrolysisCellRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEElectrolysis.HEAD_ENTITY.get(), net.createplant.client.ElectrodeHeadRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEElectrolysis.CONTROLLER_ENTITY.get(), net.createplant.client.ElectrolysisControllerRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEFusionFuel.COLD_BOX_ENTITY.get(), com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEHeating.REGULATOR_ENTITY.get(),net.createplant.client.FlowRegulatorRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CETransport.PIPE_GAUGE_ENTITY.get(),net.createplant.client.PipeGaugeRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEGasBell.TANK_ENTITY.get(),net.createplant.client.GasHolderRenderer::new);
        event.registerEntityRenderer(net.createplant.registry.CEGasBell.MOVING_SLEEVE.get(),com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEFlare.TIP_ENTITY.get(),net.createplant.client.FlareTipRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEProcessBlocks.COOLING_FAN_ENTITY.get(), net.createplant.client.CoolingFanRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CETransport.STAINLESS_GLASS_PIPE_ENTITY.get(), com.simibubi.create.content.fluids.pipes.TransparentStraightPipeRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CETransport.GAS_RECEIVER_ENTITY.get(), net.createplant.client.PulsationTubeRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CETransport.PRESSURE_TANK_ENTITY.get(), com.simibubi.create.content.fluids.tank.FluidTankRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CETransport.STAINLESS_STEEL_TANK_ENTITY.get(), com.simibubi.create.content.fluids.tank.FluidTankRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CETransport.CRYOGENIC_TANK_ENTITY.get(), com.simibubi.create.content.fluids.tank.FluidTankRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CETransport.HORIZONTAL_FLUID_TANK_ENTITY.get(), net.createplant.client.HorizontalTankRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CETransport.STAINLESS_BOILER_ENTITY.get(), net.createplant.client.HorizontalTankRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CETransport.LIDDED_VESSEL_ENTITY.get(), net.createplant.client.LiddedVesselRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEReactor.STIRRER_ENTITY.get(), net.createplant.client.VesselStirrerRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CETransport.REACTION_TOWER_ENTITY.get(), net.createplant.client.ReactionTowerRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CETransport.AMMONIA_SEPARATOR_ENTITY.get(), net.createplant.client.AmmoniaSeparatorRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEReactor.ENTITY.get(), net.createplant.client.ReactorMachineRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CENuclear.FUEL_CHANNEL_ENTITY.get(), net.createplant.client.FuelChannelRenderer::new);
        // The cap renderer offsets each pressure-channel plug independently and lets Create draw its frequency slots.
        event.registerBlockEntityRenderer(net.createplant.registry.CENuclear.TOP_CAP_ENTITY.get(), net.createplant.client.RBMKPlugRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CENuclear.ROD_LINK_ENTITY.get(), com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CENuclear.ROD_CONTROLLER_ENTITY.get(), com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CENuclear.FUEL_POOL_ENTITY.get(), net.createplant.client.FuelPoolRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CENuclear.REACTOR_CONTROL_CONSOLE_ENTITY.get(), net.createplant.client.ReactorControlConsoleRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CENuclear.REACTOR_CONSOLE_DISPLAY_ENTITY.get(), net.createplant.client.ReactorConsoleDisplayRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CENuclear.SHIELD_DRIVE_ENTITY.get(), com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CECrane.DRIVE_ENTITY.get(), net.createplant.client.CraneRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CECrane.QUARRY_DRIVE_ENTITY.get(), net.createplant.client.QuarryRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEConverter.TRUNNION_ENTITY.get(), com.simibubi.create.content.contraptions.bearing.BearingRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEReciprocating.COMPRESSOR_ENTITY.get(), net.createplant.client.ReciprocatingCompressorRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEReciprocating.SHAFT_ENTITY.get(), com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(CEBlockEntityTypes.STEAM_TURBINE.get(), SteamTurbineRenderer::new);
        event.registerBlockEntityRenderer(CEBlockEntityTypes.LARGE_FLYWHEEL.get(), LargeFlywheelRenderer::new);
        event.registerBlockEntityRenderer(CEBlockEntityTypes.HIGH_PRESSURE_TURBINE_PART.get(), HighPressureTurbinePartRenderer::new);
        event.registerBlockEntityRenderer(CEBlockEntityTypes.HIGH_PRESSURE_TURBINE_END.get(), HighPressureTurbineEndRenderer::new);
        event.registerBlockEntityRenderer(CEBlockEntityTypes.TANK_CASING.get(), TankCasingRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEFusion.CONTROLLER_ENTITY.get(), net.createplant.client.FusionReactorRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEFuelCycle.MACHINE_ENTITY.get(), net.createplant.client.FuelCycleMachineRenderer::new);
        event.registerBlockEntityRenderer(net.createplant.registry.CEFusion.VACUUM_PUMP_ENTITY.get(), com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer::new);
    }
}
