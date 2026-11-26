package net.createplant;

import net.createplant.registry.CEBlocks;
import net.createplant.registry.CEBlockEntityTypes;
import net.createplant.registry.CECreativeModeTabs;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEItems;
import net.createplant.registry.CERecipeTypes;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(CreatePlant.MOD_ID)
public class CreatePlant {
    public static final String MOD_ID = "create_refinery_n_reactors";

    public CreatePlant(IEventBus modEventBus, net.neoforged.fml.ModContainer container) {
        net.createplant.registry.CEMachineSounds.register(modEventBus);
        net.createplant.registry.CEParticles.register(modEventBus);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, net.createplant.transport.TransportConfig.SPEC);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, net.createplant.safety.HazardHudConfig.SPEC);
        net.createplant.registry.CETransport.register(modEventBus);
        net.createplant.registry.CEPressure.register(modEventBus);
        net.createplant.registry.CEFlare.register(modEventBus);
        net.createplant.registry.CEGasBell.register(modEventBus);
        net.createplant.registry.CEDisplays.register(modEventBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.createplant.block.entity.PipeGasFlow::attach);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.createplant.transport.PipeConnections::attach);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.createplant.transport.ContainmentWear::attach);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.createplant.mixture.GasPassThrough::attach);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.createplant.block.entity.ProductionMonitor::attach);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.createplant.safety.ToxicGas::maskKeepsGasOut);
        net.createplant.safety.ToxicGas.registerCloudStyles();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.createplant.safety.CorrosiveContact::protectWithClothing);
        net.createplant.registry.CEThermal.register(modEventBus);
        net.createplant.registry.CEMixtures.register(modEventBus);
        modEventBus.addListener(net.createplant.block.entity.HighPressureTurbinePartBlockEntity::registerCapabilities);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.createplant.block.ColumnLadder::climbSound);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.createplant.nuclear.FuelPoolStructure::changed);
        CEFluids.register(modEventBus);
        net.createplant.registry.CEProcessFluids.register(modEventBus);
        net.createplant.registry.CEProcessBlocks.register(modEventBus);
        modEventBus.addListener(net.createplant.block.entity.RadiatorBlockEntity::registerCapabilities);
        net.createplant.registry.CECompressor.register(modEventBus);
        net.createplant.registry.CEReciprocating.register(modEventBus);
        net.createplant.registry.CEUranium.register(modEventBus);
        net.createplant.registry.CEMetals.register(modEventBus);
        net.createplant.registry.CEWorldgen.register(modEventBus);
        net.createplant.registry.CEWetProcessing.register(modEventBus);
        net.createplant.registry.CEGasifier.register(modEventBus);
        net.createplant.compat.WaterGasBurner.register(modEventBus);
        net.createplant.registry.CEChlorAlkali.register(modEventBus);
        net.createplant.registry.CEFuelCell.register(modEventBus);
        net.createplant.registry.CEBattery.register(modEventBus);
        net.createplant.registry.CEBlasting.register(modEventBus);
        net.createplant.registry.CESafety.register(modEventBus);
        net.createplant.registry.CEFurnace.register(modEventBus);
        net.createplant.registry.CENuclear.register(modEventBus);
        net.createplant.registry.CEFusion.register(modEventBus);
        net.createplant.registry.CEPlantSystems.register(modEventBus);
        net.createplant.registry.CEFuelCycle.register(modEventBus);
        net.createplant.registry.CEIndustrialExpansion.register(modEventBus);
        net.createplant.registry.CEWaterTreatment.register(modEventBus);
        net.createplant.registry.CEBuildingMaterials.register(modEventBus);
        net.createplant.registry.CEFusionFuel.register(modEventBus);
        net.createplant.registry.CECrane.register(modEventBus);
        net.createplant.registry.CEElectrolysis.register(modEventBus);
        modEventBus.addListener(net.createplant.block.entity.HorizontalTankBlockEntity::registerCapabilities);
        modEventBus.addListener(net.createplant.block.entity.GasTankContainerBlockEntity::registerCapabilities);
        modEventBus.addListener(net.createplant.block.entity.HighPressureSeparatorBlockEntity::registerCapabilities);
        net.createplant.registry.CEHighStressMachines.register(modEventBus);
        net.createplant.registry.CELargePress.register(modEventBus);
        net.createplant.relay.CERelayComputer.register(modEventBus);
        net.createplant.registry.CEContinuousFactory.register(modEventBus);
        net.createplant.registry.CEReactor.register(modEventBus);
        net.createplant.registry.CEHeating.register(modEventBus);
        net.createplant.registry.CEElectricUtilities.register(modEventBus);
        net.createplant.registry.CETransmission.register(modEventBus);
        net.createplant.registry.CEConverter.register(modEventBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.createplant.block.ConverterStructure::changed);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.createplant.block.ConverterStructure::chunkLoaded);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.createplant.block.ConverterStructure::chunkUnloaded);
        net.createplant.registry.CEDistillation.register(modEventBus);
        modEventBus.addListener(net.createplant.block.entity.FractionatingColumnBlockEntity::registerCapabilities);
        modEventBus.addListener(net.createplant.block.entity.ReactorMachineBlockEntity::registerCapabilities);
        net.createplant.registry.CEOstwaldFluids.register(modEventBus);
        net.createplant.registry.CEPetroFluids.register(modEventBus);
        CEBlocks.register(modEventBus);
        CEBlockEntityTypes.register(modEventBus);
        CEItems.register(modEventBus);
        net.createplant.registry.CEWorldFluids.register(modEventBus);
        net.createplant.registry.CEGasBuckets.register(modEventBus);
        net.createplant.registry.CEPortableTools.register(modEventBus);
        net.createplant.registry.CEAgriculture.register(modEventBus);
        net.createplant.registry.CEPressureInfrastructure.register(modEventBus);
        net.createplant.registry.CECarbonization.register(modEventBus);
        net.createplant.registry.CEVault.register(modEventBus);
        net.createplant.registry.CEAssembly.register(modEventBus);
        net.createplant.registry.CEArm.register(modEventBus);
        CECreativeModeTabs.register(modEventBus);
        modEventBus.addListener(CEItems::registerFluidCapabilities);
        modEventBus.addListener(net.createplant.registry.CEWorldFluids::registerCapabilities);
        CERecipeTypes.register(modEventBus);
        net.createplant.recipe.StateFluidIngredient.register(modEventBus);
        modEventBus.addListener(net.createplant.block.entity.TankCasingBlockEntity::registerCapabilities);
        modEventBus.addListener(net.createplant.block.entity.PlantFluidTankBlockEntity::registerCapabilities);
        modEventBus.addListener(net.createplant.block.entity.SteamTurbineBlockEntity::registerCapabilities);
        modEventBus.addListener(net.createplant.block.entity.HighPressureTurbineEndBlockEntity::registerCapabilities);
        net.createplant.registry.CELegacyIds.register(modEventBus);
    }

    public static ResourceLocation location(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
